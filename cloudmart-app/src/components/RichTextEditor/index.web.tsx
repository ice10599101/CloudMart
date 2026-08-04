import { useRef, useCallback, forwardRef, useImperativeHandle, useState, useEffect } from 'react'
import { View, StyleSheet, TouchableOpacity, Text, ScrollView, TextInput, Platform } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { BorderRadius } from '@/constants/theme'

export interface RichTextEditorRef {
  getHTML: () => Promise<string>
  setHTML: (html: string) => void
  insertImage: (url: string) => void
}

interface RichTextEditorProps {
  placeholder?: string
  initialHTML?: string
  onChange?: (html: string) => void
  minHeight?: number
}

const FONT_SIZES = [12, 14, 16, 18, 20, 24, 28, 32, 36, 48]
const PRESET_FONT_COLORS = [
  '#000000', '#E03131', '#F08C00', '#2B8A3E',
  '#1971C2', '#6741D9', '#C2255C', '#999999',
]
const PRESET_HIGHLIGHT_COLORS = [
  '#FFE066', '#FFC9C9', '#D3F9D8', '#C3FAE8',
  '#D0EBFF', '#E5DBFF', '#FFF9DB', '#B2F2BB',
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
  { label: 'Arial', value: 'Arial' },
  { label: 'Georgia', value: 'Georgia' },
  { label: 'Courier New', value: 'Courier New' },
  { label: 'Verdana', value: 'Verdana' },
  { label: 'Trebuchet MS', value: 'Trebuchet MS' },
]

const HEX_COLOR_RE = /^#[0-9A-Fa-f]{6}$/

type PanelType = 'none' | 'size' | 'color' | 'highlight' | 'font'

type TbProps = { title: string; onPress: () => void; active?: boolean; theme: Record<string, string> }

const Tb = ({ title, onPress, active, theme }: TbProps) => (
  <TouchableOpacity
    onPress={onPress}
    style={[styles.tbBtn, { backgroundColor: active ? (theme.primaryGlow || 'rgba(0,212,255,0.15)') : 'transparent' }]}
  >
    <Text style={{ color: theme.text, fontSize: 14, fontWeight: active ? '600' : '400' }}>{title}</Text>
  </TouchableOpacity>
)

const RichTextEditorWeb = forwardRef<RichTextEditorRef, RichTextEditorProps>(
  ({ placeholder = '分享你的想法...', initialHTML = '', onChange, minHeight }, ref) => {
    const theme = useTheme()
    const editorRef = useRef<HTMLDivElement>(null)
    const [activePanel, setActivePanel] = useState<PanelType>('none')
    const [linkUrl, setLinkUrl] = useState('')
    const [linkText, setLinkText] = useState('')
    const [showLinkModal, setShowLinkModal] = useState(false)
    const [showImageModal, setShowImageModal] = useState(false)
    const [imageUrl, setImageUrl] = useState('')
    const [fontHexColor, setFontHexColor] = useState('')
    const [highlightHexColor, setHighlightHexColor] = useState('')
    const [isInTable, setIsInTable] = useState(false)

    useEffect(() => {
      if (editorRef.current && initialHTML) {
        editorRef.current.innerHTML = initialHTML
      }
    }, [initialHTML])

    const execCommand = useCallback((cmd: string, value?: string) => {
      editorRef.current?.focus()
      if (value !== undefined && value !== null) {
        document.execCommand(cmd, false, value)
      } else {
        document.execCommand(cmd, false)
      }
      if (editorRef.current) {
        onChange?.(editorRef.current.innerHTML)
      }
    }, [onChange])

    const setFontSize = useCallback((size: number) => {
      const sel = window.getSelection()
      if (!sel || sel.rangeCount === 0) {
        setActivePanel('none')
        return
      }
      const range = sel.getRangeAt(0)
      if (range.collapsed) {
        setActivePanel('none')
        return
      }
      const span = document.createElement('span')
      span.style.fontSize = `${size}px`
      range.surroundContents(span)
      if (editorRef.current) {
        onChange?.(editorRef.current.innerHTML)
      }
      setActivePanel('none')
    }, [onChange])

    const setFontFamily = useCallback((family: string) => {
      if (family) {
        execCommand('fontName', family)
      } else {
        if (editorRef.current) {
          const fonts = editorRef.current.querySelectorAll('font[face]')
          fonts.forEach((f) => f.removeAttribute('face'))
          const spans = editorRef.current.querySelectorAll('span[style*="font-family"]')
          spans.forEach((s) => {
            (s as HTMLElement).style.fontFamily = ''
          })
          onChange?.(editorRef.current.innerHTML)
        }
      }
      setActivePanel('none')
    }, [execCommand, onChange])

    const handleInput = useCallback(() => {
      if (editorRef.current) {
        onChange?.(editorRef.current.innerHTML)
      }
    }, [onChange])

    const insertLink = useCallback(() => {
      if (!linkUrl.trim()) return
      editorRef.current?.focus()
      if (linkText.trim()) {
        document.execCommand('insertHTML', false, `<a href="${linkUrl.trim()}">${linkText.trim()}</a>`)
      } else {
        document.execCommand('createLink', false, linkUrl.trim())
      }
      if (editorRef.current) {
        onChange?.(editorRef.current.innerHTML)
      }
      setShowLinkModal(false)
      setLinkUrl('')
      setLinkText('')
    }, [linkUrl, linkText, onChange])

    const insertImageFromUrl = useCallback(() => {
      if (!imageUrl.trim()) return
      editorRef.current?.focus()
      document.execCommand('insertHTML', false, `<img src="${imageUrl.trim()}" style="max-width:100%;border-radius:8px;margin:8px 0">`)
      if (editorRef.current) {
        onChange?.(editorRef.current.innerHTML)
      }
      setShowImageModal(false)
      setImageUrl('')
    }, [imageUrl, onChange])

    // 行内代码：切换包裹/解包 <code> 标签（不影响 <pre> 内的代码块）
    const toggleInlineCode = useCallback(() => {
      editorRef.current?.focus()
      const sel = window.getSelection()
      if (!sel || sel.rangeCount === 0) return
      const range = sel.getRangeAt(0)
      const anchor = sel.anchorNode
      if (!anchor) return
      const el = anchor.nodeType === Node.TEXT_NODE ? anchor.parentElement : (anchor as HTMLElement)
      const existingCode = el?.closest('code')
      if (existingCode && existingCode.parentElement?.tagName.toLowerCase() === 'pre') {
        return
      }
      if (existingCode) {
        const parent = existingCode.parentNode
        while (existingCode.firstChild) {
          parent?.insertBefore(existingCode.firstChild, existingCode)
        }
        parent?.removeChild(existingCode)
      } else {
        if (range.collapsed) return
        const code = document.createElement('code')
        try {
          range.surroundContents(code)
        } catch {
          const fragment = range.extractContents()
          code.appendChild(fragment)
          range.insertNode(code)
        }
      }
      if (editorRef.current) {
        onChange?.(editorRef.current.innerHTML)
      }
    }, [onChange])

    // 自定义十六进制颜色：实时校验，合法时立即应用
    const applyFontHexColor = useCallback((val: string) => {
      setFontHexColor(val)
      if (HEX_COLOR_RE.test(val)) {
        execCommand('foreColor', val)
      }
    }, [execCommand])

    const applyHighlightHexColor = useCallback((val: string) => {
      setHighlightHexColor(val)
      if (HEX_COLOR_RE.test(val)) {
        execCommand('hiliteColor', val)
      }
    }, [execCommand])

    // 获取当前选区所在的单元格
    const getCurrentCell = useCallback((): HTMLElement | null => {
      const sel = window.getSelection()
      if (!sel || sel.rangeCount === 0) return null
      const node = sel.anchorNode
      if (!node) return null
      const el = node.nodeType === Node.TEXT_NODE ? node.parentElement : (node as HTMLElement)
      if (!el) return null
      return el.closest('td, th')
    }, [])

    // 监听选区变化，判断光标是否在表格内
    const checkIfInTable = useCallback(() => {
      const sel = window.getSelection()
      if (!sel || sel.rangeCount === 0 || !editorRef.current) {
        setIsInTable(false)
        return
      }
      const node = sel.anchorNode
      if (!node || !editorRef.current.contains(node)) {
        setIsInTable(false)
        return
      }
      const el = node.nodeType === Node.TEXT_NODE ? node.parentElement : (node as HTMLElement)
      setIsInTable(!!el?.closest('td, th'))
    }, [])

    useEffect(() => {
      document.addEventListener('selectionchange', checkIfInTable)
      return () => document.removeEventListener('selectionchange', checkIfInTable)
    }, [checkIfInTable])

    const notifyChange = useCallback(() => {
      if (editorRef.current) {
        onChange?.(editorRef.current.innerHTML)
      }
    }, [onChange])

    // 插入 3x3 表格（含表头行）
    const insertTable = useCallback(() => {
      const html = '<table><thead><tr><th>列1</th><th>列2</th><th>列3</th></tr></thead><tbody><tr><td><br></td><td><br></td><td><br></td></tr><tr><td><br></td><td><br></td><td><br></td></tr></tbody></table><p><br></p>'
      editorRef.current?.focus()
      document.execCommand('insertHTML', false, html)
      notifyChange()
    }, [notifyChange])

    const addColumnBefore = useCallback(() => {
      const cell = getCurrentCell()
      if (!cell) return
      const row = cell.parentElement
      if (!row) return
      const cellIndex = Array.from(row.children).indexOf(cell)
      const table = cell.closest('table')
      if (!table) return
      Array.from(table.querySelectorAll('tr')).forEach((r) => {
        const refNode = r.children[cellIndex]
        const tag = refNode?.tagName.toLowerCase() === 'th' ? 'th' : 'td'
        const newCell = document.createElement(tag)
        newCell.innerHTML = '<br>'
        if (refNode) {
          r.insertBefore(newCell, refNode)
        } else {
          r.appendChild(newCell)
        }
      })
      notifyChange()
    }, [getCurrentCell, notifyChange])

    const addColumnAfter = useCallback(() => {
      const cell = getCurrentCell()
      if (!cell) return
      const row = cell.parentElement
      if (!row) return
      const cellIndex = Array.from(row.children).indexOf(cell)
      const table = cell.closest('table')
      if (!table) return
      Array.from(table.querySelectorAll('tr')).forEach((r) => {
        const refNode = r.children[cellIndex]
        const tag = refNode?.tagName.toLowerCase() === 'th' ? 'th' : 'td'
        const newCell = document.createElement(tag)
        newCell.innerHTML = '<br>'
        if (refNode?.nextSibling) {
          r.insertBefore(newCell, refNode.nextSibling)
        } else {
          r.appendChild(newCell)
        }
      })
      notifyChange()
    }, [getCurrentCell, notifyChange])

    const deleteColumn = useCallback(() => {
      const cell = getCurrentCell()
      if (!cell) return
      const row = cell.parentElement
      if (!row) return
      const cellIndex = Array.from(row.children).indexOf(cell)
      const table = cell.closest('table')
      if (!table) return
      Array.from(table.querySelectorAll('tr')).forEach((r) => {
        const target = r.children[cellIndex]
        if (target) target.remove()
      })
      notifyChange()
    }, [getCurrentCell, notifyChange])

    const addRowBefore = useCallback(() => {
      const cell = getCurrentCell()
      if (!cell) return
      const row = cell.parentElement as HTMLTableRowElement | null
      if (!row) return
      const newRow = document.createElement('tr')
      Array.from(row.children).forEach(() => {
        const newCell = document.createElement('td')
        newCell.innerHTML = '<br>'
        newRow.appendChild(newCell)
      })
      row.parentNode?.insertBefore(newRow, row)
      notifyChange()
    }, [getCurrentCell, notifyChange])

    const addRowAfter = useCallback(() => {
      const cell = getCurrentCell()
      if (!cell) return
      const row = cell.parentElement as HTMLTableRowElement | null
      if (!row) return
      const newRow = document.createElement('tr')
      Array.from(row.children).forEach(() => {
        const newCell = document.createElement('td')
        newCell.innerHTML = '<br>'
        newRow.appendChild(newCell)
      })
      if (row.nextSibling) {
        row.parentNode?.insertBefore(newRow, row.nextSibling)
      } else {
        row.parentNode?.appendChild(newRow)
      }
      notifyChange()
    }, [getCurrentCell, notifyChange])

    const deleteRow = useCallback(() => {
      const cell = getCurrentCell()
      if (!cell) return
      const row = cell.parentElement
      if (!row) return
      row.remove()
      notifyChange()
    }, [getCurrentCell, notifyChange])

    // 合并选区覆盖的连续单元格到首个单元格（累加 colspan）
    const mergeCells = useCallback(() => {
      const sel = window.getSelection()
      if (!sel || sel.rangeCount === 0) return
      const range = sel.getRangeAt(0)
      const startEl = (range.startContainer.nodeType === Node.TEXT_NODE
        ? range.startContainer.parentElement
        : range.startContainer) as HTMLElement | null
      const endEl = (range.endContainer.nodeType === Node.TEXT_NODE
        ? range.endContainer.parentElement
        : range.endContainer) as HTMLElement | null
      const startCell = startEl?.closest('td, th')
      const endCell = endEl?.closest('td, th')
      if (!startCell || !endCell || startCell === endCell) return
      const table = startCell.closest('table')
      if (!table || table !== endCell.closest('table')) return
      const allCells = Array.from(table.querySelectorAll('td, th'))
      const startIdx = allCells.indexOf(startCell)
      const endIdx = allCells.indexOf(endCell)
      if (startIdx === -1 || endIdx === -1) return
      const from = Math.min(startIdx, endIdx)
      const to = Math.max(startIdx, endIdx)
      const cellsToMerge = allCells.slice(from, to + 1)
      const target = cellsToMerge[0]
      const content = cellsToMerge
        .map((c) => c.innerHTML)
        .filter((h) => h && h !== '<br>')
        .join('<br>')
      target.innerHTML = content || '<br>'
      const totalColspan = cellsToMerge.reduce(
        (sum, c) => sum + parseInt(c.getAttribute('colspan') || '1', 10),
        0,
      )
      target.setAttribute('colspan', String(totalColspan))
      cellsToMerge.slice(1).forEach((c) => c.remove())
      notifyChange()
    }, [notifyChange])

    // 拆分单元格：还原 colspan/rowspan，并在同行/后续行补齐空单元格
    const splitCell = useCallback(() => {
      const cell = getCurrentCell()
      if (!cell) return
      const colspan = parseInt(cell.getAttribute('colspan') || '1', 10)
      const rowspan = parseInt(cell.getAttribute('rowspan') || '1', 10)
      if (colspan === 1 && rowspan === 1) return
      const row = cell.parentElement as HTMLTableRowElement | null
      const table = cell.closest('table')
      if (!row || !table) return
      const cellIndex = Array.from(row.children).indexOf(cell)
      cell.removeAttribute('colspan')
      cell.removeAttribute('rowspan')
      for (let i = 1; i < colspan; i++) {
        const newCell = document.createElement('td')
        newCell.innerHTML = '<br>'
        if (cell.nextSibling) {
          row.insertBefore(newCell, cell.nextSibling)
        } else {
          row.appendChild(newCell)
        }
      }
      const rows = Array.from(table.querySelectorAll('tr'))
      const rowIndex = rows.indexOf(row)
      for (let r = 1; r < rowspan; r++) {
        const nextRow = rows[rowIndex + r]
        if (!nextRow) continue
        const newCell = document.createElement('td')
        newCell.innerHTML = '<br>'
        const refNode = nextRow.children[cellIndex]
        if (refNode) {
          nextRow.insertBefore(newCell, refNode)
        } else {
          nextRow.appendChild(newCell)
        }
      }
      notifyChange()
    }, [getCurrentCell, notifyChange])

    const deleteTable = useCallback(() => {
      const cell = getCurrentCell()
      if (!cell) return
      const table = cell.closest('table')
      if (!table) return
      const p = document.createElement('p')
      p.innerHTML = '<br>'
      table.parentNode?.insertBefore(p, table)
      table.remove()
      notifyChange()
    }, [getCurrentCell, notifyChange])

    useImperativeHandle(ref, () => ({
      getHTML: () => {
        return Promise.resolve(editorRef.current?.innerHTML || '')
      },
      setHTML: (html: string) => {
        if (editorRef.current) {
          editorRef.current.innerHTML = html
        }
      },
      insertImage: (url: string) => {
        editorRef.current?.focus()
        document.execCommand('insertHTML', false, `<img src="${url}" style="max-width:100%;border-radius:8px;margin:8px 0">`)
        if (editorRef.current) {
          onChange?.(editorRef.current.innerHTML)
        }
      },
    }))

    const togglePanel = (panel: PanelType) => {
      setActivePanel(activePanel === panel ? 'none' : panel)
    }

    return (
      <View style={[styles.container, { backgroundColor: theme.bgInput, borderRadius: BorderRadius.md, overflow: 'hidden', minHeight }]}>
        {/* Toolbar */}
        <View style={[styles.toolbar, { backgroundColor: theme.bgContainer, borderBottomColor: theme.border }]}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <Tb theme={theme} title="B" onPress={() => execCommand('bold')} />
            <Tb theme={theme} title="I" onPress={() => execCommand('italic')} />
            <Tb theme={theme} title="U" onPress={() => execCommand('underline')} />
            <Tb theme={theme} title="S" onPress={() => execCommand('strikeThrough')} />
            <Tb theme={theme} title="X²" onPress={() => execCommand('superscript')} />
            <Tb theme={theme} title="X₂" onPress={() => execCommand('subscript')} />
            <Tb theme={theme} title="</>" onPress={toggleInlineCode} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="H1" onPress={() => execCommand('formatBlock', '<h1>')} />
            <Tb theme={theme} title="H2" onPress={() => execCommand('formatBlock', '<h2>')} />
            <Tb theme={theme} title="H3" onPress={() => execCommand('formatBlock', '<h3>')} />
            <Tb theme={theme} title="H4" onPress={() => execCommand('formatBlock', '<h4>')} />
            <Tb theme={theme} title="P" onPress={() => execCommand('formatBlock', '<p>')} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="字号" onPress={() => togglePanel('size')} active={activePanel === 'size'} />
            <Tb theme={theme} title="颜色" onPress={() => togglePanel('color')} active={activePanel === 'color'} />
            <Tb theme={theme} title="高亮" onPress={() => togglePanel('highlight')} active={activePanel === 'highlight'} />
            <Tb theme={theme} title="字体" onPress={() => togglePanel('font')} active={activePanel === 'font'} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="•≡" onPress={() => execCommand('insertUnorderedList')} />
            <Tb theme={theme} title="1." onPress={() => execCommand('insertOrderedList')} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="⫷" onPress={() => execCommand('justifyLeft')} />
            <Tb theme={theme} title="⫿" onPress={() => execCommand('justifyCenter')} />
            <Tb theme={theme} title="⫸" onPress={() => execCommand('justifyRight')} />
            <Tb theme={theme} title="≡" onPress={() => execCommand('justifyFull')} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="🔗" onPress={() => setShowLinkModal(true)} />
            <Tb theme={theme} title="🖼️" onPress={() => setShowImageModal(true)} />
            <Tb theme={theme} title="▦" onPress={insertTable} />
            <Tb theme={theme} title="❝" onPress={() => execCommand('formatBlock', '<blockquote>')} />
            <Tb theme={theme} title="⌗" onPress={() => execCommand('formatBlock', '<pre>')} />
            <Tb theme={theme} title="—" onPress={() => execCommand('insertHorizontalRule')} />
            {isInTable && (
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <View style={[styles.sep, { backgroundColor: theme.border }]} />
                <Tb theme={theme} title="+列←" onPress={addColumnBefore} />
                <Tb theme={theme} title="+列→" onPress={addColumnAfter} />
                <Tb theme={theme} title="-列" onPress={deleteColumn} />
                <Tb theme={theme} title="+行↑" onPress={addRowBefore} />
                <Tb theme={theme} title="+行↓" onPress={addRowAfter} />
                <Tb theme={theme} title="-行" onPress={deleteRow} />
                <Tb theme={theme} title="合并" onPress={mergeCells} />
                <Tb theme={theme} title="拆分" onPress={splitCell} />
                <Tb theme={theme} title="删表" onPress={deleteTable} />
              </View>
            )}
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="🚫" onPress={() => execCommand('removeFormat')} />
            <Tb theme={theme} title="↩" onPress={() => execCommand('undo')} />
            <Tb theme={theme} title="↪" onPress={() => execCommand('redo')} />
          </ScrollView>
        </View>

        {/* Font size panel */}
        {activePanel === 'size' && (
          <View style={[styles.panel, { backgroundColor: theme.bgContainer, borderColor: theme.border }]}>
            <View style={styles.sizeGrid}>
              {FONT_SIZES.map((s) => (
                <TouchableOpacity key={s} onPress={() => setFontSize(s)} style={[styles.sizeBtn, { backgroundColor: theme.bgInput, borderColor: theme.border }]}>
                  <Text style={{ color: theme.text, fontSize: 13 }}>{s}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}

        {/* Color panel */}
        {activePanel === 'color' && (
          <View style={[styles.panel, { backgroundColor: theme.bgContainer, borderColor: theme.border }]}>
            <View style={styles.swatchRow}>
              {PRESET_FONT_COLORS.map((c) => (
                <TouchableOpacity key={c} onPress={() => { execCommand('foreColor', c); setActivePanel('none') }} style={[styles.presetSwatch, { backgroundColor: c }]} />
              ))}
            </View>
            <View style={styles.hexRow}>
              <Text style={{ color: theme.text, fontSize: 12 }}>自定义:</Text>
              <TextInput
                value={fontHexColor}
                onChangeText={applyFontHexColor}
                placeholder="#FF5733"
                placeholderTextColor={theme.textTertiary}
                maxLength={7}
                autoCapitalize="none"
                autoCorrect={false}
                style={[styles.hexInput, { backgroundColor: theme.bgInput, color: theme.text, borderColor: theme.border }]}
              />
              <TouchableOpacity onPress={() => { if (HEX_COLOR_RE.test(fontHexColor)) execCommand('foreColor', fontHexColor); setActivePanel('none') }} style={[styles.applyBtn, { backgroundColor: theme.primary }]}>
                <Text style={{ color: '#fff', fontSize: 12 }}>应用</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {/* Highlight panel */}
        {activePanel === 'highlight' && (
          <View style={[styles.panel, { backgroundColor: theme.bgContainer, borderColor: theme.border }]}>
            <View style={styles.swatchRow}>
              {PRESET_HIGHLIGHT_COLORS.map((c) => (
                <TouchableOpacity key={c} onPress={() => { execCommand('hiliteColor', c); setActivePanel('none') }} style={[styles.presetSwatch, { backgroundColor: c }]} />
              ))}
            </View>
            <View style={styles.hexRow}>
              <Text style={{ color: theme.text, fontSize: 12 }}>自定义:</Text>
              <TextInput
                value={highlightHexColor}
                onChangeText={applyHighlightHexColor}
                placeholder="#FFC9C9"
                placeholderTextColor={theme.textTertiary}
                maxLength={7}
                autoCapitalize="none"
                autoCorrect={false}
                style={[styles.hexInput, { backgroundColor: theme.bgInput, color: theme.text, borderColor: theme.border }]}
              />
              <TouchableOpacity onPress={() => { if (HEX_COLOR_RE.test(highlightHexColor)) execCommand('hiliteColor', highlightHexColor); setActivePanel('none') }} style={[styles.applyBtn, { backgroundColor: theme.primary }]}>
                <Text style={{ color: '#fff', fontSize: 12 }}>应用</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => { execCommand('hiliteColor', 'transparent'); setActivePanel('none') }} style={[styles.removeHighlightBtn, { borderColor: theme.border }]}>
                <Text style={{ color: theme.text, fontSize: 11 }}>取消</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {/* Font family panel */}
        {activePanel === 'font' && (
          <View style={[styles.panel, { backgroundColor: theme.bgContainer, borderColor: theme.border, maxHeight: 200 }]}>
            <ScrollView nestedScrollEnabled>
              {FONT_FAMILIES.map((f) => (
                <TouchableOpacity key={f.value || 'default'} onPress={() => setFontFamily(f.value)} style={{ paddingVertical: 8, paddingHorizontal: 12 }}>
                  <Text style={{ color: theme.text, fontSize: 14, fontFamily: f.value || undefined }}>{f.label}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
          </View>
        )}

        {/* Link modal */}
        {showLinkModal && (
          <View style={[styles.panel, { backgroundColor: theme.bgContainer, borderColor: theme.border }]}>
            <TextInput
              placeholder="链接地址"
              value={linkUrl}
              onChangeText={setLinkUrl}
              style={{ backgroundColor: theme.bgInput, color: theme.text, borderRadius: 8, padding: 8, marginBottom: 8, borderWidth: 1, borderColor: theme.border }}
              placeholderTextColor={theme.textTertiary}
            />
            <TextInput
              placeholder="链接文字（可选）"
              value={linkText}
              onChangeText={setLinkText}
              style={{ backgroundColor: theme.bgInput, color: theme.text, borderRadius: 8, padding: 8, marginBottom: 8, borderWidth: 1, borderColor: theme.border }}
              placeholderTextColor={theme.textTertiary}
            />
            <View style={{ flexDirection: 'row', gap: 8, justifyContent: 'flex-end' }}>
              <TouchableOpacity onPress={() => { setShowLinkModal(false); setLinkUrl(''); setLinkText('') }} style={{ paddingVertical: 6, paddingHorizontal: 16, backgroundColor: theme.bgInput, borderRadius: 8 }}>
                <Text style={{ color: theme.text }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={insertLink} style={{ paddingVertical: 6, paddingHorizontal: 16, backgroundColor: theme.primary, borderRadius: 8 }}>
                <Text style={{ color: '#fff' }}>确定</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {/* Image URL modal */}
        {showImageModal && (
          <View style={[styles.panel, { backgroundColor: theme.bgContainer, borderColor: theme.border }]}>
            <TextInput
              placeholder="图片地址"
              value={imageUrl}
              onChangeText={setImageUrl}
              style={{ backgroundColor: theme.bgInput, color: theme.text, borderRadius: 8, padding: 8, marginBottom: 8, borderWidth: 1, borderColor: theme.border }}
              placeholderTextColor={theme.textTertiary}
            />
            <View style={{ flexDirection: 'row', gap: 8, justifyContent: 'flex-end' }}>
              <TouchableOpacity onPress={() => { setShowImageModal(false); setImageUrl('') }} style={{ paddingVertical: 6, paddingHorizontal: 16, backgroundColor: theme.bgInput, borderRadius: 8 }}>
                <Text style={{ color: theme.text }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={insertImageFromUrl} style={{ paddingVertical: 6, paddingHorizontal: 16, backgroundColor: theme.primary, borderRadius: 8 }}>
                <Text style={{ color: '#fff' }}>确定</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {/* ContentEditable editor */}
        <div
          ref={editorRef}
          contentEditable
          suppressContentEditableWarning
          onInput={handleInput}
          style={{
            minHeight: 300,
            padding: 12,
            outline: 'none',
            lineHeight: 1.6,
            wordWrap: 'break-word',
            color: theme.text,
            backgroundColor: theme.bgInput,
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
            fontSize: 16,
          }}
          data-placeholder={placeholder}
        />
      </View>
    )
  }
)

RichTextEditorWeb.displayName = 'RichTextEditorWeb'

// CSS for placeholder, images, tables, code etc.
if (Platform.OS === 'web' && typeof document !== 'undefined') {
  const styleId = 'rich-text-editor-placeholder'
  let styleEl = document.getElementById(styleId)
  if (!styleEl) {
    styleEl = document.createElement('style')
    styleEl.id = styleId
    styleEl.textContent = `
      [contenteditable][data-placeholder]:empty::before {
        content: attr(data-placeholder);
        opacity: 0.5;
        pointer-events: none;
      }
      [contenteditable] img { max-width: 100%; height: auto; border-radius: 8px; margin: 8px 0; }
      [contenteditable] blockquote { border-left: 3px solid currentColor; padding-left: 12px; margin: 8px 0; opacity: 0.7; }
      [contenteditable] pre { background: rgba(0,0,0,0.1); border-radius: 8px; padding: 12px; margin: 8px 0; overflow-x: auto; font-family: 'Courier New', monospace; font-size: 14px; }
      [contenteditable] a { color: #1971C2; text-decoration: underline; }
      [contenteditable] ul, [contenteditable] ol { padding-left: 24px; margin: 8px 0; }
      [contenteditable] hr { border: none; border-top: 1px solid rgba(128,128,128,0.3); margin: 12px 0; }
      [contenteditable] code { font-family: 'Courier New', monospace; background: rgba(128,128,128,0.2); padding: 2px 4px; border-radius: 4px; font-size: 0.9em; }
      [contenteditable] pre code { background: transparent; padding: 0; }
      [contenteditable] table { border-collapse: collapse; width: 100%; margin: 8px 0; }
      [contenteditable] td, [contenteditable] th { border: 1px solid rgba(128,128,128,0.5); padding: 6px 10px; min-width: 60px; vertical-align: top; }
      [contenteditable] th { background: rgba(128,128,128,0.2); font-weight: 600; text-align: left; }
    `
    document.head.appendChild(styleEl)
  }
}

const styles = StyleSheet.create({
  container: {
    height: 450,
  },
  toolbar: {
    borderBottomWidth: 1,
    paddingVertical: 4,
    paddingHorizontal: 2,
  },
  tbBtn: {
    minWidth: 36,
    height: 36,
    borderRadius: 6,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 6,
  },
  sep: {
    width: 1,
    height: 24,
    marginHorizontal: 2,
    alignSelf: 'center',
  },
  panel: {
    borderWidth: 1,
    padding: 10,
    maxHeight: 200,
  },
  sizeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
  },
  sizeBtn: {
    width: 44,
    height: 32,
    borderRadius: 6,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  swatchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 6,
  },
  presetSwatch: {
    width: 24,
    height: 24,
    borderRadius: 12,
  },
  hexRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 8,
  },
  hexInput: {
    flex: 1,
    height: 32,
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 8,
    fontSize: 13,
  },
  applyBtn: {
    height: 32,
    borderRadius: 6,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeHighlightBtn: {
    height: 32,
    borderRadius: 6,
    borderWidth: 1,
    paddingHorizontal: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
})

export { RichTextEditorWeb as RichTextEditor }
