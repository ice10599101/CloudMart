import { useState, useEffect, useCallback } from 'react'
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  Alert,
  ActivityIndicator,
  RefreshControl,
} from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as Clipboard from 'expo-clipboard'
import { userApi } from '@/api/user'
import type { MyInviteInfo } from '@/api/user'
import { wishApi } from '@/api/wish'
import type { ResourceLogItem } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { errorMessage } from '@/utils/request'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

const PAGE_SIZE = 20
const INVITE_REWARD_SOURCE = 'INVITE_REWARD'

export default function InviteCenterScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)

  const [invite, setInvite] = useState<MyInviteInfo | null>(null)
  const [infoLoading, setInfoLoading] = useState(true)
  const [bindCode, setBindCode] = useState('')
  const [binding, setBinding] = useState(false)
  const [bindDone, setBindDone] = useState(false)

  const [logs, setLogs] = useState<ResourceLogItem[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [cursor, setCursor] = useState<string | number | null>(null)

  useEffect(() => {
    if (!isLoggedIn) router.replace('/login')
  }, [isLoggedIn])

  const loadInvite = useCallback(async () => {
    try {
      const res = await userApi.getMyInvites()
      setInvite(res.data.data)
    } catch {
      setInvite(null)
    } finally {
      setInfoLoading(false)
    }
  }, [])

  const loadLogs = useCallback(async (nextCursor?: string | number | null, reset = false) => {
    if (reset) setRefreshing(true)
    else setLoadingMore(true)
    try {
      const res = await wishApi.getMyResourceLogs({
        source: INVITE_REWARD_SOURCE,
        cursor: nextCursor ?? undefined,
        pageSize: PAGE_SIZE,
      })
      const list = res.data.data ?? []
      setLogs((prev) => (reset ? list : [...prev, ...list]))
      setCursor(list.length > 0 ? String(list[list.length - 1].id) : null)
      setHasMore(list.length >= PAGE_SIZE)
    } catch {
      if (reset) setLogs([])
    } finally {
      setLoading(false)
      setRefreshing(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) return
    async function run() {
      await Promise.all([loadInvite(), loadLogs(null, true)])
    }
    void run()
  }, [isLoggedIn, loadInvite, loadLogs])

  const handleRefresh = useCallback(() => {
    void loadInvite()
    void loadLogs(null, true)
  }, [loadInvite, loadLogs])

  const handleCopyCode = useCallback(async () => {
    if (!invite?.code) return
    try {
      await Clipboard.setStringAsync(invite.code)
      Alert.alert('提示', '邀请码已复制，快分享给好友吧')
    } catch (error) {
      Alert.alert('提示', errorMessage(error, '复制失败，请长按邀请码手动选择复制'))
    }
  }, [invite])

  const handleBind = useCallback(async () => {
    if (binding) return
    const code = bindCode.trim()
    if (!code) {
      Alert.alert('提示', '请输入邀请码')
      return
    }
    setBinding(true)
    try {
      await userApi.bindInvite(code)
      setBindDone(true)
      setBindCode('')
      Alert.alert('绑定成功', '邀请关系已建立')
      void loadInvite()
    } catch (error) {
      Alert.alert('绑定失败', errorMessage(error, '请检查邀请码后重试'))
    } finally {
      setBinding(false)
    }
  }, [binding, bindCode, loadInvite])

  const renderHeader = () => (
    <View style={{ padding: Spacing.lg, paddingBottom: 0 }}>
      {/* 邀请码卡片 */}
      <View
        style={{
          backgroundColor: WishColors.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.lg,
          borderWidth: 1,
          borderColor: WishColors.border,
        }}
      >
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>我的邀请码</Text>
        {infoLoading ? (
          <ActivityIndicator color={WishColors.accentCyan} style={{ marginVertical: Spacing.lg }} />
        ) : invite ? (
          <>
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginTop: Spacing.md,
              }}
            >
              <Text style={{ fontSize: FontSize.xxl, fontWeight: '800', color: WishColors.accentGold, letterSpacing: 2 }}>
                {invite.code}
              </Text>
              <TouchableOpacity
                activeOpacity={0.85}
                onPress={handleCopyCode}
                style={{
                  paddingHorizontal: Spacing.lg,
                  paddingVertical: Spacing.sm,
                  borderRadius: BorderRadius.full,
                  borderWidth: 1,
                  borderColor: WishColors.accentCyan,
                }}
              >
                <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan, fontWeight: '600' }}>复制</Text>
              </TouchableOpacity>
            </View>
            <View style={{ flexDirection: 'row', marginTop: Spacing.lg, gap: Spacing.xl }}>
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: WishColors.text }}>
                  {invite.invitedCount}
                </Text>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>已邀请好友</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: WishColors.text }}>
                  +{invite.rewardPerInvite}
                </Text>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>每人奖励星光</Text>
              </View>
            </View>
          </>
        ) : (
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, marginTop: Spacing.md }}>
            邀请信息加载失败，下拉重试
          </Text>
        )}
      </View>

      {/* 绑定邀请人 */}
      {!bindDone && (
        <View
          style={{
            backgroundColor: WishColors.bgContainer,
            borderRadius: BorderRadius.lg,
            padding: Spacing.lg,
            marginTop: Spacing.md,
            borderWidth: 1,
            borderColor: WishColors.border,
          }}
        >
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>
            绑定邀请人
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginBottom: Spacing.sm }}>
            填写好友的邀请码，绑定后双方均可获得星光奖励
          </Text>
          <TextInput
            value={bindCode}
            onChangeText={setBindCode}
            placeholder="请输入邀请码"
            placeholderTextColor={WishColors.textTertiary}
            editable={!binding}
            autoCapitalize="none"
            autoCorrect={false}
            style={{
              padding: Spacing.md,
              borderRadius: BorderRadius.lg,
              backgroundColor: WishColors.bgElevated,
              borderWidth: 1,
              borderColor: WishColors.border,
              color: WishColors.text,
              fontSize: FontSize.md,
            }}
          />
          <TouchableOpacity
            activeOpacity={0.85}
            onPress={handleBind}
            disabled={binding}
            style={{
              marginTop: Spacing.md,
              paddingVertical: Spacing.md,
              borderRadius: BorderRadius.lg,
              backgroundColor: binding ? WishColors.bgElevated : WishColors.accentCyan,
              alignItems: 'center',
              flexDirection: 'row',
              justifyContent: 'center',
              gap: Spacing.sm,
            }}
          >
            {binding ? <ActivityIndicator size="small" color={WishColors.text} /> : null}
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: binding ? WishColors.textTertiary : '#062235' }}>
              {binding ? '绑定中...' : '确认绑定'}
            </Text>
          </TouchableOpacity>
        </View>
      )}

      <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginTop: Spacing.lg, marginBottom: Spacing.sm }}>
        邀请奖励记录
      </Text>
    </View>
  )

  const renderItem = ({ item }: { item: ResourceLogItem }) => (
    <View
      style={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        backgroundColor: WishColors.bgContainer,
        borderRadius: BorderRadius.lg,
        padding: Spacing.md,
        marginBottom: Spacing.sm,
        marginHorizontal: Spacing.lg,
      }}
    >
      <View style={{ flex: 1, marginRight: Spacing.sm }}>
        <Text style={{ fontSize: FontSize.sm, fontWeight: '500', color: WishColors.text }}>{item.reason}</Text>
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>
          {new Date(item.createdAt).toLocaleString('zh-CN')} · 余额 {item.balanceAfter}
        </Text>
      </View>
      <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#52c41a' }}>+{item.amount}</Text>
    </View>
  )

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: Spacing.lg,
          paddingBottom: Spacing.sm,
        }}
      >
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>邀请有礼</Text>
        <View style={{ width: 48 }} />
      </View>

      <FlatList
        data={logs}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderItem}
        ListHeaderComponent={renderHeader}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={WishColors.accentCyan}
          />
        }
        onEndReachedThreshold={0.2}
        onEndReached={() => {
          if (hasMore && !loadingMore && !loading) void loadLogs(cursor, false)
        }}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: 60 }} />
          ) : (
            <Text style={{ textAlign: 'center', marginTop: 60, color: WishColors.textTertiary, fontSize: FontSize.sm }}>
              暂无邀请奖励记录
            </Text>
          )
        }
        ListFooterComponent={
          loadingMore ? <ActivityIndicator color={WishColors.accentCyan} style={{ marginVertical: Spacing.md }} /> : null
        }
        contentContainerStyle={{ paddingBottom: Spacing.xxxl }}
      />
    </View>
  )
}
