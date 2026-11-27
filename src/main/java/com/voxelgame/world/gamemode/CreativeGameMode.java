package com.voxelgame.world.gamemode;

import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.block.Block;

/**
 * Chế độ Sáng tạo ({@code Creative}):
 * - Bay tự do bằng {@code Space} ×2, sử dụng đầy đủ {@code F1} (tốc độ địa hình) và {@code Ctrl + C} (ghé biome).
 * - Bất tử hoàn toàn (không mất máu, đói, Oxy; dập lửa tức thì; ẩn thanh sinh tồn trên HUD).
 * - Mob thù địch và trung lập bỏ qua người chơi.
 * - Phá khối tức thì mọi khối rắn/thực vật kể cả {@link Block#BEDROCK} mà không rơi {@code ItemEntity}.
 * - Đặt khối, bắn cung, ném đạn và dùng vật phẩm không tiêu hao số lượng.
 * - Phím {@code E} mở Kho Creative chứa toàn bộ khối và vật phẩm trong trò chơi để minh họa.
 */
public final class CreativeGameMode extends GameMode {

    /** Thể hiện Flyweight dùng chung duy nhất (zero-GC). */
    public static final CreativeGameMode INSTANCE = new CreativeGameMode();

    private CreativeGameMode() {
        super("creative", "CREATIVE");
    }

    @Override
    public boolean canFly() {
        return true;
    }

    @Override
    public boolean canUseTerrainTools() {
        return true;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean consumesItems() {
        return false;
    }

    @Override
    public boolean breaksInstantly() {
        return true;
    }

    @Override
    public boolean canBreakBlock(Block block) {
        return block != null && block != Block.AIR && !block.isLiquid();
    }

    @Override
    public boolean dropsBlockItems() {
        return false;
    }

    @Override
    public boolean showsSurvivalHud() {
        return false;
    }

    @Override
    public boolean isTargetableByMobs() {
        return false;
    }

    @Override
    public boolean usesCreativeInventory() {
        return true;
    }

    @Override
    public void onEnter(Player player) {
        if (player != null) {
            player.restoreCreativeVitals();
        }
    }

    @Override
    public GameMode toggle() {
        return SurvivalGameMode.INSTANCE;
    }
}
