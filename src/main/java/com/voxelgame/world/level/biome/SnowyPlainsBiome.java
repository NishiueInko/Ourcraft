package com.voxelgame.world.level.biome;

import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobSpawnTable;
import com.voxelgame.world.level.weather.SnowWeather;
import com.voxelgame.world.level.weather.Weather;

/**
 * Quần xã Đồng bằng tuyết ({@code Snowy Plains}): khí hậu lạnh có tuyết rơi ({@link SnowWeather}),
 * sắc cỏ/lá xanh lam lạnh ({@code 0x80B497}/{@code 0x60A17B}) và phân hóa bảng sinh thú theo 3 {@link SnowSubZone}.
 */
public final class SnowyPlainsBiome extends Biome {

    /** Thể hiện Flyweight duy nhất của SnowyPlainsBiome. */
    public static final SnowyPlainsBiome INSTANCE = new SnowyPlainsBiome();

    private SnowyPlainsBiome() {
    }

    @Override
    public BiomeType getType() {
        return BiomeType.SNOWY_PLAINS;
    }

    @Override
    public String getDisplayName() {
        return "Snowy Plains";
    }

    @Override
    public int getGrassColorRgb() {
        return 0x80B497;
    }

    @Override
    public int getFoliageColorRgb() {
        return 0x60A17B;
    }

    @Override
    public boolean hasRain() {
        return false;
    }

    @Override
    public boolean hasSnow() {
        return true;
    }

    @Override
    public boolean isDry() {
        return false;
    }

    @Override
    public Weather resolvePrecipitationWeather() {
        return SnowWeather.INSTANCE;
    }

    @Override
    public MobSpawnTable getSpawnTable(MobCategory category, SnowSubZone zone) {
        if (category == MobCategory.MONSTER) {
            return MobSpawnTable.SNOW_MONSTERS;
        }
        return switch (zone) {
            case SNOWY_PLAINS_FLAT -> MobSpawnTable.SNOW_FLAT_CREATURES;
            case SNOWY_GROVE -> MobSpawnTable.SNOW_GROVE_CREATURES;
            case SNOWY_SLOPES_PEAKS -> MobSpawnTable.SNOW_SLOPE_CREATURES;
        };
    }
}
