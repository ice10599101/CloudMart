import { useEffect, useState } from 'react'
import { ProForm, ProFormText } from '@ant-design/pro-components'
import { Card, Col, Row, Spin } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { getAdminProfile, updateAdminProfile, updateAdminPassword } from '@/api/admin/auth'
import { useMessage } from '@/utils/useMessage'

interface AdminProfile {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
}

/** 账号设置：单管理员后台，管理员在此维护自己的资料与登录密码（替代已下线的管理员管理） */
export default function AccountSettings() {
  const message = useMessage()
  const [profile, setProfile] = useState<AdminProfile | null>(null)

  useEffect(() => {
    getAdminProfile()
      .then(({ data: res }) => {
        const data = (res as { data: AdminProfile }).data
        setProfile(data)
        return data
      })
      .catch(() => {
        message.error('加载账号信息失败')
      })
  }, [message])

  const handleUpdateProfile = async (values: Record<string, any>) => {
    await updateAdminProfile({
      nickname: values.nickname,
      email: values.email,
      phone: values.phone,
      avatar: values.avatar,
    })
    message.success('资料更新成功')
  }

  const handleUpdatePassword = async (values: Record<string, any>) => {
    await updateAdminPassword({
      oldPassword: values.oldPassword,
      newPassword: values.newPassword,
    })
    message.success('密码修改成功，下次登录请使用新密码')
  }

  if (!profile) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Spin size="large" description="加载中..." />
      </div>
    )
  }

  const cardStyle = { borderRadius: 10, border: '1px solid var(--color-border)' }
  const titleStyle = { color: 'var(--color-text-secondary)' }

  return (
    <div style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title={<span style={titleStyle}>基本资料</span>} style={cardStyle}>
            <ProForm
              initialValues={profile}
              onFinish={handleUpdateProfile}
              submitter={{ searchConfig: { submitText: '保存资料' } }}
            >
              <ProFormText
                name="username"
                label="登录账号"
                disabled
                fieldProps={{ prefix: <UserOutlined /> }}
                tooltip="登录账号创建后不可修改"
              />
              <ProFormText name="nickname" label="昵称" placeholder="请输入昵称" />
              <ProFormText
                name="email"
                label="邮箱"
                placeholder="请输入邮箱"
                rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
              />
              <ProFormText name="phone" label="手机号" placeholder="请输入手机号" />
              <ProFormText name="avatar" label="头像地址" placeholder="头像图片 URL" />
            </ProForm>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={<span style={titleStyle}>修改密码</span>} style={cardStyle}>
            <ProForm
              onFinish={handleUpdatePassword}
              submitter={{ searchConfig: { submitText: '修改密码' } }}
            >
              <ProFormText.Password
                name="oldPassword"
                label="原密码"
                fieldProps={{ prefix: <LockOutlined /> }}
                placeholder="请输入原密码"
                rules={[{ required: true, message: '请输入原密码' }]}
              />
              <ProFormText.Password
                name="newPassword"
                label="新密码"
                fieldProps={{ prefix: <LockOutlined /> }}
                placeholder="至少 6 位"
                rules={[
                  { required: true, message: '请输入新密码' },
                  { min: 6, message: '新密码至少 6 位' },
                ]}
              />
              <ProFormText.Password
                name="confirmPassword"
                label="确认新密码"
                fieldProps={{ prefix: <LockOutlined /> }}
                placeholder="再次输入新密码"
                dependencies={['newPassword']}
                rules={[
                  { required: true, message: '请再次输入新密码' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('newPassword') === value) return Promise.resolve()
                      return Promise.reject(new Error('两次输入的密码不一致'))
                    },
                  }),
                ]}
              />
            </ProForm>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
