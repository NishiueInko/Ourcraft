package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.MobCategory;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobType;

/** Kiểm tra luật sinh và thu hồi mob qua dữ liệu voxel thực, không cần OpenGL. */
final class MobSpawnerChecks {
    /** Chạy các phép kiểm tra luật theo đúng nhịp mô phỏng của World. */
    static void run() {
        MobSpawnTableChecks.run();
        MobPhysicsChecks.run();
        MobGoalChecks.run();
        checkDaylightAndClearance();
        checkPopulationAndCaps();
        checkDespawn();
        checkJockeySpawn();
        checkMountedDespawn();
    }

    /** Kiểm tra ánh sáng, giới hạn khoảng cách, nền và toàn bộ hộp va chạm. */
    private static void checkDaylightAndClearance() {
        World world = World.empty();
        WorldTime time = new WorldTime();
        MobSpawner spawner = new MobSpawner(17);
        Player player = new Player(3500.5f, 31f, 600.5f);
        world.registerPlayer(player);
        try {
            for (int x = 3523; x <= 3526; x++) {
                world.setBlockInternal(x, 30, 600, Block.SAND.getId(), 0);
            }
            require(!spawner.canSpawn(world, MobType.ZOMBIE, 3524.5f, 31, 600.5f, time),
                    "Quái không được sinh dưới ánh nắng");
            world.setBlockInternal(3524, 34, 600, Block.STONE.getId(), 0);
            require(spawner.canSpawn(world, MobType.ZOMBIE, 3524.5f, 31, 600.5f, time),
                    "Quái thường được sinh dưới mái che vào ban ngày");
            for (MobType type : new MobType[]{MobType.HUSK, MobType.PARCHED, MobType.STRAY, MobType.CAMEL_HUSK}) {
                require(!spawner.canSpawn(world, type, 3524.5f, 31, 600.5f, time),
                        type + " phải lộ thiên");
            }
            time.update(850);
            require(spawner.canSpawn(world, MobType.HUSK, 3525.5f, 31, 600.5f, time),
                    "Husk được sinh lộ thiên lúc nửa đêm");
            require(!spawner.canSpawn(world, MobType.ZOMBIE, 3523.5f, 31, 600.5f, time),
                    "Cấm sinh gần hơn 24 block");
            world.setBlockInternal(3525, 32, 600, Block.STONE.getId(), 0);
            require(!spawner.canSpawn(world, MobType.ZOMBIE, 3525.5f, 31, 600.5f, time),
                    "Hộp va chạm cao phải đụng trần thấp");
            world.setBlockInternal(3526, 30, 600, Block.BEDROCK.getId(), 0);
            require(!spawner.canSpawn(world, MobType.ZOMBIE, 3526.5f, 31, 600.5f, time),
                    "Bedrock không phải nền spawn");
        } finally {
            world.dispose();
        }
    }

    /** Chạy spawner với bộ factory đơn giản để kiểm tra cả cap lẫn sinh bầy khi khám phá. */
    private static void checkPopulationAndCaps() {
        World world = World.empty();
        WorldTime time = new WorldTime();
        MobSpawner spawner = new MobSpawner(17);
        Player player = new Player(2000.5f, 220f, 800.5f);
        world.registerPlayer(player);
        MobType[] types = MobType.values();
        MobType.Factory[] previousFactories = new MobType.Factory[types.length];
        for (MobType type : types) {
            previousFactories[type.ordinal()] = type.registerFactory((x, y, z) -> new MobEntity(type, x, y, z));
        }
        try {
            for (int x = 1870; x <= 2130; x++) {
                for (int z = 670; z <= 930; z++) {
                    world.setBlockInternal(x, 30, z, Block.GRASS.getId(), 0);
                }
            }
            for (int i = 0; i < 200; i++) {
                spawner.update(world, time, 0.25f);
            }
            require(count(world, MobCategory.CREATURE) == 0, "Bay qua quá cao không sinh thú");
            player.setPosition(2000.5f, 31f, 800.5f);
            for (int i = 0; i < 200; i++) {
                spawner.update(world, time, 0.25f);
            }
            int creatures = count(world, MobCategory.CREATURE);
            require(creatures > MobCategory.CREATURE.cap(), "Sinh lần đầu phải vượt được cap 10");
            for (int i = 0; i < 200; i++) {
                spawner.update(world, time, 0.25f);
            }
            require(count(world, MobCategory.CREATURE) == creatures,
                    "Các chunk đã thử chỉ sinh bầy khám phá một lần");
            time.update(850);
            for (int i = 0; i < 300; i++) {
                spawner.update(world, time, 0.25f);
            }
            require(count(world, MobCategory.MONSTER) == 70, "Cap quái vật phải dừng đúng ở 70");
            require(count(world, MobCategory.CREATURE) == creatures, "Sinh bổ sung không vượt cap 10");
        } finally {
            for (MobType type : types) {
                type.registerFactory(previousFactories[type.ordinal()]);
            }
            world.dispose();
        }
    }

    /** Kiểm tra ngưỡng tức thì, bảo vệ persistent và đặt lại đồng hồ khi mob đến gần. */
    private static void checkDespawn() {
        World world = World.empty();
        Player player = new Player(2000.5f, 31f, 800.5f);
        world.registerPlayer(player);
        MobSpawner spawner = new MobSpawner(17);
        MobEntity zombie = new MobEntity(MobType.ZOMBIE, 2129.5f, 31, 800.5f);
        MobEntity cow = new MobEntity(MobType.COW, 2129.5f, 31, 800.5f);
        try {
            require(despawn(spawner, zombie, player), "Quái xa hơn 128 block phải biến mất");
            require(!despawn(spawner, cow, player), "Thú thường được giữ lại");
            zombie.setPersistent(true);
            require(!despawn(spawner, zombie, player), "Quái persistent được giữ lại");
            zombie.setPersistent(false);
            zombie.setPosition(2050.5f, 31, 800.5f);
            for (int i = 0; i < 599; i++) {
                require(!despawn(spawner, zombie, player), "Chưa đủ 30 giây để biến mất");
            }
            zombie.setPosition(2031.5f, 31, 800.5f);
            require(!despawn(spawner, zombie, player) && zombie.noActionTime() == 0,
                    "Vào trong bán kính 32 block phải đặt lại đồng hồ");
            zombie.setPosition(2050.5f, 31, 800.5f);
            boolean removed = false;
            for (int i = 0; i < 20_000 && !removed; i++) {
                removed = despawn(spawner, zombie, player);
            }
            require(removed, "Quái ở xa đủ lâu cần qua luật biến mất ngẫu nhiên");
        } finally {
            world.dispose();
        }
    }

    /** Dùng bước mô phỏng đúng 1 tick cho quy tắc despawn xác suất. */
    private static boolean despawn(MobSpawner spawner, MobEntity mob, Player player) {
        return spawner.shouldDespawn(mob, spawner.distanceSquared(mob, player), 0.05f);
    }

    /** Chạy bảng Desert ban đêm và xác minh bộ kỵ sĩ được ghi cùng nhau khi đã có factory. */
    private static void checkJockeySpawn() {
        World world = World.empty();
        WorldTime time = new WorldTime();
        MobSpawner spawner = new MobSpawner(13);
        time.update(850);
        world.registerPlayer(new Player(3500.5f, 31f, 800.5f));
        MobType[] types = MobType.values();
        MobType.Factory[] previous = new MobType.Factory[types.length];
        for (MobType type : types) {
            previous[type.ordinal()] = type.registerFactory(null);
        }
        MobType.HUSK.registerFactory((x, y, z) -> new MobEntity(MobType.HUSK, x, y, z));
        MobType.PARCHED.registerFactory(
                (x, y, z) -> new MobEntity(MobType.PARCHED, x, y, z));
        MobType.CAMEL_HUSK.registerFactory(
                (x, y, z) -> new MobEntity(MobType.CAMEL_HUSK, x, y, z, 2) { });
        try {
            for (int x = 3372; x <= 3628; x++) {
                for (int z = 672; z <= 928; z++) {
                    world.setBlockInternal(x, 30, z, Block.SAND.getId(), 0);
                }
            }
            for (int i = 0; i < 400; i++) {
                spawner.update(world, time, 0.25f);
            }
            int mounts = 0;
            for (int i = 0; i < world.entityCount(); i++) {
                if (world.entityAt(i) instanceof MobEntity mob && mob.type() == MobType.CAMEL_HUSK) {
                    mounts++;
                    require(mob.getPassenger(0) instanceof MobEntity front && front.type() == MobType.HUSK
                                    && mob.getPassenger(1) instanceof MobEntity back && back.type() == MobType.PARCHED,
                            "Camel Husk phải xuất hiện với Husk ở ghế trước và Parched ở ghế sau");
                }
            }
            require(mounts > 0, "Bảng Desert phải có thể sinh bộ kỵ sĩ trên nền lộ thiên");
            require(count(world, MobCategory.MONSTER) == 70, "Mỗi bộ kỵ sĩ chiếm ba suất quái");
        } finally {
            for (MobType type : types) {
                type.registerFactory(previous[type.ordinal()]);
            }
            world.dispose();
        }
    }

    /** Thu hồi cả lạc đà xác sống và hai kỵ sĩ dù thứ tự thực thể bị đảo. */
    private static void checkMountedDespawn() {
        World world = World.empty();
        Player player = new Player(2000.5f, 31f, 800.5f);
        world.registerPlayer(player);
        MobEntity camel = new MobEntity(MobType.CAMEL_HUSK, 2130.5f, 31, 800.5f, 2) { };
        MobEntity front = new MobEntity(MobType.HUSK, 2130.5f, 34, 800.5f);
        MobEntity back = new MobEntity(MobType.PARCHED, 2130.5f, 34, 800.5f);
        MobEntity cow = new MobEntity(MobType.COW, 2130.5f, 31, 800.5f);
        try {
            require(front.tryMount(camel, 0) && back.tryMount(camel, 1), "Gắn bộ kỵ sĩ trước lượt cập nhật");
            world.addEntity(front);
            world.addEntity(camel);
            world.addEntity(back);
            world.addEntity(cow);
            world.updateEntities(0.05f);
            require(world.entityCount() == 2 && world.entityAt(1) == cow,
                    "World phải giữ người chơi và thú thường sau khi thu hồi cả bộ kỵ sĩ");
            require(front.getMount() == null && back.getMount() == null,
                    "Thu hồi lạc đà phải dọn liên kết ghế theo cả hai hướng");
            require(cow.y() == 31f, "Thú ở xa giữ trạng thái tới khi trở lại vùng mô phỏng");
        } finally {
            world.dispose();
        }
    }

    /** Đếm các thành viên của nhóm trên danh sách đa hình của World. */
    private static int count(World world, MobCategory category) {
        int count = 0;
        for (int i = 0; i < world.entityCount(); i++) {
            if (world.entityAt(i) instanceof MobEntity mob && mob.type().category() == category) {
                count++;
            }
        }
        return count;
    }

    /** Báo lỗi hồi quy bằng mô tả điều kiện đã vi phạm. */
    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
