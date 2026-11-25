package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.MathUtils;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;

/** Dò tối đa hai vị trí phía trước và thử đổi hướng quanh các góc gần mob. */
public final class SimpleGridNavigator {
    /** Dung sai cho các mặt tiếp xúc, góc đổi hướng và khoảng đến đích. */
    private static final float EPSILON = 0.0001f;
    private static final float DIAGONAL = 0.70710677f;
    private static final float ARRIVAL_DISTANCE_SQUARED = 0.22f * 0.22f;

    /** Mob cung cấp tọa độ, hộp va chạm và giới hạn lên xuống bậc. */
    private final MobEntity mob;
    /** Trạng thái đích và phía vừa đi vòng để giữ hướng ổn định qua góc. */
    private boolean done = true;
    private int turnSide = 1;

    /** Tạo bộ điều hướng riêng cho một cá thể, không cần danh sách đường đi. */
    SimpleGridNavigator(MobEntity mob) {
        this.mob = mob;
    }

    /** Trả trạng thái đã đến gần đích hoặc lệnh di chuyển đã dừng. */
    public boolean isDone() {
        return done;
    }

    /** Xóa trạng thái đích để goal khác có thể nhận quyền di chuyển. */
    void stop() {
        done = true;
    }

    /**
     * Ưu tiên hướng đích, rồi thử góc 45 và 90 độ ở hai phía.
     * Khi đường chỉ thoáng ở đoạn gần, vẫn tiến tới đó để có thể dò lại ở vị trí mới.
     */
    void moveToward(World world, float x, float z, float speed) {
        float dx = x - mob.x();
        float dz = z - mob.z();
        float distanceSquared = dx * dx + dz * dz;
        if (!(distanceSquared > ARRIVAL_DISTANCE_SQUARED) || !Float.isFinite(distanceSquared)
                || !(speed > 0f) || !Float.isFinite(speed)) {
            mob.stopMoving();
            return;
        }
        done = false;
        float distance = (float) Math.sqrt(distanceSquared);
        dx /= distance;
        dz /= distance;
        if (!mob.isOnGround() || mob.inWater()) {
            mob.steer(dx, dz, speed);
            return;
        }

        float near = Math.min(distance, 0.65f);
        float far = Math.min(distance, 1.30f);
        int direct = clearance(world, dx, dz, near, far);
        if (direct == 2) {
            mob.steer(dx, dz, speed);
            return;
        }
        float bestX = dx;
        float bestZ = dz;
        int best = direct;
        int bestSide = turnSide;
        for (int i = 0; i < 4; i++) {
            int side = (i & 1) == 0 ? turnSide : -turnSide;
            float turnX = -side * dz;
            float turnZ = side * dx;
            if (i < 2) {
                turnX = (dx + turnX) * DIAGONAL;
                turnZ = (dz + turnZ) * DIAGONAL;
            }
            int clear = clearance(world, turnX, turnZ, near, far);
            // Nếu hướng thẳng còn dùng được, tránh đi ngang chỉ vì chân trời gần bị chắn.
            if (clear > best && (i < 2 || direct == 0)) {
                best = clear;
                bestX = turnX;
                bestZ = turnZ;
                bestSide = side;
                if (best == 2) {
                    break;
                }
            }
        }
        if (best > 0) {
            turnSide = bestSide;
            mob.steer(bestX, bestZ, speed);
        } else {
            mob.steer(0f, 0f, 0f);
        }
    }

    /** Trả 0 khi đoạn đầu bị chặn, 1 khi chỉ đoạn đầu hợp lệ, 2 khi cả hai hợp lệ. */
    private int clearance(World world, float dx, float dz, float near, float far) {
        float x = mob.x();
        float y = mob.y();
        float z = mob.z();
        float first = findFeetY(world, x + dx * near, y, z + dz * near);
        if (!Float.isFinite(first) || (first > y + EPSILON && !canRise(world, x, y, z, first - y))) {
            return 0;
        }
        if (far == near || Float.isFinite(findFeetY(world, x + dx * far, first, z + dz * far))) {
            return 2;
        }
        return 1;
    }

    /** Tìm cao độ mặt đỡ gần mốc y ở tâm ô, hoặc trả NaN nếu không đứng được. */
    public float findStandableY(World world, int x, int y, int z) {
        return findFeetY(world, x + 0.5f, y, z + 0.5f);
    }

    /** Tìm mặt đỡ dưới toàn bộ dấu chân trong ngưỡng bậc lên và quãng rơi an toàn. */
    float findFeetY(World world, float x, float referenceY, float z) {
        float highest = referenceY + mob.stepHeight();
        float lowest = referenceY - mob.maxSafeFall();
        float radius = mob.getHalfWidth();
        int minX = MathUtils.floor(x - radius + EPSILON);
        int maxX = MathUtils.floor(x + radius - EPSILON);
        int minZ = MathUtils.floor(z - radius + EPSILON);
        int maxZ = MathUtils.floor(z + radius - EPSILON);
        int minY = Math.max(0, MathUtils.floor(lowest) - 1);
        int maxY = Math.min(World.WORLD_SIZE_Y - 1, MathUtils.floor(highest - EPSILON));
        if (!world.isInsideWorld(minX, 0, minZ) || !world.isInsideWorld(maxX, 0, maxZ)) {
            return Float.NaN;
        }

        for (int y = maxY; y >= minY; y--) {
            float top = Float.NEGATIVE_INFINITY;
            for (int blockX = minX; blockX <= maxX; blockX++) {
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    byte id = world.getBlock(blockX, y, blockZ);
                    if (id == Block.WATER.getId()) {
                        // Mặt nước là điểm điều hướng của mob bơi, thú cưỡi cần nền thật.
                        if (!(mob instanceof MountableMob)) {
                            top = Math.max(top, y + 0.87f);
                        }
                    } else if (id != Block.AIR.getId() && id != Block.CACTUS.getId() && id != Block.LAVA.getId()) {
                        Block.Bounds bounds = world.getCollisionBounds(blockX, y, blockZ);
                        if (bounds != null && x + radius > blockX + bounds.minX()
                                && x - radius < blockX + bounds.maxX()
                                && z + radius > blockZ + bounds.minZ() && z - radius < blockZ + bounds.maxZ()) {
                            top = Math.max(top, y + bounds.maxY());
                        }
                    }
                }
            }
            if (top >= lowest - EPSILON && top <= highest + EPSILON
                    && bodyClear(world, x, top, z, top + mob.getHeight(), true)) {
                return top;
            }
        }
        return Float.NaN;
    }

    /** Kiểm tra cả thể tích quét dọc, kể cả trần nằm giữa vị trí đầu và vị trí nâng. */
    boolean canRise(World world, float x, float y, float z, float rise) {
        return bodyClear(world, x, y, z, y + mob.getHeight() + rise, true)
                && (!(mob instanceof MountableMob mount) || mount.seatsClear(world, x, y, z, rise));
    }

    /** Kiểm tra toàn thân ở vị trí dự kiến khi đặt chân trên bậc. */
    boolean canOccupy(World world, float x, float y, float z) {
        return bodyClear(world, x, y, z, y + mob.getHeight(), true)
                && (!(mob instanceof MountableMob mount) || mount.seatsClear(world, x, y, z, 0f));
    }

    /** Tránh Lava và Cactus ngay cả ở quãng ngang giữa hai lần đáp đất. */
    boolean avoidsHazards(World world, float x, float y, float z) {
        return bodyClear(world, x, y, z, y + mob.getHeight(), false);
    }

    /** Quét hộp thân thể, xét khối nguy hiểm và tùy chọn kiểm tra va chạm rắn. */
    private boolean bodyClear(World world, float x, float bottom, float z, float top, boolean checkSolids) {
        float radius = mob.getHalfWidth() - EPSILON;
        float left = x - radius;
        float right = x + radius;
        float back = z - radius;
        float front = z + radius;
        int minX = MathUtils.floor(left);
        int maxX = MathUtils.floor(right);
        int minY = MathUtils.floor(bottom + EPSILON);
        int maxY = MathUtils.floor(top - EPSILON);
        int minZ = MathUtils.floor(back);
        int maxZ = MathUtils.floor(front);
        if (!world.isInsideWorld(minX, minY, minZ) || !world.isInsideWorld(maxX, maxY, maxZ)) {
            return false;
        }
        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int y = minY; y <= maxY; y++) {
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    byte id = world.getBlock(blockX, y, blockZ);
                    if (id == Block.LAVA.getId() || id == Block.CACTUS.getId()) {
                        return false;
                    }
                    if (checkSolids && id != Block.AIR.getId()) {
                        Block.Bounds bounds = world.getCollisionBounds(blockX, y, blockZ);
                        if (bounds != null && bounds.intersects(blockX, y, blockZ,
                                left, bottom + EPSILON, back, right, top - EPSILON, front)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
