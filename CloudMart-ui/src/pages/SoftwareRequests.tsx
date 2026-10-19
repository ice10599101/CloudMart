import { useCallback, useEffect, useState } from "react";
import { App } from "antd";
import {
  Button,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from "antd";
import { history, useSearchParams } from "umi";
import {
  closeMySoftwareRequest,
  createSoftwareRequest,
  getSoftwareRequestFeed,
  listMySoftwareRequests,
  type SoftwareRequestFeedItem,
  type SoftwareRequestMineItem,
} from "@/api/software";
import { useAuthStore } from "@/stores/auth";

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  OPEN: { label: "寻找中", color: "processing" },
  ANSWERED: { label: "已找到", color: "success" },
  CLOSED: { label: "已关闭", color: "default" },
  PENDING: { label: "审核中", color: "warning" },
  APPROVED: { label: "已通过", color: "success" },
  REJECTED: { label: "已拒绝", color: "error" },
};

/** UX06：找软件需求——搜索无结果回跳保留关键词；结构化求助；进入审核 */
export default function SoftwareRequests() {
  const { message } = App.useApp();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const [searchParams] = useSearchParams();
  const sourceKeyword = searchParams.get("source") ?? "";

  const [loading, setLoading] = useState(true);
  const [feed, setFeed] = useState<SoftwareRequestFeedItem[]>([]);
  const [mine, setMine] = useState<SoftwareRequestMineItem[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const feedRes = await getSoftwareRequestFeed();
      setFeed(feedRes.data.data ?? []);
      if (isAuthenticated) {
        const mineRes = await listMySoftwareRequests();
        setMine(mineRes.data.data ?? []);
      }
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const res = await createSoftwareRequest({
        title: values.title,
        usageDesc: values.usageDesc,
        mustHaveFeatures: values.mustHaveFeatures
          ? String(values.mustHaveFeatures)
              .split(/[,，]/)
              .map((v: string) => v.trim())
              .filter(Boolean)
          : undefined,
        platformScope: values.platformScope,
        acceptableLicense: values.acceptableLicense,
        sourceKeyword: sourceKeyword || undefined,
      });
      if (res.data.success) {
        message.success("已提交，审核通过后会出现在列表中");
        setOpen(false);
        form.resetFields();
        await load();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: 860, margin: "0 auto", padding: "24px 16px 48px" }}>
      <Typography.Title level={3}>找软件需求</Typography.Title>
      <Typography.Paragraph type="secondary">
        {sourceKeyword
          ? `没有找到与"${sourceKeyword}"相关的软件？描述你的需求，运营会帮你找。`
          : "描述你要完成的事，让运营帮你找合适的软件。"}
      </Typography.Paragraph>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => setOpen(true)}>
          提交需求
        </Button>
        <Button onClick={() => history.push(sourceKeyword ? `/software/explore?keyword=${encodeURIComponent(sourceKeyword)}` : "/software/explore")}>
          返回搜索
        </Button>
      </Space>

      <Typography.Title level={5}>大家在找什么</Typography.Title>
      <Spin spinning={loading}>
        {feed.length === 0 ? (
          <Empty description="还没有公开的需求" />
        ) : (
          <List
            size="small"
            dataSource={feed}
            renderItem={(item) => (
              <List.Item>
                <Typography.Text>{item.title}</Typography.Text>
                <Tag color={STATUS_MAP[item.status]?.color}>
                  {STATUS_MAP[item.status]?.label}
                </Tag>
                {item.matchedSoftware ? (
                  item.matchedSoftware.visible ? (
                    <Button
                      type="link"
                      size="small"
                      onClick={() => history.push(`/software/${item.matchedSoftware!.softwareId}`)}
                    >
                      {item.matchedSoftware.name || "查看软件"}
                    </Button>
                  ) : (
                    <Typography.Text type="secondary">
                      已找到的软件暂不可用
                    </Typography.Text>
                  )
                ) : null}
              </List.Item>
            )}
          />
        )}

        {isAuthenticated && mine.length > 0 ? (
          <>
            <Typography.Title level={5} style={{ marginTop: 24 }}>
              我的需求
            </Typography.Title>
            <List
              size="small"
              dataSource={mine}
              renderItem={(item) => (
                <List.Item
                  actions={[
                    <Popconfirm
                      key="close"
                      title="关闭该需求？"
                      onConfirm={() =>
                        closeMySoftwareRequest(item.id).then(() => load())
                      }
                    >
                      {item.status !== "CLOSED" ? (
                        <Button type="link" size="small">
                          关闭
                        </Button>
                      ) : null}
                    </Popconfirm>,
                  ]}
                >
                  <List.Item.Meta
                    title={
                      <span>
                        {item.title}
                        <Tag color={STATUS_MAP[item.auditStatus]?.color} style={{ marginLeft: 8 }}>
                          {STATUS_MAP[item.auditStatus]?.label}
                        </Tag>
                        <Tag style={{ marginLeft: 4 }}>{STATUS_MAP[item.status]?.label}</Tag>
                      </span>
                    }
                    description={
                      item.auditStatus === "REJECTED" && item.auditReason ? (
                        <Typography.Text type="danger">
                          未通过：{item.auditReason}
                        </Typography.Text>
                      ) : item.matchedSoftware?.visible ? (
                        <Typography.Text type="secondary">
                          已找到：{item.matchedSoftware.name || `软件 ${item.matchedSoftware.softwareId}`}
                          {item.matchedReason ? `（${item.matchedReason}）` : ""}
                        </Typography.Text>
                      ) : null
                    }
                  />
                </List.Item>
              )}
            />
          </>
        ) : null}
      </Spin>

      <Modal
        title="提交找软件需求"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        confirmLoading={submitting}
        okText="提交"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="一句话需求（≤80 字）"
            rules={[{ required: true, max: 80 }]}
          >
            <Input placeholder="如：找一款按拍摄日期批量重命名照片的工具" />
          </Form.Item>
          <Form.Item
            name="usageDesc"
            label="用途描述（20~500 字，说清楚要完成什么）"
            rules={[{ required: true, min: 20, max: 500 }]}
          >
            <Input.TextArea
              rows={4}
              placeholder={
                sourceKeyword
                  ? `我搜索"${sourceKeyword}"没有找到合适的……（已自动记录来源）`
                  : "要完成什么任务、什么场景、多少量级……"
              }
            />
          </Form.Item>
          <Form.Item name="mustHaveFeatures" label="必须功能（逗号分隔，可选）">
            <Input placeholder="如：批量处理,预览,撤销" />
          </Form.Item>
          <Space size="middle" wrap>
            <Form.Item name="platformScope" label="可接受平台">
              <Select
                mode="multiple"
                style={{ width: 260 }}
                placeholder="不选=不限"
                options={["WINDOWS", "MACOS", "LINUX", "WEB"].map((v) => ({ value: v, label: v }))}
              />
            </Form.Item>
            <Form.Item name="acceptableLicense" label="可接受许可">
              <Select
                mode="multiple"
                style={{ width: 240 }}
                placeholder="不选=不限"
                options={["FREE", "OPENSOURCE", "TRIAL", "COMMERCIAL", "ANY"].map((v) => ({
                  value: v,
                  label: v,
                }))}
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
}
