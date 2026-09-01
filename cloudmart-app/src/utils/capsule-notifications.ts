import { Platform } from 'react-native'
import Constants from 'expo-constants'
import type { CapsuleItem } from '@/types'

/**
 * 胶囊到期本地推送（文档 Sprint 2.4：APP 本地推送提醒到期胶囊）。
 *
 * 关键约束：expo-notifications 在 Expo Go（Android，SDK 53+）不可用——
 * require 本身能成功但模块内部经 LogBox 报软错误（非同步 throw，
 * try/catch 接不住），因此必须在 require 之前用 Constants 识别
 * Expo Go（executionEnvironment === 'storeClient'）直接跳过加载：
 * Expo Go(Android) 调试环境降级为无本地推送（不阻断胶囊功能），
 * development build / 正式 APK / iOS Expo Go 中完整生效。
 *
 * 失败策略：静默降级（Fail Open）——权限被拒或调度失败不阻断胶囊功能。
 */

type NotificationsModule = typeof import('expo-notifications')

let cachedModule: NotificationsModule | null | undefined
let handlerInitialized = false

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

/** 前景展示样式初始化（幂等，仅首次调用生效） */
export function initCapsuleNotifications(): void {
    const Notifications = getNotifications()
    if (!Notifications || handlerInitialized) return
    handlerInitialized = true
    Notifications.setNotificationHandler({
        handleNotification: async () => ({
            shouldShowBanner: true,
            shouldShowList: true,
            shouldPlaySound: true,
            shouldSetBadge: false,
        }),
    })
}

/** 订阅通知点击回调（返回取消订阅函数；不支持环境返回 null） */
export function subscribeCapsuleNotificationTap(onTap: (capsuleId: number) => void): (() => void) | null {
    const Notifications = getNotifications()
    if (!Notifications) return null
    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
        const data = response.notification.request.content.data as { capsuleId?: number; type?: string }
        if (data?.type === 'WISH_CAPSULE_AVAILABLE' && data.capsuleId) {
            onTap(data.capsuleId)
        }
    })
    return () => subscription.remove()
}

export async function ensureNotificationPermission(): Promise<boolean> {
    const Notifications = getNotifications()
    if (!Notifications) return false
    const current = await Notifications.getPermissionsAsync()
    if (current.granted) return true
    const asked = await Notifications.requestPermissionsAsync()
    return asked.granted
}

/** 创建/重排到期提醒：openAt 已到期则跳过 */
export async function scheduleCapsuleReminder(capsule: CapsuleItem): Promise<void> {
    const Notifications = getNotifications()
    if (!Notifications) return
    if (capsule.status !== 'SEALED' && capsule.status !== 'AVAILABLE') return
    const openAt = new Date(capsule.openAt)
    if (openAt.getTime() <= Date.now()) return

    await cancelCapsuleReminder(capsule.id)
    await Notifications.scheduleNotificationAsync({
        identifier: `capsule-${capsule.id}`,
        content: {
            title: '你的时间胶囊到期了',
            body: `「${capsule.title}」已到达开启时刻，来拆开这封过去的信吧`,
            data: { capsuleId: capsule.id, type: 'WISH_CAPSULE_AVAILABLE' },
        },
        trigger: {
            type: Notifications.SchedulableTriggerInputTypes.DATE,
            date: openAt,
        },
    })
}

/** 取消到期提醒（取消胶囊/开启胶囊后调用） */
export async function cancelCapsuleReminder(capsuleId: number | string): Promise<void> {
    const Notifications = getNotifications()
    if (!Notifications) return
    try {
        await Notifications.cancelScheduledNotificationAsync(`capsule-${capsuleId}`)
    } catch {
        // identifier 不存在时静默（幂等）
    }
}
