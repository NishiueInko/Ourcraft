package com.voxelgame.world.item;

import com.voxelgame.world.effect.StatusEffectType;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Bảng tra cứu bất biến cho toàn bộ 19 loại thức ăn chuẩn Minecraft 1.21.11.
 * Dữ liệu được đồng bộ tự động từ chính các thực thể {@link FoodItem} đăng ký trong {@link Item}
 * (nguồn chân lý duy nhất, zero-GC khi tra cứu O(1)).
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

    /** Bảng ánh xạ tĩnh từ Item sang FoodSpec, tự động thu thập từ các {@link FoodItem} trong {@link Item}. */
    private static final Map<Item, FoodSpec> FOODS;

    static {
        IdentityHashMap<Item, FoodSpec> map = new IdentityHashMap<>(24);
        for (Item item : Item.allStandaloneItems()) {
            if (item instanceof FoodItem foodItem) {
                map.put(foodItem, foodItem.getFoodSpec());
            }
        }
        FOODS = Collections.unmodifiableMap(map);
    }

    /** Ngăn khởi tạo thể hiện của lớp tiện ích tĩnh. */
    private FoodRegistry() {
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
        if (item instanceof FoodItem foodItem) {
            return foodItem.getFoodSpec();
        }
        return FOODS.get(item);
    }

    /** Kiểm tra nhanh một vật phẩm có nằm trong 19 món ăn được hỗ trợ hay không. */
    public static boolean isFood(Item item) {
        return item instanceof FoodItem;
    }

    /** Trả về tổng số món ăn đã đăng ký trong bảng. */
    public static int count() {
        return FOODS.size();
    }
}
