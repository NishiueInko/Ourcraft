package com.voxelgame.entity.mob;

import com.voxelgame.entity.Item;

/** Lợn đồng bằng dùng goal lang thang, chạy hoảng, đi theo cà rốt/khoai tây và mẫu tiếng ủn ỉn của loài. */
public final class Pig extends MobEntity {
    /** Bán kính phát hiện người chơi cầm mồi và hệ số tốc độ khi đi theo. */
    private static final float TEMPT_RANGE = 10f;
    private static final float TEMPT_SPEED = 1.0f;

    /** Áp dụng tốc độ của lợn cùng cấu hình hiền lành trong danh mục và goal dụ bằng Carrot/Potato. */
    public Pig(float x, float y, float z) {
        super(MobType.PIG, x, y, z);
        setMoveSpeed(2.5f);
        goals().add(new MobGoals.TemptFollowGoal(5, Pig::isTemptItem, TEMPT_RANGE, TEMPT_SPEED));
    }

    /** Kiểm tra vật phẩm có phải mồi dụ hợp lệ của lợn ({@link Item#CARROT} hoặc {@link Item#POTATO}). */
    private static boolean isTemptItem(Item item) {
        return item == Item.CARROT || item == Item.POTATO;
    }
}
