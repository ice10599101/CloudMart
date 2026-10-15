import { useCallback, useEffect, useMemo, useState } from 'react'
import { Card, Empty, Input, Pagination, Select, Space, Spin, Tag, Typography } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { history } from 'umi'
import {
  listSoftware,
  listSoftwareCategories,
  type SoftwareCard,
  type SoftwareCategory,
  type SoftwareListQuery,
} from '@/api/software'
import styles from './SoftwareHome.module.css'

const PAGE_SIZE = 20

const SORT_OPTIONS = [
  { value: 'latest', label: '最近更新' },
  { value: 'hot', label: '热门' },
  { value: 'rating', label: '好评' },
]

const LICENSE_LABEL: Record<string, string> = {
  FREE: '免费',
  OPEN_SOURCE: '开源',
  TRIAL: '试用',
  COMMERCIAL: '商业',
}

/**
 * 图标走网关静态路由 /files（与 WishBGM 的 /files/music/... 同源约定）；
 * 素材缺失或服务未配置时回退到统一占位图，不留破图。
 */
function iconUrl(assetId: string | null): string | undefined {
  return assetId ? `/files/${assetId}` : undefined
}

/** 卡片不呈现"下载量"：获取链接次数不等于实际下载成功（方案 §3.2） */
function formatUpdatedAt(value: string | null): string {
  if (!value) return '暂无版本'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无版本'
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`
}

export default function SoftwareHome() {
  const [loading, setLoading] = useState(true)
  const [cards, setCards] = useState<SoftwareCard[]>([])
  const [categories, setCategories] = useState<SoftwareCategory[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [searchText, setSearchText] = useState('')
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [sort, setSort] = useState<SoftwareListQuery['sort']>('latest')

  useEffect(() => {
    listSoftwareCategories()
      .then((res) => {
        if (res.data.success) setCategories(res.data.data ?? [])
      })
      .catch(() => {
        // 分类加载失败不阻塞主列表：广场本身仍可浏览
      })
  }, [])

  const fetchList = useCallback(async () => {
    setLoading(true)
    try {
      const res = await listSoftware({
        keyword: searchText || undefined,
        categoryId,
        sort,
        page,
        size: PAGE_SIZE,
      })
      if (!res.data.success) return
      setCards(res.data.data?.records ?? [])
      setTotal(res.data.data?.total ?? 0)
    } finally {
      setLoading(false)
    }
  }, [categoryId, page, searchText, sort])

  useEffect(() => {
    void fetchList()
  }, [fetchList])

  const categoryTags = useMemo(
    () => (
      <Space size={[8, 8]} wrap>
        <Tag
          color={categoryId === undefined ? 'blue' : undefined}
          onClick={() => {
            setCategoryId(undefined)
            setPage(1)
          }}
          style={{ cursor: 'pointer' }}
        >
          全部
        </Tag>
        {categories.map((item) => (
          <Tag
            key={item.id}
            color={categoryId === item.id ? 'blue' : undefined}
            onClick={() => {
              setCategoryId(item.id)
              setPage(1)
            }}
            style={{ cursor: 'pointer' }}
          >
            {item.name}
          </Tag>
        ))}
      </Space>
    ),
    [categories, categoryId],
  )

  const emptyState =
    !loading && cards.length === 0 ? (
      <Empty description={searchText ? '没有匹配的软件' : '还没有已发布的软件'} />
    ) : null

  return (
    <div className={styles.page}>
      <div className={styles.hero}>
        <Typography.Title level={2} className={styles.title}>
          小答软件
        </Typography.Title>
        <Typography.Paragraph className={styles.subtitle}>
          发现好用的软件：分类浏览、关键词搜索，满足条件后获取下载来源
        </Typography.Paragraph>
        <div className={styles.searchRow}>
          <Input
            allowClear
            className={styles.searchInput}
            size="large"
            prefix={<SearchOutlined />}
            placeholder="搜索软件名称、用途、标签……"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={() => {
              setSearchText(keyword.trim())
              setPage(1)
            }}
          />
          <Select
            size="large"
            style={{ width: 140 }}
            value={sort}
            options={SORT_OPTIONS}
            onChange={(value) => {
              setSort(value)
              setPage(1)
            }}
          />
        </div>
        <div className={styles.categories}>{categoryTags}</div>
      </div>

      <Spin spinning={loading}>
        {emptyState}
        <div className={styles.grid}>
          {cards.map((item) => (
            <Card
              key={item.id}
              hoverable
              className={styles.card}
              onClick={() => history.push(`/software/${item.id}`)}
            >
              <div className={styles.cardHead}>
                <img
                  className={styles.icon}
                  src={iconUrl(item.iconAssetId) ?? '/default-avatar.png'}
                  alt={item.iconAssetId ? `${item.name} 图标` : ''}
                  onError={(event) => {
                    event.currentTarget.src = '/default-avatar.png'
                  }}
                />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <p className={styles.cardTitle} title={item.name}>
                    {item.name}
                  </p>
                  <div className={styles.meta}>
                    {item.categoryName ? <Tag>{item.categoryName}</Tag> : null}
                    {item.licenseType ? <Tag>{LICENSE_LABEL[item.licenseType] ?? item.licenseType}</Tag> : null}
                  </div>
                </div>
              </div>
              <p className={styles.summary}>{item.summary}</p>
              <div className={styles.footer}>
                <span>更新于 {formatUpdatedAt(item.lastReleaseAt)}</span>
                <span>收藏 {item.favoriteCount}</span>
                <span>评论 {item.commentCount}</span>
              </div>
            </Card>
          ))}
        </div>
      </Spin>

      <div className={styles.pagination}>
        <Pagination
          current={page}
          pageSize={PAGE_SIZE}
          total={total}
          showSizeChanger={false}
          onChange={(next) => setPage(next)}
        />
      </div>
    </div>
  )
}
