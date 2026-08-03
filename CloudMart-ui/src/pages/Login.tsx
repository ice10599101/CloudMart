import { useState } from 'react'
import { ConfigProvider, theme, Form, Input, Button, Checkbox, App } from 'antd'
import {
  UserOutlined,
  LockOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { history, useSearchParams } from 'umi'
import { useAuthStore } from '@/stores/auth'
import { useMessage } from '@/utils/useMessage'

interface LoginFormValues {
  account: string
  password: string
  remember: boolean
}

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    background: 'var(--color-bg-base)',
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, sans-serif",
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
    background:
      'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.12) 0%, transparent 70%)',
    pointerEvents: 'none' as const,
  },
  glowCircle2: {
    position: 'absolute' as const,
    bottom: '-80px',
    right: '-80px',
    width: '300px',
    height: '300px',
    borderRadius: '50%',
    background:
      'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.08) 0%, transparent 70%)',
    pointerEvents: 'none' as const,
  },
  glowCircle3: {
    position: 'absolute' as const,
    top: '40%',
    right: '10%',
    width: '200px',
    height: '200px',
    borderRadius: '50%',
    background:
      'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.06) 0%, transparent 70%)',
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
  featureText: {
    color: 'var(--color-text-secondary)',
    fontSize: '15px',
    lineHeight: '1.5',
  },
  featureTitle: {
    color: 'var(--color-text-secondary)',
    fontSize: '16px',
    fontWeight: 600,
    marginBottom: '2px',
  },
  formContainer: {
    width: '100%',
    maxWidth: '420px',
  },
  heading: {
    fontSize: '32px',
    fontWeight: 700,
    color: 'var(--color-text-secondary)',
    marginBottom: '8px',
    letterSpacing: '-0.5px',
  },
  subHeading: {
    fontSize: '15px',
    color: 'var(--color-text-secondary)',
    marginBottom: '48px',
  },
  inputWrapper: {
    marginBottom: '4px',
  },
  darkInput: {
    background: 'var(--color-bg-container)',
    border: '1px solid var(--color-border)',
    borderRadius: '10px',
    color: 'var(--color-text-secondary)',
    height: '50px',
    fontSize: '15px',
  },
  checkboxWrapper: {
    marginBottom: '32px',
  },
  loginButton: {
    height: '50px',
    borderRadius: '10px',
    fontSize: '16px',
    fontWeight: 600,
    letterSpacing: '0.5px',
    background: 'var(--color-gradient-primary)',
    border: 'none',
    boxShadow: '0 4px 20px rgba(var(--color-primary-rgb), 0.3)',
    transition: 'all 0.3s ease',
  },
  footerText: {
    textAlign: 'center' as const,
    marginTop: '32px',
    color: 'var(--color-text-secondary)',
    fontSize: '14px',
  },
  linkText: {
    color: 'var(--color-primary)',
    cursor: 'pointer',
    fontWeight: 500,
    transition: 'color 0.2s ease',
  },
  dividerLine: {
    position: 'absolute' as const,
    left: 0,
    top: '10%',
    height: '80%',
    width: '1px',
    background:
      'linear-gradient(to bottom, transparent, rgba(var(--color-primary-rgb), 0.2), transparent)',
  },
}

const features = [
  {
    icon: <RocketOutlined />,
    title: '极速体验',
    desc: '毫秒级响应，流畅无阻',
  },
  {
    icon: <SafetyCertificateOutlined />,
    title: '安全可靠',
    desc: '企业级安全防护体系',
  },
  {
    icon: <ThunderboltOutlined />,
    title: '智能推荐',
    desc: 'AI 驱动的个性化服务',
  },
]

export default function Login() {
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
      <LoginContent />
    </App>
    </ConfigProvider>
  )
}

function LoginContent() {
  const message = useMessage()
  const [loading, setLoading] = useState(false)
  const loginAction = useAuthStore((s) => s.login)
  const [searchParams] = useSearchParams()
  const urlRedirect = searchParams.get('redirect')
  const redirect = urlRedirect || sessionStorage.getItem('login_redirect') || '/'
  sessionStorage.removeItem('login_redirect')

  const handleSubmit = async (values: LoginFormValues) => {
    setLoading(true)
    try {
      await loginAction(values.account, values.password, redirect)
      message.success('登录成功')
    } catch {
      message.error('登录失败，请检查账号和密码')
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

        <div style={styles.brandName}>CloudMart</div>
        <div style={styles.tagline}>探索未来科技生活</div>

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
          <div style={styles.heading}>欢迎回来</div>
          <div style={styles.subHeading}>登录您的宝贝小答账户</div>

          <Form<LoginFormValues>
            name="login"
            onFinish={handleSubmit}
            autoComplete="off"
            initialValues={{ remember: true }}
          >
            <Form.Item
              name="account"
              rules={[{ required: true, message: '请输入小答号或邮箱' }]}
              style={styles.inputWrapper}
            >
              <Input
                prefix={
                  <UserOutlined style={{ color: 'var(--color-text-secondary)', fontSize: '16px' }} />
                }
                placeholder="小答号 / 邮箱"
                style={styles.darkInput}
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
              style={styles.inputWrapper}
            >
              <Input.Password
                prefix={
                  <LockOutlined style={{ color: 'var(--color-text-secondary)', fontSize: '16px' }} />
                }
                placeholder="密码"
                style={styles.darkInput}
              />
            </Form.Item>

            <Form.Item
              name="remember"
              valuePropName="checked"
              style={styles.checkboxWrapper}
            >
              <Checkbox
                style={{ color: 'var(--color-text-secondary)' }}
              >
                <span style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>记住我</span>
              </Checkbox>
            </Form.Item>

            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                style={styles.loginButton}
                onMouseEnter={(e) => {
                  e.currentTarget.style.boxShadow =
                    '0 6px 30px rgba(var(--color-primary-rgb), 0.45)'
                  e.currentTarget.style.transform = 'translateY(-1px)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.boxShadow =
                    '0 4px 20px rgba(var(--color-primary-rgb), 0.3)'
                  e.currentTarget.style.transform = 'translateY(0)'
                }}
              >
                登录
              </Button>
            </Form.Item>
          </Form>

          <div style={styles.footerText}>
            没有账户？{' '}
            <span
              style={styles.linkText}
              onClick={() => history.push('/register')}
              onMouseEnter={(e) => {
                e.currentTarget.style.color = '#33DDFF'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.color = 'var(--color-primary)'
              }}
            >
              立即注册
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
