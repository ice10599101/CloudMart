package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.enums.PetWallStatus;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "留言列表", description = "按宠物/作者/状态筛选，offset 分页；含 DELETED/HIDDEN 全量（审计）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetWallMessage>> listMessages(
            @RequestParam(value = "petId", required = false) Long petId,
            @RequestParam(value = "authorUserId", required = false) Long authorUserId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        LambdaQueryWrapper<PetWallMessage> wrapper = new LambdaQueryWrapper<PetWallMessage>()
                .eq(petId != null, PetWallMessage::getPetId, petId)
                .eq(authorUserId != null, PetWallMessage::getAuthorUserId, authorUserId)
                .eq(status != null && !status.isBlank(), PetWallMessage::getStatus, status)
                .orderByDesc(PetWallMessage::getId);
        Page<PetWallMessage> result = wallMessageMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)), wrapper);
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
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
