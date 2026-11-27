package com.voxelgame.world.entity.mob.monster;

import com.voxelgame.world.entity.mob.MobType;

/** Zombie dân làng giữ cơ chế Zombie và dùng model, texture, âm thanh riêng. */
public final class ZombieVillager extends Zombie {
    /** Tạo Zombie Villager trưởng thành hoặc non theo tỷ lệ tự nhiên. */
    public ZombieVillager(float x, float y, float z) {
        this(x, y, z, rollsBaby());
    }

    /** Dựng Zombie Villager với độ tuổi đã biết mà vẫn dùng tài nguyên riêng. */
    public ZombieVillager(float x, float y, float z, boolean baby) {
        super(MobType.ZOMBIE_VILLAGER, x, y, z, baby);
    }
}
