package com.voxelgame.world.item;

import com.voxelgame.world.level.block.Block;

/**
 * Vũ khí Giáo sắt ({@link Item#IRON_SPEAR}): cung cấp đòn đâm tầm xa Jab (tầm 4,2 block, 5 HP, hồi chiêu 0,65s)
 * và chế độ giữ chuột phải hạ giáo lao tới (Charge) mà không làm chậm tốc độ di chuyển.
 */
public final class SpearItem extends Item {

    /** Khởi tạo vật phẩm Giáo sắt. */
    SpearItem(int id, String displayName, int maxStackSize, String textureName,
              int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, Block.AIR, textureName, atlasTileIndex, false, fallbackRgb);
    }

    @Override
    public UseAction getUseAction() {
        return UseAction.SPEAR;
    }

    /** Khi hạ giáo Charge, người chơi vẫn giữ nguyên tốc độ chạy để tích sát thương lao tới. */
    @Override
    public boolean slowsPlayerWhileUsing() {
        return false;
    }

    @Override
    public float getMeleeReach() {
        return ItemUseHandler.SPEAR_JAB_REACH;
    }

    @Override
    public float getMeleeDamage() {
        return ItemUseHandler.SPEAR_JAB_DAMAGE;
    }

    @Override
    public float getMeleeCooldown() {
        return ItemUseHandler.SPEAR_JAB_COOLDOWN;
    }
}
