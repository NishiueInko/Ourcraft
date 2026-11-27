package com.voxelgame.world.entity.mob.animal;

import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.client.render.entity.MobVisualState;
import com.voxelgame.world.entity.mob.ai.MobGoals;
import com.voxelgame.world.entity.mob.MobAttitude;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.mob.PassiveMob;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import java.util.concurrent.ThreadLocalRandom;

/** Thỏ sa mạc và thỏ tuyết dùng chung cách di chuyển nhảy cóc và các phản ứng tháo chạy. */
public final class Rabbit extends PassiveMob {
    /** Xác suất lông trắng ở vùng tuyết, còn lại dùng lông trắng có đốm. */
    private static final float SNOW_WHITE_PROBABILITY = 0.8f;
    /** Vận tốc đứng đầu nhịp nhảy cho độ cao gần hai block với trọng lực của mob. */
    private static final float HOP_UP_SPEED = 10.3f;
    /** Giữ vận tốc ngang cho cả vòng cung và nghỉ ngắn giữa hai lần bật. */
    private static final float HOP_FLIGHT_SECONDS = 0.80f;
    private static final float HOP_INTERVAL_SECONDS = 0.87f;
    private double nextHop;
    private double hoppingUntil;

    /** Dựng thỏ vàng cát khi factory sa mạc được gọi. */
    public Rabbit(float x, float y, float z) {
        this(MobType.RABBIT, x, y, z);
    }

    /** Dựng thỏ tuyết, chọn ngẫu nhiên màu tại thời điểm cá thể được tạo. */
    public static Rabbit snow(float x, float y, float z) {
        return new Rabbit(MobType.SNOW_RABBIT, x, y, z);
    }

    /** Ánh xạ kết quả bốc trong [0,1) sang biến thể thích hợp với nơi sinh. */
    public static MobAppearance appearanceFor(MobType type, float roll) {
        if (type == MobType.RABBIT) {
            return MobAppearance.RABBIT_GOLD;
        }
        if (type != MobType.SNOW_RABBIT) {
            throw new IllegalArgumentException("Cấu hình màu phải thuộc loài thỏ");
        }
        return roll < SNOW_WHITE_PROBABILITY ? MobAppearance.RABBIT_WHITE : MobAppearance.RABBIT_WHITE_SPLOTCHED;
    }

    /** Lắp ba bán kính tránh riêng và cho bồ công anh thay đổi cách thỏ nhận diện người chơi. */
    private Rabbit(MobType type, float x, float y, float z) {
        super(type, x, y, z, 0f, 0, false);
        mobVisualState().setAppearance(appearanceFor(type, ThreadLocalRandom.current().nextFloat()));
        setMoveSpeed(2.7f);
        goals().add(new MobGoals.FloatInWaterGoal(0));
        goals().add(new MobGoals.PanicGoal(1));
        goals().add(new MobGoals.FleeEntityGoal(2, 10f, 1.85f,
                entity -> entity instanceof MobEntity animal && animal.type() == MobType.WOLF));
        goals().add(new MobGoals.FleeEntityGoal(3, 8f, 1.65f,
                entity -> entity instanceof Player player && !holdsTemptItem(player)));
        goals().add(new MobGoals.FleeEntityGoal(4, 4f, 1.65f,
                entity -> entity instanceof MobEntity monster && monster.attitude() == MobAttitude.HOSTILE));
        goals().add(new MobGoals.TemptFollowGoal(5, Rabbit::isTemptItem, 10f, 0.8f));
        goals().add(new MobGoals.WanderGoal(6, 9, 0.65f));
        goals().add(new MobGoals.LookAtPlayerGoal(7, 8f));
        goals().add(new MobGoals.RandomLookAroundGoal(8));
    }

    /** Chỉ vật phẩm còn trong chính ô đang chọn được dùng để dụ thỏ (Carrot hoặc Dandelion). */
    private static boolean holdsTemptItem(Player player) {
        return player.getHotbar().getSelectedCount() > 0 && isTemptItem(player.getHotbar().getSelectedItem());
    }

    /** Nhận diện mồi dụ của thỏ: {@link Item#CARROT} hoặc {@link Block#DANDELION}. */
    private static boolean isTemptItem(Item item) {
        return item == Item.CARROT || item == Item.of(Block.DANDELION);
    }

    /** Đổi hướng đã được điều hướng kiểm tra thành một vòng nhảy, không kéo thỏ đi bộ trên cạn. */
    @Override
    public void steer(float dx, float dz, float speed) {
        if (inWater()) {
            super.steer(dx, dz, speed);
        } else if (speed > 0f && isOnGround() && ageSeconds() >= nextHop) {
            double now = ageSeconds();
            queueLeap(dx * speed, HOP_UP_SPEED, dz * speed, HOP_FLIGHT_SECONDS);
            hoppingUntil = now + HOP_FLIGHT_SECONDS;
            nextHop = now + HOP_INTERVAL_SECONDS;
        }
    }

    /** Cập nhật dáng thu chân theo vận tốc đứng thật và trả pose thường khi đã đáp xuống. */
    @Override
    protected void updateLiving(World world, float dt) {
        super.updateLiving(world, dt);
        if (!isOnGround() && !inWater() && ageSeconds() < hoppingUntil) {
            float tuck = 1f - Math.min(1f, Math.abs(velocity.y) / HOP_UP_SPEED) * 0.6f;
            mobVisualState().setPose(MobVisualState.Pose.HOP, tuck);
        } else {
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }
    }
}
