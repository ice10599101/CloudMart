// 空模块占位符，用于小程序构建时替换 TiptapEditor 及其依赖
// 避免 Tiptap/ProseMirror 被打包进小程序（微信小程序不支持重复的 class Node 声明）
// 需要导出所有被引用的命名导出，避免 Rollup 报错

// React 组件默认导出（替换 TiptapEditor 组件）
export default function EmptyEditor() {
  return null
}

// Tiptap/ProseMirror 命名导出占位
export const Extension = function () {}
export const useEditor = () => null
export const EditorContent = function () { return null }
export const StarterKit = {}
export const Placeholder = {}
export const Image = {}
export const Link = {}
export const Highlight = {}
export const TextAlign = {}
export const Underline = {}
export const TextStyle = {}
export const Color = {}
export const Code = {}
export const Superscript = {}
export const Subscript = {}
export const Table = {}
export const TableRow = {}
export const TableCell = {}
export const TableHeader = {}
