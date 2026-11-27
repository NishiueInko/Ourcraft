package com.voxelgame.entity;

import com.voxelgame.engine.Texture;

/**
 * Khối Len Cừu (ID 27..32): Hỗ trợ 6 màu len (trắng, xám nhạt, xám, đen, nâu, hồng),
 * liên kết trực tiếp với {@link Item#WHITE_WOOL}..{@link Item#PINK_WOOL} để đồng bộ đặt khối và nhặt đồ.
 */
public final class WoolBlock extends Block {

    /** Độ cứng chuẩn của khối len khi đào tay (0.8 giây). */
    private static final float HARDNESS = 0.8f;

    /** Chỉ số ô chất liệu của khối len trong Texture Atlas 8x6. */
    private final int tileIndex;

    /**
     * Khởi tạo một biến thể màu của khối len cừu.
     *
     * @param id          mã định danh block (27..32)
     * @param displayName tên hiển thị trên giao diện
     * @param tileIndex   chỉ số ô trong {@link Texture}
     * @param iconColor   màu RGB đại diện
     */
    WoolBlock(int id, String displayName, int tileIndex, float[] iconColor) {
        super((byte) id, displayName, HARDNESS, true, false, true, iconColor);
        this.tileIndex = tileIndex;
    }

    /** Trả về cùng một mặt len cho cả 6 hướng khối lập phương. */
    @Override
    public int getTileIndex(Face face) {
        return tileIndex;
    }

    /** Len cừu luôn kết xuất ở lớp đục (Opaque). */
    @Override
    public RenderLayer getRenderLayer() {
        return RenderLayer.OPAQUE;
    }

    /** Trả về vật phẩm len tương ứng khi phá hủy hoặc truy vấn vật phẩm rơi. */
    public Item getDropItem() {
        return Item.of(this);
    }
}
