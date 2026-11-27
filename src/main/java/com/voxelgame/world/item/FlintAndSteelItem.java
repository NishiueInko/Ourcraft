package com.voxelgame.world.item;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.World;

/**
 * Công cụ Bật lửa ({@link Item#FLINT_AND_STEEL}): ghi đè hành vi Lượt 1 {@link #onInteractMob}
 * để đốt cháy bất kỳ {@link MobEntity} nào trong 8 giây trước các tương tác đặc thù của từng loài.
 */
public final class FlintAndSteelItem extends ToolItem {

    /** Thời lượng đốt cháy mob khi châm lửa bằng Flint and Steel (giây). */
    public static final float IGNITE_DURATION_SECONDS = 8.0f;

    /** Khởi tạo vật phẩm Bật lửa. */
    FlintAndSteelItem(int id, String displayName, int maxStackSize, String textureName,
                      int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, textureName, atlasTileIndex, fallbackRgb);
    }

    /** Lượt 1 điều phối tương tác mob: đốt cháy mọi mob còn sống trong 8 giây và phát âm thanh đánh lửa. */
    @Override
    public boolean onInteractMob(World world, Player player, MobEntity target) {
        if (target == null || !target.isAlive() || player == null || !player.isAlive()) {
            return false;
        }
        target.ignite(IGNITE_DURATION_SECONDS);
        AudioManager audio = world != null ? world.getAudio() : null;
        if (audio != null) {
            audio.playEffect(AudioManager.Effect.CLICK, 0.45f);
        }
        return true;
    }
}
