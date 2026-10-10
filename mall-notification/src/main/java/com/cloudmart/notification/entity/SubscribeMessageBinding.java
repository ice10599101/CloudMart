package com.cloudmart.notification.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 订阅消息授权额度（N-1）：用户在小程序每授权一次即一行 = 一次发送额度；
 * 发送成功或确定失败（43101 拒收）后标记 consumed。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("subscribe_message_binding")
public class SubscribeMessageBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String openid;

    private String templateKey;

    private Boolean consumed;

    private LocalDateTime consumedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
