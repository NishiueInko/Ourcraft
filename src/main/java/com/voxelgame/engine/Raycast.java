package com.voxelgame.engine;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.Block;

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
    }

    /** Bắn tia và trả một kết quả mới cho nơi gọi cần lưu độc lập. */
    public static HitResult cast(World world, Vector3 origin, Vector3 direction, float maxDistance) {
        return cast(world, origin, direction, maxDistance, HitResult.miss());
    }

    /** Bắn tia vào bộ kết quả có sẵn, không đổi hai vector đầu vào. */
    public static HitResult cast(World world, Vector3 origin, Vector3 direction, float maxDistance, HitResult result) {
        result.set(false, 0, 0, 0, 0, 0, 0, Block.AIR);
        float lengthSquared = direction.len2();
        if (!(lengthSquared >= 1e-8f) || !Float.isFinite(lengthSquared)
                || !Float.isFinite(maxDistance) || maxDistance < 0
                || !Float.isFinite(origin.x) || !Float.isFinite(origin.y) || !Float.isFinite(origin.z)) {
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
            if (block.isSelectable()) {
                Block.Bounds bounds = block.getSelectionBounds(world.getBlockState(x, y, z));
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
        return true;
    }
}
