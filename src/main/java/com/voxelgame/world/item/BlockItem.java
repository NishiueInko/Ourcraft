package com.voxelgame.world.item;

import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.level.block.Block;

/**
 * Vật phẩm đại diện cho một {@link Block} có thể đặt xuống thế giới (gồm cả 6 khối len cừu mini).
 */
public final class BlockItem extends Item {

    /** Tạo vật phẩm bao bọc một khối địa hình thông thường lấy UV trực tiếp từ atlas khối. */
    BlockItem(int id, Block block) {
        super(id, block.getDisplayName(), Hotbar.MAX_STACK_SIZE, block, null, -1, false, 0xFFFFFF);
    }

    /** Tạo vật phẩm khối có tile riêng trong ItemTextureAtlas (ví dụ 6 loại khối len cừu). */
    BlockItem(int id, String displayName, int maxStackSize, Block block, String textureName,
              int atlasTileIndex, boolean cubeItem, int fallbackRgb) {
        super(id, displayName, maxStackSize, block, textureName, atlasTileIndex, cubeItem, fallbackRgb);
    }
}
