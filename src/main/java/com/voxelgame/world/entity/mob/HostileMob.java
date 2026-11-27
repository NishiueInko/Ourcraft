package com.voxelgame.world.entity.mob;

/**
 * Lớp trừu tượng đại diện cho nhóm quái vật thù địch chủ động tấn công người chơi
 * (ví dụ: Zombie, Creeper và nhánh đánh xa RangedMob gồm Skeleton, Stray, Parched, Witch).
 */
public abstract class HostileMob extends MobEntity {

    /** Khởi tạo quái thù địch với thông số mặc định từ danh mục loài. */
    protected HostileMob(MobType type, float x, float y, float z) {
        super(type, x, y, z);
    }

    /** Khởi tạo quái thù địch với giáp tự nhiên, số ghế và cờ cài đặt goal mặc định. */
    protected HostileMob(MobType type, float x, float y, float z, float armor, int seatCount,
                         boolean installDefaultGoals) {
        super(type, x, y, z, armor, seatCount, installDefaultGoals);
    }

    /** Khởi tạo quái thù địch kèm tỷ lệ kích thước thể chất (dùng cho cá thể non). */
    protected HostileMob(MobType type, float x, float y, float z, float armor, int seatCount,
                         boolean installDefaultGoals, float dimensionsScale) {
        super(type, x, y, z, armor, seatCount, installDefaultGoals, dimensionsScale);
    }
}
