package com.voxelgame.entity.mob;

import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;

/** Gà đồng bằng vỗ cánh khi rơi và tiếp đất mà không mất máu. */
public final class Chicken extends MobEntity {
    /** Giới hạn vận tốc rơi khô theo block/giây, chiều âm là đi xuống. */
    private static final float GLIDE_SPEED = -1.7f;

    /** Dùng bộ goal hiền lành chung, gồm lang thang và chạy hoảng khi trúng đòn. */
    public Chicken(float x, float y, float z) {
        super(MobType.CHICKEN, x, y, z);
        setMoveSpeed(2.5f);
    }

    /** Vỗ cánh khi chuyển động xuống và thu cánh ngay ở lượt vừa đáp đất. */
    @Override
    protected void updateLiving(World world, float dt) {
        super.updateLiving(world, dt);
        boolean descending = !isOnGround() && velocity.y < 0f;
        mobVisualState().setPose(descending ? MobVisualState.Pose.FLAP : MobVisualState.Pose.IDLE,
                descending ? 1f : 0f);
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
