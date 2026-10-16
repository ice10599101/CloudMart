package com.cloudmart.software.service;

import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;

/**
 * 富文本纯文本提取（搜索与敏感词审核输入）。
 *
 * <p>历史说明：本类原含服务端 HTML 白名单消毒（2026-10-17 按用户决定移除）——
 * descriptionHtml 现原样保存，展示端安全由页面的 DOMPurify 兜底；
 * 图片经文件服务上传（/files/{assetId}），上传时的类型/大小校验仍在文件服务。</p>
 */
@Component
public class SoftwareHtmlSanitizer {

    /** 消毒后 HTML 提取纯文本（搜索与敏感词审核输入；标签外文本不进入审核） */
    public String extractText(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return "";
        }
        return Jsoup.parseBodyFragment(sanitizedHtml).body().text().trim();
    }
}
