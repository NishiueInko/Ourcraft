package com.voxelgame.world.gamemode;

import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.block.Block;

/**
 * Chế độ Sinh tồn ({@code Survival}):
 * - Khóa hoàn toàn chế độ bay ({@code Space} ×2), phím tốc độ địa hình ({@code F1}) và ghé biome ({@code Ctrl + C}).
 * - Đầy đủ cơ chế sinh tồn: 20 HP, 20 đói, bão hòa, Oxy dưới nước, sát thương rơi/lửa/dung nham/xương rồng/mob.
 * - Đào khối theo độ cứng (không phá được {@link Block#BEDROCK}) và rơi vật phẩm; đặt/dùng vật phẩm tiêu hao stack.
 * - Hiển thị đầy đủ thanh máu, đùi thịt và Oxy trên HUD; phím {@code E} mở túi đồ 36 ô sinh tồn.
 */
public final class SurvivalGameMode extends GameMode {

    /** Thể hiện Flyweight dùng chung duy nhất (zero-GC). */
    public static final SurvivalGameMode INSTANCE = new SurvivalGameMode();

    private SurvivalGameMode() {
        super("survival", "SURVIVAL");
    }

    @Override
    public boolean canFly() {
        return false;
    }

    @Override
    public boolean canUseTerrainTools() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return false;
    }

    @Override
    public boolean consumesItems() {
        return true;
    }

    @Override
    public boolean breaksInstantly() {
        return false;
    }

    @Override
    public boolean canBreakBlock(Block block) {
        return block != null && block.isBreakable();
    }

    @Override
    public boolean dropsBlockItems() {
        return true;
    }

    @Override
    public boolean showsSurvivalHud() {
        return true;
    }

    @Override
    public boolean isTargetableByMobs() {
        return true;
    }

    @Override
    public boolean usesCreativeInventory() {
        return false;
    }

    @Override
    public void onEnter(Player player) {
        if (player != null) {
            player.setFlying(false);
            player.resetTerrainSpeed();
        }
    }

    @Override
    public GameMode toggle() {
        return CreativeGameMode.INSTANCE;
    }
}
