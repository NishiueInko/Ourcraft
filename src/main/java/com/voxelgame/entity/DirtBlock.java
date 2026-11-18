package com.voxelgame.entity;

import com.voxelgame.engine.Texture;

/**
 * Khối Đất (ID = 1): Có độ cứng 0.75 giây, sử dụng đồng nhất ô ảnh `TILE_DIRT` cho cả 6 mặt.
 */
public class DirtBlock extends Block {

    public DirtBlock() {
        super(
                (byte) 1,
                "Dirt",
                0.75f,
                true,
                false,
                true,
                new float[]{0.53f, 0.38f, 0.26f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_DIRT;
    }
}
