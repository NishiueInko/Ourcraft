package com.voxelgame.entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Bảng tra cứu bất biến cho toàn bộ 19 loại thức ăn chuẩn Minecraft 1.21.11.
 * Dùng {@link IdentityHashMap} theo tham chiếu Flyweight {@link Item} để tra cứu O(1), zero-GC.
 */
public final class FoodRegistry {

    /**
     * Thông số dinh dưỡng và hiệu ứng phụ có thể kích hoạt khi ăn xong một món ăn.
     *
     * @param nutrition       điểm đói hồi phục (1..20)
     * @param saturation      điểm bão hòa cộng thêm (bị chặn tối đa bằng hunger sau khi ăn)
     * @param effect          hiệu ứng trạng thái phụ (null nếu không có)
     * @param durationSeconds thời lượng hiệu ứng phụ tính bằng giây
     * @param amplifier       cấp hiệu ứng phụ (0 = cấp I)
     * @param effectChance    xác suất kích hoạt hiệu ứng phụ trong [0, 1]
     */
    public record FoodSpec(
            int nutrition,
            float saturation,
            StatusEffectType effect,
            float durationSeconds,
            int amplifier,
            float effectChance
    ) {
    }

    /** Bảng ánh xạ tĩnh từ Item sang FoodSpec, khởi tạo một lần duy nhất khi nạp lớp. */
    private static final Map<Item, FoodSpec> FOODS;

    static {
        IdentityHashMap<Item, FoodSpec> map = new IdentityHashMap<>(24);
        // Nhóm thịt sống và thịt chín
        register(map, Item.BEEF, 3, 1.8f);
        register(map, Item.COOKED_BEEF, 8, 12.8f);
        register(map, Item.PORKCHOP, 3, 1.8f);
        register(map, Item.COOKED_PORKCHOP, 8, 12.8f);
        register(map, Item.MUTTON, 2, 1.2f);
        register(map, Item.COOKED_MUTTON, 6, 9.6f);
        register(map, Item.CHICKEN, 2, 1.2f, StatusEffectType.HUNGER, 30.0f, 0, 0.30f);
        register(map, Item.COOKED_CHICKEN, 6, 7.2f);
        register(map, Item.RABBIT, 3, 1.8f);
        register(map, Item.COOKED_RABBIT, 5, 6.0f);

        // Nhóm cá sống và cá chín
        register(map, Item.COD, 2, 0.4f);
        register(map, Item.COOKED_COD, 5, 6.0f);
        register(map, Item.SALMON, 2, 0.4f);
        register(map, Item.COOKED_SALMON, 6, 9.6f);

        // Nhóm nông sản và món gây hiệu ứng bất lợi
        register(map, Item.CARROT, 3, 3.6f);
        register(map, Item.POTATO, 1, 0.6f);
        register(map, Item.BAKED_POTATO, 5, 6.0f);
        register(map, Item.ROTTEN_FLESH, 4, 0.8f, StatusEffectType.HUNGER, 30.0f, 0, 0.80f);
        register(map, Item.SPIDER_EYE, 2, 3.2f, StatusEffectType.POISON, 4.0f, 0, 1.0f);

        FOODS = Collections.unmodifiableMap(map);
    }

    /** Ngăn khởi tạo thể hiện của lớp tiện ích tĩnh. */
    private FoodRegistry() {
    }

    /** Đăng ký món ăn thuần túy không có hiệu ứng phụ. */
    private static void register(Map<Item, FoodSpec> map, Item item, int nutrition, float saturation) {
        map.put(item, new FoodSpec(nutrition, saturation, null, 0.0f, 0, 0.0f));
    }

    /** Đăng ký món ăn kèm hiệu ứng phụ theo xác suất. */
    private static void register(Map<Item, FoodSpec> map, Item item, int nutrition, float saturation,
                                 StatusEffectType effect, float durationSeconds, int amplifier, float effectChance) {
        map.put(item, new FoodSpec(nutrition, saturation, effect, durationSeconds, amplifier, effectChance));
    }

    /**
     * Tra cứu thông số thức ăn của một {@link Item}.
     *
     * @param item vật phẩm cần kiểm tra
     * @return {@link FoodSpec} tương ứng, hoặc {@code null} nếu không thuộc 19 món ăn
     */
    public static FoodSpec of(Item item) {
        if (item == null) {
            return null;
        }
        return FOODS.get(item);
    }

    /** Kiểm tra nhanh một vật phẩm có nằm trong 19 món ăn được hỗ trợ hay không. */
    public static boolean isFood(Item item) {
        return of(item) != null;
    }

    /** Trả về tổng số món ăn đã đăng ký trong bảng. */
    public static int count() {
        return FOODS.size();
    }
}
