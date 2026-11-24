package com.voxelgame.entity;

/** Nguồn sát thương và khả năng được giảm bởi giáp tự nhiên. */
public enum DamageSource {
    MELEE(true), ARROW(true), POTION_MAGIC(false), EXPLOSION(true), FALL(false),
    CACTUS(true), LAVA(false), FIRE(false), SUNLIGHT(false), DROWNING(false);

    /** Cờ cho phép áp dụng công thức giáp vật lý. */
    private final boolean armorApplies;

    /** Gắn quy tắc giáp cho nguồn tương ứng. */
    DamageSource(boolean armorApplies) {
        this.armorApplies = armorApplies;
    }

    /** Cho biết nguồn này chịu ảnh hưởng của giáp. */
    public boolean armorApplies() {
        return armorApplies;
    }
}
