package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Item;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.PlayerInventory;
import com.voxelgame.entity.StatusEffectType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;

/**
 * Kiểm tra hồi quy CPU cho các hệ thống sinh tồn, túi đồ 36 ô và môi trường của Track C.
 */
public final class SurvivalWorldChecks {

    private SurvivalWorldChecks() {
    }

    /** Chạy toàn bộ các ca kiểm thử của Track C. */
    public static void run() {
        checkInventoryAndDrop();
        checkInventorySlotInteractions();
        checkOxygenAndSprintSwimming();
        checkLeafDecayAndShearsSpeed();
        checkLiquidFlowAndStoneReaction();
        checkGuiTexturesPresent();
    }

    /**
     * Kiểm tra túi đồ 36 ô đồng bộ với Hotbar 9 ô và thao tác vứt vật phẩm (Q / Ctrl+Q).
     */
    private static void checkInventoryAndDrop() {
        World world = World.empty();
        try {
            world.setBlockInternal(10, 10, 10, Block.STONE.getId(), 0);
            world.setBlockInternal(10, 10, 9, Block.STONE.getId(), 0);

            Player player = new Player(10.5f, 11f, 10.5f);
            Camera camera = new Camera();
            player.attachControls(camera);
            world.registerPlayer(player);

            PlayerInventory inv = player.getInventory();
            require(inv != null, "Player phải sở hữu PlayerInventory");
            require(PlayerInventory.TOTAL_SLOTS == 36, "Tổng số ô túi đồ phải bằng 36");
            require(inv.getHotbar() == player.getHotbar(), "Hotbar phải đồng bộ với 9 ô đầu của PlayerInventory");

            // Lấp đầy toàn bộ 9 ô Hotbar (ô 5 vốn trống sẽ chứa tối đa 16 Egg theo maxStackSize)
            player.getHotbar().setSlot(5, Item.EGG, 20);
            require(inv.getItem(5) == Item.EGG && inv.getCount(5) == 16,
                    "Egg trong ô 5 phải bị giới hạn đúng maxStackSize = 16");

            // Khi 9 ô Hotbar đã đầy, addItem qua Hotbar phải tự tràn sang ô 9 của PlayerInventory
            require(player.getHotbar().addItem(Item.IRON_INGOT, 10),
                    "Thêm vật phẩm khi Hotbar đầy phải tràn sang balo 27 ô");
            require(inv.getItem(9) == Item.IRON_INGOT && inv.getCount(9) == 10,
                    "Iron Ingot phải nằm tại ô đầu tiên của balo (ô 9)");

            // Kiểm tra cộng dồn và tách stack qua trần 64 giữa các ô balo
            require(inv.addItem(Item.IRON_INGOT, 60), "Phải cộng dồn được vào ô 9 và tràn sang ô 10");
            require(inv.getItem(9) == Item.IRON_INGOT && inv.getCount(9) == 64,
                    "Ô 9 phải đầy đúng 64 Iron Ingot");
            require(inv.getItem(10) == Item.IRON_INGOT && inv.getCount(10) == 6,
                    "Phần dư 6 Iron Ingot phải tràn sang ô 10");

            // Kiểm tra consumeFromSlot
            require(inv.consumeFromSlot(9, 10) == 10 && inv.getCount(9) == 54,
                    "consumeFromSlot(9, 10) phải lấy đúng 10 và còn 54");
            require(inv.consumeFromSlot(9, 100) == 54 && inv.getItem(9) == Item.NONE && inv.getCount(9) == 0,
                    "Tiêu thụ hết ô 9 phải đưa ô về Item.NONE");

            // Lấp đầy toàn bộ 36 ô để xác nhận từ chối khi túi đồ không còn chỗ
            for (int slot = 9; slot < PlayerInventory.TOTAL_SLOTS; slot++) {
                inv.setSlot(slot, Item.IRON_INGOT, 64);
            }
            require(!inv.addItem(Item.IRON_INGOT, 1), "Khi cả 36 ô đều đầy thì addItem phải trả về false");

            // Giải phóng 1 ô balo để kiểm tra vứt đồ và nhặt lại sau hết delay
            inv.setSlot(9, Item.NONE, 0);

            // Chọn ô 0 chứa 16 Dirt, gọi dropSelectedItem(world, false)
            player.getHotbar().selectSlot(0);
            require(player.getHotbar().getSelectedItem() == Item.of(Block.DIRT)
                    && player.getHotbar().getSelectedCount() == 16, "Ô 0 ban đầu phải có 16 Dirt");

            ItemEntity singleDrop = player.dropSelectedItem(world, false);
            require(singleDrop != null, "Vứt 1 vật phẩm phải tạo ItemEntity");
            require(player.getHotbar().getSelectedCount() == 15, "Sau khi vứt 1 Dirt ô 0 phải còn 15");
            require(inv.getCount(0) == 15, "PlayerInventory ô 0 phải đồng bộ còn 15");
            require(singleDrop.getItem() == Item.of(Block.DIRT) && singleDrop.getCount() == 1,
                    "ItemEntity vứt lẻ phải mang 1 Dirt");
            require(singleDrop.getPickupDelay() >= 1.0f,
                    "ItemEntity ném bằng Q phải có pickupDelay >= 1.0s để không hút lại ngay");

            Vector3 front = camera.getFront();
            float dotFront = (singleDrop.getPosition().x - player.getPosition().x) * front.x
                    + (singleDrop.getPosition().z - player.getPosition().z) * front.z;
            require(dotFront > 0f, "ItemEntity ném ra phải nằm phía trước mặt người chơi");

            // Đảm bảo ngay cả khi đứng sát người chơi trước 1.0s, ItemEntity vẫn chưa bị hút lại
            for (int i = 0; i < 6; i++) {
                singleDrop.setPosition(player.getPosition().x, player.getPosition().y + 0.9f, player.getPosition().z);
                singleDrop.update(world, player, player.getHotbar(), 0.05f);
            }
            require(!singleDrop.isCollected() && player.getHotbar().getSelectedCount() == 15,
                    "Không được tự nhặt lại ItemEntity khi chưa qua pickupDelay >= 1.0s");

            // Tiếp tục vứt cả stack còn lại (15 Dirt) với wholeStack = true
            ItemEntity stackDrop = player.dropSelectedItem(world, true);
            require(stackDrop != null, "Vứt cả stack phải tạo ItemEntity");
            require(player.getHotbar().getSelectedItem() == Item.NONE && player.getHotbar().getSelectedCount() == 0,
                    "Sau khi vứt cả stack ô 0 phải trống (Item.NONE, count 0)");
            require(inv.getItem(0) == Item.NONE && inv.getCount(0) == 0,
                    "PlayerInventory ô 0 phải trống sau khi vứt cả stack");
            require(stackDrop.getItem() == Item.of(Block.DIRT) && stackDrop.getCount() == 15,
                    "ItemEntity vứt cả stack phải mang đủ 15 Dirt");
            require(stackDrop.getPickupDelay() >= 1.0f,
                    "ItemEntity vứt cả stack cũng phải có pickupDelay >= 1.0s");

            // Vứt khi ô đang trống phải trả về null
            require(player.dropSelectedItem(world, false) == null,
                    "Vứt trên ô trống không được sinh ItemEntity");

            // Cho stackDrop trôi qua thời gian pickupDelay >= 1.0s rồi đặt sát tâm người chơi để nhặt lại
            for (int i = 0; i < 21; i++) {
                stackDrop.update(world, player, player.getHotbar(), 0.05f);
            }
            require(!stackDrop.isCollected(), "Stack bay ra xa phía trước chưa được nhặt khi ngoài tầm nam châm");
            stackDrop.setPosition(player.getPosition().x, player.getPosition().y + 0.9f, player.getPosition().z);
            stackDrop.update(world, player, player.getHotbar(), 0.05f);
            require(stackDrop.isCollected() && inv.getItem(0) == Item.of(Block.DIRT) && inv.getCount(0) == 15,
                    "Sau khi hết pickupDelay, người chơi phải nhặt lại đủ 15 Dirt vào ô trống");
        } finally {
            world.dispose();
        }
    }

    /**
     * Kiểm tra tương tác kéo thả, chia đôi stack, đặt từng đơn vị và Shift-Click trên túi đồ 36 ô.
     */
    private static void checkInventorySlotInteractions() {
        InventoryScreenRenderer screen = new InventoryScreenRenderer();
        World world = World.empty();
        try {
            Player player = new Player(10.5f, 11f, 10.5f);
            PlayerInventory inv = player.getInventory();

            require(!screen.isOpen(), "Màn hình túi đồ mặc định phải đóng");
            screen.toggle(player, world);
            require(screen.isOpen(), "toggle() phải mở màn hình túi đồ");

            // 1. Hoán đổi 2 ô bằng LEFT_CLICK: ô 9 (balo) và ô 0 (Hotbar)
            inv.setSlot(9, Block.STONE, 10);
            inv.setSlot(0, Block.OAK_LOG, 5);
            require(screen.handleSlotInteraction(inv, 9, InventoryScreenRenderer.LEFT_CLICK, false),
                    "Nhấn chuột trái vào ô 9 phải nhặt stack lên con trỏ");
            require(screen.getCursorItem() == Item.of(Block.STONE) && screen.getCursorCount() == 10,
                    "Con trỏ phải cầm 10 Stone");
            require(inv.getItem(9) == Item.NONE && inv.getCount(9) == 0,
                    "Ô 9 phải trống sau khi nhặt lên con trỏ");

            require(screen.handleSlotInteraction(inv, 0, InventoryScreenRenderer.LEFT_CLICK, false),
                    "Nhấn chuột trái vào ô 0 phải hoán đổi Stone và Oak Log");
            require(inv.getItem(0) == Item.of(Block.STONE) && inv.getCount(0) == 10,
                    "Ô 0 phải nhận 10 Stone");
            require(screen.getCursorItem() == Item.of(Block.OAK_LOG) && screen.getCursorCount() == 5,
                    "Con trỏ phải cầm lại 5 Oak Log từ ô 0");

            require(screen.handleSlotInteraction(inv, 9, InventoryScreenRenderer.LEFT_CLICK, false),
                    "Đặt 5 Oak Log vào ô 9 trống");
            require(inv.getItem(9) == Item.of(Block.OAK_LOG) && inv.getCount(9) == 5,
                    "Ô 9 phải chứa 5 Oak Log sau khi hoàn tất hoán đổi");
            require(screen.getCursorItem() == Item.NONE && screen.getCursorCount() == 0,
                    "Con trỏ phải trống sau khi đặt item xuống");

            // 2. Chia đôi stack bằng RIGHT_CLICK (16 -> cầm 8, để lại 8) và thả 1 đơn vị bằng RIGHT_CLICK
            inv.setSlot(10, Block.DIRT, 16);
            inv.setSlot(11, Item.NONE, 0);
            require(screen.handleSlotInteraction(inv, 10, InventoryScreenRenderer.RIGHT_CLICK, false),
                    "Chuột phải vào ô có 16 Dirt phải chia đôi");
            require(screen.getCursorItem() == Item.of(Block.DIRT) && screen.getCursorCount() == 8,
                    "Con trỏ phải cầm đúng 8 Dirt khi chia đôi 16");
            require(inv.getItem(10) == Item.of(Block.DIRT) && inv.getCount(10) == 8,
                    "Ô 10 phải giữ lại đúng 8 Dirt");

            require(screen.handleSlotInteraction(inv, 11, InventoryScreenRenderer.RIGHT_CLICK, false),
                    "Chuột phải vào ô 11 trống phải thả 1 Dirt");
            require(inv.getItem(11) == Item.of(Block.DIRT) && inv.getCount(11) == 1,
                    "Ô 11 phải nhận đúng 1 Dirt");
            require(screen.getCursorCount() == 7, "Con trỏ phải còn 7 Dirt sau khi thả 1");

            require(screen.handleSlotInteraction(inv, 11, InventoryScreenRenderer.RIGHT_CLICK, false),
                    "Chuột phải lần nữa vào ô 11 cùng loại phải cộng thêm 1 Dirt");
            require(inv.getCount(11) == 2 && screen.getCursorCount() == 6,
                    "Ô 11 phải tăng lên 2 Dirt và con trỏ còn 6");

            // Trả phần còn lại về ô 10
            require(screen.handleSlotInteraction(inv, 10, InventoryScreenRenderer.LEFT_CLICK, false),
                    "Chuột trái vào ô 10 phải gộp 6 Dirt còn lại vào 8 Dirt");
            require(inv.getCount(10) == 14 && screen.getCursorItem() == Item.NONE,
                    "Ô 10 phải thành 14 Dirt và con trỏ trống");

            // 3. SHIFT + LEFT_CLICK chuyển nhanh từ balo (9..35) về Hotbar (0..8) và ngược lại
            PlayerInventory quickInv = new PlayerInventory();
            // Hotbar mặc định: ô 2 có 16 Stone, ô 5 trống
            quickInv.setSlot(9, Block.STONE, 10);
            require(screen.handleSlotInteraction(quickInv, 9, InventoryScreenRenderer.LEFT_CLICK, true),
                    "Shift-Click Stone từ ô balo 9 phải chuyển về Hotbar");
            require(quickInv.getItem(2) == Item.of(Block.STONE) && quickInv.getCount(2) == 26,
                    "Shift-Click phải ưu tiên gộp vào stack Stone sẵn có ở ô Hotbar 2 (16+10=26)");
            require(quickInv.getItem(9) == Item.NONE && quickInv.getCount(9) == 0,
                    "Ô 9 phải trống sau khi chuyển hết Stone về Hotbar");

            // Chuyển item chưa có trên Hotbar -> phải vào ô trống đầu tiên (ô 5)
            quickInv.setSlot(12, Item.IRON_INGOT, 12);
            require(screen.handleSlotInteraction(quickInv, 12, InventoryScreenRenderer.LEFT_CLICK, true),
                    "Shift-Click Iron Ingot từ ô 12 phải chuyển vào ô Hotbar trống đầu tiên");
            require(quickInv.getItem(5) == Item.IRON_INGOT && quickInv.getCount(5) == 12,
                    "Ô Hotbar 5 phải nhận 12 Iron Ingot");
            require(quickInv.getItem(12) == Item.NONE, "Ô 12 phải trống sau khi Shift-Click");

            // Chuyển ngược từ Hotbar ô 5 về balo (phải vào ô trống đầu tiên của balo là ô 9)
            require(screen.handleSlotInteraction(quickInv, 5, InventoryScreenRenderer.LEFT_CLICK, true),
                    "Shift-Click từ Hotbar ô 5 phải chuyển về balo");
            require(quickInv.getItem(9) == Item.IRON_INGOT && quickInv.getCount(9) == 12,
                    "Ô balo 9 phải nhận lại 12 Iron Ingot");
            require(quickInv.getItem(5) == Item.NONE, "Ô Hotbar 5 phải trống sau khi chuyển về balo");

            // 4. Kiểm tra hit-test handleClick theo tọa độ màn hình 1280x720 và close() trả item về kho
            float hitX = -1f;
            float hitY = -1f;
            for (float y = 200f; y <= 550f && hitX < 0f; y += 8f) {
                for (float x = 350f; x <= 930f; x += 8f) {
                    if (screen.getSlotAt(x, y, 1280, 720) == 15) {
                        hitX = x;
                        hitY = y;
                        break;
                    }
                }
            }
            require(hitX >= 0f, "Phải xác định được tọa độ pixel của ô balo 15 trên màn hình 1280x720");
            player.getInventory().setSlot(15, Item.IRON_INGOT, 4);
            require(screen.handleClick(player.getInventory(), hitX, hitY, 1280, 720,
                    InventoryScreenRenderer.LEFT_CLICK, false),
                    "handleClick tại ô 15 phải xử lý nhặt 4 Iron Ingot lên con trỏ");
            require(screen.getCursorItem() == Item.IRON_INGOT && screen.getCursorCount() == 4,
                    "Con trỏ đang cầm 4 Iron Ingot trước khi đóng túi đồ");
            require(player.getInventory().getItem(15) == Item.NONE,
                    "Ô 15 của player phải trống sau khi nhặt lên con trỏ");

            screen.close(player, world);
            require(!screen.isOpen(), "close() phải đóng màn hình túi đồ");
            require(screen.getCursorItem() == Item.NONE && screen.getCursorCount() == 0,
                    "close() phải dọn sạch item đang cầm trên con trỏ");
            require(player.getInventory().getItem(5) == Item.IRON_INGOT
                    && player.getInventory().getCount(5) == 4,
                    "close() phải trả lại 4 Iron Ingot từ con trỏ về ô trống đầu tiên trong túi đồ (ô Hotbar 5)");
        } finally {
            screen.dispose();
            world.dispose();
        }
    }

    /**
     * Kiểm tra thanh Oxy (300 tick / 15 giây), ngạt nước (-2 HP/s), hiệu ứng Water Breathing,
     * hồi Oxy khi lên khỏi nước và Sprint-Swimming (Ctrl + di chuyển trong nước 1.55x).
     */
    private static void checkOxygenAndSprintSwimming() {
        Input previousInput = Gdx.input;
        boolean[] heldKeys = new boolean[256];
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed")) {
                        int key = (int) args[0];
                        return key >= 0 && key < heldKeys.length && heldKeys[key];
                    }
                    throw new AssertionError("Cuộc gọi input ngoài dự kiến: " + method.getName());
                });

        World world = World.empty();
        try {
            // Tạo bể nước kín kích thước đủ rộng để kiểm tra ngạt nước và bơi nhanh
            for (int x = 18; x <= 26; x++) {
                for (int z = 19; z <= 21; z++) {
                    world.setBlockInternal(x, 9, z, Block.STONE.getId(), 0);
                    for (int y = 10; y <= 14; y++) {
                        world.setBlockInternal(x, y, z, Block.WATER.getId(), 0);
                    }
                }
            }
            // Sàn đá khô bên ngoài bể nước để kiểm tra hồi Oxy khi lên bờ
            world.setBlockInternal(30, 10, 20, Block.STONE.getId(), 0);

            Player player = new Player(20.5f, 10.5f, 20.5f);
            Camera camera = new Camera();
            camera.rotate(90f, 0f);
            player.attachControls(camera);

            require(player.getAirTicks() == Player.AIR_MAX_TICKS,
                    "Oxy khởi đầu của người chơi phải bằng AIR_MAX_TICKS = 300");
            require(player.isEyeInWater(world),
                    "Khi đứng ngập trong bể nước, isEyeInWater(world) phải trả về true");

            // 1. Ngập nước liên tục 15.0 giây (300 tick * 0.05s) -> airTicks về 0, chưa mất HP
            for (int i = 0; i < 300; i++) {
                player.update(world, 0.05f);
            }
            require(player.getAirTicks() == 0,
                    "Sau 15.0 giây ngập nước, getAirTicks() phải giảm về 0");
            require(Math.abs(player.getHealth() - 20.0f) < 0.001f,
                    "Ngay khi Oxy vừa chạm 0 ở giây thứ 15 chưa được trừ HP ngạt");

            // 2. Chạy thêm 2.0 giây dưới nước -> mất 2.0 HP/s => HP giảm từ 20 xuống 16
            for (int i = 0; i < 40; i++) {
                player.update(world, 0.05f);
            }
            require(Math.abs(player.getHealth() - 16.0f) < 0.001f,
                    "Sau 2.0 giây ngạt nước phải mất 4 HP (từ 20 xuống 16), thực tế: " + player.getHealth());

            // 3. Lên khỏi nước -> isEyeInWater == false và airTicks hồi nhanh về 300
            player.setPosition(30.5f, 11.0f, 20.5f);
            require(!player.isEyeInWater(world),
                    "Khi đứng trên sàn đá khô, isEyeInWater(world) phải trả về false");
            player.update(world, 0.05f);
            require(player.getAirTicks() > 0,
                    "Ngay khi lên khỏi nước, airTicks phải bắt đầu hồi phục");
            for (int i = 0; i < 60; i++) {
                player.update(world, 0.05f);
            }
            require(player.getAirTicks() == Player.AIR_MAX_TICKS,
                    "Khi lên khỏi nước đủ thời gian ngắn, getAirTicks() phải hồi đầy về 300");

            // 4. Có StatusEffectType.WATER_BREATHING -> giữ nguyên 300 Oxy và không mất HP khi ngập nước
            player.heal(4.0f);
            require(Math.abs(player.getHealth() - 20.0f) < 0.001f,
                    "Hồi máu về 20.0f trước khi kiểm tra Water Breathing");
            player.setPosition(20.5f, 10.5f, 20.5f);
            player.applyEffect(StatusEffectType.WATER_BREATHING, 0, 30.0f);
            for (int i = 0; i < 400; i++) {
                player.update(world, 0.05f);
            }
            require(player.getAirTicks() == Player.AIR_MAX_TICKS,
                    "Khi có WATER_BREATHING, getAirTicks() phải giữ nguyên 300");
            require(Math.abs(player.getHealth() - 20.0f) < 0.001f,
                    "Khi có WATER_BREATHING không được mất HP vì ngạt nước");

            // 5. Kiểm tra bơi thường (không Ctrl) và Sprint-Swimming (Left Ctrl + di chuyển trong nước ~1.55x)
            Player swimmer = new Player(19.5f, 11.5f, 20.5f);
            Camera swimCam = new Camera();
            swimCam.rotate(90f, 0f); // yaw = 0, pitch = 0 (nhìn thẳng +X)
            swimmer.attachControls(swimCam);

            heldKeys[Input.Keys.W] = true;
            heldKeys[Input.Keys.CONTROL_LEFT] = false;
            swimmer.update(world, 1f / 60f);
            Vector3 normalVel = swimmer.getVelocity();
            float normalSwimSpeed = (float) Math.hypot(normalVel.x, normalVel.z);
            require(!swimmer.isSwimSprinting(),
                    "Khi không giữ Left Ctrl, isSwimSprinting() phải là false");
            require(Math.abs(normalSwimSpeed - 5.5f * 0.65f) < 0.02f,
                    "Bơi thường không nhấn Ctrl phải giữ nguyên vận tốc 3.575 block/s, thực tế: " + normalSwimSpeed);

            // Giữ Left Ctrl + W trong Block.WATER -> isSwimSprinting() == true và tốc độ tăng ~1.55x
            swimmer.setPosition(19.5f, 11.5f, 20.5f);
            heldKeys[Input.Keys.CONTROL_LEFT] = true;
            swimmer.update(world, 1f / 60f);
            Vector3 sprintVel = swimmer.getVelocity();
            float sprintSwimSpeed = (float) Math.hypot(sprintVel.x, sprintVel.z);
            require(swimmer.isSwimSprinting(),
                    "Khi ở trong Block.WATER và giữ Left Ctrl + di chuyển, isSwimSprinting() phải trả về true");
            float speedRatio = sprintSwimSpeed / normalSwimSpeed;
            require(Math.abs(speedRatio - 1.55f) < 0.02f,
                    "Tốc độ Sprint-Swimming phải đạt ~1.55x tốc độ bơi thường, thực tế: " + speedRatio);

            // Kiểm tra điều hướng dọc theo pitch khi giữ W + Left Ctrl
            swimCam.rotate(0f, -45f); // nhìn chéo xuống -45 độ
            swimmer.setPosition(20.5f, 11.5f, 20.5f);
            swimmer.update(world, 1f / 60f);
            require(swimmer.isSwimSprinting() && swimmer.getVelocity().y < -1.0f,
                    "Sprint-Swimming khi nhìn xuống phải tạo vận tốc lặn xuống theo góc pitch");

            swimCam.rotate(0f, 90f); // nhìn chéo lên +45 độ
            swimmer.setPosition(20.5f, 11.5f, 20.5f);
            swimmer.update(world, 1f / 60f);
            require(swimmer.isSwimSprinting() && swimmer.getVelocity().y > 1.0f,
                    "Sprint-Swimming khi mắt còn trong nước và nhìn lên phải tạo vận tốc nổi lên theo góc pitch");

            // Kiểm tra trong Block.LAVA không kích hoạt Sprint-Swimming
            world.setBlockInternal(35, 10, 20, Block.LAVA.getId(), 0);
            world.setBlockInternal(35, 11, 20, Block.LAVA.getId(), 0);
            Player lavaWalker = new Player(35.5f, 10.5f, 20.5f);
            lavaWalker.attachControls(swimCam);
            lavaWalker.update(world, 1f / 60f);
            require(!lavaWalker.isSwimSprinting(),
                    "Trong Block.LAVA không được kích hoạt isSwimSprinting()");
        } finally {
            Gdx.input = previousInput;
            world.dispose();
        }
    }

    /**
     * Kiểm tra hệ thống phân hủy lá cây (LeafDecaySystem), bảo vệ lá đặt tay (state = 1),
     * lan truyền khi chặt gỗ và hệ số tốc độ đào Shears 5.0x trên lá/len.
     */
    private static void checkLeafDecayAndShearsSpeed() {
        World world = World.empty();
        try {
            // 1. Lá sồi tự nhiên (state = 0) lơ lửng không có gỗ gần -> không nối gỗ và phân hủy thành AIR
            world.setBlock(40, 20, 40, Block.OAK_LEAVES.getId(), 0);
            require(!LeafDecaySystem.isConnectedToLog(world, 40, 20, 40),
                    "Lá sồi lơ lửng không có gỗ gần phải có isConnectedToLog == false");
            LeafDecaySystem.tickLeaf(world, 40, 20, 40);
            require(world.getBlock(40, 20, 40) == Block.AIR.getId(),
                    "tickLeaf trên lá sồi tự nhiên lơ lửng phải đổi khối thành Block.AIR");

            // Lá vân sam tự nhiên (state = 0) lơ lửng cũng phải phân hủy
            world.setBlock(42, 20, 40, Block.SPRUCE_LEAVES.getId(), 0);
            require(!LeafDecaySystem.isConnectedToLog(world, 42, 20, 40),
                    "Lá vân sam lơ lửng không có gỗ gần phải có isConnectedToLog == false");
            LeafDecaySystem.tickLeaf(world, 42, 20, 40);
            require(world.getBlock(42, 20, 40) == Block.AIR.getId(),
                    "tickLeaf trên lá vân sam tự nhiên lơ lửng phải đổi khối thành Block.AIR");

            // 2. Lá đặt tay (state = PERSISTENT_LEAF_STATE = 1) lơ lửng -> giữ nguyên state = 1 và không phân hủy
            world.setBlock(44, 20, 40, Block.OAK_LEAVES.getId(), LeafDecaySystem.PERSISTENT_LEAF_STATE);
            require(world.getBlockState(44, 20, 40) == LeafDecaySystem.PERSISTENT_LEAF_STATE,
                    "OakLeavesBlock phải bảo toàn metadata PERSISTENT_LEAF_STATE = 1");
            LeafDecaySystem.tickLeaf(world, 44, 20, 40);
            require(world.getBlock(44, 20, 40) == Block.OAK_LEAVES.getId(),
                    "Lá sồi đặt tay (state = 1) không bao giờ được tự phân hủy");

            world.setBlock(46, 20, 40, Block.SPRUCE_LEAVES.getId(), LeafDecaySystem.PERSISTENT_LEAF_STATE);
            require(world.getBlockState(46, 20, 40) == LeafDecaySystem.PERSISTENT_LEAF_STATE,
                    "SpruceLeavesBlock phải bảo toàn metadata PERSISTENT_LEAF_STATE = 1");
            LeafDecaySystem.tickLeaf(world, 46, 20, 40);
            require(world.getBlock(46, 20, 40) == Block.SPRUCE_LEAVES.getId(),
                    "Lá vân sam đặt tay (state = 1) không bao giờ được tự phân hủy");

            // 3. Đặt OAK_LOG cách 3 ô lá nối liền -> isConnectedToLog == true và tickLeaf giữ nguyên lá
            world.setBlock(50, 20, 40, Block.OAK_LOG.getId(), 0);
            world.setBlock(51, 20, 40, Block.OAK_LEAVES.getId(), 0);
            world.setBlock(52, 20, 40, Block.OAK_LEAVES.getId(), 0);
            world.setBlock(53, 20, 40, Block.OAK_LEAVES.getId(), 0);
            require(LeafDecaySystem.isConnectedToLog(world, 53, 20, 40),
                    "Lá nối liền qua 3 ô tới OAK_LOG phải có isConnectedToLog == true");
            LeafDecaySystem.tickLeaf(world, 53, 20, 40);
            require(world.getBlock(53, 20, 40) == Block.OAK_LEAVES.getId(),
                    "Lá còn nối tới gỗ không được phân hủy khi tickLeaf");

            // Kiểm tra giới hạn bán kính tối đa 6: ô thứ 7 không còn nối gỗ
            world.setBlock(54, 20, 40, Block.OAK_LEAVES.getId(), 0);
            world.setBlock(55, 20, 40, Block.OAK_LEAVES.getId(), 0);
            world.setBlock(56, 20, 40, Block.OAK_LEAVES.getId(), 0);
            world.setBlock(57, 20, 40, Block.OAK_LEAVES.getId(), 0);
            require(LeafDecaySystem.isConnectedToLog(world, 56, 20, 40),
                    "Lá ở khoảng cách 6 từ gỗ vẫn phải nối gỗ");
            require(!LeafDecaySystem.isConnectedToLog(world, 57, 20, 40),
                    "Lá ở khoảng cách 7 vượt quá bán kính 6 phải trả về false");

            // 4. Chặt gỗ nối lá -> notifyNeighbors + updateEntities tự phân hủy dây chuyền lá
            world.breakBlockAndSpawnDrop(50, 20, 40);
            for (int tick = 0; tick < 60; tick++) {
                world.updateEntities(0.05f);
            }
            require(world.getBlock(51, 20, 40) == Block.AIR.getId()
                    && world.getBlock(52, 20, 40) == Block.AIR.getId()
                    && world.getBlock(53, 20, 40) == Block.AIR.getId(),
                    "Sau khi chặt OAK_LOG, các lá tự nhiên nối kèm phải tự phân hủy");

            // 5. Kiểm tra hệ số tốc độ đào Shears (5.0f trên lá và 6 khối len; 1.0f trên khối/item khác)
            require(Math.abs(LeafDecaySystem.miningSpeedMultiplier(Item.SHEARS, Block.OAK_LEAVES) - 5.0f) < 0.001f,
                    "Shears trên Oak Leaves phải nhân tốc độ 5.0x");
            require(Math.abs(LeafDecaySystem.miningSpeedMultiplier(Item.SHEARS, Block.SPRUCE_LEAVES) - 5.0f) < 0.001f,
                    "Shears trên Spruce Leaves phải nhân tốc độ 5.0x");

            Block[] woolBlocks = {
                    Block.WHITE_WOOL, Block.LIGHT_GRAY_WOOL, Block.GRAY_WOOL,
                    Block.BLACK_WOOL, Block.BROWN_WOOL, Block.PINK_WOOL
            };
            for (Block wool : woolBlocks) {
                require(Math.abs(LeafDecaySystem.miningSpeedMultiplier(Item.SHEARS, wool) - 5.0f) < 0.001f,
                        "Shears trên " + wool.getDisplayName() + " phải nhân tốc độ 5.0x");
            }

            require(Math.abs(LeafDecaySystem.miningSpeedMultiplier(Item.SHEARS, Block.STONE) - 1.0f) < 0.001f,
                    "Shears trên Stone phải giữ hệ số 1.0x");
            require(Math.abs(LeafDecaySystem.miningSpeedMultiplier(Item.STICK, Block.OAK_LEAVES) - 1.0f) < 0.001f,
                    "Item khác ngoài Shears trên lá phải giữ hệ số 1.0x");
            require(Math.abs(LeafDecaySystem.miningSpeedMultiplier(Item.NONE, Block.WHITE_WOOL) - 1.0f) < 0.001f,
                    "Tay không trên len phải giữ hệ số 1.0x");
        } finally {
            world.dispose();
        }
    }

    /**
     * Kiểm tra mô phỏng dòng chảy Water (7 ô, 0.25s), Lava (3 ô, 1.50s), phản ứng tạo Stone,
     * ưu tiên chảy thẳng xuống dưới rửa trôi cây chữ X và màn hình thông số F3 (zero-GC).
     */
    private static void checkLiquidFlowAndStoneReaction() {
        World world = World.empty();
        DebugOverlayRenderer debug = new DebugOverlayRenderer();
        try {
            // 1. Sàn đá dài 12 block (x = 10..21, z = 10) có thành chắn 2 bên để đo dòng chảy chuẩn theo trục +X
            for (int x = 9; x <= 21; x++) {
                world.setBlockInternal(x, 10, 10, Block.STONE.getId(), 0);
                world.setBlockInternal(x, 11, 9, Block.STONE.getId(), 0);
                world.setBlockInternal(x, 11, 11, Block.STONE.getId(), 0);
            }
            world.setBlockInternal(9, 11, 10, Block.STONE.getId(), 0);

            // Đặt 1 nguồn Block.WATER (state = 0) tại (10, 11, 10) và chạy tick 4.0f giây
            world.setBlock(10, 11, 10, Block.WATER.getId(), 0);
            for (int i = 0; i < 80; i++) {
                world.updateEntities(0.05f);
            }
            for (int d = 0; d <= 7; d++) {
                require(world.getBlock(10 + d, 11, 10) == Block.WATER.getId(),
                        "Nước phải lan tới ô thứ " + d + " trên sàn đá");
                require(world.getBlockState(10 + d, 11, 10) == d,
                        "Mực state dòng chảy nước tại ô " + d + " phải bằng " + d
                                + ", thực tế: " + world.getBlockState(10 + d, 11, 10));
            }
            require(world.getBlock(18, 11, 10) == Block.AIR.getId(),
                    "Ô thứ 8 tính từ nguồn nước phải giữ nguyên Block.AIR");

            // 2. Đặt 1 nguồn Block.LAVA (state = 0) sát dòng chảy Block.WATER -> tạo Block.STONE tại vị trí tiếp xúc
            world.setBlock(18, 11, 10, Block.LAVA.getId(), 0);
            for (int i = 0; i < 40; i++) {
                world.updateEntities(0.05f);
            }
            require(world.getBlock(18, 11, 10) == Block.STONE.getId()
                            || world.getBlock(17, 11, 10) == Block.STONE.getId(),
                    "Tiếp xúc giữa Water và Lava phải tạo Block.STONE tại vị trí tiếp xúc");

            // 3. Kiểm tra Lava chảy ngang tối đa 3 ô với nhịp 1.50s
            for (int x = 29; x <= 40; x++) {
                world.setBlockInternal(x, 10, 10, Block.STONE.getId(), 0);
                world.setBlockInternal(x, 11, 9, Block.STONE.getId(), 0);
                world.setBlockInternal(x, 11, 11, Block.STONE.getId(), 0);
            }
            world.setBlockInternal(29, 11, 10, Block.STONE.getId(), 0);
            world.setBlock(30, 11, 10, Block.LAVA.getId(), 0);
            for (int i = 0; i < 120; i++) {
                world.updateEntities(0.05f);
            }
            require(world.getBlock(30, 11, 10) == Block.LAVA.getId() && world.getBlockState(30, 11, 10) == 0,
                    "Nguồn Lava phải giữ state = 0");
            require(world.getBlock(31, 11, 10) == Block.LAVA.getId()
                            && world.getBlock(32, 11, 10) == Block.LAVA.getId()
                            && world.getBlock(33, 11, 10) == Block.LAVA.getId(),
                    "Lava phải lan tối đa 3 ô ngang");
            require(world.getBlock(34, 11, 10) == Block.AIR.getId(),
                    "Ô thứ 4 tính từ nguồn Lava phải giữ nguyên Block.AIR");

            // 4. Nước ưu tiên chảy thẳng xuống dưới và rửa trôi Block.SHORT_GRASS thành ItemEntity rơi
            world.setBlockInternal(50, 10, 10, Block.STONE.getId(), 0);
            world.setBlockInternal(50, 11, 10, Block.DIRT.getId(), 0);
            world.setBlockInternal(50, 12, 10, Block.SHORT_GRASS.getId(), 0);
            world.setBlock(50, 15, 10, Block.WATER.getId(), 0);
            for (int i = 0; i < 30; i++) {
                world.updateEntities(0.05f);
            }
            require(world.getBlock(51, 15, 10) == Block.AIR.getId()
                            && world.getBlock(49, 15, 10) == Block.AIR.getId(),
                    "Khi bên dưới là khoảng trống, nước phải ưu tiên chảy thẳng xuống thay vì lan ngang");
            require(world.getBlock(50, 14, 10) == Block.WATER.getId()
                            && world.getBlock(50, 13, 10) == Block.WATER.getId()
                            && world.getBlock(50, 12, 10) == Block.WATER.getId(),
                    "Nước chảy xuống phải chiếm các ô không khí và rửa trôi Short Grass");

            boolean droppedShortGrass = false;
            for (int i = 0; i < world.entityCount(); i++) {
                if (world.entityAt(i) instanceof ItemEntity dropped
                        && dropped.getItem() == Item.of(Block.SHORT_GRASS)) {
                    droppedShortGrass = true;
                    break;
                }
            }
            require(droppedShortGrass, "Rửa trôi Block.SHORT_GRASS phải sinh ItemEntity rơi");

            // 5. Kiểm tra DebugOverlayRenderer (F3), tái sử dụng StringBuilder zero-GC và glyph 5x7
            require(!debug.isVisible(), "DebugOverlayRenderer mặc định phải tắt");
            debug.toggle();
            require(debug.isVisible(), "toggle() lần 1 phải bật DebugOverlayRenderer");
            debug.toggle();
            require(!debug.isVisible(), "toggle() lần 2 phải tắt DebugOverlayRenderer");
            debug.toggle();

            Player player = new Player(50.5f, 16.0f, 10.5f);
            Camera camera = new Camera();
            camera.setPosition(50.5f, 17.62f, 10.5f);
            world.registerPlayer(player);

            debug.formatLines(world, player, camera, 60);
            StringBuilder firstLine0 = debug.getLineBuffer(0);
            int lineCount = debug.getLineCount();
            require(lineCount >= 7, "DebugOverlayRenderer phải hiển thị đủ 7 dòng thông số F3");

            String allText = collectDebugText(debug);
            require(allText.contains("FPS: 60"), "F3 phải có dòng FPS");
            require(allText.contains("XYZ:"), "F3 phải có dòng XYZ");
            require(allText.contains("CHUNK:"), "F3 phải có dòng CHUNK");
            require(allText.contains("FACING:"), "F3 phải có dòng FACING");
            require(allText.contains("BIOME:"), "F3 phải có dòng BIOME");
            require(allText.contains("LIGHT:"), "F3 phải có dòng LIGHT");
            require(allText.contains("ENTITIES:"), "F3 phải có dòng ENTITIES");

            debug.formatLines(world, player, camera, 120);
            require(debug.getLineBuffer(0) == firstLine0,
                    "DebugOverlayRenderer phải tái sử dụng StringBuilder thành viên (zero-GC)");
            require(UIRenderer.hasGlyph('.') && UIRenderer.hasGlyph('/') && UIRenderer.hasGlyph(',')
                            && UIRenderer.hasGlyph('(') && UIRenderer.hasGlyph(')') && UIRenderer.hasGlyph('='),
                    "Font 5x7 của UIRenderer phải hỗ trợ đầy đủ ký tự .,/()= cho màn hình F3");
        } finally {
            debug.dispose();
            world.dispose();
        }
    }

    /**
     * Kiểm tra toàn bộ bộ sprite PNG giao diện Minecraft 1.21.11 trong {@code textures/gui/}
     * giải mã được qua {@link Pixmap} đúng kích thước và {@link GuiTextureAtlas} tính UV chuẩn zero-GC.
     */
    private static void checkGuiTexturesPresent() {
        GdxNativesLoader.load();
        ClassLoader loader = SurvivalWorldChecks.class.getClassLoader();

        String[] paths = {
                "textures/gui/container/inventory.png",
                "textures/gui/sprites/hud/hotbar.png",
                "textures/gui/sprites/hud/hotbar_selection.png",
                "textures/gui/sprites/hud/crosshair.png",
                "textures/gui/sprites/hud/heart/container.png",
                "textures/gui/sprites/hud/heart/full.png",
                "textures/gui/sprites/hud/heart/half.png",
                "textures/gui/sprites/hud/heart/poisoned_full.png",
                "textures/gui/sprites/hud/heart/poisoned_half.png",
                "textures/gui/sprites/hud/heart/vehicle_container.png",
                "textures/gui/sprites/hud/heart/vehicle_full.png",
                "textures/gui/sprites/hud/heart/vehicle_half.png",
                "textures/gui/sprites/hud/food_empty.png",
                "textures/gui/sprites/hud/food_full.png",
                "textures/gui/sprites/hud/food_half.png",
                "textures/gui/sprites/hud/food_empty_hunger.png",
                "textures/gui/sprites/hud/food_full_hunger.png",
                "textures/gui/sprites/hud/food_half_hunger.png",
                "textures/gui/sprites/hud/air.png",
                "textures/gui/sprites/hud/air_bursting.png",
                "textures/gui/sprites/hud/air_empty.png",
                "textures/gui/sprites/hud/jump_bar_background.png",
                "textures/gui/sprites/hud/jump_bar_progress.png",
                "textures/gui/sprites/hud/jump_bar_cooldown.png",
                "textures/gui/sprites/widget/button.png",
                "textures/gui/sprites/widget/button_highlighted.png",
                "textures/gui/sprites/widget/button_disabled.png",
                "textures/gui/sprites/widget/slider.png",
                "textures/gui/sprites/widget/slider_highlighted.png",
                "textures/gui/sprites/widget/slider_handle.png",
                "textures/gui/sprites/widget/slider_handle_highlighted.png"
        };
        int[] expectedWidths = {
                256, 182, 24, 15,
                9, 9, 9, 9, 9, 9, 9, 9,
                9, 9, 9, 9, 9, 9,
                9, 9, 9,
                182, 182, 182,
                200, 200, 200, 200, 200, 8, 8
        };
        int[] expectedHeights = {
                256, 22, 23, 15,
                9, 9, 9, 9, 9, 9, 9, 9,
                9, 9, 9, 9, 9, 9,
                9, 9, 9,
                5, 5, 5,
                20, 20, 20, 20, 20, 20, 20
        };

        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            try (InputStream stream = loader.getResourceAsStream(path)) {
                require(stream != null, "Thiếu tài nguyên GUI bắt buộc: " + path);
                byte[] bytes = stream.readAllBytes();
                Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
                try {
                    require(pixmap.getWidth() == expectedWidths[i] && pixmap.getHeight() == expectedHeights[i],
                            "Sai kích thước GUI PNG " + path + ": kỳ vọng "
                                    + expectedWidths[i] + "x" + expectedHeights[i]
                                    + ", thực tế " + pixmap.getWidth() + "x" + pixmap.getHeight());
                } finally {
                    pixmap.dispose();
                }
            } catch (IOException e) {
                throw new AssertionError("Không đọc được tài nguyên GUI: " + path, e);
            }
        }

        // Xác nhận toàn bộ SpriteKey của GuiTextureAtlas tính UV hợp lệ trong [0, 1] và zero-GC
        GuiTextureAtlas.SpriteKey[] keys = GuiTextureAtlas.SpriteKey.values();
        require(keys.length >= 30, "GuiTextureAtlas.SpriteKey phải đủ 30 sprite HUD & Widget");
        for (GuiTextureAtlas.SpriteKey key : keys) {
            float[] uv1 = GuiTextureAtlas.uvOf(key);
            float[] uv2 = GuiTextureAtlas.uvOf(key);
            require(uv1 == uv2, "GuiTextureAtlas.uvOf(" + key + ") phải trả mảng tính sẵn (zero-GC)");
            require(uv1.length == 4, "UV phải có đúng 4 phần tử [u0, v0, u1, v1]");
            require(uv1[0] >= 0f && uv1[0] < uv1[2] && uv1[2] <= 1f,
                    "Tọa độ U không hợp lệ cho " + key + ": [" + uv1[0] + ", " + uv1[2] + "]");
            require(uv1[1] >= 0f && uv1[1] < uv1[3] && uv1[3] <= 1f,
                    "Tọa độ V không hợp lệ cho " + key + ": [" + uv1[1] + ", " + uv1[3] + "]");
            float actualW = (uv1[2] - uv1[0]) * GuiTextureAtlas.ATLAS_WIDTH;
            float actualH = (uv1[3] - uv1[1]) * GuiTextureAtlas.ATLAS_HEIGHT;
            require(Math.abs(actualW - key.width) < 0.01f && Math.abs(actualH - key.height) < 0.01f,
                    "Kích thước UV không khớp kích thước sprite cho " + key);
        }

        // Xác nhận đóng gói CPU Pixmap cho atlas HUD & Widget không làm trắng ảnh Grayscale (button.png)
        Pixmap atlasCpu = GuiTextureAtlas.buildHudAtlasPixmap();
        try {
            require(atlasCpu.getWidth() == GuiTextureAtlas.ATLAS_WIDTH
                            && atlasCpu.getHeight() == GuiTextureAtlas.ATLAS_HEIGHT,
                    "Kích thước CPU atlas phải là 512x256");
            int btnX = GuiTextureAtlas.SpriteKey.WIDGET_BUTTON.atlasX;
            int btnY = GuiTextureAtlas.SpriteKey.WIDGET_BUTTON.atlasY;
            boolean hasDarkGray = false;
            for (int y = 0; y < GuiTextureAtlas.SpriteKey.WIDGET_BUTTON.height && !hasDarkGray; y++) {
                for (int x = 0; x < GuiTextureAtlas.SpriteKey.WIDGET_BUTTON.width; x++) {
                    int rgba = atlasCpu.getPixel(btnX + x, btnY + y);
                    int r = (rgba >>> 24) & 0xFF;
                    int g = (rgba >>> 16) & 0xFF;
                    int b = (rgba >>> 8) & 0xFF;
                    if (r == g && g == b && r < 200) {
                        hasDarkGray = true;
                        break;
                    }
                }
            }
            require(hasDarkGray, "WIDGET_BUTTON grayscale PNG không được bị giải mã thành trắng tinh");
        } finally {
            atlasCpu.dispose();
        }
    }

    /** Gom nội dung các dòng F3 trong test để kiểm tra từ khóa bắt buộc. */
    private static String collectDebugText(DebugOverlayRenderer debug) {
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < debug.getLineCount(); i++) {
            combined.append(debug.getLineBuffer(i)).append('\n');
        }
        return combined.toString();
    }

    /** Báo lỗi khẳng định nếu điều kiện kiểm tra không thỏa mãn. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
