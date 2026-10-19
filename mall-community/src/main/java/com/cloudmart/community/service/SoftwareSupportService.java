package com.cloudmart.community.service;

import com.cloudmart.community.dto.software.InteractionContextRequest;
import com.cloudmart.community.dto.software.SoftwareReviewRequest;
import com.cloudmart.community.vo.software.SoftwareInteractionContextVO;
import com.cloudmart.community.vo.software.SoftwareLevelOptionVO;
import com.cloudmart.community.vo.software.SoftwareLevelVO;
import com.cloudmart.community.vo.software.SoftwareReviewOutcomeVO;

import java.util.List;

/**
 * 小答软件域只读适配服务（方案 9.6）：在社区现有能力之上提供窄接口，
 * 不暴露敏感词清单/数据库结构，不提供任何经验发放或规则写入能力。
 * 敏感词规则与拉黑关系仍以社区库为唯一事实，软件侧不落第二份。
 */
public interface SoftwareSupportService {

    /** 实时查询用户社区等级（沿用现有成长服务"首次查询创建 1 级记录"规则） */
    SoftwareLevelVO level(Long userId);

    /** 启用中的等级配置（发布表单可短缓存展示；提交/resolve 仍实时校验） */
    List<SoftwareLevelOptionVO> levelOptions();

    /**
     * UX04：批量查询帖子公开状态（软件教程入口用）。
     * public=true 严格沿用帖子可读谓词（未删除+已发布+审核通过）；
     * title/summary 只返回公开信息，非公开帖不泄露存在性细节（仅 public=false）。
     */
    java.util.List<java.util.Map<String, Object>> postPublicStatus(java.util.List<Long> postIds);

    /** 批量审核软件消毒后的文本节点；结果与请求同序同长并回传 contentHash */
    SoftwareReviewOutcomeVO review(SoftwareReviewRequest request);

    /** 双向拉黑视角的互动资格结论（候选作者批量，评论列表一次调用） */
    SoftwareInteractionContextVO interactionContext(InteractionContextRequest request);
}
