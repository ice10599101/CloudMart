import { useCallback, useEffect, useRef, useState } from "react";
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
  Upload,
} from "antd";
import { DeleteOutlined, InboxOutlined, PlusOutlined } from "@ant-design/icons";
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
  getSoftwareEditor,
} from "@/api/software";
import { deleteFileAsset, uploadFileAsset } from "@/api/file";
import { useAuthStore } from "@/stores/auth";
import {
  clearPageDraft,
  loadPageDraft,
  savePageDraft,
} from "@/utils/pageDraft";
import styles from "./SoftwarePublish.module.css";

const LICENSE_OPTIONS: { value: SoftwareLicenseType; label: string }[] = [
  { value: "FREE", label: "免费" },
  { value: "OPEN_SOURCE", label: "开源" },
  { value: "TRIAL", label: "试用" },
  { value: "COMMERCIAL", label: "商业" },
];

const PLATFORM_OPTIONS = [
  { value: "WINDOWS", label: "Windows" },
  { value: "MACOS", label: "macOS" },
  { value: "LINUX", label: "Linux" },
  { value: "ANDROID", label: "Android" },
  { value: "IOS", label: "iOS" },
  { value: "CROSS_PLATFORM", label: "跨平台" },
];

const ARCH_OPTIONS = [
  { value: "X64", label: "x64" },
  { value: "X86", label: "x86" },
  { value: "ARM64", label: "ARM64" },
  { value: "UNIVERSAL", label: "通用" },
  { value: "OTHER", label: "其他" },
];

const ATTRIBUTION_OPTIONS = [
  { value: "ORIGINAL", label: "原创" },
  { value: "AUTHORIZED", label: "授权转载" },
  { value: "OFFICIAL_PUBLIC", label: "官方公开内容" },
];

const SOURCE_TYPE_OPTIONS: { value: SoftwareSourceType; label: string }[] = [
  { value: "NETDISK", label: "网盘链接" },
  { value: "MAGNET", label: "磁力链接" },
  { value: "TORRENT_URL", label: "种子外链" },
];

interface SourceDraft {
  /** 稳定 key：删除中间行时避免索引 key 导致输入框状态错位 */
  uid: string;
  /** 来源展示名（1~80 字，后端必填校验） */
  label: string;
  /** 多选：保存时按 平台×架构 展开为多条来源 */
  platform: string[];
  arch: string[];
  sourceType: SoftwareSourceType;
  /** 网盘 HTTPS 链接 / magnet: 链接 / .torrent 外链，按类型择一 */
  link: string;
  extractionCode: string;
}

interface ReleaseDraft {
  uid: string;
  versionName: string;
  changelog: string;
  recommended: boolean;
  sources: SourceDraft[];
}

let draftUidSeq = 0;
const nextDraftUid = () => `d${++draftUidSeq}`;

const emptyRelease = (): ReleaseDraft => ({
  uid: nextDraftUid(),
  versionName: "",
  changelog: "",
  recommended: true,
  sources: [
    {
      uid: nextDraftUid(),
      label: "网盘",
      platform: ["WINDOWS"],
      arch: ["X64"],
      sourceType: "NETDISK",
      link: "",
      extractionCode: "",
    },
  ],
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
  const [attributionType, setAttributionType] = useState<string>("ORIGINAL");
  const [iconAssetId, setIconAssetId] = useState<string | undefined>(undefined);
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

  // ---------------- 草稿跨页保留（localStorage；提交成功即清） ----------------
  const DRAFT_KEY = "software-publish-draft";
  const restoredRef = useRef(false);

  // 恢复：切走再回来时还原已填内容与服务端进度（含未提交完的 softwareId 链）
  useEffect(() => {
    const draft = loadPageDraft<{
      name: string;
      summary: string;
      categoryId?: string;
      descriptionHtml: string;
      licenseType: SoftwareLicenseType;
      developerName: string;
      officialUrl: string;
      systemRequirements: string;
      tagIds: string[];
      accessMode: SoftwareAccessMode;
      minCommunityLevel: number;
      accessContactId?: string;
      releases: ReleaseDraft[];
      softwareId: string | null;
      revisionId: string | null;
      lockVersion: number | null;
    }>(DRAFT_KEY);
    restoredRef.current = true; // 无档也置位：否则保存 effect 永不启用，内容从不落档
    if (!draft) return;
    if (draft.name) setName(draft.name);
    if (draft.summary) setSummary(draft.summary);
    if (draft.categoryId) setCategoryId(draft.categoryId);
    if (draft.descriptionHtml) setDescriptionHtml(draft.descriptionHtml);
    if (draft.licenseType) setLicenseType(draft.licenseType);
    if (draft.developerName) setDeveloperName(draft.developerName);
    if (draft.officialUrl) setOfficialUrl(draft.officialUrl);
    if (draft.systemRequirements) setSystemRequirements(draft.systemRequirements);
    if (draft.tagIds?.length) setTagIds(draft.tagIds);
    if (draft.accessMode) setAccessMode(draft.accessMode);
    if (draft.minCommunityLevel) setMinCommunityLevel(draft.minCommunityLevel);
    if (draft.accessContactId) setAccessContactId(draft.accessContactId);
    if (draft.releases?.length) setReleases(draft.releases);
    if (draft.softwareId) setSoftwareId(draft.softwareId);
    if (draft.revisionId) setRevisionId(draft.revisionId);
    if (draft.lockVersion != null) setLockVersion(draft.lockVersion);
    // 仅挂载恢复一次；置位后保存 effect 才开始写档（防挂载初始值覆盖存档）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 保存：任一草稿字段变化即落 localStorage（恢复完成前不写，防挂载空值覆盖存档）
  useEffect(() => {
    if (!restoredRef.current) return;
    savePageDraft(DRAFT_KEY, {
      name,
      summary,
      categoryId,
      descriptionHtml,
      licenseType,
      developerName,
      officialUrl,
      systemRequirements,
      tagIds,
      accessMode,
      minCommunityLevel,
      accessContactId,
      releases,
      softwareId,
      revisionId,
      lockVersion,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    name, summary, categoryId, descriptionHtml, licenseType, developerName,
    officialUrl, systemRequirements, tagIds, accessMode, minCommunityLevel,
    accessContactId, releases, softwareId, revisionId, lockVersion,
  ]);

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
        .flatMap((source) => {
          // 多平台×多架构：后端一个来源行只承载一个组合，保存时展开为多条
          const platforms = source.platform.length > 0 ? source.platform : ["WINDOWS"];
          const archs = source.arch.length > 0 ? source.arch : ["X64"];
          const combos = new Set(
            platforms.flatMap((p) => archs.map((a) => `${p}::${a}`)),
          );
          return [...combos].map((combo) => {
            const [platform, arch] = combo.split("::");
            return {
              label: source.label.trim(),
              platform,
              arch,
              sourceType: source.sourceType,
              // SW02：三种来源统一使用 url，不再发送 magnetUri/torrentUrl 别名
              url: source.link.trim(),
              ...(source.sourceType === "NETDISK"
                ? {
                    extractionCode: source.extractionCode.trim() || undefined,
                  }
                : {}),
            };
          });
        }),
    );
    return {
      name,
      summary,
      iconAssetId: iconAssetId || undefined,
      categoryId,
      descriptionHtml,
      licenseType,
      attributionType,
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
    attributionType,
    iconAssetId,
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

  /** 新建链：create（空草稿）→ saveDraft 落本地内容（软件被删自愈时也复用） */
  const doSaveFresh = useCallback(async () => {
    const created = await createSoftware(buildPayload());
    if (!created.data.success) return;
    const sid = created.data.data.softwareId;
    const rid = created.data.data.workingRevisionId;
    setSoftwareId(sid);
    setRevisionId(rid);
    const saved = await saveSoftwareDraft(sid, rid, buildPayload());
    if (!saved.data.success) return;
    setLockVersion(saved.data.data?.lockVersion ?? created.data.data.lockVersion);
    message.success("草稿已保存");
  }, [buildPayload, message]);

  const handleSaveDraft = useCallback(async () => {
    if (!name.trim()) {
      message.warning("请先填写软件名称");
      return;
    }
    setSaving(true);
    try {
      // 首次保存：create 只建空草稿（后端不读 body），须紧接着把快照 saveDraft 落库
      if (!softwareId || !revisionId) {
        await doSaveFresh();
        return;
      }
      const res = await saveSoftwareDraft(softwareId, revisionId, buildPayload());
      if (!res.data.success) return;
      setLockVersion(res.data.data?.lockVersion ?? lockVersion);
      message.success("草稿已保存");
    } catch (err) {
      const code = (err as { code?: string } | null)?.code;
      if (code === "SOFTWARE_NOT_FOUND") {
        // 本地指向的服务端草稿已被删除（如清理孤儿）：清进度重建，本地内容原样保留
        setSoftwareId(null);
        setRevisionId(null);
        setLockVersion(null);
        message.info("原草稿已不存在，已为你重新创建");
        await doSaveFresh();
        return;
      }
      // 其余错误拦截器已统一提示
    } finally {
      setSaving(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [buildPayload, doSaveFresh, lockVersion, message, name, revisionId, softwareId]);

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
        const saved = await saveSoftwareDraft(
          targetSoftwareId,
          targetRevisionId,
          buildPayload(),
        );
        if (!saved.data.success) return;
        version = saved.data.data?.lockVersion ?? version;
        setLockVersion(version);
      }
      const res = await submitSoftwareForReview(targetSoftwareId, targetRevisionId);
      if (!res.data.success) return;
      clearPageDraft(DRAFT_KEY);
      message.success("已提交审核，审核通过后公开可见");
      history.push("/software/mine");
    } catch (err) {
      const code = (err as { code?: string } | null)?.code;
      if (code === "SOFTWARE_NOT_FOUND") {
        // 提交链软件被删：重建草稿并落内容，提示再次点击提交
        setSoftwareId(null);
        setRevisionId(null);
        setLockVersion(null);
        await doSaveFresh();
        message.info("原草稿已不存在，已重新创建并保存，请再次点击提交审核");
        return;
      }
      // 其余错误拦截器已统一提示
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
          <div style={{ marginBottom: 12 }}>
            <Upload
              accept="image/*"
              showUploadList={false}
              customRequest={async (options) => {
                try {
                  const res = await uploadFileAsset(options.file as File, "PUBLIC");
                  if (res.data.success && res.data.data?.fileId) {
                    // 替换图标：旧资产联动删除并退还配额（删除接口含引用检查，被引用时保留）
                    if (iconAssetId && iconAssetId !== res.data.data.fileId) {
                      void import('@/api/file').then((mod) =>
                        mod.deleteFileAsset(iconAssetId).catch(() => undefined),
                      )
                    }
                    setIconAssetId(res.data.data.fileId);
                    options.onSuccess?.(res.data.data);
                    message.success("图标已上传");
                  } else {
                    options.onError?.(new Error("upload failed"));
                    message.error(
                      res.data.error?.message || "图标上传失败，请使用常见图片格式",
                    );
                  }
                } catch (error) {
                  options.onError?.(error as Error);
                }
              }}
            >
              {iconAssetId ? (
                <img
                  src={`/files/by-id/${iconAssetId}`}
                  alt="软件图标"
                  style={{
                    width: 72,
                    height: 72,
                    borderRadius: 12,
                    objectFit: "cover",
                    cursor: "pointer",
                  }}
                />
              ) : (
                <div
                  style={{
                    width: 72,
                    height: 72,
                    borderRadius: 12,
                    border: "1px dashed var(--color-border)",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    cursor: "pointer",
                    color: "var(--color-text-secondary)",
                    gap: 2,
                  }}
                >
                  <InboxOutlined />
                  <span style={{ fontSize: 12 }}>软件图标</span>
                </div>
              )}
            </Upload>
          </div>
          <Input
            placeholder="软件名称"
            value={name}
            maxLength={80}
            onChange={(event) => setName(event.target.value)}
          />
          <Input.TextArea
            placeholder="一句话说明（选填，10~160 字）"
            value={summary}
            maxLength={160}
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
            <Select
              style={{ width: 160 }}
              value={attributionType}
              onChange={setAttributionType}
              options={ATTRIBUTION_OPTIONS}
              placeholder="来源声明"
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
            style={{ width: "100%" }}
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
          <div key={release.uid} className={styles.releaseCard}>
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
              {releases.length > 1 ? (
                <Button
                  danger
                  type="text"
                  icon={<DeleteOutlined />}
                  onClick={() => {
                    setReleases((prev) =>
                      prev.filter((_, i) => i !== releaseIndex),
                    );
                  }}
                >
                  删除版本
                </Button>
              ) : null}
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
                <div key={source.uid} className={styles.sourceRow}>
                  <Input
                    style={{ width: 160 }}
                    placeholder="展示名，如 百度网盘"
                    value={source.label}
                    onChange={(event) => {
                      const next = [...releases];
                      next[releaseIndex].sources[sourceIndex].label =
                        event.target.value;
                      setReleases(next);
                    }}
                  />
                  <Select
                    mode="multiple"
                    style={{ minWidth: 160 }}
                    maxTagCount={2}
                    placeholder="平台"
                    value={source.platform}
                    onChange={(value) => {
                      const next = [...releases];
                      next[releaseIndex].sources[sourceIndex].platform = value;
                      setReleases(next);
                    }}
                    options={PLATFORM_OPTIONS}
                  />
                  <Select
                    mode="multiple"
                    style={{ minWidth: 130 }}
                    maxTagCount={2}
                    placeholder="架构"
                    value={source.arch}
                    onChange={(value) => {
                      const next = [...releases];
                      next[releaseIndex].sources[sourceIndex].arch = value;
                      setReleases(next);
                    }}
                    options={ARCH_OPTIONS}
                  />
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
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => {
                      const next = [...releases];
                      next[releaseIndex].sources = next[releaseIndex].sources.filter(
                        (_, i) => i !== sourceIndex,
                      );
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
                    uid: nextDraftUid(),
                    label: "网盘",
                    platform: ["WINDOWS"],
                    arch: ["X64"],
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
