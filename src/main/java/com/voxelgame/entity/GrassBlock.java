package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;
import java.util.Random;

/**
 * Khối Cỏ (ID = 2): Thể hiện rõ nét Tính Đa hình (Polymorphism) trong OOP:
 * 1. Đa hình hiển thị (`getTileIndex`): Mặt trên là cỏ xanh (`TILE_GRASS_TOP`), mặt đáy là đất (`TILE_DIRT`),
 *    4 mặt xung quanh là viền cỏ + đất (`TILE_GRASS_SIDE`).
 * 2. Đa hình hành vi (`onBreak`): Khi bị đập vỡ bằng tay không, khối cỏ sẽ làm rơi ra khối Đất (`Block.DIRT`)
 *    đúng theo cơ chế chuẩn của Minecraft gốc.
 */
public class GrassBlock extends Block {

    public GrassBlock() {
        super(
                (byte) 2,
                "Grass Block",
                0.75f,
                true,
                false,
                true,
                new float[]{0.32f, 0.68f, 0.22f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return switch (face) {
            case TOP -> Texture.TILE_GRASS_TOP;
            case BOTTOM -> Texture.TILE_DIRT;
            case NORTH, SOUTH, EAST, WEST -> Texture.TILE_GRASS_SIDE;
        };
    }

    @Override
    /** Chọn mặt phủ tuyết theo block bên trên. */
    public int getTileIndex(Face face, int state, Block above) {
        if (above == SNOW_LAYER || above == SNOW_BLOCK) {
            return switch (face) {
                case TOP -> Texture.TILE_SNOW;
                case BOTTOM -> Texture.TILE_DIRT;
                default -> Texture.TILE_GRASS_SNOW_SIDE;
            };
        }
        return getTileIndex(face);
    }

    @Override
    /** Xử lý ngay khi ô phía trên thay đổi; random tick cũng kiểm tra địa hình được sinh sẵn. */
    public void onNeighborChange(World world, int x, int y, int z) {
        if (!canSurvive(world, x, y, z)) {
            world.setBlock(x, y, z, DIRT.getId());
        }
    }

    /** Cỏ tàn dưới lớp phủ chặn sáng và gieo tối đa bốn điểm khi nguồn có nắng. */
    public void randomTick(World world, int x, int y, int z, Random random) {
        if (!canSurvive(world, x, y, z)) {
            world.setBlock(x, y, z, DIRT.getId());
            return;
        }
        if (world.worldTime().sunY() <= 0.05f || world.hasWeatherRoof(x, y + 2f, z)) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            int targetX = x + random.nextInt(3) - 1;
            int targetY = y + random.nextInt(5) - 3;
            int targetZ = z + random.nextInt(3) - 1;
            if (world.getBlock(targetX, targetY, targetZ) == DIRT.getId()
                    && canSurvive(world, targetX, targetY, targetZ)
                    && !world.hasWeatherRoof(targetX, targetY + 2f, targetZ)) {
                world.setBlock(targetX, targetY, targetZ, GRASS.getId());
            }
        }
    }

    /** Cây thấp và một lớp tuyết cho ánh sáng qua. Chất lỏng và lớp phủ dày làm cỏ tàn. */
    private static boolean canSurvive(World world, int x, int y, int z) {
        Block above = fromId(world.getBlock(x, y + 1, z));
        if (above == SNOW_LAYER) {
            return world.getBlockState(x, y + 1, z) == 0;
        }
        return !above.isLiquid() && !(above.isOpaque() && above.getGeometryType() == GeometryType.FULL_CUBE);
    }

    @Override
    public void onBreak(World world, int x, int y, int z) {
        if (world != null) {
            // Đập vỡ khối cỏ bằng tay không sẽ rơi ra khối Đất (Block.DIRT)
            world.spawnDroppedItem(new ItemEntity(x + 0.5f, y + 0.5f, z + 0.5f, Block.DIRT));
        }
    }
}
