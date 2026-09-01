import { useEffect, useMemo, useState } from 'react'
import { Button, Card, App, Tag, Image, Typography } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { history, useParams } from 'umi'
import { getCapsuleDetail, openCapsule, type CapsuleItem } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import { reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import styles from './CapsuleDetail.module.css'
import WishBGM from '@/components/WishBGM'

/** 拆信动效节奏：封蜡碎裂(0.5s) → 信封翻盖(0.6s) → 信纸升起(0.7s)，与移动/APP 端一致 */
const OPEN_ANIM_MS = 1800

/** 信封拆信动效（纯 CSS，三阶段 class 驱动） */
function EnvelopeAnim({ opening }: { opening: boolean }) {
    return (
        <div className={`${styles.envelope} ${opening ? styles.envelopeOpening : ''}`}>
            <div className={styles.envelopeBody}>
                <div className={styles.envelopeFlap} />
                <div className={styles.envelopeLetter}>
                    <span className={styles.letterEmoji}>💌</span>
                </div>
                <span className={styles.envelopeWax}>🔴</span>
                {opening &&
                    Array.from({ length: 14 }).map((_, i) => (
                        <span
                            key={i}
                            className={styles.waxShard}
                            style={{ ['--angle' as string]: `${i * 25.7}deg`, ['--delay' as string]: `${(i % 5) * 0.05}s` }}
                        />
                    ))}
            </div>
        </div>
    )
}

function BackBar() {
    return (
        <div className={styles.backBar}>
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => history.push('/wish/capsules')} className={styles.backBtn}>
                返回
            </Button>
        </div>
    )
}

export default function CapsuleDetail() {
    const params = useParams<{ id: string }>()
    const capsuleId = params.id
    const [loading, setLoading] = useState(true)
    const [capsule, setCapsule] = useState<CapsuleItem | null>(null)
    const [opening, setOpening] = useState(false)
    const [revealed, setRevealed] = useState(false)
    const [now, setNow] = useState(Date.now())
    const { message } = App.useApp()
    const { user } = useAuthStore()

    useEffect(() => {
        reportTimezoneIfNeeded()
        if (!user) {
            message.warning('请先登录后查看胶囊')
            history.push(`/login?redirect=/wish/capsules/${capsuleId}`)
            return
        }
        getCapsuleDetail(capsuleId)
            .then((res) => {
                if (res.data.success) {
                    setCapsule(res.data.data)
                    if (res.data.data.status === 'OPENED') {
                        setRevealed(true)
                    }
                }
            })
            .catch(() => {
                // 错误已由 request 拦截器处理（404 = 不存在或非本人）
            })
            .finally(() => setLoading(false))
    }, [user, capsuleId])

    // 封印中倒计时：每分钟刷新（到期瞬间亮出拆开按钮，容忍扫描间隙）
    useEffect(() => {
        if (!capsule || capsule.status !== 'SEALED' || revealed) return
        const timer = window.setInterval(() => setNow(Date.now()), 30_000)
        return () => window.clearInterval(timer)
    }, [capsule, revealed])

    const expired = useMemo(
        () => (capsule ? new Date(capsule.openAt).getTime() <= now : false),
        [capsule, now]
    )

    const canOpen = capsule !== null && capsule.status !== 'OPENED' && capsule.status !== 'CANCELLED' && expired

    const handleOpen = async () => {
        if (opening) return
        setOpening(true)
        try {
            const res = await openCapsule(capsuleId)
            if (res.data.success) {
                // 拆信动效播完再揭晓内容（未到期 409/已取消会被 catch，动效不触发）
                window.setTimeout(() => {
                    setCapsule(res.data.data)
                    setRevealed(true)
                    setOpening(false)
                }, OPEN_ANIM_MS)
            } else {
                setOpening(false)
            }
        } catch {
            setOpening(false)
        }
    }

    const formatLocal = (iso: string | null) => (iso ? new Date(iso).toLocaleString() : '-')

    if (loading) {
        return (
            <div className={`${styles.container} wish-universe-theme`}>
                <Card loading className={styles.detailCard} />
            </div>
        )
    }

    if (!capsule) {
        return (
            <div className={`${styles.container} wish-universe-theme`}>
                <Card className={styles.detailCard}>
                    <p className={styles.invalidText}>胶囊不存在</p>
                    <Button onClick={() => history.push('/wish/capsules')}>返回我的胶囊</Button>
                </Card>
            </div>
        )
    }

    // 已取消：终态展示
    if (capsule.status === 'CANCELLED') {
        return (
            <div className={`${styles.container} wish-universe-theme`}>
                <div className={styles.pageWrap}>
                    <BackBar />
                    <Card className={styles.detailCard}>
                        <div className={styles.cancelledView}>
                            <span className={styles.cancelledEmoji}>🌑</span>
                            <h2 className={styles.ceremonyTitle}>{capsule.title}</h2>
                            <p className={styles.ceremonyText}>该胶囊已被取消，封存内容永久不可开启</p>
                            <Button type="primary" onClick={() => history.push('/wish/capsules')}>
                                返回我的胶囊
                            </Button>
                        </div>
                    </Card>
                </div>
                <WishBGM />
            </div>
        )
    }

    // 未开启：封印视图（倒计时 / 待拆封）
    if (!revealed) {
        const days = Math.max(0, Math.floor((new Date(capsule.openAt).getTime() - now) / 86400000))
        const hours = Math.max(0, Math.floor(((new Date(capsule.openAt).getTime() - now) % 86400000) / 3600000))
        return (
            <div className={`${styles.container} wish-universe-theme`}>
                <div className={styles.pageWrap}>
                    <BackBar />
                    <Card className={styles.detailCard}>
                        <div className={styles.sealedView}>
                            <h2 className={styles.sealedTitle}>{capsule.title}</h2>
                            <EnvelopeAnim opening={opening} />
                            {capsule.status === 'SEALED' && !expired && (
                                <>
                                    <p className={styles.sealedSubtitle}>🔒 封印中 · 内容被时间封存</p>
                                    <div className={styles.countdownRow}>
                                        <div className={styles.countdownCell}>
                                            <span className={styles.countdownNum}>{days}</span>
                                            <span className={styles.countdownUnit}>天</span>
                                        </div>
                                        <div className={styles.countdownCell}>
                                            <span className={styles.countdownNum}>{hours}</span>
                                            <span className={styles.countdownUnit}>时</span>
                                        </div>
                                    </div>
                                    <p className={styles.sealedMeta}>
                                        预定开启：{formatLocal(capsule.openAt)}（创建时区 {capsule.openAtTimezone}；按 UTC 判定，跨时区不影响到期）
                                    </p>
                                </>
                            )}
                            {canOpen && !opening && (
                                <>
                                    <p className={styles.readySubtitle}>🎁 到了拆开它的时刻</p>
                                    <Button type="primary" size="large" className={styles.openBtn} onClick={handleOpen}>
                                        拆开胶囊
                                    </Button>
                                </>
                            )}
                            {opening && <p className={styles.openingText}>正在拆封……</p>}
                        </div>
                    </Card>
                </div>
                <WishBGM />
            </div>
        )
    }

    // 已开启：拆信揭晓（信纸升起 + 星尘 + 内容/媒体展示）
    return (
        <div className={`${styles.container} wish-universe-theme`}>
            <div className={styles.pageWrap}>
                <BackBar />
                <Card className={styles.detailCard}>
                    <div className={styles.revealedView}>
                        <div className={styles.revealBadge}>
                            <span className={styles.revealEmoji}>💌</span>
                            {Array.from({ length: 10 }).map((_, i) => (
                                <span
                                    key={i}
                                    className={styles.revealStar}
                                    style={{ ['--angle' as string]: `${i * 36}deg`, ['--delay' as string]: `${(i % 4) * 0.07}s` }}
                                />
                            ))}
                        </div>
                        <h2 className={styles.ceremonyTitle}>{capsule.title}</h2>
                        <div className={styles.letterPaper}>
                            <Typography.Paragraph className={styles.letterContent}>{capsule.content}</Typography.Paragraph>
                        </div>
                        {capsule.mediaUrls && capsule.mediaUrls.length > 0 && (
                            <div className={styles.mediaGrid}>
                                {capsule.mediaUrls.map((url) => (
                                    <div key={url} className={styles.mediaWrap}>
                                        <Image src={url} alt="封存图片" className={styles.mediaImg} />
                                    </div>
                                ))}
                            </div>
                        )}
                        <div className={styles.metaRow}>
                            <Tag color="green">已于 {formatLocal(capsule.openedAt)} 开启</Tag>
                            <Tag>封存于 {formatLocal(capsule.createdAt)}</Tag>
                            <Tag>创建时区 {capsule.openAtTimezone}</Tag>
                        </div>
                        <Button type="primary" onClick={() => history.push('/wish/capsules')} className={styles.openBtn}>
                            返回我的胶囊
                        </Button>
                    </div>
                </Card>
            </div>
            <WishBGM />
        </div>
    )
}
