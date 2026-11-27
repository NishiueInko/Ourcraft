package com.voxelgame.world.entity.projectile;

import com.voxelgame.world.effect.StatusEffectType;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.item.Item;

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

    /** Trả về vật phẩm mũi tên tương ứng khi người chơi nhặt lại mũi tên cắm trên mặt đất. */
    public Item toItem() {
        return switch (this) {
            case NORMAL -> Item.ARROW;
            case SLOWNESS -> Item.TIPPED_ARROW_SLOWNESS;
            case WEAKNESS -> Item.TIPPED_ARROW_WEAKNESS;
        };
    }
}
