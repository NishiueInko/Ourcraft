package com.voxelgame.client.render;

import com.voxelgame.world.level.block.Block;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * Kết xuất khung dây chọn block ({@code GL_LINES}, đen mờ {@code alpha = 0.65f}) ôm sát
 * {@link Block.Bounds} nới {@code 0.004f} và lớp phủ vết nứt 10 giai đoạn ({@code destroy_stage_0..9.png})
 * đóng gói thành một texture {@code 160x16} duy nhất trên GPU, cập nhật đỉnh tại chỗ theo cơ chế zero-GC.
 */
public class BlockOutlineRenderer implements Disposable {

    /** Số giai đoạn nứt vỡ chuẩn từ {@code destroy_stage_0.png} đến {@code destroy_stage_9.png}. */
    public static final int STAGE_COUNT = 10;

    /** Độ nới khung dây chọn block để tránh trùng mặt (Z-fighting) với bề mặt khối gốc. */
    public static final float OUTLINE_EXPAND = 0.004f;

    /** Độ nới lớp phủ vết nứt nằm giữa mặt khối gốc và khung dây bên ngoài. */
    public static final float CRACK_EXPAND = 0.002f;

    /** Độ mờ của khung dây chọn block màu đen. */
    public static final float OUTLINE_ALPHA = 0.65f;

    /** Định dạng 6 số thực mỗi đỉnh khớp với {@link ShaderProgram}: aPos(3), aTexCoord(2), aShade(1). */
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int LINE_VERTEX_COUNT = 24;
    private static final int CRACK_VERTEX_COUNT = 36;

    /** Kích thước atlas ngang {@code 160x16} chứa 10 giai đoạn vết nứt {@code 16x16}. */
    private static final int DESTROY_ATLAS_WIDTH = Texture.TILE_SIZE * STAGE_COUNT;
    private static final int DESTROY_ATLAS_HEIGHT = Texture.TILE_SIZE;

    private final com.badlogic.gdx.graphics.Texture destroyTexture;
    private final Mesh lineMesh;
    private final Mesh crackMesh;
    private final float[] lineVertices = new float[LINE_VERTEX_COUNT * FLOATS_PER_VERTEX];
    private final float[] crackVertices = new float[CRACK_VERTEX_COUNT * FLOATS_PER_VERTEX];
    private final Matrix4 modelMatrix = new Matrix4();
    private boolean disposed;

    /**
     * Khởi tạo bộ kết xuất viền chọn và vết nứt block:
     * nạp 10 ảnh {@code textures/destroy_stage_0..9.png}, đóng gói vào texture {@code 160x16}
     * và cấp phát trước hai {@link Mesh} cho khung dây (24 đỉnh) cùng hộp vết nứt (36 đỉnh).
     * Hỗ trợ an toàn khi chạy kiểm thử CPU không có ngữ cảnh OpenGL ({@code Gdx.gl == null}).
     */
    public BlockOutlineRenderer() {
        GdxNativesLoader.load();
        com.badlogic.gdx.graphics.Texture createdTexture = null;
        Mesh createdLineMesh = null;
        Mesh createdCrackMesh = null;

        if (Gdx.gl != null) {
            Pixmap atlasPixmap = buildDestroyAtlasPixmap();
            try {
                createdTexture = new com.badlogic.gdx.graphics.Texture(atlasPixmap);
                createdTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
                createdTexture.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);

                createdLineMesh = createDynamicMesh(LINE_VERTEX_COUNT);
                createdCrackMesh = createDynamicMesh(CRACK_VERTEX_COUNT);
            } catch (RuntimeException | Error failure) {
                if (createdTexture != null) {
                    createdTexture.dispose();
                }
                if (createdLineMesh != null) {
                    createdLineMesh.dispose();
                }
                if (createdCrackMesh != null) {
                    createdCrackMesh.dispose();
                }
                throw failure;
            } finally {
                atlasPixmap.dispose();
            }
        }

        this.destroyTexture = createdTexture;
        this.lineMesh = createdLineMesh;
        this.crackMesh = createdCrackMesh;
    }

    /** Tạo {@link Mesh} động dùng chung định dạng 6 floats/đỉnh với {@link ShaderProgram}. */
    private static Mesh createDynamicMesh(int maxVertices) {
        return new Mesh(
                Mesh.VertexDataType.VertexBufferObjectWithVAO,
                false,
                maxVertices,
                0,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "aPos"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aTexCoord"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "aShade")
        );
    }

    /**
     * Nạp 10 ảnh {@code textures/destroy_stage_0.png .. textures/destroy_stage_9.png} qua
     * {@link Texture#loadPixmapFlexible(String...)} và {@link Texture#readNormalizedPixel(Pixmap, int, int)},
     * đóng gói thành một {@link Pixmap} kích thước {@code 160x16} định dạng {@code RGBA8888}.
     */
    public static Pixmap buildDestroyAtlasPixmap() {
        Pixmap atlas = new Pixmap(DESTROY_ATLAS_WIDTH, DESTROY_ATLAS_HEIGHT, Pixmap.Format.RGBA8888);
        atlas.setBlending(Pixmap.Blending.None);
        try {
            for (int stage = 0; stage < STAGE_COUNT; stage++) {
                String primary = "textures/destroy_stage_" + stage + ".png";
                String secondary = "textures/block/destroy_stage_" + stage + ".png";
                Pixmap stagePixmap = Texture.loadPixmapFlexible(primary, secondary);
                if (stagePixmap != null) {
                    try {
                        blitStageToAtlas(atlas, stagePixmap, stage);
                    } finally {
                        stagePixmap.dispose();
                    }
                } else {
                    fillProceduralStageFallback(atlas, stage);
                }
            }
            return atlas;
        } catch (RuntimeException | Error failure) {
            atlas.dispose();
            throw failure;
        }
    }

    /** Sao chép và chuẩn hóa từng pixel từ ảnh giai đoạn nứt vào đúng ô {@code 16x16} trên atlas ngang. */
    private static void blitStageToAtlas(Pixmap atlas, Pixmap src, int stage) {
        int srcW = Math.max(1, src.getWidth());
        int srcH = Math.max(1, src.getHeight());
        int offsetX = stage * Texture.TILE_SIZE;
        for (int ty = 0; ty < Texture.TILE_SIZE; ty++) {
            int sy = Math.min(srcH - 1, (int) (((ty + 0.5f) / Texture.TILE_SIZE) * srcH));
            for (int tx = 0; tx < Texture.TILE_SIZE; tx++) {
                int sx = Math.min(srcW - 1, (int) (((tx + 0.5f) / Texture.TILE_SIZE) * srcW));
                int rgba = Texture.readNormalizedPixel(src, sx, sy);
                atlas.drawPixel(offsetX + tx, ty, rgba);
            }
        }
    }

    /** Sinh mẫu vết nứt dự phòng tất định nếu thiếu file ảnh giai đoạn đào. */
    private static void fillProceduralStageFallback(Pixmap atlas, int stage) {
        int offsetX = stage * Texture.TILE_SIZE;
        int threshold = stage + 1;
        for (int ty = 0; ty < Texture.TILE_SIZE; ty++) {
            for (int tx = 0; tx < Texture.TILE_SIZE; tx++) {
                int dist = Math.abs(tx - 7) + Math.abs(ty - 7);
                boolean isCrack = (tx == ty || tx + ty == 15) && dist <= threshold + 2;
                int rgba = isCrack ? Texture.packRgba(55, 55, 55, 255) : Texture.packRgba(255, 255, 255, 0);
                atlas.drawPixel(offsetX + tx, ty, rgba);
            }
        }
    }

    /**
     * Quy đổi tiến trình đào {@code miningProgress} sang chỉ số giai đoạn vết nứt {@code 0..9}.
     * Trả về {@code -1} khi chưa bắt đầu đào ({@code progress <= 0.001f}).
     *
     * @param progress tiến trình đào chuẩn hóa (thường trong {@code [0.0f, 1.0f]})
     * @return {@code -1} nếu chưa nứt, hoặc chỉ số giai đoạn trong {@code [0, 9]}
     */
    public static int stageForProgress(float progress) {
        if (progress <= 0.001f) {
            return -1;
        }
        float clamped = Math.min(0.9999f, progress);
        int stage = (int) (clamped * STAGE_COUNT);
        return Math.max(0, Math.min(STAGE_COUNT - 1, stage));
    }

    /**
     * Vẽ khung dây đen quanh hộp chọn {@link Block#getSelectionBounds(int)} của block mục tiêu
     * và vẽ lớp phủ vết nứt 10 giai đoạn khi {@code miningProgress > 0.001f}.
     *
     * @param shader         shader đang được bind sẵn với {@code uProjection} và {@code uView}
     * @param bx             tọa độ X nguyên của block
     * @param by             tọa độ Y nguyên của block
     * @param bz             tọa độ Z nguyên của block
     * @param block          loại block tại ô mục tiêu
     * @param state          metadata trạng thái của block
     * @param miningProgress tiến trình đập block hiện tại trong {@code [0.0f, 1.0f]}
     */
    public void render(ShaderProgram shader, int bx, int by, int bz, Block block, int state, float miningProgress) {
        if (disposed || shader == null || block == null || Gdx.gl == null
                || lineMesh == null || crackMesh == null || destroyTexture == null) {
            return;
        }
        Block.Bounds bounds = block.getSelectionBounds(state);
        if (bounds == null) {
            return;
        }

        float minX = bx + bounds.minX();
        float minY = by + bounds.minY();
        float minZ = bz + bounds.minZ();
        float maxX = bx + bounds.maxX();
        float maxY = by + bounds.maxY();
        float maxZ = bz + bounds.maxZ();

        modelMatrix.idt();
        shader.setUniformMatrix4f("uModel", modelMatrix);
        shader.setUniform1i("uEnvironmentMode", 0);
        shader.enableChunkColorMode(false);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        try {
            int stage = stageForProgress(miningProgress);
            if (stage >= 0) {
                fillCrackVertices(
                        minX - CRACK_EXPAND, minY - CRACK_EXPAND, minZ - CRACK_EXPAND,
                        maxX + CRACK_EXPAND, maxY + CRACK_EXPAND, maxZ + CRACK_EXPAND,
                        stage
                );
                destroyTexture.bind(0);
                shader.setUniform1i("uUseTexture", 1);
                shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 0.78f);
                Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                crackMesh.setVertices(crackVertices, 0, crackVertices.length);
                crackMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 0, CRACK_VERTEX_COUNT);
            }

            fillOutlineVertices(
                    minX - OUTLINE_EXPAND, minY - OUTLINE_EXPAND, minZ - OUTLINE_EXPAND,
                    maxX + OUTLINE_EXPAND, maxY + OUTLINE_EXPAND, maxZ + OUTLINE_EXPAND
            );
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.0f, 0.0f, 0.0f, OUTLINE_ALPHA);
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            lineMesh.setVertices(lineVertices, 0, lineVertices.length);
            lineMesh.render(shader.getGdxShader(), GL20.GL_LINES, 0, LINE_VERTEX_COUNT);
        } finally {
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    /** Điền trực tiếp 12 cạnh (24 đỉnh) của hộp khung dây vào mảng {@link #lineVertices} tại chỗ (zero-GC). */
    void fillOutlineVertices(float x0, float y0, float z0, float x1, float y1, float z1) {
        int offset = 0;
        // 4 cạnh đáy (Y = y0)
        offset = putSegment(lineVertices, offset, x0, y0, z0, x1, y0, z0);
        offset = putSegment(lineVertices, offset, x1, y0, z0, x1, y0, z1);
        offset = putSegment(lineVertices, offset, x1, y0, z1, x0, y0, z1);
        offset = putSegment(lineVertices, offset, x0, y0, z1, x0, y0, z0);
        // 4 cạnh đỉnh (Y = y1)
        offset = putSegment(lineVertices, offset, x0, y1, z0, x1, y1, z0);
        offset = putSegment(lineVertices, offset, x1, y1, z0, x1, y1, z1);
        offset = putSegment(lineVertices, offset, x1, y1, z1, x0, y1, z1);
        offset = putSegment(lineVertices, offset, x0, y1, z1, x0, y1, z0);
        // 4 cạnh đứng nối đáy và đỉnh
        offset = putSegment(lineVertices, offset, x0, y0, z0, x0, y1, z0);
        offset = putSegment(lineVertices, offset, x1, y0, z0, x1, y1, z0);
        offset = putSegment(lineVertices, offset, x1, y0, z1, x1, y1, z1);
        putSegment(lineVertices, offset, x0, y0, z1, x0, y1, z1);
    }

    /** Điền trực tiếp 6 mặt (36 đỉnh tam giác) của hộp vết nứt vào {@link #crackVertices} tại chỗ (zero-GC). */
    void fillCrackVertices(float x0, float y0, float z0, float x1, float y1, float z1, int stage) {
        float stageWidth = 1.0f / STAGE_COUNT;
        float epsU = 0.0005f;
        float u0 = stage * stageWidth + epsU;
        float u1 = (stage + 1) * stageWidth - epsU;
        float v0 = 0.0f;
        float v1 = 1.0f;

        int offset = 0;
        // Mặt trên (+Y) và mặt dưới (-Y)
        offset = putQuad(crackVertices, offset, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v0, u1, v1, 1.00f);
        offset = putQuad(crackVertices, offset, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1, 0.55f);
        // Mặt Nam (+Z) và mặt Bắc (-Z)
        offset = putQuad(crackVertices, offset, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v0, u1, v1, 0.85f);
        offset = putQuad(crackVertices, offset, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0, v0, u1, v1, 0.85f);
        // Mặt Đông (+X) và mặt Tây (-X)
        offset = putQuad(crackVertices, offset, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0, v0, u1, v1, 0.72f);
        putQuad(crackVertices, offset, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, v0, u1, v1, 0.72f);
    }

    /** Ghi một đoạn thẳng gồm 2 đỉnh vào mảng {@code out} với {@code shade = -1.0f} (màu thuần không nhân ánh sáng). */
    private static int putSegment(float[] out, int offset,
                                  float ax, float ay, float az,
                                  float bx, float by, float bz) {
        offset = putVertex(out, offset, ax, ay, az, 0.0f, 0.0f, -1.0f);
        return putVertex(out, offset, bx, by, bz, 0.0f, 0.0f, -1.0f);
    }

    /** Ghi một mặt tứ giác gồm 2 tam giác (6 đỉnh) theo thứ tự ngược chiều kim đồng hồ nhìn từ ngoài. */
    private static int putQuad(float[] out, int offset,
                               float ax, float ay, float az,
                               float bx, float by, float bz,
                               float cx, float cy, float cz,
                               float dx, float dy, float dz,
                               float u0, float v0, float u1, float v1, float shade) {
        offset = putVertex(out, offset, ax, ay, az, u0, v1, shade);
        offset = putVertex(out, offset, bx, by, bz, u1, v1, shade);
        offset = putVertex(out, offset, cx, cy, cz, u1, v0, shade);
        offset = putVertex(out, offset, ax, ay, az, u0, v1, shade);
        offset = putVertex(out, offset, cx, cy, cz, u1, v0, shade);
        return putVertex(out, offset, dx, dy, dz, u0, v0, shade);
    }

    /** Ghi 6 float của một đỉnh vào bộ đệm tại vị trí {@code offset}. */
    private static int putVertex(float[] out, int offset,
                                 float x, float y, float z,
                                 float u, float v, float shade) {
        out[offset] = x;
        out[offset + 1] = y;
        out[offset + 2] = z;
        out[offset + 3] = u;
        out[offset + 4] = v;
        out[offset + 5] = shade;
        return offset + FLOATS_PER_VERTEX;
    }

    /** Giải phóng 2 {@link Mesh} và texture atlas vết nứt {@code 160x16} trên GPU. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (lineMesh != null) {
            lineMesh.dispose();
        }
        if (crackMesh != null) {
            crackMesh.dispose();
        }
        if (destroyTexture != null) {
            destroyTexture.dispose();
        }
    }
}
