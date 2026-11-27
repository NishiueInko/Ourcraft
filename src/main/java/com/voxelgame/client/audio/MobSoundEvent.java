package com.voxelgame.client.audio;

import java.util.Locale;

/** Sự kiện dùng chung giữa hành vi mob và hệ thống phát âm thanh. */
public enum MobSoundEvent {
    AMBIENT, HURT, DEATH, STEP,
    CREEPER_PRIMED, EXPLODE(32f), BOW_SHOOT,
    ENDERMAN_STARE, ENDERMAN_TELEPORT, ENDERMAN_SCREAM(32f),
    WITCH_THROW, WITCH_DRINK, GOAT_RAM, GOAT_SCREAM(28f),
    WOLF_GROWL, CAMEL_DASH, HUSK_CONVERT, SPEAR_CHARGE;

    /** Khoảng nghe của các hiệu ứng thông thường, tính bằng block. */
    private static final float NORMAL_RANGE = 16f;
    /** Tên tiền tố cố định khi đóng gói tệp WAV và khoảng nghe mặc định. */
    private final String assetName;
    private final float defaultRange;

    /** Tạo sự kiện có khoảng nghe thông thường. */
    MobSoundEvent() {
        this(NORMAL_RANGE);
    }

    /** Tạo sự kiện có khoảng nghe riêng và chuẩn hóa tên tệp khi khởi tạo enum. */
    MobSoundEvent(float defaultRange) {
        this.defaultRange = defaultRange;
        assetName = name().toLowerCase(Locale.ROOT);
    }

    /** Trả về tiền tố tệp dùng cho tất cả biến thể của sự kiện. */
    public String assetName() {
        return assetName;
    }

    /** Các loài dùng mẫu bước của khối dưới chân theo Java 26.3. */
    public static boolean usesBlockSteps(String mobId) {
        return switch (mobId) {
            case "creeper", "enderman", "fox", "witch" -> true;
            default -> false;
        };
    }

    /** Trả về khoảng nghe nếu bên gọi không chỉ định bán kính dương. */
    public float defaultRange() {
        return defaultRange;
    }
}
