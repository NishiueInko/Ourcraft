package com.voxelgame.entity.mob;

import com.voxelgame.entity.StatusEffectType;
import com.voxelgame.entity.projectile.ArrowType;

/** Cung thủ sa mạc chịu nắng, miễn Weakness và bắn tên suy yếu. */
public final class Parched extends Skeleton {
    /** Dựng Parched với tên gây từ hai đến ba HP, nhịp bắn cơ sở ba giây rưỡi. */
    public Parched(float x, float y, float z) {
        super(MobType.PARCHED, x, y, z, ArrowType.WEAKNESS, 2f, 3.5f);
    }

    /** Chặn Weakness từ mọi nguồn trước khi hiệu ứng chiếm khe của thực thể. */
    @Override
    public boolean isImmuneTo(StatusEffectType type) {
        return type == StatusEffectType.WEAKNESS || super.isImmuneTo(type);
    }
}
