package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Trung tâm điều phối cửa sổ GLFW, vòng lặp Game Loop và kết nối toàn bộ kiến trúc OOP của dự án.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP TRONG WINDOW:
 * 1. Quan hệ Hợp thành (Composition - Player sở hữu Hotbar):
 *    - `Window` không còn giữ biến `Hotbar` rời rạc mà tương tác thông qua `player.getHotbar()`.
 * 2. Đa hình trên danh sách Thực thể (`world.updateEntities(deltaTime)`):
 *    - Trong `init()`, `player` được gắn điều khiển (`player.attachControls(windowHandle, camera)`) và
 *      đăng ký vào `world.registerPlayer(player)`.
 *    - Ở mỗi khung hình, `world.updateEntities(deltaTime)` cập nhật đa hình cho cả `Player` lẫn mọi `ItemEntity`.
 * 3. Đa hình giải phóng tài nguyên (`List<Cleanable>`):
 *    - Khi đóng game, `cleanup()` gom `audioManager`, `uiRenderer`, `world`, `textureAtlas`, `shaderProgram`
 *      vào `List<Cleanable>` và gọi đồng loạt `resource.cleanup()`.
 */
public class Window {

    private final String title;
    private int width;
    private int height;
    private long windowHandle;

    // Các hệ thống cốt lõi của Engine
    private ShaderProgram shaderProgram;
    private Texture textureAtlas;
    private AudioManager audioManager;
    private Camera camera;
    private World world;
    private Player player;
    private UIRenderer uiRenderer;

    // Quản lý tọa độ chuột để xoay Camera 3D hoặc tương tác trên Pause Menu 2D
    private double lastMouseX;
    private double lastMouseY;
    private double currentMouseX;
    private double currentMouseY;
    private boolean firstMouse = true;
    private static final float MOUSE_SENSITIVITY = 0.12f;

    // Trạng thái Tạm dừng (Pause Menu)
    private boolean isPaused = false;
    private boolean isDraggingVolumeSlider = false;

    // Trạng thái giữ chuột trái để đập Block theo độ cứng (Mining Hardness)
    private boolean isLeftMouseDown = false;
    private int miningTargetX = Integer.MIN_VALUE;
    private int miningTargetY = Integer.MIN_VALUE;
    private int miningTargetZ = Integer.MIN_VALUE;
    private float miningElapsedTime = 0.0f;

    // Màu bầu trời đồng bộ với sương mù (Distance Fog)
    private static final Vector3f SKY_COLOR = new Vector3f(0.53f, 0.81f, 0.92f);
    private static final float RAYCAST_REACH = 5.0f;

    public Window(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Không thể khởi tạo thư viện GLFW!");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        windowHandle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (windowHandle == NULL) {
            throw new RuntimeException("Không thể tạo cửa sổ GLFW!");
        }

        // 1. Callback khi thay đổi kích thước cửa sổ
        glfwSetFramebufferSizeCallback(windowHandle, (window, newWidth, newHeight) -> {
            if (newWidth > 0 && newHeight > 0) {
                this.width = newWidth;
                this.height = newHeight;
                glViewport(0, 0, newWidth, newHeight);
            }
        });

        // 2. Callback khi di chuyển chuột (Xoay Camera khi đang chơi, hoặc kéo thanh Volume khi đang Pause)
        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            currentMouseX = xpos;
            currentMouseY = ypos;

            if (isPaused) {
                if (isDraggingVolumeSlider && uiRenderer != null && audioManager != null) {
                    float newVol = uiRenderer.getSliderVolumeAtCursor(xpos, ypos, width, height);
                    if (newVol >= 0.0f) {
                        audioManager.setVolume(newVol);
                    }
                }
                return;
            }

            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }

            float xOffset = (float) (xpos - lastMouseX);
            float yOffset = (float) (lastMouseY - ypos);

            lastMouseX = xpos;
            lastMouseY = ypos;

            if (camera != null) {
                camera.rotate(xOffset * MOUSE_SENSITIVITY, yOffset * MOUSE_SENSITIVITY);
            }
        });

        // 3. Callback bắt sự kiện bàn phím: Phím ESC (1 lần: Pause, 2 lần: Quit) và phím số 1..9 chọn ô Hotbar
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS) {
                return;
            }

            if (key == GLFW_KEY_ESCAPE) {
                if (!isPaused) {
                    pauseGame();
                } else {
                    glfwSetWindowShouldClose(window, true);
                }
                return;
            }

            // Truy cập Hotbar thông qua đối tượng sở hữu Player (Composition)
            if (!isPaused && player != null && key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                player.getHotbar().selectSlot(key - GLFW_KEY_1);
            }
        });

        // 4. Callback bắt sự kiện cuộn con lăn chuột (Mouse Scroll Wheel) để chuyển đổi giữa 9 ô Hotbar
        glfwSetScrollCallback(windowHandle, (window, xoffset, yoffset) -> {
            if (!isPaused && player != null) {
                player.getHotbar().scroll(yoffset);
            }
        });

        // 5. Callback bắt sự kiện Click chuột (Tương tác với Pause Menu hoặc Đập / Đặt Block trong thế giới)
        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            if (isPaused) {
                handlePauseMenuMouseClick(button, action);
                return;
            }

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    isLeftMouseDown = true;
                } else if (action == GLFW_RELEASE) {
                    isLeftMouseDown = false;
                    resetMiningProgress();
                }
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                handleRightClickPlaceBlock();
            }
        });

        // 6. Căn giữa cửa sổ trên màn hình
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(windowHandle, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                        windowHandle,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                );
            }
        }

        glfwMakeContextCurrent(windowHandle);
        glfwSwapInterval(1); // Bật V-Sync (60 FPS mượt mà)
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwShowWindow(windowHandle);

        GL.createCapabilities();

        glClearColor(SKY_COLOR.x, SKY_COLOR.y, SKY_COLOR.z, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        // 7. Khởi tạo toàn bộ các thành phần của Game
        shaderProgram = new ShaderProgram();
        textureAtlas = new Texture();
        camera = new Camera();
        world = new World();
        uiRenderer = new UIRenderer();

        // Khởi tạo Player, gắn điều khiển GLFW/Camera và đăng ký vào danh sách Entity đa hình của World
        player = new Player(new Vector3f(32.0f, 16.0f, 32.0f));
        player.attachControls(windowHandle, camera);
        world.registerPlayer(player);

        // 8. Khởi tạo và phát lặp lại nhạc nền "1-08. Minecraft.mp3"
        audioManager = new AudioManager();
        audioManager.startBackgroundMusic();
    }

    private void pauseGame() {
        isPaused = true;
        isLeftMouseDown = false;
        isDraggingVolumeSlider = false;
        resetMiningProgress();
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    private void resumeGame() {
        isPaused = false;
        isDraggingVolumeSlider = false;
        firstMouse = true;
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    private void handlePauseMenuMouseClick(int button, int action) {
        if (button != GLFW_MOUSE_BUTTON_LEFT || uiRenderer == null) {
            return;
        }

        if (action == GLFW_RELEASE) {
            isDraggingVolumeSlider = false;
            return;
        }

        if (action == GLFW_PRESS) {
            if (uiRenderer.isInsideResumeButton(currentMouseX, currentMouseY, width, height)) {
                resumeGame();
                return;
            }

            if (uiRenderer.isInsideVolumeMinus(currentMouseX, currentMouseY, width, height)) {
                if (audioManager != null) {
                    audioManager.changeVolume(-0.10f);
                }
                return;
            }

            if (uiRenderer.isInsideVolumePlus(currentMouseX, currentMouseY, width, height)) {
                if (audioManager != null) {
                    audioManager.changeVolume(0.10f);
                }
                return;
            }

            float sliderVal = uiRenderer.getSliderVolumeAtCursor(currentMouseX, currentMouseY, width, height);
            if (sliderVal >= 0.0f && audioManager != null) {
                isDraggingVolumeSlider = true;
                audioManager.setVolume(sliderVal);
            }
        }
    }

    /**
     * Xử lý sự kiện Click chuột phải: Lấy Block từ `player.getHotbar()`, kiểm tra va chạm AABB và đặt vào thế giới.
     */
    private void handleRightClickPlaceBlock() {
        if (world == null || camera == null || player == null) {
            return;
        }

        Hotbar hotbar = player.getHotbar();
        Block selectedBlock = hotbar.getSelectedBlock();
        if (selectedBlock == Block.AIR || hotbar.getSelectedCount() <= 0) {
            return;
        }

        Raycast.HitResult hit = Raycast.cast(world, camera.getPosition(), camera.getFront(), RAYCAST_REACH);
        if (!hit.isHit()) {
            return;
        }

        int placeX = hit.getPrevBlockX();
        int placeY = hit.getPrevBlockY();
        int placeZ = hit.getPrevBlockZ();

        if (world.isInsideWorld(placeX, placeY, placeZ) && !player.intersectsBlock(placeX, placeY, placeZ)) {
            Block consumed = hotbar.consumeSelectedItem();
            if (consumed != Block.AIR) {
                world.setBlock(placeX, placeY, placeZ, consumed.getId());
            }
        }
    }

    /**
     * Cập nhật tiến trình giữ chuột trái để đập vỡ Block dựa trên Độ cứng (`hardness`).
     */
    private float updateMiningProgress(Raycast.HitResult currentHit, float deltaTime) {
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
        }

        miningElapsedTime += deltaTime;
        float requiredTime = targetBlock.getHardness();

        if (miningElapsedTime >= requiredTime) {
            world.breakBlockAndSpawnDrop(miningTargetX, miningTargetY, miningTargetZ);
            resetMiningProgress();
            return 0.0f;
        }

        return Math.min(1.0f, miningElapsedTime / requiredTime);
    }

    private void resetMiningProgress() {
        miningTargetX = Integer.MIN_VALUE;
        miningTargetY = Integer.MIN_VALUE;
        miningTargetZ = Integer.MIN_VALUE;
        miningElapsedTime = 0.0f;
    }

    /**
     * Vòng lặp chính của trò chơi (Main Game Loop).
     */
    private void loop() {
        double lastTime = glfwGetTime();

        while (!glfwWindowShouldClose(windowHandle)) {
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            if (deltaTime > 0.05f) {
                deltaTime = 0.05f;
            }

            Raycast.HitResult currentHit = null;
            float miningProgressRatio = 0.0f;

            // 1. Cập nhật đa hình toàn bộ Entity (Player + ItemEntity) và tiến trình đập Block khi không Pause
            if (!isPaused) {
                world.updateEntities(deltaTime);

                currentHit = Raycast.cast(world, camera.getPosition(), camera.getFront(), RAYCAST_REACH);
                miningProgressRatio = updateMiningProgress(currentHit, deltaTime);
            }

            // 2. Xóa bộ đệm màu và bộ đệm chiều sâu
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // 3. Kết xuất Thế giới 3D (Các Chunk địa hình + Các thực thể trong danh sách đa hình List<Entity>)
            shaderProgram.bind();
            shaderProgram.enable3DWorldMode();

            Matrix4f projectionMatrix = camera.getProjectionMatrix(width, height);
            Matrix4f viewMatrix = camera.getViewMatrix();

            shaderProgram.setUniformMatrix4f("uProjection", projectionMatrix);
            shaderProgram.setUniformMatrix4f("uView", viewMatrix);
            shaderProgram.setUniformVec3("uFogColor", SKY_COLOR);
            shaderProgram.setUniform1f("uFogStart", 28.0f);
            shaderProgram.setUniform1f("uFogEnd", 58.0f);

            textureAtlas.bind();
            world.render(shaderProgram);
            textureAtlas.unbind();

            // 4. Vẽ hiệu ứng khối 3D đang được ngắm / đang bị đập
            if (!isPaused && currentHit != null && currentHit.isHit()) {
                uiRenderer.renderTargetBlockOverlay3D(shaderProgram, currentHit, miningProgressRatio);
            }

            // 5. Kết xuất Giao diện 2D (9 ô Hotbar của Player, Tâm ngắm Crosshair, Thanh tiến trình đập & Pause Menu)
            uiRenderer.render2DHUD(
                    shaderProgram,
                    textureAtlas,
                    camera,
                    player.getHotbar(),
                    audioManager,
                    width,
                    height,
                    miningProgressRatio,
                    isPaused,
                    currentMouseX,
                    currentMouseY
            );

            shaderProgram.unbind();

            // 6. Hoán đổi bộ đệm khung hình và thăm dò sự kiện cửa sổ
            glfwSwapBuffers(windowHandle);
            glfwPollEvents();
        }
    }

    /**
     * Giải phóng đồng loạt toàn bộ tài nguyên hệ thống thông qua giao diện đa hình {@link Cleanable}.
     */
    private void cleanup() {
        List<Cleanable> resources = Arrays.asList(
                audioManager,
                uiRenderer,
                world,
                textureAtlas,
                shaderProgram
        );

        for (Cleanable resource : resources) {
            if (resource != null) {
                resource.cleanup();
            }
        }

        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();

        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }
}
