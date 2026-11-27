package com.voxelgame.world.level.biome;

import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobSpawnTable;
import com.voxelgame.world.level.weather.RainWeather;
import com.voxelgame.world.level.weather.Weather;

/**
 * Quần xã Đồng bằng ôn hòa ({@code Plains}): có mưa, cỏ xanh tươi {@code 0x91BD59},
 * lá {@code 0x77AB2F} và hệ sinh thái thú đồng bằng (Cừu, Lợn, Gà, Bò, Ngựa, Lừa).
 */
public final class PlainsBiome extends Biome {

    /** Thể hiện Flyweight duy nhất của PlainsBiome. */
    public static final PlainsBiome INSTANCE = new PlainsBiome();

    private PlainsBiome() {
    }

    @Override
    public BiomeType getType() {
        return BiomeType.PLAINS;
    }

    @Override
    public String getDisplayName() {
        return "Plains";
    }

    @Override
    public int getGrassColorRgb() {
        return 0x91BD59;
    }

    @Override
    public int getFoliageColorRgb() {
        return 0x77AB2F;
    }

    @Override
    public boolean hasRain() {
        return true;
    }

    @Override
    public boolean hasSnow() {
        return false;
    }

    @Override
    public boolean isDry() {
        return false;
    }

    @Override
    public Weather resolvePrecipitationWeather() {
        return RainWeather.INSTANCE;
    }

    @Override
    public MobSpawnTable getSpawnTable(MobCategory category, SnowSubZone zone) {
        return category == MobCategory.MONSTER
                ? MobSpawnTable.PLAINS_MONSTERS
                : MobSpawnTable.PLAINS_CREATURES;
    }
}
