import { LoginForm, ProFormText } from '@ant-design/pro-components'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { ConfigProvider, theme, message } from 'antd'
import { history, useLocation } from 'umi'
import { useAdminAuthStore } from '@/stores/adminAuth'

export default function AdminLogin() {
  const login = useAdminAuthStore((s) => s.login)
  const location = useLocation()

  const searchParams = new URLSearchParams(location.search)
  const redirect = searchParams.get('redirect') || '/admin/dashboard'

  const handleSubmit = async (values: Record<string, any>) => {
    try {
      await login(values.username, values.password)
      message.success('登录成功')
      history.push(redirect)
    } catch {
      message.error('登录失败，请检查用户名和密码')
    }
  }

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
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        background: 'var(--color-bg-base)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: '-20%',
          left: '-10%',
          width: 500,
          height: 500,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.12) 0%, transparent 70%)',
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: '-15%',
          right: '-5%',
          width: 400,
          height: 400,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.12) 0%, transparent 70%)',
          pointerEvents: 'none',
        }}
      />

      <div
        style={{
          display: 'flex',
          width: 920,
          minHeight: 540,
          borderRadius: 16,
          overflow: 'hidden',
          boxShadow: '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 30px rgba(var(--color-primary-rgb), 0.12)',
          border: '1px solid var(--color-border)',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <div
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
            background: 'linear-gradient(135deg, var(--color-bg-base) 0%, var(--color-bg-container) 50%, var(--color-bg-base) 100%)',
            padding: '48px 40px',
            color: 'var(--color-text-secondary)',
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              position: 'absolute',
              top: '30%',
              left: '20%',
              width: 200,
              height: 200,
              borderRadius: '50%',
              background: 'radial-gradient(circle, rgba(var(--color-primary-rgb), 0.12) 0%, transparent 70%)',
              pointerEvents: 'none',
            }}
          />
          <div
            style={{
              fontSize: 48,
              fontWeight: 700,
              marginBottom: 16,
              letterSpacing: 2,
              color: 'var(--color-primary)',
              textShadow: '0 0 30px rgba(var(--color-primary-rgb), 0.12)',
            }}
          >
            CloudMart
          </div>
          <div
            style={{
              fontSize: 18,
              opacity: 0.85,
              textAlign: 'center',
              lineHeight: 1.8,
              color: 'var(--color-text-secondary)',
            }}
          >
            智慧电商管理平台
          </div>
          <div
            style={{
              fontSize: 14,
              color: 'var(--color-text-tertiary)',
              marginTop: 32,
              textAlign: 'center',
              lineHeight: 1.8,
            }}
          >
            全链路电商运营管理系统
            <br />
            商品 · 订单 · 营销 · 数据
          </div>
        </div>

        <div
          style={{
            flex: 1,
            background: 'var(--color-bg-footer)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            padding: '48px 40px',
          }}
        >
          <LoginForm
            title="管理后台"
            subTitle="宝贝小答管理后台"
            onFinish={handleSubmit}
          >
            <ProFormText
              name="username"
              fieldProps={{
                size: 'large',
                prefix: <UserOutlined />,
              }}
              placeholder="用户名"
              rules={[
                { required: true, message: '请输入用户名' },
              ]}
            />
            <ProFormText.Password
              name="password"
              fieldProps={{
                size: 'large',
                prefix: <LockOutlined />,
              }}
              placeholder="密码"
              rules={[
                { required: true, message: '请输入密码' },
              ]}
            />
            <div
              style={{
                marginBottom: 24,
                display: 'flex',
                justifyContent: 'space-between',
              }}
            >
              <a style={{ color: 'var(--color-primary)' }}>忘记密码？</a>
            </div>
          </LoginForm>
        </div>
      </div>
    </div>
    </ConfigProvider>
  )
}
