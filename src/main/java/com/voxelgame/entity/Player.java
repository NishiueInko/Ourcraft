package com.voxelgame.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.Camera;
import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.World;

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
public class Player extends Entity {

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

    // Quan hệ Hợp thành (Composition - Player "has-a" Hotbar)
    private final Hotbar hotbar = new Hotbar();

    // Tham chiếu góc nhìn Camera
    private Camera camera;

    // Các cờ trạng thái chuyển động của nhân vật
    private boolean isFlying = false;
    private boolean isSprinting = false;
    private boolean isSneaking = false;

    // Biến phục vụ nhận diện thao tác bấm đúp phím Space (Double-tap Space)
    private boolean wasSpacePressed = false;
    private float lastSpaceTapTime = -1.0f;
    // Chỉ tính cạnh nhấn Left Shift trong cùng một lần bay, kể cả khi giữ phím qua nhiều khung hình.
    private boolean wasShiftPressed;
    private float lastShiftTapTime = Float.NEGATIVE_INFINITY;
    private float playerElapsedTime = 0.0f;

    public Player(Vector3 startPosition) {
        super(startPosition.x, startPosition.y, startPosition.z, PLAYER_HALF_WIDTH, PLAYER_HEIGHT);
    }

    public Player(float startX, float startY, float startZ) {
        super(startX, startY, startZ, PLAYER_HALF_WIDTH, PLAYER_HEIGHT);
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

    /**
     * Phương thức nạp chồng tiện ích cho phép truyền camera khi cập nhật.
     */
    public void update(World world, Camera camera, float deltaTime) {
        attachControls(camera);
        update(world, deltaTime);
    }

    /**
     * Ghi đè phương thức trừu tượng `update` của `Entity`:
     * Thực thi toàn bộ điều khiển bàn phím, trạng thái Bay/Chạy/Rón rén, vật lý va chạm AABB
     * và đồng bộ vị trí mắt Camera.
     */
    @Override
    public void update(World world, float deltaTime) {
        if (camera == null || world == null) {
            return;
        }

        playerElapsedTime += deltaTime;

        handleInput(world, camera);
        applyPhysicsAndCollision(world, deltaTime);

        // Nếu rơi xuống dưới đáy bản đồ, đưa người chơi trở lại vị trí an toàn trên mặt đất
        if (position.y < -25.0f) {
            position.set(World.CENTER_X, 22.0f, World.CENTER_Z);
            velocity.set(0.0f, 0.0f, 0.0f);
            isFlying = false;
            lastShiftTapTime = Float.NEGATIVE_INFINITY;
        }

        // Nội suy mượt mà chiều cao tầm mắt Camera khi chuyển đổi giữa Đứng thẳng (1.62f) và Sneak (1.45f)
        float targetEye = isSneaking ? SNEAKING_EYE_HEIGHT : STANDING_EYE_HEIGHT;
        float eyeLerp = Math.min(1.0f, deltaTime * 14.0f);
        currentEyeHeight += (targetEye - currentEyeHeight) * eyeLerp;

        // Đồng bộ tọa độ mắt Camera và hiệu ứng góc ống kính (Dynamic FOV)
        camera.setPosition(position.x, position.y + currentEyeHeight, position.z);
        camera.updateDynamicFov(isSprinting, isFlying, deltaTime);
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
    private void handleInput(World world, Camera camera) {
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
        Vector3 forwardXZ = new Vector3((float) Math.cos(yawRad), 0.0f, (float) Math.sin(yawRad)).nor();
        Vector3 rightXZ = new Vector3(-forwardXZ.z, 0.0f, forwardXZ.x).nor();

        Vector3 wishDir = new Vector3(0.0f, 0.0f, 0.0f);
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

        float currentSpeed = MOVE_SPEED;
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

        velocity.x = wishDir.x;
        velocity.z = wishDir.z;

        // 5. Khi bay, Space ưu tiên bay lên. Left Shift hạ dần, thả cả hai phím thì giữ độ cao.
        if (isFlying) {
            if (spaceDown) {
                velocity.y = FLY_ASCEND_SPEED;
            } else if (shiftDown) {
                velocity.y = -FLY_DESCEND_SPEED;
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

    private void applyPhysicsAndCollision(World world, float deltaTime) {
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

        float dx = velocity.x * deltaTime;
        float dz = velocity.z * deltaTime;

        // Nếu đang giữ Left Shift (Sneak) trên mặt đất, kích hoạt bảo vệ chống rơi khỏi mép block
        if (isSneaking && onGround && !isFlying) {
            dx = preventFallingOffEdgeX(world, dx);
            dz = preventFallingOffEdgeZ(world, dz);
        }

        // Di chuyển và giải quyết va chạm tách trục (X -> Z -> Y)
        position.x += dx;
        resolveCollisionX(world);

        position.z += dz;
        resolveCollisionZ(world);

        onGround = false;
        position.y += velocity.y * deltaTime;
        resolveCollisionY(world);

        if (onGround && isFlying) {
            isFlying = false;
            lastShiftTapTime = Float.NEGATIVE_INFINITY;
        }
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
                if (world.isSolidBlock(x, checkY, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isInLiquid(World world) {
        int bx = (int) Math.floor(position.x);
        int byFeet = (int) Math.floor(position.y + 0.1f);
        int byWaist = (int) Math.floor(position.y + 0.9f);
        int bz = (int) Math.floor(position.z);

        Block feetBlock = Block.fromId(world.getBlock(bx, byFeet, bz));
        Block waistBlock = Block.fromId(world.getBlock(bx, byWaist, bz));
        return feetBlock.isLiquid() || waistBlock.isLiquid();
    }

    private void resolveCollisionX(World world) {
        int minX = (int) Math.floor(position.x - halfWidth);
        int maxX = (int) Math.floor(position.x + halfWidth);
        int minY = (int) Math.floor(position.y);
        int maxY = (int) Math.floor(position.y + height - 0.0001f);
        int minZ = (int) Math.floor(position.z - halfWidth);
        int maxZ = (int) Math.floor(position.z + halfWidth);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.isSolidBlock(x, y, z)) {
                        if (velocity.x > 0) {
                            position.x = x - halfWidth - 0.0001f;
                            velocity.x = 0;
                        } else if (velocity.x < 0) {
                            position.x = x + 1.0f + halfWidth + 0.0001f;
                            velocity.x = 0;
                        }
                        return;
                    }
                }
            }
        }
    }

    private void resolveCollisionZ(World world) {
        int minX = (int) Math.floor(position.x - halfWidth);
        int maxX = (int) Math.floor(position.x + halfWidth);
        int minY = (int) Math.floor(position.y);
        int maxY = (int) Math.floor(position.y + height - 0.0001f);
        int minZ = (int) Math.floor(position.z - halfWidth);
        int maxZ = (int) Math.floor(position.z + halfWidth);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.isSolidBlock(x, y, z)) {
                        if (velocity.z > 0) {
                            position.z = z - halfWidth - 0.0001f;
                            velocity.z = 0;
                        } else if (velocity.z < 0) {
                            position.z = z + 1.0f + halfWidth + 0.0001f;
                            velocity.z = 0;
                        }
                        return;
                    }
                }
            }
        }
    }

    private void resolveCollisionY(World world) {
        int minX = (int) Math.floor(position.x - halfWidth);
        int maxX = (int) Math.floor(position.x + halfWidth);
        int minY = (int) Math.floor(position.y);
        int maxY = (int) Math.floor(position.y + height - 0.0001f);
        int minZ = (int) Math.floor(position.z - halfWidth);
        int maxZ = (int) Math.floor(position.z + halfWidth);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.isSolidBlock(x, y, z)) {
                        if (velocity.y < 0) {
                            position.y = y + 1.0f;
                            velocity.y = 0;
                            onGround = true;
                        } else if (velocity.y > 0) {
                            position.y = y - height - 0.0001f;
                            velocity.y = 0;
                        }
                        return;
                    }
                }
            }
        }
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
