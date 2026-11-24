package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobSpawner;
import com.voxelgame.engine.Raycast;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.LivingEntity;

/** Nhện đổi thái độ theo ánh sáng, vồ từ xa và bám vách để truy đuổi. */
public final class Spider extends MobEntity {
    private static final float WALK_SPEED = 3f;
    private static final float TARGET_RANGE = 16f;
    private static final float LEAP_MIN_DISTANCE_SQUARED = 2f * 2f;
    private static final float LEAP_MAX_DISTANCE_SQUARED = 4f * 4f;
    private static final float LEAP_HORIZONTAL_SPEED = 5.1f;
    private static final float LEAP_VERTICAL_SPEED = 6.7f;
    private static final float LEAP_RECOVERY_SECONDS = 1.4f;

    /** Các bộ đệm tia và vị trí đích tồn tại cùng cá thể, dùng lại cho mỗi lần cập nhật. */
    private final Vector3 targetCenter = new Vector3();
    private final Vector3 wallRayOrigin = new Vector3();
    private final Vector3 wallRayDirection = new Vector3();
    private final Raycast.HitResult wallHit = Raycast.HitResult.miss();
    private boolean dark;
    private double nextLeap;

    /** Dựng nhện với một ghế cho Spider Jockey và bộ cận chiến theo chỉ số loài. */
    public Spider(float x, float y, float z) {
        super(MobType.SPIDER, x, y, z, 0f, 1, false);
        setMoveSpeed(WALK_SPEED);
        MobGoals.addDefaults(goals(), type(), false);
        goals().add(new MobGoals.MeleeAttackGoal(4, 1f, 2f, 0.85f, 1f, TARGET_RANGE));
    }

    /** Thái độ nền là trung lập ngoài trời và thù địch dưới mái hoặc khi trời tối. */
    @Override
    public MobAttitude attitude() {
        return dark ? MobAttitude.HOSTILE : MobAttitude.NEUTRAL;
    }

    /** Chọn mục tiêu theo ánh sáng rồi bổ sung vồ và leo vào đường di chuyển chung. */
    @Override
    protected void updateGoals(World world, float dt) {
        dark = MobSpawner.isNightDark(world.worldTime())
                || world.hasWeatherRoof(MathUtils.floor(x()), y() + getHeight(), MathUtils.floor(z()));
        LivingEntity current = target();
        if (!dark && angerTimer() <= 0f) {
            current = null;
        } else if (!inRange(current) && dark) {
            LivingEntity player = world.getPlayer();
            current = inRange(player) ? player : null;
        }
        setTarget(current);
        super.updateGoals(world, dt);
        if (!inWater() && inRange(current)) {
            attackMovement(world);
        }
    }

    /** Khi vách ở sát hướng đuổi thì đưa nhện tới tiếp xúc, nếu thông thì thử vồ. */
    private void attackMovement(World world) {
        float dx = targetCenter.x - x();
        float dy = targetCenter.y - (y() + getHeight() * 0.5f);
        float dz = targetCenter.z - z();
        float horizontal = dx * dx + dz * dz;
        if (!(horizontal > 0.001f)) {
            return;
        }
        float distanceSquared = horizontal + dy * dy;
        if (hasWallAhead(world, dx, dz)) {
            steerTowardWall(targetCenter.x, targetCenter.z);
        } else if (onGround && ageSeconds() >= nextLeap && distanceSquared >= LEAP_MIN_DISTANCE_SQUARED
                && distanceSquared <= LEAP_MAX_DISTANCE_SQUARED) {
            float speed = LEAP_HORIZONTAL_SPEED / (float) Math.sqrt(horizontal);
            nextLeap = ageSeconds() + LEAP_RECOVERY_SECONDS;
            queueLeap(dx * speed, LEAP_VERTICAL_SPEED, dz * speed);
        }
    }

    /** Dò ngay trước mép thân để xác minh đường đi thực sự chạm một vách rắn. */
    private boolean hasWallAhead(World world, float dx, float dz) {
        wallRayOrigin.set(x(), y() + getHeight() * 0.55f, z());
        wallRayDirection.set(dx, 0f, dz);
        return Raycast.castOccluder(world, wallRayOrigin, wallRayDirection, getHalfWidth() + 0.55f, wallHit).isHit();
    }

    /** Hộp quét đã xác nhận nhện ép vào mặt bên, chỉ leo mặt rắn an toàn. */
    @Override
    protected boolean climbWall(World world, Block wall) {
        return !inWater() && wall.isSolid() && wall != Block.CACTUS;
    }

    /** Tính khoảng cách tâm ba chiều và ghi đích nhìn vào bộ đệm khi đối thủ còn sống. */
    private boolean inRange(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive()) {
            return false;
        }
        candidate.copyCenterPosition(targetCenter);
        return targetCenter.dst2(x(), y() + getHeight() * 0.5f, z()) <= TARGET_RANGE * TARGET_RANGE;
    }
}
