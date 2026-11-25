package com.voxelgame.entity.mob;

import java.util.concurrent.ThreadLocalRandom;

/** Lừa Plains có mô hình riêng, máu biến thiên và chỉ số di chuyển cố định. */
public final class Donkey extends MountableMob {
    /** Tạo cá thể trưởng thành hoang dã có một ghế cưỡi. */
    public Donkey(float x, float y, float z) {
        super(MobType.DONKEY, x, y, z, 1, 1f, 7.5f, 2.1f, false, true,
                Horse.randomHealth(ThreadLocalRandom.current()));
    }
}
