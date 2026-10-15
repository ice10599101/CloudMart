import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Button,
  Empty,
  Input,
  Pagination,
  Select,
  Space,
  Spin,
  Tag,
} from "antd";
import { useSearchParams } from "umi";
import {
  listSoftware,
  listSoftwareCategories,
  listSoftwareTags,
  type SoftwareCard,
  type SoftwareCategory,
  type SoftwareTag,
} from "@/api/software";
import SoftwareCardGrid from "@/components/software/SoftwareCardGrid";
import styles from "./SoftwareExplore.module.css";

const PAGE_SIZE = 20;
/** 标签检索接口 size 上限 50，筛选下拉取启用标签首页即可（P1 再做服务端搜索） */
const TAG_PAGE_SIZE = 50;

const PLATFORM_OPTIONS = [
  { value: "WINDOWS", label: "Windows" },
  { value: "MACOS", label: "macOS" },
  { value: "LINUX", label: "Linux" },
  { value: "ANDROID", label: "Android" },
  { value: "IOS", label: "iOS" },
  { value: "CROSS_PLATFORM", label: "跨平台" },
];

const LICENSE_OPTIONS = [
  { value: "FREE", label: "免费" },
  { value: "OPEN_SOURCE", label: "开源" },
  { value: "TRIAL", label: "试用" },
  { value: "COMMERCIAL", label: "商业" },
];

const SORT_OPTIONS = [
  { value: "recommend", label: "推荐" },
  { value: "latest", label: "最近更新" },
  { value: "popular", label: "热门" },
  { value: "rating", label: "评分（至少 5 人）" },
];

const SORT_VALUES = SORT_OPTIONS.map((item) => item.value);

export default function SoftwareExplore() {
  const [searchParams, setSearchParams] = useSearchParams();

  const [loading, setLoading] = useState(true);
  const [cards, setCards] = useState<SoftwareCard[]>([]);
  const [total, setTotal] = useState(0);
  const [categories, setCategories] = useState<SoftwareCategory[]>([]);
  const [tags, setTags] = useState<SoftwareTag[]>([]);
  const [keywordInput, setKeywordInput] = useState("");

  // 筛选条件以 URL 为准（可分享、可回退），组件状态只是 URL 的镜像
  const keyword = searchParams.get("keyword") ?? "";
  const categoryId = searchParams.get("categoryId") ?? undefined;
  const platform = searchParams.get("platform") ?? undefined;
  const license = searchParams.get("license") ?? undefined;
  const tagId = searchParams.get("tagId") ?? undefined;
  const rawSort = searchParams.get("sort") ?? "";
  const sort = (SORT_VALUES.includes(rawSort) ? rawSort : "recommend") as
    | "recommend"
    | "latest"
    | "popular"
    | "rating";
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1);

  // 地址栏/回退带来的 keyword 变化要同步进输入框，避免显示与结果不一致
  useEffect(() => {
    setKeywordInput(keyword);
  }, [keyword]);

  const updateParams = useCallback(
    (patch: Record<string, string | undefined>) => {
      const next = new URLSearchParams(searchParams);
      Object.entries(patch).forEach(([key, value]) => {
        if (value === undefined || value === "") next.delete(key);
        else next.set(key, value);
      });
      // 任何筛选变化都回到第一页，避免停在不存在的页码
      if (!("page" in patch)) next.delete("page");
      setSearchParams(next);
    },
    [searchParams, setSearchParams],
  );

  const clearFilters = useCallback(() => {
    setSearchParams(new URLSearchParams());
  }, [setSearchParams]);

  useEffect(() => {
    listSoftwareCategories()
      .then((res) => {
        if (res.data.success) setCategories(res.data.data ?? []);
      })
      .catch(() => undefined);
    listSoftwareTags({ page: 1, size: TAG_PAGE_SIZE })
      .then((res) => {
        if (res.data.success) setTags(res.data.data?.records ?? []);
      })
      .catch(() => undefined);
  }, []);

  const query = useMemo(
    () => ({ keyword, categoryId, platform, license, tagId, sort }),
    [categoryId, keyword, license, platform, sort, tagId],
  );

  useEffect(() => {
    setLoading(true);
    listSoftware({
      ...query,
      keyword: query.keyword || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then((res) => {
        if (!res.data.success) return;
        setCards(res.data.data?.records ?? []);
        setTotal(res.data.data?.total ?? 0);
      })
      .finally(() => setLoading(false));
  }, [page, query]);

  const hasFilter =
    Boolean(keyword) ||
    Boolean(categoryId) ||
    Boolean(platform) ||
    Boolean(license) ||
    Boolean(tagId);

  const activeTags = (
    <>
      {keyword ? (
        <Tag closable onClose={() => updateParams({ keyword: undefined })}>
          关键词：{keyword}
        </Tag>
      ) : null}
      {categoryId ? (
        <Tag closable onClose={() => updateParams({ categoryId: undefined })}>
          {categories.find((item) => item.id === categoryId)?.name ?? "分类"}
        </Tag>
      ) : null}
      {platform ? (
        <Tag closable onClose={() => updateParams({ platform: undefined })}>
          {PLATFORM_OPTIONS.find((item) => item.value === platform)?.label ??
            platform}
        </Tag>
      ) : null}
      {license ? (
        <Tag closable onClose={() => updateParams({ license: undefined })}>
          {LICENSE_OPTIONS.find((item) => item.value === license)?.label ??
            license}
        </Tag>
      ) : null}
      {tagId ? (
        <Tag closable onClose={() => updateParams({ tagId: undefined })}>
          {tags.find((item) => item.id === tagId)?.name ?? "标签"}
        </Tag>
      ) : null}
      <Button type="link" size="small" onClick={clearFilters}>
        清空全部
      </Button>
    </>
  );

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>筛选软件</h1>

      <Input.Search
        allowClear
        className={styles.keyword}
        placeholder="搜索软件名称、用途、描述……"
        value={keywordInput}
        onChange={(event) => setKeywordInput(event.target.value)}
        onSearch={(value) =>
          updateParams({ keyword: value.trim() || undefined })
        }
      />

      <Space wrap className={styles.filters}>
        <Select
          style={{ width: 200 }}
          placeholder="分类"
          allowClear
          value={categoryId}
          onChange={(value: string | undefined) =>
            updateParams({ categoryId: value })
          }
          options={categories.map((item) => ({
            value: item.id,
            label: item.name,
          }))}
        />
        <Select
          style={{ width: 160 }}
          placeholder="平台"
          allowClear
          value={platform}
          onChange={(value: string | undefined) =>
            updateParams({ platform: value })
          }
          options={PLATFORM_OPTIONS}
        />
        <Select
          style={{ width: 140 }}
          placeholder="授权"
          allowClear
          value={license}
          onChange={(value: string | undefined) =>
            updateParams({ license: value })
          }
          options={LICENSE_OPTIONS}
        />
        <Select
          style={{ width: 180 }}
          placeholder="标签"
          allowClear
          value={tagId}
          onChange={(value: string | undefined) =>
            updateParams({ tagId: value })
          }
          options={tags.map((item) => ({ value: item.id, label: item.name }))}
        />
        <Select
          style={{ width: 140 }}
          value={sort}
          onChange={(value: string) => updateParams({ sort: value })}
          options={SORT_OPTIONS}
        />
      </Space>

      {hasFilter ? (
        <div className={styles.activeFilters}>{activeTags}</div>
      ) : null}

      <Spin spinning={loading}>
        {!loading && cards.length === 0 ? (
          <Empty description="没有符合条件的软件">
            <Button type="primary" onClick={clearFilters}>
              清空条件
            </Button>
          </Empty>
        ) : (
          <SoftwareCardGrid cards={cards} />
        )}
      </Spin>

      <div className={styles.pagination}>
        <Pagination
          current={page}
          pageSize={PAGE_SIZE}
          total={total}
          showSizeChanger={false}
          onChange={(next) => updateParams({ page: String(next) })}
        />
      </div>
    </div>
  );
}
