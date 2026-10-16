import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import {
  Button,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Tag,
  Typography,
} from 'antd'
import {
  banAdminSoftware,
  deleteAdminSoftware,
  featureAdminSoftware,
  getAdminSoftwareDetail,
  listAdminSoftware,
  listAdminSoftwareCategories,
  unbanAdminSoftware,
} from '@/api/admin/software'
import type { AdminSoftwareDetail, AdminSoftwareItem } from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/** 生命周期（4.2）：ACTIVE=在架；UNLISTED=作者下架/解除封禁落点；BANNED=平台封禁 */
const LIFECYCLE_MAP: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '在架', color: 'success' },
  UNLISTED: { label: '已下架', color: 'warning' },
  BANNED: { label: '已封禁', color: 'error' },
}

const LICENSE_MAP: Record<string, string> = {
  FREE: '免费',
  OPEN_SOURCE: '开源',
  TRIAL: '试用',
  COMMERCIAL: '商业',
}

/**
 * 软件管理（方案 10.1）：关键词/分享者/分类/授权类型/状态筛选；查看线上内容、
 * 待审内容、精选与下架记录；首页精选推荐 P0 在此手动设置 featured_rank；
 * 独立封禁/解除（解除回 UNLISTED，由作者恢复展示）。权限 software:list /
 * software:moderate / software:feature，菜单/按钮由后端菜单种子控制。
 */
export default function SoftwareItems() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detail, setDetail] = useState<AdminSoftwareDetail | null>(null)
  const [featureTarget, setFeatureTarget] = useState<AdminSoftwareItem | null>(null)
  const [featureRank, setFeatureRank] = useState<number>(0)
  const [banTarget, setBanTarget] = useState<AdminSoftwareItem | null>(null)
  const [banReason, setBanReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const openDetail = async (softwareId: string) => {
    try {
      const res = await getAdminSoftwareDetail(softwareId)
      setDetail(res.data.data)
    } catch {
      message.error('加载详情失败')
    }
  }

  const confirmFeature = () => {
    Modal.confirm({
      title: '调整精选',
      content: '确认调整精选排序？rank=0 表示取消精选，保存后广场推荐位立即更新。',
      okText: '确认调整',
      cancelText: '再改改',
      onOk: () => doSubmitFeature(),
    })
  }

  const doSubmitFeature = async () => {
    if (!featureTarget) return
    setSubmitting(true)
    try {
      await featureAdminSoftware(featureTarget.softwareId, {
        featuredRank: featureRank,
        expectedVersion: featureTarget.lockVersion,
      })
      message.success(featureRank > 0 ? `已设为精选（排序 ${featureRank}）` : '已取消精选')
      setFeatureTarget(null)
      actionRef.current?.reload()
    } catch {
      // 业务错误（版本冲突等）已由全局拦截器提示；保留弹窗供核对后重试
    } finally {
      setSubmitting(false)
    }
  }

  const submitDelete = async (record: AdminSoftwareItem) => {
    setSubmitting(true)
    try {
      await deleteAdminSoftware(record.softwareId, record.lockVersion)
      message.success('已删除（软删，公开面立即隐藏）')
      actionRef.current?.reload()
    } catch {
      // 失败提示由请求层统一弹出；刷新行数据以拿到新 lockVersion 重试
      actionRef.current?.reload()
    } finally {
      setSubmitting(false)
    }
  }

  const submitBan = async () => {
    if (!banTarget) return
    if (!banReason.trim()) {
      message.warning('请填写处置原因')
      return
    }
    setSubmitting(true)
    try {
      const payload = { reason: banReason.trim(), expectedVersion: banTarget.lockVersion }
      if (banTarget.lifecycle === 'BANNED') {
        await unbanAdminSoftware(banTarget.softwareId, payload)
        message.success('已解除封禁（软件回到已下架，由作者重新上架）')
      } else {
        await banAdminSoftware(banTarget.softwareId, payload)
        message.success('已封禁，作者不可自行恢复')
      }
      setBanTarget(null)
      setBanReason('')
      actionRef.current?.reload()
    } catch {
      // 保留弹窗，便于刷新后带新版本重试
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ProColumns<AdminSoftwareItem>[] = [
    { title: '软件ID', dataIndex: 'softwareId', width: 90, search: false },
    { title: '名称', dataIndex: 'name', width: 200, ellipsis: true, search: false },
    { title: '关键词', dataIndex: 'keyword', hideInTable: true, fieldProps: { placeholder: '名称/简介' } },
    { title: '分享者ID', dataIndex: 'ownerId', width: 110, search: false },
    {
      title: '分享者',
      dataIndex: 'ownerIdFilter',
      hideInTable: true,
      fieldProps: { placeholder: '精确会员ID' },
    },
    {
      title: '分类',
      dataIndex: 'categoryId',
      width: 120,
      valueType: 'select',
      fieldProps: { showSearch: true },
      request: async () => {
        try {
          const res = await listAdminSoftwareCategories()
          return (res.data.data ?? []).map((c) => ({
            label: c.enabled ? c.name : `${c.name}（已禁用）`,
            value: c.categoryId,
          }))
        } catch {
          return []
        }
      },
      render: (_, record) => record.categoryName ?? '-',
    },
    {
      title: '授权',
      dataIndex: 'license',
      width: 90,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(LICENSE_MAP).map(([value, text]) => [value, { text }]),
      ),
      render: (_, record) =>
        record.licenseType ? (LICENSE_MAP[record.licenseType] ?? record.licenseType) : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 150,
      valueEnum: {
        ACTIVE: { text: '在架' },
        PENDING: { text: '待审' },
        FEATURED: { text: '精选' },
        UNLISTED: { text: '已下架' },
        BANNED: { text: '已封禁' },
      },
      render: (_, record) => {
        const info = LIFECYCLE_MAP[record.lifecycle] ?? {
          label: record.lifecycle,
          color: 'default',
        }
        return (
          <Space size={4}>
            <Tag color={info.color}>{info.label}</Tag>
            {record.pendingRevisionId && <Tag color="processing">待审</Tag>}
          </Space>
        )
      },
    },
    {
      title: '精选排序',
      dataIndex: 'featuredRank',
      width: 95,
      search: false,
      render: (_, record) =>
        record.featuredRank > 0 ? <Tag color="gold">{record.featuredRank}</Tag> : '-',
    },
    { title: '收藏', dataIndex: 'favoriteCount', width: 70, search: false },
    { title: '评论', dataIndex: 'commentCount', width: 70, search: false },
    {
      // 去重获取链接累计数（不是外部下载量）
      title: '获取链接数',
      dataIndex: 'linkAccessCount',
      width: 100,
      search: false,
    },
    { title: '更新时间', dataIndex: 'updatedAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, record) => [
        <a key="detail" onClick={() => openDetail(record.softwareId)}>
          详情
        </a>,
        <a
          key="featured"
          onClick={() => {
            setFeatureTarget(record)
            setFeatureRank(record.featuredRank)
          }}
        >
          {record.featuredRank > 0 ? '调整精选' : '设为精选'}
        </a>,
        <a
          key="ban"
          style={{ color: record.lifecycle === 'BANNED' ? undefined : '#d4380d' }}
          onClick={() => {
            setBanTarget(record)
            setBanReason('')
          }}
        >
          {record.lifecycle === 'BANNED' ? '解除封禁' : '封禁'}
        </a>,
        <Popconfirm
          key="delete"
          title="删除软件"
          description={`软删除「${record.name}」，公开面立即隐藏且作者不可恢复，确认？`}
          okText="删除"
          okButtonProps={{ danger: true }}
          cancelText="取消"
          onConfirm={() => void submitDelete(record)}
        >
          <a style={{ color: '#d4380d' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<AdminSoftwareItem>
        headerTitle="软件管理"
        rowKey="softwareId"
        actionRef={actionRef}
        columns={columns}
        options={{ density: false }}
        scroll={{ x: 1400 }}
        request={(params) =>
          safeProTableRequest(() =>
            listAdminSoftware({
              keyword: (params as Record<string, unknown>).keyword as string | undefined,
              ownerId: (params as Record<string, unknown>).ownerIdFilter as string | undefined,
              categoryId: (params as Record<string, unknown>).categoryId as string | undefined,
              license: (params as Record<string, unknown>).license as string | undefined,
              status: (params as Record<string, unknown>).status as string | undefined,
              page: params.current,
              size: params.pageSize,
            }),
          )
        }
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
      />

      {/* 详情：后台完整信息 + 最近审核轨迹（含秘密字段，仅管理员可见） */}
      <Drawer
        title="软件详情"
        width={640}
        open={!!detail}
        onClose={() => setDetail(null)}
        destroyOnHidden
      >
        {detail && (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="软件ID">{detail.softwareId}</Descriptions.Item>
              <Descriptions.Item label="分享者">{detail.ownerId}</Descriptions.Item>
              <Descriptions.Item label="名称">{detail.name ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {LIFECYCLE_MAP[detail.lifecycle]?.label ?? detail.lifecycle}
              </Descriptions.Item>
              <Descriptions.Item label="精选排序">{detail.featuredRank}</Descriptions.Item>
              <Descriptions.Item label="主体版本">{detail.lockVersion}</Descriptions.Item>
              <Descriptions.Item label="线上快照">
                {detail.publishedRevisionId ?? '未上线'}
              </Descriptions.Item>
              <Descriptions.Item label="工作快照">
                {detail.workingRevisionId ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="待审快照">
                {detail.pendingRevisionId ?? '无'}
              </Descriptions.Item>
              <Descriptions.Item label="审核状态">
                {detail.reviewStatus ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="简介" span={2}>
                {detail.summary ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="查看条件" span={2}>
                {detail.accessMode ?? '-'}
                {detail.minCommunityLevel != null
                  ? `（最低社区等级 ${detail.minCommunityLevel}）`
                  : ''}
              </Descriptions.Item>
              <Descriptions.Item label="获取条件说明" span={2}>
                {detail.accessNote ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 20 }}>
              最近处置轨迹
            </Typography.Title>
            {(detail.auditTrail ?? []).length === 0 ? (
              <Typography.Text type="secondary">暂无记录</Typography.Text>
            ) : (
              (detail.auditTrail ?? []).map((entry, index) => (
                <div key={index} style={{ padding: '6px 0', borderBottom: '1px solid #f0f0f0' }}>
                  <Space size={8} wrap>
                    <Tag>{entry.action}</Tag>
                    <Typography.Text type="secondary">
                      {entry.fromStatus} → {entry.toStatus}
                    </Typography.Text>
                    <Typography.Text type="secondary">{entry.createdAt}</Typography.Text>
                  </Space>
                  {entry.reason && (
                    <div>
                      <Typography.Text type="secondary">原因：{entry.reason}</Typography.Text>
                    </div>
                  )}
                </div>
              ))
            )}
          </>
        )}
      </Drawer>

      {/* 精选排序（featuredRank=0 取消精选；expectedVersion 防并发覆盖） */}
      <Modal
        title={featureTarget?.featuredRank ? '调整精选排序' : '设为精选'}
        open={!!featureTarget}
        onCancel={() => setFeatureTarget(null)}
        onOk={confirmFeature}
        confirmLoading={submitting}
        okText="保存"
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          首页「推荐」区按排序值从大到小展示，0 表示取消精选。
          {featureTarget?.name ? `（${featureTarget.name}）` : ''}
        </Typography.Paragraph>
        <InputNumber
          min={0}
          max={9999}
          value={featureRank}
          onChange={(value) => setFeatureRank(value ?? 0)}
          style={{ width: '100%' }}
        />
      </Modal>

      {/* 封禁/解除（必须原因与版本；解除回到已下架） */}
      <Modal
        title={banTarget?.lifecycle === 'BANNED' ? '解除封禁' : '封禁软件'}
        open={!!banTarget}
        onCancel={() => setBanTarget(null)}
        onOk={submitBan}
        confirmLoading={submitting}
        okText={banTarget?.lifecycle === 'BANNED' ? '确认解除' : '确认封禁'}
        okButtonProps={{ danger: banTarget?.lifecycle !== 'BANNED' }}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          {banTarget?.lifecycle === 'BANNED'
            ? '解除后软件回到「已下架」，需作者自行重新上架；原因会写入审计。'
            : '封禁后作者无法通过下架/重新上架自行恢复；分享者会收到站内通知。'}
          {banTarget?.name ? `（${banTarget.name}）` : ''}
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder="处置原因（1~500 字，必填）"
          value={banReason}
          onChange={(e) => setBanReason(e.target.value)}
        />
      </Modal>
    </>
  )
}
