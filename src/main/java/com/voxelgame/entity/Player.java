package com.voxelgame.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.AudioManager;
import com.voxelgame.engine.BiomeType;
import com.voxelgame.engine.Camera;
import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Thực thể Người chơi kế thừa từ {@link Entity} và sở hữu {@link Hotbar} theo quan hệ Hợp thành (Composition).
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Kế thừa (Inheritance): Tái sử dụng `position`, `velocity`, `halfWidth`, `height`, `onGround`
 *    và phương thức kiểm tra va chạm `intersectsBlock` từ lớp cha `Entity`.
 * 2. Hợp thành & Đóng gói (Composition & Encapsulation): Sở hữu `private final Hotbar hotbar = new Hotbar();`
 *    và quản lý trạng thái di chuyển, bao gồm bật/tắt bay bằng Space, hạ độ cao và thoát bay bằng
 *    Left Shift, chạy nhanh bằng Left Ctrl và rón rén giữ mép block.
 * 3. Đa hình (Polymorphism): Ghi đè `update(World, float)` và `render(ShaderProgram)` của `Entity`.
 */
public class Player extends LivingEntity {

    // Kích thước hộp va chạm AABB tiêu chuẩn của nhân vật (Rộng 0.6 -> halfWidth = 0.3, Cao 1.8)
    private static final float PLAYER_HALF_WIDTH = 0.3f;
    private static final float PLAYER_HEIGHT = 1.8f;

    // Chiều cao tầm mắt tiêu chuẩn (1.62f) và chiều cao tầm mắt khi cúi rón rén Sneak (1.45f)
    private static final float STANDING_EYE_HEIGHT = 1.62f;
    private static final float SNEAKING_EYE_HEIGHT = 1.45f;
    private float currentEyeHeight = STANDING_EYE_HEIGHT;

    // Các hằng số tốc độ di chuyển và vật lý
    private static final float MOVE_SPEED = 5.5f;
    private static final float SPRINT_MULTIPLIER = 1.5f;
    private static final float SNEAK_MULTIPLIER = 0.5f;
    private static final float FLY_SPEED_MULTIPLIER = 1.8f;
    private static final float FLY_ASCEND_SPEED = 7.0f;
    // Tốc độ hạ độ cao chủ động khi giữ Left Shift trong chế độ bay (block/giây).
    private static final float FLY_DESCEND_SPEED = 4.0f;

    private static final float GRAVITY = -26.0f;
    private static final float JUMP_VELOCITY = 8.6f;
    private static final float TERMINAL_VELOCITY = -45.0f;

    // Khoảng thời gian tối đa giữa hai lần nhấn Space hoặc Left Shift (0.30 giây).
    private static final float DOUBLE_TAP_THRESHOLD = 0.30f;
    /** Giới hạn bước tuyết và phản hồi khi chạm gai. */
    private static final float MAX_SNOW_STEP = 0.5f;
    private static final float CACTUS_COOLDOWN = 0.5f;
    private static final float CACTUS_FLASH_DURATION = 0.25f;
    private static final float CACTUS_PUSH = 2.0f;
    /** Máu, độ đói và nhịp chiến đấu cơ bản của người chơi. */
    private static final float PLAYER_HEALTH = 20f;
    private static final float MAX_HUNGER = 20f;
    private static final float MELEE_COOLDOWN = 0.6f;
    private static final float LAVA_BURN_SECONDS = 15f;
    /** Hồi một HP và tiêu hao một điểm đói sau mỗi bốn giây đủ điều kiện. */
    private static final float REGEN_HUNGER_THRESHOLD = 18f;
    private static final float REGEN_INTERVAL = 4f;
    private static final float HUNGER_EFFECT_PER_SECOND = 0.5f;
    /** Bộ đệm điều khiển dùng lại mỗi khung hình. */
    private final Vector3 forwardXZ = new Vector3();
    private final Vector3 rightXZ = new Vector3();
    private final Vector3 wishDir = new Vector3();
    private float cactusCooldown;
    private float cactusFlash;
    private float pushX;
    private float pushZ;
    /** Các bộ đếm sinh tồn độc lập với điều khiển và phản hồi xương rồng. */
    private float hunger = MAX_HUNGER;
    private float regenElapsed;
    private float attackCooldown;

    /** Tích lũy quãng đường và thời gian trên không để phát âm theo chuyển động thực. */
    private float stepDistance;
    private float swimDistance;
    private float airborneTime;
    /** Trạng thái âm thanh của chất lỏng tại thân, tại mắt và quanh người chơi. */
    private Block previousLiquid = Block.AIR;
    private boolean wasUnderwater;
    private float nearbyLiquidDelay;

    // Quan hệ Hợp thành (Composition - Player "has-a" Hotbar)
    private final Hotbar hotbar = new Hotbar();

    // Tham chiếu góc nhìn Camera
    private Camera camera;

    // Các cờ trạng thái chuyển động của nhân vật
    private boolean isFlying = false;
    private boolean isSprinting = false;
    private boolean isSneaking = false;

    /** Các mức tốc độ kiểm tra địa hình áp dụng cho di chuyển ngang và điều khiển độ cao khi bay. */
    private static final int[] TERRAIN_SPEED_MULTIPLIERS = {1, 3, 5, 10, 25, 50};
    private int terrainSpeedIndex;

    // Biến phục vụ nhận diện thao tác bấm đúp phím Space (Double-tap Space)
    private boolean wasSpacePressed = false;
    private float lastSpaceTapTime = -1.0f;
    // Chỉ tính cạnh nhấn Left Shift trong cùng một lần bay, kể cả khi giữ phím qua nhiều khung hình.
    private boolean wasShiftPressed;
    private float lastShiftTapTime = Float.NEGATIVE_INFINITY;
    private float playerElapsedTime = 0.0f;

    public Player(Vector3 startPosition) {
        this(startPosition.x, startPosition.y, startPosition.z);
    }

    public Player(float startX, float startY, float startZ) {
        super(startX, startY, startZ, PLAYER_HALF_WIDTH, PLAYER_HEIGHT, PLAYER_HEALTH, 0f, false);
    }

    /** Chuyển sang mức tốc độ kế tiếp và quay về 1x sau 50x. */
    public void cycleTerrainSpeed() {
        terrainSpeedIndex = (terrainSpeedIndex + 1) % TERRAIN_SPEED_MULTIPLIERS.length;
    }

    /** Đặt chân lên nền hợp lệ gần tâm biome và đồng bộ trạng thái chuyển động với vị trí mới. */
    public boolean teleportToBiome(World world, BiomeType biome) {
        if (!world.setBiomeCenterPosition(position, biome)) {
            return false;
        }

        // Xóa đà, lực đẩy, chuỗi nhấn đúp và quãng đường âm thanh ở vị trí cũ.
        velocity.setZero();
        resetFallTracking();
        pushX = pushZ = 0f;
        isFlying = false;
        onGround = true;
        lastSpaceTapTime = Float.NEGATIVE_INFINITY;
        lastShiftTapTime = Float.NEGATIVE_INFINITY;
        airborneTime = stepDistance = swimDistance = 0f;
        previousLiquid = Block.AIR;
        wasUnderwater = false;
        nearbyLiquidDelay = 0f;
        if (camera != null) {
            camera.setPosition(position.x, position.y + currentEyeHeight, position.z);
        }
        return true;
    }

    /**
     * Liên kết đối tượng Camera để Player đọc hướng nhìn và đồng bộ tọa độ tầm mắt.
     *
     * @param camera đối tượng Camera góc nhìn thứ nhất
     */
    public void attachControls(Camera camera) {
        this.camera = camera;
    }

    /**
     * Phương thức nạp chồng tiện ích giữ tương thích với các lệnh gọi cũ (nếu có).
     */
    public void attachControls(long windowHandle, Camera camera) {
        this.camera = camera;
    }

    /** Chép tia từ tâm ngắm vào bộ đệm của AI, dùng cùng góc và cao độ mắt như camera. */
    public boolean copyAim(Vector3 eye, Vector3 direction) {
        if (camera == null) {
            return false;
        }
        eye.set(position.x, position.y + currentEyeHeight, position.z);
        camera.copyFront(direction);
        return true;
    }

    /**
     * Phương thức nạp chồng tiện ích cho phép truyền camera khi cập nhật.
     */
    public void update(World world, Camera camera, float deltaTime) {
        attachControls(camera);
        update(world, deltaTime);
    }

    /**
     * Cập nhật phần điều khiển của thực thể sống:
     * Thực thi toàn bộ điều khiển bàn phím, trạng thái Bay/Chạy/Rón rén, vật lý va chạm AABB
     * và đồng bộ vị trí mắt Camera.
     */
    @Override
    protected void updateLiving(World world, float deltaTime) {
        playerElapsedTime += deltaTime;
        attackCooldown = Math.max(0f, attackCooldown - deltaTime);
        cactusCooldown = Math.max(0, cactusCooldown - deltaTime);
        cactusFlash = Math.max(0, cactusFlash - deltaTime);
        updateRegeneration(deltaTime);
        if (camera == null) {
            updateLavaContact(world);
            return;
        }

        float startX = position.x;
        float startY = position.y;
        float startZ = position.z;
        if (Gdx.input != null) {
            handleInput(world, camera, deltaTime);
        }
        Block support = applyPhysicsAndCollision(world, deltaTime);
        if (!isAlive()) {
            return;
        }

        // Nếu rơi xuống dưới đáy bản đồ, đưa người chơi trở lại vị trí an toàn trên mặt đất
        boolean respawned = position.y < -25.0f;
        if (respawned) {
            moveToRespawn(world);
        } else {
            updateLavaContact(world);
            if (!isAlive()) {
                return;
            }
        }

        // Nội suy mượt mà chiều cao tầm mắt Camera khi chuyển đổi giữa Đứng thẳng (1.62f) và Sneak (1.45f)
        float targetEye = isSneaking ? SNEAKING_EYE_HEIGHT : STANDING_EYE_HEIGHT;
        float eyeLerp = Math.min(1.0f, deltaTime * 14.0f);
        currentEyeHeight += (targetEye - currentEyeHeight) * eyeLerp;

        // Đồng bộ tọa độ mắt Camera và hiệu ứng góc ống kính (Dynamic FOV)
        camera.setPosition(position.x, position.y + currentEyeHeight, position.z);
        camera.updateDynamicFov(isSprinting, isFlying, deltaTime);

        AudioManager audio = world.getAudio();
        if (audio != null) {
            if (respawned) {
                airborneTime = 0f;
                stepDistance = 0f;
                swimDistance = 0f;
            } else {
                updateMovementSounds(world, audio, support, deltaTime,
                        position.x - startX, position.y - startY, position.z - startZ);
            }
        }
    }

    /** Xử lý hồi sinh sau khi các bước sát thương của lượt cập nhật kết thúc. */
    @Override
    protected void finishLivingUpdate(World world) {
        if (isAlive()) {
            return;
        }
        resetRespawnMotion(world);
        restoreLife();
        hunger = MAX_HUNGER;
        regenElapsed = attackCooldown = 0f;
        cactusCooldown = cactusFlash = 0f;
        if (camera != null) {
            camera.setPosition(position.x, position.y + currentEyeHeight, position.z);
        }
    }

    /** Dùng lại bộ tìm vị trí an toàn và xóa đà từ nơi vừa rơi hoặc chết. */
    private void moveToRespawn(World world) {
        world.setRespawnPosition(position);
        velocity.setZero();
        resetFallTracking();
        isFlying = false;
        lastShiftTapTime = Float.NEGATIVE_INFINITY;
    }

    /** Dọn thêm trạng thái điều khiển và âm thanh khi người chơi thực sự chết. */
    private void resetRespawnMotion(World world) {
        moveToRespawn(world);
        pushX = pushZ = 0f;
        isSprinting = false;
        isSneaking = false;
        onGround = false;
        lastSpaceTapTime = Float.NEGATIVE_INFINITY;
        airborneTime = stepDistance = swimDistance = 0f;
        previousLiquid = Block.AIR;
        wasUnderwater = false;
        nearbyLiquidDelay = 0f;
    }

    /** Gây sát thương theo cửa sổ miễn đòn và duy trì thời gian cháy khi còn trong dung nham. */
    private void updateLavaContact(World world) {
        if (touchesLiquid(world, Block.LAVA)) {
            damage(world, 4f, DamageSource.LAVA, null, 0f, 0f);
            ignite(LAVA_BURN_SECONDS);
        }
    }

    /** Tích lũy thời gian hồi máu khi người chơi đang bị thương và còn đủ độ đói. */
    private void updateRegeneration(float deltaTime) {
        if (hunger < REGEN_HUNGER_THRESHOLD || getHealth() >= getMaxHealth()) {
            regenElapsed = 0f;
            return;
        }
        regenElapsed += deltaTime;
        if (regenElapsed >= REGEN_INTERVAL) {
            regenElapsed -= REGEN_INTERVAL;
            heal(1f);
            hunger -= 1f;
        }
    }

    /** Trừ độ đói theo cấp và số giây Hunger thực sự có hiệu lực. */
    @Override
    protected void onHungerEffect(int amplifier, float activeTime) {
        hunger = Math.max(0f, hunger - HUNGER_EFFECT_PER_SECOND * (amplifier + 1f) * activeTime);
    }

    /** Trả mức đói hiện tại cho HUD. */
    public float getHunger() {
        return hunger;
    }

    /** Trả sức chứa thanh đói cho HUD. */
    public float getMaxHunger() {
        return MAX_HUNGER;
    }

    /** Đánh một mục tiêu còn sống trong nhịp cho phép; hướng ngang truyền lực đẩy. */
    public void attack(World world, LivingEntity target, Vector3 direction) {
        if (!isAlive() || !target.isAlive() || target == this || attackCooldown > 0.000001f) {
            return;
        }
        attackCooldown = MELEE_COOLDOWN;
        boolean critical = velocity.y < -0.1f && !onGround && !isFlying && !isInLiquid(world);
        float amount = getMeleeDamage(1f) * (critical ? 1.5f : 1f);
        float horizontalLength = (float) Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float push = horizontalLength > 0.000001f ? 2f / horizontalLength : 0f;
        target.damage(world, amount, DamageSource.MELEE, this, direction.x * push, direction.z * push);
    }

    /** Giữ lực đẩy cận chiến ngay cả khi đầu vào ghi lại vận tốc di chuyển. */
    @Override
    protected void applyKnockback(float x, float z) {
        pushX += x;
        pushZ += z;
    }

    /** Bỏ theo dõi rơi khi đang bay hoặc ngồi trên thú. */
    @Override
    protected boolean ignoresFall() {
        return isFlying || super.ignoresFall();
    }

    /**
     * Ghi đè phương thức trừu tượng `render` của `Entity`:
     * Vì trò chơi sử dụng góc nhìn thứ nhất (First-Person Perspective), cơ thể người chơi không cần vẽ.
     */
    @Override
    public void render(ShaderProgram shader) {
        // Góc nhìn thứ nhất: không vẽ mô hình 3D của chính người chơi
    }

    /** Đọc phím, xử lý thao tác nhấn đúp và đặt vận tốc trước khi giải quyết va chạm. */
    private void handleInput(World world, Camera camera, float deltaTime) {
        boolean spaceDown = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        boolean shiftDown = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
        boolean ctrlDown = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);

        // 1. Xử lý Double-Tap Space để Bật/Tắt chế độ Bay (Fly Mode)
        if (spaceDown && !wasSpacePressed) {
            float now = playerElapsedTime;
            if (now - lastSpaceTapTime <= DOUBLE_TAP_THRESHOLD) {
                isFlying = !isFlying;
                velocity.y = 0.0f;
                lastShiftTapTime = Float.NEGATIVE_INFINITY;
                if (isFlying) {
                    onGround = false;
                    isSneaking = false;
                }
                lastSpaceTapTime = -1.0f;
            } else {
                lastSpaceTapTime = now;
            }
        }
        wasSpacePressed = spaceDown;

        // 2. Nhấn đúp Left Shift để thoát bay, hoặc giữ phím để rón rén ngoài chế độ bay.
        if (isFlying) {
            if (shiftDown && !wasShiftPressed) {
                if (playerElapsedTime - lastShiftTapTime <= DOUBLE_TAP_THRESHOLD) {
                    isFlying = false;
                    lastShiftTapTime = Float.NEGATIVE_INFINITY;
                } else {
                    lastShiftTapTime = playerElapsedTime;
                }
            }
        } else {
            isSneaking = shiftDown;
        }
        wasShiftPressed = shiftDown;

        // 3. Tính hướng di chuyển trên mặt phẳng ngang XZ
        double yawRad = Math.toRadians(camera.getYaw());
        forwardXZ.set((float) Math.cos(yawRad), 0, (float) Math.sin(yawRad)).nor();
        rightXZ.set(-forwardXZ.z, 0, forwardXZ.x).nor();

        wishDir.setZero();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            wishDir.add(forwardXZ);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            wishDir.sub(forwardXZ);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            wishDir.add(rightXZ);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            wishDir.sub(rightXZ);
        }

        boolean isMovingHorizontally = wishDir.len2() > 0.0001f;

        // 4. Tính tốc độ di chuyển (Sprint 1.5x / Sneak 0.5x / Fly)
        isSprinting = ctrlDown && isMovingHorizontally && !isSneaking;

        int terrainSpeedMultiplier = TERRAIN_SPEED_MULTIPLIERS[terrainSpeedIndex];
        float currentSpeed = MOVE_SPEED * terrainSpeedMultiplier * getMovementSpeedMultiplier();
        if (isFlying) {
            currentSpeed *= FLY_SPEED_MULTIPLIER;
            if (isSprinting) {
                currentSpeed *= 1.35f;
            }
        } else if (isSneaking) {
            currentSpeed *= SNEAK_MULTIPLIER;
        } else if (isSprinting) {
            currentSpeed *= SPRINT_MULTIPLIER;
        }

        boolean inLiquid = isInLiquid(world);
        if (inLiquid && !isFlying) {
            currentSpeed *= 0.65f;
        }

        if (isMovingHorizontally) {
            wishDir.nor().scl(currentSpeed);
        }

        float friction = surfaceFriction(world);
        if (!isFlying && !inLiquid && onGround && friction > 0.6f) {
            // Quy đổi độ giữ đà từ bước tham chiếu 60 Hz theo thời gian thực.
            float blend = 1.0f - (float) Math.pow(isMovingHorizontally ? 0.90f : friction, deltaTime * 60.0f);
            velocity.x += (wishDir.x - velocity.x) * blend;
            velocity.z += (wishDir.z - velocity.z) * blend;
        } else {
            velocity.x = wishDir.x;
            velocity.z = wishDir.z;
        }

        // 5. Nhân tốc độ bay lên và hạ độ cao với mức F1. Space vẫn ưu tiên khi giữ cả hai phím.
        if (isFlying) {
            if (spaceDown) {
                velocity.y = FLY_ASCEND_SPEED * terrainSpeedMultiplier;
            } else if (shiftDown) {
                velocity.y = -FLY_DESCEND_SPEED * terrainSpeedMultiplier;
            } else {
                velocity.y = 0.0f;
            }
        } else if (inLiquid) {
            if (spaceDown) {
                velocity.y = 4.2f;
            }
        } else {
            if (spaceDown && onGround) {
                velocity.y = JUMP_VELOCITY;
                onGround = false;
            }
        }
    }

    /** Cập nhật vật lý và trả về khối đỡ ở lần quét đứng nếu đang tiếp đất. */
    private Block applyPhysicsAndCollision(World world, float deltaTime) {
        if (!isFlying) {
            if (isInLiquid(world)) {
                velocity.y += (GRAVITY * 0.25f) * deltaTime;
                if (velocity.y < -3.5f) {
                    velocity.y = -3.5f;
                }
            } else {
                velocity.y += GRAVITY * deltaTime;
                if (velocity.y < TERMINAL_VELOCITY) {
                    velocity.y = TERMINAL_VELOCITY;
                }
            }
        }

        float dx = (velocity.x + pushX) * deltaTime;
        float dz = (velocity.z + pushZ) * deltaTime;
        float decay = Math.max(0, 1.0f - deltaTime * 12.0f);
        pushX *= decay;
        pushZ *= decay;

        // Nếu đang giữ Left Shift (Sneak) trên mặt đất, kích hoạt bảo vệ chống rơi khỏi mép block
        if (isSneaking && onGround && !isFlying) {
            dx = preventFallingOffEdgeX(world, dx);
            dz = preventFallingOffEdgeZ(world, dz);
        }

        // Di chuyển và giải quyết va chạm tách trục (X -> Z -> Y)
        if (moveHorizontal(world, 0, dx)) {
            velocity.x = 0;
        }
        if (moveHorizontal(world, 2, dz)) {
            velocity.z = 0;
        }
        onGround = false;
        Block verticalHit = moveAxis(world, 1, velocity.y * deltaTime);
        if (verticalHit != null) {
            onGround = velocity.y < 0;
            velocity.y = 0;
            if (verticalHit == Block.CACTUS) {
                // Chốt quãng rơi tại mặt gai trước khi phản lực làm chân rời đất.
                if (onGround && !ignoresFall()) {
                    updateFall(world);
                }
                touchCactus(world, 0, 0, onGround);
            }
        }

        if (onGround && isFlying) {
            isFlying = false;
            lastShiftTapTime = Float.NEGATIVE_INFINITY;
        }
        return onGround ? verticalHit : null;
    }

    /** Chọn âm thanh từ bề mặt va chạm, quãng đường đã đi và thay đổi khi vào nước. */
    private void updateMovementSounds(World world, AudioManager audio, Block support, float deltaTime,
                                      float dx, float dy, float dz) {
        int x = (int) Math.floor(position.x);
        int z = (int) Math.floor(position.z);
        int footY = (int) Math.floor(position.y + 0.1f);
        Block liquid = liquidAtBody(world, x, footY, z);
        if (liquid != previousLiquid) {
            if (liquid == Block.WATER) {
                audio.playEffect(AudioManager.Effect.WATER_ENTER, 0.46f);
            } else if (liquid == Block.LAVA) {
                audio.playEffect(AudioManager.Effect.LAVA_POP, 0.44f);
            }
            previousLiquid = liquid;
            swimDistance = 0f;
        }

        float eyeY = position.y + currentEyeHeight;
        int eyeBlockY = (int) Math.floor(eyeY);
        boolean underwater = world.getBlock(x, eyeBlockY, z) == Block.WATER.getId()
                && eyeY - eyeBlockY < Block.WATER.getTopHeight(world, x, eyeBlockY, z);
        if (underwater != wasUnderwater) {
            audio.playEffect(underwater ? AudioManager.Effect.DIVE : AudioManager.Effect.SURFACE, 0.33f);
            wasUnderwater = underwater;
        }
        audio.setUnderwater(underwater);

        if (liquid == Block.WATER && !isFlying) {
            swimDistance += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (swimDistance >= 1.2f) {
                audio.playEffect(AudioManager.Effect.SWIM, 0.30f);
                // Bỏ các nhịp đã đi qua trong cùng khung hình khi dùng hệ số tốc độ cao.
                swimDistance %= 1.2f;
            }
        }
        if (isFlying || liquid.isLiquid()) {
            airborneTime = 0f;
            stepDistance = 0f;
        } else if (support == null) {
            airborneTime += deltaTime;
            stepDistance = 0f;
        } else {
            // Tuyết một lớp đi xuyên được nhưng vẫn phát tiếng tuyết khi phủ trên nền đỡ.
            Block surface = world.getBlock(x, footY, z) == Block.SNOW_LAYER.getId()
                    ? Block.SNOW_LAYER : support;
            if (airborneTime >= 0.15f) {
                audio.playLanding(surface, 0.82f);
                stepDistance = 0f;
            } else {
                stepDistance += (float) Math.sqrt(dx * dx + dz * dz);
                if (stepDistance >= 1.6f) {
                    audio.playStep(surface, isSneaking);
                    // Giữ phần lẻ để tránh phát dồn tiếng bước chân khi đã dừng lại.
                    stepDistance %= 1.6f;
                }
            }
            airborneTime = 0f;
        }
        if (!underwater) {
            updateNearbyLiquidSound(world, audio, x, footY, z, deltaTime);
        }
    }

    /** Tìm mặt chất lỏng gần nhất theo chu kỳ thưa, giới hạn trong bốn block. */
    private void updateNearbyLiquidSound(World world, AudioManager audio, int x, int y, int z, float deltaTime) {
        nearbyLiquidDelay -= deltaTime;
        if (nearbyLiquidDelay > 0f) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        nearbyLiquidDelay = 2.5f + random.nextFloat() * 2.5f;
        int nearest = 17;
        Block closest = Block.AIR;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance >= nearest) {
                        continue;
                    }
                    Block block = Block.fromId(world.getBlock(x + dx, y + dy, z + dz));
                    if (block.isLiquid() && world.getBlock(x + dx, y + dy + 1, z + dz) == Block.AIR.getId()) {
                        closest = block;
                        nearest = distance;
                    }
                }
            }
        }
        if (closest.isLiquid()) {
            AudioManager.Effect effect = closest == Block.WATER ? AudioManager.Effect.WATER_NEARBY
                    : random.nextBoolean() ? AudioManager.Effect.LAVA_NEARBY : AudioManager.Effect.LAVA_POP;
            audio.playEffect(effect, 0.24f / (1f + 0.2f * nearest));
        } else {
            nearbyLiquidDelay = 0.6f;
        }
    }

    /** Thử bước lên mép tuyết thấp nếu phía trên có đủ chỗ. */
    private boolean moveHorizontal(World world, int axis, float distance) {
        float origin = axis == 0 ? position.x : position.z;
        Block hit = moveAxis(world, axis, distance);
        float rise = obstacleTop - position.y;
        if (hit == Block.SNOW_LAYER && onGround && !isFlying && rise > 0 && rise <= MAX_SNOW_STEP) {
            float previousY = position.y;
            float stopped = axis == 0 ? position.x : position.z;
            setHorizontal(axis, origin);
            position.y += rise;
            if (!overlapsTerrain(world)) {
                Block raisedHit = moveAxis(world, axis, distance);
                float raised = axis == 0 ? position.x : position.z;
                if (Math.abs(raised - origin) > Math.abs(stopped - origin)) {
                    return raisedHit != null;
                }
            }
            position.y = previousY;
            setHorizontal(axis, stopped);
        }
        if (hit == Block.CACTUS) {
            float push = distance > 0 ? -CACTUS_PUSH : CACTUS_PUSH;
            touchCactus(world, axis == 0 ? push : 0, axis == 2 ? push : 0, false);
        }
        return hit != null;
    }

    /** Đặt tọa độ theo trục đang thử bước lên tuyết. */
    private void setHorizontal(int axis, float value) {
        if (axis == 0) {
            position.x = value;
        } else {
            position.z = value;
        }
    }

    /** Đẩy nhẹ theo mặt tiếp xúc và giới hạn tần suất phản hồi. */
    private void touchCactus(World world, float x, float z, boolean above) {
        if (cactusCooldown > 0) {
            return;
        }
        cactusCooldown = CACTUS_COOLDOWN;
        cactusFlash = CACTUS_FLASH_DURATION;
        AudioManager audio = world.getAudio();
        if (audio != null) {
            audio.playEffect(AudioManager.Effect.CACTUS_HURT, 0.45f);
        }
        pushX = x;
        pushZ = z;
        damage(world, 1f, DamageSource.CACTUS, null, 0f, 0f);
        if (above && !isFlying) {
            velocity.y = CACTUS_PUSH;
            onGround = false;
        }
    }

    /** Alpha tương đối của viền đỏ chạm gai. */
    public float getCactusFlash() {
        return cactusFlash / CACTUS_FLASH_DURATION;
    }

    /** Đọc vật liệu dưới tâm chân nếu chân đang ở cạnh mặt nâng đỡ. */
    private float surfaceFriction(World world) {
        int x = (int) Math.floor(position.x);
        int y = (int) Math.floor(position.y - 0.01f);
        int z = (int) Math.floor(position.z);
        Block.Bounds bounds = world.getCollisionBounds(x, y, z);
        return bounds != null && Math.abs(position.y - (y + bounds.maxY())) <= 0.02f
                ? Block.fromId(world.getBlock(x, y, z)).getFriction() : 0.6f;
    }

    private float preventFallingOffEdgeX(World world, float dx) {
        float step = 0.02f;
        while (dx != 0.0f && !hasGroundBeneath(world, position.x + dx, position.y, position.z)) {
            if (Math.abs(dx) <= step) {
                dx = 0.0f;
            } else {
                dx += (dx > 0) ? -step : step;
            }
        }
        if (dx == 0.0f) {
            velocity.x = 0.0f;
        }
        return dx;
    }

    private float preventFallingOffEdgeZ(World world, float dz) {
        float step = 0.02f;
        while (dz != 0.0f && !hasGroundBeneath(world, position.x, position.y, position.z + dz)) {
            if (Math.abs(dz) <= step) {
                dz = 0.0f;
            } else {
                dz += (dz > 0) ? -step : step;
            }
        }
        if (dz == 0.0f) {
            velocity.z = 0.0f;
        }
        return dz;
    }

    private boolean hasGroundBeneath(World world, float testX, float testY, float testZ) {
        float checkHalfWidth = halfWidth - 0.05f;
        int minX = (int) Math.floor(testX - checkHalfWidth);
        int maxX = (int) Math.floor(testX + checkHalfWidth);
        int checkY = (int) Math.floor(testY - 0.05f);
        int minZ = (int) Math.floor(testZ - checkHalfWidth);
        int maxZ = (int) Math.floor(testZ + checkHalfWidth);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block.Bounds bounds = world.getCollisionBounds(x, checkY, z);
                if (bounds != null && bounds.intersects(x, checkY, z,
                        testX - checkHalfWidth, testY - 0.05f, testZ - checkHalfWidth,
                        testX + checkHalfWidth, testY + CONTACT_EPSILON, testZ + checkHalfWidth)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isInLiquid(World world) {
        int bx = (int) Math.floor(position.x);
        int byFeet = (int) Math.floor(position.y + 0.1f);
        int bz = (int) Math.floor(position.z);

        return liquidAtBody(world, bx, byFeet, bz).isLiquid();
    }

    /** Chia sẻ cách nhận diện chất lỏng tại chân và thắt lưng với vật lý. */
    private Block liquidAtBody(World world, int x, int footY, int z) {
        Block feet = Block.fromId(world.getBlock(x, footY, z));
        int waistY = (int) Math.floor(position.y + 0.9f);
        return feet.isLiquid() ? feet : Block.fromId(world.getBlock(x, waistY, z));
    }

    public Hotbar getHotbar() {
        return hotbar;
    }

    public boolean isFlying() {
        return isFlying;
    }

    public boolean isSprinting() {
        return isSprinting;
    }

    public boolean isSneaking() {
        return isSneaking;
    }
}
