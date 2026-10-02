package com.cloudmart.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 售后案件时间线（T11）：全部状态迁移留痕，运营后台按 caseId 取证。 */
@Data
@TableName("after_sale_case_event")
public class AfterSaleCaseEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long caseId;

    private String action;

    private String operator;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
