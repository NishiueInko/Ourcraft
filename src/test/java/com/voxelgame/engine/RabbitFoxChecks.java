package com.voxelgame.engine;

import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.client.render.entity.MobVisualState;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.mob.animal.Rabbit;
import com.voxelgame.world.entity.mob.animal.SnowFox;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

/** Kiểm tra biến thể, cách đi, hành vi ngủ, săn mồi và mang vật phẩm bằng CPU. */
final class RabbitFoxChecks {
    /** Chạy các trường hợp trong thế giới phẳng, với thời gian và tư thế người chơi được đặt trực tiếp. */
    static void run() {
        checkRabbitVariants();
        checkRabbitHoppingAndTemptation();
        checkRabbitThreatRanges();
        checkFoxSleepAndSneak();
        checkFoxPounce();
        checkFoxMissesOnThinSnow();
        checkFoxCarrying();
    }

    /** Kiểm tra toàn bộ miền bốc màu với bước 1%, cộng ngưỡng phân chia chính xác. */
    private static void checkRabbitVariants() {
        int white = 0;
        int splotched = 0;
        for (int i = 0; i < 100; i++) {
            float roll = i / 100f;
            require(Rabbit.appearanceFor(MobType.RABBIT, roll) == MobAppearance.RABBIT_GOLD,
                    "Thỏ sa mạc luôn dùng lông vàng cát");
            MobAppearance snow = Rabbit.appearanceFor(MobType.SNOW_RABBIT, roll);
            white += snow == MobAppearance.RABBIT_WHITE ? 1 : 0;
            splotched += snow == MobAppearance.RABBIT_WHITE_SPLOTCHED ? 1 : 0;
        }
        require(white == 80 && splotched == 20, "Thỏ tuyết có đúng tỉ lệ 80% trắng và 20% đốm");
        require(new Rabbit(0, 0, 0).mobVisualState().appearance() == MobAppearance.RABBIT_GOLD
                && Rabbit.snow(0, 0, 0).type() == MobType.SNOW_RABBIT,
                "Hai lối khởi tạo phải chọn đúng cấu hình nơi sinh");
    }

    /** Thỏ dùng nhảy gần hai block và đổi từ tháo chạy sang đi theo người cầm bồ công anh. */
    private static void checkRabbitHoppingAndTemptation() {
        World world = arena(false);
        try {
            Rabbit rabbit = new Rabbit(100.5f, 20f, 100.5f);
            Player player = new Player(106.5f, 20f, 100.5f);
            world.registerPlayer(player);
            float highest = rabbit.y();
            boolean hopped = false;
            for (int i = 0; i < 40; i++) {
                rabbit.update(world, 0.025f);
                highest = Math.max(highest, rabbit.y());
                hopped |= rabbit.mobVisualState().pose() == MobVisualState.Pose.HOP;
            }
            require(hopped && highest > 21.7f && highest < 22.2f,
                    "Thỏ phải di chuyển bằng vòng nhảy gần hai block");
            require(rabbit.x() < 99.7f, "Thỏ phải tránh người chơi trong tám block khi không có mồi");

            require(player.getHotbar().addItem(Block.DANDELION, 1), "Cần đặt bồ công anh vào Hotbar");
            player.getHotbar().selectSlot(0);
            Rabbit following = new Rabbit(100.5f, 20f, 100.5f);
            tick(world, following, 1.1);
            require(following.x() > 101.3f, "Thỏ phải tiến về người chơi đang cầm bồ công anh");
        } finally {
            world.dispose();
        }
    }

    /** Sói cách chín block và quái thù địch cách ba block đều làm thỏ tháo chạy. */
    private static void checkRabbitThreatRanges() {
        World world = arena(false);
        try {
            Rabbit rabbit = new Rabbit(100.5f, 20f, 100.5f);
            MobEntity wolf = new MobEntity(MobType.WOLF, 109.5f, 20f, 100.5f) { };
            world.addLivingEntity(wolf);
            tick(world, rabbit, 0.6);
            require(rabbit.x() < 100f, "Thỏ phải nhận ra sói ở ngoài bán kính sợ người chơi");
            wolf.setPosition(150.5f, 20f, 100.5f);
            world.addLivingEntity(new MobEntity(MobType.ZOMBIE, 103.5f, 20f, 100.5f) { });
            Rabbit againstMonster = new Rabbit(100.5f, 20f, 100.5f);
            tick(world, againstMonster, 0.6);
            require(againstMonster.x() < 100f, "Thỏ phải tránh quái ở trong bốn block");
        } finally {
            world.dispose();
        }
    }

    /** Cáo tìm tới tán vân sam, giữ giấc ngủ trước người cúi rón rén và tỉnh dậy để bỏ chạy. */
    private static void checkFoxSleepAndSneak() {
        World world = arena(false);
        try {
            world.setBlock(103, 22, 100, Block.SPRUCE_LEAVES.getId());
            SnowFox fox = new SnowFox(100.5f, 20f, 100.5f);
            tick(world, fox, 2.2);
            require(fox.isSleeping() && fox.mobVisualState().appearance() == MobAppearance.SNOW_FOX_SLEEP,
                    "Cáo phải tìm tán vân sam xung quanh và dùng diện mạo ngủ");
            float sleepingX = fox.x();
            float sleepingZ = fox.z();
            QuietPlayer player = new QuietPlayer(sleepingX + 10f, 20f, sleepingZ);
            world.registerPlayer(player);
            tick(world, fox, 0.35);
            require(fox.isSleeping() && fox.x() == sleepingX && fox.z() == sleepingZ,
                    "Người rón rén tới gần không được làm cáo thức hay chạy trốn");
            player.sneaking = false;
            tick(world, fox, 0.8);
            require(!fox.isSleeping() && fox.mobVisualState().appearance() == MobAppearance.SNOW_FOX,
                    "Người đứng thẳng trong mười hai block phải làm cáo thức");
            float dx = fox.x() - sleepingX;
            float dz = fox.z() - sleepingZ;
            require(dx * dx + dz * dz > 0.4f, "Cáo phải chạy khỏi vị trí ngủ khi phát hiện người");
        } finally {
            world.dispose();
        }
    }

    /** Dùng một thỏ thật đứng phẳng để xác minh lấy đà, vòng cung và đúng sát thương khi vồ. */
    private static void checkFoxPounce() {
        World world = arena(true);
        try {
            SnowFox fox = new SnowFox(100.5f, 20f, 100.5f);
            Rabbit prey = Rabbit.snow(104.5f, 20f, 100.5f);
            world.addLivingEntity(prey);
            boolean crouched = false;
            boolean leapt = false;
            float highest = fox.y();
            for (int i = 0; i < 90 && prey.getHealth() == 3f; i++) {
                fox.update(world, 0.025f);
                crouched |= fox.mobVisualState().pose() == MobVisualState.Pose.CROUCH;
                leapt |= fox.mobVisualState().pose() == MobVisualState.Pose.POUNCE;
                highest = Math.max(highest, fox.y());
            }
            require(crouched && leapt && highest > 21.4f && prey.getHealth() == 1f,
                    "Cáo phải lấy đà, nhảy thành vòng cung và gây hai HP khi vồ trúng thỏ");
        } finally {
            world.dispose();
        }
    }

    /** Dời mồi sau khi cáo nhảy để cú vồ hụt kết thúc trong lớp tuyết mỏng. */
    private static void checkFoxMissesOnThinSnow() {
        World world = arena(true);
        try {
            for (int x = 103; x <= 105; x++) {
                world.setBlock(x, 20, 100, Block.SNOW_LAYER.getId(), 0);
            }
            SnowFox fox = new SnowFox(100.5f, 20f, 100.5f);
            Rabbit prey = Rabbit.snow(104.5f, 20.125f, 100.5f);
            world.addLivingEntity(prey);
            boolean moved = false;
            boolean faceplanted = false;
            for (int i = 0; i < 84 && !faceplanted; i++) {
                fox.update(world, 0.025f);
                if (!moved && fox.mobVisualState().pose() == MobVisualState.Pose.POUNCE) {
                    prey.setPosition(104.5f, 20f, 104.5f);
                    moved = true;
                }
                faceplanted = fox.mobVisualState().pose() == MobVisualState.Pose.FACEPLANT;
            }
            require(moved && faceplanted && prey.getHealth() == 3f,
                    "Cáo vồ hụt phải chúi đầu vào tuyết mỏng mà không gây sát thương");
            tick(world, fox, 0.8);
            require(fox.mobVisualState().pose() != MobVisualState.Pose.FACEPLANT,
                    "Cáo phải đứng dậy sau khi chúi xuống tuyết");
        } finally {
            world.dispose();
        }
    }

    /** Cáo nhặt đúng một vật phẩm và trả ra thế giới khi chết. */
    private static void checkFoxCarrying() {
        World world = arena(true);
        try {
            SnowFox fox = new SnowFox(100.5f, 20f, 100.5f);
            world.spawnDroppedItem(new ItemEntity(101f, 20f, 100.5f, Block.DANDELION));
            world.spawnDroppedItem(new ItemEntity(101.4f, 20f, 100.5f, Block.DIRT));
            tick(world, fox, 0.35);
            require(fox.mobVisualState().heldBlock() == Block.DANDELION,
                    "Cáo nhặt vật phẩm gần nhất để ngậm và chỉ giữ một vật phẩm");
            fox.damage(world, 20f, DamageSource.MELEE, null, 0f, 0f);
            tick(world, fox, 0.05);
            require(fox.mobVisualState().heldBlock() == null && countGroundItems(world, Block.DANDELION) == 1
                    && countGroundItems(world, Block.DIRT) == 1,
                    "Cáo chết trả lại đúng vật phẩm đã nhặt và giữ nguyên vật phẩm khác");
        } finally {
            world.dispose();
        }
    }

    /** Đếm các vật phẩm vẫn tồn tại trên mặt đất theo loại block. */
    private static int countGroundItems(World world, Block block) {
        int count = 0;
        for (int i = 0; i < world.entityCount(); i++) {
            if (world.entityAt(i) instanceof ItemEntity item && !item.isCollected() && item.getBlock() == block) {
                count++;
            }
        }
        return count;
    }

    /** Tạo mặt nền đủ rộng cho các nhịp tìm vị trí hợp lệ. */
    private static World arena(boolean night) {
        World world = World.empty();
        for (int x = 87; x <= 119; x++) {
            for (int z = 87; z <= 113; z++) {
                world.setBlock(x, 19, z, Block.STONE.getId());
            }
        }
        if (night) {
            world.worldTime().update(850);
        }
        return world;
    }

    /** Chỉ cập nhật cá thể đang kiểm tra để mục tiêu thử đứng nguyên vị trí. */
    private static void tick(World world, MobEntity mob, double seconds) {
        int frames = (int) Math.round(seconds / 0.025);
        for (int i = 0; i < frames; i++) {
            mob.update(world, 0.025f);
        }
    }

    /** Phát lỗi có ngữ cảnh khi kết quả không khớp hành vi đòi hỏi. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Người chơi đứng yên có trạng thái Sneak được đặt trực tiếp cho tình huống kiểm thử. */
    private static final class QuietPlayer extends Player {
        private boolean sneaking = true;

        /** Khởi tạo đúng hình học của người chơi thật trên nền thử. */
        private QuietPlayer(float x, float y, float z) {
            super(x, y, z);
        }

        /** Trả trạng thái cúi của nhịp thử hiện tại. */
        @Override
        public boolean isSneaking() {
            return sneaking;
        }
    }
}
