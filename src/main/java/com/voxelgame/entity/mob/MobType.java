package com.voxelgame.entity.mob;

import com.voxelgame.engine.MobAppearance;

/** Danh mục 24 cấu hình. Lớp hành vi của từng loài cung cấp hàm tạo tương ứng. */
public enum MobType {
    ZOMBIE("zombie", MobCategory.MONSTER, MobAttitude.HOSTILE, .6f, 1.95f, 20, true, true, MobAppearance.ZOMBIE),
    ZOMBIE_VILLAGER("zombie_villager", MobCategory.MONSTER, MobAttitude.HOSTILE, .6f, 1.95f, 20, true, true,
            MobAppearance.ZOMBIE_VILLAGER),
    SKELETON("skeleton", MobCategory.MONSTER, MobAttitude.HOSTILE, .6f, 1.99f, 20, true, true, MobAppearance.SKELETON),
    CREEPER("creeper", MobCategory.MONSTER, MobAttitude.HOSTILE, .6f, 1.7f, 20, false, false, MobAppearance.CREEPER),
    SPIDER("spider", MobCategory.MONSTER, MobAttitude.NEUTRAL, 1.4f, .9f, 16, false, false, MobAppearance.SPIDER),
    ENDERMAN("enderman", MobCategory.MONSTER, MobAttitude.NEUTRAL, .6f, 2.9f, 40, false, false, MobAppearance.ENDERMAN),
    WITCH("witch", MobCategory.MONSTER, MobAttitude.HOSTILE, .6f, 1.95f, 26, false, false, MobAppearance.WITCH),
    COW("cow", MobCategory.CREATURE, MobAttitude.PASSIVE, .9f, 1.4f, 10, false, false, MobAppearance.COW),
    SHEEP("sheep", MobCategory.CREATURE, MobAttitude.PASSIVE, .9f, 1.3f, 8, false, false, MobAppearance.SHEEP),
    PIG("pig", MobCategory.CREATURE, MobAttitude.PASSIVE, .9f, .9f, 10, false, false, MobAppearance.PIG),
    CHICKEN("chicken", MobCategory.CREATURE, MobAttitude.PASSIVE, .4f, .7f, 4, false, false, MobAppearance.CHICKEN),
    HORSE("horse", MobCategory.CREATURE, MobAttitude.PASSIVE, 1.3965f, 1.6f, 20, false, false, MobAppearance.HORSE_BROWN),
    DONKEY("donkey", MobCategory.CREATURE, MobAttitude.PASSIVE, 1.3965f, 1.5f, 20, false, false, MobAppearance.DONKEY),
    RABBIT("rabbit", MobCategory.CREATURE, MobAttitude.PASSIVE, .4f, .5f, 3, false, false, MobAppearance.RABBIT_GOLD),
    CAMEL("camel", MobCategory.CREATURE, MobAttitude.PASSIVE, 1.7f, 2.38f, 32, false, false, MobAppearance.CAMEL),
    CAMEL_HUSK("camel_husk", MobCategory.MONSTER, MobAttitude.HOSTILE, 1.7f, 2.38f, 32, true, false,
            MobAppearance.CAMEL_HUSK),
    HUSK("husk", MobCategory.MONSTER, MobAttitude.HOSTILE, .6f, 1.95f, 20, true, false, MobAppearance.HUSK),
    PARCHED("parched", MobCategory.MONSTER, MobAttitude.HOSTILE, .6f, 1.99f, 16, true, false, MobAppearance.PARCHED),
    SNOW_RABBIT("rabbit", MobCategory.CREATURE, MobAttitude.PASSIVE, .4f, .5f, 3, false, false, MobAppearance.RABBIT_WHITE),
    GOAT("goat", MobCategory.CREATURE, MobAttitude.NEUTRAL, .9f, 1.3f, 10, false, false, MobAppearance.GOAT),
    POLAR_BEAR("polar_bear", MobCategory.CREATURE, MobAttitude.NEUTRAL, 1.4f, 1.4f, 30, false, false,
            MobAppearance.POLAR_BEAR),
    WOLF("wolf", MobCategory.CREATURE, MobAttitude.NEUTRAL, .6f, .85f, 8, false, false, MobAppearance.WOLF_SNOWY),
    SNOW_FOX("fox", MobCategory.CREATURE, MobAttitude.NEUTRAL, .6f, .7f, 10, false, false, MobAppearance.SNOW_FOX),
    STRAY("stray", MobCategory.MONSTER, MobAttitude.HOSTILE, .6f, 1.99f, 20, true, true, MobAppearance.STRAY);

    /** Chữ ký thống nhất cho hàm dựng cá thể tại vị trí chân. */
    @FunctionalInterface
    public interface Factory {
        MobEntity create(float x, float y, float z);
    }

    /** Lưu mảng danh mục một lần để kiểm tra nhanh các nhóm đã được lắp hành vi. */
    private static final MobType[] TYPES = values();
    private final String id;
    private final MobCategory category;
    private final MobAttitude attitude;
    private final float width;
    private final float height;
    private final float health;
    private final boolean undead;
    private final boolean burnsInSunlight;
    private final MobAppearance appearance;
    private Factory factory;

    static {
        ZOMBIE.registerFactory(Zombie::new);
        ZOMBIE_VILLAGER.registerFactory(ZombieVillager::new);
        SKELETON.registerFactory(Skeleton::new);
        CREEPER.registerFactory(Creeper::new);
        SPIDER.registerFactory(Spider::new);
        ENDERMAN.registerFactory(Enderman::new);
        WITCH.registerFactory(Witch::new);
        HUSK.registerFactory(Husk::new);
        PARCHED.registerFactory(Parched::new);
        STRAY.registerFactory(Stray::new);
        COW.registerFactory(Cow::new);
        SHEEP.registerFactory(Sheep::new);
        PIG.registerFactory(Pig::new);
        CHICKEN.registerFactory(Chicken::new);
        RABBIT.registerFactory(Rabbit::new);
        HORSE.registerFactory(Horse::new);
        DONKEY.registerFactory(Donkey::new);
        CAMEL.registerFactory(Camel::new);
        CAMEL_HUSK.registerFactory(CamelHusk::new);
        SNOW_RABBIT.registerFactory(Rabbit::snow);
        GOAT.registerFactory(Goat::new);
        POLAR_BEAR.registerFactory(PolarBear::new);
        WOLF.registerFactory(Wolf::new);
        SNOW_FOX.registerFactory(SnowFox::new);
    }

    /** Ghi khóa âm thanh, kích thước người lớn và các thuộc tính nền. */
    MobType(String id, MobCategory category, MobAttitude attitude, float width, float height, float health,
            boolean undead, boolean burnsInSunlight, MobAppearance appearance) {
        this.id = id;
        this.category = category;
        this.attitude = attitude;
        this.width = width;
        this.height = height;
        this.health = health;
        this.undead = undead;
        this.burnsInSunlight = burnsInSunlight;
        this.appearance = appearance;
    }

    /** Đăng ký khi khởi tạo game trên luồng chính và trả hàm tạo trước đó. */
    public Factory registerFactory(Factory factory) {
        Factory previous = this.factory;
        this.factory = factory;
        return previous;
    }

    /** Kiểm tra cấu hình này đã có triển khai loài hay chưa. */
    public boolean implemented() {
        return factory != null;
    }

    /** Kiểm tra có thể chạy lượt sinh cho nhóm này hay chưa. */
    public static boolean anyImplemented(MobCategory category) {
        for (MobType type : TYPES) {
            if (type.category == category && type.implemented()) {
                return true;
            }
        }
        return false;
    }

    /** Dựng cá thể bằng hàm tạo đã đăng ký. Trả null nếu loài còn chờ triển khai. */
    public MobEntity create(float x, float y, float z) {
        return factory == null ? null : factory.create(x, y, z);
    }

    /** Trả khóa âm thanh dùng chung cho các biến thể cùng loài. */
    public String id() { return id; }
    /** Trả nhóm sinh tự nhiên. */
    public MobCategory category() { return category; }
    /** Trả thái độ ban đầu. */
    public MobAttitude attitude() { return attitude; }
    /** Trả bề rộng toàn bộ hộp va chạm, theo block. */
    public float width() { return width; }
    /** Trả chiều cao toàn bộ hộp va chạm, theo block. */
    public float height() { return height; }
    /** Trả máu tối đa ban đầu, tính bằng HP. */
    public float health() { return health; }
    /** Trả cờ xác sống cho tương tác hiệu ứng. */
    public boolean undead() { return undead; }
    /** Trả khả năng bốc cháy khi tiếp xúc nắng. */
    public boolean burnsInSunlight() { return burnsInSunlight; }
    /** Trả diện mạo mặc định, trước khi áp dụng biến thể cá thể. */
    public MobAppearance appearance() { return appearance; }
}
