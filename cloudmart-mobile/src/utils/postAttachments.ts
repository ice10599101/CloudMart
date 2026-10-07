/**
 * P0-6 帖子投票/问卷附件的解析与嵌入。
 *
 * 契约对齐 Web 端 TiptapEditor pollAttachment/surveyAttachment 节点：
 * 正文 HTML 以 <div data-poll data-poll-id="uuid" data-poll='JSON'> /
 * <div data-survey data-survey-id="uuid" data-survey='JSON'> 携带配置快照，
 * 发布成功后按 UUID 幂等物化（POST /community/polls|surveys，主键即 UUID）。
 * 小程序无 DOMParser，统一用正则解析；属性值内不含引号（JSON 以单引号包裹，
 * 双引号经转义），因此 " 到下一个 " 的非贪婪匹配是安全的。
 */

export interface PollAttachmentConfig {
  question: string
  options: string[]
  multiple: boolean
}

export interface SurveyAttachmentQuestion {
  text: string
  type: 'single' | 'multi' | 'text'
  options: string[]
  required: boolean
}

export interface SurveyAttachmentConfig {
  title: string
  questions: SurveyAttachmentQuestion[]
}

export interface PollAttachmentNode {
  pollId: string
  config: PollAttachmentConfig | null
}

export interface SurveyAttachmentNode {
  surveyId: string
  config: SurveyAttachmentConfig | null
}

function safeParse<T>(raw: string | null | undefined): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

/** 属性值反转义（appendAttachmentNodes 写入前做了最小转义） */
function unescapeAttr(raw: string): string {
  return raw
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
}

/**
 * 从标签串中取 data-poll / data-survey 属性值。
 * 值内 JSON 含双引号，必须用与开引号同类的闭引号（回引用）匹配，
 * 而非排除双引号的字符类——否则配置永远截断解析失败。
 */
function attrValue(tag: string, name: string): string | null {
  const m = new RegExp(`${name}=(["'])([\\s\\S]*?)\\1`).exec(tag)
  return m ? unescapeAttr(m[2]) : null
}

/** 解析正文中的投票附件节点（幂等去重） */
export function extractPollAttachments(html: string): PollAttachmentNode[] {
  if (!html || !html.includes('data-poll')) return []
  const nodes: PollAttachmentNode[] = []
  const seen = new Set<string>()
  const re = /<div[^>]*data-poll-id=["']([^"']+)["'][^>]*>/g
  let match: RegExpExecArray | null
  while ((match = re.exec(html)) !== null) {
    const pollId = match[1]
    if (seen.has(pollId)) continue
    seen.add(pollId)
    // match[0] 即完整的开始标签（正则止于 >），配置在同标签的 data-poll 属性中
    const config = safeParse<PollAttachmentConfig>(attrValue(match[0], 'data-poll'))
    nodes.push({
      pollId,
      config: config && typeof config.question === 'string' && Array.isArray(config.options)
        ? { question: config.question, options: config.options, multiple: Boolean(config.multiple) }
        : null,
    })
  }
  return nodes
}

/** 解析正文中的问卷附件节点（幂等去重） */
export function extractSurveyAttachments(html: string): SurveyAttachmentNode[] {
  if (!html || !html.includes('data-survey')) return []
  const nodes: SurveyAttachmentNode[] = []
  const seen = new Set<string>()
  const re = /<div[^>]*data-survey-id=["']([^"']+)["'][^>]*>/g
  let match: RegExpExecArray | null
  while ((match = re.exec(html)) !== null) {
    const surveyId = match[1]
    if (seen.has(surveyId)) continue
    seen.add(surveyId)
    const config = safeParse<SurveyAttachmentConfig>(attrValue(match[0], 'data-survey'))
    nodes.push({
      surveyId,
      config: config && typeof config.title === 'string' && Array.isArray(config.questions)
        ? { title: config.title, questions: config.questions }
        : null,
    })
  }
  return nodes
}

/** 渲染用：从正文中剥离附件节点（交互卡片由组件按 id 拉取实时数据渲染） */
export function stripAttachmentNodes(html: string): string {
  if (!html || (!html.includes('data-poll') && !html.includes('data-survey'))) return html
  return html
    .replace(/<div[^>]*data-poll[^>]*>[\s\S]*?<\/div>/g, '')
    .replace(/<div[^>]*data-survey[^>]*>[\s\S]*?<\/div>/g, '')
}

/** 属性值最小转义（单引号包裹 JSON，需转义其中的 &、<、>、' 防止标签/属性提前闭合） */
function escapeAttr(raw: string): string {
  return raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/'/g, '&#39;')
}

/** 发布用：把投票/问卷配置以 data-poll 节点追加到正文 HTML 尾部 */
export function appendAttachmentNodes(
  html: string,
  polls: Array<{ id: string; config: PollAttachmentConfig }>,
  surveys: Array<{ id: string; config: SurveyAttachmentConfig }>,
): string {
  let result = html
  for (const poll of polls) {
    result += `<div data-poll data-poll-id="${poll.id}" data-poll='${escapeAttr(JSON.stringify(poll.config))}'></div>`
  }
  for (const survey of surveys) {
    result += `<div data-survey data-survey-id="${survey.id}" data-survey='${escapeAttr(JSON.stringify(survey.config))}'></div>`
  }
  return result
}

/** 客户端 UUID（附件幂等主键；Taro/Expo 运行时无 crypto.randomUUID 的兜底实现） */
export function generateAttachmentId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
    const rand = (Math.random() * 16) | 0
    const value = ch === 'x' ? rand : (rand & 0x3) | 0x8
    return value.toString(16)
  })
}
