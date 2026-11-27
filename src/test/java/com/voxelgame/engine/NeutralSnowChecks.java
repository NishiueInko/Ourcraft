package com.voxelgame.engine;

import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.client.render.entity.MobVisualState;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.mob.animal.PolarBear;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.mob.monster.Stray;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.entity.projectile.ArrowEntity;
import com.voxelgame.world.entity.projectile.ArrowType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.math.Vector3;

/** Kiểm tra hành vi của dê, gấu Bắc Cực và sói bằng nhịp mô phỏng CPU. */
final class NeutralSnowChecks {
    /** Mô phỏng theo bước cố định 20 Hz. */
    private static final float TICK = 0.05f;

    /** Chạy các tình huống chiến đấu và di chuyển riêng của ba loài. */
    static void run() {
        checkGoatFallAndRam();
        checkGoatJumpsGap();
        checkPolarBearProtectsCub();
        checkWolfDefendsPackAndHunts();
        checkWolfChangesEscapedPrey();
        checkWolfRetaliatesThroughCombat();
        checkWolfTiltAndShake();
    }

    /** Bố trí một hố trống rộng ba block và giữ dê trên mỏm đá khi nó chưa lấy đà. */
    private static void checkGoatJumpsGap() {
        World world = World.empty();
        try {
            for (int x = 9; x <= 20; x++) {
                if (x >= 12 && x <= 14) {
                    continue;
                }
                for (int z = 3; z <= 17; z++) {
                    // Bờ xuất phát hẹp theo cả hai trục để mọi đích nhảy đều ở phía bên kia hố.
                    if (x >= 15 || z >= 9 && z <= 11) {
                        world.setBlockInternal(x, 0, z, Block.STONE.getId(), 0);
                    }
                }
            }
            MobEntity goat = create(MobType.GOAT, 10.5f, 1f, 10.5f);
            float peak = goat.y();
            for (int tick = 0; tick < 3200; tick++) {
                if (goat.mobVisualState().pose() != MobVisualState.Pose.HOP && goat.isOnGround()) {
                    if (goat.x() >= 15f) {
                        break;
                    }
                    goat.setPosition(10.5f, 1f, 10.5f);
                }
                goat.update(world, TICK);
                peak = Math.max(peak, goat.y());
            }
            require(goat.x() >= 15f && goat.isOnGround() && peak > 3f && peak <= 11.01f,
                    "Dê phải bật cao và tiếp đất ở phía bên kia hố trong giới hạn 10 block");
        } finally {
            world.dispose();
        }
    }

    /** Dê trừ 10 block quãng rơi và cú húc định kỳ đánh trúng mục tiêu đứng yên. */
    private static void checkGoatFallAndRam() {
        World world = World.empty();
        try {
            floor(world);
            MobEntity goat = create(MobType.GOAT, 10.5f, 21f, 10.5f);
            for (int tick = 0; tick < 120 && !goat.isOnGround(); tick++) {
                goat.update(world, TICK);
            }
            require(goat.isOnGround() && Math.abs(goat.getHealth() - 3f) < 0.05f,
                    "Dê rơi từ 20 block phải chỉ mất 7 HP");

            goat = create(MobType.GOAT, 10.5f, 1f, 10.5f);
            Player player = new Player(14.5f, 1f, 10.5f);
            world.registerPlayer(player);
            for (int tick = 0; tick < 6320 && player.getHealth() == 20f; tick++) {
                if (goat.mobVisualState().pose() != MobVisualState.Pose.RAM && goat.isOnGround()) {
                    goat.setPosition(10.5f, 1f, 10.5f);
                }
                goat.update(world, TICK);
            }
            player.attachControls(new Camera());
            player.update(world, TICK);
            require(Math.abs(player.getHealth() - 18f) < 0.001f && player.getPosition().x > 15f,
                    "Cú húc định kỳ phải gây 2 HP và đẩy mạnh người chơi khỏi dê");
        } finally {
            world.dispose();
        }
    }

    /** Gấu trưởng thành cảnh báo khi gấu con bị tiếp cận và gọi cả nhóm khi bị đánh. */
    private static void checkPolarBearProtectsCub() {
        World world = World.empty();
        try {
            floor(world);
            Player player = new Player(15.5f, 1f, 10.5f);
            world.registerPlayer(player);
            MobEntity bear = create(MobType.POLAR_BEAR, 10.5f, 1f, 10.5f);
            PolarBear cub = new PolarBear(13.5f, 1f, 10.5f, true);
            world.addLivingEntity(cub);
            require(cub.isBaby() && Math.abs(cub.getHeight() - 0.7f) < 0.001f,
                    "Gấu con phải có thân cao 0,7 block");
            bear.update(world, TICK);
            require(bear.target() == player && bear.mobVisualState().pose() == MobVisualState.Pose.REAR,
                    "Gấu phải đứng dựng lên khi người chơi áp sát gấu con");
            MobEntity ally = create(MobType.POLAR_BEAR, 19.5f, 1f, 10.5f);
            world.addLivingEntity(bear);
            world.addLivingEntity(ally);
            bear.damage(world, 1f, DamageSource.MELEE, player, 0f, 0f);
            require(ally.target() == player && cub.target() == null,
                    "Gấu trưởng thành trả đũa theo nhóm còn gấu con bỏ chạy");
        } finally {
            world.dispose();
        }
    }

    /** Sói săn cung thủ ngoài tự nhiên và đổi mắt đỏ cùng đồng loại khi bị người chơi đánh. */
    private static void checkWolfDefendsPackAndHunts() {
        World world = World.empty();
        try {
            floor(world);
            Player player = new Player(10.5f, 1f, 16.5f);
            world.registerPlayer(player);
            MobEntity wolf = create(MobType.WOLF, 10.5f, 1f, 10.5f);
            MobEntity ally = create(MobType.WOLF, 26.5f, 1f, 10.5f);
            MobEntity outsider = create(MobType.WOLF, 27f, 1f, 10.5f);
            MobEntity stray = create(MobType.STRAY, 11.5f, 1f, 10.5f);
            world.addLivingEntity(wolf);
            world.addLivingEntity(ally);
            world.addLivingEntity(outsider);
            world.addLivingEntity(stray);
            wolf.update(world, TICK);
            require(wolf.target() == stray && stray.getHealth() == stray.getMaxHealth() - 4f,
                    "Sói phải chủ động đánh Stray gây 4 HP");
            wolf.damage(world, 1f, DamageSource.MELEE, player, 0f, 0f);
            require(ally.target() == player && outsider.target() == null && wolf.target() == player
                            && wolf.mobVisualState().appearance() == MobAppearance.WOLF_SNOWY_ANGRY
                            && ally.mobVisualState().appearance() == MobAppearance.WOLF_SNOWY_ANGRY,
                    "Chỉ các con sói trong 16 block phải cùng đổi mắt đỏ và tấn công người chơi");
        } finally {
            world.dispose();
        }
    }

    /** Sói chuyển sang mồi khác khi con mồi cũ thoát tầm và vẫn giữ cơn giận với người chơi. */
    private static void checkWolfChangesEscapedPrey() {
        World world = World.empty();
        try {
            floor(world);
            MobEntity wolf = create(MobType.WOLF, 8.5f, 1f, 10.5f);
            MobEntity rabbit = create(MobType.SNOW_RABBIT, 11.5f, 1f, 10.5f);
            MobEntity skeleton = create(MobType.SKELETON, 11.5f, 1f, 12.5f);
            world.addEntity(wolf);
            world.addEntity(rabbit);
            wolf.update(world, TICK);
            require(wolf.target() == rabbit, "Sói phải nhận con thỏ gần làm mồi đầu tiên");
            rabbit.setPosition(36.5f, 1f, 10.5f);
            world.addEntity(skeleton);
            for (int tick = 0; tick < 20; tick++) {
                wolf.update(world, TICK);
            }
            require(wolf.target() == skeleton,
                    "Sói phải chuyển sang con mồi gần khi mục tiêu cũ thoát khỏi tầm đuổi");
            Player player = new Player(80.5f, 1f, 10.5f);
            wolf.damage(world, 1f, DamageSource.MELEE, player, 0f, 0f);
            for (int tick = 0; tick < 20; tick++) {
                wolf.update(world, TICK);
            }
            require(wolf.target() != player, "Sói phải bỏ mục tiêu người chơi đã chạy quá xa");
        } finally {
            world.dispose();
        }
    }

    /** Mũi tên giữ tác nhân và cả đàn có thể đuổi, cắn người chơi đã đánh sói. */
    private static void checkWolfRetaliatesThroughCombat() {
        World world = World.empty();
        try {
            floor(world);
            Player player = new Player(7.5f, 1f, 10.5f);
            world.registerPlayer(player);
            MobEntity wolf = create(MobType.WOLF, 9.5f, 1f, 10.5f);
            MobEntity ally = create(MobType.WOLF, 11.5f, 1f, 10.5f);
            world.addLivingEntity(wolf);
            world.addLivingEntity(ally);
            player.attack(world, wolf, Vector3.X);
            require(wolf.lastAttacker() == player && wolf.target() == player && ally.target() == player,
                    "Đòn tay không phải kích động cả đàn");
            wolf.setPosition(9.5f, 1f, 10.5f);
            for (int tick = 0; tick < 24; tick++) {
                wolf.update(world, TICK);
            }
            require(player.getHealth() < player.getMaxHealth(), "Sói phải đuổi và cắn người chơi");
            wolf.setPosition(9.5f, 1f, 10.5f);
            ArrowEntity arrow = new ArrowEntity(player, 8.5f, 1.5f, 10.5f,
                    24f, 0f, 0f, ArrowType.NORMAL, 1f);
            float beforeArrow = wolf.getHealth();
            arrow.update(world, TICK);
            require(wolf.getHealth() < beforeArrow && wolf.lastAttacker() == player && wolf.angerTimer() >= 20f && wolf.angerTimer() < 40f,
                    "Tên của người chơi phải làm mới thời gian giận");
        } finally {
            world.dispose();
        }
    }

    /** Sói bình thản nghiêng đầu nhìn người gần đó và lắc mình sau khi lên bờ. */
    private static void checkWolfTiltAndShake() {
        World world = World.empty();
        try {
            floor(world);
            Player player = new Player(14.5f, 1f, 10.5f);
            world.registerPlayer(player);
            MobEntity wolf = create(MobType.WOLF, 10.5f, 1f, 10.5f);
            wolf.update(world, TICK);
            require(wolf.mobVisualState().pose() == MobVisualState.Pose.TILT_HEAD,
                    "Sói bình thản phải nghiêng đầu khi người chơi ở gần");
            world.setBlockInternal(10, 1, 10, Block.WATER.getId(), 0);
            wolf.update(world, TICK);
            wolf.setPosition(12.5f, 1f, 10.5f);
            for (int tick = 0; tick < 20 && wolf.mobVisualState().pose() != MobVisualState.Pose.SHAKE; tick++) {
                wolf.update(world, TICK);
            }
            require(wolf.mobVisualState().pose() == MobVisualState.Pose.SHAKE,
                    "Sói lên bờ phải lắc người để rũ nước");
        } finally {
            world.dispose();
        }
    }

    /** Tạo qua danh mục để đồng thời kiểm tra các hàm tạo đã được nối. */
    private static MobEntity create(MobType type, float x, float y, float z) {
        MobEntity mob = type.create(x, y, z);
        require(mob != null, "Chưa đăng ký hàm tạo cho " + type);
        return mob;
    }

    /** Dựng mặt đứng phẳng và kín trong khu vực thử nghiệm. */
    private static void floor(World world) {
        for (int x = 2; x < 38; x++) {
            for (int z = 2; z < 24; z++) {
                world.setBlockInternal(x, 0, z, Block.STONE.getId(), 0);
            }
        }
    }

    /** Báo chính xác hành vi sai khi kiểm tra thất bại. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
