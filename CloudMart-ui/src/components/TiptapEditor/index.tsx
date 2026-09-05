import { useEditor, EditorContent } from '@tiptap/react'
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
  StrikethroughOutlined,
  OrderedListOutlined,
  UnorderedListOutlined,
  AlignLeftOutlined,
  AlignCenterOutlined,
  AlignRightOutlined,
  MenuOutlined,
  LinkOutlined,
  PictureOutlined,
  HighlightOutlined,
  UndoOutlined,
  RedoOutlined,
  FontSizeOutlined,
  ClearOutlined,
  TableOutlined,
  VerticalAlignTopOutlined,
  VerticalAlignBottomOutlined,
  CodeOutlined,
  MessageOutlined,
  MinusOutlined,
  FontColorsOutlined,
  BugOutlined,
  UploadOutlined,
  LoadingOutlined,
} from '@ant-design/icons'
import { Tooltip, Input, Modal, Popover, InputNumber } from 'antd'
import { message } from '@/utils/appMessage'
import { uploadFile } from '@/api/file'
import { FontSize } from './extensions/fontSize'
import { FontFamily } from './extensions/fontFamily'
import styles from './style.module.css'

interface TiptapEditorProps {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
}

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

export default function TiptapEditor({ value, onChange, placeholder }: TiptapEditorProps) {
  const [fontSizeValue, setFontSizeValue] = useState<number>(14)
  const [fontSizeInputVisible, setFontSizeInputVisible] = useState(false)
  const [fontColorVisible, setFontColorVisible] = useState(false)
  const [highlightColorVisible, setHighlightColorVisible] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [showFontPanel, setShowFontPanel] = useState(false)
  const [fontFamilyValue, setFontFamilyValue] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)
  const linkInputRef = useRef<string>('')

  const editor = useEditor({
    extensions: [
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
          onClick={() => editor.chain().focus().toggleStrike().run()}
          isActive={editor.isActive('strike')}
          icon={<StrikethroughOutlined />}
          title="删除线"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleSuperscript().run()}
          isActive={editor.isActive('superscript')}
          icon={<VerticalAlignTopOutlined />}
          title="上标"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleSubscript().run()}
          isActive={editor.isActive('subscript')}
          icon={<VerticalAlignBottomOutlined />}
          title="下标"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleCode().run()}
          isActive={editor.isActive('code')}
          icon={<CodeOutlined />}
          title="行内代码"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().clearNodes().unsetAllMarks().run()}
          icon={<ClearOutlined />}
          title="清除格式"
        />

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
          onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
          isActive={editor.isActive('heading', { level: 1 })}
          icon={<span className={styles.headingLabel}>H1</span>}
          title="标题1"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          isActive={editor.isActive('heading', { level: 2 })}
          icon={<span className={styles.headingLabel}>H2</span>}
          title="标题2"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
          isActive={editor.isActive('heading', { level: 3 })}
          icon={<span className={styles.headingLabel}>H3</span>}
          title="标题3"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleHeading({ level: 4 }).run()}
          isActive={editor.isActive('heading', { level: 4 })}
          icon={<span className={styles.headingLabel}>H4</span>}
          title="标题4"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().setParagraph().run()}
          isActive={editor.isActive('paragraph') && !editor.isActive('heading')}
          icon={<span className={styles.headingLabel}>P</span>}
          title="正文段落"
        />

        <span className={styles.divider} />

        <ToolbarButton
          onClick={() => editor.chain().focus().toggleBulletList().run()}
          isActive={editor.isActive('bulletList')}
          icon={<UnorderedListOutlined />}
          title="无序列表"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
          isActive={editor.isActive('orderedList')}
          icon={<OrderedListOutlined />}
          title="有序列表"
        />

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
        <ToolbarButton
          onClick={() => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()}
          icon={<TableOutlined />}
          title="插入表格"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleBlockquote().run()}
          isActive={editor.isActive('blockquote')}
          icon={<MessageOutlined />}
          title="引用块"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().toggleCodeBlock().run()}
          isActive={editor.isActive('codeBlock')}
          icon={<BugOutlined />}
          title="代码块"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().setHorizontalRule().run()}
          icon={<MinusOutlined />}
          title="分割线"
        />

        <span className={styles.divider} />

        {editor.isActive('table') && (
          <>
            <ToolbarButton
              onClick={() => editor.chain().focus().addColumnBefore().run()}
              icon={<span className={styles.headingLabel}>+Col←</span>}
              title="左侧插入列"
            />
            <ToolbarButton
              onClick={() => editor.chain().focus().addColumnAfter().run()}
              icon={<span className={styles.headingLabel}>+Col→</span>}
              title="右侧插入列"
            />
            <ToolbarButton
              onClick={() => editor.chain().focus().deleteColumn().run()}
              icon={<span className={styles.headingLabel}>-Col</span>}
              title="删除列"
            />
            <ToolbarButton
              onClick={() => editor.chain().focus().addRowBefore().run()}
              icon={<span className={styles.headingLabel}>+Row↑</span>}
              title="上方插入行"
            />
            <ToolbarButton
              onClick={() => editor.chain().focus().addRowAfter().run()}
              icon={<span className={styles.headingLabel}>+Row↓</span>}
              title="下方插入行"
            />
            <ToolbarButton
              onClick={() => editor.chain().focus().deleteRow().run()}
              icon={<span className={styles.headingLabel}>-Row</span>}
              title="删除行"
            />
            <ToolbarButton
              onClick={() => editor.chain().focus().mergeCells().run()}
              icon={<span className={styles.headingLabel}>Merge</span>}
              title="合并单元格"
            />
            <ToolbarButton
              onClick={() => editor.chain().focus().splitCell().run()}
              icon={<span className={styles.headingLabel}>Split</span>}
              title="拆分单元格"
            />
            <ToolbarButton
              onClick={() => {
                Modal.confirm({
                  title: '删除表格',
                  content: '确定要删除整个表格吗？此操作不可撤销。',
                  okText: '确认删除',
                  okButtonProps: { danger: true },
                  cancelText: '取消',
                  onOk: () => editor.chain().focus().deleteTable().run(),
                })
              }}
              icon={<span className={styles.headingLabel} style={{ color: 'var(--color-accent-red)' }}>DelTbl</span>}
              title="删除表格"
            />
            <span className={styles.divider} />
          </>
        )}

        <ToolbarButton
          onClick={() => editor.chain().focus().undo().run()}
          disabled={!editor.can().undo()}
          icon={<UndoOutlined />}
          title="撤销 (Ctrl+Z)"
        />
        <ToolbarButton
          onClick={() => editor.chain().focus().redo().run()}
          disabled={!editor.can().redo()}
          icon={<RedoOutlined />}
          title="重做 (Ctrl+Y)"
        />
      </div>
      <EditorContent editor={editor} className={styles.content} />
    </div>
  )
}
