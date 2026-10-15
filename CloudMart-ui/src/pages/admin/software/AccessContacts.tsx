import { useEffect, useState } from 'react'
import { Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd'
import { listAccessContacts, saveAccessContact } from '@/api/admin/software'
import type { AdminAccessContact } from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'

const CONTACT_TYPE_LABELS: Record<string, string> = {
  SITE_USER: '站内账号',
  TEXT: '文本说明',
  HTTPS_URL: 'HTTPS 链接',
}

/**
 * 联系入口（方案 10.10）：维护付款查看模式下的对外联系方式与启停状态；
 * 发布者只能选择启用中的入口；上线付款查看前至少配置一个可用入口。
 * 引用中的入口仅停用不删除（变更留痕）。
 */
export default function SoftwareAccessContacts() {
  const message = useMessage()
  const [contacts, setContacts] = useState<AdminAccessContact[]>([])
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState<AdminAccessContact | null | undefined>(undefined)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const res = await listAccessContacts()
      setContacts(res.data.data ?? [])
    } catch {
      message.error('加载联系入口失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openModal = (contact?: AdminAccessContact) => {
    setEditing(contact ?? null)
    form.setFieldsValue(
      contact
        ? {
            name: contact.name,
            contactType: contact.contactType,
            contactValue: contact.contactValue,
            enabled: contact.enabled,
            sortOrder: contact.sortOrder,
          }
        : { name: '', contactType: 'SITE_USER', contactValue: '', enabled: true, sortOrder: 0 },
    )
  }

  const submit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      await saveAccessContact({
        id: editing?.contactId,
        name: values.name,
        contactType: values.contactType,
        contactValue: values.contactValue,
        enabled: values.enabled,
        sortOrder: values.sortOrder ?? 0,
      })
      message.success('已保存')
      setEditing(undefined)
      load()
    } catch {
      // 校验/业务错误已提示
    } finally {
      setSubmitting(false)
    }
  }

  const toggleEnabled = async (contact: AdminAccessContact) => {
    setSubmitting(true)
    try {
      await saveAccessContact({
        id: contact.contactId,
        name: contact.name,
        contactType: contact.contactType,
        contactValue: contact.contactValue,
        enabled: !contact.enabled,
        sortOrder: contact.sortOrder,
      })
      message.success(contact.enabled ? '已停用（发布者不再可选，已有授权不受影响）' : '已启用')
      load()
    } catch {
      message.error('操作失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Button type="primary" onClick={() => openModal()}>
          新增联系入口
        </Button>
        <Typography.Text type="secondary">
          上线付款查看前至少保留一个启用入口；发布者在提交付款模式时只能选择启用项
        </Typography.Text>
      </Space>
      <Table<AdminAccessContact>
        rowKey="contactId"
        size="middle"
        loading={loading}
        dataSource={contacts}
        pagination={false}
        columns={[
          { title: 'ID', dataIndex: 'contactId', width: 80 },
          { title: '名称', dataIndex: 'name', width: 180 },
          {
            title: '类型',
            dataIndex: 'contactType',
            width: 120,
            render: (_, record) => CONTACT_TYPE_LABELS[record.contactType] ?? record.contactType,
          },
          { title: '内容', dataIndex: 'contactValue', ellipsis: true },
          { title: '排序', dataIndex: 'sortOrder', width: 80 },
          {
            title: '状态',
            dataIndex: 'enabled',
            width: 90,
            render: (_, record) =>
              record.enabled ? <Tag color="success">启用</Tag> : <Tag>已停用</Tag>,
          },
          {
            title: '操作',
            width: 140,
            render: (_, record) => (
              <Space>
                <a onClick={() => openModal(record)}>编辑</a>
                <a
                  style={{ color: record.enabled ? '#d4380d' : undefined }}
                  onClick={() => toggleEnabled(record)}
                >
                  {record.enabled ? '停用' : '启用'}
                </a>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing?.contactId ? '编辑联系入口' : '新增联系入口'}
        open={editing !== undefined}
        onCancel={() => setEditing(undefined)}
        onOk={submit}
        confirmLoading={submitting}
        okText="保存"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, max: 50, message: '名称须为 1~50 字' }]}
          >
            <Input maxLength={50} placeholder="例如 平台客服（工作日 9:00-18:00）" />
          </Form.Item>
          <Form.Item name="contactType" label="类型" rules={[{ required: true }]}>
            <Select
              options={Object.entries(CONTACT_TYPE_LABELS).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="contactValue"
            label="内容"
            rules={[{ required: true, max: 200, message: '内容须为 1~200 字' }]}
            extra="禁止填写密码等秘密信息；HTTPS_URL 类型必须是 https:// 外链"
          >
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序（越小越靠前）">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
