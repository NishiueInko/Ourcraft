package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.World;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

/** Kẻ săn im lặng có mồi nổ, hiệu ứng phồng và vụ nổ ảnh hưởng địa hình. */
public final class Creeper extends MobEntity {
    private static final float WALK_SPEED = 2.5f;
    private static final float DETECT_RANGE_SQUARED = 16f * 16f;
    private static final float PRIME_RANGE_SQUARED = 3f * 3f;
    private static final float CANCEL_RANGE_SQUARED = 7f * 7f;
    private static final double FUSE_SECONDS = 1.5d;
    private static final float BLAST_RADIUS = 3f;
    private static final float BLAST_DAMAGE = 43f;

    /** Tái sử dụng vị trí đích và giữ đúng mục tiêu đã kích hoạt từng lượt mồi. */
    private final Vector3 targetCenter = new Vector3();
    private LivingEntity fuseTarget;
    private double fuseSeconds;

    /** Tạo Creeper dùng các goal nghỉ và bơi chung, với quy trình tấn công riêng. */
    public Creeper(float x, float y, float z) {
        super(MobType.CREEPER, x, y, z, 0f, 0, false);
        setMoveSpeed(WALK_SPEED);
        MobGoals.addDefaults(goals(), type(), false);
    }

    /** Tiếp cận mục tiêu rồi dừng lại cho tới khi mồi cháy hết hoặc mục tiêu thoát. */
    @Override
    protected void updateGoals(World world, float dt) {
        if (fuseTarget != null) {
            if (distanceSquared(fuseTarget) <= CANCEL_RANGE_SQUARED) {
                tickFuse(world, dt);
                return;
            }
            fuseTarget = null;
            fuseSeconds = 0d;
            mobVisualState().setFuseProgress(0f);
        }
        LivingEntity target = target();
        float distance = distanceSquared(target);
        if (distance > DETECT_RANGE_SQUARED) {
            Player player = world.getPlayer();
            distance = distanceSquared(player);
            target = distance <= DETECT_RANGE_SQUARED ? player : null;
            setTarget(target);
        }
        if (target == null) {
            super.updateGoals(world, dt);
            return;
        }
        goals().stopAll(world, this);
        if (distance <= PRIME_RANGE_SQUARED) {
            fuseTarget = target;
            playSound(world, MobSoundEvent.CREEPER_PRIMED);
            tickFuse(world, dt);
        } else {
            lookAt(targetCenter.x, targetCenter.y, targetCenter.z);
            moveToward(world, targetCenter.x, targetCenter.y - target.getHeight() * 0.5f, targetCenter.z, 1f);
        }
    }

    /** Giữ tư thế tại chỗ, cập nhật tiến độ vẽ rồi thực hiện và rời thế giới ngay khi nổ. */
    private void tickFuse(World world, float dt) {
        stopMoving();
        lookAt(targetCenter.x, targetCenter.y, targetCenter.z);
        fuseSeconds += dt;
        mobVisualState().setFuseProgress((float) (fuseSeconds / FUSE_SECONDS));
        if (fuseSeconds >= FUSE_SECONDS) {
            playSound(world, MobSoundEvent.EXPLODE);
            world.explode(this, x(), y() + getHeight() * 0.5f, z(), BLAST_RADIUS, BLAST_DAMAGE);
            discard();
        }
    }

    /** Dùng chung điểm tâm cho khoảng cách kích nổ và hướng quan sát. */
    private float distanceSquared(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive()) {
            return Float.POSITIVE_INFINITY;
        }
        candidate.copyCenterPosition(targetCenter);
        return targetCenter.dst2(x(), y() + getHeight() * 0.5f, z());
    }

    /** Creeper chỉ bỏ tiếng chờ, còn tiếng bước chân phụ thuộc khối dưới chân. */
    @Override
    protected void playSound(World world, MobSoundEvent event) {
        if (event != MobSoundEvent.AMBIENT) {
            super.playSound(world, event);
        }
    }
}
