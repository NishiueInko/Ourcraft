package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Tuyết có tám độ dày và các hộp cục bộ được dùng lại. */
final class SnowLayerBlock extends Block {
    /** Hộp nhìn thấy theo từng độ dày, khai báo sau khi tạo singleton. */
    private static final Bounds[] LAYER_BOUNDS = createLayerBounds();

    /** Khởi tạo một loại block dùng chung cho mọi độ dày. */
    SnowLayerBlock() {
        super((byte) 21, "Snow Layer", 0.25f, true, false, false, new float[]{0.94f, 0.96f, 1.0f});
    }

    /** Dựng trước hộp của tám trạng thái. */
    private static Bounds[] createLayerBounds() {
        Bounds[] bounds = new Bounds[8];
        for (int state = 0; state < bounds.length; state++) {
            bounds[state] = new Bounds(0, 0, 0, 1, (state + 1) * 0.125f, 1);
        }
        return bounds;
    }

    /** Cả mặt trên và mặt cạnh dùng texture tuyết. */
    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_SNOW;
    }

    /** Hình học thay đổi theo số lớp. */
    @Override
    public GeometryType getGeometryType() {
        return GeometryType.SNOW_LAYER;
    }

    /** Metadata 0 là một lớp, metadata 7 là tám lớp. */
    @Override
    public int normalizeState(int state) {
        return Math.max(0, Math.min(7, state));
    }

    /** Hộp chọn phủ đúng hình ảnh. */
    @Override
    public Bounds getSelectionBounds(int state) {
        return LAYER_BOUNDS[normalizeState(state)];
    }

    /** Lớp mỏng nhất đi xuyên được; tám lớp cản như khối đầy. */
    @Override
    public Bounds getCollisionBounds(int state) {
        int layer = normalizeState(state);
        return layer == 0 ? null : layer == 7 ? FULL_BOUNDS : LAYER_BOUNDS[layer - 1];
    }

    /** Chỉ lớp mỏng nhất có thể bị ghi đè. */
    @Override
    public boolean isReplaceable(int state) {
        return state == 0;
    }

    /** Lá vân sam và các khối rắn hình lập phương đều đỡ được tuyết. */
    @Override
    public boolean canPlaceAt(World world, int x, int y, int z) {
        Block below = fromId(world.getBlock(x, y - 1, z));
        return below.getGeometryType() == GeometryType.FULL_CUBE && below.isSolid();
    }
}
