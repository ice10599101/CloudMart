import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('antd', () => ({
  Modal: {
    confirm: vi.fn(),
  },
}))

import { Modal } from 'antd'

function createModalConfirm() {
  let submitCloseRef = { current: false }

  const confirmSubmit = (onSubmit: () => Promise<void>): Promise<boolean> => {
    return new Promise((resolve) => {
      Modal.confirm({
        title: '确认提交',
        content: '确认提交当前编辑的内容吗？',
        okText: '确认提交',
        cancelText: '取消',
        onOk: async () => {
          try {
            await onSubmit()
            submitCloseRef.current = true
            resolve(true)
          } catch {
            resolve(false)
          }
        },
        onCancel: () => {
          resolve(false)
        },
      })
    })
  }

  const createHandleOpenChange = (
    setModalVisible: (v: boolean) => void,
    cleanup?: () => void,
  ) => {
    return (visible: boolean) => {
      if (visible) {
        setModalVisible(true)
      } else if (submitCloseRef.current) {
        submitCloseRef.current = false
        setModalVisible(false)
        cleanup?.()
      } else {
        Modal.confirm({
          title: '确认关闭',
          content: '关闭后未保存的内容将丢失，确认关闭吗？',
          okText: '确认关闭',
          cancelText: '继续编辑',
          onOk: () => {
            setModalVisible(false)
            cleanup?.()
          },
        })
        return false
      }
    }
  }

  return { confirmSubmit, createHandleOpenChange }
}

describe('useModalConfirm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('confirmSubmit resolves true on confirm', async () => {
    vi.mocked(Modal.confirm).mockImplementation(({ onOk }: any) => {
      onOk?.()
      return { destroy: vi.fn(), update: vi.fn() }
    })

    const { confirmSubmit } = createModalConfirm()
    const onSubmit = vi.fn().mockResolvedValue(undefined)

    const result = await confirmSubmit(onSubmit)

    expect(result).toBe(true)
    expect(onSubmit).toHaveBeenCalled()
  })

  it('confirmSubmit resolves false on cancel', async () => {
    vi.mocked(Modal.confirm).mockImplementation(({ onCancel }: any) => {
      onCancel?.()
      return { destroy: vi.fn(), update: vi.fn() }
    })

    const { confirmSubmit } = createModalConfirm()
    const onSubmit = vi.fn().mockResolvedValue(undefined)

    const result = await confirmSubmit(onSubmit)

    expect(result).toBe(false)
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('confirmSubmit resolves false when onSubmit throws', async () => {
    vi.mocked(Modal.confirm).mockImplementation(({ onOk }: any) => {
      onOk?.()
      return { destroy: vi.fn(), update: vi.fn() }
    })

    const { confirmSubmit } = createModalConfirm()
    const onSubmit = vi.fn().mockRejectedValue(new Error('fail'))

    const result = await confirmSubmit(onSubmit)

    expect(result).toBe(false)
  })

  it('createHandleOpenChange opens on visible=true', () => {
    const { createHandleOpenChange } = createModalConfirm()
    const setModalVisible = vi.fn()

    const handler = createHandleOpenChange(setModalVisible)
    handler(true)

    expect(setModalVisible).toHaveBeenCalledWith(true)
  })

  it('createHandleOpenChange confirms close when not submitted', () => {
    const { createHandleOpenChange } = createModalConfirm()
    const setModalVisible = vi.fn()

    const handler = createHandleOpenChange(setModalVisible)
    const result = handler(false)

    expect(Modal.confirm).toHaveBeenCalled()
    expect(result).toBe(false)
  })

  it('createHandleOpenChange closes directly when submitted', async () => {
    const { confirmSubmit, createHandleOpenChange } = createModalConfirm()
    const setModalVisible = vi.fn()
    const cleanup = vi.fn()

    vi.mocked(Modal.confirm).mockImplementation(({ onOk }: any) => {
      onOk?.()
      return { destroy: vi.fn(), update: vi.fn() }
    })

    const onSubmit = vi.fn().mockResolvedValue(undefined)
    await confirmSubmit(onSubmit)

    const handler = createHandleOpenChange(setModalVisible, cleanup)
    handler(false)

    expect(setModalVisible).toHaveBeenCalledWith(false)
    expect(cleanup).toHaveBeenCalled()
  })
})
