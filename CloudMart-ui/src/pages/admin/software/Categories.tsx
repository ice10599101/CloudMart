import { useEffect, useState } from 'react'
import { Button, Form, Input, InputNumber, Modal, Popconfirm, Space, Switch, Table, Tabs, Tag } from 'antd'
import {
  createSoftwareCategory,
  createSoftwareTag,
  disableSoftwareCategory,
  disableSoftwareTag,
  listAdminSoftwareCategories,
  listAdminSoftwareTags,
  updateSoftwareCategory,
  updateSoftwareTag,
} from '@/api/admin/software'
import type { AdminSoftwareCategory, AdminSoftwareTag } from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'

/**
 * 分类与标签（方案 10.5）：启用、禁用、排序；DELETE 语义为禁用——
 * 被引用分类不做物理删除（否则历史详情/筛选会报错），仅停止新选。
 */
export default function SoftwareCategories() {
  const message = useMessage()
  const [categories, setCategories] = useState<AdminSoftwareCategory[]>([])
  const [categoriesLoading, setCategoriesLoading] = useState(false)
  const [tags, setTags] = useState<AdminSoftwareTag[]>([])
  const [tagsLoading, setTagsLoading] = useState(false)
  const [tagKeyword, setTagKeyword] = useState('')

  const [categoryModal, setCategoryModal] = useState<AdminSoftwareCategory | null | undefined>(
    undefined,
  )
  const [tagModal, setTagModal] = useState<AdminSoftwareTag | null | undefined>(undefined)
  const [submitting, setSubmitting] = useState(false)
  const [categoryForm] = Form.useForm()
  const [tagForm] = Form.useForm()

  const loadCategories = async () => {
    setCategoriesLoading(true)
    try {
      const res = await listAdminSoftwareCategories()
      setCategories(res.data.data ?? [])
    } catch {
      message.error('加载分类失败')
    } finally {
      setCategoriesLoading(false)
    }
  }

  const loadTags = async (keyword?: string) => {
    setTagsLoading(true)
    try {
      const res = await listAdminSoftwareTags({ keyword: keyword || undefined, page: 1, size: 100 })
      setTags(res.data.data?.records ?? [])
    } catch {
      message.error('加载标签失败')
    } finally {
      setTagsLoading(false)
    }
  }

  useEffect(() => {
    loadCategories()
    loadTags()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCategoryModal = (category?: AdminSoftwareCategory) => {
    setCategoryModal(category ?? null)
    categoryForm.setFieldsValue(
      category
        ? {
            name: category.name,
            slug: category.slug,
            icon: category.icon ?? '',
            sortOrder: category.sortOrder,
            enabled: category.enabled,
          }
        : { name: '', slug: '', icon: '', sortOrder: 0, enabled: true },
    )
  }

  const submitCategory = async () => {
    const values = await categoryForm.validateFields()
    setSubmitting(true)
    try {
      const payload = {
        name: values.name,
        slug: values.slug,
        icon: values.icon || undefined,
        sortOrder: values.sortOrder ?? 0,
        enabled: values.enabled,
      }
      if (categoryModal?.categoryId) {
        await updateSoftwareCategory(categoryModal.categoryId, payload)
      } else {
        await createSoftwareCategory(payload)
      }
      message.success('已保存')
      setCategoryModal(undefined)
      loadCategories()
    } catch {
      // 校验/业务错误已提示
    } finally {
      setSubmitting(false)
    }
  }

  const toggleCategory = async (category: AdminSoftwareCategory) => {
    try {
      if (category.enabled) {
        await disableSoftwareCategory(category.categoryId)
        message.success('已禁用（历史内容保留分类名，仅停止新选）')
      } else {
        await updateSoftwareCategory(category.categoryId, {
          name: category.name,
          slug: category.slug,
          icon: category.icon ?? undefined,
          sortOrder: category.sortOrder,
          enabled: true,
        })
        message.success('已启用')
      }
      loadCategories()
    } catch {
      message.error('操作失败')
    }
  }

  const openTagModal = (tag?: AdminSoftwareTag) => {
    setTagModal(tag ?? null)
    tagForm.setFieldsValue(tag ? { name: tag.name, enabled: tag.enabled } : { name: '', enabled: true })
  }

  const submitTag = async () => {
    const values = await tagForm.validateFields()
    setSubmitting(true)
    try {
      const payload = { name: values.name, enabled: values.enabled }
      if (tagModal?.tagId) {
        await updateSoftwareTag(tagModal.tagId, payload)
      } else {
        await createSoftwareTag(payload)
      }
      message.success('已保存')
      setTagModal(undefined)
      loadTags(tagKeyword)
    } catch {
      // 校验/业务错误已提示
    } finally {
      setSubmitting(false)
    }
  }

  const toggleTag = async (tag: AdminSoftwareTag) => {
    try {
      if (tag.enabled) {
        await disableSoftwareTag(tag.tagId)
        message.success('已禁用（作者不再可选，历史快照保留标签名）')
      } else {
        await updateSoftwareTag(tag.tagId, { name: tag.name, enabled: true })
        message.success('已启用')
      }
      loadTags(tagKeyword)
    } catch {
      message.error('操作失败')
    }
  }

  return (
    <>
      <Tabs
        items={[
          {
            key: 'categories',
            label: '分类',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button type="primary" onClick={() => openCategoryModal()}>
                    新增分类
                  </Button>
                </Space>
                <Table<AdminSoftwareCategory>
                  rowKey="categoryId"
                  size="middle"
                  loading={categoriesLoading}
                  dataSource={categories}
                  pagination={false}
                  columns={[
                    { title: 'ID', dataIndex: 'categoryId', width: 80 },
                    { title: '名称', dataIndex: 'name', width: 160 },
                    { title: '标识 slug', dataIndex: 'slug', width: 160 },
                    { title: '图标', dataIndex: 'icon', width: 120, render: (v) => v ?? '-' },
                    { title: '排序', dataIndex: 'sortOrder', width: 80 },
                    {
                      title: '状态',
                      dataIndex: 'enabled',
                      width: 90,
                      render: (_, record) =>
                        record.enabled ? <Tag color="success">启用</Tag> : <Tag>已禁用</Tag>,
                    },
                    { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
                    {
                      title: '操作',
                      width: 150,
                      render: (_, record) => (
                        <Space>
                          <a onClick={() => openCategoryModal(record)}>编辑</a>
                          <Popconfirm
                            title={
                              record.enabled
                                ? '禁用后该分类不可再被选择，已发布内容不受影响，确认禁用？'
                                : '确认启用该分类？'
                            }
                            onConfirm={() => toggleCategory(record)}
                          >
                            <a style={{ color: record.enabled ? '#d4380d' : undefined }}>
                              {record.enabled ? '禁用' : '启用'}
                            </a>
                          </Popconfirm>
                        </Space>
                      ),
                    },
                  ]}
                />
              </>
            ),
          },
          {
            key: 'tags',
            label: '标签',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Input.Search
                    placeholder="按标签名搜索"
                    allowClear
                    style={{ width: 240 }}
                    onSearch={(value) => {
                      setTagKeyword(value)
                      loadTags(value)
                    }}
                  />
                  <Button type="primary" onClick={() => openTagModal()}>
                    新增标签
                  </Button>
                </Space>
                <Table<AdminSoftwareTag>
                  rowKey="tagId"
                  size="middle"
                  loading={tagsLoading}
                  dataSource={tags}
                  pagination={false}
                  columns={[
                    { title: 'ID', dataIndex: 'tagId', width: 80 },
                    { title: '标签名', dataIndex: 'name', width: 200 },
                    {
                      title: '状态',
                      dataIndex: 'enabled',
                      width: 90,
                      render: (_, record) =>
                        record.enabled ? <Tag color="success">启用</Tag> : <Tag>已禁用</Tag>,
                    },
                    { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
                    {
                      title: '操作',
                      width: 150,
                      render: (_, record) => (
                        <Space>
                          <a onClick={() => openTagModal(record)}>编辑</a>
                          <Popconfirm
                            title={
                              record.enabled
                                ? '禁用后作者不再能选择该标签，历史快照保留标签名，确认禁用？'
                                : '确认启用该标签？'
                            }
                            onConfirm={() => toggleTag(record)}
                          >
                            <a style={{ color: record.enabled ? '#d4380d' : undefined }}>
                              {record.enabled ? '禁用' : '启用'}
                            </a>
                          </Popconfirm>
                        </Space>
                      ),
                    },
                  ]}
                />
              </>
            ),
          },
        ]}
      />

      <Modal
        title={categoryModal?.categoryId ? '编辑分类' : '新增分类'}
        open={categoryModal !== undefined}
        onCancel={() => setCategoryModal(undefined)}
        onOk={submitCategory}
        confirmLoading={submitting}
        okText="保存"
        destroyOnHidden
      >
        <Form form={categoryForm} layout="vertical">
          <Form.Item
            name="name"
            label="分类名"
            rules={[{ required: true, max: 40, message: '分类名须为 1~40 字' }]}
          >
            <Input maxLength={40} />
          </Form.Item>
          <Form.Item
            name="slug"
            label="标识（URL 用）"
            rules={[
              { required: true, message: '请输入分类标识' },
              {
                pattern: /^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$/,
                message: '小写字母/数字/连字符，不以连字符开头结尾',
              },
            ]}
          >
            <Input maxLength={64} placeholder="例如 developer-tools" />
          </Form.Item>
          <Form.Item name="icon" label="图标标识（可空）">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序（越小越靠前）">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={tagModal?.tagId ? '编辑标签' : '新增标签'}
        open={tagModal !== undefined}
        onCancel={() => setTagModal(undefined)}
        onOk={submitTag}
        confirmLoading={submitting}
        okText="保存"
        destroyOnHidden
      >
        <Form form={tagForm} layout="vertical">
          <Form.Item
            name="name"
            label="标签名"
            rules={[{ required: true, min: 2, max: 20, message: '标签名须为 2~20 字' }]}
          >
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
