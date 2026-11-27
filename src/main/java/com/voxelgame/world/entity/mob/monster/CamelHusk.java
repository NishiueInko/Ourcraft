package com.voxelgame.world.entity.mob.monster;

import com.voxelgame.client.audio.MobSoundEvent;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.mob.animal.Camel;
import com.voxelgame.world.entity.mob.MobAttitude;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.World;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.math.Vector3;

/** Lạc đà xác sống đáp lệnh hai kỵ sĩ và trở thành thú cưỡi khi họ rời cả hai ghế. */
public final class CamelHusk extends Camel {
    /** Giới hạn phát hiện, hành lang lấy đà và tốc độ khi Husk hạ giáo. */
    private static final float DETECTION_RANGE = 30f;
    private static final float RUN_UP_START = 6.5f;
    private static final float RUN_UP_END = 11f;
    private static final float CHARGE_SPEED = 10.5f;
    private static final float CHARGE_TIMEOUT = 1.4f;
    /** Tầm đâm ngoài mép hộp thân và ngưỡng vận tốc tạo một đòn lao thực sự. */
    private static final float SPEAR_REACH = 2.4f;
    private static final float MIN_CHARGE_SPEED = 4f;
    private static final float THRUST_SECONDS = 0.23f;
    /** Giữ đủ khoảng cách để Parched ngắm bắn bên dưới tầm phát hiện 16 block. */
    private static final float ARCHER_NEAR = 7.5f;
    private static final float ARCHER_FAR = 12f;

    /** Bộ đệm dùng lại cho vị trí người chơi, đoạn tia và hướng lao. */
    private final Vector3 enemy = new Vector3();
    private final Vector3 spearOrigin = new Vector3();
    private final Vector3 spearPath = new Vector3();
    private final Raycast.HitResult obstacle = Raycast.HitResult.miss();
    private float chargeX;
    private float chargeZ;
    private float chargeRemaining;
    private float thrustRemaining;
    private boolean takingDistance;
    private boolean underEnemyControl;

    /** Tạo thú cưỡi hai chỗ để bộ sinh gắn các kỵ sĩ trước khi đưa vào thế giới. */
    public CamelHusk(float x, float y, float z) {
        super(MobType.CAMEL_HUSK, x, y, z);
    }

    /** Giữ ghế trước ưu tiên lãnh đạo, rồi dùng cung thủ còn sống ở ghế sau. */
    public LivingEntity activeDriver() {
        if (getPassenger(0) instanceof Husk husk && husk.hasIronSpear() && husk.isAlive()) {
            return husk;
        }
        if (getPassenger(1) instanceof Parched parched && parched.isAlive()) {
            return parched;
        }
        return null;
    }

    /** Phản ánh ghế hiện tại ngay cả trước tick kế tiếp sau khi một kỵ sĩ rời đi hoặc chết. */
    @Override
    public MobAttitude attitude() {
        return activeDriver() == null ? MobAttitude.PASSIVE : MobAttitude.HOSTILE;
    }

    /** Chặn thao tác cưỡi và cho ăn trong thời gian còn tay lái địch. */
    @Override
    protected boolean canPlayerMount(World world, Player player) {
        return activeDriver() == null && super.canPlayerMount(world, player);
    }

    /** Cập nhật đòn đánh sau va chạm để sát thương tăng theo tốc độ thật, kể cả lúc gặp bậc đá. */
    @Override
    protected void updateLiving(World world, float dt) {
        if (underEnemyControl && activeDriver() == null) {
            clearEnemyControl(world);
        }
        float startX = x();
        float startZ = z();
        super.updateLiving(world, dt);
        if (!isAlive() || chargeRemaining <= 0f || !(activeDriver() instanceof Husk husk)
                || target() == null || !target().isAlive()) {
            return;
        }
        float speed = ((x() - startX) * chargeX + (z() - startZ) * chargeZ) / dt;
        if (speed >= MIN_CHARGE_SPEED && trySpearHit(world, husk, target(), startX, startZ, speed)) {
            thrustRemaining = THRUST_SECONDS;
            resetCharge(true);
        }
    }

    /** Thay phiên điều khiển theo kỵ sĩ đang có mặt và trở lại hành vi lạc đà thường khi trống. */
    @Override
    protected void updateUnridden(World world, float dt) {
        LivingEntity driver = activeDriver();
        if (driver == null) {
            super.updateUnridden(world, dt);
            return;
        }
        if (!underEnemyControl) {
            underEnemyControl = true;
            goals().stopAll(world, this);
        }
        thrustRemaining = Math.max(0f, thrustRemaining - dt);
        Player player = world.getPlayer();
        if (player == null || !player.isAlive() || player.copyPosition(enemy).dst2(x(), y(), z())
                > DETECTION_RANGE * DETECTION_RANGE) {
            stopMoving();
            setTarget(null);
            resetCharge(false);
            return;
        }

        setTarget(player);
        if (getPassenger(1) instanceof Parched parched && parched.isAlive()) {
            parched.setTarget(player);
        }
        if (inWater()) {
            requestFloat();
        }
        float dx = enemy.x - x();
        float dz = enemy.z - z();
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (driver instanceof Husk husk) {
            driveWithSpear(world, husk, player, distance, dx, dz, dt);
        } else {
            resetCharge(false);
            driveWithArcher(world, player, distance, dx, dz);
        }
    }

    /** Mất người lái địch thì bỏ mục tiêu cũ và giải phóng mọi lệnh truy đuổi. */
    private void clearEnemyControl(World world) {
        underEnemyControl = false;
        setTarget(null);
        resetCharge(false);
        thrustRemaining = 0f;
        goals().stopAll(world, this);
        stopMoving();
    }

    /** Lùi để lấy đà khi quá gần, rồi khóa hướng lao và cho phép đối thủ né sang bên. */
    private void driveWithSpear(World world, Husk husk, Player player, float distance, float dx, float dz, float dt) {
        if (chargeRemaining > 0f) {
            chargeRemaining = Math.max(0f, chargeRemaining - dt);
            if (chargeRemaining > 0f && !inWater()
                    && dx * chargeX + dz * chargeZ >= -0.4f) {
                commandCharge();
                return;
            }
            resetCharge(true);
        }

        if (distance < RUN_UP_START) {
            takingDistance = true;
        }
        if (takingDistance && distance < RUN_UP_START + 0.75f) {
            retreat(world, dx, dz, 1.25f);
            return;
        }
        takingDistance = false;
        if (distance <= RUN_UP_END && onGround && !inWater() && spearPathClear(world, player)) {
            chargeX = dx / distance;
            chargeZ = dz / distance;
            chargeRemaining = CHARGE_TIMEOUT;
            husk.playSound(world, MobSoundEvent.SPEAR_CHARGE);
            commandCharge();
        } else {
            moveToward(world, enemy.x, enemy.y, enemy.z, 1.45f);
        }
    }

    /** Xin lệnh lao ngang tốc độ cao, vẫn qua quét vật lý và bước bậc của thú. */
    private void commandCharge() {
        float aimX = x() + chargeX;
        float aimZ = z() + chargeZ;
        faceBody(aimX, aimZ);
        steerDirect(aimX, aimZ, CHARGE_SPEED);
    }

    /** Giữ cự ly bắn và vòng sang bên nếu địa hình chắn giữa cung thủ và người chơi. */
    private void driveWithArcher(World world, Player player, float distance, float dx, float dz) {
        if (distance < ARCHER_NEAR) {
            retreat(world, dx, dz, 1.2f);
        } else if (distance > ARCHER_FAR) {
            moveToward(world, enemy.x, enemy.y, enemy.z, 1.1f);
        } else {
            copySeatPosition(1, spearOrigin);
            spearOrigin.y += getPassenger(1).getHeight() * 0.78f;
            if (!lineClear(world, player)) {
                float side = ((long) (ageSeconds() / 2d) & 1L) == 0L ? 3f / distance : -3f / distance;
                moveToward(world, x() - dz * side, y(), z() + dx * side, 1f);
            } else {
                stopMoving();
                faceBody(enemy.x, enemy.z);
            }
        }
    }

    /** Lùi ra xa điểm chân của mục tiêu với đường tránh địa hình. */
    private void retreat(World world, float dx, float dz, float speedMultiplier) {
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance <= CONTACT_EPSILON) {
            moveToward(world, x() + 4f, y(), z(), speedMultiplier);
        } else {
            moveToward(world, x() - dx * (4f / distance), y(), z() - dz * (4f / distance), speedMultiplier);
        }
    }

    /** Quét tầm đâm trên hướng lao và chỉ ghi sát thương khi tia từ lưng thú thông thoáng. */
    private boolean trySpearHit(World world, Husk husk, LivingEntity target, float startX, float startZ, float speed) {
        target.copyPosition(enemy);
        if (enemy.y >= y() + height + 0.65f || enemy.y + target.getHeight() <= y() - 0.25f) {
            return false;
        }
        float dx = enemy.x - startX;
        float dz = enemy.z - startZ;
        float forward = dx * chargeX + dz * chargeZ;
        float side = Math.abs(dx * chargeZ - dz * chargeX);
        float traveled = (x() - startX) * chargeX + (z() - startZ) * chargeZ;
        float width = halfWidth + target.getHalfWidth();
        if (forward < 0f || forward > traveled + SPEAR_REACH + width || side > width
                || !spearPathClear(world, target)) {
            return false;
        }
        float damage = 4.5f + Math.min(4.5f, speed * 0.43f);
        float push = 4f + speed * 0.85f;
        husk.thrustSpear(world, target, damage, chargeX * push, chargeZ * push);
        return true;
    }

    /** Từ gần yên tới thân mục tiêu, không cho giáo đi xuyên vật cản. */
    private boolean spearPathClear(World world, LivingEntity target) {
        spearOrigin.set(x() + chargeX * 0.42f, y() + height * 0.93f, z() + chargeZ * 0.42f);
        return lineClear(world, target);
    }

    /** Kiểm tra đoạn từ tay cầm vũ khí tới thân đối thủ bằng bộ đệm chia sẻ. */
    private boolean lineClear(World world, LivingEntity target) {
        target.copyCenterPosition(spearPath).sub(spearOrigin);
        float distance = spearPath.len();
        return distance <= CONTACT_EPSILON
                || !Raycast.castOccluder(world, spearOrigin, spearPath, distance, obstacle).isHit();
    }

    /** Giữ một nhịp đâm hoàn chỉnh, giảm độ vươn khi tay lái đang lùi để lấy đà. */
    float spearExtension() {
        return thrustRemaining > 0f ? 1f : chargeRemaining > 0f ? 0.55f : 0f;
    }

    /** Kết thúc lượt lao và quyết định có phải lùi ra trước lượt kế tiếp không. */
    private void resetCharge(boolean retreat) {
        chargeRemaining = 0f;
        chargeX = 0f;
        chargeZ = 0f;
        takingDistance = retreat;
    }
}
