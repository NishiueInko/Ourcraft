package com.voxelgame.world.entity.mob.animal;

import com.voxelgame.world.entity.mob.EquineMob;
import com.voxelgame.world.entity.mob.MobType;

import java.util.concurrent.ThreadLocalRandom;

/** Lừa Plains có mô hình riêng, máu biến thiên và chỉ số di chuyển cố định, kế thừa EquineMob ngang hàng với Horse. */
public final class Donkey extends EquineMob {
    /** Tạo cá thể trưởng thành hoang dã có một ghế cưỡi. */
    public Donkey(float x, float y, float z) {
        super(MobType.DONKEY, x, y, z, 7.5f, 2.1f, randomHealth(ThreadLocalRandom.current()));
    }
}
