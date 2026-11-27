package com.voxelgame.world.entity.projectile;

import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.level.World;

import java.util.Objects;

/** Mũi tên trực tiếp gây sát thương vật lý và có thể mang hiệu ứng. */
public final class ArrowEntity extends ProjectileEntity {
    /** Gia tốc rơi dương để bộ bắn tính độ cao ngắm. */
    public static final float GRAVITY = 20f;
    /** Kích thước, thời gian bay tối đa và độ đẩy ngang của tên. */
    private static final float RADIUS = 0.045f;
    private static final float LIFETIME = 8f;
    private static final float KNOCKBACK_SPEED = 1.4f;

    private final ArrowType type;
    private final float damage;

    /** Tạo tên tại điểm bắn và nhận lượng sát thương HP từ cấu hình cung thủ. */
    public ArrowEntity(Entity owner, float x, float y, float z, float vx, float vy, float vz,
                       ArrowType type, float damage) {
        super(owner, x, y, z, vx, vy, vz, RADIUS, GRAVITY, LIFETIME, MobAppearance.ARROW);
        this.type = Objects.requireNonNull(type);
        if (!(damage > 0f) || !Float.isFinite(damage)) {
            throw new IllegalArgumentException("Sát thương tên phải dương và hữu hạn");
        }
        this.damage = damage;
    }

    /** Cho phép né trước khi ghi sát thương, trạng thái hoặc phản ứng đánh trả. */
    @Override
    protected void onImpact(World world, LivingEntity target) {
        if (target == null || target.dodgeProjectile(world, owner())) {
            return;
        }
        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float scale = horizontalSpeed > 0f ? KNOCKBACK_SPEED / horizontalSpeed : 0f;
        if (!target.damage(world, damage, DamageSource.ARROW, owner(), velocity.x * scale, velocity.z * scale)) {
            return;
        }
        type.apply(target);
        if (target.isAlive() && owner() instanceof MobEntity shooter && shooter.isAlive()
                && target instanceof MobEntity victim && shooter.type().category() == MobCategory.MONSTER
                && victim.type().category() == MobCategory.MONSTER) {
            shooter.retaliateAgainst(victim);
            victim.retaliateAgainst(shooter);
        }
    }
}
