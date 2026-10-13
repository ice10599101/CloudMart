import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Input,
  Space,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  listPetPersonaPhrases,
  upsertPetPersonaPhrase,
  type AdminPetPersonaPhrase,
} from '@/api/admin/pet'

const { Text } = Typography

/** 性格文案（与后端 PetPersonality 枚举对齐） */
const PERSONALITY_LABELS: Record<string, string> = {
  LIVELY: '活泼',
  GENTLE: '温柔',
  TSUNDERE: '傲娇',
  SIMPLE: '憨厚',
  COOL: '高冷',
  CHATTERBOX: '话痨',
}

/** 口头禅面板（F8 配置化：按性格编辑，保存后 ≤60 秒同步全部实例） */
export function PersonaPhrasePanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<AdminPetPersonaPhrase[]>([])
  const [savingKey, setSavingKey] = useState<string | null>(null)
  const [drafts, setDrafts] = useState<Record<string, string>>({})

  const load = useCallback(async () => {
    try {
      const { data: res } = await listPetPersonaPhrases()
      if (res.success) {
        setRows(res.data || [])
        const initial: Record<string, string> = {}
        for (const row of res.data || []) {
          initial[row.personality] = row.source === 'DB' ? row.phrase : ''
        }
        setDrafts(initial)
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (personality: string) => {
    const phrase = (drafts[personality] ?? '').trim()
    if (!phrase) {
      messageApi.warning('先填写口头禅（{name} 代表宠物名）')
      return
    }
    setSavingKey(personality)
    try {
      const { data: res } = await upsertPetPersonaPhrase({ personality, phrase })
      if (res.success) {
        messageApi.success('已保存，≤60 秒同步全部实例')
        void load()
      }
    } catch {
      // 拦截器已提示
    } finally {
      setSavingKey(null)
    }
  }

  return (
    <div>
      {contextHolder}
      <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
        口头禅会注入 AI 聊天 prompt 并展示在聊天页人设卡；{'{name}'} 占位宠物名；保存后 60 秒内全部实例生效
      </Text>
      {rows.map((row) => (
        <Card size="small" key={row.personality} style={{ marginBottom: 12 }}>
          <Space wrap style={{ width: '100%' }}>
            <Tag>{PERSONALITY_LABELS[row.personality] ?? row.personality}</Tag>
            <Input
              style={{ width: 360 }}
              placeholder={row.source === 'DEFAULT' ? `出厂默认：${row.phrase}` : row.phrase}
              value={drafts[row.personality] ?? ''}
              onChange={(e) => setDrafts({ ...drafts, [row.personality]: e.target.value })}
            />
            <Button
              type="primary"
              size="small"
              loading={savingKey === row.personality}
              onClick={() => void save(row.personality)}
            >
              保存
            </Button>
            <Text type="secondary">{row.source === 'DB' ? '已自定义' : '出厂默认'}</Text>
          </Space>
        </Card>
      ))}
    </div>
  )
}
