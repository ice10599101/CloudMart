import { useState, useEffect } from 'react'
import { View, Text, Input } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

export default function SearchPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [keyword, setKeyword] = useState('')
  const [hotSearches, setHotSearches] = useState<string[]>([])
  const [searchHistory, setSearchHistory] = useState<string[]>([])
  const type = Taro.getCurrentInstance().router?.params?.type || 'post'

  useEffect(() => {
    loadHotSearch()
    loadSearchHistory()
  }, [])

  const loadHotSearch = async () => {
    try {
      const res = await communityApi.getHotSearch()
      setHotSearches(res.data?.data || [])
    } catch {
      setHotSearches(['穿搭', '美食', '旅行', '好物推荐', '护肤', '数码'])
    }
  }

  const loadSearchHistory = async () => {
    try {
      const res = await communityApi.getSearchHistory()
      setSearchHistory(res.data?.data || [])
    } catch {
      // no history
    }
  }

  const handleSearch = () => {
    if (!keyword.trim()) return
    Taro.redirectTo({
      url: `/pages/search/index?keyword=${encodeURIComponent(keyword)}&type=${type}`,
    })
  }

  const handleTagClick = (tag: string) => {
    setKeyword(tag)
    Taro.redirectTo({
      url: `/pages/search/index?keyword=${encodeURIComponent(tag)}&type=${type}`,
    })
  }

  const handleClearHistory = async () => {
    try {
      await communityApi.clearSearchHistory()
      setSearchHistory([])
    } catch {
      setSearchHistory([])
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.searchRow}>
        <Input
          className={styles.searchInput}
          placeholder={type === 'product' ? '搜索商品' : '搜索内容、用户、话题'}
          value={keyword}
          onInput={(e) => setKeyword(e.detail.value)}
          onConfirm={handleSearch}
          focus
        />
        <Text className={styles.searchBtn} onClick={handleSearch}>搜索</Text>
      </View>

      {hotSearches.length > 0 && (
        <View className={styles.section}>
          <Text className={styles.sectionTitle}>热门搜索</Text>
          <View className={styles.tagList}>
            {hotSearches.map((tag) => (
              <View key={tag} className={styles.tag} onClick={() => handleTagClick(tag)}>
                <Text className={styles.tagText}>{tag}</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      {searchHistory.length > 0 && (
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>搜索历史</Text>
            <Text className={styles.clearBtn} onClick={handleClearHistory}>清空</Text>
          </View>
          <View className={styles.tagList}>
            {searchHistory.map((tag) => (
              <View key={tag} className={styles.tag} onClick={() => handleTagClick(tag)}>
                <Text className={styles.tagText}>{tag}</Text>
              </View>
            ))}
          </View>
        </View>
      )}
    </View>
  )
}
