package com.voxelgame.world.entity.mob;

/**
 * Lớp trừu tượng đại diện cho nhóm sinh vật trung lập, chỉ nổi giận hoặc phản kích khi bị kích động
 * hoặc thỏa điều kiện môi trường (gồm Spider, Enderman, Goat, PolarBear, Wolf, SnowFox).
 */
public abstract class NeutralMob extends MobEntity {

    /** Khởi tạo sinh vật trung lập với bộ goal mặc định từ danh mục loài. */
    protected NeutralMob(MobType type, float x, float y, float z) {
        super(type, x, y, z);
    }

    /** Khởi tạo sinh vật trung lập với giáp, số ghế và cờ cài đặt goal riêng. */
    protected NeutralMob(MobType type, float x, float y, float z, float armor, int seatCount,
                         boolean installDefaultGoals) {
        super(type, x, y, z, armor, seatCount, installDefaultGoals);
    }

    /** Khởi tạo sinh vật trung lập kèm tỷ lệ kích thước thể chất (dùng cho gấu con). */
    protected NeutralMob(MobType type, float x, float y, float z, float armor, int seatCount,
                         boolean installDefaultGoals, float dimensionsScale) {
        super(type, x, y, z, armor, seatCount, installDefaultGoals, dimensionsScale);
    }
}
