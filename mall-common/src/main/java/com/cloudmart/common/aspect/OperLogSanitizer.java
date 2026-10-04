package com.cloudmart.common.aspect;

import java.util.regex.Pattern;

/**
 * 审计敏感数据脱敏（T24）：审计不能记录密码、令牌、完整地址、私密正文。
 *
 * <p>两级防御：① 参数名命中敏感名单 → 整值打码；② 序列化串级正则兜底——
 * record/Map 的 toString 展开形如 {@code password=xxx,}，按键名捕获打码。
 * 打码值统一为 {@code ***}（不保留长度信息），审计通过业务 ID 可回查事实。</p>
 */
public final class OperLogSanitizer {

    private static final String MASK = "***";

    /** 参数名/键名敏感名单（小写包含匹配） */
    private static final String[] SENSITIVE_KEY_WORDS = {
            "password", "passwd", "secret", "token", "authorization", "credential",
            "newpassword", "oldpassword", "confirmpassword",
    };

    /** 序列化串级兜底：record/Map toString 中 键=值 形态（值到下一个 ,) ] 或行尾） */
    private static final Pattern SERIALIZED_SENSITIVE = Pattern.compile(
            "(?i)((?:password|passwd|secret|token|authorization|credential)[a-z0-9]*\\s*=\\s*)"
                    + "([^,\\]\\)\\r\\n]*)");

    /** 地址键：整值打码（完整地址不入审计；业务 ID 可回查） */
    private static final Pattern SERIALIZED_ADDRESS = Pattern.compile(
            "(?i)((?:receiveraddress|address|fulladdress)\\s*=\\s*)([^,\\]\\)\\r\\n]*)");

    private OperLogSanitizer() {
    }

    /** 参数名级判定：命中敏感名单的参数整值打码 */
    public static boolean isSensitiveParamName(String paramName) {
        if (paramName == null) {
            return false;
        }
        String lower = paramName.toLowerCase();
        for (String word : SENSITIVE_KEY_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** 序列化参数串脱敏（paramNames 已知时优先按名打码，串级正则兜底 record 展开） */
    public static String sanitizeSerialized(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return serialized;
        }
        String out = SERIALIZED_SENSITIVE.matcher(serialized).replaceAll("$1" + MASK);
        out = SERIALIZED_ADDRESS.matcher(out).replaceAll("$1" + MASK);
        return out;
    }
}
