package com.voxelgame.entity.mob;

import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.StatusEffectType;
import com.voxelgame.entity.projectile.PotionType;
import com.voxelgame.entity.projectile.ThrownPotionEntity;

import java.util.concurrent.ThreadLocalRandom;

/** Phù thủy chọn thuốc ném theo tình trạng đối thủ và uống thuốc để tự phòng vệ. */
public final class Witch extends RangedMob {
    /** Các thông số điều hướng, ném và lựa chọn mục tiêu cho thuốc. */
    private static final float DETECTION_RANGE = 16f;
    private static final float THROW_RANGE = 10f;
    private static final float WINDUP = 0.25f;
    private static final float FLASK_SPEED = 11.5f;
    private static final float THROW_INTERVAL = 3f;
    private static final float THROW_POSE_SECONDS = 0.3f;
    private static final float SLOWNESS_DISTANCE = 8f;
    private static final float POISON_MIN_HEALTH = 8f;
    private static final float WEAKNESS_DISTANCE = 3f;
    private static final float WEAKNESS_CHANCE = 0.25f;

    /** Thời lượng uống, hồi phục giữa hai lần, hiệu lực thuốc phòng vệ và kháng phép. */
    private static final float DRINK_SECONDS = 1.5f;
    private static final float DRINK_RECOVERY = 0.75f;
    private static final float BUFF_SECONDS = 180f;
    private static final float HEALING_AMOUNT = 4f;
    private static final float MAGIC_DAMAGE_MULTIPLIER = 0.15f;
    private static final float SWIFTNESS_DISTANCE_SQUARED = 11f * 11f;
    private double throwPoseUntil;

    /** Dựng Witch, ưu tiên hành vi uống hơn việc đuổi và ném thuốc. */
    public Witch(float x, float y, float z) {
        super(MobType.WITCH, x, y, z, DETECTION_RANGE, THROW_RANGE, WINDUP, FLASK_SPEED, ThrownPotionEntity.GRAVITY);
        goals().add(new DrinkGoal());
    }

    /** Áp dụng mức kháng 85% cho sát thương phép, kể cả nhịp sát thương của Poison. */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        float effective = source == DamageSource.POTION_MAGIC ? amount * MAGIC_DAMAGE_MULTIPLIER : amount;
        return super.damage(world, effective, source, attacker, knockbackX, knockbackZ);
    }

    /** Chọn thuốc theo thứ tự khoảng cách, tình trạng mục tiêu rồi xác suất Weakness. */
    public static PotionType choosePotion(LivingEntity target, float distance, float weaknessRoll) {
        if (distance >= SLOWNESS_DISTANCE && !target.hasEffect(StatusEffectType.SLOWNESS)) {
            return PotionType.SLOWNESS;
        }
        if (target.getHealth() >= POISON_MIN_HEALTH && !target.hasEffect(StatusEffectType.POISON)) {
            return PotionType.POISON;
        }
        if (distance <= WEAKNESS_DISTANCE && !target.hasEffect(StatusEffectType.WEAKNESS)
                && weaknessRoll < WEAKNESS_CHANCE) {
            return PotionType.WEAKNESS;
        }
        return PotionType.HARMING;
    }

    /** Tư thế ném tồn tại trong một đoạn ngắn sau khi lọ đã rời tay. */
    @Override
    void applyAimPose(float progress) {
        float amount = (float) (throwPoseUntil - ageSeconds()) / THROW_POSE_SECONDS;
        mobVisualState().setPose(amount > 0f ? MobVisualState.Pose.THRUST : MobVisualState.Pose.IDLE,
                Math.max(0f, amount));
    }

    /** Giữ nhịp ném ba giây trong toàn bộ phạm vi tấn công. */
    @Override
    float intervalSeconds(float distanceSquared) {
        return THROW_INTERVAL;
    }

    /** Phóng thuốc theo cung đã tính và mở cửa sổ tư thế ném, cùng hiệu ứng âm thanh của Witch. */
    @Override
    void fire(World world, LivingEntity target, float distance,
              float x, float y, float z, float vx, float vy, float vz) {
        PotionType potion = choosePotion(target, distance, ThreadLocalRandom.current().nextFloat());
        world.addEntity(new ThrownPotionEntity(this, x, y, z, vx, vy, vz, potion));
        throwPoseUntil = ageSeconds() + THROW_POSE_SECONDS;
        playSound(world, MobSoundEvent.WITCH_THROW);
    }

    /** Các loại thuốc Witch tự uống, mỗi loại có một tình huống kích hoạt riêng. */
    private enum Drink {
        FIRE_RESISTANCE(StatusEffectType.FIRE_RESISTANCE),
        WATER_BREATHING(StatusEffectType.WATER_BREATHING),
        HEALING(null),
        SWIFTNESS(StatusEffectType.SWIFTNESS);

        private final StatusEffectType status;

        /** Gắn hiệu ứng kéo dài nếu có. Healing hồi HP ngay khi uống xong. */
        Drink(StatusEffectType status) {
            this.status = status;
        }
    }

    /** Ưu tiên thuốc sống sót trước, rồi hồi HP hoặc tăng tốc lúc cần đuổi xa. */
    private Drink chooseDrink(World world) {
        if (!hasEffect(StatusEffectType.FIRE_RESISTANCE)
                && (getFireTimer() > 0f || touchesLiquid(world, Block.LAVA))) {
            return Drink.FIRE_RESISTANCE;
        }
        int eyeX = (int) Math.floor(x());
        int eyeY = (int) Math.floor(y() + getHeight() * 0.8f);
        int eyeZ = (int) Math.floor(z());
        if (!hasEffect(StatusEffectType.WATER_BREATHING) && world.getBlock(eyeX, eyeY, eyeZ) == Block.WATER.getId()) {
            return Drink.WATER_BREATHING;
        }
        if (getHealth() < getMaxHealth()) {
            return Drink.HEALING;
        }
        LivingEntity target = target();
        if (target != null && !hasEffect(StatusEffectType.SWIFTNESS) && distanceSquared(target) > SWIFTNESS_DISTANCE_SQUARED) {
            return Drink.SWIFTNESS;
        }
        return null;
    }

    /** Giữ đứng yên trong thời gian uống, nhưng nhường kênh nhảy cho hành vi nổi trong nước. */
    private final class DrinkGoal implements MobGoal {
        private Drink potion;
        private double finishAt;
        private double drinkReadyAt;

        /** Phòng vệ được thực hiện trước cả di chuyển tấn công. */
        @Override
        public int priority() { return 2; }

        /** Không chạy hoặc đổi hướng ngắm khi đưa lọ lên miệng. */
        @Override
        public int controls() { return MOVE | LOOK; }

        /** Giãn nhịp kiểm tra tiếp xúc Lava khi chưa cần uống. */
        @Override
        public float retrySeconds() { return 0.1f; }

        /** Tìm điều kiện cần thuốc sau khoảng nghỉ của lần uống trước. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            if (ageSeconds() < drinkReadyAt) {
                return false;
            }
            potion = chooseDrink(world);
            return potion != null;
        }

        /** Chỉ giữ hành vi cho đến lúc hoàn tất lọ đang uống. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) { return potion != null; }

        /** Bắt đầu nâng lọ và phát âm thanh đúng một lần. */
        @Override
        public void start(World world, MobEntity mob) {
            finishAt = ageSeconds() + DRINK_SECONDS;
            playSound(world, MobSoundEvent.WITCH_DRINK);
        }

        /** Cập nhật hoạt ảnh và chỉ áp dụng thuốc khi đã uống hết. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            stopMoving();
            double remaining = finishAt - ageSeconds();
            if (remaining > 0) {
                mobVisualState().setPose(MobVisualState.Pose.DRINK, 1f - (float) remaining / DRINK_SECONDS);
                return;
            }
            if (potion == Drink.HEALING) {
                heal(HEALING_AMOUNT);
            } else {
                applyEffect(potion.status, 0, BUFF_SECONDS);
            }
            potion = null;
            drinkReadyAt = ageSeconds() + DRINK_RECOVERY;
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }

        /** Hạ lọ và bỏ thuốc đã chọn nếu vòng đời dừng trước khi uống xong. */
        @Override
        public void stop(World world, MobEntity mob) {
            potion = null;
            stopMoving();
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }
    }
}
