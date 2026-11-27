package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;

/** Cát sa mạc, rơi khi mất nền. */
final class SandBlock extends GravityBlock {
    /** Khởi tạo khối với kiểu vật liệu và độ cứng cố định. */
    SandBlock() {
        super((byte) 9, "Sand", 0.75f, Texture.TILE_SAND, new float[]{0.85f, 0.78f, 0.52f});
    }
}
