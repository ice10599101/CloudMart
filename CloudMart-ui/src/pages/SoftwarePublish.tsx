import { useCallback, useEffect, useState } from "react";
import {
  App,
  Button,
  Card,
  Input,
  InputNumber,
  Modal,
  Result,
  Select,
  Space,
  Switch,
  Typography,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { history } from "umi";
import TiptapEditor from "@/components/TiptapEditor";
import {
  createSoftware,
  getPublisherStatus,
  listSoftwareAccessContacts,
  listSoftwareCategories,
  listAllSoftwareTags,
  saveSoftwareDraft,
  submitSoftwareForReview,
  type SoftwareAccessMode,
  type SoftwareCategory,
  type SoftwareContact,
  type SoftwareLicenseType,
  type SoftwareSourceType,
  type SoftwareTag,
} from "@/api/software";
import { useAuthStore } from "@/stores/auth";
import styles from "./SoftwarePublish.module.css";

const LICENSE_OPTIONS: { value: SoftwareLicenseType; label: string }[] = [
  { value: "FREE", label: "免费" },
  { value: "OPEN_SOURCE", label: "开源" },
  { value: "TRIAL", label: "试用" },
  { value: "COMMERCIAL", label: "商业" },
];

const SOURCE_TYPE_OPTIONS: { value: SoftwareSourceType; label: string }[] = [
  { value: "NETDISK", label: "网盘链接" },
  { value: "MAGNET", label: "磁力链接" },
  { value: "TORRENT_URL", label: "种子外链" },
];

interface SourceDraft {
  sourceType: SoftwareSourceType;
  /** 网盘 HTTPS 链接 / magnet: 链接 / .torrent 外链，按类型择一 */
  link: string;
  extractionCode: string;
}

interface ReleaseDraft {
  versionName: string;
  changelog: string;
  recommended: boolean;
  sources: SourceDraft[];
}

const emptyRelease = (): ReleaseDraft => ({
  versionName: "",
  changelog: "",
  recommended: true,
  sources: [{ sourceType: "NETDISK", link: "", extractionCode: "" }],
});

export default function SoftwarePublish() {
  const { message } = App.useApp();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [checking, setChecking] = useState(true);
  const [canPublish, setCanPublish] = useState(false);
  const [denyReason, setDenyReason] = useState<string | null>(null);

  const [categories, setCategories] = useState<SoftwareCategory[]>([]);
  const [tags, setTags] = useState<SoftwareTag[]>([]);
  const [contacts, setContacts] = useState<SoftwareContact[]>([]);

  const [softwareId, setSoftwareId] = useState<string | null>(null);
  const [revisionId, setRevisionId] = useState<string | null>(null);
  const [lockVersion, setLockVersion] = useState<number | null>(null);

  const [name, setName] = useState("");
  const [summary, setSummary] = useState("");
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined);
  const [descriptionHtml, setDescriptionHtml] = useState("");
  const [licenseType, setLicenseType] = useState<SoftwareLicenseType>("FREE");
  const [developerName, setDeveloperName] = useState("");
  const [officialUrl, setOfficialUrl] = useState("");
  const [systemRequirements, setSystemRequirements] = useState("");
  const [tagIds, setTagIds] = useState<string[]>([]);
  const [accessMode, setAccessMode] =
    useState<SoftwareAccessMode>("REGISTERED");
  const [minCommunityLevel, setMinCommunityLevel] = useState<number>(1);
  const [accessContactId, setAccessContactId] = useState<string | undefined>(
    undefined,
  );
  const [releases, setReleases] = useState<ReleaseDraft[]>([emptyRelease()]);

  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 发布资格默认关闭：未开通直接拦在页面外（A31）
  useEffect(() => {
    if (!isAuthenticated) {
      setChecking(false);
      return;
    }
    getPublisherStatus()
      .then((res) => {
        if (!res.data.success) return;
        setCanPublish(res.data.data?.canPublish ?? false);
        setDenyReason(res.data.data?.reason ?? null);
      })
      .finally(() => setChecking(false));
  }, [isAuthenticated]);

  useEffect(() => {
    if (!canPublish) return;
    listSoftwareCategories()
      .then((res) => {
        if (res.data.success) setCategories(res.data.data ?? []);
      })
      .catch(() => undefined);
    listAllSoftwareTags()
      .then(setTags)
      .catch(() => undefined);
    listSoftwareAccessContacts()
      .then((res) => {
        if (res.data.success) setContacts(res.data.data ?? []);
      })
      .catch(() => undefined);
  }, [canPublish]);

  const buildPayload = useCallback(() => {
    const sourcePayload = releases.flatMap((release) =>
      release.sources
        .filter((source) => source.link.trim().length > 0)
        .map((source) => ({
          sourceType: source.sourceType,
          ...(source.sourceType === "NETDISK"
            ? {
                url: source.link.trim(),
                extractionCode: source.extractionCode.trim() || undefined,
              }
            : {}),
          ...(source.sourceType === "MAGNET"
            ? { magnetUri: source.link.trim() }
            : {}),
          ...(source.sourceType === "TORRENT_URL"
            ? { torrentUrl: source.link.trim() }
            : {}),
        })),
    );
    return {
      name,
      summary,
      categoryId,
      descriptionHtml,
      licenseType,
      developerName: developerName || undefined,
      officialUrl: officialUrl || undefined,
      systemRequirements: systemRequirements || undefined,
      accessMode,
      minCommunityLevel:
        accessMode === "COMMUNITY_LEVEL" ? minCommunityLevel : undefined,
      accessContactId:
        accessMode === "ADMIN_PAID" ? accessContactId : undefined,
      tagIds,
      releases: releases
        .filter((release) => release.versionName.trim().length > 0)
        .map((release) => ({
          versionName: release.versionName.trim(),
          changelog: release.changelog || undefined,
          recommended: release.recommended,
          sources: sourcePayload,
        })),
    };
  }, [
    accessContactId,
    accessMode,
    categoryId,
    descriptionHtml,
    developerName,
    licenseType,
    minCommunityLevel,
    name,
    officialUrl,
    releases,
    summary,
    systemRequirements,
    tagIds,
  ]);

  const handleSaveDraft = useCallback(async () => {
    if (!name.trim()) {
      message.warning("请先填写软件名称");
      return;
    }
    setSaving(true);
    try {
      // 首次保存：create 只建空草稿（后端不读 body），须紧接着把快照 saveDraft 落库
      if (!softwareId || !revisionId) {
        const created = await createSoftware(buildPayload());
        if (!created.data.success) return;
        const sid = created.data.data.softwareId;
        const rid = created.data.data.workingRevisionId;
        setSoftwareId(sid);
        setRevisionId(rid);
        const saved = await saveSoftwareDraft(sid, rid, {
          ...buildPayload(),
          expectedVersion: created.data.data.lockVersion,
        });
        if (!saved.data.success) return;
        setLockVersion(saved.data.data?.lockVersion ?? created.data.data.lockVersion);
        message.success("草稿已保存");
        return;
      }
      const res = await saveSoftwareDraft(softwareId, revisionId, {
        ...buildPayload(),
        expectedVersion: lockVersion ?? undefined,
      });
      if (!res.data.success) return;
      setLockVersion(res.data.data?.lockVersion ?? lockVersion);
      message.success("草稿已保存");
    } finally {
      setSaving(false);
    }
  }, [buildPayload, lockVersion, message, name, revisionId, softwareId]);

  /** 提交审核二次确认：提交即进入待审队列，内容冻结不可再编辑 */
  const confirmSubmit = useCallback(() => {
    Modal.confirm({
      title: '提交审核',
      content: '提交后内容冻结进入待审队列，审核期间不可再编辑（可撤回）。确认提交？',
      okText: '确认提交',
      cancelText: '再改改',
      onOk: () => doSubmit(),
    })
  }, [])

  const doSubmit = useCallback(async () => {
    if (!name.trim()) {
      message.warning("请先填写软件名称");
      return;
    }
    if (releases.every((release) => release.versionName.trim().length === 0)) {
      message.warning("请至少填写一个版本号");
      return;
    }
    setSubmitting(true);
    try {
      // 未保存过则先创建并落快照再提交，保证审核的是同一份内容（create 本身不携带快照）
      let targetSoftwareId = softwareId;
      let targetRevisionId = revisionId;
      let version = lockVersion;
      if (!targetSoftwareId || !targetRevisionId) {
        const created = await createSoftware(buildPayload());
        if (!created.data.success) return;
        targetSoftwareId = created.data.data.softwareId;
        targetRevisionId = created.data.data.workingRevisionId;
        version = created.data.data.lockVersion;
        setSoftwareId(targetSoftwareId);
        setRevisionId(targetRevisionId);
        const saved = await saveSoftwareDraft(targetSoftwareId, targetRevisionId, {
          ...buildPayload(),
          expectedVersion: version,
        });
        if (!saved.data.success) return;
        version = saved.data.data?.lockVersion ?? version;
        setLockVersion(version);
      }
      const res = await submitSoftwareForReview(
        targetSoftwareId,
        targetRevisionId,
        {
          expectedVersion: version ?? undefined,
        },
      );
      if (!res.data.success) return;
      message.success("已提交审核，审核通过后公开可见");
      history.push("/software/mine");
    } finally {
      setSubmitting(false);
    }
  }, [
    buildPayload,
    lockVersion,
    message,
    name,
    releases,
    revisionId,
    softwareId,
  ]);

  if (!isAuthenticated) {
    return (
      <div className={styles.page}>
        <Result
          status="warning"
          title="请先登录"
          subTitle="发布软件需要先登录账号"
          extra={
            <Button type="primary" onClick={() => history.push("/login")}>
              去登录
            </Button>
          }
        />
      </div>
    );
  }

  if (checking) {
    return (
      <div className={styles.page}>
        <Typography.Text>正在校验发布资格……</Typography.Text>
      </div>
    );
  }

  if (!canPublish) {
    return (
      <div className={styles.page}>
        <Result
          status="403"
          title="尚未开通发布资格"
          subTitle={
            denyReason ?? "发布权限默认关闭，需管理员在后台逐用户开通后才能投稿"
          }
          extra={
            <Button onClick={() => history.push("/software")}>
              返回软件广场
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>发布软件</h1>
      <p className={styles.subtitle}>填写资料并提交审核；审核通过后对外可见</p>

      <Card className={styles.section} title="基础信息">
        <Space direction="vertical" style={{ width: "100%" }} size="middle">
          <Input
            placeholder="软件名称"
            value={name}
            maxLength={80}
            onChange={(event) => setName(event.target.value)}
          />
          <Input.TextArea
            placeholder="一句话说明"
            value={summary}
            maxLength={200}
            showCount
            rows={2}
            onChange={(event) => setSummary(event.target.value)}
          />
          <Space wrap>
            <Select
              style={{ width: 200 }}
              placeholder="选择分类"
              value={categoryId}
              onChange={setCategoryId}
              options={categories.map((item) => ({
                value: item.id,
                label: item.name,
              }))}
            />
            <Select
              style={{ width: 160 }}
              value={licenseType}
              onChange={setLicenseType}
              options={LICENSE_OPTIONS}
            />
          </Space>
          <Space wrap>
            <Input
              style={{ width: 200 }}
              placeholder="开发者名称（选填）"
              value={developerName}
              onChange={(event) => setDeveloperName(event.target.value)}
            />
            <Input
              style={{ width: 260 }}
              placeholder="官方网站（选填）"
              value={officialUrl}
              onChange={(event) => setOfficialUrl(event.target.value)}
            />
            <Input
              style={{ width: 260 }}
              placeholder="系统要求（选填）"
              value={systemRequirements}
              onChange={(event) => setSystemRequirements(event.target.value)}
            />
          </Space>
          <Select
            mode="multiple"
            placeholder="选择标签"
            value={tagIds}
            onChange={setTagIds}
            options={tags.map((item) => ({ value: item.id, label: item.name }))}
          />
        </Space>
      </Card>

      <Card className={styles.section} title="软件介绍">
        <TiptapEditor
          value={descriptionHtml}
          onChange={setDescriptionHtml}
          placeholder="介绍功能、使用场景与注意事项..."
        />
      </Card>

      <Card className={styles.section} title="查看条件">
        <Space direction="vertical" size="middle">
          <Select
            style={{ width: 260 }}
            value={accessMode}
            onChange={setAccessMode}
            options={[
              { value: "REGISTERED", label: "注册用户登录后可见" },
              { value: "COMMUNITY_LEVEL", label: "社区等级达到要求可见" },
              { value: "ADMIN_PAID", label: "联系管理员付款查看" },
            ]}
          />
          {accessMode === "COMMUNITY_LEVEL" ? (
            <Space>
              <span>要求等级</span>
              <InputNumber
                min={1}
                max={100}
                value={minCommunityLevel}
                onChange={(value) => setMinCommunityLevel(value ?? 1)}
              />
            </Space>
          ) : null}
          {accessMode === "ADMIN_PAID" ? (
            <Select
              style={{ width: 260 }}
              placeholder="选择联系入口"
              value={accessContactId}
              onChange={setAccessContactId}
              options={contacts.map((item) => ({
                value: item.id,
                label: item.name,
              }))}
            />
          ) : null}
        </Space>
      </Card>

      <Card
        className={styles.section}
        title="版本与下载来源"
        extra={
          <Button
            icon={<PlusOutlined />}
            onClick={() => setReleases((prev) => [...prev, emptyRelease()])}
          >
            新增版本
          </Button>
        }
      >
        {releases.map((release, releaseIndex) => (
          <div key={releaseIndex} className={styles.releaseCard}>
            <Space wrap style={{ marginBottom: 8 }}>
              <Input
                style={{ width: 200 }}
                placeholder="版本号，如 1.0.0"
                value={release.versionName}
                onChange={(event) => {
                  const next = [...releases];
                  next[releaseIndex].versionName = event.target.value;
                  setReleases(next);
                }}
              />
              <Space>
                <span>推荐版本</span>
                <Switch
                  checked={release.recommended}
                  onChange={(checked) => {
                    const next = [...releases];
                    next[releaseIndex].recommended = checked;
                    setReleases(next);
                  }}
                />
              </Space>
            </Space>
            <Input.TextArea
              placeholder="更新日志（选填）"
              rows={2}
              value={release.changelog}
              onChange={(event) => {
                const next = [...releases];
                next[releaseIndex].changelog = event.target.value;
                setReleases(next);
              }}
            />
            <div style={{ marginTop: 8 }}>
              {release.sources.map((source, sourceIndex) => (
                <div key={sourceIndex} className={styles.sourceRow}>
                  <Select
                    value={source.sourceType}
                    onChange={(value) => {
                      const next = [...releases];
                      next[releaseIndex].sources[sourceIndex].sourceType =
                        value;
                      setReleases(next);
                    }}
                    options={SOURCE_TYPE_OPTIONS}
                  />
                  <Input
                    placeholder={
                      source.sourceType === "NETDISK"
                        ? "网盘 HTTPS 分享链接"
                        : source.sourceType === "MAGNET"
                          ? "magnet:?xt=urn:btih:..."
                          : "https://.../xxx.torrent"
                    }
                    value={source.link}
                    onChange={(event) => {
                      const next = [...releases];
                      next[releaseIndex].sources[sourceIndex].link =
                        event.target.value;
                      setReleases(next);
                    }}
                  />
                  <Input
                    placeholder="提取码（选填）"
                    value={source.extractionCode}
                    onChange={(event) => {
                      const next = [...releases];
                      next[releaseIndex].sources[sourceIndex].extractionCode =
                        event.target.value;
                      setReleases(next);
                    }}
                  />
                </div>
              ))}
              <Button
                type="dashed"
                block
                icon={<PlusOutlined />}
                onClick={() => {
                  const next = [...releases];
                  next[releaseIndex].sources.push({
                    sourceType: "NETDISK",
                    link: "",
                    extractionCode: "",
                  });
                  setReleases(next);
                }}
              >
                新增来源
              </Button>
            </div>
          </div>
        ))}
      </Card>

      <div className={styles.actions}>
        <Button loading={saving} onClick={() => void handleSaveDraft()}>
          保存草稿
        </Button>
        <Button
          type="primary"
          loading={submitting}
          onClick={confirmSubmit}
        >
          提交审核
        </Button>
      </div>
    </div>
  );
}
