import { useEffect, useState } from "react";
import { Button, Empty, Segmented, Spin, Tag, Typography } from "antd";
import { history } from "umi";
import {
  listSoftwareGuides,
  type SoftwareGuideRow,
  type SoftwareGuideType,
} from "@/api/software";
import styles from "./SoftwareGuides.module.css";

const TYPE_LABEL: Record<SoftwareGuideType, string> = {
  INSTALL_GUIDE: "安装入门",
  WORKFLOW: "场景工作流",
  COMPARISON: "选型对比",
  TROUBLESHOOTING: "常见问题",
};

/** UX04：教程列表（正文真源在社区；卡片跳详情，详情再跳社区文章） */
export default function SoftwareGuides() {
  const [loading, setLoading] = useState(true);
  const [type, setType] = useState<SoftwareGuideType | null>(null);
  const [rows, setRows] = useState<SoftwareGuideRow[]>([]);

  useEffect(() => {
    setLoading(true);
    listSoftwareGuides(type, 1, 24)
      .then((res) => setRows(res.data.data?.records ?? []))
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, [type]);

  return (
    <div className={styles.page}>
      <Typography.Title level={3}>教程与选型攻略</Typography.Title>
      <Typography.Paragraph type="secondary">
        安装入门、场景工作流、选型对比与常见问题；正文在社区，
        每篇标注适用平台与检查日期，关联软件可直接跳转详情。
      </Typography.Paragraph>
      <Segmented
        style={{ marginBottom: 16 }}
        value={type ?? "ALL"}
        onChange={(v) => setType(v === "ALL" ? null : (v as SoftwareGuideType))}
        options={[
          { label: "全部", value: "ALL" },
          ...Object.entries(TYPE_LABEL).map(([value, label]) => ({
            value,
            label,
          })),
        ]}
      />
      <Spin spinning={loading}>
        {rows.length === 0 ? (
          <Empty description="暂无教程；运营在有真实材料后发布" />
        ) : (
          <div className={styles.grid}>
            {rows.map((row) => (
              <Button
                key={row.id}
                type="default"
                className={styles.card}
                onClick={() => history.push(`/software/guides/${row.id}`)}
                style={{ height: "auto", textAlign: "left", padding: 16 }}
              >
                <Tag color="blue">{TYPE_LABEL[row.guideType]}</Tag>
                {row.platformScope.map((p) => (
                  <Tag key={p}>{p}</Tag>
                ))}
                <Typography.Title level={5} style={{ marginTop: 8 }}>
                  {row.title}
                </Typography.Title>
                {row.summary ? (
                  <Typography.Paragraph
                    type="secondary"
                    ellipsis={{ rows: 2 }}
                    style={{ marginBottom: 0 }}
                  >
                    {row.summary}
                  </Typography.Paragraph>
                ) : null}
              </Button>
            ))}
          </div>
        )}
      </Spin>
    </div>
  );
}
