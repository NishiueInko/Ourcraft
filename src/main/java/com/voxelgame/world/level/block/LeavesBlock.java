package com.voxelgame.world.level.block;

import com.voxelgame.world.level.LeafDecaySystem;

/**
 * Lớp trừu tượng trung gian cho các khối tán lá ({@code Oak Leaves}, {@code Spruce Leaves}).
 * Gom chung cấu hình không che khuất hoàn toàn ({@code opaque = false}), kết xuất ở lớp
 * {@link RenderLayer#CUTOUT} và lưu bit metadata {@link LeafDecaySystem#PERSISTENT_LEAF_STATE}
 * để phân biệt lá do người chơi đặt với lá tự nhiên có thể tự phân hủy.
 */
public abstract class LeavesBlock extends Block {

    /** Chỉ số tile lá cây trong Texture Atlas. */
    private final int tileIndex;

    /**
     * Khởi tạo khối tán lá.
     *
     * @param id          mã định danh block
     * @param displayName tên hiển thị trên giao diện
     * @param hardness    độ cứng khi đào tay (giây)
     * @param tileIndex   chỉ số tile trong Texture Atlas
     * @param iconColor   màu RGB đại diện
     */
    protected LeavesBlock(byte id, String displayName, float hardness, int tileIndex, float[] iconColor) {
        super(id, displayName, hardness, true, false, false, iconColor);
        this.tileIndex = tileIndex;
    }

    /** Dùng chung một tile tán lá cho cả 6 mặt. */
    @Override
    public int getTileIndex(Face face) {
        return tileIndex;
    }

    /** Lá cây kết xuất ở lớp CUTOUT để giữ các khoảng hở trong suốt giữa các cành. */
    @Override
    public RenderLayer getRenderLayer() {
        return RenderLayer.CUTOUT;
    }

    /** Giữ lại bit 0 đánh dấu lá do người chơi tự đặt ({@link LeafDecaySystem#PERSISTENT_LEAF_STATE}). */
    @Override
    public int normalizeState(int state) {
        return state & LeafDecaySystem.PERSISTENT_LEAF_STATE;
    }
}
