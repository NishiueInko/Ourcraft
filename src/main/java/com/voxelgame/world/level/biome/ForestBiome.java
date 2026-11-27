package com.voxelgame.world.level.biome;

import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobSpawnTable;
import com.voxelgame.world.level.weather.RainWeather;
import com.voxelgame.world.level.weather.Weather;

/**
 * Quần xã Rừng sồi ôn hòa ({@code Forest}): có mưa, sắc xanh rừng đậm ({@code 0x79C05A}/{@code 0x59AE30})
 * và bổ sung bầy Sói hoang thay cho Ngựa/Lừa.
 */
public final class ForestBiome extends Biome {

    /** Thể hiện Flyweight duy nhất của ForestBiome. */
    public static final ForestBiome INSTANCE = new ForestBiome();

    private ForestBiome() {
    }

    @Override
    public BiomeType getType() {
        return BiomeType.FOREST;
    }

    @Override
    public String getDisplayName() {
        return "Forest";
    }

    @Override
    public int getGrassColorRgb() {
        return 0x79C05A;
    }

    @Override
    public int getFoliageColorRgb() {
        return 0x59AE30;
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
                : MobSpawnTable.FOREST_CREATURES;
    }
}
