package com.voxelgame.engine;

import com.voxelgame.client.render.entity.MobVisualState;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.mob.animal.Chicken;
import com.voxelgame.world.entity.mob.animal.Cow;
import com.voxelgame.world.entity.mob.animal.Pig;
import com.voxelgame.world.entity.mob.animal.Sheep;
import com.voxelgame.world.entity.mob.MobAttitude;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

/** Kiểm tra chỉ số và hành vi của bốn loài hiền lành ở Plains trên CPU. */
final class PlainsAnimalChecks {
    /** Chạy các ca theo đàn, ăn cỏ, màu lông, hoảng loạn và hạ cánh. */
    static void run() {
        World world = World.empty();
        try {
            checkStatsAndPanic(world);
            checkHerd(world);
            checkWoolColors();
            checkGrazing(world);
            checkChickenFall(world);
        } finally {
            world.dispose();
        }
    }

    /** Đọc cấu hình của cá thể thực và xác nhận phản ứng bị đánh của từng loài. */
    private static void checkStatsAndPanic(World world) {
        MobEntity[] animals = {
                new Cow(5.5f, 11f, 5.5f), new Sheep(6.5f, 11f, 5.5f),
                new Pig(7.5f, 11f, 5.5f), new Chicken(8.5f, 11f, 5.5f)
        };
        float[] hp = {10f, 8f, 10f, 4f};
        float[] width = {0.9f, 0.9f, 0.9f, 0.4f};
        float[] height = {1.4f, 1.3f, 0.9f, 0.7f};
        float[] speed = {2f, 2.3f, 2.5f, 2.5f};
        Player source = new Player(4.5f, 11f, 5.5f);
        for (int i = 0; i < animals.length; i++) {
            MobEntity animal = animals[i];
            near(animal.getMaxHealth(), hp[i], "HP của thú Plains");
            near(animal.getHalfWidth() * 2f, width[i], "Chiều rộng hộp của thú Plains");
            near(animal.getHeight(), height[i], "Chiều cao hộp của thú Plains");
            near(animal.moveSpeed(), speed[i], "Tốc độ của thú Plains");
            require(animal.attitude() == MobAttitude.PASSIVE && animal.isPersistent(), "Thú Plains hiền và được giữ lại");
            require(animal.damage(world, 1f, DamageSource.MELEE, source, 0f, 0f)
                            && animal.panicTime() >= 3f && animal.panicTime() < 5f && animal.target() == null,
                    "Thú Plains hoảng khi bị đánh và không trả đòn");
        }
    }

    /** Bò đi gần lại đồng loại khi bị tách đàn trên nền an toàn. */
    private static void checkHerd(World world) {
        for (int x = 15; x <= 36; x++) {
            for (int z = 14; z <= 24; z++) {
                world.setBlockInternal(x, 15, z, Block.STONE.getId(), 0);
            }
        }
        Cow leader = new Cow(29.5f, 16f, 19.5f);
        Cow follower = new Cow(22.5f, 16f, 19.5f);
        world.addLivingEntity(leader);
        leader.update(world, 0.05f);
        follower.update(world, 0.05f);
        for (int i = 0; i < 64; i++) {
            follower.update(world, 0.05f);
        }
        require(follower.x() > 25.5f, "Bò phải tiến về đàn khi đứng cách đồng loại bảy block");
    }

    /** Quét hết bộ chọn rời rạc, tránh phép đo xác suất có thể dao động giữa các lần chạy. */
    private static void checkWoolColors() {
        Sheep.WoolColor[] colors = Sheep.WoolColor.values();
        int[] exact = {81_836, 5_000, 5_000, 5_000, 3_000, 164};
        int[] totals = new int[colors.length];
        require(colors.length == exact.length, "Bảng màu cừu có đúng sáu biến thể tự nhiên");
        for (int roll = 0; roll < 100_000; roll++) {
            totals[Sheep.WoolColor.fromRoll(roll).ordinal()]++;
        }
        for (int i = 0; i < colors.length; i++) {
            require(totals[i] == exact[i], "Tỷ lệ màu len sai ở " + colors[i]);
        }
        Sheep pink = new Sheep(0f, 0f, 0f, Sheep.WoolColor.PINK);
        require(pink.woolColor() == Sheep.WoolColor.PINK && pink.mobVisualState().sheepWoolColor() == pink.woolColor().rgb(),
                "Màu len được chuyển vào lớp hiển thị của cá thể");
    }

    /** Ăn bụi cỏ trước, gặm nền cỏ bằng đường cập nhật gameplay của World (1/1000 trưởng thành, 1/50 con non). */
    private static void checkGrazing(World world) {
        require(Sheep.ADULT_GRAZE_CHANCE_DIVISOR == 1000 && Sheep.BABY_GRAZE_CHANCE_DIVISOR == 50,
                "Xác suất ăn cỏ của cừu phải đúng 1/1000 (trưởng thành) và 1/50 (con non)");
        pen(world, 43, 30, Block.SHORT_GRASS);
        // Cỏ cao thiếu nửa trên sẽ bị dọn khi đường ghi gameplay thông báo cho ô phía trên nền.
        pen(world, 49, 30, Block.TALL_GRASS);
        Sheep eater = new Sheep(43.5f, 16f, 30.5f, Sheep.WoolColor.WHITE);
        Sheep grazer = new Sheep(49.5f, 16f, 30.5f, Sheep.WoolColor.BROWN);
        require(waitForGrass(world, eater, 43, 16, 30, Block.SHORT_GRASS, 20_000),
                "Cừu phải cúi đầu trước khi ăn bụi cỏ");
        require(world.getBlock(43, 16, 30) == Block.AIR.getId() && world.getBlock(43, 15, 30) == Block.GRASS.getId(),
                "Ăn Short Grass giữ nguyên nền cỏ");
        require(waitForGrass(world, grazer, 49, 15, 30, Block.GRASS, 20_000),
                "Cừu phải cúi đầu trước khi gặm Grass Block");
        require(world.getBlock(49, 15, 30) == Block.DIRT.getId() && world.getBlock(49, 16, 30) == Block.AIR.getId(),
                "Gặm nền cỏ đổi thành Dirt và chạy cập nhật lân cận");

        // Cừu con có hitbox 0,5× và tần suất ăn cỏ 1/50 mỗi tick (nhanh hơn rõ rệt so với cừu lớn).
        pen(world, 55, 30, Block.SHORT_GRASS);
        Sheep lamb = new Sheep(55.5f, 16f, 30.5f, Sheep.WoolColor.WHITE);
        lamb.setBaby(true);
        near(lamb.getHalfWidth() * 2f, 0.45f, "Chiều rộng hitbox cừu con");
        near(lamb.getHeight(), 0.65f, "Chiều cao hitbox cừu con");
        require(waitForGrass(world, lamb, 55, 16, 30, Block.SHORT_GRASS, 1_500),
                "Cừu con với xác suất 1/50 mỗi tick phải ăn được cỏ nhanh hơn cừu lớn");

        pen(world, 43, 38, Block.SHORT_GRASS);
        Sheep interrupted = new Sheep(43.5f, 16f, 38.5f, Sheep.WoolColor.LIGHT_GRAY);
        for (int i = 0; i < 20_000 && interrupted.mobVisualState().pose() != MobVisualState.Pose.GRAZE; i++) {
            interrupted.update(world, 0.05f);
        }
        require(interrupted.mobVisualState().pose() == MobVisualState.Pose.GRAZE, "Cừu bắt đầu lượt ăn trước khi bị đánh");
        Player attacker = new Player(41.5f, 16f, 38.5f);
        require(interrupted.damage(world, 1f, DamageSource.MELEE, attacker, 0f, 0f), "Đòn đánh ngắt lượt ăn");
        for (int i = 0; i < 25; i++) {
            interrupted.update(world, 0.05f);
        }
        require(interrupted.mobVisualState().pose() == MobVisualState.Pose.IDLE
                        && world.getBlock(43, 16, 38) == Block.SHORT_GRASS.getId(),
                "Khi chạy hoảng, cừu ngẩng đầu và không ăn cỏ của lượt đã hủy");
    }

    /** Tạo ô đứng cố định để việc canh thời điểm ăn độc lập với hướng đi ngẫu nhiên. */
    private static void pen(World world, int x, int z, Block cover) {
        world.setBlockInternal(x, 15, z, Block.GRASS.getId(), 0);
        world.setBlockInternal(x, 16, z, cover.getId(), 0);
        for (int y = 16; y < 19; y++) {
            world.setBlockInternal(x - 1, y, z, Block.STONE.getId(), 0);
            world.setBlockInternal(x + 1, y, z, Block.STONE.getId(), 0);
            world.setBlockInternal(x, y, z - 1, Block.STONE.getId(), 0);
            world.setBlockInternal(x, y, z + 1, Block.STONE.getId(), 0);
        }
    }

    /** Theo dõi tư thế cúi đầu rồi dừng ngay khi mẫu đang ăn đổi trạng thái. */
    private static boolean waitForGrass(World world, Sheep sheep, int x, int y, int z, Block expected, int maxTicks) {
        boolean grazing = false;
        for (int i = 0; i < maxTicks && world.getBlock(x, y, z) == expected.getId(); i++) {
            sheep.update(world, 0.05f);
            grazing |= sheep.mobVisualState().pose() == MobVisualState.Pose.GRAZE;
        }
        return grazing;
    }

    /** Gà rơi chậm, có hoạt ảnh cánh và miễn nhiễm cả sát thương rơi gọi trực tiếp. */
    private static void checkChickenFall(World world) {
        // Cách xa các chuồng để gà không chọn mái chuồng làm đích lang thang trong lúc rơi.
        world.setBlockInternal(85, 13, 30, Block.STONE.getId(), 0);
        Chicken chicken = new Chicken(85.5f, 34f, 30.5f);
        require(!chicken.damage(world, 100f, DamageSource.FALL, null, 0f, 0f), "Gà miễn sát thương FALL");
        for (int i = 0; i < 60; i++) {
            chicken.update(world, 0.05f);
        }
        require(chicken.y() > 28f && chicken.y() < 32f && chicken.getVelocity().y > -2f
                        && chicken.mobVisualState().pose() == MobVisualState.Pose.FLAP,
                "Gà vỗ cánh và giới hạn vận tốc trong lúc hạ xuống");
        for (int i = 0; i < 240 && !chicken.isOnGround(); i++) {
            chicken.update(world, 0.05f);
        }
        require(chicken.isOnGround() && chicken.mobVisualState().pose() == MobVisualState.Pose.IDLE,
                "Gà thu cánh khi đã đáp đất");
        near(chicken.getHealth(), 4f, "Gà không mất HP sau khi hạ từ độ cao 20 block");
    }

    /** So sánh chỉ số dấu chấm động theo sai số nhỏ. */
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.0001f, message + " (" + actual + " thay vì " + expected + ")");
    }

    /** Báo riêng tính chất không còn đúng. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
