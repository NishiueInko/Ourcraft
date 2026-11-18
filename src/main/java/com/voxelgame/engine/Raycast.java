package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import org.joml.Vector3f;

/**
 * Thực hiện thuật toán bắn tia 3D DDA (Digital Differential Analyzer - Amanatides & Woo)
 * trong không gian lưới Voxel 3D.
 *
 * THỂ HIỆN TÍNH ĐÓNG GÓI (ENCAPSULATION) & ĐA HÌNH (POLYMORPHISM):
 * - Lớp kết quả `HitResult` đóng gói toàn bộ các trường tọa độ ở phạm vi `private final` và chỉ
 *   cung cấp các phương thức truy vấn chuẩn (`isHit()`, `getBlockX()`, `getBlockY()`, `getBlockZ()`,
 *   `getPrevBlockX()`, `getPrevBlockY()`, `getPrevBlockZ()`, `getBlock()`).
 * - Tia Raycast kiểm tra tính chất vật lý của mỗi ô thông qua đối tượng đa hình `Block.fromId(blockId)`:
 *   tự động đi xuyên qua Không khí (`AirBlock`) và Chất lỏng (`WaterBlock`, `LavaBlock`), chỉ dừng lại
 *   khi chạm vào một khối rắn (`block.isSolid() == true`).
 */
public class Raycast {

    /**
     * Cấu trúc dữ liệu bất biến (Immutable Value Object) lưu trữ kết quả bắn tia Raycast.
     */
    public static class HitResult {
        private final boolean hit;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final int prevBlockX;
        private final int prevBlockY;
        private final int prevBlockZ;
        private final Block block;

        public HitResult(
                boolean hit,
                int blockX,
                int blockY,
                int blockZ,
                int prevBlockX,
                int prevBlockY,
                int prevBlockZ,
                Block block
        ) {
            this.hit = hit;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.prevBlockX = prevBlockX;
            this.prevBlockY = prevBlockY;
            this.prevBlockZ = prevBlockZ;
            this.block = (block != null) ? block : Block.AIR;
        }

        public static HitResult miss() {
            return new HitResult(false, 0, 0, 0, 0, 0, 0, Block.AIR);
        }

        public boolean isHit() {
            return hit;
        }

        public int getBlockX() {
            return blockX;
        }

        public int getBlockY() {
            return blockY;
        }

        public int getBlockZ() {
            return blockZ;
        }

        public int getPrevBlockX() {
            return prevBlockX;
        }

        public int getPrevBlockY() {
            return prevBlockY;
        }

        public int getPrevBlockZ() {
            return prevBlockZ;
        }

        public Block getBlock() {
            return block;
        }

        public Block getBlockType() {
            return block;
        }
    }

    /**
     * Bắn một tia từ vị trí mắt Camera (`origin`) theo hướng nhìn (`direction`) với tầm với tối đa (`maxDistance`).
     * Tự động bỏ qua Không khí (`Block.AIR`) và các khối Chất lỏng (`WaterBlock`, `LavaBlock`).
     */
    public static HitResult cast(World world, Vector3f origin, Vector3f direction, float maxDistance) {
        Vector3f dir = new Vector3f(direction);
        if (dir.lengthSquared() < 1e-8f) {
            return HitResult.miss();
        }
        dir.normalize();

        int mapX = (int) Math.floor(origin.x);
        int mapY = (int) Math.floor(origin.y);
        int mapZ = (int) Math.floor(origin.z);

        int prevX = mapX;
        int prevY = mapY;
        int prevZ = mapZ;

        int stepX = dir.x >= 0 ? 1 : -1;
        int stepY = dir.y >= 0 ? 1 : -1;
        int stepZ = dir.z >= 0 ? 1 : -1;

        float tDeltaX = (Math.abs(dir.x) < 1e-8f) ? Float.POSITIVE_INFINITY : Math.abs(1.0f / dir.x);
        float tDeltaY = (Math.abs(dir.y) < 1e-8f) ? Float.POSITIVE_INFINITY : Math.abs(1.0f / dir.y);
        float tDeltaZ = (Math.abs(dir.z) < 1e-8f) ? Float.POSITIVE_INFINITY : Math.abs(1.0f / dir.z);

        float nextVoxelBoundaryX = (stepX > 0) ? (mapX + 1.0f) : mapX;
        float nextVoxelBoundaryY = (stepY > 0) ? (mapY + 1.0f) : mapY;
        float nextVoxelBoundaryZ = (stepZ > 0) ? (mapZ + 1.0f) : mapZ;

        float tMaxX = (Math.abs(dir.x) < 1e-8f) ? Float.POSITIVE_INFINITY : (nextVoxelBoundaryX - origin.x) / dir.x;
        float tMaxY = (Math.abs(dir.y) < 1e-8f) ? Float.POSITIVE_INFINITY : (nextVoxelBoundaryY - origin.y) / dir.y;
        float tMaxZ = (Math.abs(dir.z) < 1e-8f) ? Float.POSITIVE_INFINITY : (nextVoxelBoundaryZ - origin.z) / dir.z;

        float traveledDistance = 0.0f;

        while (traveledDistance <= maxDistance) {
            byte blockId = world.getBlock(mapX, mapY, mapZ);
            Block block = Block.fromId(blockId);

            // Chỉ dừng lại khi gặp một Block RẮN (block.isSolid() == true)
            if (block.isSolid()) {
                return new HitResult(true, mapX, mapY, mapZ, prevX, prevY, prevZ, block);
            }

            prevX = mapX;
            prevY = mapY;
            prevZ = mapZ;

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    mapX += stepX;
                    traveledDistance = tMaxX;
                    tMaxX += tDeltaX;
                } else {
                    mapZ += stepZ;
                    traveledDistance = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    mapY += stepY;
                    traveledDistance = tMaxY;
                    tMaxY += tDeltaY;
                } else {
                    mapZ += stepZ;
                    traveledDistance = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return HitResult.miss();
    }
}
