package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.SoftwareListQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 软件广场查询 Mapper（方案 7.2）：公开可见谓词固定下推到 SQL
 * （deleted_at IS NULL AND lifecycle='ACTIVE' AND 已审快照 JOIN），
 * 过滤不捞出后在应用层再筛。
 */
@Mapper
public interface SoftwareQueryMapper {

    /**
     * 卡片行：已审快照字段 + 运营计数；排序由服务端拼接白名单片段（禁止客户端注入）。
     */
    @Select("""
            <script>
            SELECT s.id, r.name, r.summary, r.icon_asset_id AS iconAssetId,
                   r.license_type AS licenseType, r.category_id AS categoryId,
                   c.name AS categoryName, s.last_release_at AS lastReleaseAt,
                   s.favorite_count AS favoriteCount, s.comment_count AS commentCount,
                   s.rating_count AS ratingCount, s.rating_sum AS ratingSum
            FROM software s
            JOIN software_revision r
              ON r.id = s.published_revision_id AND r.review_status = 'APPROVED'
            LEFT JOIN software_category c ON c.id = r.category_id
            WHERE s.deleted_at IS NULL AND s.lifecycle = 'ACTIVE'
            <if test="q.keyword != null and q.keyword != ''">
              AND (r.name LIKE CONCAT('%', #{q.keyword}, '%')
                   OR r.summary LIKE CONCAT('%', #{q.keyword}, '%')
                   OR r.description_text LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.categoryId != null">AND r.category_id = #{q.categoryId}</if>
            <if test="q.license != null and q.license != ''">AND r.license_type = #{q.license}</if>
            <if test="q.tagId != null">
              AND EXISTS (SELECT 1 FROM software_revision_tag rt
                          WHERE rt.revision_id = r.id AND rt.tag_id = #{q.tagId})
            </if>
            <if test="q.platform != null and q.platform != ''">
              AND EXISTS (SELECT 1 FROM software_release rel
                          JOIN software_source src ON src.release_id = rel.id
                          WHERE rel.revision_id = r.id AND src.platform = #{q.platform})
            </if>
            <choose>
              <when test="q.sort == 'latest'">
                ORDER BY s.last_release_at DESC, s.id DESC
              </when>
              <when test="q.sort == 'popular'">
                ORDER BY s.link_access_count DESC, s.favorite_count DESC, s.id DESC
              </when>
              <otherwise>
                ORDER BY s.featured_rank DESC, s.last_release_at DESC, s.id DESC
              </otherwise>
            </choose>
            </script>
            """)
    Page<CardRow> pageCards(Page<CardRow> page, @Param("q") SoftwareListQuery query);

    /** 卡片平台去重集（分页结果后批量加载，避免列表 SQL 膨胀） */
    @Select("""
            <script>
            SELECT rel.revision_id AS revisionId, src.platform AS platform
            FROM software_release rel
            JOIN software_source src ON src.release_id = rel.id
            WHERE rel.revision_id IN
            <foreach collection="revisionIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            GROUP BY rel.revision_id, src.platform
            </script>
            """)
    List<PlatformRow> platformsOfRevisions(@Param("revisionIds") List<Long> revisionIds);

    /** 卡片行（Mapper 内部行结构，不外暴实体） */
    record CardRow(Long id, String name, String summary, Long iconAssetId, String licenseType,
                   Long categoryId, String categoryName, java.time.LocalDateTime lastReleaseAt,
                   Integer favoriteCount, Integer commentCount, Integer ratingCount,
                   Integer ratingSum) {
    }

    /** 平台行 */
    record PlatformRow(Long revisionId, String platform) {
    }

    /** IPage 兼容入口（防未使用告警的显式契约） */
    default IPage<CardRow> page(IPage<CardRow> page, SoftwareListQuery query) {
        return pageCards((Page<CardRow>) page, query);
    }
}
