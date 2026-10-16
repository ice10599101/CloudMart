import { useCallback, useEffect, useState } from "react";
import { App, Button, Empty, List, Popconfirm, Space, Spin, Switch, Tabs, Tag } from "antd";
import { history } from "umi";
import {
  deleteMySoftware,
  getSoftwarePreferences,
  listMySoftware,
  unsubscribeSoftware,
  updateSoftwarePreferences,
  type SoftwareMineItem,
} from "@/api/software";
import { useAuthStore } from "@/stores/auth";
import styles from "./SoftwareMine.module.css";

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
  REJECTED: "已退回",
};

export default function SoftwareMine() {
  const { message } = App.useApp();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [tab, setTab] = useState<TabKey>("published");
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<SoftwareMineItem[]>([]);
  /** P1 更新提醒总开关（缺省开启；与订阅关系相互独立） */
  const [updateNotify, setUpdateNotify] = useState(true);
  const [notifySaving, setNotifySaving] = useState(false);

  const load = useCallback(
    async (nextTab: TabKey) => {
      setLoading(true);
      try {
        const res = await listMySoftware({ tab: nextTab, page: 1, size: 50 });
        if (!res.data.success) return;
        setItems(res.data.data?.records ?? []);
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
  }, [isAuthenticated]);

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
      await deleteMySoftware(item.softwareId, item.lockVersion);
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
                      description={`软删除「${item.name}」，不可恢复，确认？`}
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
      </Spin>
    </div>
  );
}
