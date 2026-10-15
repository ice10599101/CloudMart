import { useCallback, useEffect, useState } from "react";
import { App, Button, Empty, List, Spin, Tabs, Tag } from "antd";
import { history } from "umi";
import { listMySoftware, type SoftwareMineItem } from "@/api/software";
import { useAuthStore } from "@/stores/auth";
import styles from "./SoftwareMine.module.css";

type TabKey = "published" | "draft" | "pending" | "rejected" | "favorites";

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
        <Button
          type="primary"
          onClick={() => history.push("/software/publish")}
        >
          发布软件
        </Button>
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
                  <Button
                    key="view"
                    type="link"
                    onClick={() => history.push(`/software/${item.softwareId}`)}
                  >
                    查看
                  </Button>,
                  <Button
                    key="edit"
                    type="link"
                    disabled={tab === "favorites"}
                    onClick={() =>
                      history.push(`/software/${item.softwareId}/edit`)
                    }
                  >
                    编辑
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <span>
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
