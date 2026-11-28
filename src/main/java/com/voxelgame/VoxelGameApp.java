package com.voxelgame;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.gui.CreativeInventoryScreen;
import com.voxelgame.client.gui.DebugOverlayRenderer;
import com.voxelgame.client.gui.InventoryScreen;
import com.voxelgame.client.gui.UIRenderer;
import com.voxelgame.client.input.GameInputController;
import com.voxelgame.client.render.BlockOutlineRenderer;
import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.FirstPersonRenderer;
import com.voxelgame.client.render.ItemTextureAtlas;
import com.voxelgame.client.render.LiquidAnimator;
import com.voxelgame.client.render.ParticleManager;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.client.render.SkyRenderer;
import com.voxelgame.client.render.Texture;
import com.voxelgame.client.render.WeatherRenderer;
import com.voxelgame.client.render.entity.MobRenderer;
import com.voxelgame.client.render.entity.MobTextureManager;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.gamemode.CreativeGameMode;
import com.voxelgame.world.item.ItemUseHandler;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.WorldTime;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.weather.WeatherManager;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Lớp điều phối vòng đời ứng dụng chính của VoxelGame (Application Orchestrator),
 * kế thừa {@link ApplicationAdapter} và triển khai {@link InputProcessor} bằng cách ủy quyền
 * toàn bộ xử lý đầu vào sang {@link GameInputController}.
 *
 * Cấu trúc 5 khối trách nhiệm rành mạch:
 * 1. {@link #create()}: Khởi tạo tài nguyên OpenGL, tầng World và tầng Client.
 * 2. {@link #render()}: Vòng lặp Game Loop (Input → Cập nhật World → Kết xuất 3D → Kết xuất GUI 2D).
 * 3. {@link #resize(int, int)}: Cập nhật Viewport OpenGL khi đổi kích thước cửa sổ.
 * 4. {@link #dispose()}: Giải phóng đồng loạt tài nguyên GPU, Off-heap và OpenAL.
 * 5. Ủy quyền {@link InputProcessor} sang {@link GameInputController} (hỗ trợ khởi tạo lười cho kiểm thử).
 */
public class VoxelGameApp extends ApplicationAdapter implements InputProcessor, GameInputController.Host {

    /** Màu bầu trời xanh nhạt dùng khi xóa khung hình. */
    private static final Vector3 SKY_COLOR = new Vector3(0.53f, 0.81f, 0.92f);
    /** Độ mờ của lớp phủ lửa hoạt ảnh ở cạnh dưới màn hình góc nhìn thứ nhất. */
    private static final float FIRE_OVERLAY_ALPHA = 0.80f;

    // =========================================================================
    // Các hệ thống cốt lõi của World & Client
    // =========================================================================

    /** Chương trình Shader GLSL 330 Core dùng chung cho chế độ 3D và 2D UI. */
    private ShaderProgram shaderProgram;
    /** Texture Atlas khối và Atlas vật phẩm độc lập. */
    private Texture textureAtlas;
    private ItemTextureAtlas itemAtlas;
    /** Quản lý texture và bộ kết xuất phân khớp cho toàn bộ mob. */
    private MobTextureManager mobTextures;
    private MobRenderer mobRenderer;
    /** Quản lý nhạc nền, hiệu ứng SFX và âm thanh mưa. */
    private AudioManager audioManager;
    /** Camera Euler Yaw/Pitch hỗ trợ Dynamic FOV. */
    private Camera camera;
    /** Thế giới voxel kích thước 4000 × 256 × 2048, seed 20261114L. */
    private World world;
    /** Người chơi (giữ trường trực tiếp để tương thích reflection của bộ kiểm thử hồi quy). */
    private Player player;
    /** Facade kết xuất HUD 2D, PauseScreen và Overlay 3D. */
    private UIRenderer uiRenderer;
    /** Bộ điều khiển đầu vào tách riêng theo SRP (khởi tạo lười khi test gọi trực tiếp). */
    private GameInputController inputController;

    /** Đồng hồ, thời tiết và bộ vẽ bầu trời/giáng thủy. */
    private final WorldTime worldTime = new WorldTime();
    private final WeatherManager weather = new WeatherManager(World.WORLD_SEED);
    private SkyRenderer skyRenderer;
    private WeatherRenderer weatherRenderer;
    private final Runnable renderCloudLayer = this::renderCloudLayer;

    /** Hệ thống hoạt ảnh chất lỏng, khung dây chọn block, hạt 3D và lớp phủ lửa góc nhìn 1. */
    private LiquidAnimator liquidAnimator;
    private BlockOutlineRenderer blockOutlineRenderer;
    private ParticleManager particleManager;
    private float miningParticleTimer;
    private float fireParticleTimer;
    private boolean wasPlayerInWater;
    private final Vector3 entityParticlePos = new Vector3();
    private Mesh fireOverlayMesh;
    private final float[] fireOverlayVertices = new float[6 * 6];
    private final float[] fireOverlayUv = new float[4];

    /** Cờ tạm dừng game (giữ đồng bộ trực tiếp với PauseScreen và kiểm thử reflection). */
    private boolean isPaused = false;
    /** Cờ đánh dấu đang ở luồng màn hình chính ngoài thế giới (Main Menu / Settings / Hotkeys / Credits). */
    private boolean inMainMenuFlow = false;

    /** Bộ đệm tái sử dụng cho Raycast ngắm khối và camera góc nhìn thứ nhất/thứ ba (zero-GC). */
    private final Vector3 eyePosition = new Vector3();
    private final Vector3 aimDirection = new Vector3();
    private final Raycast.HitResult renderHit = Raycast.HitResult.miss();
    private ItemUseHandler itemUseHandler;
    private FirstPersonRenderer firstPersonRenderer;
    private final Vector3 savedCameraEye = new Vector3();
    private final Vector3 renderCameraPos = new Vector3();
    private final Matrix4 renderViewMatrix = new Matrix4();

    /** Màn hình túi đồ 36 ô (Survival), Kho Creative và bảng thông số gỡ lỗi F3. */
    private InventoryScreen inventoryScreen;
    private CreativeInventoryScreen creativeInventoryScreen;
    private DebugOverlayRenderer debugOverlay;

    /** Đảm bảo {@link GameInputController} luôn sẵn sàng kể cả khi bài test chưa gọi {@link #create()}. */
    private GameInputController ensureInputController() {
        if (inputController == null) {
            inputController = new GameInputController(this);
        }
        return inputController;
    }

    // =========================================================================
    // 1. KHỞI TẠO ỨNG DỤNG
    // =========================================================================

    @Override
    public void create() {
        Gdx.gl.glClearColor(SKY_COLOR.x, SKY_COLOR.y, SKY_COLOR.z, 1.0f);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shaderProgram = new ShaderProgram();
        textureAtlas = new Texture();
        itemAtlas = new ItemTextureAtlas();
        camera = new Camera();

        world = new World();
        world.setAtlases(textureAtlas, itemAtlas);
        world.setWeather(weather);
        mobTextures = new MobTextureManager();
        try {
            mobRenderer = new MobRenderer(mobTextures, textureAtlas);
        } catch (RuntimeException | Error error) {
            dispose();
            throw error;
        }
        world.setMobRenderer(mobRenderer);

        uiRenderer = new UIRenderer();
        uiRenderer.setEnvironment(worldTime, weather);
        skyRenderer = new SkyRenderer();
        weatherRenderer = new WeatherRenderer();

        liquidAnimator = new LiquidAnimator();
        blockOutlineRenderer = new BlockOutlineRenderer();
        particleManager = new ParticleManager();
        fireOverlayMesh = new Mesh(
                Mesh.VertexDataType.VertexBufferObjectWithVAO,
                false,
                6,
                0,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "aPos"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aTexCoord"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "aShade")
        );

        player = new Player(world.getSpawnPosition());
        // Mặc định khởi chạy trò chơi ở chế độ Creative (F2 để chuyển qua lại Survival)
        player.setGameMode(CreativeGameMode.INSTANCE);
        player.attachControls(camera);
        world.registerPlayer(player);

        audioManager = new AudioManager();
        world.setAudio(audioManager);
        audioManager.startBackgroundMusic();

        itemUseHandler = new ItemUseHandler();
        try {
            firstPersonRenderer = new FirstPersonRenderer();
        } catch (RuntimeException | Error error) {
            dispose();
            throw error;
        }

        inventoryScreen = new InventoryScreen();
        creativeInventoryScreen = new CreativeInventoryScreen();
        debugOverlay = new DebugOverlayRenderer();
        uiRenderer.setInventoryScreen(inventoryScreen);
        uiRenderer.setCreativeInventoryScreen(creativeInventoryScreen);
        uiRenderer.setDebugOverlay(debugOverlay, world);

        GameInputController controller = ensureInputController();
        Gdx.input.setInputProcessor(this);

        // Khởi động vào màn hình chính OurCraft (Main Menu), mở khóa con trỏ chuột và phát nhạc nền ngay
        inMainMenuFlow = true;
        isPaused = false;
        uiRenderer.getMainMenuScreen().open();
        uiRenderer.getSettingsScreen().close();
        uiRenderer.getPauseScreen().close();
        uiRenderer.getHotkeysScreen().close();
        uiRenderer.getCreditsScreen().close();
        Gdx.input.setCursorCatched(false);
        controller.skipNextMouseDelta();
    }

    // =========================================================================
    // 2. VÒNG LẶP GAME LOOP CHÍNH
    // =========================================================================

    @Override
    public void render() {
        GameInputController controller = ensureInputController();
        float realDelta = Gdx.graphics.getDeltaTime();
        float deltaTime = Math.min(realDelta, 0.05f);

        if (audioManager != null) {
            audioManager.update();
        }

        // Khi đang ở màn hình chính / Settings / Hotkeys / Credits ngoài thế giới: chỉ vẽ Menu 2D + Steve 3D
        if (inMainMenuFlow) {
            int menuW = Gdx.graphics.getWidth();
            int menuH = Gdx.graphics.getHeight();
            if (uiRenderer != null) {
                uiRenderer.getMainMenuScreen().update(deltaTime);
            }
            Gdx.gl.glClearColor(0.04f, 0.05f, 0.07f, 1.0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            shaderProgram.bind();
            if (uiRenderer != null) {
                uiRenderer.renderFullScreenMenu(
                        shaderProgram,
                        camera,
                        audioManager,
                        firstPersonRenderer,
                        menuW,
                        menuH,
                        controller.getCurrentMouseX(),
                        controller.getCurrentMouseY()
                );
            }
            shaderProgram.unbind();
            return;
        }

        if (uiRenderer != null) {
            uiRenderer.updateToast(deltaTime);
        }

        if (!isPaused && !controller.isInventoryOpen()) {
            controller.processMouseLook();
        }

        Raycast.HitResult currentHit = null;
        float miningProgressRatio = 0.0f;

        if (!isPaused) {
            world.updateEntities(deltaTime, worldTime, weather);
            currentHit = Raycast.cast(world, camera.copyPosition(eyePosition), camera.copyFront(aimDirection),
                    GameInputController.RAYCAST_REACH, renderHit);
            miningProgressRatio = controller.isInventoryOpen()
                    ? 0.0f
                    : controller.updateMiningProgress(currentHit, deltaTime);

            worldTime.update(realDelta);
            weather.update(realDelta, world, eyePosition, player);
            audioManager.setRainGain(weather.rainVolume());

            if (itemUseHandler != null && !controller.isInventoryOpen()) {
                itemUseHandler.update(deltaTime, world, player, camera);
            }
            if (!controller.isInventoryOpen() && controller.isActivelyMining() && player != null) {
                player.continueSwing();
            }

            if (mobRenderer != null) {
                mobRenderer.setFireAnimationSeconds(weather.animationSeconds());
            }
            if (liquidAnimator != null) {
                liquidAnimator.update(realDelta, textureAtlas);
            }
            updateParticles(currentHit, miningProgressRatio, deltaTime);
        }

        camera.copyPosition(savedCameraEye);
        Camera.LiquidView liquid = Camera.LiquidView.AIR;
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        try {
            if (player != null) {
                player.applyRenderCameraPosition(world, camera, renderCameraPos);
            }
            camera.copyPosition(eyePosition);
            liquid = camera.liquidView(world);
            boolean submerged = liquid != Camera.LiquidView.AIR;
            if (submerged) {
                Gdx.gl.glClearColor(liquid.red, liquid.green, liquid.blue, 1f);
            } else {
                Gdx.gl.glClearColor(SKY_COLOR.x, SKY_COLOR.y, SKY_COLOR.z, 1f);
            }
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

            shaderProgram.bind();
            shaderProgram.enable3DWorldMode();

            Matrix4 projection = camera.getProjectionMatrix(screenWidth, screenHeight);
            Matrix4 view = player != null
                    ? player.computeRenderViewMatrix(camera, savedCameraEye, renderViewMatrix)
                    : camera.getViewMatrix();
            shaderProgram.setUniformMatrix4f("uProjection", projection);
            shaderProgram.setUniformMatrix4f("uView", view);
            if (submerged) {
                shaderProgram.enableLiquidFog(eyePosition, liquid);
            }

            float stormStrength = weather.skyStrength();
            skyRenderer.renderSky(shaderProgram, view, worldTime, stormStrength);

            textureAtlas.bind();
            float terrainDistance = submerged ? liquid.fogEnd
                    : skyRenderer.enableWorldFog(shaderProgram, eyePosition, stormStrength);
            world.render(shaderProgram, projection, view, eyePosition, renderCloudLayer, terrainDistance);
            if (!submerged) {
                shaderProgram.disableDistanceFog();
            }
            weatherRenderer.render(shaderProgram, world, eyePosition, weather);
            if (submerged) {
                shaderProgram.disableDistanceFog();
            }
            textureAtlas.unbind();
            shaderProgram.setUniformVec3("uWorldLight", 1f, 1f, 1f);

            if (particleManager != null) {
                particleManager.render(shaderProgram, camera, textureAtlas);
            }
            if (firstPersonRenderer != null && player != null
                    && player.getCameraMode() != Player.CameraMode.FIRST_PERSON) {
                firstPersonRenderer.renderThirdPersonPlayer(shaderProgram, player, itemUseHandler,
                        textureAtlas, itemAtlas);
            }

            if (!isPaused && !controller.isInventoryOpen() && currentHit != null && currentHit.isHit()) {
                int x = currentHit.getBlockX();
                int y = currentHit.getBlockY();
                int z = currentHit.getBlockZ();
                Block current = Block.fromId(world.getBlock(x, y, z));
                if (current == currentHit.getBlock()) {
                    if (blockOutlineRenderer != null) {
                        blockOutlineRenderer.render(shaderProgram, x, y, z, current,
                                world.getBlockState(x, y, z), miningProgressRatio);
                    } else {
                        uiRenderer.renderTargetBlockOverlay3D(shaderProgram, currentHit, miningProgressRatio,
                                current.getSelectionBounds(world.getBlockState(x, y, z)));
                    }
                }
            }

            if (firstPersonRenderer != null && player != null
                    && player.getCameraMode() == Player.CameraMode.FIRST_PERSON) {
                float viewportAspect = (float) Math.max(1, screenWidth) / Math.max(1, screenHeight);
                firstPersonRenderer.renderFirstPerson(shaderProgram, player, itemUseHandler,
                        textureAtlas, itemAtlas, projection, view, viewportAspect);
            }
        } finally {
            camera.setPosition(savedCameraEye.x, savedCameraEye.y, savedCameraEye.z);
        }

        uiRenderer.renderScreenEffects(shaderProgram, camera, screenWidth, screenHeight, liquid, player.getCactusFlash());
        renderFirstPersonFireOverlay(screenWidth, screenHeight);
        uiRenderer.render2DHUD(
                shaderProgram,
                textureAtlas,
                itemAtlas,
                camera,
                player,
                audioManager,
                screenWidth,
                screenHeight,
                miningProgressRatio,
                isPaused,
                controller.getCurrentMouseX(),
                controller.getCurrentMouseY()
        );

        shaderProgram.unbind();
    }

    /** Cập nhật hạt vỡ block, bắn nước và lửa cháy quanh thực thể đang cháy. */
    private void updateParticles(Raycast.HitResult currentHit, float miningProgressRatio, float deltaTime) {
        if (particleManager == null) {
            return;
        }
        if (miningProgressRatio > 0.0f && currentHit != null && currentHit.isHit()) {
            miningParticleTimer += deltaTime;
            if (miningParticleTimer >= 0.12f) {
                miningParticleTimer = 0.0f;
                particleManager.spawnBlockBreak(
                        currentHit.getBlockX(),
                        currentHit.getBlockY(),
                        currentHit.getBlockZ(),
                        currentHit.getBlock(),
                        3
                );
            }
        } else {
            miningParticleTimer = 0.0f;
        }

        if (player != null) {
            player.copyPosition(entityParticlePos);
            boolean playerInWater = world.liquidAt(
                    entityParticlePos.x, entityParticlePos.y + 0.1f, entityParticlePos.z) == Block.WATER;
            if (playerInWater && !wasPlayerInWater) {
                particleManager.spawnSplash(
                        entityParticlePos.x, entityParticlePos.y + 0.25f, entityParticlePos.z, 12);
            }
            wasPlayerInWater = playerInWater;
        }

        fireParticleTimer += deltaTime;
        if (fireParticleTimer >= 0.15f) {
            fireParticleTimer = 0.0f;
            int entityCount = world.entityCount();
            for (int i = 0; i < entityCount; i++) {
                Entity entity = world.entityAt(i);
                if (entity instanceof LivingEntity living && living.isAlive() && living.getFireTimer() > 0.0f) {
                    living.copyPosition(entityParticlePos);
                    particleManager.spawnFlame(
                            entityParticlePos.x,
                            entityParticlePos.y + living.getHeight() * 0.5f,
                            entityParticlePos.z
                    );
                }
            }
        }
        particleManager.update(deltaTime);
    }

    /** Vẽ mây trước pha trong suốt rồi gắn lại atlas cho nước và băng. */
    private void renderCloudLayer() {
        skyRenderer.renderClouds(shaderProgram, eyePosition, weather.animationSeconds(), weather.skyStrength());
        textureAtlas.bind();
    }

    /** Vẽ 2 dải lửa hoạt ảnh bán trong suốt ở cạnh dưới màn hình góc nhìn thứ nhất khi người chơi đang cháy. */
    private void renderFirstPersonFireOverlay(int screenWidth, int screenHeight) {
        if (player == null || !player.isAlive() || player.getFireTimer() <= 0f
                || player.getCameraMode() != Player.CameraMode.FIRST_PERSON
                || mobRenderer == null || fireOverlayMesh == null
                || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }
        com.badlogic.gdx.graphics.Texture fireTex0 = mobRenderer.getFireTexture0();
        com.badlogic.gdx.graphics.Texture fireTex1 = mobRenderer.getFireTexture1();
        if (fireTex0 == null || fireTex1 == null) {
            return;
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shaderProgram.enable2DUIMode(camera.getOrthoMatrix(screenWidth, screenHeight));
        shaderProgram.setUniform1i("uUseTexture", 1);
        shaderProgram.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, FIRE_OVERLAY_ALPHA);

        try {
            double animSeconds = weather.animationSeconds();
            float stripWidth = screenWidth * 0.56f;
            float leftHeight = screenHeight * 0.42f;
            float rightHeight = screenHeight * 0.40f;

            MobRenderer.fireFrameUV(animSeconds, mobRenderer.getFireFrameCount0(), fireOverlayUv);
            writeFireOverlayQuad(0.0f, screenHeight - leftHeight, stripWidth, leftHeight, fireOverlayUv);
            fireTex0.bind(0);
            fireOverlayMesh.setVertices(fireOverlayVertices, 0, fireOverlayVertices.length);
            fireOverlayMesh.render(shaderProgram.getGdxShader(), GL20.GL_TRIANGLES, 0, 6);

            MobRenderer.fireFrameUV(animSeconds + 0.25, mobRenderer.getFireFrameCount1(), fireOverlayUv);
            writeFireOverlayQuad(screenWidth - stripWidth, screenHeight - rightHeight, stripWidth, rightHeight,
                    fireOverlayUv);
            fireTex1.bind(0);
            fireOverlayMesh.setVertices(fireOverlayVertices, 0, fireOverlayVertices.length);
            fireOverlayMesh.render(shaderProgram.getGdxShader(), GL20.GL_TRIANGLES, 0, 6);
        } finally {
            if (textureAtlas != null) {
                textureAtlas.bind();
            }
            shaderProgram.enable3DWorldMode();
            Gdx.gl.glDisable(GL20.GL_BLEND);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        }
    }

    private void writeFireOverlayQuad(float x, float y, float w, float h, float[] uv) {
        float u0 = uv[0];
        float v0 = uv[1];
        float u1 = uv[2];
        float v1 = uv[3];
        writeFireOverlayVertex(0, x, y + h, u0, v1);
        writeFireOverlayVertex(6, x + w, y + h, u1, v1);
        writeFireOverlayVertex(12, x + w, y, u1, v0);
        writeFireOverlayVertex(18, x, y + h, u0, v1);
        writeFireOverlayVertex(24, x + w, y, u1, v0);
        writeFireOverlayVertex(30, x, y, u0, v0);
    }

    private void writeFireOverlayVertex(int offset, float x, float y, float u, float v) {
        fireOverlayVertices[offset] = x;
        fireOverlayVertices[offset + 1] = y;
        fireOverlayVertices[offset + 2] = 0.0f;
        fireOverlayVertices[offset + 3] = u;
        fireOverlayVertices[offset + 4] = v;
        fireOverlayVertices[offset + 5] = -1.0f;
    }

    // =========================================================================
    // 3. RESIZE & 4. DISPOSE
    // =========================================================================

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            Gdx.gl.glViewport(0, 0, width, height);
        }
    }

    @Override
    public void dispose() {
        ItemEntity.cleanupSharedMeshes();

        if (audioManager != null) {
            audioManager.dispose();
        }
        if (uiRenderer != null) {
            uiRenderer.dispose();
        }
        if (weatherRenderer != null) {
            weatherRenderer.dispose();
        }
        if (skyRenderer != null) {
            skyRenderer.dispose();
        }
        if (world != null) {
            world.dispose();
        }
        if (mobRenderer != null) {
            mobRenderer.dispose();
        }
        if (mobTextures != null) {
            mobTextures.dispose();
        }
        if (itemAtlas != null) {
            itemAtlas.dispose();
        }
        if (textureAtlas != null) {
            textureAtlas.dispose();
        }
        if (shaderProgram != null) {
            shaderProgram.dispose();
        }
        if (firstPersonRenderer != null) {
            firstPersonRenderer.dispose();
        }
        if (fireOverlayMesh != null) {
            fireOverlayMesh.dispose();
            fireOverlayMesh = null;
        }
        if (particleManager != null) {
            particleManager.dispose();
        }
        if (blockOutlineRenderer != null) {
            blockOutlineRenderer.dispose();
        }
        if (liquidAnimator != null) {
            liquidAnimator.dispose();
        }
        if (inventoryScreen != null) {
            inventoryScreen.dispose();
            inventoryScreen = null;
        }
        if (creativeInventoryScreen != null) {
            creativeInventoryScreen.dispose();
            creativeInventoryScreen = null;
        }
        if (debugOverlay != null) {
            debugOverlay.dispose();
            debugOverlay = null;
        }
    }

    // =========================================================================
    // 5. CẦU NỐI GameInputController.Host & ỦY QUYỀN InputProcessor
    // =========================================================================

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public Camera getCamera() {
        return camera;
    }

    @Override
    public AudioManager getAudioManager() {
        return audioManager;
    }

    @Override
    public UIRenderer getUiRenderer() {
        return uiRenderer;
    }

    @Override
    public InventoryScreen getInventoryScreen() {
        return inventoryScreen;
    }

    @Override
    public CreativeInventoryScreen getCreativeInventoryScreen() {
        return creativeInventoryScreen;
    }

    @Override
    public DebugOverlayRenderer getDebugOverlay() {
        return debugOverlay;
    }

    @Override
    public ItemUseHandler getItemUseHandler() {
        return itemUseHandler;
    }

    @Override
    public ParticleManager getParticleManager() {
        return particleManager;
    }

    @Override
    public WeatherManager getWeather() {
        return weather;
    }

    @Override
    public WorldTime getWorldTime() {
        return worldTime;
    }

    @Override
    public boolean isPaused() {
        return isPaused;
    }

    @Override
    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    @Override
    public boolean isInMainMenuFlow() {
        return inMainMenuFlow;
    }

    @Override
    public void setInMainMenuFlow(boolean inMainMenuFlow) {
        this.inMainMenuFlow = inMainMenuFlow;
    }

    @Override
    public boolean keyDown(int keycode) {
        return ensureInputController().keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return ensureInputController().keyUp(keycode);
    }

    @Override
    public boolean keyTyped(char character) {
        return ensureInputController().keyTyped(character);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return ensureInputController().touchDown(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return ensureInputController().touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return ensureInputController().touchDragged(screenX, screenY, pointer);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return ensureInputController().mouseMoved(screenX, screenY);
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return ensureInputController().scrolled(amountX, amountY);
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return ensureInputController().touchCancelled(screenX, screenY, pointer, button);
    }
}
