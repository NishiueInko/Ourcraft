package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.MobCategory;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobType;

import java.util.Random;

/** Kiểm tra tích hợp các loài thú qua đường tạo cá thể dùng trong game. */
final class PassiveAndNeutralMobChecks {
    /** Xác minh chín loài, trong đó thỏ dùng hai cấu hình sinh theo biome. */
    static void run() {
        MobType[] types = {MobType.COW, MobType.SHEEP, MobType.PIG, MobType.CHICKEN, MobType.RABBIT,
                MobType.SNOW_RABBIT, MobType.GOAT, MobType.POLAR_BEAR, MobType.WOLF, MobType.SNOW_FOX};
        for (MobType type : types) {
            MobEntity mob = type.create(500.5f, 40f, 500.5f);
            if (mob == null || mob.type() != type) {
                throw new AssertionError("Factory thú phải tạo đúng cấu hình: " + type);
            }
        }
        checkPolarBearPack();
        PlainsAnimalChecks.run();
        RabbitFoxChecks.run();
        NeutralSnowChecks.run();
    }

    /** Ép bốc đúng bầy hai gấu bằng hạt giống cố định, rồi thử lại với chỉ một suất trống. */
    private static void checkPolarBearPack() {
        World world = World.empty();
        WorldTime clock = new WorldTime();
        long seed = 0;
        int weight = MobSpawnTable.forBiome(BiomeType.SNOWY_PLAINS, MobCategory.CREATURE,
                SnowSubZone.SNOWY_PLAINS_FLAT).totalWeight();
        while (!choosesTwoBears(seed, weight)) {
            seed++;
        }
        try {
            world.registerPlayer(new Player(500.5f, 31, 800.5f));
            for (int x = 535; x <= 545; x++) {
                for (int z = 795; z <= 805; z++) {
                    world.setBlockInternal(x, 30, z, Block.ICE.getId(), 0);
                    world.setBlockInternal(x + 20, 30, z, Block.SNOW_BLOCK.getId(), 0);
                }
            }
            if (new MobSpawner(seed).spawnPack(world, clock, MobCategory.CREATURE, 540, 800, 2) != 2) {
                throw new AssertionError("Bảng đồng tuyết phải sinh được hai gấu trên băng");
            }
            MobEntity adult = (MobEntity) world.entityAt(1);
            MobEntity cub = (MobEntity) world.entityAt(2);
            if (adult.type() != MobType.POLAR_BEAR || adult.isBaby() || cub.type() != MobType.POLAR_BEAR
                    || !cub.isBaby() || cub.getHeight() != 0.7f || cub.getHalfWidth() != 0.35f) {
                throw new AssertionError("Bầy hai gấu phải có gấu trưởng thành và gấu con đúng kích thước");
            }
            if (new MobSpawner(seed).spawnPack(world, clock, MobCategory.CREATURE, 560, 800, 1) != 1
                    || ((MobEntity) world.entityAt(3)).isBaby()) {
                throw new AssertionError("Suất cuối trên tuyết phải sinh một gấu trưởng thành");
            }
        } finally {
            world.dispose();
        }
    }

    /** Mục gấu chiếm ô cuối trong bảng đồng tuyết và độ rộng cỡ bầy là hai. */
    private static boolean choosesTwoBears(long seed, int totalWeight) {
        Random random = new Random(seed);
        return random.nextInt(totalWeight) == totalWeight - 1 && random.nextInt(2) == 1;
    }
}
