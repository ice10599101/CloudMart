package com.cloudmart.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {
    String title() default "";
    int businessType() default 0;
    int operatorType() default 1;
    boolean isSaveRequestData() default true;
    boolean isSaveResponseData() default true;
}
