package com.voxelgame.world.entity.mob;

/**
 * Lớp trừu tượng đại diện cho nhóm động vật hiền lành, không phản công và chạy hoảng loạn khi bị đánh
 * (gồm Cow, Sheep, Pig, Chicken, Rabbit).
 */
public abstract class PassiveMob extends MobEntity {

    /** Khởi tạo thú hiền lành với bộ goal mặc định từ danh mục loài. */
    protected PassiveMob(MobType type, float x, float y, float z) {
        super(type, x, y, z);
    }

    /** Khởi tạo thú hiền lành với tùy chọn cài đặt danh sách goal riêng (như Rabbit). */
    protected PassiveMob(MobType type, float x, float y, float z, float armor, int seatCount,
                         boolean installDefaultGoals) {
        super(type, x, y, z, armor, seatCount, installDefaultGoals);
    }
}
