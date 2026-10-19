import React from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getEditor: vi.fn(), save: vi.fn(), submit: vi.fn(), upload: vi.fn(),
  message: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))
vi.mock('umi', () => ({ useParams: () => ({ id: '42' }), history: { push: vi.fn() } }))
vi.mock('@ant-design/icons', () => ({ DeleteOutlined: () => null, InboxOutlined: () => null, PlusOutlined: () => null }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => true }))
vi.mock('@/components/TiptapEditor', () => ({ default: () => null }))
vi.mock('@/api/file', () => ({ uploadFileAsset: mocks.upload, deleteFileAsset: vi.fn().mockResolvedValue({}) }))
vi.mock('@/api/software', () => ({
  getSoftwareEditor: mocks.getEditor, patchSoftwareDraft: mocks.save,
  submitSoftwareForReview: mocks.submit, createWorkingRevision: vi.fn(),
  getPublisherStatus: async () => ({ data: { data: { canPublish: true, publishMode: 'REVIEW' } } }),
  listSoftwareCategories: async () => ({ data: { success: true, data: [] } }),
  listAllSoftwareTags: async () => [],
  listSoftwareAccessContacts: async () => ({ data: { success: true, data: [] } }),
}))
vi.mock('antd', () => {
  const Box = ({ children }: any) => <div>{children}</div>
  const Input = Object.assign(({ value, onChange, placeholder }: any) =>
    <input value={value ?? ''} onChange={onChange} placeholder={placeholder} />,
  { TextArea: ({ value, onChange }: any) => <textarea value={value ?? ''} onChange={onChange} /> })
  return {
    App: { useApp: () => ({ message: mocks.message }) },
    Card: Box, Space: Box, Tag: Box, Alert: Box, Spin: () => <span>loading</span>,
    Input, InputNumber: () => null, Switch: () => null,
    Button: ({ children, onClick, disabled }: any) => <button onClick={onClick} disabled={disabled}>{children}</button>,
    Select: ({ value, onChange, options = [] }: any) => <select value={Array.isArray(value) ? '' : value ?? ''}
      onChange={(e) => onChange(e.target.value)}>
      <option value="" />{options.map((o: any) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>,
    Upload: ({ children, customRequest }: any) => <div>{children}<button onClick={() => customRequest({
      file: new File(['png'], 'icon.png', { type: 'image/png' }), onSuccess: vi.fn(), onError: vi.fn(),
    })}>上传测试图标</button></div>,
  }
})

import SoftwareEdit from './SoftwareEdit'

describe('software editor saves the latest form state', () => {
  let stored: any
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    stored = { revisionId: '7', reviewStatus: 'DRAFT', name: '软件', summary: '简介',
      iconAssetId: null, descriptionHtml: '', licenseType: 'FREE', attributionType: 'ORIGINAL',
      accessMode: 'REGISTERED', releases: [], tagIds: [], languages: [], screenshots: [],
      attributionNote: null, accessNote: null, developerName: null, officialUrl: null,
      systemRequirements: null, minCommunityLevel: null, accessContactId: null }
    mocks.getEditor.mockImplementation(async () => ({ data: { success: true, data: { ...stored } } }))
    mocks.save.mockImplementation(async (_id, _revision, payload) => {
      stored = { ...stored, ...payload }
      return { data: { success: true, data: stored } }
    })
    mocks.submit.mockResolvedValue({ data: { success: true } })
    mocks.upload.mockResolvedValue({ data: { success: true, data: { fileId: '9007199254740993' } } })
  })
  afterEach(cleanup)

  it.each(['保存', '提交审核'])('uploads only an icon, then %s with the new asset and restores it on reopen', async (action) => {
    const view = render(<SoftwareEdit />)
    fireEvent.click((await screen.findAllByText('上传测试图标'))[0])
    await waitFor(() => expect(screen.getByAltText('软件图标').getAttribute('src')).toBe('/files/by-id/9007199254740993'))
    fireEvent.click(screen.getByText(action))
    await waitFor(() => expect(mocks.save).toHaveBeenCalledWith('42', '7', expect.objectContaining({ iconAssetId: '9007199254740993' })))
    await waitFor(() => expect(mocks.message.success).toHaveBeenCalled())
    view.unmount()
    render(<SoftwareEdit />)
    expect((await screen.findByAltText('软件图标')).getAttribute('src')).toBe('/files/by-id/9007199254740993')
  })

  it('saves a change to attribution without editing other fields', async () => {
    render(<SoftwareEdit />)
    await screen.findAllByText('上传测试图标')
    const option = screen.getByRole('option', { name: '授权转载' })
    fireEvent.change(option.parentElement!, { target: { value: 'AUTHORIZED' } })
    fireEvent.click(screen.getByText('保存'))
    await waitFor(() => expect(mocks.save).toHaveBeenCalledWith('42', '7', expect.objectContaining({ attributionType: 'AUTHORIZED' })))
    // SW02：只提交被修改的字段，未编辑字段不得出现在 PATCH（否则会覆盖服务端现值）
    const patch = mocks.save.mock.calls.at(-1)?.[2] ?? {}
    expect(Object.keys(patch)).toEqual(['attributionType'])
  })
})
