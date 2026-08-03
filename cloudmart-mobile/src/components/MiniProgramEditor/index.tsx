import { useState, useRef, useCallback } from 'react'
import { View, Text, Editor, Input } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { fileApi } from '@/api/file'
import styles from './index.module.scss'

interface MiniEditorProps {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
}

const FORMAT_GROUPS = [
  { type: 'bold', label: 'B', style: { fontWeight: '700' } },
  { type: 'italic', label: 'I', style: { fontStyle: 'italic' } },
  { type: 'underline', label: 'U', style: { textDecoration: 'underline' } },
  { type: 'strike', label: 'S', style: { textDecoration: 'line-through' } },
  { type: 'divider' },
  { type: 'h1', label: 'H1' },
  { type: 'h2', label: 'H2' },
  { type: 'h3', label: 'H3' },
  { type: 'divider' },
  { type: 'fontSize', label: '字号' },
  { type: 'fontFamily', label: '字体' },
  { type: 'color', label: '颜色' },
  { type: 'highlight', label: '高亮' },
  { type: 'divider' },
  { type: 'list', label: '• 列表' },
  { type: 'olist', label: '1. 列表' },
  { type: 'divider' },
  { type: 'alignLeft', label: '⬅' },
  { type: 'alignCenter', label: '⬛' },
  { type: 'alignRight', label: '➡' },
  { type: 'divider' },
  { type: 'indent', label: '➡ 缩进' },
  { type: 'outdent', label: '⬅ 减缩' },
  { type: 'divider' },
  { type: 'insertImage', label: '🖼️ 图片' },
  { type: 'insertQuote', label: '❝ 引用' },
  { type: 'insertHr', label: '― 分割线' },
  { type: 'divider' },
  { type: 'undo', label: '↩ 撤销' },
  { type: 'redo', label: '↪ 重做' },
  { type: 'clear', label: '🧹 清除' },
]

const FONT_FAMILIES = [
  { label: '默认', value: '' },
  { label: '宋体', value: 'SimSun' },
  { label: '黑体', value: 'SimHei' },
  { label: '楷体', value: 'KaiTi' },
  { label: '仿宋', value: 'FangSong' },
  { label: '微软雅黑', value: 'Microsoft YaHei' },
  { label: '幼圆', value: 'YouYuan' },
  { label: '隶书', value: 'LiSu' },
  { label: '华文细黑', value: 'STXihei' },
  { label: '华文中宋', value: 'STZhongsong' },
  { label: '华文楷体', value: 'STKaiti' },
  { label: '华文仿宋', value: 'STFangsong' },
  { label: '华文隶书', value: 'STLiti' },
  { label: '华文行楷', value: 'STXingkai' },
  { label: '华文彩云', value: 'STCaiyun' },
]

const FONT_SIZES = [
  { label: '12px', value: 1 },
  { label: '14px', value: 2 },
  { label: '16px', value: 3 },
  { label: '18px', value: 4 },
  { label: '20px', value: 5 },
  { label: '24px', value: 6 },
  { label: '32px', value: 7 },
]

const COLORS = [
  '#000000', '#333333', '#555555', '#666666', '#888888', '#999999', '#BBBBBB', '#FFFFFF',
  '#FF0000', '#FF3300', '#FF6600', '#FF9900', '#FFCC00', '#FFFF00', '#CCFF00', '#99FF00',
  '#00CC00', '#00FF66', '#00CCCC', '#0066FF', '#0000FF', '#6600FF', '#9900FF', '#CC00FF',
  '#FF0066', '#FF3399', '#FF69B4', '#FF1493', '#8B4513', '#A0522D', '#D2691E', '#F4A460',
  '#1E90FF', '#32CD32', '#00FA9A', '#7B68EE', '#DC143C', '#B22222', '#228B22', '#2F4F4F',
]

const HIGHLIGHT_COLORS = [
  '#FFFF00', '#FFF176', '#FFEE58', '#FFE082', '#FFD54F', '#FFCA28',
  '#00FF00', '#76FF03', '#B2FF59', '#CCFF90', '#F0FFF0', '#E8F5E9',
  '#00FFFF', '#84FFFF', '#80DEEA', '#B2EBF2', '#E0F7FA', '#E0F2F1',
  '#FF69B4', '#FF80AB', '#F48FB1', '#F8BBD0', '#FCE4EC', '#FFF0F5',
  '#FFA500', '#FFB74D', '#FFCC80', '#FFE0B2', '#FFF3E0', '#FFF8E1',
  '#FF6347', '#EF9A9A', '#FFCDD2', '#FFEBEE', '#DDA0DD', '#E1BEE7',
  '#87CEEB', '#90CAF9', '#BBDEFB', '#E3F2FD', '#98FB98', '#C8E6C9',
  '#F0E68C', '#FFF9C4', '#FFFDE7', '#E6E6FA', '#D1C4E9', '#EDE7F6',
  '#FFDAB9', '#FFE0B2', '#FFCCBC', '#FBE9E7',
]

type PanelType = 'fontSize' | 'fontFamily' | 'color' | 'highlight' | null

export default function MiniProgramEditor({ value, onChange, placeholder }: MiniEditorProps) {
  const [showToolbar, setShowToolbar] = useState(false)
  const [activePanel, setActivePanel] = useState<PanelType>(null)
  const [isUploading, setIsUploading] = useState(false)
  const [colorInput, setColorInput] = useState('')
  const [highlightInput, setHighlightInput] = useState('')
  const editorRef = useRef<any>(null)

  const isValidHex = (hex: string): boolean => /^#[0-9A-Fa-f]{6}$/.test(hex)

  const handleFormat = useCallback((type: string) => {
    const editor = editorRef.current
    if (!editor) return

    const formatMap: Record<string, [string, string | number]> = {
      bold: ['bold', ''],
      italic: ['italic', ''],
      underline: ['underline', ''],
      strike: ['strike', ''],
      h1: ['header', 1],
      h2: ['header', 2],
      h3: ['header', 3],
      list: ['list', 'bullet'],
      olist: ['list', 'ordered'],
      alignLeft: ['align', 'left'],
      alignCenter: ['align', 'center'],
      alignRight: ['align', 'right'],
      indent: ['indent', ''],
      outdent: ['outdent', ''],
      insertQuote: ['blockquote', ''],
    }

    if (formatMap[type]) {
      const [name, val] = formatMap[type]
      editor.format(name, val)
      return
    }

    switch (type) {
      case 'fontSize':
      case 'fontFamily':
      case 'color':
      case 'highlight':
        setActivePanel((prev) => (prev === type ? null : type))
        break
      case 'insertImage':
        handleImageUpload()
        break
      case 'insertHr':
        editor.insertDivider()
        break
      case 'undo':
        editor.undo()
        break
      case 'redo':
        editor.redo()
        break
      case 'clear':
        editor.clear()
        break
    }
  }, [])

  const handleImageUpload = useCallback(async () => {
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
        const url = uploadRes.data?.data?.url || tempFilePath
        editorRef.current?.insertImage(url)
      } catch {
        editorRef.current?.insertImage(tempFilePath)
      } finally {
        setIsUploading(false)
      }
    } catch {
      // User cancelled
    }
  }, [])

  const handleEditorReady = useCallback(() => {
    const editor = editorRef.current
    if (!editor) return
    if (value) {
      editor.setContents({ html: value })
    }
  }, [value])

  const handleEditorInput = useCallback((e: any) => {
    const html = e.detail?.html || e.detail?.text || ''
    onChange?.(html)
  }, [onChange])

  const handleFontSizeChange = useCallback((sizeValue: number) => {
    const editor = editorRef.current
    if (!editor) return
    editor.format('fontSize', sizeValue)
    setActivePanel(null)
  }, [])

  const handleFontFamilyChange = useCallback((family: string) => {
    const editor = editorRef.current
    if (!editor) return
    editor.format('fontFamily', family)
    setActivePanel(null)
  }, [])

  const handleColorChange = useCallback((color: string) => {
    const editor = editorRef.current
    if (!editor) return
    editor.format('color', color)
    setActivePanel(null)
  }, [])

  const handleHighlightChange = useCallback((color: string) => {
    const editor = editorRef.current
    if (!editor) return
    editor.format('backgroundColor', color)
    setActivePanel(null)
  }, [])

  const panelBtnActive = (type: string) => activePanel === type ? styles.toolbarBtnActive : ''

  return (
    <View className={styles.wrapper}>
      <View className={styles.toolbarToggle} onClick={() => setShowToolbar(!showToolbar)}>
        <Text>{showToolbar ? '⌨️ 收起工具栏' : '⌨️ 展开工具栏'}</Text>
      </View>

      {showToolbar && (
        <View className={styles.toolbar}>
          {FORMAT_GROUPS.map((item, idx) => {
            if (item.type === 'divider') {
              return <View key={idx} className={styles.divider} />
            }
            const isActive = ['fontSize', 'fontFamily', 'color', 'highlight'].includes(item.type)
            return (
              <View
                key={item.type}
                className={`${styles.toolbarBtn} ${item.type === 'insertImage' && isUploading ? styles.toolbarBtnDisabled : ''} ${isActive ? panelBtnActive(item.type) : ''}`}
                onClick={() => handleFormat(item.type)}
              >
                <Text className={styles.toolbarBtnText} style={item.style}>{item.label}</Text>
              </View>
            )
          })}
        </View>
      )}

      {activePanel === 'fontSize' && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>字号</Text>
          <View className={styles.fontSizeGrid}>
            {FONT_SIZES.map((item) => (
              <View key={item.value} className={styles.fontSizeBtn} onClick={() => handleFontSizeChange(item.value)}>
                <Text style={{ fontSize: item.label }}>{item.label}</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      {activePanel === 'fontFamily' && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>字体</Text>
          <View className={styles.fontFamilyGrid}>
            {FONT_FAMILIES.map((font) => (
              <View key={font.value} className={styles.fontFamilyBtn} onClick={() => handleFontFamilyChange(font.value)}>
                <Text style={{ fontFamily: font.value || 'inherit' }}>{font.label}</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      {activePanel === 'color' && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>字体颜色</Text>
          <View className={styles.hexInputRow}>
            <View
              className={styles.colorPreview}
              style={{ backgroundColor: isValidHex(colorInput) ? colorInput : '#000000' }}
            />
            <Input
              className={styles.hexInput}
              type="text"
              value={colorInput}
              placeholder="输入颜色值 #RRGGBB"
              onInput={(e) => setColorInput(e.detail.value)}
              onBlur={() => {
                if (isValidHex(colorInput)) {
                  handleColorChange(colorInput)
                }
              }}
              onConfirm={() => {
                if (isValidHex(colorInput)) {
                  handleColorChange(colorInput)
                }
              }}
            />
          </View>
          <View className={styles.colorGrid}>
            {COLORS.map((color) => (
              <View key={color} className={styles.colorBtn} onClick={() => handleColorChange(color)}>
                <View className={styles.colorDot} style={{ backgroundColor: color }} />
              </View>
            ))}
          </View>
        </View>
      )}

      {activePanel === 'highlight' && (
        <View className={styles.panel}>
          <Text className={styles.panelTitle}>背景高亮</Text>
          <View className={styles.hexInputRow}>
            <View
              className={styles.colorPreview}
              style={{ backgroundColor: isValidHex(highlightInput) ? highlightInput : '#FFFF00' }}
            />
            <Input
              className={styles.hexInput}
              type="text"
              value={highlightInput}
              placeholder="输入颜色值 #RRGGBB"
              onInput={(e) => setHighlightInput(e.detail.value)}
              onBlur={() => {
                if (isValidHex(highlightInput)) {
                  handleHighlightChange(highlightInput)
                }
              }}
              onConfirm={() => {
                if (isValidHex(highlightInput)) {
                  handleHighlightChange(highlightInput)
                }
              }}
            />
          </View>
          <View className={styles.colorGrid}>
            {HIGHLIGHT_COLORS.map((color) => (
              <View key={color} className={styles.colorBtn} onClick={() => handleHighlightChange(color)}>
                <View className={styles.colorDot} style={{ backgroundColor: color }} />
              </View>
            ))}
          </View>
          <View
            className={styles.removeHighlightBtn}
            onClick={() => {
              editorRef.current?.format('backgroundColor', '')
              setActivePanel(null)
            }}
          >
            <Text className={styles.removeHighlightText}>取消高亮</Text>
          </View>
        </View>
      )}

      <Editor
        className={styles.content}
        placeholder={placeholder || '请输入内容...'}
        onReady={handleEditorReady}
        onInput={handleEditorInput}
        ref={editorRef}
      />
    </View>
  )
}
