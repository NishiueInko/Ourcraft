package com.voxelgame.engine;

import com.voxelgame.client.gui.BitmapFont;
import com.voxelgame.client.gui.CreativeInventoryScreen;
import com.voxelgame.client.gui.HudRenderer;
import com.voxelgame.client.gui.InventoryScreen;
import com.voxelgame.client.gui.PauseScreen;
import com.voxelgame.client.gui.Screen;
import com.voxelgame.core.Interactable;
import com.voxelgame.core.Renderable;
import com.voxelgame.core.Tickable;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.mob.EquineMob;
import com.voxelgame.world.entity.mob.HostileMob;
import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.mob.MobSpawnTable;
import com.voxelgame.world.entity.mob.MountableMob;
import com.voxelgame.world.entity.mob.NeutralMob;
import com.voxelgame.world.entity.mob.PassiveMob;
import com.voxelgame.world.entity.mob.RangedMob;
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
import com.voxelgame.world.entity.mob.animal.SnowFox;
import com.voxelgame.world.entity.mob.animal.Wolf;
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
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.entity.projectile.ProjectileEntity;
import com.voxelgame.world.gamemode.CreativeGameMode;
import com.voxelgame.world.gamemode.GameMode;
import com.voxelgame.world.gamemode.SurvivalGameMode;
import com.voxelgame.world.item.BlockItem;
import com.voxelgame.world.item.BowItem;
import com.voxelgame.world.item.BucketItem;
import com.voxelgame.world.item.ConsumableItem;
import com.voxelgame.world.item.FlintAndSteelItem;
import com.voxelgame.world.item.FoodItem;
import com.voxelgame.world.item.FoodRegistry;
import com.voxelgame.world.item.GoatHornItem;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.item.ItemUseHandler;
import com.voxelgame.world.item.PotionItem;
import com.voxelgame.world.item.ShearsItem;
import com.voxelgame.world.item.SimpleItem;
import com.voxelgame.world.item.SpearItem;
import com.voxelgame.world.item.ThrowableItem;
import com.voxelgame.world.item.ToolItem;
import com.voxelgame.world.level.LeafDecaySystem;
import com.voxelgame.world.level.LiquidFlowSimulator;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.biome.Biome;
import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.biome.DesertBiome;
import com.voxelgame.world.level.biome.FlowerForestBiome;
import com.voxelgame.world.level.biome.ForestBiome;
import com.voxelgame.world.level.biome.PlainsBiome;
import com.voxelgame.world.level.biome.SnowSubZone;
import com.voxelgame.world.level.biome.SnowyPlainsBiome;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.block.GravityBlock;
import com.voxelgame.world.level.block.LeavesBlock;
import com.voxelgame.world.level.block.LiquidBlock;
import com.voxelgame.world.level.block.LogBlock;
import com.voxelgame.world.level.block.PlantBlock;
import com.voxelgame.world.level.block.SolidBlock;
import com.voxelgame.world.level.weather.ClearWeather;
import com.voxelgame.world.level.weather.RainWeather;
import com.voxelgame.world.level.weather.SnowWeather;
import com.voxelgame.world.level.weather.Weather;
import com.voxelgame.world.level.weather.WeatherManager;

import com.badlogic.gdx.math.Vector3;
import java.lang.reflect.Modifier;

/**
 * Bộ kiểm thử CPU xác nhận kiến trúc Hướng đối tượng (OOP) chuẩn Minecraft:
 * kiểm tra cây kế thừa, tính trừu tượng, giao diện hợp đồng và hành vi đa hình.
 */
public final class OopArchitectureChecks {

    private OopArchitectureChecks() {
    }

    /** Chạy toàn bộ các kiểm tra kiến trúc OOP hiện hành. */
    public static void run() {
        checkBlockHierarchy();
        checkBiomeAndWeatherHierarchy();
        checkEntityAndMobHierarchy();
        checkItemHierarchyAndDispatch();
        checkGameModeHierarchy();
        checkClientScreenHierarchy();
    }

    /** Xác nhận hệ phân cấp OOP của GameMode (Trừu tượng, Kế thừa, Đóng gói và Đa hình). */
    private static void checkGameModeHierarchy() {
        requireAbstract(GameMode.class);
        requireSubclass(GameMode.class, CreativeGameMode.class, SurvivalGameMode.class);
        GameMode creative = CreativeGameMode.INSTANCE;
        GameMode survival = SurvivalGameMode.INSTANCE;
        require(creative.toggle() == survival && survival.toggle() == creative,
                "GameMode.toggle() phải điều phối đa hình giữa Creative và Survival");
    }

    /** Xác nhận Block đã tách khỏi Entity, đủ lớp trừu tượng trung gian và bảo toàn 33 ID 0..32. */
    private static void checkBlockHierarchy() {
        require(!Entity.class.isAssignableFrom(Block.class),
                "Block không được kế thừa Entity (sửa vi phạm LSP)");
        require(Tickable.class.isAssignableFrom(Entity.class),
                "Entity phải triển khai Tickable");
        require(Renderable.class.isAssignableFrom(Entity.class),
                "Entity phải triển khai Renderable");
        require(Tickable.class.isAssignableFrom(LeafDecaySystem.class),
                "LeafDecaySystem phải triển khai Tickable");
        require(Tickable.class.isAssignableFrom(LiquidFlowSimulator.class),
                "LiquidFlowSimulator phải triển khai Tickable");

        requireAbstract(Block.class);
        requireAbstract(SolidBlock.class);
        requireAbstract(GravityBlock.class);
        requireAbstract(LiquidBlock.class);
        requireAbstract(LogBlock.class);
        requireAbstract(LeavesBlock.class);
        requireAbstract(PlantBlock.class);

        require(Block.SAND instanceof GravityBlock && Block.GRAVEL instanceof GravityBlock,
                "Sand và Gravel phải kế thừa GravityBlock");
        require(Block.WATER instanceof LiquidBlock && Block.LAVA instanceof LiquidBlock,
                "Water và Lava phải kế thừa LiquidBlock");
        require(Block.OAK_LOG instanceof LogBlock && Block.SPRUCE_LOG instanceof LogBlock,
                "OakLog và SpruceLog phải kế thừa LogBlock");
        require(Block.OAK_LEAVES instanceof LeavesBlock && Block.SPRUCE_LEAVES instanceof LeavesBlock,
                "OakLeaves và SpruceLeaves phải kế thừa LeavesBlock");
        require(Block.DIRT instanceof SolidBlock
                        && Block.STONE instanceof SolidBlock
                        && Block.BEDROCK instanceof SolidBlock
                        && Block.SANDSTONE instanceof SolidBlock
                        && Block.SNOW_BLOCK instanceof SolidBlock
                        && Block.WHITE_WOOL instanceof SolidBlock
                        && Block.GRASS instanceof SolidBlock
                        && Block.CHEST instanceof SolidBlock,
                "Các khối rắn đục phải kế thừa SolidBlock");

        for (int id = 0; id <= 32; id++) {
            Block block = Block.fromId((byte) id);
            require(block != null && block.getId() == (byte) id,
                    "ID block " + id + " phải giữ nguyên ánh xạ trong Block.fromId");
        }
    }

    /** Xác nhận hệ phân cấp đa hình Biome (5 lớp con) và Weather (3 lớp con, State/Strategy). */
    private static void checkBiomeAndWeatherHierarchy() {
        requireAbstract(Biome.class);
        requireAbstract(Weather.class);

        for (BiomeType type : BiomeType.values()) {
            Biome b1 = Biome.of(type);
            Biome b2 = type.toBiome();
            require(b1 != null && b1 == b2,
                    "Biome.of và BiomeType.toBiome phải trả về cùng singleton Flyweight cho " + type);
            require(b1.getType() == type, "Biome.getType() phải khớp " + type);
        }

        require(Biome.of(BiomeType.PLAINS) instanceof PlainsBiome, "PLAINS phải là PlainsBiome");
        require(Biome.of(BiomeType.FOREST) instanceof ForestBiome, "FOREST phải là ForestBiome");
        require(Biome.of(BiomeType.FLOWER_FOREST) instanceof FlowerForestBiome, "FLOWER_FOREST phải là FlowerForestBiome");
        require(Biome.of(BiomeType.DESERT) instanceof DesertBiome, "DESERT phải là DesertBiome");
        require(Biome.of(BiomeType.SNOWY_PLAINS) instanceof SnowyPlainsBiome, "SNOWY_PLAINS phải là SnowyPlainsBiome");

        checkBiomeColors(BiomeType.PLAINS, 0x91BD59, 0x77AB2F);
        checkBiomeColors(BiomeType.FOREST, 0x79C05A, 0x59AE30);
        checkBiomeColors(BiomeType.FLOWER_FOREST, 0x88BB67, 0x6BA941);
        checkBiomeColors(BiomeType.DESERT, 0xBFB755, 0xAEA42A);
        checkBiomeColors(BiomeType.SNOWY_PLAINS, 0x80B497, 0x60A17B);

        require(Biome.of(BiomeType.PLAINS).resolvePrecipitationWeather() == RainWeather.INSTANCE,
                "PLAINS phải chuyển sang RainWeather khi có giáng thủy");
        require(Biome.of(BiomeType.FOREST).resolvePrecipitationWeather() == RainWeather.INSTANCE,
                "FOREST phải chuyển sang RainWeather khi có giáng thủy");
        require(Biome.of(BiomeType.FLOWER_FOREST).resolvePrecipitationWeather() == RainWeather.INSTANCE,
                "FLOWER_FOREST phải chuyển sang RainWeather khi có giáng thủy");
        require(Biome.of(BiomeType.SNOWY_PLAINS).resolvePrecipitationWeather() == SnowWeather.INSTANCE,
                "SNOWY_PLAINS phải chuyển sang SnowWeather khi có giáng thủy");
        require(Biome.of(BiomeType.DESERT).resolvePrecipitationWeather() == ClearWeather.INSTANCE,
                "DESERT phải giữ ClearWeather ngay cả khi có giáng thủy toàn cục");

        require(Biome.of(BiomeType.PLAINS).getSpawnTable(MobCategory.CREATURE, SnowSubZone.SNOWY_PLAINS_FLAT)
                == MobSpawnTable.forBiome(BiomeType.PLAINS, MobCategory.CREATURE, SnowSubZone.SNOWY_PLAINS_FLAT),
                "Biome.getSpawnTable phải đồng bộ với MobSpawnTable.forBiome");

        require("CLEAR".equals(ClearWeather.INSTANCE.getLabel())
                && !ClearWeather.INSTANCE.extinguishesFire()
                && !ClearWeather.INSTANCE.accumulatesSnow(),
                "ClearWeather phải có thuộc tính trời quang");
        require("RAIN".equals(RainWeather.INSTANCE.getLabel())
                && RainWeather.INSTANCE.extinguishesFire()
                && !RainWeather.INSTANCE.accumulatesSnow(),
                "RainWeather phải dập lửa và không tích tuyết");
        require("SNOW".equals(SnowWeather.INSTANCE.getLabel())
                && !SnowWeather.INSTANCE.extinguishesFire()
                && SnowWeather.INSTANCE.accumulatesSnow(),
                "SnowWeather phải tích tuyết và không dập lửa");

        World world = World.empty();
        try {
            WeatherManager weather = new WeatherManager(World.WORLD_SEED);
            Vector3 eye = new Vector3();
            require(weather.currentWeather() == ClearWeather.INSTANCE,
                    "Trạng thái thời tiết ban đầu phải là ClearWeather");

            weather.toggle();
            weather.update(5f, world, eye.set(2000.5f, 35f, 1024.5f), null);
            require(weather.currentWeather() == RainWeather.INSTANCE,
                    "Khi bật giáng thủy ở Plains phải là RainWeather");

            weather.update(1f, world, eye.set(200.5f, 35f, 1024.5f), null);
            require(weather.currentWeather() == SnowWeather.INSTANCE,
                    "Khi di chuyển sang Snowy Plains trong lúc giáng thủy phải là SnowWeather");

            weather.update(1f, world, eye.set(3700.5f, 35f, 1024.5f), null);
            require(weather.currentWeather() == ClearWeather.INSTANCE,
                    "Khi di chuyển sang Desert phải là ClearWeather");
        } finally {
            world.dispose();
        }
    }

    /** Xác nhận hệ phân cấp Entity/Mob (abstract, LSP Horse/Donkey) và điều phối Interactable 2 lượt. */
    private static void checkEntityAndMobHierarchy() {
        requireAbstract(Entity.class);
        requireAbstract(LivingEntity.class);
        requireAbstract(MobEntity.class);
        requireAbstract(HostileMob.class);
        requireAbstract(PassiveMob.class);
        requireAbstract(NeutralMob.class);
        requireAbstract(MountableMob.class);
        requireAbstract(EquineMob.class);
        requireAbstract(RangedMob.class);
        requireAbstract(ProjectileEntity.class);

        requireSubclass(HostileMob.class, Zombie.class, ZombieVillager.class, Husk.class, Creeper.class,
                RangedMob.class, Skeleton.class, Stray.class, Parched.class, Witch.class);
        requireSubclass(PassiveMob.class, Cow.class, Sheep.class, Pig.class, Chicken.class, Rabbit.class);
        requireSubclass(NeutralMob.class, Spider.class, Enderman.class, Goat.class, PolarBear.class,
                Wolf.class, SnowFox.class);
        requireSubclass(EquineMob.class, Horse.class, Donkey.class);
        require(!Horse.class.isAssignableFrom(Donkey.class),
                "Donkey không được kế thừa Horse (giữ nguyên lý thay thế Liskov qua EquineMob)");
        requireSubclass(MountableMob.class, EquineMob.class, Horse.class, Donkey.class, Camel.class, CamelHusk.class);

        requireSubclass(Interactable.class, Sheep.class, Cow.class, Goat.class, Wolf.class, MountableMob.class);
        require(!Interactable.class.isAssignableFrom(Zombie.class)
                && !Interactable.class.isAssignableFrom(Creeper.class),
                "Zombie và Creeper không được triển khai Interactable");

        // Kiểm tra điều phối 2 lượt: Flint and Steel ưu tiên đốt mọi mob (kể cả mob không phải Interactable và Sheep)
        World world = World.empty();
        try {
            Player player = new Player(10.5f, 10f, 10.5f);
            player.getHotbar().setSlot(0, Item.FLINT_AND_STEEL, 1);
            player.getHotbar().selectSlot(0);

            Zombie zombie = new Zombie(11.5f, 10f, 10.5f);
            require(zombie.onPlayerInteract(world, player) && zombie.getFireTimer() > 0f,
                    "Flint and Steel phải đốt được Zombie dù Zombie không triển khai Interactable");

            Sheep sheep = new Sheep(12.5f, 10f, 10.5f);
            require(sheep.onPlayerInteract(world, player) && sheep.getFireTimer() > 0f && !sheep.isSheared(),
                    "Flint and Steel phải ưu tiên đốt Sheep trước tương tác cắt len");

            player.getHotbar().setSlot(0, Item.SHEARS, 1);
            require(sheep.onPlayerInteract(world, player) && sheep.isSheared(),
                    "Shears phải kích hoạt Interactable.onInteract cắt len Sheep");
        } finally {
            world.dispose();
        }
    }

    /** Xác nhận hệ phân cấp Item đa hình (13 lớp con), 56 standalone items và 19 FoodItem khớp FoodRegistry. */
    private static void checkItemHierarchyAndDispatch() {
        requireAbstract(Item.class);
        requireAbstract(ConsumableItem.class);
        requireAbstract(ToolItem.class);

        require(Item.allStandaloneItems().size() == 56,
                "Phải bảo toàn đúng 56 vật phẩm độc lập trong Item.allStandaloneItems()");
        require(FoodRegistry.count() == 19,
                "FoodRegistry phải giữ đúng 19 món ăn chuẩn Minecraft");

        require(Item.BOW instanceof BowItem, "Item.BOW phải là BowItem");
        require(Item.IRON_SPEAR instanceof SpearItem, "Item.IRON_SPEAR phải là SpearItem");
        require(Item.ENDER_PEARL instanceof ThrowableItem && Item.EGG instanceof ThrowableItem,
                "ENDER_PEARL và EGG phải là ThrowableItem");
        require(Item.SHEARS instanceof ShearsItem, "Item.SHEARS phải là ShearsItem");
        require(Item.FLINT_AND_STEEL instanceof FlintAndSteelItem, "Item.FLINT_AND_STEEL phải là FlintAndSteelItem");
        require(Item.BUCKET instanceof BucketItem, "Item.BUCKET phải là BucketItem");
        require(Item.GOAT_HORN instanceof GoatHornItem, "Item.GOAT_HORN phải là GoatHornItem");
        require(Item.LEATHER instanceof SimpleItem && Item.ARROW instanceof SimpleItem,
                "Vật phẩm nguyên liệu/mũi tên phải là SimpleItem");

        Item[] allFoods = {
                Item.BEEF, Item.COOKED_BEEF, Item.PORKCHOP, Item.COOKED_PORKCHOP,
                Item.MUTTON, Item.COOKED_MUTTON, Item.CHICKEN, Item.COOKED_CHICKEN,
                Item.RABBIT, Item.COOKED_RABBIT, Item.COD, Item.COOKED_COD,
                Item.SALMON, Item.COOKED_SALMON, Item.CARROT, Item.POTATO,
                Item.BAKED_POTATO, Item.ROTTEN_FLESH, Item.SPIDER_EYE
        };
        require(allFoods.length == 19, "Danh sách kiểm tra thức ăn phải đủ 19 món");
        for (Item item : allFoods) {
            require(item instanceof FoodItem food && food.getFoodSpec().equals(FoodRegistry.of(item)),
                    item.getDisplayName() + " phải là FoodItem khớp hoàn toàn với FoodRegistry");
        }

        require(Item.POTION_HEALING instanceof PotionItem
                        && Item.POTION_FIRE_RESISTANCE instanceof PotionItem
                        && Item.POTION_WATER_BREATHING instanceof PotionItem
                        && Item.POTION_SWIFTNESS instanceof PotionItem
                        && Item.MILK_BUCKET instanceof PotionItem,
                "4 thuốc Witch và MILK_BUCKET phải là PotionItem");

        require(Item.WHITE_WOOL instanceof BlockItem
                        && Item.LIGHT_GRAY_WOOL instanceof BlockItem
                        && Item.GRAY_WOOL instanceof BlockItem
                        && Item.BLACK_WOOL instanceof BlockItem
                        && Item.BROWN_WOOL instanceof BlockItem
                        && Item.PINK_WOOL instanceof BlockItem,
                "6 vật phẩm len phải là BlockItem");
        require(Item.of(Block.WHITE_WOOL) == Item.WHITE_WOOL,
                "Liên kết 2 chiều Block.WHITE_WOOL -> Item.WHITE_WOOL phải nguyên vẹn");
        require(Item.of(Block.STONE) instanceof BlockItem,
                "Item.of(Block.STONE) phải là BlockItem");

        require(Item.SHEARS.getMiningSpeedMultiplier(Block.WHITE_WOOL) == 5.0f
                        && Item.SHEARS.getMiningSpeedMultiplier(Block.OAK_LEAVES) == 5.0f
                        && Item.SHEARS.getMiningSpeedMultiplier(Block.STONE) == 1.0f
                        && Item.of(Block.STONE).getMiningSpeedMultiplier(Block.WHITE_WOOL) == 1.0f,
                "ShearsItem.getMiningSpeedMultiplier phải trả 5.0f cho len/lá và 1.0f cho khối khác");

        require(Item.IRON_SPEAR.getMeleeReach() == ItemUseHandler.SPEAR_JAB_REACH
                        && Item.IRON_SPEAR.getMeleeDamage() == ItemUseHandler.SPEAR_JAB_DAMAGE,
                "SpearItem phải cung cấp đúng thông số Jab đa hình");
    }

    /** Xác nhận hệ phân cấp màn hình Client/GUI (Screen, PauseScreen, InventoryScreen, BitmapFont, HudRenderer). */
    private static void checkClientScreenHierarchy() {
        requireAbstract(Screen.class);
        requireSubclass(Screen.class, PauseScreen.class, InventoryScreen.class, CreativeInventoryScreen.class);

        PauseScreen pauseScreen = new PauseScreen();
        InventoryScreen inventoryScreen = new InventoryScreen();
        CreativeInventoryScreen creativeScreen = new CreativeInventoryScreen();
        try {
            require(pauseScreen.pausesWorld(), "PauseScreen phải tạm dừng vòng cập nhật thế giới");
            require(!inventoryScreen.pausesWorld(), "InventoryScreen không được tạm dừng thế giới");
            require(!creativeScreen.pausesWorld(), "CreativeInventoryScreen không được tạm dừng thế giới");
            require(!pauseScreen.isOpen() && !inventoryScreen.isOpen() && !creativeScreen.isOpen(),
                    "Màn hình Screen mặc định phải ở trạng thái đóng");
            pauseScreen.open();
            require(pauseScreen.isOpen(), "open() phải mở PauseScreen");
            pauseScreen.close();
            require(!pauseScreen.isOpen(), "close() phải đóng PauseScreen");
        } finally {
            pauseScreen.dispose();
            inventoryScreen.dispose();
            creativeScreen.dispose();
        }

        require(com.badlogic.gdx.utils.Disposable.class.isAssignableFrom(Screen.class)
                        && com.badlogic.gdx.utils.Disposable.class.isAssignableFrom(HudRenderer.class),
                "Screen và HudRenderer phải triển khai Disposable");

        char[] requiredChars = {'A', 'Z', 'a', 'z', '0', '9', ':', '%', '-', '+', '.', '/', ',', '(', ')', '='};
        for (char c : requiredChars) {
            require(BitmapFont.hasGlyph(c), "BitmapFont phải cung cấp glyph 5x7 cho ký tự '" + c + "'");
        }
        require(BitmapFont.measureWidth("HELLO", 2.0f) == 5 * 6.0f * 2.0f,
                "BitmapFont.measureWidth phải tính đúng bề rộng chuỗi 5x7");
    }

    /** Xác nhận mọi lớp con đều kế thừa hoặc triển khai từ lớp/giao diện cha. */
    private static void requireSubclass(Class<?> parent, Class<?>... children) {
        for (Class<?> child : children) {
            require(parent.isAssignableFrom(child),
                    child.getSimpleName() + " phải kế thừa/triển khai " + parent.getSimpleName());
        }
    }

    /** Xác nhận màu cỏ và màu lá của từng lớp con Biome khớp bảng chuẩn. */
    private static void checkBiomeColors(BiomeType type, int expectedGrass, int expectedFoliage) {
        Biome biome = Biome.of(type);
        require(biome.getGrassColorRgb() == expectedGrass && biome.getFoliageColorRgb() == expectedFoliage,
                "Màu cỏ/lá của " + type + " không khớp đặc tả");
    }

    /** Xác nhận lớp mục tiêu có khai báo abstract. */
    private static void requireAbstract(Class<?> type) {
        require(Modifier.isAbstract(type.getModifiers()),
                type.getSimpleName() + " phải là lớp abstract");
    }

    /** Ném lỗi ngay khi điều kiện kiến trúc không đạt. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("OopArchitectureChecks thất bại: " + message);
        }
    }
}
