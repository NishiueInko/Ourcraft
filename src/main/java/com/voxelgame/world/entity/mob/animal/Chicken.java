package com.voxelgame.world.entity.mob.animal;

import com.voxelgame.client.audio.MobSoundEvent;
import com.voxelgame.client.render.entity.MobVisualState;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.mob.PassiveMob;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.World;

import java.util.concurrent.ThreadLocalRandom;

/** Gà đồng bằng vỗ cánh khi rơi, miễn sát thương rơi và định kỳ đẻ trứng. */
public final class Chicken extends PassiveMob {
    /** Giới hạn vận tốc rơi khô theo block/giây, chiều âm là đi xuống. */
    private static final float GLIDE_SPEED = -1.7f;
    /** Chu kỳ đẻ trứng chuẩn Minecraft: 300 đến 600 giây (5 đến 10 phút). */
    private static final float EGG_MIN_SECONDS = 300f;
    private static final float EGG_MAX_SECONDS = 600f;

    private float eggTimer = randomEggDelay();

    /** Dùng bộ goal hiền lành chung, gồm lang thang và chạy hoảng khi trúng đòn. */
    public Chicken(float x, float y, float z) {
        super(MobType.CHICKEN, x, y, z);
        setMoveSpeed(2.5f);
    }

    /** Vỗ cánh khi chuyển động xuống, thu cánh khi đáp đất và đẻ trứng khi hết chu kỳ. */
    @Override
    protected void updateLiving(World world, float dt) {
        super.updateLiving(world, dt);
        boolean descending = !isOnGround() && velocity.y < 0f;
        mobVisualState().setPose(descending ? MobVisualState.Pose.FLAP : MobVisualState.Pose.IDLE,
                descending ? 1f : 0f);
        if (!isBaby() && isAlive()) {
            eggTimer -= dt;
            if (eggTimer <= 0f) {
                eggTimer = randomEggDelay();
                world.spawnDroppedItem(new ItemEntity(x(), y() + 0.25f, z(), Item.EGG));
                playSound(world, MobSoundEvent.AMBIENT);
            }
        }
    }

    /** Đặt lại bộ đếm đẻ trứng trong khoảng 300–600 giây. */
    private static float randomEggDelay() {
        return ThreadLocalRandom.current().nextFloat(EGG_MIN_SECONDS, EGG_MAX_SECONDS);
    }

    /** Cho phép kiểm thử đặt nhanh thời gian chờ đẻ trứng. */
    public void setEggTimerForTest(float seconds) {
        eggTimer = seconds;
    }

    /** Đọc thời gian còn lại tới lần đẻ trứng tiếp theo. */
    public float eggTimer() {
        return eggTimer;
    }

    /** Giới hạn tốc độ ngay trước quét vật lý, kể cả nhịp rơi đầu tiên. */
    @Override
    protected float terminalFallSpeed() { return GLIDE_SPEED; }

    /** Bỏ tích lũy độ cao rơi của gà trong vòng sinh tồn. */
    @Override
    protected boolean ignoresFall() { return true; }

    /** Chặn cả nguồn FALL gửi trực tiếp, còn những nguồn khác theo luật chung. */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        return source != DamageSource.FALL && super.damage(world, amount, source, attacker, knockbackX, knockbackZ);
    }
}
