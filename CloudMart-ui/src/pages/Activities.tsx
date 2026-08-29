import { useCallback, useEffect, useState } from 'react'
import { App, Button, Empty, Input, InputNumber, Modal, Select, Tag } from 'antd'
import { history } from 'umi'
import {
  ACTIVITY_TYPE_LABELS,
  applyPartner,
  getActivity,
  getActivityProgress,
  getPartnerBoard,
  joinActivity,
  listActivities,
  type ActivityItem,
  type BoardMember,
} from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import WishBGM from '@/components/WishBGM'
import styles from './Activities.module.css'

/** 活动页（Sprint 3.5）：列表/详情/参与/合伙人协作（申请/审批/看板）。 */

export default function Activities() {
  const { message } = App.useApp()
  const { user } = useAuthStore()

  const [activities, setActivities] = useState<ActivityItem[]>([])
  const [loading, setLoading] = useState(true)
  const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined)

  // 详情/合伙人模式
  const [detail, setDetail] = useState<ActivityItem | null>(null)
  const [progress, setProgress] = useState(0)
  const [board, setBoard] = useState<BoardMember[]>([])
  const [boardVisible, setBoardVisible] = useState(false)

  // 申请表单
  const [applyOpen, setApplyOpen] = useState(false)
  const [applyWishId, setApplyWishId] = useState('')
  const [applySkills, setApplySkills] = useState<string>('')
  const [applying, setApplying] = useState(false)

  const loadList = useCallback(async (type?: string) => {
    setLoading(true)
    try {
      const res = await listActivities(type ? { type } : {})
      if (res.data.success) setActivities(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadList(typeFilter)
  }, [typeFilter, loadList])

  const openDetail = useCallback(async (id: number) => {
    try {
      const [detailRes, progressRes] = await Promise.all([getActivity(id), getActivityProgress(id)])
      if (detailRes.data.success) setDetail(detailRes.data.data)
      if (progressRes.data.success) setProgress(progressRes.data.data)
    } catch {
      // 静默
    }
  }, [])

  const handleJoin = async (id: number) => {
    if (!user) {
      history.push('/login?redirect=/wish/activities')
      return
    }
    try {
      await joinActivity(id)
      message.success('参与成功')
      openDetail(id)
    } catch {
      // 拦截器已提示
    }
  }

  const handleViewBoard = async (id: number) => {
    try {
      const res = await getPartnerBoard(id)
      if (res.data.success) {
        setBoard(res.data.data?.members ?? [])
        setBoardVisible(true)
      }
    } catch {
      message.warning('仅组内成员可查看看板')
    }
  }

  const handleApply = async () => {
    const wishId = Number(applyWishId)
    if (!wishId || !detail) return
    const skills = applySkills.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
    setApplying(true)
    try {
      await applyPartner(detail.id, wishId, skills.length ? skills : undefined)
      message.success('申请已提交，等待招募发起人审批')
      setApplyOpen(false)
      setApplyWishId('')
      setApplySkills('')
    } catch {
      // 拦截器已提示
    } finally {
      setApplying(false)
    }
  }

  return (
    <div className={styles.container}>
      <div className={styles.body}>
        <div className={styles.headerBar}>
          <div>
            <h1 className={styles.pageTitle}>🎪 社区活动</h1>
            <p className={styles.pageSubtitle}>世界事件 · 节日活动 · 城市活动 · 心愿合伙人</p>
          </div>
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="全部类型"
            value={typeFilter}
            onChange={(v) => setTypeFilter(v)}
            options={Object.entries(ACTIVITY_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
          />
        </div>

        {loading ? (
          <p className={styles.emptyText}>加载中...</p>
        ) : activities.length === 0 ? (
          <Empty description="暂无进行中的活动" />
        ) : (
          <div className={styles.activityGrid}>
            {activities.map((activity) => (
              <div key={activity.id} className={styles.activityCard} onClick={() => openDetail(activity.id)}>
                <div className={styles.activityTypeTag}>{ACTIVITY_TYPE_LABELS[activity.type]}</div>
                <h3 className={styles.activityTitle}>{activity.title}</h3>
                <p className={styles.activityDesc}>{activity.description}</p>
                <div className={styles.activityProgress}>
                  <div className={styles.progressTrack}>
                    <div className={styles.progressFill} style={{ width: '100%' }} />
                  </div>
                  <span className={styles.progressNum}>{activity.progressCounter} 人参与</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <Modal
        open={!!detail}
        title={detail?.title}
        footer={null}
        onCancel={() => setDetail(null)}
        width={640}
      >
        {detail && (
          <div>
            <p className={styles.modalDesc}>{detail.description}</p>
            <p className={styles.modalProgress}>当前进度：<strong>{progress}</strong> 人参与</p>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {detail.type !== 'WISH_PARTNER' && (
                <Button type="primary" onClick={() => handleJoin(detail.id)}>参与活动</Button>
              )}
              {detail.type === 'WISH_PARTNER' && (
                <>
                  <Button type="primary" onClick={() => setApplyOpen(true)}>申请加入</Button>
                  <Button onClick={() => handleViewBoard(detail.id)}>查看组队看板</Button>
                </>
              )}
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={applyOpen}
        title="申请加入合伙人"
        okText="提交申请"
        cancelText="取消"
        confirmLoading={applying}
        onOk={handleApply}
        onCancel={() => setApplyOpen(false)}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <InputNumber
            style={{ width: '100%' }}
            placeholder="协作心愿 ID（你的公开心愿）"
            value={applyWishId === '' ? undefined : Number(applyWishId)}
            onChange={(v) => setApplyWishId(String(v ?? ''))}
          />
          <Input
            value={applySkills}
            onChange={(e) => setApplySkills(e.target.value)}
            placeholder="技能标签（逗号分隔，如 design,video）"
          />
        </div>
      </Modal>

      {boardVisible && (
        <Modal open title="组队看板" footer={null} onCancel={() => setBoardVisible(false)} width={640}>
          {board.map((member) => (
            <div key={member.userId} className={styles.boardRow}>
              <span className={styles.boardRole}>{member.role === 'LEADER' ? '👑' : '👤'}</span>
              <div style={{ flex: 1 }}>
                <div>心愿：{member.title}</div>
                <div className={styles.boardStats}>
                  进度 {member.progressPercentage}% · 打卡 {member.checkinDays} 天
                </div>
                {member.latestGrowth && <div className={styles.boardGrowth}>📝 {member.latestGrowth}</div>}
              </div>
            </div>
          ))}
        </Modal>
      )}
      <WishBGM />
    </div>
  )
}
