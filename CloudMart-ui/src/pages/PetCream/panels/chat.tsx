import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Input, Spin } from 'antd'
import {
    getPetChatPersona,
    getPetChatRequestStatus,
    listPetChatHistory,
    sendPetChat,
    type PetChatMessage,
    type PetPersona,
} from '@/api/pet'
import { getPersistedIntentKey } from '@/utils/request'
import { CreamButton } from '@/components/pet-cream/Cream'
import styles from '../../PetCream.module.css'

// ---------------- 面板：聊天 ----------------

export function ChatPanel({ petName }: { petName: string }) {
    const { message } = App.useApp()
    const [messages, setMessages] = useState<PetChatMessage[] | null>(null)
    const [draft, setDraft] = useState('')
    const [busy, setBusy] = useState(false)
    const [persona, setPersona] = useState<PetPersona | null>(null)
    const [showPersona, setShowPersona] = useState(false)
    const bodyRef = useRef<HTMLDivElement | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await listPetChatHistory({ pageSize: 30 })
        if (res.success) {
            setMessages(res.data)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    /** 人设卡按需加载：与注入 AI prompt 的身份信息同源（F8） */
    const loadPersona = useCallback(async () => {
        const { data: res } = await getPetChatPersona()
        if (res.success) {
            setPersona(res.data)
            setShowPersona(true)
        }
    }, [])

    useEffect(() => {
        const el = bodyRef.current
        if (el) {
            el.scrollTop = el.scrollHeight
        }
    }, [messages])

    const send = useCallback(async () => {
        const text = draft.trim()
        if (!text) {
            message.warning('说点什么吧')
            return
        }
        setBusy(true)
        try {
            const { data: res } = await sendPetChat(text)
            if (res.success) {
                setDraft('')
                await load()
            } else if (res.error?.code === 'PET_REQUEST_IN_PROGRESS') {
                // PET-23：在途——提示查询而非诱导重发（同键重放服务端收敛）
                message.info('它还在想回复…稍等片刻再查看结果')
            } else {
                message.warning(res.error?.message ?? '发送未成功')
            }
        } catch {
            // PET-14/§6.3：断网/超时（未知结果）——按原意图键查询终态，不生成新意图
            const requestKey = getPersistedIntentKey('POST', '/pet/chat', { message: text })
            if (!requestKey) {
                message.warning('网络不稳定，请重试')
                return
            }
            try {
                const { data: status } = await getPetChatRequestStatus(requestKey)
                if (status.success && status.data?.status === 'SUCCEEDED') {
                    setDraft('')
                    message.success('回复已送达')
                    await load()
                } else if (status.success && status.data?.status === 'PROCESSING') {
                    message.info('它还在想回复…稍后刷新即可看到')
                } else {
                    message.warning('发送未完成，可原样重发（不会重复计费）')
                }
            } catch {
                message.warning('网络不稳定，稍后可按原消息重试')
            }
        } finally {
            setBusy(false)
        }
    }, [draft, load, message])

    if (!messages) {
        return <Spin />
    }
    return (
        <div>
            <div className={styles.footerRow}>
                <p className={styles.panelDesc}>
                    {persona
                        ? `${persona.name} · ${persona.personalityText} · ${persona.careerName ?? '无业游民'} · ${persona.intimacyLevelName ?? `亲密度 Lv.${persona.intimacyLevel}`}`
                        : '它的性格、职业和口头禅，都和聊天时它"想"的一样'}
                </p>
                <CreamButton variant="ghost" onClick={() => (showPersona ? setShowPersona(false) : void loadPersona())}>
                    {showPersona ? '收起人设卡' : '看它的人设卡'}
                </CreamButton>
            </div>
            {showPersona && persona ? (
                <div className={styles.footerRow} style={{ paddingTop: 8 }}>
                    <p className={styles.panelDesc}>
                        {persona.ownerTitle ? `它叫你「${persona.ownerTitle}」` : '它还没学会怎么叫你'}
                        {persona.phrase ? ` · 口头禅：“${persona.phrase}”` : ''}
                    </p>
                </div>
            ) : null}
            <div ref={bodyRef} className={styles.chatBody}>
                {messages.map(item => (
                    <div key={String(item.messageId)} className={styles.bubbleRow}>
                        <div className={item.role === 'USER' ? styles.bubbleUser : styles.bubblePet}>
                            {item.role === 'PET' ? (
                                <span className={styles.bubbleWho}>{petName}</span>
                            ) : null}
                            {item.content}
                        </div>
                    </div>
                ))}
            </div>
            <div className={styles.renameRow}>
                <Input
                    className={styles.input}
                    value={draft}
                    onChange={event => setDraft(event.target.value)}
                    placeholder={`和${petName}说点什么…`}
                    maxLength={80}
                    onPressEnter={() => void send()}
                />
                <CreamButton loading={busy} onClick={() => void send()}>发送</CreamButton>
            </div>
        </div>
    )
}
