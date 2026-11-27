package com.voxelgame.client.render;

import com.voxelgame.world.level.block.Block;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import java.util.Arrays;
import java.util.Random;

/**
 * Hệ thống quản lý hạt 3D (Particles) với pool tĩnh {@link #MAX_PARTICLES} = 512 hạt,
 * lưu toàn bộ dữ liệu trong các mảng nguyên thủy cấp phát 1 lần để đảm bảo zero-GC trong Game Loop.
 * <p>
 * Hỗ trợ 4 loại hiệu ứng hạt: mảnh vỡ khi đào/phá block (cắt UV 4x4 từ tile của {@link Block}),
 * hạt lửa cháy tự phát sáng, hạt bắn nước và hạt thuốc nổ màu RGB.
 * Khi số hạt vượt ngưỡng 512, bộ đếm vòng sẽ tự động ghi đè hạt cũ nhất mà không tràn mảng.
 */
public class ParticleManager implements Disposable {

    /** Dung lượng tối đa cố định của pool hạt trong bộ nhớ. */
    public static final int MAX_PARTICLES = 512;

    /** Trọng lực cơ bản tác động lên các hạt (block/s^2). */
    private static final float PARTICLE_GRAVITY = -18.0f;

    /** Hệ số cản không khí ngang mỗi giây giúp hạt tản đều rồi rơi xuống. */
    private static final float HORIZONTAL_DRAG_PER_SEC = 1.5f;

    /** Định dạng 6 số thực mỗi đỉnh khớp với {@link ShaderProgram}: aPos(3), aTexCoord(2), aShade(1). */
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int VERTICES_PER_QUAD = 6;
    private static final int MAX_VERTICES = MAX_PARTICLES * VERTICES_PER_QUAD;

    /** Số hạt sinh ra mỗi lần vỡ lọ thuốc. */
    private static final int POTION_SPLASH_COUNT = 20;

    /** Bảng các mặt khối dùng lại để lấy tile ngẫu nhiên mà không cấp phát mảng. */
    private static final Block.Face[] BLOCK_FACES = Block.Face.values();

    // Toàn bộ dữ liệu 512 hạt lưu trong các mảng nguyên thủy cấp phát 1 lần (zero-GC).
    private final float[] px = new float[MAX_PARTICLES];
    private final float[] py = new float[MAX_PARTICLES];
    private final float[] pz = new float[MAX_PARTICLES];
    private final float[] vx = new float[MAX_PARTICLES];
    private final float[] vy = new float[MAX_PARTICLES];
    private final float[] vz = new float[MAX_PARTICLES];
    private final float[] life = new float[MAX_PARTICLES];
    private final float[] maxLife = new float[MAX_PARTICLES];
    private final float[] u0 = new float[MAX_PARTICLES];
    private final float[] v0 = new float[MAX_PARTICLES];
    private final float[] u1 = new float[MAX_PARTICLES];
    private final float[] v1 = new float[MAX_PARTICLES];
    private final float[] size = new float[MAX_PARTICLES];
    private final float[] pr = new float[MAX_PARTICLES];
    private final float[] pg = new float[MAX_PARTICLES];
    private final float[] pb = new float[MAX_PARTICLES];
    private final float[] gravityFactor = new float[MAX_PARTICLES];
    private final float[] shade = new float[MAX_PARTICLES];
    private final boolean[] textured = new boolean[MAX_PARTICLES];
    private final boolean[] active = new boolean[MAX_PARTICLES];

    /** Bộ đệm đỉnh thành viên và danh sách khe hạt không texture phục vụ kết xuất zero-GC. */
    private final float[] vertices = new float[MAX_VERTICES * FLOATS_PER_VERTEX];
    private final int[] untexturedSlots = new int[MAX_PARTICLES];
    private final Matrix4 modelMatrix = new Matrix4();
    private final Random random = new Random(20261127L);

    /** Mesh động cấp phát đúng 1 lần trên GPU (null khi chạy kiểm thử CPU không có OpenGL). */
    private final Mesh mesh;

    private int writeCursor;
    private int activeCount;
    private boolean disposed;

    /**
     * Khởi tạo bộ quản lý hạt với pool tĩnh 512 hạt.
     * Khi có ngữ cảnh OpenGL ({@code Gdx.gl != null}), cấp phát sẵn 1 {@link Mesh} động
     * dùng định dạng 6 floats/đỉnh tương thích {@code uUseVertexColor = 0}.
     */
    public ParticleManager() {
        if (Gdx.gl != null) {
            this.mesh = new Mesh(
                    Mesh.VertexDataType.VertexBufferObjectWithVAO,
                    false,
                    MAX_VERTICES,
                    0,
                    new VertexAttribute(VertexAttributes.Usage.Position, 3, "aPos"),
                    new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aTexCoord"),
                    new VertexAttribute(VertexAttributes.Usage.Generic, 1, "aShade")
            );
        } else {
            this.mesh = null;
        }
    }

    /**
     * Cấp phát một khe hạt mới trong pool 512 phần tử.
     * Ưu tiên tái sử dụng khe đã hết hạn; khi toàn bộ 512 khe đang hoạt động,
     * bộ đếm vòng sẽ ghi đè khe cũ nhất mà không bao giờ vượt biên mảng.
     */
    private int allocateSlot() {
        if (activeCount < MAX_PARTICLES) {
            for (int probe = 0; probe < MAX_PARTICLES; probe++) {
                int idx = (writeCursor + probe) % MAX_PARTICLES;
                if (!active[idx]) {
                    active[idx] = true;
                    activeCount++;
                    writeCursor = (idx + 1) % MAX_PARTICLES;
                    return idx;
                }
            }
        }
        int overwriteIdx = writeCursor;
        if (!active[overwriteIdx]) {
            active[overwriteIdx] = true;
            activeCount++;
        }
        writeCursor = (overwriteIdx + 1) % MAX_PARTICLES;
        return overwriteIdx;
    }

    /**
     * Sinh các hạt mảnh vỡ từ khối {@link Block} tại tọa độ nguyên {@code (x, y, z)}.
     * Mỗi hạt tự động cắt một mảnh UV ngẫu nhiên kích thước {@code 4x4} pixel trong ô tile của khối.
     *
     * @param x     tọa độ X nguyên của block
     * @param y     tọa độ Y nguyên của block
     * @param z     tọa độ Z nguyên của block
     * @param block loại block nguồn mảnh vỡ
     * @param count số lượng hạt cần sinh
     */
    public void spawnBlockBreak(int x, int y, int z, Block block, int count) {
        if (disposed || block == null || block == Block.AIR || count <= 0) {
            return;
        }
        float tileWidth = 1.0f / Texture.ATLAS_COLS;
        float tileHeight = 1.0f / Texture.ATLAS_ROWS;
        float eps = 0.0008f;

        for (int i = 0; i < count; i++) {
            Block.Face face = BLOCK_FACES[random.nextInt(BLOCK_FACES.length)];
            int tileIndex = block.getTileIndex(face);
            int col = tileIndex % Texture.ATLAS_COLS;
            int row = tileIndex / Texture.ATLAS_COLS;

            boolean cactusCap = tileIndex == Texture.TILE_CACTUS_TOP || tileIndex == Texture.TILE_CACTUS_BOTTOM;
            float insetU = cactusCap || tileIndex == Texture.TILE_CACTUS_SIDE ? 1.0f / Texture.ATLAS_WIDTH : 0.0f;
            float insetV = cactusCap ? 1.0f / Texture.ATLAS_HEIGHT : 0.0f;

            float baseU0 = col * tileWidth + insetU + eps;
            float baseV0 = row * tileHeight + insetV + eps;
            float baseU1 = (col + 1) * tileWidth - insetU - eps;
            float baseV1 = (row + 1) * tileHeight - insetV - eps;

            // Cắt ngẫu nhiên một ô con 4x4 pixel trong lưới 16x16 của tile.
            float ox = random.nextInt(13) / (float) Texture.TILE_SIZE;
            float oy = random.nextInt(13) / (float) Texture.TILE_SIZE;
            float patchSpan = 4.0f / Texture.TILE_SIZE;
            float spanU = baseU1 - baseU0;
            float spanV = baseV1 - baseV0;

            int slot = allocateSlot();
            px[slot] = x + 0.15f + random.nextFloat() * 0.70f;
            py[slot] = y + 0.15f + random.nextFloat() * 0.70f;
            pz[slot] = z + 0.15f + random.nextFloat() * 0.70f;
            vx[slot] = (random.nextFloat() - 0.5f) * 3.2f;
            vy[slot] = 1.2f + random.nextFloat() * 2.8f;
            vz[slot] = (random.nextFloat() - 0.5f) * 3.2f;
            float duration = 0.45f + random.nextFloat() * 0.45f;
            life[slot] = duration;
            maxLife[slot] = duration;
            u0[slot] = baseU0 + ox * spanU;
            v0[slot] = baseV0 + oy * spanV;
            u1[slot] = baseU0 + (ox + patchSpan) * spanU;
            v1[slot] = baseV0 + (oy + patchSpan) * spanV;
            size[slot] = 0.10f + random.nextFloat() * 0.08f;
            pr[slot] = 1.0f;
            pg[slot] = 1.0f;
            pb[slot] = 1.0f;
            gravityFactor[slot] = 1.0f;
            shade[slot] = 1.0f;
            textured[slot] = true;
        }
    }

    /**
     * Sinh một hạt lửa tự phát sáng bay lơ lửng lên trên tại vị trí thực thể đang cháy.
     *
     * @param x tọa độ X thế giới
     * @param y tọa độ Y thế giới
     * @param z tọa độ Z thế giới
     */
    public void spawnFlame(float x, float y, float z) {
        if (disposed) {
            return;
        }
        int slot = allocateSlot();
        px[slot] = x + (random.nextFloat() - 0.5f) * 0.35f;
        py[slot] = y + (random.nextFloat() - 0.5f) * 0.35f;
        pz[slot] = z + (random.nextFloat() - 0.5f) * 0.35f;
        vx[slot] = (random.nextFloat() - 0.5f) * 0.45f;
        vy[slot] = 0.65f + random.nextFloat() * 0.85f;
        vz[slot] = (random.nextFloat() - 0.5f) * 0.45f;
        float duration = 0.35f + random.nextFloat() * 0.35f;
        life[slot] = duration;
        maxLife[slot] = duration;
        u0[slot] = 0.0f;
        v0[slot] = 0.0f;
        u1[slot] = 1.0f;
        v1[slot] = 1.0f;
        size[slot] = 0.12f + random.nextFloat() * 0.08f;
        pr[slot] = 1.0f;
        pg[slot] = 0.45f + random.nextFloat() * 0.40f;
        pb[slot] = 0.08f + random.nextFloat() * 0.12f;
        gravityFactor[slot] = -0.08f;
        shade[slot] = -1.0f;
        textured[slot] = false;
    }

    /**
     * Sinh các hạt giọt nước bắn tung tóe tại vị trí va chạm mặt nước.
     *
     * @param x     tọa độ X thế giới
     * @param y     tọa độ Y thế giới
     * @param z     tọa độ Z thế giới
     * @param count số lượng hạt cần sinh
     */
    public void spawnSplash(float x, float y, float z, int count) {
        if (disposed || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int slot = allocateSlot();
            px[slot] = x + (random.nextFloat() - 0.5f) * 0.45f;
            py[slot] = y + random.nextFloat() * 0.20f;
            pz[slot] = z + (random.nextFloat() - 0.5f) * 0.45f;
            vx[slot] = (random.nextFloat() - 0.5f) * 2.8f;
            vy[slot] = 1.4f + random.nextFloat() * 2.4f;
            vz[slot] = (random.nextFloat() - 0.5f) * 2.8f;
            float duration = 0.35f + random.nextFloat() * 0.35f;
            life[slot] = duration;
            maxLife[slot] = duration;
            u0[slot] = 0.0f;
            v0[slot] = 0.0f;
            u1[slot] = 1.0f;
            v1[slot] = 1.0f;
            size[slot] = 0.09f + random.nextFloat() * 0.07f;
            pr[slot] = 0.35f + random.nextFloat() * 0.25f;
            pg[slot] = 0.65f + random.nextFloat() * 0.25f;
            pb[slot] = 0.95f + random.nextFloat() * 0.05f;
            gravityFactor[slot] = 0.85f;
            shade[slot] = 1.0f;
            textured[slot] = false;
        }
    }

    /**
     * Sinh chùm hạt hiệu ứng thuốc nổ theo mã màu RGB {@code 0xRRGGBB}.
     *
     * @param x   tọa độ X thế giới
     * @param y   tọa độ Y thế giới
     * @param z   tọa độ Z thế giới
     * @param rgb mã màu RGB đóng gói 24-bit
     */
    public void spawnPotionSplash(float x, float y, float z, int rgb) {
        if (disposed) {
            return;
        }
        float baseR = ((rgb >>> 16) & 0xFF) / 255.0f;
        float baseG = ((rgb >>> 8) & 0xFF) / 255.0f;
        float baseB = (rgb & 0xFF) / 255.0f;

        for (int i = 0; i < POTION_SPLASH_COUNT; i++) {
            float tone = 0.85f + random.nextFloat() * 0.15f;
            int slot = allocateSlot();
            px[slot] = x + (random.nextFloat() - 0.5f) * 0.60f;
            py[slot] = y + random.nextFloat() * 0.35f;
            pz[slot] = z + (random.nextFloat() - 0.5f) * 0.60f;
            vx[slot] = (random.nextFloat() - 0.5f) * 4.0f;
            vy[slot] = 0.8f + random.nextFloat() * 3.0f;
            vz[slot] = (random.nextFloat() - 0.5f) * 4.0f;
            float duration = 0.60f + random.nextFloat() * 0.50f;
            life[slot] = duration;
            maxLife[slot] = duration;
            u0[slot] = 0.0f;
            v0[slot] = 0.0f;
            u1[slot] = 1.0f;
            v1[slot] = 1.0f;
            size[slot] = 0.11f + random.nextFloat() * 0.09f;
            pr[slot] = Math.min(1.0f, baseR * tone);
            pg[slot] = Math.min(1.0f, baseG * tone);
            pb[slot] = Math.min(1.0f, baseB * tone);
            gravityFactor[slot] = 0.35f;
            shade[slot] = -1.0f;
            textured[slot] = false;
        }
    }

    /**
     * Cập nhật vị trí, vận tốc và tuổi thọ còn lại của toàn bộ hạt đang hoạt động theo cơ chế zero-GC.
     *
     * @param dt khoảng thời gian từ khung hình trước (giây)
     */
    public void update(float dt) {
        if (disposed || dt <= 0.0f || activeCount == 0) {
            return;
        }
        float drag = Math.max(0.0f, 1.0f - Math.min(0.5f, HORIZONTAL_DRAG_PER_SEC * dt));
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (!active[i]) {
                continue;
            }
            life[i] -= dt;
            if (life[i] <= 0.0f) {
                active[i] = false;
                activeCount--;
                continue;
            }
            vy[i] += PARTICLE_GRAVITY * gravityFactor[i] * dt;
            vx[i] *= drag;
            vz[i] *= drag;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
        }
    }

    /**
     * Dựng các quad billboard luôn xoay hướng về phía {@link Camera} từ ma trận View
     * và vẽ lên màn hình bằng {@link Mesh} động cấp phát 1 lần với {@code uUseVertexColor = 0}.
     *
     * @param shader     shader 3D đang bind sẵn {@code uProjection} và {@code uView}
     * @param camera     camera góc nhìn hiện tại dùng để lấy trục phải/lên cho billboard
     * @param blockAtlas texture atlas khối dùng cho các hạt mảnh vỡ block
     */
    public void render(ShaderProgram shader, Camera camera, Texture blockAtlas) {
        if (disposed || activeCount == 0 || shader == null || camera == null || Gdx.gl == null || mesh == null) {
            return;
        }

        // Trích trực tiếp vector Right (hàng 0) và Up (hàng 1) của camera từ ma trận View (zero-GC).
        float[] viewVal = camera.getViewMatrix().val;
        float rx = viewVal[Matrix4.M00];
        float ry = viewVal[Matrix4.M01];
        float rz = viewVal[Matrix4.M02];
        float ux = viewVal[Matrix4.M10];
        float uy = viewVal[Matrix4.M11];
        float uz = viewVal[Matrix4.M12];

        int floatOffset = 0;
        int texturedQuadCount = 0;
        int untexturedQuadCount = 0;

        // Lượt 1: gom toàn bộ hạt mảnh vỡ có texture để vẽ thành một lô duy nhất.
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (!active[i] || !textured[i]) {
                continue;
            }
            floatOffset = writeBillboardQuad(i, floatOffset, rx, ry, rz, ux, uy, uz);
            texturedQuadCount++;
        }

        // Lượt 2: gom các hạt màu thuần (lửa, bắn nước, thuốc) ngay phía sau trong cùng bộ đệm đỉnh.
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (!active[i] || textured[i]) {
                continue;
            }
            floatOffset = writeBillboardQuad(i, floatOffset, rx, ry, rz, ux, uy, uz);
            untexturedSlots[untexturedQuadCount++] = i;
        }

        int totalQuads = texturedQuadCount + untexturedQuadCount;
        if (totalQuads == 0) {
            return;
        }

        mesh.setVertices(vertices, 0, totalQuads * VERTICES_PER_QUAD * FLOATS_PER_VERTEX);

        modelMatrix.idt();
        shader.setUniformMatrix4f("uModel", modelMatrix);
        shader.setUniform1i("uEnvironmentMode", 0);
        shader.enableChunkColorMode(false);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        try {
            if (texturedQuadCount > 0 && blockAtlas != null) {
                blockAtlas.bind();
                shader.setUniform1i("uUseTexture", 1);
                shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
                mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 0, texturedQuadCount * VERTICES_PER_QUAD);
            }
            if (untexturedQuadCount > 0) {
                shader.setUniform1i("uUseTexture", 0);
                for (int k = 0; k < untexturedQuadCount; k++) {
                    int slot = untexturedSlots[k];
                    float lifeRatio = maxLife[slot] > 0.0f ? life[slot] / maxLife[slot] : 0.0f;
                    float alpha = Math.max(0.0f, Math.min(1.0f, lifeRatio * 2.0f));
                    shader.setUniformVec4("uTintColor", pr[slot], pg[slot], pb[slot], alpha);
                    int vertexOffset = (texturedQuadCount + k) * VERTICES_PER_QUAD;
                    mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, vertexOffset, VERTICES_PER_QUAD);
                }
            }
        } finally {
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    /** Ghi 6 đỉnh của một quad billboard vào mảng {@link #vertices} tại chỗ (zero-GC). */
    private int writeBillboardQuad(int slot, int offset,
                                   float rx, float ry, float rz,
                                   float ux, float uy, float uz) {
        float half = size[slot] * 0.5f;
        float cx = px[slot];
        float cy = py[slot];
        float cz = pz[slot];

        float hrx = rx * half;
        float hry = ry * half;
        float hrz = rz * half;
        float hux = ux * half;
        float huy = uy * half;
        float huz = uz * half;

        // 4 góc của quad xoay hướng camera theo thứ tự BL, BR, TR, TL.
        float blX = cx - hrx - hux;
        float blY = cy - hry - huy;
        float blZ = cz - hrz - huz;

        float brX = cx + hrx - hux;
        float brY = cy + hry - huy;
        float brZ = cz + hrz - huz;

        float trX = cx + hrx + hux;
        float trY = cy + hry + huy;
        float trZ = cz + hrz + huz;

        float tlX = cx - hrx + hux;
        float tlY = cy - hry + huy;
        float tlZ = cz - hrz + huz;

        float pu0 = u0[slot];
        float pv0 = v0[slot];
        float pu1 = u1[slot];
        float pv1 = v1[slot];
        float s = shade[slot];

        offset = putVertex(offset, blX, blY, blZ, pu0, pv1, s);
        offset = putVertex(offset, brX, brY, brZ, pu1, pv1, s);
        offset = putVertex(offset, trX, trY, trZ, pu1, pv0, s);
        offset = putVertex(offset, blX, blY, blZ, pu0, pv1, s);
        offset = putVertex(offset, trX, trY, trZ, pu1, pv0, s);
        return putVertex(offset, tlX, tlY, tlZ, pu0, pv0, s);
    }

    /** Ghi 6 float của một đỉnh vào bộ đệm thành viên {@link #vertices}. */
    private int putVertex(int offset, float x, float y, float z, float u, float v, float s) {
        vertices[offset] = x;
        vertices[offset + 1] = y;
        vertices[offset + 2] = z;
        vertices[offset + 3] = u;
        vertices[offset + 4] = v;
        vertices[offset + 5] = s;
        return offset + FLOATS_PER_VERTEX;
    }

    /** Trả về số lượng hạt đang còn hoạt động trong pool (trong khoảng {@code 0..512}). */
    public int activeCount() {
        return activeCount;
    }

    /** Giải phóng {@link Mesh} trên GPU và đặt lại toàn bộ trạng thái hạt trong pool. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        Arrays.fill(active, false);
        activeCount = 0;
        writeCursor = 0;
        if (mesh != null) {
            mesh.dispose();
        }
    }
}
