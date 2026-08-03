import { View, Text, ScrollView, TouchableOpacity, RefreshControl, ActivityIndicator, Image } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { notificationApi } from '@/api/notification'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Conversation, Notification as AppNotification } from '@/types'

function ConversationItem({ item, theme }: { item: Conversation; theme: ReturnType<typeof useTheme> }) {
  return (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => router.push(`/chat?conversationId=${item.id}`)}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        padding: Spacing.lg,
        marginBottom: Spacing.sm,
      }}
    >
      {item.targetUser?.avatar ? (
        <View style={{ width: 48, height: 48, borderRadius: 24, marginRight: Spacing.md, overflow: 'hidden' }}>
          <Image source={{ uri: item.targetUser.avatar }} style={{ width: 48, height: 48 }} />
        </View>
      ) : (
        <View style={{
          width: 48, height: 48, borderRadius: 24,
          backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center',
          marginRight: Spacing.md,
        }}>
          <Text style={{ fontSize: 18, color: theme.primary }}>{item.targetUser?.nickname?.[0] || '?'}</Text>
        </View>
      )}
      <View style={{ flex: 1 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>{item.targetUser?.nickname}</Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{item.lastMessageTime}</Text>
        </View>
        <Text numberOfLines={1} style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 2 }}>
          {item.lastMessage}
        </Text>
      </View>
      {item.unreadCount > 0 && (
        <View style={{
          backgroundColor: theme.accentRed, borderRadius: 10,
          minWidth: 20, height: 20, justifyContent: 'center', alignItems: 'center',
          marginLeft: Spacing.sm, paddingHorizontal: 6,
        }}>
          <Text style={{ color: '#FFFFFF', fontSize: FontSize.xs, fontWeight: '600' }}>{item.unreadCount}</Text>
        </View>
      )}
    </TouchableOpacity>
  )
}

export default function MessagePage() {
  const theme = useTheme()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const [activeTab, setActiveTab] = useState<'chat' | 'notify'>('chat')
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  const loadData = useCallback(async () => {
    if (!isLoggedIn) return
    setLoading(true)
    try {
      const [convRes, notifRes] = await Promise.all([
        notificationApi.getConversations(),
        notificationApi.getList(),
      ])
      setConversations(convRes.data?.data?.list || [])
      setNotifications(notifRes.data?.data?.list || [])
    } catch {
      // ignore
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    loadData()
  }, [isLoggedIn])

  const onRefresh = () => {
    setRefreshing(true)
    loadData()
  }

  if (!isLoggedIn) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <Text style={{ fontSize: 48, marginBottom: Spacing.lg }}>💬</Text>
        <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary, marginBottom: Spacing.xl }}>登录后查看消息</Text>
        <TouchableOpacity
          onPress={() => router.push('/login')}
          style={{ backgroundColor: theme.primary, borderRadius: BorderRadius.lg, paddingHorizontal: Spacing.xxl, paddingVertical: Spacing.md }}
        >
          <Text style={{ color: '#FFFFFF', fontSize: FontSize.lg, fontWeight: '600' }}>去登录</Text>
        </TouchableOpacity>
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Header */}
      <View style={{ paddingHorizontal: Spacing.lg, paddingTop: Spacing.lg, paddingBottom: Spacing.sm }}>
        <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text, marginBottom: Spacing.lg }}>消息</Text>
        <View style={{ flexDirection: 'row', gap: Spacing.md, marginBottom: Spacing.lg }}>
          <TouchableOpacity
            onPress={() => router.push('/ai-chat')}
            style={{ flex: 1, flexDirection: 'row', alignItems: 'center', backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.md, ...theme.shadowCard }}
          >
            <Text style={{ fontSize: 24, marginRight: Spacing.sm }}>🤖</Text>
            <View>
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>AI 助手</Text>
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>智能推荐·答疑解惑</Text>
            </View>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => router.push('/notifications')}
            style={{ flex: 1, flexDirection: 'row', alignItems: 'center', backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.md, ...theme.shadowCard }}
          >
            <Text style={{ fontSize: 24, marginRight: Spacing.sm }}>🔔</Text>
            <View>
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>通知</Text>
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>互动·系统消息</Text>
            </View>
          </TouchableOpacity>
        </View>
        <View style={{ flexDirection: 'row', backgroundColor: theme.bgInput, borderRadius: BorderRadius.xl, padding: 3 }}>
          <TouchableOpacity
            onPress={() => setActiveTab('chat')}
            style={{
              flex: 1, paddingVertical: Spacing.sm, borderRadius: BorderRadius.xl,
              backgroundColor: activeTab === 'chat' ? theme.primary : 'transparent',
              alignItems: 'center',
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: activeTab === 'chat' ? '#FFFFFF' : theme.textSecondary }}>
              聊天
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => setActiveTab('notify')}
            style={{
              flex: 1, paddingVertical: Spacing.sm, borderRadius: BorderRadius.xl,
              backgroundColor: activeTab === 'notify' ? theme.primary : 'transparent',
              alignItems: 'center',
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: activeTab === 'notify' ? '#FFFFFF' : theme.textSecondary }}>
              通知
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Content */}
      <ScrollView
        contentContainerStyle={{ padding: Spacing.lg }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
      >
        {loading ? (
          <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xxl }} />
        ) : activeTab === 'chat' ? (
          conversations.length > 0 ? (
            conversations.map((conv) => <ConversationItem key={conv.id} item={conv} theme={theme} />)
          ) : (
            <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 2 }}>
              <Text style={{ fontSize: 48, marginBottom: Spacing.lg }}>💬</Text>
              <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无聊天</Text>
            </View>
          )
        ) : notifications.length > 0 ? (
          notifications.map((notif) => (
            <TouchableOpacity
              key={notif.id}
              activeOpacity={0.7}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                backgroundColor: notif.isRead ? theme.bgContainer : theme.bgElevated,
                borderRadius: BorderRadius.lg,
                padding: Spacing.lg,
                marginBottom: Spacing.sm,
                borderLeftWidth: notif.isRead ? 0 : 3,
                borderLeftColor: theme.primary,
              }}
            >
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>{notif.title}</Text>
                <Text numberOfLines={2} style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 2 }}>
                  {notif.content}
                </Text>
                <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 4 }}>{notif.createdAt}</Text>
              </View>
            </TouchableOpacity>
          ))
        ) : (
          <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 2 }}>
            <Text style={{ fontSize: 48, marginBottom: Spacing.lg }}>🔔</Text>
            <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无通知</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
