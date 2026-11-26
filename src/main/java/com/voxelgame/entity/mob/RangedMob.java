package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.Raycast;
import com.voxelgame.engine.World;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

/** Dùng chung điểm ngắm, kiểm tra vật cản, giương vũ khí và hồi chiêu cho mob đánh xa. */
abstract class RangedMob extends MobEntity {
    /** Bán kính giữ mục tiêu cũ rộng hơn vùng phát hiện để tránh mất mục tiêu ở ngay mép. */
    private static final float RETAIN_DISTANCE = 2f;
    /** Thời gian bay ngắn nhất khi hai thực thể gần như cùng một cột. */
    private static final float MIN_FLIGHT_TIME = 0.075f;

    private final float detectionRangeSquared;
    private final float retentionRangeSquared;
    private final float attackRangeSquared;
    private final float preparationSeconds;
    private final float shotSpeed;
    private final float shotGravity;

    /** Bộ đệm phục vụ giữ mục tiêu, thử tia và tính vận tốc đạn. */
    private final Vector3 aimPoint = new Vector3();
    private final Vector3 shotOrigin = new Vector3();
    private final Vector3 displacement = new Vector3();
    private final Raycast.HitResult occluder = Raycast.HitResult.miss();

    /** Hồi chiêu không bị xóa khi đổi mục tiêu hoặc chuyển giữa đứng và cưỡi. */
    private double shotReadyAt;
    private double chargeSince;
    private LivingEntity chargingTarget;

    /** Lắp các goal nền, bỏ cận chiến và dùng các giới hạn riêng của vũ khí. */
    RangedMob(MobType type, float x, float y, float z, float detectionRange, float attackRange,
              float preparationSeconds, float shotSpeed, float shotGravity) {
        super(type, x, y, z, 0f, 0, false);
        detectionRangeSquared = detectionRange * detectionRange;
        float retentionRange = detectionRange + RETAIN_DISTANCE;
        retentionRangeSquared = retentionRange * retentionRange;
        attackRangeSquared = attackRange * attackRange;
        this.preparationSeconds = preparationSeconds;
        this.shotSpeed = shotSpeed;
        this.shotGravity = shotGravity;
        setMoveSpeed(2.5f);
        MobGoals.addDefaults(goals(), type, false);
        goals().add(new RangedAttackGoal());
    }

    /** Chọn mục tiêu một lần trước khi bộ chọn phân quyền cho tháo chạy, phòng vệ hoặc bắn. */
    @Override
    protected final void updateGoals(World world, float dt) {
        refreshTarget(world);
        super.updateGoals(world, dt);
    }

    /** Khi đang cưỡi, tiếp tục ngắm và bắn; ghế vẫn quyết định toàn bộ vị trí của cá thể. */
    @Override
    protected final void updateMounted(World world, float dt) {
        refreshTarget(world);
        LivingEntity target = target();
        if (target != null) {
            updateAttack(world, target, true);
        } else {
            clearAim();
        }
    }

    /** Giữ mục tiêu trả đũa đã được gán và chỉ tự tìm người chơi khi chưa có mục tiêu. */
    private void refreshTarget(World world) {
        LivingEntity activeTarget = target();
        if (activeTarget != null && activeTarget.isAlive()
                && distanceSquared(activeTarget) <= retentionRangeSquared) {
            return;
        }
        setTarget(null);
        Player player = world.getPlayer();
        if (player != null && player.isAlive()
                && distanceSquared(player) <= detectionRangeSquared && lineClear(world)) {
            setTarget(player);
        }
    }

    /** Trả bình phương khoảng cách từ tâm mob đến mục tiêu và giữ tọa độ ngắm. */
    final float distanceSquared(LivingEntity target) {
        target.copyCenterPosition(aimPoint);
        float dx = aimPoint.x - x();
        float dy = aimPoint.y - (y() + getHeight() * 0.5f);
        float dz = aimPoint.z - z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Kiểm tra đá hoặc khối rắn khác chắn giữa vị trí phóng và tâm mục tiêu. */
    private boolean lineClear(World world) {
        shotOrigin.set(x(), y() + getHeight() * 0.78f, z());
        displacement.set(aimPoint).sub(shotOrigin);
        float distance = displacement.len();
        return distance <= 0.0001f
                || !Raycast.castOccluder(world, shotOrigin, displacement, distance, occluder).isHit();
    }

    /** Bám chân mục tiêu, giữ tư thế ngắm, và tạo đạn sau khi đủ thời gian chuẩn bị. */
    private void updateAttack(World world, LivingEntity target, boolean mounted) {
        float distanceSquared = distanceSquared(target);
        boolean canShoot = distanceSquared <= attackRangeSquared && lineClear(world);
        float targetFeetY = aimPoint.y - target.getHeight() * 0.5f;
        lookAt(aimPoint.x, targetFeetY + target.getHeight() * 0.8f, aimPoint.z);
        if (canShoot) {
            faceBody(aimPoint.x, aimPoint.z);
        }
        if (!mounted) {
            if (canShoot) {
                moveWhileAiming(world, aimPoint.x, aimPoint.z);
            } else {
                moveToward(world, aimPoint.x, targetFeetY, aimPoint.z, 1f);
            }
        }
        if (!canShoot) {
            clearAim();
            return;
        }
        double now = ageSeconds();
        if (chargingTarget != target) {
            chargingTarget = target;
            chargeSince = now;
        }
        float aimProgress = Math.max(0f, Math.min(1f, (float) (now - chargeSince) / preparationSeconds));
        if (now >= shotReadyAt && aimProgress >= 1f) {
            launch(world, target, (float) Math.sqrt(distanceSquared));
            shotReadyAt = now + intervalSeconds(distanceSquared);
            // Cung nhả dây ngay khi phóng tên, rồi bắt đầu kéo đủ 20 tick trước phát kế.
            if (this instanceof Skeleton) {
                chargeSince = Math.max(now, shotReadyAt - preparationSeconds);
                aimProgress = 0f;
            } else {
                chargeSince = now;
            }
        }
        applyAimPose(aimProgress);
    }

    /** Tính thời gian bay theo khoảng cách ngang và bù độ rơi của đạn trong đúng khoảng đó. */
    private void launch(World world, LivingEntity target, float distance) {
        float horizontalDistance = (float) Math.sqrt(displacement.x * displacement.x + displacement.z * displacement.z);
        float flightTime = Math.max(MIN_FLIGHT_TIME, horizontalDistance / shotSpeed);
        float vx = displacement.x / flightTime;
        float vy = displacement.y / flightTime + shotGravity * flightTime * 0.5f;
        float vz = displacement.z / flightTime;
        fire(world, target, distance, shotOrigin.x, shotOrigin.y, shotOrigin.z, vx, vy, vz);
    }

    /** Hạ vũ khí khi mất tầm ngắm hoặc bị một hành vi ưu tiên cao hơn ngắt. */
    private void clearAim() {
        chargingTarget = null;
        mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
    }

    /** Tùy chỉnh cách đứng hoặc đi trong lúc ngắm; mặc định dừng tại chỗ. */
    void moveWhileAiming(World world, float targetX, float targetZ) {
        stopMoving();
    }

    /** Áp tư thế tay cho từng kiểu đánh xa. Tiến độ bằng không khi chưa giương vũ khí. */
    abstract void applyAimPose(float progress);

    /** Trả khoảng nghỉ giữa hai phát đạn, có thể phụ thuộc độ xa của đích. */
    abstract float intervalSeconds(float distanceSquared);

    /** Dựng loại đạn cụ thể và phát âm thanh lúc tung đòn. */
    abstract void fire(World world, LivingEntity target, float distance,
                       float x, float y, float z, float vx, float vy, float vz);

    /** Giữ riêng kênh di chuyển và nhìn khi bám mục tiêu tấn công. */
    private final class RangedAttackGoal implements MobGoal {
        /** Tháo chạy và tìm nơi trú được giành quyền trước đòn đánh xa. */
        @Override
        public int priority() { return 4; }

        /** Đường chạy và hướng nhìn phải do cùng một mục tiêu quyết định. */
        @Override
        public int controls() { return MOVE | LOOK; }

        /** Vào hành vi khi đã thấy người chơi hoặc được gán mục tiêu trả đũa. */
        @Override
        public boolean canStart(World world, MobEntity mob) { return target() != null; }

        /** Giữ tấn công khi mục tiêu chưa chết và chưa vượt tầm bám. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) { return canStart(world, mob); }

        /** Tiến độ giương bắt đầu ở lần kiểm tra tầm bắn đầu tiên. */
        @Override
        public void start(World world, MobEntity mob) {
        }

        /** Bám hoặc bắn vào đích hiện tại theo điều kiện vật cản. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            LivingEntity target = target();
            if (target != null) {
                updateAttack(world, target, false);
            }
        }

        /** Bỏ chuyển động ngang và giương vũ khí khi hành vi khác tiếp quản. */
        @Override
        public void stop(World world, MobEntity mob) {
            stopMoving();
            clearAim();
        }
    }
}
