package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.FoodRegistry;
import com.voxelgame.entity.Item;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.StatusEffectType;
import com.voxelgame.entity.mob.Chicken;
import com.voxelgame.entity.mob.Creeper;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobType;
import com.voxelgame.entity.mob.Pig;
import com.voxelgame.entity.mob.Rabbit;
import com.voxelgame.entity.mob.Sheep;
import com.voxelgame.entity.mob.Wolf;
import com.voxelgame.entity.mob.Zombie;
import com.voxelgame.entity.projectile.ArrowEntity;
import com.voxelgame.entity.projectile.ArrowType;
import com.voxelgame.entity.projectile.ThrownEggEntity;
import com.voxelgame.entity.projectile.ThrownEnderPearlEntity;
import com.badlogic.gdx.math.Vector3;

import java.lang.reflect.Proxy;

/**
 * Bộ kiểm thử hồi quy trên CPU cho Track A:
 * chiến đấu, thức ăn, thuốc Witch, đạn ném, thuần hóa mob và góc nhìn.
 */
final class CombatItemsChecks {

    /** Chạy toàn bộ các ca kiểm thử của Track A. */
    static void run() {
        checkFoodAndPotions();
        checkEnderPearlAndEgg();
        checkWolfTamingAndTempt();
        checkBowAndIronSpear();
        checkCameraModeCycle();
        checkEntityKnockbackAndHitSfx();
    }

    /** Kiểm tra đủ 19 món ăn trong FoodRegistry, ăn hồi đói/saturation và 4 lọ thuốc Witch. */
    private static void checkFoodAndPotions() {
        // 1. Kiểm tra đủ 19 món ăn và đúng thông số chuẩn Minecraft
        Item[] expectedFoods = {
                Item.BEEF, Item.COOKED_BEEF,
                Item.PORKCHOP, Item.COOKED_PORKCHOP,
                Item.MUTTON, Item.COOKED_MUTTON,
                Item.CHICKEN, Item.COOKED_CHICKEN,
                Item.RABBIT, Item.COOKED_RABBIT,
                Item.COD, Item.COOKED_COD,
                Item.SALMON, Item.COOKED_SALMON,
                Item.CARROT, Item.POTATO, Item.BAKED_POTATO,
                Item.ROTTEN_FLESH, Item.SPIDER_EYE
        };
        require(FoodRegistry.count() == 19, "FoodRegistry phải đăng ký đủ 19 món ăn");
        for (Item food : expectedFoods) {
            require(FoodRegistry.of(food) != null, "Thiếu thông số FoodRegistry cho " + food.getDisplayName());
        }
        require(FoodRegistry.of(null) == null, "null không phải thức ăn");
        require(FoodRegistry.of(Item.NONE) == null, "Item.NONE không phải thức ăn");
        require(FoodRegistry.of(Item.BONE) == null, "Item.BONE không phải thức ăn");
        require(FoodRegistry.of(Item.POTION_HEALING) == null, "Thuốc không phải thức ăn trong FoodRegistry");

        FoodRegistry.FoodSpec steak = FoodRegistry.of(Item.COOKED_BEEF);
        require(steak.nutrition() == 8 && Math.abs(steak.saturation() - 12.8f) < 0.001f
                && steak.effect() == null && steak.effectChance() == 0f,
                "COOKED_BEEF phải hồi +8 đói và 12.8 saturation");

        FoodRegistry.FoodSpec rotten = FoodRegistry.of(Item.ROTTEN_FLESH);
        require(rotten.nutrition() == 4 && Math.abs(rotten.saturation() - 0.8f) < 0.001f
                && rotten.effect() == StatusEffectType.HUNGER
                && Math.abs(rotten.durationSeconds() - 30f) < 0.001f
                && rotten.amplifier() == 0
                && Math.abs(rotten.effectChance() - 0.8f) < 0.001f,
                "ROTTEN_FLESH phải hồi +4 đói, 0.8 sat và 80% HUNGER 30s");

        FoodRegistry.FoodSpec spiderEye = FoodRegistry.of(Item.SPIDER_EYE);
        require(spiderEye.nutrition() == 2 && Math.abs(spiderEye.saturation() - 3.2f) < 0.001f
                && spiderEye.effect() == StatusEffectType.POISON
                && Math.abs(spiderEye.durationSeconds() - 4f) < 0.001f
                && Math.abs(spiderEye.effectChance() - 1.0f) < 0.001f,
                "SPIDER_EYE phải hồi +2 đói, 3.2 sat và 100% POISON 4s");

        FoodRegistry.FoodSpec rawChicken = FoodRegistry.of(Item.CHICKEN);
        require(rawChicken.nutrition() == 2 && Math.abs(rawChicken.saturation() - 1.2f) < 0.001f
                && rawChicken.effect() == StatusEffectType.HUNGER
                && Math.abs(rawChicken.durationSeconds() - 30f) < 0.001f
                && Math.abs(rawChicken.effectChance() - 0.3f) < 0.001f,
                "CHICKEN phải hồi +2 đói, 1.2 sat và 30% HUNGER 30s");

        // 2. Kiểm tra Player.consumeFood(Item) tăng hunger & saturation đúng trần (saturation <= hunger <= 20)
        World world = World.empty();
        try {
            Player player = new Player(10.5f, 15f, 10.5f);
            near(player.getSaturation(), 5.0f, "Saturation khởi đầu của Player phải là 5.0");
            require(!player.consumeFood(Item.COOKED_BEEF), "Không được ăn khi hunger >= 20");
            require(!player.consumeFood(Item.BONE), "Không được ăn vật phẩm không phải thức ăn");

            // Hạ hunger xuống 2 bằng hiệu ứng HUNGER (cấp 9 => trừ 5 điểm/giây, trong 3.6s => mất 18 điểm)
            player.applyEffect(StatusEffectType.HUNGER, 9, 3.6f);
            player.update(world, 3.6f);
            near(player.getHunger(), 2.0f, "Hiệu ứng HUNGER phải giảm độ đói xuống 2.0");
            near(player.getSaturation(), 2.0f, "Saturation phải luôn bị chặn không vượt quá hunger");

            // Ăn COOKED_BEEF (+8 đói, +12.8 sat) -> hunger = 10, saturation bị chặn ở 10 (saturation <= hunger)
            require(player.consumeFood(Item.COOKED_BEEF), "Phải ăn được COOKED_BEEF khi hunger = 2");
            near(player.getHunger(), 10.0f, "Sau khi ăn COOKED_BEEF hunger phải tăng lên 10");
            near(player.getSaturation(), 10.0f, "Saturation không được vượt quá hunger hiện tại (10)");

            // Ăn SPIDER_EYE (+2 đói, +3.2 sat, 100% POISON 4s) -> hunger = 12, sat = 12
            require(player.consumeFood(Item.SPIDER_EYE), "Phải ăn được SPIDER_EYE khi hunger = 10");
            near(player.getHunger(), 12.0f, "Sau khi ăn SPIDER_EYE hunger phải tăng lên 12");
            near(player.getSaturation(), 12.0f, "Saturation phải bị chặn ở 12");
            require(player.hasEffect(StatusEffectType.POISON), "Ăn SPIDER_EYE phải dính POISON");
            near(player.getEffectDuration(StatusEffectType.POISON), 4.0f, "Thời lượng POISON của SPIDER_EYE phải là 4s");

            // Ăn COOKED_BEEF lần nữa (+8 đói, +12.8 sat) -> hunger = 20, sat = 20
            require(player.consumeFood(Item.COOKED_BEEF), "Phải ăn được COOKED_BEEF khi hunger = 12");
            near(player.getHunger(), 20.0f, "Hunger tối đa là 20");
            near(player.getSaturation(), 20.0f, "Saturation tối đa bằng hunger (20)");
            require(!player.consumeFood(Item.CARROT), "Đã đầy hunger = 20 thì phải trả false");

            // Kiểm tra xác suất hiệu ứng phụ ROTTEN_FLESH với giá trị tung xúc xắc tường minh
            Player rottenTester = new Player(12.5f, 15f, 10.5f);
            rottenTester.applyEffect(StatusEffectType.HUNGER, 9, 1.0f);
            rottenTester.update(world, 1.0f);
            require(!rottenTester.hasEffect(StatusEffectType.HUNGER), "HUNGER phải hết sau 1s");
            require(rottenTester.consumeFood(Item.ROTTEN_FLESH, 0.90f), "Vẫn ăn được ROTTEN_FLESH khi roll >= 0.8");
            require(!rottenTester.hasEffect(StatusEffectType.HUNGER), "Roll 0.90 >= 0.80 không kích hoạt HUNGER");
            require(rottenTester.consumeFood(Item.ROTTEN_FLESH, 0.50f), "Ăn ROTTEN_FLESH khi roll < 0.8");
            require(rottenTester.hasEffect(StatusEffectType.HUNGER), "Roll 0.50 < 0.80 phải kích hoạt HUNGER");
            near(rottenTester.getEffectDuration(StatusEffectType.HUNGER), 30.0f, "ROTTEN_FLESH gây HUNGER 30s");

            // 3. Kiểm tra uống 4 lọ thuốc Witch và trả GLASS_BOTTLE
            Player drinker = new Player(14.5f, 15f, 10.5f);
            require(!drinker.consumePotion(Item.GLASS_BOTTLE), "Vật phẩm không phải thuốc phải trả false");
            require(!drinker.consumePotion(null), "null không phải thuốc");

            // Đổ đầy tất cả 9 ô Hotbar, đặt POTION_HEALING ở ô 0 đang chọn để kiểm tra thay thế đúng ô bằng GLASS_BOTTLE
            for (int slot = 0; slot < 9; slot++) {
                drinker.getHotbar().setSlot(slot, Block.STONE, 16);
            }
            drinker.getHotbar().setSlot(0, Item.POTION_HEALING, 1);
            drinker.getHotbar().selectSlot(0);
            drinker.damage(world, 6.0f, DamageSource.MELEE, null, 0f, 0f);
            near(drinker.getHealth(), 14.0f, "HP trước khi uống thuốc hồi máu phải là 14");
            require(drinker.consumePotion(Item.POTION_HEALING), "Phải uống được POTION_HEALING");
            near(drinker.getHealth(), 18.0f, "POTION_HEALING phải hồi +4 HP");
            require(drinker.getHotbar().getSlotItem(0) == Item.GLASS_BOTTLE
                    && drinker.getHotbar().getSlotCount(0) == 1,
                    "Uống POTION_HEALING ở ô đầy phải trả GLASS_BOTTLE đúng ô vừa uống");

            // Uống POTION_FIRE_RESISTANCE (180s) -> miễn sát thương Lửa & Lava
            drinker.getHotbar().setSlot(1, Item.POTION_FIRE_RESISTANCE, 1);
            drinker.getHotbar().selectSlot(1);
            require(drinker.consumePotion(Item.POTION_FIRE_RESISTANCE), "Phải uống được POTION_FIRE_RESISTANCE");
            require(drinker.hasFireResistance() && drinker.isImmuneToFire(),
                    "Sau khi uống Fire Resistance thì hasFireResistance & isImmuneToFire phải true");
            near(drinker.getEffectDuration(StatusEffectType.FIRE_RESISTANCE), 180.0f,
                    "Thời lượng FIRE_RESISTANCE phải là 180s");
            require(drinker.getHotbar().getSlotItem(1) == Item.GLASS_BOTTLE,
                    "Uống Fire Resistance phải trả GLASS_BOTTLE (có thể gộp hoặc đặt ở ô)");
            require(!drinker.damage(world, 4.0f, DamageSource.LAVA, null, 0f, 0f),
                    "FIRE_RESISTANCE phải chặn sát thương Lava");
            require(!drinker.damage(world, 1.0f, DamageSource.FIRE, null, 0f, 0f),
                    "FIRE_RESISTANCE phải chặn sát thương Lửa");
            drinker.ignite(10.0f);
            near(drinker.getFireTimer(), 0.0f, "Miễn nhiễm lửa không được bắt cháy");

            // Uống POTION_SWIFTNESS (180s) -> tốc độ di chuyển +20%
            drinker.getHotbar().setSlot(2, Item.POTION_SWIFTNESS, 1);
            drinker.getHotbar().selectSlot(2);
            require(drinker.consumePotion(Item.POTION_SWIFTNESS), "Phải uống được POTION_SWIFTNESS");
            near(drinker.getEffectDuration(StatusEffectType.SWIFTNESS), 180.0f,
                    "Thời lượng SWIFTNESS phải là 180s");
            near(drinker.getSpeedMultiplier(), 1.20f, "SWIFTNESS I phải tăng tốc độ +20%");
            near(drinker.getMovementSpeedMultiplier(), 1.20f, "getMovementSpeedMultiplier phải đồng bộ 1.20x");

            // Kiểm tra tốc độ di chuyển ngang thực tế của Player tăng +20% khi có SWIFTNESS
            Input previousInput = Gdx.input;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> method.getName().equals("isKeyPressed") && (int) args[0] == Input.Keys.W);
            try {
                Camera camera = new Camera();
                camera.rotate(90f, 0f);
                Player swiftPlayer = new Player(20.5f, 15f, 10.5f);
                swiftPlayer.attachControls(camera);
                require(swiftPlayer.consumePotion(Item.POTION_SWIFTNESS), "Phải uống được POTION_SWIFTNESS trực tiếp");
                require(swiftPlayer.getHotbar().getSlotItem(5) == Item.GLASS_BOTTLE,
                        "Uống thuốc trực tiếp vẫn phải trả GLASS_BOTTLE vào ô trống Hotbar");
                swiftPlayer.update(world, 0.05f);
                near(swiftPlayer.getVelocity().x, 5.5f * 1.20f,
                        "SWIFTNESS phải tăng vận tốc điều khiển ngang của Player lên 1.20x");
            } finally {
                Gdx.input = previousInput;
            }

            // Uống POTION_WATER_BREATHING (180s) -> chặn sát thương ngạt
            drinker.getHotbar().setSlot(3, Item.POTION_WATER_BREATHING, 1);
            drinker.getHotbar().selectSlot(3);
            require(drinker.consumePotion(Item.POTION_WATER_BREATHING), "Phải uống được POTION_WATER_BREATHING");
            near(drinker.getEffectDuration(StatusEffectType.WATER_BREATHING), 180.0f,
                    "Thời lượng WATER_BREATHING phải là 180s");
            require(!drinker.damage(world, 2.0f, DamageSource.DROWNING, null, 0f, 0f),
                    "WATER_BREATHING phải chặn sát thương ngạt nước");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra đạn ném Ender Pearl, Egg (xác suất 1/8 và 1/32) và hằng số cooldown Goat Horn. */
    private static void checkEnderPearlAndEgg() {
        near(Player.GOAT_HORN_COOLDOWN, 7.0f, "GOAT_HORN_COOLDOWN phải là 7.0 giây");

        World world = World.empty();
        try {
            // Tạo sàn đá tại Y = 10 (mặt trên ở Y = 11.0) quanh khu vực rơi (x, z in [28..44])
            for (int x = 28; x <= 44; x++) {
                for (int z = 28; z <= 44; z++) {
                    world.setBlockInternal(x, 10, z, Block.STONE.getId(), 0);
                }
            }

            // 1. Ném Ender Pearl rơi xuống sàn đá Y=10 -> dịch chuyển Player tới quanh điểm rơi, xóa fallDistance, mất 5 HP
            Player player = new Player(10.5f, 20.0f, 10.5f);
            Camera camera = new Camera();
            player.attachControls(camera);
            world.registerPlayer(player);

            // Tạo độ rơi tích lũy cho Player (không có Input) để kiểm chứng Ender Pearl xóa fallDistance về 0
            player.update(world, 0.25f);
            require(player.getFallDistance() > 0f, "Player đang rơi phải có fallDistance > 0 trước khi dịch chuyển");

            ThrownEnderPearlEntity pearl = new ThrownEnderPearlEntity(
                    world, player, 30.5f, 15.0f, 30.5f, 2.0f, -12.0f, 0.0f);
            world.addEntity(pearl);
            for (int tick = 0; tick < 30 && !pearl.isFinished(); tick++) {
                world.updateEntities(0.05f);
            }

            require(pearl.isFinished(), "Ender Pearl chạm sàn đá phải kết thúc vòng đời");
            require(player.getPosition().y >= 11.0f && player.getPosition().y < 13.0f,
                    "Player sau dịch chuyển phải đứng trên mặt sàn Y >= 11 (thực tế: " + player.getPosition().y + ")");
            require(Math.abs(player.getPosition().x - 31.0f) < 1.5f
                    && Math.abs(player.getPosition().z - 30.5f) < 1.0f,
                    "Player phải dịch chuyển tới quanh điểm rơi Ender Pearl");
            near(player.getFallDistance(), 0.0f, "Ender Pearl phải xóa fallDistance về 0");
            near(player.getHealth(), 15.0f, "Ender Pearl phải gây 5.0 sát thương (HP 20 -> 15)");

            // 2. Kiểm tra va chạm mặt bên tường đá: lùi nhẹ để chân và đầu không kẹt trong tường
            for (int y = 11; y <= 14; y++) {
                for (int z = 29; z <= 32; z++) {
                    world.setBlockInternal(40, y, z, Block.STONE.getId(), 0);
                }
            }
            Player wallThrower = new Player(35.5f, 11.0f, 30.5f);
            world.addEntity(wallThrower);
            ThrownEnderPearlEntity wallPearl = new ThrownEnderPearlEntity(
                    world, wallThrower, 37.5f, 11.6f, 30.5f, 20.0f, 0.0f, 0.0f);
            world.addEntity(wallPearl);
            for (int tick = 0; tick < 20 && !wallPearl.isFinished(); tick++) {
                world.updateEntities(0.05f);
            }
            require(wallPearl.isFinished(), "Ender Pearl đâm vào mặt bên tường phải va chạm");
            require(wallThrower.getPosition().x <= 40.0f - wallThrower.getHalfWidth() + 0.001f,
                    "Dịch chuyển khi đâm mặt bên phải lùi ra khỏi khối tường (x=" + wallThrower.getPosition().x + ")");
            near(wallThrower.getHealth(), 15.0f, "Dịch chuyển khi đâm mặt bên vẫn trừ 5.0 HP");

            // 3. Nếu chủ sở hữu đã chết trước khi pearl chạm đích thì không dịch chuyển
            Player deadThrower = new Player(32.5f, 11.0f, 35.5f);
            deadThrower.discard();
            ThrownEnderPearlEntity deadPearl = new ThrownEnderPearlEntity(
                    world, deadThrower, 35.5f, 13.0f, 35.5f, 0.0f, -20.0f, 0.0f);
            world.addEntity(deadPearl);
            for (int tick = 0; tick < 20 && !deadPearl.isFinished(); tick++) {
                world.updateEntities(0.05f);
            }
            require(deadPearl.isFinished(), "Ender Pearl của chủ đã chết vẫn kết thúc khi chạm sàn");
            near(deadThrower.getPosition().x, 32.5f, "Chủ đã chết không được dịch chuyển");

            // 4. Kiểm tra bảng xác suất nở gà con của ThrownEggEntity.rollChickCount(0..255)
            int zeroCount = 0;
            int oneCount = 0;
            int fourCount = 0;
            for (int roll = 0; roll < 256; roll++) {
                int hatched = ThrownEggEntity.rollChickCount(roll);
                if (hatched == 0) {
                    zeroCount++;
                } else if (hatched == 1) {
                    oneCount++;
                } else if (hatched == 4) {
                    fourCount++;
                } else {
                    throw new AssertionError("rollChickCount chỉ được trả 0, 1 hoặc 4 nhưng trả về " + hatched);
                }
            }
            require(zeroCount == 224 && oneCount == 31 && fourCount == 1,
                    "Xác suất nở gà phải là 224 lần 0, 31 lần 1 và 1 lần 4 trong 256 mẫu (thực tế: 0="
                            + zeroCount + ", 1=" + oneCount + ", 4=" + fourCount + ")");

            // Kiểm tra thực tế sinh Chicken con (setBaby(true)) trong World khi trứng vỡ
            int chickensBefore = countBabyChickens(world);
            ThrownEggEntity eggFour = new ThrownEggEntity(world, player, 32.5f, 13.0f, 32.5f, 0f, -18f, 0f);
            eggFour.setHatchRollForTest(0);
            world.addEntity(eggFour);
            for (int tick = 0; tick < 20 && !eggFour.isFinished(); tick++) {
                world.updateEntities(0.05f);
            }
            require(eggFour.isFinished(), "Egg chạm sàn phải vỡ");
            require(countBabyChickens(world) == chickensBefore + 4,
                    "Egg với roll = 0 phải nở đúng 4 Chicken con");

            ThrownEggEntity eggOne = new ThrownEggEntity(world, player, 33.5f, 13.0f, 32.5f, 0f, -18f, 0f);
            eggOne.setHatchRollForTest(8);
            world.addEntity(eggOne);
            for (int tick = 0; tick < 20 && !eggOne.isFinished(); tick++) {
                world.updateEntities(0.05f);
            }
            require(countBabyChickens(world) == chickensBefore + 5,
                    "Egg với roll = 8 phải nở đúng 1 Chicken con");

            ThrownEggEntity eggZero = new ThrownEggEntity(world, player, 34.5f, 13.0f, 32.5f, 0f, -18f, 0f);
            eggZero.setHatchRollForTest(3);
            world.addEntity(eggZero);
            for (int tick = 0; tick < 20 && !eggZero.isFinished(); tick++) {
                world.updateEntities(0.05f);
            }
            require(countBabyChickens(world) == chickensBefore + 5,
                    "Egg với roll = 3 không được nở thêm Chicken con");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra thuần hóa Wolf bằng Bone, ngồi/đứng, đi theo/dịch chuyển, bảo vệ chủ và dụ Pig/Rabbit. */
    private static void checkWolfTamingAndTempt() {
        World world = World.empty();
        try {
            // Tạo sàn đá rộng tại Y = 10 (đứng ở Y = 11) cho thử nghiệm sói, lợn và thỏ
            for (int x = 4; x <= 55; x++) {
                for (int z = 4; z <= 28; z++) {
                    world.setBlockInternal(x, 10, z, Block.STONE.getId(), 0);
                }
            }

            Player owner = new Player(12.5f, 11f, 10.5f);
            world.registerPlayer(owner);
            Wolf wolf = new Wolf(10.5f, 11f, 10.5f);
            world.addLivingEntity(wolf);

            require(!wolf.isTamed() && !wolf.isSitting() && wolf.getOwner() == null,
                    "Sói hoang ban đầu chưa thuần hóa và chưa có chủ");
            near(wolf.getMaxHealth(), 8.0f, "Sói hoang ban đầu có 8 HP tối đa");

            // Trang bị 2 xương ở ô 5 (đang chọn) để kiểm tra tiêu thụ và xác suất 1/3
            owner.getHotbar().setSlot(5, Item.BONE, 2);
            owner.getHotbar().selectSlot(5);
            require(!wolf.interactWithPlayer(owner, Item.CARROT), "Không thể thuần hóa sói bằng Carrot");
            require(!wolf.isTamed(), "Sói chưa thể thuần hóa bằng vật phẩm khác Bone");

            require(wolf.interactWithPlayer(owner, Item.BONE, 0.50f),
                    "Thử thuần hóa với roll >= 1/3 vẫn tiêu thụ 1 xương");
            require(!wolf.isTamed(), "Roll 0.50 >= 1/3 không được thuần hóa sói");
            near(wolf.getMaxHealth(), 8.0f, "Sói chưa thuần hóa giữ nguyên 8 HP tối đa");
            require(owner.getHotbar().getSlotCount(5) == 1, "Thử thuần hóa thất bại phải trừ 1 xương");

            // Làm sói mất 2 HP trước khi thuần hóa để kiểm tra HP hồi lên đủ 20
            require(wolf.damage(world, 2.0f, DamageSource.MELEE, null, 0f, 0f), "Sói phải nhận sát thương thử");
            near(wolf.getHealth(), 6.0f, "Sói bị đánh mất 2 HP còn 6");

            require(wolf.interactWithPlayer(owner, Item.BONE, 0.20f),
                    "Thuần hóa với roll < 1/3 phải thành công");
            require(wolf.isTamed() && wolf.isPersistent() && wolf.getOwner() == owner && !wolf.isSitting(),
                    "Sói thuần hóa phải ghi nhận owner, persistent=true và đang đứng");
            near(wolf.getMaxHealth(), 20.0f, "Sói sau khi thuần hóa phải nâng maxHealth lên 20");
            near(wolf.getHealth(), 20.0f, "Sói sau khi thuần hóa phải hồi HP lên 20");
            require(owner.getHotbar().getSlotCount(5) == 0 && owner.getHotbar().getSlotItem(5) == Item.NONE,
                    "Thuần hóa thành công phải tiêu thụ nốt xương cuối");

            // Chuột phải tay không (qua onPlayerInteract) -> chuyển ngồi và giữ nguyên vị trí
            require(wolf.onPlayerInteract(world, owner), "Chủ chuột phải tay không vào sói phải được xử lý");
            require(wolf.isSitting() && wolf.mobVisualState().pose() == MobVisualState.Pose.SIT,
                    "Chuột phải tay không phải làm sói ngồi");
            owner.setPosition(23.5f, 11f, 10.5f);
            float sitX = wolf.x();
            for (int tick = 0; tick < 20; tick++) {
                wolf.update(world, 0.05f);
            }
            near(wolf.x(), sitX, "Sói đang ngồi không được chạy theo chủ");

            // Chuột phải tay không lần nữa -> đứng dậy và đi theo chủ ở khoảng cách > 10 block
            require(wolf.onPlayerInteract(world, owner), "Chuột phải tay không lần hai phải cho sói đứng dậy");
            require(!wolf.isSitting(), "Sói phải thoát trạng thái ngồi");
            for (int tick = 0; tick < 30; tick++) {
                wolf.update(world, 0.05f);
            }
            require(wolf.x() > sitX + 1.5f, "Sói đã đứng dậy phải chạy theo chủ khi cách > 10 block");

            // Chủ đi xa > 24 block -> sói tự dịch chuyển tới gần chủ
            owner.setPosition(48.5f, 11f, 10.5f);
            wolf.update(world, 0.05f);
            require(Math.abs(wolf.x() - owner.getPosition().x) <= 4.5f,
                    "Sói đứng cách chủ > 24 block phải dịch chuyển tới gần chủ");

            // Sói đã thuần hóa không tấn công chủ khi chủ đánh nhầm và bỏ qua onOwnerAttackOrHurt(owner)
            owner.setPosition(20.5f, 11f, 10.5f);
            wolf.setPosition(18.5f, 11f, 10.5f);
            owner.attack(world, wolf, Vector3.X);
            wolf.update(world, 0.05f);
            require(wolf.target() == null && wolf.mobVisualState().appearance() == MobAppearance.WOLF_SNOWY,
                    "Sói đã thuần hóa tuyệt đối không được nổi giận hay tấn công chủ");
            wolf.onOwnerAttackOrHurt(owner);
            require(wolf.target() == null, "onOwnerAttackOrHurt(owner) phải bị bỏ qua");

            // Sói thuần hóa không tự săn Sheep, nhưng đánh trả Zombie khi chủ tấn công hoặc bị đánh
            Sheep sheep = new Sheep(19.5f, 11f, 12.5f);
            world.addLivingEntity(sheep);
            for (int tick = 0; tick < 15; tick++) {
                wolf.update(world, 0.05f);
            }
            require(wolf.target() == null, "Sói đã thuần hóa không được tự săn cừu");

            Zombie threat = new Zombie(19.5f, 11f, 10.5f);
            world.addLivingEntity(threat);
            wolf.onOwnerAttackOrHurt(threat);
            require(wolf.target() == threat, "Sói phải nhắm vào kẻ thù khi chủ đánh hoặc bị đánh");
            for (int tick = 0; tick < 20; tick++) {
                wolf.update(world, 0.05f);
            }
            require(threat.getHealth() < threat.getMaxHealth(),
                    "Sói đã thuần hóa phải cắn trả kẻ thù của chủ");

            // Kiểm tra Pig đi theo Carrot/Potato và Rabbit đi theo Carrot/Dandelion trong thế giới sạch
            World temptWorld = World.empty();
            try {
                for (int x = 10; x <= 26; x++) {
                    for (int z = 12; z <= 26; z++) {
                        temptWorld.setBlockInternal(x, 10, z, Block.STONE.getId(), 0);
                    }
                }
                Player baiter = new Player(20.5f, 11f, 20.5f);
                temptWorld.registerPlayer(baiter);
                baiter.getHotbar().selectSlot(5);

                baiter.getHotbar().setSlot(5, Item.CARROT, 1);
                Pig pigCarrot = new Pig(14.5f, 11f, 20.5f);
                Rabbit rabbitCarrot = new Rabbit(14.5f, 11f, 18.5f);
                for (int tick = 0; tick < 30; tick++) {
                    pigCarrot.update(temptWorld, 0.05f);
                    rabbitCarrot.update(temptWorld, 0.05f);
                }
                require(pigCarrot.x() > 16.0f, "Pig phải đi theo người chơi cầm Item.CARROT");
                require(rabbitCarrot.x() > 15.5f, "Rabbit phải đi theo người chơi cầm Item.CARROT");

                baiter.getHotbar().setSlot(5, Item.POTATO, 1);
                Pig pigPotato = new Pig(14.5f, 11f, 22.5f);
                for (int tick = 0; tick < 30; tick++) {
                    pigPotato.update(temptWorld, 0.05f);
                }
                require(pigPotato.x() > 16.0f, "Pig phải đi theo người chơi cầm Item.POTATO");

                baiter.getHotbar().setSlot(5, Block.DANDELION, 1);
                Rabbit rabbitDandelion = new Rabbit(14.5f, 11f, 16.5f);
                for (int tick = 0; tick < 30; tick++) {
                    rabbitDandelion.update(temptWorld, 0.05f);
                }
                require(rabbitDandelion.x() > 15.5f, "Rabbit vẫn phải đi theo người chơi cầm Block.DANDELION");
            } finally {
                temptWorld.dispose();
            }
        } finally {
            world.dispose();
        }
    }

    /**
     * Kiểm tra toàn diện ItemUseHandler: Cung + 3 loại mũi tên, Giáo sắt Jab & Charge,
     * ăn 1.6s, uống thuốc/Milk Bucket trả bình/xô, ném Ender Pearl/Egg, Goat Horn cooldown 7s,
     * giảm tốc 0.4x khi kéo cung/ăn/uống, FOV zoom của Camera và hủy khi đổi ô Hotbar.
     */
    private static void checkBowAndIronSpear() {
        World world = World.empty();
        try {
            // Tạo sàn đá phẳng tại Y = 10 (mặt trên ở Y = 11) cho các thử nghiệm chiến đấu
            for (int x = 4; x <= 60; x++) {
                for (int z = 4; z <= 40; z++) {
                    world.setBlockInternal(x, 10, z, Block.STONE.getId(), 0);
                }
            }

            Camera camera = new Camera();
            camera.rotate(90f, 0f); // Nhìn về hướng +X (cos(0)=1, sin(0)=0)
            Player player = new Player(10.5f, 11.0f, 20.5f);
            player.attachControls(camera);
            world.registerPlayer(player);
            camera.setPosition(player.getPosition().x, player.getPosition().y + 1.62f, player.getPosition().z);

            ItemUseHandler handler = new ItemUseHandler();

            // 1. Kiểm tra Cung (BOW) không có tên -> không kéo được; có tên kéo < 0.15s -> không bắn
            player.getHotbar().setSlot(0, Item.BOW, 1);
            player.getHotbar().setSlot(1, Item.NONE, 0);
            player.getHotbar().selectSlot(0);
            require(!handler.onSecondaryPress(world, player, camera),
                    "Không có mũi tên trong kho thì không được bắt đầu kéo cung");
            require(handler.getUseState() == ItemUseHandler.UseState.NONE,
                    "Trạng thái phải vẫn là NONE khi thiếu mũi tên");

            player.getHotbar().setSlot(1, Item.ARROW, 2);
            require(handler.onSecondaryPress(world, player, camera), "Có ARROW phải bắt đầu kéo cung được");
            require(handler.getUseState() == ItemUseHandler.UseState.DRAW_BOW, "Trạng thái phải là DRAW_BOW");
            handler.update(0.10f, world, player, camera);
            require(handler.onSecondaryRelease(world, player, camera), "Nhả chuột khi đang kéo cung phải được xử lý");
            require(handler.getUseState() == ItemUseHandler.UseState.NONE, "Nhả cung phải đưa trạng thái về NONE");
            require(player.getHotbar().getSlotCount(1) == 2, "Kéo dưới 0.15s không được bắn hay tiêu mũi tên");
            require(countArrows(world) == 0, "Kéo dưới 0.15s không được sinh ArrowEntity");

            // 2. Kéo cung đủ 1.0s rồi nhả -> tiêu 1 ARROW, sinh ArrowEntity vận tốc lớn hơn kéo nhanh 0.2s
            require(handler.onSecondaryPress(world, player, camera), "Bắt đầu kéo cung đủ lực");
            for (int step = 0; step < 20; step++) {
                handler.update(0.05f, world, player, camera);
            }
            near(handler.getBowPullProgress(), 1.0f, "Kéo đủ 1.0s phải đạt bowPullProgress = 1.0");
            near(handler.getBowZoomOffset(), -8.0f, "Kéo đủ 1.0s phải đạt bowZoomOffset = -8.0");
            require(camera.getBowZoomOffset() < -7.0f, "Camera phải áp dụng mượt bowZoomOffset âm khi kéo cung");

            // Kiểm tra tốc độ di chuyển ngang bị giảm còn 0.4x khi đang kéo cung
            Input previousInput = Gdx.input;
            boolean[] keys = new boolean[256];
            keys[Input.Keys.W] = true;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> method.getName().equals("isKeyPressed") && keys[(int) args[0]]);
            try {
                player.update(world, 0.05f);
                require(player.getVelocity().x > 0f && player.getVelocity().x <= 5.5f * 0.4f + 0.05f,
                        "Đang kéo cung phải giảm tốc độ đi bộ còn 0.4x (thực tế: " + player.getVelocity().x + ")");
            } finally {
                Gdx.input = previousInput;
            }
            player.setPosition(10.5f, 11.0f, 20.5f);
            camera.setPosition(10.5f, 12.62f, 20.5f);

            int entitiesBeforeFull = world.entityCount();
            require(handler.onSecondaryRelease(world, player, camera), "Nhả cung đủ lực phải bắn tên");
            require(player.getHotbar().getSlotCount(1) == 1, "Bắn cung phải tiêu 1 mũi tên (còn 1)");
            ArrowEntity fullArrow = findNewestArrow(world, entitiesBeforeFull);
            require(fullArrow != null, "Bắn cung đủ lực phải sinh 1 ArrowEntity trong World");
            float fullSpeed = fullArrow.getVelocity().len();

            // Kéo nhanh 0.2s rồi nhả -> tiêu nốt ARROW, vận tốc nhỏ hơn phát kéo đủ 1.0s
            require(handler.onSecondaryPress(world, player, camera), "Bắt đầu kéo cung nhanh 0.2s");
            handler.update(0.20f, world, player, camera);
            near(handler.getBowPullProgress(), 0.2f, "Kéo 0.2s phải đạt bowPullProgress = 0.2");
            int entitiesBeforeQuick = world.entityCount();
            require(handler.onSecondaryRelease(world, player, camera), "Nhả cung 0.2s phải bắn tên");
            require(player.getHotbar().getSlotCount(1) == 0 && player.getHotbar().getSlotItem(1) == Item.NONE,
                    "Bắn phát thứ hai phải tiêu nốt mũi tên cuối");
            ArrowEntity quickArrow = findNewestArrow(world, entitiesBeforeQuick);
            require(quickArrow != null, "Kéo 0.2s vẫn phải sinh 1 ArrowEntity");
            float quickSpeed = quickArrow.getVelocity().len();
            require(fullSpeed > quickSpeed + 10.0f,
                    "Tên kéo đủ 1.0s phải nhanh hơn rõ rệt tên kéo 0.2s (" + fullSpeed + " vs " + quickSpeed + ")");

            // Dọn các mũi tên thử nghiệm cũ để không chặn sát thương của mũi tên có hiệu ứng tiếp theo
            for (int tick = 0; tick < 15; tick++) {
                world.updateEntities(0.05f);
            }

            // 3. Hỗ trợ TIPPED_ARROW_SLOWNESS & TIPPED_ARROW_WEAKNESS
            player.getHotbar().setSlot(2, Item.TIPPED_ARROW_SLOWNESS, 1);
            player.getHotbar().setSlot(3, Item.TIPPED_ARROW_WEAKNESS, 1);

            Zombie slowTarget = new Zombie(16.5f, 11.0f, 20.5f, false);
            world.addLivingEntity(slowTarget);
            require(handler.onSecondaryPress(world, player, camera), "Kéo cung với TIPPED_ARROW_SLOWNESS");
            handler.update(1.0f, world, player, camera);
            require(handler.onSecondaryRelease(world, player, camera), "Nhả cung bắn TIPPED_ARROW_SLOWNESS");
            require(player.getHotbar().getSlotCount(2) == 0, "Phải tiêu thụ TIPPED_ARROW_SLOWNESS ở ô 2");
            for (int tick = 0; tick < 20 && !slowTarget.hasEffect(StatusEffectType.SLOWNESS); tick++) {
                world.updateEntities(0.05f);
            }
            require(slowTarget.hasEffect(StatusEffectType.SLOWNESS),
                    "TIPPED_ARROW_SLOWNESS bắn trúng Zombie phải gây hiệu ứng SLOWNESS");
            slowTarget.discard();

            Zombie weakTarget = new Zombie(16.5f, 11.0f, 20.5f, false);
            world.addLivingEntity(weakTarget);
            require(handler.onSecondaryPress(world, player, camera), "Kéo cung với TIPPED_ARROW_WEAKNESS");
            handler.update(1.0f, world, player, camera);
            require(handler.onSecondaryRelease(world, player, camera), "Nhả cung bắn TIPPED_ARROW_WEAKNESS");
            require(player.getHotbar().getSlotCount(3) == 0, "Phải tiêu thụ TIPPED_ARROW_WEAKNESS ở ô 3");
            for (int tick = 0; tick < 20 && !weakTarget.hasEffect(StatusEffectType.WEAKNESS); tick++) {
                world.updateEntities(0.05f);
            }
            require(weakTarget.hasEffect(StatusEffectType.WEAKNESS),
                    "TIPPED_ARROW_WEAKNESS bắn trúng Zombie phải gây hiệu ứng WEAKNESS");
            weakTarget.discard();

            // 4. Đổi ô Hotbar giữa chừng hủy sạch sẽ DRAW_BOW / EAT_FOOD / DRINK_POTION / CHARGE_SPEAR
            player.getHotbar().setSlot(1, Item.ARROW, 1);
            player.getHotbar().setSlot(4, Item.COOKED_BEEF, 2);
            player.getHotbar().setSlot(5, Item.POTION_HEALING, 1);
            player.getHotbar().setSlot(6, Item.IRON_SPEAR, 1);
            player.getHotbar().selectSlot(0);

            require(handler.onSecondaryPress(world, player, camera), "Kéo cung để thử hủy khi đổi ô");
            handler.update(0.5f, world, player, camera);
            require(handler.getBowZoomOffset() < 0f && camera.getBowZoomOffset() < 0f,
                    "Trước khi đổi ô phải có bowZoomOffset âm");
            player.getHotbar().selectSlot(4);
            handler.onHotbarSlotChanged();
            require(handler.getUseState() == ItemUseHandler.UseState.NONE, "Đổi ô phải hủy DRAW_BOW về NONE");
            near(handler.getBowPullProgress(), 0.0f, "bowPullProgress phải về 0 khi đổi ô");
            near(handler.getBowZoomOffset(), 0.0f, "bowZoomOffset phải về 0 khi đổi ô");
            near(camera.getBowZoomOffset(), 0.0f, "Camera bowZoomOffset phải về 0 ngay khi đổi ô");

            // Hủy EAT_FOOD khi đổi ô
            player.applyEffect(StatusEffectType.HUNGER, 9, 1.0f);
            player.update(world, 1.0f);
            require(handler.onSecondaryPress(world, player, camera), "Bắt đầu ăn COOKED_BEEF");
            handler.update(0.8f, world, player, camera);
            require(handler.getUseState() == ItemUseHandler.UseState.EAT_FOOD && handler.getUseProgress() > 0.4f,
                    "Đang ăn phải có trạng thái EAT_FOOD");
            player.getHotbar().selectSlot(5);
            handler.onHotbarSlotChanged();
            require(handler.getUseState() == ItemUseHandler.UseState.NONE, "Đổi ô phải hủy EAT_FOOD");
            near(handler.getUseProgress(), 0.0f, "Tiến trình ăn phải về 0 khi đổi ô");
            require(player.getHotbar().getSlotCount(4) == 2, "Hủy ăn giữa chừng không được tiêu thức ăn");

            // Hủy DRINK_POTION khi đổi ô
            require(handler.onSecondaryPress(world, player, camera), "Bắt đầu uống POTION_HEALING");
            handler.update(0.8f, world, player, camera);
            require(handler.getUseState() == ItemUseHandler.UseState.DRINK_POTION,
                    "Đang uống phải có trạng thái DRINK_POTION");
            player.getHotbar().selectSlot(6);
            handler.onHotbarSlotChanged();
            require(handler.getUseState() == ItemUseHandler.UseState.NONE, "Đổi ô phải hủy DRINK_POTION");
            require(player.getHotbar().getSlotItem(5) == Item.POTION_HEALING,
                    "Hủy uống giữa chừng không được tiêu lọ thuốc");

            // Hủy CHARGE_SPEAR khi đổi ô
            require(handler.onSecondaryPress(world, player, camera), "Bắt đầu tụ giáo IRON_SPEAR");
            require(handler.isChargingSpear(), "isChargingSpear() phải true khi giữ chuột phải cầm IRON_SPEAR");
            player.getHotbar().selectSlot(0);
            handler.onHotbarSlotChanged();
            require(handler.getUseState() == ItemUseHandler.UseState.NONE && !handler.isChargingSpear(),
                    "Đổi ô phải hủy CHARGE_SPEAR sạch sẽ");

            // 5. Kiểm tra Giáo sắt (IRON_SPEAR): Jab tầm 4.2f (trúng ở 3.8f, tay không 3.0f trượt) & Crit 7.5f
            player.setPosition(10.5f, 11.0f, 20.5f);
            camera.setPosition(10.5f, 12.62f, 20.5f);
            Creeper jabTarget = new Creeper(14.3f, 11.0f, 20.5f); // Khoảng cách tâm tới hộp: ~3.8f (ngoài 3.0f, trong 4.2f)
            world.addLivingEntity(jabTarget);
            float jabInitialHp = jabTarget.getHealth();

            player.getHotbar().selectSlot(7);
            player.getHotbar().setSlot(7, Item.NONE, 0);
            require(!handler.onPrimaryMelee(world, player, camera),
                    "Tay không tầm 3.0f không được đánh trúng mục tiêu ở khoảng 3.8f");
            near(jabTarget.getHealth(), jabInitialHp, "Đánh trượt không được trừ HP mục tiêu");

            player.getHotbar().selectSlot(6); // IRON_SPEAR
            require(handler.onPrimaryMelee(world, player, camera),
                    "IRON_SPEAR tầm 4.2f phải đánh trúng mục tiêu ở khoảng 3.8f");
            near(jabTarget.getHealth(), jabInitialHp - 5.0f,
                    "IRON_SPEAR Jab cơ sở phải gây đúng 5.0 HP sát thương");

            // Hết hồi chiêu 0.65s và đánh crit khi đang rơi
            player.update(world, 0.70f);
            jabTarget.update(world, 0.70f);
            jabTarget.setPosition(14.3f, 11.0f, 20.5f);
            player.setPosition(10.5f, 11.5f, 20.5f);
            camera.setPosition(10.5f, 13.12f, 20.5f);
            camera.rotate(0f, -15f);
            // Cho player rơi nhẹ 0.05s để velocity.y < -0.1f và !onGround
            player.update(world, 0.05f);
            require(handler.onPrimaryMelee(world, player, camera), "IRON_SPEAR Jab crit phải trúng");
            near(jabTarget.getHealth(), jabInitialHp - 12.5f,
                    "IRON_SPEAR Jab crit phải gây 7.5 HP (5.0 * 1.5)");
            camera.rotate(0f, 15f);
            jabTarget.discard();

            // 6. Kiểm tra IRON_SPEAR Charge khi Player lao với speed >= 6.0f -> sát thương >= 9.0f, đẩy lùi và cooldown 0.4s
            player.setPosition(12.5f, 11.0f, 20.5f);
            camera.setPosition(12.5f, 12.62f, 20.5f);
            Zombie chargeTarget = new Zombie(20.5f, 11.0f, 20.5f, false);
            world.addLivingEntity(chargeTarget);
            float chargeHpBefore = chargeTarget.getHealth();

            require(handler.onSecondaryPress(world, player, camera), "Giữ chuột phải cầm IRON_SPEAR vào CHARGE_SPEAR");
            keys[Input.Keys.W] = true;
            keys[Input.Keys.CONTROL_LEFT] = true;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> method.getName().equals("isKeyPressed") && keys[(int) args[0]]);
            try {
                boolean chargedHit = false;
                for (int step = 0; step < 30; step++) {
                    player.update(world, 0.05f);
                    handler.update(0.05f, world, player, camera);
                    if (chargeTarget.getHealth() < chargeHpBefore) {
                        chargedHit = true;
                        break;
                    }
                }
                require(chargedHit, "Lao với IRON_SPEAR ở tốc độ cao phải đâm trúng Zombie trong tầm 3.5f");
                float damageDealt = chargeHpBefore - chargeTarget.getHealth();
                require(damageDealt >= 9.0f,
                        "IRON_SPEAR Charge khi speed >= 6.0f phải gây >= 9.0 HP (thực tế: " + damageDealt + ")");
                chargeTarget.update(world, 0.05f);
                require(chargeTarget.x() > 20.5f,
                        "IRON_SPEAR Charge phải đẩy lùi mục tiêu theo hướng lao (x=" + chargeTarget.x() + ")");

                // Kiểm tra cooldown 0.4s cho cùng mục tiêu
                float hpAfterFirstCharge = chargeTarget.getHealth();
                handler.update(0.05f, world, player, camera);
                near(chargeTarget.getHealth(), hpAfterFirstCharge,
                        "Trong cửa sổ cooldown 0.4s không được gây sát thương Charge lặp lên cùng mục tiêu");
            } finally {
                Gdx.input = previousInput;
                handler.onSecondaryRelease(world, player, camera);
                chargeTarget.discard();
            }

            // Kiểm tra tốc độ chậm (< 4.0f) không kích hoạt sát thương Spear Charge
            player.stopMotion();
            player.setPosition(18.0f, 11.0f, 20.5f);
            camera.setPosition(18.0f, 12.62f, 20.5f);
            Zombie slowWalkTarget = new Zombie(20.5f, 11.0f, 20.5f, false);
            world.addLivingEntity(slowWalkTarget);
            require(handler.onSecondaryPress(world, player, camera), "Giữ chuột phải IRON_SPEAR");
            handler.update(0.05f, world, player, camera);
            near(slowWalkTarget.getHealth(), slowWalkTarget.getMaxHealth(),
                    "Đứng yên hoặc tốc độ < 4.0f không được gây sát thương Charge");
            handler.onSecondaryRelease(world, player, camera);
            slowWalkTarget.discard();

            // 7. Kiểm tra ăn FoodRegistry đúng 1.6s, uống Potion & Milk Bucket trả GLASS_BOTTLE/BUCKET, Goat Horn 7.0s, Ender Pearl & Egg
            player.setPosition(10.5f, 11.0f, 20.5f);
            camera.setPosition(10.5f, 12.62f, 20.5f);
            player.getHotbar().selectSlot(4); // COOKED_BEEF (count=2)
            float hungerBeforeEat = player.getHunger();
            require(handler.onSecondaryPress(world, player, camera), "Bắt đầu ăn COOKED_BEEF");
            handler.update(1.50f, world, player, camera);
            require(handler.getUseState() == ItemUseHandler.UseState.EAT_FOOD,
                    "Ở mốc 1.50s (< 1.6s) chưa được hoàn tất ăn");
            near(player.getHunger(), hungerBeforeEat, "Trước 1.6s chưa được cộng hunger");
            handler.update(0.15f, world, player, camera);
            require(handler.getUseState() == ItemUseHandler.UseState.NONE,
                    "Đạt 1.60s phải hoàn tất ăn và trở về NONE");
            require(player.getHunger() > hungerBeforeEat, "Ăn xong ở 1.6s phải tăng hunger");
            require(player.getHotbar().getSlotCount(4) == 1, "Ăn xong phải tiêu thụ 1 COOKED_BEEF");

            // Uống POTION_HEALING mất 1.6s và trả GLASS_BOTTLE
            player.damage(world, 6.0f, DamageSource.MELEE, null, 0f, 0f);
            player.getHotbar().selectSlot(5); // POTION_HEALING
            require(handler.onSecondaryPress(world, player, camera), "Bắt đầu uống POTION_HEALING");
            handler.update(1.65f, world, player, camera);
            require(handler.getUseState() == ItemUseHandler.UseState.NONE, "Uống xong sau 1.6s phải về NONE");
            require(player.getHotbar().getSlotItem(5) == Item.GLASS_BOTTLE,
                    "Uống xong POTION_HEALING phải trả GLASS_BOTTLE");
            near(player.getHealth(), 18.0f, "POTION_HEALING phải hồi +4 HP");

            // Uống MILK_BUCKET mất 1.6s, xóa mọi hiệu ứng và trả BUCKET
            player.applyEffect(StatusEffectType.SLOWNESS, 0, 30.0f);
            player.getHotbar().setSlot(5, Item.MILK_BUCKET, 1);
            require(handler.onSecondaryPress(world, player, camera), "Bắt đầu uống MILK_BUCKET");
            handler.update(1.65f, world, player, camera);
            require(!player.hasEffect(StatusEffectType.SLOWNESS), "Uống xong MILK_BUCKET phải xóa mọi hiệu ứng");
            require(player.getHotbar().getSlotItem(5) == Item.BUCKET, "Uống xong MILK_BUCKET phải trả BUCKET");

            // Hồi chiêu Goat Horn là 7.0 giây
            player.getHotbar().setSlot(8, Item.GOAT_HORN, 1);
            player.getHotbar().selectSlot(8);
            require(handler.onSecondaryPress(world, player, camera), "Thổi GOAT_HORN lần đầu phải thành công");
            near(handler.getGoatHornCooldown(), 7.0f, "Cooldown GOAT_HORN phải là 7.0s");
            handler.update(3.5f, world, player, camera);
            near(handler.getGoatHornCooldown(), 3.5f, "Cooldown GOAT_HORN phải giảm theo thời gian");
            handler.update(3.6f, world, player, camera);
            near(handler.getGoatHornCooldown(), 0.0f, "Sau 7.0s cooldown GOAT_HORN phải về 0");

            // Ném Ender Pearl & Egg qua ItemUseHandler
            player.getHotbar().setSlot(7, Item.ENDER_PEARL, 1);
            player.getHotbar().selectSlot(7);
            int beforePearl = world.entityCount();
            require(handler.onSecondaryPress(world, player, camera), "Ném ENDER_PEARL qua ItemUseHandler");
            require(player.getHotbar().getSlotCount(7) == 0, "Ném ENDER_PEARL phải tiêu 1 ngọc");
            require(countTypeSince(world, beforePearl, ThrownEnderPearlEntity.class) == 1,
                    "Phải sinh 1 ThrownEnderPearlEntity trong World");

            player.getHotbar().setSlot(7, Item.EGG, 1);
            int beforeEgg = world.entityCount();
            require(handler.onSecondaryPress(world, player, camera), "Ném EGG qua ItemUseHandler");
            require(player.getHotbar().getSlotCount(7) == 0, "Ném EGG phải tiêu 1 trứng");
            require(countTypeSince(world, beforeEgg, ThrownEggEntity.class) == 1,
                    "Phải sinh 1 ThrownEggEntity trong World");

            // Camera Dynamic FOV Sprint/Fly giữ nguyên khi bowZoomOffset thay đổi
            Camera fovCam = new Camera();
            fovCam.updateDynamicFov(true, false, 1.0f);
            float sprintFov = fovCam.getCurrentFovDeg();
            require(sprintFov > 77.0f, "Sprint Dynamic FOV phải mở rộng gần 78 độ");
            fovCam.updateBowZoom(-8.0f, 1.0f);
            require(fovCam.getCurrentFovDeg() < sprintFov - 7.0f,
                    "Bow Zoom phải trừ mượt vào FOV hiệu dụng mà không xóa trạng thái Sprint FOV");
            fovCam.setBowZoomOffset(0f);
            near(fovCam.getCurrentFovDeg(), sprintFov,
                    "Khi xóa bowZoomOffset, Sprint Dynamic FOV phải giữ nguyên không bị ảnh hưởng");

            // 8. Kiểm tra ưu tiên tương tác mob trước vật phẩm & Block trả false để đặt khối
            Wolf tamedWolf = new Wolf(12.5f, 11.0f, 20.5f);
            world.addLivingEntity(tamedWolf);
            player.setPosition(10.5f, 11.0f, 20.5f);
            camera.setPosition(10.5f, 12.62f, 20.5f);
            camera.rotate(0f, -25f);
            player.getHotbar().setSlot(7, Item.BONE, 1);
            player.getHotbar().selectSlot(7);
            require(handler.onSecondaryPress(world, player, camera),
                    "Click chuột phải vào Wolf với Bone phải được xử lý ở ưu tiên 1");
            camera.rotate(0f, 25f);
            if (!tamedWolf.isTamed()) {
                tamedWolf.interactWithPlayer(player, Item.BONE, 0.1f); // Đảm bảo đã thuần hóa và đang đứng
            }
            require(tamedWolf.isTamed() && !tamedWolf.isSitting(), "Wolf phải đã thuần hóa và đang đứng");

            player.getHotbar().setSlot(7, Block.STONE, 16);
            // Nhìn ra khoảng không/khối không có mob để kiểm tra nhánh fallback đặt Block
            player.setPosition(10.5f, 11.0f, 25.5f);
            camera.setPosition(10.5f, 12.62f, 25.5f);
            require(!handler.onSecondaryPress(world, player, camera),
                    "Cầm Block không trúng mob phải trả false để VoxelGameApp đặt khối");

            // Sói đã thuần hóa tự đánh trả khi chủ tấn công hoặc bị đánh qua Player.attack / Player.damage
            player.stopMotion();
            player.update(world, 0.70f);
            Zombie packThreat = new Zombie(15.5f, 11.0f, 20.5f, false);
            world.addLivingEntity(packThreat);
            player.setPosition(14.0f, 11.0f, 20.5f);
            player.attack(world, packThreat, Vector3.X);
            require(tamedWolf.target() == packThreat,
                    "Khi chủ đánh trúng Zombie, sói thuần hóa phải tự động nhắm vào Zombie đó");

            tamedWolf.setTarget(null);
            Zombie hurtThreat = new Zombie(16.5f, 11.0f, 20.5f, false);
            world.addLivingEntity(hurtThreat);
            player.damage(world, 2.0f, DamageSource.MELEE, hurtThreat, 0f, 0f);
            require(tamedWolf.target() == hurtThreat,
                    "Khi chủ bị Zombie đánh, sói thuần hóa phải tự động nhắm vào kẻ tấn công chủ");
        } finally {
            world.dispose();
        }
    }

    /**
     * Kiểm tra xoay vòng CameraMode (F5), cắt tia camera góc nhìn thứ 3 không xuyên tường rắn,
     * View Bobbing tăng theo quãng đường trên mặt đất và swing timer khi vung tay.
     */
    private static void checkCameraModeCycle() {
        World world = World.empty();
        try {
            // Tạo sàn đá tại Y = 10 (mặt đứng Y = 11)
            for (int x = 2; x <= 30; x++) {
                for (int z = 8; z <= 14; z++) {
                    world.setBlockInternal(x, 10, z, Block.STONE.getId(), 0);
                }
            }

            Camera camera = new Camera();
            camera.rotate(90f, 0f); // Nhìn dọc +X: front = (1, 0, 0)
            Player player = new Player(15.5f, 11.0f, 11.5f);
            player.attachControls(camera);
            world.registerPlayer(player);

            // 1. Mặc định FIRST_PERSON và xoay vòng 3 lần quay về FIRST_PERSON
            require(player.getCameraMode() == Player.CameraMode.FIRST_PERSON,
                    "Player mặc định phải ở CameraMode.FIRST_PERSON");
            player.cycleCameraMode();
            require(player.getCameraMode() == Player.CameraMode.THIRD_BACK,
                    "Nhấn F5 lần 1 phải chuyển sang THIRD_BACK");
            player.cycleCameraMode();
            require(player.getCameraMode() == Player.CameraMode.THIRD_FRONT,
                    "Nhấn F5 lần 2 phải chuyển sang THIRD_FRONT");
            player.cycleCameraMode();
            require(player.getCameraMode() == Player.CameraMode.FIRST_PERSON,
                    "Nhấn F5 lần 3 phải quay lại FIRST_PERSON");

            // 2. computeThirdPersonCameraPos khi không có tường -> lùi đủ 4.0f block
            Vector3 outCam = new Vector3();
            player.cycleCameraMode(); // THIRD_BACK
            player.computeThirdPersonCameraPos(world, camera, outCam);
            near(outCam.x, 15.5f - 4.0f, "THIRD_BACK không có tường phải lùi đủ 4.0f block về -X");
            near(outCam.y, 11.0f + 1.62f, "THIRD_BACK giữ đúng cao độ mắt khi pitch = 0");

            player.cycleCameraMode(); // THIRD_FRONT
            player.computeThirdPersonCameraPos(world, camera, outCam);
            near(outCam.x, 15.5f + 4.0f, "THIRD_FRONT không có tường phải tiến đủ 4.0f block về +X");

            // 3. Đặt tường đá sát sau lưng ở X = 13 (mặt +X của khối ở 14.0) -> camera THIRD_BACK bị rút ngắn trước mặt tường
            for (int y = 11; y <= 14; y++) {
                for (int z = 10; z <= 13; z++) {
                    world.setBlockInternal(13, y, z, Block.STONE.getId(), 0);
                }
            }
            player.cycleCameraMode(); // FIRST_PERSON
            player.cycleCameraMode(); // THIRD_BACK
            player.computeThirdPersonCameraPos(world, camera, outCam);
            require(outCam.x > 14.0f && outCam.x < 15.5f,
                    "THIRD_BACK khi có tường đá ở X=13 (mặt tại 14.0) phải rút ngắn trước mặt tường (x=" + outCam.x + ")");
            require(world.getCollisionBounds((int) Math.floor(outCam.x), (int) Math.floor(outCam.y),
                    (int) Math.floor(outCam.z)) == null,
                    "Vị trí camera góc nhìn thứ 3 không được xuyên vào khối rắn");

            // Đặt tường đá phía trước ở X = 17 (mặt -X của khối ở 17.0) -> THIRD_FRONT rút ngắn trước mặt tường
            for (int y = 11; y <= 14; y++) {
                for (int z = 10; z <= 13; z++) {
                    world.setBlockInternal(17, y, z, Block.STONE.getId(), 0);
                }
            }
            player.cycleCameraMode(); // THIRD_FRONT
            player.computeThirdPersonCameraPos(world, camera, outCam);
            require(outCam.x > 15.5f && outCam.x < 17.0f,
                    "THIRD_FRONT khi có tường đá ở X=17 phải rút ngắn trước mặt tường (x=" + outCam.x + ")");

            // Quay về FIRST_PERSON để kiểm tra View Bobbing và Swing Timer
            player.cycleCameraMode(); // FIRST_PERSON

            // 4. View Bobbing tăng khi đi trên mặt đất (onGround && !isFlying) và đứng yên khi đang bay
            player.setPosition(20.5f, 11.0f, 11.5f);
            float initialBob = player.getBobPhase();
            Input previousInput = Gdx.input;
            boolean[] keys = new boolean[256];
            keys[Input.Keys.W] = true;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> method.getName().equals("isKeyPressed") && keys[(int) args[0]]);
            try {
                // Cho chạm đất và đi bộ 10 khung hình
                for (int i = 0; i < 10; i++) {
                    player.update(world, 0.05f);
                }
                float walkedBob = player.getBobPhase();
                require(player.isOnGround() && !player.isFlying(), "Player phải đang đứng trên mặt đất");
                require(walkedBob > initialBob + 0.5f,
                        "View Bobbing (bobPhase) phải tăng theo quãng di chuyển trên mặt đất (" + walkedBob + ")");
                require(player.getBobStrength() > 0.1f,
                        "Cường độ View Bobbing phải tăng > 0 khi đang đi bộ trên mặt đất");

                // Bật bay (double-tap Space) -> bobPhase không được tăng thêm khi đang bay
                keys[Input.Keys.SPACE] = true;
                player.update(world, 0.05f);
                keys[Input.Keys.SPACE] = false;
                player.update(world, 0.05f);
                keys[Input.Keys.SPACE] = true;
                player.update(world, 0.05f);
                keys[Input.Keys.SPACE] = false;
                require(player.isFlying(), "Player phải vào trạng thái bay khi nhấn đúp Space");
                float bobBeforeFly = player.getBobPhase();
                for (int i = 0; i < 6; i++) {
                    player.update(world, 0.05f);
                }
                near(player.getBobPhase(), bobBeforeFly,
                        "bobPhase không được tăng khi đang bay (isFlying == true)");
            } finally {
                Gdx.input = previousInput;
            }

            // 5. Swing timer kích hoạt khi vung tay và tự tắt sau khi hết chu kỳ
            near(player.getSwingProgress(), 0.0f, "Ban đầu swingProgress phải bằng 0");
            player.triggerSwing();
            require(player.isSwinging(), "Sau triggerSwing(), isSwinging() phải true");
            player.update(world, 0.05f);
            float midSwing = player.getSwingProgress();
            require(midSwing > 0.0f && midSwing < 1.0f,
                    "Trong khi vung tay, swingProgress phải nằm trong (0, 1) (thực tế: " + midSwing + ")");
            for (int i = 0; i < 10; i++) {
                player.update(world, 0.05f);
            }
            require(!player.isSwinging(), "Sau khi hoàn thành chu kỳ vung tay, isSwinging() phải false");
            near(player.getSwingProgress(), 0.0f, "Sau khi vung tay xong, swingProgress phải về 0");

            // 6. Khởi tạo & giải phóng FirstPersonRenderer an toàn trong môi trường CPU không có OpenGL
            FirstPersonRenderer fpRenderer = new FirstPersonRenderer();
            fpRenderer.dispose();
            fpRenderer.dispose();
        } finally {
            world.dispose();
        }
    }

    /**
     * Kiểm tra Knockback bật nảy chuẩn Minecraft (MobEntity 4.8f, Player 4.5f, pushDecay 6.5f)
     * và bộ đếm sự kiện SFX trúng đòn giữa mọi thực thể (Player <-> Mob, Mob <-> Mob, đạn đạo) trên CPU.
     */
    private static void checkEntityKnockbackAndHitSfx() {
        World world = World.empty();
        try {
            // Tạo sàn đá tại Y = 10 (bề mặt đứng ở Y = 11.0)
            for (int x = 4; x <= 32; x++) {
                for (int z = 4; z <= 20; z++) {
                    world.setBlockInternal(x, 10, z, Block.STONE.getId(), 0);
                }
            }

            Camera camera = new Camera();
            camera.rotate(90f, 0f);
            Player player = new Player(10.5f, 11.0f, 10.5f);
            player.attachControls(camera);
            world.registerPlayer(player);
            // Đưa Player vào trạng thái chạm đất ổn định
            for (int i = 0; i < 3; i++) {
                player.update(world, 0.05f);
            }
            require(player.isOnGround(), "Player phải đang đứng trên mặt đất trước khi thử knockback");

            // 1. Player đánh Zombie đứng trên mặt đất -> Zombie bật nảy (vy >= 4.0f) & có xung đẩy ngang + tăng hitSound
            Zombie zombie = new Zombie(12.5f, 11.0f, 10.5f, false);
            world.addLivingEntity(zombie);
            zombie.update(world, 0.05f);
            zombie.update(world, 0.05f);
            require(zombie.isOnGround() && !zombie.inWater(), "Zombie phải đứng trên mặt đất khô");

            int hitSoundsBefore = world.getEntityHitSoundCount();
            player.attack(world, zombie, Vector3.X);
            require(zombie.getHealth() < zombie.getMaxHealth(), "Player phải đánh trúng Zombie");
            require(zombie.getVelocity().y >= 4.0f,
                    "Zombie đứng trên mặt đất bị đánh phải bật nảy với vy >= 4.0f (thực tế: "
                            + zombie.getVelocity().y + ")");
            require(zombie.pushX() > 0f,
                    "Zombie bị đánh phải có vận tốc đẩy lùi ngang khác 0 (pushX=" + zombie.pushX() + ")");
            require(!zombie.isOnGround(), "Zombie bật nảy phải tạm rời mặt đất");
            require(world.getEntityHitSoundCount() >= hitSoundsBefore + 1,
                    "Player đánh trúng Zombie phải tăng bộ đếm entityHitSoundCount");

            zombie.update(world, 0.05f);
            require(zombie.y() > 11.0f,
                    "Sau 1 tick, Zombie phải bật nảy lên khỏi mặt đất (y=" + zombie.y() + ")");
            zombie.discard();

            // 2. Zombie cận chiến đánh trúng Player đứng trên mặt đất -> Player bật nảy & bị đẩy lùi + tăng hitSound
            Player victimPlayer = new Player(10.5f, 11.0f, 14.5f);
            Camera victimCam = new Camera();
            victimPlayer.attachControls(victimCam);
            world.registerPlayer(victimPlayer);
            for (int i = 0; i < 3; i++) {
                victimPlayer.update(world, 0.05f);
            }
            require(victimPlayer.isOnGround() && !victimPlayer.isFlying(),
                    "victimPlayer phải đang đứng trên mặt đất");

            Zombie meleeAttacker = new Zombie(11.3f, 11.0f, 14.5f, false);
            world.addLivingEntity(meleeAttacker);
            int hitSoundsBeforeMelee = world.getEntityHitSoundCount();
            for (int tick = 0; tick < 10 && victimPlayer.getHealth() >= 20f; tick++) {
                meleeAttacker.update(world, 0.05f);
            }
            require(victimPlayer.getHealth() < 20f, "Zombie phải đánh trúng Player ở cự ly gần");
            require(victimPlayer.getVelocity().y >= 4.0f,
                    "Player đứng trên mặt đất bị Zombie đánh phải bật nảy với vy >= 4.0f (thực tế: "
                            + victimPlayer.getVelocity().y + ")");
            require(Math.abs(victimPlayer.getPushX()) > 0.01f || Math.abs(victimPlayer.getPushZ()) > 0.01f,
                    "Player bị Zombie đánh phải nhận lực đẩy lùi ngang khác 0");
            require(world.getEntityHitSoundCount() >= hitSoundsBeforeMelee + 1,
                    "Zombie đánh trúng Player phải tăng bộ đếm entityHitSoundCount");
            victimPlayer.update(world, 0.05f);
            require(victimPlayer.getPosition().y > 11.0f,
                    "Sau khi bị đánh, Player phải bật lên khỏi mặt đất (y=" + victimPlayer.getPosition().y + ")");
            meleeAttacker.discard();

            // 3. Mob <-> Mob (Skeleton bắn ArrowEntity trúng Mob đứng trên mặt đất) -> bật nảy, đẩy lùi & tăng hitSound
            MobEntity shooter = new MobEntity(MobType.SKELETON, 25.0f, 11.0f, 10.5f);
            MobEntity arrowVictim = new MobEntity(MobType.CREEPER, 28.0f, 11.0f, 10.5f) {
                @Override
                protected void updateGoals(World w, float dt) {
                    // Cô lập vật lý knockback khỏi chuyển động ngẫu nhiên của RandomStrollGoal
                }
            };
            world.addLivingEntity(shooter);
            world.addLivingEntity(arrowVictim);
            arrowVictim.update(world, 0.05f);
            arrowVictim.update(world, 0.05f);
            require(arrowVictim.isOnGround(), "Mob mục tiêu phải đứng trên mặt đất trước khi trúng tên");

            int hitSoundsBeforeArrow = world.getEntityHitSoundCount();
            ArrowEntity arrow = new ArrowEntity(shooter, 26.0f, 11.5f, 10.5f, 35.0f, 0.0f, 0.0f,
                    ArrowType.NORMAL, 4.0f);
            world.addEntity(arrow);
            for (int tick = 0; tick < 10 && !arrow.isFinished(); tick++) {
                arrow.update(world, 0.05f);
            }
            require(arrow.isFinished() && arrowVictim.getHealth() < arrowVictim.getMaxHealth(),
                    "Mũi tên từ Skeleton phải bắn trúng Mob mục tiêu");
            require(arrowVictim.getVelocity().y >= 4.0f,
                    "Mob bị đạn đạo đánh trúng trên mặt đất phải bật nảy vy >= 4.0f (thực tế: "
                            + arrowVictim.getVelocity().y + ")");
            require(arrowVictim.pushX() > 0f,
                    "Mob bị đạn đạo đánh trúng phải nhận lực đẩy lùi ngang (pushX=" + arrowVictim.pushX() + ")");
            require(world.getEntityHitSoundCount() >= hitSoundsBeforeArrow + 1,
                    "Đạn đạo trúng Mob phải tăng bộ đếm entityHitSoundCount");
            arrowVictim.update(world, 0.05f);
            require(arrowVictim.y() > 11.0f && arrowVictim.x() > 28.0f,
                    "Sau 1 tick, Mob bị trúng tên phải vừa bật lên (y=" + arrowVictim.y() + ") vừa bị đẩy lùi ngang (x="
                            + arrowVictim.x() + ")");

            // 4. Mob ở dưới nước không bật nảy lên trời khi bị đánh
            world.setBlockInternal(26, 11, 10, Block.WATER.getId(), 0);
            Zombie waterZombie = new Zombie(26.5f, 11.0f, 10.5f, false);
            world.addLivingEntity(waterZombie);
            waterZombie.update(world, 0.05f);
            require(waterZombie.inWater(), "Zombie đứng trong nước phải có inWater() == true");
            waterZombie.damage(world, 2.0f, DamageSource.MELEE, player, 2.0f, 0.0f);
            require(waterZombie.getVelocity().y < 4.0f,
                    "Mob đang ở trong nước không được bật nảy knockback lên cao");

            // 5. Kiểm tra suy giảm âm lượng tuyến tính trong 16 block của AudioManager
            near(AudioManager.mobGain(0f, 16f), 1.0f, "Tại khoảng cách 0 block, gain phải là 1.0");
            near(AudioManager.mobGain(8f * 8f, 16f), 0.5f, "Tại khoảng cách 8/16 block, gain phải là 0.5");
            near(AudioManager.mobGain(16f * 16f, 16f), 0.0f, "Ngoài 16 block, gain phải tắt về 0.0");
        } finally {
            world.dispose();
        }
    }

    /** Đếm số ArrowEntity hiện có trong thế giới kiểm thử. */
    private static int countArrows(World world) {
        return countTypeSince(world, 0, ArrowEntity.class);
    }

    /** Đếm số thực thể thuộc lớp chỉ định được thêm kể từ chỉ số startIndex. */
    private static int countTypeSince(World world, int startIndex, Class<?> clazz) {
        int count = 0;
        for (int i = startIndex; i < world.entityCount(); i++) {
            if (clazz.isInstance(world.entityAt(i))) {
                count++;
            }
        }
        return count;
    }

    /** Tìm ArrowEntity mới nhất được thêm kể từ chỉ số startIndex. */
    private static ArrowEntity findNewestArrow(World world, int startIndex) {
        for (int i = world.entityCount() - 1; i >= startIndex; i--) {
            if (world.entityAt(i) instanceof ArrowEntity arrow) {
                return arrow;
            }
        }
        return null;
    }

    /** Đếm số cá thể Chicken con đang tồn tại trong thế giới kiểm thử. */
    private static int countBabyChickens(World world) {
        int count = 0;
        for (int i = 0; i < world.entityCount(); i++) {
            if (world.entityAt(i) instanceof Chicken chicken && chicken.isBaby()) {
                count++;
            }
        }
        return count;
    }

    /** Kiểm tra giá trị thực tế khớp giá trị kỳ vọng trong sai số nhỏ. */
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.001f,
                message + " (" + actual + " thay vì " + expected + ")");
    }

    /** Ném AssertionError nếu điều kiện kiểm thử không thỏa mãn. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
