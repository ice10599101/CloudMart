import { useState, useEffect } from 'react'
import { View, Text, Input, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { productApi } from '@/api/product'
import { useThemeClass } from '@/composables/useThemeClass'
import type { Post, Product, UserBasic } from '@/types'
import styles from './index.module.scss'

// 对齐 Web 端搜索页：商品/帖子/用户 三类结果 + 排序 + 分页
const TYPE_TABS = [
  { key: 'product', label: '商品' },
  { key: 'post', label: '帖子' },
  { key: 'user', label: '用户' },
]

// 排序值对齐后端 ProductSearchRequest（与 Web 端一致）
const SORTS = [
  { key: '', label: '综合' },
  { key: 'sales_desc', label: '销量' },
  { key: 'price_asc', label: '价格↑' },
  { key: 'price_desc', label: '价格↓' },
  { key: 'rating_desc', label: '评分' },
  { key: 'created', label: '新品' },
]

const PRICE_RANGES = [
  { label: '全部', min: undefined as number | undefined, max: undefined as number | undefined },
  { label: '0-100', min: 0, max: 100 },
  { label: '100-500', min: 100, max: 500 },
  { label: '500-2000', min: 500, max: 2000 },
  { label: '2000以上', min: 2000, max: undefined },
]

export default function SearchPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const routerParams = Taro.getCurrentInstance().router?.params ?? {}
  const [keyword, setKeyword] = useState(routerParams.keyword ? decodeURIComponent(routerParams.keyword) : '')
  const [type, setType] = useState(routerParams.type || 'post')
  const [sort, setSort] = useState(routerParams.sort || '')
  const [hotSearches, setHotSearches] = useState<string[]>([])
  const [searchHistory, setSearchHistory] = useState<string[]>([])

  const [products, setProducts] = useState<Product[]>([])
  const [brandFacets, setBrandFacets] = useState<Array<{ key: string; name: string; count: number }>>([])
  const [categoryFacets, setCategoryFacets] = useState<Array<{ key: number; name: string; count: number }>>([])
  const [selectedBrand, setSelectedBrand] = useState<string | undefined>(undefined)
  const [selectedPrice, setSelectedPrice] = useState(0)
  const [posts, setPosts] = useState<Post[]>([])
  const [users, setUsers] = useState<UserBasic[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [page, setPage] = useState(1)
  const [searching, setSearching] = useState(false)

  const hasKeyword = !!keyword.trim()

  useEffect(() => {
    loadHotSearch()
    loadSearchHistory()
  }, [])

  useEffect(() => {
    if (hasKeyword) {
      void doSearch(1, false)
    }
  }, [keyword, type, sort])

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

  const doSearch = async (pageNum: number, append: boolean) => {
    const kw = keyword.trim()
    if (!kw) return
    setSearching(true)
    try {
      if (type === 'product') {
        // 契约对齐后端 ProductSearchResultVO：products + 品牌/分类聚合分面
        const res = await productApi.search({ keyword: kw, page: pageNum, size: 10, sort: sort || undefined })
        const data = res.data?.data as unknown as { products?: Product[]; list?: Product[]; brands?: Array<{ key?: string; name?: string; count?: number }>; categories?: Array<{ key?: number; name?: string; count?: number }> } | Product[] | undefined
        const list = Array.isArray(data) ? data : (data?.products ?? data?.list ?? [])
        setProducts((prev) => (append ? [...prev, ...list] : list))
        setHasMore(list.length >= 10)
        if (!append && !Array.isArray(data)) {
          setBrandFacets((data?.brands ?? []).map((b) => ({ key: String(b.key ?? b.name ?? ''), name: b.name ?? String(b.key ?? ''), count: b.count ?? 0 })))
          setCategoryFacets((data?.categories ?? []).map((c) => ({ key: Number(c.key ?? 0), name: c.name ?? '', count: c.count ?? 0 })))
        }
      } else if (type === 'user') {
        const res = await communityApi.searchUsers({ keyword: kw, page: pageNum, pageSize: 20 })
        const list = res.data?.data?.list ?? []
        setUsers((prev) => (append ? [...prev, ...list] : list))
        setHasMore(list.length >= 20)
      } else {
        const res = await communityApi.searchPosts({ keyword: kw, page: pageNum, pageSize: 20 })
        const list = res.data?.data?.list ?? res.data?.data ?? []
        setPosts((prev) => (append ? [...prev, ...list] : list))
        setHasMore(list.length >= 20)
      }
      setPage(pageNum)
    } catch {
      if (!append) {
        setProducts([])
        setPosts([])
        setUsers([])
      }
      setHasMore(false)
    } finally {
      setSearching(false)
    }
  }

  const handleSearch = () => {
    if (!keyword.trim()) return
    setKeyword(keyword)
    void doSearch(1, false)
    loadSearchHistory()
  }

  const handleTagClick = (tag: string) => {
    setKeyword(tag)
  }

  const handleTypeSwitch = (key: string) => {
    setType(key)
    setProducts([])
    setPosts([])
    setUsers([])
  }

  const handleClearHistory = async () => {
    try {
      await communityApi.clearSearchHistory()
      setSearchHistory([])
    } catch {
      setSearchHistory([])
    }
  }

  const goProduct = (id: number) => Taro.navigateTo({ url: `/pages/productDetail/index?id=${id}` })
  const goPost = (id: number) => Taro.navigateTo({ url: `/pages/postDetail/index?id=${id}` })
  const goUser = (id: number) => Taro.navigateTo({ url: `/pages/userProfile/index?userId=${id}` })

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.searchRow}>
        <Input
          className={styles.searchInput}
          placeholder={type === 'product' ? '搜索商品' : type === 'user' ? '搜索用户' : '搜索内容、用户、话题'}
          value={keyword}
          onInput={(e) => setKeyword(e.detail.value)}
          onConfirm={handleSearch}
          focus
        />
        <Text className={styles.searchBtn} onClick={handleSearch}>搜索</Text>
      </View>

      {/* 结果类型 Tab（对齐 Web 端 商品/帖子双 Tab，扩展用户） */}
      {hasKeyword && (
        <View className={styles.typeTabs}>
          {TYPE_TABS.map((tab) => (
            <View
              key={tab.key}
              className={`${styles.typeTab} ${type === tab.key ? styles.typeTabActive : ''}`}
              onClick={() => handleTypeSwitch(tab.key)}
            >
              <Text className={type === tab.key ? styles.typeTabTextActive : styles.typeTabText}>{tab.label}</Text>
            </View>
          ))}
        </View>
      )}

      {/* 排序（商品结果） */}
      {hasKeyword && type === 'product' && (
        <View className={styles.sortRow}>
          {SORTS.map((s) => (
            <View
              key={s.key}
              className={`${styles.sortChip} ${sort === s.key ? styles.sortChipActive : ''}`}
              onClick={() => setSort(s.key)}
            >
              <Text className={sort === s.key ? styles.sortChipTextActive : styles.sortChipText}>{s.label}</Text>
            </View>
          ))}
        </View>
      )}

      {/* 筛选侧边（对齐 Web 端：品牌/分类/价格区间聚合分面） */}
      {hasKeyword && type === 'product' && (brandFacets.length > 0 || categoryFacets.length > 0) && (
        <View className={styles.facetRow}>
          <ScrollView scrollX enhanced showScrollbar={false} className={styles.facetScroll}>
            <View className={styles.facetInner}>
              {selectedBrand && (
                <View className={styles.facetChipActive} onClick={() => setSelectedBrand(undefined)}>
                  <Text className={styles.facetChipTextActive}>品牌:{selectedBrand} ✕</Text>
                </View>
              )}
              {brandFacets.map((b) => (
                <View
                  key={b.key}
                  className={`${styles.facetChip} ${selectedBrand === b.name ? styles.facetChipActive : ''}`}
                  onClick={() => setSelectedBrand(selectedBrand === b.name ? undefined : b.name)}
                >
                  <Text className={selectedBrand === b.name ? styles.facetChipTextActive : styles.facetChipText}>{b.name}({b.count})</Text>
                </View>
              ))}
              {categoryFacets.map((c) => (
                <View key={`cat-${c.key}`} className={styles.facetChip} onClick={() => setSelectedBrand(undefined)}>
                  <Text className={styles.facetChipText}>{c.name}({c.count})</Text>
                </View>
              ))}
              {PRICE_RANGES.map((r, i) => (
                <View
                  key={`price-${i}`}
                  className={`${styles.facetChip} ${selectedPrice === i ? styles.facetChipActive : ''}`}
                  onClick={() => setSelectedPrice(i)}
                >
                  <Text className={selectedPrice === i ? styles.facetChipTextActive : styles.facetChipText}>{r.label}</Text>
                </View>
              ))}
            </View>
          </ScrollView>
        </View>
      )}

      {!hasKeyword && hotSearches.length > 0 && (
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

      {!hasKeyword && searchHistory.length > 0 && (
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

      {/* ===== 结果区 ===== */}
      {hasKeyword && (
        <ScrollView scrollY className={styles.resultArea}>
          {searching && <Text className={styles.hint}>搜索中...</Text>}

          {!searching && type === 'product' && (
            (() => {
              const priceRange = PRICE_RANGES[selectedPrice]
              const filtered = products.filter((product) => {
                if (selectedBrand && (product.brandName ?? '') !== selectedBrand) return false
                if (priceRange && priceRange.min != null && product.price < priceRange.min) return false
                if (priceRange && priceRange.max != null && product.price > priceRange.max) return false
                return true
              })
              return filtered.length > 0 ? (
              <View className={styles.productGrid}>
                {filtered.map((product) => (
                  <View key={product.id} className={styles.productCard} onClick={() => goProduct(product.id)}>
                    {product.mainImage && <Image className={styles.productImage} src={product.mainImage} mode='aspectFill' />}
                    <Text className={styles.productName}>{product.name}</Text>
                    <View className={styles.productPriceRow}>
                      <Text className={styles.productPrice}>¥{product.price}</Text>
                      {product.originalPrice && product.originalPrice > product.price && (
                        <Text className={styles.productOriginal}>¥{product.originalPrice}</Text>
                      )}
                    </View>
                    {product.sales > 0 && <Text className={styles.productSales}>{product.sales}人已购</Text>}
                  </View>
                ))}
              </View>
              ) : (
                !searching && <Text className={styles.hint}>没有找到相关商品</Text>
              )
            })()
          )}

          {!searching && type === 'user' && (
            users.length > 0 ? (
              users.map((u) => (
                <View key={u.id} className={styles.userRow} onClick={() => goUser(u.id)}>
                  {u.avatar && <Image className={styles.userAvatar} src={u.avatar} mode='aspectFill' />}
                  <View className={styles.userInfo}>
                    <Text className={styles.userName}>{u.nickname}</Text>
                    {u.signature && <Text className={styles.userSignature}>{u.signature}</Text>}
                  </View>
                </View>
              ))
            ) : (
              !searching && <Text className={styles.hint}>没有找到相关用户</Text>
            )
          )}

          {!searching && type === 'post' && (
            posts.length > 0 ? (
              posts.map((post) => (
                <View key={post.id} className={styles.postCard} onClick={() => goPost(post.id)}>
                  {post.coverImage && <Image className={styles.postCover} src={post.coverImage} mode='aspectFill' />}
                  <View className={styles.postInfo}>
                    <Text className={styles.postTitle}>{post.title}</Text>
                    <Text className={styles.postMeta}>
                      {post.user?.nickname ?? '匿名'} · ❤ {post.likeCount} · 💬 {post.commentCount}
                    </Text>
                  </View>
                </View>
              ))
            ) : (
              !searching && <Text className={styles.hint}>没有找到相关帖子</Text>
            )
          )}

          {hasMore && !searching && (
            <View className={styles.loadMore} onClick={() => void doSearch(page + 1, true)}>
              <Text className={styles.loadMoreText}>加载更多</Text>
            </View>
          )}
        </ScrollView>
      )}
    </View>
  )
}
