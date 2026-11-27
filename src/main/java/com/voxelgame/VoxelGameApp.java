package com.voxelgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.AudioManager;
import com.voxelgame.engine.BiomeType;
import com.voxelgame.engine.BlockOutlineRenderer;
import com.voxelgame.engine.Camera;
import com.voxelgame.engine.DebugOverlayRenderer;
import com.voxelgame.engine.FirstPersonRenderer;
import com.voxelgame.engine.InventoryScreenRenderer;
import com.voxelgame.engine.ItemTextureAtlas;
import com.voxelgame.engine.ItemUseHandler;
import com.voxelgame.engine.LeafDecaySystem;
import com.voxelgame.engine.LiquidAnimator;
import com.voxelgame.engine.MobRenderer;
import com.voxelgame.engine.MobTextureManager;
import com.voxelgame.engine.ParticleManager;
import com.voxelgame.engine.Raycast;
import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.SkyRenderer;
import com.voxelgame.engine.Texture;
import com.voxelgame.engine.UIRenderer;
import com.voxelgame.engine.WeatherManager;
import com.voxelgame.engine.WeatherRenderer;
import com.voxelgame.engine.World;
import com.voxelgame.engine.WorldTime;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.Item;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

/**
 * Lớp ứng dụng chính của VoxelGame, kế thừa {@link ApplicationAdapter} và triển khai {@link InputProcessor}.
 *
 * Lớp này đảm nhận toàn bộ vai trò của {@code Window.java} cũ, bao gồm:
 * - Khởi tạo tài nguyên đồ họa, âm thanh, thế giới voxel ({@link #create()})
 * - Vòng lặp Game Loop: cập nhật logic + kết xuất đồ họa ({@link #render()})
 * - Xử lý sự kiện bàn phím, chuột thông qua giao diện {@link InputProcessor}
 * - Giải phóng toàn bộ tài nguyên khi đóng game ({@link #dispose()})
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Quan hệ Hợp thành (Composition): {@code Player} sở hữu {@code Hotbar},
 *    truy cập qua {@code player.getHotbar()}.
 * 2. Đa hình trên danh sách Thực thể: {@code world.updateEntities(deltaTime)}
 *    cập nhật đa hình cho cả {@code Player} lẫn mọi {@code ItemEntity}.
 * 3. Đa hình giải phóng tài nguyên: Toàn bộ hệ thống con triển khai
 *    {@code com.badlogic.gdx.utils.Disposable} và được gọi {@code dispose()} đồng loạt.
 */
public class VoxelGameApp extends ApplicationAdapter implements InputProcessor {

    /** Thứ tự ghé thăm các biome theo trục X từ tây sang đông. */
    private static final BiomeType[] BIOME_TOUR = {
            BiomeType.SNOWY_PLAINS, BiomeType.FOREST, BiomeType.PLAINS, BiomeType.FLOWER_FOREST, BiomeType.DESERT
    };
    private int nextBiomeIndex;

    /** Trạng thái Ctrl theo thứ tự sự kiện trong hàng đợi bàn phím của libGDX. */
    private boolean leftControlDown;
    private boolean rightControlDown;

    // =====================================================================
    // Các hệ thống cốt lõi của Engine
    // =====================================================================

    /** Chương trình Shader GLSL 330 Core dùng chung cho cả chế độ 3D (Thế giới) và 2D (UI) */
    private ShaderProgram shaderProgram;

    /** Texture Atlas khối và vật phẩm độc lập. */
    private Texture textureAtlas;
    private ItemTextureAtlas itemAtlas;
    /** Texture và mesh phân khớp của mob dùng chung theo biến thể. */
    private MobTextureManager mobTextures;
    private MobRenderer mobRenderer;

    /** Quản lý vòng nhạc nền, hiệu ứng vật liệu và điều chỉnh âm lượng nhạc. */
    private AudioManager audioManager;

    /** Camera góc nhìn thứ nhất (First-Person), Euler Yaw/Pitch, Dynamic FOV */
    private Camera camera;

    /** Thế giới voxel, sinh địa hình tự nhiên với seed 20261114L. */
    private World world;

    /** Người chơi với điều khiển WASD, Nhảy/Bơi, Bay, Rón rén, Sprint và va chạm AABB */
    private Player player;

    /** Kết xuất Overlay 3D quanh block mục tiêu và toàn bộ giao diện 2D HUD + Pause Menu */
    private UIRenderer uiRenderer;

    /** Đồng hồ, thời tiết và hai lượt vẽ môi trường của thế giới hiện tại. */
    private final WorldTime worldTime = new WorldTime();
    private final WeatherManager weather = new WeatherManager(World.WORLD_SEED);
    private SkyRenderer skyRenderer;
    private WeatherRenderer weatherRenderer;
    private final Runnable renderCloudLayer = this::renderCloudLayer;
    // === HOOK: TRACK B - FIELDS ===
    /** Bộ phát hoạt ảnh 32 frame nước và 20 frame dung nham trên GPU. */
    private LiquidAnimator liquidAnimator;
    /** Bộ vẽ khung dây chọn block và lớp phủ vết nứt 10 giai đoạn. */
    private BlockOutlineRenderer blockOutlineRenderer;
    /** Hệ thống pool tĩnh 512 hạt 3D zero-GC. */
    private ParticleManager particleManager;
    /** Bộ đếm nhịp sinh hạt khi giữ chuột đào và khi thực thể đang cháy. */
    private float miningParticleTimer;
    private float fireParticleTimer;
    /** Theo dõi trạng thái người chơi chạm nước để sinh hạt bắn nước tức thời. */
    private boolean wasPlayerInWater;
    /** Bộ đệm tọa độ tái sử dụng khi sinh hạt quanh thực thể (zero-GC). */
    private final Vector3 entityParticlePos = new Vector3();
    /** Độ mờ của lớp phủ lửa hoạt ảnh ở cạnh dưới màn hình góc nhìn 1. */
    private static final float FIRE_OVERLAY_ALPHA = 0.80f;
    /** Mesh quad 2D (6 đỉnh × 6 floats) và bộ đệm tái sử dụng cho lớp phủ lửa góc nhìn 1 (zero-GC). */
    private Mesh fireOverlayMesh;
    private final float[] fireOverlayVertices = new float[6 * 6];
    private final float[] fireOverlayUv = new float[4];
    // === END HOOK: TRACK B - FIELDS ===

    // =====================================================================
    // Trạng thái chuột để xoay Camera 3D hoặc tương tác trên Pause Menu 2D
    // =====================================================================

    /** Tọa độ chuột khung hình trước (dùng để tính delta xoay Camera) */
    private float lastMouseX;
    private float lastMouseY;

    /** Tọa độ chuột hiện tại (dùng cho Hit-Testing nút bấm trên Pause Menu) */
    private float currentMouseX;
    private float currentMouseY;

    /**
     * Cờ bỏ qua delta chuột ở lần đọc đầu tiên.
     * Khi lần đầu bắt chuột hoặc khi vừa thoát Pause Menu, con trỏ chuột bị hệ điều hành
     * đưa về tâm màn hình tạo ra giá trị delta đột biến. Cờ này đảm bảo bỏ qua delta đó
     * để góc nhìn Camera không bị giật.
     */
    private boolean skipNextMouseDelta = true;

    /** Độ nhạy chuột khi xoay Camera (giữ nguyên giá trị gốc 0.12f) */
    private static final float MOUSE_SENSITIVITY = 0.12f;

    // =====================================================================
    // Trạng thái Tạm dừng (Pause Menu)
    // =====================================================================

    /** Cờ đánh dấu game đang ở trạng thái Tạm dừng (hiển thị Pause Menu, đóng băng cập nhật) */
    private boolean isPaused = false;

    /** Cờ đánh dấu người dùng đang kéo thanh trượt Volume trên Pause Menu */
    private boolean isDraggingVolumeSlider = false;

    // =====================================================================
    // Trạng thái giữ chuột trái để đập Block theo Độ cứng (Mining Hardness)
    // =====================================================================

    /** Cờ đánh dấu chuột trái đang được giữ (người chơi đang đào block) */
    private boolean isLeftMouseDown = false;

    /** Tọa độ block đang bị đào (Integer.MIN_VALUE = chưa có mục tiêu) */
    private int miningTargetX = Integer.MIN_VALUE;
    private int miningTargetY = Integer.MIN_VALUE;
    private int miningTargetZ = Integer.MIN_VALUE;

    /** Thời gian đã đào tích lũy trên block hiện tại (giây) */
    private float miningElapsedTime = 0.0f;
    /** Mốc tiếp theo để phát tiếng gõ khi giữ chuột đào cùng một khối. */
    private float nextMiningSoundTime;

    // =====================================================================
    // Hằng số đồ họa và gameplay
    // =====================================================================

    /** Màu bầu trời xanh nhạt dùng khi xóa khung hình. */
    private static final Vector3 SKY_COLOR = new Vector3(0.53f, 0.81f, 0.92f);

    /** Tầm với tối đa của tia bắn Raycast khi đào/đặt Block (5 ô voxel) */
    private static final float RAYCAST_REACH = 5.0f;
    /** Tầm với riêng dành cho đòn cận chiến. */
    private static final float MELEE_REACH = 3.0f;
    /** Bộ đệm riêng cho lần ngắm trong render và lần bấm đặt block. */
    private final Vector3 eyePosition = new Vector3();
    private final Vector3 aimDirection = new Vector3();
    private final Raycast.HitResult renderHit = Raycast.HitResult.miss();
    private final Raycast.HitResult placementHit = Raycast.HitResult.miss();
    /** Hai kết quả dùng lại để chọn thực thể và kiểm tra tia bị vật cản che. */
    private final Raycast.HitResult occluderHit = Raycast.HitResult.miss();
    private final Raycast.EntityHitResult entityHit = new Raycast.EntityHitResult();
    // === HOOK: TRACK A - FIELDS ===
    /** Bộ quản lý sử dụng vật phẩm chủ động (Cung, Giáo sắt, Thức ăn, Thuốc, Đạn ném, Sừng dê). */
    private ItemUseHandler itemUseHandler;
    /** Bộ kết xuất tay/vật phẩm góc nhìn thứ nhất và nhân vật Steve góc nhìn thứ ba (F5). */
    private FirstPersonRenderer firstPersonRenderer;
    /** Bộ đệm lưu và khôi phục tọa độ/ma trận camera khi áp dụng View Bobbing hoặc góc nhìn F5. */
    private final Vector3 savedCameraEye = new Vector3();
    private final Vector3 renderCameraPos = new Vector3();
    private final Matrix4 renderViewMatrix = new Matrix4();
    // === END HOOK: TRACK A - FIELDS ===

    // === HOOK: TRACK C - FIELDS ===
    // Khai báo màn hình túi đồ, F3 debug và mô phỏng sinh tồn của Track C tại đây.
    private InventoryScreenRenderer inventoryScreen;
    private DebugOverlayRenderer debugOverlay;

    /** Kiểm tra giao diện túi đồ 36 ô hiện có đang mở hay không. */
    private boolean isInventoryOpen() {
        return inventoryScreen != null && inventoryScreen.isOpen();
    }

    /** Bật/tắt giao diện túi đồ 36 ô khi nhấn phím E, giữ nguyên vòng đời thế giới. */
    private void toggleInventory() {
        if (inventoryScreen == null || player == null) {
            return;
        }
        if (inventoryScreen.isOpen()) {
            closeInventory();
        } else {
            inventoryScreen.open();
            isLeftMouseDown = false;
            resetMiningProgress();
            if (itemUseHandler != null) {
                itemUseHandler.onHotbarSlotChanged();
            }
            Gdx.input.setCursorCatched(false);
            skipNextMouseDelta = true;
        }
    }

    /** Đóng giao diện túi đồ, trả item đang cầm trên con trỏ về kho/đất và khóa lại chuột. */
    private void closeInventory() {
        if (inventoryScreen == null || !inventoryScreen.isOpen()) {
            return;
        }
        inventoryScreen.close(player, world);
        isLeftMouseDown = false;
        resetMiningProgress();
        Gdx.input.setCursorCatched(true);
        skipNextMouseDelta = true;
    }
    // === END HOOK: TRACK C - FIELDS ===

    // =====================================================================
    // VÒNG ĐỜI ỨNG DỤNG LIBGDX
    // =====================================================================

    /**
     * Khởi tạo toàn bộ tài nguyên đồ họa, âm thanh, thế giới và người chơi.
     * Phương thức này được libGDX gọi đúng 1 lần sau khi cửa sổ và OpenGL Context đã sẵn sàng.
     */
    @Override
    public void create() {
        // -----------------------------------------------------------------
        // 1. Cấu hình trạng thái OpenGL nền tảng
        // -----------------------------------------------------------------

        // Màu xóa nền bầu trời xanh nhạt.
        Gdx.gl.glClearColor(SKY_COLOR.x, SKY_COLOR.y, SKY_COLOR.z, 1.0f);

        // Bật kiểm tra chiều sâu (Depth Test) để các vật thể 3D che khuất chính xác theo khoảng cách
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        // Bật loại bỏ mặt sau (Back-Face Culling) để tăng tốc kết xuất:
        // Chỉ vẽ mặt trước (front face) của tam giác, bỏ qua mặt sau ẩn bên trong khối
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);

        // Quy ước mặt trước theo thứ tự quấn đỉnh ngược chiều kim đồng hồ (Counter-Clockwise)
        Gdx.gl.glFrontFace(GL20.GL_CCW);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // -----------------------------------------------------------------
        // 2. Khởi tạo toàn bộ các hệ thống con của Engine
        // -----------------------------------------------------------------

        // Shader GLSL 330 Core: Biên dịch, liên kết Vertex/Fragment Shader và cache Uniform Locations
        shaderProgram = new ShaderProgram();

        // Texture Atlas khối và Atlas vật phẩm độc lập
        textureAtlas = new Texture();
        itemAtlas = new ItemTextureAtlas();

        // Camera góc nhìn thứ nhất: Quản lý Euler Yaw/Pitch, ma trận View/Projection, Dynamic FOV
        camera = new Camera();

        // Sinh địa hình và chuẩn bị mesh quanh tâm bản đồ.
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

        // UI Renderer: Chuẩn bị tài nguyên GPU cho Overlay 3D, Crosshair, Hotbar, Pause Menu, font bitmap
        uiRenderer = new UIRenderer();
        uiRenderer.setEnvironment(worldTime, weather);
        skyRenderer = new SkyRenderer();
        weatherRenderer = new WeatherRenderer();
        // === HOOK: TRACK B - CREATE ===
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
        // === END HOOK: TRACK B - CREATE ===

        // -----------------------------------------------------------------
        // 3. Khởi tạo Player, gắn điều khiển và đăng ký vào World
        // -----------------------------------------------------------------

        // Đặt chân người chơi ngay trên mặt đất tại tâm bản đồ.
        player = new Player(world.getSpawnPosition());

        // Gắn tham chiếu Camera để Player cập nhật hướng nhìn và tầm mắt
        player.attachControls(camera);

        // Đăng ký Player vào danh sách Entity đa hình của World
        // (để world.updateEntities() cập nhật cả Player lẫn ItemEntity)
        world.registerPlayer(player);

        // -----------------------------------------------------------------
        // 4. Khởi tạo và phát lặp nhạc nền
        // -----------------------------------------------------------------

        // Khởi tạo âm thanh cho tương tác trong World và bắt đầu vòng nhạc đã xáo.
        audioManager = new AudioManager();
        world.setAudio(audioManager);
        audioManager.startBackgroundMusic();
        // === HOOK: TRACK A - CREATE ===
        itemUseHandler = new ItemUseHandler();
        try {
            firstPersonRenderer = new FirstPersonRenderer();
        } catch (RuntimeException | Error error) {
            dispose();
            throw error;
        }
        // === END HOOK: TRACK A - CREATE ===

        // === HOOK: TRACK C - CREATE ===
        // Khởi tạo LeafDecaySystem, LiquidFlowSimulator, DebugOverlayRenderer của Track C tại đây.
        inventoryScreen = new InventoryScreenRenderer();
        debugOverlay = new DebugOverlayRenderer();
        if (uiRenderer != null) {
            uiRenderer.setInventoryScreen(inventoryScreen);
            uiRenderer.setDebugOverlay(debugOverlay, world);
        }
        // === END HOOK: TRACK C - CREATE ===

        // -----------------------------------------------------------------
        // 5. Thiết lập xử lý Input và bắt chuột
        // -----------------------------------------------------------------

        // Đăng ký lớp hiện tại làm bộ xử lý sự kiện bàn phím/chuột cho libGDX
        Gdx.input.setInputProcessor(this);

        // Khóa con trỏ chuột ở tâm màn hình để xoay Camera 360 độ liên tục
        Gdx.input.setCursorCatched(true);

        // Đặt cờ bỏ qua delta chuột ở khung hình đầu tiên (tránh giật Camera)
        skipNextMouseDelta = true;
    }

    /**
     * Vòng lặp chính của trò chơi (Main Game Loop).
     * Phương thức này được libGDX gọi mỗi khung hình (frame), thực hiện 2 việc:
     *   1. Cập nhật logic game (vật lý, va chạm, đào/đặt block, nhập liệu chuột)
     *   2. Kết xuất đồ họa 3D (Thế giới + Overlay) và 2D (HUD + Pause Menu)
     */
    @Override
    public void render() {
        // =================================================================
        // BƯỚC 1: Tính delta thời gian và giới hạn tối đa 0.05 giây
        // =================================================================
        float realDelta = Gdx.graphics.getDeltaTime();
        float deltaTime = Math.min(realDelta, 0.05f);
        // Nhạc và khoảng nghỉ giữa hai bài tiếp tục chạy khi mở Pause Menu.
        if (audioManager != null) {
            audioManager.update();
        }

        // =================================================================
        // BƯỚC 2: Xử lý xoay Camera bằng di chuyển chuột (khi không Pause và không mở túi đồ)
        // =================================================================
        if (!isPaused && !isInventoryOpen()) {
            processMouseLook();
        }

        // =================================================================
        // BƯỚC 3: Cập nhật logic game (khi không Pause)
        // =================================================================
        Raycast.HitResult currentHit = null;
        float miningProgressRatio = 0.0f;

        if (!isPaused) {
            // Dùng môi trường chung cho người chơi, vật phẩm và chu kỳ sinh/thu hồi mob.
            world.updateEntities(deltaTime, worldTime, weather);

            // Bắn tia từ mắt Camera theo hướng nhìn để tìm block đang ngắm
            currentHit = Raycast.cast(world, camera.copyPosition(eyePosition), camera.copyFront(aimDirection),
                    RAYCAST_REACH, renderHit);

            // Cập nhật tiến trình đào block nếu đang giữ chuột trái (tắt khi túi đồ đang mở)
            miningProgressRatio = isInventoryOpen() ? 0.0f : updateMiningProgress(currentHit, deltaTime);

            // Thời gian môi trường dùng giây thực; vật lý vẫn giữ delta đã giới hạn.
            worldTime.update(realDelta);
            weather.update(realDelta, world, eyePosition, player);
            audioManager.setRainGain(weather.rainVolume());
            // === HOOK: TRACK A - UPDATE ===
            if (itemUseHandler != null && !isInventoryOpen()) {
                itemUseHandler.update(deltaTime, world, player, camera);
            }
            if (!isInventoryOpen() && isLeftMouseDown && miningTargetX != Integer.MIN_VALUE && player != null) {
                player.continueSwing();
            }
            // === END HOOK: TRACK A - UPDATE ===

            // === HOOK: TRACK B - UPDATE ===
            if (mobRenderer != null) {
                mobRenderer.setFireAnimationSeconds(weather.animationSeconds());
            }
            if (liquidAnimator != null) {
                liquidAnimator.update(realDelta, textureAtlas);
            }
            if (particleManager != null) {
                // Sinh mảnh vỡ nhỏ theo nhịp khi đang đào block.
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

                // Sinh hạt bắn nước khi người chơi vừa bước vào khối nước.
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

                // Sinh hạt lửa quanh người chơi hoặc mob đang cháy (getFireTimer() > 0).
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
            // === END HOOK: TRACK B - UPDATE ===

            // === HOOK: TRACK C - UPDATE ===
            // Cập nhật LeafDecaySystem, LiquidFlowSimulator và màn hình túi đồ Track C tại đây.
            // === END HOOK: TRACK C - UPDATE ===
        }

        // =================================================================
        // BƯỚC 4: Xóa bộ đệm màu và bộ đệm chiều sâu
        // =================================================================
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

            // =================================================================
            // BƯỚC 5: Kết xuất Thế giới 3D (Các Chunk địa hình + Các Entity đa hình)
            // =================================================================
            shaderProgram.bind();
            shaderProgram.enable3DWorldMode();

            // Dùng cùng ma trận cho Shader và kiểm tra khung nhìn, kể cả khi FOV thay đổi.
            Matrix4 projection = camera.getProjectionMatrix(screenWidth, screenHeight);
            Matrix4 view = player != null
                    ? player.computeRenderViewMatrix(camera, savedCameraEye, renderViewMatrix)
                    : camera.getViewMatrix();
            shaderProgram.setUniformMatrix4f("uProjection", projection);
            shaderProgram.setUniformMatrix4f("uView", view);
            if (submerged) {
                shaderProgram.enableLiquidFog(eyePosition, liquid);
            }

            // Vòm trời và thiên thể nằm sau mọi hình học; mây và giáng thủy xét độ sâu.
            float stormStrength = weather.skyStrength();
            skyRenderer.renderSky(shaderProgram, view, worldTime, stormStrength);

            // Kết xuất các chunk giao khung nhìn và toàn bộ thực thể trong thế giới.
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
            // === HOOK: TRACK B - RENDER 3D ===
            if (particleManager != null) {
                particleManager.render(shaderProgram, camera, textureAtlas);
            }
            // === END HOOK: TRACK B - RENDER 3D ===

            // === HOOK: TRACK A - RENDER VIEWMODEL ===
            if (firstPersonRenderer != null && player != null
                    && player.getCameraMode() != Player.CameraMode.FIRST_PERSON) {
                firstPersonRenderer.renderThirdPersonPlayer(shaderProgram, player, itemUseHandler,
                        textureAtlas, itemAtlas);
            }
            // === END HOOK: TRACK A - RENDER VIEWMODEL ===

            // =================================================================
            // BƯỚC 6: Vẽ hiệu ứng Overlay 3D quanh block đang ngắm/đào
            // =================================================================
            if (!isPaused && !isInventoryOpen() && currentHit != null && currentHit.isHit()) {
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

            // Kết xuất tay/vật phẩm góc nhìn thứ nhất sau khối ngắm 3D (xóa Depth Buffer bên trong)
            if (firstPersonRenderer != null && player != null
                    && player.getCameraMode() == Player.CameraMode.FIRST_PERSON) {
                float viewportAspect = (float) Math.max(1, screenWidth) / Math.max(1, screenHeight);
                firstPersonRenderer.renderFirstPerson(shaderProgram, player, itemUseHandler,
                        textureAtlas, itemAtlas, projection, view, viewportAspect);
            }
        } finally {
            camera.setPosition(savedCameraEye.x, savedCameraEye.y, savedCameraEye.z);
        }

        // =================================================================
        // BƯỚC 7: Kết xuất Giao diện 2D (Crosshair, Hotbar, Thanh đào, Pause Menu)
        // =================================================================
        uiRenderer.renderScreenEffects(shaderProgram, camera, screenWidth, screenHeight, liquid, player.getCactusFlash());
        // === HOOK: TRACK B - RENDER 2D ===
        renderFirstPersonFireOverlay(screenWidth, screenHeight);
        // === END HOOK: TRACK B - RENDER 2D ===
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
                currentMouseX,
                currentMouseY
        );

        shaderProgram.unbind();
    }

    /** Vẽ mây trước pha trong suốt rồi gắn lại atlas cho nước và băng. */
    private void renderCloudLayer() {
        skyRenderer.renderClouds(shaderProgram, eyePosition, weather.animationSeconds(), weather.skyStrength());
        textureAtlas.bind();
    }

    // === HOOK: TRACK B - HELPERS ===
    /**
     * Vẽ 2 dải lửa hoạt ảnh bán trong suốt ({@code alpha = 0.80f}) ở cạnh dưới màn hình 2D
     * ngay trước lớp HUD khi người chơi đang cháy ({@code player.getFireTimer() > 0f}),
     * sau đó khôi phục hoàn toàn chế độ 3D và trạng thái OpenGL.
     */
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

            // Dải lửa trái dùng fire_0.png.
            MobRenderer.fireFrameUV(animSeconds, mobRenderer.getFireFrameCount0(), fireOverlayUv);
            writeFireOverlayQuad(0.0f, screenHeight - leftHeight, stripWidth, leftHeight, fireOverlayUv);
            fireTex0.bind(0);
            fireOverlayMesh.setVertices(fireOverlayVertices, 0, fireOverlayVertices.length);
            fireOverlayMesh.render(shaderProgram.getGdxShader(), GL20.GL_TRIANGLES, 0, 6);

            // Dải lửa phải dùng fire_1.png lệch pha 0.25s để hai ngọn lửa nhấp nhô tự nhiên.
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

    /** Điền 6 đỉnh của một dải lửa 2D trực giao vào {@link #fireOverlayVertices} (zero-GC). */
    private void writeFireOverlayQuad(float x, float y, float w, float h, float[] uv) {
        float u0 = uv[0];
        float v0 = uv[1];
        float u1 = uv[2];
        float v1 = uv[3];
        // Phép chiếu trực giao 2D có y=0 ở đỉnh màn hình, y=height ở đáy màn hình.
        writeFireOverlayVertex(0, x, y + h, u0, v1);
        writeFireOverlayVertex(6, x + w, y + h, u1, v1);
        writeFireOverlayVertex(12, x + w, y, u1, v0);
        writeFireOverlayVertex(18, x, y + h, u0, v1);
        writeFireOverlayVertex(24, x + w, y, u1, v0);
        writeFireOverlayVertex(30, x, y, u0, v0);
    }

    /** Ghi 1 đỉnh 6 float (x, y, 0, u, v, -1) vào bộ đệm lớp phủ lửa 2D. */
    private void writeFireOverlayVertex(int offset, float x, float y, float u, float v) {
        fireOverlayVertices[offset] = x;
        fireOverlayVertices[offset + 1] = y;
        fireOverlayVertices[offset + 2] = 0.0f;
        fireOverlayVertices[offset + 3] = u;
        fireOverlayVertices[offset + 4] = v;
        fireOverlayVertices[offset + 5] = -1.0f;
    }
    // === END HOOK: TRACK B - HELPERS ===

    /**
     * Xử lý xoay Camera bằng delta chuột mỗi khung hình.
     * Sử dụng {@code Gdx.input.getDeltaX()} và {@code getDeltaY()} để lấy khoảng cách
     * di chuyển chuột tích lũy từ khung hình trước.
     *
     * Kỹ thuật chống giật góc nhìn (Anti-Camera Jumping):
     * Khi vừa bắt chuột lại (sau Pause hoặc lần đầu khởi chạy), cờ {@code skipNextMouseDelta}
     * được bật để bỏ qua delta đột biến do hệ điều hành đưa con trỏ về tâm màn hình.
     */
    private void processMouseLook() {
        if (skipNextMouseDelta) {
            // Bỏ qua delta chuột ở khung hình đầu tiên sau khi bắt lại chuột
            // để tránh giật góc nhìn Camera đột ngột
            skipNextMouseDelta = false;
            return;
        }

        // Lấy khoảng cách di chuyển chuột tích lũy từ khung hình trước
        float deltaX = Gdx.input.getDeltaX();
        float deltaY = Gdx.input.getDeltaY();

        // Áp dụng độ nhạy chuột và xoay Camera
        // deltaX > 0 nghĩa là chuột di chuyển sang phải -> tăng yaw (quay phải)
        // deltaY > 0 trong libGDX nghĩa là chuột di chuyển xuống dưới -> giảm pitch (nhìn xuống)
        // Nên truyền -deltaY để khớp với quy ước "kéo chuột lên = nhìn lên"
        if (camera != null) {
            camera.rotate(deltaX * MOUSE_SENSITIVITY, -deltaY * MOUSE_SENSITIVITY);
        }
    }

    // =====================================================================
    // XỬ LÝ TRẠNG THÁI PAUSE MENU
    // =====================================================================

    /**
     * Chuyển game sang trạng thái Tạm dừng (Pause).
     * - Dừng cập nhật Entity và Raycast
     * - Mở khóa con trỏ chuột để tương tác với Pause Menu
     * - Reset tiến trình đào block đang dở
     */
    private void pauseGame() {
        if (isInventoryOpen()) {
            inventoryScreen.close(player, world);
        }
        isPaused = true;
        isLeftMouseDown = false;
        isDraggingVolumeSlider = false;
        resetMiningProgress();
        if (itemUseHandler != null) {
            itemUseHandler.onHotbarSlotChanged();
        }
        if (audioManager != null) {
            audioManager.setUnderwater(false);
            audioManager.setRainPaused(true);
        }

        // Mở khóa con trỏ chuột để người dùng click vào nút Resume, chỉnh Volume
        Gdx.input.setCursorCatched(false);
    }

    /**
     * Tiếp tục game từ trạng thái Tạm dừng.
     * - Bật lại cập nhật Entity và Raycast
     * - Khóa lại con trỏ chuột ở tâm màn hình để xoay Camera
     * - Bật cờ {@code skipNextMouseDelta} để tránh giật góc nhìn Camera
     */
    private void resumeGame() {
        isPaused = false;
        isDraggingVolumeSlider = false;
        if (audioManager != null) {
            audioManager.setRainPaused(false);
        }

        // Khóa lại con trỏ chuột để tiếp tục xoay Camera
        Gdx.input.setCursorCatched(true);

        // Bật cờ bỏ qua delta chuột ở khung hình đầu tiên sau khi khóa lại chuột
        // để con trỏ bị kéo về tâm không tạo giật góc nhìn đột ngột
        skipNextMouseDelta = true;
    }

    // =====================================================================
    // XỬ LÝ TƯƠNG TÁC VỚI PAUSE MENU
    // =====================================================================

    /**
     * Xử lý sự kiện click chuột trên Pause Menu.
     * Kiểm tra Hit-Testing cho nút Resume, nút giảm/tăng Volume, và thanh trượt Volume.
     *
     * @param screenX tọa độ X chuột trên màn hình (tính từ trái)
     * @param screenY tọa độ Y chuột trên màn hình (tính từ đỉnh, hệ Top-Left của libGDX)
     * @param pressed true nếu vừa nhấn chuột, false nếu vừa nhả chuột
     */
    private void handlePauseMenuMouseClick(float screenX, float screenY, boolean pressed) {
        if (uiRenderer == null) {
            return;
        }

        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();

        // Nhả chuột: dừng kéo thanh trượt Volume
        if (!pressed) {
            isDraggingVolumeSlider = false;
            return;
        }

        // Nhấn chuột: kiểm tra vị trí click

        // Kiểm tra nút "RESUME GAME"
        if (uiRenderer.isInsideResumeButton(screenX, screenY, screenWidth, screenHeight)) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
            }
            resumeGame();
            return;
        }

        // Kiểm tra nút "[-]" giảm âm lượng 10%
        if (uiRenderer.isInsideVolumeMinus(screenX, screenY, screenWidth, screenHeight)) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
                audioManager.changeVolume(-0.10f);
            }
            return;
        }

        // Kiểm tra nút "[+]" tăng âm lượng 10%
        if (uiRenderer.isInsideVolumePlus(screenX, screenY, screenWidth, screenHeight)) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
                audioManager.changeVolume(0.10f);
            }
            return;
        }

        // Kiểm tra click vào thanh trượt Volume -> bắt đầu kéo
        float sliderVal = uiRenderer.getSliderVolumeAtCursor(screenX, screenY, screenWidth, screenHeight);
        if (sliderVal >= 0.0f && audioManager != null) {
            isDraggingVolumeSlider = true;
            audioManager.setVolume(sliderVal);
        }
    }

    // =====================================================================
    // XỬ LÝ ĐÀO BLOCK (MINING) VÀ ĐẶT BLOCK
    // =====================================================================

    /**
     * Cập nhật tiến trình đào block khi đang giữ chuột trái.
     * Tích lũy thời gian đào ({@code miningElapsedTime}) cho đến khi đạt ngưỡng
     * Độ cứng ({@code hardness}) của block mục tiêu, lúc đó sẽ phá hủy block
     * và sinh {@code ItemEntity} tại vị trí đó.
     *
     * @param currentHit kết quả Raycast từ mắt Camera đến block đang ngắm
     * @param deltaTime  khoảng thời gian từ khung hình trước (giây)
     * @return tỷ lệ tiến trình đào (0.0f đến 1.0f), hoặc 0.0f nếu không đang đào
     */
    private float updateMiningProgress(Raycast.HitResult currentHit, float deltaTime) {
        // Nếu không giữ chuột trái hoặc không có block mục tiêu -> reset
        if (!isLeftMouseDown || currentHit == null || !currentHit.isHit()) {
            resetMiningProgress();
            return 0.0f;
        }

        // Lấy loại block đang nhắm
        Block targetBlock = currentHit.getBlock();

        // Block không thể phá hủy (hardness < 0) như Air, Bedrock, Water, Lava -> reset
        if (!targetBlock.isBreakable()) {
            resetMiningProgress();
            return 0.0f;
        }

        // Lấy tọa độ ô block đang nhắm
        int hitX = currentHit.getBlockX();
        int hitY = currentHit.getBlockY();
        int hitZ = currentHit.getBlockZ();

        // Nếu chuyển sang nhắm block khác -> reset tiến trình và bắt đầu lại từ 0
        if (hitX != miningTargetX || hitY != miningTargetY || hitZ != miningTargetZ) {
            miningTargetX = hitX;
            miningTargetY = hitY;
            miningTargetZ = hitZ;
            miningElapsedTime = 0.0f;
            nextMiningSoundTime = 0.0f;
        }

        // Tích lũy thời gian đào
        // === HOOK: TRACK C - MINING SPEED ===
        // Áp dụng hệ số tốc độ đào Shears trên len/lá tại đây.
        Item heldItem = player != null ? player.getHotbar().getSelectedItem() : Item.NONE;
        float speedMultiplier = LeafDecaySystem.miningSpeedMultiplier(heldItem, targetBlock);
        miningElapsedTime += deltaTime * speedMultiplier;
        // === END HOOK: TRACK C - MINING SPEED ===
        float requiredTime = targetBlock.getHardness();

        // Nếu đã đào đủ thời gian -> phá hủy block, sinh item rơi và reset tiến trình
        if (miningElapsedTime >= requiredTime) {
            if (particleManager != null) {
                particleManager.spawnBlockBreak(miningTargetX, miningTargetY, miningTargetZ, targetBlock, 20);
            }
            world.breakBlockAndSpawnDrop(miningTargetX, miningTargetY, miningTargetZ);
            resetMiningProgress();
            return 0.0f;
        }

        // Khối phá nhanh chỉ phát tiếng vỡ, khối cứng phát tiếng gõ cách nhau 0,25 giây.
        if (audioManager != null && requiredTime > 0.25f && miningElapsedTime >= nextMiningSoundTime) {
            audioManager.playMining(targetBlock);
            nextMiningSoundTime += 0.25f;
        }

        // Trả về tỷ lệ tiến trình đào (giới hạn trong [0.0, 1.0])
        return Math.min(1.0f, miningElapsedTime / requiredTime);
    }

    /**
     * Đặt lại trạng thái đào block về mặc định (không có mục tiêu, thời gian = 0).
     * Được gọi khi: nhả chuột trái, chuyển sang block khác, block không thể đào,
     * hoặc đào xong phá hủy block thành công.
     */
    private void resetMiningProgress() {
        miningTargetX = Integer.MIN_VALUE;
        miningTargetY = Integer.MIN_VALUE;
        miningTargetZ = Integer.MIN_VALUE;
        miningElapsedTime = 0.0f;
        nextMiningSoundTime = 0.0f;
    }

    /** Chọn thực thể từ mắt người chơi và giới hạn tia tại hộp va chạm của địa hình. */
    private Raycast.EntityHitResult aimAtEntity(float reach) {
        camera.copyPosition(eyePosition);
        camera.copyFront(aimDirection);
        Raycast.castOccluder(world, eyePosition, aimDirection, reach, occluderHit);
        return Raycast.castEntity(world, eyePosition, aimDirection, reach, occluderHit.getDistance(),
                player, entityHit);
    }

    /** Mỗi lần nhấn chọn đòn đánh (hỗ trợ Jab tầm 4.2f của IRON_SPEAR) hoặc bắt đầu giữ để đào. */
    private void handleLeftClick() {
        if (world == null || camera == null || player == null) {
            return;
        }
        // === HOOK: TRACK A - LEFT CLICK MELEE ===
        if (itemUseHandler != null) {
            boolean hitMob = itemUseHandler.onPrimaryMelee(world, player, camera);
            isLeftMouseDown = !hitMob;
            if (hitMob) {
                resetMiningProgress();
            }
            return;
        }
        // === END HOOK: TRACK A - LEFT CLICK MELEE ===
        Raycast.EntityHitResult hit = aimAtEntity(MELEE_REACH);
        isLeftMouseDown = !hit.isHit();
        if (hit.isHit()) {
            resetMiningProgress();
            player.attack(world, hit.getEntity(), aimDirection);
        }
    }

    /**
     * Xử lý sự kiện click chuột phải: Đặt block từ Hotbar vào thế giới.
     * - Lấy block đang chọn trong Hotbar
     * - Bắn tia Raycast tìm mặt phẳng đang nhìn
     * - Đặt block vào tọa độ trước mặt block bị tia chạm (prevBlock)
     * - Kiểm tra không trùng vị trí người chơi (AABB) trước khi đặt
     */
    private void handleRightClickPlaceBlock() {
        if (world == null || camera == null || player == null) {
            return;
        }

        // Tương tác thực thể được ưu tiên, kể cả khi ô Hotbar đang trống.
        Raycast.EntityHitResult target = aimAtEntity(RAYCAST_REACH);
        if (target.isHit() && target.getEntity().onPlayerInteract(world, player)) {
            return;
        }
        // === HOOK: TRACK A - RIGHT CLICK ITEM ===
        // Xử lý tiêu thụ thức ăn, thuốc, ném ngọc Ender, trứng, tù và, cung, giáo sắt tại đây.
        // === END HOOK: TRACK A - RIGHT CLICK ITEM ===

        // Lấy Hotbar của Player thông qua Composition
        Hotbar hotbar = player.getHotbar();
        Item selectedItem = hotbar.getSelectedItem();

        // Uống Milk Bucket: xóa mọi hiệu ứng trạng thái và trả lại Bucket rỗng
        if (selectedItem == Item.MILK_BUCKET && hotbar.getSelectedCount() > 0) {
            player.clearStatusEffects();
            hotbar.consumeSelectedItemStack();
            if (!hotbar.addItem(Item.BUCKET, 1)) {
                player.copyPosition(entityParticlePos);
                world.spawnDroppedItem(new ItemEntity(entityParticlePos.x,
                        entityParticlePos.y + 1.0f, entityParticlePos.z, Item.BUCKET));
            }
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.PICKUP, 0.35f);
            }
            return;
        }

        // Kiểm tra ô đang chọn có block khả dụng không
        Block selectedBlock = hotbar.getSelectedBlock();
        if (selectedBlock == Block.AIR || hotbar.getSelectedCount() <= 0) {
            return;
        }

        // Bắn tia từ mắt Camera theo hướng nhìn, tầm với 5.0 ô voxel
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

        // === HOOK: TRACK C - PLACE LEAF STATE ===
        // Lá do người chơi tự đặt mang metadata PERSISTENT_LEAF_STATE = 1 để không tự phân hủy.
        int placeState = LeafDecaySystem.isLeaf(selectedBlock) ? LeafDecaySystem.PERSISTENT_LEAF_STATE : 0;
        // Ưu tiên thay thế ô đang ngắm, sau đó thử ô sát mặt mà tia chạm.
        if (world.tryPlaceBlock(x, y, z, selectedBlock, placeState, player)
                || world.tryPlaceBlock(hit.getPrevBlockX(), hit.getPrevBlockY(), hit.getPrevBlockZ(),
                selectedBlock, placeState, player)) {
            finishBlockPlacement(hotbar, selectedBlock);
        }
        // === END HOOK: TRACK C - PLACE LEAF STATE ===
    }

    /** Trừ vật phẩm và phát mẫu của vật liệu sau khi World chấp nhận thao tác đặt. */
    private void finishBlockPlacement(Hotbar hotbar, Block placedBlock) {
        hotbar.consumeSelectedItem();
        if (audioManager != null) {
            audioManager.playPlace(placedBlock);
        }
    }

    // =====================================================================
    // CALLBACK RESIZE CỬA SỔ
    // =====================================================================

    /**
     * Xử lý khi cửa sổ bị thay đổi kích thước.
     * Cập nhật viewport OpenGL để khớp với kích thước mới.
     *
     * @param width  chiều rộng mới của cửa sổ (pixel)
     * @param height chiều cao mới của cửa sổ (pixel)
     */
    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            Gdx.gl.glViewport(0, 0, width, height);
        }
    }

    // =====================================================================
    // GIẢI PHÓNG TÀI NGUYÊN KHI ĐÓNG GAME
    // =====================================================================

    /**
     * Giải phóng toàn bộ tài nguyên GPU, bộ nhớ và luồng âm thanh khi đóng game.
     * Phương thức này được libGDX gọi tự động khi cửa sổ bị đóng.
     *
     * Thứ tự giải phóng: Âm thanh (dừng luồng) -> UI -> Thế giới (Chunk meshes + Entity meshes)
     * -> Texture Atlas (VRAM) -> Shader (GPU program).
     */
    @Override
    public void dispose() {
        // Giải phóng Mesh dùng chung cho các ItemEntity trước khi hủy World
        ItemEntity.cleanupSharedMeshes();

        // Giải phóng lần lượt từng hệ thống con
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
        // === HOOK: TRACK A - DISPOSE ===
        if (firstPersonRenderer != null) {
            firstPersonRenderer.dispose();
        }
        // === END HOOK: TRACK A - DISPOSE ===

        // === HOOK: TRACK B - DISPOSE ===
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
        // === END HOOK: TRACK B - DISPOSE ===

        // === HOOK: TRACK C - DISPOSE ===
        // Giải phóng InventoryScreenRenderer, DebugOverlayRenderer của Track C tại đây.
        if (inventoryScreen != null) {
            inventoryScreen.dispose();
            inventoryScreen = null;
        }
        if (debugOverlay != null) {
            debugOverlay.dispose();
            debugOverlay = null;
        }
        // === END HOOK: TRACK C - DISPOSE ===
    }

    // =====================================================================
    // TRIỂN KHAI GIAO DIỆN InputProcessor
    // Xử lý toàn bộ sự kiện bàn phím, chuột, con lăn từ libGDX
    // =====================================================================

    /**
     * Xử lý sự kiện phím bàn phím được nhấn xuống.
     * - Phím ESC: Tạm dừng game (lần 1) hoặc thoát hoàn toàn (lần 2 khi đang Pause)
     * - Phím 1-9: Chọn ô Hotbar tương ứng
     * - F1: Đổi mức tốc độ kiểm tra địa hình, kể cả khi đang tạm dừng
     * - Ctrl+C: Ghé tâm biome để kiểm tra địa hình
     *
     * @param keycode mã phím libGDX (hằng số {@link Input.Keys})
     * @return true nếu sự kiện đã được xử lý
     */
    @Override
    public boolean keyDown(int keycode) {
        // Backend chỉ gửi keyDown ở lần nhấn đầu, nên giữ F1 không làm đổi thêm mức.
        if (keycode == Input.Keys.F1 && player != null) {
            player.cycleTerrainSpeed();
            return true;
        }

        // Ghi nhận Ctrl cả khi Pause để Ctrl+C đọc đúng trạng thái tại lúc nhấn.
        if (keycode == Input.Keys.CONTROL_LEFT) {
            leftControlDown = true;
            return false;
        }
        if (keycode == Input.Keys.CONTROL_RIGHT) {
            rightControlDown = true;
            return false;
        }

        // Phím ESC: Nếu túi đồ đang mở thì đóng túi đồ trước, ngược lại tạm dừng / thoát game
        if (keycode == Input.Keys.ESCAPE) {
            if (isInventoryOpen()) {
                closeInventory();
                return true;
            }
            if (!isPaused) {
                pauseGame();
            } else {
                // ESC lần 2 khi đang Pause -> đóng cửa sổ, thoát hoàn toàn khỏi game
                Gdx.app.exit();
            }
            return true;
        }

        // Phím số 1..9: Chọn ô trên thanh 9 ô Hotbar (chỉ khi đang chơi, không Pause)
        if (!isPaused && player != null) {
            // Dùng trạng thái theo sự kiện vì Ctrl có thể đã được nhả khi hàng đợi được xử lý.
            if (keycode == Input.Keys.C && (leftControlDown || rightControlDown)) {
                BiomeType biome = BIOME_TOUR[nextBiomeIndex];
                nextBiomeIndex = (nextBiomeIndex + 1) % BIOME_TOUR.length;
                if (player.teleportToBiome(world, biome)) {
                    resetMiningProgress();
                }
                return true;
            }
            // Phím thử môi trường chỉ hoạt động khi đang chơi.
            switch (keycode) {
                case Input.Keys.F6 -> {
                    weather.toggle();
                    return true;
                }
                case Input.Keys.F7 -> {
                    worldTime.nextMilestone();
                    return true;
                }
                case Input.Keys.F8 -> {
                    worldTime.toggleFastForward();
                    return true;
                }
                default -> {
                    // Các phím còn lại tiếp tục được xử lý bởi Hotbar và Player.
                }
            }
            // === HOOK: TRACK A - KEYDOWN ===
            if (keycode == Input.Keys.F5 && !isInventoryOpen()) {
                player.cycleCameraMode();
                return true;
            }
            // === END HOOK: TRACK A - KEYDOWN ===

            // === HOOK: TRACK C - KEYDOWN ===
            // Xử lý phím E (Inventory), Q / Ctrl+Q (vứt đồ), F3 (Debug overlay) tại đây.
            if (keycode == Input.Keys.E) {
                toggleInventory();
                return true;
            }
            if (keycode == Input.Keys.Q && world != null && !isInventoryOpen()) {
                player.dropSelectedItem(world, leftControlDown || rightControlDown);
                return true;
            }
            if (keycode == Input.Keys.F3 && debugOverlay != null) {
                debugOverlay.toggle();
                return true;
            }
            // === END HOOK: TRACK C - KEYDOWN ===
            int slotIndex = getHotbarSlotFromKeycode(keycode);
            if (slotIndex >= 0 && !isInventoryOpen()) {
                int previousSlot = player.getHotbar().getSelectedSlot();
                player.getHotbar().selectSlot(slotIndex);
                if (itemUseHandler != null && player.getHotbar().getSelectedSlot() != previousSlot) {
                    itemUseHandler.onHotbarSlotChanged();
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Chuyển đổi mã phím libGDX sang chỉ số ô Hotbar (0-8).
     * Phím 1 -> ô 0, phím 2 -> ô 1, ..., phím 9 -> ô 8.
     *
     * @param keycode mã phím libGDX
     * @return chỉ số ô Hotbar (0-8), hoặc -1 nếu phím không phải 1-9
     */
    private int getHotbarSlotFromKeycode(int keycode) {
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            return keycode - Input.Keys.NUM_1;
        }
        return -1;
    }

    /**
     * Xử lý sự kiện phím bàn phím được nhả ra.
     * Cập nhật trạng thái Ctrl dùng cho phím tắt ghé tâm biome.
     *
     * @param keycode mã phím libGDX
     * @return false (không tiêu thụ sự kiện)
     */
    @Override
    public boolean keyUp(int keycode) {
        if (keycode == Input.Keys.CONTROL_LEFT) {
            leftControlDown = false;
        } else if (keycode == Input.Keys.CONTROL_RIGHT) {
            rightControlDown = false;
        }
        return false;
    }

    /**
     * Xử lý sự kiện nhập ký tự Unicode (dùng cho trường nhập liệu văn bản).
     * Game hiện tại không có trường nhập liệu nên không xử lý.
     *
     * @param character ký tự Unicode được nhập
     * @return false (không tiêu thụ sự kiện)
     */
    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    /**
     * Xử lý sự kiện nhấn chuột (Mouse Button Down).
     * - Khi đang Pause: Hit-Testing các nút trên Pause Menu
     * - Khi đang chơi: Chuột trái bắt đầu đào block, chuột phải đặt block
     *
     * @param screenX tọa độ X chuột trên màn hình (pixel, từ trái sang phải)
     * @param screenY tọa độ Y chuột trên màn hình (pixel, từ trên xuống dưới - hệ Top-Left)
     * @param pointer chỉ số con trỏ (0 cho chuột chính)
     * @param button  nút chuột ({@link Input.Buttons#LEFT}, {@link Input.Buttons#RIGHT})
     * @return true nếu sự kiện đã được xử lý
     */
    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        // Cập nhật tọa độ chuột hiện tại (dùng cho Hit-Testing)
        currentMouseX = screenX;
        currentMouseY = screenY;

        if (isPaused) {
            // Trạng thái Pause: chỉ xử lý click chuột trái trên Pause Menu
            if (button == Input.Buttons.LEFT) {
                handlePauseMenuMouseClick(screenX, screenY, true);
            }
            return true;
        }

        // Trạng thái chơi: xử lý đào và đặt block
        // === HOOK: TRACK C - TOUCH DOWN ===
        // Chuyển sự kiện click vào InventoryScreenRenderer khi túi đồ đang mở.
        if (isInventoryOpen() && player != null) {
            boolean shiftDown = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            inventoryScreen.handleClick(player.getInventory(), screenX, screenY,
                    Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), button, shiftDown);
            return true;
        }
        // === END HOOK: TRACK C - TOUCH DOWN ===
        if (button == Input.Buttons.LEFT) {
            // === HOOK: TRACK A - SWING ATTACK ===
            if (player != null) {
                player.triggerSwing();
            }
            // === END HOOK: TRACK A - SWING ATTACK ===
            handleLeftClick();
        } else if (button == Input.Buttons.RIGHT) {
            // === HOOK: TRACK A - RIGHT CLICK ITEM ===
            if (itemUseHandler != null && itemUseHandler.onSecondaryPress(world, player, camera)) {
                return true;
            }
            // === END HOOK: TRACK A - RIGHT CLICK ITEM ===
            handleRightClickPlaceBlock();
        }

        return true;
    }

    /**
     * Xử lý sự kiện nhả chuột (Mouse Button Up).
     * - Khi đang Pause: dừng kéo thanh trượt Volume
     * - Khi đang chơi: dừng đào block (nhả chuột trái)
     *
     * @param screenX tọa độ X chuột trên màn hình
     * @param screenY tọa độ Y chuột trên màn hình
     * @param pointer chỉ số con trỏ
     * @param button  nút chuột
     * @return true nếu sự kiện đã được xử lý
     */
    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        currentMouseX = screenX;
        currentMouseY = screenY;

        if (isPaused) {
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

        // Nhả chuột trái: dừng đào block và reset tiến trình
        if (button == Input.Buttons.LEFT) {
            isLeftMouseDown = false;
            resetMiningProgress();
        }
        // === HOOK: TRACK A - TOUCH UP ===
        if (button == Input.Buttons.RIGHT && itemUseHandler != null) {
            itemUseHandler.onSecondaryRelease(world, player, camera);
        }
        // === END HOOK: TRACK A - TOUCH UP ===

        return true;
    }

    /**
     * Xử lý sự kiện kéo chuột khi đang giữ nút (Mouse Drag / touchDragged).
     * Khi đang Pause và đang kéo thanh trượt Volume: cập nhật âm lượng theo vị trí chuột.
     *
     * @param screenX tọa độ X chuột trên màn hình
     * @param screenY tọa độ Y chuột trên màn hình
     * @param pointer chỉ số con trỏ
     * @return true nếu sự kiện đã được xử lý
     */
    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        currentMouseX = screenX;
        currentMouseY = screenY;

        // Khi đang Pause và đang kéo thanh trượt Volume: cập nhật âm lượng mượt mà theo vị trí chuột
        if (isPaused && isDraggingVolumeSlider && uiRenderer != null && audioManager != null) {
            int screenWidth = Gdx.graphics.getWidth();
            int screenHeight = Gdx.graphics.getHeight();
            float newVol = uiRenderer.getSliderVolumeAtCursor(screenX, screenY, screenWidth, screenHeight);
            if (newVol >= 0.0f) {
                audioManager.setVolume(newVol);
            }
        }

        return true;
    }

    /**
     * Xử lý sự kiện di chuyển chuột khi không giữ nút (Mouse Move / mouseMoved).
     * Cập nhật tọa độ chuột hiện tại để dùng cho Hit-Testing hover trên Pause Menu.
     * Việc xoay Camera được xử lý riêng trong {@link #processMouseLook()}.
     *
     * @param screenX tọa độ X chuột trên màn hình
     * @param screenY tọa độ Y chuột trên màn hình
     * @return true nếu sự kiện đã được xử lý
     */
    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        currentMouseX = screenX;
        currentMouseY = screenY;
        return true;
    }

    /**
     * Xử lý sự kiện cuộn con lăn chuột (Mouse Scroll Wheel).
     * Cuộn lên/xuống để chuyển đổi giữa 9 ô trên thanh Hotbar (xoay vòng modulo 9).
     *
     * @param amountX lượng cuộn theo trục X (thường = 0)
     * @param amountY lượng cuộn theo trục Y (âm = cuộn lên, dương = cuộn xuống)
     * @return true nếu sự kiện đã được xử lý
     */
    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (!isPaused && !isInventoryOpen() && player != null) {
            // amountY > 0 (cuộn xuống) = tăng chỉ số ô -> truyền dương
            // amountY < 0 (cuộn lên)   = giảm chỉ số ô -> truyền âm
            // Hàm hotbar.scroll() kỳ vọng: giá trị dương = cuộn lên (giảm slot),
            // giá trị âm = cuộn xuống (tăng slot) -> đảo dấu amountY
            int previousSlot = player.getHotbar().getSelectedSlot();
            player.getHotbar().scroll(-amountY);
            if (itemUseHandler != null && player.getHotbar().getSelectedSlot() != previousSlot) {
                itemUseHandler.onHotbarSlotChanged();
            }
        }
        return true;
    }

    /**
     * Xử lý sự kiện hủy bỏ thao tác chạm (touchCancelled).
     * Trên Desktop sự kiện này hiếm khi xảy ra. Xử lý tương tự nhả chuột.
     *
     * @param screenX tọa độ X
     * @param screenY tọa độ Y
     * @param pointer chỉ số con trỏ
     * @param button  nút chuột
     * @return true
     */
    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return touchUp(screenX, screenY, pointer, button);
    }
}
