import React, { useEffect, useState } from "react";
import { Button, Empty, Spin, Table, Tag, Typography } from "antd";
import { history, useSearchParams } from "umi";
import {
  compareSoftware,
  type SoftwareCompareItem,
} from "@/api/software";

const NOT_PROVIDED = "未提供";

const LICENSE_LABEL: Record<string, string> = {
  FREE: "免费",
  OPENSOURCE: "开源",
  COMMERCIAL: "商业",
  TRIAL: "试用",
  FREEMIUM: "免费增值",
  SUBSCRIPTION: "订阅",
};

/** UX04-B：软件对比（2~3 款并排）——字段行×软件列；缺失"未提供"；不生成立场性排行 */
export default function SoftwareCompare() {
  const [searchParams] = useSearchParams();
  const idsParam = searchParams.get("ids") ?? "";
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<SoftwareCompareItem[]>([]);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const ids = idsParam.split(",").map((v) => v.trim()).filter(Boolean);
    if (ids.length < 2) {
      setFailed(true);
      setLoading(false);
      return;
    }
    setLoading(true);
    compareSoftware(ids)
      .then((res) => setItems(res.data.data ?? []))
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  }, [idsParam]);

  if (loading) return <Spin style={{ display: "block", margin: "80px auto" }} />;
  if (failed || items.length < 2) {
    return (
      <div style={{ maxWidth: 720, margin: "48px auto", padding: "0 16px" }}>
        <Empty description="对比需要 2~3 个可见软件（链接可能包含已下架软件）">
          <Button onClick={() => history.push("/software/explore")}>去筛选软件</Button>
        </Empty>
      </div>
    );
  }

  const boolCell = (v: boolean | null) =>
    v === null ? <Typography.Text type="secondary">{NOT_PROVIDED}</Typography.Text>
      : v ? <Tag color="success">是</Tag> : <Tag>否</Tag>;

  const rows: Array<{ key: string; label: string; render: (it: SoftwareCompareItem) => React.ReactNode }> = [
    {
      key: "purpose",
      label: "用途",
      render: (it) => <Typography.Text>{it.summary || NOT_PROVIDED}</Typography.Text>,
    },
    {
      key: "platforms",
      label: "平台",
      render: (it) => it.fields.platforms.length
        ? it.fields.platforms.map((p) => <Tag key={p}>{p}</Tag>)
        : <Typography.Text type="secondary">{NOT_PROVIDED}</Typography.Text>,
    },
    {
      key: "license",
      label: "许可",
      render: (it) => {
        const license = it.fields.licenseType;
        if (!license) return <Typography.Text type="secondary">{NOT_PROVIDED}</Typography.Text>;
        return (
          <span>
            <Tag color="blue">{LICENSE_LABEL[license] ?? license}</Tag>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              以官网为准
            </Typography.Text>
          </span>
        );
      },
    },
    { key: "openSource", label: "是否开源", render: (it) => boolCell(it.fields.openSource) },
    { key: "offline", label: "离线可用", render: (it) => boolCell(it.fields.offlineCapable) },
    {
      key: "languages",
      label: "语言",
      render: (it) => it.fields.languages.length
        ? it.fields.languages.map((l) => <Tag key={l}>{l}</Tag>)
        : <Typography.Text type="secondary">{NOT_PROVIDED}</Typography.Text>,
    },
    {
      key: "sysreq",
      label: "系统要求",
      render: (it) => <Typography.Text>{it.fields.systemRequirements || NOT_PROVIDED}</Typography.Text>,
    },
    {
      key: "updated",
      label: "近期更新",
      render: (it) => <Typography.Text>{it.lastReleaseAt?.slice(0, 10) || NOT_PROVIDED}</Typography.Text>,
    },
    {
      key: "rating",
      label: "用户评分",
      render: (it) => it.ratingCount === 0
        ? <Typography.Text type="secondary">暂无评分</Typography.Text>
        : <span>{it.ratingAvg ?? "-"} 分（{it.ratingCount} 人）</span>,
    },
    {
      key: "access",
      label: "获取条件",
      render: (it) => <Typography.Text>{it.accessMode ?? "自由获取"}</Typography.Text>,
    },
  ];

  return (
    <div style={{ maxWidth: 1080, margin: "0 auto", padding: "24px 16px 48px" }}>
      <Typography.Title level={3}>软件对比</Typography.Title>
      <Typography.Paragraph type="secondary">
        客观字段并排展示；标注"未提供"表示运营未声明该项，不代表支持或不支持。
        许可仅显示已知类别，实时价格以官网为准。
      </Typography.Paragraph>
      {/* 移动端横向滚动，固定字段名列 */}
      <div style={{ overflowX: "auto" }}>
        <Table
          rowKey="key"
          size="small"
          bordered
          pagination={false}
          dataSource={rows}
          columns={[
            { title: "字段", dataIndex: "label", width: 110, fixed: "left" as const },
            ...items.map((it) => ({
              title: (
                <a onClick={() => history.push(`/software/${it.softwareId}`)}>{it.name}</a>
              ),
              key: it.softwareId,
              render: (_: unknown, row: (typeof rows)[number]) => row.render(it),
            })),
          ] as never}
        />
      </div>
    </div>
  );
}
