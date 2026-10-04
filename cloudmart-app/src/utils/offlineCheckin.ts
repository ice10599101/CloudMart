import * as SQLite from 'expo-sqlite'
import { wishApi } from '@/api/wish'
import NetInfo from '@react-native-community/netinfo'

/**
 * 离线打卡队列（Sprint 1.3 APP 验收；T17 重构——意图不丢失）：
 *
 * <p>T17 修复：</p>
 * <ul>
 *   <li>队列按账号分区（user_id 列）——切账号不提交原账号队列；</li>
 *   <li>每项生成稳定 intentId 并随请求携带（X-Idempotency-Key）——服务端
 *       幂等加速层/持久操作记录按其去重，网络重试不产生第二次打卡；</li>
 *   <li>flush 不再无条件删除：仅服务端确认成功或确认同事实已完成
 *       （WISH_ALREADY_CHECKIN_TODAY）才出队；网络/5xx 保留待重试
 *       （退避），永久错误标记 failed 可见可清理；</li>
 *   <li>flush 互斥锁防并行处理同项；</li>
 *   <li>跨天不补记过去日期：补传时发现已过打卡日期（服务端按当日语义判定），
 *       标记 expired 展示"已过打卡日期"，保留内容供本人重新确认今日打卡，
 *       不静默把昨日操作记作今天。</li>
 * </ul>
 */

export interface QueuedCheckin {
    id: number
    userId: number | string
    wishId: string
    content: string | null
    intentId: string
    queuedAt: string
    status: 'pending' | 'failed' | 'expired'
    attempts: number
    nextRetryAt: string | null
    lastError: string | null
}

/** 退避序列（秒）：1 分钟 → 5 分钟 → 30 分钟，之后按 30 分钟循环 */
const RETRY_BACKOFF_SECONDS = [60, 300, 1800]

function newIntentId(): string {
    const g = globalThis as { crypto?: { randomUUID?: () => string } }
    return g.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null

async function getDb(): Promise<SQLite.SQLiteDatabase> {
    if (!dbPromise) {
        dbPromise = SQLite.openDatabaseAsync('wish-offline.db').then((db) => {
            return db.execAsync(`
        CREATE TABLE IF NOT EXISTS offline_checkins (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          user_id TEXT NOT NULL DEFAULT '',
          wish_id TEXT NOT NULL,
          content TEXT,
          intent_id TEXT NOT NULL DEFAULT '',
          queued_at TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'pending',
          attempts INTEGER NOT NULL DEFAULT 0,
          next_retry_at TEXT,
          last_error TEXT
        );
      `).then(async () => {
                // 旧表升级：新增列（重复执行无害）
                const cols = await db.getAllAsync<{ name: string }>(
                    'PRAGMA table_info(offline_checkins)')
                const names = cols.map((c) => c.name)
                if (!names.includes('user_id')) {
                    await db.execAsync('ALTER TABLE offline_checkins ADD COLUMN user_id TEXT NOT NULL DEFAULT \'\'')
                }
                if (!names.includes('intent_id')) {
                    await db.execAsync('ALTER TABLE offline_checkins ADD COLUMN intent_id TEXT NOT NULL DEFAULT \'\'')
                }
                if (!names.includes('status')) {
                    await db.execAsync('ALTER TABLE offline_checkins ADD COLUMN status TEXT NOT NULL DEFAULT \'pending\'')
                }
                if (!names.includes('attempts')) {
                    await db.execAsync('ALTER TABLE offline_checkins ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0')
                }
                if (!names.includes('next_retry_at')) {
                    await db.execAsync('ALTER TABLE offline_checkins ADD COLUMN next_retry_at TEXT')
                }
                if (!names.includes('last_error')) {
                    await db.execAsync('ALTER TABLE offline_checkins ADD COLUMN last_error TEXT')
                }
            }).then(() => SQLite.openDatabaseAsync('wish-offline.db'))
        })
    }
    return dbPromise
}

/** 当前是否在线 */
export async function isOnline(): Promise<boolean> {
    const state = await NetInfo.fetch()
    return Boolean(state.isConnected && state.isInternetReachable !== false)
}

/** 断网入队（按账号分区；返回 true = 已入队离线队列） */
export async function enqueueCheckin(
    userId: number | string,
    wishId: string,
    content: string | null,
): Promise<boolean> {
    const db = await getDb()
    await db.runAsync(
        'INSERT INTO offline_checkins (user_id, wish_id, content, intent_id, queued_at, status, attempts) '
        + 'VALUES (?, ?, ?, ?, ?, ?, 0)',
        String(userId), wishId, content, newIntentId(), new Date().toISOString(), 'pending',
    )
    return true
}

/** 队列内待补传数量（当前账号） */
export async function pendingCount(userId: number | string): Promise<number> {
    const db = await getDb()
    const row = await db.getFirstAsync<{ cnt: number }>(
        'SELECT COUNT(*) AS cnt FROM offline_checkins WHERE user_id = ? AND status = \'pending\'',
        String(userId),
    )
    return row?.cnt ?? 0
}

/** 清理当前账号的失败/过期项（用户显式操作） */
export async function clearFinished(userId: number | string): Promise<void> {
    const db = await getDb()
    await db.runAsync(
        'DELETE FROM offline_checkins WHERE user_id = ? AND status != \'pending\'',
        String(userId),
    )
}

let flushing = false

/**
 * 冲洗队列（T17：仅当前账号；互斥；失败保留退避重试）。
 * 由网络恢复监听与应用启动时调用。
 */
export async function flushQueue(
    userId: number | string,
    onItemDone?: (wishId: string, ok: boolean, streak?: number) => void,
): Promise<{ flushed: number; failed: number }> {
    if (flushing) {
        return { flushed: 0, failed: 0 }
    }
    flushing = true
    try {
        const db = await getDb()
        const rows = await db.getAllAsync<QueuedCheckin>(
            'SELECT id, user_id AS userId, wish_id AS wishId, content, intent_id AS intentId, '
            + 'queued_at AS queuedAt, status, attempts, next_retry_at AS nextRetryAt, '
            + 'last_error AS lastError '
            + 'FROM offline_checkins WHERE user_id = ? AND status = \'pending\' ORDER BY id',
            String(userId),
        )
        let flushed = 0
        let failed = 0
        for (const row of rows) {
            if (row.nextRetryAt && new Date(row.nextRetryAt).getTime() > Date.now()) {
                continue
            }
            try {
                const res = await wishApi.checkinWish(
                    row.wishId, row.content ?? undefined, row.intentId)
                if (res.data?.success) {
                    const { currentStreak } = res.data.data
                    onItemDone?.(row.wishId, true, currentStreak)
                    await db.runAsync('DELETE FROM offline_checkins WHERE id = ?', row.id)
                    flushed++
                } else {
                    failed++
                }
            } catch (error) {
                const code = (error as { response?: { data?: { error?: { code?: string } } } })
                    ?.response?.data?.error?.code
                if (code === 'WISH_ALREADY_CHECKIN_TODAY') {
                    // 同事实已完成：确认成功出队
                    onItemDone?.(row.wishId, true)
                    await db.runAsync('DELETE FROM offline_checkins WHERE id = ?', row.id)
                    flushed++
                } else if (code === 'WISH_CHECKIN_DATE_PASSED') {
                    // T17：跨天不补记——标记过期供用户重新确认，不静默改记今天
                    await db.runAsync(
                        'UPDATE offline_checkins SET status = \'expired\', last_error = ? WHERE id = ?',
                        '已过打卡日期', row.id)
                    onItemDone?.(row.wishId, false)
                    failed++
                } else {
                    // 网络/5xx/未知业务失败：保留并退避重试
                    const attempts = (row.attempts ?? 0) + 1
                    const backoff = RETRY_BACKOFF_SECONDS[
                        Math.min(attempts - 1, RETRY_BACKOFF_SECONDS.length - 1)]
                    await db.runAsync(
                        'UPDATE offline_checkins SET attempts = ?, next_retry_at = ?, '
                        + 'last_error = ? WHERE id = ?',
                        attempts,
                        new Date(Date.now() + backoff * 1000).toISOString(),
                        code ?? 'NETWORK_ERROR',
                        row.id)
                    onItemDone?.(row.wishId, false)
                    failed++
                }
            }
        }
        return { flushed, failed }
    } finally {
        flushing = false
    }
}
