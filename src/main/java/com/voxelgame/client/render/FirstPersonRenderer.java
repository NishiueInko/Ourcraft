package com.voxelgame.client.render;

import com.voxelgame.client.model.ModelPart;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.item.ItemUseHandler;
import com.voxelgame.world.level.block.Block;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * Hệ thống kết xuất mô hình tay & vật phẩm ở góc nhìn thứ nhất (Viewmodel)
 * và mô hình nhân vật {@code steve.png} ở góc nhìn thứ 3 ({@code F5}), triển khai {@link Disposable}.
 *
 * <p>Thiết kế tuân thủ zero-GC trong vòng lặp vẽ: toàn bộ {@link Mesh}, {@link Matrix4} và {@link Vector3}
 * được cấp phát một lần khi khởi tạo hoặc lưu trữ vào bộ đệm tĩnh tái sử dụng mọi khung hình,
 * đồng thời giải phóng toàn bộ tài nguyên GPU qua {@link #dispose()}.</p>
 */
public final class FirstPersonRenderer implements Disposable {

    /** Đường dẫn tài nguyên skin người chơi 64×64 và trang bị cầm tay 128×80. */
    private static final String STEVE_SKIN_PATH = "textures/entity/player/steve.png";
    private static final String HELD_EQUIPMENT_PATH = "textures/entity/equipment/held.png";

    /** Kích thước tấm ảnh chuẩn và tỉ lệ quy đổi pixel sang block cho nhân vật góc nhìn thứ 3. */
    private static final int STEVE_TEX_SIZE = 64;
    private static final int HELD_TEX_WIDTH = 128;
    private static final int HELD_TEX_HEIGHT = 80;
    private static final float PIXEL_TO_BLOCK = 1.0f / 16.0f;
    private static final float PLAYER_PIXEL_SCALE = 1.8f / 32.0f;

    /** Định dạng đỉnh 6 float dùng chung với {@link ShaderProgram}: aPos(3), aTexCoord(2), aShade(1). */
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade")
    );

    /** Texture GPU cho skin Steve và bộ trang bị cung/giáo. */
    private com.badlogic.gdx.graphics.Texture steveTexture;
    private com.badlogic.gdx.graphics.Texture heldTexture;

    /** Các lưới tĩnh dựng sẵn cho tay phải góc nhìn 1, 4 khung cung, giáo sắt và 6 bộ phận góc nhìn 3. */
    private Mesh armMesh;
    private final Mesh[] bowMeshes = new Mesh[4];
    private Mesh spearMesh;

    /** Cache lưới cho item 2D độc lập (theo {@code atlasTileIndex}) và block 3D thu nhỏ (theo block ID). */
    private final Mesh[] itemMeshes = new Mesh[64];
    private final Mesh[] blockMeshes = new Mesh[256];

    /** Sáu bộ phận hình học humanoid (đầu, thân, hai tay, hai chân) của người chơi góc nhìn thứ 3. */
    private Mesh thirdHeadMesh;
    private Mesh thirdBodyMesh;
    private Mesh thirdRightArmMesh;
    private Mesh thirdLeftArmMesh;
    private Mesh thirdRightLegMesh;
    private Mesh thirdLeftLegMesh;

    /** Ma trận và vector thành viên tái sử dụng mỗi khung hình (zero-GC). */
    private final Matrix4 identityMatrix = new Matrix4();
    private final Matrix4 viewmodelProjection = new Matrix4();
    private final Matrix4 modelMatrix = new Matrix4();
    private final Matrix4 rootMatrix = new Matrix4();
    private final Matrix4 partMatrix = new Matrix4();
    private final Matrix4 heldThirdMatrix = new Matrix4();
    private final Vector3 tmpPos = new Vector3();

    private float animationClock;
    private boolean glInitialized;
    private boolean disposed;

    /**
     * Khởi tạo bộ kết xuất góc nhìn thứ nhất và góc nhìn thứ ba.
     * Nếu gọi trong môi trường kiểm thử CPU không có ngữ cảnh OpenGL ({@code Gdx.gl == null}),
     * quá trình tạo tài nguyên GPU được bỏ qua an toàn.
     */
    public FirstPersonRenderer() {
        if (Gdx.gl != null && Gdx.files != null) {
            initGlResources();
        }
    }

    /** Nạp texture và dựng trước toàn bộ lưới tĩnh trên luồng có ngữ cảnh OpenGL. */
    private void initGlResources() {
        if (glInitialized || disposed) {
            return;
        }
        try {
            steveTexture = loadTextureSafe(STEVE_SKIN_PATH, 0x3C70B5);
            heldTexture = loadTextureSafe(HELD_EQUIPMENT_PATH, 0x8A7059);

            // Cánh tay phải ở góc nhìn thứ 1: dùng UV cánh tay phải của Steve (40, 16) kích thước 4×12×4
            armMesh = buildBoxMesh(0f, -6f, -2f, 4, 12, 4, 40, 16, false);

            // 4 khung kéo dây cung trên equipment/held.png (x = 0, 32, 64, 96; y = 64; 16×16)
            for (int i = 0; i < bowMeshes.length; i++) {
                bowMeshes[i] = buildDoubleSidedQuadMesh(
                        (i * 32.0f) / HELD_TEX_WIDTH,
                        64.0f / HELD_TEX_HEIGHT,
                        (i * 32.0f + 16.0f) / HELD_TEX_WIDTH,
                        (64.0f + 16.0f) / HELD_TEX_HEIGHT
                );
            }

            // Giáo sắt trên equipment/held.png (x = 0, y = 0, 16×16)
            spearMesh = buildDoubleSidedQuadMesh(
                    0.0f,
                    0.0f,
                    16.0f / HELD_TEX_WIDTH,
                    16.0f / HELD_TEX_HEIGHT
            );

            // 6 bộ phận nhân vật góc nhìn thứ 3 theo chuẩn skin 64×64
            thirdHeadMesh = buildBoxMesh(-4f, 0f, -4f, 8, 8, 8, 0, 0, false);
            thirdBodyMesh = buildBoxMesh(-4f, 0f, -2f, 8, 12, 4, 16, 16, false);
            thirdRightArmMesh = buildBoxMesh(-1f, -10f, -2f, 4, 12, 4, 40, 16, false);
            thirdLeftArmMesh = buildBoxMesh(-3f, -10f, -2f, 4, 12, 4, 32, 48, false);
            thirdRightLegMesh = buildBoxMesh(-2f, -12f, -2f, 4, 12, 4, 0, 16, false);
            thirdLeftLegMesh = buildBoxMesh(-2f, -12f, -2f, 4, 12, 4, 16, 48, false);

            glInitialized = true;
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    /**
     * Kết xuất tay phải hoặc vật phẩm đang cầm ở góc nhìn thứ nhất, tự động xóa Depth Buffer
     * để tay/vật phẩm không bao giờ cắt xuyên tường và khôi phục đầy đủ trạng thái OpenGL/Shader.
     *
     * @param shader         chương trình shader đang bind
     * @param player         người chơi hiện tại
     * @param useHandler     bộ quản lý trạng thái kéo cung/hạ giáo/ăn/uống
     * @param blockAtlas     texture atlas các khối 3D
     * @param itemAtlas      texture atlas vật phẩm 2D
     * @param viewportAspect tỉ lệ khung hình {@code width / height}
     */
    public void render(ShaderProgram shader, Player player, ItemUseHandler useHandler,
                       Texture blockAtlas, ItemTextureAtlas itemAtlas, float viewportAspect) {
        renderFirstPerson(shader, player, useHandler, blockAtlas, itemAtlas, null, null, viewportAspect);
    }

    /**
     * Kết xuất lớp phủ góc nhìn thứ nhất (First-Person Viewmodel) với đầy đủ hiệu ứng
     * View Bobbing, Swing, kéo cung 4 khung, hạ giáo Charge và nâng đồ ăn/thuốc,
     * sau đó khôi phục ma trận chiếu & ma trận nhìn thế giới cho các lượt vẽ kế tiếp.
     */
    public void renderFirstPerson(ShaderProgram shader, Player player, ItemUseHandler useHandler,
                                  Texture blockAtlas, ItemTextureAtlas itemAtlas,
                                  Matrix4 worldProjection, Matrix4 worldView, float viewportAspect) {
        if (disposed || shader == null || player == null || Gdx.gl == null) {
            return;
        }
        if (player.getCameraMode() != Player.CameraMode.FIRST_PERSON) {
            return;
        }
        if (!glInitialized) {
            initGlResources();
        }

        float dt = Gdx.graphics != null ? Math.min(Gdx.graphics.getDeltaTime(), 0.05f) : 0.0f;
        animationClock += dt;

        // Xóa bộ đệm chiều sâu để tay và vật phẩm góc nhìn 1 luôn nằm trước địa hình
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        try {
            shader.enableChunkColorMode(false);
            shader.setUniform1i("uEnvironmentMode", 0);
            shader.disableDistanceFog();
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            shader.setUniform1f("uMobWhiteFlash", 0.0f);
            shader.setUniformVec3("uWorldLight", 1.0f, 1.0f, 1.0f);

            float safeAspect = Math.max(0.1f, viewportAspect);
            viewmodelProjection.setToProjection(0.05f, 10.0f, 70.0f, safeAspect);
            shader.setUniformMatrix4f("uProjection", viewmodelProjection);
            shader.setUniformMatrix4f("uView", identityMatrix);

            Hotbar hotbar = player.getHotbar();
            Item held = (hotbar != null && hotbar.getSelectedCount() > 0) ? hotbar.getSelectedItem() : Item.NONE;

            float bobPhase = player.getBobPhase();
            float bobStrength = player.getBobStrength();
            float bobX = (float) Math.sin(bobPhase) * 0.025f * bobStrength;
            float bobY = -(float) Math.abs(Math.cos(bobPhase)) * 0.025f * bobStrength;

            float swing = player.getSwingProgress();
            float swingSin = (float) Math.sin(swing * Math.PI);
            float swingSqrt = (float) Math.sin(Math.sqrt(swing) * Math.PI);

            ItemUseHandler.UseState useState = useHandler != null ? useHandler.getUseState() : ItemUseHandler.UseState.NONE;
            float useProgress = useHandler != null ? useHandler.getUseProgress() : 0.0f;

            if (held == null || held.isNone()) {
                renderFirstPersonArm(shader, bobX, bobY, swingSin, swingSqrt);
            } else if (held.isBlock()) {
                renderFirstPersonBlock(shader, blockAtlas, held.getBlock(), bobX, bobY, swingSin, swingSqrt);
            } else if (held == Item.BOW) {
                float pull = (useState == ItemUseHandler.UseState.DRAW_BOW && useHandler != null)
                        ? useHandler.getBowPullProgress() : 0.0f;
                renderFirstPersonBow(shader, bobX, bobY, swingSin, swingSqrt, pull);
            } else if (held == Item.IRON_SPEAR) {
                float charge = (useState == ItemUseHandler.UseState.CHARGE_SPEAR) ? useProgress : 0.0f;
                renderFirstPersonSpear(shader, bobX, bobY, swingSin, swingSqrt, charge);
            } else {
                boolean consuming = useState == ItemUseHandler.UseState.EAT_FOOD
                        || useState == ItemUseHandler.UseState.DRINK_POTION;
                renderFirstPersonItem(shader, itemAtlas, held, bobX, bobY, swingSin, swingSqrt,
                        consuming ? useProgress : 0.0f, consuming);
            }
        } finally {
            shader.setUniformMatrix4f("uModel", identityMatrix);
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniform1f("uMobWhiteFlash", 0.0f);
            if (worldProjection != null) {
                shader.setUniformMatrix4f("uProjection", worldProjection);
            }
            if (worldView != null) {
                shader.setUniformMatrix4f("uView", worldView);
            }
            if (blockAtlas != null) {
                blockAtlas.bind();
            }
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    /**
     * Kết xuất mô hình nhân vật người chơi bằng skin {@code steve.png} ở góc nhìn thứ 3
     * ({@code THIRD_BACK} / {@code THIRD_FRONT}) ngay trong không gian thế giới hiện tại.
     */
    public void renderThirdPersonPlayer(ShaderProgram shader, Player player, ItemUseHandler useHandler,
                                        Texture blockAtlas, ItemTextureAtlas itemAtlas) {
        if (disposed || shader == null || player == null || Gdx.gl == null) {
            return;
        }
        if (player.getCameraMode() == Player.CameraMode.FIRST_PERSON) {
            return;
        }
        if (!glInitialized) {
            initGlResources();
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        try {
            shader.enableChunkColorMode(false);
            shader.setUniform1i("uEnvironmentMode", 0);
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniform1f("uMobWhiteFlash", 0.0f);
            if (player.getHurtFlashTimer() > 0.0f) {
                shader.setUniformVec4("uTintColor", 1.0f, 0.45f, 0.45f, 1.0f);
            } else {
                shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            }

            player.copyPosition(tmpPos);
            Camera cam = player.getAttachedCamera();
            float bodyYawDeg = cam != null ? -(cam.getYaw() + 90.0f) : 0.0f;
            float headPitchDeg = cam != null ? cam.getPitch() : 0.0f;
            boolean sneaking = player.isSneaking();
            float sneakDrop = sneaking ? -0.125f : 0.0f;
            float torsoLeanDeg = sneaking ? -24.0f : 0.0f;

            rootMatrix.idt()
                    .translate(tmpPos.x, tmpPos.y + sneakDrop, tmpPos.z)
                    .rotate(Vector3.Y, bodyYawDeg)
                    .scale(PLAYER_PIXEL_SCALE, PLAYER_PIXEL_SCALE, PLAYER_PIXEL_SCALE);

            float walkAngle = (float) Math.cos(player.getLimbSwing()) * player.getLimbSwingAmount() * 55.0f;
            float swingSin = (float) Math.sin(player.getSwingProgress() * Math.PI);

            ItemUseHandler.UseState useState = useHandler != null ? useHandler.getUseState() : ItemUseHandler.UseState.NONE;
            float useProgress = useHandler != null ? useHandler.getUseProgress() : 0.0f;

            steveTexture.bind(0);

            // 1. Hai chân (xoay tại hông Y = 12)
            partMatrix.set(rootMatrix).translate(2.0f, 12.0f, 0.0f).rotate(Vector3.X, -walkAngle);
            shader.setUniformMatrix4f("uModel", partMatrix);
            thirdRightLegMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);

            partMatrix.set(rootMatrix).translate(-2.0f, 12.0f, 0.0f).rotate(Vector3.X, walkAngle);
            shader.setUniformMatrix4f("uModel", partMatrix);
            thirdLeftLegMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);

            // 2. Thân (xoay tại hông Y = 12 khi cúi rón rén)
            partMatrix.set(rootMatrix).translate(0.0f, 12.0f, 0.0f).rotate(Vector3.X, torsoLeanDeg);
            shader.setUniformMatrix4f("uModel", partMatrix);
            thirdBodyMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);

            // 3. Đầu (xoay theo góc ngẩng/cúi của camera)
            float headY = sneaking ? 22.8f : 24.0f;
            float headZ = sneaking ? -4.2f : 0.0f;
            partMatrix.set(rootMatrix).translate(0.0f, headY, headZ).rotate(Vector3.X, headPitchDeg);
            shader.setUniformMatrix4f("uModel", partMatrix);
            thirdHeadMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);

            // 4. Tay trái
            float leftArmX = -walkAngle * 0.8f;
            float leftArmY = 0.0f;
            if (useState == ItemUseHandler.UseState.DRAW_BOW) {
                leftArmX = 85.0f + headPitchDeg;
                leftArmY = 24.0f;
            }
            partMatrix.set(rootMatrix).translate(-5.0f, 22.0f, headZ)
                    .rotate(Vector3.Y, leftArmY)
                    .rotate(Vector3.X, leftArmX)
                    .rotate(Vector3.Z, 3.0f);
            shader.setUniformMatrix4f("uModel", partMatrix);
            thirdLeftArmMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);

            // 5. Tay phải + vật phẩm cầm tay
            float rightArmX = walkAngle * 0.8f + swingSin * 85.0f;
            float rightArmY = 0.0f;
            if (useState == ItemUseHandler.UseState.DRAW_BOW) {
                rightArmX = 85.0f + headPitchDeg;
                rightArmY = -6.0f;
            } else if (useState == ItemUseHandler.UseState.CHARGE_SPEAR) {
                rightArmX = 75.0f + headPitchDeg;
            } else if (useState == ItemUseHandler.UseState.EAT_FOOD || useState == ItemUseHandler.UseState.DRINK_POTION) {
                rightArmX = 52.0f + (float) Math.sin(useProgress * Math.PI * 10.0f) * 10.0f;
                rightArmY = -20.0f;
            }
            partMatrix.set(rootMatrix).translate(5.0f, 22.0f, headZ)
                    .rotate(Vector3.Y, rightArmY)
                    .rotate(Vector3.X, rightArmX)
                    .rotate(Vector3.Z, -3.0f);
            shader.setUniformMatrix4f("uModel", partMatrix);
            thirdRightArmMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);

            renderThirdPersonHeldItem(shader, player, useHandler, blockAtlas, itemAtlas, partMatrix);
        } finally {
            shader.setUniformMatrix4f("uModel", identityMatrix);
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniform1f("uMobWhiteFlash", 0.0f);
            if (blockAtlas != null) {
                blockAtlas.bind();
            }
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    /** Vẽ tay trần góc nhìn thứ nhất từ skin {@code steve.png}. */
    private void renderFirstPersonArm(ShaderProgram shader, float bobX, float bobY,
                                      float swingSin, float swingSqrt) {
        modelMatrix.idt()
                .translate(0.56f + bobX, -0.56f + bobY, -0.76f)
                .translate(-0.34f * swingSqrt, 0.22f * (float) Math.sin(Math.asin(Math.min(1f, swingSin)) * 2.0), -0.32f * swingSin)
                .rotate(Vector3.Y, 42.0f - 24.0f * swingSqrt)
                .rotate(Vector3.X, -66.0f - 45.0f * swingSin)
                .rotate(Vector3.Z, 12.0f + 20.0f * swingSqrt)
                .scale(PIXEL_TO_BLOCK, PIXEL_TO_BLOCK, PIXEL_TO_BLOCK);
        steveTexture.bind(0);
        shader.setUniformMatrix4f("uModel", modelMatrix);
        armMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);
    }

    /** Vẽ khối 3D thu nhỏ trên tay phải góc nhìn thứ nhất. */
    private void renderFirstPersonBlock(ShaderProgram shader, Texture blockAtlas, Block block,
                                        float bobX, float bobY, float swingSin, float swingSqrt) {
        if (blockAtlas == null || block == null || block == Block.AIR) {
            return;
        }
        Mesh mesh = getOrCreateBlockMesh(block);
        modelMatrix.idt()
                .translate(0.56f + bobX, -0.52f + bobY, -0.82f)
                .translate(-0.32f * swingSqrt, 0.20f * swingSin, -0.28f * swingSin)
                .rotate(Vector3.Y, 45.0f - 30.0f * swingSqrt)
                .rotate(Vector3.X, -14.0f - 48.0f * swingSin)
                .rotate(Vector3.Z, 18.0f * swingSqrt)
                .scale(0.38f, 0.38f, 0.38f);
        blockAtlas.bind();
        shader.setUniformMatrix4f("uModel", modelMatrix);
        mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);
    }

    /** Vẽ 4 khung hình cung kéo dây từ {@code equipment/held.png}. */
    private void renderFirstPersonBow(ShaderProgram shader, float bobX, float bobY,
                                      float swingSin, float swingSqrt, float pull) {
        int frame = pull <= 0.0f ? 0 : (pull < 0.65f ? 1 : (pull < 0.90f ? 2 : 3));
        float jitter = pull >= 0.90f ? (float) Math.sin(animationClock * 50.0f) * 0.004f : 0.0f;
        modelMatrix.idt()
                .translate(0.50f + bobX - 0.14f * pull + jitter, -0.40f + bobY + 0.06f * pull + jitter, -0.76f + 0.12f * pull)
                .translate(-0.28f * swingSqrt, 0.16f * swingSin, -0.24f * swingSin)
                .rotate(Vector3.Y, -12.0f + 10.0f * pull)
                .rotate(Vector3.X, -8.0f - 40.0f * swingSin)
                .rotate(Vector3.Z, -16.0f + 8.0f * pull)
                .scale(0.55f, 0.55f, 0.55f);
        heldTexture.bind(0);
        shader.setUniformMatrix4f("uModel", modelMatrix);
        bowMeshes[frame].render(shader.getGdxShader(), GL20.GL_TRIANGLES);
    }

    /** Vẽ giáo sắt {@link Item#IRON_SPEAR} từ {@code equipment/held.png} với hoạt ảnh Jab & Charge. */
    private void renderFirstPersonSpear(ShaderProgram shader, float bobX, float bobY,
                                        float swingSin, float swingSqrt, float charge) {
        modelMatrix.idt()
                .translate(0.54f + bobX - 0.18f * charge, -0.42f + bobY + 0.14f * charge, -0.78f - 0.18f * charge)
                .translate(-0.26f * swingSqrt, 0.14f * swingSin, -0.40f * swingSin)
                .rotate(Vector3.Y, -14.0f - 8.0f * charge)
                .rotate(Vector3.X, -10.0f - 38.0f * charge - 46.0f * swingSin)
                .rotate(Vector3.Z, -18.0f + 16.0f * charge)
                .scale(0.62f, 0.62f, 0.62f);
        heldTexture.bind(0);
        shader.setUniformMatrix4f("uModel", modelMatrix);
        spearMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);
    }

    /** Vẽ vật phẩm 2D bất kỳ từ {@link ItemTextureAtlas} với hoạt ảnh nâng lên ăn/uống. */
    private void renderFirstPersonItem(ShaderProgram shader, ItemTextureAtlas itemAtlas, Item item,
                                       float bobX, float bobY, float swingSin, float swingSqrt,
                                       float useProgress, boolean consuming) {
        if (itemAtlas == null || item == null || item.isNone()) {
            return;
        }
        Mesh mesh = getOrCreateItemMesh(item);
        if (mesh == null) {
            return;
        }
        float lift = consuming ? Math.min(1.0f, useProgress * 4.5f) : 0.0f;
        float nibble = consuming ? (float) Math.abs(Math.sin(useProgress * Math.PI * 10.0f)) * 0.045f : 0.0f;
        modelMatrix.idt()
                .translate(0.52f + bobX - 0.28f * lift, -0.44f + bobY + 0.18f * lift + nibble, -0.76f + 0.12f * lift)
                .translate(-0.28f * swingSqrt, 0.16f * swingSin, -0.26f * swingSin)
                .rotate(Vector3.Y, -16.0f + 18.0f * lift - 20.0f * swingSqrt)
                .rotate(Vector3.X, -8.0f + 12.0f * lift - 45.0f * swingSin)
                .rotate(Vector3.Z, -8.0f + 22.0f * lift)
                .scale(0.46f, 0.46f, 0.46f);
        itemAtlas.bind();
        shader.setUniformMatrix4f("uModel", modelMatrix);
        mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);
    }

    /** Vẽ vật phẩm đang cầm trên tay phải của Steve ở góc nhìn thứ 3. */
    private void renderThirdPersonHeldItem(ShaderProgram shader, Player player, ItemUseHandler useHandler,
                                           Texture blockAtlas, ItemTextureAtlas itemAtlas, Matrix4 rightArmTransform) {
        Hotbar hotbar = player.getHotbar();
        if (hotbar == null || hotbar.getSelectedCount() <= 0) {
            return;
        }
        Item held = hotbar.getSelectedItem();
        if (held == null || held.isNone()) {
            return;
        }

        if (held.isBlock() && blockAtlas != null) {
            Mesh mesh = getOrCreateBlockMesh(held.getBlock());
            heldThirdMatrix.set(rightArmTransform)
                    .translate(1.0f, -10.0f, -3.5f)
                    .rotate(Vector3.X, 45.0f)
                    .scale(6.0f, 6.0f, 6.0f);
            blockAtlas.bind();
            shader.setUniformMatrix4f("uModel", heldThirdMatrix);
            mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);
            return;
        }

        if (held == Item.BOW) {
            float pull = useHandler != null ? useHandler.getBowPullProgress() : 0.0f;
            int frame = pull <= 0.0f ? 0 : (pull < 0.65f ? 1 : (pull < 0.90f ? 2 : 3));
            heldThirdMatrix.set(rightArmTransform)
                    .translate(1.0f, -10.0f, -3.0f)
                    .rotate(Vector3.Y, 90.0f)
                    .rotate(Vector3.Z, -40.0f)
                    .scale(12.0f, 12.0f, 12.0f);
            heldTexture.bind(0);
            shader.setUniformMatrix4f("uModel", heldThirdMatrix);
            bowMeshes[frame].render(shader.getGdxShader(), GL20.GL_TRIANGLES);
            return;
        }

        if (held == Item.IRON_SPEAR) {
            heldThirdMatrix.set(rightArmTransform)
                    .translate(1.0f, -10.0f, -3.0f)
                    .rotate(Vector3.Y, 90.0f)
                    .rotate(Vector3.X, 45.0f)
                    .scale(12.0f, 12.0f, 12.0f);
            heldTexture.bind(0);
            shader.setUniformMatrix4f("uModel", heldThirdMatrix);
            spearMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);
            return;
        }

        if (itemAtlas != null) {
            Mesh mesh = getOrCreateItemMesh(held);
            if (mesh != null) {
                heldThirdMatrix.set(rightArmTransform)
                        .translate(1.0f, -10.0f, -3.0f)
                        .rotate(Vector3.Y, 90.0f)
                        .rotate(Vector3.Z, -20.0f)
                        .scale(8.0f, 8.0f, 8.0f);
                itemAtlas.bind();
                shader.setUniformMatrix4f("uModel", heldThirdMatrix);
                mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);
            }
        }
    }

    /** Dựng lưới 3D cho một bộ phận hộp chuẩn Minecraft từ skin 64×64 của Steve. */
    private static Mesh buildBoxMesh(float offsetX, float offsetY, float offsetZ,
                                     int sizeX, int sizeY, int sizeZ,
                                     int texU, int texV, boolean mirrored) {
        ModelPart.UvOrientation orientation = mirrored
                ? ModelPart.UvOrientation.MIRRORED : ModelPart.UvOrientation.MINECRAFT;
        ModelPart part = new ModelPart(0f, 0f, 0f, offsetX, offsetY, offsetZ,
                sizeX, sizeY, sizeZ, 0f, new ModelPart.CuboidUv(texU, texV, sizeX, sizeY, sizeZ, orientation));
        float[] vertices = part.buildVertices(STEVE_TEX_SIZE, STEVE_TEX_SIZE);
        Mesh mesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true,
                vertices.length / ModelPart.STRIDE, 0, VERTEX_ATTRIBUTES);
        try {
            mesh.setVertices(vertices);
            return mesh;
        } catch (RuntimeException | Error failure) {
            mesh.dispose();
            throw failure;
        }
    }

    /** Dựng quad 2D có hai mặt trước/sau chuẩn CCW (hiển thị từ cả hai phía mà vẫn giữ {@code GL_CULL_FACE}). */
    private static Mesh buildDoubleSidedQuadMesh(float u0, float v0, float u1, float v1) {
        float[] vertices = new float[12 * 6];
        int idx = putQuadFace(vertices, 0,
                -0.5f, -0.5f, 0.0f, u0, v1,
                 0.5f, -0.5f, 0.0f, u1, v1,
                 0.5f,  0.5f, 0.0f, u1, v0,
                -0.5f,  0.5f, 0.0f, u0, v0, 0.96f);
        putQuadFace(vertices, idx,
                 0.5f, -0.5f, 0.0f, u0, v1,
                -0.5f, -0.5f, 0.0f, u1, v1,
                -0.5f,  0.5f, 0.0f, u1, v0,
                 0.5f,  0.5f, 0.0f, u0, v0, 0.86f);
        Mesh mesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true, 12, 0, VERTEX_ATTRIBUTES);
        try {
            mesh.setVertices(vertices);
            return mesh;
        } catch (RuntimeException | Error failure) {
            mesh.dispose();
            throw failure;
        }
    }

    /** Lấy hoặc dựng cache lưới 2 mặt cho vật phẩm độc lập từ {@link ItemTextureAtlas}. */
    private Mesh getOrCreateItemMesh(Item item) {
        int tileIndex = item.getAtlasTileIndex();
        if (tileIndex < 0 || tileIndex >= itemMeshes.length) {
            return null;
        }
        Mesh cached = itemMeshes[tileIndex];
        if (cached == null) {
            float[] uv = item.getTileUV();
            cached = buildDoubleSidedQuadMesh(uv[0], uv[1], uv[2], uv[3]);
            itemMeshes[tileIndex] = cached;
        }
        return cached;
    }

    /** Lấy hoặc dựng cache lưới cho khối 3D thu nhỏ (hộp lập phương hoặc cây chữ X). */
    private Mesh getOrCreateBlockMesh(Block block) {
        int id = Byte.toUnsignedInt(block.getId());
        Mesh cached = blockMeshes[id];
        if (cached != null) {
            return cached;
        }
        if (block.getGeometryType() == Block.GeometryType.CROSS_PLANT) {
            float[] uv = block.getFaceUV(Block.Face.NORTH);
            float[] vertices = new float[24 * 6];
            int idx = putQuadFace(vertices, 0,
                    -0.35f, -0.5f, -0.35f, uv[0], uv[3],
                     0.35f, -0.5f,  0.35f, uv[2], uv[3],
                     0.35f,  0.5f,  0.35f, uv[2], uv[1],
                    -0.35f,  0.5f, -0.35f, uv[0], uv[1], 1.0f);
            idx = putQuadFace(vertices, idx,
                     0.35f, -0.5f,  0.35f, uv[0], uv[3],
                    -0.35f, -0.5f, -0.35f, uv[2], uv[3],
                    -0.35f,  0.5f, -0.35f, uv[2], uv[1],
                     0.35f,  0.5f,  0.35f, uv[0], uv[1], 1.0f);
            idx = putQuadFace(vertices, idx,
                    -0.35f, -0.5f,  0.35f, uv[0], uv[3],
                     0.35f, -0.5f, -0.35f, uv[2], uv[3],
                     0.35f,  0.5f, -0.35f, uv[2], uv[1],
                    -0.35f,  0.5f,  0.35f, uv[0], uv[1], 1.0f);
            putQuadFace(vertices, idx,
                     0.35f, -0.5f, -0.35f, uv[0], uv[3],
                    -0.35f, -0.5f,  0.35f, uv[2], uv[3],
                    -0.35f,  0.5f,  0.35f, uv[2], uv[1],
                     0.35f,  0.5f, -0.35f, uv[0], uv[1], 1.0f);
            Mesh mesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true, 24, 0, VERTEX_ATTRIBUTES);
            try {
                mesh.setVertices(vertices);
                blockMeshes[id] = mesh;
                return mesh;
            } catch (RuntimeException | Error failure) {
                mesh.dispose();
                throw failure;
            }
        }

        float[] topUV = block.getFaceUV(Block.Face.TOP);
        float[] botUV = block.getFaceUV(Block.Face.BOTTOM);
        float[] sideUV = block.getFaceUV(Block.Face.NORTH);
        float[] vertices = new float[36 * 6];
        int idx = putQuadFace(vertices, 0,
                -0.5f,  0.5f,  0.5f, topUV[0], topUV[3],
                 0.5f,  0.5f,  0.5f, topUV[2], topUV[3],
                 0.5f,  0.5f, -0.5f, topUV[2], topUV[1],
                -0.5f,  0.5f, -0.5f, topUV[0], topUV[1], 1.0f);
        idx = putQuadFace(vertices, idx,
                -0.5f, -0.5f, -0.5f, botUV[0], botUV[3],
                 0.5f, -0.5f, -0.5f, botUV[2], botUV[3],
                 0.5f, -0.5f,  0.5f, botUV[2], botUV[1],
                -0.5f, -0.5f,  0.5f, botUV[0], botUV[1], 0.55f);
        idx = putQuadFace(vertices, idx,
                -0.5f, -0.5f,  0.5f, sideUV[0], sideUV[3],
                 0.5f, -0.5f,  0.5f, sideUV[2], sideUV[3],
                 0.5f,  0.5f,  0.5f, sideUV[2], sideUV[1],
                -0.5f,  0.5f,  0.5f, sideUV[0], sideUV[1], 0.85f);
        idx = putQuadFace(vertices, idx,
                 0.5f, -0.5f, -0.5f, sideUV[0], sideUV[3],
                -0.5f, -0.5f, -0.5f, sideUV[2], sideUV[3],
                -0.5f,  0.5f, -0.5f, sideUV[2], sideUV[1],
                 0.5f,  0.5f, -0.5f, sideUV[0], sideUV[1], 0.85f);
        idx = putQuadFace(vertices, idx,
                 0.5f, -0.5f,  0.5f, sideUV[0], sideUV[3],
                 0.5f, -0.5f, -0.5f, sideUV[2], sideUV[3],
                 0.5f,  0.5f, -0.5f, sideUV[2], sideUV[1],
                 0.5f,  0.5f,  0.5f, sideUV[0], sideUV[1], 0.72f);
        putQuadFace(vertices, idx,
                -0.5f, -0.5f, -0.5f, sideUV[0], sideUV[3],
                -0.5f, -0.5f,  0.5f, sideUV[2], sideUV[3],
                -0.5f,  0.5f,  0.5f, sideUV[2], sideUV[1],
                -0.5f,  0.5f, -0.5f, sideUV[0], sideUV[1], 0.72f);

        Block.Bounds shape = block.getSelectionBounds(0);
        if (shape != null && shape != Block.FULL_BOUNDS) {
            float sx = shape.maxX() - shape.minX();
            float sy = shape.maxY() - shape.minY();
            float sz = shape.maxZ() - shape.minZ();
            for (int i = 0; i < vertices.length; i += 6) {
                vertices[i] *= sx;
                vertices[i + 1] = (vertices[i + 1] + 0.5f) * sy - 0.5f;
                vertices[i + 2] *= sz;
            }
        }

        Mesh mesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true, 36, 0, VERTEX_ATTRIBUTES);
        try {
            mesh.setVertices(vertices);
            blockMeshes[id] = mesh;
            return mesh;
        } catch (RuntimeException | Error failure) {
            mesh.dispose();
            throw failure;
        }
    }

    /** Ghi 2 tam giác của một mặt tứ giác theo thứ tự quấn đỉnh CCW. */
    private static int putQuadFace(float[] out, int offset,
                                   float x1, float y1, float z1, float u1, float v1,
                                   float x2, float y2, float z2, float u2, float v2,
                                   float x3, float y3, float z3, float u3, float v3,
                                   float x4, float y4, float z4, float u4, float v4,
                                   float shade) {
        offset = putVertex(out, offset, x1, y1, z1, u1, v1, shade);
        offset = putVertex(out, offset, x2, y2, z2, u2, v2, shade);
        offset = putVertex(out, offset, x3, y3, z3, u3, v3, shade);
        offset = putVertex(out, offset, x1, y1, z1, u1, v1, shade);
        offset = putVertex(out, offset, x3, y3, z3, u3, v3, shade);
        return putVertex(out, offset, x4, y4, z4, u4, v4, shade);
    }

    /** Ghi 6 float thuộc tính của một đỉnh vào mảng đệm. */
    private static int putVertex(float[] out, int offset, float x, float y, float z,
                                 float u, float v, float shade) {
        out[offset++] = x;
        out[offset++] = y;
        out[offset++] = z;
        out[offset++] = u;
        out[offset++] = v;
        out[offset++] = shade;
        return offset;
    }

    /** Nạp PNG từ classpath với định dạng RGBA8888, dùng màu dự phòng an toàn nếu thiếu tệp. */
    private static com.badlogic.gdx.graphics.Texture loadTextureSafe(String path, int fallbackRgb) {
        com.badlogic.gdx.graphics.Texture texture = null;
        try {
            FileHandle file = Gdx.files.classpath(path);
            if (!file.exists()) {
                throw new IllegalArgumentException("Thiếu tệp PNG: " + path);
            }
            texture = new com.badlogic.gdx.graphics.Texture(file, Pixmap.Format.RGBA8888, false);
            texture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
                    com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest);
            texture.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge,
                    com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge);
            return texture;
        } catch (RuntimeException error) {
            if (texture != null) {
                texture.dispose();
            }
            Gdx.app.error("FirstPersonRenderer", "Dùng ảnh dự phòng cho " + path + ": " + error.getMessage());
            return createFallbackTexture(fallbackRgb);
        }
    }

    /** Tạo texture đơn sắc 16×16 dự phòng và luôn giải phóng {@link Pixmap} ngoài heap ngay sau tải. */
    private static com.badlogic.gdx.graphics.Texture createFallbackTexture(int rgb) {
        Pixmap image = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        com.badlogic.gdx.graphics.Texture texture = null;
        try {
            image.setColor((rgb << 8) | 0xFF);
            image.fill();
            texture = new com.badlogic.gdx.graphics.Texture(image, false);
            texture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
                    com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest);
            texture.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge,
                    com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge);
            return texture;
        } catch (RuntimeException error) {
            if (texture != null) {
                texture.dispose();
            }
            throw error;
        } finally {
            image.dispose();
        }
    }

    /** Giải phóng an toàn một {@link Disposable} nếu khác {@code null}. */
    private static void disposeMesh(Mesh mesh) {
        if (mesh != null) {
            mesh.dispose();
        }
    }

    /**
     * Giải phóng toàn bộ {@link Mesh} (VAO/VBO) và {@link com.badlogic.gdx.graphics.Texture} trên VRAM.
     * An toàn khi gọi nhiều lần hoặc gọi khi quá trình khởi tạo chưa hoàn tất.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        glInitialized = false;

        disposeMesh(armMesh);
        armMesh = null;

        for (int i = 0; i < bowMeshes.length; i++) {
            disposeMesh(bowMeshes[i]);
            bowMeshes[i] = null;
        }
        disposeMesh(spearMesh);
        spearMesh = null;

        disposeMesh(thirdHeadMesh);
        thirdHeadMesh = null;
        disposeMesh(thirdBodyMesh);
        thirdBodyMesh = null;
        disposeMesh(thirdRightArmMesh);
        thirdRightArmMesh = null;
        disposeMesh(thirdLeftArmMesh);
        thirdLeftArmMesh = null;
        disposeMesh(thirdRightLegMesh);
        thirdRightLegMesh = null;
        disposeMesh(thirdLeftLegMesh);
        thirdLeftLegMesh = null;

        for (int i = 0; i < itemMeshes.length; i++) {
            disposeMesh(itemMeshes[i]);
            itemMeshes[i] = null;
        }
        for (int i = 0; i < blockMeshes.length; i++) {
            disposeMesh(blockMeshes[i]);
            blockMeshes[i] = null;
        }

        if (steveTexture != null) {
            steveTexture.dispose();
            steveTexture = null;
        }
        if (heldTexture != null) {
            heldTexture.dispose();
            heldTexture = null;
        }
    }
}
