package com.voxelgame.entity.mob;

/** Lợn đồng bằng dùng goal lang thang, chạy hoảng và mẫu tiếng ủn ỉn của loài. */
public final class Pig extends MobEntity {
    /** Áp dụng tốc độ của lợn cùng cấu hình hiền lành trong danh mục. */
    public Pig(float x, float y, float z) {
        super(MobType.PIG, x, y, z);
        setMoveSpeed(2.5f);
    }
}
