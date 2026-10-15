import { useCallback, useEffect, useMemo, useState } from "react";
import {
  App,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  List,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
} from "antd";
import { CopyOutlined, StarFilled, StarOutlined } from "@ant-design/icons";
import { history, useParams } from "umi";
import {
  createSoftwareComment,
  favoriteSoftware,
  getSoftwareAccessState,
  getSoftwareDetail,
  likeSoftwareComment,
  listSoftwareComments,
  listSoftwareReleases,
  resolveSoftwareSource,
  unfavoriteSoftware,
  type SoftwareAccessState,
  type SoftwareComment,
  type SoftwareDetail,
  type SoftwareRelease,
  type SoftwareSourceSummary,
} from "@/api/software";
import RichText from "@/components/RichText";
import { useAuthStore } from "@/stores/auth";
import styles from "./SoftwareDetail.module.css";

const LICENSE_LABEL: Record<string, string> = {
  FREE: "免费",
  OPEN_SOURCE: "开源",
  TRIAL: "试用",
  COMMERCIAL: "商业",
};

function iconUrl(assetId: string | null): string | undefined {
  return assetId ? `/files/${assetId}` : undefined;
}

export default function SoftwareDetail() {
  const { id = "" } = useParams<{ id: string }>();
  const { message } = App.useApp();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<SoftwareDetail | null>(null);
  const [releases, setReleases] = useState<SoftwareRelease[]>([]);
  const [comments, setComments] = useState<SoftwareComment[]>([]);
  const [commentText, setCommentText] = useState("");

  const [releaseKey, setReleaseKey] = useState<string | undefined>(undefined);
  const [sourceId, setSourceId] = useState<string | undefined>(undefined);
  const [accessState, setAccessState] = useState<SoftwareAccessState | null>(
    null,
  );
  const [resolving, setResolving] = useState(false);
  /** 真实链接只保留在本次会话内存里，不落 localStorage、不写 URL */
  const [resolved, setResolved] = useState<{
    url: string;
    code: string | null;
  } | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      getSoftwareDetail(id),
      listSoftwareReleases(id),
      listSoftwareComments(id, { page: 1, size: 20 }),
    ])
      .then(([detailRes, releaseRes, commentRes]) => {
        if (detailRes.data.success) setDetail(detailRes.data.data);
        if (releaseRes.data.success) setReleases(releaseRes.data.data ?? []);
        if (commentRes.data.success)
          setComments(commentRes.data.data?.records ?? []);
      })
      .finally(() => setLoading(false));
  }, [id]);

  // 游客也能看到查看条件（联系入口/等级要求），但不拉需要登录的 access-state
  useEffect(() => {
    if (!id || !isAuthenticated) return;
    getSoftwareAccessState(id)
      .then((res) => {
        if (res.data.success) setAccessState(res.data.data);
      })
      .catch(() => setAccessState(null));
  }, [id, isAuthenticated]);

  const currentRelease = useMemo(() => {
    if (releases.length === 0) return undefined;
    const picked = releases.find((item) => item.releaseKey === releaseKey);
    return picked ?? releases.find((item) => item.recommended) ?? releases[0];
  }, [releases, releaseKey]);

  const sources = currentRelease?.sources ?? [];

  const refreshAccessState = useCallback(async () => {
    if (!id) return;
    const res = await getSoftwareAccessState(id);
    if (res.data.success) setAccessState(res.data.data);
  }, [id]);

  const handleCopy = useCallback(
    async (text: string, label: string) => {
      try {
        await navigator.clipboard.writeText(text);
        message.success(`${label}已复制`);
      } catch {
        // 剪贴板不可用时给出可操作回退：选中并提示手动复制
        message.warning("浏览器拒绝了剪贴板访问，请长按手动复制");
      }
    },
    [message],
  );

  const handleResolve = useCallback(async () => {
    if (!id || !sourceId) return;
    setResolving(true);
    try {
      const res = await resolveSoftwareSource(id, sourceId);
      if (!res.data.success) return;
      setResolved({
        url: res.data.data.url,
        code: res.data.data.extractionCode,
      });
    } finally {
      setResolving(false);
    }
  }, [id, sourceId]);

  const handleToggleFavorite = useCallback(async () => {
    if (!detail || !isAuthenticated) {
      message.warning("请先登录");
      return;
    }
    const res = detail.isFavorited
      ? await unfavoriteSoftware(id)
      : await favoriteSoftware(id);
    if (!res.data.success) return;
    setDetail({ ...detail, isFavorited: !detail.isFavorited });
  }, [detail, id, isAuthenticated, message]);

  const handleSendComment = useCallback(async () => {
    if (!isAuthenticated) {
      message.warning("请先登录");
      return;
    }
    const content = commentText.trim();
    if (!content) return;
    const res = await createSoftwareComment(id, { content });
    if (!res.data.success) return;
    setCommentText("");
    message.success("评论成功");
    const listRes = await listSoftwareComments(id, { page: 1, size: 20 });
    if (listRes.data.success) setComments(listRes.data.data?.records ?? []);
  }, [commentText, id, isAuthenticated, message]);

  const handleLike = useCallback(
    async (commentId: string) => {
      if (!isAuthenticated) {
        message.warning("请先登录");
        return;
      }
      const res = await likeSoftwareComment(id, commentId);
      if (!res.data.success) return;
      setComments((prev) =>
        prev.map((item) =>
          item.id === commentId
            ? { ...item, likedByMe: true, likeCount: item.likeCount + 1 }
            : item,
        ),
      );
    },
    [id, isAuthenticated, message],
  );

  /** 满足条件之前不渲染任何真实链接/提取码 */
  const accessPanel = useMemo(() => {
    if (!isAuthenticated) {
      return (
        <div className={styles.panel}>
          登录后按发布者设置的查看条件获取下载来源。
          <div style={{ marginTop: 8 }}>
            <Button
              type="primary"
              size="small"
              onClick={() => history.push("/login")}
            >
              去登录
            </Button>
          </div>
        </div>
      );
    }
    if (!accessState)
      return <div className={styles.panel}>正在校验查看条件……</div>;
    if (accessState.allowed) return null;
    if (accessState.accessMode === "COMMUNITY_LEVEL") {
      return (
        <div className={styles.panel}>
          需要社区等级 {accessState.requiredLevel ?? "-"}，当前{" "}
          {accessState.currentLevel ?? "-"}。
          <div style={{ marginTop: 8 }}>
            <Button
              size="small"
              onClick={() => history.push("/community/growth")}
            >
              去社区成长
            </Button>
          </div>
        </div>
      );
    }
    if (accessState.accessMode === "ADMIN_PAID") {
      return (
        <div className={styles.panel}>
          <div>需联系管理员付款后开通本软件的查看权。</div>
          {accessState.contacts.length > 0 ? (
            <div style={{ marginTop: 6 }}>
              {accessState.contacts.map((contact) => (
                <div key={contact.id}>
                  {contact.name}：{contact.contactValue}
                </div>
              ))}
            </div>
          ) : null}
          {/* 刷新只重新查询授权，不会因点击而自行解锁 */}
          <div style={{ marginTop: 8 }}>
            <Button size="small" onClick={() => void refreshAccessState()}>
              已联系，刷新权限
            </Button>
          </div>
        </div>
      );
    }
    return <div className={styles.panel}>当前账号暂不满足查看条件。</div>;
  }, [accessState, isAuthenticated, refreshAccessState]);

  if (loading) {
    return (
      <div className={styles.page}>
        <Spin />
      </div>
    );
  }

  if (!detail) {
    return (
      <div className={styles.page}>
        <Empty description="软件不存在或已下架" />
      </div>
    );
  }

  const sourceList = sources.map((item: SoftwareSourceSummary) => (
    <div key={item.sourceId} className={styles.sourceItem}>
      <div className={styles.sourceLabel}>
        {item.label || item.provider || item.sourceType}
      </div>
      <div className={styles.sourceMeta}>
        {[item.platform, item.arch, item.sizeLabel, item.availability]
          .filter(Boolean)
          .join(" · ")}
      </div>
    </div>
  ));

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <img
          className={styles.icon}
          src={iconUrl(detail.iconAssetId) ?? "/default-avatar.png"}
          alt={detail.iconAssetId ? `${detail.name} 图标` : ""}
          onError={(event) => {
            event.currentTarget.src = "/default-avatar.png";
          }}
        />
        <div style={{ minWidth: 0 }}>
          <h1 className={styles.title}>{detail.name}</h1>
          <p className={styles.summary}>{detail.summary}</p>
          <div className={styles.metaRow}>
            {detail.categoryName ? <Tag>{detail.categoryName}</Tag> : null}
            {detail.licenseType ? (
              <Tag>
                {LICENSE_LABEL[detail.licenseType] ?? detail.licenseType}
              </Tag>
            ) : null}
            {detail.tags.map((tag) => (
              <Tag key={tag.id}>{tag.name}</Tag>
            ))}
          </div>
        </div>
      </div>

      <div className={styles.layout}>
        <Card>
          <Tabs
            items={[
              {
                key: "intro",
                label: "软件介绍",
                children: (
                  <div>
                    <RichText content={detail.descriptionHtml} />
                    <Descriptions column={1} size="small" bordered>
                      {detail.developerName ? (
                        <Descriptions.Item label="开发者">
                          {detail.developerName}
                        </Descriptions.Item>
                      ) : null}
                      {detail.officialUrl ? (
                        <Descriptions.Item label="官方网站">
                          {detail.officialUrl}
                        </Descriptions.Item>
                      ) : null}
                      {detail.systemRequirements ? (
                        <Descriptions.Item label="系统要求">
                          {detail.systemRequirements}
                        </Descriptions.Item>
                      ) : null}
                    </Descriptions>
                  </div>
                ),
              },
              {
                key: "releases",
                label: "版本记录",
                children:
                  releases.length === 0 ? (
                    <Empty description="暂无版本" />
                  ) : (
                    <List
                      dataSource={releases}
                      renderItem={(item) => (
                        <List.Item>
                          <List.Item.Meta
                            title={`${item.versionName}${
                              item.recommended ? "（发布者推荐）" : ""
                            }`}
                            description={item.changelog || "暂无更新日志"}
                          />
                          <span>{item.releaseDate ?? ""}</span>
                        </List.Item>
                      )}
                    />
                  ),
              },
              {
                key: "comments",
                label: `交流讨论（${detail.commentCount}）`,
                children: (
                  <div>
                    <Space.Compact style={{ width: "100%", marginBottom: 12 }}>
                      <Input
                        value={commentText}
                        onChange={(event) => setCommentText(event.target.value)}
                        placeholder="留下你的看法"
                        maxLength={2000}
                      />
                      <Button
                        type="primary"
                        onClick={() => void handleSendComment()}
                      >
                        发送
                      </Button>
                    </Space.Compact>
                    {comments.length === 0 ? (
                      <Empty description="还没有评论" />
                    ) : (
                      comments.map((item) => (
                        <div key={item.id} className={styles.commentItem}>
                          <div>
                            <strong>{item.nickname}</strong>
                            <span style={{ opacity: 0.6, marginLeft: 8 }}>
                              {item.createdAt}
                            </span>
                          </div>
                          <div>{item.content}</div>
                          <Space size={12} style={{ marginTop: 6 }}>
                            <Button
                              type="link"
                              size="small"
                              onClick={() => void handleLike(item.id)}
                            >
                              赞 {item.likeCount}
                            </Button>
                          </Space>
                          {item.replies.map((reply) => (
                            <div key={reply.id} className={styles.replyItem}>
                              <div>
                                <strong>{reply.nickname}</strong>
                              </div>
                              <div>{reply.content}</div>
                            </div>
                          ))}
                        </div>
                      ))
                    )}
                  </div>
                ),
              },
            ]}
          />
        </Card>

        <Card className={styles.sideCard} title="下载来源">
          {releases.length > 0 ? (
            <Select
              style={{ width: "100%", marginBottom: 12 }}
              value={currentRelease?.releaseKey}
              onChange={(value) => {
                setReleaseKey(value);
                setSourceId(undefined);
                setResolved(null);
              }}
              options={releases.map((item) => ({
                value: item.releaseKey,
                label: `${item.versionName}${item.recommended ? "（推荐）" : ""}`,
              }))}
            />
          ) : null}

          {sources.length === 0 ? (
            <Empty description="暂无可用链接" />
          ) : (
            <Space direction="vertical" style={{ width: "100%" }}>
              <Select
                style={{ width: "100%" }}
                placeholder="选择下载来源"
                value={sourceId}
                onChange={(value) => {
                  setSourceId(value);
                  setResolved(null);
                }}
                options={sources.map((item) => ({
                  value: item.sourceId,
                  label: `${item.label || item.provider || item.sourceType}${item.platform ? ` · ${item.platform}` : ""}`,
                }))}
              />
              {sourceList}
            </Space>
          )}

          {accessPanel}

          {accessState?.allowed ? (
            <div className={styles.panel}>
              {resolved ? (
                <>
                  <div className={styles.secretRow}>
                    <span className={styles.secretValue}>{resolved.url}</span>
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => void handleCopy(resolved.url, "链接")}
                    >
                      复制
                    </Button>
                  </div>
                  {resolved.code ? (
                    <div className={styles.secretRow}>
                      <span>提取码：</span>
                      <span className={styles.secretValue}>
                        {resolved.code}
                      </span>
                      <Button
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() =>
                          void handleCopy(resolved.code ?? "", "提取码")
                        }
                      >
                        复制
                      </Button>
                    </div>
                  ) : null}
                </>
              ) : (
                <Button
                  type="primary"
                  block
                  loading={resolving}
                  disabled={!sourceId}
                  onClick={() => void handleResolve()}
                >
                  获取下载链接
                </Button>
              )}
            </div>
          ) : null}

          <div style={{ marginTop: 12 }}>
            <Button
              block
              icon={detail.isFavorited ? <StarFilled /> : <StarOutlined />}
              onClick={() => void handleToggleFavorite()}
            >
              {detail.isFavorited ? "已收藏" : "收藏"}
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
}
