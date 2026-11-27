package com.voxelgame.world.entity.mob;

import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.mob.animal.Sheep;
import com.voxelgame.world.entity.mob.monster.Enderman;
import com.voxelgame.world.entity.mob.monster.Husk;
import com.voxelgame.world.entity.mob.monster.Skeleton;
import com.voxelgame.world.entity.mob.monster.Witch;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Bảng rơi vật phẩm chuẩn Minecraft 1.21.11 cho toàn bộ 23 loài mob khi chết,
 * hỗ trợ đầy đủ thịt chín khi cháy, điều kiện người chơi giết, đĩa nhạc từ Skeleton bắn Creeper,
 * thuốc phù thủy đang uống và khối đang cầm của Enderman.
 */
public final class MobLootTable {

    /** Sáu vật phẩm rơi ngẫu nhiên của Witch chuẩn Java 1.21+ (Redstone rơi riêng cố định 4–8). */
    private static final Item[] WITCH_DROPS = {
            Item.GLASS_BOTTLE,
            Item.GLOWSTONE_DUST,
            Item.GUNPOWDER,
            Item.SPIDER_EYE,
            Item.SUGAR,
            Item.STICK
    };

    /** Ba vật phẩm hiếm khi tiêu diệt Zombie, Zombie Villager hoặc Husk (2,5%). */
    private static final Item[] ZOMBIE_RARE_DROPS = {
            Item.IRON_INGOT,
            Item.CARROT,
            Item.POTATO
    };

    private MobLootTable() {
    }

    /**
     * Tính toán và sinh toàn bộ vật phẩm rơi ra tại vị trí mob chết.
     *
     * @param world thế giới voxel
     * @param mob cá thể vừa chết
     * @param onFire true nếu mob đang cháy hoặc chết bởi lửa/dung nham
     * @param killer thực thể gây ra đòn kết liễu (có thể là null)
     */
    public static void dropDeathLoot(World world, MobEntity mob, boolean onFire, Entity killer) {
        if (world == null || mob == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean killedByPlayer = killer instanceof Player;

        // Enderman vẫn rơi lại khối đang cầm dù ở trạng thái nào
        if (mob instanceof Enderman enderman) {
            Block carried = enderman.takeHeldBlockForLoot();
            if (carried != null && carried != Block.AIR) {
                spawnDrops(world, mob, Item.of(carried), 1);
            }
        }

        // Cá thể non trong Minecraft không rơi loot tiêu chuẩn khi chết
        if (mob.isBaby()) {
            return;
        }

        switch (mob.type()) {
            case COW -> {
                spawnDrops(world, mob, Item.LEATHER, random.nextInt(3));
                spawnDrops(world, mob, onFire ? Item.COOKED_BEEF : Item.BEEF, random.nextInt(1, 4));
            }
            case SHEEP -> {
                if (mob instanceof Sheep sheep && !sheep.isSheared()) {
                    spawnDrops(world, mob, sheep.woolColor().woolItem(), 1);
                }
                spawnDrops(world, mob, onFire ? Item.COOKED_MUTTON : Item.MUTTON, random.nextInt(1, 3));
            }
            case PIG -> spawnDrops(world, mob, onFire ? Item.COOKED_PORKCHOP : Item.PORKCHOP, random.nextInt(1, 4));
            case CHICKEN -> {
                spawnDrops(world, mob, Item.FEATHER, random.nextInt(3));
                spawnDrops(world, mob, onFire ? Item.COOKED_CHICKEN : Item.CHICKEN, 1);
            }
            case RABBIT, SNOW_RABBIT -> {
                spawnDrops(world, mob, Item.RABBIT_HIDE, random.nextInt(2));
                spawnDrops(world, mob, onFire ? Item.COOKED_RABBIT : Item.RABBIT, random.nextInt(2));
                if (killedByPlayer && random.nextFloat() < 0.10f) {
                    spawnDrops(world, mob, Item.RABBIT_FOOT, 1);
                }
            }
            case POLAR_BEAR -> {
                if (random.nextFloat() < 0.75f) {
                    spawnDrops(world, mob, onFire ? Item.COOKED_COD : Item.COD, random.nextInt(3));
                } else {
                    spawnDrops(world, mob, onFire ? Item.COOKED_SALMON : Item.SALMON, random.nextInt(3));
                }
            }
            case HORSE, DONKEY -> spawnDrops(world, mob, Item.LEATHER, random.nextInt(3));
            case CAMEL_HUSK -> spawnDrops(world, mob, Item.ROTTEN_FLESH, random.nextInt(2, 4));
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK -> {
                spawnDrops(world, mob, Item.ROTTEN_FLESH, random.nextInt(3));
                if (killedByPlayer && random.nextFloat() < 0.025f) {
                    Item rare = ZOMBIE_RARE_DROPS[random.nextInt(ZOMBIE_RARE_DROPS.length)];
                    if (rare == Item.POTATO && onFire) {
                        rare = Item.BAKED_POTATO;
                    }
                    spawnDrops(world, mob, rare, 1);
                }
                if (mob instanceof Husk husk && husk.hasIronSpear() && killedByPlayer && random.nextFloat() < 0.085f) {
                    spawnDrops(world, mob, Item.IRON_SPEAR, 1);
                }
            }
            case SKELETON -> {
                spawnDrops(world, mob, Item.BONE, random.nextInt(3));
                spawnDrops(world, mob, Item.ARROW, random.nextInt(3));
                if (killedByPlayer && random.nextFloat() < 0.085f) {
                    spawnDrops(world, mob, Item.BOW, 1);
                }
            }
            case STRAY -> {
                spawnDrops(world, mob, Item.BONE, random.nextInt(3));
                spawnDrops(world, mob, Item.ARROW, random.nextInt(3));
                if (killedByPlayer && random.nextBoolean()) {
                    spawnDrops(world, mob, Item.TIPPED_ARROW_SLOWNESS, 1);
                }
                if (killedByPlayer && random.nextFloat() < 0.085f) {
                    spawnDrops(world, mob, Item.BOW, 1);
                }
            }
            case PARCHED -> {
                spawnDrops(world, mob, Item.BONE, random.nextInt(3));
                spawnDrops(world, mob, Item.ARROW, random.nextInt(3));
                if (killedByPlayer && random.nextBoolean()) {
                    spawnDrops(world, mob, Item.TIPPED_ARROW_WEAKNESS, 1);
                }
                if (killedByPlayer && random.nextFloat() < 0.085f) {
                    spawnDrops(world, mob, Item.BOW, 1);
                }
            }
            case CREEPER -> {
                spawnDrops(world, mob, Item.GUNPOWDER, random.nextInt(3));
                // Bất kỳ cung thủ họ Skeleton (Skeleton, Stray, Parched) bắn chết Creeper sẽ rơi đĩa nhạc
                if (killer instanceof Skeleton) {
                    Item disc = random.nextBoolean() ? Item.MUSIC_DISC_13 : Item.MUSIC_DISC_CAT;
                    spawnDrops(world, mob, disc, 1);
                }
            }
            case SPIDER -> {
                spawnDrops(world, mob, Item.STRING, random.nextInt(3));
                if (killedByPlayer && random.nextInt(3) == 0) {
                    spawnDrops(world, mob, Item.SPIDER_EYE, 1);
                }
            }
            case ENDERMAN -> spawnDrops(world, mob, Item.ENDER_PEARL, random.nextInt(2));
            case WITCH -> {
                // Chuẩn Minecraft Java 1.21+: luôn rơi 4–8 Redstone Dust cộng 1–3 lượt × 0–2 từ 6 món còn lại.
                spawnDrops(world, mob, Item.REDSTONE, random.nextInt(4, 9));
                int rolls = random.nextInt(1, 4);
                for (int i = 0; i < rolls; i++) {
                    Item chosen = WITCH_DROPS[random.nextInt(WITCH_DROPS.length)];
                    spawnDrops(world, mob, chosen, random.nextInt(3));
                }
                if (killedByPlayer && mob instanceof Witch witch) {
                    Item drinkingPotion = witch.drinkingPotionItem();
                    if (drinkingPotion != null && random.nextFloat() < 0.085f) {
                        spawnDrops(world, mob, drinkingPotion, 1);
                    }
                }
            }
            case GOAT, WOLF, SNOW_FOX, CAMEL -> {
                // Goat chỉ rơi sừng khi húc và sữa khi vắt; SnowFox tự thả vật ngậm trong finishLivingUpdate; Wolf & Camel không rơi khi chết.
            }
        }
    }

    /** Sinh từng ItemEntity riêng lẻ để tương thích với giới hạn 1 item/thực thể nhặt của World. */
    private static void spawnDrops(World world, MobEntity mob, Item item, int count) {
        if (item == null || item.isNone() || count <= 0) {
            return;
        }
        float dropY = mob.y() + Math.min(0.45f, mob.getHeight() * 0.4f);
        for (int i = 0; i < count; i++) {
            world.spawnDroppedItem(new ItemEntity(mob.x(), dropY, mob.z(), item));
        }
    }
}
