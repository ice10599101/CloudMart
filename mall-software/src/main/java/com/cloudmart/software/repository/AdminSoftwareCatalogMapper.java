package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.dto.software.AdminItemQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 管理端软件目录查询 Mapper（方案 7.5/10.1）：后台可见全部生命周期
 * （线上/待审/精选/下架/封禁），仅排除软删除行；名称与分类/授权按
 * “线上快照优先，其次工作快照”取值，待审快照 ID 单独标出。
 */
@Mapper
public interface AdminSoftwareCatalogMapper {

    /**
     * 目录分页：keyword（名称/简介）、ownerId、categoryId、license、status。
     * status 白名单在服务层校验，SQL 只接收已校验值（禁止客户端注入）。
     */
    @Select("""
            <script>
            SELECT s.id, s.owner_id AS ownerId, s.lifecycle, s.featured_rank AS featuredRank,
                   s.published_revision_id AS publishedRevisionId,
                   s.working_revision_id AS workingRevisionId,
                   (SELECT pr.id FROM software_revision pr
                     WHERE pr.software_id = s.id AND pr.review_status = 'PENDING'
                     ORDER BY pr.id DESC LIMIT 1) AS pendingRevisionId,
                   COALESCE(p.name, w.name) AS name,
                   COALESCE(p.summary, w.summary) AS summary,
                   COALESCE(p.category_id, w.category_id) AS categoryId,
                   c.name AS categoryName,
                   COALESCE(p.license_type, w.license_type) AS licenseType,
                   s.comment_count AS commentCount, s.favorite_count AS favoriteCount,
                   s.link_access_count AS linkAccessCount, s.rating_count AS ratingCount,
                   s.rating_sum AS ratingSum, s.published_at AS publishedAt,
                   s.last_release_at AS lastReleaseAt, s.created_at AS createdAt,
                   s.updated_at AS updatedAt
            FROM software s
            LEFT JOIN software_revision p ON p.id = s.published_revision_id
            LEFT JOIN software_revision w ON w.id = s.working_revision_id
            LEFT JOIN software_category c ON c.id = COALESCE(p.category_id, w.category_id)
            WHERE s.deleted_at IS NULL AND s.lifecycle &lt;&gt; 'DELETED'
            <if test="q.keyword != null and q.keyword != ''">
              AND (COALESCE(p.name, w.name) LIKE CONCAT('%', #{q.keyword}, '%')
                   OR COALESCE(p.summary, w.summary) LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.ownerId != null">AND s.owner_id = #{q.ownerId}</if>
            <if test="q.categoryId != null">AND COALESCE(p.category_id, w.category_id) = #{q.categoryId}</if>
            <if test="q.license != null and q.license != ''">
              AND COALESCE(p.license_type, w.license_type) = #{q.license}
            </if>
            <if test="q.status == 'ACTIVE'">AND s.lifecycle = 'ACTIVE'</if>
            <if test="q.status == 'UNLISTED'">AND s.lifecycle = 'UNLISTED'</if>
            <if test="q.status == 'BANNED'">AND s.lifecycle = 'BANNED'</if>
            <if test="q.status == 'FEATURED'">AND s.featured_rank &gt; 0</if>
            <if test="q.status == 'PENDING'">
              AND EXISTS (SELECT 1 FROM software_revision pr
                          WHERE pr.software_id = s.id AND pr.review_status = 'PENDING')
            </if>
            ORDER BY s.updated_at DESC, s.id DESC
            </script>
            """)
    Page<ItemRow> pageItems(Page<ItemRow> page, @Param("q") AdminItemQuery query);

    /** 目录行（Mapper 内部行结构，不外暴实体） */
    record ItemRow(Long id, Long ownerId, String lifecycle, Integer featuredRank,
                   Long publishedRevisionId,
                   Long workingRevisionId, Long pendingRevisionId, String name, String summary,
                   Long categoryId, String categoryName, String licenseType, Integer commentCount,
                   Integer favoriteCount, Long linkAccessCount, Integer ratingCount,
                   Integer ratingSum, LocalDateTime publishedAt, LocalDateTime lastReleaseAt,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
