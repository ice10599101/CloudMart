import { useEffect, useState } from 'react'
import { Form, Input, Button, Upload, Card, App, Modal, Tag, DatePicker, Popconfirm } from 'antd'
import { PlusOutlined, CloseOutlined, MailOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { history, useSearchParams } from 'umi'
import dayjs, { type Dayjs } from 'dayjs'
import { createCapsule, getWishDetail } from '@/api/wish'
import { uploadFile } from '@/api/file'
import { useAuthStore } from '@/stores/auth'
import { reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import styles from './CapsuleCreate.module.css'
import WishBGM from '@/components/WishBGM'

const { TextArea } = Input
const MAX_MEDIA = 9
const MAX_FILE_SIZE = 10 * 1024 * 1024
const MAX_TITLE = 100
const MAX_CONTENT = 5000
const MAX_YEARS_AHEAD = 10

/** 封印仪式：信封合拢 + 蜡封压印 + 星尘环绕（拆信动效在开启页） */
function SealCeremony({ openAtLocal }: { openAtLocal: string }) {
    return (
        <div className={styles.ceremony}>
            <div className={styles.sealCore}>
                <span className={styles.sealEnvelope}>✉️</span>
                <span className={styles.sealWax}>🔴</span>
                {Array.from({ length: 10 }).map((_, i) => (
                    <span
                        key={i}
                        className={styles.sealStar}
                        style={{ ['--angle' as string]: `${i * 36}deg`, ['--delay' as string]: `${(i % 5) * 0.08}s` }}
                    />
                ))}
            </div>
            <h2 className={styles.ceremonyTitle}>胶囊已封印</h2>
            <p className={styles.ceremonyText}>此刻的心意已被封存，将于 {openAtLocal} 开启</p>
            <Tag color="gold" className={styles.ceremonyTag}>🔒 到期前任何人（包括你自己）都无法查看内容</Tag>
        </div>
    )
}

export default function CapsuleCreate() {
    const [form] = Form.useForm()
    const [submitting, setSubmitting] = useState(false)
    const [sealed, setSealed] = useState<{ openAtLocal: string } | null>(null)
    const [mediaUrls, setMediaUrls] = useState<string[]>([])
    const [uploadingCount, setUploadingCount] = useState(0)
    const [leaveConfirmOpen, setLeaveConfirmOpen] = useState(false)
    const [searchParams, setSearchParams] = useSearchParams()
    const { message } = App.useApp()
    const { user } = useAuthStore()

    useEffect(() => {
        reportTimezoneIfNeeded()
        if (!user) {
            message.warning('请先登录后再封存胶囊')
            history.push('/login?redirect=/wish/capsules/create')
        }
    }, [user])

    // 预期管理通知「转入时间胶囊」深链：预填心愿标题/内容/照片，open_at 默认 +30 天
    const prefillWishId = searchParams.get('wishId')
    useEffect(() => {
        if (!prefillWishId || !user) return
        let cancelled = false
        getWishDetail(Number(prefillWishId))
            .then((res) => {
                if (cancelled || !res.data.success || !res.data.data) return
                const detail = res.data.data
                form.setFieldsValue({
                    title: detail.title,
                    content: detail.description,
                })
                if (detail.mediaUrls.length > 0) {
                    setMediaUrls(detail.mediaUrls.slice(0, MAX_MEDIA))
                }
                form.setFieldsValue({ openAt: dayjs().add(30, 'day') })
                setSearchParams(new URLSearchParams())
            })
            .catch(() => {
                // 心愿不可见/已删除时静默，不影响正常创建
            })
        return () => {
            cancelled = true
        }
    }, [prefillWishId, user, form, setSearchParams])

    const handleUpload = async (file: File) => {
        if (mediaUrls.length + uploadingCount >= MAX_MEDIA) {
            message.warning(`最多封存 ${MAX_MEDIA} 张图片`)
            return false
        }
        if (!file.type.startsWith('image/')) {
            message.error('仅支持图片文件')
            return false
        }
        if (file.size > MAX_FILE_SIZE) {
            message.error('单张图片不能超过 10MB')
            return false
        }
        setUploadingCount((n) => n + 1)
        try {
            const res = await uploadFile(file)
            if (res.data.success && res.data.data?.url) {
                setMediaUrls((prev) => [...prev, res.data.data!.url!])
            } else {
                message.error('上传失败，请重试')
            }
        } catch {
            message.error('上传失败，请检查网络后重试')
        } finally {
            setUploadingCount((n) => n - 1)
        }
        return false
    }

    const handleSubmit = async (values: { title: string; content: string; openAt: Dayjs }) => {
        const openAtUtc = values.openAt.toDate()
        if (openAtUtc.getTime() <= Date.now()) {
            message.error('开启时间必须晚于当前时间')
            return
        }
        if (openAtUtc.getTime() > Date.now() + MAX_YEARS_AHEAD * 365 * 24 * 3600 * 1000) {
            message.error('开启时间最远不能超过 10 年')
            return
        }
        setSubmitting(true)
        try {
            const res = await createCapsule({
                title: values.title.trim(),
                content: values.content,
                mediaUrls: mediaUrls.length > 0 ? mediaUrls : undefined,
                openAt: openAtUtc.toISOString(),
                openAtTz: Intl.DateTimeFormat().resolvedOptions().timeZone,
            })
            if (res.data.success) {
                setSealed({ openAtLocal: values.openAt.format('YYYY-MM-DD HH:mm') })
            }
        } catch {
            // 错误已由 request 拦截器处理
        } finally {
            setSubmitting(false)
        }
    }

    /** 已填写内容时离开需二次确认 */
    const handleBack = () => {
        const values = form.getFieldsValue() as { title?: string; content?: string; openAt?: Dayjs }
        const hasDraft = Boolean(values.title?.trim() || values.content?.trim() || values.openAt) || mediaUrls.length > 0
        if (hasDraft && !sealed) {
            setLeaveConfirmOpen(true)
            return
        }
        history.push('/wish/capsules')
    }

    if (sealed) {
        return (
            <div className={`${styles.container} wish-universe-theme`}>
                <div className={styles.formWrap}>
                    <Card className={styles.formCard}>
                        <SealCeremony openAtLocal={sealed.openAtLocal} />
                        <div className={styles.ceremonyActions}>
                            <Button type="primary" size="large" className={styles.submitBtn} onClick={() => history.push('/wish/capsules')}>
                                查看我的胶囊
                            </Button>
                            <Button
                                size="large"
                                onClick={() => {
                                    form.resetFields()
                                    setMediaUrls([])
                                    setSealed(null)
                                }}
                            >
                                再封一个
                            </Button>
                        </div>
                    </Card>
                </div>
                <WishBGM />
            </div>
        )
    }

    return (
        <div className={`${styles.container} wish-universe-theme`}>
            <div className={styles.formWrap}>
                <div className={styles.backBar}>
                    <Button type="text" icon={<ArrowLeftOutlined />} onClick={handleBack} className={styles.backBtn}>
                        返回
                    </Button>
                </div>
                <h1 className={styles.pageTitle}>
                    <MailOutlined /> 封存时间胶囊
                </h1>
                <p className={styles.pageSubtitle}>写给未来的自己——到期那一刻，拆开此刻封存的心意</p>
                <Card className={styles.formCard}>
                    <Form form={form} layout="vertical" onFinish={handleSubmit} className={styles.form}>
                        <Form.Item
                            name="title"
                            label="胶囊标题"
                            rules={[
                                { required: true, message: '请给胶囊起个名字' },
                                { max: MAX_TITLE, message: `标题不超过 ${MAX_TITLE} 字` },
                            ]}
                        >
                            <Input placeholder="如：写给一年后的自己" showCount maxLength={MAX_TITLE} />
                        </Form.Item>

                        <Form.Item
                            name="content"
                            label="封存内容（开启前不可见）"
                            rules={[
                                { required: true, message: '写下想封存的内容' },
                                { max: MAX_CONTENT, message: `内容不超过 ${MAX_CONTENT} 字` },
                            ]}
                        >
                            <TextArea
                                placeholder="此刻的你想对未来的自己说什么？愿望、心情、约定……封存后到期前任何人无法查看"
                                showCount
                                maxLength={MAX_CONTENT}
                                rows={10}
                            />
                        </Form.Item>

                        <Form.Item label={`封存照片（可选，最多 ${MAX_MEDIA} 张）`}>
                            <div className={styles.uploadArea}>
                                {mediaUrls.map((url) => (
                                    <div key={url} className={styles.mediaItem}>
                                        <img src={url} alt="封存图片" className={styles.mediaPreview} />
                                        <button
                                            type="button"
                                            className={styles.mediaRemove}
                                            onClick={() => setMediaUrls((prev) => prev.filter((u) => u !== url))}
                                            aria-label="移除图片"
                                        >
                                            ×
                                        </button>
                                    </div>
                                ))}
                                {mediaUrls.length < MAX_MEDIA && (
                                    <Upload accept="image/*" showUploadList={false} beforeUpload={handleUpload} disabled={uploadingCount > 0} multiple>
                                        <div className={styles.uploadTrigger}>
                                            {uploadingCount > 0 ? <span className={styles.uploadingDot} /> : <PlusOutlined />}
                                        </div>
                                    </Upload>
                                )}
                            </div>
                        </Form.Item>

                        <Form.Item
                            name="openAt"
                            label="开启时间"
                            extra={`按你的时区（${Intl.DateTimeFormat().resolvedOptions().timeZone}）选择；到期判定按 UTC，跨时区旅行不影响`}
                            rules={[{ required: true, message: '请选择开启时间' }]}
                        >
                            <DatePicker
                                showTime={{ format: 'HH:mm' }}
                                format="YYYY-MM-DD HH:mm"
                                disabledDate={(d) => d.isBefore(dayjs().startOf('day'))}
                                showNow={false}
                                placeholder="选择未来的开启时刻"
                                className={styles.openAtPicker}
                            />
                        </Form.Item>

                        <p className={styles.rewardHint}>封存后状态为「封印中」，到期后可拆开；开启前不可取消查看</p>

                        <Form.Item className={styles.submitArea}>
                            <Popconfirm
                                title="确定封存这个胶囊吗？"
                                description="封存后到期前无法查看内容，也不可修改"
                                onConfirm={() => form.submit()}
                                okText="封存"
                                cancelText="再想想"
                            >
                                <Button type="primary" size="large" loading={submitting} disabled={uploadingCount > 0} className={styles.submitBtn}>
                                    🔒 封存胶囊
                                </Button>
                            </Popconfirm>
                        </Form.Item>
                    </Form>
                </Card>
            </div>

            <Modal
                title="确认离开"
                open={leaveConfirmOpen}
                okText="放弃并离开"
                cancelText="继续编辑"
                onOk={() => history.push('/wish/capsules')}
                onCancel={() => setLeaveConfirmOpen(false)}
            >
                <p>胶囊尚未封存，离开后将丢失已填写的内容。</p>
            </Modal>
            <WishBGM />
        </div>
    )
}
