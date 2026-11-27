package com.voxelgame.engine;

import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.mob.monster.Zombie;
import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.weather.WeatherManager;
import com.voxelgame.world.level.World;

/** Kiểm tra chuyển động mob với địa hình nhỏ, không cần GPU hoặc bộ phát âm thanh. */
final class MobPhysicsChecks {
    /** Chạy các phép thử bậc, chất lỏng, tránh nguy hiểm và ánh nắng. */
    static void run() {
        World world = World.empty();
        try {
            checkJumpAndCeiling(world);
            checkDirectStepAndHazards(world);
            checkCornerRouting(world);
            checkWaterAndLava(world);
            checkSunlight(world);
        } finally {
            world.dispose();
        }
    }

    /** Bộ điều hướng đổi hướng quanh đầu tường ngắn rồi tiếp tục về đích. */
    private static void checkCornerRouting(World world) {
        fill(world, 68, 78, 47, 57, 15, Block.STONE);
        for (int y = 16; y <= 18; y++) {
            fill(world, 72, 72, 50, 52, y, Block.STONE);
        }
        Fixture corner = new Fixture(MobType.ZOMBIE, 70.5f, 16f, 51.5f);
        corner.update(world, 0.05f);
        for (int i = 0; i < 92; i++) {
            corner.moveToward(world, 76.5f, 16f, 51.5f, 1f);
            corner.update(world, 0.05f);
        }
        require(corner.x() > 75f && Math.abs(corner.z() - 51.5f) < 0.4f,
                "Điều hướng phải vòng được đầu tường thay vì dừng ở góc");
    }

    /** Bước thẳng được qua bậc 1,5 block và lệnh đi vẫn tránh gai, vực sâu. */
    private static void checkDirectStepAndHazards(World world) {
        fill(world, 41, 47, 10, 13, 15, Block.STONE);
        fill(world, 43, 47, 10, 13, 16, Block.STONE);
        for (int x = 43; x <= 47; x++) {
            for (int z = 10; z <= 13; z++) {
                world.setBlockInternal(x, 17, z, Block.SNOW_LAYER.getId(), 4);
            }
        }
        Fixture mount = new Fixture(MobType.COW, 42.5f, 16f, 11.5f);
        mount.setStepHeight(1.5f);
        mount.setDirectStep(true);
        mount.update(world, 0.05f);
        for (int i = 0; i < 50; i++) {
            mount.moveToward(world, 45.5f, 17.5f, 11.5f, 1f);
            mount.update(world, 0.05f);
            require(mount.isOnGround() && mount.y() <= 17.5f, "Bước thẳng phải luôn giữ chân trên nền");
        }
        require(mount.x() > 44.5f && mount.y() == 17.5f, "Thú dùng bước thẳng phải lên được bậc 1,5 block");

        fill(world, 50, 54, 10, 13, 20, Block.STONE);
        fill(world, 55, 59, 10, 13, 15, Block.STONE);
        fill(world, 52, 52, 10, 13, 21, Block.CACTUS);
        Fixture nearCactus = new Fixture(MobType.ZOMBIE, 51.5f, 21f, 11.5f);
        Fixture nearCliff = new Fixture(MobType.ZOMBIE, 54.5f, 21f, 11.5f);
        nearCactus.update(world, 0.05f);
        nearCliff.update(world, 0.05f);
        for (int i = 0; i < 50; i++) {
            nearCactus.moveToward(world, 54.5f, 21f, 11.5f, 1f);
            nearCliff.moveToward(world, 58.5f, 16f, 11.5f, 1f);
            nearCactus.update(world, 0.05f);
            nearCliff.update(world, 0.05f);
        }
        require(nearCactus.x() < 52f && nearCliff.y() == 21f && nearCliff.x() < 55.5f,
                "Điều hướng phải tránh gai và từ chối vực sâu quá ba block");
        require(new Fixture(MobType.GOAT, 0f, 0f, 0f).maxSafeFall() > nearCliff.maxSafeFall()
                        && new Fixture(MobType.CHICKEN, 0f, 0f, 0f).maxSafeFall() > nearCliff.maxSafeFall(),
                "Dê và gà phải có ngưỡng rơi an toàn lớn hơn mặc định");
    }

    /** Một bậc cao một khối đi được, cùng bậc dưới trần thấp phải chặn. */
    private static void checkJumpAndCeiling(World world) {
        fill(world, 11, 17, 10, 13, 15, Block.STONE);
        fill(world, 13, 17, 10, 13, 16, Block.STONE);
        Fixture open = new Fixture(MobType.ZOMBIE, 12.5f, 16f, 11.5f);
        open.update(world, 0.05f);
        float top = open.y();
        for (int i = 0; i < 64; i++) {
            open.moveToward(world, 15.5f, 17f, 11.5f, 1f);
            open.update(world, 0.05f);
            top = Math.max(top, open.y());
        }
        require(open.x() > 14f && open.y() >= 17f && top > 17.1f,
                "Mob phải tự bật qua bậc cao một block");

        fill(world, 12, 17, 10, 13, 18, Block.STONE);
        Fixture roof = new Fixture(MobType.ZOMBIE, 12.5f, 16f, 11.5f);
        roof.update(world, 0.05f);
        for (int i = 0; i < 40; i++) {
            roof.moveToward(world, 15.5f, 17f, 11.5f, 1f);
            roof.update(world, 0.05f);
        }
        require(roof.x() < 13f && Math.abs(roof.y() - 16f) < 0.01f,
                "Mob chỉ nhảy nếu đủ khoảng trống phía trên bậc");
    }

    /** Mob tự nổi ở nước và giữ chân khỏi dung nham, kể cả khi đích nằm bên kia. */
    private static void checkWaterAndLava(World world) {
        fill(world, 21, 25, 21, 25, 14, Block.STONE);
        fill(world, 21, 25, 21, 25, 15, Block.WATER);
        fill(world, 21, 25, 21, 25, 16, Block.WATER);
        Fixture swimmer = new Fixture(MobType.COW, 23.5f, 15.05f, 23.5f);
        for (int i = 0; i < 28; i++) {
            swimmer.update(world, 0.05f);
        }
        require(swimmer.y() > 16.3f && swimmer.y() < 17.7f,
                "Mob phải tự nổi lên gần mặt nước");
        swimmer.setCanFloat(false);
        for (int i = 0; i < 32; i++) {
            swimmer.update(world, 0.05f);
        }
        require(swimmer.y() < 15.5f, "AI của loài có thể tắt lực nổi mặc định");

        fill(world, 30, 37, 20, 27, 14, Block.STONE);
        fill(world, 33, 33, 20, 27, 15, Block.LAVA);
        Fixture walker = new Fixture(MobType.COW, 32.5f, 15f, 23.5f);
        walker.update(world, 0.05f);
        for (int i = 0; i < 32; i++) {
            walker.moveToward(world, 36.5f, 15f, 23.5f, 1f);
            walker.update(world, 0.05f);
        }
        require(walker.x() < 33f && walker.getFireTimer() == 0f,
                "Mob không được tự bước vào dung nham");
    }

    /** Đúng loài bốc cháy khi nắng, còn bóng râm, nước, mưa (ở Plains/Forest/Flower Forest) và đêm thì bảo vệ. */
    private static void checkSunlight(World world) {
        final int plainsX = 2000;
        fill(world, plainsX, plainsX + 7, 60, 60, 11, Block.STONE);
        world.setBlockInternal(plainsX + 2, 15, 60, Block.OAK_LEAVES.getId(), 0);
        world.setBlockInternal(plainsX + 4, 12, 60, Block.WATER.getId(), 0);
        Fixture sunny = new Fixture(MobType.ZOMBIE, plainsX + 0.5f, 12f, 60.5f);
        Fixture shaded = new Fixture(MobType.ZOMBIE, plainsX + 2.5f, 12f, 60.5f);
        Fixture wet = new Fixture(MobType.ZOMBIE, plainsX + 4.5f, 12f, 60.5f);
        Fixture nonBurning = new Fixture(MobType.HUSK, plainsX + 6.5f, 12f, 60.5f);
        sunny.update(world, 0.05f);
        shaded.update(world, 0.05f);
        wet.update(world, 0.05f);
        nonBurning.update(world, 0.05f);
        require(sunny.getFireTimer() > 1.9f && shaded.getFireTimer() == 0f && wet.getFireTimer() == 0f
                && nonBurning.getFireTimer() == 0f, "Cháy nắng phải đúng loại và xét bóng râm, nước");

        WeatherManager weather = new WeatherManager(72);
        weather.toggle();
        weather.update(5f);
        world.setWeather(weather);
        Fixture rain = new Fixture(MobType.ZOMBIE, plainsX + 0.5f, 12f, 60.5f);
        rain.update(world, 0.05f);
        require(rain.getFireTimer() == 0f, "Mưa tại cột Plains phải ngăn cháy nắng");

        fill(world, 1200, 1200, 60, 60, 11, Block.STONE);
        fill(world, 2800, 2800, 60, 60, 11, Block.STONE);
        Fixture forestRain = new Fixture(MobType.ZOMBIE, 1200.5f, 12f, 60.5f);
        Fixture flowerForestRain = new Fixture(MobType.ZOMBIE, 2800.5f, 12f, 60.5f);
        forestRain.update(world, 0.05f);
        flowerForestRain.update(world, 0.05f);
        require(forestRain.getFireTimer() == 0f && flowerForestRain.getFireTimer() == 0f,
                "Mưa ở Forest và Flower Forest cũng phải ngăn cháy nắng");

        fill(world, 3500, 3500, 60, 60, 11, Block.STONE);
        Fixture desert = new Fixture(MobType.ZOMBIE, 3500.5f, 12f, 60.5f);
        desert.update(world, 0.05f);
        require(world.getBiome(3500, 60) == BiomeType.DESERT && desert.getFireTimer() > 1.9f,
                "Mưa ở nơi khác không bảo vệ mob tại cột Desert");

        world.setWeather(null);
        world.worldTime().update(850f);
        Fixture night = new Fixture(MobType.ZOMBIE, plainsX + 0.5f, 12f, 60.5f);
        night.update(world, 0.05f);
        require(night.getFireTimer() == 0f, "Zombie không tự bắt lửa vào ban đêm");
    }

    /** Đặt một khu vực nhỏ trên cùng cao độ cho từng kịch bản vật lý. */
    private static void fill(World world, int minX, int maxX, int minZ, int maxZ, int y, Block block) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockInternal(x, y, z, block.getId(), 0);
            }
        }
    }

    /** Báo rõ tính chất vi phạm nếu phép thử không đạt. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Bỏ bộ goal ngẫu nhiên để đo trực tiếp vật lý chung của cùng kiểu mob. */
    private static final class Fixture extends MobEntity {
        /** Tạo mob theo kích thước thực trong danh mục. */
        private Fixture(MobType type, float x, float y, float z) {
            super(type, x, y, z);
        }

        /** Để nguyên các yêu cầu chuyển động mà phép thử đã gửi. */
        @Override
        protected void updateGoals(World world, float dt) {
        }
    }
}
