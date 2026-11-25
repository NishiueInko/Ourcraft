package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/** Dê núi có thể lấy đà nhảy vượt địa hình hoặc lao húc mục tiêu đứng gần. */
public final class Goat extends MobEntity {
    /** Tốc độ, tầm chọn đích và thời lượng chuẩn bị cho cú húc. */
    private static final float RAM_SPEED = 13f;
    private static final float RAM_RANGE = 9f;
    private static final float RAM_PREPARE = 0.8f;
    private static final float RAM_TIMEOUT = 1.1f;
    /** Gia tốc rơi và giới hạn bán kính, chênh cao của một cú nhảy núi. */
    private static final float GRAVITY_MAGNITUDE = 26f;
    private static final float JUMP_RANGE = 10f;
    private static final float JUMP_PREPARE = 0.4f;

    /** Phân biệt lấy đà, lao và nhảy để các hành vi không giành quyền điều khiển. */
    private enum Action {
        IDLE, RAM_WINDUP, RAM_CHARGE, JUMP_WINDUP, JUMP_FLIGHT
    }

    private final boolean screaming;
    private final Vector3 targetCenter = new Vector3();
    private final Vector3 targetVelocity = new Vector3();
    private final Vector3 jumpDestination = new Vector3();
    private LivingEntity ramTarget;
    private Action action = Action.IDLE;
    private float actionRemaining;
    private float ramDelay;
    private float jumpDelay = ThreadLocalRandom.current().nextFloat(9f, 23f);
    private float ramX;
    private float ramZ;
    private float ramDirectionX;
    private float ramDirectionZ;

    /** Tạo dê trưởng thành với xác suất 2% thuộc biến thể dê hét. */
    public Goat(float x, float y, float z) {
        this(x, y, z, ThreadLocalRandom.current().nextInt(50) == 0);
    }

    /** Khởi tạo biến thể và đặt sẵn khoảng chờ cú húc đầu tiên. */
    public Goat(float x, float y, float z, boolean screaming) {
        super(MobType.GOAT, x, y, z, 0f, 0, false);
        this.screaming = screaming;
        setMoveSpeed(2.5f);
        setMaxSafeFall(13f);
        MobGoals.addDefaults(goals(), type(), false);
        scheduleRam();
    }

    /** Cập nhật va chạm của cú húc sau khi vật lý đã quét xong chuyển động ngang. */
    @Override
    protected void updateLiving(World world, float dt) {
        float fromX = x();
        float fromZ = z();
        super.updateLiving(world, dt);
        if (action != Action.RAM_CHARGE || !isAlive()) {
            return;
        }
        if (hitRamTarget(world, fromX, fromZ) || inWater()
                || (x() - ramX) * ramDirectionX + (z() - ramZ) * ramDirectionZ >= 0f
                || actionRemaining <= 0f) {
            finishAction();
        }
    }

    /** Ưu tiên cú húc đến hạn, sau đó mới thử tìm bãi đáp an toàn để nhảy. */
    @Override
    protected void updateGoals(World world, float dt) {
        ramDelay -= dt;
        jumpDelay -= dt;
        if (action != Action.IDLE) {
            updateAction(world, dt);
            return;
        }
        if (onGround && !inWater()) {
            if (ramDelay <= 0f) {
                ramTarget = findRamTarget(world);
                if (ramTarget != null) {
                    action = Action.RAM_WINDUP;
                    actionRemaining = RAM_PREPARE;
                    goals().stopAll(world, this);
                    playSound(world, screaming ? MobSoundEvent.GOAT_SCREAM : MobSoundEvent.GOAT_RAM);
                    updateAction(world, dt);
                    return;
                }
                ramDelay = 1.5f;
            }
            if (jumpDelay <= 0f) {
                jumpDelay = ThreadLocalRandom.current().nextFloat(9f, 23f);
                if (findJumpDestination(world)) {
                    action = Action.JUMP_WINDUP;
                    actionRemaining = JUMP_PREPARE;
                    goals().stopAll(world, this);
                    updateAction(world, dt);
                    return;
                }
            }
        }
        super.updateGoals(world, dt);
    }

    /** Thực hiện pha lấy đà hoặc lao hiện tại và nhường lại điều khiển khi kết thúc. */
    private void updateAction(World world, float dt) {
        actionRemaining -= dt;
        switch (action) {
            case RAM_WINDUP -> prepareRam(dt);
            case RAM_CHARGE -> steerDirect(x() + ramDirectionX, z() + ramDirectionZ, RAM_SPEED);
            case JUMP_WINDUP -> prepareJump(world, dt);
            case JUMP_FLIGHT -> {
                if (onGround || inWater() || actionRemaining <= 0f) {
                    finishAction();
                }
            }
            case IDLE -> {
                // Chỉ gọi khi một hành vi đang giữ quyền điều khiển.
            }
        }
    }

    /** Dê chỉ lấy đà vào thực thể sống đang đứng yên hoặc di chuyển chậm ở gần. */
    private LivingEntity findRamTarget(World world) {
        LivingEntity nearest = null;
        float best = RAM_RANGE * RAM_RANGE;
        for (int index = 0, count = world.entityCount(); index < count; index++) {
            if (world.entityAt(index) instanceof LivingEntity candidate && candidate != this && candidate.isAlive()
                    && isSlow(candidate)) {
                candidate.copyCenterPosition(targetCenter);
                float distance = targetCenter.dst2(x(), y() + height * 0.5f, z());
                if (distance <= best && distance >= 1f && Math.abs(targetCenter.y - y() - height * 0.5f) < 1.5f) {
                    nearest = candidate;
                    best = distance;
                }
            }
        }
        return nearest;
    }

    /** Đọc vận tốc vào bộ đệm tái sử dụng để loại mục tiêu đang chạy hoặc rơi nhanh. */
    private boolean isSlow(LivingEntity candidate) {
        return candidate.copyVelocity(targetVelocity).len2() <= 1.5f * 1.5f;
    }

    /** Hạ đầu trong lúc nhìn đích, rồi khóa hướng húc để mục tiêu có thể né sang bên. */
    private void prepareRam(float dt) {
        stopMoving();
        if (!onGround || inWater() || ramTarget == null || !ramTarget.isAlive()) {
            finishAction();
            return;
        }
        ramTarget.copyCenterPosition(targetCenter);
        lookAt(targetCenter.x, targetCenter.y, targetCenter.z);
        faceBody(targetCenter.x, targetCenter.z);
        mobVisualState().setPose(MobVisualState.Pose.RAM, Math.min(1f, 1f - actionRemaining / RAM_PREPARE + 0.15f));
        if (actionRemaining <= 0f) {
            float dx = targetCenter.x - x();
            float dz = targetCenter.z - z();
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            if (distance < CONTACT_EPSILON || distance > RAM_RANGE + 1f || !isSlow(ramTarget)) {
                finishAction();
                return;
            }
            ramDirectionX = dx / distance;
            ramDirectionZ = dz / distance;
            ramX = targetCenter.x + ramDirectionX * 0.9f;
            ramZ = targetCenter.z + ramDirectionZ * 0.9f;
            action = Action.RAM_CHARGE;
            actionRemaining = RAM_TIMEOUT + dt;
            steerDirect(x() + ramDirectionX, z() + ramDirectionZ, RAM_SPEED);
        }
    }

    /** Kiểm tra đoạn đường vừa quét và truyền xung lực mạnh theo hướng húc. */
    private boolean hitRamTarget(World world, float fromX, float fromZ) {
        if (ramTarget == null || !ramTarget.isAlive()) {
            return true;
        }
        ramTarget.copyPosition(targetCenter);
        if (targetCenter.y >= y() + height || targetCenter.y + ramTarget.getHeight() <= y()) {
            return false;
        }
        float dx = x() - fromX;
        float dz = z() - fromZ;
        float distance = dx * dx + dz * dz;
        float t = distance > 0f ? MathUtils.clamp(((targetCenter.x - fromX) * dx
                + (targetCenter.z - fromZ) * dz) / distance, 0f, 1f) : 0f;
        float reach = halfWidth + ramTarget.getHalfWidth();
        float missX = targetCenter.x - (fromX + t * dx);
        float missZ = targetCenter.z - (fromZ + t * dz);
        if (missX * missX + missZ * missZ > reach * reach) {
            return false;
        }
        ramTarget.damage(world, getMeleeDamage(2f), DamageSource.MELEE, this,
                ramDirectionX * 17f, ramDirectionZ * 17f);
        return true;
    }

    /** Thử các bãi đáp có nền, ưu tiên bề mặt cao trước mặt phẳng hiện tại. */
    private boolean findJumpDestination(World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            float angle = random.nextFloat(MathUtils.PI2);
            float distance = random.nextFloat(4.5f, JUMP_RANGE);
            int bx = MathUtils.floor(x() + MathUtils.cos(angle) * distance);
            int bz = MathUtils.floor(z() + MathUtils.sin(angle) * distance);
            float dx = bx + 0.5f - x();
            float dz = bz + 0.5f - z();
            if (dx * dx + dz * dz > JUMP_RANGE * JUMP_RANGE) {
                continue;
            }
            float feet = navigator().findStandableY(world, bx, MathUtils.floor(y() + JUMP_RANGE), bz);
            if (!Float.isFinite(feet)) {
                feet = navigator().findStandableY(world, bx, MathUtils.floor(y()), bz);
            }
            if (Float.isFinite(feet) && Math.abs(feet - y()) <= JUMP_RANGE) {
                jumpDestination.set(bx + 0.5f, feet, bz + 0.5f);
                if (canTraverseJump(world)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Giữ tư thế lấy đà, kiểm tra lại quỹ đạo rồi phóng theo vận tốc giải từ phương trình rơi. */
    private void prepareJump(World world, float dt) {
        stopMoving();
        faceBody(jumpDestination.x, jumpDestination.z);
        mobVisualState().setPose(MobVisualState.Pose.HOP, Math.min(1f, 1f - actionRemaining / JUMP_PREPARE));
        if (actionRemaining <= 0f) {
            if (!onGround || inWater() || !canTraverseJump(world)) {
                finishAction();
                return;
            }
            float rise = jumpDestination.y - y();
            float apex = jumpApex(rise);
            float speed = jumpSpeed(apex);
            float seconds = jumpTime(speed, apex, rise);
            float powered = Math.max(0.25f, seconds - 0.04f);
            queueLeap((jumpDestination.x - x()) / powered, speed + GRAVITY_MAGNITUDE * dt * 0.5f,
                    (jumpDestination.z - z()) / powered, seconds + 0.06f);
            action = Action.JUMP_FLIGHT;
            actionRemaining = seconds + 0.5f;
        }
    }

    /** Dò thể tích thân qua quỹ đạo để tránh nhảy vào vách, trần hoặc khối nguy hiểm. */
    private boolean canTraverseJump(World world) {
        float rise = jumpDestination.y - y();
        float apex = jumpApex(rise);
        float speed = jumpSpeed(apex);
        float seconds = jumpTime(speed, apex, rise);
        for (int step = 1; step <= 20; step++) {
            float fraction = step / 20f;
            float time = seconds * fraction;
            float feet = y() + speed * time - 0.5f * GRAVITY_MAGNITUDE * time * time;
            if (!navigator().canOccupy(world, MathUtils.lerp(x(), jumpDestination.x, fraction),
                    step == 20 ? jumpDestination.y : feet,
                    MathUtils.lerp(z(), jumpDestination.z, fraction))) {
                return false;
            }
        }
        return true;
    }

    /** Chọn độ cao đủ qua mép bãi đáp trong giới hạn 10 block. */
    private static float jumpApex(float rise) {
        return Math.min(JUMP_RANGE, Math.max(2.75f, rise + 1.5f));
    }

    /** Đổi độ cao đỉnh thành vận tốc dọc lúc bật. */
    private static float jumpSpeed(float apex) {
        return (float) Math.sqrt(2f * GRAVITY_MAGNITUDE * apex);
    }

    /** Tính thời gian lên đỉnh rồi rơi tới đúng cao độ bãi đáp. */
    private static float jumpTime(float speed, float apex, float rise) {
        return speed / GRAVITY_MAGNITUDE + (float) Math.sqrt(2f * (apex - rise) / GRAVITY_MAGNITUDE);
    }

    /** Xóa tư thế riêng và tính lại hồi chiêu sau mỗi lượt húc. */
    private void finishAction() {
        if (action == Action.RAM_WINDUP || action == Action.RAM_CHARGE) {
            ramTarget = null;
            scheduleRam();
        }
        action = Action.IDLE;
        mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
    }

    /** Dê hét dùng khoảng 5–15 giây, các cá thể còn lại dùng 30–300 giây. */
    private void scheduleRam() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ramDelay = screaming ? random.nextFloat(5f, 15f) : random.nextFloat(30f, 300f);
    }
}
