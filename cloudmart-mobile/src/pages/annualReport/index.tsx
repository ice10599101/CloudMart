import { useCallback, useEffect, useState } from 'react'
import { Picker, Text, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { AnnualReportData } from '@/types'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

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

export default function AnnualReportPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()

  const [year, setYear] = useState(new Date().getFullYear())
  const [report, setReport] = useState<AnnualReportData | null>(null)
  const [loading, setLoading] = useState(true)

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
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    loadReport(year)
  }, [isLoggedIn, year, loadReport])

  const maxCategoryCount = report?.topCategories.reduce((max, c) => Math.max(max, c.count), 1) ?? 1
  const hasNoData =
    report && report.fulfilledCount === 0 && report.totalCheckinDays === 0 && report.milestones.length === 0
  const years = buildYearOptions()

  return (
    <View className={styles.container}>
      <CustomNavBar title="年度报告" back />
      <View style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
        <View className={styles.body}>
          <View className={styles.topBar}>
            <View>
              <Text className={styles.reportTitle}>我的 {year} 成长之旅</Text>
              <View className={styles.reportSubtitle}>
                <Text>数据仅含你自己的心愿、打卡与成长记录</Text>
              </View>
            </View>
            <Picker
              mode="selector"
              range={years.map((y) => `${y} 年`)}
              value={years.indexOf(year)}
              onChange={(e) => setYear(years[Number(e.detail.value)])}
            >
              <View className={styles.yearPicker}>
                <Text className={styles.yearPickerText}>{year} 年 ▾</Text>
              </View>
            </Picker>
          </View>

          {loading ? (
            <View className={styles.emptyWrap}>
              <Text className={styles.emptyText}>报告生成中...</Text>
            </View>
          ) : !report ? (
            <View className={styles.emptyWrap}>
              <Text className={styles.emptyMoon}>🌙</Text>
              <View className={styles.emptyTitle}>
                <Text>报告暂时不可读</Text>
              </View>
              <View className={styles.emptyText}>
                <Text>请稍后再来看看</Text>
              </View>
            </View>
          ) : hasNoData ? (
            <View className={styles.emptyWrap}>
              <Text className={styles.emptyMoon}>✨</Text>
              <View className={styles.emptyTitle}>
                <Text>{year} 年的故事还等着你书写</Text>
              </View>
              <View className={styles.emptyText}>
                <Text>许下心愿、坚持打卡，年底就能收获一份专属报告</Text>
              </View>
              <View className={styles.ctaBtn} onClick={() => Taro.navigateTo({ url: '/pages/wishCreate/index' })}>
                <Text className={styles.ctaBtnText}>去许愿</Text>
              </View>
            </View>
          ) : (
            <>
              <View className={styles.statCard}>
                <View className={styles.statItem}>
                  <View className={styles.statValue}>
                    <Text>{report.fulfilledCount}</Text>
                  </View>
                  <View className={styles.statLabel}>
                    <Text>实现心愿</Text>
                  </View>
                </View>
                <View className={styles.statItem}>
                  <View className={styles.statValue}>
                    <Text>{report.totalCheckinDays}</Text>
                  </View>
                  <View className={styles.statLabel}>
                    <Text>打卡天数</Text>
                  </View>
                </View>
              </View>

              <View className={styles.card}>
                <Text className={styles.cardTitle}>成长总结</Text>
                <View className={styles.summaryText}>
                  <Text>{report.growthSummary}</Text>
                </View>
                <View className={styles.summaryHint}>
                  <Text>总结由 AI 生成（首次访问为模板文案，稍后重查自动更新为 AI 版）</Text>
                </View>
              </View>

              {report.topCategories.length > 0 && (
                <View className={styles.card}>
                  <Text className={styles.cardTitle}>热门分类 TOP {report.topCategories.length}</Text>
                  {report.topCategories.map((category) => (
                    <View key={category.name} className={styles.categoryRow}>
                      <Text className={styles.categoryName}>{category.name}</Text>
                      <View className={styles.categoryTrack}>
                        <View
                          className={styles.categoryBar}
                          style={{ width: `${Math.round((category.count / maxCategoryCount) * 100)}%` }}
                        />
                      </View>
                      <Text className={styles.categoryCount}>{category.count}</Text>
                    </View>
                  ))}
                </View>
              )}

              {report.milestones.length > 0 && (
                <View className={styles.card}>
                  <Text className={styles.cardTitle}>成长里程碑</Text>
                  {report.milestones.map((milestone, index) => (
                    <View key={`${milestone.date}-${index}`} className={styles.timelineItem}>
                      <View className={styles.timelineDot} />
                      {report.milestones.length - 1 !== index && <View className={styles.timelineLine} />}
                      <View>
                        <Text className={styles.timelineDate}>{milestone.date}</Text>
                        <View className={styles.timelineTitle}>
                          <Text>{milestone.title}</Text>
                        </View>
                        {milestone.description && (
                          <View className={styles.timelineDesc}>
                            <Text>{milestone.description}</Text>
                          </View>
                        )}
                      </View>
                    </View>
                  ))}
                </View>
              )}
            </>
          )}
        </View>
      </View>
      <WishBGM />
    </View>
  )
}
