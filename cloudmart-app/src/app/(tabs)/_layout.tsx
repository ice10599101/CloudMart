import { Tabs } from 'expo-router'
import { View, Text, Platform } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize } from '@/constants/theme'

function TabIcon({ name, focused, theme }: { name: string; focused: boolean; theme: any }) {
  const icons: Record<string, string> = {
    index: '🏠',
    mall: '🛍️',
    publish: '✏️',
    message: '💬',
    mine: '👤',
  }
  return (
    <View style={{ alignItems: 'center', paddingTop: Spacing.sm }}>
      <Text style={{ fontSize: 20 }}>{icons[name] || '📋'}</Text>
      <Text
        style={{
          fontSize: FontSize.xs,
          color: focused ? theme.tabBarActive : theme.tabBarInactive,
          marginTop: 2,
        }}
      >
        {name === 'index' ? '首页' : name === 'mall' ? '商城' : name === 'publish' ? '发布' : name === 'message' ? '消息' : '我的'}
      </Text>
    </View>
  )
}

export default function TabLayout() {
  const theme = useTheme()

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarStyle: {
          backgroundColor: theme.tabBarBg,
          borderTopColor: theme.border,
          borderTopWidth: 1,
          height: Platform.OS === 'ios' ? 85 : 65,
          paddingBottom: Platform.OS === 'ios' ? 25 : 8,
          paddingTop: 4,
        },
        tabBarActiveTintColor: theme.tabBarActive,
        tabBarInactiveTintColor: theme.tabBarInactive,
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          tabBarIcon: ({ focused }) => <TabIcon name="index" focused={focused} theme={theme} />,
          tabBarLabel: () => null,
        }}
      />
      <Tabs.Screen
        name="mall"
        options={{
          tabBarIcon: ({ focused }) => <TabIcon name="mall" focused={focused} theme={theme} />,
          tabBarLabel: () => null,
        }}
      />
      <Tabs.Screen
        name="publish"
        options={{
          tabBarIcon: ({ focused }) => <TabIcon name="publish" focused={focused} theme={theme} />,
          tabBarLabel: () => null,
        }}
      />
      <Tabs.Screen
        name="message"
        options={{
          tabBarIcon: ({ focused }) => <TabIcon name="message" focused={focused} theme={theme} />,
          tabBarLabel: () => null,
        }}
      />
      <Tabs.Screen
        name="mine"
        options={{
          tabBarIcon: ({ focused }) => <TabIcon name="mine" focused={focused} theme={theme} />,
          tabBarLabel: () => null,
        }}
      />
    </Tabs>
  )
}
