package com.voxelgame.world.item;

import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.block.LeavesBlock;
import com.voxelgame.world.level.block.WoolBlock;

/**
 * Công cụ Kéo ({@link Item#SHEARS}): tăng tốc độ đào khối lá cây và len lên 5×
 * và kích hoạt hành vi cắt len đặc thù qua {@link com.voxelgame.world.entity.mob.animal.Sheep#onInteract}.
 */
public final class ShearsItem extends ToolItem {

    /** Hệ số tăng tốc độ đào chuẩn Minecraft cho lá cây và len. */
    public static final float MINING_SPEED_MULTIPLIER = 5.0f;

    /** Khởi tạo vật phẩm Kéo. */
    ShearsItem(int id, String displayName, int maxStackSize, String textureName,
               int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, textureName, atlasTileIndex, fallbackRgb);
    }

    /** Đào khối thuộc nhánh {@link LeavesBlock} hoặc {@link WoolBlock} nhanh gấp 5 lần. */
    @Override
    public float getMiningSpeedMultiplier(Block block) {
        if (block instanceof LeavesBlock || block instanceof WoolBlock) {
            return MINING_SPEED_MULTIPLIER;
        }
        return 1.0f;
    }
}
