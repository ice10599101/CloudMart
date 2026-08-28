import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button, Empty, Input, Modal, Popconfirm, Select, Tag } from 'antd'
import {
  AimOutlined,
  BellOutlined,
  CrownOutlined,
  DislikeOutlined,
  PlusOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import {
  createMatchGroup,
  dissolveMatchGroup,
  joinMatchGroup,
  leaveMatchGroup,
  listMyMatchGroups,
  recommendMatchGroups,
  remindSquadMembers,
  type MatchGroupDetail,
  type MatchGroupItem,
} from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import WishBGM from '@/components/WishBGM'
import styles from './MatchSquad.module.css'

const MAX_KEYWORD = 60

/** 建组仪式星尘轨迹（确定性伪随机，渲染不跳变） */
const CEREMONY_STARS = Array.from({ length: 14 }, (_, i) => {
  const angle = (i / 14) * Math.PI * 2
  const radius = 140 + (i % 4) * 30
  return {
    left: `calc(50% + ${Math.round(Math.cos(angle) * radius)}px)`,
    top: `calc(50% + ${Math.round(Math.sin(angle) * radius * 0.5)}px)`,
    '--tx': `${Math.round(-Math.cos(angle) * 60)}px`,
    '--ty': `${Math.round(-Math.sin(angle) * 30)}px`,
    animationDelay: `${(i % 5) * 0.08}s`,
  } as React.CSSProperties
})

export default function MatchSquad() {
  const { message } = App.useApp()
  const { user } = useAuthStore()

  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [recommend, setRecommend] = useState<MatchGroupItem[]>([])
  const [myGroups, setMyGroups] = useState<MatchGroupDetail[]>([])
  const [myLoading, setMyLoading] = useState(true)

  const [createOpen, setCreateOpen] = useState(false)
  const [createKeyword, setCreateKeyword] = useState('')
  const [createMax, setCreateMax] = useState<number>(4)
  const [creating, setCreating] = useState(false)
  const [ceremony, setCeremony] = useState<string | null>(null)

  /** 加载推荐列表（登录态下服务端自动去重已加入的组 + 同城优先） */
  const loadRecommend = useCallback(async (kw?: string) => {
    setLoading(true)
    try {
      const res = await recommendMatchGroups({
        keyword: kw?.trim() || undefined,
        pageSize: 20,
      })
      if (res.data.success) setRecommend(res.data.data ?? [])
    } catch {
      // 推荐失败保持空态
    } finally {
      setLoading(false)
    }
  }, [])

  const loadMyGroups = useCallback(async () => {
    if (!user) {
      setMyGroups([])
      setMyLoading(false)
      return
    }
    setMyLoading(true)
    try {
      const res = await listMyMatchGroups()
      if (res.data.success) setMyGroups(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setMyLoading(false)
    }
  }, [user])

  useEffect(() => {
    loadRecommend()
    loadMyGroups()
  }, [loadRecommend, loadMyGroups])

  const handleSearch = () => loadRecommend(keyword)

  const handleJoin = async (item: MatchGroupItem) => {
    if (!user) {
      history.push('/login?redirect=/wish/match')
      return
    }
    try {
      await joinMatchGroup(item.groupId)
      message.success('已加入小队，一起加油')
      loadRecommend(keyword)
      loadMyGroups()
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_GROUP_FULL') message.warning('来晚一步，小队刚好满员了')
      else if (code === 'WISH_KICKED_COOLDOWN') message.warning('被移出同主题小队后 24 小时内无法加入')
      else if (code === 'WISH_GROUP_KEYWORD_DUPLICATED') message.warning('你已在同主题的小队中')
    }
  }

  const handleCreate = async () => {
    const kw = createKeyword.trim()
    if (!kw) {
      message.warning('先给小队起个主题吧')
      return
    }
    if (!user) {
      history.push('/login?redirect=/wish/match')
      return
    }
    setCreating(true)
    try {
      const res = await createMatchGroup({ keyword: kw, maxMembers: createMax })
      if (res.data.success) {
        setCreateOpen(false)
        setCeremony(kw)
        window.setTimeout(() => setCeremony(null), 1700)
        setKeyword('')
        loadRecommend()
        loadMyGroups()
      }
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_RATE_LIMITED') message.warning('今天建的小队有点多，明天再来吧')
      else if (code === 'WISH_GROUP_KEYWORD_DUPLICATED') message.warning('你已在同主题的小队中')
    } finally {
      setCreating(false)
    }
  }

  const handleLeave = async (groupId: number) => {
    if (!user) return
    try {
      await leaveMatchGroup(groupId, user.id)
      message.success('已退出小队')
      loadMyGroups()
      loadRecommend(keyword)
    } catch {
      // 拦截器已提示
    }
  }

  const handleKick = async (groupId: number, targetUserId: number) => {
    try {
      await leaveMatchGroup(groupId, targetUserId)
      message.success('已移出该成员')
      loadMyGroups()
    } catch {
      // 拦截器已提示
    }
  }

  const handleDissolve = async (groupId: number) => {
    try {
      await dissolveMatchGroup(groupId)
      message.success('小队已解散')
      loadMyGroups()
    } catch {
      // 拦截器已提示
    }
  }

  /** 提醒：点名未打卡成员，或一键提醒全部 idle 组员（idleDays 为 null 视为从未活跃） */
  const handleRemind = async (groupId: number, idleMembers: number[]) => {
    try {
      if (idleMembers.length === 1) {
        await remindSquadMembers(groupId, idleMembers[0])
      } else {
        await remindSquadMembers(groupId)
      }
      message.success('提醒已送达，等待伙伴回归吧')
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_RATE_LIMITED') message.warning('今天的提醒次数用完了')
      else if (code === 'WISH_VALIDATION_ERROR') message.info('组员们最近都很活跃，暂时不需要提醒')
    }
  }

  const idleMemberIds = useMemo(
    () => (group: MatchGroupDetail) =>
      group.members
        .filter((m) => m.userId !== user?.id && (m.idleDays === null || m.idleDays >= 3))
        .map((m) => m.userId),
    [user],
  )

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.body}>
        <div className={styles.headerBar}>
          <div>
            <h1 className={styles.pageTitle}>
              <TeamOutlined /> 同路人监督小队
            </h1>
            <p className={styles.pageSubtitle}>2-4 人打卡小队 · 互相提醒 · 一起把心愿还掉</p>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            创建小队
          </Button>
        </div>

        <div className={styles.columns}>
          {/* 推荐 */}
          <div>
            <div className={styles.searchBar}>
              <Input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onPressEnter={handleSearch}
                placeholder="想和谁一起？输入关键词，如「看极光」"
                maxLength={MAX_KEYWORD}
                allowClear
              />
              <Button icon={<AimOutlined />} onClick={handleSearch}>
                匹配
              </Button>
            </div>
            {loading ? (
              <div className={styles.emptyText}>正在为你寻找同路人...</div>
            ) : recommend.length === 0 ? (
              <Empty description="暂时没有匹配的小队，换个关键词或自己创建一个吧" />
            ) : (
              recommend.map((item) => (
                <div key={item.groupId} className={styles.groupCard}>
                  <div className={styles.groupTop}>
                    <p className={styles.keyword}>「{item.keyword}」</p>
                    <span className={styles.scorePill} title={item.matchReason}>
                      相似度 {Math.round(item.matchScore * 100)}%
                    </span>
                  </div>
                  <p className={styles.matchReason}>{item.matchReason}</p>
                  <div className={styles.meta}>
                    <span className={styles.memberLine}>
                      {item.leaderNickname} 发起 · {item.memberCount}/{item.maxMembers} 人
                    </span>
                    <Button
                      size="small"
                      type="primary"
                      ghost
                      onClick={() => handleJoin(item)}
                    >
                      加入
                    </Button>
                  </div>
                </div>
              ))
            )}
          </div>

          {/* 我的小队 */}
          <div className={styles.sectionCard}>
            <h3 className={styles.sectionTitle}>我的小队</h3>
            {myLoading ? (
              <p className={styles.emptyText}>加载中...</p>
            ) : myGroups.length === 0 ? (
              <p className={styles.emptyText}>还没有加入任何小队</p>
            ) : (
              myGroups.map((group) => {
                const idleIds = idleMemberIds(group)
                const isLeader = group.viewerRole === 'LEADER'
                return (
                  <div key={group.groupId} className={styles.groupCard}>
                    <div className={styles.groupTop}>
                      <p className={styles.keyword}>「{group.keyword}」</p>
                      <Tag color={group.status === 'OPEN' ? 'green' : group.status === 'FULL' ? 'gold' : 'default'}>
                        {group.status === 'OPEN' ? '招募中' : group.status === 'FULL' ? '已满员' : '已解散'}
                      </Tag>
                    </div>
                    {group.members.map((member) => (
                      <div key={member.userId} className={styles.memberRow}>
                        <span className={styles.memberName}>
                          {member.role === 'LEADER' && <CrownOutlined style={{ color: '#ffd700', marginRight: 4 }} />}
                          {member.nickname}
                          {member.idleDays !== null && member.idleDays >= 3 && (
                            <span className={styles.idleTag}>{member.idleDays} 天未打卡</span>
                          )}
                        </span>
                        <span className={styles.memberActions}>
                          {member.userId !== user?.id && (member.idleDays === null || member.idleDays >= 3) && (
                            <Button
                              size="small"
                              type="text"
                              icon={<BellOutlined />}
                              onClick={() => handleRemind(group.groupId, [member.userId])}
                            >
                              提醒
                            </Button>
                          )}
                          {isLeader && member.role !== 'LEADER' && (
                            <Popconfirm title={`确定移出 ${member.nickname} 吗？`} onConfirm={() => handleKick(group.groupId, member.userId)}>
                              <Button size="small" type="text" danger icon={<DislikeOutlined />}>
                                移出
                              </Button>
                            </Popconfirm>
                          )}
                        </span>
                      </div>
                    ))}
                    <div className={styles.meta}>
                      <span className={styles.memberLine}>
                        {group.memberCount}/{group.maxMembers} 人
                      </span>
                      <span className={styles.memberActions}>
                        {idleIds.length > 0 && (
                          <Button size="small" icon={<BellOutlined />} onClick={() => handleRemind(group.groupId, idleIds)}>
                            一键提醒未打卡
                          </Button>
                        )}
                        {isLeader ? (
                          <Popconfirm title="解散后所有成员都会收到通知，确定吗？" onConfirm={() => handleDissolve(group.groupId)}>
                            <Button size="small" danger>
                              解散
                            </Button>
                          </Popconfirm>
                        ) : (
                          <Popconfirm title="确定退出小队吗？" onConfirm={() => handleLeave(group.groupId)}>
                            <Button size="small">退出</Button>
                          </Popconfirm>
                        )}
                      </span>
                    </div>
                  </div>
                )
              })
            )}
          </div>
        </div>
      </div>

      <Modal
        open={createOpen}
        title="创建同路人小队"
        okText="召唤同路人"
        cancelText="取消"
        confirmLoading={creating}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Input
            value={createKeyword}
            onChange={(e) => setCreateKeyword(e.target.value)}
            maxLength={MAX_KEYWORD}
            placeholder="小队主题，如「坚持晨跑一百天」"
          />
          <Select
            value={createMax}
            onChange={setCreateMax}
            style={{ width: 160 }}
            options={[
              { value: 2, label: '2 人小队' },
              { value: 3, label: '3 人小队' },
              { value: 4, label: '4 人小队' },
            ]}
          />
        </div>
      </Modal>

      {ceremony && (
        <div className={styles.ceremony}>
          {CEREMONY_STARS.map((style, i) => (
            <span key={i} className={styles.ceremonyStar} style={style} />
          ))}
          <h2 className={styles.ceremonyTitle}>「{ceremony}」小队已建立</h2>
        </div>
      )}
      <WishBGM />
    </div>
  )
}
