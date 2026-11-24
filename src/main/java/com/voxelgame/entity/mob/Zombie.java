package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.World;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Quái hình người tấn công cận chiến và gọi Zombie ở gần khi bị đánh. */
public class Zombie extends MobEntity {
    /** Tốc độ người lớn, độ nhỏ của cá thể non và ngưỡng phát hiện. */
    private static final float WALK_SPEED = 2.3f;
    private static final float BABY_SPEED_MULTIPLIER = 1.5f;
    private static final float BABY_DIMENSIONS_SCALE = 0.5f;
    private static final float TARGET_RANGE = 35f;
    private static final float HELP_RANGE_SQUARED = 16f * 16f;
    private static final int BABY_CHANCE_DIVISOR = 20;

    /** Bộ đệm vị trí người chơi dùng lại khi chọn mục tiêu. */
    private final Vector3 candidateCenter = new Vector3();

    /** Tạo cá thể tự nhiên, với xác suất 5% là Zombie con. */
    public Zombie(float x, float y, float z) {
        this(x, y, z, rollsBaby());
    }

    /** Tạo cá thể với độ tuổi đã biết, dùng cho chuyển hóa và kiểm tra hitbox. */
    public Zombie(float x, float y, float z, boolean baby) {
        this(MobType.ZOMBIE, x, y, z, baby);
    }

    /** Dùng chung chỉ số chiến đấu cho Zombie thường, Zombie Villager và Husk. */
    protected Zombie(MobType type, float x, float y, float z, boolean baby) {
        super(type, x, y, z, 2f, 0, false, baby ? BABY_DIMENSIONS_SCALE : 1f);
        setBaby(baby);
        setMoveSpeed(baby ? WALK_SPEED * BABY_SPEED_MULTIPLIER : WALK_SPEED);
        MobGoals.addDefaults(goals(), type, false);
        goals().add(new MobGoals.MeleeAttackGoal(4, 1f, 3f, 0.95f, 1f, TARGET_RANGE));
    }

    /** Chọn độ tuổi đúng một lần trước khi thiết lập kích thước hộp vật lý. */
    protected static boolean rollsBaby() {
        return ThreadLocalRandom.current().nextInt(BABY_CHANCE_DIVISOR) == 0;
    }

    /** Giữ đối thủ còn gần và tìm người chơi trong bán kính phát hiện nếu chưa có đối thủ. */
    @Override
    protected void updateGoals(World world, float dt) {
        if (!inTargetRange(target())) {
            Player player = world.getPlayer();
            setTarget(inTargetRange(player) ? player : null);
        }
        super.updateGoals(world, dt);
    }

    /** Chỉ theo đuổi mục tiêu sống và còn trong tầm cảm nhận của cá thể. */
    private boolean inTargetRange(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive()) {
            return false;
        }
        candidate.copyCenterPosition(candidateCenter);
        return candidateCenter.dst2(x(), y() + getHeight() * 0.5f, z()) <= TARGET_RANGE * TARGET_RANGE;
    }

    /** Báo cho Zombie thường chưa giao chiến quanh mình khi đòn đánh có tác nhân sống. */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        if (!super.damage(world, amount, source, attacker, knockbackX, knockbackZ)) {
            return false;
        }
        if (world != null && attacker instanceof LivingEntity living && living != this && living.isAlive()) {
            for (int i = 0; i < world.entityCount(); i++) {
                if (world.entityAt(i) instanceof Zombie ally && ally != this && ally != living
                        && ally.type() == MobType.ZOMBIE && ally.isAlive()
                        && (ally.target() == null || !ally.target().isAlive()) && isNearbyAlly(ally)) {
                    ally.setTarget(living);
                }
            }
        }
        return true;
    }

    /** So sánh bán kính tiếp viện bằng bình phương khoảng cách chân. */
    private boolean isNearbyAlly(Zombie ally) {
        float dx = ally.x() - x();
        float dy = ally.y() - y();
        float dz = ally.z() - z();
        return dx * dx + dy * dy + dz * dz <= HELP_RANGE_SQUARED;
    }
}
