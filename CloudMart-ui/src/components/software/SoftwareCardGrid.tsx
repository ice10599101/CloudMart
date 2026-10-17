import type { KeyboardEvent } from "react";
import { StarFilled } from "@ant-design/icons";
import { history } from "umi";
import type { SoftwareCard } from "@/api/software";
import {
  handleSoftwareIconError,
  softwareIconUrl,
} from "@/utils/softwareIcon";
import styles from "./SoftwareCardGrid.module.css";

const LICENSE_META: Record<string, { label: string; color: string }> = {
  FREE: { label: "免费", color: "var(--color-accent-green)" },
  OPEN_SOURCE: { label: "开源", color: "var(--color-accent-purple)" },
  TRIAL: { label: "试用", color: "var(--color-accent-orange)" },
  COMMERCIAL: { label: "商业", color: "var(--color-accent-gold)" },
};

/** 卡片不呈现"下载量"：获取链接次数不等于实际下载成功（方案 §3.2） */
function formatUpdatedAt(value: string | null): string {
  if (!value) return "暂无版本";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "暂无版本";
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")}`;
}

interface Props {
  cards: SoftwareCard[];
  /** 榜单场景：显示名次（第 offset+1 名起），广场/筛选不传 */
  rankOffset?: number;
}

/** 软件卡片网格：广场、筛选、榜单、专题共用，避免多处维护同一套卡片结构 */
export default function SoftwareCardGrid({ cards, rankOffset }: Props) {
  const openDetail = (id: string) => history.push(`/software/${id}`);

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>, id: string) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      openDetail(id);
    }
  };

  return (
    <div className={styles.grid}>
      {cards.map((item, index) => {
        const license = item.licenseType ? LICENSE_META[item.licenseType] : null;
        const rank = rankOffset !== undefined ? rankOffset + index + 1 : undefined;
        return (
          <div
            key={item.id}
            role="button"
            tabIndex={0}
            aria-label={item.name}
            className={styles.card}
            onClick={() => openDetail(item.id)}
            onKeyDown={(event) => handleKeyDown(event, item.id)}
          >
            <div className={styles.cardTop}>
              {rank !== undefined ? (
                <span
                  className={`${styles.rankBadge}${rank <= 3 ? ` ${styles.rankTop3}` : ""}`}
                  aria-label={`第 ${rank} 名`}
                >
                  {rank}
                </span>
              ) : null}
              <div className={styles.iconWrap}>
                <img
                  className={styles.icon}
                  src={softwareIconUrl(item.iconAssetId)}
                  alt={item.iconAssetId ? `${item.name} 图标` : ""}
                  onError={handleSoftwareIconError}
                />
              </div>
              <div className={styles.info}>
                <p className={styles.name} title={item.name}>
                  {item.name}
                </p>
                <div className={styles.badges}>
                  {license ? (
                    <span
                      className={styles.licenseBadge}
                      style={{ color: license.color, borderColor: license.color }}
                    >
                      {license.label}
                    </span>
                  ) : null}
                  {item.categoryName ? (
                    <span className={styles.categoryChip} title={item.categoryName}>
                      {item.categoryName}
                    </span>
                  ) : null}
                </div>
              </div>
            </div>

            {item.summary ? <p className={styles.summary}>{item.summary}</p> : null}

            <div className={styles.stats}>
              {item.ratingAvg != null && item.ratingAvg > 0 ? (
                <span className={styles.rating}>
                  <StarFilled style={{ fontSize: 12 }} />
                  {item.ratingAvg.toFixed(1)}
                  {item.ratingCount > 0 ? (
                    <span className={styles.ratingCount}>({item.ratingCount})</span>
                  ) : null}
                </span>
              ) : null}
              <span>收藏 {item.favoriteCount}</span>
              <span>评论 {item.commentCount}</span>
              <span className={styles.updated}>更新于 {formatUpdatedAt(item.lastReleaseAt)}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
