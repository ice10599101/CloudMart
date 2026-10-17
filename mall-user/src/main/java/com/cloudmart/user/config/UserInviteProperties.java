package com.cloudmart.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WISH-04：邀请奖励资格规则（真实业务配置——非硬编码；页面展示 ruleVersion）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "user.invite")
public class UserInviteProperties {

    /** 邀请人每日最多有效奖励数 */
    private int dailyLimit = 5;

    /** 邀请人每月最多有效奖励数 */
    private int monthlyLimit = 30;

    /** 受邀账号最小年龄（小时）——注册满 24 小时才有资格 */
    private int accountAgeHours = 24;

    /** 资格要求的最少不同业务日签到次数（首期=2） */
    private int requiredDistinctSigninDays = 2;

    /** 规则版本（页面展示；改规则即升版本） */
    private int ruleVersion = 1;
}
