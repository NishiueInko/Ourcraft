package com.voxelgame.engine;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.FallingBlockEntity;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.Enderman;
import com.voxelgame.entity.mob.MobCategory;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobType;

/** Kiểm tra hành vi thù địch qua vòng mô phỏng thực mà không cần đồ họa. */
final class HostileMobChecks {
    /** Chạy kiểm tra tích hợp các loài, đội hình và hướng ngắm. */
    static void run() {
        ProjectileChecks.run();
        CloseHostileChecks.run();
        RangedHostileChecks.run();
        checkFactoriesAndSunlight();
        checkBlastNeighborhood();
        checkSpiderJockey();
        checkEndermanGaze();
        checkEndermanWaterAndArrow();
        checkEndermanSparseLanding();
    }

    /** Tác động ngoài mép nổ phải đi qua luật cát, sỏi và cây mất nền. */
    private static void checkBlastNeighborhood() {
        World world = World.empty();
        try {
            world.setBlockInternal(1500, 33, 800, Block.STONE.getId(), 0);
            world.setBlockInternal(1500, 34, 800, Block.SAND.getId(), 0);
            world.setBlockInternal(1501, 32, 800, Block.STONE.getId(), 0);
            world.setBlockInternal(1501, 33, 800, Block.GRAVEL.getId(), 0);
            world.setBlockInternal(1499, 32, 800, Block.GRASS.getId(), 0);
            world.setBlockInternal(1499, 33, 800, Block.DANDELION.getId(), 0);
            world.explode(null, 1500.5f, 30.5f, 800.5f, 3f, 43f);
            int falling = 0;
            for (int i = 0; i < world.entityCount(); i++) {
                if (world.entityAt(i) instanceof FallingBlockEntity) {
                    falling++;
                }
            }
            require(falling == 2 && world.getBlock(1500, 34, 800) == Block.AIR.getId()
                            && world.getBlock(1501, 33, 800) == Block.AIR.getId(),
                    "Cát và sỏi ngoài bán kính phải rơi sau khi mất nền trong vụ nổ");
            require(world.getBlock(1499, 33, 800) == Block.AIR.getId(),
                    "Hoa ngoài bán kính phải gãy nếu nền đã bị nổ");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra ghế nhện, biến thể bản địa và việc từ chối trần thấp. */
    private static void checkSpiderJockey() {
        World world = World.empty();
        MobSpawner spawner = new MobSpawner(31);
        int[] x = {500, 1500, 2500};
        MobType[] riders = {MobType.STRAY, MobType.SKELETON, MobType.PARCHED};
        try {
            for (int i = 0; i < x.length; i++) {
                require(spawner.spawnSpiderJockey(world, x[i] + 0.5f, 31, 800.5f), "Phải tạo được cặp nhện lộ thiên");
                MobEntity mount = (MobEntity) world.entityAt(i * 2);
                MobEntity rider = (MobEntity) world.entityAt(i * 2 + 1);
                require(mount.type() == MobType.SPIDER && rider.type() == riders[i]
                        && mount.getPassenger(0) == rider && rider.getMount() == mount,
                        "Cung thủ bản địa phải được liên kết với ghế nhện");
            }
            world.setBlockInternal(510, 34, 800, Block.STONE.getId(), 0);
            require(spawner.spawnSpiderJockey(world, 510.5f, 31, 800.5f)
                    && ((MobEntity) world.entityAt(7)).type() == MobType.SKELETON,
                    "Cặp nhện trong phòng kín vùng tuyết dùng Skeleton thường");
            world.setBlockInternal(2510, 32, 800, Block.STONE.getId(), 0);
            require(!spawner.spawnSpiderJockey(world, 2510.5f, 31, 800.5f) && world.entityCount() == 8,
                    "Trần thấp từ chối cả cặp nhện và cung thủ");
        } finally {
            world.dispose();
        }
    }

    /** Lượt né tên vẫn tìm thấy điểm duy nhất ở xa sau khi lượt thử ngẫu nhiên thất bại. */
    private static void checkEndermanSparseLanding() {
        World world = World.empty();
        Enderman mob = new Enderman(1500.5f, 31, 800.5f);
        try {
            world.setBlockInternal(1500, 30, 800, Block.STONE.getId(), 0);
            world.setBlockInternal(1512, 30, 800, Block.STONE.getId(), 0);
            world.addEntity(mob);
            require(mob.dodgeProjectile(world, null) && mob.x() == 1512.5f,
                    "Phải dịch chuyển đến ô đứng hợp lệ dù nằm ngoài vùng dự phòng gần");
            world.addEntity(new Player(1500.5f, 31, 800.5f));
            require(mob.dodgeProjectile(world, null) && mob.x() == 1512.5f,
                    "Vẫn né sát thương khi vị trí đáp duy nhất đã có thực thể");
        } finally {
            world.dispose();
        }
    }

    /** Mười factory phải tạo đúng mob và chỉ các loài nhạy nắng bắt lửa. */
    private static void checkFactoriesAndSunlight() {
        World world = flatWorld();
        try {
            int found = 0;
            for (MobType type : MobType.values()) {
                if (type.category() != MobCategory.MONSTER || type == MobType.CAMEL_HUSK) {
                    continue;
                }
                MobEntity mob = type.create(1510.5f, 31, 810.5f);
                require(mob != null && mob.type() == type, "Factory phải trả đúng loại " + type);
                mob.update(world, 0.05f);
                boolean burns = switch (type) {
                    case ZOMBIE, ZOMBIE_VILLAGER, SKELETON, STRAY -> true;
                    default -> false;
                };
                require((mob.getFireTimer() > 0f) == burns, "Khả năng cháy nắng sai ở " + type);
                found++;
            }
            require(found == 10, "Phải có đúng 10 cấu hình thù địch được kiểm tra");
        } finally {
            world.dispose();
        }
    }

    /** So hướng nhìn thân, ngắm mắt qua tường và ngắm mắt khi hết vật chắn. */
    private static void checkEndermanGaze() {
        World world = flatWorld();
        Player player = new Player(1510.5f, 31f, 815.5f);
        Camera camera = new Camera();
        player.attachControls(camera);
        world.registerPlayer(player);
        Enderman mob = new Enderman(1510.5f, 31f, 805.5f);
        try {
            mob.update(world, 0.05f);
            require(mob.target() == null, "Nhìn vào thân Enderman không kích hoạt giận dữ");
            camera.rotate(0, 6);
            world.setBlockInternal(1510, 33, 810, Block.STONE.getId(), 0);
            world.setBlockInternal(1510, 32, 810, Block.STONE.getId(), 0);
            mob.update(world, 0.2f);
            require(mob.target() == null, "Đá phải che đường ngắm vào mắt");
            world.setBlockInternal(1510, 33, 810, Block.AIR.getId(), 0);
            world.setBlockInternal(1510, 32, 810, Block.AIR.getId(), 0);
            mob.update(world, 0.2f);
            require(mob.target() == player && mob.angerTimer() > 0f,
                    "Ngắm vào mắt không bị che phải kích hoạt giận dữ");
        } finally {
            world.dispose();
        }
    }

    /** Nước và mưa rút một HP, tên luôn được né trước khi áp sát thương. */
    private static void checkEndermanWaterAndArrow() {
        World world = flatWorld();
        Enderman mob = new Enderman(1510.5f, 31f, 810.5f);
        Vector3 before = mob.getPosition();
        try {
            require(mob.dodgeProjectile(world, null) && before.dst2(mob.getPosition()) > 0.25f,
                    "Enderman phải dịch chuyển khi né tên nếu còn điểm an toàn");
            mob.setPosition(1510.5f, 31f, 810.5f);
            world.setBlockInternal(1510, 31, 810, Block.WATER.getId(), 0);
            mob.update(world, 0.05f);
            require(mob.getHealth() == 39f && before.dst2(mob.getPosition()) > 0.25f,
                    "Chạm nước phải mất một HP và dịch ra điểm khô");
            world.setBlockInternal(1510, 31, 810, Block.AIR.getId(), 0);
            Enderman wet = new Enderman(1510.5f, 31f, 810.5f);
            WeatherManager rain = new WeatherManager(18);
            rain.toggle();
            rain.update(5f);
            world.setWeather(rain);
            wet.update(world, 0.05f);
            require(wet.getHealth() == 39f && before.dst2(wet.getPosition()) > 0.25f,
                    "Mưa ngoài trời Plains phải làm Enderman mất một HP và dịch chuyển");
        } finally {
            world.dispose();
        }
    }

    /** Tạo nền rộng hơn bán kính dịch chuyển để kiểm tra hoạt động có điểm đáp an toàn. */
    private static World flatWorld() {
        World world = World.empty();
        for (int x = 1470; x <= 1550; x++) {
            for (int z = 770; z <= 850; z++) {
                world.setBlockInternal(x, 30, z, Block.STONE.getId(), 0);
            }
        }
        return world;
    }

    /** Ném lỗi với mô tả trường hợp hồi quy. */
    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
