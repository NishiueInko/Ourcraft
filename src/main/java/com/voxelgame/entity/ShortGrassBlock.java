package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Cỏ thấp có thể bị ghi đè khi đặt block rắn. */
final class ShortGrassBlock extends PlantBlock {
    /** Đăng ký cỏ thấp. */
    ShortGrassBlock() {
        super(14, "Short Grass", Texture.TILE_SHORT_GRASS);
    }

    /** Cho phép đặt một khối rắn vào cùng ô. */
    @Override
    public boolean isReplaceable(int state) {
        return true;
    }
}
