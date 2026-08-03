import { useState, useEffect, type CSSProperties } from 'react'
import { ConfigProvider, theme, Input, Badge, Avatar, Dropdown, App } from 'antd'
import {
  ShoppingCartOutlined,
  BellOutlined,
  UserOutlined,
  AppstoreOutlined,
  VideoCameraOutlined,
  LogoutOutlined,
  ProfileOutlined,
  UnorderedListOutlined,
  RobotOutlined,
  SearchOutlined,
  MessageOutlined,
  PlusCircleOutlined,
  CompassOutlined,
  CalendarOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { Outlet, history, useLocation } from 'umi'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useNotificationStore } from '@/stores/notification'
import { useThemeStore } from '@/stores/theme'
import type { ThemeMode } from '@/stores/theme'
import { getThemeTokens, applyCssVariables } from '@/theme/tokens'
import type { ThemeTokens } from '@/theme/tokens'
import CheckInModal from '@/components/CheckInModal'
import pageStyles from './UserLayout.module.css'

const NAV_ITEMS = [
  { key: '/', label: '首页', icon: <CompassOutlined /> },
  { key: '/products', label: '商城', icon: <AppstoreOutlined /> },
  { key: '/live', label: '直播', icon: <VideoCameraOutlined /> },
  { key: '/messages', label: '消息', icon: <MessageOutlined /> },
  { key: '/profile', label: '我的', icon: <UserOutlined /> },
  { key: '/publish', label: '发布', icon: <PlusCircleOutlined />, isCenter: true },
]

const FOOTER_COLUMNS = [
  {
    title: '关于我们',
    links: [
      { label: '平台介绍', href: '/' },
      { label: '社区规范', href: '/' },
      { label: '常见问题', href: '/' },
      { label: '联系客服', href: '/' },
    ],
  },
  {
    title: '创作者',
    links: [
      { label: '创作指南', href: '/' },
      { label: '成长体系', href: '/' },
      { label: '创作者激励', href: '/' },
      { label: '认证申请', href: '/' },
    ],
  },
  {
    title: '商城服务',
    links: [
      { label: '购物流程', href: '/' },
      { label: '配送说明', href: '/' },
      { label: '退换政策', href: '/' },
      { label: '支付方式', href: '/' },
    ],
  },
  {
    title: '更多',
    links: [
      { label: '直播', href: '/live' },
      { label: 'AI助手', href: '/ai-chat' },
      { label: '隐私政策', href: '/' },
      { label: '用户协议', href: '/' },
    ],
  },
]

function getSelectedKey(pathname: string): string {
  if (pathname === '/' || pathname === '') return '/'
  const matched = NAV_ITEMS.find((item) => item.key !== '/' && pathname.startsWith(item.key))
  return matched?.key ?? '/'
}

function buildStyles(t: ThemeTokens) {
  return {
    layout: {
      display: 'flex' as const,
      flexDirection: 'column' as const,
      minHeight: '100vh',
      background: t.colorBgBase,
    },
    header: {
      position: 'sticky' as const,
      top: 0,
      zIndex: 1000,
      height: 64,
      display: 'flex',
      alignItems: 'center',
      padding: '0 32px',
      background: t.colorBgHeader,
      backdropFilter: 'blur(24px) saturate(180%)',
      WebkitBackdropFilter: 'blur(24px) saturate(180%)',
      borderBottom: `1px solid ${t.colorBorder}`,
    },
    logo: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      cursor: 'pointer',
      marginRight: 40,
      flexShrink: 0,
    },
    logoIcon: {
      fontSize: 26,
      color: t.colorPrimary,
      filter: `drop-shadow(0 0 8px ${t.colorPrimaryGlow})`,
    },
    logoText: {
      fontSize: 22,
      fontWeight: 800,
      letterSpacing: '1.5px',
      color: t.colorPrimary,
      textShadow: `0 0 20px ${t.colorPrimaryGlow}, 0 0 40px rgba(${t.colorPrimaryRgb}, 0.15)`,
      whiteSpace: 'nowrap' as const,
      userSelect: 'none' as const,
    },
    nav: {
      display: 'flex',
      alignItems: 'center',
      gap: 4,
      flexShrink: 0,
    },
    navItem: (isActive: boolean): CSSProperties => ({
      display: 'flex',
      alignItems: 'center',
      gap: 6,
      padding: '8px 16px',
      fontSize: 14,
      fontWeight: isActive ? 600 : 400,
      color: isActive ? t.colorPrimary : t.colorTextSecondary,
      cursor: 'pointer',
      borderRadius: 6,
      transition: 'all 0.25s ease',
      position: 'relative',
      whiteSpace: 'nowrap' as const,
      background: isActive ? `rgba(${t.colorPrimaryRgb}, 0.15)` : 'transparent',
      textShadow: isActive ? `0 0 12px rgba(${t.colorPrimaryRgb}, 0.3)` : 'none',
    }),
    navItemUnderline: {
      position: 'absolute' as const,
      bottom: 0,
      left: '50%',
      transform: 'translateX(-50%)',
      width: '60%',
      height: 2,
      background: t.colorPrimary,
      borderRadius: 1,
      boxShadow: `0 0 8px ${t.colorPrimaryGlow}`,
    },
    searchWrapper: {
      flex: 1,
      display: 'flex',
      justifyContent: 'center',
      padding: '0 24px',
      maxWidth: 480,
      margin: '0 auto',
    },
    actions: {
      display: 'flex',
      alignItems: 'center',
      gap: 20,
      flexShrink: 0,
      marginLeft: 24,
    },
    iconBtn: {
      fontSize: 20,
      color: t.colorTextSecondary,
      cursor: 'pointer',
      transition: 'all 0.25s ease',
      padding: 6,
      borderRadius: 6,
    },
    loginBtn: {
      padding: '6px 20px',
      fontSize: 13,
      fontWeight: 600,
      color: t.colorBgBase,
      background: t.colorGradientPrimary,
      border: 'none',
      borderRadius: 6,
      cursor: 'pointer',
      letterSpacing: '0.5px',
      boxShadow: `0 2px 12px rgba(${t.colorPrimaryRgb}, 0.3)`,
      transition: 'all 0.25s ease',
      whiteSpace: 'nowrap' as const,
    },
    avatar: {
      cursor: 'pointer',
      border: `2px solid ${t.colorBorder}`,
      transition: 'border-color 0.25s ease',
    },
    content: {
      flex: 1,
      background: t.colorBgBase,
      padding: '0',
    },
    footer: {
      background: t.colorBgFooter,
      borderTop: `1px solid ${t.colorBorder}`,
      padding: '64px 32px 32px',
    },
    footerInner: {
      maxWidth: 1280,
      margin: '0 auto',
    },
    footerGrid: {
      display: 'grid',
      gridTemplateColumns: 'repeat(4, 1fr)',
      gap: 40,
      marginBottom: 48,
    },
    footerColTitle: {
      fontSize: 15,
      fontWeight: 600,
      color: t.colorText,
      marginBottom: 20,
      letterSpacing: '0.5px',
    },
    footerLink: {
      display: 'block',
      fontSize: 13,
      color: t.colorTextTertiary,
      textDecoration: 'none',
      marginBottom: 12,
      cursor: 'pointer',
      transition: 'color 0.2s ease',
    },
    footerDivider: {
      height: 1,
      background: t.isDark ? 'var(--color-border)' : 'rgba(0, 0, 0, 0.06)',
      marginBottom: 24,
    },
    footerBottom: {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      flexWrap: 'wrap' as const,
      gap: 12,
    },
    footerCopyright: {
      fontSize: 13,
      color: t.colorTextTertiary,
    },
    footerBrand: {
      fontSize: 14,
      fontWeight: 700,
      color: t.colorPrimary,
      textShadow: `0 0 12px rgba(${t.colorPrimaryRgb}, 0.3)`,
      letterSpacing: '1px',
    },
    themeToggle: {
      width: 36,
      height: 36,
      borderRadius: '50%',
      border: `1px solid ${t.colorBorder}`,
      background: t.colorBgInput,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      cursor: 'pointer',
      transition: 'all 0.3s ease',
      fontSize: 18,
      flexShrink: 0,
      padding: 0,
      lineHeight: 1,
    },
  }
}

function ThemeToggle({ mode, onToggle }: { mode: ThemeMode; onToggle: () => void }) {
  const t = getThemeTokens(mode)
  return (
    <button
      onClick={onToggle}
      style={{
        width: 36,
        height: 36,
        borderRadius: '50%',
        border: `1px solid ${t.colorBorder}`,
        background: t.colorBgInput,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        fontSize: 18,
        flexShrink: 0,
        padding: 0,
        lineHeight: 1,
      }}
      title={mode === 'ocean' ? '切换到樱花粉' : '切换到深海蓝'}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = t.colorPrimary
        e.currentTarget.style.boxShadow = `0 0 12px rgba(${t.colorPrimaryRgb}, 0.3)`
        e.currentTarget.style.transform = 'scale(1.1)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = t.colorBorder
        e.currentTarget.style.boxShadow = 'none'
        e.currentTarget.style.transform = 'scale(1)'
      }}
    >
      {mode === 'ocean' ? '🌸' : '🌊'}
    </button>
  )
}

export default function UserLayout() {
  const location = useLocation()
  const { isAuthenticated, user, logout, accessToken } = useAuthStore()
  const { totalCount, fetchCart } = useCartStore()
  const { unreadCount, connect, disconnect, fetchUnreadCount } = useNotificationStore()
  const { mode, toggleMode } = useThemeStore()
  const [searchValue, setSearchValue] = useState('')
  const [hoveredNav, setHoveredNav] = useState<string | null>(null)
  const [avatarHovered, setAvatarHovered] = useState(false)
  const [checkInVisible, setCheckInVisible] = useState(false)

  const tokens = getThemeTokens(mode)
  const styles = buildStyles(tokens)

  useEffect(() => {
    applyCssVariables(tokens)
  }, [mode])

  useEffect(() => {
    if (isAuthenticated) {
      fetchCart()
      fetchUnreadCount()
      if (accessToken) {
        connect(accessToken)
      }
    } else {
      disconnect()
    }
    return () => {
      disconnect()
    }
  }, [isAuthenticated, fetchCart, fetchUnreadCount, connect, disconnect, accessToken])

  const handleSearch = (value: string) => {
    if (value.trim()) {
      history.push(`/search?q=${encodeURIComponent(value.trim())}`)
    }
  }

  const handleLogout = async () => {
    await logout()
  }

  const userMenuItems = [
    {
      key: 'profile',
      icon: <ProfileOutlined />,
      label: '个人中心',
      onClick: () => history.push('/profile'),
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '设置',
      onClick: () => history.push('/settings'),
    },
    {
      key: 'orders',
      icon: <UnorderedListOutlined />,
      label: '我的订单',
      onClick: () => history.push('/orders'),
    },
    {
      type: 'divider' as const,
      key: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ]

  const selectedKey = getSelectedKey(location.pathname)

  return (
    <ConfigProvider
      theme={{
        algorithm: tokens.isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: tokens.colorPrimary,
          colorBgContainer: tokens.colorBgContainer,
          colorBgElevated: tokens.colorBgElevated,
          colorBgLayout: tokens.colorBgBase,
          colorBorder: tokens.colorBorder,
          colorText: tokens.colorText,
          colorTextSecondary: tokens.colorTextSecondary,
          colorTextTertiary: tokens.colorTextTertiary,
          colorFillSecondary: tokens.isDark ? 'var(--color-border)' : 'rgba(0,0,0,0.04)',
          borderRadius: 8,
          fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, sans-serif",
        },
        components: {
          Card: { colorBgContainer: tokens.colorBgContainer, colorBorderSecondary: tokens.isDark ? 'var(--color-border)' : 'rgba(0,0,0,0.06)' },
          Table: { colorBgContainer: tokens.colorBgContainer, headerBg: tokens.colorBgFooter, rowHoverBg: tokens.colorBgElevated },
          Input: { colorBgContainer: tokens.colorBgInput, activeBorderColor: tokens.colorPrimary, hoverBorderColor: tokens.colorPrimary },
          Select: { colorBgContainer: tokens.colorBgInput, optionSelectedBg: `rgba(${tokens.colorPrimaryRgb}, 0.12)` },
          Modal: { contentBg: tokens.colorBgContainer, headerBg: tokens.colorBgContainer },
          Tabs: { inkBarColor: tokens.colorPrimary, itemActiveColor: tokens.colorPrimary, itemSelectedColor: tokens.colorPrimary },
          Tag: { defaultBg: tokens.isDark ? 'var(--color-border)' : 'rgba(0,0,0,0.04)' },
        },
      }}
    >
    <App>
    <div style={styles.layout}>
      <header style={styles.header}>
        <div style={styles.logo} onClick={() => history.push('/')}>
          <AppstoreOutlined style={styles.logoIcon} />
          <span style={styles.logoText}>宝贝小答</span>
        </div>

        <nav style={styles.nav}>
          {NAV_ITEMS.map((item) => {
            const isActive = selectedKey === item.key
            const isHovered = hoveredNav === item.key
            return (
              <div
                key={item.key}
                style={{
                  ...styles.navItem(isActive),
                  ...(isHovered && !isActive
                    ? {
                        color: tokens.colorText,
                        background: tokens.isDark ? 'var(--color-border)' : 'rgba(0, 0, 0, 0.04)',
                      }
                    : {}),
                }}
                onClick={() => history.push(item.key)}
                onMouseEnter={() => setHoveredNav(item.key)}
                onMouseLeave={() => setHoveredNav(null)}
              >
                <span style={{ fontSize: 15, display: 'flex', alignItems: 'center' }}>{item.icon}</span>
                {item.key === '/messages' ? (
                  <Badge dot={unreadCount > 0} color={tokens.colorAccentRed} offset={[2, 0]}>
                    <span style={{ color: 'var(--color-text-secondary)' }}>{item.label}</span>
                  </Badge>
                ) : (
                  <span>{item.label}</span>
                )}
                {isActive && <div style={styles.navItemUnderline} />}
              </div>
            )
          })}
        </nav>

        <div style={styles.searchWrapper}>
          <Input
            placeholder="搜索商品..."
            prefix={<SearchOutlined style={{ color: tokens.colorTextTertiary, fontSize: 14 }} />}
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            onPressEnter={() => handleSearch(searchValue)}
            allowClear
            style={{
              height: 38,
              borderRadius: 16,
              background: tokens.colorBgInput,
              border: `1px solid ${tokens.colorBorder}`,
              color: tokens.colorText,
            }}
          />
        </div>

        <div style={styles.actions}>
          <ThemeToggle mode={mode} onToggle={toggleMode} />

          <Badge count={totalCount} size="small" offset={[2, -2]} color={tokens.colorPrimary}>
            <ShoppingCartOutlined
              style={styles.iconBtn}
              onClick={() => history.push('/cart')}
              onMouseEnter={(e) => {
                e.currentTarget.style.color = tokens.colorPrimary
                e.currentTarget.style.textShadow = `0 0 12px rgba(${tokens.colorPrimaryRgb}, 0.4)`
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.color = tokens.colorTextSecondary
                e.currentTarget.style.textShadow = 'none'
              }}
            />
          </Badge>

          <RobotOutlined
            style={styles.iconBtn}
            onClick={() => history.push('/ai-chat')}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = tokens.colorPrimary
              e.currentTarget.style.textShadow = `0 0 12px rgba(${tokens.colorPrimaryRgb}, 0.4)`
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = tokens.colorTextSecondary
              e.currentTarget.style.textShadow = 'none'
            }}
          />

          <Badge count={unreadCount} size="small" offset={[2, -2]} color={tokens.colorAccentRed}>
            <BellOutlined
              style={styles.iconBtn}
              onClick={() => history.push('/profile')}
              onMouseEnter={(e) => {
                e.currentTarget.style.color = tokens.colorPrimary
                e.currentTarget.style.textShadow = `0 0 12px rgba(${tokens.colorPrimaryRgb}, 0.4)`
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.color = tokens.colorTextSecondary
                e.currentTarget.style.textShadow = 'none'
              }}
            />
          </Badge>

          {isAuthenticated ? (
            <>
              <CalendarOutlined
                style={styles.iconBtn}
                onClick={() => setCheckInVisible(true)}
                onMouseEnter={(e) => {
                  e.currentTarget.style.color = tokens.colorPrimary
                  e.currentTarget.style.textShadow = `0 0 12px rgba(${tokens.colorPrimaryRgb}, 0.4)`
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.color = tokens.colorTextSecondary
                  e.currentTarget.style.textShadow = 'none'
                }}
              />
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" trigger={['click']}>
                <span
                  style={{ display: 'inline-flex', cursor: 'pointer' }}
                  onMouseEnter={() => setAvatarHovered(true)}
                  onMouseLeave={() => setAvatarHovered(false)}
                >
                  <Avatar
                    icon={<UserOutlined />}
                    src={user?.avatar}
                    style={{
                      ...styles.avatar,
                      borderColor: avatarHovered ? tokens.colorPrimary : tokens.colorBorder,
                    }}
                  />
                </span>
              </Dropdown>
            </>
          ) : (
            <button
              style={styles.loginBtn}
              onClick={() => history.push('/login')}
              onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = `0 4px 24px rgba(${tokens.colorPrimaryRgb}, 0.5)`
                e.currentTarget.style.transform = 'translateY(-1px)'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = `0 2px 12px rgba(${tokens.colorPrimaryRgb}, 0.3)`
                e.currentTarget.style.transform = 'translateY(0)'
              }}
            >
              登录
            </button>
          )}
        </div>
      </header>

      <main style={styles.content}>
        <div key={location.pathname} className={pageStyles.pageTransitionWrap}>
          <Outlet />
        </div>
      </main>

      <footer style={styles.footer}>
        <div style={styles.footerInner}>
          <div style={styles.footerGrid}>
            {FOOTER_COLUMNS.map((col) => (
              <div key={col.title}>
                <div style={styles.footerColTitle}>{col.title}</div>
                {col.links.map((link) => (
                  <a
                    key={link.label}
                    style={styles.footerLink}
                    href={link.href}
                    onClick={(e) => {
                      e.preventDefault()
                      history.push(link.href)
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.color = tokens.colorPrimary
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.color = tokens.colorTextTertiary
                    }}
                  >
                    {link.label}
                  </a>
                ))}
              </div>
            ))}
          </div>

          <div style={styles.footerDivider} />

          <div style={styles.footerBottom}>
            <span style={styles.footerBrand}>宝贝小答</span>
            <span style={styles.footerCopyright}>
              © {new Date().getFullYear()} 宝贝小答 · 保留所有权利
            </span>
          </div>
        </div>
      </footer>
    </div>
    <CheckInModal visible={checkInVisible} onClose={() => setCheckInVisible(false)} />
    </App>
    </ConfigProvider>
  )
}
