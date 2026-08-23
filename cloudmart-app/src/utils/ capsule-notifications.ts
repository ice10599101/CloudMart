import { Platform } from 'react-native'
import * as Notifications from 'expo-notifications'
import type { CapsuleItem } from '@/types'

/**
 * 胶囊到期本地推送（文档 Sprint 2.4：APP 本地推送提醒到期胶囊）。
 *
 * 策略：创建成功即调度一条 CalendarTrigger 本地通知；取消胶囊时按
 * userInfo.capsuleId 反查并撤销。到期判定仍以服务端 UTC openAt 为准，
 * 本地推送仅为触达辅助——设备时区变化由系统 CalendarTrigger 自动换算。
 *
 * 失败策略：静默降级（Fail Open）——推送权限被拒或调度失败不阻断胶囊功能。
 */

/** 通知前景展示样式（iOS/Android 前景收到时也弹横幅） */
Notifications.setNotificationHandler({
    handleNotification: async () => ({
        shouldShowBanner: true,
        shouldShowList: true,
        shouldPlaySound: true,
        shouldSetBadge: false,
    }),
})

export async function ensureNotificationPermission(): Promise<boolean> {
    if (Platform.OS === 'web') return false
    const current = await Notifications.getPermissionsAsync()
    if (current.granted) return true
    const asked = await Notifications.requestPermissionsAsync()
    return asked.granted
}

/** 创建/重排到期提醒：openAt 已到期则跳过 */
export async function scheduleCapsuleReminder(capsule: CapsuleItem): Promise<void> {
    if (Platform.OS === 'web') return
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
export async function cancelCapsuleReminder(capsuleId: number): Promise<void> {
    if (Platform.OS === 'web') return
    try {
        await Notifications.cancelScheduledNotificationAsync(`capsule-${capsuleId}`)
    } catch {
        // identifier 不存在时静默（幂等）
    }
}
