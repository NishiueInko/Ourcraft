package com.voxelgame.world.level.biome;

import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobSpawnTable;
import com.voxelgame.world.level.weather.RainWeather;
import com.voxelgame.world.level.weather.Weather;

/**
 * Quần xã Rừng hoa ôn hòa ({@code Flower Forest}): có mưa, sắc cỏ/lá tươi sáng
 * ({@code 0x88BB67}/{@code 0x6BA941}) và bảng sinh thú có Thỏ trắng thay cho Sói.
 */
public final class FlowerForestBiome extends Biome {

    /** Thể hiện Flyweight duy nhất của FlowerForestBiome. */
    public static final FlowerForestBiome INSTANCE = new FlowerForestBiome();

    private FlowerForestBiome() {
    }

    @Override
    public BiomeType getType() {
        return BiomeType.FLOWER_FOREST;
    }

    @Override
    public String getDisplayName() {
        return "Flower Forest";
    }

    @Override
    public int getGrassColorRgb() {
        return 0x88BB67;
    }

    @Override
    public int getFoliageColorRgb() {
        return 0x6BA941;
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
                : MobSpawnTable.FLOWER_FOREST_CREATURES;
    }
}
