import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Empty, Input, Modal, Select, Spin, Tag } from 'antd'
import {
  listGroupActivities,
  openGroup,
  joinGroup,
  getGroupOrders,
  getMyGroups,
  getGroupOrder,
} from '@/api/marketing'
import type { GroupActivity, GroupOrder } from '@/api/marketing'
import { listAddresses } from '@/api/user'
import { getUserProfile } from '@/api/user'
import type { ApiResponse } from '@/types/api'

const GROUP_STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '拼团中', color: '#FFA502' },
  SUCCESS: { label: '拼团成功', color: '#2ED573' },
  FAILED: { label: '已成团失败', color: '#FF4757' },
  EXPIRED: { label: '已过期', color: '#8d8d8d' },
}

/** 服务器时间校准：以响应 Date 头为基准计算偏移，倒计时不用本地时钟直判 */
function serverOffsetFrom(res: unknown): number {
  const dateHeader = (res as { headers?: { date?: string } })?.headers?.date
  if (!dateHeader) return 0
  const serverNow = Date.parse(dateHeader)
  return Number.isNaN(serverNow) ? 0 : serverNow - Date.now()
}

export default function GroupBuyPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [activities, setActivities] = useState<GroupActivity[]>([])
  const [myGroups, setMyGroups] = useState<GroupOrder[]>([])
  const [openGroups, setOpenGroups] = useState<GroupOrder[]>([])
  const [openGroupsActivity, setOpenGroupsActivity] = useState<GroupActivity | null>(null)
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'activities' | 'orders'>('activities')
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null)
  const serverOffsetRef = useRef(0)
  const [nowTick, setNowTick] = useState(Date.now())
  // WEB-02：开团/加入此团两动作分离——加入必须携带所选 groupId 与收货地址
  const [joinTarget, setJoinTarget] = useState<{ activityId: number; groupOrderId?: number } | null>(null)
  const [addresses, setAddresses] = useState<Array<Record<string, any>>>([])
  const [selectedAddressId, setSelectedAddressId] = useState<number | string | null>(null)
  const [joinSubmitting, setJoinSubmitting] = useState(false)
  // WEB-02：groupId 深链详情（不提供从 URL 切到无关团的入口）
  const [deepLinkGroup, setDeepLinkGroup] = useState<GroupOrder | null>(null)
  const myUserIdRef = useRef<number | null>(null)

  useEffect(() => {
    const timer = setInterval(() => setNowTick(Date.now()), 30_000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    if (toast) {
      const t = setTimeout(() => setToast(null), 3000)
      return () => clearTimeout(t)
    }
  }, [toast])

  const notify = (msg: string, type: 'success' | 'error') => setToast({ message: msg, type })

  /** 打开地址选择并提交参团：无有效地址时明确阻断，不静默形成无地址团 */
  const openJoinFlow = useCallback(async (activityId: number, groupOrderId?: number) => {
    setJoinTarget({ activityId, groupOrderId })
    try {
      const { data: res } = await listAddresses()
      const response = res as ApiResponse<Array<Record<string, any>>>
      const list = response.data ?? []
      setAddresses(list)
      const def = list.find((a) => a.isDefault) ?? list[0]
      setSelectedAddressId(def ? def.id : null)
    } catch {
      setAddresses([])
      setSelectedAddressId(null)
    }
  }, [])

  const doJoin = async () => {
    if (!joinTarget) return
    // WEB-02：缺有效地址不能静默形成无地址订单（后端冻结参团地址快照）
    if (!selectedAddressId) {
      notify('请先添加收货地址再参与拼团', 'error')
      return
    }
    setJoinSubmitting(true)
    try {
      const payload: Record<string, unknown> = {
        activityId: joinTarget.activityId,
        addressId: selectedAddressId,
      }
      // 加入此团必须带所选 groupId；开团不带
      if (joinTarget.groupOrderId != null) payload.groupOrderId = joinTarget.groupOrderId
      await joinGroup(payload as { activityId: number })
      notify(joinTarget.groupOrderId != null ? '已加入该团' : '开团成功', 'success')
      setJoinTarget(null)
      void refresh()
    } catch {
      notify('参与失败（可能已参加过/团已满/已过期）', 'error')
    } finally {
      setJoinSubmitting(false)
    }
  }

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const actRes = await listGroupActivities(1, 20)
      serverOffsetRef.current = serverOffsetFrom(actRes)
      setActivities(actRes.data?.data?.records ?? [])
      const myRes = await getMyGroups(1, 20)
      serverOffsetRef.current = serverOffsetFrom(myRes)
      setMyGroups(myRes.data?.data?.records ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  // WEB-02：groupId 深链——只展示该团，不从 URL 切换到无关团
  useEffect(() => {
    void refresh()
    void (async () => {
      try {
        const { data: profile } = await getUserProfile()
        myUserIdRef.current = (profile as ApiResponse<{ id: number }>).data?.id ?? null
      } catch {
        myUserIdRef.current = null
      }
    })()
    const groupId = searchParams.get('groupId')
    if (groupId) {
      void (async () => {
        try {
          const { data: res } = await getGroupOrder(Number(groupId))
          setDeepLinkGroup((res as ApiResponse<GroupOrder>).data ?? null)
        } catch {
          notify('拼团组不存在或已失效', 'error')
        }
      })()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const expandOpenGroups = async (activity: GroupActivity) => {
    setOpenGroupsActivity(openGroupsActivity?.id === activity.id ? null : activity)
    setOpenGroups([])
    try {
      const { data: res } = await getGroupOrders(activity.id, 1, 10)
      serverOffsetRef.current = serverOffsetFrom(res)
      setOpenGroups(res.data?.records ?? [])
    } catch {
      setOpenGroups([])
    }
  }

  const remaining = (expireTime: string | null | undefined) => {
    if (!expireTime) return null
    const diff = Math.max(0, Math.floor((new Date(expireTime).getTime() - (nowTick + serverOffsetRef.current)) / 1000))
    if (diff <= 0) return '已截止'
    const h = Math.floor(diff / 3600)
    const m = Math.floor((diff % 3600) / 60)
    return h > 0 ? `剩余 ${h}时${m}分` : `剩余 ${m}分钟`
  }

  /** 本人成员视图（成团建单结果/补偿状态） */
  const myMemberOf = (order: GroupOrder) =>
    order.members?.find((m) => myUserIdRef.current != null && m.userId === myUserIdRef.current) ?? null

  const groupOrderCard = (order: GroupOrder) => {
    const meta = GROUP_STATUS_META[order.status] ?? { label: order.status, color: '#8d8d8d' }
    const percent = order.targetNumber > 0 ? Math.round((order.currentNumber / order.targetNumber) * 100) : 0
    const mine = myMemberOf(order)
    // WEB-02：成团失败/过期仅释放预留权益——不展示不存在的"退款中"
    const failureNote =
      order.status === 'FAILED' || order.status === 'EXPIRED' ? '（预留权益已释放，未产生订单与扣款）' : ''
    // 补偿状态：成团成功但本人建单失败/缺地址——如实展示，不伪造全部建单成功
    const compensation =
      order.status === 'SUCCESS' && mine && (mine.orderTaskStatus === 'FAILED' || mine.orderTaskStatus === 'ABSENT_ADDRESS')
        ? mine.orderTaskStatus === 'ABSENT_ADDRESS'
          ? '参团时未登记收货地址，建单未完成——请联系客服补建订单'
          : `建单失败${mine.orderTaskError ? `：${mine.orderTaskError}` : ''}——已进入补偿处理`
        : null
    return (
      <div
        key={order.id}
        style={{
          background: 'var(--color-bg-container)',
          border: '1px solid var(--color-border)',
          borderRadius: 10,
          padding: 16,
          marginBottom: 12,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, color: 'var(--color-text-tertiary)', marginBottom: 4 }}>
              团 ID {order.id} · 活动 #{order.activityId}
            </div>
            <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 6 }}>
              {order.currentNumber}/{order.targetNumber} 人成团
              {remaining(order.expireTime) ? (
                <span style={{ marginLeft: 10, fontSize: 12, color: '#FFA502' }}>{remaining(order.expireTime)}</span>
              ) : null}
            </div>
            <div style={{ height: 4, background: 'var(--color-border)', borderRadius: 2, maxWidth: 220, overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${percent}%`, background: 'var(--color-primary)', borderRadius: 2 }} />
            </div>
            {compensation && (
              <div style={{ marginTop: 8, fontSize: 13, color: '#d46b08' }}>⚠ {compensation}</div>
            )}
            {failureNote && (
              <div style={{ marginTop: 8, fontSize: 12, color: 'var(--color-text-tertiary)' }}>{failureNote}</div>
            )}
          </div>
          <div style={{ textAlign: 'right' }}>
            <Tag color={meta.color}>{meta.label}</Tag>
            {/* 成团后：本人建单成功 → 订单入口（待付款金额与支付期限在订单详情/支付页） */}
            {order.status === 'SUCCESS' && mine?.orderId != null && (
              <Button
                type="primary"
                size="small"
                style={{ marginTop: 8 }}
                onClick={() => navigate(`/orders/${mine.orderId}`)}
              >
                去支付订单
              </Button>
            )}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)' }}>
      {toast && (
        <div
          style={{
            position: 'fixed',
            top: 24,
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 9999,
            padding: '12px 32px',
            borderRadius: 10,
            background: toast.type === 'success' ? 'rgba(var(--color-primary-rgb), 0.15)' : 'rgba(255,71,87,0.15)',
            color: toast.type === 'success' ? 'var(--color-primary)' : '#FF4757',
            fontSize: 14,
            fontWeight: 500,
          }}
        >
          {toast.message}
        </div>
      )}

      <div
        style={{
          background: 'var(--color-gradient-hero)',
          padding: '56px 24px 40px',
          textAlign: 'center',
        }}
      >
        <h1 style={{ fontSize: 38, fontWeight: 900, color: 'var(--color-text-secondary)', margin: 0 }}>
          拼团<span style={{ color: 'var(--color-primary)' }}>专区</span>
        </h1>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: 15, marginTop: 6 }}>一起拼，更优惠</div>
      </div>

      <div style={{ maxWidth: 1280, margin: '0 auto', padding: '0 24px 80px' }}>
        <div style={{ display: 'flex', gap: 0, borderBottom: '1px solid var(--color-border)', marginBottom: 24 }}>
          {(
            [
              { key: 'activities' as const, label: '拼团活动' },
              { key: 'orders' as const, label: '我的拼团' },
            ] as const
          ).map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              style={{
                padding: '14px 28px',
                border: 'none',
                background: 'transparent',
                color: activeTab === tab.key ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                fontSize: 15,
                fontWeight: activeTab === tab.key ? 700 : 400,
                cursor: 'pointer',
                borderBottom: activeTab === tab.key ? '2px solid var(--color-primary)' : '2px solid transparent',
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: 80 }}>
            <Spin />
          </div>
        ) : activeTab === 'activities' ? (
          activities.length === 0 ? (
            <Empty description="暂无拼团活动" style={{ padding: '64px 0' }} />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {activities.map((activity) => {
                const expired = activity.endTime
                  ? new Date(activity.endTime).getTime() <= nowTick + serverOffsetRef.current
                  : false
                const isActive = activity.status === 'ENABLED' && !expired
                const showList = openGroupsActivity?.id === activity.id
                return (
                  <div
                    key={activity.id}
                    style={{
                      background: 'var(--color-bg-container)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 12,
                      padding: 18,
                    }}
                  >
                    {/* WEB-02：活动卡只显示规则/团价/截止——不拿活动团数冒充人数 */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                      <div style={{ flex: 1, minWidth: 240 }}>
                        <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>{activity.name}</div>
                        <div style={{ fontSize: 13, color: 'var(--color-text-tertiary)', marginBottom: 8 }}>
                          {activity.description}
                        </div>
                        <div style={{ display: 'flex', gap: 14, fontSize: 13, color: 'var(--color-text-secondary)' }}>
                          <span>
                            <span style={{ fontSize: 20, fontWeight: 800, color: 'var(--color-primary)' }}>
                              ¥{activity.groupPrice?.toFixed(2)}
                            </span>
                            <span style={{ marginLeft: 8, textDecoration: 'line-through', color: 'var(--color-text-tertiary)' }}>
                              ¥{activity.originalPrice?.toFixed(2)}
                            </span>
                          </span>
                          <span>{activity.targetNumber} 人成团</span>
                          {remaining(activity.endTime) && <span style={{ color: '#FFA502' }}>{remaining(activity.endTime)}</span>}
                        </div>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, justifyContent: 'center' }}>
                        {/* WEB-02：开团 / 加入此团 两动作分离 */}
                        <Button
                          type="primary"
                          disabled={!isActive}
                          onClick={() => void openJoinFlow(activity.id)}
                        >
                          {isActive ? '我要开团' : '已结束'}
                        </Button>
                        <Button disabled={!isActive} onClick={() => void expandOpenGroups(activity)}>
                          {showList ? '收起开放团' : '查看开放团'}
                        </Button>
                      </div>
                    </div>

                    {/* WEB-02：开放团列表（groupId/人数/截止），加入必须带所选 groupId */}
                    {showList && (
                      <div style={{ marginTop: 14, borderTop: '1px dashed var(--color-border)', paddingTop: 12 }}>
                        {openGroups.length === 0 ? (
                          <div style={{ fontSize: 13, color: 'var(--color-text-tertiary)' }}>
                            暂无等待中的团，成为第一个团长吧
                          </div>
                        ) : (
                          openGroups.map((g) => (
                            <div
                              key={g.id}
                              style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                padding: '8px 0',
                                gap: 12,
                              }}
                            >
                              <span style={{ fontSize: 13 }}>
                                团 {g.id} · {g.currentNumber}/{g.targetNumber} 人
                                {remaining(g.expireTime) ? (
                                  <span style={{ marginLeft: 8, color: '#FFA502' }}>{remaining(g.expireTime)}</span>
                                ) : null}
                              </span>
                              <Button size="small" disabled={!isActive} onClick={() => void openJoinFlow(activity.id, g.id)}>
                                加入此团
                              </Button>
                            </div>
                          ))
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )
        ) : myGroups.length === 0 ? (
          <Empty description="暂未参与任何拼团" style={{ padding: '64px 0' }} />
        ) : (
          <div>{myGroups.map(groupOrderCard)}</div>
        )}
      </div>

      {/* 参团地址选择（后端冻结地址快照；无地址明确阻断） */}
      <Modal
        title={joinTarget?.groupOrderId != null ? '加入此团' : '我要开团'}
        open={joinTarget !== null}
        onOk={doJoin}
        confirmLoading={joinSubmitting}
        onCancel={() => setJoinTarget(null)}
        okText="确认参与"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {addresses.length === 0 ? (
            <div style={{ color: '#d46b08', fontSize: 13 }}>
              尚无收货地址。请先在「个人中心 → 收货地址」新增地址后再参与拼团（成团建单将使用参团时的地址快照）。
            </div>
          ) : (
            <>
              <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>选择收货地址（成团建单按此快照）：</div>
              <Select
                style={{ width: '100%' }}
                value={selectedAddressId ?? undefined}
                onChange={(v) => setSelectedAddressId(v)}
                options={addresses.map((a) => ({
                  value: a.id,
                  label: `${a.receiverName ?? a.name ?? ''} ${a.phone ?? ''} ${a.address ?? ''}${
                    a.isDefault ? '（默认）' : ''
                  }`,
                }))}
              />
            </>
          )}
        </div>
      </Modal>

      {/* groupId 深链详情（只展示该团，不可切团） */}
      <Modal
        title={`拼团组 ${deepLinkGroup?.id ?? ''}`}
        open={deepLinkGroup !== null}
        onCancel={() => setDeepLinkGroup(null)}
        footer={[
          <Button key="close" onClick={() => setDeepLinkGroup(null)}>
            关闭
          </Button>,
          ...(deepLinkGroup && deepLinkGroup.status === 'PENDING'
            ? [
                <Button
                  key="join"
                  type="primary"
                  onClick={() => {
                    const activityId = deepLinkGroup.activityId
                    setDeepLinkGroup(null)
                    void openJoinFlow(activityId, deepLinkGroup.id)
                  }}
                >
                  加入此团
                </Button>,
              ]
            : []),
        ]}
      >
        {deepLinkGroup && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 14 }}>
            <div>
              进度：{deepLinkGroup.currentNumber}/{deepLinkGroup.targetNumber} 人
              <Tag color={GROUP_STATUS_META[deepLinkGroup.status]?.color} style={{ marginLeft: 10 }}>
                {GROUP_STATUS_META[deepLinkGroup.status]?.label ?? deepLinkGroup.status}
              </Tag>
            </div>
            <div style={{ color: 'var(--color-text-tertiary)', fontSize: 13 }}>
              截止：{deepLinkGroup.expireTime?.slice(0, 19).replace('T', ' ')}
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
