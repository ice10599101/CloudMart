import * as SQLite from 'expo-sqlite'
import { wishApi } from '@/api/wish'
import { isBusinessError } from '@/utils/request'
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

/**
 * WISH-02 统一状态机：QUEUED 待发 / SENDING 在途（崩溃残留按 QUEUED 恢复）/
 * RETRYABLE 可重试失败（退避）/ FAILED 永久失败（无权限·内容不合规·超限）/
 * EXPIRED 意图日期已过（跨日不补记，保留内容可作今天新记录）/ CONFIRMED 服务端确认。
 */
export type QueuedCheckinStatus =
    | 'QUEUED' | 'SENDING' | 'RETRYABLE' | 'FAILED' | 'EXPIRED' | 'CONFIRMED'

export interface QueuedCheckin {
    id: number
    userId: number | string
    wishId: string
    content: string | null
    intentId: string
    queuedAt: string
    status: QueuedCheckinStatus
    attempts: number
    nextRetryAt: string | null
    lastError: string | null
    /** WISH-02：意图业务日（服务端口径 UTC+8 复算；服务端核验跨日拒绝不补记） */
    intendedBusinessDate: string | null
    /** 原发生时刻（入队时刻 ISO-8601），服务端落 occurred_at_utc 事实 */
    occurredAt: string | null
}

/** 永久业务失败：重试无意义，直接终态 FAILED（区别于网络/5xx 可重试） */
const PERMANENT_ERROR_CODES = new Set([
    'WISH_NOT_FOUND',       // 心愿被删除
    'WISH_NOT_AUTHOR',      // 无权限
    'WISH_STATUS_CONFLICT', // 心愿已完成/归档不可打卡
    'WISH_VALIDATION_ERROR', // 内容不合规/超长
])

/** 重试上限：超过后转 FAILED（限重试，避免无限 30 分钟循环） */
const MAX_ATTEMPTS = 10

/** 服务端经济业务日（Asia/Shanghai=UTC+8 无夏令时，客户端可靠复算；仅供意图标记，授予与否由服务端核验） */
function currentBusinessDate(): string {
    return new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10)
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
          status TEXT NOT NULL DEFAULT 'QUEUED',
          attempts INTEGER NOT NULL DEFAULT 0,
          next_retry_at TEXT,
          last_error TEXT,
          intended_business_date TEXT,
          occurred_at TEXT
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
                if (!names.includes('intended_business_date')) {
                    await db.execAsync('ALTER TABLE offline_checkins ADD COLUMN intended_business_date TEXT')
                }
                if (!names.includes('occurred_at')) {
                    await db.execAsync('ALTER TABLE offline_checkins ADD COLUMN occurred_at TEXT')
                }
                // WISH-02：旧三态迁移到六态状态机
                await db.execAsync(
                    "UPDATE offline_checkins SET status = 'QUEUED' WHERE status = 'pending'")
                await db.execAsync(
                    "UPDATE offline_checkins SET status = 'RETRYABLE' WHERE status = 'failed'")
                await db.execAsync(
                    "UPDATE offline_checkins SET status = 'EXPIRED' WHERE status = 'expired'")
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
    const occurredAt = new Date().toISOString()
    await db.runAsync(
        'INSERT INTO offline_checkins (user_id, wish_id, content, intent_id, queued_at, status, attempts, '
        + 'intended_business_date, occurred_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)',
        String(userId), wishId, content, newIntentId(), occurredAt, 'QUEUED', 0,
        currentBusinessDate(), occurredAt,
    )
    return true
}

/** 队列内待补传数量（当前账号） */
export async function pendingCount(userId: number | string): Promise<number> {
    const db = await getDb()
    const row = await db.getFirstAsync<{ cnt: number }>(
        "SELECT COUNT(*) AS cnt FROM offline_checkins WHERE user_id = ? AND status IN ('QUEUED', 'RETRYABLE', 'SENDING')",
        String(userId),
    )
    return row?.cnt ?? 0
}

/** 清理当前账号的失败/过期项（用户显式操作） */
export async function clearFinished(userId: number | string): Promise<void> {
    const db = await getDb()
    await db.runAsync(
        "DELETE FROM offline_checkins WHERE user_id = ? AND status IN ('CONFIRMED', 'FAILED', 'EXPIRED')",
        String(userId),
    )
}

let flushing = false

/**
 * 冲洗队列（T17/WISH-02：仅当前账号；互斥；六态状态机；意图日期服务端核验）。
 * 由网络恢复监听与应用启动时调用。
 *
 * @param shouldAbort 会话守卫——每项处理前调用，返回 true 即中止（退出登录/切账号
 *   时旧批次停止，不串写其他账号队列；队列按 user_id 分区，不删除他人数据）
 */
export async function flushQueue(
    userId: number | string,
    onItemDone?: (wishId: string, ok: boolean, streak?: number) => void,
    shouldAbort?: () => boolean,
): Promise<{ flushed: number; failed: number }> {
    if (flushing) {
        return { flushed: 0, failed: 0 }
    }
    flushing = true
    try {
        const db = await getDb()
        // WISH-02：QUEUED/SENDING（崩溃残留按待发恢复）/RETRYABLE 均参与冲洗
        const rows = await db.getAllAsync<QueuedCheckin>(
            'SELECT id, user_id AS userId, wish_id AS wishId, content, intent_id AS intentId, '
            + 'queued_at AS queuedAt, status, attempts, next_retry_at AS nextRetryAt, '
            + 'last_error AS lastError, intended_business_date AS intendedBusinessDate, '
            + 'occurred_at AS occurredAt '
            + "FROM offline_checkins WHERE user_id = ? AND status IN ('QUEUED', 'SENDING', 'RETRYABLE') "
            + 'ORDER BY id',
            String(userId),
        )
        let flushed = 0
        let failed = 0
        for (const row of rows) {
            // 会话守卫：切账号/退出登录立即中止旧批次
            if (shouldAbort?.()) break
            if (row.nextRetryAt && new Date(row.nextRetryAt).getTime() > Date.now()) {
                continue
            }
            // SENDING：标记在途（进程崩溃后下次 flush 仍会捞起重试，intentId 幂等兜底）
            await db.runAsync(
                "UPDATE offline_checkins SET status = 'SENDING' WHERE id = ?", row.id)
            try {
                const res = await wishApi.checkinWish(
                    row.wishId, row.content ?? undefined, row.intentId,
                    row.intendedBusinessDate ?? undefined, row.occurredAt ?? undefined)
                const { currentStreak } = res.data.data
                onItemDone?.(row.wishId, true, currentStreak)
                await db.runAsync(
                    "UPDATE offline_checkins SET status = 'CONFIRMED', last_error = NULL WHERE id = ?",
                    row.id)
                flushed++
            } catch (error) {
                // R25：失败统一为 BusinessError，机器码在 error.code
                const code = isBusinessError(error) ? error.code : undefined
                if (code === 'WISH_ALREADY_CHECKIN_TODAY') {
                    // 同事实已完成：确认（CONFIRMED）
                    onItemDone?.(row.wishId, true)
                    await db.runAsync(
                        "UPDATE offline_checkins SET status = 'CONFIRMED', last_error = ? WHERE id = ?",
                        '同事实已完成', row.id)
                    flushed++
                } else if (code === 'WISH_CHECKIN_DATE_PASSED') {
                    // WISH-02：意图日期已过业务日——EXPIRED 不发奖励，保留内容供用户
                    // 明确"作为今天的新记录提交"（产生新意图），不静默改记今天
                    await db.runAsync(
                        "UPDATE offline_checkins SET status = 'EXPIRED', last_error = ? WHERE id = ?",
                        '已过打卡日期，可作今天的新记录重新提交', row.id)
                    onItemDone?.(row.wishId, false)
                    failed++
                } else if (code !== undefined && PERMANENT_ERROR_CODES.has(code)) {
                    // 永久业务失败：心愿被删/无权限/状态冲突/内容不合规——终态 FAILED 不再重试
                    await db.runAsync(
                        "UPDATE offline_checkins SET status = 'FAILED', last_error = ? WHERE id = ?",
                        code, row.id)
                    onItemDone?.(row.wishId, false)
                    failed++
                } else {
                    // 网络/5xx/未知业务失败：保留并退避重试；超限转 FAILED（限重试）
                    const attempts = (row.attempts ?? 0) + 1
                    if (attempts >= MAX_ATTEMPTS) {
                        await db.runAsync(
                            "UPDATE offline_checkins SET status = 'FAILED', attempts = ?, "
                            + "last_error = ? WHERE id = ?",
                            attempts, code ?? 'MAX_ATTEMPTS_EXCEEDED', row.id)
                    } else {
                        const backoff = RETRY_BACKOFF_SECONDS[
                            Math.min(attempts - 1, RETRY_BACKOFF_SECONDS.length - 1)]
                        await db.runAsync(
                            'UPDATE offline_checkins SET status = \'RETRYABLE\', attempts = ?, '
                            + 'next_retry_at = ?, last_error = ? WHERE id = ?',
                            attempts,
                            new Date(Date.now() + backoff * 1000).toISOString(),
                            code ?? 'NETWORK_ERROR',
                            row.id)
                    }
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

/**
 * WISH-02：把 EXPIRED 项"作为今天的新记录提交"——产生新意图（新 intentId/
 * 新意图日期），原 EXPIRED 项保留为审计终态。
 */
export async function resubmitAsToday(userId: number | string, id: number): Promise<boolean> {
    const db = await getDb()
    const row = await db.getFirstAsync<{ wish_id: string; content: string | null }>(
        'SELECT wish_id, content FROM offline_checkins WHERE id = ? AND user_id = ? AND status = \'EXPIRED\'',
        id, String(userId),
    )
    if (!row) return false
    await enqueueCheckin(userId, row.wish_id, row.content)
    return true
}

/** 队列明细（当前账号全部状态；状态页展示/重试/删除/复制内容用） */
export async function listQueue(userId: number | string): Promise<QueuedCheckin[]> {
    const db = await getDb()
    return db.getAllAsync<QueuedCheckin>(
        'SELECT id, user_id AS userId, wish_id AS wishId, content, intent_id AS intentId, '
        + 'queued_at AS queuedAt, status, attempts, next_retry_at AS nextRetryAt, '
        + 'last_error AS lastError, intended_business_date AS intendedBusinessDate, '
        + 'occurred_at AS occurredAt '
        + 'FROM offline_checkins WHERE user_id = ? ORDER BY id DESC',
        String(userId),
    )
}

/** 删除单条队列项（用户显式操作） */
export async function removeItem(userId: number | string, id: number): Promise<void> {
    const db = await getDb()
    await db.runAsync('DELETE FROM offline_checkins WHERE id = ? AND user_id = ?', id, String(userId))
}
