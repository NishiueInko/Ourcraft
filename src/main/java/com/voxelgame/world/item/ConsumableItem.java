package com.voxelgame.world.item;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.block.Block;

/**
 * Lớp trừu tượng áp dụng mẫu Template Method cho nhóm vật phẩm tiêu thụ bằng cách giữ chuột phải 1,6 giây
 * (gồm {@link FoodItem} và {@link PotionItem}).
 */
public abstract class ConsumableItem extends Item {

    /** Khởi tạo vật phẩm tiêu thụ độc lập. */
    protected ConsumableItem(int id, String displayName, int maxStackSize, String textureName,
                             int atlasTileIndex, int fallbackRgb) {
        super(id, displayName, maxStackSize, Block.AIR, textureName, atlasTileIndex, false, fallbackRgb);
    }

    /** Thời lượng giữ chuột phải tiêu chuẩn để ăn hoặc uống xong (1,6 giây). */
    @Override
    public float getUseDurationSeconds() {
        return ItemUseHandler.CONSUME_DURATION_SECONDS;
    }

    /** Âm thanh lặp định kỳ mỗi 0,25 giây trong lúc đang ăn hoặc uống. */
    public abstract AudioManager.Effect getConsumeTickSound();

    /** Âm thanh phát ra khi hoàn tất tiêu thụ vật phẩm. */
    public abstract AudioManager.Effect getConsumeFinishSound();

    /** Âm lượng của âm thanh hoàn tất tiêu thụ. */
    public float getConsumeFinishGain() {
        return 0.45f;
    }

    /**
     * Template Method hoàn tất quá trình tiêu thụ: áp dụng tác dụng cụ thể của lớp con
     * rồi phát âm thanh kết thúc tương ứng.
     */
    @Override
    public final void onConsumeComplete(World world, Player player) {
        applyConsumeEffect(world, player);
        AudioManager audio = world != null ? world.getAudio() : null;
        if (audio != null) {
            audio.playEffect(getConsumeFinishSound(), getConsumeFinishGain());
        }
    }

    /** Hook cho lớp con thực thi hiệu ứng hồi đói, hồi máu, áp trạng thái và hoàn trả vật chứa. */
    protected abstract void applyConsumeEffect(World world, Player player);
}
