package com.voxelgame.world.physics;

import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.math.Vector3;

/** Duyệt ô theo DDA và kiểm tra phần hình học có thể ngắm trong mỗi ô. */
public final class Raycast {
    /** Thành phần nhỏ hơn giá trị này được xem như song song với mặt hộp. */
    private static final float PARALLEL_EPSILON = 1e-8f;

    /** Giữ tọa độ block trúng và ô kề theo pháp tuyến của mặt trúng. */
    public static final class HitResult {
        private boolean hit;
        private int blockX;
        private int blockY;
        private int blockZ;
        private int prevBlockX;
        private int prevBlockY;
        private int prevBlockZ;
        private Block block;
        private float distance = Float.POSITIVE_INFINITY;

        /** Khởi tạo kết quả có thể dùng lại trong các lần bắn tia. */
        public HitResult(boolean hit, int x, int y, int z, int prevX, int prevY, int prevZ, Block block) {
            set(hit, x, y, z, prevX, prevY, prevZ, block == null ? Block.AIR : block);
        }

        /** Tạo kết quả mặc định cho lần không trúng. */
        public static HitResult miss() {
            return new HitResult(false, 0, 0, 0, 0, 0, 0, Block.AIR);
        }

        /** Cập nhật đồng bộ mọi trường, kể cả khi kết quả bị đặt lại. */
        private void set(boolean hit, int x, int y, int z, int prevX, int prevY, int prevZ, Block block) {
            this.hit = hit;
            blockX = x;
            blockY = y;
            blockZ = z;
            prevBlockX = prevX;
            prevBlockY = prevY;
            prevBlockZ = prevZ;
            this.block = block;
        }

        /** Báo tia đã chạm hộp chọn. */
        public boolean isHit() { return hit; }
        /** Trả tọa độ X của ô trúng. */
        public int getBlockX() { return blockX; }
        /** Trả tọa độ Y của ô trúng. */
        public int getBlockY() { return blockY; }
        /** Trả tọa độ Z của ô trúng. */
        public int getBlockZ() { return blockZ; }
        /** Trả tọa độ X của ô tiếp giáp mặt trúng. */
        public int getPrevBlockX() { return prevBlockX; }
        /** Trả tọa độ Y của ô tiếp giáp mặt trúng. */
        public int getPrevBlockY() { return prevBlockY; }
        /** Trả tọa độ Z của ô tiếp giáp mặt trúng. */
        public int getPrevBlockZ() { return prevBlockZ; }
        /** Trả khối được ngắm hoặc Air. */
        public Block getBlock() { return block; }
        /** Trả khối được ngắm qua tên truy vấn tương thích. */
        public Block getBlockType() { return block; }
        /** Trả khoảng cách từ đầu tia đến mặt trúng hoặc vô cực nếu không trúng. */
        public float getDistance() { return distance; }
    }

    /** Kết quả tia thực thể và bộ đệm tâm hộp dùng qua nhiều lần chọn. */
    public static final class EntityHitResult {
        private final Vector3 center = new Vector3();
        private LivingEntity entity;
        private float distance = Float.POSITIVE_INFINITY;

        /** Trả mục tiêu còn sống gần nhất, hoặc null khi tia không trúng. */
        public LivingEntity getEntity() { return entity; }
        /** Trả khoảng cách mặt hộp hoặc vô cực khi tia không trúng. */
        public float getDistance() { return distance; }
        /** Báo tia đã chọn được mục tiêu. */
        public boolean isHit() { return entity != null; }
    }

    /** Bắn tia và trả một kết quả mới cho nơi gọi cần lưu độc lập. */
    public static HitResult cast(World world, Vector3 origin, Vector3 direction, float maxDistance) {
        return cast(world, origin, direction, maxDistance, HitResult.miss());
    }

    /** Bắn tia vào bộ kết quả có sẵn, không đổi hai vector đầu vào. */
    public static HitResult cast(World world, Vector3 origin, Vector3 direction, float maxDistance, HitResult result) {
        return castBlocks(world, origin, direction, maxDistance, result, false);
    }

    /** Tìm hộp va chạm gần nhất để chặn đánh xuyên khối mà vẫn cho tia đi qua cây chữ X. */
    public static HitResult castOccluder(World world, Vector3 origin, Vector3 direction, float maxDistance,
                                         HitResult result) {
        return castBlocks(world, origin, direction, maxDistance, result, true);
    }

    /** Dùng cùng bước DDA cho hộp chọn block và hộp cản đường chiến đấu. */
    private static HitResult castBlocks(World world, Vector3 origin, Vector3 direction, float maxDistance,
                                        HitResult result, boolean collisionOnly) {
        result.set(false, 0, 0, 0, 0, 0, 0, Block.AIR);
        result.distance = Float.POSITIVE_INFINITY;
        float lengthSquared = direction.len2();
        if (!validRay(origin, lengthSquared, maxDistance)) {
            return result;
        }

        float inverseLength = 1.0f / (float) Math.sqrt(lengthSquared);
        float dx = direction.x * inverseLength;
        float dy = direction.y * inverseLength;
        float dz = direction.z * inverseLength;
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);
        int sx = dx >= 0 ? 1 : -1;
        int sy = dy >= 0 ? 1 : -1;
        int sz = dz >= 0 ? 1 : -1;
        float nextX = timeToBoundary(origin.x, x, sx, dx);
        float nextY = timeToBoundary(origin.y, y, sy, dy);
        float nextZ = timeToBoundary(origin.z, z, sz, dz);
        float stepX = Math.abs(1.0f / dx);
        float stepY = Math.abs(1.0f / dy);
        float stepZ = Math.abs(1.0f / dz);

        float entered = 0;
        while (entered <= maxDistance) {
            float leaves = Math.min(nextX, Math.min(nextY, nextZ));
            Block block = Block.fromId(world.getBlock(x, y, z));
            if (block.isSelectable() || collisionOnly) {
                int state = world.getBlockState(x, y, z);
                Block.Bounds bounds = collisionOnly ? block.getCollisionBounds(state) : block.getSelectionBounds(state);
                if (bounds != null && hitBounds(result, block, bounds, x, y, z, origin, dx, dy, dz,
                        entered, Math.min(leaves, maxDistance))) {
                    return result;
                }
            }

            if (nextX < nextY && nextX < nextZ) {
                x += sx;
                entered = nextX;
                nextX += stepX;
            } else if (nextY < nextZ) {
                y += sy;
                entered = nextY;
                nextY += stepY;
            } else {
                z += sz;
                entered = nextZ;
                nextZ += stepZ;
            }
        }
        return result;
    }

    /** Chọn hộp thực thể gần nhất trong tầm và nằm trước mặt khối cản. */
    public static EntityHitResult castEntity(World world, Vector3 origin, Vector3 direction, float maxDistance,
                                             float blockDistanceLimit, Entity exclude, EntityHitResult out) {
        out.entity = null;
        out.distance = Float.POSITIVE_INFINITY;
        float lengthSquared = direction.len2();
        if (!validRay(origin, lengthSquared, maxDistance) || !(blockDistanceLimit > 0f)) {
            return out;
        }
        float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
        float dx = direction.x * inverseLength;
        float dy = direction.y * inverseLength;
        float dz = direction.z * inverseLength;
        float limit = Math.min(maxDistance, blockDistanceLimit);
        for (int i = 0; i < world.entityCount(); i++) {
            Entity candidate = world.entityAt(i);
            if (candidate == exclude || !(candidate instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            Vector3 center = living.copyCenterPosition(out.center);
            float distance = entityEntry(origin, dx, dy, dz, center, living.getHalfWidth(),
                    living.getHeight() * 0.5f, limit);
            if (distance < blockDistanceLimit && distance < out.distance) {
                out.entity = living;
                out.distance = distance;
                limit = distance;
            }
        }
        return out;
    }

    /** Kiểm tra đầu tia hữu hạn, hướng đủ dài và tầm với hợp lệ. */
    private static boolean validRay(Vector3 origin, float lengthSquared, float maxDistance) {
        return lengthSquared >= 1e-8f && Float.isFinite(lengthSquared)
                && Float.isFinite(maxDistance) && maxDistance >= 0f
                && Float.isFinite(origin.x) && Float.isFinite(origin.y) && Float.isFinite(origin.z);
    }

    /** Giao ba khoảng chiếu trên các trục, lấy khoảng cách vào hộp hoặc vô cực. */
    private static float entityEntry(Vector3 origin, float dx, float dy, float dz, Vector3 center,
                                     float halfWidth, float halfHeight, float limit) {
        float near = 0f;
        float far = limit;
        for (int axis = 0; axis < 3; axis++) {
            float relative = axis == 0 ? center.x - origin.x : axis == 1 ? center.y - origin.y : center.z - origin.z;
            float direction = axis == 0 ? dx : axis == 1 ? dy : dz;
            float halfExtent = axis == 1 ? halfHeight : halfWidth;
            if (Math.abs(direction) < PARALLEL_EPSILON) {
                if (Math.abs(relative) > halfExtent) {
                    return Float.POSITIVE_INFINITY;
                }
                continue;
            }
            float centerDistance = relative / direction;
            float radius = halfExtent / Math.abs(direction);
            near = Math.max(near, centerDistance - radius);
            far = Math.min(far, centerDistance + radius);
            if (far < near) {
                return Float.POSITIVE_INFINITY;
            }
        }
        return near;
    }

    /** Tính thời điểm tới biên ô, tránh chia cho thành phần hướng gần 0. */
    private static float timeToBoundary(float origin, int cell, int step, float direction) {
        return Math.abs(direction) < PARALLEL_EPSILON
                ? Float.POSITIVE_INFINITY : (cell + (step > 0 ? 1.0f : 0.0f) - origin) / direction;
    }

    /** Cắt tia với ba khoảng hộp trong đoạn DDA hiện tại và lấy pháp tuyến mặt vào. */
    private static boolean hitBounds(HitResult out, Block block, Block.Bounds b, int x, int y, int z,
                                     Vector3 origin, float dx, float dy, float dz, float from, float to) {
        float near = Float.NEGATIVE_INFINITY;
        float far = to;
        int nx = 0;
        int ny = 0;
        int nz = 0;
        for (int axis = 0; axis < 3; axis++) {
            float o = axis == 0 ? origin.x : axis == 1 ? origin.y : origin.z;
            float d = axis == 0 ? dx : axis == 1 ? dy : dz;
            float min = axis == 0 ? x + b.minX() : axis == 1 ? y + b.minY() : z + b.minZ();
            float max = axis == 0 ? x + b.maxX() : axis == 1 ? y + b.maxY() : z + b.maxZ();
            if (Math.abs(d) < PARALLEL_EPSILON) {
                if (o < min || o > max) {
                    return false;
                }
                continue;
            }
            float first = ((d > 0 ? min : max) - o) / d;
            float last = ((d > 0 ? max : min) - o) / d;
            if (first > near) {
                near = first;
                int sign = d > 0 ? -1 : 1;
                nx = axis == 0 ? sign : 0;
                ny = axis == 1 ? sign : 0;
                nz = axis == 2 ? sign : 0;
            }
            far = Math.min(far, last);
            if (far < Math.max(near, from)) {
                return false;
            }
        }
        if (near < 0) {
            nx = 0;
            ny = 0;
            nz = 0;
        }
        out.set(true, x, y, z, x + nx, y + ny, z + nz, block);
        out.distance = Math.max(near, from);
        return true;
    }
}
