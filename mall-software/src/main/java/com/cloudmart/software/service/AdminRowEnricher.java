package com.cloudmart.software.service;

import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台列表行级信息补全（昵称/软件名）——各管理列表共用的静态工具。
 *
 * <p>约定：ID 列保留原始值，昵称/名称写入新的输出键（前端"ID + 名字"双列展示）。
 * profiles/查询失败时留空，前端回落为 ID 展示，列表主流程不受阻。</p>
 */
public final class AdminRowEnricher {

    private AdminRowEnricher() {
    }

    private static List<Long> collectIds(List<Map<String, Object>> rows, String idKey) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get(idKey);
            if (raw == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(String.valueOf(raw)));
            } catch (NumberFormatException ignored) {
                // 非数字 ID：跳过
            }
        }
        return ids.stream().distinct().toList();
    }

    /** 批量补用户昵称：rows[idKey] → rows[outKey] = 昵称 */
    public static void fillUserNicknames(List<Map<String, Object>> rows,
                                         UserSupportFeignClient feign,
                                         String idKey, String outKey) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = collectIds(rows, idKey);
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nicknameById = new HashMap<>();
        try {
            for (var profile : feign.profiles(Map.of("userIds", ids)).data()) {
                nicknameById.put(profile.id(), profile.nickname());
            }
        } catch (Exception ignored) {
            // 资料服务不可用：昵称留空
        }
        for (Map<String, Object> row : rows) {
            Object raw = row.get(idKey);
            if (raw == null) {
                continue;
            }
            try {
                String nickname = nicknameById.get(Long.parseLong(String.valueOf(raw)));
                if (nickname != null && !nickname.isBlank()) {
                    row.put(outKey, nickname);
                }
            } catch (NumberFormatException ignored) {
                // 非数字 ID：留空
            }
        }
    }

    /** 批量补软件名：rows[idKey] → rows[outKey] = 最新内容名称（工作快照优先，其次线上） */
    public static void fillSoftwareNames(List<Map<String, Object>> rows,
                                         SoftwareMapper softwareMapper,
                                         SoftwareRevisionMapper revisionMapper,
                                         String idKey, String outKey) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = collectIds(rows, idKey);
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Long softwareId : ids) {
            Software software = softwareMapper.selectById(softwareId);
            if (software == null) {
                continue;
            }
            Long revisionId = software.getWorkingRevisionId() != null
                    ? software.getWorkingRevisionId() : software.getPublishedRevisionId();
            if (revisionId == null) {
                continue;
            }
            SoftwareRevision revision = revisionMapper.selectById(revisionId);
            if (revision != null && revision.getName() != null && !revision.getName().isBlank()) {
                nameById.put(softwareId, revision.getName());
            }
        }
        for (Map<String, Object> row : rows) {
            Object raw = row.get(idKey);
            if (raw == null) {
                continue;
            }
            try {
                String name = nameById.get(Long.parseLong(String.valueOf(raw)));
                if (name != null) {
                    row.put(outKey, name);
                }
            } catch (NumberFormatException ignored) {
                // 非数字 ID：留空
            }
        }
    }
}
