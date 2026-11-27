package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Mía mọc thành cột trên nền có nước tưới ở ô ngang. */
final class SugarCaneBlock extends PlantBlock {
    /** Đăng ký mía và ảnh thân. */
    SugarCaneBlock() {
        super(20, "Sugar Cane", Texture.TILE_SUGAR_CANE);
    }

    /** Kiểm tra cây cùng loại hoặc nền hợp lệ có nước ở mức nền. */
    @Override
    public boolean canPlaceAt(World world, int x, int y, int z) {
        int baseY = y - 1;
        Block below = fromId(world.getBlock(x, baseY, z));
        if (below == SUGAR_CANE) {
            return true;
        }
        if (below != GRASS && below != DIRT && below != SAND) {
            return false;
        }
        byte water = WATER.getId();
        return world.getBlock(x - 1, baseY, z) == water || world.getBlock(x + 1, baseY, z) == water
                || world.getBlock(x, baseY, z - 1) == water || world.getBlock(x, baseY, z + 1) == water;
    }
}
