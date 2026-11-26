package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Nền tảng ghế, điều khiển cưỡi, thuần hóa và xuống thú an toàn. */
public abstract class MountableMob extends MobEntity {
    /** Ngưỡng thiện cảm tối đa (100) và điểm cộng mỗi lần bị hất xuống (+5) chuẩn Minecraft. */
    public static final int MAX_TEMPER = 100;
    public static final int TEMPER_PER_ATTEMPT = 5;
    private static final float CHARGE_SECONDS = 0.9f;
    /** Gia tốc trọng lực chung của mob và dung sai mặt tiếp xúc. */
    private static final float GRAVITY_MAGNITUDE = 26f;
    private static final float SURFACE_EPSILON = 0.001f;
    /** Giới hạn tìm mặt đứng khi đặt người cưỡi xuống. */
    private static final float DISMOUNT_RISE = 1f;
    private static final float DISMOUNT_DROP = 2f;
    /** Ngưỡng lội của thú thấp và hệ số tốc độ khi chở khách qua nước. */
    private static final float MIN_WADING_DEPTH = 2f;
    private static final float RIDDEN_WATER_SPEED = 0.4f;

    private final float jumpHeight;
    /** Vectơ tạm dùng khi kiểm tra giao nhau với các thực thể cạnh ghế. */
    private final Vector3 dismountProbe = new Vector3();
    private boolean tamed;
    private int temper;
    private float tamingTimeLeft;
    private Player primaryRider;
    /** Hướng điều khiển và trạng thái cạnh phím được đọc trước lượt vật lý. */
    private float inputX;
    private float inputZ;
    private boolean sprinting;
    private boolean spaceDown;
    private boolean charging;
    private float charge;
    private float releasedCharge;

    /** Khởi tạo thú cưỡi từ chỉ số loài, giữ bộ goal mặc định. */
    protected MountableMob(MobType type, float x, float y, float z, int seats, float stepHeight,
                           float moveSpeed, float jumpHeight, boolean initiallyTamed) {
        this(type, x, y, z, seats, stepHeight, moveSpeed, jumpHeight, initiallyTamed, true);
    }

    /** Cho biến thể chuyên biệt tự đăng ký goal phù hợp. */
    protected MountableMob(MobType type, float x, float y, float z, int seats, float stepHeight,
                           float moveSpeed, float jumpHeight, boolean initiallyTamed, boolean installDefaultGoals) {
        this(type, x, y, z, seats, stepHeight, moveSpeed, jumpHeight, initiallyTamed,
                installDefaultGoals, type.health());
    }

    /** Cho ngựa và lừa khởi tạo máu riêng cho mỗi cá thể. */
    protected MountableMob(MobType type, float x, float y, float z, int seats, float stepHeight,
                           float moveSpeed, float jumpHeight, boolean initiallyTamed, boolean installDefaultGoals,
                           float maxHealth) {
        super(type, x, y, z, 0f, seats, installDefaultGoals, 1f, maxHealth);
        this.jumpHeight = jumpHeight;
        setMoveSpeed(moveSpeed);
        setStepHeight(stepHeight);
        setDirectStep(true);
        if (initiallyTamed) {
            tame();
        }
    }

    /** Chuột phải xử lý đốt bằng Flint and Steel trước, sau đó chọn ghế từ trước ra sau. */
    @Override
    public boolean onPlayerInteract(World world, Player player) {
        if (super.onPlayerInteract(world, player)) {
            return true;
        }
        if (!canPlayerMount(world, player)) {
            return false;
        }
        for (int seat = 0; seat < getSeatCount(); seat++) {
            if (getPassenger(seat) == null && seatClear(world, seat, player, x(), y(), z(), 0f)
                    && player.tryMount(this, seat)) {
                if (seat == 0) {
                    startPrimaryRide(player);
                }
                onPlayerMounted(world, player, seat);
                return true;
            }
        }
        return false;
    }

    /** Chỉ cá thể trưởng thành còn sống mới nhận người đang đi bộ. */
    protected boolean canPlayerMount(World world, Player player) {
        return isAlive() && !isBaby() && player.isAlive() && player.getMount() == null;
    }

    /** Điểm mở rộng cho loài cần đổi tư thế hoặc trạng thái sau khi nhận người. */
    protected void onPlayerMounted(World world, Player player, int seat) {
    }

    /** Đặt mốc làm quen và xóa toàn bộ lệnh từ người cưỡi trước. */
    private void startPrimaryRide(Player player) {
        clearRiderInput();
        primaryRider = player;
        tamingTimeLeft = ThreadLocalRandom.current().nextFloat(2.2f, 3.5f);
    }

    /** Ghi lệnh ngang đã xoay theo camera và nhận cạnh thả của phím nạp lực. */
    public final void updateRiderInput(Player player, float worldX, float worldZ, boolean sprint,
                                       boolean heldSpace, float dt) {
        if (getPassenger(0) != player || !isAlive()) {
            return;
        }
        if (primaryRider != player) {
            startPrimaryRide(player);
        }
        float lengthSquared = worldX * worldX + worldZ * worldZ;
        float divisor = lengthSquared > 1f ? (float) Math.sqrt(lengthSquared) : 1f;
        inputX = worldX / divisor;
        inputZ = worldZ / divisor;
        sprinting = sprint;
        if (heldSpace) {
            if (!spaceDown) {
                charging = tamed && canChargeAbility();
            }
            if (charging && canChargeAbility()) {
                charge = Math.min(1f, charge + Math.max(0f, dt) / CHARGE_SECONDS);
            } else {
                charging = false;
                charge = 0f;
            }
        } else if (spaceDown) {
            releasedCharge = charging ? charge : 0f;
            charging = false;
            charge = 0f;
        }
        spaceDown = heldSpace;
    }

    /** Lượt có người điều khiển thay goal di chuyển, nhưng vẫn đi qua quét vật lý chung. */
    @Override
    protected final void updateGoals(World world, float dt) {
        dismountInDeepWater(world);
        if (!(getPassenger(0) instanceof Player driver)) {
            if (primaryRider != null) {
                clearRiderInput();
            }
            updateUnridden(world, dt);
            return;
        }
        if (primaryRider != driver) {
            startPrimaryRide(driver);
        }
        goals().stopAll(world, this);
        if (!tamed) {
            updateTaming(world, driver, dt);
            return;
        }
        if (!canRiderDrive(world)) {
            releasedCharge = 0f;
            stopMoving();
            return;
        }
        float speed = riddenSpeed(sprinting);
        steer(inputX, inputZ, speed);
        if (inputX != 0f || inputZ != 0f) {
            faceBody(x() + inputX * 2f, z() + inputZ * 2f);
        }
        if (releasedCharge > 0f && canChargeAbility()) {
            onChargedAction(world, releasedCharge, inputX, inputZ);
        }
        releasedCharge = 0f;
    }

    /** Kiểm tra mọi ghế, kể cả khi lạc đà chỉ có người ngồi phía sau. */
    private boolean hasRider() {
        for (int seat = 0; seat < getSeatCount(); seat++) {
            if (getPassenger(seat) != null) {
                return true;
            }
        }
        return false;
    }

    /** Thú có hành khách lội theo trọng lực. Thú đi một mình mới tự nổi. */
    @Override
    public final boolean canFloat() {
        return super.canFloat() && !hasRider();
    }

    /** Giảm mạnh tốc độ khi thú đang lội và chở người. */
    @Override
    protected final float waterSpeedMultiplier() {
        return hasRider() ? RIDDEN_WATER_SPEED : super.waterSpeedMultiplier();
    }

    /** Cho phép người lái đưa thú vào nước sâu dù đáy nằm ngoài tầm tìm nền đứng. */
    final boolean canWadeInto(World world, float x, float z) {
        return getPassenger(0) instanceof Player
                && world.liquidAt(x, y() - 0.25f, z) == Block.WATER
                && navigator().avoidsHazards(world, x, y(), z);
    }

    /** Gỡ mọi ghế tại vị trí hiện tại khi nước vượt ngưỡng lội, cho người chơi tự bơi. */
    private void dismountInDeepWater(World world) {
        float wadingDepth = Math.max(MIN_WADING_DEPTH, type().height());
        if (!inWater() || !hasRider() || world.liquidAt(x(), y() + wadingDepth, z()) != Block.WATER) {
            return;
        }
        for (int seat = 0; seat < getSeatCount(); seat++) {
            LivingEntity rider = getPassenger(seat);
            if (rider instanceof Player player) {
                player.syncMountedPosition();
            } else if (rider != null) {
                copySeatPosition(seat, dismountProbe);
                rider.setPosition(dismountProbe.x, dismountProbe.y, dismountProbe.z);
            }
            if (rider != null) {
                rider.leaveMount();
            }
        }
        clearRiderInput();
        mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
    }

    /** Khi ghế trước không có người chơi, thú dùng goal của loài. */
    protected void updateUnridden(World world, float dt) {
        super.updateGoals(world, dt);
    }

    /** Kiểm tra thú có đủ khoảng trống hoặc đang ở tư thế sẵn sàng điều khiển. */
    protected boolean canRiderDrive(World world) {
        return true;
    }

    /** Ngựa và lừa dùng chỉ số tốc độ của cá thể cho cả hai chế độ nhập. */
    protected float riddenSpeed(boolean sprint) {
        return moveSpeed();
    }

    /** Kỹ năng nạp lực bắt đầu ở mặt đất khô. */
    protected boolean canChargeAbility() {
        return isOnGround() && !inWater();
    }

    /** Đổi chiều cao mong muốn thành vận tốc ban đầu cho lượt nhảy. */
    protected void onChargedAction(World world, float strength, float directionX, float directionZ) {
        float height = jumpHeight * MathUtils.lerp(0.25f, 1f, strength);
        float vy = (float) Math.sqrt(2f * GRAVITY_MAGNITUDE * height);
        queueLeap(0f, vy, 0f, 0f);
    }

    /** Giữ yên hướng điều khiển khi thú hoang đang làm quen rồi hất xuống nếu có chỗ đứng. */
    private void updateTaming(World world, Player driver, float dt) {
        stopMoving();
        if (inWater()) {
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
            return;
        }
        tamingTimeLeft = Math.max(0f, tamingTimeLeft - dt);
        if (tamingTimeLeft > 0f) {
            float rear = MathUtils.clamp((0.4f - tamingTimeLeft) / 0.4f, 0f, 1f);
            mobVisualState().setPose(rear > 0f ? MobVisualState.Pose.REAR : MobVisualState.Pose.IDLE, rear);
            return;
        }
        if (ThreadLocalRandom.current().nextInt(MAX_TEMPER) < temper) {
            tame();
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        } else if (tryDismount(world, driver)) {
            temper = Math.min(MAX_TEMPER, temper + TEMPER_PER_ATTEMPT);
        } else {
            tamingTimeLeft = 0.25f;
        }
    }

    /** Ghi nhận thuần hóa và bảo vệ cá thể khỏi việc tự loại khỏi thế giới. */
    protected final void tame() {
        tamed = true;
        temper = MAX_TEMPER;
        setPersistent(true);
    }

    /** Trục trước tại yaw bằng 0 hướng về Z âm, cùng quy ước với model. */
    @Override
    public Vector3 copySeatPosition(int seat, Vector3 out) {
        float yaw = mobVisualState().bodyYaw();
        float forward = seatForwardOffset(seat);
        return out.set(x() - MathUtils.sinDeg(yaw) * forward, y() + seatHeight(seat),
                z() - MathUtils.cosDeg(yaw) * forward);
    }

    /** Ghế ngựa hoặc lừa nằm gần giữa lưng, biến thể hai ghế ghi đè tọa độ này. */
    protected float seatForwardOffset(int seat) {
        return 0f;
    }

    /** Trả độ cao tương đối của điểm đặt chân kỵ sĩ. */
    protected float seatHeight(int seat) {
        return getHeight() * 0.86f;
    }

    /** Chừa khoảng ngang đủ cho ghế ở mọi góc quay, kể cả khi quay thân tại chỗ. */
    private float seatClearanceRadius(int seat, LivingEntity rider) {
        return Math.abs(seatForwardOffset(seat)) + rider.getHalfWidth();
    }

    /** Quét đồng thời thân thú và người ngồi khi đi ngang hoặc đi lên. */
    @Override
    protected final void sweepAttachedTerrainBoxes(World world, int axis, float distance) {
        if (axis == 1 && distance < 0f) {
            return;
        }
        for (int seat = 0; seat < getSeatCount(); seat++) {
            LivingEntity rider = getPassenger(seat);
            if (rider != null) {
                sweepTerrainBox(world, axis, distance, x(), y() + seatHeight(seat), z(),
                        seatClearanceRadius(seat, rider), rider.getHeight());
            }
        }
    }

    /** Kiểm tra cùng khoảng trống khi bước lên bậc hoặc đổi chiều cao thân. */
    final boolean seatsClear(World world, float x, float y, float z, float lift) {
        for (int seat = 0; seat < getSeatCount(); seat++) {
            LivingEntity rider = getPassenger(seat);
            if (rider != null && !seatClear(world, seat, rider, x, y, z, lift)) {
                return false;
            }
        }
        return true;
    }

    /** Xét hộp kỵ sĩ trong toàn bộ quãng nâng vai, dùng hình va chạm thật của ô. */
    private boolean seatClear(World world, int seat, LivingEntity rider, float x, float y, float z, float lift) {
        float radius = seatClearanceRadius(seat, rider) - CONTACT_EPSILON;
        float left = x - radius;
        float right = x + radius;
        float back = z - radius;
        float front = z + radius;
        float bottom = y + seatHeight(seat) + CONTACT_EPSILON;
        float top = bottom + rider.getHeight() + lift - 2f * CONTACT_EPSILON;
        int minX = MathUtils.floor(left);
        int maxX = MathUtils.floor(right);
        int minY = MathUtils.floor(bottom);
        int maxY = MathUtils.floor(top);
        int minZ = MathUtils.floor(back);
        int maxZ = MathUtils.floor(front);
        if (!world.isInsideWorld(minX, minY, minZ) || !world.isInsideWorld(maxX, maxY, maxZ)) {
            return false;
        }
        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockY = minY; blockY <= maxY; blockY++) {
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    Block.Bounds bounds = world.getCollisionBounds(blockX, blockY, blockZ);
                    if (bounds != null && bounds.intersects(blockX, blockY, blockZ, left, bottom, back, right, top, front)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Tìm điểm đứng phía gần nhất, giữ nguyên người cưỡi nếu toàn bộ vị trí bị chặn. */
    public final boolean tryDismount(World world, Player rider) {
        if (rider.getMount() != this) {
            return false;
        }
        boolean isDriver = getPassenger(0) == rider;
        if (isDriver) {
            charging = false;
            charge = 0f;
            releasedCharge = 0f;
        }
        float spacing = getHalfWidth() + rider.getHalfWidth() + SURFACE_EPSILON;
        float bestDistance = Float.POSITIVE_INFINITY;
        float bestX = 0f;
        float bestY = 0f;
        float bestZ = 0f;
        // Ưu tiên hai bên theo hướng thân khi khoảng cách ngang bằng nhau.
        float yaw = mobVisualState().bodyYaw();
        float leftX = MathUtils.cosDeg(yaw);
        float leftZ = -MathUtils.sinDeg(yaw);
        for (int row = -2; row <= 2; row++) {
            for (int col = -2; col <= 2; col++) {
                float dx = col * spacing * leftX + row * spacing * leftZ;
                float dz = col * spacing * leftZ - row * spacing * leftX;
                if (Math.max(Math.abs(dx), Math.abs(dz)) + SURFACE_EPSILON < spacing) {
                    continue;
                }
                float distance = dx * dx + dz * dz + (row == 0 ? 0f : SURFACE_EPSILON);
                if (distance >= bestDistance) {
                    continue;
                }
                float testX = x() + dx;
                float testZ = z() + dz;
                float floor = dismountFloor(world, rider, testX, testZ);
                if (Float.isFinite(floor)) {
                    bestDistance = distance;
                    bestX = testX;
                    bestY = floor;
                    bestZ = testZ;
                }
            }
        }
        if (!Float.isFinite(bestDistance)) {
            return false;
        }
        rider.leaveMount();
        rider.setPosition(bestX, bestY, bestZ);
        if (isDriver) {
            clearRiderInput();
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }
        return true;
    }

    /** Tìm mặt đỡ khô tại tâm người chơi trong ngưỡng bước lên và rơi an toàn. */
    private float dismountFloor(World world, Player rider, float x, float z) {
        int blockX = MathUtils.floor(x);
        int blockZ = MathUtils.floor(z);
        float low = y() - DISMOUNT_DROP;
        float high = y() + DISMOUNT_RISE;
        int minY = Math.max(0, MathUtils.floor(low) - 1);
        int maxY = Math.min(World.WORLD_SIZE_Y - 1, MathUtils.floor(high - SURFACE_EPSILON));
        for (int blockY = maxY; blockY >= minY; blockY--) {
            if (!world.isInsideWorld(blockX, blockY, blockZ)) {
                return Float.NaN;
            }
            byte id = world.getBlock(blockX, blockY, blockZ);
            if (id == Block.AIR.getId() || id == Block.WATER.getId()
                    || id == Block.LAVA.getId() || id == Block.CACTUS.getId()) {
                continue;
            }
            Block.Bounds bounds = world.getCollisionBounds(blockX, blockY, blockZ);
            if (bounds == null || x < blockX + bounds.minX() || x > blockX + bounds.maxX()
                    || z < blockZ + bounds.minZ() || z > blockZ + bounds.maxZ()) {
                continue;
            }
            float top = blockY + bounds.maxY();
            if (top >= low && top <= high && hasDismountRoom(world, rider, x, top, z)) {
                return top;
            }
        }
        return Float.NaN;
    }

    /** Quét hộp người chơi qua trần, tường, chất lỏng, gai và thực thể sống. */
    private boolean hasDismountRoom(World world, Player rider, float x, float y, float z) {
        float radius = rider.getHalfWidth() - SURFACE_EPSILON;
        float left = x - radius;
        float right = x + radius;
        float back = z - radius;
        float front = z + radius;
        float bottom = y + SURFACE_EPSILON;
        float top = y + rider.getHeight() - SURFACE_EPSILON;
        int minX = MathUtils.floor(left);
        int maxX = MathUtils.floor(right);
        int minY = MathUtils.floor(bottom);
        int maxY = MathUtils.floor(top);
        int minZ = MathUtils.floor(back);
        int maxZ = MathUtils.floor(front);
        if (!world.isInsideWorld(minX, minY, minZ) || !world.isInsideWorld(maxX, maxY, maxZ)) {
            return false;
        }
        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockY = minY; blockY <= maxY; blockY++) {
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    byte id = world.getBlock(blockX, blockY, blockZ);
                    if (id == Block.WATER.getId() || id == Block.LAVA.getId() || id == Block.CACTUS.getId()) {
                        return false;
                    }
                    Block.Bounds bounds = world.getCollisionBounds(blockX, blockY, blockZ);
                    if (bounds != null && bounds.intersects(blockX, blockY, blockZ, left, bottom, back, right, top, front)) {
                        return false;
                    }
                }
            }
        }
        for (int i = 0; i < world.entityCount(); i++) {
            Entity entity = world.entityAt(i);
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity == rider || entity == this) {
                continue;
            }
            entity.copyPosition(dismountProbe);
            float otherRadius = entity.getHalfWidth();
            if (dismountProbe.x - otherRadius < right && dismountProbe.x + otherRadius > left
                    && dismountProbe.z - otherRadius < front && dismountProbe.z + otherRadius > back
                    && dismountProbe.y < top && dismountProbe.y + entity.getHeight() > bottom) {
                return false;
            }
        }
        return true;
    }

    /** Hủy nạp lực và dọn lệnh điều khiển khi ghế trước đổi người. */
    private void clearRiderInput() {
        primaryRider = null;
        inputX = 0f;
        inputZ = 0f;
        sprinting = false;
        spaceDown = false;
        charging = false;
        charge = 0f;
        releasedCharge = 0f;
    }

    /** Trả trạng thái cho phép điều khiển. */
    public final boolean isTamed() { return tamed; }

    /** Trả độ quen từ 0 đến ngưỡng thuần hóa. */
    public final int temper() { return temper; }

    /** Trả sức nhảy tối đa của cá thể. */
    public final float jumpHeight() { return jumpHeight; }

    /** Trả tỷ lệ nạp lực hiện tại cho HUD. */
    public final float chargeProgress() { return charge; }

    /** Cho HUD biết loài dùng kỹ năng lao thay cho nhảy. */
    public boolean isDashMount() { return false; }

    /** Trả số giây chờ của kỹ năng lao, bằng không với thú dùng nhảy. */
    public float dashCooldownRemaining() { return 0f; }
}
