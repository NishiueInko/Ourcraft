package com.voxelgame.client.input;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.gui.DebugOverlayRenderer;
import com.voxelgame.client.gui.InventoryScreen;
import com.voxelgame.client.gui.UIRenderer;
import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.ParticleManager;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.item.ItemUseHandler;
import com.voxelgame.world.level.LeafDecaySystem;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.WorldTime;
import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.weather.WeatherManager;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector3;

/**
 * Bộ điều khiển Đầu vào & Tương tác Người chơi của Client ({@link InputProcessor}),
 * được tách khỏi {@code VoxelGameApp} theo nguyên lý Đơn trách nhiệm (SRP).
 *
 * Chịu trách nhiệm đóng gói toàn bộ xử lý bàn phím và chuột:
 * - Xoay Camera góc nhìn thứ nhất/thứ ba với cơ chế chống giật chuột ({@code skipNextMouseDelta}).
 * - Phím tắt hệ thống: {@code ESC} (Pause/Resume/Quit), {@code E} (Túi đồ 36 ô), {@code Q}/{@code Ctrl+Q}
 *   (vứt vật phẩm), {@code F1} (tốc độ địa hình), {@code F3} (Debug Overlay), {@code F5} (CameraMode),
 *   {@code F6}/{@code F7}/{@code F8} (thời tiết/giờ), {@code Ctrl+C} (ghé tâm 5 biome), {@code 1..9} & cuộn chuột.
 * - Đào block theo độ cứng ({@code updateMiningProgress}), đặt block, đánh cận chiến và sử dụng vật phẩm.
 * - Hit-testing cho {@link com.voxelgame.client.gui.PauseScreen} và {@link InventoryScreen}.
 */
public class GameInputController implements InputProcessor {

    /**
     * Hợp đồng cầu nối giữa {@link GameInputController} và lớp điều phối ứng dụng ({@code VoxelGameApp}),
     * giúp đọc/ghi trạng thái sống mà không tạo vòng phụ thuộc chặt.
     */
    public interface Host {
        World getWorld();
        Player getPlayer();
        Camera getCamera();
        AudioManager getAudioManager();
        UIRenderer getUiRenderer();
        InventoryScreen getInventoryScreen();
        DebugOverlayRenderer getDebugOverlay();
        ItemUseHandler getItemUseHandler();
        ParticleManager getParticleManager();
        WeatherManager getWeather();
        WorldTime getWorldTime();
        boolean isPaused();
        void setPaused(boolean paused);
    }

    /** Thứ tự ghé thăm 5 biome theo trục X từ tây sang đông khi bấm Ctrl+C. */
    private static final BiomeType[] BIOME_TOUR = {
            BiomeType.SNOWY_PLAINS, BiomeType.FOREST, BiomeType.PLAINS, BiomeType.FLOWER_FOREST, BiomeType.DESERT
    };

    /** Độ nhạy chuột khi xoay Camera (giữ nguyên giá trị chuẩn 0.12f). */
    private static final float MOUSE_SENSITIVITY = 0.12f;
    /** Tầm với tối đa của tia bắn Raycast khi đào/đặt Block (5 ô voxel). */
    public static final float RAYCAST_REACH = 5.0f;
    /** Tầm với cơ bản dành cho đòn cận chiến tay không. */
    private static final float MELEE_REACH = 3.0f;

    /** Cầu nối tới các hệ thống con của ứng dụng. */
    private final Host host;

    /** Trạng thái xoay vòng biome cho phím tắt Ctrl+C. */
    private int nextBiomeIndex;

    /** Trạng thái phím Ctrl trái/phải theo thứ tự sự kiện hàng đợi của libGDX. */
    private boolean leftControlDown;
    private boolean rightControlDown;

    /** Tọa độ chuột hiện tại (dùng cho Hit-Testing nút bấm trên PauseScreen và InventoryScreen). */
    private float currentMouseX;
    private float currentMouseY;

    /** Cờ bỏ qua delta chuột ở khung hình đầu tiên sau khi khóa lại con trỏ để chống giật Camera. */
    private boolean skipNextMouseDelta = true;

    /** Cờ đánh dấu người dùng đang kéo thanh trượt âm lượng trên PauseScreen. */
    private boolean isDraggingVolumeSlider = false;

    /** Trạng thái giữ chuột trái và tích lũy tiến trình đào block theo độ cứng. */
    private boolean isLeftMouseDown = false;
    private int miningTargetX = Integer.MIN_VALUE;
    private int miningTargetY = Integer.MIN_VALUE;
    private int miningTargetZ = Integer.MIN_VALUE;
    private float miningElapsedTime = 0.0f;
    private float nextMiningSoundTime;

    /** Các bộ đệm tái sử dụng cho Raycast và sinh vật phẩm rơi (zero-GC). */
    private final Vector3 eyePosition = new Vector3();
    private final Vector3 aimDirection = new Vector3();
    private final Vector3 dropPosBuffer = new Vector3();
    private final Raycast.HitResult placementHit = Raycast.HitResult.miss();
    private final Raycast.HitResult occluderHit = Raycast.HitResult.miss();
    private final Raycast.EntityHitResult entityHit = new Raycast.EntityHitResult();

    /** Khởi tạo bộ điều khiển đầu vào gắn với cầu nối {@link Host}. */
    public GameInputController(Host host) {
        this.host = host;
    }

    /** Trả về tọa độ X chuột gần nhất (pixel màn hình). */
    public float getCurrentMouseX() {
        return currentMouseX;
    }

    /** Trả về tọa độ Y chuột gần nhất (pixel màn hình). */
    public float getCurrentMouseY() {
        return currentMouseY;
    }

    /** Đánh dấu bỏ qua delta chuột ở khung hình kế tiếp (khi vừa khởi chạy hoặc khóa lại chuột). */
    public void skipNextMouseDelta() {
        this.skipNextMouseDelta = true;
    }

    /** Kiểm tra người chơi có đang giữ chuột trái và đào một mục tiêu hợp lệ hay không. */
    public boolean isActivelyMining() {
        return isLeftMouseDown && miningTargetX != Integer.MIN_VALUE;
    }

    /** Kiểm tra giao diện túi đồ 36 ô hiện có đang mở hay không. */
    public boolean isInventoryOpen() {
        InventoryScreen screen = host.getInventoryScreen();
        return screen != null && screen.isOpen();
    }

    /**
     * Xử lý xoay Camera bằng delta chuột mỗi khung hình.
     * Bỏ qua khung hình đầu tiên sau khi khóa chuột nhờ cờ {@link #skipNextMouseDelta}.
     */
    public void processMouseLook() {
        if (skipNextMouseDelta) {
            skipNextMouseDelta = false;
            return;
        }
        float deltaX = Gdx.input.getDeltaX();
        float deltaY = Gdx.input.getDeltaY();
        Camera camera = host.getCamera();
        if (camera != null) {
            camera.rotate(deltaX * MOUSE_SENSITIVITY, -deltaY * MOUSE_SENSITIVITY);
        }
    }

    /** Bật/tắt giao diện túi đồ 36 ô khi nhấn phím {@code E}, giữ nguyên vòng đời thế giới. */
    public void toggleInventory() {
        InventoryScreen inventoryScreen = host.getInventoryScreen();
        Player player = host.getPlayer();
        if (inventoryScreen == null || player == null) {
            return;
        }
        if (inventoryScreen.isOpen()) {
            closeInventory();
        } else {
            inventoryScreen.open();
            isLeftMouseDown = false;
            resetMiningProgress();
            ItemUseHandler itemUseHandler = host.getItemUseHandler();
            if (itemUseHandler != null) {
                itemUseHandler.onHotbarSlotChanged();
            }
            Gdx.input.setCursorCatched(false);
            skipNextMouseDelta = true;
        }
    }

    /** Đóng giao diện túi đồ, trả item trên con trỏ về kho/đất và khóa lại chuột. */
    public void closeInventory() {
        InventoryScreen inventoryScreen = host.getInventoryScreen();
        if (inventoryScreen == null || !inventoryScreen.isOpen()) {
            return;
        }
        inventoryScreen.close(host.getPlayer(), host.getWorld());
        isLeftMouseDown = false;
        resetMiningProgress();
        Gdx.input.setCursorCatched(true);
        skipNextMouseDelta = true;
    }

    /** Chuyển game sang trạng thái Tạm dừng, mở PauseScreen và mở khóa con trỏ chuột. */
    public void pauseGame() {
        if (isInventoryOpen()) {
            host.getInventoryScreen().close(host.getPlayer(), host.getWorld());
        }
        host.setPaused(true);
        UIRenderer uiRenderer = host.getUiRenderer();
        if (uiRenderer != null) {
            uiRenderer.getPauseScreen().open();
        }
        isLeftMouseDown = false;
        isDraggingVolumeSlider = false;
        resetMiningProgress();
        ItemUseHandler itemUseHandler = host.getItemUseHandler();
        if (itemUseHandler != null) {
            itemUseHandler.onHotbarSlotChanged();
        }
        AudioManager audioManager = host.getAudioManager();
        if (audioManager != null) {
            audioManager.setUnderwater(false);
            audioManager.setRainPaused(true);
        }
        Gdx.input.setCursorCatched(false);
    }

    /** Tiếp tục game từ trạng thái Tạm dừng, đóng PauseScreen và khóa lại con trỏ chuột. */
    public void resumeGame() {
        host.setPaused(false);
        UIRenderer uiRenderer = host.getUiRenderer();
        if (uiRenderer != null) {
            uiRenderer.getPauseScreen().close();
        }
        isDraggingVolumeSlider = false;
        AudioManager audioManager = host.getAudioManager();
        if (audioManager != null) {
            audioManager.setRainPaused(false);
        }
        Gdx.input.setCursorCatched(true);
        skipNextMouseDelta = true;
    }

    /**
     * Cập nhật tiến trình đào block khi đang giữ chuột trái, áp dụng hệ số đa hình
     * {@link Item#getMiningSpeedMultiplier(Block)} của vật phẩm đang cầm.
     */
    public float updateMiningProgress(Raycast.HitResult currentHit, float deltaTime) {
        if (!isLeftMouseDown || currentHit == null || !currentHit.isHit()) {
            resetMiningProgress();
            return 0.0f;
        }

        Block targetBlock = currentHit.getBlock();
        if (!targetBlock.isBreakable()) {
            resetMiningProgress();
            return 0.0f;
        }

        int hitX = currentHit.getBlockX();
        int hitY = currentHit.getBlockY();
        int hitZ = currentHit.getBlockZ();

        if (hitX != miningTargetX || hitY != miningTargetY || hitZ != miningTargetZ) {
            miningTargetX = hitX;
            miningTargetY = hitY;
            miningTargetZ = hitZ;
            miningElapsedTime = 0.0f;
            nextMiningSoundTime = 0.0f;
        }

        Player player = host.getPlayer();
        Item heldItem = (player != null && player.getHotbar().getSelectedCount() > 0
                && player.getHotbar().getSelectedItem() != null)
                ? player.getHotbar().getSelectedItem() : Item.NONE;
        float speedMultiplier = heldItem.getMiningSpeedMultiplier(targetBlock);
        miningElapsedTime += deltaTime * speedMultiplier;
        float requiredTime = targetBlock.getHardness();

        if (miningElapsedTime >= requiredTime) {
            ParticleManager particleManager = host.getParticleManager();
            if (particleManager != null) {
                particleManager.spawnBlockBreak(miningTargetX, miningTargetY, miningTargetZ, targetBlock, 20);
            }
            World world = host.getWorld();
            if (world != null) {
                world.breakBlockAndSpawnDrop(miningTargetX, miningTargetY, miningTargetZ);
            }
            resetMiningProgress();
            return 0.0f;
        }

        AudioManager audioManager = host.getAudioManager();
        if (audioManager != null && requiredTime > 0.25f && miningElapsedTime >= nextMiningSoundTime) {
            audioManager.playMining(targetBlock);
            nextMiningSoundTime += 0.25f;
        }

        return Math.min(1.0f, miningElapsedTime / requiredTime);
    }

    /** Đặt lại trạng thái đào block về mặc định. */
    public void resetMiningProgress() {
        miningTargetX = Integer.MIN_VALUE;
        miningTargetY = Integer.MIN_VALUE;
        miningTargetZ = Integer.MIN_VALUE;
        miningElapsedTime = 0.0f;
        nextMiningSoundTime = 0.0f;
    }

    /** Xử lý click chuột trên Pause Menu (nút Resume, nút -/+, thanh trượt âm lượng). */
    private void handlePauseMenuMouseClick(float screenX, float screenY, boolean pressed) {
        UIRenderer uiRenderer = host.getUiRenderer();
        if (uiRenderer == null) {
            return;
        }
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();

        if (!pressed) {
            isDraggingVolumeSlider = false;
            return;
        }

        AudioManager audioManager = host.getAudioManager();
        if (uiRenderer.isInsideResumeButton(screenX, screenY, screenWidth, screenHeight)) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
            }
            resumeGame();
            return;
        }
        if (uiRenderer.isInsideVolumeMinus(screenX, screenY, screenWidth, screenHeight)) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
                audioManager.changeVolume(-0.10f);
            }
            return;
        }
        if (uiRenderer.isInsideVolumePlus(screenX, screenY, screenWidth, screenHeight)) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
                audioManager.changeVolume(0.10f);
            }
            return;
        }
        float sliderVal = uiRenderer.getSliderVolumeAtCursor(screenX, screenY, screenWidth, screenHeight);
        if (sliderVal >= 0.0f && audioManager != null) {
            isDraggingVolumeSlider = true;
            audioManager.setVolume(sliderVal);
        }
    }

    /** Chọn thực thể từ mắt người chơi và giới hạn tia tại hộp va chạm của địa hình. */
    private Raycast.EntityHitResult aimAtEntity(float reach) {
        Camera camera = host.getCamera();
        World world = host.getWorld();
        Player player = host.getPlayer();
        camera.copyPosition(eyePosition);
        camera.copyFront(aimDirection);
        Raycast.castOccluder(world, eyePosition, aimDirection, reach, occluderHit);
        return Raycast.castEntity(world, eyePosition, aimDirection, reach, occluderHit.getDistance(),
                player, entityHit);
    }

    /** Xử lý nhấn chuột trái: ưu tiên đòn cận chiến (kể cả Jab tầm 4.2f của Iron Spear), ngược lại đào block. */
    private void handleLeftClick() {
        World world = host.getWorld();
        Camera camera = host.getCamera();
        Player player = host.getPlayer();
        if (world == null || camera == null || player == null) {
            return;
        }
        ItemUseHandler itemUseHandler = host.getItemUseHandler();
        if (itemUseHandler != null) {
            boolean hitMob = itemUseHandler.onPrimaryMelee(world, player, camera);
            isLeftMouseDown = !hitMob;
            if (hitMob) {
                resetMiningProgress();
            }
            return;
        }
        Raycast.EntityHitResult hit = aimAtEntity(MELEE_REACH);
        isLeftMouseDown = !hit.isHit();
        if (hit.isHit()) {
            resetMiningProgress();
            player.attack(world, hit.getEntity(), aimDirection);
        }
    }

    /** Xử lý nhấn chuột phải: tương tác thực thể, uống Milk Bucket dự phòng hoặc đặt block từ Hotbar. */
    private void handleRightClickPlaceBlock() {
        World world = host.getWorld();
        Camera camera = host.getCamera();
        Player player = host.getPlayer();
        if (world == null || camera == null || player == null) {
            return;
        }

        Raycast.EntityHitResult target = aimAtEntity(RAYCAST_REACH);
        if (target.isHit() && target.getEntity().onPlayerInteract(world, player)) {
            return;
        }

        Hotbar hotbar = player.getHotbar();
        Item selectedItem = hotbar.getSelectedItem();

        if (selectedItem == Item.MILK_BUCKET && hotbar.getSelectedCount() > 0) {
            player.clearStatusEffects();
            hotbar.consumeSelectedItemStack();
            if (!hotbar.addItem(Item.BUCKET, 1)) {
                player.copyPosition(dropPosBuffer);
                world.spawnDroppedItem(new ItemEntity(dropPosBuffer.x,
                        dropPosBuffer.y + 1.0f, dropPosBuffer.z, Item.BUCKET));
            }
            AudioManager audioManager = host.getAudioManager();
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.PICKUP, 0.35f);
            }
            return;
        }

        Block selectedBlock = hotbar.getSelectedBlock();
        if (selectedBlock == Block.AIR || hotbar.getSelectedCount() <= 0) {
            return;
        }

        Raycast.HitResult hit = Raycast.cast(world, camera.copyPosition(eyePosition), camera.copyFront(aimDirection),
                RAYCAST_REACH, placementHit);
        if (!hit.isHit()) {
            return;
        }

        int x = hit.getBlockX();
        int y = hit.getBlockY();
        int z = hit.getBlockZ();
        if (selectedBlock == Block.SNOW_LAYER && hit.getBlock() == Block.SNOW_LAYER
                && world.getBlockState(x, y, z) < 7) {
            if (world.tryStackSnow(x, y, z, player)) {
                finishBlockPlacement(hotbar, selectedBlock);
            }
            return;
        }

        int placeState = LeafDecaySystem.isLeaf(selectedBlock) ? LeafDecaySystem.PERSISTENT_LEAF_STATE : 0;
        if (world.tryPlaceBlock(x, y, z, selectedBlock, placeState, player)
                || world.tryPlaceBlock(hit.getPrevBlockX(), hit.getPrevBlockY(), hit.getPrevBlockZ(),
                selectedBlock, placeState, player)) {
            finishBlockPlacement(hotbar, selectedBlock);
        }
    }

    /** Trừ vật phẩm trên Hotbar và phát SFX vật liệu sau khi đặt block thành công. */
    private void finishBlockPlacement(Hotbar hotbar, Block placedBlock) {
        hotbar.consumeSelectedItem();
        AudioManager audioManager = host.getAudioManager();
        if (audioManager != null) {
            audioManager.playPlace(placedBlock);
        }
    }

    /** Dịch chuyển người chơi lần lượt qua tâm 5 biome theo thứ tự từ tây sang đông. */
    private void teleportToNextBiome(Player player, World world) {
        BiomeType biome = BIOME_TOUR[nextBiomeIndex];
        nextBiomeIndex = (nextBiomeIndex + 1) % BIOME_TOUR.length;
        if (player.teleportToBiome(world, biome)) {
            resetMiningProgress();
        }
    }

    private static int getHotbarSlotFromKeycode(int keycode) {
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            return keycode - Input.Keys.NUM_1;
        }
        return -1;
    }

    // =========================================================================
    // TRIỂN KHAI GIAO DIỆN InputProcessor
    // =========================================================================

    @Override
    public boolean keyDown(int keycode) {
        Player player = host.getPlayer();
        if (keycode == Input.Keys.F1 && player != null) {
            player.cycleTerrainSpeed();
            return true;
        }

        if (keycode == Input.Keys.CONTROL_LEFT) {
            leftControlDown = true;
            return false;
        }
        if (keycode == Input.Keys.CONTROL_RIGHT) {
            rightControlDown = true;
            return false;
        }

        if (keycode == Input.Keys.ESCAPE) {
            if (isInventoryOpen()) {
                closeInventory();
                return true;
            }
            if (!host.isPaused()) {
                pauseGame();
            } else {
                Gdx.app.exit();
            }
            return true;
        }

        if (!host.isPaused() && player != null) {
            if (keycode == Input.Keys.C && (leftControlDown || rightControlDown)) {
                teleportToNextBiome(player, host.getWorld());
                return true;
            }
            switch (keycode) {
                case Input.Keys.F6 -> {
                    WeatherManager weather = host.getWeather();
                    if (weather != null) {
                        weather.toggle();
                    }
                    return true;
                }
                case Input.Keys.F7 -> {
                    WorldTime worldTime = host.getWorldTime();
                    if (worldTime != null) {
                        worldTime.nextMilestone();
                    }
                    return true;
                }
                case Input.Keys.F8 -> {
                    WorldTime worldTime = host.getWorldTime();
                    if (worldTime != null) {
                        worldTime.toggleFastForward();
                    }
                    return true;
                }
                default -> {
                }
            }
            if (keycode == Input.Keys.F5 && !isInventoryOpen()) {
                player.cycleCameraMode();
                return true;
            }
            if (keycode == Input.Keys.E) {
                toggleInventory();
                return true;
            }
            World world = host.getWorld();
            if (keycode == Input.Keys.Q && world != null && !isInventoryOpen()) {
                player.dropSelectedItem(world, leftControlDown || rightControlDown);
                return true;
            }
            DebugOverlayRenderer debugOverlay = host.getDebugOverlay();
            if (keycode == Input.Keys.F3 && debugOverlay != null) {
                debugOverlay.toggle();
                return true;
            }
            int slotIndex = getHotbarSlotFromKeycode(keycode);
            if (slotIndex >= 0 && !isInventoryOpen()) {
                int previousSlot = player.getHotbar().getSelectedSlot();
                player.getHotbar().selectSlot(slotIndex);
                ItemUseHandler itemUseHandler = host.getItemUseHandler();
                if (itemUseHandler != null && player.getHotbar().getSelectedSlot() != previousSlot) {
                    itemUseHandler.onHotbarSlotChanged();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode == Input.Keys.CONTROL_LEFT) {
            leftControlDown = false;
        } else if (keycode == Input.Keys.CONTROL_RIGHT) {
            rightControlDown = false;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        currentMouseX = screenX;
        currentMouseY = screenY;

        if (host.isPaused()) {
            if (button == Input.Buttons.LEFT) {
                handlePauseMenuMouseClick(screenX, screenY, true);
            }
            return true;
        }

        Player player = host.getPlayer();
        if (isInventoryOpen() && player != null) {
            boolean shiftDown = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            host.getInventoryScreen().handleClick(player.getInventory(), screenX, screenY,
                    Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), button, shiftDown);
            return true;
        }

        if (button == Input.Buttons.LEFT) {
            if (player != null) {
                player.triggerSwing();
            }
            handleLeftClick();
        } else if (button == Input.Buttons.RIGHT) {
            ItemUseHandler itemUseHandler = host.getItemUseHandler();
            if (itemUseHandler != null && itemUseHandler.onSecondaryPress(host.getWorld(), player, host.getCamera())) {
                return true;
            }
            handleRightClickPlaceBlock();
        }
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        currentMouseX = screenX;
        currentMouseY = screenY;

        if (host.isPaused()) {
            if (button == Input.Buttons.LEFT) {
                handlePauseMenuMouseClick(screenX, screenY, false);
            }
            return true;
        }

        if (isInventoryOpen()) {
            isLeftMouseDown = false;
            resetMiningProgress();
            return true;
        }

        if (button == Input.Buttons.LEFT) {
            isLeftMouseDown = false;
            resetMiningProgress();
        }
        ItemUseHandler itemUseHandler = host.getItemUseHandler();
        if (button == Input.Buttons.RIGHT && itemUseHandler != null) {
            itemUseHandler.onSecondaryRelease(host.getWorld(), host.getPlayer(), host.getCamera());
        }
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        currentMouseX = screenX;
        currentMouseY = screenY;

        UIRenderer uiRenderer = host.getUiRenderer();
        AudioManager audioManager = host.getAudioManager();
        if (host.isPaused() && isDraggingVolumeSlider && uiRenderer != null && audioManager != null) {
            int screenWidth = Gdx.graphics.getWidth();
            int screenHeight = Gdx.graphics.getHeight();
            float newVol = uiRenderer.getSliderVolumeAtCursor(screenX, screenY, screenWidth, screenHeight);
            if (newVol >= 0.0f) {
                audioManager.setVolume(newVol);
            }
        }
        return true;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        currentMouseX = screenX;
        currentMouseY = screenY;
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        Player player = host.getPlayer();
        if (!host.isPaused() && !isInventoryOpen() && player != null) {
            int previousSlot = player.getHotbar().getSelectedSlot();
            player.getHotbar().scroll(-amountY);
            ItemUseHandler itemUseHandler = host.getItemUseHandler();
            if (itemUseHandler != null && player.getHotbar().getSelectedSlot() != previousSlot) {
                itemUseHandler.onHotbarSlotChanged();
            }
        }
        return true;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return touchUp(screenX, screenY, pointer, button);
    }
}
