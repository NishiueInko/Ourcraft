package com.voxelgame.world.item;

import com.voxelgame.world.level.block.Block;

/**
 * Vật phẩm nguyên liệu, chiến lợi phẩm, mũi tên hoặc đĩa nhạc không có hành vi sử dụng chủ động.
 */
public final class SimpleItem extends Item {

    /** Khởi tạo vật phẩm độc lập đơn giản đăng ký vào ItemTextureAtlas. */
    SimpleItem(int id, String displayName, int maxStackSize, String textureName,
               int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, Block.AIR, textureName, atlasTileIndex, false, fallbackRgb);
    }
}
