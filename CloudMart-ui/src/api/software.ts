import type { SoftwarePublishMode } from '@/utils/softwarePublishMode'
import request from "@/utils/request";
import type { ApiResponse } from "@/types/api";
import { softwareWriteConfig } from "@/utils/softwareIntent";

/**
 * 小答软件 Web 端 API（方案 §7.3/§7.4）。
 *
 * 契约要点：
 * - 业务 ID 由后端全局序列化为字符串，前端统一用 string，避免大数精度丢失。
 * - 公开列表/详情/来源摘要刻意不含真实链接与提取码；只有 resolve 在满足查看
 *   条件后才返回，且响应 no-store。
 * - 管理端点（/api/admin/software）不在此文件，见 src/api/admin/。
 */

// ========== 枚举与公共类型 ==========

/** 查看条件：注册用户 / 社区等级 / 联系管理员付款查看 */
export type SoftwareAccessMode =
  "REGISTERED" | "COMMUNITY_LEVEL" | "ADMIN_PAID";
/** 授权标签 */
export type SoftwareLicenseType =
  "FREE" | "OPEN_SOURCE" | "TRIAL" | "COMMERCIAL";
/** 来源类型 */
export type SoftwareSourceType =
  | "NETDISK"
  | "MAGNET"
  | "TORRENT_URL"
  | "HTTPS_DOWNLOAD"
  | "OFFICIAL_PAGE"
  | "WEB_APP";
/** 软件生命周期 */
export type SoftwareLifecycle = "ACTIVE" | "BANNED" | "UNLISTED";
/** 快照审核状态 */
export type SoftwareReviewStatus =
  "DRAFT" | "PENDING" | "WITHDRAWN" | "APPROVED" | "REJECTED";

export interface PageResult<T> {
  current: number;
  pages: number;
  size: number;
  total: number;
  records: T[];
}

// ========== 卡片 / 列表 ==========

export interface SoftwareCard {
  id: string;
  name: string;
  summary: string;
  iconAssetId: string | null;
  categoryId: string | null;
  categoryName: string | null;
  licenseType: SoftwareLicenseType | null;
  platforms: string[];
  lastReleaseAt: string | null;
  favoriteCount: number;
  commentCount: number;
  ratingCount: number;
  ratingAvg: number | null;
}

export interface SoftwareCategory {
  id: string;
  name: string;
  slug: string;
  icon: string | null;
  sortOrder: number;
  enabled: boolean;
}

export interface SoftwareTag {
  id: string;
  name: string;
}

/**
 * 广场列表查询参数（方案 7.2）。
 * 参数名与后端 SoftwareListQuery 逐字段一致：授权筛选是 `license`（不是 licenseType），
 * sort 白名单 recommend/latest/popular，非法值服务端回落 recommend。
 */
export interface SoftwareListQuery {
  keyword?: string;
  categoryId?: string;
  platform?: string;
  /** 对应 license_type 列，取值 SoftwareLicenseType */
  license?: string;
  tagId?: string;
  /** recommend=推荐位优先、latest=最近更新、popular=获取链接与收藏热度 */
  sort?: "recommend" | "featured" | "latest" | "popular" | "rating";
  page?: number;
  size?: number;
}

// ========== 详情 ==========

export interface SoftwareScreenshot {
  assetId: string;
  displayOrder: number;
  altText: string | null;
}

/** UX03：同类选择条目（公开可见目标；relationType=SIMILAR_USE/ALTERNATIVE_PLATFORM） */
export interface SoftwareRelatedItem {
  softwareId: string;
  name: string;
  summary: string | null;
  iconAssetId: string | null;
  relationType: string;
  reason: string | null;
}

/** UX03：发布者资料卡（"本站分享者"公开资料；不返回草稿/审核信息） */
export interface SoftwarePublisherSummary {
  userId: string;
  nickname: string | null;
  avatar: string | null;
  publishedCount: number;
  lastPublishedAt: string | null;
}

export interface SoftwareDetail {
  id: string;  name: string;
  summary: string;
  descriptionHtml: string;
  iconAssetId: string | null;
  categoryId: string | null;
  categoryName: string | null;
  tags: SoftwareTag[];
  screenshots: SoftwareScreenshot[];
  developerName: string | null;
  officialUrl: string | null;
  licenseType: SoftwareLicenseType | null;
  attributionType: string | null;
  attributionNote: string | null;
  systemRequirements: string | null;
  /** SF02：安装指南（随快照审核） */
  installationGuide: string | null;
  /** SF02：已知问题（随快照审核） */
  knownIssues: string | null;
  /** UX03：短亮点（≤3 条，随快照审核） */
  highlights: string[];
  /** UX03：同类选择（人工关系；不可见目标已实时过滤） */
  relatedItems: SoftwareRelatedItem[];
  /** UX03：发布者资料卡（"本站分享者"公开资料） */
  publisherSummary: SoftwarePublisherSummary | null;
  languages: string[];
  publishedAt: string | null;
  lastReleaseAt: string | null;
  commentCount: number;
  favoriteCount: number;
  ratingCount: number;
  ratingAvg: number | null;
  ownerNickname: string | null;
  ownerAvatar: string | null;
  isFavorited: boolean;
  /** 分享者会员 ID（前端判断作者操作如置顶留言） */
  ownerId: string;
  /** 我的评分 1~5（未评分/游客 null；P1） */
  myScore: number | null;
  /** 是否已订阅更新（游客 null；P1） */
  isSubscribed: boolean | null;
}

export interface SoftwareSourceSummary {
  sourceId: string;
  sourceKey: string;
  sourceType: SoftwareSourceType;
  provider: string | null;
  platform: string | null;
  arch: string | null;
  label: string | null;
  sizeLabel: string | null;
  claimedSha256: string | null;
  note: string | null;
  availability: string;
}

export interface SoftwareRelease {
  releaseId: string;
  releaseKey: string;
  versionName: string;
  channel: string | null;
  releaseDate: string | null;
  changelog: string | null;
  recommended: boolean;
  sources: SoftwareSourceSummary[];
}

// ========== 查看条件与获取链接 ==========

export interface SoftwareContact {
  id: string;
  name: string;
  contactType: string;
  contactValue: string;
}

export interface SoftwareAccessPolicy {
  softwareId: string;
  accessMode: SoftwareAccessMode;
  requiredLevel: number | null;
  accessNote: string | null;
  contacts: SoftwareContact[];
}

export interface SoftwareAccessState {
  softwareId: string;
  accessMode: SoftwareAccessMode;
  allowed: boolean;
  reasonCode: string | null;
  currentLevel: number | null;
  requiredLevel: number | null;
  grantExpiresAt: string | null;
  contacts: SoftwareContact[];
}

/** 仅允许出现在满足查看条件后的面板里，不得预渲染进公开页面 */
export interface SoftwareResolveResult {
  sourceId: string;
  sourceKey: string;
  sourceType: SoftwareSourceType;
  provider: string | null;
  platform: string | null;
  arch: string | null;
  label: string | null;
  url: string;
  extractionCode: string | null;
  archivePassword: string | null;
  sizeLabel: string | null;
  availability: string;
  availabilityTip: string | null;
}

// ========== 评论 ==========

export interface SoftwareComment {
  id: string;
  parentId: string | null;
  userId: string;
  nickname: string;
  avatar: string | null;
  content: string;
  status: "VISIBLE" | "PENDING" | "HIDDEN" | "DELETED";
  likeCount: number;
  likedByMe: boolean;
  createdAt: string;
  replies: SoftwareComment[];
  replyHasMore: boolean;
  /** 作者置顶（P1；每软件最多一个可见根评论） */
  pinned: boolean;
}

export interface SoftwareCommentCreatePayload {
  content: string;
  parentId?: string;
  replyToCommentId?: string;
}

// ========== 发布者（草稿/编辑/发布） ==========

export interface SoftwarePublisherStatus {
  publishMode?: SoftwarePublishMode
  canPublish: boolean;
  reason: string | null;
}

export interface SoftwareMineItem {
  softwareId: string;
  name: string;
  summary: string;
  status: string;
  revisionId: string | null;
  favoriteCount: number;
  updatedAt: string;
  unavailable: boolean;
}

export interface SoftwareCreated {
  softwareId: string;
  workingRevisionId: string;
}

/** SW01：提交回执只返回不可变提交记录与审核状态 */
export interface SoftwareSubmitted {
  softwareId: string;
  revisionId: string;
  reviewStatus: SoftwareReviewStatus;
}

export interface SoftwareSourceInput {
  sourceType: SoftwareSourceType;
  /** 稳定来源键：编辑既有来源时必须原样回传；新来源留空由服务端生成 */
  sourceKey?: string;
  provider?: string;
  platform?: string;
  arch?: string;
  label?: string;
  sizeLabel?: string;
  /** 三种来源统一使用 url（NETDISK 网盘地址 / MAGNET 磁力链接 / TORRENT_URL 种子地址） */
  url?: string;
  extractionCode?: string;
  archivePassword?: string;
  claimedSha256?: string;
  note?: string;
  displayOrder?: number;
}

export interface SoftwareReleaseInput {
  releaseKey?: string;
  versionName: string;
  channel?: string;
  releaseDate?: string;
  changelog?: string;
  recommended?: boolean;
  displayOrder?: number;
  sources: SoftwareSourceInput[];
}

/** 编辑器快照入参（方案 §4.1：草稿宽松、提交严格） */
export interface SoftwareSnapshotInput {  name?: string;
  summary?: string;
  categoryId?: string;
  iconAssetId?: string;
  descriptionHtml?: string;
  licenseType?: SoftwareLicenseType;
  developerName?: string;
  officialUrl?: string;
  attributionType?: string;
  attributionNote?: string;
  systemRequirements?: string;
  installationGuide?: string;
  knownIssues?: string;
  /** UX03：短亮点（≤3 条、每条 1~60 字） */
  highlights?: string[];
  languages?: string[];
  accessMode?: SoftwareAccessMode;
  minCommunityLevel?: number;
  accessContactId?: string;
  accessNote?: string;
  tagIds?: string[];
  screenshots?: { assetId: string; altText?: string; displayOrder?: number }[];
  releases?: SoftwareReleaseInput[];
}

/**
 * SW02 局部保存入参：只有出现在对象里的字段会被修改。
 * 显式 null 表示清空可空标量；集合必须用数组（清空用 []，不能用 null）。
 */
export interface SoftwarePatchInput {
  name?: string | null;
  summary?: string | null;
  categoryId?: string | null;
  iconAssetId?: string | null;
  descriptionHtml?: string | null;
  licenseType?: SoftwareLicenseType | null;
  developerName?: string | null;
  officialUrl?: string | null;
  attributionType?: string | null;
  attributionNote?: string | null;
  systemRequirements?: string | null;
  installationGuide?: string | null;
  knownIssues?: string | null;
  /** UX03：短亮点（数组整体替换；空数组清空） */
  highlights?: string[];
  languages?: string[];
  accessMode?: SoftwareAccessMode;
  minCommunityLevel?: number | null;
  accessContactId?: string | null;
  accessNote?: string | null;
  tagIds?: string[];
  screenshots?: { assetId: string; altText?: string | null; displayOrder?: number }[];
  releases?: SoftwareReleaseInput[];
}

/** 编辑器快照里的来源：含真实 url/提取码/解压密码，仅发布者本人可见 */
export interface SoftwareEditorSource {
  sourceId: string | null;
  sourceKey: string | null;
  sourceType: SoftwareSourceType;
  provider: string | null;
  platform: string | null;
  arch: string | null;
  label: string | null;
  url: string | null;
  extractionCode: string | null;
  archivePassword: string | null;
  sizeLabel: string | null;
  claimedSha256: string | null;
  note: string | null;
  displayOrder: number | null;
}

export interface SoftwareEditorRelease {
  releaseId: string | null;
  releaseKey: string;
  versionName: string;
  channel: string | null;
  releaseDate: string | null;
  changelog: string | null;
  recommended: boolean;
  displayOrder: number | null;
  sources: SoftwareEditorSource[];
}

export interface SoftwareEditorData {
  softwareId: string;
  revisionId: string;
  reviewStatus: SoftwareReviewStatus;
  name: string;
  summary: string;
  categoryId: string | null;
  iconAssetId: string | null;
  descriptionHtml: string | null;
  licenseType: SoftwareLicenseType | null;
  developerName: string | null;
  officialUrl: string | null;
  attributionType: string | null;
  attributionNote: string | null;
  systemRequirements: string | null;
  /** SF02：安装指南（随快照审核） */
  installationGuide: string | null;
  /** SF02：已知问题（随快照审核） */
  knownIssues: string | null;
  /** UX03：短亮点（≤3 条） */
  highlights: string[];
  languages: string[];
  accessMode: SoftwareAccessMode;
  minCommunityLevel: number | null;
  accessContactId: string | null;
  accessNote: string | null;
  tagIds: string[];
  screenshots: SoftwareScreenshot[];
  releases: SoftwareEditorRelease[];
  submittedAt: string | null;
  reviewedAt: string | null;
  reviewerId: string | null;
  reviewReason: string | null;
  lifecycle: SoftwareLifecycle;
  softwareLockVersion: number;
  publishedRevisionId: string | null;
  workingRevisionId: string | null;
}

const BASE = "/software";

// ========== 浏览（游客可见） ==========

export function listSoftware(params?: SoftwareListQuery) {
  return request.get<ApiResponse<PageResult<SoftwareCard>>>(BASE, { params });
}

export function listSoftwareCategories() {
  return request.get<ApiResponse<SoftwareCategory[]>>(`${BASE}/categories`);
}

/** 标签检索：服务端返回分页（size 上限 50），keyword 匹配标签名 */
export function listSoftwareTags(params?: {
  keyword?: string;
  page?: number;
  size?: number;
}) {
  return request.get<ApiResponse<PageResult<SoftwareTag>>>(`${BASE}/tags`, {
    params,
  });
}

/**
 * 全量启用标签（编辑器标签选择器用）。后端单页上限 50，按 pages 逐页合并，
 * 避免标签数超过一页时静默丢失后段选项。
 */
export async function listAllSoftwareTags(): Promise<SoftwareTag[]> {
  const pageSize = 50;
  const first = await listSoftwareTags({ page: 1, size: pageSize });
  const records = first.data.data?.records ?? [];
  const pages = first.data.data?.pages ?? 1;
  if (!first.data.success || pages <= 1) return records;
  const rest = await Promise.all(
    Array.from({ length: pages - 1 }, (_, index) =>
      listSoftwareTags({ page: index + 2, size: pageSize }),
    ),
  );
  return rest.reduce<SoftwareTag[]>(
    (all, res) =>
      all.concat(res.data.success ? (res.data.data?.records ?? []) : []),
    records,
  );
}

export function getSoftwareDetail(id: string) {
  return request.get<ApiResponse<SoftwareDetail>>(`${BASE}/${id}`);
}

export function getSoftwareAccessPolicy(id: string) {
  return request.get<ApiResponse<SoftwareAccessPolicy>>(
    `${BASE}/${id}/access-policy`,
  );
}

/** 后端返回 Page 分页结构；详情页一次取全量版本（默认 50 足够） */
export function listSoftwareReleases(id: string, page = 1, size = 50) {
  return request.get<ApiResponse<PageResult<SoftwareRelease>>>(
    `${BASE}/${id}/releases?page=${page}&size=${size}`,
  );
}

// ========== 查看条件与获取链接 ==========

/**
 * 查看条件判定（需登录）。按软件整体判定而非按单个来源，且响应 no-store。
 * 得到 allowed=false 时页面只展示门槛与联系入口，不得渲染真实链接。
 */
/** 发布者可选的管理员联系入口（付款查看模式绑定）；仅启用项 */
export function listSoftwareAccessContacts() {
  return request.get<ApiResponse<SoftwareContact[]>>(`${BASE}/access-contacts`);
}

export function getSoftwareAccessState(id: string) {
  return request.get<ApiResponse<SoftwareAccessState>>(
    `${BASE}/${id}/access-state`,
  );
}

/**
 * 获取下载来源。响应 no-store 且只在满足条件时返回真实链接/提取码，
 * 结果必须保留在本次会话状态里，不得写入 localStorage 或 URL。
 */
export function resolveSoftwareSource(
  id: string,
  sourceId: string,
  idempotencyKey?: string,
) {
  // 可选键（手册 7.3：获取去重主要依赖服务端日统计唯一键，不依赖浏览器 UUID）
  return request.post<ApiResponse<SoftwareResolveResult>>(
    `${BASE}/${id}/sources/${sourceId}/resolve`,
    null,
    idempotencyKey ? { headers: { "Idempotency-Key": idempotencyKey } } : undefined,
  );
}

// ========== 评论 ==========

export function createSoftwareComment(
  id: string,
  payload: SoftwareCommentCreatePayload,
  idempotencyKey?: string,
) {
  const url = `${BASE}/${id}/comments`;
  return request.post<ApiResponse<SoftwareComment>>(
    url,
    payload,
    softwareWriteConfig("POST", url, payload, idempotencyKey),
  );
}

export function listSoftwareComments(
  id: string,
  params?: { page?: number; size?: number },
) {
  return request.get<ApiResponse<PageResult<SoftwareComment>>>(
    `${BASE}/${id}/comments`,
    { params },
  );
}

export function listSoftwareReplies(
  id: string,
  rootId: string,
  params?: { page?: number; size?: number },
) {
  return request.get<ApiResponse<PageResult<SoftwareComment>>>(
    `${BASE}/${id}/comments/${rootId}/replies`,
    { params },
  );
}

export function deleteSoftwareComment(id: string, commentId: string) {
  return request.delete<ApiResponse<boolean>>(
    `${BASE}/${id}/comments/${commentId}`,
  );
}

export function likeSoftwareComment(id: string, commentId: string) {
  return request.put<ApiResponse<boolean>>(
    `${BASE}/${id}/comments/${commentId}/like`,
  );
}

export function unlikeSoftwareComment(id: string, commentId: string) {
  return request.delete<ApiResponse<boolean>>(
    `${BASE}/${id}/comments/${commentId}/like`,
  );
}

// ========== 收藏 ==========

export function favoriteSoftware(id: string) {
  return request.put<ApiResponse<boolean>>(`${BASE}/${id}/favorite`);
}

export function unfavoriteSoftware(id: string) {
  return request.delete<ApiResponse<boolean>>(`${BASE}/${id}/favorite`);
}

// ========== 举报 ==========

/** 举报目标类型（软件库 software_report；与社区举报接口相互独立） */
export type SoftwareReportTargetType = "SOFTWARE" | "SOFTWARE_COMMENT";

export interface SoftwareReportCreatePayload {
  targetType: SoftwareReportTargetType;
  /** 评论举报必填（该软件下的可见评论）；软件举报不需要 */
  targetId?: string;
  reason: string;
  description?: string;
}

/** 举报软件或评论（幂等：重复提交收敛为同一举报；处置由管理员显式选择） */
export function reportSoftware(
  id: string,
  payload: SoftwareReportCreatePayload,
  idempotencyKey?: string,
) {
  const url = `${BASE}/${id}/reports`;
  return request.post<ApiResponse<{ reportId: string; status: string }>>(
    url,
    payload,
    softwareWriteConfig("POST", url, payload, idempotencyKey),
  );
}

// ========== 排行榜 / 专题（P1） ==========

export type SoftwareRankingType = "access" | "favorite" | "rating";

/** 排行榜（P1）：access=7 天获取去重人数；favorite=7 天新增收藏；rating=贝叶斯（≥5 人） */
export function listSoftwareRankings(params?: {
  type?: SoftwareRankingType;
  page?: number;
  size?: number;
}) {
  return request.get<ApiResponse<PageResult<SoftwareCard>>>(
    `${BASE}/rankings`,
    { params },
  );
}

export interface SoftwareTopicSummary {
  topicId: string;
  slug: string;
  title: string;
  summary: string | null;
  coverAssetId: string | null;
  sortOrder: number;
}

export interface SoftwareTopicDetail {
  topicId: string;
  slug: string;
  title: string;
  summary: string | null;
  description: string | null;
  coverAssetId: string | null;
  /** UX02：工具箱三要素 */
  audience?: string | null;
  problem?: string | null;
  prerequisites?: string | null;
  items: SoftwareCard[];
  /** UX02：分组结构（稳定 groupKey 关联；受限软件仅公开信息） */
  groups?: Array<{
    groupKey: string;
    items: Array<{
      softwareId: string;
      groupKey: string;
      roleNote: string | null;
      optional: boolean;
      sortOrder: number;
    }>;
  }>;
}

/** 专题列表（P1）：只返回 PUBLISHED */
export function listSoftwareTopics() {
  return request.get<ApiResponse<SoftwareTopicSummary[]>>(`${BASE}/topics`);
}

/** 专题详情（P1）：清单已按实时可见性过滤 */
export function getSoftwareTopic(slug: string) {
  return request.get<ApiResponse<SoftwareTopicDetail>>(
    `${BASE}/topics/${slug}`,
  );
}

// ========== 评分 / 订阅 / 提醒偏好（P1） ==========

export interface SoftwareRatingResult {
  softwareId: string;
  myScore: number | null;
  ratingCount: number;
  ratingAvg: number | null;
}

/** 评分（P1）：新建/改分幂等；分享者不能给自己评分 */
export function rateSoftware(
  id: string,
  score: number,
  expectedVersion?: number,
) {
  return request.put<ApiResponse<SoftwareRatingResult>>(
    `${BASE}/${id}/rating`,
    { score, expectedVersion },
  );
}

/** 撤销评分（P1）：幂等 */
export function revokeSoftwareRating(id: string, expectedVersion?: number) {
  return request.delete<ApiResponse<SoftwareRatingResult>>(
    `${BASE}/${id}/rating`,
    { params: { expectedVersion } },
  );
}

/** 订阅更新（P1）：幂等；与收藏相互独立 */
export function subscribeSoftware(id: string) {
  return request.put<ApiResponse<void>>(`${BASE}/${id}/subscription`);
}

/** 取消订阅（P1）：幂等；软件下架也成功 */
export function unsubscribeSoftware(id: string) {
  return request.delete<ApiResponse<void>>(`${BASE}/${id}/subscription`);
}

export interface SoftwarePreference {
  updateNotifications: boolean;
  lockVersion: number;
}

/** 读取更新提醒偏好（P1）：无记录视为开启 */
export function getSoftwarePreferences() {
  return request.get<ApiResponse<SoftwarePreference>>(`${BASE}/preferences`);
}

/** 修改更新提醒偏好（P1） */
export function updateSoftwarePreferences(payload: {
  updateNotifications: boolean;
  expectedVersion?: number;
}) {
  return request.put<ApiResponse<SoftwarePreference>>(
    `${BASE}/preferences`,
    payload,
  );
}

/** 置顶留言（P1）：仅分享者；每软件最多一个可见根留言 */
export function pinSoftwareComment(
  id: string,
  commentId: string,
  expectedVersion?: number,
) {
  return request.put<ApiResponse<void>>(
    `${BASE}/${id}/comments/${commentId}/pin`,
    null,
    { params: { expectedVersion } },
  );
}

/** 取消置顶（P1）：仅分享者；幂等 */
export function unpinSoftwareComment(
  id: string,
  commentId: string,
  expectedVersion?: number,
) {
  return request.delete<ApiResponse<void>>(
    `${BASE}/${id}/comments/${commentId}/pin`,
    { params: { expectedVersion } },
  );
}

// ========== 发布者侧 ==========

export function getPublisherStatus() {
  return request.get<ApiResponse<SoftwarePublisherStatus>>(
    `${BASE}/publisher-status`,
  );
}

/** SF03：发布者申请（申请/我的申请/撤回） */
export interface SoftwarePublisherApplication {
  applicationId: string;
  userId: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
  intro: string;
  intendedCategories: string;
  reason: string | null;
  submittedAt: string;
  reviewedAt: string | null;
}

export function submitPublisherApplication(
  payload: { intro: string; intendedCategories: string },
  idempotencyKey?: string,
) {
  const url = `${BASE}/publisher-applications`;
  return request.post<ApiResponse<SoftwarePublisherApplication>>(
    url,
    payload,
    softwareWriteConfig("POST", url, payload, idempotencyKey),
  );
}

export function listMyPublisherApplications(page = 1, size = 10) {
  return request.get<ApiResponse<PageResult<SoftwarePublisherApplication>>>(
    `${BASE}/publisher-applications/mine`,
    { params: { page, size } },
  );
}

export function cancelPublisherApplication(
  applicationId: string,
  idempotencyKey?: string,
) {
  const url = `${BASE}/publisher-applications/${applicationId}/cancel`;
  return request.post<ApiResponse<boolean>>(
    url,
    {},
    softwareWriteConfig("POST", url, {}, idempotencyKey),
  );
}

export function createSoftware(
  payload?: SoftwareSnapshotInput,
  idempotencyKey?: string,
) {
  return request.post<ApiResponse<SoftwareCreated>>(
    BASE,
    payload,
    softwareWriteConfig("POST", BASE, payload, idempotencyKey),
  );
}

export function getSoftwareEditor(id: string) {
  return request.get<ApiResponse<SoftwareEditorData>>(`${BASE}/${id}/editor`);
}

/** 保存工作副本：全量 PUT（过渡方案；新代码优先级用 PATCH 局部保存） */
export function saveSoftwareDraft(
  id: string,
  revisionId: string,
  payload: SoftwareSnapshotInput,
) {
  return request.put<ApiResponse<SoftwareEditorData>>(
    `${BASE}/${id}/revisions/${revisionId}`,
    payload,
  );
}

/**
 * SW02：局部保存——只提交用户实际修改的字段。
 * 缺省=不修改；显式 null=清空可空标量；数组=整体替换该集合（清空用 []）。
 * 返回服务端规范化后的完整编辑器数据，页面必须以它为准。
 */
export function patchSoftwareDraft(
  id: string,
  revisionId: string,
  patch: SoftwarePatchInput,
) {
  return request.patch<ApiResponse<SoftwareEditorData>>(
    `${BASE}/${id}/revisions/${revisionId}`,
    patch,
  );
}

/** 删除我的软件（软删 lifecycle=DELETED，幂等） */
export function deleteMySoftware(id: string) {
  return request.delete<ApiResponse<boolean>>(`${BASE}/${id}`);
}

/** 提交审核：冻结当前不可变快照，不接收批次/版本号 */
export function submitSoftwareForReview(
  id: string,
  revisionId: string,
  idempotencyKey?: string,
) {
  const url = `${BASE}/${id}/revisions/${revisionId}/submit`;
  return request.post<ApiResponse<SoftwareSubmitted>>(
    url,
    {},
    softwareWriteConfig("POST", url, {}, idempotencyKey),
  );
}

export function listMySoftware(params?: {
  tab?: string;
  page?: number;
  size?: number;
}) {
  return request.get<ApiResponse<PageResult<SoftwareMineItem>>>(
    `${BASE}/mine`,
    { params },
  );
}

export function createWorkingRevision(id: string, idempotencyKey?: string) {
  const url = `${BASE}/${id}/working-revision`;
  return request.post<ApiResponse<SoftwareEditorData>>(
    url,
    null,
    softwareWriteConfig("POST", url, null, idempotencyKey),
  );
}

/** SF04：受限来源访问申请（申请/我的申请/撤回） */
export interface SoftwareAccessRequest {
  requestId: string;
  softwareId: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
  message: string;
  reason: string | null;
  grantId: string | null;
  createdAt: string;
  handledAt: string | null;
}

export function submitAccessRequest(
  softwareId: string,
  payload: { message: string },
  idempotencyKey?: string,
) {
  const url = `${BASE}/access-requests/${softwareId}`;
  return request.post<ApiResponse<SoftwareAccessRequest>>(
    url,
    payload,
    softwareWriteConfig("POST", url, payload, idempotencyKey),
  );
}

export function listMyAccessRequests(page = 1, size = 10) {
  return request.get<ApiResponse<PageResult<SoftwareAccessRequest>>>(
    `${BASE}/access-requests/mine`,
    { params: { page, size } },
  );
}

export function cancelAccessRequest(requestId: string, idempotencyKey?: string) {
  const url = `${BASE}/access-requests/${requestId}/cancel`;
  return request.post<ApiResponse<boolean>>(
    url,
    {},
    softwareWriteConfig("POST", url, {}, idempotencyKey),
  );
}

/** SF08：恢复历史内容（owner 本人；复制为新的 DRAFT 走审核） */
export function draftFromHistory(
  softwareId: string,
  sourceRevisionId: string,
  idempotencyKey?: string,
) {
  const url = `${BASE}/${softwareId}/draft-from-history`;
  return request.post<ApiResponse<SoftwareEditorData>>(
    url,
    { sourceRevisionId },
    softwareWriteConfig("POST", url, { sourceRevisionId }, idempotencyKey),
  );
}

// ==================== UX01：首页区块聚合（公共） ====================

export interface SoftwareHomeSectionItem {
  itemId: string
  targetType: 'SOFTWARE' | 'TOPIC' | 'GUIDE' | 'FILTER'
  headline?: string
  editorNote?: string
  coverAssetId?: string
  filterPreset?: string
  software?: SoftwareCard
  topic?: { id: string; slug: string; title: string; summary?: string }
}

export interface SoftwareHomeSection {
  id: string
  slotKey: string
  kind: 'SPOTLIGHT' | 'SCENARIOS' | 'SOFTWARE_GRID' | 'RANKING_LIST' | 'UPDATE_FEED' | 'GUIDE_LIST'
  title: string
  subtitle?: string
  /** OK=正常；ERROR=本块失败（显示本区重试）；EMPTY=显式空态 */
  state?: 'OK' | 'ERROR' | 'EMPTY'
  items: SoftwareHomeSectionItem[]
  hasMore: boolean
  moreTarget?: string | null
}

/** 首页固定区块聚合（platform=WEB/APP/TARO，缺省 WEB） */
export function getSoftwareHome(platform: 'WEB' | 'APP' | 'TARO' = 'WEB') {
  return request.get<ApiResponse<SoftwareHomeSection[]>>(`${BASE}/home`, {
    params: { platform },
  });
}

// ==================== UX05：我的订阅更新流 ====================

export interface SoftwareSubscriptionRow {
  softwareId: string
  platforms: string
  channels: string
  subscribedAt: string | null
  name: string | null
  iconAssetId: string | null
  visible: boolean
  unreadCount: number
}

export interface SoftwareUpdateItem {
  eventId: string
  softwareId: string
  read: boolean
  publishedAt: string | null
  /** false=纯文案发布（后台未推给订阅者，历史保留展示） */
  businessChange?: boolean
  softwareName?: string
  versionName?: string
  changelog?: string
  /** 软件当前下架/封禁时为 false：历史保留，入口标注不可用 */
  softwareVisible: boolean
}

export interface SoftwareUpdateFeed {
  items: SoftwareUpdateItem[]
  hasMore: boolean
  nextCursor: string | null
}

export function listMySubscriptions() {
  return request.get<ApiResponse<SoftwareSubscriptionRow[]>>(`${BASE}/subscriptions/mine`);
}

export function listMyUpdates(cursor: string | null, size = 20) {
  return request.get<ApiResponse<SoftwareUpdateFeed>>(`${BASE}/updates/mine`, {
    params: { cursor: cursor ?? 0, size },
  });
}

/** 批量标记已读（≤50；重复标记幂等） */
export function markUpdatesRead(eventIds: string[]) {
  return request.post<ApiResponse<number>>(`${BASE}/updates/read`, {
    eventIds: eventIds.map((v) => Number(v)),
  });
}

export function getUpdatesUnreadCount() {
  return request.get<ApiResponse<number>>(`${BASE}/updates/unread-count`);
}

// ==================== UX04：软件教程（正文真源在社区） ====================

export type SoftwareGuideType =
  | 'INSTALL_GUIDE'
  | 'WORKFLOW'
  | 'COMPARISON'
  | 'TROUBLESHOOTING';

export interface SoftwareGuideRow {
  id: string
  guideType: SoftwareGuideType
  title: string
  summary: string | null
  coverAssetId: string | null
  articleId: string
  platformScope: string[]
}

export interface SoftwareGuideDetail extends SoftwareGuideRow {
  appliesToVersion: string | null
  checkedAt: string | null
  checkNote: string | null
  relatedSoftwares: Array<{ softwareId: string; name: string | null }>
  relatedTopicId: string | null
}

export interface SoftwareGuidePage {
  records: SoftwareGuideRow[]
  total: number
  page: number
  size: number
}

export function listSoftwareGuides(
  guideType: SoftwareGuideType | null,
  page = 1,
  size = 12,
) {
  return request.get<ApiResponse<SoftwareGuidePage>>(`${BASE}/guides`, {
    params: { guideType: guideType ?? undefined, page, size },
  });
}

export function getSoftwareGuide(id: string) {
  return request.get<ApiResponse<SoftwareGuideDetail>>(`${BASE}/guides/${id}`);
}

// ==================== UX04-B：软件对比（2~3 款并排） ====================

export interface SoftwareCompareItem {
  softwareId: string
  name: string
  summary: string | null
  iconAssetId: string | null
  lastReleaseAt: string | null
  ratingCount: number
  ratingAvg: number | null
  accessMode: string | null
  fields: {
    platforms: string[]
    licenseType: string | null
    /** null=未提供（不推断支持/不支持） */
    openSource: boolean | null
    offlineCapable: boolean | null
    languages: string[]
    systemRequirements: string | null
  }
}

export function compareSoftware(ids: string[]) {
  return request.get<ApiResponse<SoftwareCompareItem[]>>(`${BASE}/compare`, {
    params: { ids: ids.join(',') },
  });
}

// ==================== UX06：找软件需求 ====================

export interface SoftwareRequestFeedItem {
  id: string
  title: string
  status: 'OPEN' | 'ANSWERED' | 'CLOSED'
  matchedSoftware?: { softwareId: string; visible: boolean; name?: string }
}

export interface SoftwareRequestMineItem {
  id: string
  title: string
  usageDesc: string
  status: 'OPEN' | 'ANSWERED' | 'CLOSED'
  auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  auditReason: string | null
  createdAt: string | null
  matchedSoftware?: { softwareId: string; visible: boolean; name?: string }
  matchedReason?: string | null
}

export function getSoftwareRequestFeed() {
  return request.get<ApiResponse<SoftwareRequestFeedItem[]>>(`${BASE}/requests/public`);
}

export function createSoftwareRequest(input: {
  title: string
  usageDesc: string
  mustHaveFeatures?: string[]
  platformScope?: string[]
  acceptableLicense?: string[]
  sourceKeyword?: string
}) {
  return request.post<ApiResponse<string>>(`${BASE}/requests`, input);
}

export function listMySoftwareRequests() {
  return request.get<ApiResponse<SoftwareRequestMineItem[]>>(`${BASE}/requests/mine`);
}

export function closeMySoftwareRequest(id: string) {
  return request.post<ApiResponse<boolean>>(`${BASE}/requests/${id}/close`);
}
