package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.StatusEffectType;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobType;
import com.voxelgame.entity.mob.Parched;
import com.voxelgame.entity.mob.Skeleton;
import com.voxelgame.entity.mob.Stray;
import com.voxelgame.entity.mob.Witch;
import com.voxelgame.entity.projectile.ArrowEntity;
import com.voxelgame.entity.projectile.PotionType;
import com.voxelgame.entity.projectile.ProjectileEntity;
import com.voxelgame.entity.projectile.ThrownPotionEntity;

/** Kiểm tra AI đánh xa, nhắm mục tiêu và hiệu ứng của các mob bước bốn trên CPU. */
final class RangedHostileChecks {
    /** Chạy các trường hợp bắn từ mặt đất, từ thú cưỡi, chọn thuốc và phòng vệ. */
    static void run() {
        checkStrafingAndMountedFire();
        checkArrowEffects(false);
        checkArrowEffects(true);
        checkWitchPotionPriority();
        checkWitchThrows();
        checkWitchDrinks();
        checkWitchWaterAndSpeed();
    }

    /** Skeleton lùi trước người chơi áp sát và có thể bắn khi đang ngồi trên lưng nhện. */
    private static void checkStrafingAndMountedFire() {
        World world = arena();
        try {
            Skeleton walker = new Skeleton(100.5f, 20f, 100.5f);
            Player player = new Player(103.5f, 20f, 100.5f);
            world.registerPlayer(player);
            for (int i = 0; i < 8; i++) {
                walker.update(world, 0.05f);
            }
            require(walker.x() < 100.1f, "Skeleton phải lùi khi mục tiêu cách dưới bốn block");
            require(walker.mobVisualState().pose() == MobVisualState.Pose.AIM_BOW,
                    "Skeleton phải giương cung trong khi di chuyển");
            Player distant = new Player(109.5f, 20f, 105.5f);
            world.registerPlayer(distant);
            Skeleton strafe = new Skeleton(100.5f, 20f, 105.5f);
            for (int i = 0; i < 8; i++) {
                strafe.update(world, 0.05f);
            }
            require(Math.abs(strafe.z() - 105.5f) > 0.3f, "Skeleton phải di chuyển ngang khi đã trong tầm bắn");
            Skeleton rider = new Skeleton(100.5f, 20f, 105.5f);
            TestMount mount = new TestMount(100.5f, 20f, 105.5f);
            world.addLivingEntity(mount);
            require(rider.tryMount(mount, 0), "Ghế của nhện thử phải nhận Skeleton");
            for (int i = 0; i < 20; i++) {
                rider.update(world, 0.05f);
            }
            require(projectile(world, rider, ArrowEntity.class) == null
                            && rider.mobVisualState().poseAmount() >= 0.9f,
                    "Cung đã căng nhưng chưa bắn khi chưa đủ 20 tick kể từ lúc bắt đầu kéo");
            rider.update(world, 0.05f);
            require(projectile(world, rider, ArrowEntity.class) != null && rider.getMount() == mount
                            && rider.mobVisualState().poseAmount() == 0f,
                    "Skeleton cưỡi nhện phải nhả dây đúng tick tạo tên");
        } finally {
            world.dispose();
        }
    }

    /** Đạn do AI tạo phải đến được người chơi và áp dụng hiệu ứng riêng của loài. */
    private static void checkArrowEffects(boolean parchedVariant) {
        World world = arena();
        try {
            Skeleton archer = parchedVariant ? new Parched(100.5f, 20f, 100.5f)
                    : new Stray(100.5f, 20f, 100.5f);
            Player player = new Player(109.5f, 20f, 100.5f);
            world.registerPlayer(player);
            world.addLivingEntity(archer);
            for (int i = 0; i < 30 && projectile(world, archer, ArrowEntity.class) == null; i++) {
                archer.update(world, 0.05f);
            }
            ArrowEntity arrow = projectile(world, archer, ArrowEntity.class);
            require(arrow != null, "Mob bắn cung phải tạo đạn trong khoảng giương cung đầu");
            for (int i = 0; i < 35 && player.getHealth() == 20f; i++) {
                arrow.update(world, 0.025f);
            }
            StatusEffectType effect = parchedVariant ? StatusEffectType.WEAKNESS : StatusEffectType.SLOWNESS;
            require(player.hasEffect(effect) && player.getHealth() < 20f,
                    "Mũi tên trúng phải gây sát thương và hiệu ứng " + effect);
            near(player.getEffectDuration(effect), 30f, "Tên đặc biệt phải gây hiệu ứng trong đúng ba mươi giây");
            float damage = 20f - player.getHealth();
            float damageMinimum = parchedVariant ? 2f : 3f;
            require(damage >= damageMinimum && damage <= damageMinimum + 1f,
                    "Sát thương tên phải nằm trong dải HP của biến thể");
            if (parchedVariant) {
                require(archer.getHealth() == 16f && !archer.applyEffect(StatusEffectType.WEAKNESS, 0, 30f)
                        && !archer.hasEffect(StatusEffectType.WEAKNESS),
                        "Parched phải có 16 HP và không thể bị suy yếu");
            }
        } finally {
            world.dispose();
        }
    }

    /** Xác minh thứ tự ưu tiên thuốc và cả hai phía của ngưỡng ngẫu nhiên. */
    private static void checkWitchPotionPriority() {
        Player target = new Player(105.5f, 20f, 100.5f);
        require(Witch.choosePotion(target, 8f, 0.9f) == PotionType.SLOWNESS,
                "Khoảng cách từ tám block phải ưu tiên Slowness");
        target.applyEffect(StatusEffectType.SLOWNESS, 0, 90f);
        require(Witch.choosePotion(target, 8f, 0.9f) == PotionType.POISON,
                "Khi đã chậm, mục tiêu đủ HP nhận Poison");
        require(Witch.choosePotion(target, 3f, 0.1f) == PotionType.POISON,
                "Poison phải được ưu tiên hơn Weakness");
        target.applyEffect(StatusEffectType.POISON, 0, 45f);
        require(Witch.choosePotion(target, 3f, 0.249f) == PotionType.WEAKNESS,
                "Weakness phải dùng khoảng xác suất nhỏ hơn 25%");
        require(Witch.choosePotion(target, 3f, 0.25f) == PotionType.HARMING,
                "Xác suất đúng 25% thuộc nhánh Harming");
        target.applyEffect(StatusEffectType.WEAKNESS, 0, 90f);
        require(Witch.choosePotion(target, 2f, 0f) == PotionType.HARMING,
                "Có hiệu ứng Weakness rồi thì dùng Harming");
    }

    /** Witch phải tạo lọ thuốc có vùng hiệu ứng khi thấy mục tiêu mặc dù không đứng trong tầm cận chiến. */
    private static void checkWitchThrows() {
        World world = arena();
        try {
            Witch witch = new Witch(100.5f, 20f, 100.5f);
            Player player = new Player(109.5f, 20f, 100.5f);
            world.registerPlayer(player);
            world.addLivingEntity(witch);
            for (int i = 0; i < 30 && projectile(world, witch, ThrownPotionEntity.class) == null; i++) {
                witch.update(world, 0.05f);
            }
            ThrownPotionEntity potion = projectile(world, witch, ThrownPotionEntity.class);
            require(potion != null, "Witch phải ném thuốc khi mục tiêu ở cách chín block");
            for (int i = 0; i < 80 && !player.hasEffect(StatusEffectType.SLOWNESS); i++) {
                potion.update(world, 0.025f);
            }
            require(player.hasEffect(StatusEffectType.SLOWNESS), "Lọ thuốc quan sát được phải làm chậm mục tiêu");
        } finally {
            world.dispose();
        }
    }

    /** Kháng phép đúng 85% và uống xong hồi đúng bốn HP, không tạo đòn đánh thay thế. */
    private static void checkWitchDrinks() {
        World world = arena();
        try {
            Witch witch = new Witch(100.5f, 20f, 100.5f);
            require(witch.damage(world, 6f, DamageSource.POTION_MAGIC, null, 0f, 0f),
                    "Witch vẫn phải nhận phần sát thương phép sau kháng");
            near(witch.getHealth(), 25.1f, "Witch phải giảm 85% sát thương phép");
            for (int i = 0; i < 11; i++) {
                witch.update(world, 0.05f);
            }
            witch.damage(world, 6f, DamageSource.MELEE, null, 0f, 0f);
            float injured = witch.getHealth();
            for (int i = 0; i < 24; i++) {
                witch.update(world, 0.05f);
            }
            near(witch.getHealth(), injured + 4f, "Sau khi uống thuốc, Witch phải hồi bốn HP");
            Witch burning = new Witch(104.5f, 20f, 100.5f);
            burning.ignite(10f);
            for (int i = 0; i < 35; i++) {
                burning.update(world, 0.05f);
            }
            require(burning.hasEffect(StatusEffectType.FIRE_RESISTANCE)
                    && !burning.damage(world, 4f, DamageSource.LAVA, null, 0f, 0f),
                    "Witch đang cháy phải uống kháng lửa và chặn sát thương Lava");

            world.setBlock(108, 20, 100, Block.LAVA.getId());
            Witch inLava = new Witch(108.5f, 20f, 100.5f);
            require(inLava.getFireTimer() == 0f, "Witch thử nghiệm chưa được châm lửa trước nhịp đầu");
            inLava.update(world, 0.05f);
            require(inLava.mobVisualState().pose() == MobVisualState.Pose.DRINK,
                    "Witch phải bắt đầu uống ngay khi thân chạm Lava");
            for (int i = 0; i < 34; i++) {
                inLava.update(world, 0.05f);
            }
            require(inLava.hasEffect(StatusEffectType.FIRE_RESISTANCE),
                    "Witch chạm Lava phải hoàn tất thuốc kháng lửa");
        } finally {
            world.dispose();
        }
    }

    /** Khi ngập đầu hoặc đuổi người chơi ở xa, Witch chọn đúng thuốc phòng vệ kéo dài. */
    private static void checkWitchWaterAndSpeed() {
        World world = arena();
        try {
            for (int y = 20; y <= 25; y++) {
                world.setBlock(104, y, 100, Block.WATER.getId());
            }
            Witch submerged = new Witch(104.5f, 20f, 100.5f);
            for (int i = 0; i < 35; i++) {
                submerged.update(world, 0.05f);
            }
            require(submerged.hasEffect(StatusEffectType.WATER_BREATHING)
                    && !submerged.damage(world, 2f, DamageSource.DROWNING, null, 0f, 0f),
                    "Witch bị ngập đầu phải uống thuốc thở dưới nước");

            Witch pursuing = new Witch(100.5f, 20f, 105.5f);
            world.registerPlayer(new Player(112.5f, 20f, 105.5f));
            for (int i = 0; i < 35; i++) {
                pursuing.update(world, 0.05f);
            }
            require(pursuing.hasEffect(StatusEffectType.SWIFTNESS) && pursuing.getMovementSpeedMultiplier() > 1f,
                    "Witch đang đuổi mục tiêu xa trên mười một block phải uống thuốc tăng tốc");
        } finally {
            world.dispose();
        }
    }

    /** Dùng vùng nền phẳng đủ chỗ né ngang mà không sinh ngẫu nhiên thêm thực thể. */
    private static World arena() {
        World world = World.empty();
        for (int x = 94; x <= 114; x++) {
            for (int z = 94; z <= 111; z++) {
                world.setBlock(x, 19, z, Block.STONE.getId());
            }
        }
        world.worldTime().update(850);
        return world;
    }

    /** Lấy đúng đạn do cá thể đang thử bắn ra khỏi danh sách của World. */
    private static <T extends ProjectileEntity> T projectile(World world, MobEntity owner, Class<T> projectileType) {
        for (int i = 0; i < world.entityCount(); i++) {
            Entity entity = world.entityAt(i);
            if (projectileType.isInstance(entity) && entity instanceof ProjectileEntity shot
                    && shot.owner() == owner) {
                return projectileType.cast(entity);
            }
        }
        return null;
    }

    /** So sánh số thực trong giới hạn nhiễu tính toán khung cập nhật. */
    private static void near(float actual, float expected, String reason) {
        require(Math.abs(actual - expected) < 0.001f, reason + ". Nhận " + actual + ", đợi " + expected);
    }

    /** Phát lỗi có ngữ cảnh khi điều kiện hồi quy không khớp. */
    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }

    /** Nhện đứng yên có một ghế để kiểm tra đường cập nhật khi cá thể bắn đang cưỡi. */
    private static final class TestMount extends MobEntity {
        /** Dựng hitbox nhện và một ghế sử dụng cơ chế cưỡi chung. */
        private TestMount(float x, float y, float z) {
            super(MobType.SPIDER, x, y, z, 0f, 1, false);
        }
    }
}
