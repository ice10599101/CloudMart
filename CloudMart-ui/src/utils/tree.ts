export interface TreeSelectNode {
  title: string
  value: number
  key: number
  children?: TreeSelectNode[]
}

export function convertToTreeSelect<T extends { id: number; children?: T[] }>(
  nodes: T[],
  labelKey: keyof T,
): TreeSelectNode[] {
  return nodes.map((node) => ({
    title: String(node[labelKey]),
    value: node.id,
    key: node.id,
    children: node.children ? convertToTreeSelect(node.children, labelKey) : undefined,
  }))
}
