package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Lạc đà sa mạc chở hai hành khách, ngồi nghỉ trên cát và lướt khi nhả phím nạp. */
public class Camel extends MountableMob {
    /** Chiều cao hộp va chạm khi gập chân và mặt đỡ ghế trên lưng khi đứng. */
    private static final float SITTING_HEIGHT = 0.945f;
    private static final float STANDING_SEAT_HEIGHT = 1.91f;
    /** Vận tốc cơ bản, vận tốc chạy và bước qua bậc cao. */
    private static final float WALK_SPEED = 4f;
    private static final float SPRINT_SPEED = 6.8f;
    private static final float STEP_HEIGHT = 1.5f;
    /** Hồi chiêu, thời gian duy trì xung và vận tốc khi thanh lướt đầy. */
    private static final float DASH_COOLDOWN = 2.75f;
    private static final float DASH_DURATION = 0.55f;
    private static final float DASH_HORIZONTAL_SPEED = 21f;
    private static final float DASH_VERTICAL_SPEED = 7.55f;
    /** Xương rồng chữa hai điểm sinh lực; dụ lạc đà từ mười block. */
    private static final float CACTUS_HEAL = 2f;
    private static final float TEMPT_RANGE = 10f;

    /** Bộ đệm vị trí người cầm mồi dùng lại qua các lượt cập nhật. */
    private final Vector3 feederPosition = new Vector3();
    private double nextRest;
    private double restUntil;
    private double dashReadyAt;
    private boolean sitting;

    /** Lạc đà sống được cưỡi ngay khi gặp. */
    public Camel(float x, float y, float z) {
        this(MobType.CAMEL, x, y, z);
    }

    /** Biến thể undead chia sẻ thể chất và chỉ dùng các hành vi hiền khi đã hết kỵ sĩ. */
    protected Camel(MobType type, float x, float y, float z) {
        super(type, x, y, z, 2, STEP_HEIGHT, WALK_SPEED, 1f, type == MobType.CAMEL, false);
        setMaxSafeFall(maxSafeFall() + 4f);
        scheduleRest();
        goals().add(new MobGoals.FloatInWaterGoal(0));
        goals().add(new MobGoals.PanicGoal(2));
        goals().add(new MobGoals.TemptFollowGoal(5, Block.CACTUS, TEMPT_RANGE, 0.85f));
        goals().add(new MobGoals.WanderGoal(6, 9, 0.7f));
        goals().add(new MobGoals.LookAtPlayerGoal(7, 8f));
        goals().add(new MobGoals.RandomLookAroundGoal(8));
    }

    /** Trao xương rồng trước khi thử lên ghế và chỉ dùng vật phẩm nếu có thể hồi máu. */
    @Override
    public boolean onPlayerInteract(World world, Player player) {
        if (attitude() != MobAttitude.PASSIVE || !isAlive() || !player.isAlive()) {
            return false;
        }
        Hotbar hotbar = player.getHotbar();
        if (getHealth() < getMaxHealth() && hotbar.getSelectedBlock() == Block.CACTUS
                && hotbar.getSelectedCount() > 0) {
            hotbar.consumeSelectedItem();
            heal(CACTUS_HEAL);
            tryStand(world);
            return true;
        }
        return super.onPlayerInteract(world, player);
    }

    /** Yêu cầu đủ khoảng trống để đứng lên trước khi nhận người lên yên. */
    @Override
    protected boolean canPlayerMount(World world, Player player) {
        return attitude() == MobAttitude.PASSIVE && super.canPlayerMount(world, player) && tryStand(world);
    }

    /** Lạc đà hiền hoặc lạc đà undead đã được giải thoát đều thuần hóa khi người chơi cưỡi. */
    @Override
    protected void onPlayerMounted(World world, Player player, int seat) {
        tame();
        scheduleRest();
    }

    /** Chân người cưỡi đi xuống cùng thân khi lạc đà ngồi. */
    @Override
    protected float seatHeight(int seat) {
        return STANDING_SEAT_HEIGHT + getHeight() - type().height();
    }

    /** Định vị hai ghế dọc sống lưng, ghế lái ở phía đầu. */
    @Override
    protected float seatForwardOffset(int seat) {
        return seat == 0 ? 0.45f : -0.55f;
    }

    /** Tốc độ chạy nước rút chỉ áp dụng khi người lái yêu cầu. */
    @Override
    protected float riddenSpeed(boolean sprint) {
        return sprint ? SPRINT_SPEED : WALK_SPEED;
    }

    /** Tránh điều khiển lạc đà sống khi chưa thể đứng, hoặc lạc đà còn bị phe địch chiếm. */
    @Override
    protected boolean canRiderDrive(World world) {
        return attitude() == MobAttitude.PASSIVE && tryStand(world);
    }

    /** Chọn thanh Dash trong giao diện thay vì thanh nhảy của ngựa hoặc lừa. */
    @Override
    public boolean isDashMount() {
        return true;
    }

    /** Đọc bộ đếm hồi chiêu từ cùng đồng hồ dùng để cấp quyền kích hoạt lướt. */
    @Override
    public float dashCooldownRemaining() {
        return Math.max(0f, (float) (dashReadyAt - ageSeconds()));
    }

    /** Lướt bắt đầu khi chân chạm đất, dưới điều khiển của người chơi và đã hết hồi. */
    @Override
    protected boolean canChargeAbility() {
        return !sitting && ageSeconds() >= dashReadyAt && super.canChargeAbility();
    }

    /** Nhả nạp để phóng lên một vòng cung, dùng cùng quét vật lý khi chạy và bước bậc. */
    @Override
    protected void onChargedAction(World world, float charge, float directionX, float directionZ) {
        if (!canChargeAbility() || !navigator().canRise(world, x(), y(), z(), 0.15f)) {
            return;
        }
        if (directionX == 0f && directionZ == 0f) {
            float bodyYaw = mobVisualState().bodyYaw();
            directionX = -MathUtils.sinDeg(bodyYaw);
            directionZ = -MathUtils.cosDeg(bodyYaw);
        }
        float horizontalSpeed = DASH_HORIZONTAL_SPEED * (0.28f + 0.72f * charge);
        float verticalSpeed = DASH_VERTICAL_SPEED * (0.7f + 0.3f * charge);
        float vx = horizontalSpeed * directionX;
        float vz = horizontalSpeed * directionZ;
        queueLeap(vx, verticalSpeed, vz, DASH_DURATION);
        velocity.x = vx;
        velocity.z = vz;
        faceBody(x() + directionX, z() + directionZ);
        dashReadyAt = ageSeconds() + DASH_COOLDOWN;
        playSound(world, MobSoundEvent.CAMEL_DASH);
    }

    /** Chọn nghỉ trên cát, hoặc đứng dậy khi gặp mồi, bị đánh, vào nước hay hết lượt nghỉ. */
    @Override
    protected void updateUnridden(World world, float dt) {
        if (attitude() != MobAttitude.PASSIVE) {
            return;
        }
        boolean needsToStand = getPassenger(0) != null || getPassenger(1) != null
                || inWater() || getHurtFlashTimer() > 0f || panicTime() > 0f || isTempted(world.getPlayer());
        if (sitting) {
            if ((!needsToStand && ageSeconds() < restUntil) || !tryStand(world)) {
                stopMoving();
                return;
            }
        } else if (!needsToStand && isOnGround() && ageSeconds() >= nextRest && isOnSand(world)) {
            goals().stopAll(world, this);
            stopMoving();
            height = SITTING_HEIGHT;
            sitting = true;
            restUntil = ageSeconds() + ThreadLocalRandom.current().nextFloat(3f, 6f);
            mobVisualState().setPose(MobVisualState.Pose.SIT, 1f);
            return;
        }
        super.updateUnridden(world, dt);
    }

    /** Đọc khối ngay dưới chân ở tâm thân để khởi động nghỉ đúng nền cát. */
    private boolean isOnSand(World world) {
        return world.getBlock((int) Math.floor(x()), (int) Math.floor(y() - 0.02f), (int) Math.floor(z()))
                == Block.SAND.getId();
    }

    /** Kiểm tra mồi cầm tay để đánh thức lạc đà trong tầm dụ. */
    private boolean isTempted(Player player) {
        if (player == null || !player.isAlive() || player.getHotbar().getSelectedBlock() != Block.CACTUS
                || player.getHotbar().getSelectedCount() <= 0) {
            return false;
        }
        return player.copyPosition(feederPosition).dst2(x(), y(), z()) <= TEMPT_RANGE * TEMPT_RANGE;
    }

    /** Mở rộng hộp va chạm ngay khi toàn bộ thể tích đứng không có vật cản. */
    private boolean tryStand(World world) {
        if (!sitting) {
            return true;
        }
        float standingHeight = type().height();
        if (!navigator().canRise(world, x(), y(), z(), standingHeight - height)) {
            return false;
        }
        height = standingHeight;
        sitting = false;
        mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        scheduleRest();
        return true;
    }

    /** Rải thời điểm nghỉ để các cá thể cùng xuất hiện không ngồi đồng loạt. */
    private void scheduleRest() {
        nextRest = ageSeconds() + ThreadLocalRandom.current().nextFloat(8f, 18f);
    }
}
