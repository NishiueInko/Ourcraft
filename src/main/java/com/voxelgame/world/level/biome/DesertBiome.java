package com.voxelgame.world.level.biome;

import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobSpawnTable;
import com.voxelgame.world.level.weather.ClearWeather;
import com.voxelgame.world.level.weather.Weather;

/**
 * Quần xã Sa mạc khô hạn ({@code Desert}): không có mưa hay tuyết, luôn giữ {@link ClearWeather},
 * sắc cỏ/lá vàng khô ({@code 0xBFB755}/{@code 0xAEA42A}) và sinh hệ quái sa mạc (Husk, Parched).
 */
public final class DesertBiome extends Biome {

    /** Thể hiện Flyweight duy nhất của DesertBiome. */
    public static final DesertBiome INSTANCE = new DesertBiome();

    private DesertBiome() {
    }

    @Override
    public BiomeType getType() {
        return BiomeType.DESERT;
    }

    @Override
    public String getDisplayName() {
        return "Desert";
    }

    @Override
    public int getGrassColorRgb() {
        return 0xBFB755;
    }

    @Override
    public int getFoliageColorRgb() {
        return 0xAEA42A;
    }

    @Override
    public boolean hasRain() {
        return false;
    }

    @Override
    public boolean hasSnow() {
        return false;
    }

    @Override
    public boolean isDry() {
        return true;
    }

    @Override
    public Weather resolvePrecipitationWeather() {
        return ClearWeather.INSTANCE;
    }

    @Override
    public MobSpawnTable getSpawnTable(MobCategory category, SnowSubZone zone) {
        return category == MobCategory.MONSTER
                ? MobSpawnTable.DESERT_MONSTERS
                : MobSpawnTable.DESERT_CREATURES;
    }
}
