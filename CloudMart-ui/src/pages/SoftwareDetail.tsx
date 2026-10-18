import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  App,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  List,
  Rate,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
  Result,
  Modal,
} from "antd";
import { CopyOutlined, StarFilled, StarOutlined } from "@ant-design/icons";
import { history, useParams } from "umi";
import {
  handleSoftwareIconError,
  softwareIconUrl,
} from "@/utils/softwareIcon";
import CommentToolbar, { insertAtCursor } from "@/components/CommentToolbar";
import CommentContent from "@/components/CommentContent";
import { searchUsers, recordBrowseHistory} from "@/api/community";
import {
  createSoftwareComment,
  favoriteSoftware,
  getSoftwareAccessState,
  getSoftwareDetail,
  likeSoftwareComment,
  listSoftwareComments,
  listSoftwareReleases,
  pinSoftwareComment,
  rateSoftware,
  reportSoftware,
  resolveSoftwareSource,
  revokeSoftwareRating,
  subscribeSoftware,
  unfavoriteSoftware,
  unpinSoftwareComment,
  unsubscribeSoftware,
  type SoftwareAccessState,
  type SoftwareComment,
  type SoftwareDetail,
  type SoftwareRelease,
  type SoftwareSourceSummary,
  submitAccessRequest,
  listMyAccessRequests,
  cancelAccessRequest,
  type SoftwareAccessRequest,
} from "@/api/software";
import ReportModal from "@/components/ReportModal";
import RichText from "@/components/RichText";
import { useAuthStore } from "@/stores/auth";
import styles from "./SoftwareDetail.module.css";

const LICENSE_LABEL: Record<string, string> = {
  FREE: "免费",
  OPEN_SOURCE: "开源",
  TRIAL: "试用",
  COMMERCIAL: "商业",
};

const LICENSE_COLOR: Record<string, string> = {
  FREE: "var(--color-accent-green)",
  OPEN_SOURCE: "var(--color-accent-purple)",
  TRIAL: "var(--color-accent-orange)",
  COMMERCIAL: "var(--color-accent-gold)",
};

export default function SoftwareDetail() {
  const { id = "" } = useParams<{ id: string }>();
  const { message } = App.useApp();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<SoftwareDetail | null>(null);
  const [releases, setReleases] = useState<SoftwareRelease[]>([]);
  const [comments, setComments] = useState<SoftwareComment[]>([]);
  const [commentText, setCommentText] = useState("");
  const [pendingImages, setPendingImages] = useState<string[]>([]);
  const commentTextareaRef = useRef<any>(null);
  const [mentionResults, setMentionResults] = useState<
    { id: number; nickname: string }[]
  >([]);
  const [mentionVisible, setMentionVisible] = useState(false);
  const mentionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [releaseKey, setReleaseKey] = useState<string | undefined>(undefined);
  const [sourceId, setSourceId] = useState<string | undefined>(undefined);
  const [accessState, setAccessState] = useState<SoftwareAccessState | null>(
    null,
  );
  /** SF04：ADMIN_PAID 访问申请 */
  const [myRequests, setMyRequests] = useState<SoftwareAccessRequest[]>([]);
  const [requestOpen, setRequestOpen] = useState(false);
  const [requestMessage, setRequestMessage] = useState("");
  const [requestSubmitting, setRequestSubmitting] = useState(false);
  const [resolving, setResolving] = useState(false);
  /** 真实链接只保留在本次会话内存里，不落 localStorage、不写 URL */
  const [resolved, setResolved] = useState<{
    url: string;
    code: string | null;
  } | null>(null);
  /** 举报目标（复用社区举报弹窗，注入软件举报提交；与社区接口相互独立） */
  const [reportTarget, setReportTarget] = useState<{
    type: "SOFTWARE" | "SOFTWARE_COMMENT";
    id: string;
  } | null>(null);
  /** P1：评分/订阅本地状态（详情返回后同步） */
  const [myScore, setMyScore] = useState(0);
  const [ratingCount, setRatingCount] = useState(0);
  const [ratingAvg, setRatingAvg] = useState<number | null>(null);
  const [subscribed, setSubscribed] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [ratingBusy, setRatingBusy] = useState(false);
  const currentUserId = useAuthStore((s) => s.user?.id);

  // SF04：加载本人访问申请（用于门槛面板状态展示）
  useEffect(() => {
    if (!id) return;
    listMyAccessRequests(1, 10)
      .then((res) => setMyRequests(res.data.data?.records ?? []))
      .catch(() => undefined);
  }, [id]);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      getSoftwareDetail(id),
      listSoftwareReleases(id),
      listSoftwareComments(id, { page: 1, size: 20 }),
    ])
      .then(([detailRes, releaseRes, commentRes]) => {
        if (!detailRes.data.success) {
          setLoadError(detailRes.data.error?.message || "软件不存在或已下架");
          return;
        }
        if (detailRes.data.success) {
          setDetail(detailRes.data.data);
          setMyScore(detailRes.data.data.myScore ?? 0);
          setRatingCount(detailRes.data.data.ratingCount ?? 0);
          setRatingAvg(detailRes.data.data.ratingAvg ?? null);
          setSubscribed(Boolean(detailRes.data.data.isSubscribed));
          // 浏览足迹上报（静默）：同一软件重复浏览仅由后端刷新时间
          void recordBrowseHistory({
            targetType: "SOFTWARE",
            targetId: id,
            title: detailRes.data.data.name ?? undefined,
            cover: detailRes.data.data.iconAssetId
              ? `/files/by-id/${detailRes.data.data.iconAssetId}`
              : undefined,
          }).catch(() => undefined);
        }
        if (releaseRes.data.success)
          setReleases(releaseRes.data.data?.records ?? []);
        if (commentRes.data.success)
          setComments(commentRes.data.data?.records ?? []);
      })
      .catch((err) => {
        // 拦截器已把业务失败转为 reject（如未发布软件的 SOFTWARE_NOT_FOUND）——
        // 在此转为页面内的友好错误态，绝不向上抛出（防 Unhandled Rejection 炸屏）
        const code = (err as { code?: string } | null)?.code;
        setLoadError(
          code === "SOFTWARE_NOT_FOUND"
            ? "软件不存在或尚未公开"
            : "加载失败，请稍后重试",
        );
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

  const applyRatingResult = (result: {
    myScore: number | null;
    ratingCount: number;
    ratingAvg: number | null;
  }) => {
    setMyScore(result.myScore ?? 0);
    setRatingCount(result.ratingCount);
    setRatingAvg(result.ratingAvg);
  };

  /** SF04：提交/撤回访问申请 */
  const latestRequest = myRequests[0] ?? null;

  const handleAccessRequest = async () => {
    if (!requestMessage.trim()) {
      alert("请填写申请说明");
      return;
    }
    setRequestSubmitting(true);
    try {
      await submitAccessRequest(id, { message: requestMessage.trim() });
      setRequestOpen(false);
      setRequestMessage("");
      listMyAccessRequests(1, 10)
        .then((res) => setMyRequests(res.data.data?.records ?? []))
        .catch(() => undefined);
    } catch {
      // 拦截器统一提示
    } finally {
      setRequestSubmitting(false);
    }
  };

  const handleCancelAccessRequest = async (requestId: string) => {
    try {
      await cancelAccessRequest(requestId);
      listMyAccessRequests(1, 10)
        .then((res) => setMyRequests(res.data.data?.records ?? []))
        .catch(() => undefined);
    } catch {
      // 拦截器统一提示
    }
  };

  const handleRate = async (score: number) => {
    if (!isAuthenticated) {
      message.warning("请先登录后再评分");
      return;
    }
    setRatingBusy(true);
    try {
      const res = await rateSoftware(id, score);
      applyRatingResult(res.data.data);
      message.success(`已评 ${score} 星`);
    } catch {
      // 业务错误（分享者自评/不可见等）已由全局拦截器提示
    } finally {
      setRatingBusy(false);
    }
  };

  const handleRevokeRating = async () => {
    setRatingBusy(true);
    try {
      const res = await revokeSoftwareRating(id);
      applyRatingResult(res.data.data);
      message.success("已撤销评分");
    } catch {
      // 提示由拦截器统一处理
    } finally {
      setRatingBusy(false);
    }
  };

  const handleToggleSubscribe = async () => {
    if (!isAuthenticated) {
      message.warning("请先登录后再订阅更新");
      return;
    }
    try {
      if (subscribed) {
        await unsubscribeSoftware(id);
        setSubscribed(false);
        message.success("已取消订阅");
      } else {
        await subscribeSoftware(id);
        setSubscribed(true);
        message.success("已订阅更新（仅新增版本时提醒）");
      }
    } catch {
      // 提示由拦截器统一处理
    }
  };

  const handleTogglePin = async (comment: SoftwareComment) => {
    try {
      if (comment.pinned) {
        await unpinSoftwareComment(id, comment.id);
        message.success("已取消置顶");
      } else {
        await pinSoftwareComment(id, comment.id);
        message.success("已置顶（每款软件最多一个）");
      }
      const res = await listSoftwareComments(id, { page: 1, size: 20 });
      if (res.data.success) setComments(res.data.data?.records ?? []);
    } catch {
      // 提示由拦截器统一处理
    }
  };

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

  /** @提及检测（与社区同口径：@ 后无空格且 ≤20 字触发防抖搜索） */
  const handleCommentTextChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      const value = e.target.value;
      setCommentText(value);
      const lastAtIndex = value.lastIndexOf("@");
      if (lastAtIndex !== -1) {
        const afterAt = value.slice(lastAtIndex + 1);
        const hasSpace = afterAt.includes(" ");
        if (!hasSpace && afterAt.length > 0 && afterAt.length <= 20) {
          if (mentionTimerRef.current) clearTimeout(mentionTimerRef.current);
          mentionTimerRef.current = setTimeout(async () => {
            try {
              const res = await searchUsers(afterAt);
              setMentionResults(res.data.data ?? []);
              setMentionVisible(true);
            } catch {
              setMentionResults([]);
            }
          }, 300);
        } else if (hasSpace) {
          setMentionVisible(false);
          setMentionResults([]);
        }
      } else {
        setMentionVisible(false);
        setMentionResults([]);
      }
    },
    [],
  );

  const handleSelectMention = useCallback(
    (user: { id: number; nickname: string }) => {
      const lastAtIndex = commentText.lastIndexOf("@");
      if (lastAtIndex !== -1) {
        setCommentText(`${commentText.slice(0, lastAtIndex)}@${user.nickname} `);
      }
      setMentionVisible(false);
      setMentionResults([]);
    },
    [commentText],
  );

  const handleSendComment = useCallback(async () => {
    if (!isAuthenticated) {
      message.warning("请先登录");
      return;
    }
    const trimmed = commentText.trim();
    const content =
      (trimmed +
        pendingImages.map((url) => `\n![图片](${url})`).join("")).trim();
    if (!content) return;
    const res = await createSoftwareComment(id, { content });
    if (!res.data.success) return;
    setCommentText("");
    setPendingImages([]);
    setMentionVisible(false);
    setMentionResults([]);
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
      const latestRequest = myRequests[0] ?? null;
      return (
        <div className={styles.panel}>
          <div>本软件为付款查看模式：联系管理员完成确认后开通查看权。</div>
          {/* SF04：访问申请状态与入口 */}
          {latestRequest ? (
            <div style={{ marginTop: 6 }}>
              <Typography.Text strong style={{ fontSize: 13 }}>
                {latestRequest.status === "PENDING"
                  ? "访问申请审核中"
                  : latestRequest.status === "APPROVED"
                    ? "访问申请已通过"
                    : "访问申请未通过"}
              </Typography.Text>
              {latestRequest.reason && (
                <div style={{ fontSize: 13, opacity: 0.75 }}>说明：{latestRequest.reason}</div>
              )}
              {latestRequest.status === "PENDING" && (
                <Button
                  size="small"
                  style={{ marginTop: 4 }}
                  onClick={() => void handleCancelAccessRequest(latestRequest.requestId)}
                >
                  撤回申请
                </Button>
              )}
            </div>
          ) : (
            <div style={{ marginTop: 6 }}>
              <Button size="small" onClick={() => setRequestOpen(true)}>
                提交访问申请
              </Button>
            </div>
          )}
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
    if (loadError) {
    return (
      <div style={{ padding: 48 }}>
        <Result
          status="404"
          title="软件不存在或已下架"
          subTitle={loadError}
          extra={
            <Button type="primary" onClick={() => history.push("/software")}>
              返回软件广场
            </Button>
          }
        />
      </div>
    );
  }

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
        {[
          item.platform,
          item.arch,
          item.sizeLabel,
          item.availability && item.availability !== "UNKNOWN" ? item.availability : null,
        ]
          .filter(Boolean)
          .join(" · ")}
      </div>
    </div>
  ));

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div className={styles.iconWrap}>
          <img
            className={styles.icon}
            src={softwareIconUrl(detail.iconAssetId)}
            alt={detail.iconAssetId ? `${detail.name} 图标` : ""}
            onError={handleSoftwareIconError}
          />
        </div>
        <div className={styles.headInfo}>
          <h1 className={styles.title}>{detail.name}</h1>
          <p className={styles.summary}>{detail.summary}</p>
          <div className={styles.metaRow}>
            {detail.licenseType ? (
              <span
                className={`${styles.tag} ${styles.licenseTag}`}
                style={{
                  color: LICENSE_COLOR[detail.licenseType],
                  borderColor: LICENSE_COLOR[detail.licenseType],
                }}
              >
                {LICENSE_LABEL[detail.licenseType] ?? detail.licenseType}
              </span>
            ) : null}
            {detail.categoryName ? (
              <span className={styles.tag}>{detail.categoryName}</span>
            ) : null}
            {detail.tags.map((tag) => (
              <span key={tag.id} className={styles.tag}>
                {tag.name}
              </span>
            ))}
          </div>
          <div className={styles.stats}>
            {detail.ratingAvg != null && detail.ratingAvg > 0 ? (
              <span className={styles.statRating}>
                <StarFilled />
                {detail.ratingAvg.toFixed(1)}
                <span
                  style={{ color: "var(--color-text-tertiary)", fontWeight: 400 }}
                >
                  {detail.ratingCount > 0
                    ? `${detail.ratingCount} 人评分`
                    : "暂无评分"}
                </span>
              </span>
            ) : null}
            <span>收藏 {detail.favoriteCount}</span>
            <span>评论 {detail.commentCount}</span>
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
                    {/* P1 评分：独立区块（不把评论自动变成评分）；零票显示"暂无评分" */}
                    <div className={styles.ratingBlock}>
                      <div className={styles.ratingSummary}>
                        <span className={styles.ratingScore}>
                          {ratingAvg == null ? "暂无评分" : ratingAvg.toFixed(1)}
                        </span>
                        <span className={styles.ratingMeta}>
                          {ratingCount > 0 ? `${ratingCount} 人评分` : "成为第一个评分的人"}
                        </span>
                      </div>
                      <Space>
                        <Rate
                          value={myScore}
                          disabled={ratingBusy || detail.ownerId === String(currentUserId ?? "")}
                          onChange={(value) => void handleRate(value)}
                        />
                        {myScore > 0 ? (
                          <Button
                            type="link"
                            size="small"
                            loading={ratingBusy}
                            onClick={() => void handleRevokeRating()}
                          >
                            撤销评分
                          </Button>
                        ) : null}
                      </Space>
                      {detail.ownerId === String(currentUserId ?? "") ? (
                        <Typography.Text type="secondary">
                          分享者不能给自己的软件评分
                        </Typography.Text>
                      ) : null}
                    </div>
                    <div style={{ marginBottom: 12, position: "relative" }}>
                      <Input.TextArea
                        ref={commentTextareaRef}
                        value={commentText}
                        onChange={handleCommentTextChange}
                        placeholder={
                          isAuthenticated
                            ? "留下你的看法…（@提及用户，可附图片）"
                            : "请先登录后再评论"
                        }
                        disabled={!isAuthenticated}
                        autoSize={{ minRows: 2, maxRows: 4 }}
                        maxLength={1000}
                      />
                      <div style={{ marginTop: 6 }}>
                        <CommentToolbar
                          textareaRef={commentTextareaRef}
                          onInsert={(fragment) =>
                            setCommentText((prev) =>
                              insertAtCursor(
                                commentTextareaRef,
                                fragment,
                                prev,
                                1000,
                              ),
                            )
                          }
                          pendingImages={pendingImages}
                          onImagePicked={(url) =>
                            setPendingImages((prev) => [...prev, url])
                          }
                          onRemoveImage={(url) =>
                            setPendingImages((prev) =>
                              prev.filter((item) => item !== url),
                            )
                          }
                          disabled={!isAuthenticated}
                        />
                      </div>
                      {mentionVisible && mentionResults.length > 0 ? (
                        <div
                          style={{
                            position: "absolute",
                            bottom: "100%",
                            left: 0,
                            right: 0,
                            background: "var(--color-bg-elevated)",
                            border: "1px solid var(--color-border)",
                            borderRadius: 8,
                            maxHeight: 180,
                            overflowY: "auto",
                            zIndex: 100,
                          }}
                        >
                          {mentionResults.map((user) => (
                            <div
                              key={user.id}
                              onClick={() => handleSelectMention(user)}
                              style={{ padding: "6px 12px", cursor: "pointer" }}
                            >
                              @{user.nickname}
                            </div>
                          ))}
                        </div>
                      ) : null}
                      <Button
                        type="primary"
                        style={{ marginTop: 6 }}
                        onClick={() => void handleSendComment()}
                        disabled={!isAuthenticated}
                      >
                        发送
                      </Button>
                    </div>
                    {comments.length === 0 ? (
                      <Empty description="还没有评论" />
                    ) : (
                      comments.map((item) => (
                        <div key={item.id} className={styles.commentItem}>
                          <div>
                            <strong>{item.nickname}</strong>
                            {item.pinned ? <Tag color="gold" style={{ marginLeft: 8 }}>置顶</Tag> : null}
                            <span style={{ opacity: 0.6, marginLeft: 8 }}>
                              {item.createdAt}
                            </span>
                          </div>
                          <div>
                            <CommentContent content={item.content} />
                          </div>
                          <Space size={12} style={{ marginTop: 6 }}>
                            <Button
                              type="link"
                              size="small"
                              onClick={() => void handleLike(item.id)}
                            >
                              赞 {item.likeCount}
                            </Button>
                            <Button
                              type="link"
                              size="small"
                              onClick={() =>
                                setReportTarget({
                                  type: "SOFTWARE_COMMENT",
                                  id: item.id,
                                })
                              }
                            >
                              举报
                            </Button>
                            {detail.ownerId === String(currentUserId ?? "") ? (
                              <Button
                                type="link"
                                size="small"
                                onClick={() => void handleTogglePin(item)}
                              >
                                {item.pinned ? "取消置顶" : "置顶"}
                              </Button>
                            ) : null}
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
                  label: `${item.label || (item.provider && item.provider !== 'UNKNOWN' ? item.provider : '') || item.sourceType}${item.platform ? ` · ${item.platform}` : ""}`,
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
            <Button
              block
              style={{ marginTop: 8 }}
              onClick={() => void handleToggleSubscribe()}
            >
              {subscribed ? "已订阅更新" : "订阅更新"}
            </Button>
            <Button
              block
              type="text"
              danger
              style={{ marginTop: 4 }}
              onClick={() => setReportTarget({ type: "SOFTWARE", id })}
            >
              举报该软件
            </Button>
          </div>
        </Card>
      </div>

      {reportTarget ? (
        <ReportModal
          visible
          onClose={() => setReportTarget(null)}
          targetType={reportTarget.type}
          targetId={reportTarget.id}
          onSubmit={async (payload) => {
            await reportSoftware(id, {
              targetType: reportTarget.type,
              targetId:
                reportTarget.type === "SOFTWARE_COMMENT"
                  ? reportTarget.id
                  : undefined,
              reason: payload.reason,
              description: payload.description,
            });
          }}
        />
      ) : null}
      {/* SF04：访问申请弹窗 */}
      <Modal
        title="提交访问申请"
        open={requestOpen}
        onCancel={() => setRequestOpen(false)}
        onOk={() => void handleAccessRequest()}
        confirmLoading={requestSubmitting}
        okText="提交申请"
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          说明用途与凭据线索（如线下付款单号），授权员人工核对后开通；申请通过前不获得访问权。
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={1000}
          placeholder="申请说明（1~1000 字）"
          value={requestMessage}
          onChange={(e) => setRequestMessage(e.target.value)}
        />
      </Modal>
    </div>
  );
}
