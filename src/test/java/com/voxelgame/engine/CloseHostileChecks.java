package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.StatusEffectType;
import com.voxelgame.entity.mob.Creeper;
import com.voxelgame.entity.mob.Enderman;
import com.voxelgame.entity.mob.Husk;
import com.voxelgame.entity.mob.MobAttitude;
import com.voxelgame.entity.mob.Spider;
import com.voxelgame.entity.mob.Zombie;
import com.voxelgame.entity.mob.ZombieVillager;

/** Kiểm tra hành vi của nhóm quái cận chiến bằng dữ liệu voxel và nhịp mô phỏng thật. */
final class CloseHostileChecks {
    /** Bước mô phỏng dùng chung cho các kiểm tra thời gian ở 20 Hz. */
    private static final float TICK = 0.05f;

    /** Chạy các kiểm tra chỉ số, điều kiện tấn công và phá khối. */
    static void run() {
        checkZombieAttributesAndReinforcements();
        checkHuskBabyAndDimensions();
        checkHuskHunger();
        checkHuskUnderwaterConversion();
        checkEndermanBlockRates();
        checkCreeperFuseAndBlockDamage();
        checkCreeperCancelsFuse();
        checkSpiderStanceAndLeap();
        checkSpiderClimbsWall();
    }

    /** Zombie con có đúng kích thước vật lý, và Zombie gần đó nhận mục tiêu khi đồng loại bị đánh. */
    private static void checkZombieAttributesAndReinforcements() {
        World world = World.empty();
        try {
            Zombie zombie = new Zombie(10.5f, 1f, 10.5f, false);
            Zombie baby = new Zombie(12.5f, 1f, 10.5f, true);
            ZombieVillager villager = new ZombieVillager(11.5f, 1f, 10.5f, false);
            require(zombie.getArmor() == 2 && villager.getArmor() == 2 && zombie.getMaxHealth() == 20,
                    "Zombie và Zombie Villager dùng 20 HP và 2 giáp");
            near(baby.getHalfWidth() * 2f, 0.3f, "Chiều rộng hitbox Zombie con");
            near(baby.getHeight(), 0.975f, "Chiều cao hitbox Zombie con");
            near(baby.moveSpeed(), zombie.moveSpeed() * 1.5f, "Tốc độ Zombie con");
            require(baby.burnsInSunlight() && villager.burnsInSunlight(), "Zombie con và Zombie Villager cháy nắng");

            Player player = new Player(43.5f, 1f, 10.5f);
            world.registerPlayer(player);
            world.addLivingEntity(zombie);
            world.addLivingEntity(baby);
            zombie.update(world, TICK);
            require(zombie.target() == player, "Zombie nhận ra người chơi trong phạm vi 35 block");
            require(zombie.damage(world, 1f, DamageSource.MELEE, player, 0f, 0f) && baby.target() == player,
                    "Zombie vừa bị đánh gọi đồng loại gần đó cùng truy đuổi");
        } finally {
            world.dispose();
        }
    }

    /** Husk có 5% cơ hội là con non với hitbox 0,5×, tốc độ 1,5×, giữ scale 1,06× ở trưởng thành và kỵ sĩ luôn lớn. */
    private static void checkHuskBabyAndDimensions() {
        Husk adult = new Husk(10.5f, 1f, 10.5f, false, false);
        Husk baby = new Husk(10.5f, 1f, 10.5f, false, true);
        near(adult.getHalfWidth() * 2f, 0.6f, "Chiều rộng hitbox Husk trưởng thành");
        near(adult.getHeight(), 1.95f, "Chiều cao hitbox Husk trưởng thành");
        near(adult.mobVisualState().scale(), 1.06f, "Tỷ lệ hình hiển thị Husk trưởng thành");
        near(baby.getHalfWidth() * 2f, 0.3f, "Chiều rộng hitbox Husk con");
        near(baby.getHeight(), 0.975f, "Chiều cao hitbox Husk con");
        near(baby.moveSpeed(), adult.moveSpeed() * 1.5f, "Tốc độ Husk con");
        near(baby.mobVisualState().scale(), 0.6f * 1.06f, "Tỷ lệ hình hiển thị Husk con");

        // Kiểm tra khôi phục kích thước 1,0× và visual 1,06× khi chuyển baby true -> false.
        baby.setBaby(false);
        near(baby.getHalfWidth() * 2f, 0.6f, "Khôi phục chiều rộng hitbox Husk");
        near(baby.getHeight(), 1.95f, "Khôi phục chiều cao hitbox Husk");
        near(baby.mobVisualState().scale(), 1.06f, "Khôi phục tỷ lệ hiển thị Husk trưởng thành");

        for (int i = 0; i < 64; i++) {
            Husk jockey = new Husk(0f, 1f, 0f, true);
            require(!jockey.isBaby() && jockey.hasIronSpear(), "Husk cầm giáo cưỡi Camel Husk luôn là người lớn");
        }

        int babyCount = 0;
        for (int i = 0; i < 2000; i++) {
            if (new Husk(0f, 1f, 0f).isBaby()) {
                babyCount++;
            }
        }
        require(babyCount > 40 && babyCount < 180,
                "Tỉ lệ Husk con tự nhiên phải dao động quanh 5% (nhận " + babyCount + "/2000)");
    }

    /** Một đòn cận chiến thật của Husk gây 3 HP và áp Hunger I đúng thời lượng. */
    private static void checkHuskHunger() {
        World world = World.empty();
        try {
            floor(world, 0);
            Player player = new Player(11.5f, 1f, 10.5f);
            world.registerPlayer(player);
            Husk husk = new Husk(10.5f, 1f, 10.5f, false, false);
            husk.update(world, TICK);
            near(player.getHealth(), 17f, "Sát thương cận chiến của Husk");
            float hunger = player.getEffectDuration(StatusEffectType.HUNGER);
            require(hunger >= 7f && hunger <= 14f, "Husk phải áp Hunger I từ 7 đến 14 giây");
            require(!husk.burnsInSunlight(), "Husk không bắt lửa ngoài nắng");
        } finally {
            world.dispose();
        }
    }

    /** Buồng ngập kín giữ đầu Husk dưới nước đủ 30 giây, sau đó chờ pha rung 15 giây. */
    private static void checkHuskUnderwaterConversion() {
        World world = World.empty();
        try {
            for (int x = 9; x <= 11; x++) {
                for (int z = 9; z <= 11; z++) {
                    world.setBlockInternal(x, 0, z, Block.STONE.getId(), 0);
                    world.setBlockInternal(x, 4, z, Block.STONE.getId(), 0);
                    for (int y = 1; y <= 3; y++) {
                        boolean center = x == 10 && z == 10;
                        world.setBlockInternal(x, y, z, (center ? Block.WATER : Block.STONE).getId(), 0);
                    }
                }
            }
            Husk husk = new Husk(10.5f, 1f, 10.5f, false, false);
            world.addLivingEntity(husk);
            for (int tick = 0; tick < 899; tick++) {
                world.updateEntities(TICK);
            }
            require(world.entityCount() == 1 && world.entityAt(0) == husk,
                    "Chưa đủ 45 giây thì chưa thể đổi Husk thành Zombie");
            for (int tick = 0; tick < 3; tick++) {
                world.updateEntities(TICK);
            }
            require(world.entityCount() == 1 && world.entityAt(0).getClass() == Zombie.class,
                    "Husk chìm đầu đủ lâu phải được thay bằng một Zombie trưởng thành");
        } finally {
            world.dispose();
        }
    }

    /** Enderman dùng chu kỳ 2 tick (0,10s) với xác suất nhặt 1/20 và đặt 1/2000 trên 1 ô ngẫu nhiên. */
    private static void checkEndermanBlockRates() {
        require(Enderman.PICKUP_CHANCE_DIVISOR == 20 && Enderman.PLACE_CHANCE_DIVISOR == 2000
                        && Math.abs(Enderman.BLOCK_CHECK_INTERVAL - 0.10f) < 0.0001f,
                "Hằng số nhặt/đặt khối của Enderman phải khớp chuẩn 1/20 và 1/2000 mỗi 2 tick");
        World world = World.empty();
        try {
            // Bao tường đá ở bán kính 3 để Enderman đứng yên và mọi ô -2..+2 quanh chân đều hợp lệ.
            for (int x = 7; x <= 13; x++) {
                for (int z = 7; z <= 13; z++) {
                    world.setBlockInternal(x, 0, z, Block.STONE.getId(), 0);
                    boolean rim = x == 7 || x == 13 || z == 7 || z == 13;
                    if (rim) {
                        for (int y = 1; y <= 4; y++) {
                            world.setBlockInternal(x, y, z, Block.STONE.getId(), 0);
                        }
                    } else if (!(x == 10 && z == 10)) {
                        world.setBlockInternal(x, 1, z, Block.GRASS.getId(), 0);
                    }
                }
            }
            Enderman enderman = new Enderman(10.5f, 1f, 10.5f);
            for (int tick = 0; tick < 4000 && enderman.heldBlock() == Block.AIR; tick++) {
                enderman.update(world, TICK);
            }
            require(enderman.heldBlock() == Block.GRASS, "Enderman phải nhặt được khối cỏ quanh chân");

            // Dọn sạch cỏ còn lại để có nền đá trống cho phép đặt khối (giữ khoảng trống -2..+2).
            for (int x = 8; x <= 12; x++) {
                for (int z = 8; z <= 12; z++) {
                    world.setBlockInternal(x, 1, z, Block.AIR.getId(), 0);
                }
            }
            for (int tick = 0; tick < 120_000 && enderman.heldBlock() != Block.AIR; tick++) {
                enderman.update(world, TICK);
            }
            require(enderman.heldBlock() == Block.AIR,
                    "Enderman đang cầm khối phải đặt lại được xuống nền rắn theo xác suất 1/2000 mỗi 2 tick");
        } finally {
            world.dispose();
        }
    }

    /** Vụ nổ theo mồi 1,5 giây làm mất khối có thể phá nhưng giữ nền bất hoại và chất lỏng. */
    private static void checkCreeperFuseAndBlockDamage() {
        World world = World.empty();
        try {
            floor(world, 0);
            world.registerPlayer(new Player(13.1f, 1f, 10.5f));
            world.setBlockInternal(10, 1, 9, Block.STONE.getId(), 0);
            world.setBlockInternal(9, 1, 10, Block.BEDROCK.getId(), 0);
            world.setBlockInternal(10, 1, 11, Block.WATER.getId(), 0);
            world.setBlockInternal(11, 1, 11, Block.LAVA.getId(), 0);
            Creeper creeper = new Creeper(10.5f, 1f, 10.5f);
            for (int tick = 0; tick < 29; tick++) {
                creeper.update(world, TICK);
            }
            require(creeper.isAlive() && creeper.mobVisualState().fuseProgress() > 0f
                            && world.getBlock(10, 1, 9) == Block.STONE.getId(), "Mồi cháy đủ 1,5 giây mới nổ");
            creeper.update(world, TICK);
            require(!creeper.isAlive() && world.getBlock(10, 1, 9) == Block.AIR.getId(),
                    "Creeper tự hủy và nổ vỡ đá gần tâm đúng nhịp");
            require(world.getBlock(9, 1, 10) == Block.BEDROCK.getId()
                            && world.getBlock(10, 1, 11) == Block.WATER.getId()
                            && world.getBlock(11, 1, 11) == Block.LAVA.getId(),
                    "Vụ nổ giữ Bedrock, Water và Lava");
        } finally {
            world.dispose();
        }
    }

    /** Mục tiêu rời bán kính 7 block trước khi hết mồi làm Creeper xẹp và ngừng nổ. */
    private static void checkCreeperCancelsFuse() {
        World world = World.empty();
        try {
            floor(world, 0);
            Player player = new Player(13.1f, 1f, 10.5f);
            world.registerPlayer(player);
            Creeper creeper = new Creeper(10.5f, 1f, 10.5f);
            creeper.update(world, TICK);
            require(creeper.mobVisualState().fuseProgress() > 0f, "Áp sát trong 3 block phải bắt đầu mồi");
            player.setPosition(19.5f, 1f, 10.5f);
            creeper.update(world, TICK);
            require(creeper.isAlive() && creeper.mobVisualState().fuseProgress() == 0f,
                    "Ra ngoài 7 block phải hủy mồi trong lượt kế tiếp");
        } finally {
            world.dispose();
        }
    }

    /** Nhện chỉ phát hiện người chơi khi trở trời tối và nhảy vồ hướng mục tiêu từ khoảng 3 block. */
    private static void checkSpiderStanceAndLeap() {
        World world = World.empty();
        try {
            floor(world, 0);
            Player player = new Player(13.5f, 1f, 10.5f);
            world.registerPlayer(player);
            Spider spider = new Spider(10.5f, 1f, 10.5f);
            spider.update(world, TICK);
            require(spider.attitude() == MobAttitude.NEUTRAL && spider.target() == null,
                    "Nhện ngoài trời ban ngày phải trung lập");
            world.worldTime().update(850);
            spider.update(world, TICK);
            require(spider.attitude() == MobAttitude.HOSTILE && spider.target() == player,
                    "Nhện ban đêm phải phát hiện người chơi");
            require(spider.y() > 1.05f && spider.x() > 10.5f, "Nhện ở cự ly 3 block phải bật nhảy về phía đích");
        } finally {
            world.dispose();
        }
    }

    /** Nhện đang di chuyển vào một vách cao vượt khả năng nhảy phải leo lên vách. */
    private static void checkSpiderClimbsWall() {
        World world = World.empty();
        try {
            floor(world, 0);
            for (int y = 1; y <= 5; y++) {
                for (int z = 8; z <= 12; z++) {
                    world.setBlockInternal(12, y, z, Block.STONE.getId(), 0);
                }
            }
            world.worldTime().update(850);
            world.registerPlayer(new Player(13.5f, 5f, 10.5f));
            Spider spider = new Spider(11.2f, 1f, 10.5f);
            for (int tick = 0; tick < 20; tick++) {
                spider.update(world, TICK);
            }
            require(spider.y() > 2f, "Nhện phải leo vách hơn một block khi ép sát để đuổi mục tiêu");
        } finally {
            world.dispose();
        }
    }

    /** Dựng nền phẳng dưới khu vực đấu và đuổi thử. */
    private static void floor(World world, int y) {
        for (int x = 4; x <= 48; x++) {
            for (int z = 5; z <= 15; z++) {
                world.setBlockInternal(x, y, z, Block.GRASS.getId(), 0);
            }
        }
    }

    /** So sánh kích thước hoặc chỉ số với sai số dấu phẩy động nhỏ. */
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.001f, message + " (" + actual + ")");
    }

    /** Nêu đúng trạng thái sai khi bộ kiểm tra thất bại. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
