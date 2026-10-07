import { describe, it, expect } from 'vitest'
import {
  extractPollAttachments,
  extractSurveyAttachments,
  stripAttachmentNodes,
  appendAttachmentNodes,
  generateAttachmentId,
  type PollAttachmentConfig,
} from './postAttachments'

const pollConfig = { question: '你最喜欢哪种水果？', options: ['苹果', '香蕉', '橙子'], multiple: false }
const surveyConfig = {
  title: '用户体验调研',
  questions: [{ text: '你常用的设备？', type: 'single' as const, options: ['手机', '电脑'], required: true }],
}

describe('extractPollAttachments', () => {
  it('解析 data-poll-id 与 data-poll 配置', () => {
    const html = `<p>正文</p><div data-poll data-poll-id="p1" data-poll='${JSON.stringify(pollConfig)}'></div>`
    const nodes = extractPollAttachments(html)
    expect(nodes).toHaveLength(1)
    expect(nodes[0].pollId).toBe('p1')
    expect(nodes[0].config).toEqual(pollConfig)
  })

  it('无附件正文返回空数组', () => {
    expect(extractPollAttachments('<p>纯文本</p>')).toEqual([])
    expect(extractPollAttachments('')).toEqual([])
  })

  it('同 id 重复出现去重', () => {
    const tag = `<div data-poll data-poll-id="p1" data-poll='${JSON.stringify(pollConfig)}'></div>`
    const nodes = extractPollAttachments(tag + tag)
    expect(nodes).toHaveLength(1)
  })

  it('配置 JSON 非法时 config 为 null', () => {
    const html = `<div data-poll data-poll-id="p1" data-poll='not-json'></div>`
    const nodes = extractPollAttachments(html)
    expect(nodes[0].pollId).toBe('p1')
    expect(nodes[0].config).toBeNull()
  })
})

describe('extractSurveyAttachments', () => {
  it('解析 data-survey-id 与 data-survey 配置', () => {
    const html = `<div data-survey data-survey-id="s1" data-survey='${JSON.stringify(surveyConfig)}'></div>`
    const nodes = extractSurveyAttachments(html)
    expect(nodes).toHaveLength(1)
    expect(nodes[0].surveyId).toBe('s1')
    expect(nodes[0].config).toEqual(surveyConfig)
  })

  it('data-poll 不应误匹配 data-survey 前缀', () => {
    const html = `<div data-survey data-survey-id="s1" data-survey='${JSON.stringify(surveyConfig)}'></div>`
    expect(extractPollAttachments(html)).toEqual([])
  })
})

describe('stripAttachmentNodes', () => {
  it('剥离附件节点保留正文', () => {
    const html = `<p>正文一</p><div data-poll data-poll-id="p1" data-poll='{"question":"q","options":["a","b"],"multiple":false}'></div><p>正文二</p>`
    const stripped = stripAttachmentNodes(html)
    expect(stripped).toContain('正文一')
    expect(stripped).toContain('正文二')
    expect(stripped).not.toContain('data-poll')
  })

  it('无附件正文原样返回', () => {
    const html = '<p>纯文本</p>'
    expect(stripAttachmentNodes(html)).toBe(html)
  })
})

describe('appendAttachmentNodes', () => {
  it('追加的节点可被 extract 解析回原始配置', () => {
    const html = appendAttachmentNodes(
      '<p>正文</p>',
      [{ id: 'p1', config: pollConfig }],
      [{ id: 's1', config: surveyConfig }],
    )
    const polls = extractPollAttachments(html)
    const surveys = extractSurveyAttachments(html)
    expect(polls[0].config).toEqual(pollConfig)
    expect(surveys[0].config).toEqual(surveyConfig)
    // 剥离后还原为纯正文
    expect(stripAttachmentNodes(html)).toBe('<p>正文</p>')
  })

  it('配置文本含引号/尖括号/& 时往返无损', () => {
    const tricky: PollAttachmentConfig = {
      question: "don't <stop> the \"music\" & enjoy >it<",
      options: ["rock'n'roll <b>", '"quoted" & "amp"', '普通'],
      multiple: true,
    }
    const html = appendAttachmentNodes('', [{ id: 'p9', config: tricky }], [])
    expect(extractPollAttachments(html)[0].config).toEqual(tricky)
  })
})

describe('generateAttachmentId', () => {
  it('生成合法 UUID v4 且不重复', () => {
    const a = generateAttachmentId()
    const b = generateAttachmentId()
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(b).not.toBe(a)
  })
})
