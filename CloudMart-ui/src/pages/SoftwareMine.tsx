import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "umi";
import { App, Button, Empty, Input, List, Modal, Pagination, Popconfirm, Space, Spin, Switch, Tabs, Tag, Typography } from "antd";
import { history } from "umi";
import {
  deleteMySoftware,
  getSoftwarePreferences,
  listMySoftware,
  unsubscribeSoftware,
  updateSoftwarePreferences,
  type SoftwareMineItem,
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
] as const;

type TabKey =
  | "published"
  | "draft"
  | "pending"
  | "rejected"
  | "favorites"
  | "subscriptions";

const STATUS_LABEL: Record<string, string> = {
  PUBLISHED: "已发布",
  DRAFT: "草稿",
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
  /** P1 更新提醒总开关（缺省开启；与订阅关系相互独立） */
  const [updateNotify, setUpdateNotify] = useState(true);
  /** SF03：发布资格与申请状态 */
  const [pubStatus, setPubStatus] = useState<{ canPublish: boolean } | null>(null);
  const [myApplications, setMyApplications] = useState<SoftwarePublisherApplication[]>([]);
  const [applyOpen, setApplyOpen] = useState(false);
  const [applyIntro, setApplyIntro] = useState("");
  const [applyCategories, setApplyCategories] = useState("");
  const [applySubmitting, setApplySubmitting] = useState(false);
  const [notifySaving, setNotifySaving] = useState(false);

  const load = useCallback(
    async (nextTab: TabKey, nextPage = 1) => {
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
    if (!isAuthenticated) return;
    void load(tab);
  }, [isAuthenticated, load, tab]);

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
        ]}
      />
      <Spin spinning={loading}>
        {items.length === 0 ? (
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
                    disabled={tab === "favorites" || tab === "subscriptions"}
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
                  tab === "subscriptions" ? (
                    <Button
                      key="unsubscribe"
                      type="link"
                      onClick={() => void handleUnsubscribe(item.softwareId)}
                    >
                      取消订阅
                    </Button>
                  ) : null,
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
    </div>
  );
}
