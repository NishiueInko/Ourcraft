package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;

/**
 * Khối Gỗ Sồi (ID = 5): Có độ cứng 3.0 giây.
 * Đa hình họa tiết: Mặt trên và mặt dưới hiển thị vân gỗ tròn (`TILE_OAK_LOG_TOP`),
 * 4 mặt bên hiển thị lớp vỏ cây sồi dọc (`TILE_OAK_LOG_SIDE`).
 */
public class OakLogBlock extends LogBlock {

    public OakLogBlock() {
        super(
                (byte) 5,
                "Oak Log",
                3.0f,
                Texture.TILE_OAK_LOG_TOP,
                Texture.TILE_OAK_LOG_SIDE,
                new float[]{0.43f, 0.33f, 0.20f}
        );
    }
}
