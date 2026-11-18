package com.voxelgame.entity;

import com.voxelgame.engine.Texture;

/**
 * Khối Gỗ Sồi (ID = 5): Có độ cứng 3.0 giây.
 * Đa hình họa tiết: Mặt trên và mặt dưới hiển thị vân gỗ tròn (`TILE_OAK_LOG_TOP`),
 * 4 mặt bên hiển thị lớp vỏ cây sồi dọc (`TILE_OAK_LOG_SIDE`).
 */
public class OakLogBlock extends Block {

    public OakLogBlock() {
        super(
                (byte) 5,
                "Oak Log",
                3.0f,
                true,
                false,
                true,
                new float[]{0.43f, 0.33f, 0.20f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return switch (face) {
            case TOP, BOTTOM -> Texture.TILE_OAK_LOG_TOP;
            case NORTH, SOUTH, EAST, WEST -> Texture.TILE_OAK_LOG_SIDE;
        };
    }
}
