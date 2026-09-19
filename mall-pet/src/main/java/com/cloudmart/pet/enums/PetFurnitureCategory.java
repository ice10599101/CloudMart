package com.cloudmart.pet.enums;

/**
 * 家具分类。
 *
 * <p>{@code theme} = 房间风格键（墙纸/地板，穿戴在 pet_room 上，不占网格）；
 * 其余分类可摆放到房间网格，单位舒适度累加为房间舒适度。</p>
 */
public enum PetFurnitureCategory {
    /** 墙纸（风格键） */
    WALL("墙纸", true),
    /** 地板（风格键） */
    FLOOR("地板", true),
    /** 家具 */
    FURNITURE("家具", false),
    /** 绿植 */
    PLANT("绿植", false),
    /** 玩具 */
    TOY("玩具", false),
    /** 床 */
    BED("床", false);

    private final String label;
    private final boolean theme;

    PetFurnitureCategory(String label, boolean theme) {
        this.label = label;
        this.theme = theme;
    }

    public String label() {
        return label;
    }

    /** 是否风格键（穿戴而非摆放） */
    public boolean theme() {
        return theme;
    }
}
