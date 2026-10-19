import { useEffect, useState } from "react";
import { Button, Descriptions, Empty, Spin, Tag, Typography } from "antd";
import { history, useParams } from "umi";
import { getSoftwareGuide, type SoftwareGuideDetail } from "@/api/software";

const TYPE_LABEL: Record<string, string> = {
  INSTALL_GUIDE: "安装入门",
  WORKFLOW: "场景工作流",
  COMPARISON: "选型对比",
  TROUBLESHOOTING: "常见问题",
};

/** UX04：教程详情——元数据（适用范围/检查日期/关联软件）+ 社区文章跳转（正文真源） */
export default function SoftwareGuideDetail() {
  const { id } = useParams<{ id: string }>();
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<SoftwareGuideDetail | null>(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getSoftwareGuide(id)
      .then((res) => {
        if (res.data.success) setDetail(res.data.data);
        else setNotFound(true);
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Spin style={{ display: "block", margin: "80px auto" }} />;
  if (notFound || !detail) {
    return (
      <div style={{ maxWidth: 720, margin: "48px auto", padding: "0 16px" }}>
        <Empty description="教程不存在或已下架">
          <Button onClick={() => history.push("/software/guides")}>返回教程列表</Button>
        </Empty>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 860, margin: "0 auto", padding: "24px 16px 48px" }}>
      <Button type="link" onClick={() => history.push("/software/guides")}>
        ← 返回教程列表
      </Button>
      <Typography.Title level={3}>{detail.title}</Typography.Title>
      <div style={{ marginBottom: 12 }}>
        <Tag color="blue">{TYPE_LABEL[detail.guideType] ?? detail.guideType}</Tag>
        {detail.platformScope.map((p) => (
          <Tag key={p}>{p}</Tag>
        ))}
      </div>
      {detail.summary ? (
        <Typography.Paragraph type="secondary">{detail.summary}</Typography.Paragraph>
      ) : null}
      <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
        {detail.appliesToVersion ? (
          <Descriptions.Item label="适用版本">
            {detail.appliesToVersion}
          </Descriptions.Item>
        ) : null}
        {detail.checkedAt ? (
          <Descriptions.Item label="内容检查日期">
            {detail.checkedAt}
            {detail.checkNote ? `（${detail.checkNote}）` : ""}
          </Descriptions.Item>
        ) : null}
      </Descriptions>
      {(detail.relatedSoftwares ?? []).length > 0 ? (
        <div style={{ marginBottom: 16 }}>
          <Typography.Text strong>关联软件：</Typography.Text>
          {detail.relatedSoftwares.map((s) => (
            <Button
              key={s.softwareId}
              type="link"
              onClick={() => history.push(`/software/${s.softwareId}`)}
            >
              {s.name || `软件 ${s.softwareId}`}
            </Button>
          ))}
        </div>
      ) : null}
      {/* 正文真源在社区：跳转社区文章页；文章撤回时本入口已实时隐藏 */}
      <Button
        type="primary"
        onClick={() => history.push(`/community/posts/${detail.articleId}`)}
      >
        阅读完整教程（社区文章）
      </Button>
      <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
        教程正文与讨论在社区；软件入口同步文章状态，文章下线后本页将不再展示。
      </Typography.Paragraph>
    </div>
  );
}
