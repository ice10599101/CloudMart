import { Card, Tag } from "antd";
import { history } from "umi";
import type { SoftwareCard } from "@/api/software";
import {
  handleSoftwareIconError,
  softwareIconUrl,
} from "@/utils/softwareIcon";
import styles from "./SoftwareCardGrid.module.css";

const LICENSE_LABEL: Record<string, string> = {
  FREE: "免费",
  OPEN_SOURCE: "开源",
  TRIAL: "试用",
  COMMERCIAL: "商业",
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

/** 软件卡片网格：广场与筛选结果共用，避免两处维护同一套卡片结构 */
export default function SoftwareCardGrid({ cards, rankOffset }: Props) {
  return (
    <div className={styles.grid}>
      {cards.map((item, index) => (
        <Card
          key={item.id}
          hoverable
          className={styles.card}
          onClick={() => history.push(`/software/${item.id}`)}
        >
          <div className={styles.cardHead}>
            {rankOffset !== undefined ? (
              <span className={styles.rankBadge} aria-label={`第 ${rankOffset + index + 1} 名`}>
                {rankOffset + index + 1}
              </span>
            ) : null}
            <img
              className={styles.icon}
              src={softwareIconUrl(item.iconAssetId)}
              alt={item.iconAssetId ? `${item.name} 图标` : ""}
              onError={handleSoftwareIconError}
            />
            <div style={{ minWidth: 0, flex: 1 }}>
              <p className={styles.cardTitle} title={item.name}>
                {item.name}
              </p>
              <div className={styles.meta}>
                {item.categoryName ? <Tag>{item.categoryName}</Tag> : null}
                {item.licenseType ? (
                  <Tag>
                    {LICENSE_LABEL[item.licenseType] ?? item.licenseType}
                  </Tag>
                ) : null}
              </div>
            </div>
          </div>
          <p className={styles.summary}>{item.summary}</p>
          <div className={styles.footer}>
            <span>更新于 {formatUpdatedAt(item.lastReleaseAt)}</span>
            <span>收藏 {item.favoriteCount}</span>
            <span>评论 {item.commentCount}</span>
          </div>
        </Card>
      ))}
    </div>
  );
}
