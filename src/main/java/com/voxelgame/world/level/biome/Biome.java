package com.voxelgame.world.level.biome;

import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobSpawnTable;
import com.voxelgame.world.level.weather.Weather;

/**
 * Lớp trừu tượng gốc đại diện cho một quần xã sinh học (Biome) theo mẫu thiết kế Flyweight / Strategy.
 * Mỗi lớp con đóng gói toàn bộ đặc tính khí hậu, bảng màu cỏ/lá, thời tiết giáng thủy
 * và bảng sinh mob đặc thù của riêng mình, đồng thời ánh xạ O(1) với khóa hiệu năng {@link BiomeType}.
 */
public abstract class Biome {

    /** Bảng tra cứu O(1) bất biến nối 5 giá trị {@link BiomeType} tới đúng 5 singleton {@link Biome}. */
    private static final Biome[] BY_TYPE = new Biome[BiomeType.values().length];

    static {
        register(SnowyPlainsBiome.INSTANCE);
        register(ForestBiome.INSTANCE);
        register(PlainsBiome.INSTANCE);
        register(FlowerForestBiome.INSTANCE);
        register(DesertBiome.INSTANCE);
    }

    /** Constructor bảo vệ chỉ cho 5 lớp con cụ thể khởi tạo singleton. */
    protected Biome() {
    }

    /** Đăng ký singleton biome vào mảng tra cứu theo ordinal của {@link BiomeType}. */
    private static void register(Biome biome) {
        BY_TYPE[biome.getType().ordinal()] = biome;
    }

    /**
     * Tra cứu thực thể {@link Biome} bất biến tương ứng với {@link BiomeType} trong O(1), không cấp phát rác.
     *
     * @param type khóa enum của biome
     * @return singleton {@link Biome} tương ứng
     */
    public static Biome of(BiomeType type) {
        return BY_TYPE[type.ordinal()];
    }

    /** Trả khóa enum {@link BiomeType} gắn với biome này. */
    public abstract BiomeType getType();

    /** Trả tên hiển thị thân thiện của biome. */
    public abstract String getDisplayName();

    /** Trả mã màu cỏ RGB 24-bit chuẩn của biome. */
    public abstract int getGrassColorRgb();

    /** Trả mã màu tán lá RGB 24-bit chuẩn của biome. */
    public abstract int getFoliageColorRgb();

    /** Cho biết biome có xuất hiện mưa khi giáng thủy hay không. */
    public abstract boolean hasRain();

    /** Cho biết biome có tuyết rơi và đóng băng mặt nước khi giáng thủy hay không. */
    public abstract boolean hasSnow();

    /** Cho biết biome thuộc khí hậu khô hạn, không có mưa hay tuyết. */
    public abstract boolean isDry();

    /**
     * Phân giải đa hình trạng thái thời tiết cục bộ khi chu kỳ giáng thủy toàn cục đang hoạt động.
     *
     * @return {@code RainWeather}, {@code SnowWeather} hoặc {@code ClearWeather}
     */
    public abstract Weather resolvePrecipitationWeather();

    /**
     * Trả bảng chọn loài mob của biome theo nhóm sinh vật và phân vùng tuyết (nếu có).
     *
     * @param category nhóm sinh vật ({@code CREATURE} hoặc {@code MONSTER})
     * @param zone     phân vùng địa hình tuyết (dùng cho {@code SnowyPlainsBiome})
     * @return bảng trọng số sinh mob tương ứng
     */
    public abstract MobSpawnTable getSpawnTable(MobCategory category, SnowSubZone zone);
}
