import { Node, mergeAttributes } from '@tiptap/core'

/**
 * 问卷附件节点。
 *
 * <p>块级原子节点：插入时由配置弹窗生成（标题 + 题目列表），
 * surveyId 为客户端生成的 UUID，发布时经 /community/surveys 幂等落库
 * （主键即该 UUID），发布流程无需回写正文。编辑器内渲染静态卡片，
 * 交互渲染由 RichText 的 SurveyBlock 完成。</p>
 */

export type SurveyQuestionType = 'single' | 'multi' | 'text'

export interface SurveyQuestionConfig {
  text: string
  type: SurveyQuestionType
  options: string[]
  required: boolean
}

export interface SurveyConfig {
  title: string
  questions: SurveyQuestionConfig[]
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    surveyAttachment: {
      insertSurveyAttachment: (options: { surveyId: string; config: SurveyConfig }) => ReturnType
    }
  }
}

export function serializeSurveyConfig(config: SurveyConfig): string {
  return JSON.stringify(config)
}

export function parseSurveyConfig(raw: string | null | undefined): SurveyConfig | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<SurveyConfig>
    if (!parsed || typeof parsed.title !== 'string' || !Array.isArray(parsed.questions)) return null
    return {
      title: parsed.title,
      questions: parsed.questions
        .filter((question): question is SurveyQuestionConfig =>
          typeof question?.text === 'string' &&
          (question.type === 'single' || question.type === 'multi' || question.type === 'text'))
        .map((question) => ({
          text: question.text,
          type: question.type,
          options: Array.isArray(question.options)
            ? question.options.filter((option): option is string => typeof option === 'string')
            : [],
          required: Boolean(question.required),
        })),
    }
  } catch {
    return null
  }
}

export const SurveyAttachment = Node.create({
  name: 'surveyAttachment',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      surveyId: { default: null },
      config: { default: null },
    }
  },

  parseHTML() {
    return [
      {
        tag: 'div[data-survey]',
        getAttrs: (element) => ({
          surveyId: (element as HTMLElement).getAttribute('data-survey-id'),
          config: (element as HTMLElement).getAttribute('data-survey'),
        }),
      },
    ]
  },

  renderHTML({ node }) {
    const config = parseSurveyConfig(node.attrs.config)
    const title = config?.title ?? '问卷'
    const questionItems = (config?.questions ?? [])
      .map((question) => ['li', { class: 'survey-question-preview' }, question.text])
    return [
      'div',
      mergeAttributes({
        'data-survey': node.attrs.config ?? '',
        'data-survey-id': node.attrs.surveyId ?? '',
        class: 'attachment-survey',
      }),
      ['p', { class: 'attachment-survey-title' }, `📋 ${title}`],
      questionItems.length > 0 ? ['ul', {}, ...questionItems] : '',
    ]
  },

  addCommands() {
    return {
      insertSurveyAttachment:
        (options) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { surveyId: options.surveyId, config: serializeSurveyConfig(options.config) },
          }),
    }
  },
})
