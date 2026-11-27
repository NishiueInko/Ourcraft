package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.item.Item;

/**
 * Khối Len Cừu (ID 27..32): Hỗ trợ 6 màu len (trắng, xám nhạt, xám, đen, nâu, hồng),
 * liên kết trực tiếp với {@link Item#WHITE_WOOL}..{@link Item#PINK_WOOL} để đồng bộ đặt khối và nhặt đồ.
 */
public final class WoolBlock extends SolidBlock {

    /** Độ cứng chuẩn của khối len khi đào tay (0.8 giây). */
    private static final float HARDNESS = 0.8f;

    /**
     * Khởi tạo một biến thể màu của khối len cừu.
     *
     * @param id          mã định danh block (27..32)
     * @param displayName tên hiển thị trên giao diện
     * @param tileIndex   chỉ số ô trong {@link Texture}
     * @param iconColor   màu RGB đại diện
     */
    WoolBlock(int id, String displayName, int tileIndex, float[] iconColor) {
        super((byte) id, displayName, HARDNESS, tileIndex, iconColor);
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
