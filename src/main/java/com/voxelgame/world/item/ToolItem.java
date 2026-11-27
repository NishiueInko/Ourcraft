package com.voxelgame.world.item;

import com.voxelgame.world.level.block.Block;

/**
 * Lớp trừu tượng gốc cho nhóm công cụ tương tác đặc biệt
 * ({@link ShearsItem}, {@link FlintAndSteelItem}, {@link BucketItem}, {@link GoatHornItem}).
 */
public abstract class ToolItem extends Item {

    /** Khởi tạo công cụ độc lập đăng ký trong ItemTextureAtlas. */
    protected ToolItem(int id, String displayName, int maxStackSize, String textureName,
                       int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, Block.AIR, textureName, atlasTileIndex, false, fallbackRgb);
    }
}
