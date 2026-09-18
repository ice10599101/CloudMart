import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { App, Button, Checkbox, Empty, Input, Spin, Upload } from 'antd'
import { AudioFilled, AudioOutlined, CloseOutlined, CommentOutlined, PictureOutlined, SendOutlined } from '@ant-design/icons'
import type { TextAreaRef } from 'antd/es/input/TextArea'
import { history } from 'umi'
import {
  addDriftBottleComment,
  listDriftBottleComments,
  type DriftBottleCommentItem,
} from '@/api/wish'
import { uploadFile } from '@/api/file'
import DecoratedAvatar from '@/components/DecoratedAvatar'
import EmojiPanel from '@/components/EmojiPanel'
import VoiceRecorderModal from '@/components/TiptapEditor/modals/VoiceRecorderModal'
import styles from './style.module.css'

/**
 * 漂流瓶瓶下评论树（捞到瓶后与瓶子作者交流的载体）：
 * 仅投瓶人与捞起人可看/可评；评论默认匿名（可切实名）；支持回复（parentId）。
 * 输入框支持表情选择器、图片上传与语音录制：图片/语音作为附件预览（缩略图/芯片），
 * 发送时以 ![图片](url) / [语音](url) 标记拼进内容；渲染时还原为图片与播放器。
 * cursor 分页 + 发表后重载首屏。
 */

const COMMENT_PAGE_SIZE = 10

/** 行内附件标记：图片 ![图片](url) / 语音 [语音](url)，仅接受 http(s) 链接 */
const INLINE_ATTACHMENT_PATTERN = /!\[[^\]]*\]\((https?:\/\/[^)\s]+)\)|\[语音\]\((https?:\/\/[^)\s]+)\)/g

/** 评论内容 → 文本 + 图片/语音节点（未匹配部分按纯文本渲染，React 自动转义） */
function renderInlineContent(content: string) {
  const nodes: ReactNode[] = []
  const pattern = new RegExp(INLINE_ATTACHMENT_PATTERN)
  let lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = pattern.exec(content)) !== null) {
    if (match.index > lastIndex) nodes.push(content.slice(lastIndex, match.index))
    if (match[1]) {
      nodes.push(<img key={`img-${match.index}`} src={match[1]} alt="图片" className={styles.inlineImage} />)
    } else if (match[2]) {
      nodes.push(<audio key={`audio-${match.index}`} controls preload="none" src={match[2]} className={styles.inlineAudio} />)
    }
    lastIndex = match.index + match[0].length
  }
  if (lastIndex < content.length) nodes.push(content.slice(lastIndex))
  return nodes
}

interface DriftBottleCommentsProps {
  bottleId: number
}

export default function DriftBottleComments({ bottleId }: DriftBottleCommentsProps) {
  const { message } = App.useApp()
  const [comments, setComments] = useState<DriftBottleCommentItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [posting, setPosting] = useState(false)
  const [draft, setDraft] = useState('')
  /** 待发送附件：图片（缩略图预览）与语音（芯片预览），发送时拼接为标记 */
  const [pendingImages, setPendingImages] = useState<string[]>([])
  const [pendingVoices, setPendingVoices] = useState<string[]>([])
  /** 回复目标（null = 发表顶级评论） */
  const [replyTo, setReplyTo] = useState<DriftBottleCommentItem | null>(null)
  /** 是否匿名发言（默认匿名） */
  const [anonymous, setAnonymous] = useState(true)
  /** 语音录制弹窗 */
  const [voiceOpen, setVoiceOpen] = useState(false)
  /** 图片上传中 */
  const [uploadingImage, setUploadingImage] = useState(false)
  /** 防重复加载（重载/竞态） */
  const requestSeq = useRef(0)
  const inputRef = useRef<TextAreaRef | null>(null)

  const loadFirstPage = useCallback(async () => {
    const seq = ++requestSeq.current
    setLoading(true)
    try {
      const res = await listDriftBottleComments(bottleId, { pageSize: COMMENT_PAGE_SIZE })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        setComments(res.data.data ?? [])
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [bottleId])

  useEffect(() => {
    loadFirstPage()
  }, [loadFirstPage])

  const handleLoadMore = async () => {
    if (!cursor || loadingMore) return
    const seq = ++requestSeq.current
    setLoadingMore(true)
    try {
      const res = await listDriftBottleComments(bottleId, {
        cursor,
        pageSize: COMMENT_PAGE_SIZE,
      })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        // cursor 分页按 id 去重合并，防御服务端数据变动导致的重复
        setComments((prev) => {
          const seen = new Set(prev.map((c) => c.id))
          return [...prev, ...(res.data.data ?? []).filter((c) => !seen.has(c.id))]
        })
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      if (seq === requestSeq.current) setLoadingMore(false)
    }
  }

  /** 在光标处插入文本（表情），插入后光标移到插入内容末尾 */
  const insertToDraft = (text: string) => {
    const el = inputRef.current?.resizableTextArea?.textArea
    if (!el) {
      setDraft((prev) => prev + text)
      return
    }
    const start = el.selectionStart ?? draft.length
    const end = el.selectionEnd ?? draft.length
    setDraft(draft.slice(0, start) + text + draft.slice(end))
    requestAnimationFrame(() => {
      el.focus()
      el.setSelectionRange(start + text.length, start + text.length)
    })
  }

  const handleUploadImage = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.warning('只能上传图片文件')
      return
    }
    setUploadingImage(true)
    try {
      const { data: response } = await uploadFile(file)
      if (response.data?.url) {
        setPendingImages((prev) => [...prev, response.data.url])
      } else if (response.error) {
        message.error(response.error.message || '图片上传失败')
      }
    } catch {
      // 拦截器已提示
    } finally {
      setUploadingImage(false)
    }
  }

  const handleVoiceUploaded = (url: string) => {
    setPendingVoices((prev) => [...prev, url])
  }

  const handleSubmit = async () => {
    // 附件标记在发送时拼进内容；字符数与正文共享 500 上限（服务端校验兜底）
    const attachmentMarkers = [
      ...pendingImages.map((url) => `![图片](${url})`),
      ...pendingVoices.map((url) => `[语音](${url})`),
    ]
    const content = [draft.trim(), ...attachmentMarkers].filter(Boolean).join('\n')
    if (!content) {
      message.warning('先写点什么吧')
      return
    }
    setPosting(true)
    try {
      await addDriftBottleComment(bottleId, {
        content,
        parentId: replyTo?.id ?? null,
        isAnonymous: anonymous,
      })
      message.success(replyTo ? '回复已送达 💬' : '评论已发出 💬')
      setDraft('')
      setPendingImages([])
      setPendingVoices([])
      setReplyTo(null)
      loadFirstPage()
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setPosting(false)
    }
  }

  const goProfile = (userId: number | null) => {
    if (userId !== null) history.push(`/user/${userId}`)
  }

  const renderTime = (createdAt: string) =>
    new Date(createdAt).toLocaleString('zh-CN', { hour12: false })

  const isComposerEmpty = !draft.trim() && pendingImages.length === 0 && pendingVoices.length === 0

  return (
    <div className={styles.section}>
      {/* 评论输入区 */}
      <div className={styles.composer}>
        {replyTo && (
          <div className={styles.replyBar}>
            <span className={styles.replyLabel}>回复 @{replyTo.nickname}</span>
            <Button size="small" type="text" onClick={() => setReplyTo(null)} aria-label="取消回复">
              取消
            </Button>
          </div>
        )}
        <Input.TextArea
          ref={inputRef}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={replyTo ? `回复 @${replyTo.nickname}…` : '与瓶子主人说点什么…'}
          autoSize={{ minRows: 2, maxRows: 5 }}
          maxLength={500}
          className={styles.composerInput}
        />

        {/* 附件预览：图片缩略图 + 语音芯片（发送前可移除） */}
        {(pendingImages.length > 0 || pendingVoices.length > 0) && (
          <div className={styles.attachmentRow}>
            {pendingImages.map((url) => (
              <div key={url} className={styles.imageThumb}>
                <img src={url} alt="待发送图片" />
                <button
                  type="button"
                  className={styles.thumbRemove}
                  aria-label="移除图片"
                  onClick={() => setPendingImages((prev) => prev.filter((item) => item !== url))}
                >
                  <CloseOutlined />
                </button>
              </div>
            ))}
            {pendingVoices.map((url) => (
              <div key={url} className={styles.voiceChip}>
                <AudioFilled />
                <span>语音消息</span>
                <button
                  type="button"
                  className={styles.thumbRemove}
                  aria-label="移除语音"
                  onClick={() => setPendingVoices((prev) => prev.filter((item) => item !== url))}
                >
                  <CloseOutlined />
                </button>
              </div>
            ))}
          </div>
        )}

        <div className={styles.composerFooter}>
          <div className={styles.toolsRow}>
            <EmojiPanel onPick={insertToDraft} />
            <Upload
              accept="image/*"
              showUploadList={false}
              beforeUpload={(file) => {
                handleUploadImage(file)
                return false
              }}
              disabled={uploadingImage}
            >
              <Button
                type="text"
                size="small"
                icon={<PictureOutlined />}
                loading={uploadingImage}
                aria-label="上传图片"
                title="图片"
              />
            </Upload>
            <Button
              type="text"
              size="small"
              icon={<AudioOutlined />}
              onClick={() => setVoiceOpen(true)}
              aria-label="录制语音"
              title="语音"
            />
          </div>
          <div className={styles.composerActions}>
            <Checkbox checked={anonymous} onChange={(e) => setAnonymous(e.target.checked)}>
              匿名发言（不选将展示你的昵称头像）
            </Checkbox>
            <Button
              type="primary"
              size="small"
              icon={<SendOutlined />}
              loading={posting}
              disabled={isComposerEmpty}
              onClick={handleSubmit}
            >
              发送
            </Button>
          </div>
        </div>
      </div>

      {/* 评论列表 */}
      {loading ? (
        <div className={styles.loadingWrap}>
          <Spin />
        </div>
      ) : comments.length === 0 ? (
        <Empty description="还没有评论，来聊聊吧" className={styles.empty} />
      ) : (
        <ul className={styles.list}>
          {comments.map((comment) => (
            <li key={comment.id} className={styles.item}>
              <DecoratedAvatar
                userId={comment.userId}
                size={32}
                src={comment.avatar || undefined}
                fallback={<CommentOutlined />}
                style={comment.userId === null ? undefined : { cursor: 'pointer' }}
                onClick={() => goProfile(comment.userId)}
              />
              <div className={styles.itemBody}>
                <div className={styles.itemMeta}>
                  <span
                    className={comment.userId === null ? styles.itemNicknamePlain : styles.itemNickname}
                    onClick={() => goProfile(comment.userId)}
                  >
                    {comment.nickname}
                    {comment.isAnonymous && <em className={styles.anonMark}>匿名</em>}
                  </span>
                  <span className={styles.itemTime}>{renderTime(comment.createdAt)}</span>
                </div>
                <p className={styles.itemContent}>
                  {comment.replyToNickname && (
                    <span className={styles.replyTarget}>回复 @{comment.replyToNickname}：</span>
                  )}
                  {renderInlineContent(comment.content)}
                </p>
                <Button
                  size="small"
                  type="text"
                  className={styles.replyBtn}
                  onClick={() => setReplyTo(comment)}
                >
                  回复
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {hasMore && !loading && (
        <div className={styles.loadMoreWrap}>
          <Button loading={loadingMore} onClick={handleLoadMore}>
            加载更多评论
          </Button>
        </div>
      )}

      <VoiceRecorderModal
        open={voiceOpen}
        onClose={() => setVoiceOpen(false)}
        onUploaded={handleVoiceUploaded}
      />
    </div>
  )
}
