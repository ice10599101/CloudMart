import { useEffect, useMemo } from 'react'
import { ProLayout } from '@ant-design/pro-components'
import { ConfigProvider, theme, Dropdown, Spin, Alert, Button, App } from 'antd'
import {
  LogoutOutlined,
  DashboardOutlined,
  SettingOutlined,
  ShoppingOutlined,
  MonitorOutlined,
  ToolOutlined,
  UserOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  TagOutlined,
  ThunderboltOutlined,
  ShoppingCartOutlined,
  CommentOutlined,
  InboxOutlined,
  PayCircleOutlined,
  NotificationOutlined,
  AppstoreOutlined,
  RiseOutlined,
  VideoCameraOutlined,
  DatabaseOutlined,
  StopOutlined,
  RobotOutlined,
  ScheduleOutlined,
  CodeOutlined,
  CarOutlined,
  SafetyOutlined,
  CustomerServiceOutlined,
  UploadOutlined,
  CrownOutlined,
  FlagOutlined,
  AlertOutlined,
  TrophyOutlined,
  MessageOutlined,
  StarOutlined,
  HeartOutlined,
  ExperimentOutlined,
  EnvironmentOutlined,
  LockOutlined,
  FireOutlined,
  ApartmentOutlined,
  BookOutlined,
  EditOutlined,
  FileTextOutlined,
  LoginOutlined,
  TagsOutlined,
  ClusterOutlined,
} from '@ant-design/icons'
import { Outlet, history, useLocation } from 'umi'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useAdminMenuStore, toLayoutMenu, type LayoutMenuItem } from '@/stores/adminMenu'
import { useThemeStore } from '@/stores/theme'
import { getThemeTokens, applyCssVariables } from '@/theme/tokens'
import pageStyles from './AdminLayout.module.css'
import AppMessageBinder from '@/components/AppMessageBinder'

interface MenuRouteItem {
  path: string
  name: string
  icon?: React.ReactNode
  routes?: MenuRouteItem[]
}

/**
 * admin_menu.icon 字符串 → antd 图标映射。
 * 新增菜单若 icon 不在映射内，目录回落 AppstoreOutlined、菜单不显示图标。
 */
const MENU_ICON_MAP: Record<string, React.ReactNode> = {
  dashboard: <DashboardOutlined />,
  setting: <SettingOutlined />,
  shopping: <ShoppingOutlined />,
  monitor: <MonitorOutlined />,
  tool: <ToolOutlined />,
  user: <UserOutlined />,
  peoples: <TeamOutlined />,
  'tree-table': <ApartmentOutlined />,
  dict: <BookOutlined />,
  edit: <EditOutlined />,
  log: <FileTextOutlined />,
  form: <FileTextOutlined />,
  logininfor: <LoginOutlined />,
  goods: <TagsOutlined />,
  list: <UnorderedListOutlined />,
  money: <PayCircleOutlined />,
  time: <ThunderboltOutlined />,
  box: <InboxOutlined />,
  message: <MessageOutlined />,
  'shopping-cart': <ShoppingCartOutlined />,
  star: <StarOutlined />,
  heart: <HeartOutlined />,
  comment: <CommentOutlined />,
  trophy: <TrophyOutlined />,
  medal: <TrophyOutlined />,
  music: <CustomerServiceOutlined />,
  flag: <FlagOutlined />,
  tree: <ClusterOutlined />,
  lock: <LockOutlined />,
  shield: <SafetyOutlined />,
  safety: <SafetyOutlined />,
  fire: <FireOutlined />,
  video: <VideoCameraOutlined />,
  map: <EnvironmentOutlined />,
  adjust: <ExperimentOutlined />,
  team: <TeamOutlined />,
  robot: <RobotOutlined />,
  schedule: <ScheduleOutlined />,
  code: <CodeOutlined />,
  car: <CarOutlined />,
  database: <DatabaseOutlined />,
  upload: <UploadOutlined />,
  crown: <CrownOutlined />,
  stop: <StopOutlined />,
  alert: <AlertOutlined />,
  tag: <TagOutlined />,
  rise: <RiseOutlined />,
  'unordered-list': <UnorderedListOutlined />,
  notification: <NotificationOutlined />,
}

/** iconKey → 图标组件；目录缺省回落 AppstoreOutlined，菜单缺省不显示图标 */
function attachIcons(items: LayoutMenuItem[]): MenuRouteItem[] {
  // 注意：叶子节点必须完全省略 routes 键（不能是 routes: undefined），
  // ProLayout 会按"是否存在 routes"区分目录/叶子
  return items.map(({ iconKey, routes, ...rest }) => {
    const item: MenuRouteItem = {
      ...rest,
      icon: iconKey
        ? MENU_ICON_MAP[iconKey] ?? <AppstoreOutlined />
        : routes
          ? <AppstoreOutlined />
          : undefined,
    }
    if (routes) item.routes = attachIcons(routes)
    return item
  })
}

export default function AdminLayout() {
  const location = useLocation()
  const { adminInfo, logout, hasPermission, fetchProfile, accessToken, permissions } = useAdminAuthStore()
  const { menuTree, status: menuStatus, fetchMenus } = useAdminMenuStore()
  const { mode, toggleMode } = useThemeStore()
  const tokens = getThemeTokens(mode)

  useEffect(() => {
    applyCssVariables(tokens)
  }, [mode])

  useEffect(() => {
    if (accessToken && !adminInfo) {
      fetchProfile()
    }
  }, [accessToken, adminInfo, fetchProfile])

  useEffect(() => {
    if (accessToken && adminInfo && menuStatus === 'idle') {
      fetchMenus()
    }
  }, [accessToken, adminInfo, menuStatus, fetchMenus])

  const filteredMenu = useMemo(
    () => attachIcons(toLayoutMenu(menuTree, hasPermission)),
    // permissions 变化时 hasPermission 的判定结果随之变化，需纳入依赖重算
    [menuTree, hasPermission, permissions],
  )

  if (location.pathname === '/admin/login') {
    return <Outlet />
  }

  if (!accessToken) {
    history.replace('/admin/login')
    return null
  }

  const handleLogout = () => {
    logout()
    history.push('/admin/login')
  }

  if (accessToken && !adminInfo) {
    return (
      <ConfigProvider
        theme={{
          algorithm: tokens.isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
          token: { colorPrimary: tokens.colorPrimary, colorBgContainer: tokens.colorBgContainer, colorBgElevated: tokens.colorBgElevated, colorBgLayout: tokens.colorBgBase, colorBorder: tokens.colorBorder, colorText: tokens.colorText, colorTextSecondary: tokens.colorTextSecondary },
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: tokens.colorBgBase }}>
          <Spin size="large" description="加载中..." />
        </div>
      </ConfigProvider>
    )
  }

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
          Card: { colorBgContainer: tokens.colorBgContainer, colorBorderSecondary: tokens.colorBorder },
          Table: { colorBgContainer: tokens.colorBgContainer, headerBg: tokens.colorBgFooter, rowHoverBg: tokens.colorBgElevated },
          Input: { colorBgContainer: tokens.colorBgInput, activeBorderColor: tokens.colorPrimary, hoverBorderColor: tokens.colorPrimary },
          Select: { colorBgContainer: tokens.colorBgInput, optionSelectedBg: `rgba(${tokens.colorPrimaryRgb}, 0.12)` },
          Modal: { contentBg: tokens.colorBgContainer, headerBg: tokens.colorBgContainer },
          Menu: {
            darkItemBg: 'transparent',
            darkItemSelectedBg: `rgba(${tokens.colorPrimaryRgb}, 0.12)`,
            darkItemHoverBg: tokens.colorBorder,
            darkItemColor: tokens.colorTextSecondary,
            darkItemSelectedColor: tokens.colorPrimary,
          },
          Statistic: { titleFontSize: 14 },
          Descriptions: { labelBg: tokens.colorBgFooter },
          Progress: { remainingColor: tokens.colorBorder },
        },
      }}
    >
      <App>
        <AppMessageBinder />
      <ProLayout
        // ProLayout 仅在首次渲染时从 route 初始化菜单数据，之后 route 更新不生效；
        // 菜单是异步加载的，必须以 menuStatus 为 key 强制在数据就绪后重挂载
        key={menuStatus}
        title="宝贝小答"
        logo={null}
        layout="mix"
        fixedHeader
        fixSiderbar
        location={{ pathname: location.pathname }}
        route={{ routes: filteredMenu }}
        token={{
          header: {
            colorBgHeader: tokens.colorBgBase,
            colorHeaderTitle: tokens.colorText,
            colorTextMenu: tokens.colorTextSecondary,
            colorTextMenuSecondary: tokens.colorTextSecondary,
            colorTextMenuSelected: tokens.colorPrimary,
            colorBgMenuItemSelected: `rgba(${tokens.colorPrimaryRgb}, 0.12)`,
            colorTextMenuActive: `rgba(${tokens.colorPrimaryRgb}, 0.12)`,
            colorTextRightActionsItem: tokens.colorTextSecondary,
          },
          sider: {
            colorMenuBackground: tokens.colorBgFooter,
            colorMenuItemDivider: tokens.colorBorder,
            colorTextMenu: tokens.colorTextSecondary,
            colorTextMenuSelected: tokens.colorPrimary,
            colorBgMenuItemSelected: `rgba(${tokens.colorPrimaryRgb}, 0.12)`,
            colorTextMenuActive: `rgba(${tokens.colorPrimaryRgb}, 0.12)`,
            colorTextMenuSecondary: tokens.colorTextTertiary,
          },
          pageContainer: {
            colorBgPageContainer: tokens.colorBgBase,
            paddingInlinePageContainerContent: 24,
          },
        }}
        contentStyle={{ background: tokens.colorBgBase }}
        actionsRender={() => [
          <button
            key="theme-toggle"
            type="button"
            onClick={toggleMode}
            style={{
              width: 36,
              height: 36,
              borderRadius: '50%',
              border: `1px solid ${tokens.colorBorder}`,
              background: tokens.colorBgInput,
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
              e.currentTarget.style.borderColor = tokens.colorPrimary
              e.currentTarget.style.boxShadow = `0 0 12px rgba(${tokens.colorPrimaryRgb}, 0.3)`
              e.currentTarget.style.transform = 'scale(1.1)'
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = tokens.colorBorder
              e.currentTarget.style.boxShadow = 'none'
              e.currentTarget.style.transform = 'scale(1)'
            }}
          >
            {mode === 'ocean' ? '🌸' : '🌊'}
          </button>,
        ]}
        menuItemRender={(item, dom) => (
          <a
            onClick={() => {
              if (item.path) history.push(item.path)
            }}
          >
            {dom}
          </a>
        )}
        subMenuItemRender={(item, dom) => <span>{dom}</span>}
        avatarProps={{
          src: adminInfo?.avatar,
          title: adminInfo?.username ?? '管理员',
          size: 'small',
          render: (_props, dom) => (
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'logout',
                    icon: <LogoutOutlined />,
                    label: '退出登录',
                    onClick: handleLogout,
                  },
                ],
              }}
              placement="bottomRight"
            >
              {dom}
            </Dropdown>
          ),
        }}
        headerTitleRender={(logo, title) => (
          <a
            onClick={() => history.push('/admin/dashboard')}
            style={{ display: 'flex', alignItems: 'center', gap: 10 }}
          >
            {logo}
            <span style={{ color: tokens.colorPrimary, fontWeight: 700, fontSize: 18, letterSpacing: 1 }}>
              {title}
            </span>
          </a>
        )}
      >
        <div key={location.pathname} className={pageStyles.pageTransitionWrap}>
          {menuStatus === 'error' && (
            <Alert
              type="error"
              showIcon
              message="侧边栏菜单加载失败"
              description="菜单由后端（mall-admin /admin/menus/tree）下发，加载失败时侧边栏为空。可点击重试，或检查 mall-admin 服务状态。"
              action={
                <Button size="small" danger onClick={() => useAdminMenuStore.getState().fetchMenus()}>
                  重试
                </Button>
              }
              style={{ marginBottom: 16 }}
            />
          )}
          <Outlet />
        </div>
      </ProLayout>
      </App>
    </ConfigProvider>
  )
}
