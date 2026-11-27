package com.voxelgame.engine;

import com.voxelgame.VoxelGameApp;
import com.voxelgame.client.gui.CreativeInventoryScreen;
import com.voxelgame.client.gui.DebugOverlayRenderer;
import com.voxelgame.client.gui.HudRenderer;
import com.voxelgame.client.gui.InventoryScreen;
import com.voxelgame.client.render.Camera;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.mob.monster.Zombie;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.gamemode.CreativeGameMode;
import com.voxelgame.world.gamemode.GameMode;
import com.voxelgame.world.gamemode.SurvivalGameMode;
import com.voxelgame.world.item.CreativeCatalog;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.item.ItemUseHandler;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.block.WoolBlock;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputEventQueue;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Bộ kiểm thử CPU (headless, không cần OpenGL) xác nhận toàn bộ hệ thống Chế độ chơi
 * Creative / Survival ({@code Ctrl + M}), quy tắc khóa bay/{@code F1}/{@code Ctrl + C} ở Survival,
 * phá Bedrock tức thì không rơi đồ ở Creative, danh mục {@link CreativeCatalog} và màn hình
 * {@link CreativeInventoryScreen}.
 */
public final class GameModeChecks {

    private GameModeChecks() {
    }

    /** Chạy toàn bộ các kiểm tra cho hệ thống GameMode và Kho Creative. */
    public static void run() {
        checkPolymorphicGameModeContracts();
        checkModeTransitionAndSurvivalLocks();
        checkCreativeInvulnerabilityAndMobAggro();
        checkBedrockAndInstantBreakNoDrops();
        checkInfiniteItemsInCreative();
        checkF2ToggleAndScreenSync();
        checkCreativeCatalogCompleteness();
        checkCreativeInventoryInteractions();
        checkDebugOverlayAndToastZeroGc();
    }

    /** 1. Xác nhận hợp đồng đa hình và Flyweight zero-GC của CreativeGameMode & SurvivalGameMode. */
    private static void checkPolymorphicGameModeContracts() {
        GameMode creative = CreativeGameMode.INSTANCE;
        GameMode survival = SurvivalGameMode.INSTANCE;

        require(creative.canFly() && !survival.canFly(), "Chỉ Creative được phép bay");
        require(creative.canUseTerrainTools() && !survival.canUseTerrainTools(),
                "Chỉ Creative được dùng F1 và Ctrl+C");
        require(creative.isInvulnerable() && !survival.isInvulnerable(),
                "Creative phải bất tử, Survival phải chịu sát thương");
        require(!creative.consumesItems() && survival.consumesItems(),
                "Creative không trừ đồ, Survival phải trừ đồ");
        require(creative.breaksInstantly() && !survival.breaksInstantly(),
                "Creative phải phá khối tức thì");
        require(creative.canBreakBlock(Block.BEDROCK) && !survival.canBreakBlock(Block.BEDROCK),
                "Creative phải phá được Bedrock, Survival không được phá Bedrock");
        require(!creative.canBreakBlock(Block.AIR) && !creative.canBreakBlock(Block.WATER),
                "Creative không phá Air hay chất lỏng");
        require(!creative.dropsBlockItems() && survival.dropsBlockItems(),
                "Creative không rơi ItemEntity khi phá khối");
        require(!creative.showsSurvivalHud() && survival.showsSurvivalHud(),
                "Creative ẩn thanh sinh tồn trên HUD, Survival hiển thị đầy đủ");
        require(!creative.isTargetableByMobs() && survival.isTargetableByMobs(),
                "Mob bỏ qua người chơi ở Creative và nhắm mục tiêu ở Survival");
        require(creative.usesCreativeInventory() && !survival.usesCreativeInventory(),
                "Creative mở Kho Creative, Survival mở túi đồ 36 ô");
        require(creative.toggle() == survival && survival.toggle() == creative,
                "toggle() phải đảo qua lại 2 thể hiện Flyweight zero-GC");
    }

    /** 2. Xác nhận chuyển Creative -> Survival tắt bay, đưa F1 về 1x và khóa bay/F1/Ctrl+C ở Survival. */
    private static void checkModeTransitionAndSurvivalLocks() {
        World world = World.empty();
        try {
            Player player = new Player(100.5f, 30f, 100.5f);
            require(player.getGameMode() == SurvivalGameMode.INSTANCE,
                    "new Player() phải mặc định Survival để bảo toàn 28 bộ test cũ");

            // Ở Survival: khóa bay, khóa F1, khóa Ctrl+C
            player.setFlying(true);
            require(!player.isFlying(), "Survival không được phép bật bay");
            player.cycleTerrainSpeed();
            require(player.getTerrainSpeedMultiplier() == 1, "Survival phải khóa F1 ở mức 1x");
            require(!player.teleportToBiome(world, BiomeType.DESERT),
                    "Survival phải khóa dịch chuyển biome Ctrl+C");

            // Sang Creative: bật bay và nâng F1 lên 5x
            player.setGameMode(CreativeGameMode.INSTANCE);
            player.setFlying(true);
            player.cycleTerrainSpeed();
            player.cycleTerrainSpeed();
            require(player.isFlying() && player.getTerrainSpeedMultiplier() == 5,
                    "Creative phải bật được bay và đổi mức tốc độ F1");

            // Chuyển về Survival: tự động tắt bay và đưa F1 về 1x
            player.toggleGameMode();
            require(player.getGameMode() == SurvivalGameMode.INSTANCE, "toggleGameMode() phải quay về Survival");
            require(!player.isFlying(), "Chuyển sang Survival phải tự động tắt chế độ bay");
            require(player.getTerrainSpeedMultiplier() == 1,
                    "Chuyển sang Survival phải đặt tốc độ F1 về 1x");
        } finally {
            world.dispose();
        }
    }

    /** 3. Xác nhận Creative bất tử, không tụt Oxy/lửa và mob thù địch bỏ qua / nhả mục tiêu. */
    private static void checkCreativeInvulnerabilityAndMobAggro() {
        World world = World.empty();
        try {
            for (int x = 18; x <= 24; x++) {
                for (int z = 18; z <= 24; z++) {
                    world.setBlockInternal(x, 9, z, Block.STONE.getId(), 0);
                }
            }
            Player player = new Player(20.5f, 10f, 20.5f);
            world.registerPlayer(player);

            // Ở Survival: nhận sát thương và bị Zombie nhắm làm mục tiêu
            require(player.damage(world, 6f, DamageSource.MELEE, null, 0f, 0f),
                    "Survival phải nhận sát thương bình thường");
            require(player.getHealth() < 20f, "HP ở Survival phải giảm sau đòn đánh");
            Zombie zombie = new Zombie(22.5f, 10f, 20.5f);
            world.addEntity(zombie);
            zombie.setTarget(player);
            require(zombie.target() == player, "Zombie phải nhắm được người chơi ở Survival");

            // Chuyển sang Creative: hồi đầy sinh lực, miễn sát thương và Zombie tự nhả mục tiêu
            player.setGameMode(CreativeGameMode.INSTANCE);
            require(player.getHealth() == player.getMaxHealth(), "Vào Creative phải hồi đầy máu");
            require(!player.damage(world, 10f, DamageSource.FALL, null, 0f, 0f),
                    "Creative phải miễn mọi sát thương");
            zombie.update(world, 0.05f);
            require(zombie.target() == null, "Zombie phải tự nhả mục tiêu khi người chơi sang Creative");
            zombie.setTarget(player);
            require(zombie.target() == null, "Mob không được phép nhận người chơi Creative làm mục tiêu");

            // Ngập nước ở Creative không tụt Oxy
            world.setBlockInternal(20, 10, 20, Block.WATER.getId(), 0);
            world.setBlockInternal(20, 11, 20, Block.WATER.getId(), 0);
            for (int i = 0; i < 60; i++) {
                player.update(world, 0.05f);
            }
            require(player.getAirTicks() == Player.AIR_MAX_TICKS, "Creative không được tụt Oxy dưới nước");
        } finally {
            world.dispose();
        }
    }

    /** 4. Xác nhận phá Bedrock tức thì không sinh ItemEntity trong Creative. */
    private static void checkBedrockAndInstantBreakNoDrops() {
        World world = World.empty();
        try {
            world.setBlockInternal(10, 10, 10, Block.BEDROCK.getId(), 0);
            world.setBlockInternal(11, 10, 10, Block.STONE.getId(), 0);

            // Đường Survival không phá được Bedrock
            world.breakBlockAndSpawnDrop(10, 10, 10);
            require(world.getBlock(10, 10, 10) == Block.BEDROCK.getId(),
                    "Survival breakBlockAndSpawnDrop không được phép phá Bedrock");

            // Đường Creative phá được cả Bedrock và Stone mà không sinh ItemEntity
            int entitiesBefore = world.entityCount();
            require(world.breakBlockWithoutDrop(10, 10, 10), "Creative phải phá được Bedrock");
            require(world.getBlock(10, 10, 10) == Block.AIR.getId(), "Bedrock phải biến thành Air sau khi phá");
            require(world.breakBlockWithoutDrop(11, 10, 10), "Creative phải phá được Stone");
            require(world.getBlock(11, 10, 10) == Block.AIR.getId(), "Stone phải biến thành Air");

            int itemEntities = 0;
            for (int i = entitiesBefore; i < world.entityCount(); i++) {
                if (world.entityAt(i) instanceof ItemEntity) {
                    itemEntities++;
                }
            }
            require(itemEntities == 0, "Phá khối ở Creative không được sinh ItemEntity rơi rác");
        } finally {
            world.dispose();
        }
    }

    /** 5. Xác nhận Creative bắn cung không cần mũi tên và không trừ đồ khi ném Egg / uống thuốc. */
    private static void checkInfiniteItemsInCreative() {
        World world = World.empty();
        try {
            Player player = new Player(30.5f, 20f, 30.5f);
            Camera camera = new Camera();
            camera.setPosition(30.5f, 21.62f, 30.5f);
            player.attachControls(camera);
            player.setGameMode(CreativeGameMode.INSTANCE);
            world.registerPlayer(player);

            Hotbar hotbar = player.getHotbar();
            for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
                hotbar.setSlot(i, Item.NONE, 0);
            }

            // Bắn cung không cần mũi tên trong Creative
            hotbar.setSlot(0, Item.BOW, 1);
            hotbar.selectSlot(0);
            ItemUseHandler handler = new ItemUseHandler();
            require(Item.BOW.canStartContinuousUse(world, player),
                    "Creative phải kéo được cung ngay cả khi không có mũi tên");
            int beforeBow = world.entityCount();
            require(handler.onSecondaryPress(world, player, camera), "Phải bắt đầu kéo cung ở Creative");
            handler.update(0.5f, world, player, camera);
            require(handler.onSecondaryRelease(world, player, camera), "Phải bắn được tên ở Creative");
            require(world.entityCount() > beforeBow, "Bắn cung ở Creative phải sinh ArrowEntity");

            // Ném Egg không giảm số lượng stack
            hotbar.setSlot(1, Item.EGG, 4);
            hotbar.selectSlot(1);
            require(handler.onSecondaryPress(world, player, camera), "Phải ném được Egg ở Creative");
            require(hotbar.getSlotCount(1) == 4, "Ném Egg ở Creative không được trừ số lượng");
        } finally {
            world.dispose();
        }
    }

    /** 6. Xác nhận phím F2 đổi chế độ, tự đóng màn hình túi đồ đang mở, Ctrl+M không còn tác dụng và phím E chọn đúng màn hình. */
    private static void checkF2ToggleAndScreenSync() {
        World world = World.empty();
        InventoryScreen invScreen = new InventoryScreen();
        CreativeInventoryScreen creativeScreen = new CreativeInventoryScreen();
        try {
            Player player = new Player(50.5f, 20f, 50.5f);
            player.setGameMode(CreativeGameMode.INSTANCE);
            VoxelGameApp app = new VoxelGameApp();
            setField(app, "world", world);
            setField(app, "player", player);
            setField(app, "inventoryScreen", invScreen);
            setField(app, "creativeInventoryScreen", creativeScreen);

            InputEventQueue queue = new InputEventQueue();

            // Ctrl + M không còn đổi chế độ
            queue.keyDown(Input.Keys.CONTROL_LEFT, 1);
            queue.keyDown(Input.Keys.M, 2);
            queue.keyUp(Input.Keys.M, 3);
            queue.keyUp(Input.Keys.CONTROL_LEFT, 4);
            queue.drain(app);
            require(player.getGameMode() == CreativeGameMode.INSTANCE,
                    "Ctrl+M đã bị xóa, không được phép đổi chế độ");

            // Nhấn E ở Creative mở CreativeInventoryScreen
            queue.keyDown(Input.Keys.E, 5);
            queue.keyUp(Input.Keys.E, 6);
            queue.drain(app);
            require(creativeScreen.isOpen() && !invScreen.isOpen(),
                    "Phím E ở Creative phải mở CreativeInventoryScreen");

            // Nhấn F2 khi đang mở Kho Creative: đóng Kho Creative và chuyển sang Survival
            queue.keyDown(Input.Keys.F2, 7);
            queue.keyUp(Input.Keys.F2, 8);
            queue.drain(app);
            require(player.getGameMode() == SurvivalGameMode.INSTANCE,
                    "F2 phải chuyển từ Creative sang Survival");
            require(!creativeScreen.isOpen() && !invScreen.isOpen(),
                    "F2 phải đóng màn hình túi đồ đang mở để tránh lệch UI");

            // Nhấn E ở Survival mở InventoryScreen 36 ô
            queue.keyDown(Input.Keys.E, 9);
            queue.keyUp(Input.Keys.E, 10);
            queue.drain(app);
            require(invScreen.isOpen() && !creativeScreen.isOpen(),
                    "Phím E ở Survival phải mở InventoryScreen 36 ô");

            // Nhấn F2 lần nữa quay lại Creative
            queue.keyDown(Input.Keys.F2, 11);
            queue.keyUp(Input.Keys.F2, 12);
            queue.drain(app);
            require(player.getGameMode() == CreativeGameMode.INSTANCE,
                    "F2 phải chuyển ngược từ Survival về Creative");
            require(!invScreen.isOpen(), "F2 phải đóng InventoryScreen đang mở");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Không thiết lập được VoxelGameApp để kiểm tra F2", e);
        } finally {
            invScreen.dispose();
            creativeScreen.dispose();
            world.dispose();
        }
    }

    /** 7. Xác nhận CreativeCatalog chứa đủ 100% khối hợp lệ (gồm Bedrock) & 56 vật phẩm độc lập, không trùng. */
    private static void checkCreativeCatalogCompleteness() {
        Item[] all = CreativeCatalog.getItems(CreativeCatalog.Tab.ALL);
        Set<Item> unique = new HashSet<>();
        for (Item item : all) {
            require(item != null && !item.isNone(), "CreativeCatalog không được chứa phần tử null/NONE");
            require(unique.add(item), "CreativeCatalog không được trùng lặp vật phẩm: " + item.getDisplayName());
        }

        // Mọi khối hợp lệ trừ AIR, WATER, LAVA và 6 WoolBlock (đã có qua Item.*_WOOL) đều phải có mặt
        for (int id = 1; id < 256; id++) {
            Block b = Block.fromId((byte) id);
            if (b == null || b == Block.AIR || b.isLiquid() || b instanceof WoolBlock) {
                continue;
            }
            require(unique.contains(Item.of(b)),
                    "CreativeCatalog thiếu khối ID: " + Byte.toUnsignedInt(b.getId()));
        }
        require(unique.contains(Item.of(Block.BEDROCK)), "CreativeCatalog phải chứa Bedrock");

        // Mọi vật phẩm độc lập (56 món gồm 6 len, 19 thức ăn, vũ khí, công cụ, thuốc...) đều phải có mặt
        for (Item standalone : Item.allStandaloneItems()) {
            require(unique.contains(standalone),
                    "CreativeCatalog thiếu vật phẩm độc lập: " + standalone.getDisplayName());
        }

        // Cả 5 tab đều có dữ liệu hợp lệ
        for (CreativeCatalog.Tab tab : CreativeCatalog.Tab.values()) {
            require(CreativeCatalog.getItemCount(tab) > 0,
                    "Tab " + tab + " trong CreativeCatalog không được rỗng");
        }
    }

    /** 8. Xác nhận tương tác chuột trên CreativeInventoryScreen (lấy full stack, lấy 1, Shift+Click, cuộn, X). */
    private static void checkCreativeInventoryInteractions() {
        CreativeInventoryScreen screen = new CreativeInventoryScreen();
        try {
            Player player = new Player(0f, 30f, 0f);
            for (int s = 0; s < 9; s++) {
                player.getInventory().setSlot(s, Item.NONE, 0);
            }
            screen.open();
            int screenW = 1280;
            int screenH = 720;

            // Tính đúng tâm ô catalog đầu tiên (0, 0) ở scale = 3 (1280/240=5, 720/200=3 -> scale=3)
            float scale = 3.0f;
            float panelX = Math.round((screenW - 196.0f * scale) * 0.5f);
            float panelY = Math.round((screenH - 152.0f * scale) * 0.5f);
            float firstSlotX = panelX + (8.0f + 8.0f) * scale;
            float firstSlotY = panelY + (24.0f + 8.0f) * scale;
            Item firstItem = CreativeCatalog.getItem(CreativeCatalog.Tab.ALL, 0);

            // Click trái lấy full stack lên con trỏ
            require(screen.handleClick(player.getInventory(), firstSlotX, firstSlotY,
                    screenW, screenH, CreativeInventoryScreen.LEFT_CLICK, false),
                    "Click trái vào ô danh mục Creative phải lấy đồ");
            require(screen.getCursorItem() == firstItem
                            && screen.getCursorCount() == firstItem.getMaxStackSize(),
                    "Click trái phải nhặt đủ full stack");

            // Click vào ô Hotbar 0 đặt stack xuống
            float hotbar0X = panelX + (8.0f + 8.0f) * scale;
            float hotbar0Y = panelY + (122.0f + 8.0f) * scale;
            require(screen.handleClick(player.getInventory(), hotbar0X, hotbar0Y,
                    screenW, screenH, CreativeInventoryScreen.LEFT_CLICK, false),
                    "Click vào ô Hotbar 0 phải đặt stack xuống");
            require(player.getInventory().getItem(0) == firstItem
                            && player.getInventory().getCount(0) == firstItem.getMaxStackSize(),
                    "Ô Hotbar 0 phải nhận đúng vật phẩm vừa lấy");

            // Click phải vào ô danh mục lấy đúng 1 đơn vị
            require(screen.handleClick(player.getInventory(), firstSlotX, firstSlotY,
                    screenW, screenH, CreativeInventoryScreen.RIGHT_CLICK, false),
                    "Click phải vào ô danh mục phải lấy 1 đơn vị");
            require(screen.getCursorItem() == firstItem && screen.getCursorCount() == 1,
                    "Click phải phải đặt cursorCount = 1");

            // Click ô Thùng rác [X] xóa đồ đang cầm; Shift+Click [X] xóa sạch cả 9 ô Hotbar
            float trashX = panelX + (174.0f + 8.0f) * scale;
            float trashY = panelY + (122.0f + 8.0f) * scale;
            require(screen.handleClick(player.getInventory(), trashX, trashY,
                    screenW, screenH, CreativeInventoryScreen.LEFT_CLICK, false),
                    "Click ô Thùng rác phải xóa đồ trên con trỏ");
            require(screen.getCursorItem().isNone() && screen.getCursorCount() == 0,
                    "Đồ trên con trỏ phải bị xóa sau khi bấm [X]");
            require(screen.handleClick(player.getInventory(), trashX, trashY,
                    screenW, screenH, CreativeInventoryScreen.LEFT_CLICK, true),
                    "Shift+Click [X] phải dọn sạch Hotbar");
            require(player.getInventory().getCount(0) == 0, "Ô Hotbar 0 phải trống sau Shift+Click [X]");

            // Shift+Click vào ô danh mục đẩy thẳng 1 full stack vào Hotbar
            require(screen.handleClick(player.getInventory(), firstSlotX, firstSlotY,
                    screenW, screenH, CreativeInventoryScreen.LEFT_CLICK, true),
                    "Shift+Click ô danh mục phải thêm thẳng vào Hotbar");
            require(player.getInventory().getItem(0) == firstItem,
                    "Hotbar 0 phải nhận vật phẩm sau Shift+Click");

            // Kiểm tra cuộn xuống và cuộn lên
            require(screen.getMaxScrollRow() > 0, "Tab ALL phải có nhiều hơn 45 món để cuộn được");
            screen.scroll(1.0f);
            require(screen.getScrollRow() == 1, "scroll(+1) phải tăng scrollRow lên 1");
            screen.scroll(-1.0f);
            require(screen.getScrollRow() == 0, "scroll(-1) phải giảm scrollRow về 0");
        } finally {
            screen.dispose();
        }
    }

    /** 9. Xác nhận DebugOverlayRenderer (F3) hiển thị dòng MODE zero-GC và HudRenderer toast hoạt động. */
    private static void checkDebugOverlayAndToastZeroGc() {
        DebugOverlayRenderer debug = new DebugOverlayRenderer();
        HudRenderer hud = new HudRenderer();
        World world = World.empty();
        try {
            Player player = new Player(10f, 20f, 10f);
            Camera camera = new Camera();
            player.setGameMode(CreativeGameMode.INSTANCE);

            StringBuilder modeBufBefore = debug.getLineBuffer(7);
            debug.formatLines(world, player, camera, 60);
            require(debug.getLineBuffer(7) == modeBufBefore,
                    "Dòng MODE trên F3 phải tái sử dụng StringBuilder (zero-GC)");
            require(debug.getLineBuffer(7).toString().contains("CREATIVE"),
                    "F3 phải hiển thị MODE: CREATIVE khi ở Creative");

            player.toggleGameMode();
            debug.formatLines(world, player, camera, 60);
            require(debug.getLineBuffer(7).toString().contains("SURVIVAL"),
                    "F3 phải hiển thị MODE: SURVIVAL khi ở Survival");

            hud.showGameModeToast(CreativeGameMode.INSTANCE);
            require("MODE: CREATIVE".equals(hud.getActiveModeToastLabel()),
                    "HudRenderer phải lưu nhãn toast MODE: CREATIVE");
            hud.updateToast(3.0f);
            require(hud.getActiveModeToastLabel() == null,
                    "Toast phải tự ẩn sau khi hết thời lượng hiển thị");
        } finally {
            debug.dispose();
            hud.dispose();
            world.dispose();
        }
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("GameModeChecks thất bại: " + message);
        }
    }
}
