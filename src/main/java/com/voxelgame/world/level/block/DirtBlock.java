package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;

/**
 * Khối Đất (ID = 1): Có độ cứng 0.75 giây, sử dụng đồng nhất ô ảnh `TILE_DIRT`
 * cho cả 6 mặt.
 */
public class DirtBlock extends SolidBlock {

    public DirtBlock() {
        super(
                (byte) 1,
                "Dirt",
                0.75f,
                Texture.TILE_DIRT,
                new float[] { 0.53f, 0.38f, 0.26f });
    }
}
