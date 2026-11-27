package com.voxelgame.entity.projectile;

import com.voxelgame.engine.MobAppearance;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.mob.Chicken;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Quả trứng ném bay theo parabol và có xác suất nở ra gà con ({@link Chicken#setBaby(boolean)})
 * khi vỡ tại điểm va chạm: xác suất {@code 1/8} nở 1 con, và trong số đó {@code 1/32} nở 4 con.
 */
public final class ThrownEggEntity extends ProjectileEntity {
    /** Gia tốc trọng trường dương (block/giây²) dùng chung với đạn ném bằng tay. */
    public static final float GRAVITY = 14f;
    /** Không gian mẫu chuẩn {@code [0, 255]} để biểu diễn chính xác xác suất {@code 1/8} và {@code 1/256}. */
    public static final int ROLL_RANGE = 256;
    /** Bán kính va chạm và thời gian bay tối đa của trứng ném. */
    private static final float RADIUS = 0.125f;
    private static final float LIFETIME = 10f;

    /** Giá trị tung xúc xắc cố định dùng cho kiểm thử đơn vị ({@code -1} nghĩa là ngẫu nhiên). */
    private int forcedRollForTest = -1;

    /**
     * Tạo quả trứng ném từ chủ sở hữu trong thế giới hiện tại.
     *
     * @param world thế giới nơi ném đạn (tham số ngữ cảnh thống nhất giao diện Track A)
     * @param owner thực thể sống ném trứng
     * @param x     tọa độ xuất phát X
     * @param y     tọa độ xuất phát Y
     * @param z     tọa độ xuất phát Z
     * @param vx    vận tốc ban đầu theo trục X
     * @param vy    vận tốc ban đầu theo trục Y
     * @param vz    vận tốc ban đầu theo trục Z
     */
    public ThrownEggEntity(World world, LivingEntity owner, float x, float y, float z,
                           float vx, float vy, float vz) {
        this((Entity) owner, x, y, z, vx, vy, vz);
    }

    /**
     * Tạo quả trứng ném với chữ ký đồng nhất cùng {@link ArrowEntity} và {@link ThrownPotionEntity}.
     *
     * @param owner thực thể ném trứng
     * @param x     tọa độ xuất phát X
     * @param y     tọa độ xuất phát Y
     * @param z     tọa độ xuất phát Z
     * @param vx    vận tốc ban đầu theo trục X
     * @param vy    vận tốc ban đầu theo trục Y
     * @param vz    vận tốc ban đầu theo trục Z
     */
    public ThrownEggEntity(Entity owner, float x, float y, float z, float vx, float vy, float vz) {
        super(owner, x, y, z, vx, vy, vz, RADIUS, GRAVITY, LIFETIME, MobAppearance.POTION);
    }

    /**
     * Tính số lượng gà con nở ra từ một giá trị tung xúc xắc nguyên theo đúng xác suất Minecraft:
     * <ul>
     *   <li>{@code roll % 8 != 0}: không nở ({@code 0} con, 224/256 = {@code 7/8}).</li>
     *   <li>{@code roll % 8 == 0} nhưng {@code normalized != 0}: nở {@code 1} con (31/256).</li>
     *   <li>{@code normalized == 0}: nở {@code 4} con (1/256, tức {@code 1/32} trong số các lần nở {@code 1/8}).</li>
     * </ul>
     *
     * @param roll giá trị nguyên bất kỳ (được chuẩn hóa về {@code [0, 255]})
     * @return số gà con nở ra ({@code 0}, {@code 1} hoặc {@code 4})
     */
    public static int rollChickCount(int roll) {
        int normalized = Math.floorMod(roll, ROLL_RANGE);
        if (normalized % 8 != 0) {
            return 0;
        }
        return normalized == 0 ? 4 : 1;
    }

    /** Cho phép kiểm thử hồi quy ấn định giá trị tung xúc xắc xác định trước. */
    public void setHatchRollForTest(int roll) {
        forcedRollForTest = roll;
    }

    /**
     * Khi trứng chạm khối hoặc thực thể, tung xác suất nở gà con tại vị trí vỡ.
     */
    @Override
    protected void onImpact(World world, LivingEntity target) {
        if (world == null) {
            return;
        }
        int roll = forcedRollForTest >= 0 ? forcedRollForTest : ThreadLocalRandom.current().nextInt(ROLL_RANGE);
        int chickCount = rollChickCount(roll);
        for (int i = 0; i < chickCount; i++) {
            Chicken chick = new Chicken(position.x, position.y, position.z);
            chick.setBaby(true);
            world.addEntity(chick);
        }
    }
}
