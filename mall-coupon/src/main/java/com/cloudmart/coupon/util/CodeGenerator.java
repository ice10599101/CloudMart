package com.cloudmart.coupon.util;

import org.springframework.stereotype.Component;

/**
 * 兑换码生成器
 * <p>
 * 算法组成：
 * <ol>
 *   <li>序列号：由 Redis INCR 原子递增，保证分布式唯一</li>
 *   <li>校验码：对序列号各位数字做加权求和（质数权重），取模 10 得到 1 位校验位</li>
 *   <li>组合：payload = seq * 10 + checksum，将校验位附加到序列号末尾</li>
 *   <li>编码：Base32 编码 payload，使用去除易混淆字符（0/1/I/O）的 32 字符字母表</li>
 * </ol>
 * 设计考量：
 * <ul>
 *   <li>校验码可在前端快速拦截非法输入，降低 DB/Redis 压力</li>
 *   <li>Base32 相比 Base10 更紧凑，相比 Base64 更友好（全大写字母+数字）</li>
 *   <li>去除 0/1/I/O 避免用户肉眼混淆，降低输入错误率</li>
 * </ul>
 * </p>
 */
@Component
public class CodeGenerator {

    /** Base32 字母表，去除易混淆字符 0/1/I/O */
    private static final String BASE32_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** 质数权重表，用于加权校验码计算 */
    private static final int[] WEIGHTS = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29};

    /** 校验码基数（取模 10 得到 1 位校验位） */
    private static final int CHECKSUM_BASE = 10;

    /** Base32 每字符比特数 */
    private static final int BITS_PER_CHAR = 5;

    /**
     * 根据序列号生成兑换码
     *
     * @param sequence 序列号（必须 > 0）
     * @return Base32 编码的兑换码
     * @throws IllegalArgumentException 序列号非正
     */
    public String generate(long sequence) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("序列号必须为正数: " + sequence);
        }
        int checksum = computeChecksum(sequence);
        long payload = sequence * CHECKSUM_BASE + checksum;
        return base32Encode(payload);
    }

    /**
     * 校验兑换码格式合法性（校验码匹配）
     * <p>
     * 仅校验格式，不保证兑换码在数据库中存在或可用。
     * </p>
     *
     * @param code 待校验的兑换码
     * @return true 表示校验码匹配，false 表示格式非法或校验码不匹配
     */
    public boolean validate(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        Long payload = base32Decode(code);
        if (payload == null) {
            return false;
        }
        long sequence = payload / CHECKSUM_BASE;
        int checksum = (int) (payload % CHECKSUM_BASE);
        return sequence > 0 && computeChecksum(sequence) == checksum;
    }

    /**
     * 从兑换码中提取序列号（不做合法性校验，调用方应先调用 {@link #validate}）
     *
     * @param code 兑换码
     * @return 序列号，若解码失败返回 null
     */
    public Long extractSequence(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        Long payload = base32Decode(code);
        if (payload == null) {
            return null;
        }
        return payload / CHECKSUM_BASE;
    }

    /**
     * 计算序列号的加权校验码
     * <p>
     * 对序列号从低位到高位，依次乘以质数权重（循环使用），求和后取模 10。
     * </p>
     *
     * @param sequence 序列号
     * @return 校验码（0-9）
     */
    private int computeChecksum(long sequence) {
        long temp = sequence;
        int sum = 0;
        int i = 0;
        while (temp > 0) {
            int digit = (int) (temp % 10);
            sum += digit * WEIGHTS[i % WEIGHTS.length];
            temp /= 10;
            i++;
        }
        return sum % CHECKSUM_BASE;
    }

    /**
     * Base32 编码（无符号右移，逐 5 位映射）
     */
    private String base32Encode(long value) {
        if (value == 0) {
            return String.valueOf(BASE32_ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            int idx = (int) (value & 0x1F);
            sb.insert(0, BASE32_ALPHABET.charAt(idx));
            value >>>= BITS_PER_CHAR;
        }
        return sb.toString();
    }

    /**
     * Base32 解码
     *
     * @param code 兑换码
     * @return 解码后的 payload，若含非法字符返回 null
     */
    private Long base32Decode(String code) {
        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            int idx = BASE32_ALPHABET.indexOf(code.charAt(i));
            if (idx < 0) {
                return null;
            }
            result = (result << BITS_PER_CHAR) | idx;
        }
        return result;
    }
}
