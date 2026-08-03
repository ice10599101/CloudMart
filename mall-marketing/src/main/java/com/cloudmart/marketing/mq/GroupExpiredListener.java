package com.cloudmart.marketing.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.marketing.config.RocketMQConfig;
import com.cloudmart.marketing.entity.GroupMember;
import com.cloudmart.marketing.entity.GroupOrder;
import com.cloudmart.marketing.repository.GroupMemberMapper;
import com.cloudmart.marketing.repository.GroupOrderMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 拼团超时消费者：标记过期+清理Redis。退款由 mall-payment 通过独立的 ConsumerGroup 独立消费。
 */
@Component
@RocketMQMessageListener(
        topic = RocketMQConfig.MARKETING_TOPIC,
        consumerGroup = RocketMQConfig.CG_MARKETING_GROUP_EXPIRED,
        selectorExpression = RocketMQConfig.MARKETING_TAG_GROUP_EXPIRED
)
public class GroupExpiredListener implements RocketMQListener<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(GroupExpiredListener.class);

    private final GroupOrderMapper groupOrderMapper;
    private final GroupMemberMapper memberMapper;
    private final StringRedisTemplate redisTemplate;

    public GroupExpiredListener(GroupOrderMapper groupOrderMapper,
                                GroupMemberMapper memberMapper,
                                StringRedisTemplate redisTemplate) {
        this.groupOrderMapper = groupOrderMapper;
        this.memberMapper = memberMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public void onMessage(Map<String, Object> message) {
        Long groupOrderId = ((Number) message.get("groupOrderId")).longValue();
        log.info("Processing group expiration for groupOrder={}", groupOrderId);

        GroupOrder groupOrder = groupOrderMapper.selectById(groupOrderId);
        if (groupOrder == null || "EXPIRED".equals(groupOrder.getStatus())) {
            return;
        }

        // 标记拼团组过期
        groupOrder.setStatus("EXPIRED");
        groupOrderMapper.updateById(groupOrder);

        // 标记所有成员退款
        List<GroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupOrderId, groupOrderId)
                        .eq(GroupMember::getStatus, "JOINED")
        );

        for (GroupMember member : members) {
            member.setStatus("EXPIRED");
            memberMapper.updateById(member);
        }

        // 清理 Redis
        redisTemplate.delete("marketing:group:" + groupOrderId);
        redisTemplate.delete("marketing:group_users:" + groupOrderId);

        log.info("Group order {} expired, {} members marked for refund", groupOrderId, members.size());
    }
}
