package com.voxelgame.world.entity.projectile;

import com.voxelgame.world.effect.StatusEffectType;
import com.voxelgame.world.entity.LivingEntity;

/** Đầu tên và hiệu ứng có thể gắn trên một hit thành công. */
public enum ArrowType {
    NORMAL(null), SLOWNESS(StatusEffectType.SLOWNESS), WEAKNESS(StatusEffectType.WEAKNESS);

    /** Đầu tên có hiệu ứng dùng cùng thời lượng và cấp I. */
    private static final float EFFECT_SECONDS = 30f;
    private final StatusEffectType effect;

    /** Khai báo trạng thái của mẫu tên, null dành cho tên thường. */
    ArrowType(StatusEffectType effect) {
        this.effect = effect;
    }

    /** Gắn hiệu ứng qua luật miễn nhiễm của LivingEntity. */
    void apply(LivingEntity target) {
        if (effect != null) {
            target.applyEffect(effect, 0, EFFECT_SECONDS);
        }
    }
}
