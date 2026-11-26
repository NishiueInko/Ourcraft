package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.mob.MobCategory;
import com.voxelgame.entity.mob.MobType;

/** Kiểm tra xác suất sinh bầy và phân vùng tuyết từ dữ liệu voxel trên CPU. */
final class MobSpawnTableChecks {
    /** Lưu một dòng đặc tả độc lập với bảng đang kiểm tra. */
    private record Expected(MobType type, int weight, int min, int max) {
    }

    /** Chạy các kiểm tra xác suất và phân vùng tuyết trong bộ hồi quy chung. */
    static void run() {
        checkPlains();
        checkForestAndFlowerForest();
        checkDesert();
        checkSnowTables();
        checkSnowTerrain();
    }

    /** Đồng bằng dùng các bầy chăn thả ban ngày và danh mục quái mặc định. */
    private static void checkPlains() {
        checkTable(BiomeType.PLAINS, MobCategory.CREATURE, null,
                new Expected(MobType.SHEEP, 12, 4, 4), new Expected(MobType.PIG, 10, 4, 4),
                new Expected(MobType.CHICKEN, 10, 4, 4), new Expected(MobType.COW, 8, 4, 4),
                new Expected(MobType.HORSE, 5, 2, 6), new Expected(MobType.DONKEY, 1, 1, 3));
        checkTable(BiomeType.PLAINS, MobCategory.MONSTER, null,
                new Expected(MobType.SPIDER, 100, 4, 4), new Expected(MobType.SKELETON, 100, 4, 4),
                new Expected(MobType.CREEPER, 100, 4, 4), new Expected(MobType.ZOMBIE, 95, 4, 4),
                new Expected(MobType.ENDERMAN, 10, 1, 4), new Expected(MobType.ZOMBIE_VILLAGER, 5, 1, 1),
                new Expected(MobType.WITCH, 5, 1, 1));
    }

    /** Rừng sồi có thêm sói hoang; rừng hoa có thêm thỏ trắng thay cho ngựa và lừa. */
    private static void checkForestAndFlowerForest() {
        checkTable(BiomeType.FOREST, MobCategory.CREATURE, null,
                new Expected(MobType.SHEEP, 12, 4, 4), new Expected(MobType.PIG, 10, 4, 4),
                new Expected(MobType.CHICKEN, 10, 4, 4), new Expected(MobType.COW, 8, 4, 4),
                new Expected(MobType.WOLF, 5, 4, 4));
        checkTable(BiomeType.FLOWER_FOREST, MobCategory.CREATURE, null,
                new Expected(MobType.SHEEP, 12, 4, 4), new Expected(MobType.PIG, 10, 4, 4),
                new Expected(MobType.CHICKEN, 10, 4, 4), new Expected(MobType.COW, 8, 4, 4),
                new Expected(MobType.SNOW_RABBIT, 4, 2, 3));
        for (BiomeType forest : new BiomeType[]{BiomeType.FOREST, BiomeType.FLOWER_FOREST}) {
            checkTable(forest, MobCategory.MONSTER, null,
                    new Expected(MobType.SPIDER, 100, 4, 4), new Expected(MobType.SKELETON, 100, 4, 4),
                    new Expected(MobType.CREEPER, 100, 4, 4), new Expected(MobType.ZOMBIE, 95, 4, 4),
                    new Expected(MobType.ENDERMAN, 10, 1, 4), new Expected(MobType.ZOMBIE_VILLAGER, 5, 1, 1),
                    new Expected(MobType.WITCH, 5, 1, 1));
        }
    }

    /** Sa mạc chỉ sinh thỏ vàng (trọng số 4), với tổng trọng số quái bằng 415. */
    private static void checkDesert() {
        checkTable(BiomeType.DESERT, MobCategory.CREATURE, null,
                new Expected(MobType.RABBIT, 4, 2, 3));
        checkTable(BiomeType.DESERT, MobCategory.MONSTER, null,
                new Expected(MobType.SPIDER, 100, 4, 4), new Expected(MobType.CREEPER, 100, 4, 4),
                new Expected(MobType.HUSK, 80, 4, 4), new Expected(MobType.PARCHED, 50, 4, 4),
                new Expected(MobType.SKELETON, 50, 4, 4), new Expected(MobType.ZOMBIE, 19, 4, 4),
                new Expected(MobType.ENDERMAN, 10, 1, 4), new Expected(MobType.WITCH, 5, 1, 1),
                new Expected(MobType.ZOMBIE_VILLAGER, 1, 1, 1));
    }

    /** Ba phân vùng tuyết có ba nhóm thú (Grove có thỏ trọng số 4) và cùng một bảng quái. */
    private static void checkSnowTables() {
        checkTable(BiomeType.SNOWY_PLAINS, MobCategory.CREATURE, SnowSubZone.SNOWY_PLAINS_FLAT,
                new Expected(MobType.SNOW_RABBIT, 10, 2, 3), new Expected(MobType.POLAR_BEAR, 1, 1, 2));
        checkTable(BiomeType.SNOWY_PLAINS, MobCategory.CREATURE, SnowSubZone.SNOWY_GROVE,
                new Expected(MobType.WOLF, 8, 4, 4), new Expected(MobType.SNOW_FOX, 8, 2, 4),
                new Expected(MobType.SNOW_RABBIT, 4, 2, 3));
        checkTable(BiomeType.SNOWY_PLAINS, MobCategory.CREATURE, SnowSubZone.SNOWY_SLOPES_PEAKS,
                new Expected(MobType.GOAT, 5, 1, 3), new Expected(MobType.SNOW_RABBIT, 4, 2, 3));
        for (SnowSubZone zone : SnowSubZone.values()) {
            checkTable(BiomeType.SNOWY_PLAINS, MobCategory.MONSTER, zone,
                    new Expected(MobType.SPIDER, 100, 4, 4), new Expected(MobType.CREEPER, 100, 4, 4),
                    new Expected(MobType.ZOMBIE, 95, 4, 4), new Expected(MobType.STRAY, 80, 4, 4),
                    new Expected(MobType.SKELETON, 20, 4, 4), new Expected(MobType.ENDERMAN, 10, 1, 4),
                    new Expected(MobType.ZOMBIE_VILLAGER, 5, 1, 1), new Expected(MobType.WITCH, 5, 1, 1));
        }
    }

    /** Duyệt hết các giá trị xúc xắc để kiểm tra chính xác trọng số và cỡ bầy. */
    private static void checkTable(BiomeType biome, MobCategory category, SnowSubZone zone, Expected... expected) {
        MobSpawnTable table = MobSpawnTable.forBiome(biome, category, zone);
        int roll = 0;
        for (Expected row : expected) {
            for (int i = 0; i < row.weight(); i++) {
                MobSpawnTable.Entry actual = table.select(roll++);
                require(actual.type() == row.type() && actual.weight() == row.weight()
                                && actual.minPackSize() == row.min() && actual.maxPackSize() == row.max(),
                        "Sai dữ liệu sinh bầy của " + row.type() + " trong " + biome + " / " + category);
            }
        }
        require(roll == table.totalWeight(), "Tổng trọng số sai trong " + biome + " / " + category);
    }

    /** Kiểm tra nền bằng, ngưỡng cao, mặt dốc và khoảng cách thực tới vân sam. */
    private static void checkSnowTerrain() {
        World world = World.empty();
        try {
            world.setBlockInternal(100, 29, 100, Block.GRASS.getId(), 0);
            world.setBlockInternal(110, 30, 101, Block.SPRUCE_LEAVES.getId(), 0);
            require(SnowSubZone.classify(world, 100, 30, 100) == SnowSubZone.SNOWY_PLAINS_FLAT,
                    "Lá vân sam cách hơn mười block theo đường chéo phải nằm ngoài Grove");
            world.setBlockInternal(110, 30, 100, Block.SPRUCE_LOG.getId(), 0);
            require(SnowSubZone.classify(world, 100, 30, 100) == SnowSubZone.SNOWY_GROVE,
                    "Vân sam cách đúng mười block thuộc Grove");
            require(SnowSubZone.classify(world, 100, 29, 100) == SnowSubZone.SNOWY_PLAINS_FLAT,
                    "Độ cao dưới 30 thuộc đồng bằng tuyết");

            world.setBlockInternal(100, 61, 100, Block.GRASS.getId(), 0);
            world.setBlockInternal(100, 63, 100, Block.SPRUCE_LEAVES.getId(), 0);
            require(SnowSubZone.classify(world, 100, 62, 100) == SnowSubZone.SNOWY_SLOPES_PEAKS,
                    "Ngưỡng núi cao được ưu tiên dù có vân sam");

            world.setBlockInternal(150, 53, 150, Block.STONE.getId(), 0);
            world.setBlockInternal(151, 51, 150, Block.GRAVEL.getId(), 0);
            world.setBlockInternal(150, 56, 150, Block.SPRUCE_LEAVES.getId(), 0);
            require(SnowSubZone.classify(world, 150, 54, 150) == SnowSubZone.SNOWY_SLOPES_PEAKS,
                    "Nền đá dốc ở cao độ chân núi phải thuộc sườn tuyết");
        } finally {
            world.dispose();
        }
    }

    /** Nêu điều kiện sai để bộ hồi quy báo đúng khu vực cần sửa. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
