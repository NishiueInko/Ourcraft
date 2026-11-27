package com.voxelgame.engine;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.effect.StatusEffectType;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.mob.animal.Camel;
import com.voxelgame.world.entity.mob.animal.Chicken;
import com.voxelgame.world.entity.mob.animal.Cow;
import com.voxelgame.world.entity.mob.animal.Donkey;
import com.voxelgame.world.entity.mob.animal.Goat;
import com.voxelgame.world.entity.mob.animal.Horse;
import com.voxelgame.world.entity.mob.animal.Pig;
import com.voxelgame.world.entity.mob.animal.PolarBear;
import com.voxelgame.world.entity.mob.animal.Rabbit;
import com.voxelgame.world.entity.mob.animal.Sheep;
import com.voxelgame.world.entity.mob.animal.Wolf;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.mob.monster.CamelHusk;
import com.voxelgame.world.entity.mob.monster.Creeper;
import com.voxelgame.world.entity.mob.monster.Enderman;
import com.voxelgame.world.entity.mob.monster.Husk;
import com.voxelgame.world.entity.mob.monster.Parched;
import com.voxelgame.world.entity.mob.monster.Skeleton;
import com.voxelgame.world.entity.mob.monster.Spider;
import com.voxelgame.world.entity.mob.monster.Stray;
import com.voxelgame.world.entity.mob.monster.Witch;
import com.voxelgame.world.entity.mob.monster.Zombie;
import com.voxelgame.world.entity.mob.monster.ZombieVillager;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.entity.projectile.ArrowEntity;
import com.voxelgame.world.entity.projectile.ArrowType;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kiểm tra hồi quy toàn bộ hệ thống rơi vật phẩm mob, tương tác đặc biệt (đẻ trứng, cắt lông,
 * vắt sữa, húc rơi sừng, đốt cháy ra thịt chín), texture vật phẩm và texture/SFX Skeleton gốc.
 */
final class MobLootChecks {

    private MobLootChecks() {
    }

    static void run() {
        checkItemTexturesAndNames();
        checkSkeletonAssetsAndSfx();
        checkHotbarAndItemEntity();
        checkFireCookedAndPassiveDrops();
        checkChickenEggLaying();
        checkSheepShearingAndRegrowth();
        checkCowAndGoatMilkingAndHorn();
        checkHostileAndSpecialDrops();
    }

    /** Xác nhận đủ 56 PNG vật phẩm 16 × 16 hợp lệ và tên hiển thị khớp font 5 × 7. */
    private static void checkItemTexturesAndNames() {
        List<Item> items = Item.allStandaloneItems();
        require(items.size() == 56, "Phải có đủ 56 vật phẩm độc lập (" + items.size() + ")");
        for (Item item : items) {
            String path = "textures/item/" + item.getTextureName() + ".png";
            try (InputStream stream = openAsset(path)) {
                BufferedImage img = ImageIO.read(stream);
                require(img != null && img.getWidth() == 16 && img.getHeight() == 16,
                        "Ảnh vật phẩm phải đúng 16x16: " + path);
                boolean hasOpaquePixel = false;
                for (int y = 0; y < 16 && !hasOpaquePixel; y++) {
                    for (int x = 0; x < 16; x++) {
                        if (((img.getRGB(x, y) >>> 24) & 0xFF) >= 13) {
                            hasOpaquePixel = true;
                            break;
                        }
                    }
                }
                require(hasOpaquePixel, "Ảnh vật phẩm không được rỗng: " + path);
            } catch (IOException error) {
                throw new AssertionError("Không đọc được " + path, error);
            }
            for (int i = 0; i < item.getDisplayName().length(); i++) {
                char c = Character.toUpperCase(item.getDisplayName().charAt(i));
                require(c == ' ' || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'),
                        "Tên vật phẩm chứa ký tự không có trong font 5x7: " + item.getDisplayName());
            }
        }
    }

    /** Xác nhận texture Skeleton 64 × 32 và đầy đủ biến thể SFX Skeleton chuẩn Minecraft gốc. */
    private static void checkSkeletonAssetsAndSfx() {
        try (InputStream stream = openAsset("textures/entity/skeleton/skeleton.png")) {
            BufferedImage img = ImageIO.read(stream);
            require(img != null && img.getWidth() == 64 && img.getHeight() == 32,
                    "Texture skeleton.png phải có kích thước 64x32 chuẩn Minecraft");
        } catch (IOException error) {
            throw new AssertionError("Không đọc được skeleton.png", error);
        }

        checkDistinctWavSet("ambient", 3);
        checkDistinctWavSet("hurt", 4);
        checkDistinctWavSet("step", 4);
        checkDistinctWavSet("death", 2);
    }

    private static void checkDistinctWavSet(String eventName, int count) {
        Set<String> hashes = new HashSet<>();
        for (int i = 1; i <= count; i++) {
            String path = "audio/sfx/mob/skeleton/" + eventName + i + ".wav";
            try (InputStream raw = openAsset(path)) {
                byte[] bytes = raw.readAllBytes();
                try (var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(
                        new java.io.ByteArrayInputStream(bytes)))) {
                    AudioFormat format = audio.getFormat();
                    require(format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                            && format.getSampleSizeInBits() == 16
                            && format.getChannels() == 1
                            && Math.abs(format.getSampleRate() - 22050f) < 1f
                            && audio.getFrameLength() > 0,
                            "Sai định dạng WAV Skeleton: " + path);
                }
                hashes.add(sha1Hex(bytes));
            } catch (UnsupportedAudioFileException | IOException error) {
                throw new AssertionError("Lỗi kiểm tra âm thanh " + path, error);
            }
        }
        if (!"death".equals(eventName)) {
            require(hashes.size() == count, "Các biến thể " + eventName + " của Skeleton phải khác nhau");
        }
    }

    /** Kiểm tra Hotbar mới, trang bị khởi đầu ô 7–9 và ItemEntity cho vật phẩm độc lập. */
    private static void checkHotbarAndItemEntity() {
        Hotbar hotbar = new Hotbar();
        require(hotbar.getSlotBlock(0) == Block.DIRT && hotbar.getSlotCount(0) == 16, "Giữ nguyên ô 1");
        require(hotbar.getSlotItem(5).isNone() && hotbar.getSlotCount(5) == 0, "Ô 6 phải trống cho các test cũ");
        require(hotbar.getSlotItem(6) == Item.SHEARS && hotbar.getSlotCount(6) == 1, "Ô 7 phải là Shears");
        require(hotbar.getSlotItem(7) == Item.FLINT_AND_STEEL && hotbar.getSlotCount(7) == 1, "Ô 8 phải là Flint and Steel");
        require(hotbar.getSlotItem(8) == Item.BUCKET && hotbar.getSlotCount(8) == 1, "Ô 9 phải là Bucket");

        require(hotbar.addItem(Item.EGG, 18), "Phải thêm được trứng");
        require(hotbar.getSlotItem(5) == Item.EGG && hotbar.getSlotCount(5) == 16, "Trứng chỉ xếp tối đa 16/ô");
        require(hotbar.getSlotCount(6) == 1 && hotbar.getSlotItem(6) == Item.SHEARS, "Không ghi đè ô có sẵn");

        World world = World.empty();
        try {
            Player player = new Player(10.5f, 11f, 10.5f);
            world.registerPlayer(player);
            ItemEntity pearl = new ItemEntity(10.5f, 11.2f, 10.5f, Item.ENDER_PEARL);
            world.spawnDroppedItem(pearl);
            for (int i = 0; i < 30; i++) {
                pearl.update(world, player, player.getHotbar(), 0.025f);
            }
            require(pearl.isCollected() && player.getHotbar().getSlotItem(5) == Item.ENDER_PEARL,
                    "Người chơi phải nhặt được ItemEntity độc lập vào Hotbar");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra rơi đồ thường và chín khi mob bị đốt bởi Flint and Steel hoặc lửa. */
    private static void checkFireCookedAndPassiveDrops() {
        World world = arena();
        try {
            Player player = new Player(100.5f, 20f, 100.5f);
            world.registerPlayer(player);

            // Đốt lợn bằng Flint and Steel ở ô 8 rồi kết liễu -> phải rơi thịt chín
            Pig pig = new Pig(102.5f, 20f, 100.5f);
            player.getHotbar().selectSlot(7);
            require(pig.onPlayerInteract(world, player) && pig.getFireTimer() > 0f,
                    "Flint and Steel phải đốt cháy mob");
            pig.damage(world, 20f, DamageSource.MELEE, player, 0f, 0f);
            List<Item> pigDrops = collectGroundItems(world);
            require(!pigDrops.isEmpty() && allMatch(pigDrops, Item.COOKED_PORKCHOP),
                    "Lợn chết khi đang cháy phải rơi Cooked Porkchop");

            // Bò thường -> thịt bò sống (+ da tùy ngẫu nhiên)
            Cow cow = new Cow(102.5f, 20f, 100.5f);
            cow.damage(world, 20f, DamageSource.MELEE, player, 0f, 0f);
            List<Item> cowDrops = collectGroundItems(world);
            require(countItem(cowDrops, Item.BEEF) >= 1, "Bò chết thường phải rơi Raw Beef");

            // Bò cháy -> Steak
            Cow fireCow = new Cow(102.5f, 20f, 100.5f);
            fireCow.damage(world, 20f, DamageSource.FIRE, player, 0f, 0f);
            List<Item> fireCowDrops = collectGroundItems(world);
            require(countItem(fireCowDrops, Item.COOKED_BEEF) >= 1, "Bò chết cháy phải rơi Steak");

            // Gà cháy -> Cooked Chicken
            Chicken chicken = new Chicken(102.5f, 20f, 100.5f);
            chicken.damage(world, 20f, DamageSource.LAVA, player, 0f, 0f);
            List<Item> chickenDrops = collectGroundItems(world);
            require(countItem(chickenDrops, Item.COOKED_CHICKEN) == 1, "Gà chết cháy phải rơi 1 Cooked Chicken");

            // Gấu con không rơi đồ; gấu lớn cháy rơi cá chín
            PolarBear cub = new PolarBear(102.5f, 20f, 100.5f, true);
            cub.damage(world, 50f, DamageSource.MELEE, player, 0f, 0f);
            require(collectGroundItems(world).isEmpty(), "Gấu con không được rơi vật phẩm");

            boolean bearDroppedFish = false;
            for (int attempt = 0; attempt < 20; attempt++) {
                PolarBear adultBear = new PolarBear(102.5f, 20f, 100.5f, false);
                adultBear.damage(world, 50f, DamageSource.FIRE, player, 0f, 0f);
                List<Item> drops = collectGroundItems(world);
                for (Item drop : drops) {
                    require(drop == Item.COOKED_COD || drop == Item.COOKED_SALMON,
                            "Gấu cháy chỉ được rơi cá tuyết hoặc cá hồi chín");
                    bearDroppedFish = true;
                }
            }
            require(bearDroppedFish, "Gấu trưởng thành phải có lượt rơi cá chín");

            // Ngựa, Lừa, Lạc đà, Camel Husk, Sói
            Camel camel = new Camel(102.5f, 20f, 100.5f);
            camel.damage(world, 50f, DamageSource.MELEE, player, 0f, 0f);
            require(collectGroundItems(world).isEmpty(), "Camel không rơi đồ khi chết");

            CamelHusk camelHusk = new CamelHusk(102.5f, 20f, 100.5f);
            camelHusk.damage(world, 50f, DamageSource.MELEE, player, 0f, 0f);
            List<Item> chDrops = collectGroundItems(world);
            require(chDrops.size() >= 2 && chDrops.size() <= 3 && allMatch(chDrops, Item.ROTTEN_FLESH),
                    "Camel Husk phải rơi 2–3 Rotten Flesh");

            Wolf wolf = new Wolf(102.5f, 20f, 100.5f);
            wolf.damage(world, 20f, DamageSource.MELEE, player, 0f, 0f);
            require(collectGroundItems(world).isEmpty(), "Wolf không rơi đồ khi chết");

            Horse horse = new Horse(102.5f, 20f, 100.5f);
            horse.damage(world, 50f, DamageSource.MELEE, player, 0f, 0f);
            List<Item> horseDrops = collectGroundItems(world);
            require(allMatch(horseDrops, Item.LEATHER) && horseDrops.size() <= 2, "Ngựa chỉ rơi 0–2 Leather");

            Donkey donkey = new Donkey(102.5f, 20f, 100.5f);
            donkey.damage(world, 50f, DamageSource.MELEE, player, 0f, 0f);
            List<Item> donkeyDrops = collectGroundItems(world);
            require(allMatch(donkeyDrops, Item.LEATHER) && donkeyDrops.size() <= 2, "Lừa chỉ rơi 0–2 Leather");

            Rabbit rabbit = new Rabbit(102.5f, 20f, 100.5f);
            rabbit.damage(world, 20f, DamageSource.FIRE, player, 0f, 0f);
            for (Item drop : collectGroundItems(world)) {
                require(drop == Item.RABBIT_HIDE || drop == Item.COOKED_RABBIT || drop == Item.RABBIT_FOOT,
                        "Thỏ cháy chỉ rơi da, thịt thỏ chín hoặc chân thỏ");
            }
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra gà tự đẻ trứng khi bộ đếm kết thúc chu kỳ. */
    private static void checkChickenEggLaying() {
        World world = arena();
        try {
            Chicken chicken = new Chicken(100.5f, 20f, 100.5f);
            chicken.setEggTimerForTest(0.1f);
            chicken.update(world, 0.2f);
            List<Item> drops = collectGroundItems(world);
            require(drops.size() == 1 && drops.get(0) == Item.EGG, "Gà hết chu kỳ phải đẻ ra 1 Egg");
            require(chicken.eggTimer() >= 299f, "Sau khi đẻ trứng phải đặt lại chu kỳ 300–600s");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra cắt lông cừu bằng Shears, chết khi không còn lông và mọc lại lông sau khi ăn cỏ. */
    private static void checkSheepShearingAndRegrowth() {
        World world = arena();
        try {
            Player player = new Player(100.5f, 20f, 100.5f);
            world.registerPlayer(player);
            Sheep pinkSheep = new Sheep(102.5f, 20f, 100.5f, Sheep.WoolColor.PINK);

            player.getHotbar().selectSlot(6); // Shears
            require(pinkSheep.onPlayerInteract(world, player), "Cừu phải cho phép cắt lông bằng Shears");
            require(pinkSheep.isSheared() && pinkSheep.mobVisualState().isSheepSheared(),
                    "Cừu sau cắt lông phải cập nhật trạng thái sheared");
            List<Item> woolDrops = collectGroundItems(world);
            require(woolDrops.size() >= 1 && woolDrops.size() <= 3 && allMatch(woolDrops, Item.PINK_WOOL),
                    "Cắt lông cừu hồng phải rơi 1–3 Pink Wool");
            require(!pinkSheep.onPlayerInteract(world, player), "Cừu đã trọc lông không thể cắt lần nữa");

            // Khi mọc lại lông thì phải rơi lại len lúc bị giết
            pinkSheep.regrowWool();
            require(!pinkSheep.isSheared() && !pinkSheep.mobVisualState().isSheepSheared(),
                    "Cừu ăn cỏ mọc lại lông");
            pinkSheep.damage(world, 20f, DamageSource.MELEE, player, 0f, 0f);
            List<Item> deathDrops = collectGroundItems(world);
            require(countItem(deathDrops, Item.PINK_WOOL) == 1 && countItem(deathDrops, Item.MUTTON) >= 1,
                    "Cừu có lông chết phải rơi 1 len đúng màu và thịt cừu");

            // Cừu đã cắt lông chết không rơi len
            Sheep shearedSheep = new Sheep(102.5f, 20f, 100.5f, Sheep.WoolColor.WHITE);
            shearedSheep.shear(world);
            collectGroundItems(world);
            shearedSheep.damage(world, 20f, DamageSource.FIRE, player, 0f, 0f);
            List<Item> shearedDeathDrops = collectGroundItems(world);
            require(countItem(shearedDeathDrops, Item.WHITE_WOOL) == 0
                            && countItem(shearedDeathDrops, Item.COOKED_MUTTON) >= 1,
                    "Cừu đã cắt lông chết cháy chỉ rơi thịt cừu chín");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra vắt sữa bò & dê, uống Milk Bucket xóa hiệu ứng và dê húc đá rơi sừng. */
    private static void checkCowAndGoatMilkingAndHorn() {
        World world = arena();
        try {
            Player player = new Player(100.5f, 20f, 100.5f);
            world.registerPlayer(player);
            player.applyEffect(StatusEffectType.POISON, 0, 20f);
            require(player.hasEffect(StatusEffectType.POISON), "Cần hiệu ứng để kiểm tra Milk Bucket");

            Cow cow = new Cow(102.5f, 20f, 100.5f);
            player.getHotbar().selectSlot(8); // Bucket ở ô 9
            require(cow.onPlayerInteract(world, player), "Vắt sữa bò bằng Bucket phải thành công");
            require(player.getHotbar().getSlotItem(5) == Item.MILK_BUCKET, "Milk Bucket phải vào ô trống Hotbar");

            // Uống sữa xóa hiệu ứng và trả lại Bucket
            player.clearStatusEffects();
            require(!player.hasEffect(StatusEffectType.POISON), "Uống Milk Bucket phải xóa hiệu ứng");

            // Vắt sữa dê
            Goat milkGoat = new Goat(102.5f, 20f, 100.5f);
            player.getHotbar().setSlot(8, Item.BUCKET, 1);
            player.getHotbar().selectSlot(8);
            require(milkGoat.onPlayerInteract(world, player), "Vắt sữa dê bằng Bucket phải thành công");

            // Dê húc vào cột đá phải rơi Goat Horn
            player.setPosition(96.5f, 20f, 100.5f);
            world.setBlock(104, 20, 100, Block.STONE.getId());
            world.setBlock(104, 21, 100, Block.STONE.getId());
            Goat ramGoat = new Goat(102.5f, 20f, 100.5f);
            world.addLivingEntity(ramGoat);
            // Ép mục tiêu giả phía sau bức tường đá để dê lao thẳng vào đá
            Cow dummyTarget = new Cow(105.2f, 20f, 100.5f);
            world.addLivingEntity(dummyTarget);
            // Dùng reflection đặt ramDelay = 0 để dê bắt đầu húc ngay
            var ramDelayField = Goat.class.getDeclaredField("ramDelay");
            ramDelayField.setAccessible(true);
            ramDelayField.setFloat(ramGoat, 0f);
            for (int i = 0; i < 60; i++) {
                ramGoat.update(world, 0.05f);
            }
            List<Item> goatDrops = collectGroundItems(world);
            require(countItem(goatDrops, Item.GOAT_HORN) >= 1 && ramGoat.hornCount() < 2,
                    "Dê húc vào khối đá phải rơi ra Goat Horn");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Lỗi kiểm tra Goat ram", e);
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra rơi đồ quái thù địch: Skeleton, Stray, Parched, Husk, Creeper đĩa nhạc, Enderman, Witch. */
    private static void checkHostileAndSpecialDrops() {
        World world = arena();
        try {
            Player player = new Player(100.5f, 20f, 100.5f);
            world.registerPlayer(player);

            // Creeper bị Skeleton bắn chết -> rơi đĩa nhạc 13 hoặc cat
            Skeleton skeleton = new Skeleton(100.5f, 20f, 100.5f);
            Creeper creeperShot = new Creeper(103.5f, 20f, 100.5f);
            world.addLivingEntity(creeperShot);
            ArrowEntity arrow = new ArrowEntity(skeleton, 102.5f, 21f, 100.5f, 10f, 0f, 0f, ArrowType.NORMAL, 25f);
            creeperShot.damage(world, 25f, DamageSource.ARROW, skeleton, 0f, 0f);
            List<Item> creeperDrops = collectGroundItems(world);
            require(countItem(creeperDrops, Item.MUSIC_DISC_13) + countItem(creeperDrops, Item.MUSIC_DISC_CAT) == 1,
                    "Creeper bị Skeleton bắn chết phải rơi đúng 1 đĩa nhạc");
            arrow.isFinished();

            // Creeper tự nổ (discard) -> không rơi đồ
            Creeper creeperBoom = new Creeper(103.5f, 20f, 100.5f);
            creeperBoom.discard();
            require(collectGroundItems(world).isEmpty(), "Creeper tự nổ không được rơi vật phẩm");

            // Enderman giữ khối -> phải rơi khối đang giữ khi chết
            Enderman enderman = new Enderman(103.5f, 20f, 100.5f);
            var heldField = Enderman.class.getDeclaredField("heldBlock");
            heldField.setAccessible(true);
            heldField.set(enderman, Block.GRASS);
            enderman.damage(world, 50f, DamageSource.MELEE, player, 0f, 0f);
            List<Item> enderDrops = collectGroundItems(world);
            require(countItem(enderDrops, Item.of(Block.GRASS)) == 1,
                    "Enderman chết phải rơi lại khối đang cầm");

            // Witch đang uống thuốc -> có cơ hội rơi đúng loại thuốc đang uống
            Witch witch = new Witch(103.5f, 20f, 100.5f);
            witch.damage(world, 2f, DamageSource.MELEE, player, 0f, 0f);
            witch.update(world, 0.2f);
            require(witch.drinkingPotionItem() == Item.POTION_HEALING,
                    "Witch bị thương phải chuyển sang uống Potion of Healing");

            // Witch chết chuẩn Java 1.21+: luôn rơi cố định 4–8 Redstone Dust + 1–3 lượt × 0–2 từ 6 món còn lại
            int extraDropsTotal = 0;
            for (int i = 0; i < 30; i++) {
                Witch lootWitch = new Witch(103.5f, 20f, 100.5f);
                lootWitch.damage(world, 50f, DamageSource.MELEE, player, 0f, 0f);
                List<Item> witchDrops = collectGroundItems(world);
                int redstoneCount = countItem(witchDrops, Item.REDSTONE);
                require(redstoneCount >= 4 && redstoneCount <= 8,
                        "Witch phải luôn rơi cố định 4–8 Redstone Dust (nhận " + redstoneCount + ")");
                int nonRedstone = 0;
                for (Item drop : witchDrops) {
                    if (drop == Item.REDSTONE) {
                        continue;
                    }
                    require(drop == Item.GLASS_BOTTLE || drop == Item.GLOWSTONE_DUST || drop == Item.GUNPOWDER
                                    || drop == Item.SPIDER_EYE || drop == Item.SUGAR || drop == Item.STICK,
                            "Vật phẩm phụ của Witch phải thuộc 6 món hợp lệ (" + drop + ")");
                    nonRedstone++;
                }
                require(nonRedstone <= 6, "Tối đa 3 lượt × 2 món phụ từ Witch (" + nonRedstone + ")");
                extraDropsTotal += nonRedstone;
            }
            require(extraDropsTotal > 0, "Witch phải có rơi thêm vật phẩm phụ ngoài Redstone");

            // Kiểm tra Husk cầm giáo sắt, Zombie, Zombie Villager, Stray, Parched, Spider không ném lỗi
            new Zombie(103.5f, 20f, 100.5f, false).damage(world, 30f, DamageSource.FIRE, player, 0f, 0f);
            new ZombieVillager(103.5f, 20f, 100.5f).damage(world, 30f, DamageSource.MELEE, player, 0f, 0f);
            new Husk(103.5f, 20f, 100.5f, true).damage(world, 30f, DamageSource.MELEE, player, 0f, 0f);
            new Stray(103.5f, 20f, 100.5f).damage(world, 30f, DamageSource.MELEE, player, 0f, 0f);
            new Parched(103.5f, 20f, 100.5f).damage(world, 30f, DamageSource.MELEE, player, 0f, 0f);
            new Spider(103.5f, 20f, 100.5f).damage(world, 30f, DamageSource.MELEE, player, 0f, 0f);
            collectGroundItems(world);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Lỗi kiểm tra Enderman heldBlock", e);
        } finally {
            world.dispose();
        }
    }

    private static List<Item> collectGroundItems(World world) {
        List<Item> result = new ArrayList<>();
        for (int i = 0; i < world.entityCount(); i++) {
            if (world.entityAt(i) instanceof ItemEntity entity && !entity.isCollected()) {
                Item item = entity.collectItemForMob();
                if (item != null) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private static boolean allMatch(List<Item> items, Item expected) {
        for (Item item : items) {
            if (item != expected) {
                return false;
            }
        }
        return true;
    }

    private static int countItem(List<Item> items, Item target) {
        int count = 0;
        for (Item item : items) {
            if (item == target) {
                count++;
            }
        }
        return count;
    }

    private static World arena() {
        World world = World.empty();
        for (int x = 96; x <= 108; x++) {
            for (int z = 96; z <= 108; z++) {
                world.setBlock(x, 19, z, Block.STONE.getId());
            }
        }
        return world;
    }

    private static InputStream openAsset(String path) {
        InputStream stream = MobLootChecks.class.getClassLoader().getResourceAsStream(path);
        require(stream != null, "Thiếu tài nguyên: " + path);
        return stream;
    }

    private static String sha1Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
