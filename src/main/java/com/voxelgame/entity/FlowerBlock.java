package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Bốn hoa Plains cùng luật nền và rơi đúng vật phẩm. */
final class FlowerBlock extends PlantBlock {
    /** Khởi tạo loại hoa theo ID và texture riêng. */
    FlowerBlock(int id, String name, int tile) {
        super(id, name, tile);
    }
}
