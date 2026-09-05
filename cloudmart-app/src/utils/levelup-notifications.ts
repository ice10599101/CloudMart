import { Platform } from 'react-native'
import Constants from 'expo-constants'
import type { LevelUpEvent } from '@/api/wish'

/**
 * 等级提升本地推送（文档 L1917/L1923：APP 等级提升推送本地通知）。
 *
 * 与 capsule-notifications 共享 expo-notifications 的惰性加载策略：
 * Expo Go（Android）不可用 → 静默降级（Fail-Open）。
 * 失败策略：权限被拒或调度失败不阻断等级提升弹窗。
 */

type NotificationsModule = typeof import('expo-notifications')

let cachedModule: NotificationsModule | null | undefined

/** 惰性获取 expo-notifications：web / Expo Go 恒 null，其余环境加载一次并缓存 */
function getNotifications(): NotificationsModule | null {
    if (Platform.OS === 'web') return null
    if (Constants.executionEnvironment === 'storeClient') return null
    if (cachedModule !== undefined) return cachedModule
    try {
        cachedModule = require('expo-notifications') as NotificationsModule
    } catch {
        cachedModule = null
    }
    return cachedModule
}

/**
 * 立即推送等级提升本地通知（非定时，即时展示）。
 *
 * 文档 L1917：APP 端等级提升触发本地 Push 通知。
 * 与 LevelUpModal 弹窗独立：弹窗在应用内可见时展示，
 * 推送确保用户即便切到后台也能看到等级提升事件。
 */
export async function notifyLevelUp(levelUp: LevelUpEvent): Promise<void> {
    const Notifications = getNotifications()
    if (!Notifications) return

    try {
        await Notifications.scheduleNotificationAsync({
            identifier: `levelup-${levelUp.newLevel}-${Date.now()}`,
            content: {
                title: `✨ 等级提升！Lv.${levelUp.newLevel}`,
                body: `恭喜晋升「${levelUp.newLevelTitle}」，继续探索心愿宇宙吧`,
                data: {
                    type: 'WISH_LEVEL_UP',
                    previousLevel: levelUp.previousLevel,
                    newLevel: levelUp.newLevel,
                    newLevelTitle: levelUp.newLevelTitle,
                },
            },
            // 触发器：null 或 {} 表示立即触发（expo-notifications >=0.14 已废弃 false）
            trigger: null,
        })
    } catch {
        // 调度失败静默降级（Fail-Open）：不阻断弹窗
    }
}
