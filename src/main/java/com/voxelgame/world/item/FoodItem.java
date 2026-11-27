package com.voxelgame.world.item;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.World;

/**
 * Vật phẩm thức ăn đóng gói trực tiếp {@link FoodRegistry.FoodSpec} (nguồn chân lý duy nhất cho 19 món ăn).
 */
public final class FoodItem extends ConsumableItem {

    /** Thông số dinh dưỡng và hiệu ứng phụ bất biến của món ăn. */
    private final FoodRegistry.FoodSpec foodSpec;

    /** Khởi tạo món ăn kèm thông số dinh dưỡng và hiệu ứng phụ chuẩn Minecraft. */
    FoodItem(int id, String displayName, int maxStackSize, String textureName,
             int atlasTileIndex, int fallbackRgb, FoodRegistry.FoodSpec foodSpec) {
        super(id, displayName, maxStackSize, textureName, atlasTileIndex, fallbackRgb);
        this.foodSpec = foodSpec;
    }

    /** Trả về thông số dinh dưỡng và hiệu ứng phụ của món ăn. */
    public FoodRegistry.FoodSpec getFoodSpec() {
        return foodSpec;
    }

    @Override
    public UseAction getUseAction() {
        return UseAction.EAT;
    }

    /** Chỉ cho phép bắt đầu ăn khi thanh đói chưa đầy. */
    @Override
    public boolean canStartContinuousUse(World world, Player player) {
        return player != null && player.getHunger() < player.getMaxHunger();
    }

    @Override
    public AudioManager.Effect getConsumeTickSound() {
        return AudioManager.Effect.EAT;
    }

    @Override
    public AudioManager.Effect getConsumeFinishSound() {
        return AudioManager.Effect.BURP;
    }

    /** Áp dụng hồi đói, độ bão hòa và hiệu ứng phụ (nếu có), sau đó trừ 1 đơn vị trên Hotbar. */
    @Override
    protected void applyConsumeEffect(World world, Player player) {
        if (player == null) {
            return;
        }
        Hotbar hotbar = player.getHotbar();
        if (player.consumeFood(this) && hotbar != null) {
            hotbar.consumeSelectedItemStack();
        }
    }
}
