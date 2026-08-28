import { useCallback, useEffect, useState } from 'react'
import { Button, Select, Spin } from 'antd'
import { ArrowLeftOutlined, RobotOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { getAnnualReport, type AnnualReportData } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import WishBGM from '@/components/WishBGM'
import styles from './AnnualReport.module.css'

/** 年度报告最早可选年份（心愿宇宙 2026 年上线） */
const MIN_YEAR = 2026

function buildYearOptions(): Array<{ value: number; label: string }> {
  const currentYear = new Date().getFullYear()
  const years: Array<{ value: number; label: string }> = []
  for (let year = currentYear; year >= MIN_YEAR; year -= 1) {
    years.push({ value: year, label: `${year} 年` })
  }
  return years
}

export default function AnnualReport() {
  const { user } = useAuthStore()
  const [year, setYear] = useState(new Date().getFullYear())
  const [report, setReport] = useState<AnnualReportData | null>(null)
  const [loading, setLoading] = useState(true)

  const loadReport = useCallback(async (targetYear: number) => {
    setLoading(true)
    try {
      const res = await getAnnualReport(targetYear)
      if (res.data.success) setReport(res.data.data)
    } catch {
      // 报告加载失败保持空态
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!user) {
      history.push('/login?redirect=/wish/annual-report')
      return
    }
    loadReport(year)
  }, [user, year, loadReport])

  const maxCategoryCount = report?.topCategories.reduce((max, c) => Math.max(max, c.count), 1) ?? 1
  const hasNoData =
    report && report.fulfilledCount === 0 && report.totalCheckinDays === 0 && report.milestones.length === 0

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.header}>
        <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => history.back()} className={styles.backBtn}>
          返回
        </Button>
        <div className={styles.headerTitle}>
          <span>🌟</span>
          <span>年度报告</span>
        </div>
        <Button type="text" icon={<RobotOutlined />} onClick={() => history.push('/wish/assistant')} className={styles.backBtn}>
          AI 助手
        </Button>
      </div>

      <div className={styles.body}>
        <div className={styles.topBar}>
          <div>
            <h1 className={styles.reportTitle}>我的 {year} 成长之旅</h1>
            <p className={styles.reportSubtitle}>数据仅含你自己的心愿、打卡与成长记录</p>
          </div>
          <Select value={year} options={buildYearOptions()} onChange={setYear} style={{ width: 120 }} />
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 80 }}>
            <Spin size="large" />
          </div>
        ) : !report ? (
          <div className={styles.emptyWrap}>
            <div className={styles.emptyMoon}>🌙</div>
            <p className={styles.emptyTitle}>报告暂时不可读</p>
            <p className={styles.emptyText}>请稍后再来看看</p>
          </div>
        ) : hasNoData ? (
          <div className={styles.emptyWrap}>
            <div className={styles.emptyMoon}>✨</div>
            <p className={styles.emptyTitle}>{year} 年的故事还等着你书写</p>
            <p className={styles.emptyText}>许下心愿、坚持打卡，年底就能收获一份专属报告</p>
            <Button type="primary" onClick={() => history.push('/wish/create')}>
              去许愿
            </Button>
          </div>
        ) : (
          <div className={styles.reportGrid}>
            <div className={styles.statCard}>
              <div className={styles.statPair}>
                <div className={styles.statItem} title={`${report.year} 年实现的心愿总数`}>
                  <div className={styles.statValue}>{report.fulfilledCount}</div>
                  <div className={styles.statLabel}>实现心愿</div>
                </div>
                <div className={styles.statItem} title={`${report.year} 年去重后的打卡总天数`}>
                  <div className={styles.statValue}>{report.totalCheckinDays}</div>
                  <div className={styles.statLabel}>打卡天数</div>
                </div>
              </div>
            </div>

            <div className={styles.sectionCard}>
              <h3 className={styles.sectionTitle}>成长总结</h3>
              <p className={styles.summaryText}>{report.growthSummary}</p>
              <p className={styles.summaryHint}>总结由 AI 生成（首次访问为模板文案，稍后重查自动更新为 AI 版）</p>
            </div>

            {report.topCategories.length > 0 && (
              <div className={styles.sectionCard}>
                <h3 className={styles.sectionTitle}>热门分类 TOP {report.topCategories.length}</h3>
                {report.topCategories.map((category) => (
                  <div
                    key={category.name}
                    className={styles.categoryRow}
                    title={`${category.name}：${category.count} 个心愿`}
                  >
                    <span className={styles.categoryName}>{category.name}</span>
                    <div className={styles.categoryTrack}>
                      <div
                        className={styles.categoryBar}
                        style={{ width: `${Math.round((category.count / maxCategoryCount) * 100)}%` }}
                      />
                    </div>
                    <span className={styles.categoryCount}>{category.count}</span>
                  </div>
                ))}
              </div>
            )}

            {report.milestones.length > 0 && (
              <div className={styles.sectionCard}>
                <h3 className={styles.sectionTitle}>成长里程碑</h3>
                <div className={styles.timelineWrap}>
                  {report.milestones.map((milestone, index) => (
                    <div key={`${milestone.date}-${index}`} className={styles.timelineItem} title={milestone.description}>
                      <div className={styles.timelineDot} />
                      {!(
                        report.milestones.length - 1 === index
                      ) && <div className={styles.timelineLine} />}
                      <div>
                        <div className={styles.timelineDate}>{milestone.date}</div>
                        <div className={styles.timelineTitle}>{milestone.title}</div>
                        {milestone.description && <p className={styles.timelineDesc}>{milestone.description}</p>}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
      <WishBGM />
    </div>
  )
}
