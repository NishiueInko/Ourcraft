package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.StatusEffectType;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobType;
import com.voxelgame.entity.projectile.ArrowEntity;
import com.voxelgame.entity.projectile.ArrowType;
import com.voxelgame.entity.projectile.PotionType;
import com.voxelgame.entity.projectile.ThrownPotionEntity;

/** Kiểm tra đường đi và tác động của đạn đạo trên CPU. */
final class ProjectileChecks {
    /** Chạy các ca va chạm, phe, hiệu ứng và vòng đời. */
    static void run() {
        World world = World.empty();
        try {
            checkSweptArrowAndRetaliation(world);
            checkOcclusionAndDisposal(world);
            checkGrazingBlock(world);
            checkTippedArrowsAndDodge(world);
            checkPotionSplash(world);
            checkInstantPotions(world);
        } finally {
            world.dispose();
        }
    }

    /** Bán kính đạn chạm đá ở ô bên cạnh, còn cạnh trống quanh xương rồng vẫn thông. */
    private static void checkGrazingBlock(World world) {
        world.setBlockInternal(73, 11, 70, Block.STONE.getId(), 0);
        world.setBlockInternal(73, 11, 80, Block.CACTUS.getId(), 0);
        Dummy behindStone = new Dummy(75.5f, 10.5f, 69.97f, false, 0);
        Dummy behindCactus = new Dummy(75.5f, 10.5f, 79.97f, false, 0);
        world.addEntity(behindStone);
        world.addEntity(behindCactus);

        ArrowEntity stone = new ArrowEntity(null, 71.5f, 11.6f, 69.97f, 35f, 0f, 0f, ArrowType.NORMAL, 4f);
        stone.update(world, 0.15f);
        require(stone.isFinished(), "Tên sượt cạnh phải dừng trước khối chắn");
        near(stone.getPosition().x, 73f - 0.045f, "Tâm tên dừng cách mặt đá đúng bán kính tên");
        near(behindStone.getHealth(), 20f, "Đá chặn tên sượt mép trước mục tiêu");

        ArrowEntity cactus = new ArrowEntity(null, 71.5f, 11.6f, 79.97f, 35f, 0f, 0f, ArrowType.NORMAL, 4f);
        cactus.update(world, 0.15f);
        near(behindCactus.getHealth(), 16f, "Tên lọt qua mép trống của hộp va chạm xương rồng");

        ThrownPotionEntity potion = new ThrownPotionEntity(null, 71.5f, 11.6f, 69.9f, 35f, 0f, 0f,
                PotionType.SLOWNESS);
        potion.update(world, 0.05f);
        require(potion.isFinished(), "Lọ thuốc sượt khối phải vỡ theo bán kính riêng");

        world.setBlockInternal(73, 11, 65, Block.STONE.getId(), 0);
        ArrowEntity tangent = new ArrowEntity(null, 75.5f, 11.6f, 66f + 0.045f, -35f, 0f, 0f,
                ArrowType.NORMAL, 4f);
        tangent.update(world, 0.15f);
        require(tangent.isFinished(), "Biên ô phía dưới vẫn được tính khi hộp tên vừa chạm mặt");
        near(tangent.getPosition().x, 74f + 0.045f, "Tên bắn ngược dừng ở mặt đá gần");
    }

    /** Một đoạn dài vẫn chọn nạn nhân gần nhất bằng hộp và loại chủ, thú cưỡi, đồng phe. */
    private static void checkSweptArrowAndRetaliation(World world) {
        Dummy mount = new Dummy(11.3f, 10f, 10.5f, false, 1);
        MobEntity shooter = new MobEntity(MobType.SKELETON, 10.5f, 10f, 10.5f);
        MobEntity ally = new MobEntity(MobType.SKELETON, 12.8f, 10f, 10.5f);
        MobEntity victim = new MobEntity(MobType.ZOMBIE, 15.5f, 10f, 10.5f);
        Dummy far = new Dummy(17.5f, 10f, 10.5f, false, 0);
        require(shooter.tryMount(mount, 0), "Chuẩn bị thú cưỡi cho người bắn");
        world.addEntity(far);
        world.addEntity(ally);
        world.addEntity(victim);
        world.addEntity(mount);
        world.addEntity(shooter);
        ArrowEntity arrow = new ArrowEntity(shooter, 10.5f, 11.5f, 10.5f, 50f, 0f, 0f,
                ArrowType.NORMAL, 3.5f);
        arrow.update(world, 0.19f);
        require(arrow.isFinished() && victim.getHealth() < victim.getMaxHealth(),
                "Tên tốc độ cao phải chạm quái vật khác phe gần nhất");
        near(shooter.getHealth(), shooter.getMaxHealth(), "Tên không trúng người bắn");
        near(ally.getHealth(), ally.getMaxHealth(), "Tên không trúng đồng phe");
        near(mount.getHealth(), mount.getMaxHealth(), "Tên không trúng thú đang cưỡi");
        near(far.getHealth(), far.getMaxHealth(), "Tên dừng trước mục tiêu phía sau");
        require(shooter.target() == victim && victim.target() == shooter, "Hai quái khác phe phải trả đòn");
    }

    /** Hộp block chặn đường tới mục tiêu và đạn hết hạn kết thúc vòng đời. */
    private static void checkOcclusionAndDisposal(World world) {
        Dummy victim = new Dummy(25.5f, 10f, 20.5f, false, 0);
        world.addEntity(victim);
        world.setBlockInternal(24, 11, 20, Block.STONE.getId(), 0);
        ArrowEntity arrow = new ArrowEntity(null, 20.5f, 11.5f, 20.5f, 80f, 0f, 0f,
                ArrowType.NORMAL, 4f);
        arrow.update(world, 0.15f);
        require(arrow.isFinished() && arrow.getPosition().x <= 24.01f, "Tên dừng ở mặt tường");
        near(victim.getHealth(), 20f, "Tên không xuyên khối chắc");

        ArrowEntity expired = new ArrowEntity(null, 110.5f, 40f, 110.5f, 0f, 80f, 0f,
                ArrowType.NORMAL, 4f);
        expired.update(world, 7.9f);
        require(!expired.isFinished(), "Đạn trong thế giới vẫn bay trước khi hết tuổi thọ");
        expired.update(world, 0.11f);
        require(expired.isFinished() && expired.getPosition().y > 0f, "Đạn hết tuổi thọ phải kết thúc trong thế giới");

        ArrowEntity leaving = new ArrowEntity(null, 0.5f, 11.5f, 20.5f, -40f, 0f, 0f,
                ArrowType.NORMAL, 4f);
        int previousCount = world.entityCount();
        world.addEntity(leaving);
        world.updateEntities(0.05f);
        require(leaving.isFinished() && world.entityCount() == previousCount,
                "World phải thu hồi đạn vừa rời biên");
    }

    /** Tên đặc thù gắn đúng hiệu ứng; hook né được gọi trước mọi tác động. */
    private static void checkTippedArrowsAndDodge(World world) {
        Dummy slow = new Dummy(35.5f, 10f, 30.5f, false, 0);
        Dummy weak = new Dummy(35.5f, 10f, 34.5f, false, 0);
        Dodger dodger = new Dodger(35.5f, 10f, 38.5f);
        world.addEntity(slow);
        world.addEntity(weak);
        world.addEntity(dodger);
        new ArrowEntity(null, 31.5f, 11.4f, 30.5f, 30f, 0f, 0f, ArrowType.SLOWNESS, 3f).update(world, 0.2f);
        new ArrowEntity(null, 31.5f, 11.4f, 34.5f, 30f, 0f, 0f, ArrowType.WEAKNESS, 3f).update(world, 0.2f);
        new ArrowEntity(null, 31.5f, 11.4f, 38.5f, 30f, 0f, 0f, ArrowType.SLOWNESS, 3f).update(world, 0.2f);
        near(slow.getEffectDuration(StatusEffectType.SLOWNESS), 30f, "Tên Stray làm chậm 30 giây");
        require(slow.getVelocity().x > 0f, "Tên đẩy mục tiêu theo hướng bay");
        near(weak.getEffectDuration(StatusEffectType.WEAKNESS), 30f, "Tên Parched làm yếu 30 giây");
        require(dodger.attempts == 1 && !dodger.hasEffect(StatusEffectType.SLOWNESS), "Né chặn cả hiệu ứng tên");
        near(dodger.getHealth(), 20f, "Né xảy ra trước sát thương");
    }

    /** Vỡ trên block tạo vòng ảnh hưởng quanh điểm va chạm thật. */
    private static void checkPotionSplash(World world) {
        Dummy inside = new Dummy(46.5f, 10.5f, 42.5f, false, 0);
        Dummy outside = new Dummy(46.5f, 10.5f, 44.8f, false, 0);
        world.addEntity(inside);
        world.addEntity(outside);
        world.setBlockInternal(46, 11, 40, Block.STONE.getId(), 0);
        ThrownPotionEntity splash = new ThrownPotionEntity(null, 40.5f, 11.7f, 40.5f, 38f, 0f, 0f,
                PotionType.SLOWNESS);
        splash.update(world, 0.2f);
        require(splash.isFinished(), "Lọ thuốc vỡ trên mặt khối");
        near(inside.getEffectDuration(StatusEffectType.SLOWNESS), 90f, "Làm chậm trong bán kính bốn block");
        require(!outside.hasEffect(StatusEffectType.SLOWNESS), "Bên ngoài bán kính không nhận thuốc");

        Dummy poison = new Dummy(52.5f, 10f, 41.5f, false, 0);
        Dummy weakness = new Dummy(52.5f, 10f, 48.5f, false, 0);
        world.addEntity(poison);
        world.addEntity(weakness);
        tossAt(world, null, 52.5f, 41.5f, PotionType.POISON);
        tossAt(world, null, 52.5f, 48.5f, PotionType.WEAKNESS);
        near(poison.getEffectDuration(StatusEffectType.POISON), 45f, "Thuốc độc kéo dài 45 giây");
        near(weakness.getEffectDuration(StatusEffectType.WEAKNESS), 90f, "Thuốc yếu kéo dài 90 giây");
    }

    /** Thuốc tức thời đổi dấu theo undead, giảm độ mạnh khi vỡ xa hơn. */
    private static void checkInstantPotions(World world) {
        Dummy direct = new Dummy(66.5f, 10f, 58.5f, false, 0);
        Dummy near = new Dummy(66.5f, 10f, 60.5f, false, 0);
        Dummy undead = new Dummy(66.5f, 10f, 56.8f, true, 0);
        Dummy owner = new Dummy(62.5f, 10f, 58.5f, false, 0);
        undead.damage(world, 8f, DamageSource.FALL, null, 0f, 0f);
        world.addEntity(direct);
        world.addEntity(near);
        world.addEntity(undead);
        world.addEntity(owner);
        tossAt(world, owner, 66.5f, 58.5f, PotionType.HARMING);
        near(direct.getHealth(), 14f, "Trúng trực tiếp Harming mất sáu HP");
        require(near.getHealth() > 14f && near.getHealth() < 20f, "Mức sát thương giảm dần theo khoảng cách");
        require(undead.getHealth() > 12f && undead.getHealth() < 16f, "Harming hồi HP cho undead gần tâm");
        near(owner.getHealth(), 20f, "Miễn tác động cho người ném ngay cả trong vòng thuốc");

        Dummy cursed = new Dummy(66.5f, 10f, 68.5f, true, 0);
        world.addEntity(cursed);
        tossAt(world, null, 66.5f, 68.5f, PotionType.HEALING);
        near(cursed.getHealth(), 14f, "Healing gây sát thương phép cho undead");

        Dummy wounded = new Dummy(66.5f, 10f, 74.5f, false, 0);
        wounded.damage(world, 5f, DamageSource.FALL, null, 0f, 0f);
        world.addEntity(wounded);
        tossAt(world, null, 66.5f, 74.5f, PotionType.HEALING);
        near(wounded.getHealth(), 19f, "Healing hồi bốn HP nếu trúng trực tiếp cá thể sống");
    }

    /** Phóng một lọ theo đường cố định tới vị trí kiểm tra. */
    private static void tossAt(World world, Entity owner, float x, float z, PotionType type) {
        new ThrownPotionEntity(owner, x - 4f, 11.4f, z, 30f, 0f, 0f, type).update(world, 0.2f);
    }

    /** So sánh đủ chính xác để phát hiện lượng HP hay giây hiệu ứng bị đổi. */
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.0001f, message + " (" + actual + " thay vì " + expected + ")");
    }

    /** Báo tình huống vi phạm thay vì tiếp tục cả chuỗi kiểm tra. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Mục tiêu bất động giúp đo chính xác điểm và lượng tác động. */
    private static class Dummy extends LivingEntity {
        /** Dựng hộp mục tiêu thông thường và số ghế yêu cầu. */
        private Dummy(float x, float y, float z, boolean undead, int seats) {
            super(x, y, z, 0.3f, 1.8f, 20f, 0f, undead, seats);
        }

        /** Giữ nguyên vị trí giữa các lần bắn. */
        @Override
        protected void updateLiving(World world, float dt) {
        }

        /** Mục tiêu CPU không cần hình học. */
        @Override
        public void render(ShaderProgram shader) {
        }
    }

    /** Mục tiêu kiểm tra đường gọi né chung của mọi mũi tên. */
    private static final class Dodger extends Dummy {
        private int attempts;

        /** Tạo mục tiêu có khả năng né dứt khoát. */
        private Dodger(float x, float y, float z) {
            super(x, y, z, false, 0);
        }

        /** Đếm lần gọi để xác nhận mũi tên không gây tác động sau khi né. */
        @Override
        public boolean dodgeProjectile(World world, Entity owner) {
            attempts++;
            return true;
        }
    }
}
