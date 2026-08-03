import { useRef } from 'react'
import { Modal } from 'antd'

export function useModalConfirm() {
  const submitCloseRef = useRef(false)

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
