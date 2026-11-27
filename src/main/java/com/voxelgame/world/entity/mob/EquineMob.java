package com.voxelgame.world.entity.mob;

import java.util.random.RandomGenerator;

/**
 * Lớp trừu tượng đại diện cho nhóm thú cưỡi họ ngựa (tương đương {@code AbstractHorse} trong Minecraft),
 * gom chung cấu hình một ghế ngồi, bước lên bậc 1 block và phân bố máu ngẫu nhiên 15–30 HP.
 * {@link com.voxelgame.world.entity.mob.animal.Horse} và
 * {@link com.voxelgame.world.entity.mob.animal.Donkey} kế thừa ngang hàng từ lớp này để giữ đúng
 * nguyên lý thay thế Liskov (LSP).
 */
public abstract class EquineMob extends MountableMob {
    /** Số ghế đơn và chiều cao bước trực tiếp chuẩn của họ ngựa. */
    private static final int EQUINE_SEAT_COUNT = 1;
    private static final float EQUINE_STEP_HEIGHT = 1f;

    /**
     * Khởi tạo cá thể họ ngựa hoang dã với một ghế ngồi và chỉ số tốc độ/nhảy/máu riêng.
     *
     * @param type       loại mob ({@link MobType#HORSE} hoặc {@link MobType#DONKEY})
     * @param x          tọa độ X ban đầu
     * @param y          tọa độ Y ban đầu
     * @param z          tọa độ Z ban đầu
     * @param moveSpeed  tốc độ di chuyển cơ sở (block/giây)
     * @param jumpHeight chiều cao nhảy tối đa (block)
     * @param maxHealth  máu tối đa của cá thể (15–30 HP)
     */
    protected EquineMob(MobType type, float x, float y, float z,
                        float moveSpeed, float jumpHeight, float maxHealth) {
        super(type, x, y, z, EQUINE_SEAT_COUNT, EQUINE_STEP_HEIGHT, moveSpeed, jumpHeight, false, true, maxHealth);
    }

    /** Chọn máu nguyên từ 15 đến 30 với xác suất tập trung cao hơn quanh giá trị trung tâm. */
    protected static int randomHealth(RandomGenerator random) {
        return 15 + random.nextInt(8) + random.nextInt(9);
    }
}
