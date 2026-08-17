package com.cloudmart.wish.enums;

/**
 * 心愿果实类型枚举。
 *
 * <p>状态转换规则（见文档第二章 3. 心愿果实系统）：</p>
 * <ul>
 *   <li>GLOW（微光果实）：新愿望发布时生成</li>
 *   <li>RESONANCE（共鸣果实）：获得互动（同求/点亮）后生成，体积和光效随互动量动态变大</li>
 *   <li>BLOOM（绽放果实）：完成愿望后生成，附带粒子炸裂动效</li>
 *   <li>SPARK（星火果实）：永久收藏状态，还愿后作者可设为星火永久展示</li>
 * </ul>
 */
public enum FruitType {
    /** 微光果实（新愿望） */
    GLOW,
    /** 共鸣果实（获得互动） */
    RESONANCE,
    /** 绽放果实（完成愿望） */
    BLOOM,
    /** 星火果实（永久收藏，不可归档） */
    SPARK
}
