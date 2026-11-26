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
        checkBabyHitboxAndSpawnRates();
        PlainsAnimalChecks.run();
        RabbitFoxChecks.run();
        NeutralSnowChecks.run();
    }

    /** Kiểm tra thu nhỏ hitbox 0,5× và tỉ lệ con non khi sinh qua MobSpawner ở các biome. */
    private static void checkBabyHitboxAndSpawnRates() {
        MobType[] scaledTypes = {
                MobType.COW, MobType.SHEEP, MobType.PIG, MobType.CHICKEN,
                MobType.RABBIT, MobType.SNOW_RABBIT, MobType.WOLF, MobType.GOAT
        };
        for (MobType type : scaledTypes) {
            MobEntity mob = type.create(10f, 10f, 10f);
            mob.setBaby(true);
            if (Math.abs(mob.getHalfWidth() * 2f - type.width() * 0.5f) > 0.001f
                    || Math.abs(mob.getHeight() - type.height() * 0.5f) > 0.001f) {
                throw new AssertionError("setBaby(true) phải thu nhỏ hitbox 0,5× cho " + type);
            }
            mob.setBaby(false);
            if (Math.abs(mob.getHalfWidth() * 2f - type.width()) > 0.001f
                    || Math.abs(mob.getHeight() - type.height()) > 0.001f) {
                throw new AssertionError("setBaby(false) phải khôi phục hitbox 1,0× cho " + type);
            }
        }

        World world = World.empty();
        WorldTime day = new WorldTime();
        day.update(300);
        try {
            // Sinh thử ở Forest (có Wolf) và Flower Forest (có Snow Rabbit), cùng Desert (chỉ Rabbit 10% non).
            for (int x = 1222; x <= 1260; x++) {
                for (int z = 780; z <= 820; z++) {
                    world.setBlockInternal(x, 30, z, Block.GRASS.getId(), 0);
                    world.setBlockInternal(x + 1600, 30, z, Block.GRASS.getId(), 0);
                    world.setBlockInternal(x + 2300, 30, z, Block.SAND.getId(), 0);
                }
            }
            Player player = new Player(1200.5f, 31f, 800.5f);
            world.registerPlayer(player);
            MobSpawner spawner = new MobSpawner(77);
            boolean sawForestWolf = false;
            boolean sawFlowerRabbit = false;
            boolean sawBabyAnimal = false;
            for (int i = 0; i < 100 && !(sawForestWolf && sawBabyAnimal); i++) {
                int before = world.entityCount();
                int sx = 1226 + (i % 10) * 3;
                int sz = 785 + (i / 10) * 3;
                spawner.spawnPack(world, day, MobCategory.CREATURE, sx, sz, 10);
                for (int e = before; e < world.entityCount(); e++) {
                    if (world.entityAt(e) instanceof MobEntity mob) {
                        sawForestWolf |= mob.type() == MobType.WOLF;
                        sawBabyAnimal |= mob.isBaby();
                    }
                }
            }
            if (!sawForestWolf || !sawBabyAnimal) {
                throw new AssertionError("Forest phải sinh được cả Wolf và thú con (5%) qua MobSpawner");
            }

            player.setPosition(2800.5f, 31f, 800.5f);
            for (int i = 0; i < 100 && !sawFlowerRabbit; i++) {
                int before = world.entityCount();
                int sx = 2826 + (i % 10) * 3;
                int sz = 785 + (i / 10) * 3;
                spawner.spawnPack(world, day, MobCategory.CREATURE, sx, sz, 10);
                for (int e = before; e < world.entityCount(); e++) {
                    if (world.entityAt(e) instanceof MobEntity mob && mob.type() == MobType.SNOW_RABBIT) {
                        sawFlowerRabbit = true;
                    }
                }
            }
            if (!sawFlowerRabbit) {
                throw new AssertionError("Flower Forest phải sinh được Snow Rabbit qua MobSpawner");
            }

            player.setPosition(3500.5f, 31f, 800.5f);
            int desertRabbits = 0;
            int babyRabbits = 0;
            for (int i = 0; i < 80; i++) {
                int before = world.entityCount();
                int sx = 3526 + (i % 10) * 3;
                int sz = 785 + (i / 10) * 3;
                spawner.spawnPack(world, day, MobCategory.CREATURE, sx, sz, 10);
                for (int e = before; e < world.entityCount(); e++) {
                    if (world.entityAt(e) instanceof MobEntity mob) {
                        if (mob.type() != MobType.RABBIT) {
                            throw new AssertionError("Desert Creatures chỉ được sinh Rabbit, gặp " + mob.type());
                        }
                        desertRabbits++;
                        if (mob.isBaby()) {
                            babyRabbits++;
                        }
                    }
                }
            }
            if (desertRabbits < 20 || babyRabbits == 0) {
                throw new AssertionError("Desert phải sinh được Rabbit trưởng thành lẫn thỏ con (~10%)");
            }
        } finally {
            world.dispose();
        }
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
