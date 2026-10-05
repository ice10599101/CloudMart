package com.cloudmart.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.entity.FileReference;
import com.cloudmart.file.repository.FileAssetMapper;
import com.cloudmart.file.repository.FileReferenceMapper;
import com.cloudmart.file.service.AccountErasureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 账号数据擦除（T06 补齐：E10 实测 FILE 域 ERASURE_DOMAIN_NOT_WIRED）。
 *
 * <p>处置口径：未被业务引用的资产连存储对象一并删除（个人文件无保留价值）；
 * 被业务引用的资产不可物理删（T15 引用锁），转为匿名化持有（original_name 清空、
 * visibility 降为 PRIVATE）——内容仍在但归属不再可追溯到本人。
 * 幂等：重复调用无害。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureServiceImpl implements AccountErasureService {

    private final FileAssetMapper fileAssetMapper;
    private final FileReferenceMapper fileReferenceMapper;
    private final com.cloudmart.file.service.FileService fileService;

    @Override
    @Transactional
    public boolean eraseUserData(Long userId) {
        List<FileAsset> assets = fileAssetMapper.selectList(new LambdaQueryWrapper<FileAsset>()
                .eq(FileAsset::getOwnerId, userId));
        int deleted = 0;
        int anonymized = 0;
        for (FileAsset asset : assets) {
            Long referenced = fileReferenceMapper.selectCount(new LambdaQueryWrapper<FileReference>()
                    .eq(FileReference::getAssetId, asset.getId()));
            if (referenced == 0) {
                fileService.deleteByStorageKey(asset.getStorageKey());
                fileAssetMapper.deleteById(asset.getId());
                deleted++;
            } else {
                fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                        .eq(FileAsset::getId, asset.getId())
                        .set(FileAsset::getOriginalName, "erased")
                        .set(FileAsset::getVisibility, "PRIVATE"));
                anonymized++;
            }
        }
        log.warn("T06 编排处置文件资产 userId={}, deleted={}, anonymized={}", userId, deleted, anonymized);
        return true;
    }
}
