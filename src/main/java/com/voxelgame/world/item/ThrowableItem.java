package com.voxelgame.world.item;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.Camera;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.entity.projectile.ProjectileEntity;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.block.Block;

import com.badlogic.gdx.math.Vector3;

/**
 * Vật phẩm ném tức thời tạo đạn đạo ({@link Item#ENDER_PEARL}, {@link Item#EGG})
 * sử dụng mẫu Factory để tách logic tạo {@link ProjectileEntity} khỏi {@link ItemUseHandler}.
 */
public final class ThrowableItem extends Item {

    /** Vận tốc ném chuẩn của ngọc Ender và trứng gà (block/giây). */
    private static final float THROW_SPEED = 22.0f;

    /** Hợp đồng Factory tạo thực thể đạn đạo tương ứng khi người chơi ném vật phẩm. */
    @FunctionalInterface
    public interface ProjectileFactory {
        ProjectileEntity create(World world, LivingEntity owner,
                                float x, float y, float z,
                                float vx, float vy, float vz);
    }

    /** Factory bất biến tạo đạn đạo cho từng loại vật phẩm ném. */
    private final ProjectileFactory projectileFactory;

    /** Khởi tạo vật phẩm ném kèm Factory tạo thực thể đạn đạo tương ứng. */
    ThrowableItem(int id, String displayName, int maxStackSize, String textureName,
                  int atlasTileIndex, int fallbackRgb, ProjectileFactory projectileFactory) {
        super(id, displayName, maxStackSize, Block.AIR, textureName, atlasTileIndex, false, fallbackRgb);
        this.projectileFactory = projectileFactory;
    }

    /** Tiêu thụ 1 vật phẩm, tạo đạn đạo theo tia ngắm của người chơi và phát âm thanh ném. */
    @Override
    public boolean onInstantUse(World world, Player player, Camera camera, ItemUseHandler handler) {
        if (world == null || player == null || !player.isAlive()) {
            return false;
        }
        Hotbar hotbar = player.getHotbar();
        if (hotbar == null || hotbar.getSelectedCount() <= 0) {
            return false;
        }
        if (player.getGameMode().consumesItems()) {
            hotbar.consumeSelectedItemStack();
        }
        Vector3 eye = handler.aimEye(player, camera);
        Vector3 dir = handler.aimDirection();
        ProjectileEntity projectile = projectileFactory.create(
                world,
                player,
                eye.x,
                eye.y - ItemUseHandler.EYE_SPAWN_DROP,
                eye.z,
                dir.x * THROW_SPEED,
                dir.y * THROW_SPEED,
                dir.z * THROW_SPEED
        );
        world.addEntity(projectile);
        handler.playEffect(world, AudioManager.Effect.THROW, 0.50f);
        return true;
    }
}
