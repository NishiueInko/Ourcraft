package com.voxelgame.engine;

/** Danh mục diện mạo, mô hình dùng chung và các tấm ảnh pixel theo lớp. */
public enum MobAppearance {
    ZOMBIE(MobModels.ZOMBIE, "zombie", "zombie", 0x577259),
    ZOMBIE_VILLAGER(MobModels.ZOMBIE_VILLAGER, "zombie_villager", "zombie_villager", 0x6a7964),
    SKELETON(MobModels.SKELETON, "skeleton", "skeleton", 0xc8bda2),
    CREEPER(MobModels.CREEPER, "creeper", "creeper", 0x549078),
    SPIDER(MobModels.SPIDER, "spider", "spider", 0x584b58,
            null, new MobTextureKey("spider", "spider_eyes", 0xf6a75e)),
    ENDERMAN(MobModels.ENDERMAN, "enderman", "enderman", 0x342b48,
            null, new MobTextureKey("enderman", "enderman_eyes", 0x86e9db)),
    WITCH(MobModels.WITCH, "witch", "witch", 0x456877),
    COW(MobModels.COW, "cow", "temperate_cow", 0x735344),
    SHEEP(MobModels.SHEEP, "sheep", "sheep", 0xb39b88,
            new MobTextureKey("sheep", "sheep_fur", 0xe4cfb2), null),
    PIG(MobModels.PIG, "pig", "temperate_pig", 0xc7857b),
    CHICKEN(MobModels.CHICKEN, "chicken", "temperate_chicken", 0xe0c3a1),
    HORSE_WHITE(MobModels.HORSE, "horse", "horse_white", 0xe6ddcf),
    HORSE_CREAMY(MobModels.HORSE, "horse", "horse_creamy", 0xd7b67f),
    HORSE_CHESTNUT(MobModels.HORSE, "horse", "horse_chestnut", 0x9a4f36),
    HORSE_BROWN(MobModels.HORSE, "horse", "horse_brown", 0x805339),
    HORSE_BLACK(MobModels.HORSE, "horse", "horse_black", 0x32323b),
    HORSE_GRAY(MobModels.HORSE, "horse", "horse_gray", 0x8b8585),
    HORSE_DARKBROWN(MobModels.HORSE, "horse", "horse_darkbrown", 0x513a35),
    DONKEY(MobModels.DONKEY, "horse", "donkey", 0x877368),
    RABBIT_GOLD(MobModels.RABBIT, "rabbit", "gold", 0xcb9c54),
    CAMEL(MobModels.CAMEL, "camel", "camel", 0xbf925c),
    CAMEL_HUSK(MobModels.CAMEL, "camel", "camel_husk", 0x877254),
    HUSK(MobModels.HUSK, "zombie", "husk", 0xb39067),
    PARCHED(MobModels.PARCHED, "skeleton", "parched", 0xc0a46b),
    RABBIT_WHITE(MobModels.RABBIT, "rabbit", "white", 0xe5ded9),
    RABBIT_WHITE_SPLOTCHED(MobModels.RABBIT, "rabbit", "white_splotched", 0xe5ded9),
    GOAT(MobModels.GOAT, "goat", "goat", 0xb3a598),
    POLAR_BEAR(MobModels.POLAR_BEAR, "bear", "polarbear", 0xe2d9ca),
    WOLF_SNOWY(MobModels.WOLF, "wolf", "wolf_snowy", 0xc8ced0),
    WOLF_SNOWY_ANGRY(MobModels.WOLF, "wolf", "wolf_snowy_angry", 0xc8ced0),
    SNOW_FOX(MobModels.FOX, "fox", "snow_fox", 0xdfddd4),
    SNOW_FOX_SLEEP(MobModels.FOX, "fox", "snow_fox_sleep", 0xdfddd4),
    STRAY(MobModels.STRAY, "skeleton", "stray", 0xa2aab0,
            new MobTextureKey("skeleton", "stray_overlay", 0x678c9a), null),
    ARROW(MobModels.ARROW, "projectile", "arrow", 0xb7895a),
    POTION(MobModels.POTION, "projectile", "potion", 0x9f648e);

    /** Các lớp đốm ngựa được phối độc lập với bảy màu lông. */
    public enum HorseMarkings {
        NONE(null),
        WHITE(new MobTextureKey("horse", "horse_markings_white", 0xf1e9d7)),
        WHITE_FIELD(new MobTextureKey("horse", "horse_markings_whitefield", 0xf1e9d7)),
        WHITE_DOTS(new MobTextureKey("horse", "horse_markings_whitedots", 0xf1e9d7)),
        BLACK_DOTS(new MobTextureKey("horse", "horse_markings_blackdots", 0x342c2d));

        /** Khóa tấm ảnh của kiểu đốm đã chọn. */
        private final MobTextureKey texture;

        /** Đăng ký một kiểu đốm, hoặc trạng thái màu lông trơn. */
        HorseMarkings(MobTextureKey texture) {
            this.texture = texture;
        }

        /** Trả về lớp đốm, hoặc null khi ngựa có màu lông trơn. */
        public MobTextureKey texture() {
            return texture;
        }
    }

    /** Khung mô hình và thứ tự các lớp ảnh ứng với diện mạo. */
    private final MobModel model;
    private final MobTextureKey texture;
    private final MobTextureKey overlay;
    private final MobTextureKey eyes;

    /** Đăng ký diện mạo chỉ dùng lớp nền. */
    MobAppearance(MobModel model, String group, String name, int fallbackRgb) {
        this(model, group, name, fallbackRgb, null, null);
    }

    /** Đăng ký lớp nền cùng các lớp phủ và phát sáng tùy chọn. */
    MobAppearance(MobModel model, String group, String name, int fallbackRgb,
                  MobTextureKey overlay, MobTextureKey eyes) {
        this.model = model;
        this.texture = new MobTextureKey(group, name, fallbackRgb);
        this.overlay = overlay;
        this.eyes = eyes;
    }

    /** Trả về mô hình tĩnh được dùng chung giữa các cá thể cùng khung. */
    public MobModel model() {
        return model;
    }

    /** Giữ tỉ lệ dọc 1:1 theo texel chuẩn Minecraft, không kéo giãn làm méo pixel. */
    float heightScale() {
        return 1f;
    }

    /** Hệ số tỉ lệ đồng nhất cả 3 trục theo chuẩn Minecraft (Thỏ thu nhỏ 0,6 lần). */
    float modelScale() {
        return switch (this) {
            case RABBIT_GOLD, RABBIT_WHITE, RABBIT_WHITE_SPLOTCHED -> 0.6f;
            default -> 1f;
        };
    }

    /** Trả về khóa tấm ảnh nền. */
    public MobTextureKey texture() {
        return texture;
    }

    /** Trả về khóa lớp phủ cố định nếu diện mạo có sử dụng. */
    public MobTextureKey overlay() {
        return overlay;
    }

    /** Trả về khóa tấm ảnh mắt phát sáng nếu diện mạo có sử dụng. */
    public MobTextureKey eyes() {
        return eyes;
    }
}
