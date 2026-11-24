package com.voxelgame.entity.projectile;

import com.voxelgame.engine.World;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.StatusEffectType;

/** Tác động của dung dịch văng từ lọ thuốc. */
public enum PotionType {
    SLOWNESS(StatusEffectType.SLOWNESS, 90f),
    POISON(StatusEffectType.POISON, 45f),
    WEAKNESS(StatusEffectType.WEAKNESS, 90f),
    HARMING(null, 0f),
    HEALING(null, 0f);

    /** Mức HP tức thời trước khi nhân theo suy giảm bán kính. */
    private static final float MAGIC_DAMAGE = 6f;
    private static final float HEAL_AMOUNT = 4f;
    private final StatusEffectType effect;
    private final float duration;

    /** Lưu trạng thái và số giây từ loại thuốc có thời lượng. */
    PotionType(StatusEffectType effect, float duration) {
        this.effect = effect;
        this.duration = duration;
    }

    /** Áp dụng hiệu ứng cấp I hoặc đảo chiều hồi máu và sát thương cho undead. */
    void apply(World world, LivingEntity target, Entity owner, float strength) {
        if (effect != null) {
            target.applyEffect(effect, 0, duration);
        } else if (target.isUndead() == (this == HEALING)) {
            target.damage(world, MAGIC_DAMAGE * strength, DamageSource.POTION_MAGIC, owner, 0f, 0f);
        } else {
            target.heal(HEAL_AMOUNT * strength);
        }
    }
}
