import { useCallback, useEffect, useState, useRef} from "react";
import {
  Alert,
  App,
  Button,
  Card,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Upload,
} from "antd";
import { DeleteOutlined, InboxOutlined, PlusOutlined } from "@ant-design/icons";
import { history, useParams } from "umi";
import TiptapEditor from "@/components/TiptapEditor";
import {
  createWorkingRevision,
  getSoftwareEditor,
  listSoftwareAccessContacts,
  listSoftwareCategories,
  listAllSoftwareTags,
  saveSoftwareDraft,
  submitSoftwareForReview,
  type SoftwareAccessMode,
  type SoftwareCategory,
  type SoftwareContact,
  type SoftwareEditorData,
  type SoftwareEditorSource,
  type SoftwareLicenseType,
  type SoftwareSourceType,
  type SoftwareTag,
} from "@/api/software";
import { deleteFileAsset, uploadFileAsset } from "@/api/file";
import {
  clearPageDraft,
  loadPageDraft,
  savePageDraft,
} from "@/utils/pageDraft";
import { useAuthStore } from "@/stores/auth";
import styles from "./SoftwareEdit.module.css";

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

const REVIEW_LABEL: Record<string, string> = {
  DRAFT: "草稿",
  PENDING: "待审核",
  APPROVED: "已通过",
  REJECTED: "已退回",
};

function sourceLink(source: SoftwareEditorSource): string {
  return source.url ?? "";
}

interface SoftwareEditorReleaseDraft {
  /** 稳定 key：删除中间行时避免索引 key 导致输入框状态错位 */
  uid: string;
  versionName: string;
  changelog: string;
  recommended: boolean;
  sources: {
    uid: string;
    label: string;
    platform: string[];
    arch: string[];
    sourceType: SoftwareSourceType;
    link: string;
    extractionCode: string;
    archivePassword: string;
  }[];
}

let draftUidSeq = 0;
/** 稳定 key：删除中间行时避免索引 key 导致输入框状态错位 */
const nextDraftUid = () => `d${++draftUidSeq}`;

const emptySource = () => ({
  uid: nextDraftUid(),
  label: "网盘",
  platform: ["WINDOWS"],
  arch: ["X64"],
  sourceType: "NETDISK" as SoftwareSourceType,
  link: "",
  extractionCode: "",
  archivePassword: "",
});

export default function SoftwareEdit() {
  const { id = "" } = useParams<{ id: string }>();
  const { message } = App.useApp();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [loading, setLoading] = useState(true);
  const [editor, setEditor] = useState<SoftwareEditorData | null>(null);
  const [conflict, setConflict] = useState(false);

  const [categories, setCategories] = useState<SoftwareCategory[]>([]);
  const [tags, setTags] = useState<SoftwareTag[]>([]);
  const [contacts, setContacts] = useState<SoftwareContact[]>([]);

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
  const [releases, setReleases] = useState<SoftwareEditorReleaseDraft[]>([]);

  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // ---------------- 未保存内容跨页保留（localStorage；保存/提交成功即清） ----------------
  const EDIT_DRAFT_KEY = "software-edit-draft";
  const editRestoredRef = useRef(false);
  useEffect(() => {
    const draft = loadPageDraft<{
      name?: string;
      summary?: string;
      descriptionHtml?: string;
      developerName?: string;
      officialUrl?: string;
      systemRequirements?: string;
    }>(EDIT_DRAFT_KEY);
    editRestoredRef.current = true; // 无档也置位：否则保存 effect 永不启用
    if (!draft) return;
    if (draft.name) setName(draft.name);
    if (draft.summary) setSummary(draft.summary);
    if (draft.descriptionHtml) setDescriptionHtml(draft.descriptionHtml);
    if (draft.developerName) setDeveloperName(draft.developerName);
    if (draft.officialUrl) setOfficialUrl(draft.officialUrl);
    if (draft.systemRequirements) setSystemRequirements(draft.systemRequirements);
    editRestoredRef.current = true;
  }, []);

  useEffect(() => {
    if (!editRestoredRef.current) return;
    savePageDraft(EDIT_DRAFT_KEY, {
      name,
      summary,
      descriptionHtml,
      developerName,
      officialUrl,
      systemRequirements,
    });
  }, [name, summary, descriptionHtml, developerName, officialUrl, systemRequirements]);

  const loadEditor = useCallback(async () => {
    setLoading(true);
    setConflict(false);
    try {
      const res = await getSoftwareEditor(id);
      if (!res.data.success) return;
      const data = res.data.data;
      setEditor(data);
      setName(data.name);
      setSummary(data.summary);
      setCategoryId(data.categoryId ?? undefined);
      setDescriptionHtml(data.descriptionHtml ?? "");
      setLicenseType(data.licenseType ?? "FREE");
      setAttributionType(data.attributionType ?? "ORIGINAL");
      setIconAssetId(data.iconAssetId ?? undefined);
      // 已发布软件直接编辑会撞「仅草稿/退回快照可编辑」——自动创建工作副本
      if (data.reviewStatus === "APPROVED" && !data.workingRevisionId) {
        try {
          const created = await createWorkingRevision(id);
          if (created.data.success) {
            message.info("已发布内容不可直接修改，已自动创建新的编辑版本");
            await loadEditor();
            return;
          }
        } catch {
          // 失败则按原数据继续展示（保存时报错由拦截器提示）
        }
      }
      setDeveloperName(data.developerName ?? "");
      setOfficialUrl(data.officialUrl ?? "");
      setSystemRequirements(data.systemRequirements ?? "");
      setTagIds(data.tagIds ?? []);
      setAccessMode(data.accessMode);
      setMinCommunityLevel(data.minCommunityLevel ?? 1);
      setAccessContactId(data.accessContactId ?? undefined);
      setReleases(
        data.releases.map((release) => ({
          uid: nextDraftUid(),
          versionName: release.versionName,
          changelog: release.changelog ?? "",
          recommended: release.recommended,
          sources:
            release.sources.length > 0
              ? (() => {
                  // 同一来源（展示名+类型+链接+提取码+解压密码一致）在保存时
                  // 被展开为多条平台×架构组合——加载时合并回一行多选
                  const groups = new Map<
                    string,
                    {
                      uid: string;
                      label: string;
                      platforms: string[];
                      archs: string[];
                      sourceType: SoftwareSourceType;
                      link: string;
                      extractionCode: string;
                      archivePassword: string;
                    }
                  >();
                  for (const source of release.sources) {
                    const key = [
                      source.label ?? "",
                      source.sourceType,
                      sourceLink(source),
                      source.extractionCode ?? "",
                      source.archivePassword ?? "",
                    ].join("\u0001");
                    const existing = groups.get(key);
                    if (existing) {
                      if (source.platform && !existing.platforms.includes(source.platform))
                        existing.platforms.push(source.platform);
                      if (source.arch && !existing.archs.includes(source.arch))
                        existing.archs.push(source.arch);
                    } else {
                      groups.set(key, {
                        uid: nextDraftUid(),
                        label: source.label ?? "",
                        platforms: [source.platform ?? "WINDOWS"],
                        archs: [source.arch ?? "X64"],
                        sourceType: source.sourceType,
                        link: sourceLink(source),
                        extractionCode: source.extractionCode ?? "",
                        archivePassword: source.archivePassword ?? "",
                      });
                    }
                  }
                  const rows: SoftwareEditorReleaseDraft["sources"] = [
                    ...groups.values(),
                  ].map((group) => ({
                    uid: group.uid,
                    label: group.label,
                    platform: group.platforms,
                    arch: group.archs,
                    sourceType: group.sourceType,
                    link: group.link,
                    extractionCode: group.extractionCode,
                    archivePassword: group.archivePassword,
                  }));
                  return rows;
                })()
              : [
                  {
                    uid: nextDraftUid(),
                    label: "网盘",
                    platform: ["WINDOWS"],
                    arch: ["X64"],
                    sourceType: "NETDISK",
                    link: "",
                    extractionCode: "",
                    archivePassword: "",
                  },
                ],
        })),
      );
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (!id || !isAuthenticated) return;
    void loadEditor();
  }, [id, isAuthenticated, loadEditor]);

  useEffect(() => {
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
  }, []);

  const buildPayload = useCallback(() => {
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
          sources: release.sources
            .filter((source) => source.link.trim().length > 0)
            .flatMap((source) => {
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
                  ...(source.sourceType === "NETDISK"
                    ? {
                        url: source.link.trim(),
                        extractionCode: source.extractionCode.trim() || undefined,
                        archivePassword:
                          source.archivePassword.trim() || undefined,
                      }
                    : {}),
                  ...(source.sourceType === "MAGNET"
                    ? { magnetUri: source.link.trim() }
                    : {}),
                  ...(source.sourceType === "TORRENT_URL"
                    ? { torrentUrl: source.link.trim() }
                    : {}),
                };
              });
            })
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

  const updateRelease = useCallback(
    (index: number, patch: Partial<SoftwareEditorReleaseDraft>) => {
      setReleases((prev) =>
        prev.map((item, i) => (i === index ? { ...item, ...patch } : item)),
      );
    },
    [],
  );

  const updateSource = useCallback(
    (
      releaseIndex: number,
      sourceIndex: number,
      patch: Partial<SoftwareEditorReleaseDraft["sources"][number]>,
    ) => {
      setReleases((prev) =>
        prev.map((release, i) =>
          i === releaseIndex
            ? {
                ...release,
                sources: release.sources.map((source, j) =>
                  j === sourceIndex ? { ...source, ...patch } : source,
                ),
              }
            : release,
        ),
      );
    },
    [],
  );

  const addSource = useCallback((releaseIndex: number) => {
    setReleases((prev) =>
      prev.map((release, i) =>
        i === releaseIndex
          ? { ...release, sources: [...release.sources, emptySource()] }
          : release,
      ),
    );
  }, []);

  const removeSource = useCallback((releaseIndex: number, sourceIndex: number) => {
    setReleases((prev) =>
      prev.map((release, i) =>
        i === releaseIndex
          ? {
              ...release,
              sources: release.sources.filter((_, j) => j !== sourceIndex),
            }
          : release,
      ),
    );
  }, []);

  const removeRelease = useCallback((releaseIndex: number) => {
    setReleases((prev) => prev.filter((_, i) => i !== releaseIndex));
  }, []);

  const handleAddRelease = useCallback(() => {
    setReleases((prev) => [
      ...prev,
      {
        uid: nextDraftUid(),
        versionName: "",
        changelog: "",
        recommended: false,
        sources: [emptySource()],
      },
    ]);
  }, []);

      /** 保存草稿：原样保存，版本号机制已移除 */
    const handleSave = useCallback(async () => {
      if (!editor) return;
      setSaving(true);
      try {
        const res = await saveSoftwareDraft(id, editor.revisionId, buildPayload());
        if (!res.data.success) return;
        await loadEditor();
        message.success("已保存");
      } catch {
        message.error("保存失败，请稍后重试");
      } finally {
        setSaving(false);
      }
    }, [buildPayload, editor, id, loadEditor, message]);

  const handleSubmit = useCallback(async () => {
    if (!editor) return;
    setSubmitting(true);
    try {
      await saveSoftwareDraft(id, editor.revisionId, buildPayload());
      const res = await submitSoftwareForReview(id, editor.revisionId);
      if (!res.data.success) return;
      message.success("已提交审核");
      history.push("/software/mine");
    } catch {
      message.error("提交失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  }, [buildPayload, editor, id, message]);

  /** 已上线软件修改需先创建工作副本（克隆已发布快照） */
  const handleCreateWorkingRevision = useCallback(async () => {
    const res = await createWorkingRevision(id);
    if (!res.data.success) return;
    await loadEditor();
    message.success("已创建工作副本");
  }, [id, loadEditor, message]);

  if (loading) {
    return (
      <div className={styles.page}>
        <Spin />
      </div>
    );
  }

  if (!editor) {
    return (
      <div className={styles.page}>
        <Alert
          type="error"
          message="加载失败"
          description="无法读取该软件的工作副本"
        />
      </div>
    );
  }

  const frozen = editor.reviewStatus === "PENDING";

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>编辑软件</h1>
        <Space>
          <Tag>{REVIEW_LABEL[editor.reviewStatus] ?? editor.reviewStatus}</Tag>
          {editor?.reviewStatus === "APPROVED" &&
          editor?.reviewStatus !== undefined ? (
            <Button onClick={() => history.push(`/software/${id}`)}>
              查看前台
            </Button>
          ) : (
            <Button
              onClick={() => history.push(`/software/${id}/preview`)}
            >
              预览
            </Button>
          )}
        </Space>
      </div>

      {frozen ? (
        <Alert
          className={styles.section}
          type="warning"
          showIcon
          message="已提交审核，内容已冻结"
          description="审核期间不能修改；如需调整请先撤回提交。"
        />
      ) : null}

      {conflict ? (
        <Alert
          className={styles.section}
          type="error"
          showIcon
          message="版本冲突"
          description="你填写的内容已保留在页面上。请点击「重新加载」获取最新版本后再合并修改。"
          action={
            <Button size="small" onClick={() => void loadEditor()}>
              重新加载
            </Button>
          }
        />
      ) : null}

      <Card className={styles.section} title="基础信息">
        <Space direction="vertical" style={{ width: "100%" }} size="middle">
          <div style={{ marginBottom: 12 }}>
            <Upload
              accept="image/*"
              showUploadList={false}
              disabled={frozen}
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
                    cursor: frozen ? "default" : "pointer",
                  }}
                />
              ) : (
                <div
                  style={{
                    width: 72,
                    height: 72,
                    borderRadius: 12,
                    border: "1px dashed var(--color-border, #444)",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    cursor: frozen ? "default" : "pointer",
                    color: "var(--color-text-secondary, #999)",
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
            disabled={frozen}
            placeholder="软件名称"
            value={name}
            maxLength={80}
            onChange={(event) => setName(event.target.value)}
          />
          <Input.TextArea
            disabled={frozen}
            placeholder="一句话说明（选填，10~160 字）"
            value={summary}
            maxLength={160}
            showCount
            rows={2}
            onChange={(event) => setSummary(event.target.value)}
          />
          <Space wrap>
            <Select
              disabled={frozen}
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
              disabled={frozen}
              style={{ width: 160 }}
              value={licenseType}
              onChange={setLicenseType}
              options={LICENSE_OPTIONS}
            />
            <Select
              value={attributionType}
              onChange={setAttributionType}
              options={ATTRIBUTION_OPTIONS}
              placeholder="来源声明"
            />
          </Space>
          <Space wrap>
            <Input
              disabled={frozen}
              style={{ width: 200 }}
              placeholder="开发者名称（选填）"
              value={developerName}
              onChange={(event) => setDeveloperName(event.target.value)}
            />
            <Input
              disabled={frozen}
              style={{ width: 260 }}
              placeholder="官方网站（选填）"
              value={officialUrl}
              onChange={(event) => setOfficialUrl(event.target.value)}
            />
            <Input
              disabled={frozen}
              style={{ width: 260 }}
              placeholder="系统要求（选填）"
              value={systemRequirements}
              onChange={(event) => setSystemRequirements(event.target.value)}
            />
          </Space>
          <Select
            disabled={frozen}
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
            disabled={frozen}
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
                disabled={frozen}
                min={1}
                max={100}
                value={minCommunityLevel}
                onChange={(value) => setMinCommunityLevel(value ?? 1)}
              />
            </Space>
          ) : null}
          {accessMode === "ADMIN_PAID" ? (
            <Select
              disabled={frozen}
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

      <Card className={styles.section} title="版本与下载来源">
        {releases.length === 0 ? (
          <Button icon={<PlusOutlined />} onClick={handleAddRelease}>
            新增版本
          </Button>
        ) : (
          releases.map((release, releaseIndex) => (
            <div key={release.uid} className={styles.releaseCard}>
              <Space wrap style={{ marginBottom: 8 }}>
                <Input
                  disabled={frozen}
                  style={{ width: 200 }}
                  placeholder="版本号"
                  value={release.versionName}
                  onChange={(event) =>
                    updateRelease(releaseIndex, {
                      versionName: event.target.value,
                    })
                  }
                />
                <Space>
                  <span>推荐版本</span>
                  <Switch
                    disabled={frozen}
                    checked={release.recommended}
                    onChange={(checked) =>
                      updateRelease(releaseIndex, { recommended: checked })
                    }
                  />
                </Space>
                {releases.length > 1 ? (
                  <Button
                    disabled={frozen}
                    danger
                    type="text"
                    icon={<DeleteOutlined />}
                    onClick={() => removeRelease(releaseIndex)}
                  >
                    删除版本
                  </Button>
                ) : null}
              </Space>
              <Input.TextArea
                disabled={frozen}
                placeholder="更新日志（选填）"
                rows={2}
                value={release.changelog}
                onChange={(event) =>
                  updateRelease(releaseIndex, { changelog: event.target.value })
                }
              />
              <div style={{ marginTop: 8 }}>
                {release.sources.map((source, sourceIndex) => (
                  <div key={source.uid} className={styles.sourceRow}>
                    <Input
                      disabled={frozen}
                      style={{ width: 160 }}
                      placeholder="展示名，如 百度网盘"
                      value={source.label}
                      onChange={(event) =>
                        updateSource(releaseIndex, sourceIndex, {
                          label: event.target.value,
                        })
                      }
                    />
                    <Select
                      disabled={frozen}
                      mode="multiple"
                      style={{ minWidth: 160 }}
                      maxTagCount={2}
                      placeholder="平台"
                      value={source.platform}
                      onChange={(value) =>
                        updateSource(releaseIndex, sourceIndex, {
                          platform: value,
                        })
                      }
                      options={PLATFORM_OPTIONS}
                    />
                    <Select
                      disabled={frozen}
                      mode="multiple"
                      style={{ minWidth: 130 }}
                      maxTagCount={2}
                      placeholder="架构"
                      value={source.arch}
                      onChange={(value) =>
                        updateSource(releaseIndex, sourceIndex, {
                          arch: value,
                        })
                      }
                      options={ARCH_OPTIONS}
                    />
                    <Select
                      disabled={frozen}
                      value={source.sourceType}
                      onChange={(value) =>
                        updateSource(releaseIndex, sourceIndex, {
                          sourceType: value,
                        })
                      }
                      options={SOURCE_TYPE_OPTIONS}
                    />
                    <Input
                      disabled={frozen}
                      placeholder={
                        source.sourceType === "NETDISK"
                          ? "网盘 HTTPS 分享链接"
                          : source.sourceType === "MAGNET"
                            ? "magnet:?xt=urn:btih:..."
                            : "https://.../xxx.torrent"
                      }
                      value={source.link}
                      onChange={(event) =>
                        updateSource(releaseIndex, sourceIndex, {
                          link: event.target.value,
                        })
                      }
                    />
                    <Input
                      disabled={frozen}
                      placeholder="提取码（选填）"
                      value={source.extractionCode}
                      onChange={(event) =>
                        updateSource(releaseIndex, sourceIndex, {
                          extractionCode: event.target.value,
                        })
                      }
                    />
                    <Input
                      disabled={frozen}
                      placeholder="解压密码（选填）"
                      value={source.archivePassword}
                      onChange={(event) =>
                        updateSource(releaseIndex, sourceIndex, {
                          archivePassword: event.target.value,
                        })
                      }
                    />
                    <Button
                      disabled={frozen}
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => removeSource(releaseIndex, sourceIndex)}
                    />
                  </div>
                ))}
                <Button
                  disabled={frozen}
                  type="dashed"
                  block
                  icon={<PlusOutlined />}
                  onClick={() => addSource(releaseIndex)}
                >
                  新增来源
                </Button>
              </div>
            </div>
          ))
        )}
      </Card>

      <div className={styles.actions}>
        {editor.reviewStatus === "APPROVED" && !editor.workingRevisionId ? (
          <Button onClick={() => void handleCreateWorkingRevision()}>
            创建工作副本
          </Button>
        ) : null}
        <Button
          disabled={frozen}
          loading={saving}
          onClick={() => void handleSave()}
        >
          保存
        </Button>
        <Button
          type="primary"
          disabled={frozen}
          loading={submitting}
          onClick={() => void handleSubmit()}
        >
          提交审核
        </Button>
      </div>
    </div>
  );
}
