package com.voxelgame.entity.mob;

import com.voxelgame.entity.projectile.ArrowType;

/** Cung thủ vùng tuyết có cùng dáng chiến đấu nhưng bắn tên làm chậm. */
public final class Stray extends Skeleton {
    /** Dựng Stray với hai giây giữa các phát và mũi tên Slowness I. */
    public Stray(float x, float y, float z) {
        super(MobType.STRAY, x, y, z, ArrowType.SLOWNESS, 3f, 2f);
    }
}
