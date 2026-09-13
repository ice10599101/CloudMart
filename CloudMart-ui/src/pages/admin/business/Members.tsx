import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
  ProFormTextArea,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch } from 'antd'
import { getMembers, getMember, updateMember, updateMemberStatus, resetMemberPassword } from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

// 列表项（mall-admin /business/members：id/username/email/nickname/avatar/status/createdAt）
interface MemberRecord {
  id: number
  username: string
  nickname: string
  email: string | null
  avatar: string | null
  gender: string | null
  status: number
  createdAt: string
}

// 编辑弹窗数据 = 用户全量资料（编辑前经 /business/members/{id} 拉取 mall-user UserVO）
interface MemberDetail {
  id: number
  username: string
  nickname: string
  email: string | null
  avatar: string | null
  signature: string | null
  gender: string | null
  birthday: string | null
  constellation: string | null
  occupation: string | null
  school: string | null
  location: string | null
  hobbies: string | null
  status: number
}

const GENDER_OPTIONS = [
  { label: '未设置', value: 'UNKNOWN' },
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
]

const GENDER_TEXT: Record<string, string> = { MALE: '男', FEMALE: '女', UNKNOWN: '未设置' }

/** 用户管理：社区真实用户（mall-user）全资料管理 */
export default function Members() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<MemberDetail | null>(null)
  const [editingLoading, setEditingLoading] = useState(false)
  const [resetTarget, setResetTarget] = useState<MemberRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleStatusChange = async (record: MemberRecord, newStatus: number) => {
    try {
      await updateMemberStatus(record.id, { status: newStatus })
      message.success('状态更新成功')
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  // 编辑前拉取全量资料（列表接口不含签名/性别等扩展字段）
  const openEdit = async (record: MemberRecord) => {
    setEditingLoading(true)
    try {
      const { data: res } = await getMember(record.id)
      const detail = (res as { data: MemberDetail }).data
      setEditingRecord(detail)
      setModalVisible(true)
    } catch {
      message.error('加载用户资料失败')
    } finally {
      setEditingLoading(false)
    }
  }

  const handleUpdate = async (values: Record<string, any>) => {
    if (!editingRecord) return false
    return confirmSubmit(async () => {
      // 编辑契约 = mall-user UpdateProfileRequest 全字段；空邮箱清除为后端 null
      const payload = {
        nickname: values.nickname,
        email: values.email ?? '',
        avatar: values.avatar,
        signature: values.signature,
        gender: values.gender,
        birthday: values.birthday,
        constellation: values.constellation,
        occupation: values.occupation,
        school: values.school,
        location: values.location,
        hobbies: values.hobbies,
      }
      await updateMember(editingRecord.id, payload)
      message.success('更新成功')
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleResetPassword = async (record: MemberRecord, newPassword: string) => {
    try {
      await resetMemberPassword(record.id, newPassword)
      message.success(`已重置 ${record.username} 的登录密码`)
    } catch {
      // 失败提示由 request 拦截器统一呈现（含后端业务文案）
    }
  }

  const columns: ProColumns<MemberRecord>[] = [
    { title: '用户ID', dataIndex: 'id', width: 80, search: false },
    { title: '小答号', dataIndex: 'username', width: 110 },
    { title: '昵称', dataIndex: 'nickname', width: 130, ellipsis: true },
    { title: '邮箱', dataIndex: 'email', width: 210, search: false, ellipsis: true, render: (_, record) => record.email || '-' },
    {
      title: '性别',
      dataIndex: 'gender',
      width: 80,
      search: false,
      render: (_, record) => GENDER_TEXT[record.gender as string] ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Popconfirm
          title={Number(record.status) === 1 ? '确认禁用该用户？' : '确认启用该用户？'}
          onConfirm={() => handleStatusChange(record, Number(record.status) === 1 ? 0 : 1)}
        >
          <Switch
            checked={Number(record.status) === 1}
            checkedChildren="正常"
            unCheckedChildren="禁用"
          />
        </Popconfirm>
      ),
    },
    { title: '注册时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="edit"
          type="link"
          size="small"
          loading={editingLoading}
          onClick={() => openEdit(record)}
        >
          编辑
        </Button>,
        <Button
          key="resetPwd"
          type="link"
          size="small"
          onClick={() => setResetTarget(record)}
        >
          重置密码
        </Button>,
      ],
    },
  ]

  return (
    <>
      <ProTable<MemberRecord>
        headerTitle="用户管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1000 }}
        request={async (params) => {
          return safeProTableRequest<MemberRecord>(() =>
            getMembers({
              page: params.current,
              pageSize: params.pageSize,
              username: params.username,
              nickname: params.nickname,
              status: params.status,
            })
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={`编辑用户 · ${editingRecord?.username ?? ''}`}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleUpdate}
        initialValues={editingRecord ?? {}}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={640}
      >
        <ProFormText name="username" label="小答号" disabled tooltip="小答号是用户身份标识，不可修改" />
        <ProFormText name="nickname" label="昵称" placeholder="请输入昵称" rules={[{ required: true, message: '请输入昵称' }]} />
        <ProFormText
          name="email"
          label="邮箱"
          placeholder="留空清除邮箱"
          rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
        />
        <ProFormSelect name="gender" label="性别" options={GENDER_OPTIONS} />
        <ProFormText name="birthday" label="生日" placeholder="如：2000-01-01" />
        <ProFormText name="occupation" label="职业" placeholder="请输入职业" />
        <ProFormText name="school" label="学校" placeholder="请输入学校" />
        <ProFormText name="location" label="所在地区" placeholder="请输入所在地区" />
        <ProFormText name="constellation" label="星座" placeholder="如：摩羯座" />
        <ProFormText name="avatar" label="头像地址" placeholder="头像图片 URL" />
        <ProFormTextArea name="signature" label="个性签名" placeholder="请输入个性签名" fieldProps={{ rows: 2, maxLength: 200, showCount: true }} />
        <ProFormTextArea name="hobbies" label="兴趣爱好" placeholder="请输入兴趣爱好" fieldProps={{ rows: 2, maxLength: 200, showCount: true }} />
      </ModalForm>

      <ModalForm
        title={`重置密码 · ${resetTarget?.username ?? ''}`}
        open={resetTarget !== null}
        onOpenChange={createHandleOpenChange(
          (open) => setResetTarget(open ? resetTarget : null),
          () => setResetTarget(null),
        )}
        onFinish={async (values: Record<string, any>) => {
          if (!resetTarget) return false
          await handleResetPassword(resetTarget, values.newPassword)
          setResetTarget(null)
          return true
        }}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={420}
      >
        <ProFormText.Password
          name="newPassword"
          label="新密码"
          placeholder="至少 6 位"
          rules={[
            { required: true, message: '请输入新密码' },
            { min: 6, message: '新密码至少 6 位' },
          ]}
        />
      </ModalForm>
    </>
  )
}
