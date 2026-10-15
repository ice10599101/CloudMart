import request from "@/utils/request";
import type { ApiResponse } from "@/types/api";

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
export type SoftwareSourceType = "NETDISK" | "MAGNET" | "TORRENT_URL";
/** 软件生命周期 */
export type SoftwareLifecycle = "ACTIVE" | "BANNED" | "UNLISTED";
/** 快照审核状态 */
export type SoftwareReviewStatus =
  "DRAFT" | "PENDING" | "APPROVED" | "REJECTED";

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

export interface SoftwareListQuery {
  keyword?: string;
  categoryId?: string;
  platform?: string;
  licenseType?: string;
  tagId?: string;
  sort?: "latest" | "hot" | "rating";
  page?: number;
  size?: number;
}

// ========== 详情 ==========

export interface SoftwareScreenshot {
  assetId: string;
  displayOrder: number;
  altText: string | null;
}

export interface SoftwareDetail {
  id: string;
  name: string;
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
}

export interface SoftwareCommentCreatePayload {
  content: string;
  parentId?: string;
  replyToCommentId?: string;
}

// ========== 发布者（草稿/编辑/发布） ==========

export interface SoftwarePublisherStatus {
  canPublish: boolean;
  reason: string | null;
}

export interface SoftwareMineItem {
  softwareId: string;
  name: string;
  summary: string;
  status: string;
  revisionId: string | null;
  lockVersion: number;
  favoriteCount: number;
  updatedAt: string;
  unavailable: boolean;
}

export interface SoftwareCreated {
  softwareId: string;
  workingRevisionId: string;
  lockVersion: number;
}

export interface SoftwareSourceInput {
  sourceType: SoftwareSourceType;
  provider?: string;
  platform?: string;
  arch?: string;
  label?: string;
  sizeLabel?: string;
  url?: string;
  magnetUri?: string;
  torrentUrl?: string;
  extractionCode?: string;
  archivePassword?: string;
  claimedSha256?: string;
  note?: string;
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
export interface SoftwareSnapshotInput {
  name?: string;
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
  languages?: string[];
  accessMode?: SoftwareAccessMode;
  minCommunityLevel?: number;
  accessContactId?: string;
  accessNote?: string;
  tagIds?: string[];
  screenshots?: { assetId: string; altText?: string; displayOrder?: number }[];
  releases?: SoftwareReleaseInput[];
}

export interface SoftwareEditorData {
  softwareId: string;
  revisionId: string;
  revisionNo: number;
  reviewStatus: SoftwareReviewStatus;
  submitSeq: number | null;
  lockVersion: number;
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
  languages: string[];
  accessMode: SoftwareAccessMode;
  minCommunityLevel: number | null;
  accessContactId: string | null;
  accessNote: string | null;
  tagIds: string[];
  screenshots: SoftwareScreenshot[];
  releases: SoftwareRelease[];
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

export function listSoftwareTags() {
  return request.get<ApiResponse<SoftwareTag[]>>(`${BASE}/tags`);
}

export function getSoftwareDetail(id: string) {
  return request.get<ApiResponse<SoftwareDetail>>(`${BASE}/${id}`);
}

export function getSoftwareAccessPolicy(id: string) {
  return request.get<ApiResponse<SoftwareAccessPolicy>>(
    `${BASE}/${id}/access-policy`,
  );
}

export function listSoftwareReleases(id: string) {
  return request.get<ApiResponse<SoftwareRelease[]>>(`${BASE}/${id}/releases`);
}

// ========== 查看条件与获取链接 ==========

/**
 * 查看条件判定（需登录）。按软件整体判定而非按单个来源，且响应 no-store。
 * 得到 allowed=false 时页面只展示门槛与联系入口，不得渲染真实链接。
 */
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
  return request.post<ApiResponse<SoftwareResolveResult>>(
    `${BASE}/${id}/sources/${sourceId}/resolve`,
    null,
    {
      headers: idempotencyKey
        ? { "Idempotency-Key": idempotencyKey }
        : undefined,
    },
  );
}

// ========== 评论 ==========

export function createSoftwareComment(
  id: string,
  payload: SoftwareCommentCreatePayload,
  idempotencyKey?: string,
) {
  return request.post<ApiResponse<SoftwareComment>>(
    `${BASE}/${id}/comments`,
    payload,
    {
      headers: idempotencyKey
        ? { "Idempotency-Key": idempotencyKey }
        : undefined,
    },
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

// ========== 发布者侧 ==========

export function getPublisherStatus() {
  return request.get<ApiResponse<SoftwarePublisherStatus>>(
    `${BASE}/publisher-status`,
  );
}

export function createSoftware(
  payload?: SoftwareSnapshotInput,
  idempotencyKey?: string,
) {
  return request.post<ApiResponse<SoftwareCreated>>(BASE, payload, {
    headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
  });
}

export function getSoftwareEditor(id: string) {
  return request.get<ApiResponse<SoftwareEditorData>>(`${BASE}/${id}/editor`);
}

/** 保存工作副本（期望乐观锁版本用于拦截两标签页并发） */
export function saveSoftwareDraft(
  id: string,
  revisionId: string,
  payload: SoftwareSnapshotInput & { expectedVersion?: number },
) {
  return request.put<ApiResponse<{ lockVersion: number }>>(
    `${BASE}/${id}/revisions/${revisionId}`,
    payload,
  );
}

export function submitSoftwareForReview(
  id: string,
  revisionId: string,
  payload: { expectedVersion?: number },
  idempotencyKey?: string,
) {
  return request.post<ApiResponse<{ submitSeq: number }>>(
    `${BASE}/${id}/revisions/${revisionId}/submit`,
    payload,
    {
      headers: idempotencyKey
        ? { "Idempotency-Key": idempotencyKey }
        : undefined,
    },
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
  return request.post<ApiResponse<SoftwareEditorData>>(
    `${BASE}/${id}/working-revision`,
    null,
    {
      headers: idempotencyKey
        ? { "Idempotency-Key": idempotencyKey }
        : undefined,
    },
  );
}
