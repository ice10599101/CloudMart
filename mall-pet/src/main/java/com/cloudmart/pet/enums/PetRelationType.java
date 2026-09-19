package com.cloudmart.pet.enums;

/**
 * 宠物关系类型（原文档三期：情侣/闺蜜/兄弟/死党）。
 *
 * <p>{@code exclusive} 表示该类型每只宠物只能有一段（情侣 1v1，关系确定后不可再对他人发起）；
 * {@code maxPerPet} 是非独占类型的上限，与 Nacos 配置的默认值保持一致。</p>
 */
public enum PetRelationType {
    /** 情侣（独占，每只宠物至多 1 段） */
    COUPLE("情侣", true, 1),
    /** 闺蜜 */
    BESTIE("闺蜜", false, 3),
    /** 兄弟 */
    BROTHER("兄弟", false, 3),
    /** 死党 */
    CONFIDANT("死党", false, 3);

    private final String label;
    private final boolean exclusive;
    private final int maxPerPet;

    PetRelationType(String label, boolean exclusive, int maxPerPet) {
        this.label = label;
        this.exclusive = exclusive;
        this.maxPerPet = maxPerPet;
    }

    /** 中文名（通知文案用） */
    public String label() {
        return label;
    }

    /** 是否独占（每只宠物至多一段） */
    public boolean exclusive() {
        return exclusive;
    }

    /** 每只宠物最大段数 */
    public int maxPerPet() {
        return maxPerPet;
    }
}
