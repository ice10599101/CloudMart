package com.cloudmart.software.dto.software;

import lombok.Data;

import java.util.List;

/** UX06：软件需求提交入参（结构化求助：用途/平台/必须功能/可接受许可） */
@Data
public class SoftwareRequestCreateInput {

    private String title;

    /** 用途描述（要完成什么、什么场景；50~500 字） */
    private String usageDesc;

    private List<String> mustHaveFeatures;

    /** WINDOWS/MACOS/LINUX/WEB */
    private List<String> platformScope;

    private List<String> acceptableLicense;

    /** 来源搜索关键词（搜索无结果进入时保留） */
    private String sourceKeyword;
}
