import { describe, it, expect } from 'vitest'
import { serializePollConfig, parsePollConfig } from '../extensions/pollNode'
import { serializeSurveyConfig, parseSurveyConfig } from '../extensions/surveyNode'

/**
 * 编辑器附件节点纯函数单测：配置序列化/反序列化的往返一致性与脏数据防御。
 */

describe('pollNode config', () => {
  it('round-trips a valid poll config', () => {
    const config = { question: '周末去哪？', options: ['爬山', '看电影'], multiple: true }
    expect(parsePollConfig(serializePollConfig(config))).toEqual(config)
  })

  it('returns null for malformed JSON', () => {
    expect(parsePollConfig('{not json')).toBeNull()
    expect(parsePollConfig(null)).toBeNull()
    expect(parsePollConfig(undefined)).toBeNull()
  })

  it('returns null when question/options missing', () => {
    expect(parsePollConfig(JSON.stringify({ options: ['a'] }))).toBeNull()
    expect(parsePollConfig(JSON.stringify({ question: 'q' }))).toBeNull()
  })

  it('filters non-string options and coerces multiple to boolean', () => {
    const parsed = parsePollConfig(JSON.stringify({ question: 'q', options: ['a', 1, null], multiple: 1 }))
    expect(parsed?.options).toEqual(['a'])
    expect(parsed?.multiple).toBe(true)
  })
})

describe('surveyNode config', () => {
  it('round-trips a valid survey config', () => {
    const config = {
      title: '满意度调查',
      questions: [
        { text: '你喜欢吗？', type: 'single' as const, options: ['喜欢', '一般'], required: true },
        { text: '建议', type: 'text' as const, options: [], required: false },
      ],
    }
    expect(parseSurveyConfig(serializeSurveyConfig(config))).toEqual(config)
  })

  it('returns null for malformed input', () => {
    expect(parseSurveyConfig('nope')).toBeNull()
    expect(parseSurveyConfig(JSON.stringify({ title: 't' }))).toBeNull()
  })

  it('drops invalid questions and normalizes options', () => {
    const parsed = parseSurveyConfig(JSON.stringify({
      title: 't',
      questions: [
        { text: 'q1', type: 'single', options: ['a', 42], required: true },
        { text: 123, type: 'text', options: [] },
        { text: 'q3', type: 'unknown', options: [] },
      ],
    }))
    expect(parsed?.questions).toHaveLength(1)
    expect(parsed?.questions[0].options).toEqual(['a'])
  })
})
