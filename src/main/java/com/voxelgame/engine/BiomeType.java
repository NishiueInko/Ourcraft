package com.voxelgame.engine;

/** Kiểu khí hậu quyết định địa tầng, thảm thực vật, thời tiết và hệ sinh thái của từng cột. */
public enum BiomeType {
    /** Đồng bằng lạnh ở rìa tây, có tuyết, hồ/sông đóng băng và cây vân sam. */
    SNOWY_PLAINS,
    /** Rừng sồi ôn hòa có mật độ cây dày và mưa. */
    FOREST,
    /** Đồng bằng ôn hòa thoáng đãng, có cỏ, hoa và mưa. */
    PLAINS,
    /** Rừng hoa ôn hòa rực rỡ nhiều loài hoa đan xen và mưa. */
    FLOWER_FOREST,
    /** Vùng nóng khô ở rìa đông, có cồn cát, xương rồng và bụi khô. */
    DESERT;

    /** Cho biết biome có xuất hiện mưa và dập lửa ngoài trời khi giáng thủy hay không. */
    public boolean hasRain() {
        return this == FOREST || this == PLAINS || this == FLOWER_FOREST;
    }

    /** Cho biết biome có tuyết rơi và tích tuyết, đóng băng nước mặt thoáng hay không. */
    public boolean hasSnow() {
        return this == SNOWY_PLAINS;
    }

    /** Cho biết biome thuộc khí hậu khô hạn, hoàn toàn không có giáng thủy. */
    public boolean isDry() {
        return this == DESERT;
    }
}
