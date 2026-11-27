package com.voxelgame.world.item;

import com.voxelgame.client.render.Camera;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.World;

/**
 * Công cụ Tù và Sừng dê ({@link Item#GOAT_HORN}): phát âm thanh gọi sừng dê với hồi chiêu 7,0 giây.
 */
public final class GoatHornItem extends ToolItem {

    /** Khởi tạo vật phẩm Sừng dê. */
    GoatHornItem(int id, String displayName, int maxStackSize, String textureName,
                 int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, textureName, atlasTileIndex, fallbackRgb);
    }

    /** Kích hoạt thổi tù và sừng dê qua bộ quản lý hồi chiêu trong {@link ItemUseHandler}. */
    @Override
    public boolean onInstantUse(World world, Player player, Camera camera, ItemUseHandler handler) {
        if (world == null || player == null || !player.isAlive()) {
            return false;
        }
        handler.triggerGoatHorn(world);
        return true;
    }
}
