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
import Code from '@tiptap/extension-code'
import Superscript from '@tiptap/extension-superscript'
import Subscript from '@tiptap/extension-subscript'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import { useEffect, useCallback, useState } from 'react'
import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { fileApi } from '@/api/file'
import { FontSize } from './extensions/fontSize'
import { FontFamily } from './extensions/fontFamily'
import styles from './index.module.scss'

interface TiptapEditorProps {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
}

const FONT_SIZES = [12, 14, 16, 18, 20, 24, 28, 32, 36, 48]

const FONT_FAMILIES = [
  { label: '默认', value: '' },
  { label: '宋体', value: 'SimSun, STSong, serif' },
  { label: '黑体', value: 'SimHei, STHeiti, sans-serif' },
  { label: '楷体', value: 'KaiTi, STKaiti, serif' },
  { label: '仿宋', value: 'FangSong, STFangsong, serif' },
  { label: '微软雅黑', value: 'Microsoft YaHei, sans-serif' },
  { label: '幼圆', value: 'YouYuan, sans-serif' },
  { label: '隶书', value: 'LiSu, STLiti, serif' },
  { label: '华文细黑', value: 'STXihei, sans-serif' },
  { label: '华文中宋', value: 'STZhongsong, serif' },
  { label: '华文楷体', value: 'STKaiti, serif' },
  { label: '华文仿宋', value: 'STFangsong, serif' },
  { label: '华文隶书', value: 'STLiti, serif' },
  { label: '华文行楷', value: 'STXingkai, serif' },
  { label: '华文彩云', value: 'STCaiyun, serif' },
  { label: 'Verdana', value: 'Verdana, sans-serif' },
  { label: 'Trebuchet MS', value: 'Trebuchet MS, sans-serif' },
  { label: '等宽', value: 'Menlo, Monaco, Consolas, monospace' },
]

function ToolbarBtn({ onClick, isActive, children, disabled }: {
  onClick: () => void
  isActive?: boolean
  children: React.ReactNode
  disabled?: boolean
}) {
  return (
    <View
      className={`${styles.toolbarBtn} ${isActive ? styles.toolbarBtnActive : ''} ${disabled ? styles.toolbarBtnDisabled : ''}`}
      onClick={disabled ? undefined : onClick}
    >
      <Text className={styles.toolbarBtnText}>{children}</Text>
    </View>
  )
}

export default function TiptapEditor({ value, onChange, placeholder }: TiptapEditorProps) {
  const [showToolbar, setShowToolbar] = useState(false)
  const [showFontSize, setShowFontSize] = useState(false)
  const [showFontFamily, setShowFontFamily] = useState(false)
  const [showColorPicker, setShowColorPicker] = useState(false)
  const [showHighlightPicker, setShowHighlightPicker] = useState(false)
  const [linkUrl, setLinkUrl] = useState('')
  const [showLinkInput, setShowLinkInput] = useState(false)
  const [imageUrl, setImageUrl] = useState('')
  const [showImageUrlInput, setShowImageUrlInput] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [colorHex, setColorHex] = useState('#000000')
  const [highlightHex, setHighlightHex] = useState('#FFFF00')

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3, 4] },
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
      Code,
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

  const handleImageUpload = useCallback(async () => {
    if (!editor) return
    try {
      const res = await Taro.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
      })
      const tempFilePath = res.tempFilePaths[0]
      setIsUploading(true)
      try {
        const uploadRes = await fileApi.upload(tempFilePath)
        const url = uploadRes.data?.data?.url
        if (url) {
          editor.chain().focus().setImage({ src: url }).run()
        } else {
          editor.chain().focus().setImage({ src: tempFilePath }).run()
        }
      } catch {
        editor.chain().focus().setImage({ src: tempFilePath }).run()
      } finally {
        setIsUploading(false)
      }
    } catch {
      // User cancelled
    }
  }, [editor])

  const handleAddLink = useCallback(() => {
    if (!editor) return
    const existingHref = editor.getAttributes('link').href ?? ''
    setLinkUrl(existingHref)
    setShowLinkInput(true)
  }, [editor])

  const confirmLink = useCallback(() => {
    if (!editor) return
    if (linkUrl) {
      editor.chain().focus().setLink({ href: linkUrl }).run()
    } else {
      editor.chain().focus().unsetLink().run()
    }
    setShowLinkInput(false)
    setLinkUrl('')
  }, [editor, linkUrl])

  const handleAddImageUrl = useCallback(() => {
    if (!editor) return
    setImageUrl('')
    setShowImageUrlInput(true)
  }, [editor])

  const confirmImageUrl = useCallback(() => {
    if (!editor) return
    if (imageUrl) {
      editor.chain().focus().setImage({ src: imageUrl }).run()
    }
    setShowImageUrlInput(false)
    setImageUrl('')
  }, [editor, imageUrl])

  const handleDeleteTable = useCallback(() => {
    if (!editor) return
    Taro.showModal({
      title: '删除表格',
      content: '确定要删除整个表格吗？此操作不可撤销。',
      confirmText: '确认删除',
      cancelText: '取消',
      success: (modalRes) => {
        if (modalRes.confirm) {
          editor.chain().focus().deleteTable().run()
        }
      },
    })
  }, [editor])

  const handleFontSizeChange = useCallback((size: number) => {
    if (!editor) return
    if (size === 14) {
      editor.chain().focus().unsetFontSize().run()
    } else {
      editor.chain().focus().setFontSize(`${size}px`).run()
    }
    setShowFontSize(false)
  }, [editor])

  const handleFontFamilyChange = useCallback((family: string) => {
    if (!editor) return
    if (!family) {
      editor.chain().focus().unsetFontFamily().run()
    } else {
      editor.chain().focus().setFontFamily(family).run()
    }
    setShowFontFamily(false)
  }, [editor])

  const handleColorChange = useCallback((color: string) => {
    if (!editor) return
    editor.chain().focus().setColor(color).run()
    setShowColorPicker(false)
  }, [editor])

  const handleHighlightChange = useCallback((color: string) => {
    if (!editor) return
    editor.chain().focus().toggleHighlight({ color }).run()
    setShowHighlightPicker(false)
  }, [editor])

  const COLOR_PRESETS = ['#000000', '#FF4D6A', '#F53F3F', '#FF7D00', '#FADB14', '#00B42A', '#3491FA', '#722ED1']
  const HIGHLIGHT_PRESETS = ['#FFFF00', '#FF9800', '#FF4D6A', '#00B42A', '#3491FA', '#722ED1', '#E5E6EB', '#FFFFFF']

  if (!editor) return null

  return (
    <View className={styles.wrapper}>
      {/* Toggle toolbar button */}
      <View className={styles.toolbarToggle} onClick={() => setShowToolbar(!showToolbar)}>
        <Text>{showToolbar ? '⌨️ 收起工具栏' : '⌨️ 展开工具栏'}</Text>
      </View>

      {showToolbar && (
        <View className={styles.toolbar}>
          {/* Text formatting */}
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleBold().run()}
            isActive={editor.isActive('bold')}
          >B</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleItalic().run()}
            isActive={editor.isActive('italic')}
          ><Text style={{ fontStyle: 'italic' }}>I</Text></ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleUnderline().run()}
            isActive={editor.isActive('underline')}
          ><Text style={{ textDecoration: 'underline' }}>U</Text></ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleStrike().run()}
            isActive={editor.isActive('strike')}
          ><Text style={{ textDecoration: 'line-through' }}>S</Text></ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleCode().run()}
            isActive={editor.isActive('code')}
          >{'</>'}</ToolbarBtn>

          <View className={styles.divider} />

          {/* Headings */}
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
            isActive={editor.isActive('heading', { level: 1 })}
          >H1</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
            isActive={editor.isActive('heading', { level: 2 })}
          >H2</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
            isActive={editor.isActive('heading', { level: 3 })}
          >H3</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleHeading({ level: 4 }).run()}
            isActive={editor.isActive('heading', { level: 4 })}
          >H4</ToolbarBtn>

          <View className={styles.divider} />

          {/* Lists */}
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleBulletList().run()}
            isActive={editor.isActive('bulletList')}
          >• 列表</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
            isActive={editor.isActive('orderedList')}
          >1. 列表</ToolbarBtn>

          <View className={styles.divider} />

          {/* Alignment */}
          <ToolbarBtn
            onClick={() => editor.chain().focus().setTextAlign('left').run()}
            isActive={editor.isActive({ textAlign: 'left' })}
          >⬅</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().setTextAlign('center').run()}
            isActive={editor.isActive({ textAlign: 'center' })}
          >⬛</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().setTextAlign('right').run()}
            isActive={editor.isActive({ textAlign: 'right' })}
          >➡</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().setTextAlign('justify').run()}
            isActive={editor.isActive({ textAlign: 'justify' })}
          >☰</ToolbarBtn>

          <View className={styles.divider} />

          {/* Font size */}
          <ToolbarBtn onClick={() => { setShowFontSize(!showFontSize); setShowFontFamily(false); setShowColorPicker(false); setShowHighlightPicker(false) }}>
            字号
          </ToolbarBtn>

          {/* Font family */}
          <ToolbarBtn onClick={() => { setShowFontFamily(!showFontFamily); setShowFontSize(false); setShowColorPicker(false); setShowHighlightPicker(false) }}>
            字体
          </ToolbarBtn>

          {/* Font color */}
          <ToolbarBtn onClick={() => { setShowColorPicker(!showColorPicker); setShowFontSize(false); setShowFontFamily(false); setShowHighlightPicker(false) }}>
            🎨 颜色
          </ToolbarBtn>

          {/* Highlight */}
          <ToolbarBtn onClick={() => { setShowHighlightPicker(!showHighlightPicker); setShowFontSize(false); setShowFontFamily(false); setShowColorPicker(false) }}>
            🖍️ 高亮
          </ToolbarBtn>

          <View className={styles.divider} />

          {/* Insert */}
          <ToolbarBtn onClick={handleImageUpload} disabled={isUploading}>
            {isUploading ? '⏳' : '🖼️ 图片'}
          </ToolbarBtn>
          <ToolbarBtn onClick={handleAddImageUrl}>🌐 图链</ToolbarBtn>
          <ToolbarBtn onClick={handleAddLink}>🔗 链接</ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleBlockquote().run()}
            isActive={editor.isActive('blockquote')}
          >
            ❝ 引用
          </ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().toggleCodeBlock().run()}
            isActive={editor.isActive('codeBlock')}
          >
            {'</> 代码块'}
          </ToolbarBtn>
          <ToolbarBtn
            onClick={() => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()}
          >
            ⊞ 表格
          </ToolbarBtn>
          <ToolbarBtn onClick={() => editor.chain().focus().setHorizontalRule().run()}>
            ― 分割线
          </ToolbarBtn>

          <View className={styles.divider} />

          {/* Table editing - only shown when inside a table */}
          {editor.isActive('table') && (
            <>
              <ToolbarBtn onClick={() => editor.chain().focus().addColumnBefore().run()}>
                +Col←
              </ToolbarBtn>
              <ToolbarBtn onClick={() => editor.chain().focus().addColumnAfter().run()}>
                +Col→
              </ToolbarBtn>
              <ToolbarBtn onClick={() => editor.chain().focus().deleteColumn().run()}>
                -Col
              </ToolbarBtn>
              <ToolbarBtn onClick={() => editor.chain().focus().addRowBefore().run()}>
                +Row↑
              </ToolbarBtn>
              <ToolbarBtn onClick={() => editor.chain().focus().addRowAfter().run()}>
                +Row↓
              </ToolbarBtn>
              <ToolbarBtn onClick={() => editor.chain().focus().deleteRow().run()}>
                -Row
              </ToolbarBtn>
              <ToolbarBtn onClick={() => editor.chain().focus().mergeCells().run()}>
                ⇄ 合并
              </ToolbarBtn>
              <ToolbarBtn onClick={() => editor.chain().focus().splitCell().run()}>
                ⇆ 拆分
              </ToolbarBtn>
              <ToolbarBtn onClick={handleDeleteTable}>
                ✖ 删表
              </ToolbarBtn>
              <View className={styles.divider} />
            </>
          )}

          {/* Clear / Undo / Redo */}
          <ToolbarBtn onClick={() => editor.chain().focus().clearNodes().unsetAllMarks().run()}>
            🧹 清除
          </ToolbarBtn>
          <ToolbarBtn onClick={() => editor.chain().focus().undo().run()} disabled={!editor.can().undo()}>
            ↩ 撤销
          </ToolbarBtn>
          <ToolbarBtn onClick={() => editor.chain().focus().redo().run()} disabled={!editor.can().redo()}>
            ↪ 重做
          </ToolbarBtn>
        </View>
      )}

      {/* Font size panel */}
      {showFontSize && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>字号大小</Text>
          <View className={styles.fontSizeGrid}>
            {FONT_SIZES.map((size) => (
              <View key={size} className={styles.fontSizeBtn} onClick={() => handleFontSizeChange(size)}>
                <Text style={{ fontSize: `${Math.min(size, 24)}px` }}>{size}px</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      {/* Font family panel */}
      {showFontFamily && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>字体</Text>
          <View className={styles.fontFamilyGrid}>
            {FONT_FAMILIES.map((font) => (
              <View key={font.value || 'default'} className={styles.fontFamilyBtn} onClick={() => handleFontFamilyChange(font.value)}>
                <Text style={{ fontFamily: font.value || 'inherit' }}>{font.label}</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      {/* Color picker panel */}
      {showColorPicker && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>字体颜色</Text>
          <View className={styles.colorPickerCustom}>
            <input
              type="color"
              value={colorHex}
              onChange={(e) => setColorHex(e.target.value)}
              onInput={(e) => setColorHex((e.target as HTMLInputElement).value)}
              className={styles.nativeColorInputLarge}
            />
            <input
              type="text"
              value={colorHex}
              onChange={(e) => setColorHex(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') handleColorChange(colorHex) }}
              placeholder="#000000"
              className={styles.colorHexInput}
            />
          </View>
          <View className={styles.colorPresets}>
            {COLOR_PRESETS.map((color) => (
              <View
                key={color}
                className={styles.colorPresetBtn}
                style={{ backgroundColor: color, border: color === '#FFFFFF' ? '1px solid #E5E6EB' : 'none' }}
                onClick={() => { setColorHex(color); handleColorChange(color) }}
              />
            ))}
          </View>
        </View>
      )}

      {/* Highlight picker panel */}
      {showHighlightPicker && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>背景高亮</Text>
          <View className={styles.colorPickerCustom}>
            <input
              type="color"
              value={highlightHex}
              onChange={(e) => setHighlightHex(e.target.value)}
              onInput={(e) => setHighlightHex((e.target as HTMLInputElement).value)}
              className={styles.nativeColorInputLarge}
            />
            <input
              type="text"
              value={highlightHex}
              onChange={(e) => setHighlightHex(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') handleHighlightChange(highlightHex) }}
              placeholder="#FFFF00"
              className={styles.colorHexInput}
            />
          </View>
          <View className={styles.colorPresets}>
            {HIGHLIGHT_PRESETS.map((color) => (
              <View
                key={color}
                className={styles.colorPresetBtn}
                style={{ backgroundColor: color, border: color === '#FFFFFF' || color === '#E5E6EB' ? '1px solid #E5E6EB' : 'none' }}
                onClick={() => { setHighlightHex(color); handleHighlightChange(color) }}
              />
            ))}
          </View>
          <View className={styles.panelRow}>
            <View className={styles.removeHighlightBtn} onClick={() => { editor.chain().focus().unsetHighlight().run(); setShowHighlightPicker(false) }}>
              <Text>取消高亮</Text>
            </View>
          </View>
        </View>
      )}

      {/* Link input panel */}
      {showLinkInput && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>插入链接</Text>
          <View className={styles.linkInputRow}>
            <input
              className={styles.linkInput}
              placeholder="请输入链接URL"
              value={linkUrl}
              onChange={(e) => setLinkUrl(e.target.value)}
            />
            <View className={styles.linkConfirmBtn} onClick={confirmLink}>
              <Text className={styles.linkConfirmText}>确定</Text>
            </View>
          </View>
          <View className={styles.panelRow}>
            <View className={styles.removeHighlightBtn} onClick={() => { editor.chain().focus().unsetLink().run(); setShowLinkInput(false) }}>
              <Text>移除链接</Text>
            </View>
          </View>
        </View>
      )}

      {/* Image URL input panel */}
      {showImageUrlInput && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>插入图片链接</Text>
          <View className={styles.linkInputRow}>
            <input
              className={styles.linkInput}
              placeholder="请输入图片URL"
              value={imageUrl}
              onChange={(e) => setImageUrl(e.target.value)}
            />
            <View className={styles.linkConfirmBtn} onClick={confirmImageUrl}>
              <Text className={styles.linkConfirmText}>确定</Text>
            </View>
          </View>
        </View>
      )}

      {/* Editor content */}
      <EditorContent editor={editor} className={styles.content} />
    </View>
  )
}
