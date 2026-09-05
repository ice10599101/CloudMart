import * as SQLite from 'expo-sqlite'
import { wishApi } from '@/api/wish'
import NetInfo from '@react-native-community/netinfo'

/**
 * 离线打卡队列（Sprint 1.3 APP 验收，四AB APP P0-2）：
 * 断网时打卡写入 expo-sqlite 队列；网络恢复后静默补传（幂等——
 * 服务端 uk_checkin_daily 每日一次兜底，重复提交 409 视为已成功）。
 */

export interface QueuedCheckin {
    id: number
    wishId: string
    content: string | null
    queuedAt: string
}

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null

async function getDb(): Promise<SQLite.SQLiteDatabase> {
    if (!dbPromise) {
        dbPromise = SQLite.openDatabaseAsync('wish-offline.db').then((db) => {
            return db.execAsync(`
        CREATE TABLE IF NOT EXISTS offline_checkins (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          wish_id TEXT NOT NULL,
          content TEXT,
          queued_at TEXT NOT NULL
        );
      `)
        }).then(() => SQLite.openDatabaseAsync('wish-offline.db'))
    }
    return dbPromise
}

/** 当前是否在线 */
export async function isOnline(): Promise<boolean> {
    const state = await NetInfo.fetch()
    return Boolean(state.isConnected && state.isInternetReachable !== false)
}

/** 断网入队（返回 true = 已入队离线队列） */
export async function enqueueCheckin(wishId: string, content: string | null): Promise<boolean> {
    const db = await getDb()
    await db.runAsync(
        'INSERT INTO offline_checkins (wish_id, content, queued_at) VALUES (?, ?, ?)',
        wishId, content, new Date().toISOString(),
    )
    return true
}

/** 队列内待补传数量 */
export async function pendingCount(): Promise<number> {
    const db = await getDb()
    const row = await db.getFirstAsync<{ cnt: number }>('SELECT COUNT(*) AS cnt FROM offline_checkins')
    return row?.cnt ?? 0
}

/**
 * 冲洗队列：逐条补传；成功/WISH_ALREADY_CHECKIN_TODAY（409 幂等）均删除。
 * 由网络恢复监听与应用启动时调用。
 */
export async function flushQueue(
    onItemDone?: (wishId: string, ok: boolean, streak?: number) => void,
): Promise<{ flushed: number; failed: number }> {
    const db = await getDb()
    const rows = await db.getAllAsync<QueuedCheckin>(
        'SELECT id, wish_id AS wishId, content, queued_at AS queuedAt FROM offline_checkins ORDER BY id',
    )
    let flushed = 0
    let failed = 0
    for (const row of rows) {
        try {
            const res = await wishApi.checkinWish(row.wishId, row.content ?? undefined)
            if (res.data?.success) {
                const { currentStreak } = res.data.data
                onItemDone?.(row.wishId, true, currentStreak)
                flushed++
            } else {
                failed++
            }
        } catch (error) {
            const code = (error as { response?: { data?: { error?: { code?: string } } } })
                ?.response?.data?.error?.code
            if (code === 'WISH_ALREADY_CHECKIN_TODAY') {
                onItemDone?.(row.wishId, true)
                flushed++
            } else {
                failed++
                onItemDone?.(row.wishId, false)
            }
        }
        await db.runAsync('DELETE FROM offline_checkins WHERE id = ?', row.id)
    }
    return { flushed, failed }
}
