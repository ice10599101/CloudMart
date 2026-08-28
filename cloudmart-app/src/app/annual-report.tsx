import { View, Text, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { AnnualReportData } from '@/types'

/** 年度报告最早可选年份（心愿宇宙 2026 年上线） */
const MIN_YEAR = 2026

function buildYearOptions(): number[] {
  const currentYear = new Date().getFullYear()
  const years: number[] = []
  for (let year = currentYear; year >= MIN_YEAR; year -= 1) {
    years.push(year)
  }
  return years
}

export default function AnnualReportScreen() {
  const insets = useSafeAreaInsets()
  const user = useAuthStore((s) => s.user)

  const [year, setYear] = useState(new Date().getFullYear())
  const [report, setReport] = useState<AnnualReportData | null>(null)
  const [loading, setLoading] = useState(true)
  const years = buildYearOptions()

  const loadReport = useCallback(async (targetYear: number) => {
    setLoading(true)
    try {
      const res = await wishApi.getAnnualReport(targetYear)
      if (res.data.success) setReport(res.data.data)
    } catch {
      // 报告加载失败保持空态
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!user) {
      router.replace('/login')
      return
    }
    loadReport(year)
  }, [user, year, loadReport])

  const maxCategoryCount = report?.topCategories.reduce((max, c) => Math.max(max, c.count), 1) ?? 1
  const hasNoData =
    report && report.fulfilledCount === 0 && report.totalCheckinDays === 0 && report.milestones.length === 0

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      {/* 顶栏 */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回">
          <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}>年度报告</Text>
        <TouchableOpacity onPress={() => router.push('/ai-assistant')} accessibilityLabel="AI 助手">
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>AI 助手</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={report && !hasNoData && !loading ? report.milestones : []}
        keyExtractor={(item, index) => `${item.date}-${index}`}
        renderItem={({ item, index }) => (
          <View style={{ flexDirection: 'row', gap: Spacing.md, paddingBottom: Spacing.lg }}>
            <View style={{ alignItems: 'center' }}>
              <View
                style={{
                  width: 12,
                  height: 12,
                  borderRadius: 6,
                  backgroundColor: WishColors.accentGold,
                  marginTop: 4,
                }}
              />
              {report && report.milestones.length - 1 !== index && (
                <View style={{ width: 2, flex: 1, backgroundColor: 'rgba(255,255,255,0.12)', marginTop: 4 }} />
              )}
            </View>
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>{item.date}</Text>
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginTop: 2 }}>
                {item.title}
              </Text>
              {item.description ? (
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginTop: 2 }}>
                  {item.description}
                </Text>
              ) : null}
            </View>
          </View>
        )}
        ListHeaderComponent={
          <View style={{ padding: Spacing.md, paddingBottom: 0 }}>
            {/* 年份切换 + 标题 */}
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: Spacing.md,
              }}
            >
              <View>
                <Text style={{ fontSize: FontSize.xxl, fontWeight: '700', color: WishColors.text }}>
                  我的 {year} 成长之旅
                </Text>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
                  数据仅含你自己的心愿、打卡与成长记录
                </Text>
              </View>
              <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                {years.map((y) => (
                  <TouchableOpacity
                    key={y}
                    accessibilityLabel={`切换到 ${y} 年`}
                    onPress={() => setYear(y)}
                    style={{
                      paddingHorizontal: Spacing.sm,
                      paddingVertical: 4,
                      borderRadius: BorderRadius.sm,
                      backgroundColor: y === year ? WishColors.primary : 'rgba(255,255,255,0.06)',
                    }}
                  >
                    <Text style={{ fontSize: FontSize.xs, color: y === year ? '#fff' : WishColors.textSecondary }}>
                      {y}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {loading ? (
              <ActivityIndicator color={WishColors.primary} style={{ marginVertical: 64 }} />
            ) : !report ? (
              <View style={{ alignItems: 'center', paddingVertical: 64 }}>
                <Text style={{ fontSize: 48 }}>🌙</Text>
                <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text, marginTop: Spacing.md }}>
                  报告暂时不可读
                </Text>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, marginTop: 4 }}>请稍后再来看看</Text>
              </View>
            ) : hasNoData ? (
              <View style={{ alignItems: 'center', paddingVertical: 64 }}>
                <Text style={{ fontSize: 48 }}>✨</Text>
                <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text, marginTop: Spacing.md }}>
                  {year} 年的故事还等着你书写
                </Text>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, marginTop: 4 }}>
                  许下心愿、坚持打卡，年底就能收获一份专属报告
                </Text>
                <TouchableOpacity
                  onPress={() => router.push('/wish-create')}
                  style={{
                    marginTop: Spacing.lg,
                    backgroundColor: WishColors.primary,
                    borderRadius: BorderRadius.full,
                    paddingHorizontal: Spacing.xl,
                    paddingVertical: Spacing.sm,
                  }}
                >
                  <Text style={{ color: '#fff', fontSize: FontSize.sm }}>去许愿</Text>
                </TouchableOpacity>
              </View>
            ) : (
              <>
                {/* 统计卡 */}
                <View
                  style={{
                    flexDirection: 'row',
                    backgroundColor: 'rgba(15, 52, 96, 0.6)',
                    borderWidth: 1,
                    borderColor: 'rgba(233,69,96,0.3)',
                    borderRadius: BorderRadius.xl,
                    padding: Spacing.lg,
                    marginBottom: Spacing.md,
                  }}
                >
                  <View style={{ flex: 1, alignItems: 'center' }}>
                    <Text style={{ fontSize: 32, fontWeight: '700', color: WishColors.accentGold }}>
                      {report.fulfilledCount}
                    </Text>
                    <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, marginTop: 4 }}>实现心愿</Text>
                  </View>
                  <View style={{ flex: 1, alignItems: 'center' }}>
                    <Text style={{ fontSize: 32, fontWeight: '700', color: WishColors.accentGold }}>
                      {report.totalCheckinDays}
                    </Text>
                    <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, marginTop: 4 }}>打卡天数</Text>
                  </View>
                </View>

                {/* 成长总结 */}
                <View
                  style={{
                    backgroundColor: WishColors.bgContainer,
                    borderWidth: 1,
                    borderColor: WishColors.border,
                    borderRadius: BorderRadius.xl,
                    padding: Spacing.md,
                    marginBottom: Spacing.md,
                  }}
                >
                  <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>
                    成长总结
                  </Text>
                  <Text
                    style={{
                      fontSize: FontSize.sm,
                      lineHeight: 22,
                      color: WishColors.text,
                      borderLeftWidth: 3,
                      borderLeftColor: WishColors.primary,
                      paddingLeft: Spacing.sm,
                    }}
                  >
                    {report.growthSummary}
                  </Text>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.sm }}>
                    总结由 AI 生成（首次访问为模板文案，稍后重查自动更新为 AI 版）
                  </Text>
                </View>

                {/* 热门分类 */}
                {report.topCategories.length > 0 && (
                  <View
                    style={{
                      backgroundColor: WishColors.bgContainer,
                      borderWidth: 1,
                      borderColor: WishColors.border,
                      borderRadius: BorderRadius.xl,
                      padding: Spacing.md,
                      marginBottom: Spacing.md,
                    }}
                  >
                    <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>
                      热门分类 TOP {report.topCategories.length}
                    </Text>
                    {report.topCategories.map((category) => (
                      <View
                        key={category.name}
                        style={{
                          flexDirection: 'row',
                          alignItems: 'center',
                          gap: Spacing.sm,
                          marginBottom: Spacing.sm,
                        }}
                      >
                        <Text
                          style={{
                            width: 70,
                            fontSize: FontSize.sm,
                            color: WishColors.textSecondary,
                            textAlign: 'right',
                          }}
                        >
                          {category.name}
                        </Text>
                        <View
                          style={{
                            flex: 1,
                            height: 12,
                            borderRadius: BorderRadius.full,
                            backgroundColor: 'rgba(255,255,255,0.08)',
                            overflow: 'hidden',
                          }}
                        >
                          <View
                            style={{
                              width: `${Math.round((category.count / maxCategoryCount) * 100)}%`,
                              height: '100%',
                              borderRadius: BorderRadius.full,
                              backgroundColor: WishColors.primary,
                            }}
                          />
                        </View>
                        <Text style={{ width: 28, fontSize: FontSize.sm, fontWeight: '600', color: WishColors.accentGold }}>
                          {category.count}
                        </Text>
                      </View>
                    ))}
                  </View>
                )}

                {/* 里程碑标题 */}
                {report.milestones.length > 0 && (
                  <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>
                    成长里程碑
                  </Text>
                )}
              </>
            )}
          </View>
        }
        ListEmptyComponent={
          loading || !report || hasNoData ? null : (
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, textAlign: 'center' }}>
              该年度暂无里程碑记录
            </Text>
          )
        }
        contentContainerStyle={{ paddingBottom: insets.bottom + Spacing.xxl }}
      />
    </View>
  )
}
