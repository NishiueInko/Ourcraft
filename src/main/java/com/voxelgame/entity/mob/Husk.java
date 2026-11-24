package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.MathUtils;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.StatusEffectType;

import java.util.concurrent.ThreadLocalRandom;

/** Zombie sa mạc áp Hunger khi đánh và đổi thành Zombie thường sau khi chìm đầu. */
public final class Husk extends Zombie {
    /** Chìm đầu 30 giây liên tục trước pha chuyển hóa dài 15 giây. */
    private static final double SUBMERGE_SECONDS = 30d;
    private static final double CONVERSION_SECONDS = 15d;
    private static final float HUNGER_MIN_SECONDS = 7f;
    private static final float HUNGER_MAX_SECONDS = 14f;

    private double submergedSeconds;
    private double convertingSeconds;
    private boolean converting;
    private boolean converted;

    /** Tạo Husk người lớn có kích thước hình vẽ lớn hơn 6% nhưng giữ hộp vật lý cũ. */
    public Husk(float x, float y, float z) {
        super(MobType.HUSK, x, y, z, false);
        mobVisualState().setScale(1.06f);
    }

    /** Chỉ áp Hunger I khi sát thương cận chiến được mục tiêu chấp nhận. */
    @Override
    protected boolean strikeMelee(World world, LivingEntity target, float baseDamage, float knockbackX, float knockbackZ) {
        if (!super.strikeMelee(world, target, baseDamage, knockbackX, knockbackZ)) {
            return false;
        }
        target.applyEffect(StatusEffectType.HUNGER, 0,
                ThreadLocalRandom.current().nextFloat(HUNGER_MIN_SECONDS, HUNGER_MAX_SECONDS));
        return true;
    }

    /** Đếm thời gian theo bước mô phỏng, cập nhật hiệu ứng rung và thay cá thể đúng một lần. */
    @Override
    protected void updateLiving(World world, float dt) {
        super.updateLiving(world, dt);
        if (converted) {
            return;
        }
        if (!converting) {
            submergedSeconds = headUnderwater(world) ? submergedSeconds + dt : 0d;
            if (submergedSeconds < SUBMERGE_SECONDS) {
                return;
            }
            converting = true;
            // Dồn phần dư của nhịp vượt ngưỡng vào pha rung để giữ đủ 45 giây tổng cộng.
            convertingSeconds = submergedSeconds - SUBMERGE_SECONDS;
        } else {
            convertingSeconds += dt;
        }
        if (convertingSeconds < CONVERSION_SECONDS) {
            shake();
            return;
        }
        converted = true;
        playSound(world, MobSoundEvent.HUSK_CONVERT);
        world.replaceLivingEntity(this, new Zombie(x(), y(), z(), false));
    }

    /** Điểm ở phần mặt phải thấp hơn bề mặt chất lỏng trong chính ô chứa đầu. */
    private boolean headUnderwater(World world) {
        float headY = y() + getHeight() * 0.9f;
        int blockX = MathUtils.floor(x());
        int blockY = MathUtils.floor(headY);
        int blockZ = MathUtils.floor(z());
        return world.getBlock(blockX, blockY, blockZ) == Block.WATER.getId()
                && headY < blockY + Block.WATER.getTopHeight(world, blockX, blockY, blockZ);
    }

    /** Cộng dao động vẽ mỗi lượt mà không thay đổi hướng di chuyển hoặc cấp phát đối tượng. */
    private void shake() {
        MobVisualState visual = mobVisualState();
        float shake = MathUtils.sin((float) (convertingSeconds * 33d)) * 8f;
        visual.setAnimation(visual.bodyYaw() + shake * 0.4f, visual.headYaw() + shake, visual.headPitch(),
                visual.limbSwing(), visual.limbSwingAmount(), visual.ageInTicks());
    }
}
