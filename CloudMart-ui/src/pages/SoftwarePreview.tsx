import { useEffect, useState } from "react";
import { Button, Card, Descriptions, Spin, Tag, Typography } from "antd";
import DOMPurify from "dompurify";
import { history, useParams } from "umi";
import {
  getSoftwareEditor,
  listSoftwareCategories,
  type SoftwareCategory,
  type SoftwareEditorData,
} from "@/api/software";
import styles from "./SoftwarePreview.module.css";

const LICENSE_LABEL: Record<string, string> = {
  FREE: "免费",
  OPEN_SOURCE: "开源",
  TRIAL: "试用",
  COMMERCIAL: "商业",
};

const ATTRIBUTION_LABEL: Record<string, string> = {
  ORIGINAL: "原创",
  AUTHORIZED: "授权转载",
  OFFICIAL_PUBLIC: "官方公开内容",
};

const SOURCE_TYPE_LABEL: Record<string, string> = {
  NETDISK: "网盘链接",
  MAGNET: "磁力链接",
  TORRENT_URL: "种子外链",
};

const PLATFORM_LABEL: Record<string, string> = {
  WINDOWS: "Windows",
  MACOS: "macOS",
  LINUX: "Linux",
  ANDROID: "Android",
  IOS: "iOS",
  CROSS_PLATFORM: "跨平台",
};

/** 来源展示：网盘展示链接与提取码；磁力/种子显示链接文本（作者本人可见完整信息） */
function SourceLines({ sources }: { sources: SoftwareEditorData["releases"] }) {
  return (
    <div className={styles.sourceList}>
      {sources.map((release) => (
        <Card
          key={release.releaseId ?? release.versionName}
          size="small"
          title={`版本 ${release.versionName}${release.recommended ? "（推荐）" : ""}`}
          className={styles.releaseCard}
        >
          {release.changelog ? (
            <Typography.Paragraph
              type="secondary"
              style={{ whiteSpace: "pre-wrap", marginBottom: 8 }}
            >
              更新日志：{release.changelog}
            </Typography.Paragraph>
          ) : null}
          {release.sources.map((source, index) => (
            <div key={source.sourceId ?? index} className={styles.sourceRow}>
              <Tag>{source.label}</Tag>
              <Tag>{PLATFORM_LABEL[source.platform ?? ""] ?? source.platform}</Tag>
              <span className={styles.sourceType}>
                {SOURCE_TYPE_LABEL[source.sourceType] ?? source.sourceType}
              </span>
              {source.url ? (
                <Typography.Text copyable className={styles.sourceUrl}>
                  {source.url}
                </Typography.Text>
              ) : null}
              {source.extractionCode ? (
                <Typography.Text type="secondary">
                  提取码 {source.extractionCode}
                </Typography.Text>
              ) : null}
            </div>
          ))}
        </Card>
      ))}
    </div>
  );
}

/** 作者侧只读预览：草稿/待审/被退回的「查看」入口（编辑走 /edit） */
export default function SoftwarePreview() {
  const { id = "" } = useParams<{ id: string }>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editor, setEditor] = useState<SoftwareEditorData | null>(null);
  const [categories, setCategories] = useState<SoftwareCategory[]>([]);

  useEffect(() => {
    listSoftwareCategories()
      .then((res) => {
        if (res.data.success) setCategories(res.data.data ?? []);
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getSoftwareEditor(id)
      .then((res) => {
        if (res.data.success) {
          setEditor(res.data.data);
        } else {
          setError(res.data.error?.message || "草稿不存在或无权查看");
        }
      })
      .catch(() => setError("加载失败，请稍后重试"))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error || !editor) {
    return (
      <div style={{ padding: 48 }}>
        <Typography.Title level={4}>{error ?? "加载失败"}</Typography.Title>
        <Button type="primary" onClick={() => history.push("/software/mine")}>
          返回我的软件
        </Button>
      </div>
    );
  }

  const statusLabel: Record<string, string> = {
    DRAFT: "草稿",
    PENDING: "待审核",
    APPROVED: "已通过",
    REJECTED: "已退回",
  };

  return (
    <div className={styles.page}>
      <Card className={styles.section}>
        <div className={styles.head}>
          {editor.iconAssetId ? (
            <img
              src={`/files/by-id/${editor.iconAssetId}`}
              alt="软件图标"
              style={{
                width: 56,
                height: 56,
                borderRadius: 12,
                objectFit: "cover",
              }}
            />
          ) : null}
          <Typography.Title level={3} style={{ marginBottom: 0 }}>
            {editor.name}
          </Typography.Title>
          <Tag color="processing">
            {statusLabel[editor.reviewStatus] ?? editor.reviewStatus}
          </Tag>
        </div>
        {editor.summary ? (
          <Typography.Paragraph className={styles.summary}>
            {editor.summary}
          </Typography.Paragraph>
        ) : null}
        <Descriptions column={2} size="small" className={styles.meta}>
          <Descriptions.Item label="分类">
            {categories.find((item) => item.id === editor.categoryId)?.name ??
              editor.categoryId ??
              "-"}
          </Descriptions.Item>
          <Descriptions.Item label="授权类型">
            {editor.licenseType
              ? (LICENSE_LABEL[editor.licenseType] ?? editor.licenseType)
              : "-"}
          </Descriptions.Item>
          <Descriptions.Item label="来源声明">
            {editor.attributionType
              ? (ATTRIBUTION_LABEL[editor.attributionType] ??
                editor.attributionType)
              : "-"}
          </Descriptions.Item>
          <Descriptions.Item label="开发者">
            {editor.developerName || "-"}
          </Descriptions.Item>
          <Descriptions.Item label="官方网站">
            {editor.officialUrl || "-"}
          </Descriptions.Item>
          <Descriptions.Item label="系统要求">
            {editor.systemRequirements || "-"}
          </Descriptions.Item>
        </Descriptions>
        {editor.reviewStatus === "REJECTED" && editor.reviewReason ? (
          <Typography.Paragraph type="danger">
            退回原因：{editor.reviewReason}
          </Typography.Paragraph>
        ) : null}
      </Card>

      <Card className={styles.section} title="软件介绍">
        <div
          className={styles.description}
          dangerouslySetInnerHTML={{
            // 服务端保存时已消毒；客户端 DOMPurify 二次兜底
            __html: DOMPurify.sanitize(editor.descriptionHtml ?? ""),
          }}
        />
      </Card>

      <Card className={styles.section} title="版本与下载来源">
        {editor.releases.length === 0 ? (
          <Typography.Text type="secondary">暂无版本</Typography.Text>
        ) : (
          <SourceLines sources={editor.releases} />
        )}
      </Card>

      <div className={styles.actions}>
        <Button onClick={() => history.push("/software/mine")}>
          返回我的软件
        </Button>
        {editor.reviewStatus === "DRAFT" ||
        editor.reviewStatus === "REJECTED" ? (
          <Button
            type="primary"
            onClick={() => history.push(`/software/${editor.softwareId}/edit`)}
          >
            去编辑
          </Button>
        ) : null}
      </div>
    </div>
  );
}
