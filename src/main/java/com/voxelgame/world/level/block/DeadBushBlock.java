package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Bụi khô cần nền cát hoặc đất. */
final class DeadBushBlock extends PlantBlock {
    /** Đăng ký bụi khô và tile tương ứng. */
    DeadBushBlock() {
        super(13, "Dead Bush", Texture.TILE_DEAD_BUSH);
    }

    /** Chỉ cát và đất đỡ được bụi khô. */
    @Override
    public boolean canPlaceAt(World world, int x, int y, int z) {
        Block below = fromId(world.getBlock(x, y - 1, z));
        return below == SAND || below == DIRT;
    }
}
