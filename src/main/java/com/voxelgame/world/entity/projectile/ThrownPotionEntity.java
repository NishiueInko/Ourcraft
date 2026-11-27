package com.voxelgame.world.entity.projectile;

import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.level.World;

import java.util.Objects;

/** Lọ thuốc bay theo parabol và tạo vùng ảnh hưởng ngay khi vỡ. */
public final class ThrownPotionEntity extends ProjectileEntity {
    /** Gia tốc rơi dương để bộ ném tính độ nâng parabol. */
    public static final float GRAVITY = 14f;
    /** Bán kính chạm đích, khoảng ảnh hưởng và tuổi thọ khi vẫn đang bay. */
    private static final float RADIUS = 0.12f;
    private static final float SPLASH_RADIUS = 4f;
    private static final float SPLASH_RADIUS_SQUARED = SPLASH_RADIUS * SPLASH_RADIUS;
    private static final float LIFETIME = 6f;

    private final PotionType type;

    /** Tạo lọ đang bay với dung dịch cố định cho lần vỡ duy nhất. */
    public ThrownPotionEntity(Entity owner, float x, float y, float z, float vx, float vy, float vz,
                              PotionType type) {
        super(owner, x, y, z, vx, vy, vz, RADIUS, GRAVITY, LIFETIME, MobAppearance.POTION);
        this.type = Objects.requireNonNull(type);
    }

    /** Quét mục tiêu trong hình cầu tại điểm vỡ, giữ toàn lực cho lần trúng trực tiếp. */
    @Override
    protected void onImpact(World world, LivingEntity target) {
        for (int i = 0; i < world.entityCount(); i++) {
            if (!(world.entityAt(i) instanceof LivingEntity nearby) || !canAffect(nearby)) {
                continue;
            }
            if (nearby == target) {
                type.apply(world, nearby, owner(), 1f);
                continue;
            }
            float distanceSquared = squaredDistanceTo(nearby);
            if (distanceSquared < SPLASH_RADIUS_SQUARED) {
                float strength = 1f - (float) Math.sqrt(distanceSquared) / SPLASH_RADIUS;
                type.apply(world, nearby, owner(), strength);
            }
        }
    }
}
