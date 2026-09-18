import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== 编辑器附件：投票 / 问卷（mall-community） ==========

export type AttachmentTargetType = 'POST' | 'WISH' | 'CAPSULE' | 'LETTER'

export interface PollData {
  id: string
  question: string
  multiple: boolean
  options: { id: number; content: string; voteCount: number }[]
  /** 参与人数（去重用户数） */
  totalVotes: number
  /** 我的选中选项（未登录/未投为空数组） */
  myOptionIds: number[]
}

export interface SurveyQuestionData {
  id: number
  text: string
  type: 'SINGLE' | 'MULTI' | 'TEXT'
  options: string[]
  required: boolean
  /** 选择题：各选项票数（与 options 按下标对齐） */
  optionCounts: number[]
  /** 填空题：有效答卷数 */
  textAnswerCount: number
}

export interface SurveyData {
  id: string
  title: string
  questions: SurveyQuestionData[]
  /** 我的答案（questionId → 选项下标数组或填空文本） */
  myAnswers: { questionId: number; optionIds: number[]; text: string | null }[]
  responseCount: number
}

/** 创建投票（幂等：客户端 UUID 为主键） */
export function createPoll(data: {
  id: string
  targetType: AttachmentTargetType
  targetId: string
  question: string
  multiple: boolean
  options: string[]
}) {
  return request.post<ApiResponse<PollData>>('/community/polls', data)
}

export function getPoll(pollId: string) {
  return request.get<ApiResponse<PollData>>(`/community/polls/${pollId}`)
}

export function votePoll(pollId: string, optionIds: number[]) {
  return request.post<ApiResponse<void>>(`/community/polls/${pollId}/vote`, { optionIds })
}

/** 创建问卷（幂等） */
export function createSurvey(data: {
  id: string
  targetType: AttachmentTargetType
  targetId: string
  title: string
  questions: { text: string; type: 'single' | 'multi' | 'text'; options: string[]; required: boolean }[]
}) {
  return request.post<ApiResponse<SurveyData>>('/community/surveys', data)
}

export function getSurvey(surveyId: string) {
  return request.get<ApiResponse<SurveyData>>(`/community/surveys/${surveyId}`)
}

/** 提交答卷（重复提交覆盖更新；选择题选项为下标） */
export function submitSurveyResponse(
  surveyId: string,
  answers: { questionId: number; optionIds?: number[]; text?: string }[],
) {
  return request.post<ApiResponse<void>>(`/community/surveys/${surveyId}/responses`, { answers })
}
