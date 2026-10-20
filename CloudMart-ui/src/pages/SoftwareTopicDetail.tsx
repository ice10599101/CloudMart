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
              {/* SF05：专题介绍（后台维护的运营文案） */}
              {topic.description ? (
                <p className={styles.description}>{topic.description}</p>
              ) : null}
            </div>
            {/* UX02：工具箱三要素（有内容才显示） */}
            {topic.problem ? (
              <section className={styles.toolboxSection}>
                <h2>这个专题能帮你做什么</h2>
                <p>{topic.problem}</p>
              </section>
            ) : null}
            {topic.audience ? (
              <section className={styles.toolboxSection}>
                <h2>适合谁</h2>
                <p>{topic.audience}</p>
              </section>
            ) : null}
            {topic.prerequisites ? (
              <section className={styles.toolboxSection}>
                <h2>准备条件</h2>
                <p>{topic.prerequisites}</p>
              </section>
            ) : null}
            {topic.groups && topic.groups.length > 0 && topic.groups.some((g) => g.items.length > 0) ? (
              <>
                {topic.groups
                  .filter((g) => g.items.length > 0)
                  .map((group) => (
                    <section key={group.groupKey} className={styles.toolboxGroup}>
                      <h2>{group.groupKey === '' ? '推荐软件' : group.groupKey}</h2>
                      <SoftwareCardGrid
                        cards={group.items
                          .map((item) =>
                            topic.items.find((card) => card.id === item.softwareId),
                          )
                          .filter((card): card is NonNullable<typeof card> => !!card)}
                      />
                      <ul className={styles.roleNotes}>
                        {group.items
                          .filter((item) => item.roleNote)
                          .map((item) => (
                            <li key={item.softwareId}>
                              {item.optional ? '（可选替代）' : ''}
                              {item.roleNote}
                            </li>
                          ))}
                      </ul>
                    </section>
                  ))}
              </>
            ) : topic.items.length === 0 ? (
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
