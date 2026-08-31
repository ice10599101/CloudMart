import { useState } from 'react'
import { ConfigProvider, theme, App, Form, Input, Button, Modal } from 'antd'
import {
  LockOutlined,
  MailOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  GlobalOutlined,
  SmileOutlined,
  CopyOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import { register } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import AppMessageBinder from '@/components/AppMessageBinder'
import { useMessage } from '@/utils/useMessage'

interface RegisterFormValues {
  password: string
  confirmPassword: string
  email: string
  nickname: string
}

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    background: 'var(--color-bg-base)',
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, sans-serif",
  },
  leftPanel: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column' as const,
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative' as const,
    overflow: 'hidden',
    background: 'var(--color-gradient-hero)',
    padding: '60px',
  },
  rightPanel: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column' as const,
    justifyContent: 'center',
    alignItems: 'center',
    background: 'var(--color-bg-base)',
    padding: '60px',
    position: 'relative' as const,
  },
  glowCircle1: {
    position: 'absolute' as const,
    top: '-120px',
    left: '-120px',
    width: '400px',
    height: '400px',
    borderRadius: '50%',
    background: 'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.12) 0%, transparent 70%)',
    pointerEvents: 'none' as const,
  },
  glowCircle2: {
    position: 'absolute' as const,
    bottom: '-80px',
    right: '-80px',
    width: '300px',
    height: '300px',
    borderRadius: '50%',
    background: 'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.08) 0%, transparent 70%)',
    pointerEvents: 'none' as const,
  },
  glowCircle3: {
    position: 'absolute' as const,
    top: '40%',
    right: '10%',
    width: '200px',
    height: '200px',
    borderRadius: '50%',
    background: 'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.06) 0%, transparent 70%)',
    pointerEvents: 'none' as const,
  },
  brandName: {
    fontSize: '56px',
    fontWeight: 800,
    letterSpacing: '-1px',
    color: 'var(--color-text-secondary)',
    textShadow: '0 0 40px rgba(var(--color-primary-rgb), 0.3), 0 0 80px rgba(var(--color-primary-rgb), 0.15)',
    marginBottom: '16px',
    position: 'relative' as const,
    zIndex: 1,
  },
  tagline: {
    fontSize: '20px',
    color: 'var(--color-text-secondary)',
    letterSpacing: '2px',
    marginBottom: '64px',
    position: 'relative' as const,
    zIndex: 1,
  },
  featureList: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '28px',
    position: 'relative' as const,
    zIndex: 1,
  },
  featureItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  },
  featureIcon: {
    width: '44px',
    height: '44px',
    borderRadius: '12px',
    background: 'rgba(var(--color-primary-rgb), 0.1)',
    border: '1px solid rgba(var(--color-primary-rgb), 0.15)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--color-primary)',
    fontSize: '20px',
    flexShrink: 0,
  },
  featureText: { color: 'var(--color-text-secondary)', fontSize: '15px', lineHeight: '1.5' },
  featureTitle: { color: 'var(--color-text-secondary)', fontSize: '16px', fontWeight: 600, marginBottom: '2px' },
  formContainer: { width: '100%', maxWidth: '420px' },
  heading: { fontSize: '32px', fontWeight: 700, color: 'var(--color-text-secondary)', marginBottom: '8px', letterSpacing: '-0.5px' },
  subHeading: { fontSize: '15px', color: 'var(--color-text-secondary)', marginBottom: '40px' },
  inputWrapper: { marginBottom: '4px' },
  darkInput: {
    background: 'var(--color-bg-container)',
    border: '1px solid var(--color-border)',
    borderRadius: '10px',
    color: 'var(--color-text-secondary)',
    height: '50px',
    fontSize: '15px',
  },
  registerButton: {
    height: '50px',
    borderRadius: '10px',
    fontSize: '16px',
    fontWeight: 600,
    letterSpacing: '0.5px',
    background: 'var(--color-gradient-primary)',
    border: 'none',
    boxShadow: '0 4px 20px rgba(var(--color-primary-rgb), 0.3)',
    transition: 'all 0.3s ease',
    marginTop: '8px',
  },
  footerText: { textAlign: 'center' as const, marginTop: '32px', color: 'var(--color-text-secondary)', fontSize: '14px' },
  linkText: { color: 'var(--color-primary)', cursor: 'pointer', fontWeight: 500, transition: 'color 0.2s ease' },
  dividerLine: {
    position: 'absolute' as const,
    left: 0,
    top: '10%',
    height: '80%',
    width: '1px',
    background: 'linear-gradient(to bottom, transparent, rgba(var(--color-primary-rgb), 0.2), transparent)',
  },
  xiaoDaHaoTip: {
    background: 'rgba(var(--color-primary-rgb), 0.08)',
    border: '1px solid rgba(var(--color-primary-rgb), 0.15)',
    borderRadius: '8px',
    padding: '12px 16px',
    marginBottom: '20px',
    color: 'var(--color-text-secondary)',
    fontSize: '13px',
    lineHeight: '1.6',
  },
}

const features = [
  { icon: <RocketOutlined />, title: '极速体验', desc: '毫秒级响应，流畅无阻' },
  { icon: <SafetyCertificateOutlined />, title: '安全可靠', desc: '企业级安全防护体系' },
  { icon: <GlobalOutlined />, title: '全球畅享', desc: '跨越地域，连接世界' },
]

export default function Register() {
  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: 'var(--color-primary)',
          colorBgContainer: 'var(--color-bg-container)',
          colorBgElevated: 'var(--color-bg-elevated)',
          colorBgLayout: 'var(--color-bg-base)',
          colorBorder: 'var(--color-border)',
          colorText: '#FFFFFF',
          colorTextSecondary: 'var(--color-text-secondary)',
        },
      }}
    >
      <App>
        <AppMessageBinder />
        <RegisterContent />
      </App>
    </ConfigProvider>
  )
}

function RegisterContent() {
  const messageApi = useMessage()
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm<RegisterFormValues>()
  const loginAction = useAuthStore((s) => s.login)

  const handleSubmit = async (values: RegisterFormValues) => {
    setLoading(true)
    try {
      const { data: response } = await register({
        password: values.password,
        email: values.email,
        nickname: values.nickname,
      })
      const { username, nickname } = response.data

      Modal.success({
        title: '🎉 注册成功',
        content: (
          <div>
            <p style={{ marginBottom: 8 }}>
              欢迎你，<strong>{nickname}</strong>！
            </p>
            <p style={{ marginBottom: 4 }}>你的专属小答号：</p>
            <div
              style={{
                background: '#f0f5ff',
                padding: '12px 16px',
                borderRadius: 8,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <span style={{ fontSize: 24, fontWeight: 700, color: 'var(--color-primary-dark)', letterSpacing: 2 }}>
                {username}
              </span>
              <Button
                type="link"
                icon={<CopyOutlined />}
                onClick={() => {
                  navigator.clipboard.writeText(username)
                  messageApi.success('已复制小答号')
                }}
              >
                复制
              </Button>
            </div>
            <p style={{ marginTop: 8, color: '#999', fontSize: 12 }}>
              小答号可用于登录，请妥善保管
            </p>
          </div>
        ),
        okText: '立即进入',
        onOk: async () => {
          await loginAction(values.email, values.password, '/')
        },
      })
    } catch {
      messageApi.error('注册失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.leftPanel}>
        <div style={styles.glowCircle1} />
        <div style={styles.glowCircle2} />
        <div style={styles.glowCircle3} />

        <div style={styles.brandName}>宝贝小答</div>
        <div style={styles.tagline}>开启你的科技之旅</div>

        <div style={styles.featureList}>
          {features.map((f) => (
            <div key={f.title} style={styles.featureItem}>
              <div style={styles.featureIcon}>{f.icon}</div>
              <div>
                <div style={styles.featureTitle}>{f.title}</div>
                <div style={styles.featureText}>{f.desc}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div style={styles.rightPanel}>
        <div style={styles.dividerLine} />
        <div style={styles.formContainer}>
          <div style={styles.heading}>创建账户</div>
          <div style={styles.subHeading}>注册以开始您的宝贝小答之旅</div>

          <div style={styles.xiaoDaHaoTip}>
            🎉 注册成功后将自动分配专属「小答号」，可用于登录和分享
          </div>

          <Form<RegisterFormValues>
            form={form}
            name="register"
            onFinish={handleSubmit}
            autoComplete="off"
          >
            <Form.Item
              name="nickname"
              rules={[
                { required: true, message: '请输入昵称' },
                { max: 20, message: '昵称最多20个字符' },
              ]}
              style={styles.inputWrapper}
            >
              <Input
                prefix={<SmileOutlined style={{ color: 'var(--color-text-secondary)', fontSize: '16px' }} />}
                placeholder="昵称（必填，不可重复）"
                style={styles.darkInput}
              />
            </Form.Item>

            <Form.Item
              name="email"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '请输入有效的邮箱地址' },
              ]}
              style={styles.inputWrapper}
            >
              <Input
                prefix={<MailOutlined style={{ color: 'var(--color-text-secondary)', fontSize: '16px' }} />}
                placeholder="邮箱（必填，不可重复）"
                style={styles.darkInput}
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6个字符' },
              ]}
              style={styles.inputWrapper}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: 'var(--color-text-secondary)', fontSize: '16px' }} />}
                placeholder="密码"
                style={styles.darkInput}
              />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              dependencies={['password']}
              rules={[
                { required: true, message: '请确认密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve()
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'))
                  },
                }),
              ]}
              style={styles.inputWrapper}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: 'var(--color-text-secondary)', fontSize: '16px' }} />}
                placeholder="确认密码"
                style={styles.darkInput}
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                style={styles.registerButton}
                onMouseEnter={(e) => {
                  e.currentTarget.style.boxShadow = '0 6px 30px rgba(var(--color-primary-rgb), 0.45)'
                  e.currentTarget.style.transform = 'translateY(-1px)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.boxShadow = '0 4px 20px rgba(var(--color-primary-rgb), 0.3)'
                  e.currentTarget.style.transform = 'translateY(0)'
                }}
              >
                注册
              </Button>
            </Form.Item>
          </Form>

          <div style={styles.footerText}>
            已有账户？{' '}
            <span
              style={styles.linkText}
              onClick={() => history.push('/login')}
              onMouseEnter={(e) => { e.currentTarget.style.color = '#33DDFF' }}
              onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--color-primary)' }}
            >
              立即登录
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
