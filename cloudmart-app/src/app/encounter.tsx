import { View, Text, ScrollView, TouchableOpacity, TextInput, Modal, ActivityIndicator, Alert } from 'react-native'
import { useEffect, useState, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { encounterApi, type EncounterLetter } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

/**
 * P0-5 相遇信笺信箱（擦肩而过用户端入口，与 Taro 端 encounter 页同构）。
 * 信笺匿名化；PENDING → DELIVERED 可拆信 → READ；互动 BLESS/LIGHT 单信笺每日 1 次。
 */

const STATUS_META: Record<string, { label: string; hint: string }> = {
  PENDING: { label: '投递中', hint: '相遇的记忆正在飞往对方' },
  DELIVERED: { label: '待拆封', hint: '信已送达，点击拆信' },
  READ: { label: '已拆封', hint: '' },
}

export default function EncounterScreen() {
  const theme = useTheme()
  const [letters, setLetters] = useState<EncounterLetter[]>([])
  const [loading, setLoading] = useState(true)
  const [opened, setOpened] = useState<Record<number, string>>({})
  const [interacted, setInteracted] = useState<Record<number, boolean>>({})
  const [blessOpenId, setBlessOpenId] = useState<number | null>(null)
  const [blessContent, setBlessContent] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const loadLetters = useCallback(async () => {
    setLoading(true)
    try {
      const res = await encounterApi.listLetters()
      setLetters((res.data as unknown as { data?: EncounterLetter[] })?.data ?? [])
    } catch {
      setLetters([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadLetters()
  }, [loadLetters])

  const errMessage = (err: unknown) => {
    const e = err as { response?: { data?: { error?: { message?: string } } } }
    return e?.response?.data?.error?.message
  }

  const handleOpen = async (letter: EncounterLetter) => {
    if (busyId !== null) return
    if (letter.status === 'PENDING') return
    setBusyId(letter.letterId)
    try {
      const res = await encounterApi.readLetter(letter.letterId)
      const detail = (res.data as unknown as { data?: EncounterLetter })?.data
      if (detail?.content) {
        setOpened((prev) => ({ ...prev, [letter.letterId]: detail.content as string }))
      }
      loadLetters()
    } catch (err) {
      Alert.alert('提示', errMessage(err) || '拆信失败')
    } finally {
      setBusyId(null)
    }
  }

  const submitInteract = async (letter: EncounterLetter, type: 'BLESS' | 'LIGHT', content?: string) => {
    if (busyId !== null) return
    setBusyId(letter.letterId)
    try {
      await encounterApi.interactLetter(letter.letterId, type, content)
      setInteracted((prev) => ({ ...prev, [letter.letterId]: true }))
      setBlessOpenId(null)
      setBlessContent('')
      Alert.alert('提示', type === 'LIGHT' ? '已点亮对方的心愿（-2 星光）' : '匿名祝福已寄出')
    } catch (err) {
      Alert.alert('提示', errMessage(err) || '操作失败')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: Spacing.lg, paddingTop: Spacing.xxxl, paddingBottom: Spacing.md }}>
        <TouchableOpacity activeOpacity={0.7} onPress={router.back} style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: theme.bgElevated, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ fontSize: 18, color: theme.text }}>←</Text>
        </TouchableOpacity>
        <Text style={{ flex: 1, textAlign: 'center', fontSize: FontSize.xl, fontWeight: '600', color: theme.text, marginRight: 36 }}>相遇信笺</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.lg, paddingBottom: Spacing.xxl }}>
        <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 20, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg, marginBottom: Spacing.lg }}>
          与陌生人的心愿擦肩而过时，会留下一封匿名信笺——没有身份，只有一段相遇和一颗需要被点亮的心愿。
        </Text>

        {loading ? (
          <ActivityIndicator color={theme.primary} style={{ marginTop: Spacing.xxl }} />
        ) : letters.length === 0 ? (
          <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 2 }}>
            <Text style={{ fontSize: 48, marginBottom: Spacing.md, opacity: 0.4 }}>💌</Text>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>还没有相遇的信笺</Text>
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: Spacing.sm, textAlign: 'center', lineHeight: 18 }}>
              在「附近心愿」地图开启附近模式，带着心愿出门走走吧
            </Text>
          </View>
        ) : (
          letters.map((letter) => {
            const meta = STATUS_META[letter.status] ?? { label: letter.status, hint: '' }
            const body = opened[letter.letterId] ?? letter.content
            return (
              <View key={letter.letterId} style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg, marginBottom: Spacing.md }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginBottom: Spacing.sm }}>
                  <Text style={{ fontSize: FontSize.xs, color: theme.text, backgroundColor: theme.accentOrange, paddingHorizontal: Spacing.sm, paddingVertical: 2, borderRadius: BorderRadius.full, fontWeight: '600' }}>
                    {meta.label}
                  </Text>
                  {letter.wishTags?.length > 0 && (
                    <Text numberOfLines={1} style={{ flex: 1, fontSize: FontSize.xs, color: theme.primary }}>
                      {letter.wishTags.map((t) => `#${t}`).join(' ')}
                    </Text>
                  )}
                  <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
                    {new Date(letter.encounterTime).toLocaleDateString('zh-CN')}
                  </Text>
                </View>

                {letter.status === 'PENDING' && <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 20 }}>✉️ {meta.hint}</Text>}

                {body ? (
                  <Text style={{ fontSize: FontSize.md, color: theme.text, lineHeight: 24 }}>{body}</Text>
                ) : letter.status === 'DELIVERED' ? (
                  <TouchableOpacity
                    activeOpacity={0.8}
                    onPress={busyId === letter.letterId ? () => undefined : () => handleOpen(letter)}
                    style={{ alignItems: 'center', height: 40, justifyContent: 'center', backgroundColor: theme.primary, borderRadius: BorderRadius.full }}
                  >
                    <Text style={{ fontSize: FontSize.sm, color: '#FFFFFF', fontWeight: '600' }}>
                      {busyId === letter.letterId ? '拆信中...' : '✂️ 拆信'}
                    </Text>
                  </TouchableOpacity>
                ) : null}

                {(letter.status === 'DELIVERED' || letter.status === 'READ') && (
                  <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.md }}>
                    <TouchableOpacity
                      activeOpacity={0.7}
                      disabled={Boolean(interacted[letter.letterId])}
                      onPress={() => { setBlessOpenId(letter.letterId); setBlessContent('') }}
                      style={{ flex: 1, alignItems: 'center', height: 36, justifyContent: 'center', borderRadius: BorderRadius.full, borderWidth: 1, borderColor: theme.border, opacity: interacted[letter.letterId] ? 0.45 : 1 }}
                    >
                      <Text style={{ fontSize: FontSize.xs, color: theme.text }}>🕊️ 匿名祝福</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      activeOpacity={0.7}
                      disabled={Boolean(interacted[letter.letterId])}
                      onPress={() => submitInteract(letter, 'LIGHT')}
                      style={{ flex: 1, alignItems: 'center', height: 36, justifyContent: 'center', borderRadius: BorderRadius.full, borderWidth: 1, borderColor: theme.border, opacity: interacted[letter.letterId] ? 0.45 : 1 }}
                    >
                      <Text style={{ fontSize: FontSize.xs, color: theme.text }}>✨ 点亮心愿（2 星光）</Text>
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            )
          })
        )}
      </ScrollView>

      <Modal visible={blessOpenId !== null} transparent animationType="fade" onRequestClose={() => setBlessOpenId(null)}>
        <TouchableOpacity activeOpacity={1} style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', alignItems: 'center', justifyContent: 'center' }} onPress={() => setBlessOpenId(null)}>
          <TouchableOpacity activeOpacity={1} style={{ width: '86%', backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.xl }}>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text, marginBottom: Spacing.md }}>寄出匿名祝福</Text>
            <TextInput
              value={blessContent}
              onChangeText={setBlessContent}
              placeholder="写点什么（选填，仅对方可见这句祝福）"
              placeholderTextColor={theme.textTertiary}
              maxLength={200}
              multiline
              style={{ borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, color: theme.text, fontSize: FontSize.sm, minHeight: 80, textAlignVertical: 'top', backgroundColor: theme.bgInput, marginBottom: Spacing.lg }}
            />
            <View style={{ flexDirection: 'row', gap: Spacing.md }}>
              <TouchableOpacity activeOpacity={0.7} onPress={() => setBlessOpenId(null)} style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderRadius: BorderRadius.full, borderWidth: 1, borderColor: theme.border }}>
                <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={() => {
                  const letter = letters.find((l) => l.letterId === blessOpenId)
                  if (letter) submitInteract(letter, 'BLESS', blessContent.trim() || undefined)
                }}
                style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderRadius: BorderRadius.full, backgroundColor: theme.primary }}
              >
                <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>
                  {busyId === blessOpenId ? '寄出中...' : '寄出'}
                </Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  )
}
