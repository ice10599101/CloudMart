import { useEffect, useState } from "react";
import { Button, Empty, Input, Spin, Tag, Typography } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import { history } from "umi";
import {
  listSoftware,
  listSoftwareCategories,
  type SoftwareCard,
  type SoftwareCategory,
} from "@/api/software";
import SoftwareCardGrid from "@/components/software/SoftwareCardGrid";
import styles from "./SoftwareHome.module.css";

const SECTION_SIZE = 8;
/** 最新更新多取一批：剔除已出现在推荐区的软件，避免同页重复同一张卡片 */
const LATEST_FETCH_SIZE = SECTION_SIZE * 2;

/** 跳转到筛选页：搜索条件一律写进 URL，由 /software/explore 统一解释（方案 §7.2） */
function goExplore(params: Record<string, string>) {
  const search = new URLSearchParams(params).toString();
  history.push(search ? `/software/explore?${search}` : "/software/explore");
}

export default function SoftwareHome() {
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [categories, setCategories] = useState<SoftwareCategory[]>([]);
  const [recommended, setRecommended] = useState<SoftwareCard[]>([]);
  const [latest, setLatest] = useState<SoftwareCard[]>([]);

  useEffect(() => {
    listSoftwareCategories()
      .then((res) => {
        if (res.data.success) setCategories(res.data.data ?? []);
      })
      .catch(() => {
        // 分类加载失败不阻塞主列表：广场本身仍可浏览
      });
  }, []);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      listSoftware({ sort: "recommend", page: 1, size: SECTION_SIZE }),
      listSoftware({ sort: "latest", page: 1, size: LATEST_FETCH_SIZE }),
    ])
      .then(([recommendRes, latestRes]) => {
        if (recommendRes.data.success)
          setRecommended(recommendRes.data.data?.records ?? []);
        if (latestRes.data.success)
          setLatest(latestRes.data.data?.records ?? []);
      })
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, []);

  const isEmpty = !loading && recommended.length === 0 && latest.length === 0;

  // 推荐区优先级最高，同页不再重复出现同一张卡片
  const recommendedIds = new Set(recommended.map((item) => item.id));
  const latestVisible = latest
    .filter((item) => !recommendedIds.has(item.id))
    .slice(0, SECTION_SIZE);

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
              const trimmed = keyword.trim();
              goExplore(trimmed ? { keyword: trimmed } : {});
            }}
          />
        </div>
        <div className={styles.categories}>
          <Tag style={{ cursor: "pointer" }} onClick={() => goExplore({})}>
            全部
          </Tag>
          {categories.map((item) => (
            <Tag
              key={item.id}
              style={{ cursor: "pointer" }}
              onClick={() => goExplore({ categoryId: item.id })}
            >
              {item.name}
            </Tag>
          ))}
        </div>
      </div>

      <Spin spinning={loading}>
        {isEmpty ? (
          <Empty description="还没有已发布的软件" />
        ) : (
          <>
            <section>
              <div className={styles.sectionHead}>
                <h2 className={styles.sectionTitle}>推荐</h2>
                <Button
                  type="link"
                  size="small"
                  onClick={() => goExplore({ sort: "recommend" })}
                >
                  查看全部
                </Button>
              </div>
              <SoftwareCardGrid cards={recommended} />
            </section>

            <section>
              <div className={styles.sectionHead}>
                <h2 className={styles.sectionTitle}>最新更新</h2>
                <Button
                  type="link"
                  size="small"
                  onClick={() => goExplore({ sort: "latest" })}
                >
                  查看全部
                </Button>
              </div>
              <SoftwareCardGrid cards={latestVisible} />
            </section>
          </>
        )}
      </Spin>
    </div>
  );
}
