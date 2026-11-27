package com.voxelgame.world.level.block;

import com.voxelgame.world.level.World;

/**
 * Lớp trừu tượng trung gian cho các khối chất lỏng ({@code Water}, {@code Lava}).
 * Gom chung cấu hình vật lý ({@code solid = false}, {@code liquid = true}, {@code opaque = false}),
 * chuẩn hóa mức dòng chảy {@code 0..7}, hạ mặt thoáng {@code 0.88f} khi phía trên là không khí
 * và ngăn rơi vật phẩm khi đào tay.
 */
public abstract class LiquidBlock extends Block {

    /** Chỉ số ô texture đại diện cho chất lỏng trên Texture Atlas. */
    private final int tileIndex;

    /**
     * Khởi tạo khối chất lỏng không thể phá hủy bằng tay không.
     *
     * @param id          mã định danh block
     * @param displayName tên hiển thị
     * @param tileIndex   chỉ số tile trong Texture Atlas
     * @param iconColor   màu RGB đại diện
     */
    protected LiquidBlock(byte id, String displayName, int tileIndex, float[] iconColor) {
        super(id, displayName, -1.0f, false, true, false, iconColor);
        this.tileIndex = tileIndex;
    }

    /** Dùng chung một tile hoạt ảnh cho cả 6 mặt chất lỏng. */
    @Override
    public int getTileIndex(Face face) {
        return tileIndex;
    }

    /** Chất lỏng không thể bị đập vỡ thành vật phẩm bằng tay không. */
    @Override
    public void onBreak(World world, int x, int y, int z) {
        // Không rơi vật phẩm khi tương tác đào trên chất lỏng
    }

    /** Giới hạn mức dòng chảy hợp lệ trong khoảng 0 (nguồn) đến 7 (mỏng nhất). */
    @Override
    public int normalizeState(int state) {
        return Math.max(0, Math.min(7, state));
    }

    /** Hạ mặt thoáng xuống 0.88f cho ô nguồn và giảm dần theo mức chảy khi phía trên là không khí. */
    @Override
    public float getTopHeight(World world, int wx, int wy, int wz) {
        if (world != null && world.getBlock(wx, wy + 1, wz) == Block.AIR.getId()) {
            int level = normalizeState(world.getBlockState(wx, wy, wz));
            if (level == 0) {
                return 0.88f;
            }
            return Math.max(0.125f, 0.88f - level * 0.105f);
        }
        return 1.0f;
    }
}
