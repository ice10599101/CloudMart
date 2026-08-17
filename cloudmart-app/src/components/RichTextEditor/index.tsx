import { useRef, useCallback, forwardRef, useImperativeHandle, useState } from 'react'
import { View, StyleSheet, TouchableOpacity, Text, ScrollView, TextInput } from 'react-native'
import { WebView, WebViewMessageEvent } from 'react-native-webview'
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

const EDITOR_HTML = (theme: Record<string, string>, placeholder: string, initialHTML: string) => `
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    font-size: 16px;
    color: ${theme.text};
    background: ${theme.bgInput};
    -webkit-text-size-adjust: none;
  }
  #editor {
    min-height: 300px;
    outline: none;
    line-height: 1.6;
    word-wrap: break-word;
    padding: 12px;
  }
  #editor:empty::before {
    content: '${placeholder}';
    color: ${theme.textTertiary};
    pointer-events: none;
  }
  #editor img { max-width: 100%; height: auto; border-radius: 8px; margin: 8px 0; }
  #editor blockquote { border-left: 3px solid ${theme.primary}; padding-left: 12px; margin: 8px 0; color: ${theme.textSecondary}; }
  #editor pre { background: ${theme.bgElevated || '#1a1a2e'}; border-radius: 8px; padding: 12px; margin: 8px 0; overflow-x: auto; font-family: 'Courier New', monospace; font-size: 14px; white-space: pre; }
  #editor code { background: ${theme.bgElevated || '#1a1a2e'}; padding: 2px 6px; border-radius: 4px; font-family: 'Courier New', monospace; font-size: 14px; color: ${theme.primary}; }
  #editor a { color: ${theme.primary}; text-decoration: underline; }
  #editor h1 { font-size: 28px; font-weight: bold; margin: 8px 0; }
  #editor h2 { font-size: 24px; font-weight: bold; margin: 8px 0; }
  #editor h3 { font-size: 20px; font-weight: bold; margin: 8px 0; }
  #editor h4 { font-size: 18px; font-weight: bold; margin: 8px 0; }
  #editor ul, #editor ol { padding-left: 24px; margin: 8px 0; }
  #editor hr { border: none; border-top: 1px solid ${theme.border}; margin: 12px 0; }
  #editor table { border-collapse: collapse; width: 100%; margin: 8px 0; border: 1px solid ${theme.border}; }
  #editor td, #editor th { border: 1px solid ${theme.border}; padding: 6px 8px; min-width: 40px; vertical-align: top; }
  #editor th { background: ${theme.bgElevated || '#1a1a2e'}; font-weight: bold; text-align: left; }

  .modal-overlay {
    display: none;
    position: fixed; top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.5);
    z-index: 100;
    align-items: center;
    justify-content: center;
  }
  .modal-overlay.show { display: flex; }
  .modal-box {
    background: ${theme.bgContainer};
    border-radius: 12px;
    padding: 20px;
    width: 85%;
    max-width: 360px;
  }
  .modal-box h3 { color: ${theme.text}; margin-bottom: 12px; font-size: 16px; }
  .modal-box input {
    width: 100%;
    padding: 10px 12px;
    border: 1px solid ${theme.border};
    border-radius: 8px;
    font-size: 14px;
    color: ${theme.text};
    background: ${theme.bgInput};
    margin-bottom: 12px;
    outline: none;
  }
  .modal-box .btn-row { display: flex; gap: 8px; justify-content: flex-end; }
  .modal-box button.action { padding: 8px 20px; border: none; border-radius: 8px; font-size: 14px; cursor: pointer; }
  .modal-box .confirm { background: ${theme.primary}; color: #fff; }
  .modal-box .cancel { background: ${theme.bgInput}; color: ${theme.text}; }
</style>
</head>
<body>
<div id="editor" contenteditable="true">${initialHTML}</div>

<div class="modal-overlay" id="linkModal">
  <div class="modal-box">
    <h3>插入链接</h3>
    <input id="linkUrl" placeholder="输入链接地址" type="url">
    <input id="linkText" placeholder="链接文字（可选）">
    <div class="btn-row">
      <button class="action cancel" onclick="hideModal('linkModal')">取消</button>
      <button class="action confirm" onclick="insertLink()">确定</button>
    </div>
  </div>
</div>

<div class="modal-overlay" id="imageModal">
  <div class="modal-box">
    <h3>插入图片</h3>
    <input id="imageUrl" placeholder="输入图片地址" type="url">
    <div class="btn-row">
      <button class="action cancel" onclick="hideModal('imageModal')">取消</button>
      <button class="action confirm" onclick="insertImage()">确定</button>
    </div>
  </div>
</div>

<input type="color" id="fontColorPicker" style="position:fixed;top:-100px;left:-100px;opacity:0;width:1px;height:1px;">
<input type="color" id="highlightColorPicker" style="position:fixed;top:-100px;left:-100px;opacity:0;width:1px;height:1px;">

<script>
  var editor = document.getElementById('editor');
  var savedRange = null;
  var currentCell = null;

  editor.addEventListener('input', notifyChange);
  editor.addEventListener('keyup', saveSelection);
  editor.addEventListener('mouseup', saveSelection);
  editor.addEventListener('click', function(e) {
    var cell = null;
    if (e.target && e.target.closest) {
      cell = e.target.closest('td, th');
    }
    currentCell = cell || null;
    saveSelection();
  });

  function saveSelection() {
    var sel = window.getSelection();
    if (sel.rangeCount > 0) savedRange = sel.getRangeAt(0);
  }

  function restoreSelection() {
    if (savedRange) {
      var sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(savedRange);
    }
  }

  function notifyChange() {
    window.ReactNativeWebView.postMessage(JSON.stringify({
      type: 'change',
      html: editor.innerHTML
    }));
  }

  function showModal(id) { saveSelection(); document.getElementById(id).classList.add('show'); }
  function hideModal(id) { document.getElementById(id).classList.remove('show'); var inputs = document.getElementById(id).querySelectorAll('input'); inputs.forEach(function(i) { i.value = ''; }); }

  function insertLink() {
    var url = document.getElementById('linkUrl').value.trim();
    var text = document.getElementById('linkText').value.trim();
    if (!url) return;
    hideModal('linkModal');
    restoreSelection();
    editor.focus();
    if (text) { document.execCommand('insertHTML', false, '<a href="' + url + '">' + text + '</a>'); }
    else { document.execCommand('createLink', false, url); }
    notifyChange();
  }

  function insertImage() {
    var url = document.getElementById('imageUrl').value.trim();
    if (!url) return;
    hideModal('imageModal');
    restoreSelection();
    editor.focus();
    document.execCommand('insertHTML', false, '<img src="' + url + '" style="max-width:100%">');
    notifyChange();
  }

  document.getElementById('fontColorPicker').addEventListener('input', function(e) {
    restoreSelection();
    editor.focus();
    document.execCommand('foreColor', false, e.target.value);
    notifyChange();
    window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'colorPicked', pickerType: 'font', color: e.target.value }));
  });

  document.getElementById('highlightColorPicker').addEventListener('input', function(e) {
    restoreSelection();
    editor.focus();
    document.execCommand('hiliteColor', false, e.target.value);
    notifyChange();
    window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'colorPicked', pickerType: 'highlight', color: e.target.value }));
  });

  function wrapInlineCode() {
    restoreSelection();
    editor.focus();
    var sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    var range = sel.getRangeAt(0);
    if (range.collapsed) return;
    var node = range.startContainer;
    var checkNode = node.nodeType === 3 ? node.parentNode : node;
    if (checkNode && checkNode.closest) {
      var existing = checkNode.closest('code');
      if (existing) {
        while (existing.firstChild) {
          existing.parentNode.insertBefore(existing.firstChild, existing);
        }
        existing.parentNode.removeChild(existing);
        notifyChange();
        return;
      }
    }
    var code = document.createElement('code');
    try {
      range.surroundContents(code);
    } catch (err) {
      var contents = range.extractContents();
      code.appendChild(contents);
      range.insertNode(code);
    }
    notifyChange();
  }

  function getCellColumnIndex(cell) {
    var row = cell.parentNode;
    var current = 0;
    for (var i = 0; i < row.children.length; i++) {
      if (row.children[i] === cell) return current;
      current += parseInt(row.children[i].getAttribute('colspan') || '1', 10);
    }
    return -1;
  }

  function findCellByColumnIndex(row, colIdx) {
    var current = 0;
    for (var i = 0; i < row.children.length; i++) {
      if (current === colIdx) return row.children[i];
      current += parseInt(row.children[i].getAttribute('colspan') || '1', 10);
    }
    return null;
  }

  function insertTable(rows, cols) {
    restoreSelection();
    editor.focus();
    var html = '<table><tbody><tr>';
    for (var c = 0; c < cols; c++) {
      html += '<th>列' + (c + 1) + '</th>';
    }
    html += '</tr>';
    for (var r = 1; r < rows; r++) {
      html += '<tr>';
      for (var c = 0; c < cols; c++) {
        html += '<td>&nbsp;</td>';
      }
      html += '</tr>';
    }
    html += '</tbody></table><p><br></p>';
    document.execCommand('insertHTML', false, html);
    notifyChange();
  }

  function addColumnBefore() {
    if (!currentCell) return;
    var table = currentCell.closest('table');
    if (!table) return;
    var colIdx = getCellColumnIndex(currentCell);
    var rows = table.querySelectorAll('tr');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var cell = findCellByColumnIndex(row, colIdx);
      var newCell = document.createElement(cell && cell.tagName === 'TH' ? 'th' : 'td');
      newCell.innerHTML = '&nbsp;';
      if (cell) {
        row.insertBefore(newCell, cell);
      } else {
        row.appendChild(newCell);
      }
    }
    notifyChange();
  }

  function addColumnAfter() {
    if (!currentCell) return;
    var table = currentCell.closest('table');
    if (!table) return;
    var colIdx = getCellColumnIndex(currentCell);
    var span = parseInt(currentCell.getAttribute('colspan') || '1', 10);
    var targetCol = colIdx + span;
    var rows = table.querySelectorAll('tr');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var cell = findCellByColumnIndex(row, targetCol);
      var newCell = document.createElement(cell && cell.tagName === 'TH' ? 'th' : 'td');
      newCell.innerHTML = '&nbsp;';
      if (cell) {
        row.insertBefore(newCell, cell);
      } else {
        row.appendChild(newCell);
      }
    }
    notifyChange();
  }

  function deleteColumn() {
    if (!currentCell) return;
    var table = currentCell.closest('table');
    if (!table) return;
    var colIdx = getCellColumnIndex(currentCell);
    var rows = table.querySelectorAll('tr');
    var toRemove = [];
    for (var i = 0; i < rows.length; i++) {
      var cell = findCellByColumnIndex(rows[i], colIdx);
      if (cell) toRemove.push(cell);
    }
    for (var j = 0; j < toRemove.length; j++) {
      toRemove[j].parentNode.removeChild(toRemove[j]);
    }
    currentCell = null;
    notifyChange();
  }

  function addRowBefore() {
    if (!currentCell) return;
    var row = currentCell.parentNode;
    var tbody = row.parentNode;
    var colCount = 0;
    for (var i = 0; i < row.children.length; i++) {
      colCount += parseInt(row.children[i].getAttribute('colspan') || '1', 10);
    }
    var newRow = document.createElement('tr');
    for (var c = 0; c < colCount; c++) {
      var cell = document.createElement('td');
      cell.innerHTML = '&nbsp;';
      newRow.appendChild(cell);
    }
    tbody.insertBefore(newRow, row);
    notifyChange();
  }

  function addRowAfter() {
    if (!currentCell) return;
    var row = currentCell.parentNode;
    var tbody = row.parentNode;
    var colCount = 0;
    for (var i = 0; i < row.children.length; i++) {
      colCount += parseInt(row.children[i].getAttribute('colspan') || '1', 10);
    }
    var newRow = document.createElement('tr');
    for (var c = 0; c < colCount; c++) {
      var cell = document.createElement('td');
      cell.innerHTML = '&nbsp;';
      newRow.appendChild(cell);
    }
    if (row.nextSibling) {
      tbody.insertBefore(newRow, row.nextSibling);
    } else {
      tbody.appendChild(newRow);
    }
    notifyChange();
  }

  function deleteRow() {
    if (!currentCell) return;
    var row = currentCell.parentNode;
    row.parentNode.removeChild(row);
    currentCell = null;
    notifyChange();
  }

  function mergeCells() {
    if (!currentCell) return;
    var table = currentCell.closest('table');
    if (!table) return;
    var sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    var range = sel.getRangeAt(0);
    var allCells = table.querySelectorAll('td, th');
    var selected = [];
    for (var i = 0; i < allCells.length; i++) {
      if (range.intersectsNode(allCells[i])) selected.push(allCells[i]);
    }
    if (selected.length < 2) return;
    var rows = table.querySelectorAll('tr');
    var positions = [];
    for (var i = 0; i < selected.length; i++) {
      var cell = selected[i];
      var rowIdx = -1;
      for (var j = 0; j < rows.length; j++) {
        if (rows[j] === cell.parentNode) { rowIdx = j; break; }
      }
      positions.push({ cell: cell, rowIdx: rowIdx, colIdx: getCellColumnIndex(cell) });
    }
    var minRow = positions[0].rowIdx, maxRow = positions[0].rowIdx;
    var minCol = positions[0].colIdx, maxCol = positions[0].colIdx;
    for (var i = 1; i < positions.length; i++) {
      if (positions[i].rowIdx < minRow) minRow = positions[i].rowIdx;
      if (positions[i].rowIdx > maxRow) maxRow = positions[i].rowIdx;
      if (positions[i].colIdx < minCol) minCol = positions[i].colIdx;
      if (positions[i].colIdx > maxCol) maxCol = positions[i].colIdx;
    }
    var firstCell = null;
    for (var i = 0; i < positions.length; i++) {
      if (positions[i].rowIdx === minRow && positions[i].colIdx === minCol) {
        firstCell = positions[i].cell;
        break;
      }
    }
    if (!firstCell) return;
    for (var i = 0; i < positions.length; i++) {
      if (positions[i].cell !== firstCell) {
        firstCell.innerHTML += positions[i].cell.innerHTML;
        positions[i].cell.parentNode.removeChild(positions[i].cell);
      }
    }
    firstCell.setAttribute('colspan', maxCol - minCol + 1);
    firstCell.setAttribute('rowspan', maxRow - minRow + 1);
    currentCell = firstCell;
    notifyChange();
  }

  function splitCell() {
    if (!currentCell) return;
    var cell = currentCell;
    var colspan = parseInt(cell.getAttribute('colspan') || '1', 10);
    var rowspan = parseInt(cell.getAttribute('rowspan') || '1', 10);
    if (colspan <= 1 && rowspan <= 1) return;
    var row = cell.parentNode;
    var rows = row.parentNode.children;
    var rowIdx = -1;
    for (var i = 0; i < rows.length; i++) {
      if (rows[i] === row) { rowIdx = i; break; }
    }
    cell.removeAttribute('colspan');
    cell.removeAttribute('rowspan');
    for (var c = 1; c < colspan; c++) {
      var newCell = document.createElement(cell.tagName === 'TH' ? 'th' : 'td');
      newCell.innerHTML = '&nbsp;';
      if (cell.nextSibling) {
        row.insertBefore(newCell, cell.nextSibling);
      } else {
        row.appendChild(newCell);
      }
    }
    for (var r = 1; r < rowspan; r++) {
      var nextRow = rows[rowIdx + r];
      if (nextRow) {
        for (var c = 0; c < colspan; c++) {
          var fillCell = document.createElement('td');
          fillCell.innerHTML = '&nbsp;';
          nextRow.appendChild(fillCell);
        }
      }
    }
    notifyChange();
  }

  function deleteTable() {
    if (!currentCell) return;
    var table = currentCell.closest('table');
    if (table) {
      table.parentNode.removeChild(table);
      currentCell = null;
      notifyChange();
    }
  }

  window.addEventListener('message', function(e) {
    try {
      var data = JSON.parse(e.data);
      if (data.type === 'execCommand') {
        restoreSelection();
        editor.focus();
        if (data.value !== undefined && data.value !== null) {
          document.execCommand(data.cmd, false, data.value);
        } else {
          document.execCommand(data.cmd, false, null);
        }
        notifyChange();
      } else if (data.type === 'getHTML') {
        window.ReactNativeWebView.postMessage(JSON.stringify({
          type: 'htmlResponse',
          requestId: data.requestId,
          html: editor.innerHTML
        }));
      } else if (data.type === 'setHTML') {
        editor.innerHTML = data.html;
      } else if (data.type === 'insertImage') {
        restoreSelection();
        editor.focus();
        document.execCommand('insertHTML', false, '<img src="' + data.url + '" style="max-width:100%">');
        notifyChange();
      }
    } catch(err) {}
  });
</script>
</body>
</html>
`

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
  { label: 'Times New Roman', value: 'Times New Roman' },
  { label: 'Courier New', value: 'Courier New' },
  { label: 'Verdana', value: 'Verdana' },
  { label: 'Trebuchet MS', value: 'Trebuchet MS' },
]

type PanelType = 'none' | 'size' | 'color' | 'highlight' | 'font'

type TbProps = { title: string; onPress: () => void; active?: boolean; theme: { text: string; primaryGlow?: string } }

const Tb = ({ title, onPress, active, theme }: TbProps) => (
  <TouchableOpacity
    onPress={onPress}
    style={[styles.tbBtn, { backgroundColor: active ? (theme.primaryGlow || 'rgba(0,212,255,0.15)') : 'transparent' }]}
  >
    <Text style={{ color: theme.text, fontSize: 14, fontWeight: active ? '600' : '400' }}>{title}</Text>
  </TouchableOpacity>
)

const HEX_COLOR_REGEX = /^#[0-9A-Fa-f]{6}$/

const RichTextEditor = forwardRef<RichTextEditorRef, RichTextEditorProps>(
  ({ placeholder = '分享你的想法...', initialHTML = '', onChange, minHeight }, ref) => {
    const theme = useTheme()
    const webViewRef = useRef<WebView>(null)
    const requestIdRef = useRef(0)
    const pendingRequests = useRef<Map<number, (html: string) => void>>(new Map())
    const [activePanel, setActivePanel] = useState<PanelType>('none')
    const [fontHexColor, setFontHexColor] = useState('#000000')
    const [highlightHexColor, setHighlightHexColor] = useState('#FFFF00')
    let editorHTML = initialHTML

    const execCommand = useCallback((cmd: string, value?: string) => {
      webViewRef.current?.injectJavaScript(`
        (function() {
          var editor = document.getElementById('editor');
          editor.focus();
          document.execCommand('${cmd}', false, ${value !== undefined ? JSON.stringify(value) : 'null'});
          window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'change', html: editor.innerHTML }));
        })();
        true;
      `)
    }, [])

    const execTableOp = useCallback((fnName: string, args?: string) => {
      const argStr = args ?? ''
      webViewRef.current?.injectJavaScript(`
        (function() {
          if (typeof window.${fnName} === 'function') {
            window.${fnName}(${argStr});
          }
        })();
        true;
      `)
    }, [])

    const toggleInlineCode = useCallback(() => {
      webViewRef.current?.injectJavaScript(`
        (function() {
          if (typeof window.wrapInlineCode === 'function') {
            window.wrapInlineCode();
          }
        })();
        true;
      `)
    }, [])

    const setFontSize = useCallback((size: number) => {
      webViewRef.current?.injectJavaScript(`
        (function() {
          var editor = document.getElementById('editor');
          editor.focus();
          var sel = window.getSelection();
          if (sel.rangeCount === 0) return;
          var range = sel.getRangeAt(0);
          if (range.collapsed) return;
          var span = document.createElement('span');
          span.style.fontSize = '${size}px';
          range.surroundContents(span);
          window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'change', html: editor.innerHTML }));
        })();
        true;
      `)
      setActivePanel('none')
    }, [])

    const setFontColor = useCallback((color: string) => {
      execCommand('foreColor', color)
    }, [execCommand])

    const setHighlight = useCallback((color: string) => {
      execCommand('hiliteColor', color)
    }, [execCommand])

    const removeHighlight = useCallback(() => {
      execCommand('hiliteColor', 'transparent')
    }, [execCommand])

    const openFontColorPicker = useCallback(() => {
      webViewRef.current?.injectJavaScript("document.getElementById('fontColorPicker').click(); true;")
    }, [])

    const openHighlightColorPicker = useCallback(() => {
      webViewRef.current?.injectJavaScript("document.getElementById('highlightColorPicker').click(); true;")
    }, [])

    const applyFontHexColor = useCallback(() => {
      const hex = fontHexColor.trim()
      if (HEX_COLOR_REGEX.test(hex)) {
        setFontColor(hex)
      }
    }, [fontHexColor, setFontColor])

    const applyHighlightHexColor = useCallback(() => {
      const hex = highlightHexColor.trim()
      if (HEX_COLOR_REGEX.test(hex)) {
        setHighlight(hex)
      }
    }, [highlightHexColor, setHighlight])

    const setFontFamily = useCallback((family: string) => {
      if (!family) {
        webViewRef.current?.injectJavaScript(`
          (function() {
            var editor = document.getElementById('editor');
            editor.focus();
            var fonts = editor.querySelectorAll('font[face]');
            fonts.forEach(function(f) { f.removeAttribute('face'); });
            var spans = editor.querySelectorAll('span[style*="font-family"]');
            spans.forEach(function(s) { s.style.fontFamily = ''; });
            window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'change', html: editor.innerHTML }));
          })();
          true;
        `)
      } else {
        execCommand('fontName', family)
      }
      setActivePanel('none')
    }, [execCommand])

    useImperativeHandle(ref, () => ({
      getHTML: () => {
        return new Promise<string>((resolve) => {
          const id = ++requestIdRef.current
          pendingRequests.current.set(id, resolve)
          webViewRef.current?.injectJavaScript(`
            window.ReactNativeWebView.postMessage(JSON.stringify({
              type: 'htmlResponse', requestId: ${id}, html: document.getElementById('editor').innerHTML
            }));
            true;
          `)
          setTimeout(() => {
            pendingRequests.current.delete(id)
            resolve(editorHTML)
          }, 500)
        })
      },
      setHTML: (html: string) => {
        webViewRef.current?.injectJavaScript(`
          document.getElementById('editor').innerHTML = ${JSON.stringify(html)};
          true;
        `)
      },
      insertImage: (url: string) => {
        webViewRef.current?.injectJavaScript(`
          (function() {
            var editor = document.getElementById('editor');
            editor.focus();
            document.execCommand('insertHTML', false, '<img src="${url}" style="max-width:100%">');
            window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'change', html: editor.innerHTML }));
          })();
          true;
        `)
      },
    }))

    const handleMessage = useCallback((event: WebViewMessageEvent) => {
      try {
        const data = JSON.parse(event.nativeEvent.data)
        if (data.type === 'change') {
          editorHTML = data.html
          onChange?.(data.html)
        } else if (data.type === 'htmlResponse') {
          const resolve = pendingRequests.current.get(data.requestId)
          if (resolve) {
            resolve(data.html)
            pendingRequests.current.delete(data.requestId)
          }
        } else if (data.type === 'colorPicked') {
          setActivePanel('none')
        }
      } catch {
        // ignore
      }
    }, [onChange])

    const togglePanel = (panel: PanelType) => {
      setActivePanel(activePanel === panel ? 'none' : panel)
    }

    const html = EDITOR_HTML(
      {
        text: theme.text,
        textSecondary: theme.textSecondary,
        textTertiary: theme.textTertiary,
        bgInput: theme.bgInput,
        bgContainer: theme.bgContainer,
        bgElevated: theme.bgElevated,
        primary: theme.primary,
        border: theme.border,
      },
      placeholder,
      initialHTML,
    )

    return (
      <View style={[styles.container, { backgroundColor: theme.bgInput, borderRadius: BorderRadius.md, overflow: 'hidden', minHeight }]}>
        {/* Native Toolbar */}
        <View style={[styles.toolbar, { backgroundColor: theme.bgContainer, borderBottomColor: theme.border }]}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <Tb theme={theme} title="B" onPress={() => execCommand('bold')} />
            <Tb theme={theme} title="I" onPress={() => execCommand('italic')} />
            <Tb theme={theme} title="U" onPress={() => execCommand('underline')} />
            <Tb theme={theme} title="S" onPress={() => execCommand('strikeThrough')} />
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
            <Tb theme={theme} title="☰" onPress={() => execCommand('justifyFull')} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="🔗" onPress={() => webViewRef.current?.injectJavaScript("showModal('linkModal'); true;")} />
            <Tb theme={theme} title="🖼️" onPress={() => webViewRef.current?.injectJavaScript("showModal('imageModal'); true;")} />
            <Tb theme={theme} title="❝" onPress={() => execCommand('formatBlock', '<blockquote>')} />
            <Tb theme={theme} title="{ }" onPress={toggleInlineCode} />
            <Tb theme={theme} title="</>" onPress={() => execCommand('formatBlock', '<pre>')} />
            <Tb theme={theme} title="—" onPress={() => execCommand('insertHorizontalRule')} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="X²" onPress={() => execCommand('superscript')} />
            <Tb theme={theme} title="X₂" onPress={() => execCommand('subscript')} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="表格" onPress={() => execTableOp('insertTable', '3, 3')} />
            <Tb theme={theme} title="+列←" onPress={() => execTableOp('addColumnBefore')} />
            <Tb theme={theme} title="+列→" onPress={() => execTableOp('addColumnAfter')} />
            <Tb theme={theme} title="-列" onPress={() => execTableOp('deleteColumn')} />
            <Tb theme={theme} title="+行↑" onPress={() => execTableOp('addRowBefore')} />
            <Tb theme={theme} title="+行↓" onPress={() => execTableOp('addRowAfter')} />
            <Tb theme={theme} title="-行" onPress={() => execTableOp('deleteRow')} />
            <Tb theme={theme} title="合并" onPress={() => execTableOp('mergeCells')} />
            <Tb theme={theme} title="拆分" onPress={() => execTableOp('splitCell')} />
            <Tb theme={theme} title="删表" onPress={() => execTableOp('deleteTable')} />
            <View style={[styles.sep, { backgroundColor: theme.border }]} />
            <Tb theme={theme} title="🚫" onPress={() => execCommand('removeFormat')} />
            <Tb theme={theme} title="↩" onPress={() => execCommand('undo')} />
            <Tb theme={theme} title="↪" onPress={() => execCommand('redo')} />
          </ScrollView>
        </View>

        {/* Panel */}
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
        {activePanel === 'color' && (
          <View style={[styles.panel, { backgroundColor: theme.bgContainer, borderColor: theme.border }]}>
            <View style={styles.swatchRow}>
              {PRESET_FONT_COLORS.map((c) => (
                <TouchableOpacity key={c} onPress={() => setFontColor(c)} style={[styles.presetSwatch, { backgroundColor: c }]} />
              ))}
            </View>
            <View style={styles.hexRow}>
              <TextInput
                value={fontHexColor}
                onChangeText={setFontHexColor}
                placeholder="#RRGGBB"
                placeholderTextColor={theme.textTertiary}
                maxLength={7}
                autoCapitalize="characters"
                style={[styles.hexInput, { backgroundColor: theme.bgInput, color: theme.text, borderColor: theme.border }]}
              />
              <TouchableOpacity onPress={applyFontHexColor} style={[styles.applyBtn, { backgroundColor: theme.primary }]}>
                <Text style={styles.applyBtnText}>应用</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={openFontColorPicker} style={[styles.pickerBtn, { borderColor: theme.border }]}>
                <Text style={{ color: theme.text, fontSize: 12 }}>取色器</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
        {activePanel === 'highlight' && (
          <View style={[styles.panel, { backgroundColor: theme.bgContainer, borderColor: theme.border }]}>
            <View style={styles.swatchRow}>
              {PRESET_HIGHLIGHT_COLORS.map((c) => (
                <TouchableOpacity key={c} onPress={() => setHighlight(c)} style={[styles.presetSwatch, { backgroundColor: c }]} />
              ))}
              <TouchableOpacity onPress={removeHighlight} style={[styles.removeHighlightBtn, { borderColor: theme.border }]}>
                <Text style={{ color: theme.text, fontSize: 11 }}>取消高亮</Text>
              </TouchableOpacity>
            </View>
            <View style={styles.hexRow}>
              <TextInput
                value={highlightHexColor}
                onChangeText={setHighlightHexColor}
                placeholder="#RRGGBB"
                placeholderTextColor={theme.textTertiary}
                maxLength={7}
                autoCapitalize="characters"
                style={[styles.hexInput, { backgroundColor: theme.bgInput, color: theme.text, borderColor: theme.border }]}
              />
              <TouchableOpacity onPress={applyHighlightHexColor} style={[styles.applyBtn, { backgroundColor: theme.primary }]}>
                <Text style={styles.applyBtnText}>应用</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={openHighlightColorPicker} style={[styles.pickerBtn, { borderColor: theme.border }]}>
                <Text style={{ color: theme.text, fontSize: 12 }}>取色器</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
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

        {/* WebView Editor */}
        <WebView
          ref={webViewRef}
          source={{ html }}
          style={{ backgroundColor: theme.bgInput, flex: 1 }}
          onMessage={handleMessage}
          scrollEnabled
          nestedScrollEnabled
          keyboardDisplayRequiresUserAction={false}
          startInLoadingState={false}
          automaticallyAdjustContentInsets={false}
          contentInsetAdjustmentBehavior="never"
          originWhitelist={['*']}
          javaScriptEnabled
          domStorageEnabled={false}
          cacheEnabled={false}
          incognito={true}
        />
      </View>
    )
  }
)

RichTextEditor.displayName = 'RichTextEditor'

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
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 6,
    marginBottom: 8,
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
  applyBtnText: {
    color: '#fff',
    fontSize: 12,
  },
  pickerBtn: {
    height: 32,
    borderRadius: 6,
    borderWidth: 1,
    paddingHorizontal: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeHighlightBtn: {
    height: 24,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
})

export { RichTextEditor }
