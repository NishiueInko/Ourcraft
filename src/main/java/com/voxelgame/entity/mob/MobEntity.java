package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.MathUtils;
import com.voxelgame.engine.AudioManager;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisual;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Nền tảng vật lý, hoạt ảnh và goal dùng chung cho các loài mob sống. */
public class MobEntity extends LivingEntity implements MobVisual {
    /** Gia tốc theo block/giây² và giới hạn vận tốc rơi theo từng môi trường. */
    private static final float GRAVITY = -26f;
    private static final float MAX_FALL_SPEED = -40f;
    private static final float WATER_GRAVITY = GRAVITY * 0.25f;
    private static final float WATER_FALL_SPEED = -2.8f;
    private static final float FLOAT_SPEED = 2.5f;
    /** Nhảy qua bậc một block và hỗ trợ bậc cao hơn bằng bước trực tiếp. */
    private static final float JUMP_SPEED = 8.6f;
    private static final float MAX_JUMP_STEP = 1f;
    /** Khoảng rơi an toàn mặc định và ngưỡng cho hai loài nhẹ chân. */
    private static final float NORMAL_SAFE_FALL = 3f;
    private static final float LONG_SAFE_FALL = 8f;
    /** Khoảng thời gian phản ứng và khoảng cách giữa hai tiếng chân. */
    private static final float NEUTRAL_ANGER_SECONDS = 15f;
    private static final float STEP_SOUND_SPACING = 0.9f;
    /** Tỷ lệ hiển thị và cao độ cho cá thể non. */
    private static final float BABY_SCALE = 0.6f;
    private static final float BABY_PITCH = 1.5f;

    /** Cấu hình loài, trạng thái kết xuất, bộ điều hướng và goal cấp phát một lần. */
    private final MobType type;
    private final MobGoalSelector goals = new MobGoalSelector();
    private final SimpleGridNavigator navigator = new SimpleGridNavigator(this);
    private final MobVisualState visual;

    /** Sinh trưởng, lưu giữ cá thể, cấu hình chuyển động và trạng thái trong nước. */
    private boolean baby;
    private boolean persistent;
    private boolean autoFloat = true;
    private boolean canFloat = true;
    private boolean inWater;
    private boolean directStep;
    private float stepHeight = MAX_JUMP_STEP;
    private float maxSafeFall;
    private float moveSpeed;
    private float noActionTime;

    /** Đích sống, tác nhân gây đòn gần nhất và các bộ đếm phản ứng. */
    private LivingEntity target;
    private Entity lastAttacker;
    private int hurtSequence;
    private float angerTimer;
    private float panicTime;
    private boolean stoppedAfterDeath;

    /** Điều khiển ngắn hạn dùng giữa lượt goal và lượt quét vật lý. */
    private float wishX;
    private float wishZ;
    private float pushX;
    private float pushZ;
    private boolean requestJump;
    private boolean requestFloat;
    /** Lệnh bám tường và đà ngang cho cú vồ trong thời gian đang bay. */
    private boolean wallClimbRequested;
    private boolean climbing;
    private float leapRemaining;
    private float leapX;
    private float leapZ;
    private boolean lookRequested;
    private boolean faceRequested;
    private float faceX;
    private float faceZ;
    private float lookX;
    private float lookY;
    private float lookZ;

    /** Pha hoạt ảnh, hướng thân và đầu, quãng đường phát bước và nhịp tiếng chờ. */
    private float bodyYaw;
    private float headYaw;
    private float headPitch;
    private float limbSwing;
    private float limbSwingAmount;
    private double ageSeconds;
    private float stepSoundDistance;
    private float idleSoundTimer;

    /** Khởi tạo cá thể với hộp va chạm và chỉ số chung từ danh mục loài. */
    public MobEntity(MobType type, float x, float y, float z) {
        this(type, x, y, z, 0);
    }

    /** Khởi tạo mẫu cưỡi có số ghế đúng với lớp chuyên biệt. */
    protected MobEntity(MobType type, float x, float y, float z, int seatCount) {
        this(type, x, y, z, 0f, seatCount);
    }

    /** Cho phép lớp chuyên biệt thêm giáp tự nhiên và ghế, dùng nguyên vòng đời của LivingEntity. */
    protected MobEntity(MobType type, float x, float y, float z, float armor, int seatCount) {
        this(type, x, y, z, armor, seatCount, true);
    }

    /** Cho phép lớp chuyên biệt thay toàn bộ danh sách goal ngay lúc khởi tạo. */
    protected MobEntity(MobType type, float x, float y, float z, float armor, int seatCount,
                        boolean installDefaultGoals) {
        this(type, x, y, z, armor, seatCount, installDefaultGoals, 1f);
    }

    /** Dùng đúng kích thước thể chất ngay khi tạo cá thể non hoặc biến thể thu nhỏ. */
    protected MobEntity(MobType type, float x, float y, float z, float armor, int seatCount,
                        boolean installDefaultGoals, float dimensionsScale) {
        super(x, y, z, type.width() * 0.5f * dimensionsScale, type.height() * dimensionsScale,
                type.health(), armor, type.undead(), seatCount);
        this.type = type;
        visual = new MobVisualState(type.appearance());
        persistent = type.category() == MobCategory.CREATURE;
        maxSafeFall = type == MobType.CHICKEN || type == MobType.GOAT ? LONG_SAFE_FALL : NORMAL_SAFE_FALL;
        moveSpeed = type.attitude() == MobAttitude.HOSTILE ? 3f : 2.45f;
        idleSoundTimer = ThreadLocalRandom.current().nextFloat(2.5f, 5f);
        if (installDefaultGoals) {
            MobGoals.addDefaults(goals, type);
        }
    }

    /** Cập nhật AI, vật lý theo X → Z → Y và thông tin hoạt ảnh đã cấp phát. */
    @Override
    protected void updateLiving(World world, float dt) {
        ageSeconds += dt;
        updateReactionTimers(dt);
        inWater = touchesLiquid(world, Block.WATER);
        if (!persistent && hasPlayerPassenger()) {
            persistent = true;
        }
        LivingEntity mount = getMount();
        if (mount != null) {
            mount.copyCenterPosition(position);
            position.y += mount.getHeight() * 0.5f;
            velocity.setZero();
            onGround = false;
            stopMoving();
            goals.stopAll(world, this);
            updateMounted(world, dt);
            updateAnimation(0f, 0f, dt);
        } else {
            updateGoals(world, dt);
            if (!isAlive()) {
                return;
            }
            float oldX = position.x;
            float oldZ = position.z;
            move(world, dt);
            updateAnimation(position.x - oldX, position.z - oldZ, dt);
        }
        wishX = 0f;
        wishZ = 0f;
        requestJump = false;
        requestFloat = false;
        wallClimbRequested = false;
        lookRequested = false;
        faceRequested = false;
        inWater = touchesLiquid(world, Block.WATER);
        if (burnsInSunlight() && isSunExposed(world)) {
            ignite(2f);
        }
        updateIdleSound(world, dt);
    }

    /** Điểm mở rộng cho lớp chuyên biệt thay đổi điều khiển trước khi quét vật lý. */
    protected void updateGoals(World world, float dt) {
        goals.tick(world, this, dt);
    }

    /** Cung cấp lượt tấn công hoặc ngắm bắn riêng cho kỵ sĩ sau khi đã đồng bộ ghế. */
    protected void updateMounted(World world, float dt) {
    }

    /** Giảm thời gian hốt hoảng, xóa mục tiêu đã chết hoặc khi mob trung lập hết giận. */
    private void updateReactionTimers(float dt) {
        panicTime = Math.max(0f, panicTime - dt);
        if (angerTimer > 0f) {
            angerTimer = Math.max(0f, angerTimer - dt);
            if (angerTimer == 0f && attitude() == MobAttitude.NEUTRAL) {
                target = null;
            }
        }
        if (target != null && !target.isAlive()) {
            target = null;
        }
    }

    /** Tách vận tốc điều khiển khỏi xung lực rồi quét từng trục của hộp va chạm. */
    private void move(World world, float dt) {
        climbing = false;
        if (leapRemaining > 0f) {
            leapRemaining = Math.max(0f, leapRemaining - dt);
            wishX = leapX;
            wishZ = leapZ;
        }
        float speedScale = getMovementSpeedMultiplier() * (inWater ? waterSpeedMultiplier() : 1f);
        float blend = Math.min(1f, dt * 18f);
        velocity.x = wishX == 0f ? 0f : MathUtils.lerp(velocity.x, wishX * speedScale, blend);
        velocity.z = wishZ == 0f ? 0f : MathUtils.lerp(velocity.z, wishZ * speedScale, blend);
        if (requestJump && onGround && !inWater && navigator.canRise(world, x(), y(), z(), 0.1f)) {
            beginJump();
        }
        moveHorizontal(world, 0, velocity.x * dt, pushX * dt);
        moveHorizontal(world, 2, velocity.z * dt, pushZ * dt);
        float pushDecay = Math.max(0f, 1f - dt * 12f);
        pushX *= pushDecay;
        pushZ *= pushDecay;

        if (climbing && !inWater) {
            velocity.y = 3.2f;
        } else if (inWater) {
            boolean rise = canFloat() && (autoFloat || requestFloat);
            velocity.y = rise ? Math.max(velocity.y, FLOAT_SPEED)
                    : Math.max(WATER_FALL_SPEED, velocity.y + WATER_GRAVITY * dt);
        } else {
            velocity.y = Math.max(terminalFallSpeed(), velocity.y + GRAVITY * dt);
        }
        Block hit = moveAxis(world, 1, velocity.y * dt);
        onGround = hit != null && velocity.y < 0f;
        if (hit != null) {
            velocity.y = 0f;
        }
        if (onGround) {
            leapRemaining = 0f;
        }
    }

    /** Chặn lệnh đi vào hố hoặc gai rồi thử nhảy/bước khi quét gặp bậc phù hợp. */
    private void moveHorizontal(World world, int axis, float requested, float pushed) {
        boolean xAxis = axis == 0;
        float destX = x() + (xAxis ? requested : 0f);
        float destZ = z() + (xAxis ? 0f : requested);
        if (requested != 0f) {
            boolean legal = onGround && !inWater
                    ? Float.isFinite(navigator.findFeetY(world, destX, y(), destZ))
                    : navigator.avoidsHazards(world, destX, y(), destZ);
            boolean approachesWall = !legal && wallClimbRequested && navigator.avoidsHazards(world, destX, y(), destZ)
                    && !navigator.canOccupy(world, destX, y(), destZ);
            if (!legal && !approachesWall) {
                requested = 0f;
            }
        }
        float start = xAxis ? x() : z();
        float distance = requested + pushed;
        Block hit = moveAxis(world, axis, distance);
        if (hit == null) {
            return;
        }
        if (requested != 0f && !inWater && climbWall(world, hit)) {
            climbing = true;
        }
        float rise = obstacleTop - y();
        float stepLimit = directStep ? stepHeight : Math.min(stepHeight, MAX_JUMP_STEP);
        if (requested != 0f && onGround && !inWater && hit != Block.CACTUS
                && rise > CONTACT_EPSILON && rise <= stepLimit + CONTACT_EPSILON
                && navigator.canRise(world, x(), y(), z(), rise)
                && navigator.canOccupy(world, destX, y() + rise, destZ)) {
            if (!directStep) {
                beginJump();
                return;
            }
            position.y += rise;
            float reached = xAxis ? x() : z();
            if (moveAxis(world, axis, start + distance - reached) == null) {
                return;
            }
        }
        if (xAxis) {
            velocity.x = 0f;
        } else {
            velocity.z = 0f;
        }
    }

    /** Bật vận tốc hướng lên sau khi kiểm tra đủ chỗ theo độ cao bậc. */
    private void beginJump() {
        velocity.y = JUMP_SPEED;
        onGround = false;
    }

    /** Nhận một cú vồ ngắn với vận tốc ngang được giữ qua nhiều khung hình. */
    protected final void queueLeap(float vx, float vy, float vz) {
        queueLeap(vx, vy, vz, 0.46f);
    }

    /** Giữ động lượng ngang đến khi hết nhịp phóng hoặc chân chạm nền. */
    protected final void queueLeap(float vx, float vy, float vz, float seconds) {
        if (onGround && !inWater && vy > 0f) {
            leapX = vx;
            leapZ = vz;
            leapRemaining = seconds;
            velocity.y = vy;
            onGround = false;
        }
    }

    /** Hướng cú lao theo tọa độ đích, vẫn qua quét va chạm và kiểm tra nguy hiểm. */
    protected final void steerDirect(float x, float z, float speed) {
        float dx = x - position.x;
        float dz = z - position.z;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length > CONTACT_EPSILON) {
            steer(dx / length, dz / length, speed);
        }
    }

    /** Giới hạn vận tốc rơi trong không khí, mặc định dùng vật lý chung của mob. */
    protected float terminalFallSpeed() {
        return MAX_FALL_SPEED;
    }

    /** Hệ số tốc độ ngang trong nước, có thể tăng cho loài bơi khỏe. */
    protected float waterSpeedMultiplier() {
        return 0.67f;
    }

    /** Tiếp tục ép nhẹ vào mặt vật cản để loài biết leo có tiếp xúc ngang với tường. */
    protected final void steerTowardWall(float x, float z) {
        float dx = x - position.x;
        float dz = z - position.z;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length > CONTACT_EPSILON) {
            steer(dx / length, dz / length, moveSpeed);
            wallClimbRequested = true;
        }
    }

    /** Cho phép loài biết leo xác nhận loại khối đang va chạm ở mặt bên. */
    protected boolean climbWall(World world, Block wall) {
        return false;
    }

    /** Đặt lại quãng rơi khi đang bám tường hoặc khi được thú cưỡi đỡ. */
    @Override
    protected boolean ignoresFall() {
        return climbing || super.ignoresFall();
    }

    /** Dùng chuyển động thật và đích nhìn để xoay thân, xoay đầu và điều khiển pha chân. */
    private void updateAnimation(float dx, float dz, float dt) {
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (faceRequested) {
            bodyYaw = moveAngle(bodyYaw, yawOf(faceX - x(), faceZ - z()), dt * 420f);
        } else if (distance > CONTACT_EPSILON) {
            float heading = yawOf(dx, dz);
            bodyYaw = moveAngle(bodyYaw, heading, dt * 420f);
        }
        float wantHeadYaw = 0f;
        float wantHeadPitch = 0f;
        if (lookRequested) {
            float toX = lookX - x();
            float toZ = lookZ - z();
            float horizontal = (float) Math.sqrt(toX * toX + toZ * toZ);
            if (horizontal > CONTACT_EPSILON) {
                wantHeadYaw = MathUtils.clamp(wrapDegrees(yawOf(toX, toZ) - bodyYaw), -75f, 75f);
            }
            wantHeadPitch = MathUtils.clamp(MathUtils.atan2(lookY - (y() + height * 0.8f), horizontal)
                    * MathUtils.radiansToDegrees, -65f, 65f);
        }
        headYaw = moveAngle(headYaw, wantHeadYaw, dt * 300f);
        headPitch = moveAngle(headPitch, wantHeadPitch, dt * 240f);
        limbSwing = (limbSwing + distance * 6f) % MathUtils.PI2;
        limbSwingAmount = MathUtils.lerp(limbSwingAmount, Math.min(1f, distance / dt * 0.38f),
                Math.min(1f, dt * 10f));
        float ageInTicks = (float) (ageSeconds * 20d);
        visual.setAnimation(bodyYaw, headYaw, headPitch, limbSwing, limbSwingAmount, ageInTicks);
        if (onGround) {
            stepSoundDistance += distance;
        }
    }

    /** Trả góc quanh trục Y cho mẫu hướng về -Z ở tư thế gốc. */
    private static float yawOf(float dx, float dz) {
        return MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees;
    }

    /** Xoay theo hướng ngắn nhất và chặn theo tốc độ góc. */
    private static float moveAngle(float current, float goal, float limit) {
        return wrapDegrees(current + MathUtils.clamp(wrapDegrees(goal - current), -limit, limit));
    }

    /** Quy góc về khoảng -180 đến 180 độ để nội suy qua đường cắt. */
    private static float wrapDegrees(float angle) {
        return (angle + 540f) % 360f - 180f;
    }

    /** Phát bước theo quãng đường và giọng chờ theo khoảng nghỉ ngẫu nhiên. */
    private void updateIdleSound(World world, float dt) {
        if (stepSoundDistance >= STEP_SOUND_SPACING) {
            stepSoundDistance %= STEP_SOUND_SPACING;
            playSound(world, MobSoundEvent.STEP);
        }
        idleSoundTimer -= dt;
        if (idleSoundTimer <= 0f) {
            idleSoundTimer = ThreadLocalRandom.current().nextFloat(4f, 10f);
            playSound(world, MobSoundEvent.AMBIENT);
        }
    }

    /** Kiểm tra nắng trực tiếp tại đầu mob, loại trừ nước, mái và mưa cục bộ. */
    public boolean isSunExposed(World world) {
        if (world.worldTime().sunY() <= 0.05f || touchesLiquid(world, Block.WATER)) {
            return false;
        }
        float headY = y() + height;
        return !world.hasWeatherRoof(MathUtils.floor(x()), headY, MathUtils.floor(z()))
                && !world.isRainingAt(x(), headY, z());
    }

    /** Gửi một lệnh lái theo đích thế giới cho lượt vật lý kế tiếp. */
    public void moveToward(World world, float x, float y, float z, float speedMultiplier) {
        if (!Float.isFinite(y)) {
            stopMoving();
            return;
        }
        navigator.moveToward(world, x, z, moveSpeed * speedMultiplier);
    }

    /** Đặt hướng chạy đã chuẩn hóa, duy trì xung lực bị đánh độc lập. */
    void steer(float dx, float dz, float speed) {
        wishX = dx * speed;
        wishZ = dz * speed;
    }

    /** Hủy điều khiển ngang của goal nhưng tiếp tục xử lý động lượng đẩy lùi. */
    public void stopMoving() {
        wishX = 0f;
        wishZ = 0f;
        navigator.stop();
    }

    /** Hướng mắt về một điểm thế giới trong lượt hoạt ảnh hiện tại. */
    public void lookAt(float x, float y, float z) {
        lookX = x;
        lookY = y;
        lookZ = z;
        lookRequested = true;
    }

    /** Giữ thân hướng về đích trong khi đi ngang, lùi hoặc ngắm từ trên lưng thú. */
    public final void faceBody(float x, float z) {
        faceX = x;
        faceZ = z;
        faceRequested = Math.abs(x - position.x) + Math.abs(z - position.z) > CONTACT_EPSILON;
    }

    /** Yêu cầu nhảy trên mặt đất trong lượt vật lý tiếp theo. */
    public void requestJump() {
        requestJump = true;
    }

    /** Yêu cầu bơi lên khi một goal giữ quyền chuyển động dọc. */
    public void requestFloat() {
        requestFloat = true;
    }

    /** Cho phép điều khiển lớp con tắt lực nổi nội tại và để goal chủ động yêu cầu bơi lên. */
    protected final void setAutoFloat(boolean enabled) {
        autoFloat = enabled;
    }

    /** Điều chỉnh khả năng nổi, áp dụng cả lực tự nổi lẫn lệnh của goal. */
    public void setCanFloat(boolean allowed) {
        canFloat = allowed;
    }

    /** Kiểm tra loại mob hiện cho phép nổi trong nước hay không. */
    public boolean canFloat() {
        return canFloat;
    }

    /** Trả về mặt đỡ hợp lệ gần cao độ tham chiếu tại tâm ô. */
    public boolean canStandAt(World world, int x, int y, int z) {
        return Float.isFinite(navigator.findStandableY(world, x, y, z));
    }

    /** Ghi nhận hit thành công, tạo phản ứng hoảng/giận và phát đúng tiếng bị thương hoặc chết. */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        if (!super.damage(world, amount, source, attacker, knockbackX, knockbackZ)) {
            return false;
        }
        hurtSequence++;
        lastAttacker = attacker;
        if (isAlive()) {
            if (attacker != null && attacker != this && attitude() == MobAttitude.PASSIVE) {
                panicTime = ThreadLocalRandom.current().nextFloat(3f, 5f);
            }
            if (attacker instanceof LivingEntity living && living != this && living.isAlive()
                    && attitude() != MobAttitude.PASSIVE) {
                target = living;
                angerTimer = NEUTRAL_ANGER_SECONDS;
            }
            playSound(world, MobSoundEvent.HURT);
        } else {
            target = null;
            playSound(world, MobSoundEvent.DEATH);
        }
        return true;
    }

    /** Gây đòn đã qua kiểm tra tầm và hồi chiêu, cho phép loài thêm hiệu ứng sau khi trúng. */
    protected boolean strikeMelee(World world, LivingEntity victim, float baseDamage, float knockbackX, float knockbackZ) {
        return victim.damage(world, getMeleeDamage(baseDamage), DamageSource.MELEE, this, knockbackX, knockbackZ);
    }

    /** Nhận diện đồng loại, thú cưỡi và kỵ sĩ của cùng một đội. */
    public boolean isAlliedTo(MobEntity other) {
        LivingEntity mount = getMount();
        return other == this || type == other.type || other == mount || other.getMount() == this
                || mount != null && mount == other.getMount();
    }

    /** Ghi mục tiêu mới khi đạn lạc trúng quái ngoài đội. */
    public final void retaliateAgainst(MobEntity other) {
        if (isAlive() && other.isAlive() && !isAlliedTo(other)) {
            enrage(other, NEUTRAL_ANGER_SECONDS);
        }
    }

    /** Tạo phản ứng giận dữ có thời hạn cho một đòn đánh hoặc kích thích riêng của loài. */
    protected final void enrage(LivingEntity victim, float seconds) {
        if (victim != this && victim.isAlive()) {
            target = victim;
            angerTimer = Math.max(angerTimer, seconds);
        }
    }

    /** Dừng các goal đúng một lần khi đã vào vòng đời chết. */
    @Override
    protected void finishLivingUpdate(World world) {
        if (!isAlive() && !stoppedAfterDeath) {
            goals.stopAll(world, this);
            stopMoving();
            stoppedAfterDeath = true;
        }
    }

    /** Giữ đẩy lùi riêng để lệnh AI kế tiếp không xóa xung lực. */
    @Override
    protected void applyKnockback(float x, float z) {
        pushX += x;
        pushZ += z;
    }

    /** Loài có giới hạn rơi lớn hơn cũng giảm sát thương theo phần ngưỡng được tăng. */
    @Override
    protected float getFallReduction() {
        return Math.max(0f, maxSafeFall - NORMAL_SAFE_FALL);
    }

    /** Phát sự kiện của loài theo vị trí và cao độ cá thể qua bộ âm thanh đã nạp. */
    protected void playSound(World world, MobSoundEvent event) {
        AudioManager audio = world == null ? null : world.getAudio();
        if (audio != null) {
            audio.playMobSound(type.id(), event, x(), y() + height * 0.5f, z(), 0f, baby ? BABY_PITCH : 1f);
        }
    }

    /** Trả đối tượng trạng thái cố định để lượt vẽ mob dùng lại. */
    @Override
    public MobVisualState mobVisualState() { return visual; }

    /** Hình học mob được vẽ tập trung trong MobRenderer. */
    @Override
    public void render(ShaderProgram shader) {
    }

    /** Trả tên loài và các thuộc tính cố định trong danh mục. */
    public final MobType type() { return type; }
    /** Trả thái độ cơ bản để goal và lớp chuyên biệt chọn phản ứng. */
    public MobAttitude attitude() { return type.attitude(); }
    /** Trả hiệu lực cháy nắng của loài. */
    public boolean burnsInSunlight() { return type.burnsInSunlight(); }
    /** Trả bộ goal tái sử dụng để lớp con có thể đăng ký hành vi. */
    public final MobGoalSelector goals() { return goals; }
    /** Trả bộ điều hướng cố định của cá thể. */
    public final SimpleGridNavigator navigator() { return navigator; }
    /** Trả tọa độ chân theo trục X mà không tạo Vector3. */
    public final float x() { return position.x; }
    /** Trả tọa độ chân theo trục Y mà không tạo Vector3. */
    public final float y() { return position.y; }
    /** Trả tọa độ chân theo trục Z mà không tạo Vector3. */
    public final float z() { return position.z; }
    /** Trả trạng thái chạm nước tại phần thân của cá thể. */
    public final boolean inWater() { return inWater; }
    /** Trả tốc độ cơ bản theo block/giây. */
    public final float moveSpeed() { return moveSpeed; }
    /** Trả số giây mô phỏng của cá thể, dùng làm mốc đơn điệu cho goal. */
    public final double ageSeconds() { return ageSeconds; }
    /** Gán tốc độ cơ bản không âm và hữu hạn cho lớp chuyên biệt. */
    public final void setMoveSpeed(float speed) { moveSpeed = Float.isFinite(speed) ? Math.max(0f, speed) : 0f; }
    /** Trả độ cao tối đa mà bộ điều hướng chấp nhận đi lên. */
    public final float stepHeight() { return stepHeight; }
    /** Đặt ngưỡng lên bậc, tối đa 1,5 block cho thú có khả năng bước. */
    public final void setStepHeight(float height) {
        if (Float.isFinite(height)) {
            stepHeight = MathUtils.clamp(height, 0f, 1.5f);
        }
    }
    /** Bật bước thẳng thay cho nhảy, dùng cho thú cưỡi. */
    public final void setDirectStep(boolean enabled) { directStep = enabled; }
    /** Trả quãng rơi lớn nhất ứng với một điểm đi xuống. */
    public final float maxSafeFall() { return maxSafeFall; }
    /** Đặt quãng rơi an toàn cho lớp chuyên biệt. */
    public final void setMaxSafeFall(float blocks) {
        if (Float.isFinite(blocks)) {
            maxSafeFall = MathUtils.clamp(blocks, 0f, World.WORLD_SIZE_Y);
        }
    }
    /** Trả mục tiêu hiện tại mà goal chiến đấu có thể bám theo. */
    public final LivingEntity target() { return target; }
    /** Đổi mục tiêu hoặc xóa khi không còn đối tượng phù hợp. */
    public final void setTarget(LivingEntity target) { this.target = target == this ? null : target; }
    /** Trả tác nhân của lần bị thương gần nhất, nếu đòn có tác nhân. */
    public final Entity lastAttacker() { return lastAttacker; }
    /** Trả số thứ tự đòn trúng để goal phân biệt các lần gây sát thương. */
    public final int hurtSequence() { return hurtSequence; }
    /** Trả thời gian hoảng còn lại, tính bằng giây. */
    public final float panicTime() { return panicTime; }
    /** Trả thời gian giận còn lại, tính bằng giây. */
    public final float angerTimer() { return angerTimer; }
    /** Trả trạng thái non để hành vi và âm thanh dùng cùng một giá trị. */
    public final boolean isBaby() { return baby; }
    /** Đặt trạng thái non và tỷ lệ hình hiển thị liên quan. */
    public final void setBaby(boolean baby) {
        this.baby = baby;
        visual.setScale(baby ? BABY_SCALE : 1f);
    }
    /** Đánh dấu cá thể thuần hóa hoặc được giữ bằng quy tắc riêng của loài. */
    public final void setPersistent(boolean persistent) { this.persistent = persistent; }
    /** Giữ cá thể đã được đánh dấu hoặc đang chở người chơi. */
    public final boolean isPersistent() {
        return persistent || hasPlayerPassenger();
    }

    /** Phân biệt người cưỡi với mob ngồi trên thú trong một nhóm xuất hiện tự nhiên. */
    private boolean hasPlayerPassenger() {
        for (int seat = 0; seat < getSeatCount(); seat++) {
            if (getPassenger(seat) instanceof Player) {
                return true;
            }
        }
        return false;
    }
    /** Đọc số giây ở xa người chơi mà bộ spawn đã tích lũy. */
    public final float noActionTime() { return noActionTime; }
    /** Cập nhật thời lượng ở xa hoặc xóa khi trở về gần người chơi. */
    public final void setNoActionTime(float seconds) { noActionTime = Math.max(0f, seconds); }
}
