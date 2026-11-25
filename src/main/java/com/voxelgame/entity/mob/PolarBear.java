package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Gấu Bắc Cực bảo vệ gấu con, trả đũa theo nhóm và bơi với tốc độ đầy đủ. */
public final class PolarBear extends MobEntity {
    /** Khoảng cách bảo vệ bầy và khoảng cách người chơi làm gấu con gặp nguy hiểm. */
    private static final float FAMILY_RANGE_SQUARED = 16f * 16f;
    private static final float CUB_THREAT_SQUARED = 4f * 4f;
    /** Thời gian cảnh báo bằng tư thế dựng hai chân, và thời gian trả đũa. */
    private static final float REAR_SECONDS = 0.85f;
    private static final float ANGER_SECONDS = 20f;
    /** Đệm vị trí dùng lại trong các lượt kiểm tra và quan sát. */
    private final Vector3 nearby = new Vector3();
    private final Vector3 playerCenter = new Vector3();
    private float rearRemaining;
    private float fleeRemaining;
    private float familyDelay;
    private float foxDelay = ThreadLocalRandom.current().nextFloat(7f, 18f);

    /** Tạo gấu trưởng thành thông qua danh mục loài. */
    public PolarBear(float x, float y, float z) {
        this(x, y, z, false);
    }

    /** Khởi tạo gấu con với chiều rộng và chiều cao bằng nửa gấu trưởng thành. */
    public PolarBear(float x, float y, float z, boolean baby) {
        super(MobType.POLAR_BEAR, x, y, z, 0f, 0, false, baby ? 0.5f : 1f);
        setBaby(baby);
        mobVisualState().setScale(baby ? 0.5f : 1f);
        setMoveSpeed(2.5f);
        MobGoals.addDefaults(goals(), type(), false);
        if (baby) {
            goals().add(new MobGoals.FleeEntityGoal(2, 16f, 1.45f,
                    other -> fleeRemaining > 0f && other instanceof Player));
        } else {
            goals().add(new MobGoals.MeleeAttackGoal(4, 1.4f, 6f, 1.65f, 1.15f, 20f));
        }
    }

    /** Gấu con chỉ tìm lối chạy, còn gấu trưởng thành cảnh báo trước khi tiếp cận. */
    @Override
    protected void updateGoals(World world, float dt) {
        if (isBaby()) {
            fleeRemaining = Math.max(0f, fleeRemaining - dt);
            setTarget(null);
            super.updateGoals(world, dt);
            return;
        }

        familyDelay -= dt;
        Player player = world.getPlayer();
        if (familyDelay <= 0f) {
            familyDelay = 0.25f;
            if (player != null && player.isAlive() && target() != player && cubInDanger(world, player)) {
                protectFamily(world, player);
            }
        }
        foxDelay -= dt;
        if (foxDelay <= 0f && target() == null) {
            foxDelay = ThreadLocalRandom.current().nextFloat(7f, 18f);
            lookForFox(world);
        }
        if (rearRemaining > 0f && target() != null && !inWater()) {
            goals().stopAll(world, this);
            stopMoving();
            target().copyCenterPosition(nearby);
            lookAt(nearby.x, nearby.y, nearby.z);
            faceBody(nearby.x, nearby.z);
            mobVisualState().setPose(MobVisualState.Pose.REAR, Math.min(1f, (REAR_SECONDS - rearRemaining) * 8f + 0.2f));
            rearRemaining = Math.max(0f, rearRemaining - dt);
            return;
        }
        rearRemaining = 0f;
        mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        super.updateGoals(world, dt);
    }

    /** Bảo toàn tốc độ bơi của gấu so với tốc độ điều khiển trên cạn. */
    @Override
    protected float waterSpeedMultiplier() {
        return 1f;
    }

    /** Tìm gấu con ở gần cả gấu trưởng thành lẫn người chơi. */
    private boolean cubInDanger(World world, Player player) {
        player.copyCenterPosition(playerCenter);
        for (int index = 0, count = world.entityCount(); index < count; index++) {
            if (world.entityAt(index) instanceof PolarBear cub && cub.isBaby() && cub.isAlive()
                    && distanceSquared(cub) <= FAMILY_RANGE_SQUARED && nearby.dst2(playerCenter) <= CUB_THREAT_SQUARED) {
                return true;
            }
        }
        return false;
    }

    /** Thỉnh thoảng săn một con cáo tuyết đi ngang qua gần gấu. */
    private void lookForFox(World world) {
        for (int index = 0, count = world.entityCount(); index < count; index++) {
            if (world.entityAt(index) instanceof MobEntity fox && fox.type() == MobType.SNOW_FOX
                    && fox.isAlive() && distanceSquared(fox) <= 36f) {
                enrage(fox, 6f);
                return;
            }
        }
    }

    /** Báo động cho gấu trưởng thành và gấu con trong vùng khi người chơi đánh một con gấu. */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        boolean applied = super.damage(world, amount, source, attacker, knockbackX, knockbackZ);
        if (applied && world != null && attacker instanceof Player player && player.isAlive()) {
            if (isAlive()) {
                protectFamily(world, player);
            }
            for (int index = 0, count = world.entityCount(); index < count; index++) {
                if (world.entityAt(index) instanceof PolarBear bear && bear != this && bear.isAlive()
                        && distanceSquared(bear) <= FAMILY_RANGE_SQUARED) {
                    bear.protectFamily(world, player);
                }
            }
        }
        return applied;
    }

    /** Kích hoạt cảnh báo hai chân ở gấu trưởng thành hoặc bỏ chạy ở gấu con. */
    private void protectFamily(World world, Player player) {
        if (isBaby()) {
            setTarget(null);
            fleeRemaining = 7f;
        } else {
            enrage(player, ANGER_SECONDS);
            if (rearRemaining == 0f && !inWater()) {
                rearRemaining = REAR_SECONDS;
                playSound(world, MobSoundEvent.AMBIENT);
            }
        }
    }

    /** Chép tâm vào bộ đệm và trả bình phương khoảng cách tới gấu. */
    private float distanceSquared(LivingEntity other) {
        return other.copyCenterPosition(nearby).dst2(x(), y() + height * 0.5f, z());
    }
}
