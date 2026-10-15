import { useEffect, useState } from "react";
import { App, Empty, Spin, Typography } from "antd";
import { history, useParams } from "umi";
import { getSoftwareTopic, type SoftwareTopicDetail } from "@/api/software";
import SoftwareCardGrid from "@/components/software/SoftwareCardGrid";
import styles from "./SoftwareTopicDetail.module.css";

/**
 * 专题详情（P1 方案 §6.11）：运营说明 + 软件清单；清单由服务端按实时可见性
 * 过滤（下架软件自动从公共专题消失），前端不做二次拼装。
 */
export default function SoftwareTopicDetailPage() {
  const { slug = "" } = useParams<{ slug: string }>();
  const { message } = App.useApp();
  const [topic, setTopic] = useState<SoftwareTopicDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    if (!slug) return;
    setLoading(true);
    getSoftwareTopic(slug)
      .then((res) => setTopic(res.data.data))
      .catch(() => {
        setNotFound(true);
        message.error("专题不存在或已下线");
      })
      .finally(() => setLoading(false));
  }, [slug, message]);

  if (notFound) {
    return (
      <div className={styles.page}>
        <Empty description="专题不存在或已下线">
          <a onClick={() => history.push("/software")}>返回软件广场</a>
        </Empty>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <Spin spinning={loading}>
        {topic ? (
          <>
            <Typography.Title level={3} style={{ marginBottom: 4 }}>
              {topic.title}
            </Typography.Title>
            {topic.summary ? (
              <Typography.Paragraph type="secondary">{topic.summary}</Typography.Paragraph>
            ) : null}
            {topic.items.length === 0 ? (
              <Empty description="专题内暂无可见软件" />
            ) : (
              <SoftwareCardGrid cards={topic.items} />
            )}
          </>
        ) : null}
      </Spin>
    </div>
  );
}
