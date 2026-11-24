package com.voxelgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.AudioManager;
import com.voxelgame.engine.BiomeType;
import com.voxelgame.engine.Camera;
import com.voxelgame.engine.MobRenderer;
import com.voxelgame.engine.MobTextureManager;
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
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.ItemEntity;
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
    private static final BiomeType[] BIOME_TOUR = {BiomeType.SNOWY_PLAINS, BiomeType.PLAINS, BiomeType.DESERT};
    private int nextBiomeIndex;

    /** Trạng thái Ctrl theo thứ tự sự kiện trong hàng đợi bàn phím của libGDX. */
    private boolean leftControlDown;
    private boolean rightControlDown;

    // =====================================================================
    // Các hệ thống cốt lõi của Engine
    // =====================================================================

    /** Chương trình Shader GLSL 330 Core dùng chung cho cả chế độ 3D (Thế giới) và 2D (UI) */
    private ShaderProgram shaderProgram;

    /** Texture Atlas 64x64 chứa toàn bộ 10 tile (Dirt, Grass, Stone, Bedrock, Log, Leaves, Water, Lava) */
    private Texture textureAtlas;
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

        // Texture Atlas 64x64: Nạp ảnh, tách sheet 32x32 Grass, nhuộm Water, sinh Procedural fallback
        textureAtlas = new Texture();

        // Camera góc nhìn thứ nhất: Quản lý Euler Yaw/Pitch, ma trận View/Projection, Dynamic FOV
        camera = new Camera();

        // Sinh địa hình và chuẩn bị mesh quanh tâm bản đồ.
        world = new World();
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
        // BƯỚC 2: Xử lý xoay Camera bằng di chuyển chuột (khi không Pause)
        // =================================================================
        if (!isPaused) {
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

            // Cập nhật tiến trình đào block nếu đang giữ chuột trái
            miningProgressRatio = updateMiningProgress(currentHit, deltaTime);

            // Thời gian môi trường dùng giây thực; vật lý vẫn giữ delta đã giới hạn.
            worldTime.update(realDelta);
            weather.update(realDelta, world, eyePosition, player);
            audioManager.setRainGain(weather.rainVolume());
        }

        // =================================================================
        // BƯỚC 4: Xóa bộ đệm màu và bộ đệm chiều sâu
        // =================================================================
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // =================================================================
        // BƯỚC 5: Kết xuất Thế giới 3D (Các Chunk địa hình + Các Entity đa hình)
        // =================================================================
        shaderProgram.bind();
        shaderProgram.enable3DWorldMode();

        // Lấy kích thước cửa sổ hiện tại (hỗ trợ resize)
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();

        // Dùng cùng ma trận cho Shader và kiểm tra khung nhìn, kể cả khi FOV thay đổi.
        Matrix4 projection = camera.getProjectionMatrix(screenWidth, screenHeight);
        Matrix4 view = camera.getViewMatrix();
        shaderProgram.setUniformMatrix4f("uProjection", projection);
        shaderProgram.setUniformMatrix4f("uView", view);
        camera.copyPosition(eyePosition);

        // Vòm trời và thiên thể nằm sau mọi hình học; mây và giáng thủy xét độ sâu.
        float stormStrength = weather.skyStrength();
        skyRenderer.renderSky(shaderProgram, view, worldTime, stormStrength);

        // Kết xuất các chunk giao khung nhìn và toàn bộ thực thể trong thế giới.
        textureAtlas.bind();
        float terrainDistance = skyRenderer.enableWorldFog(shaderProgram, eyePosition, stormStrength);
        world.render(shaderProgram, projection, view, eyePosition, renderCloudLayer, terrainDistance);
        shaderProgram.disableDistanceFog();
        weatherRenderer.render(shaderProgram, world, eyePosition, weather);
        textureAtlas.unbind();
        shaderProgram.setUniformVec3("uWorldLight", 1f, 1f, 1f);

        // =================================================================
        // BƯỚC 6: Vẽ hiệu ứng Overlay 3D quanh block đang ngắm/đào
        // =================================================================
        if (!isPaused && currentHit != null && currentHit.isHit()) {
            int x = currentHit.getBlockX();
            int y = currentHit.getBlockY();
            int z = currentHit.getBlockZ();
            Block current = Block.fromId(world.getBlock(x, y, z));
            if (current == currentHit.getBlock()) {
                uiRenderer.renderTargetBlockOverlay3D(shaderProgram, currentHit, miningProgressRatio,
                        current.getSelectionBounds(world.getBlockState(x, y, z)));
            }
        }

        // =================================================================
        // BƯỚC 7: Kết xuất Giao diện 2D (Crosshair, Hotbar, Thanh đào, Pause Menu)
        // =================================================================
        uiRenderer.renderCactusFlash(shaderProgram, camera, screenWidth, screenHeight, player.getCactusFlash());
        uiRenderer.render2DHUD(
                shaderProgram,
                textureAtlas,
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
        isPaused = true;
        isLeftMouseDown = false;
        isDraggingVolumeSlider = false;
        resetMiningProgress();
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
        miningElapsedTime += deltaTime;
        float requiredTime = targetBlock.getHardness();

        // Nếu đã đào đủ thời gian -> phá hủy block, sinh item rơi và reset tiến trình
        if (miningElapsedTime >= requiredTime) {
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

    /** Mỗi lần nhấn chọn đòn đánh hoặc bắt đầu giữ để đào. */
    private void handleLeftClick() {
        if (world == null || camera == null || player == null) {
            return;
        }
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

        // Lấy Hotbar của Player thông qua Composition
        Hotbar hotbar = player.getHotbar();

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

        // Ưu tiên thay thế ô đang ngắm, sau đó thử ô sát mặt mà tia chạm.
        if (world.tryPlaceBlock(x, y, z, selectedBlock, player)
                || world.tryPlaceBlock(hit.getPrevBlockX(), hit.getPrevBlockY(), hit.getPrevBlockZ(), selectedBlock, player)) {
            finishBlockPlacement(hotbar, selectedBlock);
        }
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
        if (textureAtlas != null) {
            textureAtlas.dispose();
        }
        if (shaderProgram != null) {
            shaderProgram.dispose();
        }
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

        // Phím ESC: Tạm dừng (lần 1) hoặc thoát game (lần 2 khi đang Pause)
        if (keycode == Input.Keys.ESCAPE) {
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
            int slotIndex = getHotbarSlotFromKeycode(keycode);
            if (slotIndex >= 0) {
                player.getHotbar().selectSlot(slotIndex);
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
        if (button == Input.Buttons.LEFT) {
            handleLeftClick();
        } else if (button == Input.Buttons.RIGHT) {
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

        // Nhả chuột trái: dừng đào block và reset tiến trình
        if (button == Input.Buttons.LEFT) {
            isLeftMouseDown = false;
            resetMiningProgress();
        }

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
        if (!isPaused && player != null) {
            // amountY > 0 (cuộn xuống) = tăng chỉ số ô -> truyền dương
            // amountY < 0 (cuộn lên)   = giảm chỉ số ô -> truyền âm
            // Hàm hotbar.scroll() kỳ vọng: giá trị dương = cuộn lên (giảm slot),
            // giá trị âm = cuộn xuống (tăng slot) -> đảo dấu amountY
            player.getHotbar().scroll(-amountY);
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
