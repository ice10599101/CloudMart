import { useCallback, useEffect, useState } from 'react'
import { Input, ScrollView, Text, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { createSoftwareRequest, listMySoftwareRequests } from '@/api/software'
import styles from './index.module.scss'

const STATUS_LABEL: Record<string, string> = {
  OPEN: '寻找中',
  ANSWERED: '已找到',
  CLOSED: '已关闭',
  PENDING: '审核中',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
}

/** UX06：找软件需求（小程序端）——搜索无结果回跳保留关键词；提交进审核 */
export default function SoftwareRequests() {
  const [mine, setMine] = useState<Array<{
    id: string
    title: string
    usageDesc: string
    status: string
    auditStatus: string
    auditReason: string | null
  }>>([])
  const [title, setTitle] = useState('')
  const [usage, setUsage] = useState('')
  const [features, setFeatures] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(async () => {
    try {
      const res = await listMySoftwareRequests()
      setMine(res.data?.data ?? [])
    } catch {
      setMine([])
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const submit = async () => {
    if (!title.trim() || usage.trim().length < 20) {
      Taro.showToast({ title: '请填写标题与用途描述（≥20 字）', icon: 'none' })
      return
    }
    setSubmitting(true)
    try {
      await createSoftwareRequest({
        title: title.trim(),
        usageDesc: usage.trim(),
        mustHaveFeatures: features
          .split(/[,，]/)
          .map((v) => v.trim())
          .filter(Boolean),
      })
      Taro.showToast({ title: '已提交，审核通过后展示', icon: 'success' })
      setTitle('')
      setUsage('')
      setFeatures('')
      await load()
    } catch {
      Taro.showToast({ title: '提交失败，请稍后重试', icon: 'none' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <View className={styles.page}>
      <ScrollView scrollY style={{ height: '100vh' }}>
        <Text className={styles.title}>找软件需求</Text>
        <Text className={styles.cardSummary}>
          描述你要完成的事，让运营帮你找合适的软件。
        </Text>
        <View className={styles.card}>
          <Text className={styles.cardTitle}>一句话需求（≤80 字）</Text>
          <Input
            value={title}
            onInput={(e) => setTitle(e.detail.value)}
            maxlength={80}
            placeholder='如：找一款按拍摄日期批量重命名照片的工具'
          />
        </View>
        <View className={styles.card}>
          <Text className={styles.cardTitle}>用途描述（20~500 字）</Text>
          <Input
            value={usage}
            onInput={(e) => setUsage(e.detail.value)}
            maxlength={500}
            placeholder='要完成什么任务、什么场景、多少量级……'
          />
        </View>
        <View className={styles.card}>
          <Text className={styles.cardTitle}>必须功能（逗号分隔，可选）</Text>
          <Input
            value={features}
            onInput={(e) => setFeatures(e.detail.value)}
            placeholder='如：批量处理,预览,撤销'
          />
        </View>
        <View
          className={styles.newBtn}
          onClick={() => void submit()}
        >
          <Text className={styles.newBtnText}>
            {submitting ? '提交中…' : '提交需求（进入审核）'}
          </Text>
        </View>

        {mine.length > 0 ? (
          <View style={{ marginTop: 16 }}>
            <Text className={styles.title}>我的需求</Text>
            {mine.map((item) => (
              <View key={item.id} className={styles.card}>
                <Text className={styles.cardName}>{item.title}</Text>
                <Text className={styles.cardSummary}>
                  {STATUS_LABEL[item.auditStatus] ?? item.auditStatus} ·{' '}
                  {STATUS_LABEL[item.status] ?? item.status}
                </Text>
                {item.auditStatus === 'REJECTED' && item.auditReason ? (
                  <Text className={styles.cardSummary} style={{ color: '#FF4D4F' }}>
                    未通过：{item.auditReason}
                  </Text>
                ) : null}
              </View>
            ))}
          </View>
        ) : null}
      </ScrollView>
    </View>
  )
}
