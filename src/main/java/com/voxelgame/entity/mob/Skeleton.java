package com.voxelgame.entity.mob;

import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.projectile.ArrowEntity;
import com.voxelgame.entity.projectile.ArrowType;

import java.util.concurrent.ThreadLocalRandom;

/** Cung thủ theo sát mục tiêu, đi ngang khi ngắm và lùi khi bị áp sát. */
public class Skeleton extends RangedMob {
    /** Tầm phát hiện và bắn, thời gian giương cung, và tốc độ ngang của mũi tên. */
    private static final float RANGE = 16f;
    private static final float DRAW_SECONDS = 0.55f;
    private static final float ARROW_SPEED = 19f;
    /** Khoảng cách lùi và ngưỡng rút ngắn nhịp nạp tên khi đánh gần. */
    private static final float RETREAT_DISTANCE_SQUARED = 4f * 4f;
    private static final float FAST_SHOT_DISTANCE_SQUARED = 6f * 6f;

    private final ArrowType arrowType;
    private final float minimumDamage;
    private final float shotInterval;
    private float strafeSide = ThreadLocalRandom.current().nextBoolean() ? 1f : -1f;
    private double reverseStrafeAt = 1.35;

    /** Dựng Skeleton thường với tên gây từ ba đến bốn HP, nhịp bắn hai giây. */
    public Skeleton(float x, float y, float z) {
        this(MobType.SKELETON, x, y, z, ArrowType.NORMAL, 3f, 2f);
    }

    /** Cho biến thể thay mũi tên và nhịp bắn, dùng lại toàn bộ hành vi di chuyển và ngắm. */
    protected Skeleton(MobType type, float x, float y, float z, ArrowType arrowType, float minimumDamage,
                       float shotInterval) {
        super(type, x, y, z, RANGE, RANGE, DRAW_SECONDS, ARROW_SPEED, ArrowEntity.GRAVITY);
        this.arrowType = arrowType;
        this.minimumDamage = minimumDamage;
        this.shotInterval = shotInterval;
    }

    /** Lùi thẳng khi đối thủ áp sát, còn ở khoảng bắn thì đổi phía đi ngang theo nhịp. */
    @Override
    void moveWhileAiming(World world, float targetX, float targetZ) {
        float dx = targetX - x();
        float dz = targetZ - z();
        float horizontalSquared = dx * dx + dz * dz;
        if (horizontalSquared < 0.0001f) {
            moveToward(world, x() + strafeSide * 2f, y(), z(), 0.9f);
            return;
        }
        float scale = 2f / (float) Math.sqrt(horizontalSquared);
        if (horizontalSquared < RETREAT_DISTANCE_SQUARED) {
            moveToward(world, x() - dx * scale, y(), z() - dz * scale, 0.9f);
            return;
        }
        if (ageSeconds() >= reverseStrafeAt) {
            strafeSide = -strafeSide;
            reverseStrafeAt = ageSeconds() + 1f + ThreadLocalRandom.current().nextFloat() * 0.65f;
        }
        float step = scale * strafeSide;
        moveToward(world, x() - dz * step, y(), z() + dx * step, 0.75f);
    }

    /** Cung theo hướng đầu trong toàn bộ thời gian đang lên dây. */
    @Override
    void applyAimPose(float progress) {
        mobVisualState().setPose(progress > 0f ? MobVisualState.Pose.AIM_BOW : MobVisualState.Pose.IDLE, progress);
    }

    /** Ở gần, giảm khoảng nghỉ nhưng giữ khác biệt tốc độ giữa các loại cung thủ. */
    @Override
    float intervalSeconds(float distanceSquared) {
        return distanceSquared < FAST_SHOT_DISTANCE_SQUARED ? shotInterval * 0.8f : shotInterval;
    }

    /** Phóng mũi tên của loài, chọn sát thương trong dải một HP và phát tiếng nhả dây. */
    @Override
    void fire(World world, LivingEntity target, float distance,
              float x, float y, float z, float vx, float vy, float vz) {
        float damage = minimumDamage + ThreadLocalRandom.current().nextFloat();
        world.addEntity(new ArrowEntity(this, x, y, z, vx, vy, vz, arrowType, damage));
        playSound(world, MobSoundEvent.BOW_SHOOT);
    }
}
