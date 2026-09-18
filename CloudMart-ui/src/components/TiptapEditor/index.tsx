import { useEditor, EditorContent } from '@tiptap/react'
import TiptapEmojiAt from '@/components/TiptapEmojiAt'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Highlight from '@tiptap/extension-highlight'
import TextAlign from '@tiptap/extension-text-align'
import Underline from '@tiptap/extension-underline'
import { TextStyle } from '@tiptap/extension-text-style'
import Color from '@tiptap/extension-color'
import Superscript from '@tiptap/extension-superscript'
import Subscript from '@tiptap/extension-subscript'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import { useEffect, useCallback, useState, useRef } from 'react'
import {
  BoldOutlined,
  ItalicOutlined,
  UnderlineOutlined,
  AlignLeftOutlined,
  AlignCenterOutlined,
  AlignRightOutlined,
  MenuOutlined,
  LinkOutlined,
  PictureOutlined,
  HighlightOutlined,
  FontSizeOutlined,
  ClearOutlined,
  FontColorsOutlined,
  UploadOutlined,
  LoadingOutlined,
  AudioOutlined,
  PlayCircleOutlined,
  CustomerServiceOutlined,
  EditOutlined,
  BarChartOutlined,
  SurveyOutlined,
} from '@ant-design/icons'
import { Tooltip, Input, Modal, Popover, InputNumber, Segmented } from 'antd'
import { message } from '@/utils/appMessage'
import { uploadFile } from '@/api/file'
import { FontSize } from './extensions/fontSize'
import { FontFamily } from './extensions/fontFamily'
import { MediaAudio } from './extensions/mediaAudio'
import { MediaVideo } from './extensions/mediaVideo'
import { PollAttachment } from './extensions/pollNode'
import { SurveyAttachment } from './extensions/surveyNode'
import type { PollConfig } from './extensions/pollNode'
import type { SurveyConfig } from './extensions/surveyNode'
import VoiceRecorderModal from './modals/VoiceRecorderModal'
import DoodleModal from './modals/DoodleModal'
import PollConfigModal from './modals/PollConfigModal'
import SurveyConfigModal from './modals/SurveyConfigModal'
import styles from './style.module.css'

/**
 * 全站统一富文本编辑器（TipTap）。
 *
 * <p>工具栏按站点内容规范裁剪：删除线/上下标/行内代码/标题/列表/表格/
 * 引用块/代码块/分割线/撤销重做按钮已移除（对应扩展仍加载，保证历史内容
 * 编辑时不会被解析丢弃——数据完整性优先）。新增：语音、音视频上传（≤50MB）、
 * 音视频外链（本站播放器）、涂鸦、投票、问卷。</p>
 */

interface TiptapEditorProps {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
}

/** 音视频上传大小上限（与 mall-file file.max-size 一致） */
const MAX_MEDIA_BYTES = 50 * 1024 * 1024

type MediaUrlKind = 'audio' | 'video'

function ColorPickerPanel({
  currentColor,
  onColorChange,
  label,
}: {
  currentColor: string
  onColorChange: (color: string) => void
  label: string
}) {
  const [color, setColor] = useState(currentColor || '#FFFFFF')

  useEffect(() => {
    setColor(currentColor || '#FFFFFF')
  }, [currentColor])

  return (
    <div className={styles.colorPicker}>
      <div className={styles.colorPickerTitle}>{label}</div>
      <div className={styles.colorPickerCustom}>
        <input
          type="color"
          value={color}
          onChange={(e) => {
            setColor(e.target.value)
            onColorChange(e.target.value)
          }}
          className={styles.nativeColorInputLarge}
        />
        <Input
          value={color}
          onChange={(e) => {
            const val = e.target.value
            setColor(val)
            if (/^#[0-9A-Fa-f]{6}$/.test(val)) {
              onColorChange(val)
            }
          }}
          className={styles.colorHexInput}
          maxLength={7}
        />
      </div>
    </div>
  )
}

function ToolbarButton({
  onClick,
  isActive,
  icon,
  title,
  disabled,
}: {
  onClick: () => void
  isActive?: boolean
  icon: React.ReactNode
  title: string
  disabled?: boolean
}) {
  return (
    <Tooltip title={title}>
      <button
        type="button"
        onClick={onClick}
        disabled={disabled}
        className={`${styles.toolbarBtn} ${isActive ? styles.toolbarBtnActive : ''} ${disabled ? styles.toolbarBtnDisabled : ''}`}
      >
        {icon}
      </button>
    </Tooltip>
  )
}

function detectMediaKind(url: string): MediaUrlKind | null {
  const clean = url.split('?')[0].toLowerCase()
  if (/\.(mp3|m4a|aac|wav|ogg|oga|flac|webm)(\s*)$/.test(clean)) {
    // webm 既可音频也可视频：默认按音频处理，用户可在选择器改视频
    return 'audio'
  }
  if (/\.(mp4|mov|avi|mkv|m4v|ogv)$/.test(clean)) return 'video'
  return null
}

/** 生成投票/问卷客户端 UUID（发布时作为后端主键幂等落库） */
function newAttachmentId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `att-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

export default function TiptapEditor({ value, onChange, placeholder }: TiptapEditorProps) {
  const [fontSizeValue, setFontSizeValue] = useState<number>(14)
  const [fontSizeInputVisible, setFontSizeInputVisible] = useState(false)
  const [fontColorVisible, setFontColorVisible] = useState(false)
  const [highlightColorVisible, setHighlightColorVisible] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [isMediaUploading, setIsMediaUploading] = useState(false)
  const [showFontPanel, setShowFontPanel] = useState(false)
  const [fontFamilyValue, setFontFamilyValue] = useState('')
  const [voiceOpen, setVoiceOpen] = useState(false)
  const [doodleOpen, setDoodleOpen] = useState(false)
  const [pollOpen, setPollOpen] = useState(false)
  const [surveyOpen, setSurveyOpen] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const mediaInputRef = useRef<HTMLInputElement>(null)
  const linkInputRef = useRef<string>('')
  const mediaUrlInputRef = useRef<string>('')
  const mediaKindRef = useRef<MediaUrlKind>('audio')

  const editor = useEditor({
    extensions: [
      // StarterKit 中 heading/blockquote/list/table/codeBlock 等扩展保留：
      // 历史内容（含标题/表格/引用的旧帖）再次编辑时不会被解析丢弃（数据完整性优先），
      // 仅按内容规范移除了对应工具栏入口
      StarterKit.configure({
        heading: { levels: [1, 2, 3, 4, 5, 6] },
        blockquote: {},
        horizontalRule: {},
        codeBlock: {},
      }),
      Placeholder.configure({ placeholder: placeholder ?? '请输入内容...' }),
      Image.configure({ inline: false, allowBase64: true }),
      Link.configure({ openOnClick: false, autolink: true }),
      Highlight.configure({ multicolor: true }),
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      Underline,
      TextStyle,
      Color,
      Superscript,
      Subscript,
      Table.configure({ resizable: true }),
      TableRow,
      TableCell,
      TableHeader,
      FontSize,
      FontFamily,
      MediaAudio,
      MediaVideo,
      PollAttachment,
      SurveyAttachment,
    ],
    content: value ?? '',
    onUpdate: ({ editor: e }) => {
      onChange?.(e.getHTML())
    },
    onSelectionUpdate: ({ editor: e }) => {
      const attrs = e.getAttributes('textStyle')
      if (attrs.fontSize) {
        const parsed = parseInt(attrs.fontSize, 10)
        if (!isNaN(parsed)) setFontSizeValue(parsed)
      } else {
        setFontSizeValue(14)
      }
    },
  })

  useEffect(() => {
    if (!editor) return
    if (value === undefined || value === null) return
    try {
      if (editor.getHTML() === value) return
    } catch {
      return
    }
    editor.commands.setContent(value, { emitUpdate: false })
  }, [value, editor])

  const handleLocalImageUpload = useCallback(() => {
    fileInputRef.current?.click()
  }, [])

  const handleMediaUpload = useCallback(() => {
    mediaInputRef.current?.click()
  }, [])

  const insertUploadedMedia = useCallback((url: string, kind: MediaUrlKind, title?: string) => {
    if (!editor) return
    if (kind === 'audio') {
      editor.chain().focus().setMediaAudio({ src: url, title }).run()
    } else {
      editor.chain().focus().setMediaVideo({ src: url, title }).run()
    }
  }, [editor])

  const handleMediaFileChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !editor) return
    e.target.value = ''
    if (!file.type.startsWith('audio/') && !file.type.startsWith('video/')) {
      message.error('仅支持音频或视频文件')
      return
    }
    if (file.size > MAX_MEDIA_BYTES) {
      message.error(`文件 ${(file.size / 1024 / 1024).toFixed(1)} MB 超过 50MB 上限`)
      return
    }
    setIsMediaUploading(true)
    try {
      const { data: response } = await uploadFile(file)
      if (response.data?.url) {
        insertUploadedMedia(response.data.url, file.type.startsWith('audio/') ? 'audio' : 'video', file.name)
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上传失败，请重试')
    } finally {
      setIsMediaUploading(false)
    }
  }, [editor, insertUploadedMedia])

  const handleFileChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !editor) return
    setIsUploading(true)
    try {
      const { data: response } = await uploadFile(file)
      if (response.data?.url) {
        editor.chain().focus().setImage({ src: response.data.url }).run()
      }
    } catch {
      message.error('图片上传失败，请重试')
    } finally {
      setIsUploading(false)
      e.target.value = ''
    }
  }, [editor])

  const addImageByUrl = useCallback(() => {
    if (!editor) return
    let imageUrl = ''
    Modal.confirm({
      title: '插入图片',
      content: (
        <Input
          placeholder="请输入图片URL"
          onChange={(e) => { imageUrl = e.target.value }}
          onPressEnter={() => {
            if (imageUrl) {
              editor.chain().focus().setImage({ src: imageUrl }).run()
              Modal.destroyAll()
            }
          }}
        />
      ),
      okText: '插入',
      cancelText: '取消',
      onOk: () => {
        if (imageUrl) {
          editor.chain().focus().setImage({ src: imageUrl }).run()
        }
      },
    })
  }, [editor])

  const addMediaByUrl = useCallback(() => {
    if (!editor) return
    mediaUrlInputRef.current = ''
    mediaKindRef.current = 'audio'
    Modal.confirm({
      title: '插入音乐 / 视频',
      content: (
        <div>
          <Input
            placeholder="粘贴音频或视频链接（mp3/mp4 等，本站播放器播放）"
            onChange={(e) => {
              mediaUrlInputRef.current = e.target.value
              const detected = detectMediaKind(e.target.value)
              if (detected) mediaKindRef.current = detected
            }}
          />
          <div style={{ marginTop: 12 }}>
            <Segmented
              defaultValue="audio"
              options={[
                { label: '音乐', value: 'audio' },
                { label: '视频', value: 'video' },
              ]}
              onChange={(value) => { mediaKindRef.current = value as MediaUrlKind }}
            />
          </div>
        </div>
      ),
      okText: '插入',
      cancelText: '取消',
      onOk: () => {
        const url = mediaUrlInputRef.current.trim()
        if (!url) return
        if (!/^https?:\/\//i.test(url) && !url.startsWith('/')) {
          message.error('请输入有效的链接地址')
          return
        }
        insertUploadedMedia(url, mediaKindRef.current)
      },
    })
  }, [editor, insertUploadedMedia])

  const handlePollSubmit = useCallback((config: PollConfig) => {
    if (!editor) return
    editor.chain().focus().insertPollAttachment({ pollId: newAttachmentId(), config }).run()
  }, [editor])

  const handleSurveySubmit = useCallback((config: SurveyConfig) => {
    if (!editor) return
    editor.chain().focus().insertSurveyAttachment({ surveyId: newAttachmentId(), config }).run()
  }, [editor])

  const addLink = useCallback(() => {
    if (!editor) return
    const existingHref = editor.getAttributes('link').href ?? ''
    linkInputRef.current = existingHref
    Modal.confirm({
      title: existingHref ? '编辑链接' : '插入链接',
      content: (
        <Input
          defaultValue={existingHref}
          placeholder="请输入链接URL"
          onChange={(e) => { linkInputRef.current = e.target.value }}
          onPressEnter={() => {
            if (linkInputRef.current) {
              editor.chain().focus().setLink({ href: linkInputRef.current }).run()
              Modal.destroyAll()
            }
          }}
        />
      ),
      okText: '确定',
      cancelText: '取消',
      onOk: () => {
        if (linkInputRef.current) {
          editor.chain().focus().setLink({ href: linkInputRef.current }).run()
        } else {
          editor.chain().focus().unsetLink().run()
        }
      },
    })
  }, [editor])

  const handleFontColorChange = useCallback((color: string) => {
    if (!editor) return
    editor.chain().focus().setColor(color).run()
  }, [editor])

  const handleHighlightColorChange = useCallback((color: string) => {
    if (!editor) return
    editor.chain().focus().toggleHighlight({ color }).run()
  }, [editor])

  const handleFontSizeChange = useCallback((size: number | null) => {
    if (!editor || size === null) return
    if (size === 14) {
      editor.chain().focus().unsetFontSize().run()
    } else {
      editor.chain().focus().setFontSize(`${size}px`).run()
    }
    setFontSizeValue(size)
  }, [editor])

  if (!editor) return null

  const currentFontColor = editor.getAttributes('textStyle').color || '#FFFFFF'
  const currentHighlight = editor.getAttributes('highlight').color || ''

  return (
    <div className={styles.wrapper}>
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
      <input
        ref={mediaInputRef}
        type="file"
        accept="audio/*,video/*"
        style={{ display: 'none' }}
        onChange={handleMediaFileChange}
      />
      <div className={styles.toolbar}>
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleBold().run()}
          isActive={editor.isActive('bold')}
          icon={<BoldOutlined />}
          title="加粗 (Ctrl+B)"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleItalic().run()}
          isActive={editor.isActive('italic')}
          icon={<ItalicOutlined />}
          title="斜体 (Ctrl+I)"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleUnderline().run()}
          isActive={editor.isActive('underline')}
          icon={<UnderlineOutlined />}
          title="下划线 (Ctrl+U)"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().clearNodes().unsetAllMarks().run()}
          icon={<ClearOutlined />}
          title="清除格式"
        />

        <TiptapEmojiAt editor={editor as any} />

        <span className={styles.divider} />

        <Popover
          open={fontColorVisible}
          onOpenChange={setFontColorVisible}
          trigger="click"
          placement="bottom"
          overlayClassName={styles.colorPopover}
          content={
            <ColorPickerPanel
              currentColor={currentFontColor}
              onColorChange={handleFontColorChange}
              label="字体颜色"
            />
          }
        >
          <Tooltip title="字体颜色">
            <button type="button" className={styles.toolbarBtn}>
              <FontColorsOutlined />
              <span
                className={styles.colorIndicator}
                style={{ backgroundColor: currentFontColor }}
              />
            </button>
          </Tooltip>
        </Popover>

        <Popover
          open={highlightColorVisible}
          onOpenChange={setHighlightColorVisible}
          trigger="click"
          placement="bottom"
          overlayClassName={styles.colorPopover}
          content={
            <ColorPickerPanel
              currentColor={currentHighlight || '#FFFF00'}
              onColorChange={handleHighlightColorChange}
              label="背景高亮"
            />
          }
        >
          <Tooltip title="背景高亮">
            <button type="button" className={styles.toolbarBtn}>
              <HighlightOutlined />
              <span
                className={styles.colorIndicator}
                style={{ backgroundColor: currentHighlight || '#FFFF00' }}
              />
            </button>
          </Tooltip>
        </Popover>

        <Tooltip title="字体样式">
          <button
            type="button"
            className={styles.toolbarBtn}
            onClick={() => {
              const sel = editor.state.selection
              const mark = editor.state.doc.nodeAt(sel.from)?.marks.find(m => m.type.name === 'textStyle')
              const current = mark?.attrs.fontFamily || ''
              setFontFamilyValue(current as string)
              setShowFontPanel(!showFontPanel)
            }}
          >
            字体
          </button>
        </Tooltip>
        {showFontPanel && (
          <div className="font-panel" style={{
            position: 'absolute',
            top: 40,
            zIndex: 50,
            background: 'var(--color-bg-card, #fff)',
            border: '1px solid var(--color-border, #e8e8e8)',
            borderRadius: 8,
            padding: 8,
            boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
            maxHeight: 280,
            overflowY: 'auto',
            color: 'var(--color-text-primary, #333)',
          }}>
            {[
              { label: '默认', value: '' },
              { label: '宋体', value: 'SimSun, serif' },
              { label: '黑体', value: 'SimHei, sans-serif' },
              { label: '楷体', value: 'KaiTi, serif' },
              { label: '仿宋', value: 'FangSong, serif' },
              { label: '微软雅黑', value: 'Microsoft YaHei, sans-serif' },
              { label: 'Arial', value: 'Arial, sans-serif' },
              { label: 'Georgia', value: 'Georgia, serif' },
              { label: 'Times New Roman', value: 'Times New Roman, serif' },
              { label: 'Courier New', value: 'Courier New, monospace' },
              { label: 'Verdana', value: 'Verdana, sans-serif' },
              { label: 'Trebuchet MS', value: 'Trebuchet MS, sans-serif' },
              { label: '幼圆', value: 'YouYuan, sans-serif' },
              { label: '隶书', value: 'LiSu, serif' },
              { label: '华文细黑', value: 'STXihei, sans-serif' },
              { label: '华文中宋', value: 'STZhongsong, serif' },
              { label: '华文楷体', value: 'STKaiti, serif' },
              { label: '华文仿宋', value: 'STFangsong, serif' },
              { label: '华文隶书', value: 'STLiti, serif' },
              { label: '华文行楷', value: 'STXingkai, serif' },
              { label: '华文彩云', value: 'STCaiyun, serif' },
            ].map((font) => (
              <div
                key={font.value}
                style={{
                  padding: '6px 12px',
                  cursor: 'pointer',
                  fontFamily: font.value || 'inherit',
                  borderRadius: 4,
                  background: fontFamilyValue === font.value ? 'var(--color-accent-dim, #e6f7ff)' : 'transparent',
                }}
                onClick={() => {
                  if (font.value) {
                    editor.chain().focus().setFontFamily(font.value).run()
                  } else {
                    editor.chain().focus().unsetFontFamily().run()
                  }
                  setShowFontPanel(false)
                }}
              >
                {font.label}
              </div>
            ))}
          </div>
        )}

        <Popover
          open={fontSizeInputVisible}
          onOpenChange={setFontSizeInputVisible}
          trigger="click"
          placement="bottom"
          overlayClassName={styles.colorPopover}
          content={
            <div className={styles.fontSizeInputPanel}>
              <div className={styles.colorPickerTitle}>字号大小</div>
              <InputNumber
                min={8}
                max={96}
                value={fontSizeValue}
                onChange={handleFontSizeChange}
                addonAfter="px"
                size="small"
                className={styles.fontSizeInputNumber}
                onPressEnter={() => setFontSizeInputVisible(false)}
              />
              <div className={styles.fontSizePresets}>
                {[12, 14, 16, 18, 20, 24, 28, 32, 36, 48].map((s) => (
                  <button
                    key={s}
                    type="button"
                    className={`${styles.fontSizePresetBtn} ${fontSizeValue === s ? styles.fontSizePresetActive : ''}`}
                    onClick={() => {
                      handleFontSizeChange(s)
                      setFontSizeInputVisible(false)
                    }}
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>
          }
        >
          <Tooltip title="字号大小">
            <button type="button" className={styles.toolbarBtnWide}>
              <FontSizeOutlined />
              <span className={styles.fontSizeDisplay}>{fontSizeValue}px</span>
            </button>
          </Tooltip>
        </Popover>

        <span className={styles.divider} />

        <ToolbarButton
          onClick={() => editor.chain().focus().setTextAlign('left').run()}
          isActive={editor.isActive({ textAlign: 'left' })}
          icon={<AlignLeftOutlined />}
          title="左对齐"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().setTextAlign('center').run()}
          isActive={editor.isActive({ textAlign: 'center' })}
          icon={<AlignCenterOutlined />}
          title="居中对齐"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().setTextAlign('right').run()}
          isActive={editor.isActive({ textAlign: 'right' })}
          icon={<AlignRightOutlined />}
          title="右对齐"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().setTextAlign('justify').run()}
          isActive={editor.isActive({ textAlign: 'justify' })}
          icon={<MenuOutlined />}
          title="两端对齐"
        />

        <span className={styles.divider} />

        <ToolbarButton
          onClick={addLink}
          isActive={editor.isActive('link')}
          icon={<LinkOutlined />}
          title="插入/编辑链接"
        />
        <ToolbarButton
          onClick={handleLocalImageUpload}
          disabled={isUploading}
          icon={isUploading ? <LoadingOutlined /> : <UploadOutlined />}
          title={isUploading ? '上传中...' : '本地上传图片'}
        />
        <ToolbarButton
          onClick={addImageByUrl}
          icon={<PictureOutlined />}
          title="URL插入图片"
        />

        <span className={styles.divider} />

        <ToolbarButton
          onClick={() => setVoiceOpen(true)}
          icon={<AudioOutlined />}
          title="录制语音"
        />
        <ToolbarButton
          onClick={handleMediaUpload}
          disabled={isMediaUploading}
          icon={isMediaUploading ? <LoadingOutlined /> : <CustomerServiceOutlined />}
          title={isMediaUploading ? '上传中...' : '上传视频 / 音乐（50M以内）'}
        />
        <ToolbarButton
          onClick={addMediaByUrl}
          icon={<PlayCircleOutlined />}
          title="插入音乐 / 视频链接"
        />
        <ToolbarButton
          onClick={() => setDoodleOpen(true)}
          icon={<EditOutlined />}
          title="涂鸦"
        />
        <ToolbarButton
          onClick={() => setPollOpen(true)}
          icon={<BarChartOutlined />}
          title="发起投票"
        />
        <ToolbarButton
          onClick={() => setSurveyOpen(true)}
          icon={<SurveyOutlined />}
          title="发起问卷"
        />
      </div>
      <EditorContent editor={editor} className={styles.content} />

      <VoiceRecorderModal
        open={voiceOpen}
        onClose={() => setVoiceOpen(false)}
        onUploaded={(url) => insertUploadedMedia(url, 'audio', '语音')}
      />
      <DoodleModal
        open={doodleOpen}
        onClose={() => setDoodleOpen(false)}
        onUploaded={(url) => editor.chain().focus().setImage({ src: url, alt: '涂鸦' }).run()}
      />
      <PollConfigModal
        open={pollOpen}
        onClose={() => setPollOpen(false)}
        onSubmit={handlePollSubmit}
      />
      <SurveyConfigModal
        open={surveyOpen}
        onClose={() => setSurveyOpen(false)}
        onSubmit={handleSurveySubmit}
      />
    </div>
  )
}
