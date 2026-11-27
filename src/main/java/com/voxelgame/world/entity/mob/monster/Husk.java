package com.voxelgame.world.entity.mob.monster;

import com.voxelgame.client.audio.MobSoundEvent;
import com.voxelgame.client.render.entity.MobVisualState;
import com.voxelgame.world.effect.StatusEffectType;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.concurrent.ThreadLocalRandom;

/** Zombie sa mạc áp Hunger khi đánh và đổi thành Zombie thường sau khi chìm đầu. */
public final class Husk extends Zombie {
    /** Tỷ lệ hiển thị người lớn của Husk (cao hơn Zombie thường 6%). */
    private static final float ADULT_VISUAL_SCALE = 1.06f;
    /** Chìm đầu 30 giây liên tục trước pha chuyển hóa dài 15 giây. */
    private static final double SUBMERGE_SECONDS = 30d;
    private static final double CONVERSION_SECONDS = 15d;
    private static final float HUNGER_MIN_SECONDS = 7f;
    private static final float HUNGER_MAX_SECONDS = 14f;

    /** Trang bị của kỵ sĩ được giữ nguyên nếu nó rời ghế. */
    private final boolean ironSpear;
    private final Vector3 spearTarget;

    private double submergedSeconds;
    private double convertingSeconds;
    private boolean converting;
    private boolean converted;

    /** Tạo Husk tự nhiên với 5% cơ hội là cá thể non (theo Zombie.rollsBaby). */
    public Husk(float x, float y, float z) {
        this(x, y, z, false, rollsBaby());
    }

    /** Trang bị giáo cho Husk ghế lái của bộ kỵ sĩ sa mạc (luôn là người lớn). */
    public Husk(float x, float y, float z, boolean ironSpear) {
        this(x, y, z, ironSpear, false);
    }

    /** Khởi tạo Husk với trang bị giáo và trạng thái non/trưởng thành tường minh. */
    public Husk(float x, float y, float z, boolean ironSpear, boolean baby) {
        super(MobType.HUSK, x, y, z, baby);
        this.ironSpear = ironSpear;
        spearTarget = ironSpear ? new Vector3() : null;
        if (ironSpear) {
            mobVisualState().setPose(MobVisualState.Pose.THRUST, 0f);
        }
    }

    /** Giữ tỷ lệ hiển thị 1,06× cho Husk trưởng thành (và 0,6 × 1,06 cho Husk con). */
    @Override
    protected float adultVisualScale() {
        return ADULT_VISUAL_SCALE;
    }

    /** Nhận diện Husk có thể chỉ huy cú lao bằng giáo. */
    public boolean hasIronSpear() {
        return ironSpear;
    }

    /** Giữ giáo trên tay và ngắm theo đích mà thú cưỡi đang tiếp cận. */
    @Override
    protected void updateMounted(World world, float dt) {
        if (ironSpear && getMount() instanceof CamelHusk camel && camel.activeDriver() == this) {
            LivingEntity target = camel.target();
            if (target != null) {
                target.copyCenterPosition(spearTarget);
                lookAt(spearTarget.x, spearTarget.y, spearTarget.z);
                faceBody(spearTarget.x, spearTarget.z);
            }
            mobVisualState().setPose(MobVisualState.Pose.THRUST, camel.spearExtension());
        }
    }

    /** Thực hiện đòn giáo từ ghế lái, giữ hiệu ứng Hunger vốn có của Husk. */
    boolean thrustSpear(World world, LivingEntity target, float damage, float knockbackX, float knockbackZ) {
        return ironSpear && strikeMelee(world, target, damage, knockbackX, knockbackZ);
    }

    /** Chỉ áp Hunger I khi sát thương cận chiến được mục tiêu chấp nhận. */
    @Override
    public boolean strikeMelee(World world, LivingEntity target, float baseDamage, float knockbackX, float knockbackZ) {
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
        if (ironSpear && !(getMount() instanceof CamelHusk)) {
            mobVisualState().setPose(MobVisualState.Pose.THRUST, 0f);
        }
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
        world.replaceLivingEntity(this, new Zombie(x(), y(), z(), isBaby()));
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
