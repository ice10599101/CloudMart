import { useCallback, useEffect, useState } from "react";
import { Button, Empty, Input, Spin, Tag, Typography } from "antd";
import {
  CloudUploadOutlined,
  FolderOpenOutlined,
  ReloadOutlined,
  RocketOutlined,
  SearchOutlined,
  TrophyOutlined,
} from "@ant-design/icons";
import { history } from "umi";
import {
  getSoftwareHome,
  listSoftware,
  listSoftwareTopics,
  listSoftwareCategories,
  type SoftwareCard,
  type SoftwareCategory,
  type SoftwareHomeSection,
  type SoftwareTopicSummary,
} from "@/api/software";
import SoftwareCardGrid from "@/components/software/SoftwareCardGrid";
import styles from "./SoftwareHome.module.css";

const SECTION_SIZE = 8;
const LATEST_FETCH_SIZE = SECTION_SIZE;

/** 跳转到筛选页：搜索条件一律写进 URL，由 /software/explore 统一解释（方案 §7.2） */
function goExplore(params: Record<string, string>) {
  const search = new URLSearchParams(params).toString();
  history.push(search ? `/software/explore?${search}` : "/software/explore");
}

/** 分类胶囊装饰图标：按分类名关键词匹配 emoji（纯展示装饰，非业务数据；未命中兜底 📦） */
const CATEGORY_EMOJI_RULES: [RegExp, string][] = [
  [/办公|效率|文档/, "💼"],
  [/开发|编程|代码/, "🛠️"],
  [/图像|设计|图片|绘画/, "🎨"],
  [/影音|视频|音频|媒体|音乐|播放/, "🎬"],
  [/学习|教育|课程/, "📚"],
  [/游戏|娱乐/, "🎮"],
  [/网络|浏览器|下载/, "🌐"],
  [/系统|驱动|安全|清理/, "⚙️"],
];

function categoryEmoji(name: string): string {
  return CATEGORY_EMOJI_RULES.find(([re]) => re.test(name))?.[1] ?? "📦";
}

export default function SoftwareHome() {
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [categories, setCategories] = useState<SoftwareCategory[]>([]);
  /** P1：专题合集（只取已上线；无专题时整块不渲染） */
  const [topics, setTopics] = useState<SoftwareTopicSummary[]>([]);
  const [recommended, setRecommended] = useState<SoftwareCard[]>([]);
  const [latest, setLatest] = useState<SoftwareCard[]>([]);
  /** 已收录软件总数（推荐列表接口返回的真实 total，用于 hero 统计条） */
  const [totalCount, setTotalCount] = useState(0);
  /** UX01：运营配置的首页区块（null=接口不可用/未配置 → 降级内置渲染） */
  const [homeSections, setHomeSections] = useState<SoftwareHomeSection[] | null>(null);

  const loadHomeSections = useCallback(() => {
    getSoftwareHome("WEB")
      .then((res) => {
        const blocks = res.data.data ?? [];
        setHomeSections(blocks.length > 0 ? blocks : null);
      })
      .catch(() => setHomeSections(null));
  }, []);

  useEffect(() => {
    loadHomeSections();
  }, [loadHomeSections]);

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
    listSoftwareTopics()
      .then((res) => setTopics(res.data.data ?? []))
      .catch(() => setTopics([]));
  }, []);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      // recommend 会优先展示精选项，也会在没有精选项时用已发布软件填充推荐区。
      listSoftware({ sort: "recommend", page: 1, size: SECTION_SIZE }),
      listSoftware({ sort: "latest", page: 1, size: LATEST_FETCH_SIZE }),
    ])
      .then(([recommendRes, latestRes]) => {
        if (recommendRes.data.success) {
          setRecommended(recommendRes.data.data?.records ?? []);
          setTotalCount(recommendRes.data.data?.total ?? 0);
        }
        if (latestRes.data.success)
          setLatest(latestRes.data.data?.records ?? []);
      })
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, []);

  const isEmpty = !loading && recommended.length === 0 && latest.length === 0;

  // 推荐与最新表达的是不同维度，同一软件符合条件时允许同时出现。
  const latestVisible = latest.slice(0, SECTION_SIZE);

  return (
    <div className={styles.page}>
      <div className={styles.hero}>
        <div className={styles.heroContent}>
          <div className={styles.eyebrow}>
            <RocketOutlined className={styles.eyebrowIcon} />
            软件广场
          </div>
          <h1 className={styles.title}>小答软件</h1>
          <p className={styles.subtitle}>
            发现好用的软件：分类浏览、关键词搜索，满足条件后获取下载来源
          </p>
          {totalCount > 0 ? (
            <div className={styles.heroStats}>
              已收录
              <span className={styles.heroStatsStrong}>{totalCount}</span>
              款软件 · {categories.length} 个分类
            </div>
          ) : null}
          <div className={styles.searchWrap}>
            <Input
              allowClear
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
            <button
              type="button"
              className={`${styles.categoryPill} ${styles.categoryPillPrimary}`}
              onClick={() => goExplore({})}
            >
              <span className={styles.categoryEmoji} aria-hidden>
                ✨
              </span>
              全部
            </button>
            {categories.map((item) => (
              <button
                type="button"
                key={item.id}
                className={styles.categoryPill}
                onClick={() => goExplore({ categoryId: item.id })}
              >
                <span className={styles.categoryEmoji} aria-hidden>
                  {categoryEmoji(item.name)}
                </span>
                {item.name}
              </button>
            ))}
          </div>
          <div className={styles.actions}>
            <button
              type="button"
              className={styles.actionBtn}
              onClick={() => history.push("/software/rankings")}
            >
              <TrophyOutlined className={styles.actionBtnIcon} />
              排行榜
            </button>
            <button
              type="button"
              className={styles.actionBtn}
              onClick={() => history.push("/software/publish")}
            >
              <CloudUploadOutlined className={styles.actionBtnIcon} />
              发布软件
            </button>
            {/* UX06：小入口（不以大块信息流占据主页；无真实内容时需求页空态给提交入口） */}
            <button
              type="button"
              className={styles.actionBtn}
              onClick={() => history.push("/software/requests")}
            >
              <SearchOutlined className={styles.actionBtnIcon} />
              没找到？提需求
            </button>
            <button
              type="button"
              className={styles.actionBtn}
              onClick={() => history.push("/software/mine")}
            >
              <FolderOpenOutlined className={styles.actionBtnIcon} />
              我的软件
            </button>
          </div>
        </div>
      </div>

      <Spin spinning={loading}>
        {isEmpty ? (
          <Empty description="还没有已发布的软件" />
        ) : (
          <>
            {/* UX01：运营配置区块（配置存在时优先；单块失败显示本区重试，不拖垮其他块） */}
            {(homeSections ?? []).map((section) => (
              <section key={section.id} style={{ marginBottom: 28 }}>
                <div className={styles.sectionHead}>
                  <h2 className={styles.sectionTitle}>{section.title}</h2>
                  {section.subtitle ? (
                    <Typography.Text type="secondary">{section.subtitle}</Typography.Text>
                  ) : null}
                </div>
                {section.state === "ERROR" ? (
                  <Empty description="本区块加载失败">
                    <Button
                      icon={<ReloadOutlined />}
                      onClick={() => {
                        setHomeSections(null);
                        loadHomeSections();
                      }}
                    >
                      重试
                    </Button>
                  </Empty>
                ) : (section.items ?? []).length === 0 ? (
                  section.state === "EMPTY" ? (
                    <Empty description="内容准备中，敬请期待" />
                  ) : null
                ) : section.kind === "GUIDE_LIST" ? (
                  <div className={styles.categories}>
                    {(section.items ?? []).map((item) =>
                      item.guide ? (
                        <button
                          key={item.itemId}
                          type="button"
                          className={styles.categoryPill}
                          onClick={() => history.push(`/software/guides/${item.guide!.id}`)}
                        >
                          <span className={styles.categoryEmoji} aria-hidden>
                            📖
                          </span>
                          {item.headline || item.guide.title}
                        </button>
                      ) : null,
                    )}
                  </div>
                ) : section.kind === "SCENARIOS" ? (
                  <div className={styles.categories}>
                    {(section.items ?? []).map((item) =>
                      item.topic ? (
                        <button
                          key={item.itemId}
                          type="button"
                          className={styles.categoryPill}
                          onClick={() => history.push(`/software/topics/${item.topic!.slug}`)}
                        >
                          <span className={styles.categoryEmoji} aria-hidden>
                            🧰
                          </span>
                          {item.headline || item.topic.title}
                        </button>
                      ) : null,
                    )}
                  </div>
                ) : (
                  <SoftwareCardGrid
                    cards={(section.items ?? [])
                      .filter((item) => item.software)
                      .map((item) => item.software!)}
                  />
                )}
              </section>
            ))}
            <section>
              <div className={styles.sectionHead}>
                <h2 className={styles.sectionTitle}>
                  <span className={styles.sectionIcon} aria-hidden>
                    🔥
                  </span>
                  推荐
                </h2>
                <span
                  className={styles.moreLink}
                  role="button"
                  tabIndex={0}
                  onClick={() => goExplore({ sort: "recommend" })}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      goExplore({ sort: "recommend" });
                    }
                  }}
                >
                  查看全部
                </span>
              </div>
              <SoftwareCardGrid cards={recommended} />
            </section>

            <section>
              <div className={styles.sectionHead}>
                <h2 className={styles.sectionTitle}>
                  <span className={styles.sectionIcon} aria-hidden>
                    ⚡
                  </span>
                  最新更新
                </h2>
                <span
                  className={styles.moreLink}
                  role="button"
                  tabIndex={0}
                  onClick={() => goExplore({ sort: "latest" })}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      goExplore({ sort: "latest" });
                    }
                  }}
                >
                  查看全部
                </span>
              </div>
              <SoftwareCardGrid cards={latestVisible} />
            </section>

            {/* P1 专题合集：运营维护，只展示已上线专题 */}
            {topics.length > 0 ? (
              <section>
                <div className={styles.sectionHead}>
                  <h2 className={styles.sectionTitle}>
                    <span className={styles.sectionIcon} aria-hidden>
                      ⭐
                    </span>
                    专题合集
                  </h2>
                </div>
                <div className={styles.topicList}>
                  {topics.map((topic) => (
                    <span
                      key={topic.topicId}
                      className={styles.topicPill}
                      role="button"
                      tabIndex={0}
                      onClick={() => history.push(`/software/topics/${topic.slug}`)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          history.push(`/software/topics/${topic.slug}`);
                        }
                      }}
                    >
                      {topic.title}
                    </span>
                  ))}
                </div>
              </section>
            ) : null}
          </>
        )}
      </Spin>
    </div>
  );
}
