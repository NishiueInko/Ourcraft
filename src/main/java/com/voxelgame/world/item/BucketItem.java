package com.voxelgame.world.item;

/**
 * Công cụ Xô rỗng ({@link Item#BUCKET}): vật phẩm chứa dùng cho hành vi vắt sữa đặc thù
 * tại {@link com.voxelgame.world.entity.mob.animal.Cow#onInteract} và
 * {@link com.voxelgame.world.entity.mob.animal.Goat#onInteract}.
 */
public final class BucketItem extends ToolItem {

    /** Khởi tạo vật phẩm Xô rỗng. */
    BucketItem(int id, String displayName, int maxStackSize, String textureName,
               int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, textureName, atlasTileIndex, fallbackRgb);
    }
}
