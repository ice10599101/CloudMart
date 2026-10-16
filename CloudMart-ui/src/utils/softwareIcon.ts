/**
 * 软件图标统一解析与兜底。
 *
 * 图标走网关静态路由 /files/{assetId}（与 WishBGM 的 /files/music/... 同源约定）。
 * 兜底用内联 SVG data URI 而非 public 下的静态图：占位图永不 404、不发网络请求；
 * onError 只回退一次（dataset 标记），避免「回退图也加载失败 → 再触发 onError」
 * 的死循环导致卡片图标持续闪烁（实测踩坑）。
 */

const ICON_FALLBACK_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96"><rect width="96" height="96" rx="20" fill="#e8eaf0"/><path d="M48 22 76 36v24L48 74 20 60V36z" fill="none" stroke="#a8b0c0" stroke-width="5" stroke-linejoin="round"/><path d="M20 36l28 14 28-14M48 50v24" fill="none" stroke="#a8b0c0" stroke-width="5" stroke-linejoin="round"/></svg>`;

export const SOFTWARE_ICON_FALLBACK = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(
  ICON_FALLBACK_SVG,
)}`;

export function softwareIconUrl(assetId: string | null): string {
  return assetId ? `/files/${assetId}` : SOFTWARE_ICON_FALLBACK;
}

/** <img onError> 处理：仅在尚未回退过时切占位图，且回退后摘除监听防循环 */
export function handleSoftwareIconError(
  event: React.SyntheticEvent<HTMLImageElement>,
): void {
  const img = event.currentTarget;
  if (img.dataset.iconFallback) {
    img.onerror = null;
    return;
  }
  img.dataset.iconFallback = "1";
  img.src = SOFTWARE_ICON_FALLBACK;
}
