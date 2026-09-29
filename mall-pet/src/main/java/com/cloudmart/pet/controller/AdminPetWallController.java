package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.enums.PetWallStatus;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 宠物留言墙管理端接口（审核）。
 *
 * <p>治理口径：用户删自己的留言 → {@code DELETED}；管理员隐藏违规留言 → {@code HIDDEN}
 * （用户端立即不可见，管理端仍可查看，保留溯源）；恢复 → {@code NORMAL}。</p>
 */
@RestController
@RequestMapping("/admin/pet/wall")
@Tag(name = "宠物留言墙管理", description = "留言审核（隐藏/恢复/删除）")
@RequiredArgsConstructor
public class AdminPetWallController {

    private final PetWallMessageMapper wallMessageMapper;

    @GetMapping("/messages")
    @Operation(summary = "留言列表", description = "按宠物/作者/状态筛选；beforeId 游标模式（深翻页首选）或 offset 分页（兼容）；含 DELETED/HIDDEN 全量（审计）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetWallMessage>> listMessages(
            @RequestParam(value = "petId", required = false) Long petId,
            @RequestParam(value = "authorUserId", required = false) Long authorUserId,
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "游标：上一页最后一条的 id（与 page 互斥，提供时走游标模式）")
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        // P2-3：admin 列表 size 上限统一 100，防止一次性拉爆
        int safeSize = Math.min(Math.max(1, size), 100);
        LambdaQueryWrapper<PetWallMessage> wrapper = new LambdaQueryWrapper<PetWallMessage>()
                .eq(petId != null, PetWallMessage::getPetId, petId)
                .eq(authorUserId != null, PetWallMessage::getAuthorUserId, authorUserId)
                .eq(status != null && !status.isBlank(), PetWallMessage::getStatus, status)
                .orderByDesc(PetWallMessage::getId);
        if (beforeId != null) {
            // P2-3：游标模式（id 倒序 + beforeId 截断）——大 offset 的深翻页不再有 offset 扫描成本
            List<PetWallMessage> records = wallMessageMapper.selectList(wrapper
                    .lt(PetWallMessage::getId, beforeId)
                    .last("LIMIT " + (safeSize + 1)));
            boolean hasMore = records.size() > safeSize;
            if (hasMore) {
                records = records.subList(0, safeSize);
            }
            String nextCursor = hasMore && !records.isEmpty()
                    ? String.valueOf(records.get(records.size() - 1).getId()) : null;
            return ApiResponse.okWithCursor(records, safeSize, nextCursor, hasMore);
        }
        Page<PetWallMessage> result = wallMessageMapper.selectPage(
                new Page<>(Math.max(1, page), safeSize), wrapper);
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @PutMapping("/messages/{id}/hide")
    @Operation(summary = "隐藏留言（F6）", description = "NORMAL → HIDDEN（用户端显示占位文案，管理端保留审计）；已隐藏/已删除跳过")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> hide(@PathVariable("id") Long id) {
        PetWallMessage message = wallMessageMapper.selectById(id);
        if (message == null) {
            throw new BusinessException(PetErrorCodes.PET_WALL_MESSAGE_NOT_FOUND, "留言不存在");
        }
        wallMessageMapper.update(null, new LambdaUpdateWrapper<PetWallMessage>()
                .set(PetWallMessage::getStatus, PetWallStatus.HIDDEN.name())
                .eq(PetWallMessage::getId, id)
                .eq(PetWallMessage::getStatus, PetWallStatus.NORMAL.name()));
        return ApiResponse.ok(null);
    }

    @GetMapping("/messages/export-page")
    @Operation(summary = "导出分页（F6，供代理层流式拼装 CSV）", description = "beforeId 游标 + 时间范围；返回原始行，幂等")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> exportPage(
            @Parameter(description = "游标：上一批最后一条 id") @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "size", defaultValue = "500") int size,
            @Parameter(description = "起始日（含，UTC）") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "结束日（含，UTC）") @RequestParam(value = "to", required = false) String to) {
        int safeSize = Math.min(Math.max(1, size), 1000);
        LambdaQueryWrapper<PetWallMessage> wrapper = new LambdaQueryWrapper<PetWallMessage>()
                .orderByDesc(PetWallMessage::getId)
                .last("LIMIT " + safeSize);
        if (beforeId != null) {
            wrapper.lt(PetWallMessage::getId, beforeId);
        }
        if (from != null && !from.isBlank()) {
            wrapper.ge(PetWallMessage::getCreatedAt, java.time.LocalDate.parse(from).atStartOfDay());
        }
        if (to != null && !to.isBlank()) {
            wrapper.lt(PetWallMessage::getCreatedAt, java.time.LocalDate.parse(to).plusDays(1).atStartOfDay());
        }
        java.util.List<PetWallMessage> rows = wallMessageMapper.selectList(wrapper);
        java.util.List<java.util.Map<String, Object>> result = rows.stream()
                .map(row -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
                    map.put("id", row.getId());
                    map.put("petId", row.getPetId());
                    map.put("authorUserId", row.getAuthorUserId());
                    map.put("status", row.getStatus());
                    map.put("likeCount", row.getLikeCount());
                    map.put("content", row.getContent());
                    map.put("createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
                    return map;
                })
                .toList();
        return ApiResponse.ok(result, 0, safeSize, result.size());
    }

    @PutMapping("/messages/{id}/status")
    @Operation(summary = "留言状态流转", description = "NORMAL 恢复 / HIDDEN 隐藏 / DELETED 删除（仅允许已实现的三种状态）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> body) {
        Object rawStatus = body.get("status");
        String status = rawStatus != null ? rawStatus.toString() : null;
        if (status == null || !List.of(PetWallStatus.NORMAL.name(), PetWallStatus.HIDDEN.name(),
                PetWallStatus.DELETED.name()).contains(status)) {
            throw new BusinessException(PetErrorCodes.PET_WALL_MESSAGE_INVALID, "状态非法");
        }
        PetWallMessage message = wallMessageMapper.selectById(id);
        if (message == null) {
            throw new BusinessException(PetErrorCodes.PET_WALL_MESSAGE_NOT_FOUND, "留言不存在");
        }
        PetWallMessage patch = new PetWallMessage();
        patch.setId(id);
        patch.setStatus(status);
        wallMessageMapper.updateById(patch);
        return ApiResponse.ok(null);
    }
}
