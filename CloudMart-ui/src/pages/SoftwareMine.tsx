import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "umi";
import { App, Button, Empty, Form, Input, List, Modal, Pagination, Popconfirm, Space, Spin, Switch, Tabs, Tag, Typography } from "antd";
import { history } from "umi";
import {
  deleteMySoftware,
  getSoftwarePreferences,
  getUpdatesUnreadCount,
  listMySoftware,
  listMySourceIssues,
  type SoftwareSourceIssue,
  listMySubscriptions,
  listMyUpdates,
  markUpdatesRead,
  readRecentBrowse,
  clearRecentBrowse,
  getSoftwareCardsByIds,
  subscribeSoftware,
  type SoftwareCard,
  unsubscribeSoftware,
  updateSoftwarePreferences,
  type SoftwareMineItem,
  type SoftwareSubscriptionRow,
  type SoftwareUpdateItem,
  getPublisherStatus,
  submitPublisherApplication,
  listMyPublisherApplications,
  cancelPublisherApplication,
  type SoftwarePublisherApplication,
} from "@/api/software";
import { useAuthStore } from "@/stores/auth";
import styles from "./SoftwareMine.module.css";

const TAB_KEYS = [
  "published",
  "draft",
  "pending",
  "rejected",
  "favorites",
  "subscriptions",
  "updates",
  "recent",
] as const;

type TabKey =
  | "published"
  | "draft"
  | "pending"
  | "rejected"
  | "favorites"
  | "subscriptions"
  | "updates"
  | "recent";

/** Only these tabs are backed by GET /software/mine. */
const MINE_LIST_TABS: readonly TabKey[] = [
  "published",
  "draft",
  "pending",
  "rejected",
  "favorites",
  "subscriptions",
];

const STATUS_LABEL: Record<string, string> = {
  PUBLISHED: "已发布",
  DRAFT: "草稿",
  CHECKING: "审核检查中",
  PENDING: "待审核",
  WITHDRAWN: "已撤回",
  REJECTED: "已退回",
};

export default function SoftwareMine() {
  const { message } = App.useApp();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [searchParams, setSearchParams] = useSearchParams();
  const urlTab = searchParams.get("tab") as TabKey | null;
  const [tab, setTabState] = useState<TabKey>(
    urlTab && TAB_KEYS.includes(urlTab) ? urlTab : "published",
  );
  const setTab = (next: TabKey) => {
    setTabState(next);
    setPage(1);
    // tab 写入 URL：每次切换成为一条浏览器历史，后退/前进可还原
    const params = new URLSearchParams();
    if (next !== "published") params.set("tab", next);
    setSearchParams(params);
  };
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<SoftwareMineItem[]>([]);
  /** SW11：翻页——不再固定 1/50，后端 size 上限 50 */
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const PAGE_SIZE = 20;

  // 浏览器前进/后退会改变 URL；让当前页签跟随 URL 恢复，避免保留旧的本地 tab。
  useEffect(() => {
    const routeTab = searchParams.get("tab");
    const nextTab: TabKey = routeTab && TAB_KEYS.includes(routeTab as TabKey)
      ? (routeTab as TabKey)
      : "published";
    setTabState((current) => current === nextTab ? current : nextTab);
    setPage(1);
  }, [searchParams]);

  /** P1 更新提醒总开关（缺省开启；与订阅关系相互独立） */
  const [updateNotify, setUpdateNotify] = useState(true);
  /** SF03：发布资格与申请状态 */
  const [pubStatus, setPubStatus] = useState<{ canPublish: boolean } | null>(null);

  // UX05：订阅更新流（游标分页 + 批量已读）
  const [updateItems, setUpdateItems] = useState<SoftwareUpdateItem[]>([]);
  const [updateCursor, setUpdateCursor] = useState<string | null>(null);
  const [updateHasMore, setUpdateHasMore] = useState(false);
  const [updateLoading, setUpdateLoading] = useState(false);
  const [subRows, setSubRows] = useState<SoftwareSubscriptionRow[]>([]);
  const [unreadBadge, setUnreadBadge] = useState(0);
  const [prefTarget, setPrefTarget] = useState<SoftwareSubscriptionRow | null>(null);
  const [pendingCount, setPendingCount] = useState(0);
  const [rejectedCount, setRejectedCount] = useState(0);
  // SF07：待修复来源聚合（巡检 INVALID）
  const [sourceIssues, setSourceIssues] = useState<SoftwareSourceIssue[]>([]);
  const [prefForm] = Form.useForm();
  // UX05：最近浏览（本地 30 条 → 服务端批量查询过滤不可见）
  const [recentCards, setRecentCards] = useState<SoftwareCard[]>([]);

  const loadUpdates = useCallback(async (append: boolean, cursor: string | null) => {
    setUpdateLoading(true);
    try {
      const res = await listMyUpdates(append ? cursor : null, 20);
      if (res.data.success) {
        const feed = res.data.data;
        setUpdateItems((prev) => (append ? [...prev, ...feed.items] : feed.items));
        setUpdateCursor(feed.nextCursor);
        setUpdateHasMore(feed.hasMore);
      }
    } finally {
      setUpdateLoading(false);
    }
  }, []);

  const loadSubscriptionRows = useCallback(async () => {
    const res = await listMySubscriptions();
    if (res.data.success) setSubRows(res.data.data ?? []);
  }, []);
  const [myApplications, setMyApplications] = useState<SoftwarePublisherApplication[]>([]);
  const [applyOpen, setApplyOpen] = useState(false);
  const [applyIntro, setApplyIntro] = useState("");
  const [applyCategories, setApplyCategories] = useState("");
  const [applySubmitting, setApplySubmitting] = useState(false);
  const [notifySaving, setNotifySaving] = useState(false);

  const load = useCallback(
    async (nextTab: TabKey, nextPage = 1) => {
      // “最近浏览”和“更新”使用各自的数据源；不能把 UI tab 透传给
      // GET /software/mine（后端只接受 MINE_LIST_TABS）。
      if (!MINE_LIST_TABS.includes(nextTab)) return;
      setLoading(true);
      try {
        const res = await listMySoftware({
          tab: nextTab,
          page: nextPage,
          size: PAGE_SIZE,
        });
        if (!res.data.success) return;
        setItems(res.data.data?.records ?? []);
        setTotal(res.data.data?.total ?? 0);
        setPage(nextPage);
      } catch {
        message.error("加载失败，请稍后重试");
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    if (!isAuthenticated || !MINE_LIST_TABS.includes(tab)) return;
    void load(tab);
  }, [isAuthenticated, load, tab]);

  // UX05：更新流/订阅行按页签加载
  useEffect(() => {
    if (!isAuthenticated) return;
    if (tab === "updates") {
      void loadUpdates(false, null);
      getUpdatesUnreadCount()
        .then((res) => setUnreadBadge(res.data.data ?? 0))
        .catch(() => undefined);
    }
    if (tab === "subscriptions") void loadSubscriptionRows();
    if (tab === "recent") {
      const entries = readRecentBrowse();
      if (entries.length === 0) {
        setRecentCards([]);
        return;
      }
      // 服务端批量查询：不可见软件自然被过滤（cardsByIds 仅返回公开可见）
      getSoftwareCardsByIds(entries.map((e) => e.softwareId))
        .then((res) => setRecentCards(res.data.data ?? []))
        .catch(() => setRecentCards([]));
    }
  }, [isAuthenticated, tab, loadUpdates, loadSubscriptionRows]);

  const handleMarkRead = async (eventId: string) => {
    const res = await markUpdatesRead([eventId]);
    if (res.data.success) {
      setUpdateItems((prev) =>
        prev.map((it) => (it.eventId === eventId ? { ...it, read: true } : it)),
      );
      setUnreadBadge((n) => Math.max(0, n - 1));
    }
  };

  const handleUnsubscribeRow = async (softwareId: string) => {
    const res = await unsubscribeSoftware(softwareId);
    if (res.data.success) {
      message.success("已取消订阅");
      await loadSubscriptionRows();
    }
  };

  // 待办聚合：拉一次全量 mine 列表统计审核中/被退回（与页签数据无关）
  useEffect(() => {
    if (!isAuthenticated) return;
    listMySoftware({ page: 1, size: 50 })
      .then((res) => {
        const records = res.data.data?.records ?? [];
        setPendingCount(records.filter(
          (it) => it.status === "PENDING" || it.status === "CHECKING",
        ).length);
        setRejectedCount(records.filter((it) => it.status === "REJECTED").length);
      })
      .catch(() => undefined);
    listMySourceIssues()
      .then((res) => setSourceIssues(res.data.data ?? []))
      .catch(() => undefined);
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated) return;
    getSoftwarePreferences()
      .then((res) => setUpdateNotify(res.data.data?.updateNotifications ?? true))
      .catch(() => undefined);
    // SF03：资格状态与申请记录
    getPublisherStatus()
      .then((res) => setPubStatus(res.data.data ?? null))
      .catch(() => undefined);
    listMyPublisherApplications(1, 10)
      .then((res) => setMyApplications(res.data.data?.records ?? []))
      .catch(() => undefined);
  }, [isAuthenticated]);

  const latestApplication = myApplications[0] ?? null;

  const handleApply = async () => {
    if (!applyIntro.trim() || !applyCategories.trim()) {
      message.warning("请填写发布用途与拟投分类");
      return;
    }
    setApplySubmitting(true);
    try {
      await submitPublisherApplication({
        intro: applyIntro.trim(),
        intendedCategories: applyCategories.trim(),
      });
      message.success("申请已提交，等待管理员审核");
      setApplyOpen(false);
      setApplyIntro("");
      setApplyCategories("");
      listMyPublisherApplications(1, 10)
        .then((res) => setMyApplications(res.data.data?.records ?? []))
        .catch(() => undefined);
    } catch {
      // 拦截器统一提示
    } finally {
      setApplySubmitting(false);
    }
  };

  const handleCancelApplication = async (applicationId: string) => {
    try {
      await cancelPublisherApplication(applicationId);
      message.success("已撤回申请");
      listMyPublisherApplications(1, 10)
        .then((res) => setMyApplications(res.data.data?.records ?? []))
        .catch(() => undefined);
    } catch {
      // 拦截器统一提示
    }
  };

  const handleToggleNotify = async (enabled: boolean) => {
    setNotifySaving(true);
    try {
      const res = await updateSoftwarePreferences({ updateNotifications: enabled });
      setUpdateNotify(res.data.data?.updateNotifications ?? enabled);
      message.success(enabled ? "已开启软件更新提醒" : "已关闭软件更新提醒");
    } catch {
      // 提示由拦截器统一处理
    } finally {
      setNotifySaving(false);
    }
  };

  const handleDeleteDraft = async (item: SoftwareMineItem) => {
    try {
      await deleteMySoftware(item.softwareId);
      message.success("已删除");
      void load(tab);
    } catch {
      // 拦截器已统一提示
    }
  };

  const handleUnsubscribe = async (softwareId: string) => {
    try {
      await unsubscribeSoftware(softwareId);
      message.success("已取消订阅");
      void load(tab);
    } catch {
      // 提示由拦截器统一处理
    }
  };

  if (!isAuthenticated) {
    return (
      <div className={styles.page}>
        <Empty description="请先登录">
          <Button type="primary" onClick={() => history.push("/login")}>
            去登录
          </Button>
        </Empty>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>我的软件</h1>
        <Space size={16}>
          <Space size={8}>
            <span style={{ fontSize: 13, opacity: 0.75 }}>软件更新提醒</span>
            <Switch
              checked={updateNotify}
              loading={notifySaving}
              onChange={(checked) => void handleToggleNotify(checked)}
            />
          </Space>
          <Button
            type="primary"
            onClick={() => history.push("/software/publish")}
          >
            发布软件
          </Button>
        </Space>
      </div>
      {/* SF03：发布资格与申请状态 */}
      {!pubStatus?.canPublish && (
        <div style={{ marginBottom: 16, padding: "12px 16px", borderRadius: 8, border: "1px solid #e2e8f0" }}>
          <Space direction="vertical" size={4} style={{ width: "100%" }}>
            <Typography.Text strong>
              {latestApplication?.status === "PENDING"
                ? "发布资格申请审核中"
                : latestApplication?.status === "REJECTED"
                  ? "发布资格申请未通过"
                  : "尚未获得发布资格"}
            </Typography.Text>
            {latestApplication?.status === "REJECTED" && latestApplication.reason && (
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                原因：{latestApplication.reason}
              </Typography.Text>
            )}
            {latestApplication?.status === "PENDING" ? (
              <Space size={8}>
                <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                  提交于 {latestApplication.submittedAt.replace("T", " ").slice(0, 16)}
                </Typography.Text>
                <Button
                  size="small"
                  onClick={() => void handleCancelApplication(latestApplication.applicationId)}
                >
                  撤回申请
                </Button>
              </Space>
            ) : (
              <Button size="small" type="primary" onClick={() => setApplyOpen(true)}>
                {latestApplication?.status === "REJECTED" ? "重新申请" : "申请发布资格"}
              </Button>
            )}
          </Space>
        </div>
      )}
      {/* SF03/UX05：作者工作台待办聚合（真实待办：审核中/需修复；不用虚拟打卡奖励制造回访） */}
      {tab === "published" && (pendingCount > 0 || rejectedCount > 0 || sourceIssues.length > 0) ? (
        <div style={{ marginBottom: 12 }}>
          <Space wrap>
            {pendingCount > 0 ? (
              <Tag color="processing">审核中 {pendingCount} 个快照</Tag>
            ) : null}
            {rejectedCount > 0 ? (
              <Button
                size="small"
                type="link"
                onClick={() => setTab("rejected")}
              >
                被退回 {rejectedCount} 个需修复 →
              </Button>
            ) : null}
            {sourceIssues.length > 0 ? (
              <Tag color="warning">
                {sourceIssues.length} 个来源待修复（巡检失效）
              </Tag>
            ) : null}
          </Space>
          {sourceIssues.length > 0 ? (
            <div style={{ marginTop: 4 }}>
              {sourceIssues.slice(0, 5).map((issue) => (
                <div key={`${issue.softwareId}-${issue.sourceKey}`}>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => history.push(`/software/${issue.softwareId}/edit`)}
                  >
                    {issue.softwareName || `软件 ${issue.softwareId}`} · {issue.sourceKey}
                    {issue.reason ? `（${issue.reason.slice(0, 40)}）` : ""} →
                  </Button>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key as TabKey)}
        items={[
          { key: "published", label: "已发布" },
          { key: "draft", label: "草稿" },
          { key: "pending", label: "待审核" },
          { key: "rejected", label: "被退回" },
          { key: "favorites", label: "收藏" },
          { key: "subscriptions", label: "订阅" },
          { key: "recent", label: "最近浏览" },
          {
            key: "updates",
            label: (
              <span>
                更新
                {unreadBadge > 0 ? (
                  <Tag color="red" style={{ marginLeft: 6, marginRight: 0 }}>
                    {unreadBadge > 99 ? "99+" : unreadBadge}
                  </Tag>
                ) : null}
              </span>
            ),
          },
        ]}
      />
      <Spin spinning={loading && tab !== "updates"}>
        {tab === "updates" ? (
          /* UX05：订阅更新流——突出"这次改了什么"，不重复简介 */
          <div>
            {updateItems.length === 0 ? (
              <Empty description="暂无更新；订阅软件发布新版本后会出现在这里" />
            ) : (
              <List
                dataSource={updateItems}
                renderItem={(it) => (
                  <List.Item
                    actions={[
                      it.softwareVisible ? (
                        <Button
                          key="go"
                          type="link"
                          onClick={() => history.push(`/software/${it.softwareId}`)}
                        >
                          查看软件
                        </Button>
                      ) : (
                        <Tag key="na">当前不可用</Tag>
                      ),
                      !it.read ? (
                        <Button
                          key="read"
                          type="link"
                          onClick={() => void handleMarkRead(it.eventId)}
                        >
                          标记已读
                        </Button>
                      ) : (
                        <Typography.Text key="read" type="secondary">
                          已读
                        </Typography.Text>
                      ),
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <span>
                          {!it.read ? <Tag color="red">未读</Tag> : null}
                          {it.softwareName || `软件 ${it.softwareId}`}
                          {it.versionName ? (
                            <Tag style={{ marginLeft: 8 }}>{it.versionName}</Tag>
                          ) : null}
                          {it.businessChange === false ? (
                            <Tag style={{ marginLeft: 4 }}>文案更新</Tag>
                          ) : null}
                        </span>
                      }
                      description={
                        <>
                          {it.changelog ? (
                            <Typography.Paragraph
                              style={{ whiteSpace: "pre-wrap", marginBottom: 4 }}
                            >
                              {it.changelog}
                            </Typography.Paragraph>
                          ) : null}
                          {it.publishedAt ? (
                            <Typography.Text type="secondary">
                              发布于 {it.publishedAt.slice(0, 16).replace("T", " ")}
                            </Typography.Text>
                          ) : null}
                        </>
                      }
                    />
                  </List.Item>
                )}
              />
            )}
            {updateHasMore ? (
              <div style={{ textAlign: "center", marginTop: 12 }}>
                <Button onClick={() => void loadUpdates(true, updateCursor)}>加载更多</Button>
              </div>
            ) : null}
          </div>
        ) : tab === "recent" ? (
          /* UX05：最近浏览（本地记录；服务端过滤不可见；可清空） */
          <div>
            {recentCards.length === 0 ? (
              <Empty description="暂无浏览记录" />
            ) : (
              <>
                <div style={{ marginBottom: 8, textAlign: "right" }}>
                  <Button
                    size="small"
                    onClick={() => {
                      clearRecentBrowse();
                      setRecentCards([]);
                    }}
                  >
                    清空浏览记录
                  </Button>
                </div>
                <List
                  size="small"
                  dataSource={recentCards}
                  renderItem={(card) => (
                    <List.Item
                      actions={[
                        <Button
                          key="go"
                          type="link"
                          onClick={() => history.push(`/software/${card.id}`)}
                        >
                          查看
                        </Button>,
                      ]}
                    >
                      <List.Item.Meta title={card.name} description={card.summary ?? ""} />
                    </List.Item>
                  )}
                />
              </>
            )}
          </div>
        ) : tab === "subscriptions" ? (
          /* UX05：订阅管理（偏好展示 + 退订；调整偏好走详情页订阅设置） */
          <div>
            {subRows.length === 0 ? (
              <Empty description="还没有订阅任何软件" />
            ) : (
              <List
                dataSource={subRows}
                renderItem={(row) => (
                  <List.Item
                    actions={[
                      row.visible ? (
                        <Button
                          key="go"
                          type="link"
                          onClick={() => history.push(`/software/${row.softwareId}`)}
                        >
                          查看软件
                        </Button>
                      ) : (
                        <Tag key="na">当前不可用</Tag>
                      ),
                      <Button
                        key="pref"
                        type="link"
                        onClick={() => {
                          setPrefTarget(row);
                          prefForm.setFieldsValue({
                            platforms: row.platforms || "",
                            channels: row.channels || "",
                          });
                        }}
                      >
                        调整偏好
                      </Button>,
                      <Popconfirm
                        key="unsub"
                        title="确认取消订阅？取消后不再收到该软件的更新推送"
                        okText="取消订阅"
                        onConfirm={() => void handleUnsubscribeRow(row.softwareId)}
                      >
                        <Button key="unsub" type="link" danger>
                          退订
                        </Button>
                      </Popconfirm>,
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <span>
                          {row.name || `软件 ${row.softwareId}`}
                          {row.unreadCount > 0 ? (
                            <Tag color="red" style={{ marginLeft: 8 }}>
                              {row.unreadCount} 条未读
                            </Tag>
                          ) : null}
                        </span>
                      }
                      description={
                        <Typography.Text type="secondary">
                          平台 {row.platforms || "全部"} · 渠道 {row.channels || "全部"}
                        </Typography.Text>
                      }
                    />
                  </List.Item>
                )}
              />
            )}
          </div>
        ) : items.length === 0 ? (
          <Empty description="暂无内容" />
        ) : (
          <List
            dataSource={items}
            renderItem={(item) => (
              <List.Item
                className={styles.item}
                actions={[
                  item.status === "PUBLISHED" ? (
                    <Button
                      key="view"
                      type="link"
                      onClick={() => history.push(`/software/${item.softwareId}`)}
                    >
                      查看
                    </Button>
                  ) : null,
                  <Button
                    key="edit"
                    type="link"
                    disabled={tab === "favorites"
                      || item.status === "CHECKING" || item.status === "PENDING"}
                    title={
                      item.status === "CHECKING" || item.status === "PENDING"
                        ? "提交检查/审核中的快照不可编辑，撤回后可继续修改"
                        : undefined
                    }
                    onClick={() =>
                      history.push(`/software/${item.softwareId}/edit`)
                    }
                  >
                    编辑
                  </Button>,
                  item.status === "DRAFT" ||
                  item.status === "REJECTED" ||
                  item.status === "PENDING" ? (
                    <Popconfirm
                      key="delete"
                      title="删除草稿"
                      description={`删除后不可恢复，确定删除「${item.name}」吗？`}
                      okText="删除"
                      okButtonProps={{ danger: true }}
                      cancelText="取消"
                      onConfirm={() => void handleDeleteDraft(item)}
                    >
                      <Button type="link" danger>
                        删除
                      </Button>
                    </Popconfirm>
                  ) : null,
                  item.status !== "PUBLISHED" ? (
                    <Button
                      key="preview"
                      type="link"
                      onClick={() =>
                        history.push(`/software/${item.softwareId}/preview`)
                      }
                    >
                      查看
                    </Button>
                  ) : null,
                  tab === "favorites" ? null : (
                    <Button
                      key="unsubscribe"
                      type="link"
                      onClick={() => void handleUnsubscribe(item.softwareId)}
                    >
                      取消订阅
                    </Button>
                  ),
                ].filter(Boolean)}
              >
                <List.Item.Meta
                  title={
                    <span
                      style={{
                        cursor: item.unavailable ? 'default' : 'pointer',
                      }}
                      onClick={() => {
                        if (item.unavailable) return
                        history.push(
                          item.status === 'PUBLISHED'
                            ? `/software/${item.softwareId}`
                            : `/software/${item.softwareId}/edit`,
                        )
                      }}
                    >
                      {item.name}
                      {STATUS_LABEL[item.status] ? (
                        <Tag style={{ marginLeft: 8 }}>
                          {STATUS_LABEL[item.status]}
                        </Tag>
                      ) : null}
                    </span>
                  }
                  description={item.summary}
                />
              </List.Item>
            )}
          />
        )}
        {items.length > 0 && (
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
            <Pagination
              current={page}
              pageSize={PAGE_SIZE}
              total={total}
              showSizeChanger={false}
              onChange={(next) => void load(tab, next)}
            />
          </div>
        )}

        <Modal
          title="申请发布资格"
          open={applyOpen}
          onCancel={() => setApplyOpen(false)}
          onOk={() => void handleApply()}
          confirmLoading={applySubmitting}
          okText="提交申请"
          destroyOnHidden
        >
          <Typography.Paragraph type="secondary">
            说明你的发布用途与来源渠道，管理员会据此审核；通过后即可创建软件并提交。
          </Typography.Paragraph>
          <Input.TextArea
            rows={3}
            maxLength={1000}
            placeholder="发布用途与来源说明（1~1000 字）"
            value={applyIntro}
            onChange={(e) => setApplyIntro(e.target.value)}
            style={{ marginBottom: 12 }}
          />
          <Input
            maxLength={500}
            placeholder="拟投分类（如：工具, 效率, 开发）"
            value={applyCategories}
            onChange={(e) => setApplyCategories(e.target.value)}
          />
        </Modal>
      </Spin>
      {/* UX05：订阅偏好调整（平台/渠道，空=全部） */}
      <Modal
        title={`调整订阅偏好（${prefTarget?.name ?? ""}）`}
        open={!!prefTarget}
        onCancel={() => setPrefTarget(null)}
        onOk={async () => {
          if (!prefTarget) return;
          const values = await prefForm.validateFields();
          const res = await subscribeSoftware(prefTarget.softwareId, {
            platforms: values.platforms || undefined,
            channels: values.channels || undefined,
          });
          if (res.data.success) {
            message.success("偏好已更新");
            setPrefTarget(null);
            await loadSubscriptionRows();
          }
        }}
        okText="保存"
        destroyOnHidden
      >
        <Form form={prefForm} layout="vertical">
          <Form.Item
            name="platforms"
            label="平台（逗号分隔，空=全部）"
            extra="如：WINDOWS,MACOS"
          >
            <Input />
          </Form.Item>
          <Form.Item name="channels" label="渠道（逗号分隔，空=全部）" extra="如：STABLE">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
