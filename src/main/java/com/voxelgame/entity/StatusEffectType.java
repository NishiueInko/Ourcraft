package com.voxelgame.entity;

/** Các hiệu ứng sinh tồn và chiến đấu, theo thứ tự chỉ số trong mảng hiệu ứng. */
public enum StatusEffectType {
    HUNGER, WEAKNESS, SLOWNESS, POISON;

    /** Đếm loại hiệu ứng một lần khi nạp lớp để cấp phát các khe cố định. */
    public static final int COUNT = values().length;

    /** Trả loại hiệu ứng theo chỉ số để duyệt mà không sao chép mảng enum. */
    public static StatusEffectType at(int index) {
        return switch (index) {
            case 0 -> HUNGER;
            case 1 -> WEAKNESS;
            case 2 -> SLOWNESS;
            case 3 -> POISON;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }
}
