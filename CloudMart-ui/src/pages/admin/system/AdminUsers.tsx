import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Drawer, Modal, Popconfirm, Select, Space, Tag, Typography } from 'antd'
import { PlusOutlined, KeyOutlined } from '@ant-design/icons'
import {
  listAdminUsers,
  getAdminUser,
  createAdminUser,
  updateAdminUser,
  deleteAdminUser,
  changeAdminUserStatus,
  resetAdminUserPassword,
  listAdminRoles,
  type AdminUserRow,
  type AdminRoleRow,
} from '@/api/admin/system'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/**
 * T24 多运营：管理员账号管理（区别于"用户管理 1001"=C 端会员）。
 * 账号 CRUD + 启停 + 重置密码 + 角色分配；操作落 OperLog 审计。
 * 撤权链路（T24 验收）：禁用/删角色后该账号旧会话经 SEC-02 强退链即时失效。
 */

const { Paragraph } = Typography

export default function AdminUsers() {
  const actionRef = useRef<ActionType>(null)
  const message = useMessage()
  const [modalVisible, setModalVisible] = useState(false)
  const [editing, setEditing] = useState<AdminUserRow | null>(null)
  const [roles, setRoles] = useState<AdminRoleRow[]>([])
  const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([])
  const [pwdModal, setPwdModal] = useState<AdminUserRow | null>(null)
  const [newPwd, setNewPwd] = useState('')

  const reload = () => actionRef.current?.reload()

  const loadRoles = async () => {
    const res = await listAdminRoles()
    setRoles(res.data.data ?? [])
  }

  const openCreate = async () => {
    setEditing(null)
    setSelectedRoleIds([])
    await loadRoles()
    setModalVisible(true)
  }

  const openEdit = async (row: AdminUserRow) => {
    await loadRoles()
    const detail = await getAdminUser(row.id)
    const d = detail.data.data
    setSelectedRoleIds(
      ((d?.roles ?? []) as Array<{ roleId?: number }>)
        .map((r) => Number(r.roleId ?? 0))
        .filter(Boolean),
    )
    setEditing(row)
    setModalVisible(true)
  }

  const handleSubmit = async () => {
    const payload = { roleIds: selectedRoleIds }
    if (editing) {
      await updateAdminUser(editing.id, payload)
      message.success('账号已更新')
    } else {
      message.warning('请在弹窗表单中填写账号信息')
    }
    setModalVisible(false)
    reload()
  }

  const handleStatus = async (row: AdminUserRow, next: number) => {
    await changeAdminUserStatus(row.id, next)
    message.success(next === 1 ? '已启用' : '已禁用（旧会话即时失效）')
    reload()
  }

  const handleResetPwd = async () => {
    if (!pwdModal) return
    if (newPwd.length < 6) {
      message.warning('新密码至少 6 位')
      return
    }
    await resetAdminUserPassword(pwdModal.id, newPwd)
    message.success('密码已重置')
    setPwdModal(null)
    setNewPwd('')
  }

  const columns: ProColumns<AdminUserRow>[] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username', width: 140 },
    { title: '昵称', dataIndex: 'nickname', width: 120 },
    { title: '邮箱', dataIndex: 'email', width: 180, ellipsis: true },
    {
      title: '角色',
      dataIndex: 'roles',
      width: 200,
      render: (_, row) => (
        <Space size={4} wrap>
          {(row.roles ?? []).map((r: { roleKey: string; roleName: string }) => (
            <Tag key={r.roleKey} color="blue">{r.roleName}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, row) =>
        row.status === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 160 },
    {
      title: '操作',
      valueType: 'option',
      width: 260,
      fixed: 'right',
      render: (_, row) => [
        <Button key="edit" type="link" size="small" onClick={() => openEdit(row)}>编辑</Button>,
        row.status === 1 ? (
          <Popconfirm key="disable" title="禁用后该账号旧会话即时失效，确定？" onConfirm={() => handleStatus(row, 0)}>
            <Button type="link" size="small" danger>禁用</Button>
          </Popconfirm>
        ) : (
          <Button key="enable" type="link" size="small" onClick={() => handleStatus(row, 1)}>启用</Button>
        ),
        <Button key="pwd" type="link" size="small" icon={<KeyOutlined />} onClick={() => { setPwdModal(row); setNewPwd('') }}>
          重置密码
        </Button>,
        <Popconfirm key="delete" title="确认删除该运营账号？" onConfirm={async () => { await deleteAdminUser(row.id); message.success('已删除'); reload() }}>
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ].filter(Boolean),
    },
  ]

  return (
    <>
      <ProTable<AdminUserRow>
        headerTitle="管理员账号（T24 多运营）"
        actionRef={actionRef}
        rowKey="id"
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增运营账号
          </Button>,
        ]}
        request={async (params) =>
          safeProTableRequest<AdminUserRow>(async () => {
            const { current = 1, pageSize = 20, username } = params as {
              current?: number; pageSize?: number; username?: string
            }
            return listAdminUsers({ username, page: current, pageSize })
          })
        }
        columns={columns}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 1100 }}
      />

      <Modal
        title={editing ? `编辑账号角色：${editing.username}` : '编辑账号角色'}
        open={modalVisible && editing !== null}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Paragraph type="secondary">
          选择该账号可执行的功能域（T24：权限以接口动作拆分，角色下菜单/权限点在"角色管理"维护）。
          账号基础信息（用户名/密码）创建后如需变更请用对应操作。
        </Paragraph>
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder="选择角色"
          value={selectedRoleIds}
          onChange={setSelectedRoleIds}
          options={roles.map((r) => ({ value: r.id, label: `${r.roleName}（${r.roleKey}）` }))}
        />
      </Modal>

      {/* 新增账号表单（username/password 必填，后端校验 3-50 / 6-100 位） */}
      <Modal
        title="新增运营账号"
        open={modalVisible && editing === null}
        onCancel={() => setModalVisible(false)}
        footer={null}
        destroyOnClose
      >
        <CreateUserForm
          roles={roles}
          onDone={() => { setModalVisible(false); reload() }}
        />
      </Modal>

      <Modal
        title={pwdModal ? `重置密码：${pwdModal.username}` : '重置密码'}
        open={pwdModal !== null}
        onOk={handleResetPwd}
        onCancel={() => setPwdModal(null)}
        okText="重置"
        cancelText="取消"
      >
        <Select
          style={{ width: '100%', marginBottom: 12 }}
          placeholder="或从建议密码中选择"
          value={undefined as unknown as string}
          onChange={(v: string) => setNewPwd(v)}
          options={[
            { value: `Ops#${Math.random().toString(36).slice(2, 10)}!`, label: '生成随机强密码（选择后可再编辑）' },
          ]}
        />
        <input
          className="ant-input"
          value={newPwd}
          onChange={(e) => setNewPwd(e.target.value)}
          placeholder="新密码（至少 6 位，SEC-03 会一次性展示给操作者分发）"
        />
      </Modal>

      <Drawer title={null} open={false} onClose={() => undefined} />
    </>
  )
}

/** 新增账号表单：username/nickname/password/角色多选（POST /admin/users 全字段走后端校验） */
function CreateUserForm({ roles, onDone }: { roles: AdminRoleRow[]; onDone: () => void }) {
  const message = useMessage()
  const [username, setUsername] = useState('')
  const [nickname, setNickname] = useState('')
  const [password, setPassword] = useState('')
  const [roleIds, setRoleIds] = useState<number[]>([])
  const [saving, setSaving] = useState(false)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <input className="ant-input" placeholder="用户名（3-50 位，唯一）" value={username} onChange={(e) => setUsername(e.target.value)} />
      <input className="ant-input" placeholder="昵称" value={nickname} onChange={(e) => setNickname(e.target.value)} />
      <input className="ant-input" type="password" placeholder="初始密码（6-100 位）" value={password} onChange={(e) => setPassword(e.target.value)} />
      <Select
        mode="multiple"
        placeholder="选择角色"
        value={roleIds}
        onChange={setRoleIds}
        options={roles.map((r) => ({ value: r.id, label: `${r.roleName}（${r.roleKey}）` }))}
      />
      <Button
        type="primary"
        loading={saving}
        onClick={async () => {
          if (username.trim().length < 3 || password.length < 6) {
            message.warning('用户名至少 3 位、密码至少 6 位')
            return
          }
          setSaving(true)
          try {
            await createAdminUser({
              username: username.trim(),
              nickname: nickname.trim() || username.trim(),
              password,
              roleIds,
              status: 1,
            })
            message.success('账号已创建（SEC-03：初始密码请线下分发）')
            onDone()
          } finally {
            setSaving(false)
          }
        }}
      >
        创建
      </Button>
    </div>
  )
}
