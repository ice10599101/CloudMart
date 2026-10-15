package com.cloudmart.software.service;

import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 软件介绍富文本服务端消毒（方案 4.1/11.1）：Tiptap 兼容子集白名单。
 *
 * <p>允许：p/br/h1-h3/ul/ol/li/strong/em/b/i/code/pre/blockquote
 * 与 a[href]（仅 https，强制 rel/target 白名单属性）；
 * 其余标签/属性（含 img、style、on* 事件）一律剥离——正文媒体不内嵌 HTML，
 * 经素材引用绑定（bizType=SOFTWARE_REVISION）独立展示。</p>
 *
 * <p>防御对象：客户端绕过表单直接提交的任意 HTML/脚本（XSS）。
 * 消毒输出与提取纯文本（搜索/审核用）由本服务统一产生，保存前调用。</p>
 */
@Component
public class SoftwareHtmlSanitizer {

    private final Safelist safelist;
    private final org.jsoup.nodes.Document.OutputSettings outputSettings;

    public SoftwareHtmlSanitizer() {
        // basic() 已含 a[href]/em/strong/blockquote/code/ul/ol/li/p 等；
        // 补齐标题与换行，剥离所有图片；协议收紧到 https
        this.safelist = Safelist.basic()
                .addTags("h1", "h2", "h3", "br")
                .removeTags("img")
                .addProtocols("a", "href", "https")
                .removeProtocols("a", "href", "ftp", "http", "mailto");
        this.outputSettings = new Document.OutputSettings().prettyPrint(false);
    }

    /**
     * @param rawHtml 前端提交的富文本（≤50000 字符由 DTO 校验）
     * @return 消毒后的 HTML（Tiptap 兼容子集）
     */
    public String sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }
        String cleaned = Jsoup.clean(rawHtml, "", safelist, outputSettings);
        // Jsoup.clean 输出片段；再经 body 解析规范化（嵌套/闭合），剥离首尾空白
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(outputSettings);
        return document.body().html().trim();
    }

    /** 消毒后 HTML 提取纯文本（搜索与敏感词审核输入；标签外文本不进入审核） */
    public String extractText(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return "";
        }
        return Jsoup.parseBodyFragment(sanitizedHtml).body().text().trim();
    }
}
