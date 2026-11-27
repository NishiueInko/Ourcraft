package com.voxelgame.world.item;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.Camera;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.entity.projectile.ArrowEntity;
import com.voxelgame.world.entity.projectile.ArrowType;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.block.Block;

import com.badlogic.gdx.math.Vector3;

/**
 * Vũ khí tầm xa Cung ({@link Item#BOW}): kéo dây tối đa 1,0 giây, giảm tốc 0,4×, thu hẹp FOV -8°,
 * tự động tìm mũi tên đầu tiên trên Hotbar và bắn {@link ArrowEntity} khi nhả chuột phải.
 */
public final class BowItem extends Item {

    /** Vận tốc và sát thương theo mức kéo dây cung. */
    private static final float MIN_SPEED = 12.0f;
    private static final float MAX_SPEED = 42.0f;
    private static final float MIN_DAMAGE = 2.0f;
    private static final float MAX_DAMAGE = 9.0f;

    /** Khởi tạo vật phẩm Cung. */
    BowItem(int id, String displayName, int maxStackSize, String textureName,
            int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, Block.AIR, textureName, atlasTileIndex, false, fallbackRgb);
    }

    @Override
    public UseAction getUseAction() {
        return UseAction.BOW;
    }

    @Override
    public float getUseDurationSeconds() {
        return ItemUseHandler.BOW_FULL_DRAW_SECONDS;
    }

    /** Chỉ cho phép bắt đầu kéo dây khi người chơi có ít nhất một mũi tên hợp lệ trên Hotbar. */
    @Override
    public boolean canStartContinuousUse(World world, Player player) {
        return player != null && findFirstArrowSlot(player.getHotbar()) >= 0;
    }

    /** Khi nhả chuột phải sau tối thiểu 0,15 giây kéo dây, tiêu thụ 1 mũi tên và bắn ra thế giới. */
    @Override
    public void onReleaseUse(World world, Player player, Camera camera, float useElapsed, ItemUseHandler handler) {
        if (world == null || player == null || !player.isAlive() || useElapsed < ItemUseHandler.BOW_MIN_DRAW_SECONDS) {
            return;
        }
        Hotbar hotbar = player.getHotbar();
        if (hotbar == null || hotbar.getSelectedItem() != this || hotbar.getSelectedCount() <= 0) {
            return;
        }
        int arrowSlot = findFirstArrowSlot(hotbar);
        if (arrowSlot < 0) {
            return;
        }
        Item arrowItem = hotbar.getSlotItem(arrowSlot);
        ArrowType arrowType = toArrowType(arrowItem);
        if (arrowType == null) {
            return;
        }
        float pull = Math.min(1.0f, useElapsed / ItemUseHandler.BOW_FULL_DRAW_SECONDS);
        float speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * pull;
        float damage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * pull;
        hotbar.setSlot(arrowSlot, arrowItem, hotbar.getSlotCount(arrowSlot) - 1);

        Vector3 eye = handler.aimEye(player, camera);
        Vector3 dir = handler.aimDirection();
        ArrowEntity arrow = new ArrowEntity(
                player,
                eye.x,
                eye.y - ItemUseHandler.EYE_SPAWN_DROP,
                eye.z,
                dir.x * speed,
                dir.y * speed,
                dir.z * speed,
                arrowType,
                damage
        );
        world.addEntity(arrow);
        handler.playEffect(world, AudioManager.Effect.BOW_SHOOT, 0.60f);
    }

    /** Tìm ô Hotbar đầu tiên từ trái sang phải có chứa mũi tên hợp lệ. */
    static int findFirstArrowSlot(Hotbar hotbar) {
        if (hotbar == null) {
            return -1;
        }
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            if (hotbar.getSlotCount(i) > 0 && toArrowType(hotbar.getSlotItem(i)) != null) {
                return i;
            }
        }
        return -1;
    }

    /** Ánh xạ vật phẩm mũi tên sang {@link ArrowType} tương ứng, hoặc {@code null} nếu không phải mũi tên. */
    private static ArrowType toArrowType(Item item) {
        if (item == Item.ARROW) {
            return ArrowType.NORMAL;
        }
        if (item == Item.TIPPED_ARROW_SLOWNESS) {
            return ArrowType.SLOWNESS;
        }
        if (item == Item.TIPPED_ARROW_WEAKNESS) {
            return ArrowType.WEAKNESS;
        }
        return null;
    }
}
