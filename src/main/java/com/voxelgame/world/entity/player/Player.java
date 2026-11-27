package com.voxelgame.world.entity.player;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.world.effect.StatusEffectType;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.mob.animal.Wolf;
import com.voxelgame.world.entity.mob.MountableMob;
import com.voxelgame.world.gamemode.GameMode;
import com.voxelgame.world.gamemode.SurvivalGameMode;
import com.voxelgame.world.item.FoodRegistry;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
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
    // === HOOK: TRACK A - FIELDS ===
    /** Thời gian hồi chiêu chuẩn Minecraft (giây) sau khi thổi sừng dê Goat Horn. */
    public static final float GOAT_HORN_COOLDOWN = 7.0f;
    /** Mức bão hòa (saturation) khởi đầu chuẩn Minecraft và thông số thuốc Witch. */
    private static final float INITIAL_SATURATION = 5.0f;
    private static final float POTION_HEALING_AMOUNT = 4.0f;
    private static final float POTION_EFFECT_DURATION = 180.0f;
    /** Độ bão hòa hiện tại, luôn bị chặn không vượt quá {@code hunger}. */
    private float saturation = INITIAL_SATURATION;
    /** Hệ số giảm tốc khi kéo cung, ăn hoặc uống (0.4x tốc độ đi bộ). */
    private static final float ITEM_USE_SPEED_MULTIPLIER = 0.4f;
    private boolean itemUseSlowdown;

    /** Chế độ góc nhìn camera người chơi chuyển đổi qua phím F5. */
    public enum CameraMode {
        /** Góc nhìn thứ nhất mặc định. */
        FIRST_PERSON,
        /** Góc nhìn thứ ba phía sau lưng người chơi. */
        THIRD_BACK,
        /** Góc nhìn thứ ba phía trước mặt nhìn ngược lại người chơi. */
        THIRD_FRONT
    }

    /** Khoảng cách lùi/tiến tối đa ở góc nhìn thứ 3 và khoảng đệm tránh xuyên vào mặt tường rắn. */
    private static final float THIRD_PERSON_DISTANCE = 4.0f;
    private static final float THIRD_PERSON_CLIP_PADDING = 0.15f;
    /** Thời lượng một chu kỳ vung tay cận chiến/đào block (giây). */
    private static final float SWING_DURATION = 0.25f;
    /** Biên độ dao động View Bobbing ngang và dọc trên camera góc nhìn thứ nhất. */
    private static final float BOB_CAMERA_SWAY_X = 0.04f;
    private static final float BOB_CAMERA_BOUNCE_Y = 0.04f;

    private CameraMode cameraMode = CameraMode.FIRST_PERSON;
    private float bobPhase;
    private float bobStrength;
    private float limbSwing;
    private float limbSwingAmount;
    private boolean swinging;
    private float swingElapsed;

    /** Bộ đệm tia và vector tái sử dụng (zero-GC) cho camera góc nhìn thứ 3 và View Bobbing. */
    private final Vector3 tmpCamOrigin = new Vector3();
    private final Vector3 tmpCamDir = new Vector3();
    private final Vector3 tmpCamRight = new Vector3();
    private final Vector3 tmpCamUp = new Vector3();
    private final Raycast.HitResult tmpCamHit = Raycast.HitResult.miss();
    // === END HOOK: TRACK A - FIELDS ===

    // === HOOK: TRACK C - FIELDS ===
    // Khai báo PlayerInventory 36 ô, oxy (tối đa 300 ticks / 10 bóng) và sprint-swim của Track C tại đây.
    /** Mức Oxy tối đa khi lặn dưới nước (300 tick = 15 giây ở nhịp 20 tick/giây). */
    public static final int AIR_MAX_TICKS = 300;
    /** Tốc độ tiêu hao Oxy mỗi giây khi mắt ngập nước (20 tick/giây). */
    private static final float AIR_DRAIN_PER_SECOND = 20.0f;
    /** Tốc độ hồi Oxy mỗi giây khi mắt lên khỏi mặt nước (hồi đầy 300 tick trong 2 giây). */
    private static final float AIR_RECOVER_PER_SECOND = 150.0f;
    /** Chu kỳ trừ HP khi đã cạn sạch Oxy (1.0 giây/lần). */
    private static final float DROWN_DAMAGE_INTERVAL = 1.0f;
    /** Sát thương ngạt nước mỗi chu kỳ (2.0 HP/giây). */
    private static final float DROWN_DAMAGE_AMOUNT = 2.0f;
    /** Hệ số tăng tốc bơi khi giữ Left Ctrl trong nước (Sprint-Swimming). */
    private static final float SWIM_SPRINT_MULTIPLIER = 1.55f;
    /** Sai số dấu phẩy động nhỏ khi so sánh mốc tích lũy Oxy và ngạt nước. */
    private static final float OXYGEN_EPSILON = 0.0001f;

    /** Túi đồ 36 ô đồng bộ 9 ô đầu với Hotbar. */
    private final PlayerInventory inventory = new PlayerInventory(hotbar);
    /** Bộ đệm vector dùng lại khi ném vật phẩm (Q / Ctrl+Q) để tránh cấp phát rác GC. */
    private final Vector3 dropOriginBuffer = new Vector3();
    private final Vector3 dropDirectionBuffer = new Vector3();
    private final Vector3 dropVelocityBuffer = new Vector3();
    /** Lượng Oxy hiện tại (tính theo float để tích lũy chính xác theo deltaTime). */
    private float airTicksExact = AIR_MAX_TICKS;
    /** Bộ đếm thời gian tích lũy để gây sát thương ngạt nước mỗi 1.0 giây. */
    private float drownTimer = 0.0f;
    /** Cờ lưu trạng thái mắt đang ngập trong nước từ lượt cập nhật gần nhất cho HUD. */
    private boolean eyeInWater = false;
    /** Cờ trạng thái người chơi đang Sprint-Swimming (giữ Left Ctrl + di chuyển trong nước). */
    private boolean isSwimSprinting = false;
    // === END HOOK: TRACK C - FIELDS ===

    // Tham chiếu góc nhìn Camera
    private Camera camera;

    // Các cờ trạng thái chuyển động của nhân vật
    private boolean isFlying = false;
    private boolean isSprinting = false;
    private boolean isSneaking = false;

    /** Chế độ chơi hiện tại (mặc định Survival ở constructor để bảo toàn các bộ test đơn vị). */
    private GameMode gameMode = SurvivalGameMode.INSTANCE;

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

    /** Trả về chế độ chơi hiện tại của người chơi ({@code Creative} hoặc {@code Survival}). */
    public GameMode getGameMode() {
        return gameMode;
    }

    /**
     * Đặt chế độ chơi mới và kích hoạt hook đa hình {@link GameMode#onEnter(Player)}
     * để đồng bộ trạng thái bay, tốc độ và chỉ số sinh tồn.
     */
    public void setGameMode(GameMode mode) {
        if (mode == null) {
            return;
        }
        this.gameMode = mode;
        mode.onEnter(this);
    }

    /** Chuyển đổi qua lại giữa Creative và Survival theo hợp đồng đa hình {@link GameMode#toggle()}. */
    public GameMode toggleGameMode() {
        GameMode next = gameMode.toggle();
        setGameMode(next);
        return next;
    }

    /** Đặt trạng thái bay trực tiếp (chỉ bật được khi chế độ hiện tại cho phép {@link GameMode#canFly()}). */
    public void setFlying(boolean flying) {
        if (flying && !gameMode.canFly()) {
            return;
        }
        this.isFlying = flying;
        this.lastSpaceTapTime = Float.NEGATIVE_INFINITY;
        this.lastShiftTapTime = Float.NEGATIVE_INFINITY;
        if (flying) {
            this.onGround = false;
            this.isSneaking = false;
            this.velocity.y = 0.0f;
        } else {
            resetFallTracking();
        }
    }

    /** Đưa hệ số tốc độ kiểm tra địa hình ({@code F1}) về mức cơ sở {@code 1x}. */
    public void resetTerrainSpeed() {
        terrainSpeedIndex = 0;
    }

    /** Trả về hệ số tốc độ địa hình hiện tại ({@code 1, 3, 5, 10, 25, 50}). */
    public int getTerrainSpeedMultiplier() {
        return TERRAIN_SPEED_MULTIPLIERS[terrainSpeedIndex];
    }

    /** Hồi đầy máu, đói, bão hòa, Oxy và dập lửa khi chuyển sang chế độ Creative. */
    public void restoreCreativeVitals() {
        restoreLife();
        hunger = MAX_HUNGER;
        saturation = INITIAL_SATURATION;
        airTicksExact = AIR_MAX_TICKS;
        drownTimer = 0.0f;
        regenElapsed = 0.0f;
        cactusCooldown = 0.0f;
        cactusFlash = 0.0f;
        extinguish();
    }

    /** Chuyển sang mức tốc độ kế tiếp và quay về 1x sau 50x (chỉ hiệu lực khi chế độ cho phép). */
    public void cycleTerrainSpeed() {
        if (!gameMode.canUseTerrainTools()) {
            return;
        }
        terrainSpeedIndex = (terrainSpeedIndex + 1) % TERRAIN_SPEED_MULTIPLIERS.length;
    }

    /** Đặt chân lên nền hợp lệ gần tâm biome và đồng bộ trạng thái chuyển động với vị trí mới. */
    public boolean teleportToBiome(World world, BiomeType biome) {
        if (!gameMode.canUseTerrainTools() || world == null || !world.setBiomeCenterPosition(position, biome)) {
            return false;
        }
        leaveMount();

        // Xóa đà, lực đẩy, chuỗi nhấn đúp và quãng đường âm thanh ở vị trí cũ.
        velocity.setZero();
        resetFallTracking();
        pushX = pushZ = 0f;
        isFlying = false;
        onGround = true;
        lastSpaceTapTime = Float.NEGATIVE_INFINITY;
        lastShiftTapTime = Float.NEGATIVE_INFINITY;
        airborneTime = stepDistance = swimDistance = 0f;
        resetViewmodelMotion();
        previousLiquid = Block.AIR;
        wasUnderwater = false;
        nearbyLiquidDelay = 0f;
        isSwimSprinting = false;
        eyeInWater = isEyeInWater(world);
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
        // === HOOK: TRACK C - LIVING UPDATE ===
        // Cập nhật bộ đếm Oxy dưới nước, ngạt thở và sprint-swim của Track C tại đây.
        updateOxygen(world, deltaTime);
        if (!isAlive()) {
            return;
        }
        // === END HOOK: TRACK C - LIVING UPDATE ===

        // === HOOK: TRACK A - LIVING UPDATE ===
        // Giữ bất biến saturation <= hunger <= 20; giữ nguyên logic trừ hunger của regen và HungerEffect cũ.
        saturation = Math.min(saturation, hunger);
        if (swinging) {
            swingElapsed += deltaTime;
            if (swingElapsed >= SWING_DURATION) {
                swinging = false;
                swingElapsed = 0f;
            }
        }
        // === END HOOK: TRACK A - LIVING UPDATE ===
        if (getMount() instanceof MountableMob vehicle) {
            updateRiding(world, vehicle, deltaTime);
            return;
        }
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
        boolean respawned = fellBelowWorld();
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

        // === HOOK: TRACK A - BOBBING UPDATE ===
        if (respawned) {
            resetViewmodelMotion();
        } else {
            updateViewBobbingAndLimbSwing(deltaTime, position.x - startX, position.z - startZ);
        }
        // === END HOOK: TRACK A - BOBBING UPDATE ===

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

    /** Gửi thao tác lái trước khi World cập nhật thú, đồng thời tắt các chế độ đi bộ và bay. */
    private void updateRiding(World world, MountableMob vehicle, float dt) {
        syncMountedPosition();
        if (fellBelowWorld()) {
            resetRespawnMotion(world);
            if (camera != null) {
                camera.setPosition(position.x, position.y + currentEyeHeight, position.z);
            }
            return;
        }
        isFlying = false;
        isSneaking = false;
        isSprinting = false;
        isSwimSprinting = false;
        pushX = pushZ = 0f;
        lastSpaceTapTime = lastShiftTapTime = Float.NEGATIVE_INFINITY;
        if (camera != null && Gdx.input != null) {
            boolean shiftDown = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
            boolean spaceDown = Gdx.input.isKeyPressed(Input.Keys.SPACE);
            boolean tryLeave = shiftDown && !wasShiftPressed;
            wasShiftPressed = shiftDown;
            wasSpacePressed = spaceDown;
            if (tryLeave && vehicle.tryDismount(world, this)) {
                camera.setPosition(position.x, position.y + currentEyeHeight, position.z);
                return;
            }
            readMovementDirection(camera);
            wishDir.nor();
            boolean sprint = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && !wishDir.isZero();
            vehicle.updateRiderInput(this, wishDir.x, wishDir.z, sprint, spaceDown, dt);
            camera.updateDynamicFov(sprint, false, dt);
        }
        currentEyeHeight += (STANDING_EYE_HEIGHT - currentEyeHeight) * Math.min(1f, dt * 14f);
        // === HOOK: TRACK A - RIDING BOBBING ===
        bobStrength += (0f - bobStrength) * Math.min(1f, dt * 10f);
        limbSwingAmount += (0f - limbSwingAmount) * Math.min(1f, dt * 10f);
        // === END HOOK: TRACK A - RIDING BOBBING ===
        updateLavaContact(world);
    }

    /** Cùng ngưỡng hồi sinh khi đi bộ và khi thú cưỡi rơi ra ngoài thế giới. */
    private boolean fellBelowWorld() {
        return position.y < -25f;
    }

    /** Gắn chân và mắt vào ghế sau lượt chuyển động của thú để camera theo kịp ngay trong khung hình. */
    public void syncMountedPosition() {
        LivingEntity vehicle = getMount();
        if (vehicle == null) {
            return;
        }
        vehicle.copySeatPosition(getMountedSeat(), position);
        velocity.setZero();
        onGround = false;
        if (camera != null) {
            camera.setPosition(position.x, position.y + currentEyeHeight, position.z);
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
        airTicksExact = AIR_MAX_TICKS;
        drownTimer = 0f;
        isSwimSprinting = false;
        eyeInWater = isEyeInWater(world);
        if (camera != null) {
            camera.setPosition(position.x, position.y + currentEyeHeight, position.z);
        }
    }

    /** Dùng lại bộ tìm vị trí an toàn và xóa đà từ nơi vừa rơi hoặc chết. */
    private void moveToRespawn(World world) {
        leaveMount();
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
        itemUseSlowdown = false;
        onGround = false;
        lastSpaceTapTime = Float.NEGATIVE_INFINITY;
        airborneTime = stepDistance = swimDistance = 0f;
        resetViewmodelMotion();
        previousLiquid = Block.AIR;
        wasUnderwater = false;
        nearbyLiquidDelay = 0f;
    }

    /** Gây sát thương theo cửa sổ miễn đòn và duy trì thời gian cháy khi còn trong dung nham. */
    private void updateLavaContact(World world) {
        if (gameMode.isInvulnerable()) {
            return;
        }
        if (touchesLiquid(world, Block.LAVA)) {
            damage(world, 4f, DamageSource.LAVA, null, 0f, 0f);
            ignite(LAVA_BURN_SECONDS);
        }
    }

    /** Tích lũy thời gian hồi máu khi người chơi đang bị thương và còn đủ độ đói. */
    private void updateRegeneration(float deltaTime) {
        if (gameMode.isInvulnerable()) {
            regenElapsed = 0f;
            return;
        }
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
        if (gameMode.isInvulnerable()) {
            return;
        }
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
        attack(world, target, direction, 1f, MELEE_COOLDOWN);
    }

    /** Giữ lực đẩy cận chiến ngay cả khi đầu vào ghi lại vận tốc di chuyển và bật nảy khi đứng trên mặt đất. */
    @Override
    protected void applyKnockback(float x, float z) {
        pushX += x;
        pushZ += z;
        // HOOK: TRACK A - Bật nảy chuẩn Minecraft khi bị thực thể khác hoặc đạn đánh trúng trên mặt đất
        if (onGround && !isFlying && (x * x + z * z) > 0.0001f) {
            velocity.y = Math.max(velocity.y, 4.5f);
            onGround = false;
        }
    }

    /** Bỏ theo dõi rơi khi bất tử (Creative), đang bay hoặc ngồi trên thú. */
    @Override
    protected boolean ignoresFall() {
        return gameMode.isInvulnerable() || isFlying || super.ignoresFall();
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

        // 1. Xử lý Double-Tap Space để Bật/Tắt chế độ Bay (Fly Mode) chỉ khi chế độ chơi cho phép
        if (!gameMode.canFly()) {
            if (isFlying) {
                isFlying = false;
            }
            lastSpaceTapTime = Float.NEGATIVE_INFINITY;
        } else if (spaceDown && !wasSpacePressed) {
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

        // 3. Đọc hướng di chuyển ngang theo góc camera.
        readMovementDirection(camera);
        boolean isMovingHorizontally = wishDir.len2() > 0.0001f;

        // 4. Tính tốc độ di chuyển (Sprint 1.5x / Sneak 0.5x / Fly / Sprint-Swimming 1.55x / ItemUse 0.4x)
        Block bodyLiquid = liquidAtBody(world);
        boolean inLiquid = bodyLiquid.isLiquid();
        isSprinting = ctrlDown && isMovingHorizontally && !isSneaking && !itemUseSlowdown;
        isSwimSprinting = !isFlying && !isSneaking && ctrlDown && isMovingHorizontally
                && bodyLiquid == Block.WATER && !itemUseSlowdown;

        int terrainSpeedMultiplier = TERRAIN_SPEED_MULTIPLIERS[terrainSpeedIndex];
        float currentSpeed = MOVE_SPEED * terrainSpeedMultiplier * getMovementSpeedMultiplier();
        if (isFlying) {
            currentSpeed *= FLY_SPEED_MULTIPLIER;
            if (isSprinting) {
                currentSpeed *= 1.35f;
            }
        } else if (isSneaking) {
            currentSpeed *= SNEAK_MULTIPLIER;
        } else if (isSwimSprinting) {
            // Bơi nhanh trong Block.WATER nhân 1.55x trên vận tốc bơi cơ bản (0.65x)
            currentSpeed *= SWIM_SPRINT_MULTIPLIER;
        } else if (isSprinting) {
            currentSpeed *= SPRINT_MULTIPLIER;
        }
        if (itemUseSlowdown && !isFlying) {
            currentSpeed *= ITEM_USE_SPEED_MULTIPLIER;
        }

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
            } else if (isSwimSprinting) {
                // Giữ W khi đang Sprint-Swimming sẽ điều hướng lặn/nổi dọc theo góc pitch của camera
                boolean movingForward = Gdx.input.isKeyPressed(Input.Keys.W)
                        && !Gdx.input.isKeyPressed(Input.Keys.S);
                if (movingForward) {
                    float pitchRad = (float) Math.toRadians(camera.getPitch());
                    float cosPitch = (float) Math.cos(pitchRad);
                    float sinPitch = (float) Math.sin(pitchRad);
                    velocity.x *= cosPitch;
                    velocity.z *= cosPitch;
                    float swimY = sinPitch * currentSpeed;
                    if (swimY > 0f && !isEyeInWater(world)) {
                        swimY = 0f;
                    }
                    velocity.y = swimY;
                } else {
                    velocity.y = 0.0f;
                }
            }
        } else {
            if (spaceDown && onGround) {
                velocity.y = JUMP_VELOCITY;
                onGround = false;
            }
        }
    }

    /** Dùng chung phép đổi WASD sang trục thế giới cho đi bộ và lái thú. */
    private void readMovementDirection(Camera camera) {
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
    }

    /** Cập nhật vật lý và trả về khối đỡ ở lần quét đứng nếu đang tiếp đất. */
    private Block applyPhysicsAndCollision(World world, float deltaTime) {
        if (!isFlying) {
            if (isInLiquid(world)) {
                if (!isSwimSprinting) {
                    velocity.y += (GRAVITY * 0.25f) * deltaTime;
                    if (velocity.y < -3.5f) {
                        velocity.y = -3.5f;
                    }
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
        if (gameMode.isInvulnerable() || cactusCooldown > 0) {
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
        return liquidAtBody(world).isLiquid();
    }

    /** Chia sẻ cách nhận diện chất lỏng tại chân và thắt lưng theo vị trí hiện tại của người chơi. */
    private Block liquidAtBody(World world) {
        int bx = (int) Math.floor(position.x);
        int byFeet = (int) Math.floor(position.y + 0.1f);
        int bz = (int) Math.floor(position.z);
        return liquidAtBody(world, bx, byFeet, bz);
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
    // === HOOK: TRACK A - METHODS ===
    /**
     * Trả mức bão hòa hiện tại (luôn bảo đảm không vượt quá {@code hunger}).
     */
    public float getSaturation() {
        return Math.min(saturation, hunger);
    }

    /**
     * Ăn một vật phẩm thuộc {@link FoodRegistry} nếu người chơi còn sống và chưa đầy đói ({@code hunger < 20}).
     *
     * @param item vật phẩm thức ăn cần tiêu thụ
     * @return {@code true} nếu ăn thành công, {@code false} nếu không phải thức ăn hoặc đã no
     */
    public boolean consumeFood(Item item) {
        return consumeFood(item, ThreadLocalRandom.current().nextFloat());
    }

    /**
     * Phiên bản xác định trước giá trị tung xác suất hiệu ứng phụ để phục vụ kiểm thử đơn vị.
     *
     * @param item       vật phẩm thức ăn cần tiêu thụ
     * @param effectRoll giá trị ngẫu nhiên trong {@code [0, 1)}
     * @return {@code true} nếu ăn thành công
     */
    public boolean consumeFood(Item item, float effectRoll) {
        FoodRegistry.FoodSpec spec = FoodRegistry.of(item);
        if (spec == null || !isAlive() || hunger >= MAX_HUNGER) {
            return false;
        }
        float clampedSaturation = Math.min(saturation, hunger);
        hunger = Math.min(MAX_HUNGER, hunger + spec.nutrition());
        saturation = Math.min(hunger, clampedSaturation + spec.saturation());
        if (spec.effect() != null && spec.durationSeconds() > 0f && effectRoll < spec.effectChance()) {
            applyEffect(spec.effect(), spec.amplifier(), spec.durationSeconds());
        }
        return true;
    }

    /**
     * Uống một trong 4 lọ thuốc Witch ({@code POTION_HEALING}, {@code POTION_FIRE_RESISTANCE},
     * {@code POTION_WATER_BREATHING}, {@code POTION_SWIFTNESS}), áp dụng hiệu ứng tương ứng và trả {@link Item#GLASS_BOTTLE}.
     *
     * @param potionItem lọ thuốc cần uống
     * @return {@code true} nếu là lọ thuốc hợp lệ và uống thành công
     */
    public boolean consumePotion(Item potionItem) {
        if (!isAlive() || potionItem == null) {
            return false;
        }
        if (potionItem == Item.POTION_HEALING) {
            heal(POTION_HEALING_AMOUNT);
        } else if (potionItem == Item.POTION_FIRE_RESISTANCE) {
            applyEffect(StatusEffectType.FIRE_RESISTANCE, 0, POTION_EFFECT_DURATION);
        } else if (potionItem == Item.POTION_WATER_BREATHING) {
            applyEffect(StatusEffectType.WATER_BREATHING, 0, POTION_EFFECT_DURATION);
        } else if (potionItem == Item.POTION_SWIFTNESS) {
            applyEffect(StatusEffectType.SWIFTNESS, 0, POTION_EFFECT_DURATION);
        } else {
            return false;
        }

        if (!gameMode.consumesItems()) {
            return true;
        }
        // Nếu lọ thuốc đang nằm ở ô Hotbar được chọn thì tiêu thụ và thay bằng lọ thủy tinh rỗng ngay tại ô đó
        if (hotbar.getSelectedItem() == potionItem && hotbar.getSelectedCount() > 0) {
            int selectedSlot = hotbar.getSelectedSlot();
            hotbar.consumeSelectedItemStack();
            if (hotbar.getSlotCount(selectedSlot) == 0) {
                hotbar.setSlot(selectedSlot, Item.GLASS_BOTTLE, 1);
            } else {
                hotbar.addItem(Item.GLASS_BOTTLE, 1);
            }
        } else {
            hotbar.addItem(Item.GLASS_BOTTLE, 1);
        }
        return true;
    }

    /** Kiểm tra người chơi đang có hiệu ứng kháng lửa ({@link StatusEffectType#FIRE_RESISTANCE}). */
    public boolean hasFireResistance() {
        return hasEffect(StatusEffectType.FIRE_RESISTANCE);
    }

    /**
     * Trả về hệ số tốc độ di chuyển ngang theo hiệu ứng hiện có (gồm {@link StatusEffectType#SWIFTNESS} +20%/cấp
     * và {@link StatusEffectType#SLOWNESS} -15%/cấp).
     */
    public float getSpeedMultiplier() {
        return getMovementSpeedMultiplier();
    }

    /**
     * Bật hoặc tắt trạng thái giảm tốc 0.4x khi đang kéo cung, ăn thức ăn hoặc uống thuốc.
     *
     * @param slowdown {@code true} khi đang thực hiện thao tác giữ chuột phải cần giảm tốc
     */
    public void setItemUseSlowdown(boolean slowdown) {
        this.itemUseSlowdown = slowdown;
    }

    /** Trả {@code true} nếu người chơi đang bị giảm tốc do kéo cung, ăn hoặc uống. */
    public boolean isItemUseSlowdown() {
        return itemUseSlowdown;
    }

    /**
     * Tấn công cận chiến với sát thương cơ sở và thời gian hồi chiêu tùy theo vũ khí (tay không 1 HP / 0.6s,
     * giáo sắt {@code IRON_SPEAR} Jab 5 HP / 0.65s), giữ nguyên nhân 1.5x khi đánh chí mạng lúc rơi và gọi sói thuần hóa.
     *
     * @param world           thế giới hiện tại
     * @param target          mục tiêu bị đánh
     * @param direction       hướng ngắm để truyền đẩy lùi ngang
     * @param baseDamage      sát thương cơ sở trước Weakness và Critical Hit
     * @param cooldownSeconds thời gian hồi chiêu sau đòn đánh
     * @return {@code true} nếu đòn đánh thực sự gây sát thương lên mục tiêu
     */
    public boolean attack(World world, LivingEntity target, Vector3 direction,
                          float baseDamage, float cooldownSeconds) {
        if (!isAlive() || target == null || !target.isAlive() || target == this
                || attackCooldown > 0.000001f) {
            return false;
        }
        attackCooldown = cooldownSeconds;
        boolean critical = velocity.y < -0.1f && !onGround && !isFlying && !isInLiquid(world);
        float amount = getMeleeDamage(baseDamage) * (critical ? 1.5f : 1f);
        float horizontalLength = (float) Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float push = horizontalLength > 0.000001f ? 2f / horizontalLength : 0f;
        boolean applied = target.damage(world, amount, DamageSource.MELEE, this,
                direction.x * push, direction.z * push);
        if (applied) {
            notifyTamedWolves(world, target);
        }
        return applied;
    }

    /**
     * Khi người chơi nhận sát thương từ một thực thể khác, báo cho đàn sói đã thuần hóa để đánh trả.
     * Bỏ qua hoàn toàn nếu chế độ chơi hiện tại miễn nhiễm sát thương ({@link GameMode#isInvulnerable()}).
     */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        if (gameMode.isInvulnerable()) {
            return false;
        }
        boolean applied = super.damage(world, amount, source, attacker, knockbackX, knockbackZ);
        if (applied && isAlive() && attacker instanceof LivingEntity threat && threat != this) {
            notifyTamedWolves(world, threat);
        }
        return applied;
    }

    /**
     * Báo hiệu cho toàn bộ sói đã thuần hóa thuộc sở hữu của người chơi này tấn công mục tiêu đe dọa.
     *
     * @param world  thế giới chứa các thực thể
     * @param threat mục tiêu vừa bị chủ đánh hoặc vừa gây sát thương cho chủ
     */
    public void notifyTamedWolves(World world, LivingEntity threat) {
        if (world == null || threat == null || !threat.isAlive() || threat == this) {
            return;
        }
        for (int i = 0, count = world.entityCount(); i < count; i++) {
            if (world.entityAt(i) instanceof Wolf wolf && wolf.isTamed() && wolf.getOwner() == this) {
                wolf.onOwnerAttackOrHurt(threat);
            }
        }
    }

    /** Trả về chế độ góc nhìn camera hiện tại ({@code FIRST_PERSON}, {@code THIRD_BACK}, {@code THIRD_FRONT}). */
    public CameraMode getCameraMode() {
        return cameraMode;
    }

    /**
     * Xoay vòng chế độ góc nhìn theo thứ tự {@code FIRST_PERSON -> THIRD_BACK -> THIRD_FRONT -> FIRST_PERSON}
     * khi người chơi nhấn phím {@code F5}.
     *
     * @return chế độ góc nhìn mới sau khi chuyển
     */
    public CameraMode cycleCameraMode() {
        cameraMode = switch (cameraMode) {
            case FIRST_PERSON -> CameraMode.THIRD_BACK;
            case THIRD_BACK -> CameraMode.THIRD_FRONT;
            case THIRD_FRONT -> CameraMode.FIRST_PERSON;
        };
        return cameraMode;
    }

    /** Kích hoạt chu kỳ hoạt ảnh vung tay góc nhìn thứ nhất và thứ ba khi đánh hoặc bắt đầu đào. */
    public void triggerSwing() {
        swinging = true;
        swingElapsed = 0.0f;
    }

    /** Tiếp tục lặp chu kỳ vung tay mượt mà khi giữ chuột trái đào khối liên tục. */
    public void continueSwing() {
        if (!swinging) {
            triggerSwing();
        }
    }

    /** Trả về {@code true} nếu cánh tay đang trong chu kỳ vung. */
    public boolean isSwinging() {
        return swinging;
    }

    /** Trả về tiến trình vung tay hiện tại trong đoạn {@code [0.0f, 1.0f]}. */
    public float getSwingProgress() {
        return swinging ? Math.min(1.0f, swingElapsed / SWING_DURATION) : 0.0f;
    }

    /** Trả về pha dao động View Bobbing tích lũy theo quãng đường đi trên mặt đất. */
    public float getBobPhase() {
        return bobPhase;
    }

    /** Trả về cường độ View Bobbing hiện tại trong đoạn {@code [0.0f, 1.25f]}. */
    public float getBobStrength() {
        return bobStrength;
    }

    /** Trả về pha đánh tay chân dùng cho mô hình nhân vật ở góc nhìn thứ 3. */
    public float getLimbSwing() {
        return limbSwing;
    }

    /** Trả về biên độ đánh tay chân hiện tại ({@code 0.0f..1.0f}) cho góc nhìn thứ 3. */
    public float getLimbSwingAmount() {
        return limbSwingAmount;
    }

    /** Trả về chiều cao tầm mắt hiện tại đã nội suy (1.62f đứng, 1.45f khi rón rén). */
    public float getCurrentEyeHeight() {
        return currentEyeHeight;
    }

    /** Trả về tham chiếu {@link Camera} gắn với người chơi (có thể {@code null} trong kiểm thử). */
    public Camera getAttachedCamera() {
        return camera;
    }

    /** Trả vận tốc đẩy lùi ngang hiện tại theo trục X (block/giây). */
    public float getPushX() {
        return pushX;
    }

    /** Trả vận tốc đẩy lùi ngang hiện tại theo trục Z (block/giây). */
    public float getPushZ() {
        return pushZ;
    }

    /** Cập nhật pha View Bobbing (chỉ khi {@code onGround && !isFlying}) và pha bước chân góc nhìn 3. */
    private void updateViewBobbingAndLimbSwing(float dt, float movedX, float movedZ) {
        float horizDist = (float) Math.sqrt(movedX * movedX + movedZ * movedZ);
        float horizSpeed = dt > 0.0001f ? horizDist / dt : 0.0f;
        float targetStrength;
        if (onGround && !isFlying && horizDist > 0.0001f) {
            bobPhase += horizDist * 2.4f;
            targetStrength = Math.min(1.25f, horizSpeed / MOVE_SPEED);
        } else {
            targetStrength = 0.0f;
        }
        float lerp = Math.min(1.0f, dt * 10.0f);
        bobStrength += (targetStrength - bobStrength) * lerp;

        if (horizDist > 0.0001f) {
            limbSwing += horizDist * 2.4f;
        }
        float targetLimbAmount = Math.min(1.0f, horizSpeed / MOVE_SPEED);
        limbSwingAmount += (targetLimbAmount - limbSwingAmount) * lerp;
    }

    /** Đặt lại toàn bộ trạng thái dao động tay chân khi chết hoặc dịch chuyển sang biome mới. */
    private void resetViewmodelMotion() {
        bobPhase = 0.0f;
        bobStrength = 0.0f;
        limbSwing = 0.0f;
        limbSwingAmount = 0.0f;
        swinging = false;
        swingElapsed = 0.0f;
    }

    /**
     * Tính tọa độ đặt camera ở góc nhìn thứ 3 dựa trên {@link Camera} gắn sẵn của người chơi,
     * có bắn tia {@link Raycast#castOccluder} để không xuyên vào khối rắn.
     */
    public Vector3 computeThirdPersonCameraPos(World world, Vector3 out) {
        return computeThirdPersonCameraPos(world, this.camera, out);
    }

    /**
     * Tính tọa độ đặt camera ở góc nhìn thứ 3 dựa trên hướng nhìn của {@code cam},
     * có bắn tia {@link Raycast#castOccluder} để rút ngắn khoảng cách trước tường rắn.
     */
    public Vector3 computeThirdPersonCameraPos(World world, Camera cam, Vector3 out) {
        if (cam != null) {
            cam.copyFront(tmpCamDir);
        } else {
            tmpCamDir.set(0.0f, 0.0f, -1.0f);
        }
        return computeThirdPersonCameraPos(
                world,
                position.x,
                position.y + currentEyeHeight,
                position.z,
                tmpCamDir.x,
                tmpCamDir.y,
                tmpCamDir.z,
                cameraMode,
                THIRD_PERSON_DISTANCE,
                out
        );
    }

    /**
     * Tính tọa độ camera góc nhìn thứ 3 từ tâm mắt và hướng nhìn chỉ định,
     * rút ngắn khoảng cách khi có khối va chạm rắn nằm trên đường nối.
     *
     * @param world       thế giới voxel để kiểm tra vật cản (có thể {@code null})
     * @param eyeX        tọa độ X tâm mắt người chơi
     * @param eyeY        tọa độ Y tâm mắt người chơi
     * @param eyeZ        tọa độ Z tâm mắt người chơi
     * @param dirX        thành phần X hướng nhìn phía trước
     * @param dirY        thành phần Y hướng nhìn phía trước
     * @param dirZ        thành phần Z hướng nhìn phía trước
     * @param mode        chế độ camera mục tiêu
     * @param maxDistance khoảng cách tối đa (mặc định 4.0 block)
     * @param out         vector nhận kết quả (tái sử dụng zero-GC)
     * @return {@code out} chứa vị trí đặt camera an toàn
     */
    public Vector3 computeThirdPersonCameraPos(World world,
                                               float eyeX, float eyeY, float eyeZ,
                                               float dirX, float dirY, float dirZ,
                                               CameraMode mode, float maxDistance, Vector3 out) {
        out.set(eyeX, eyeY, eyeZ);
        if (mode == null || mode == CameraMode.FIRST_PERSON || !(maxDistance > 0.0f)) {
            return out;
        }
        float sign = mode == CameraMode.THIRD_BACK ? -1.0f : 1.0f;
        tmpCamDir.set(dirX * sign, dirY * sign, dirZ * sign);
        float lenSq = tmpCamDir.len2();
        if (lenSq < 1e-8f || !Float.isFinite(lenSq)) {
            return out;
        }
        tmpCamDir.scl(1.0f / (float) Math.sqrt(lenSq));

        float distance = maxDistance;
        if (world != null) {
            tmpCamOrigin.set(eyeX, eyeY, eyeZ);
            Raycast.castOccluder(world, tmpCamOrigin, tmpCamDir, maxDistance + THIRD_PERSON_CLIP_PADDING, tmpCamHit);
            if (tmpCamHit.isHit()) {
                distance = Math.max(0.0f, Math.min(maxDistance, tmpCamHit.getDistance() - THIRD_PERSON_CLIP_PADDING));
            }
        }
        out.set(
                eyeX + tmpCamDir.x * distance,
                eyeY + tmpCamDir.y * distance,
                eyeZ + tmpCamDir.z * distance
        );
        return out;
    }

    /**
     * Áp dụng tạm thời vị trí camera cho lượt kết xuất (View Bobbing ở góc nhìn 1 hoặc vị trí lùi/tiến ở góc nhìn 3)
     * mà không làm thay đổi {@link Entity#position} hay kết quả Raycast sau khi khôi phục cuối lượt vẽ.
     *
     * @param world        thế giới để tránh xuyên tường ở góc nhìn thứ 3
     * @param cam          camera cần tạm đặt vị trí kết xuất
     * @param outRenderPos bộ đệm nhận tọa độ kết xuất đã áp dụng
     */
    public void applyRenderCameraPosition(World world, Camera cam, Vector3 outRenderPos) {
        if (cam == null) {
            outRenderPos.set(position.x, position.y + currentEyeHeight, position.z);
            return;
        }
        if (cameraMode == CameraMode.FIRST_PERSON) {
            outRenderPos.set(position.x, position.y + currentEyeHeight, position.z);
            if (bobStrength > 0.0001f) {
                cam.copyFront(tmpCamDir);
                tmpCamRight.set(tmpCamDir).crs(Vector3.Y);
                if (tmpCamRight.len2() > 1e-6f) {
                    tmpCamRight.nor();
                    tmpCamUp.set(tmpCamRight).crs(tmpCamDir).nor();
                } else {
                    tmpCamRight.set(1.0f, 0.0f, 0.0f);
                    tmpCamUp.set(0.0f, 1.0f, 0.0f);
                }
                float swayX = (float) Math.sin(bobPhase) * BOB_CAMERA_SWAY_X * bobStrength;
                float bounceY = -(float) Math.abs(Math.cos(bobPhase)) * BOB_CAMERA_BOUNCE_Y * bobStrength;
                outRenderPos.add(
                        tmpCamRight.x * swayX + tmpCamUp.x * bounceY,
                        tmpCamRight.y * swayX + tmpCamUp.y * bounceY,
                        tmpCamRight.z * swayX + tmpCamUp.z * bounceY
                );
            }
            cam.setPosition(outRenderPos.x, outRenderPos.y, outRenderPos.z);
            return;
        }
        computeThirdPersonCameraPos(world, cam, outRenderPos);
        cam.setPosition(outRenderPos.x, outRenderPos.y, outRenderPos.z);
    }

    /**
     * Tính ma trận nhìn cho lượt kết xuất hiện tại: dùng {@code lookAt} ngược từ vị trí trước mặt
     * về tầm mắt người chơi khi ở {@link CameraMode#THIRD_FRONT} mà không làm trôi {@code yaw}/{@code pitch}.
     *
     * @param cam       camera hiện tại (đã đặt tại vị trí kết xuất)
     * @param eyeTarget tọa độ tầm mắt người chơi để camera nhìn về ở {@code THIRD_FRONT}
     * @param outView   ma trận đích tái sử dụng (zero-GC)
     * @return {@code outView} đã cập nhật
     */
    public Matrix4 computeRenderViewMatrix(Camera cam, Vector3 eyeTarget, Matrix4 outView) {
        if (cam == null) {
            return outView.idt();
        }
        if (cameraMode == CameraMode.THIRD_FRONT) {
            cam.copyPosition(tmpCamOrigin);
            return outView.setToLookAt(tmpCamOrigin, eyeTarget, Vector3.Y);
        }
        return outView.set(cam.getViewMatrix());
    }
    // === END HOOK: TRACK A - METHODS ===

    // === HOOK: TRACK C - METHODS ===
    // Getter/setter PlayerInventory 36 ô, Oxygen và Hunger của Track C tại đây.

    /**
     * Trả về túi đồ đầy đủ 36 ô (0..8 Hotbar, 9..35 balo) thuộc sở hữu của người chơi.
     *
     * @return đối tượng {@link PlayerInventory}
     */
    public PlayerInventory getInventory() {
        return inventory;
    }

    /**
     * Vứt 1 đơn vị (Q) hoặc toàn bộ stack (Ctrl+Q) ở ô Hotbar đang chọn ra phía trước mặt người chơi.
     * Vật phẩm rơi có {@code pickupDelay = 1.0f} giây để không bị nam châm hút ngược lại ngay lập tức.
     *
     * @param world thế giới để thả thực thể {@link ItemEntity}
     * @param wholeStack true nếu vứt cả stack, false nếu chỉ vứt 1 đơn vị
     * @return thực thể {@link ItemEntity} vừa sinh ra, hoặc {@code null} nếu ô trống hoặc thiếu world
     */
    public ItemEntity dropSelectedItem(World world, boolean wholeStack) {
        if (world == null) {
            return null;
        }
        int selectedSlot = hotbar.getSelectedSlot();
        Item selectedItem = hotbar.getSelectedItem();
        int selectedCount = hotbar.getSelectedCount();
        if (selectedItem == null || selectedItem.isNone() || selectedCount <= 0) {
            return null;
        }

        int dropAmount = wholeStack ? selectedCount : 1;
        int consumed = inventory.consumeFromSlot(selectedSlot, dropAmount);
        if (consumed <= 0) {
            return null;
        }

        if (!copyAim(dropOriginBuffer, dropDirectionBuffer)) {
            dropOriginBuffer.set(position.x, position.y + currentEyeHeight, position.z);
            dropDirectionBuffer.set(0f, 0f, -1f);
        }

        // Sinh vật phẩm ở phía trước tầm mắt để bay ra ngoài tự nhiên
        float spawnX = dropOriginBuffer.x + dropDirectionBuffer.x * 0.45f;
        float spawnY = dropOriginBuffer.y - 0.25f + dropDirectionBuffer.y * 0.25f;
        float spawnZ = dropOriginBuffer.z + dropDirectionBuffer.z * 0.45f;

        dropVelocityBuffer.set(
                dropDirectionBuffer.x * 5.2f,
                dropDirectionBuffer.y * 5.2f + 1.8f,
                dropDirectionBuffer.z * 5.2f
        );

        ItemEntity dropped = new ItemEntity(spawnX, spawnY, spawnZ, dropVelocityBuffer,
                selectedItem, consumed, 1.0f);
        world.spawnDroppedItem(dropped);
        return dropped;
    }

    /**
     * Cập nhật thanh Oxy dưới nước (giảm 20 tick/giây khi mắt ngập nước và không có Water Breathing),
     * gây 2.0 HP sát thương ngạt mỗi 1.0 giây khi cạn Oxy, hoặc hồi nhanh về 300 khi lên khỏi nước.
     */
    private void updateOxygen(World world, float deltaTime) {
        eyeInWater = isEyeInWater(world);
        if (gameMode.isInvulnerable()) {
            airTicksExact = AIR_MAX_TICKS;
            drownTimer = 0.0f;
            return;
        }
        if (!eyeInWater) {
            airTicksExact = Math.min(AIR_MAX_TICKS, airTicksExact + AIR_RECOVER_PER_SECOND * deltaTime);
            drownTimer = 0.0f;
            return;
        }
        if (hasEffect(StatusEffectType.WATER_BREATHING)) {
            drownTimer = 0.0f;
            return;
        }

        float remainingTime = deltaTime;
        if (airTicksExact > OXYGEN_EPSILON) {
            float timeToEmpty = airTicksExact / AIR_DRAIN_PER_SECOND;
            if (remainingTime <= timeToEmpty + OXYGEN_EPSILON) {
                airTicksExact = Math.max(0.0f, airTicksExact - AIR_DRAIN_PER_SECOND * remainingTime);
                if (airTicksExact <= OXYGEN_EPSILON) {
                    airTicksExact = 0.0f;
                }
                return;
            }
            remainingTime -= timeToEmpty;
            airTicksExact = 0.0f;
        } else {
            airTicksExact = 0.0f;
        }

        drownTimer += remainingTime;
        while (isAlive() && drownTimer + OXYGEN_EPSILON >= DROWN_DAMAGE_INTERVAL) {
            drownTimer = Math.max(0.0f, drownTimer - DROWN_DAMAGE_INTERVAL);
            damage(world, DROWN_DAMAGE_AMOUNT, DamageSource.DROWNING, null, 0.0f, 0.0f);
        }
    }

    /**
     * Trả về lượng Oxy hiện tại (0..{@link #AIR_MAX_TICKS}).
     *
     * @return số tick Oxy còn lại
     */
    public int getAirTicks() {
        return Math.round(airTicksExact);
    }

    /**
     * Kiểm tra tầm mắt của người chơi có đang ngập trong {@link Block#WATER} hay không.
     *
     * @param world thế giới chứa dữ liệu chất lỏng
     * @return {@code true} nếu cao độ mắt đang nằm dưới mặt thoáng khối nước
     */
    public boolean isEyeInWater(World world) {
        if (world == null) {
            return false;
        }
        return world.liquidAt(position.x, position.y + currentEyeHeight, position.z) == Block.WATER;
    }

    /**
     * Trả về cờ mắt đang ngập nước đã lưu từ lượt cập nhật sinh tồn gần nhất (dùng cho HUD).
     *
     * @return {@code true} nếu mắt người chơi đang ở dưới nước
     */
    public boolean isEyeInWater() {
        return eyeInWater;
    }

    /**
     * Cho biết người chơi có đang ở trạng thái Sprint-Swimming (giữ Left Ctrl + di chuyển trong nước).
     *
     * @return {@code true} nếu đang bơi nhanh trong nước
     */
    public boolean isSwimSprinting() {
        return isSwimSprinting;
    }
    // === END HOOK: TRACK C - METHODS ===
}
