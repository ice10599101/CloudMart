import { useEffect, useState } from "react";
import { App, Empty, Spin } from "antd";
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
            <div className={styles.hero}>
              <p className={styles.eyebrow}>专题合集</p>
              <h1 className={styles.title}>{topic.title}</h1>
              {topic.summary ? (
                <p className={styles.summary}>{topic.summary}</p>
              ) : null}
            </div>
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
