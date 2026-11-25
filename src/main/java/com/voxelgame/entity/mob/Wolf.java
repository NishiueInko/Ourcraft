package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobAppearance;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

/** Sói tuyết hoang dã săn mồi và bảo vệ đàn khi bị người chơi tấn công. */
public final class Wolf extends MobEntity {
    /** Phạm vi gọi đàn và tìm mồi, dùng khoảng cách ba chiều. */
    private static final float PACK_RANGE_SQUARED = 16f * 16f;
    /** Dùng cùng giới hạn cho đòn cận chiến và việc giữ con mồi đã bỏ chạy. */
    private static final float PURSUIT_RANGE = 20f;
    /** Thời gian giữ mục tiêu trả đũa trước khi săn mồi trở lại. */
    private static final float ANGER_SECONDS = 30f;
    /** Đệm vị trí dùng lại trong lượt ngắm và tìm mồi. */
    private final Vector3 nearby = new Vector3();
    private float huntDelay;
    private float shakeRemaining;
    private boolean wasWet;

    /** Tạo sói tuyết trưởng thành, dùng 4 HP mỗi cú cắn. */
    public Wolf(float x, float y, float z) {
        super(MobType.WOLF, x, y, z, 0f, 0, false);
        setMoveSpeed(3f);
        MobGoals.addDefaults(goals(), type(), false);
        goals().add(new MobGoals.MeleeAttackGoal(4, 1.35f, 4f, 1.55f, 1f, PURSUIT_RANGE));
    }

    /** Tìm mồi theo nhịp riêng và ưu tiên trả đũa hơn chu kỳ săn. */
    @Override
    protected void updateGoals(World world, float dt) {
        huntDelay -= dt;
        LivingEntity prey = target();
        if (prey != null && !(prey instanceof Player) && distanceSquared(prey) > PURSUIT_RANGE * PURSUIT_RANGE) {
            setTarget(null);
        }
        if (target() == null && huntDelay <= 0f) {
            huntDelay = 0.5f;
            setTarget(findPrey(world));
        }
        super.updateGoals(world, dt);

        if (inWater()) {
            wasWet = true;
            shakeRemaining = 0f;
        } else if (wasWet && onGround) {
            wasWet = false;
            shakeRemaining = 0.85f;
        }
        updateAppearance();
        Player player = world.getPlayer();
        if (shakeRemaining > 0f) {
            stopMoving();
            shakeRemaining = Math.max(0f, shakeRemaining - dt);
            mobVisualState().setPose(MobVisualState.Pose.SHAKE, shakeRemaining / 0.85f);
        } else if (target() == null && player != null && player.isAlive() && distanceSquared(player) <= 25f) {
            lookAt(nearby.x, nearby.y, nearby.z);
            mobVisualState().setPose(MobVisualState.Pose.TILT_HEAD, 1f);
        } else {
            mobVisualState().setPose(target() instanceof Player ? MobVisualState.Pose.ANGRY : MobVisualState.Pose.IDLE,
                    target() instanceof Player ? 1f : 0f);
        }
    }

    /** Chọn cá thể sống gần nhất trong các loài sói tuyết săn. */
    private MobEntity findPrey(World world) {
        MobEntity nearest = null;
        float best = PACK_RANGE_SQUARED;
        for (int index = 0, count = world.entityCount(); index < count; index++) {
            if (!(world.entityAt(index) instanceof MobEntity mob) || !mob.isAlive()) {
                continue;
            }
            boolean prey = switch (mob.type()) {
                case RABBIT, SNOW_RABBIT, SNOW_FOX, SHEEP, SKELETON, STRAY -> true;
                default -> false;
            };
            if (prey) {
                float distance = distanceSquared(mob);
                if (distance <= best) {
                    nearest = mob;
                    best = distance;
                }
            }
        }
        return nearest;
    }

    /** Bị người chơi đánh thành công sẽ phát tiếng gầm và gọi mọi sói sống trong 16 block. */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        boolean applied = super.damage(world, amount, source, attacker, knockbackX, knockbackZ);
        if (applied && world != null && attacker instanceof Player player && player.isAlive()) {
            if (isAlive()) {
                defendPack(world, player);
            }
            for (int index = 0, count = world.entityCount(); index < count; index++) {
                if (world.entityAt(index) instanceof Wolf wolf && wolf != this && wolf.isAlive()
                        && distanceSquared(wolf) <= PACK_RANGE_SQUARED) {
                    wolf.defendPack(world, player);
                }
            }
        }
        return applied;
    }

    /** Cập nhật đồng thời mục tiêu, texture và tiếng gầm khi cả đàn đáp trả. */
    private void defendPack(World world, Player player) {
        if (mobVisualState().appearance() != MobAppearance.WOLF_SNOWY_ANGRY) {
            playSound(world, MobSoundEvent.WOLF_GROWL);
        }
        enrage(player, ANGER_SECONDS);
        updateAppearance();
    }

    /** Chỉ dùng hình mắt đỏ trong lúc đang truy đuổi người chơi. */
    private void updateAppearance() {
        mobVisualState().setAppearance(target() instanceof Player
                ? MobAppearance.WOLF_SNOWY_ANGRY : MobAppearance.WOLF_SNOWY);
    }

    /** Chép tâm vào bộ đệm và trả bình phương khoảng cách. */
    private float distanceSquared(LivingEntity other) {
        return other.copyCenterPosition(nearby).dst2(x(), y() + height * 0.5f, z());
    }
}
