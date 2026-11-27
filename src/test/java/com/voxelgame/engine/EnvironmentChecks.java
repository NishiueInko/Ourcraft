package com.voxelgame.engine;

import com.voxelgame.client.render.Camera;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.weather.WeatherManager;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.WorldTime;

import com.badlogic.gdx.math.Vector3;

/** Kiểm tra đồng hồ, chu kỳ thời tiết chuẩn Minecraft, tích tuyết và mái che trên CPU. */
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
        checkWeatherDurations();
        weather.toggle();
        weather.update(2.5f);
        check(Math.abs(weather.strength() - 0.5f) < 0.001f, "Mưa tăng dần");
        weather.update(2.5f);
        check(weather.strength() == 1f, "Mưa đạt cường độ tối đa");
        checkBiomeWeatherResponse(weather);
        weather.toggle();
        weather.update(5f);
        check(weather.strength() == 0f, "Mưa tạnh dần");

        checkWeatherSurface();
        checkRandomTickSnowAccumulation();
        checkCameraLiquidFog();
    }

    /** Kiểm tra chu kỳ quang đãng (10–150 phút) và giáng thủy (10–20 phút) chuẩn Minecraft. */
    private static void checkWeatherDurations() {
        for (long seed = 1L; seed <= 12L; seed++) {
            WeatherManager cycle = new WeatherManager(seed);
            for (int step = 0; step < 5; step++) {
                float clearSeconds = cycle.remainingSeconds();
                check(clearSeconds >= WeatherManager.CLEAR_MIN_SECONDS
                                && clearSeconds <= WeatherManager.CLEAR_MAX_SECONDS,
                        "Thời lượng trời quang phải trong 600–9000 giây: " + clearSeconds);
                cycle.toggle();
                float precipSeconds = cycle.remainingSeconds();
                check(precipSeconds >= WeatherManager.PRECIP_MIN_SECONDS
                                && precipSeconds <= WeatherManager.PRECIP_MAX_SECONDS,
                        "Thời lượng giáng thủy phải trong 600–1200 giây: " + precipSeconds);
                cycle.toggle();
            }
        }
    }

    /** Xác nhận đủ 5 biome phản hồi nhãn và âm lượng mưa đúng khí hậu. */
    private static void checkBiomeWeatherResponse(WeatherManager weather) {
        World world = World.empty();
        try {
            Player player = new Player(2000.5f, 30f, 1024.5f);
            Vector3 eye = new Vector3();
            int[] rainXs = {1200, 2000, 2800};
            for (int x : rainXs) {
                eye.set(x + 0.5f, 31.6f, 1024.5f);
                for (int i = 0; i < 40; i++) {
                    weather.update(0.1f, world, eye, player);
                }
                check("RAIN".equals(weather.label()) && weather.rainVolume() > 0.9f,
                        "Biome mưa tại X=" + x + " phải có nhãn RAIN và âm lượng đầy");
            }
            eye.set(400.5f, 31.6f, 1024.5f);
            for (int i = 0; i < 40; i++) {
                weather.update(0.1f, world, eye, player);
            }
            check("SNOW".equals(weather.label()) && weather.rainVolume() < 0.01f,
                    "Snowy Plains phải hiển thị SNOW và không phát tiếng mưa");
            eye.set(3600.5f, 31.6f, 1024.5f);
            for (int i = 0; i < 40; i++) {
                weather.update(0.1f, world, eye, player);
            }
            check("CLEAR".equals(weather.label()) && weather.skyStrength() < 0.01f && weather.rainVolume() < 0.01f,
                    "Desert luôn quang đãng và khô ráo");
        } finally {
            world.dispose();
        }
    }

    /** Mắt phải ở dưới mặt thoáng 0,88 block mới đổi môi trường quan sát. */
    private static void checkCameraLiquidFog() {
        World world = World.empty();
        try {
            Camera camera = new Camera();
            world.setBlock(10, 10, 10, Block.WATER.getId());
            world.setBlock(11, 10, 10, Block.LAVA.getId());
            camera.setPosition(10.5f, 10.9f, 10.5f);
            check(camera.liquidView(world) == Camera.LiquidView.AIR, "Mắt trên mặt nước nhìn qua không khí");
            camera.setPosition(10.5f, 10.8f, 10.5f);
            check(camera.liquidView(world) == Camera.LiquidView.WATER, "Mắt ngập nước chọn sương xanh");
            camera.setPosition(11.5f, 10.8f, 10.5f);
            check(camera.liquidView(world) == Camera.LiquidView.LAVA, "Mắt ngập lava chọn sương dày");
            world.setBlock(10, 11, 10, Block.WATER.getId());
            camera.setPosition(10.5f, 10.95f, 10.5f);
            check(camera.liquidView(world) == Camera.LiquidView.WATER, "Lớp nước dưới đầy tới biên block");
            check(Camera.LiquidView.WATER.fogStart == 1f && Camera.LiquidView.WATER.fogEnd == 22f
                            && Camera.LiquidView.LAVA.fogStart == 0.25f && Camera.LiquidView.LAVA.fogEnd == 1.6f,
                    "Khoảng nhìn trong lava phải ngắn hơn nhiều so với nước");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra mái cao, tán lá, giới hạn 1 lớp tuyết tự nhiên, nước lộ thiên và dung nham. */
    private static void checkWeatherSurface() {
        World world = World.empty();
        int x = 400;
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
            check(world.getBlock(x, 91, z) == Block.SNOW_LAYER.getId()
                    && world.getBlockState(x, 91, z) == 0, "Tuyết tích 1 lớp trên lá");
            world.accumulateSnow(x, z, null, true);
            check(world.getBlockState(x, 91, z) == 0, "Tuyết tự nhiên dừng ở 1 lớp (snowAccumulationHeight = 1)");
            check(world.getBlock(x, 4, z) == Block.WATER.getId(), "Nước dưới mái không đóng băng");
            check(world.getPrecipitationSurface(x, z) == 91.125f, "Mái tính cả lớp tuyết thấp");
            world.setBlock(x, 90, z, Block.AIR.getId());
            world.setBlock(x + 1, 4, z, Block.LAVA.getId());
            world.accumulateSnow(x, z, null, false);
            check(world.getBlock(x, 4, z) == Block.WATER.getId(), "Nước gần dung nham");
            world.setBlock(x + 1, 4, z, Block.AIR.getId());
            world.accumulateSnow(x, z, null, false);
            check(world.getBlock(x, 4, z) == Block.ICE.getId(), "Nước lộ thiên đóng băng");

            world.setBlock(3_600, 10, z, Block.STONE.getId());
            world.accumulateSnow(3_600, z, null, true);
            check(world.getBlock(3_600, 11, z) == Block.AIR.getId(), "Sa mạc luôn khô");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra cơ chế random tick 1/16 mỗi chunk/tick đóng băng và phủ tối đa 1 lớp tuyết. */
    private static void checkRandomTickSnowAccumulation() {
        World world = World.empty();
        try {
            Player player = new Player(392.5f, 15f, 120.5f);
            world.registerPlayer(player);
            for (int x = 384; x < 400; x++) {
                for (int z = 112; z < 128; z++) {
                    world.setBlockInternal(x, 10, z, x == 384 ? Block.WATER.getId() : Block.STONE.getId(), 0);
                }
            }
            WeatherManager snow = new WeatherManager(99L);
            snow.toggle();
            snow.update(5f);
            world.setWeather(snow);
            for (int tick = 0; tick < 2_400; tick++) {
                world.updateEntities(0.05f);
            }
            boolean frozeWater = false;
            boolean placedSnow = false;
            for (int x = 384; x < 400; x++) {
                for (int z = 112; z < 128; z++) {
                    if (x == 384 && world.getBlock(x, 10, z) == Block.ICE.getId()) {
                        frozeWater = true;
                    }
                    if (world.getBlock(x, 11, z) == Block.SNOW_LAYER.getId()) {
                        placedSnow = true;
                        check(world.getBlockState(x, 11, z) == 0,
                                "Tuyết tự nhiên không được tự chồng quá 1 lớp");
                    }
                }
            }
            check(frozeWater, "Nước lộ thiên phải tự đóng băng khi tuyết rơi");
            check(placedSnow, "Mặt đá lộ thiên phải được phủ 1 lớp tuyết");
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
