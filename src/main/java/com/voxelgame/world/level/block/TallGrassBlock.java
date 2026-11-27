package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Cỏ cao gồm nửa dưới mặc định và nửa trên. */
final class TallGrassBlock extends PlantBlock {
    /** Đăng ký cỏ cao với tile mặc định ở nửa dưới. */
    TallGrassBlock() {
        super(15, "Tall Grass", Texture.TILE_TALL_GRASS_BOTTOM);
    }

    /** Chọn sprite theo nửa cây. */
    @Override
    public int getTileIndex(Face face, int state, Block above) {
        return state == 1 ? Texture.TILE_TALL_GRASS_TOP : getTileIndex(face);
    }

    /** Chỉ có hai trạng thái, 0 dưới và 1 trên. */
    @Override
    public int normalizeState(int state) {
        return state == 1 ? 1 : 0;
    }

    /** Một khối rắn có thể ghi đè vào ô cỏ. */
    @Override
    public boolean isReplaceable(int state) {
        return true;
    }

    /** Gốc cần đất/cỏ, ngọn cần đúng nửa gốc ở bên dưới. */
    @Override
    public void onNeighborChange(World world, int x, int y, int z) {
        if (world.getBlockState(x, y, z) == 0) {
            if (!super.canPlaceAt(world, x, y, z)
                    || world.getBlock(x, y + 1, z) != getId()
                    || world.getBlockState(x, y + 1, z) != 1) {
                world.breakBlockAndSpawnDrop(x, y, z);
            }
        } else if (world.getBlock(x, y - 1, z) != getId() || world.getBlockState(x, y - 1, z) != 0) {
            world.breakBlockAndSpawnDrop(x, y, z);
        }
    }
}
