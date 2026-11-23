package com.voxelgame.entity;

import com.voxelgame.engine.Texture;

/** Cát sa mạc, rơi khi mất nền. */
final class SandBlock extends Block {
    /** Khởi tạo khối với kiểu vật liệu và độ cứng cố định. */
    SandBlock() {
        super((byte) 9, "Sand", 0.75f, true, false, true, new float[]{0.85f, 0.78f, 0.52f});
    }

    /** Dùng một tile cho cả sáu mặt. */
    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_SAND;
    }

    /** Khối rơi khi không còn va chạm nâng đỡ ở ô dưới. */
    @Override
    public boolean isGravityBlock() {
        return true;
    }
}
