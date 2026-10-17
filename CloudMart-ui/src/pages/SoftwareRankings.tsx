import { useCallback, useEffect, useState } from "react";
import { App, Empty, Pagination, Segmented, Spin } from "antd";
import {
  listSoftwareRankings,
  type SoftwareCard,
  type SoftwareRankingType,
} from "@/api/software";
import SoftwareCardGrid from "@/components/software/SoftwareCardGrid";
import styles from "./SoftwareRankings.module.css";

const TYPE_LABEL: Record<SoftwareRankingType, string> = {
  access: "获取热榜（7 天）",
  favorite: "收藏榜（7 天）",
  rating: "评分榜",
};

/** 榜单依据必须明示：不得把"获取链接"说成"下载量"（方案 §4.5/§3.2） */
const TYPE_NOTE: Record<SoftwareRankingType, string> = {
  access: "按最近 7 天内去重获取链接人数排序；获取链接数不是外部下载量。",
  favorite: "按最近 7 天新增收藏数排序（趋势值，不等于收藏总量）。",
  rating: "至少 5 人评分后按贝叶斯平滑分排序（展示评分人数）；分数为 1~5 星均分。",
};

const PAGE_SIZE = 20;

export default function SoftwareRankings() {
  const { message } = App.useApp();
  const [type, setType] = useState<SoftwareRankingType>("access");
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [items, setItems] = useState<SoftwareCard[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    (nextType: SoftwareRankingType, nextPage: number) => {
      setLoading(true);
      listSoftwareRankings({ type: nextType, page: nextPage, size: PAGE_SIZE })
        .then((res) => {
          setItems(res.data.data?.records ?? []);
          setTotal(res.data.data?.total ?? 0);
        })
        .catch(() => message.error("榜单加载失败"))
        .finally(() => setLoading(false));
    },
    [message],
  );

  useEffect(() => {
    load(type, page);
  }, [load, type, page]);

  return (
    <div className={styles.page}>
      <div className={styles.head}>
        <h1 className={styles.title}>排行榜</h1>
        <Segmented
          value={type}
          onChange={(value) => {
            setType(value as SoftwareRankingType);
            setPage(1);
          }}
          options={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))}
        />
      </div>
      <p className={styles.note}>{TYPE_NOTE[type]}</p>

      <Spin spinning={loading}>
        {items.length === 0 && !loading ? (
          <Empty description="榜单暂无数据（可见软件不足或统计窗口内无数据）" />
        ) : (
          <SoftwareCardGrid cards={items} rankOffset={(page - 1) * PAGE_SIZE} />
        )}
      </Spin>
      {total > PAGE_SIZE ? (
        <div className={styles.pager}>
          <Pagination
            current={page}
            pageSize={PAGE_SIZE}
            total={total}
            showSizeChanger={false}
            onChange={setPage}
          />
        </div>
      ) : null}
    </div>
  );
}
