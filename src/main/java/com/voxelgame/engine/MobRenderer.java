package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lượt vẽ mô hình mob dùng chung mesh, ma trận khớp và cache vật liệu. */
public final class MobRenderer implements Disposable {
    /** Quy đổi đơn vị pixel của mô hình sang block thế giới. */
    private static final float PIXEL_TO_BLOCK = 1f / 16f;
    /** Cỡ bố cục gốc khi ảnh 16 × 16 dự phòng thay cho ảnh có UV chi tiết. */
    private static final int NOMINAL_WIDTH = 128;
    private static final int NOMINAL_HEIGHT = 128;
    /** Định dạng cần dùng chung với shader và mesh chunk. */
    private static final VertexAttributes FORMAT = new VertexAttributes(
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "aPos"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "aShade"));
    /** Thời lượng mỗi khung hoạt ảnh lửa (20 FPS = 0.05s) và thông số hình học chữ X ôm hitbox. */
    private static final double FIRE_FRAME_SECONDS = 0.05;
    private static final int FIRE_STRIDE = 6;
    private static final int FIRE_VERTEX_COUNT = 12;
    private static final float FIRE_SELF_SHADE = -1.0f;
    private static final float FIRE_WIDTH_SCALE = 1.15f;
    private static final float FIRE_HEIGHT_SCALE = 1.05f;
    private static final float FIRE_LAYER_TIME_OFFSET = 0.25f;

    /** Hai bộ phận chỉ cần chung mesh khi hình học, kích thước UV và kiểu sáng trùng nhau. */
    private record MeshKey(ModelPart.Geometry geometry, boolean eyes, int width, int height) {}
    /** Tổ hợp mesh tĩnh và texture đã nạp dành cho một lượt vẽ. */
    private record Material(Mesh mesh, MobTextureManager.Skin skin) {}
    /** Một nhánh cây có vật liệu mặc định và các lớp hoa văn đã dựng từ đầu. */
    private record PreparedPart(ModelPart part, Material material, Material[] markings, PreparedPart[] children) {}
    /** Mẫu cùng các nhánh đã chuẩn bị trong ngữ cảnh OpenGL hiện tại. */
    private record PreparedModel(MobModel model, PreparedPart[] roots) {}

    private final Texture blockAtlas;
    private final Map<MeshKey, Mesh> meshes = new HashMap<>();
    private final PreparedModel[] models;
    private final Matrix4[] transforms;
    private final Matrix4 rootTransform = new Matrix4();
    /** Ma trận của vật được giữ ở miệng cáo hoặc tay Enderman, dùng lại giữa các lần vẽ. */
    private final Matrix4 heldBlockTransform = new Matrix4();
    /** Ma trận và bộ đệm dùng lại khi vẽ ngọn lửa chữ X ôm sát hitbox mob (zero-GC). */
    private final Matrix4 fireTransform = new Matrix4();
    private final Vector3 savedWorldLight = new Vector3();
    private final float[] fireVertices = new float[FIRE_VERTEX_COUNT * FIRE_STRIDE];
    private final float[] fireUv0 = new float[4];
    private final float[] fireUv1 = new float[4];
    private final Mesh fireMesh;
    private final com.badlogic.gdx.graphics.Texture fireTexture0;
    private final com.badlogic.gdx.graphics.Texture fireTexture1;
    private final int fireFrameCount0;
    private final int fireFrameCount1;
    private int lastFireFrame0 = -1;
    private int lastFireFrame1 = -1;
    private double fireAnimationSeconds;
    private final Vector3 center = new Vector3();
    private MobTextureManager.Skin boundSkin;
    private int tintMode;
    private int appliedTint;
    private int woolColor;
    private boolean hideOverlay;
    private Block heldBlock;
    private ModelPart heldBlockPart;
    private MobAppearance currentAppearance;
    private boolean blendEnabled;
    private boolean cullDisabled;
    private int deepestPart;
    private boolean disposed;

    /** Nạp vật liệu và dựng toàn bộ mesh một lần trên luồng có ngữ cảnh OpenGL. */
    public MobRenderer(MobTextureManager textures, Texture blockAtlas) {
        this.blockAtlas = blockAtlas;
        MobAppearance[] appearances = MobAppearance.values();
        models = new PreparedModel[appearances.length];
        MobAppearance.HorseMarkings[] markingTypes = MobAppearance.HorseMarkings.values();
        MobTextureManager.Skin[] markingSkins = new MobTextureManager.Skin[markingTypes.length];
        try {
            MobTextureManager.Skin equipment = textures.load(MobTextureManager.HELD_EQUIPMENT);
            MobModel horse = MobAppearance.HORSE_BROWN.model();
            for (MobAppearance.HorseMarkings marking : markingTypes) {
                markingSkins[marking.ordinal()] = loadLayer(textures, marking.texture(), horse, ModelPart.Layer.OVERLAY);
            }
            for (MobAppearance appearance : appearances) {
                MobModel model = appearance.model();
                MobTextureManager.Skin base = loadLayer(textures, appearance.texture(), model, ModelPart.Layer.BASE);
                MobTextureManager.Skin overlay = loadLayer(textures, appearance.overlay(), model, ModelPart.Layer.OVERLAY);
                MobTextureManager.Skin eyes = loadLayer(textures, appearance.eyes(), model, ModelPart.Layer.EYES);
                MobTextureManager.Skin[] optionalMarkings = appearance.name().startsWith("HORSE_")
                        ? markingSkins : null;
                PreparedPart[] roots = prepareParts(model.roots(), base, overlay, eyes, equipment, optionalMarkings, 1);
                models[appearance.ordinal()] = new PreparedModel(model, roots);
            }
            transforms = new Matrix4[deepestPart];
            for (int i = 0; i < transforms.length; i++) {
                transforms[i] = new Matrix4();
            }
            Pixmap firePixmap0 = loadNormalizedFirePixmap("textures/fire_0.png", "fire_0.png");
            try {
                fireFrameCount0 = Math.max(1, firePixmap0.getHeight() / Math.max(1, firePixmap0.getWidth()));
                fireTexture0 = uploadFireTexture(firePixmap0);
            } finally {
                firePixmap0.dispose();
            }
            Pixmap firePixmap1 = loadNormalizedFirePixmap("textures/fire_1.png", "fire_1.png");
            try {
                fireFrameCount1 = Math.max(1, firePixmap1.getHeight() / Math.max(1, firePixmap1.getWidth()));
                fireTexture1 = uploadFireTexture(firePixmap1);
            } finally {
                firePixmap1.dispose();
            }
            if (Gdx.gl != null) {
                fireMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, false, FIRE_VERTEX_COUNT, 0, FORMAT);
                updateFireMeshVertices(0.0);
            } else {
                fireMesh = null;
            }
        } catch (RuntimeException | Error error) {
            dispose();
            throw error;
        }
    }

    /**
     * Tính tọa độ UV {@code [u0, v0, u1, v1]} của khung lửa ở thời điểm {@code timeSeconds},
     * cuộn đều qua {@code 0..frameCount-1} mỗi {@code 0.05s} (20 FPS), ghi trực tiếp vào {@code outUv} (zero-GC).
     *
     * @param timeSeconds thời gian hoạt ảnh tính bằng giây
     * @param frameCount  tổng số khung hình vuông theo chiều dọc của dải ảnh lửa
     * @param outUv       mảng độ dài >= 4 nhận {@code [u0, v0, u1, v1]} trong khoảng {@code [0, 1]}
     */
    public static void fireFrameUV(double timeSeconds, int frameCount, float[] outUv) {
        if (outUv == null || outUv.length < 4) {
            throw new IllegalArgumentException("Mảng outUv phải có ít nhất 4 phần tử");
        }
        int safeCount = Math.max(1, frameCount);
        double safeTime = Math.max(0.0, timeSeconds);
        long step = (long) Math.floor(safeTime / FIRE_FRAME_SECONDS + 1e-7);
        int frame = (int) (step % safeCount);
        outUv[0] = 0.0f;
        outUv[1] = (float) frame / safeCount;
        outUv[2] = 1.0f;
        outUv[3] = (float) (frame + 1) / safeCount;
    }

    /** Cập nhật đồng hồ hoạt ảnh lửa dùng chung với lượt vẽ thế giới (không bị tăng tốc bởi F8). */
    public void setFireAnimationSeconds(double seconds) {
        fireAnimationSeconds = Math.max(0.0, seconds);
    }

    /** Trả texture GPU dải lửa {@code fire_0.png} để lớp phủ góc nhìn 1 tái sử dụng. */
    public com.badlogic.gdx.graphics.Texture getFireTexture0() {
        return fireTexture0;
    }

    /** Trả texture GPU dải lửa {@code fire_1.png} để lớp phủ góc nhìn 1 tái sử dụng. */
    public com.badlogic.gdx.graphics.Texture getFireTexture1() {
        return fireTexture1;
    }

    /** Trả số khung của dải {@code fire_0.png}. */
    public int getFireFrameCount0() {
        return fireFrameCount0;
    }

    /** Trả số khung của dải {@code fire_1.png}. */
    public int getFireFrameCount1() {
        return fireFrameCount1;
    }

    /** Đọc ảnh dải lửa và chuẩn hóa toàn bộ pixel về {@code RGBA8888} qua {@link Texture#readNormalizedPixel}. */
    private static Pixmap loadNormalizedFirePixmap(String... names) {
        Pixmap raw = Texture.loadPixmapFlexible(names);
        if (raw == null) {
            Pixmap fallback = new Pixmap(Texture.TILE_SIZE, Texture.TILE_SIZE * 16, Pixmap.Format.RGBA8888);
            fallback.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < fallback.getHeight(); y++) {
                for (int x = 0; x < fallback.getWidth(); x++) {
                    int rgba = (y % Texture.TILE_SIZE > 5)
                            ? Texture.packRgba(255, 145, 24, 220)
                            : Texture.packRgba(255, 220, 90, 0);
                    fallback.drawPixel(x, y, rgba);
                }
            }
            return fallback;
        }
        try {
            int width = Math.max(1, raw.getWidth());
            int height = Math.max(1, raw.getHeight());
            Pixmap normalized = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            normalized.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    normalized.drawPixel(x, y, Texture.readNormalizedPixel(raw, x, y));
                }
            }
            return normalized;
        } finally {
            raw.dispose();
        }
    }

    /** Đưa {@link Pixmap} dải lửa đã chuẩn hóa lên GPU khi có ngữ cảnh OpenGL ({@code Gdx.gl != null}). */
    private static com.badlogic.gdx.graphics.Texture uploadFireTexture(Pixmap normalizedPixmap) {
        if (Gdx.gl == null) {
            return null;
        }
        com.badlogic.gdx.graphics.Texture texture = new com.badlogic.gdx.graphics.Texture(normalizedPixmap, false);
        try {
            texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            texture.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
            return texture;
        } catch (RuntimeException | Error error) {
            texture.dispose();
            throw error;
        }
    }

    /** Giữ hình nền dự phòng và ẩn lớp phụ lỗi để lớp màu đục không che nhân vật. */
    private static MobTextureManager.Skin loadLayer(MobTextureManager textures, MobTextureKey key,
                                                    MobModel model, ModelPart.Layer layer) {
        MobTextureManager.Skin skin = textures.load(key);
        if (skin == null) {
            return null;
        }
        if (!skin.fallback() && !model.fitsLayer(layer, skin.width(), skin.height())) {
            skin = textures.layoutFallback(key);
        }
        return skin.fallback() && layer != ModelPart.Layer.BASE ? null : skin;
    }

    /** Chuẩn bị cùng cấu trúc khớp cho một biến thể; tận dụng mesh đã dựng của biến thể trước. */
    private PreparedPart[] prepareParts(ModelPart[] parts, MobTextureManager.Skin base,
                                       MobTextureManager.Skin overlay, MobTextureManager.Skin eyes,
                                       MobTextureManager.Skin equipment, MobTextureManager.Skin[] markingSkins, int depth) {
        PreparedPart[] prepared = new PreparedPart[parts.length];
        if (parts.length > 0) {
            deepestPart = Math.max(deepestPart, depth);
        }
        for (int i = 0; i < parts.length; i++) {
            ModelPart part = parts[i];
            Material material = switch (part.layer()) {
                case BASE -> prepareMaterial(part, base);
                case OVERLAY -> prepareMaterial(part, overlay);
                case EYES -> prepareMaterial(part, eyes);
                case EQUIPMENT -> prepareMaterial(part, equipment);
            };
            Material[] markings = null;
            if (part.layer() == ModelPart.Layer.OVERLAY && markingSkins != null) {
                markings = new Material[markingSkins.length];
                for (int mark = 0; mark < markings.length; mark++) {
                    markings[mark] = prepareMaterial(part, markingSkins[mark]);
                }
            }
            PreparedPart[] children = prepareParts(part.children(), base, overlay, eyes, equipment, markingSkins, depth + 1);
            prepared[i] = new PreparedPart(part, material, markings, children);
        }
        return prepared;
    }

    /** Tạo hoặc dùng lại mesh của một bộ phận với kích thước UV của ảnh tương ứng. */
    private Material prepareMaterial(ModelPart part, MobTextureManager.Skin skin) {
        if (skin == null) {
            return null;
        }
        int width = skin.fallback() ? NOMINAL_WIDTH : skin.width();
        int height = skin.fallback() ? NOMINAL_HEIGHT : skin.height();
        MeshKey key = new MeshKey(part.geometry(), part.layer() == ModelPart.Layer.EYES, width, height);
        Mesh mesh = meshes.get(key);
        if (mesh == null) {
            float[] vertices = part.buildVertices(width, height);
            int vertexCount = vertices.length / ModelPart.STRIDE;
            mesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true, vertexCount, 0, FORMAT);
            try {
                mesh.setVertices(vertices);
                meshes.put(key, mesh);
            } catch (RuntimeException | Error error) {
                mesh.dispose();
                throw error;
            }
        }
        return new Material(mesh, skin);
    }

    /** Vẽ các thực thể còn trong khung nhìn, giữ ánh sáng, sương và trạng thái OpenGL sạch sẽ. */
    void render(List<Entity> entities, ShaderProgram shader, Frustum frustum, Vector3 eye, float drawDistance) {
        if (disposed) {
            return;
        }
        boundSkin = null;
        tintMode = -1;
        appliedTint = -1;
        blendEnabled = false;
        cullDisabled = false;
        try {
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                if (!(entity instanceof MobVisual visual)) {
                    continue;
                }
                MobVisualState state = visual.mobVisualState();
                currentAppearance = state.appearance();
                PreparedModel model = models[currentAppearance.ordinal()];
                float fuse = state.fuseProgress();
                float scale = state.scale() * currentAppearance.modelScale()
                        * PIXEL_TO_BLOCK * (1f + 0.25f * fuse * fuse);
                entity.copyCenterPosition(center);
                center.y -= entity.getHeight() * 0.5f;
                float radius = model.model.renderRadius() * scale * Math.max(1f, currentAppearance.heightScale());
                if (isInView(radius, frustum, eye, drawDistance)) {
                    drawMob(shader, entity, state, model, scale, fuse);
                }
            }
        } finally {
            setBlend(false);
            setDoubleSided(false);
            if (tintMode != -1) {
                shader.setUniformVec4("uTintColor", 1f, 1f, 1f, 1f);
                shader.setUniform1f("uMobWhiteFlash", 0f);
            }
            if (boundSkin != null) {
                blockAtlas.bind();
                boundSkin = null;
            }
        }
    }

    /** Bật hoặc tắt hòa trộn alpha cho lớp phủ đốm ngựa bán trong suốt. */
    private void setBlend(boolean enable) {
        if (blendEnabled == enable) {
            return;
        }
        blendEnabled = enable;
        if (enable) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    /** Tắt hoặc bật face culling cho mặt phẳng hai chiều và các bộ phận hở hai mặt. */
    private void setDoubleSided(boolean doubleSided) {
        if (cullDisabled == doubleSided) {
            return;
        }
        cullDisabled = doubleSided;
        if (doubleSided) {
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        } else {
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        }
    }

    /** Dùng cầu bao hình học quanh chân để giữ sừng, tai và vũ khí còn giao khung nhìn. */
    private boolean isInView(float radius, Frustum frustum, Vector3 eye, float drawDistance) {
        float limit = drawDistance + radius;
        return center.dst2(eye) <= limit * limit && frustum.sphereInFrustum(center, radius);
    }

    /** Tính biến đổi gốc và áp dụng tư thế của cá thể trước khi vẽ các phần. */
    private void drawMob(ShaderProgram shader, Entity entity, MobVisualState state,
                         PreparedModel model, float scale, float fuse) {
        model.model.resetPose();
        LivingEntity living = entity instanceof LivingEntity mob ? mob : null;
        if (living != null) {
            model.model.setupAnimation(living, state.limbSwing(), state.limbSwingAmount(), state.ageInTicks(),
                    state.headYaw(), state.headPitch());
        }

        rootTransform.setToTranslation(center);
        if (state.bodyYaw() != 0f) {
            rootTransform.rotate(Vector3.Y, state.bodyYaw());
        }
        if (state.bodyPitch() != 0f) {
            rootTransform.rotate(Vector3.X, state.bodyPitch());
        }
        if (living != null && !living.isAlive()) {
            float progress = MathUtils.clamp(1f - living.getDeathTimer(), 0f, 1f);
            rootTransform.rotate(Vector3.Z, progress * 90f);
        }
        rootTransform.scale(scale, scale * currentAppearance.heightScale(), scale);

        // Màu len chỉ áp dụng cho lớp phủ, còn dấu đỏ áp dụng cho cả cá thể.
        boolean hurt = living != null && living.getHurtFlashTimer() > 0f;
        boolean whiteFlash = fuse > 0f && ((int) (12f * fuse * fuse) & 1) == 1;
        int mode = hurt ? 1 : whiteFlash ? 2 : 0;
        if (tintMode != mode) {
            tintMode = mode;
            shader.setUniform1f("uMobWhiteFlash", mode == 2 ? 0.85f : 0f);
        }
        woolColor = currentAppearance == MobAppearance.SHEEP ? state.sheepWoolColor() : 0xffffff;
        hideOverlay = currentAppearance == MobAppearance.SHEEP && state.isSheepSheared();
        heldBlock = state.heldBlock();
        heldBlockPart = heldBlock == null ? null : model.model.heldBlockPart();
        MobAppearance.HorseMarkings markings = state.horseMarkings();
        int markingId = markings == null ? -1 : markings.ordinal();
        for (PreparedPart root : model.roots) {
            renderPart(root, rootTransform, 0, markingId, shader);
        }
        if (living != null && living.isAlive() && living.getFireTimer() > 0f) {
            drawFireOnMob(shader, entity);
        }
    }

    /**
     * Vẽ 2 mặt lửa chéo dạng chữ X ôm sát hộp va chạm của mob đang cháy,
     * tự phát sáng ({@code uWorldLight = (1, 1, 1)} và {@code aShade = -1}),
     * tắt face culling cục bộ rồi khôi phục toàn bộ trạng thái OpenGL và shader.
     */
    private void drawFireOnMob(ShaderProgram shader, Entity entity) {
        if (Gdx.gl == null || fireMesh == null || fireTexture0 == null || fireTexture1 == null) {
            return;
        }
        updateFireMeshVertices(fireAnimationSeconds);

        float width = entity.getHalfWidth() * 2.0f;
        float height = entity.getHeight();
        fireTransform.setToTranslation(center)
                .scale(width * FIRE_WIDTH_SCALE, height * FIRE_HEIGHT_SCALE, width * FIRE_WIDTH_SCALE);

        shader.copyWorldLight(savedWorldLight);
        setBlend(true);
        setDoubleSided(true);
        if (tintMode != 0) {
            tintMode = 0;
            shader.setUniform1f("uMobWhiteFlash", 0f);
        }
        applyTint(shader, 0xffffff);
        shader.setUniformVec3("uWorldLight", 1f, 1f, 1f);
        shader.setUniformMatrix4f("uModel", fireTransform);
        try {
            fireTexture0.bind(0);
            boundSkin = null;
            fireMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 0, 6);

            fireTexture1.bind(0);
            fireMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 6, 6);
        } finally {
            shader.setUniformVec3("uWorldLight", savedWorldLight);
            setDoubleSided(false);
            setBlend(false);
            blockAtlas.bind();
        }
    }

    /** Cập nhật tọa độ UV 2 mặt chữ X trong {@code [-0.5..0.5, 0..1, -0.5..0.5]} khi đổi khung hình. */
    private void updateFireMeshVertices(double timeSeconds) {
        double safeTime = Math.max(0.0, timeSeconds);
        int frame0 = (int) ((long) Math.floor(safeTime / FIRE_FRAME_SECONDS + 1e-7) % fireFrameCount0);
        int frame1 = (int) ((long) Math.floor((safeTime + FIRE_LAYER_TIME_OFFSET) / FIRE_FRAME_SECONDS + 1e-7)
                % fireFrameCount1);
        if (frame0 == lastFireFrame0 && frame1 == lastFireFrame1) {
            return;
        }
        lastFireFrame0 = frame0;
        lastFireFrame1 = frame1;
        fireFrameUV(safeTime, fireFrameCount0, fireUv0);
        fireFrameUV(safeTime + FIRE_LAYER_TIME_OFFSET, fireFrameCount1, fireUv1);

        int offset = 0;
        offset = writeFireQuad(fireVertices, offset,
                -0.5f, 0.0f, -0.5f,
                0.5f, 0.0f, 0.5f,
                0.5f, 1.0f, 0.5f,
                -0.5f, 1.0f, -0.5f,
                fireUv0);
        writeFireQuad(fireVertices, offset,
                0.5f, 0.0f, -0.5f,
                -0.5f, 0.0f, 0.5f,
                -0.5f, 1.0f, 0.5f,
                0.5f, 1.0f, -0.5f,
                fireUv1);
        if (fireMesh != null) {
            fireMesh.setVertices(fireVertices, 0, fireVertices.length);
        }
    }

    /** Ghi 1 mặt tứ giác lửa (6 đỉnh × 6 floats) vào mảng {@code out} với {@code aShade = -1.0f}. */
    private static int writeFireQuad(float[] out, int offset,
                                     float ax, float ay, float az,
                                     float bx, float by, float bz,
                                     float cx, float cy, float cz,
                                     float dx, float dy, float dz,
                                     float[] uv) {
        float u0 = uv[0];
        float v0 = uv[1];
        float u1 = uv[2];
        float v1 = uv[3];
        offset = writeFireVertex(out, offset, ax, ay, az, u0, v1);
        offset = writeFireVertex(out, offset, bx, by, bz, u1, v1);
        offset = writeFireVertex(out, offset, cx, cy, cz, u1, v0);
        offset = writeFireVertex(out, offset, ax, ay, az, u0, v1);
        offset = writeFireVertex(out, offset, cx, cy, cz, u1, v0);
        return writeFireVertex(out, offset, dx, dy, dz, u0, v0);
    }

    /** Ghi một đỉnh 6 số thực của ngọn lửa vào bộ đệm tái sử dụng. */
    private static int writeFireVertex(float[] out, int offset, float x, float y, float z, float u, float v) {
        out[offset] = x;
        out[offset + 1] = y;
        out[offset + 2] = z;
        out[offset + 3] = u;
        out[offset + 4] = v;
        out[offset + 5] = FIRE_SELF_SHADE;
        return offset + FIRE_STRIDE;
    }

    /** Đi sâu cây khớp bằng ngăn xếp ma trận cấp phát một lần lúc nạp. */
    private void renderPart(PreparedPart prepared, Matrix4 parent, int depth, int markingId, ShaderProgram shader) {
        ModelPart part = prepared.part;
        if (!part.isVisible()) {
            return;
        }
        Matrix4 transform = transforms[depth];
        part.writeTransform(parent, transform);
        Material material = prepared.markings != null && markingId >= 0
                ? prepared.markings[markingId] : prepared.material;
        if (hideOverlay && part.layer() == ModelPart.Layer.OVERLAY) {
            material = null;
        }
        if (material != null) {
            setBlend(part.layer() == ModelPart.Layer.OVERLAY);
            setDoubleSided(part.isDoubleSided());
            applyTint(shader, part.layer() == ModelPart.Layer.OVERLAY ? woolColor : 0xffffff);
            if (boundSkin != material.skin) {
                boundSkin = material.skin;
                boundSkin.bind();
            }
            shader.setUniformMatrix4f("uModel", transform);
            material.mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES);
        }
        if (part == heldBlockPart) {
            drawHeldBlock(shader, transform);
        }
        for (PreparedPart child : prepared.children) {
            renderPart(child, transform, depth + 1, markingId, shader);
        }
    }

    /** Tái sử dụng uniform khi hai phần có chung màu len và trạng thái sát thương. */
    private void applyTint(ShaderProgram shader, int rgb) {
        int encoded = rgb | tintMode << 24;
        if (appliedTint != encoded) {
            appliedTint = encoded;
            float greenBlueScale = (tintMode == 1 ? 0.45f : 1f) / 255f;
            shader.setUniformVec4("uTintColor", ((rgb >> 16) & 255) / 255f,
                    ((rgb >> 8) & 255) * greenBlueScale, (rgb & 255) * greenBlueScale, 1f);
        }
    }

    /** Vẽ khối thu nhỏ theo khớp đầu cáo hoặc giữa hai tay Enderman bằng atlas khối. */
    private void drawHeldBlock(ShaderProgram shader, Matrix4 jointTransform) {
        setBlend(false);
        setDoubleSided(false);
        applyTint(shader, 0xffffff);
        blockAtlas.bind();
        boundSkin = null;
        if (currentAppearance == MobAppearance.ENDERMAN) {
            heldBlockTransform.set(jointTransform).translate(0f, 16f, -10f).scale(8f, 8f, 8f);
        } else {
            heldBlockTransform.set(jointTransform).translate(0f, -2.8f, -7.2f).scale(2.9f, 2.9f, 2.9f);
        }
        ItemEntity.renderBlockMesh(shader, heldBlock, heldBlockTransform);
    }

    /** Giải phóng toàn bộ VAO/VBO và texture lửa, kể cả khi quá trình khởi tạo chỉ hoàn thành một phần. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Mesh mesh : meshes.values()) {
            mesh.dispose();
        }
        meshes.clear();
        if (fireMesh != null) {
            fireMesh.dispose();
        }
        if (fireTexture0 != null) {
            fireTexture0.dispose();
        }
        if (fireTexture1 != null) {
            fireTexture1.dispose();
        }
    }
}
