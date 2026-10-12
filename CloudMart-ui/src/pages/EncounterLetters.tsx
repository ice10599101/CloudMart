import { useCallback, useEffect, useState } from 'react'
import { Alert, App, Button, Card, Empty, Input, List, Modal, Skeleton, Switch, Tag, Typography } from 'antd'
import { EnvironmentOutlined, ReloadOutlined } from '@ant-design/icons'
import { history } from 'umi'
import {
  getNearbyMode,
  interactEncounterLetter,
  listEncounterLetters,
  markEncounterLetterRead,
  setNearbyMode,
  type EncounterInteractType,
  type EncounterLetter,
} from '@/api/wish'
import listStyles from './CapsuleList.module.css'
import styles from './EncounterLetters.module.css'

const STATUS_META: Record<string, { label: string; color: string; hint: string }> = {
  PENDING: { label: '投递中', color: 'default', hint: '相遇的记忆正在飞往对方' },
  DELIVERED: { label: '待拆封', color: 'processing', hint: '信已送达，点击拆信' },
  READ: { label: '已拆封', color: 'success', hint: '' },
}

function formatTime(time?: string | null): string {
  if (!time) return ''
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * R19 擦肩信笺信箱（与移动端 encounter 页同构）。
 * 信笺匿名化（无对方身份信息）：PENDING 待投递 → DELIVERED 可拆信 → READ 已拆信；
 * 互动 BLESS 匿名祝福（免费）/ LIGHT 点亮对方心愿（扣星光 2），单信笺每日 1 次。
 * 网页端无移动定位能力，不做轨迹上报（trace 仅 App 端调用），信笺列表/拆信/互动可用。
 */
export default function EncounterLetters() {
  const { message } = App.useApp()
  const [letters, setLetters] = useState<EncounterLetter[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [nearbyEnabled, setNearbyEnabled] = useState(false)
  const [nearbyLoading, setNearbyLoading] = useState(false)
  /** 拆信后本地展开的正文（letterId → content） */
  const [opened, setOpened] = useState<Record<number, string>>({})
  /** 本次会话已互动（后端不回传互动标记，与 M/A 端一致按会话记录） */
  const [interacted, setInteracted] = useState<Record<number, boolean>>({})
  const [busyId, setBusyId] = useState<number | null>(null)
  const [blessLetterId, setBlessLetterId] = useState<number | null>(null)
  const [blessContent, setBlessContent] = useState('')

  const loadLetters = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const res = await listEncounterLetters()
      if (res.data.success) {
        setLetters(res.data.data ?? [])
      }
    } catch {
      setLoadError(true)
      // 错误已由 request 拦截器处理
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadLetters()
  }, [loadLetters])

  useEffect(() => {
    getNearbyMode()
      .then((res) => {
        if (res.data.success) setNearbyEnabled(res.data.data === true)
      })
      .catch(() => {
        // 状态回显失败保持关闭，错误已由 request 拦截器处理
      })
  }, [])

  const toggleNearbyMode = async (enabled: boolean) => {
    if (nearbyLoading) return
    setNearbyLoading(true)
    try {
      await setNearbyMode(enabled)
      setNearbyEnabled(enabled)
      message.success(enabled ? '附近模式已开启，相遇正在发生' : '附近模式已关闭')
    } catch {
      // 开关状态不变即自动回滚，错误已由 request 拦截器处理
    } finally {
      setNearbyLoading(false)
    }
  }

  const handleOpen = async (letter: EncounterLetter) => {
    if (busyId !== null || letter.status === 'PENDING') return
    setBusyId(letter.letterId)
    try {
      const res = await markEncounterLetterRead(letter.letterId)
      if (res.data.success) {
        const detail = res.data.data
        if (detail?.content) {
          setOpened((prev) => ({ ...prev, [letter.letterId]: detail.content as string }))
        }
        await loadLetters()
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setBusyId(null)
    }
  }

  const submitInteract = async (letter: EncounterLetter, type: EncounterInteractType, content?: string) => {
    if (busyId !== null) return
    setBusyId(letter.letterId)
    try {
      const res = await interactEncounterLetter(letter.letterId, type, content)
      if (res.data.success) {
        setInteracted((prev) => ({ ...prev, [letter.letterId]: true }))
        setBlessLetterId(null)
        setBlessContent('')
        message.success(type === 'LIGHT' ? '已点亮对方的心愿（-2 星光）' : '匿名祝福已寄出')
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setBusyId(null)
    }
  }

  const pendingCount = letters.filter((letter) => letter.status === 'DELIVERED').length

  return (
    <div className={`${listStyles.container} wish-universe-theme`}>
      <div className={listStyles.pageWrap}>
        <div className={styles.hero}>
          <h1 className={styles.title}>💌 擦肩信笺</h1>
          <Typography.Paragraph type="secondary" className={styles.subtitle}>
            与陌生人的心愿擦肩而过时，会留下一封匿名信笺——没有身份，只有一段相遇和一颗需要被点亮的心愿。
          </Typography.Paragraph>
          <Button onClick={() => history.back()}>返回心愿中心</Button>
        </div>

        <Card className={styles.nearbyCard}>
          <div className={styles.nearbyHead}>
            <div>
              <div className={styles.nearbyTitle}>✨ 附近模式</div>
              <div className={styles.nearbyHint}>开启后匿名上报轨迹，相遇时留下信笺（开关 24 小时有效）</div>
            </div>
            <Switch
              checked={nearbyEnabled}
              loading={nearbyLoading}
              onChange={toggleNearbyMode}
              checkedChildren="开启"
              unCheckedChildren="关闭"
            />
          </div>
          <Alert
            className={styles.traceNote}
            type="info"
            showIcon
            icon={<EnvironmentOutlined />}
            message="网页端不支持轨迹上报"
            description="浏览器无法进行移动定位，不会产生相遇轨迹。请在 App「附近心愿」地图开启附近模式并携带设备出行，收到的信笺会同步展示在这里。"
          />
        </Card>

        <div className={styles.sectionHead}>
          <Typography.Text strong>我的信笺</Typography.Text>
          <div className={styles.sectionActions}>
            {pendingCount > 0 && <Tag color="processing">{pendingCount} 封待拆封</Tag>}
            <Button size="small" icon={<ReloadOutlined />} onClick={loadLetters} loading={loading}>
              刷新
            </Button>
          </div>
        </div>

        {loading ? (
          <Card className={styles.letterCard}>
            <Skeleton active paragraph={{ rows: 2 }} />
          </Card>
        ) : loadError ? (
          <Card className={styles.emptyCard}>
            <Alert
              type="error"
              showIcon
              message="信笺加载失败"
              description="网络开小差了，请稍后重试。"
              action={
                <Button size="small" danger onClick={loadLetters}>
                  重新加载
                </Button>
              }
            />
          </Card>
        ) : letters.length === 0 ? (
          <Card className={styles.emptyCard}>
            <Empty description="还没有相遇的信笺" image={Empty.PRESENTED_IMAGE_SIMPLE}>
              <Typography.Text type="secondary">在 App「附近心愿」地图开启附近模式，带着心愿出门走走吧</Typography.Text>
            </Empty>
          </Card>
        ) : (
          <List
            dataSource={letters}
            renderItem={(letter) => {
              const meta = STATUS_META[letter.status] ?? {
                label: letter.status,
                color: 'default',
                hint: '',
              }
              const body = opened[letter.letterId] ?? letter.content
              const done = interacted[letter.letterId] === true
              return (
                <List.Item className={styles.letterItem}>
                  <Card className={styles.letterCard}>
                    <div className={styles.letterHead}>
                      <Tag color={meta.color}>{meta.label}</Tag>
                      {letter.wishTags?.length > 0 && (
                        <Typography.Text className={styles.letterTags} ellipsis>
                          {letter.wishTags.map((tag) => `#${tag}`).join(' ')}
                        </Typography.Text>
                      )}
                      <Typography.Text type="secondary" className={styles.letterTime}>
                        {formatTime(letter.encounterTime)}
                      </Typography.Text>
                    </div>

                    {letter.status === 'PENDING' && (
                      <Typography.Paragraph type="secondary" className={styles.letterPending}>
                        ✉️ {meta.hint}
                      </Typography.Paragraph>
                    )}

                    {body ? (
                      <Typography.Paragraph className={styles.letterBody}>{body}</Typography.Paragraph>
                    ) : (
                      letter.status === 'DELIVERED' && (
                        <Button
                          type="primary"
                          ghost
                          block
                          loading={busyId === letter.letterId}
                          onClick={() => handleOpen(letter)}
                        >
                          ✂️ 拆信
                        </Button>
                      )
                    )}

                    {(letter.status === 'DELIVERED' || letter.status === 'READ') && (
                      <div className={styles.interactRow}>
                        <Button
                          size="small"
                          disabled={done || busyId === letter.letterId}
                          onClick={() => {
                            setBlessLetterId(letter.letterId)
                            setBlessContent('')
                          }}
                        >
                          {done ? '已回应' : '🕊️ 匿名祝福'}
                        </Button>
                        <Button
                          size="small"
                          disabled={done || busyId === letter.letterId}
                          loading={busyId === letter.letterId}
                          onClick={() => submitInteract(letter, 'LIGHT')}
                        >
                          ✨ 点亮心愿（2 星光）
                        </Button>
                      </div>
                    )}
                  </Card>
                </List.Item>
              )
            }}
          />
        )}
      </div>

      <Modal
        title="寄出匿名祝福"
        open={blessLetterId !== null}
        onCancel={() => setBlessLetterId(null)}
        confirmLoading={busyId !== null}
        okText="寄出"
        cancelText="取消"
        onOk={() => {
          const letter = letters.find((item) => item.letterId === blessLetterId)
          if (letter) submitInteract(letter, 'BLESS', blessContent.trim() || undefined)
        }}
      >
        <Input.TextArea
          value={blessContent}
          onChange={(event) => setBlessContent(event.target.value)}
          maxLength={200}
          showCount
          rows={4}
          placeholder="写点什么（选填，仅对方可见这句祝福）"
        />
      </Modal>
    </div>
  )
}
