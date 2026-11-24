package com.voxelgame.entity.mob;

/** Nhóm dân số tự nhiên, mỗi nhóm có hạn mức riêng cho lượt sinh định kỳ. */
public enum MobCategory {
    MONSTER(70),
    CREATURE(10);

    private final int cap;

    /** Ghi hạn mức cho nhóm ngay khi nạp danh mục. */
    MobCategory(int cap) {
        this.cap = cap;
    }

    /** Trả hạn mức sinh định kỳ. Sinh bầy khi khám phá bỏ qua hạn mức CREATURE. */
    public int cap() {
        return cap;
    }
}
