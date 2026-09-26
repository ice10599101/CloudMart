package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishDraft;
import com.cloudmart.wish.service.impl.WishDraftServiceImpl;

import java.util.List;

/** 心愿草稿（N03）：只本人可见、不发奖励；publish 复用发布领域命令并幂等关联。 */
public interface WishDraftService {

    /** 保存草稿（clientDraftId 幂等；version 乐观锁自动保存；每人最多 20 份） */
    WishDraft saveDraft(Long userId, String clientDraftId, WishDraftServiceImpl.SaveDraftRequest request);

    /** 本人草稿列表 */
    List<WishDraft> listMyDrafts(Long userId, Long cursor, int pageSize);

    /** 删除草稿（软删；version CAS） */
    void deleteDraft(Long userId, Long draftId, Long version);

    /** 发布草稿（复用 createWish；同草稿仅首次发布，幂等） */
    Wish publishDraft(Long userId, Long draftId, CreateWishRequest request);

    /** 归属校验（非作者统一 404） */
    WishDraft requireOwnedDraft(Long userId, Long draftId);
}
