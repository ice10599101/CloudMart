import { Node, mergeAttributes } from '@tiptap/core'

/**
 * 投票附件节点。
 *
 * <p>块级原子节点：插入时由配置弹窗生成（问题 + 选项 + 单/多选），
 * pollId 为客户端生成的 UUID，发布时经 /community/polls 幂等落库（主键即该 UUID），
 * 因此发布流程无需回写正文。编辑器内渲染静态卡片，交互渲染由 RichText 的
 * PollBlock 完成。</p>
 */

export interface PollConfig {
  question: string
  options: string[]
  multiple: boolean
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    pollAttachment: {
      insertPollAttachment: (options: { pollId: string; config: PollConfig }) => ReturnType
    }
  }
}

/** 序列化进 HTML 的配置 JSON（经 DOMPurify data-* 属性保留） */
export function serializePollConfig(config: PollConfig): string {
  return JSON.stringify(config)
}

export function parsePollConfig(raw: string | null | undefined): PollConfig | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<PollConfig>
    if (!parsed || typeof parsed.question !== 'string' || !Array.isArray(parsed.options)) return null
    return {
      question: parsed.question,
      options: parsed.options.filter((option): option is string => typeof option === 'string'),
      multiple: Boolean(parsed.multiple),
    }
  } catch {
    return null
  }
}

export const PollAttachment = Node.create({
  name: 'pollAttachment',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      pollId: { default: null },
      config: { default: null },
    }
  },

  parseHTML() {
    return [
      {
        tag: 'div[data-poll]',
        getAttrs: (element) => ({
          pollId: (element as HTMLElement).getAttribute('data-poll-id'),
          config: (element as HTMLElement).getAttribute('data-poll'),
        }),
      },
    ]
  },

  renderHTML({ node }) {
    const config = parsePollConfig(node.attrs.config)
    const question = config?.question ?? '投票'
    const optionItems = (config?.options ?? [])
      .map((option) => ['li', { class: 'poll-option-preview' }, option])
    return [
      'div',
      mergeAttributes({
        'data-poll': node.attrs.config ?? '',
        'data-poll-id': node.attrs.pollId ?? '',
        class: 'attachment-poll',
      }),
      ['p', { class: 'attachment-poll-question' }, `📊 ${question}`],
      optionItems.length > 0 ? ['ul', {}, ...optionItems] : '',
    ]
  },

  addCommands() {
    return {
      insertPollAttachment:
        (options) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { pollId: options.pollId, config: serializePollConfig(options.config) },
          }),
    }
  },
})
