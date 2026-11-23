package com.voxelgame.engine;

import com.voxelgame.entity.Block;

/** Kiểm tra đồng hồ, chuyển thời tiết và mái che trên CPU. */
final class EnvironmentChecks {
    /** Chạy cùng các ca kiểm thử hồi quy của thế giới. */
    static void run() {
        WorldTime time = new WorldTime();
        check(time.minuteOfDay() == 420 && time.dayCount() == 0, "Giờ khởi đầu");
        time.nextMilestone();
        check(time.minuteOfDay() == 360 && time.sunX() > 0.999f, "Mặt trời mọc phía đông");
        time.nextMilestone();
        check(time.sunY() > 0.999f, "Mặt trời lên thiên đỉnh lúc trưa");
        time.nextMilestone();
        check(time.sunX() < -0.999f, "Mặt trời lặn phía tây");
        time.nextMilestone();
        check(time.sunY() < -0.999f, "Mặt trăng lên thiên đỉnh lúc nửa đêm");
        time.nextMilestone();
        check(time.dayCount() == 1 && time.sunX() > 0.999f, "Bình minh ngày tiếp theo");
        time.toggleFastForward();
        time.update(60);
        check(time.dayCount() == 2 && time.minuteOfDay() == 360, "Một ngày ở tốc độ 20x");

        WeatherManager weather = new WeatherManager(42L);
        weather.toggle();
        weather.update(2.5f);
        check(Math.abs(weather.strength() - 0.5f) < 0.001f, "Mưa tăng dần");
        weather.update(2.5f);
        check(weather.strength() == 1f, "Mưa đạt cường độ tối đa");
        weather.toggle();
        weather.update(5f);
        check(weather.strength() == 0f, "Mưa tạnh dần");

        checkWeatherSurface();
    }

    /** Kiểm tra mái cao, tán lá, nước lộ thiên và ảnh hưởng của dung nham. */
    private static void checkWeatherSurface() {
        World world = World.empty();
        int x = 500;
        int z = 120;
        try {
            world.setBlock(x, 4, z, Block.WATER.getId());
            check(Math.abs(world.getPrecipitationSurface(x, z) - 4.88f) < 0.001f, "Mặt nước");
            check(!world.hasWeatherRoof(x, 4.5f, z), "Mặt nước không phải mái che");
            world.setBlock(x, 90, z, Block.SPRUCE_LEAVES.getId());
            check(world.getPrecipitationSurface(x, z) == 91f, "Mái lá ở chunk cao");
            check(world.hasWeatherRoof(x, 89.5f, z), "Đứng dưới tán lá");
            check(!world.hasWeatherRoof(x, 91.5f, z), "Đứng trên tán lá");
            world.accumulateSnow(x, z, null, false);
            check(world.getBlock(x, 91, z) == Block.SNOW_LAYER.getId(), "Tuyết tích trên lá");
            check(world.getBlock(x, 4, z) == Block.WATER.getId(), "Nước dưới mái không đóng băng");
            check(world.getPrecipitationSurface(x, z) == 91.125f, "Mái tính cả lớp tuyết thấp");
            world.setBlock(x, 90, z, Block.AIR.getId());
            world.setBlock(x + 1, 4, z, Block.LAVA.getId());
            world.accumulateSnow(x, z, null, false);
            check(world.getBlock(x, 4, z) == Block.WATER.getId(), "Nước gần dung nham");
            world.setBlock(x + 1, 4, z, Block.AIR.getId());
            world.accumulateSnow(x, z, null, false);
            check(world.getBlock(x, 4, z) == Block.ICE.getId(), "Nước lộ thiên đóng băng");

            world.setBlock(2_500, 10, z, Block.STONE.getId());
            world.accumulateSnow(2_500, z, null, true);
            check(world.getBlock(2_500, 11, z) == Block.AIR.getId(), "Sa mạc luôn khô");
        } finally {
            world.dispose();
        }
    }

    /** Báo chính xác điều kiện nào sai trong bộ kiểm thử JavaExec. */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
