package com.voxelgame.world.item;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.math.Vector3;

/**
 * Vật phẩm đồ uống gồm 4 lọ thuốc Witch và {@link Item#MILK_BUCKET}.
 * Tự xác định hiệu ứng áp dụng và vật chứa hoàn trả ({@link Item#GLASS_BOTTLE} hoặc {@link Item#BUCKET}).
 */
public final class PotionItem extends ConsumableItem {

    /** Cờ phân biệt xô sữa (xóa toàn bộ hiệu ứng trạng thái) với thuốc Witch. */
    private final boolean clearsEffects;
    /** Vật chứa rỗng được hoàn trả sau khi uống xong. */
    private final Item returnContainer;

    /** Khởi tạo vật phẩm đồ uống với vật chứa hoàn trả tương ứng. */
    PotionItem(int id, String displayName, int maxStackSize, String textureName,
               int atlasTileIndex, int fallbackRgb, boolean clearsEffects, Item returnContainer) {
        super(id, displayName, maxStackSize, textureName, atlasTileIndex, fallbackRgb);
        this.clearsEffects = clearsEffects;
        this.returnContainer = returnContainer;
    }

    /** Trả về vật chứa rỗng được hoàn trả sau khi uống xong. */
    public Item getReturnContainer() {
        return returnContainer;
    }

    @Override
    public UseAction getUseAction() {
        return UseAction.DRINK;
    }

    @Override
    public AudioManager.Effect getConsumeTickSound() {
        return AudioManager.Effect.DRINK;
    }

    @Override
    public AudioManager.Effect getConsumeFinishSound() {
        return AudioManager.Effect.DRINK;
    }

    @Override
    public float getConsumeFinishGain() {
        return 0.50f;
    }

    /** Thực thi hiệu ứng uống thuốc hoặc xóa hiệu ứng của xô sữa và hoàn trả vật chứa rỗng. */
    @Override
    protected void applyConsumeEffect(World world, Player player) {
        if (player == null) {
            return;
        }
        Hotbar hotbar = player.getHotbar();
        if (clearsEffects) {
            player.clearStatusEffects();
            if (hotbar != null) {
                int selectedSlot = hotbar.getSelectedSlot();
                hotbar.consumeSelectedItemStack();
                if (returnContainer != null) {
                    if (hotbar.getSlotCount(selectedSlot) == 0) {
                        hotbar.setSlot(selectedSlot, returnContainer, 1);
                    } else if (!hotbar.addItem(returnContainer, 1) && world != null) {
                        Vector3 pos = player.getPosition();
                        world.spawnDroppedItem(new ItemEntity(pos.x, pos.y + 1.0f, pos.z, returnContainer));
                    }
                }
            }
            return;
        }
        player.consumePotion(this);
    }
}
