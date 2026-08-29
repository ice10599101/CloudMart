import { useEffect, useMemo } from 'react'
import { ProLayout } from '@ant-design/pro-components'
import { ConfigProvider, theme, Dropdown, Spin, App } from 'antd'
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
  PlayCircleOutlined,
} from '@ant-design/icons'
import { Outlet, history, useLocation } from 'umi'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useThemeStore } from '@/stores/theme'
import { getThemeTokens, applyCssVariables } from '@/theme/tokens'
import pageStyles from './AdminLayout.module.css'

interface MenuRouteItem {
  path: string
  name: string
  icon?: React.ReactNode
  permission?: string
  routes?: MenuRouteItem[]
}

const MENU_CONFIG: MenuRouteItem[] = [
  {
    path: '/admin/dashboard',
    name: '工作台',
    icon: <DashboardOutlined />,
  },
  {
    path: '/admin/system',
    name: '系统管理',
    icon: <SettingOutlined />,
    routes: [
      { path: '/admin/system/users', name: '用户管理', icon: <UserOutlined />, permission: 'system:user:list' },
      { path: '/admin/system/roles', name: '角色管理', icon: <TeamOutlined />, permission: 'system:role:list' },
      { path: '/admin/system/menus', name: '菜单管理', icon: <UnorderedListOutlined />, permission: 'system:menu:list' },
      { path: '/admin/system/depts', name: '部门管理', icon: <TeamOutlined />, permission: 'system:dept:list' },
      { path: '/admin/system/posts', name: '岗位管理', icon: <UserOutlined />, permission: 'system:post:list' },
      { path: '/admin/system/dict', name: '字典管理', icon: <TagOutlined />, permission: 'system:dict:list' },
      { path: '/admin/system/config', name: '参数设置', icon: <SettingOutlined />, permission: 'system:config:list' },
      { path: '/admin/system/notices', name: '通知公告', icon: <NotificationOutlined />, permission: 'system:notice:list' },
      { path: '/admin/system/oper-log', name: '操作日志', icon: <UnorderedListOutlined />, permission: 'system:operlog:list' },
      { path: '/admin/system/login-log', name: '登录日志', icon: <UnorderedListOutlined />, permission: 'system:loginlog:list' },
    ],
  },
  {
    path: '/admin/business',
    name: '业务管理',
    icon: <ShoppingOutlined />,
    routes: [
      { path: '/admin/business/products', name: '商品管理', icon: <ShoppingOutlined />, permission: 'business:product:list' },
      { path: '/admin/business/categories', name: '分类管理', icon: <AppstoreOutlined />, permission: 'business:category:list' },
      { path: '/admin/business/orders', name: '订单管理', icon: <UnorderedListOutlined />, permission: 'business:order:list' },
      { path: '/admin/business/members', name: '会员管理', icon: <UserOutlined />, permission: 'business:member:list' },
      { path: '/admin/business/coupons', name: '优惠券管理', icon: <TagOutlined />, permission: 'business:coupon:list' },
      { path: '/admin/business/seckill', name: '秒杀管理', icon: <ThunderboltOutlined />, permission: 'business:seckill:list' },
      { path: '/admin/business/cart', name: '购物车管理', icon: <ShoppingCartOutlined />, permission: 'business:cart:list' },
      { path: '/admin/business/reviews', name: '评价管理', icon: <CommentOutlined />, permission: 'business:review:list' },
      { path: '/admin/business/inventory', name: '库存管理', icon: <InboxOutlined />, permission: 'business:inventory:list' },
      { path: '/admin/business/payments', name: '支付管理', icon: <PayCircleOutlined />, permission: 'business:payment:list' },
      { path: '/admin/business/notifications', name: '通知管理', icon: <NotificationOutlined />, permission: 'business:notification:list' },
      { path: '/admin/business/group-activity', name: '拼团活动', icon: <TeamOutlined />, permission: 'business:group:list' },
      { path: '/admin/business/tiered-promotion', name: '阶梯促销', icon: <RiseOutlined />, permission: 'business:promotion:list' },
      { path: '/admin/business/live', name: '直播管理', icon: <VideoCameraOutlined />, permission: 'business:live:list' },
      { path: '/admin/business/wms', name: '仓储管理', icon: <DatabaseOutlined />, permission: 'business:wms:list' },
      { path: '/admin/business/blacklist', name: '黑名单管理', icon: <StopOutlined />, permission: 'business:blacklist:list' },
      { path: '/admin/business/ai', name: 'AI 管理', icon: <RobotOutlined />, permission: 'business:ai:list' },
      { path: '/admin/business/brands', name: '品牌管理', icon: <CrownOutlined />, permission: 'business:brand:list' },
      { path: '/admin/business/risk-records', name: '风控记录', icon: <SafetyOutlined />, permission: 'business:risk:list' },
      { path: '/admin/business/risk-rules', name: '风控规则', icon: <SafetyOutlined />, permission: 'business:risk:list' },
      { path: '/admin/business/shipping', name: '物流管理', icon: <CarOutlined />, permission: 'business:shipping:list' },
      { path: '/admin/business/warehouses', name: '仓库管理', icon: <DatabaseOutlined />, permission: 'business:warehouse:list' },
      { path: '/admin/business/file-upload', name: '文件管理', icon: <UploadOutlined />, permission: 'business:file:list' },
      { path: '/admin/business/wishes', name: '心愿管理', icon: <StarOutlined />, permission: 'business:wish:list' },
      { path: '/admin/business/wish-categories', name: '心愿分类', icon: <StarOutlined />, permission: 'business:wishCategory:list' },
      { path: '/admin/business/wish-interactions', name: '互动记录', icon: <HeartOutlined />, permission: 'business:wishInteraction:list' },
      { path: '/admin/business/wish-comments', name: '心愿评论', icon: <CommentOutlined />, permission: 'business:wishComment:list' },
      { path: '/admin/business/wish-badges', name: '徽章管理', icon: <TrophyOutlined />, permission: 'business:wishBadge:list' },
      { path: '/admin/business/wish-bgm', name: '背景音乐', icon: <CustomerServiceOutlined />, permission: 'business:wishBgm:list' },
      { path: '/admin/business/wish-ai', name: 'AI 心愿助手', icon: <RobotOutlined />, permission: 'business:aiPrompt:list' },
      { path: '/admin/business/match', name: '同路人小队', icon: <TeamOutlined />, permission: 'business:matchGroup:list' },
      { path: '/admin/business/legacy', name: '传承与排行榜', icon: <TrophyOutlined />, permission: 'business:legacy:list' },
      { path: '/admin/business/grayscale', name: '灰度控制台', icon: <ExperimentOutlined />, permission: 'business:grayscale:list' },
      { path: '/admin/business/map-audit', name: 'LBS 隐私审计', icon: <EnvironmentOutlined />, permission: 'business:map:audit' },
      { path: '/admin/business/live-widget', name: '直播挂件配置', icon: <PlayCircleOutlined />, permission: 'business:liveWidget:list' },
    ],
  },
  {
    path: '/admin/community',
    name: '社区管理',
    icon: <FlagOutlined />,
    routes: [
      { path: '/admin/community/posts', name: '帖子管理', icon: <UnorderedListOutlined />, permission: 'community:post:list' },
      { path: '/admin/community/review', name: '内容审核', icon: <SafetyOutlined />, permission: 'community:review:list' },
      { path: '/admin/community/reports', name: '举报管理', icon: <AlertOutlined />, permission: 'community:report:list' },
      { path: '/admin/community/comments', name: '评论管理', icon: <CommentOutlined />, permission: 'community:comment:list' },
      { path: '/admin/community/tags', name: '标签管理', icon: <TagOutlined />, permission: 'community:tag:list' },
      { path: '/admin/community/badges', name: '徽章管理', icon: <TrophyOutlined />, permission: 'community:badge:list' },
      { path: '/admin/community/growth', name: '成长配置', icon: <RiseOutlined />, permission: 'community:growth:list' },
      { path: '/admin/community/notifications', name: '通知发送', icon: <NotificationOutlined />, permission: 'community:notification:list' },
      { path: '/admin/community/chat', name: '聊天管理', icon: <MessageOutlined />, permission: 'community:chat:list' },
    ],
  },
  {
    path: '/admin/monitor',
    name: '监控管理',
    icon: <MonitorOutlined />,
    routes: [
      { path: '/admin/monitor/job', name: '定时任务', icon: <ScheduleOutlined />, permission: 'monitor:job:list' },
      { path: '/admin/monitor/server', name: '服务监控', icon: <MonitorOutlined />, permission: 'monitor:server:list' },
      { path: '/admin/monitor/cache', name: '缓存监控', icon: <DatabaseOutlined />, permission: 'monitor:cache:list' },
      { path: '/admin/monitor/online', name: '在线用户', icon: <UserOutlined />, permission: 'monitor:online:list' },
    ],
  },
  {
    path: '/admin/tool',
    name: '工具',
    icon: <ToolOutlined />,
    routes: [
      { path: '/admin/tool/gen', name: '代码生成', icon: <CodeOutlined />, permission: 'tool:gen:list' },
    ],
  },
]

function filterMenuByPermission(
  items: MenuRouteItem[],
  hasPermission: (perm: string) => boolean,
): MenuRouteItem[] {
  return items
    .map((item) => {
      if (item.routes) {
        const filteredChildren = filterMenuByPermission(item.routes, hasPermission)
        if (filteredChildren.length === 0) return null
        return { ...item, routes: filteredChildren }
      }
      if (item.permission && !hasPermission(item.permission)) return null
      return item
    })
    .filter((item): item is MenuRouteItem => item !== null)
}

export default function AdminLayout() {
  const location = useLocation()
  const { adminInfo, logout, hasPermission, fetchProfile, accessToken, permissions } = useAdminAuthStore()
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

  const filteredMenu = useMemo(
    () => filterMenuByPermission(MENU_CONFIG, hasPermission),
    [hasPermission, permissions],
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
      <ProLayout
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
          <Outlet />
        </div>
      </ProLayout>
      </App>
    </ConfigProvider>
  )
}
