package com.voxelgame.engine;

import com.voxelgame.entity.mob.MobCategory;
import com.voxelgame.entity.mob.MobType;

/** Bảng loài và trọng số cho mỗi biome, dùng chung giữa các lượt sinh. */
public final class MobSpawnTable {
    /** Một loài ứng viên, số ô xác suất và khoảng cỡ bầy tính cả hai đầu. */
    public record Entry(MobType type, int weight, int minPackSize, int maxPackSize) {
    }

    /** Trọng số và cỡ bầy của đồng bằng ôn hòa. */
    private static final MobSpawnTable PLAINS_CREATURES = new MobSpawnTable(
            new Entry(MobType.SHEEP, 12, 4, 4), new Entry(MobType.PIG, 10, 4, 4),
            new Entry(MobType.CHICKEN, 10, 4, 4), new Entry(MobType.COW, 8, 4, 4),
            new Entry(MobType.HORSE, 5, 2, 6), new Entry(MobType.DONKEY, 1, 1, 3));
    private static final MobSpawnTable PLAINS_MONSTERS = new MobSpawnTable(
            new Entry(MobType.SPIDER, 100, 4, 4), new Entry(MobType.SKELETON, 100, 4, 4),
            new Entry(MobType.CREEPER, 100, 4, 4), new Entry(MobType.ZOMBIE, 95, 4, 4),
            new Entry(MobType.ENDERMAN, 10, 1, 4), new Entry(MobType.ZOMBIE_VILLAGER, 5, 1, 1),
            new Entry(MobType.WITCH, 5, 1, 1));

    /** Sa mạc có thỏ vàng và lạc đà, với bảng quái thiên về các loài bản địa. */
    private static final MobSpawnTable DESERT_CREATURES = new MobSpawnTable(
            new Entry(MobType.RABBIT, 12, 2, 3), new Entry(MobType.CAMEL, 1, 1, 1));
    private static final MobSpawnTable DESERT_MONSTERS = new MobSpawnTable(
            new Entry(MobType.SPIDER, 100, 4, 4), new Entry(MobType.CREEPER, 100, 4, 4),
            new Entry(MobType.HUSK, 80, 4, 4), new Entry(MobType.PARCHED, 50, 4, 4),
            new Entry(MobType.SKELETON, 50, 4, 4), new Entry(MobType.ZOMBIE, 19, 4, 4),
            new Entry(MobType.ENDERMAN, 10, 1, 4), new Entry(MobType.WITCH, 5, 1, 1),
            new Entry(MobType.ZOMBIE_VILLAGER, 1, 1, 1));

    /** Ba bảng thú tuyết theo địa hình và một bảng quái chung cho cả biome. */
    private static final MobSpawnTable SNOW_FLAT_CREATURES = new MobSpawnTable(
            new Entry(MobType.SNOW_RABBIT, 10, 2, 3), new Entry(MobType.POLAR_BEAR, 1, 1, 2));
    private static final MobSpawnTable SNOW_GROVE_CREATURES = new MobSpawnTable(
            new Entry(MobType.WOLF, 8, 4, 4), new Entry(MobType.SNOW_FOX, 8, 2, 4),
            new Entry(MobType.SNOW_RABBIT, 8, 2, 3));
    private static final MobSpawnTable SNOW_SLOPE_CREATURES = new MobSpawnTable(
            new Entry(MobType.GOAT, 5, 1, 3), new Entry(MobType.SNOW_RABBIT, 4, 2, 3));
    private static final MobSpawnTable SNOW_MONSTERS = new MobSpawnTable(
            new Entry(MobType.SPIDER, 100, 4, 4), new Entry(MobType.CREEPER, 100, 4, 4),
            new Entry(MobType.ZOMBIE, 95, 4, 4), new Entry(MobType.STRAY, 80, 4, 4),
            new Entry(MobType.SKELETON, 20, 4, 4), new Entry(MobType.ENDERMAN, 10, 1, 4),
            new Entry(MobType.ZOMBIE_VILLAGER, 5, 1, 1), new Entry(MobType.WITCH, 5, 1, 1));

    /** Danh mục cố định và tổng miền bốc thăm, tính một lần khi dựng bảng. */
    private final Entry[] entries;
    private final int totalWeight;

    /** Dùng mảng riêng tạo tại chỗ cho mỗi bảng để các lượt sinh chỉ đọc. */
    private MobSpawnTable(Entry... entries) {
        this.entries = entries;
        int sum = 0;
        for (Entry entry : entries) {
            sum += entry.weight();
        }
        totalWeight = sum;
    }

    /** Chọn bảng cố định. Phân vùng chỉ cần khi sinh thú trong biome tuyết. */
    public static MobSpawnTable forBiome(BiomeType biome, MobCategory category, SnowSubZone zone) {
        boolean monster = category == MobCategory.MONSTER;
        return switch (biome) {
            case PLAINS, FOREST, FLOWER_FOREST -> monster ? PLAINS_MONSTERS : PLAINS_CREATURES;
            case DESERT -> monster ? DESERT_MONSTERS : DESERT_CREATURES;
            case SNOWY_PLAINS -> monster ? SNOW_MONSTERS : switch (zone) {
                case SNOWY_PLAINS_FLAT -> SNOW_FLAT_CREATURES;
                case SNOWY_GROVE -> SNOW_GROVE_CREATURES;
                case SNOWY_SLOPES_PEAKS -> SNOW_SLOPE_CREATURES;
            };
        };
    }

    /** Trả tổng trọng số làm giới hạn trên loại trừ của lần lấy số ngẫu nhiên. */
    public int totalWeight() {
        return totalWeight;
    }

    /** Cho biết biome đã có thú hoạt động để tránh đánh dấu khám phá quá sớm. */
    static boolean anyImplementedCreatures(BiomeType biome) {
        return switch (biome) {
            case PLAINS, FOREST, FLOWER_FOREST -> PLAINS_CREATURES.hasImplementedType();
            case DESERT -> DESERT_CREATURES.hasImplementedType();
            case SNOWY_PLAINS -> SNOW_FLAT_CREATURES.hasImplementedType()
                    || SNOW_GROVE_CREATURES.hasImplementedType() || SNOW_SLOPE_CREATURES.hasImplementedType();
        };
    }

    /** Tìm factory đã đăng ký trong một bảng nhỏ cố định. */
    private boolean hasImplementedType() {
        for (Entry entry : entries) {
            if (entry.type().implemented()) {
                return true;
            }
        }
        return false;
    }

    /** Trả mục có khoảng trọng số chứa số đã bốc, dùng lại đối tượng của bảng. */
    public Entry select(int roll) {
        if (roll < 0 || roll >= totalWeight) {
            throw new IllegalArgumentException("Giá trị chọn mob nằm ngoài miền trọng số");
        }
        for (Entry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        throw new AssertionError("Không tìm thấy mục mob trong miền trọng số đã xác thực");
    }
}
