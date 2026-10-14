package com.cloudmart.pet.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字符串必须是指定枚举的合法常量名（C18：DTO 白名单统一以枚举为唯一字典，
 * 禁止在校验注解里再维护一份散落的正则字面量）。null 放行——必填由 {@code @NotBlank} 表达。
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InEnumValidator.class)
public @interface InEnum {

    Class<? extends Enum<?>> value();

    String message() default "取值非法";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
