package com.voxelgame.engine;

import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.world.entity.mob.animal.Camel;
import com.voxelgame.world.entity.mob.animal.Horse;
import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobSpawner;
import com.voxelgame.world.entity.mob.MobSpawnTable;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.mob.monster.CamelHusk;
import com.voxelgame.world.entity.mob.monster.Husk;
import com.voxelgame.world.entity.mob.monster.Parched;
import com.voxelgame.world.entity.mob.MountableMob;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.WorldTime;

import java.util.Random;

/** Kiểm tra tích hợp thú cưỡi và đội kỵ sĩ qua các đường gọi của game. */
final class MountAndCamelHuskChecks {
    /** Mọi mục trong danh mục phải tạo được cá thể thực ở mốc cuối. */
    static void run() {
        for (MobType type : MobType.values()) {
            var mob = type.create(500.5f, 40, 500.5f);
            require(mob != null && mob.type() == type, "Thiếu factory cho cấu hình " + type);
        }
        checkHorsePack();
        checkNaturalCamelHusk();
        checkMountedVoidRecovery();
        checkWaterDismountForEveryMount();
        EquineMountChecks.run();
        CamelMountChecks.run();
        CamelHuskBehaviorChecks.run();
    }

    /** Mọi thú giữ ghế trong nước nông và gỡ đủ ghế khi ngập sâu. */
    private static void checkWaterDismountForEveryMount() {
        for (MobType type : new MobType[]{MobType.HORSE, MobType.DONKEY, MobType.CAMEL, MobType.CAMEL_HUSK}) {
            World world = World.empty();
            try {
                for (int x = 19; x <= 21; x++) {
                    for (int z = 19; z <= 21; z++) {
                        world.setBlockInternal(x, 19, z, Block.STONE.getId(), 0);
                        world.setBlockInternal(x, 20, z, Block.WATER.getId(), 0);
                    }
                }
                MountableMob mount = (MountableMob) type.create(20.5f, 20f, 20.5f);
                Player rider = new Player(20.5f, 20f, 20.5f);
                int seat = mount.getSeatCount() - 1;
                require(rider.tryMount(mount, seat), "Cần ghế để kiểm tra " + type);
                mount.update(world, 0.05f);
                require(rider.getMount() == mount && mount.y() == 20f && !mount.canFloat(),
                        type + " phải giữ ghế và đứng trên đáy nước nông");
                world.setBlockInternal(20, 21, 20, Block.WATER.getId(), 0);
                world.setBlockInternal(20, 22, 20, Block.WATER.getId(), 0);
                mount.update(world, 0.05f);
                require(rider.getMount() == null && mount.getPassenger(seat) == null && mount.canFloat(),
                        type + " phải gỡ ghế trong nước sâu rồi bật lại lực nổi");
            } finally {
                world.dispose();
            }
        }
    }

    /** Giữ đường hồi sinh từ đáy thế giới khi không tìm được mặt đất để xuống thú. */
    private static void checkMountedVoidRecovery() {
        World world = World.empty();
        Player player = new Player(2000.5f, 35f, 500.5f);
        Camel camel = new Camel(2000.5f, 35f, 500.5f);
        world.registerPlayer(player);
        try {
            require(player.tryMount(camel, 0), "Phải gắn ghế trước khi mô phỏng thú rơi");
            camel.setPosition(2000.5f, -30f, 500.5f);
            player.update(world, 0.05f);
            require(player.getMount() == null && camel.getPassenger(0) == null && player.getPosition().y >= 0f,
                    "Rơi ngoài bản đồ khi cưỡi phải tháo liên kết ghế và đưa người chơi lên lại");
        } finally {
            world.dispose();
        }
    }

    /** Ép kết quả chọn bầy ngựa trên cỏ, kiểm tra quy mô bầy và màu lông chung. */
    private static void checkHorsePack() {
        World world = World.empty();
        world.registerPlayer(new Player(2000.5f, 31f, 500.5f));
        WorldTime day = new WorldTime();
        day.update(300);
        MobSpawnTable table = MobSpawnTable.forBiome(BiomeType.PLAINS, MobCategory.CREATURE, null);
        long seed = 0;
        while (table.select(new Random(seed).nextInt(table.totalWeight())).type() != MobType.HORSE) {
            seed++;
        }
        try {
            for (int x = 2030; x <= 2050; x++) {
                for (int z = 490; z <= 510; z++) {
                    world.setBlockInternal(x, 30, z, Block.GRASS.getId(), 0);
                }
            }
            require(world.getBiome(2040, 500) == BiomeType.PLAINS, "Nền kiểm thử ngựa phải thuộc Plains");
            int created = new MobSpawner(seed).spawnPack(world, day, MobCategory.CREATURE, 2040, 500, 6);
            require(created >= 2 && created <= 6, "Bầy ngựa trên cỏ phải có từ 2 đến 6 con");
            MobAppearance coat = ((Horse) world.entityAt(1)).mobVisualState().appearance();
            for (int i = 1; i <= created; i++) {
                require(world.entityAt(i) instanceof Horse horse
                                && horse.mobVisualState().appearance() == coat,
                        "Các cá thể ngựa cùng bầy phải dùng cùng màu lông nền");
            }
        } finally {
            world.dispose();
        }
    }

    /** Đi qua lượt sinh tự nhiên với factory thật để xác nhận trang bị và ghế của cả bộ. */
    private static void checkNaturalCamelHusk() {
        World world = World.empty();
        world.registerPlayer(new Player(3500.5f, 31f, 800.5f));
        WorldTime night = new WorldTime();
        night.update(850);
        MobSpawnTable table = MobSpawnTable.forBiome(BiomeType.DESERT, MobCategory.MONSTER, null);
        try {
            for (int x = 3530; x <= 3550; x++) {
                for (int z = 790; z <= 810; z++) {
                    world.setBlockInternal(x, 30, z, Block.SAND.getId(), 0);
                }
            }
            for (long seed = 0; seed < 1000; seed++) {
                Random random = new Random(seed);
                if (random.nextInt(4) == 0) {
                    continue;
                }
                MobSpawnTable.Entry husk = table.select(random.nextInt(table.totalWeight()));
                if (husk.type() != MobType.HUSK) {
                    continue;
                }
                random.nextInt(husk.maxPackSize() - husk.minPackSize() + 1);
                if (random.nextInt(10) != 0) {
                    continue;
                }
                int created = new MobSpawner(seed).spawnPack(world, night, MobCategory.MONSTER, 3540, 800, 3);
                require(created == 3 && world.entityAt(1) instanceof CamelHusk camel
                                && camel.getPassenger(0) instanceof Husk front && front.hasIronSpear()
                                && camel.getPassenger(1) instanceof Parched,
                        "Husk trên mặt cát ban đêm phải sinh được cụm 3 cá thể đúng trang bị");
                return;
            }
            throw new AssertionError("Không tìm được seed chọn Camel Husk trong dãy kiểm thử");
        } finally {
            world.dispose();
        }
    }

    /** Báo nguyên nhân cụ thể khi đường gọi tích hợp trả kết quả sai. */
    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
