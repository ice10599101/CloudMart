import { createPoll, createSurvey } from '@/api/attachment'
import type { AttachmentTargetType } from '@/api/attachment'

/**
 * 发布后物化正文中的投票/问卷附件。
 *
 * 编辑器插入的投票/问卷节点携带客户端 UUID（正文 data-poll-id / data-survey-id），
 * 宿主内容创建成功后调用本函数将其幂等落库（后端以 UUID 为主键去重）。
 * 物化失败不回滚宿主内容（内容已发布），仅提示用户；再次保存/编辑时自动重试。
 */

const materializedIds = new Set<string>()

/** JSON.parse 安全封装：空值/非法 JSON 返回 null（附件配置随正文走，解析失败按无附件处理） */
function safeParse<T>(raw: string | null): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export async function materializeAttachments(
  html: string,
  targetType: AttachmentTargetType,
  targetId: string | number,
): Promise<void> {
  if (!html || (!html.includes('data-poll') && !html.includes('data-survey'))) {
    return
  }
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const tasks: Promise<unknown>[] = []

  for (const node of Array.from(doc.body.querySelectorAll('[data-poll]'))) {
    const pollId = node.getAttribute('data-poll-id')
    const rawConfig = node.getAttribute('data-poll')
    if (!pollId || materializedIds.has(pollId)) continue
    const config = safeParse<{ question?: string; options?: string[]; multiple?: boolean }>(rawConfig)
    if (!config?.question || !Array.isArray(config.options) || config.options.length < 2) continue
    tasks.push(
      createPoll({
        id: pollId,
        targetType,
        targetId: String(targetId),
        question: config.question,
        multiple: Boolean(config.multiple),
        options: config.options,
      })
        .then(() => materializedIds.add(pollId)),
    )
  }

  for (const node of Array.from(doc.body.querySelectorAll('[data-survey]'))) {
    const surveyId = node.getAttribute('data-survey-id')
    const rawConfig = node.getAttribute('data-survey')
    if (!surveyId || materializedIds.has(surveyId)) continue
    const config = safeParse<{
      title?: string
      questions?: { text?: string; type?: string; options?: string[]; required?: boolean }[]
    }>(rawConfig)
    if (!config?.title || !Array.isArray(config.questions) || config.questions.length === 0) continue
    const questions = config.questions
      .filter((question) => typeof question.text === 'string' && question.text.trim())
      .map((question) => ({
        text: question.text!.trim(),
        type: (question.type === 'multi' || question.type === 'text' ? question.type : 'single') as
          | 'single'
          | 'multi'
          | 'text',
        options: Array.isArray(question.options) ? question.options : [],
        required: question.required !== false,
      }))
    if (questions.length === 0) continue
    tasks.push(
      createSurvey({
        id: surveyId,
        targetType,
        targetId: String(targetId),
        title: config.title,
        questions,
      })
        .then(() => materializedIds.add(surveyId)),
    )
  }

  if (tasks.length === 0) return
  const results = await Promise.allSettled(tasks)
  const failureCount = results.filter((result) => result.status === 'rejected').length
  if (failureCount > 0) {
    // 静默物化（宿主内容已发布）：提示但不阻塞；编辑重新保存时自动重试
    const { message } = await import('@/utils/appMessage')
    message.warning('部分投票/问卷创建失败，互动功能可能暂不可用；重新编辑保存可重试')
  }
}
