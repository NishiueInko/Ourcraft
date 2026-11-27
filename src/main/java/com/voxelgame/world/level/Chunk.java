package com.voxelgame.world.level;

import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.biome.BiomeTint;
import com.voxelgame.world.level.block.Block.Face;
import com.voxelgame.world.level.block.Block.GeometryType;
import com.voxelgame.world.level.block.Block.RenderLayer;
import com.voxelgame.world.level.block.Block;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import java.util.Arrays;

/** Lưu block trong ô lưới 16³, dựng hình học và sở hữu hai mesh của vùng. */
public class Chunk implements Disposable {
    // Số ô theo mỗi trục và bán kích thước hộp bao của chunk.
    public static final int SIZE = 16;
    private static final float HALF_SIZE = SIZE / 2.0f;

    // Thứ tự thuộc tính mỗi đỉnh: x, y, z, u, v, hệ số ánh sáng, màu đỉnh đóng gói
    // (aColor).
    public static final int FLOATS_PER_VERTEX = 7;
    /** Vị trí offset của thuộc tính {@code aShade} trong một đỉnh. */
    static final int SHADE_OFFSET = 5;
    /** Vị trí offset của thuộc tính {@code aColor} đóng gói trong một đỉnh. */
    static final int COLOR_OFFSET = 6;
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade"),
            new VertexAttribute(Usage.ColorPacked, 4, "aColor"));

    // Tọa độ UV và giá trị enum được tính một lần cho tất cả lần dựng.
    private static final float[][] TILE_UVS = createTileUvs();
    private static final Face[] FACES = Face.values();
    /** Giới hạn của chỉ số đỉnh 16 bit mà Mesh libGDX gửi lên GPU. */
    private static final int MAX_INDEXED_QUADS = 65_536 / 4;
    private static final short[] QUAD_INDICES = createQuadIndices();

    // Hệ số độ sáng theo hướng mặt.
    private static final float SHADE_TOP = 1.0f;
    private static final float SHADE_BOTTOM = 0.55f;
    private static final float SHADE_NORTH_SOUTH = 0.85f;
    private static final float SHADE_EAST_WEST = 0.72f;
    /** Mức giảm độ sáng ở mỗi bậc che góc và độ che một phần của lá khoét alpha. */
    private static final float AO_STEP = 0.17f;
    private static final float LEAF_OCCLUSION = 0.5f;
    /**
     * Ánh sáng mặc định 4-bit đóng gói: Sky Light = 15 (nibble cao), Block Light =
     * 0 (nibble thấp).
     */
    static final byte DEFAULT_LIGHT = (byte) 0xF0;

    // Tọa độ góc nhỏ nhất của chunk trong thế giới.
    private final int baseWorldX;
    private final int baseWorldY;
    private final int baseWorldZ;

    // Mỗi ID chiếm một byte. Mảng metadata và light chỉ xuất hiện khi có dữ liệu
    // khác mặc định.
    private final byte[] blocks = new byte[SIZE * SIZE * SIZE];
    private byte[] metadata;
    private byte defaultLight = DEFAULT_LIGHT;
    private byte[] light;
    private int blockCount;

    private Mesh opaqueMesh;
    private Mesh translucentMesh;
    private boolean dirty = true;

    // Bộ đệm RGB dùng lại khi tính màu biome trong buildVertices để tránh cấp phát
    // rác GC.
    private final float[] biomeTintBuffer = new float[3];

    // Khung hình gần nhất mà hộp bao chunk giao khung nhìn.
    long lastVisibleFrame;

    /** Tính trước tọa độ dùng để dựng mesh và kiểm tra khung nhìn. */
    public Chunk(int chunkX, int chunkY, int chunkZ) {
        baseWorldX = chunkX * SIZE;
        baseWorldY = chunkY * SIZE;
        baseWorldZ = chunkZ * SIZE;
    }

    /** Tính chỉ số mảng phẳng với trục Z liên tiếp trong bộ nhớ. */
    private static int blockIndex(int x, int y, int z) {
        return (x * SIZE + y) * SIZE + z;
    }

    /** Kiểm tra tọa độ cục bộ trước khi đọc dữ liệu, kể cả ở mép bản đồ. */
    private static boolean contains(int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE;
    }

    /** Chuẩn bị bảng UV từ công thức atlas dùng chung của các loại block. */
    private static float[][] createTileUvs() {
        float[][] tileUvs = new float[Texture.ATLAS_COLS * Texture.ATLAS_ROWS][];
        for (int tile = 0; tile < tileUvs.length; tile++) {
            tileUvs[tile] = Block.getTileUV(tile);
        }
        return tileUvs;
    }

    /** Lấy UV dùng chung để đọc, tránh cấp phát khi vẽ biểu tượng Hotbar. */
    public static float[] tileUv(int tile) {
        return TILE_UVS[tile];
    }

    /** Đọc ID khối, trả Air cho tọa độ nằm ngoài chunk. */
    byte getBlockLocal(int x, int y, int z) {
        return contains(x, y, z) ? blocks[blockIndex(x, y, z)] : Block.AIR.getId();
    }

    /** Đọc metadata dạng không dấu. Ô chưa từng lưu trạng thái trả 0. */
    int getStateLocal(int x, int y, int z) {
        return metadata != null && contains(x, y, z)
                ? Byte.toUnsignedInt(metadata[blockIndex(x, y, z)])
                : 0;
    }

    /** Đọc Sky Light cục bộ (0..15) từ nibble cao của byte ánh sáng. */
    int getSkyLightLocal(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return ChunkLighting.MAX_LIGHT;
        }
        int packed = light != null
                ? Byte.toUnsignedInt(light[blockIndex(x, y, z)])
                : Byte.toUnsignedInt(defaultLight);
        return packed >>> 4;
    }

    /** Đọc Block Light cục bộ (0..15) từ nibble thấp của byte ánh sáng. */
    int getBlockLightLocal(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return 0;
        }
        int packed = light != null
                ? Byte.toUnsignedInt(light[blockIndex(x, y, z)])
                : Byte.toUnsignedInt(defaultLight);
        return packed & 0x0F;
    }

    /**
     * Ghi Sky Light cục bộ (0..15), cấp phát lười mảng byte[4096] khi khác mặc
     * định.
     */
    boolean setSkyLightLocal(int x, int y, int z, int level) {
        if (!contains(x, y, z)) {
            return false;
        }
        int clamped = Math.max(0, Math.min(ChunkLighting.MAX_LIGHT, level));
        int index = blockIndex(x, y, z);
        int current = light != null ? Byte.toUnsignedInt(light[index]) : Byte.toUnsignedInt(defaultLight);
        if ((current >>> 4) == clamped) {
            return false;
        }
        if (light == null) {
            light = new byte[SIZE * SIZE * SIZE];
            Arrays.fill(light, defaultLight);
        }
        light[index] = (byte) ((clamped << 4) | (current & 0x0F));
        return true;
    }

    /**
     * Ghi Block Light cục bộ (0..15), cấp phát lười mảng byte[4096] khi khác mặc
     * định.
     */
    boolean setBlockLightLocal(int x, int y, int z, int level) {
        if (!contains(x, y, z)) {
            return false;
        }
        int clamped = Math.max(0, Math.min(ChunkLighting.MAX_LIGHT, level));
        int index = blockIndex(x, y, z);
        int current = light != null ? Byte.toUnsignedInt(light[index]) : Byte.toUnsignedInt(defaultLight);
        if ((current & 0x0F) == clamped) {
            return false;
        }
        if (light == null) {
            light = new byte[SIZE * SIZE * SIZE];
            Arrays.fill(light, defaultLight);
        }
        light[index] = (byte) ((current & 0xF0) | clamped);
        return true;
    }

    /**
     * Đặt ánh sáng đồng nhất cho cả chunk để giữ `light = null` (tiết kiệm RAM ở
     * vùng đồng nhất).
     */
    void setUniformLight(byte packedLight) {
        defaultLight = packedLight;
        light = null;
    }

    /** Nạp mảng ánh sáng 4096 byte đã tính sẵn từ bộ đệm cột của ChunkLighting. */
    void applyLightArray(byte[] source) {
        if (light == null) {
            light = new byte[SIZE * SIZE * SIZE];
        }
        System.arraycopy(source, 0, light, 0, light.length);
    }

    /**
     * Kiểm tra chunk có lưu dữ liệu ánh sáng khác mặc định (sky=15, block=0) hay
     * không.
     */
    boolean hasNonDefaultLight() {
        if (light == null) {
            return defaultLight != DEFAULT_LIGHT;
        }
        for (byte value : light) {
            if (value != DEFAULT_LIGHT) {
                return true;
            }
        }
        return false;
    }

    /** Ghi ID với trạng thái mặc định, đồng thời loại bỏ metadata của ô cũ. */
    boolean setBlockLocal(int x, int y, int z, byte blockId) {
        return setBlockLocal(x, y, z, blockId, 0);
    }

    /**
     * Ghi ID và trạng thái cùng lúc. ID hoặc metadata thay đổi đều làm bẩn mesh.
     */
    boolean setBlockLocal(int x, int y, int z, byte blockId, int state) {
        int index = blockIndex(x, y, z);
        byte previous = blocks[index];
        byte nextState = blockId == 0 ? 0 : (byte) state;
        byte previousState = metadata == null ? 0 : metadata[index];
        if (previous == blockId && previousState == nextState) {
            return false;
        }
        if (nextState != 0 && metadata == null) {
            metadata = new byte[blocks.length];
        }
        if (metadata != null) {
            metadata[index] = nextState;
        }
        if (previous == 0) {
            blockCount++;
        } else if (blockId == 0) {
            blockCount--;
        }
        blocks[index] = blockId;
        dirty = true;
        return true;
    }

    /**
     * Ghi dải địa tầng khác Air trong một cột đã được World cắt theo biên chunk.
     */
    void fillGeneratedColumnSegment(int x, int z, int firstY, int endY, byte blockId) {
        int position = blockIndex(x, firstY, z);
        for (int y = firstY; y < endY; y++, position += SIZE) {
            if (blocks[position] == 0) {
                blockCount++;
            }
            blocks[position] = blockId;
            if (metadata != null) {
                metadata[position] = 0;
            }
        }
        dirty = true;
    }

    /** Kiểm tra chunk chỉ còn không khí. */
    boolean isEmpty() {
        return blockCount == 0;
    }

    /** Với mỗi mặt phẳng, thử đúng góc hộp xa nhất về phía trong của khung nhìn. */
    boolean intersectsFrustum(Frustum frustum) {
        for (Plane plane : frustum.planes) {
            Vector3 normal = plane.normal;
            int x = normal.x >= 0f ? baseWorldX + SIZE : baseWorldX;
            int y = normal.y >= 0f ? baseWorldY + SIZE : baseWorldY;
            int z = normal.z >= 0f ? baseWorldZ + SIZE : baseWorldZ;
            if (plane.testPoint(x, y, z) == Plane.PlaneSide.Back) {
                return false;
            }
        }
        return true;
    }

    /** Tính khoảng cách bình phương đến hộp bao để ưu tiên dựng các chunk gần. */
    float distanceSquaredTo(Vector3 point) {
        float dx = Math.max(0, Math.max(baseWorldX - point.x, point.x - (baseWorldX + SIZE)));
        float dy = Math.max(0, Math.max(baseWorldY - point.y, point.y - (baseWorldY + SIZE)));
        float dz = Math.max(0, Math.max(baseWorldZ - point.z, point.z - (baseWorldZ + SIZE)));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Tính khoảng cách bình phương đến tâm chunk cho thứ tự vẽ pha trong suốt. */
    float centerDistanceSquaredTo(Vector3 point) {
        float dx = baseWorldX + HALF_SIZE - point.x;
        float dy = baseWorldY + HALF_SIZE - point.y;
        float dz = baseWorldZ + HALF_SIZE - point.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Kiểm tra bộ đệm mesh của ít nhất một pha kết xuất đang tồn tại. */
    boolean hasMesh() {
        return opaqueMesh != null || translucentMesh != null;
    }

    /** Kiểm tra chunk có hình học ghi chiều sâu trong lượt đầu. */
    boolean hasOpaqueMesh() {
        return opaqueMesh != null;
    }

    /** Kiểm tra chunk có đỉnh để vẽ trong pha hòa màu. */
    boolean hasTranslucentMesh() {
        return translucentMesh != null;
    }

    /** Thu hồi cả hai mesh và cho phép dựng lại khi chunk quay vào tầm nhìn. */
    void releaseCachedMesh() {
        dispose();
        markDirty();
    }

    /** Báo dữ liệu block đã thay đổi kể từ lần dựng gần nhất. */
    boolean isDirty() {
        return dirty;
    }

    /**
     * Đánh dấu cần dựng lại khi hình học hoặc ánh sáng phụ thuộc chunk bên cạnh
     * thay đổi.
     */
    void markDirty() {
        dirty = true;
    }

    /**
     * Dựng lại hai mesh từ các bộ đệm số thực được thế giới dùng lại giữa các
     * chunk.
     */
    void rebuildMesh(World world, FloatArray opaqueVertices, FloatArray translucentVertices) {
        buildVertices(world, opaqueVertices, translucentVertices);
        opaqueMesh = uploadMesh(opaqueMesh, opaqueVertices);
        translucentMesh = uploadMesh(translucentMesh, translucentVertices);
        dirty = false;
    }

    /** Tính các mặt lộ ra của khối và ghi đỉnh vào đúng pha kết xuất. */
    void buildVertices(World world, FloatArray opaqueVertices, FloatArray translucentVertices) {
        opaqueVertices.clear();
        translucentVertices.clear();

        int index = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++, index++) {
                    byte id = blocks[index];
                    if (id == 0) {
                        continue;
                    }
                    Block block = Block.fromId(id);
                    if (block == Block.AIR) {
                        continue;
                    }

                    int wx = baseWorldX + x;
                    int wy = baseWorldY + y;
                    int wz = baseWorldZ + z;
                    int state = metadata == null ? 0 : Byte.toUnsignedInt(metadata[index]);
                    Block above = Block.fromId(world.getBlock(wx, wy + 1, wz));
                    FloatArray vertices = block.getRenderLayer() == RenderLayer.TRANSLUCENT
                            ? translucentVertices
                            : opaqueVertices;
                    if (block.getGeometryType() == GeometryType.CROSS_PLANT) {
                        float[] uv = tileUv(block.getTileIndex(Face.SOUTH, state, above));
                        float plantLight = computeLightFactor(world, wx, wy, wz);
                        float plantColor = BiomeTint.WHITE_PACKED_COLOR;
                        if (block == Block.SHORT_GRASS || block == Block.TALL_GRASS) {
                            BiomeTint.grassMultiplier(world, wx, wz, biomeTintBuffer);
                            plantColor = BiomeTint.packMultiplier(biomeTintBuffer);
                        }
                        addCross(vertices, wx, wy, wz, block.getSelectionBounds(state), uv, plantLight, plantColor);
                    } else {
                        int firstVertex = vertices.size;
                        addBox(world, vertices, block, state, above, wx, wy, wz, biomeTintBuffer);
                        // Dùng dấu của hệ số mặt để đánh dấu phát sáng mà không tăng kích thước đỉnh.
                        if (block == Block.LAVA) {
                            for (int shade = firstVertex
                                    + SHADE_OFFSET; shade < vertices.size; shade += FLOATS_PER_VERTEX) {
                                vertices.items[shade] = -vertices.items[shade];
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Cập nhật bộ đệm hiện có hoặc thay bằng mesh lớn hơn khi số đỉnh vượt dung
     * lượng.
     */
    private static Mesh uploadMesh(Mesh current, FloatArray vertices) {
        if (vertices.size == 0) {
            if (current != null) {
                current.dispose();
            }
            return null;
        }

        int quads = vertices.size / (6 * FLOATS_PER_VERTEX);
        boolean indexed = quads <= MAX_INDEXED_QUADS;
        int indexCount = indexed ? quads * 6 : 0;
        if (indexed) {
            compactQuads(vertices, quads);
        }
        int vertexCount = vertices.size / FLOATS_PER_VERTEX;
        if (current != null && current.getMaxVertices() >= vertexCount
                && (current.getMaxIndices() > 0) == indexed && current.getMaxIndices() >= indexCount) {
            uploadVertices(current, vertices, indexCount);
            return current;
        }

        Mesh replacement = new Mesh(true, vertexCount, indexCount, VERTEX_ATTRIBUTES);
        try {
            uploadVertices(replacement, vertices, indexCount);
        } catch (RuntimeException | Error failure) {
            replacement.dispose();
            throw failure;
        }
        if (current != null) {
            current.dispose();
        }
        return replacement;
    }

    /**
     * Ưu tiên bốn đỉnh mỗi mặt để giảm kích thước tải và công việc của vertex
     * shader.
     */
    static void compactQuads(FloatArray vertices, int quads) {
        for (int quad = 0; quad < quads; quad++) {
            int from = quad * 6 * FLOATS_PER_VERTEX;
            int to = quad * 4 * FLOATS_PER_VERTEX;
            System.arraycopy(vertices.items, from, vertices.items, to, 3 * FLOATS_PER_VERTEX);
            System.arraycopy(vertices.items, from + 5 * FLOATS_PER_VERTEX,
                    vertices.items, to + 3 * FLOATS_PER_VERTEX, FLOATS_PER_VERTEX);
        }
        vertices.size = quads * 4 * FLOATS_PER_VERTEX;
    }

    /** Dùng lại dãy hai tam giác 0–1–2, 0–2–3 cho tất cả các mesh có chỉ số. */
    private static short[] createQuadIndices() {
        short[] indices = new short[MAX_INDEXED_QUADS * 6];
        for (int quad = 0; quad < MAX_INDEXED_QUADS; quad++) {
            int vertex = quad * 4;
            int index = quad * 6;
            indices[index] = (short) vertex;
            indices[index + 1] = (short) (vertex + 1);
            indices[index + 2] = (short) (vertex + 2);
            indices[index + 3] = (short) vertex;
            indices[index + 4] = (short) (vertex + 2);
            indices[index + 5] = (short) (vertex + 3);
        }
        return indices;
    }

    /** Cập nhật chỉ số khi số mặt thay đổi và tải phần đỉnh có dữ liệu. */
    private static void uploadVertices(Mesh mesh, FloatArray vertices, int indexCount) {
        mesh.setVertices(vertices.items, 0, vertices.size);
        if (indexCount != 0 && mesh.getNumIndices() != indexCount) {
            mesh.setIndices(QUAD_INDICES, 0, indexCount);
        }
    }

    /** Vẽ chung các mặt đục và mặt khoét alpha trong pha ghi độ sâu. */
    void render(ShaderProgram shader) {
        renderMesh(opaqueMesh, shader);
    }

    /** Vẽ nước và băng trong pha hòa màu do World điều phối. */
    void renderTranslucent(ShaderProgram shader) {
        renderMesh(translucentMesh, shader);
    }

    /** Gửi các tam giác đã tải của một mesh sang shader. */
    private static void renderMesh(Mesh mesh, ShaderProgram shader) {
        if (mesh != null) {
            int indices = mesh.getNumIndices();
            mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 0,
                    indices == 0 ? mesh.getNumVertices() : indices);
        }
    }

    /** Giải phóng cả hai bộ đệm libGDX và cho phép gọi lại khi đã thu hồi. */
    @Override
    public void dispose() {
        if (opaqueMesh != null) {
            opaqueMesh.dispose();
            opaqueMesh = null;
        }
        if (translucentMesh != null) {
            translucentMesh.dispose();
            translucentMesh = null;
        }
    }

    /**
     * Dựng tối đa sáu mặt cho hình hộp, cắt phần bên cạnh đã bị phủ kín và áp dụng
     * nhuộm biome phù hợp.
     */
    private static void addBox(World world, FloatArray v, Block block, int state, Block above,
            int x, int y, int z, float[] tintRgb) {
        float inset = horizontalInset(block);
        float height = renderHeight(world, block, state, x, y, z);
        float leavesColor = BiomeTint.WHITE_PACKED_COLOR;
        if (block == Block.OAK_LEAVES) {
            BiomeTint.foliageMultiplier(world, x, z, tintRgb);
            leavesColor = BiomeTint.packMultiplier(tintRgb);
        }
        for (Face face : FACES) {
            int first = v.size;
            int nx = x + (face == Face.EAST ? 1 : face == Face.WEST ? -1 : 0);
            int ny = y + (face == Face.TOP ? 1 : face == Face.BOTTOM ? -1 : 0);
            int nz = z + (face == Face.SOUTH ? 1 : face == Face.NORTH ? -1 : 0);
            Block neighbor = face == Face.TOP ? above : Block.fromId(world.getBlock(nx, ny, nz));

            // Dung nham giữ quy tắc cũ: chỉ vẽ những mặt giáp không khí.
            if (block == Block.LAVA && neighbor != Block.AIR) {
                continue;
            }
            boolean covers = canCover(block, neighbor);
            if (face == Face.TOP || face == Face.BOTTOM) {
                float coverInset = 0.5f;
                if (covers && (face == Face.TOP ? height == 1.0f
                        : neighborHeight(world, neighbor, nx, ny, nz) == 1.0f)) {
                    coverInset = horizontalInset(neighbor);
                }
                if (coverInset > inset) {
                    int tile = block.getTileIndex(face, state, above);
                    float faceColor = leavesColor;
                    if (block == Block.GRASS && face == Face.TOP && tile == Texture.TILE_GRASS_TOP) {
                        BiomeTint.grassMultiplier(world, x, z, tintRgb);
                        faceColor = BiomeTint.packMultiplier(tintRgb);
                    }
                    float[] uv = tileUv(tile);
                    addHorizontalFace(v, face, x, face == Face.TOP ? y + height : y, z,
                            inset, coverInset, uv, faceColor);
                }
            } else {
                // Chỉ hai hình chạm đến ranh giới ô mới có thể che thành bên của nhau.
                float visibleFrom = 0.0f;
                if (covers && inset == 0.0f && horizontalInset(neighbor) == 0.0f) {
                    visibleFrom = neighborHeight(world, neighbor, nx, ny, nz);
                }
                if (visibleFrom < height) {
                    float[] uv = tileUv(block.getTileIndex(face, state, above));
                    // Mặt bên GRASS có phần đất bên dưới nên giữ trắng 0xFFFFFFFF để không nhuộm
                    // sai màu đất.
                    addSideFace(v, face, x, y, z, inset, visibleFrom, height, uv, leavesColor);
                }
            }
            if (v.size != first && block != Block.LAVA) {
                // Với mặt trên thấp, các góc nhìn vào lớp ô ngang mặt thay vì lớp cao hơn.
                int sampleY = face == Face.TOP && height < 1.0f ? y : ny;
                shadeFace(world, v, first, face, x, y, z, nx, sampleY, nz);
            }
        }
    }

    /**
     * Kết hợp ánh sáng bầu trời / ánh sáng khối theo công thức
     * {@code max(skyLight/15f * skyFactor, blockLight/15f)} với sàn tối thiểu
     * 0.04f.
     */
    private static float computeLightFactor(World world, int x, int y, int z) {
        int sky = world.getSkyLight(x, y, z);
        int blockLight = world.getBlockLight(x, y, z);
        float skyFactor = world.getSkyLightFactor();
        float combined = Math.max((sky / 15.0f) * skyFactor, blockLight / 15.0f);
        return Math.max(ChunkLighting.MIN_LIGHT_LEVEL, combined);
    }

    /**
     * Lấy độ che ở bốn góc của mặt và nhân thêm cường độ Sky/Block Light lên đỉnh.
     */
    private static void shadeFace(World world, FloatArray vertices, int first, Face face,
            int x, int y, int z, int sampleX, int sampleY, int sampleZ) {
        // Nếu ô lấy mẫu bị khối đục chiếm (ví dụ mặt cạnh Cactus sát tường), đọc ánh
        // sáng ngay trong ô khối.
        boolean sampleOpaque = Block.fromId(world.getBlock(sampleX, sampleY, sampleZ)).isOpaque();
        float lightFactor = sampleOpaque
                ? computeLightFactor(world, x, y, z)
                : computeLightFactor(world, sampleX, sampleY, sampleZ);

        boolean alongZ = face == Face.EAST || face == Face.WEST;
        boolean horizontal = face == Face.TOP || face == Face.BOTTOM;
        int ux = alongZ ? 0 : 1;
        int uz = alongZ ? 1 : 0;
        int vy = horizontal ? 0 : 1;
        int vz = horizontal ? 1 : 0;
        float sideU0 = occlusionAt(world, sampleX - ux, sampleY, sampleZ - uz);
        float sideU1 = occlusionAt(world, sampleX + ux, sampleY, sampleZ + uz);
        float sideV0 = occlusionAt(world, sampleX, sampleY - vy, sampleZ - vz);
        float sideV1 = occlusionAt(world, sampleX, sampleY + vy, sampleZ + vz);
        float light00 = cornerLight(sideU0, sideV0, occlusionAt(world, sampleX - ux, sampleY - vy, sampleZ - uz - vz));
        float light01 = cornerLight(sideU0, sideV1, occlusionAt(world, sampleX - ux, sampleY + vy, sampleZ - uz + vz));
        float light10 = cornerLight(sideU1, sideV0, occlusionAt(world, sampleX + ux, sampleY - vy, sampleZ + uz - vz));
        float light11 = cornerLight(sideU1, sideV1, occlusionAt(world, sampleX + ux, sampleY + vy, sampleZ + uz + vz));
        boolean noAo = light00 == 1.0f && light01 == 1.0f && light10 == 1.0f && light11 == 1.0f;
        if (noAo && lightFactor == 1.0f) {
            return;
        }

        int axisU = alongZ ? 2 : 0;
        int axisV = horizontal ? 2 : 1;
        int originU = alongZ ? z : x;
        int originV = horizontal ? z : y;
        float[] items = vertices.items;
        for (int quad = first; quad < vertices.size; quad += 6 * FLOATS_PER_VERTEX) {
            if (noAo) {
                for (int i = quad; i < quad + 6 * FLOATS_PER_VERTEX; i += FLOATS_PER_VERTEX) {
                    items[i + SHADE_OFFSET] *= lightFactor;
                }
            } else {
                for (int i = quad; i < quad + 6 * FLOATS_PER_VERTEX; i += FLOATS_PER_VERTEX) {
                    float u = items[i + axisU] - originU;
                    float v = items[i + axisV] - originV;
                    items[i + SHADE_OFFSET] *= lightFactor
                            * lerp(lerp(light00, light10, u), lerp(light01, light11, u), v);
                }
                rotateIfDarkerDiagonal(items, quad);
            }
        }
    }

    /**
     * Đọc ô từ thế giới để tính che khuất cả qua mặt, cạnh và đỉnh chung của chunk.
     */
    private static float occlusionAt(World world, int x, int y, int z) {
        Block block = Block.fromId(world.getBlock(x, y, z));
        if (block.isOpaque() || (block == Block.SNOW_LAYER && world.getBlockState(x, y, z) == 7)) {
            return 1.0f;
        }
        return block == Block.OAK_LEAVES || block == Block.SPRUCE_LEAVES ? LEAF_OCCLUSION : 0.0f;
    }

    /** Hai ô đặc cạnh nhau che trọn góc, bất kể có thêm khối chéo. */
    private static float cornerLight(float sideU, float sideV, float diagonal) {
        float covered = sideU == 1.0f && sideV == 1.0f ? 3.0f : sideU + sideV + diagonal;
        return 1.0f - AO_STEP * covered;
    }

    /** Chọn đường chéo tối hơn và giữ mẫu hai tam giác 0–1–2, 0–2–3 sau khi nén. */
    private static void rotateIfDarkerDiagonal(float[] items, int first) {
        int vertex = FLOATS_PER_VERTEX;
        int shade = first + SHADE_OFFSET;
        if (items[shade] + items[shade + 2 * vertex] <= items[shade + vertex] + items[shade + 5 * vertex]) {
            return;
        }

        // Đổi A B C A C D thành B C D B D A, dùng hai đỉnh trùng làm chỗ lưu tạm.
        System.arraycopy(items, first + 5 * vertex, items, first + 4 * vertex, vertex);
        System.arraycopy(items, first, items, first + 5 * vertex, vertex);
        System.arraycopy(items, first + vertex, items, first + 3 * vertex, vertex);
        System.arraycopy(items, first + 2 * vertex, items, first + vertex, vertex);
        System.arraycopy(items, first + 4 * vertex, items, first + 2 * vertex, vertex);
        System.arraycopy(items, first + 3 * vertex, items, first, vertex);
    }

    /**
     * Lá và cây khoét alpha không che kín mặt kề. Nước hoặc băng che mặt tiếp xúc
     * cùng loại.
     */
    private static boolean canCover(Block current, Block neighbor) {
        if (neighbor == Block.AIR) {
            return false;
        }
        GeometryType shape = neighbor.getGeometryType();
        return neighbor.isOpaque() || shape == GeometryType.SNOW_LAYER || shape == GeometryType.CACTUS
                || (current == neighbor && current.getRenderLayer() == RenderLayer.TRANSLUCENT);
    }

    /** Lấy độ lùi ngang theo hình hộp xương rồng đã dùng cho hộp chọn. */
    private static float horizontalInset(Block block) {
        return block.getGeometryType() == GeometryType.CACTUS ? block.getSelectionBounds(0).minX() : 0.0f;
    }

    /** Đọc trạng thái ô bên chỉ khi chiều cao hình học phụ thuộc số lớp tuyết. */
    private static float neighborHeight(World world, Block block, int x, int y, int z) {
        int state = block.getGeometryType() == GeometryType.SNOW_LAYER ? world.getBlockState(x, y, z) : 0;
        return renderHeight(world, block, state, x, y, z);
    }

    /**
     * Dùng chiều cao hiển thị của khối lỏng và tuyết, các hộp còn lại cao một ô.
     */
    private static float renderHeight(World world, Block block, int state, int x, int y, int z) {
        return switch (block.getGeometryType()) {
            case LIQUID -> block.getTopHeight(world, x, y, z);
            case SNOW_LAYER -> block.getSelectionBounds(state).maxY();
            default -> 1.0f;
        };
    }

    /** Vẽ nguyên mặt ngang hoặc bốn dải viền còn lộ quanh hình hộp hẹp kề bên. */
    private static void addHorizontalFace(FloatArray v, Face face, float x, float y, float z,
            float inset, float coverInset, float[] uv, float color) {
        float edge = 1.0f - inset;
        if (coverInset == 0.5f) {
            addHorizontalRect(v, face, x, y, z, inset, edge, inset, edge, inset, uv, color);
            return;
        }
        float coverEdge = 1.0f - coverInset;
        addHorizontalRect(v, face, x, y, z, inset, coverInset, inset, edge, inset, uv, color);
        addHorizontalRect(v, face, x, y, z, coverEdge, edge, inset, edge, inset, uv, color);
        addHorizontalRect(v, face, x, y, z, coverInset, coverEdge, inset, coverInset, inset, uv, color);
        addHorizontalRect(v, face, x, y, z, coverInset, coverEdge, coverEdge, edge, inset, uv, color);
    }

    /** Quy đổi hình chữ nhật ngang sang tọa độ thế giới và UV của mặt gốc. */
    private static void addHorizontalRect(FloatArray v, Face face, float x, float y, float z,
            float minX, float maxX, float minZ, float maxZ,
            float inset, float[] uv, float color) {
        // Tọa độ UV đã bỏ viền PNG. Quy đổi phần lộ ra theo bề rộng thực của mặt.
        float width = 1.0f - 2.0f * inset;
        float u0 = lerp(uv[0], uv[2], (minX - inset) / width);
        float u1 = lerp(uv[0], uv[2], (maxX - inset) / width);
        float near = (minZ - inset) / width;
        float far = (maxZ - inset) / width;
        if (face == Face.TOP) {
            addQuad(v, x + minX, y, z + maxZ, x + maxX, y, z + maxZ,
                    x + maxX, y, z + minZ, x + minX, y, z + minZ,
                    u0, lerp(uv[1], uv[3], near), u1, lerp(uv[1], uv[3], far), SHADE_TOP, color);
        } else {
            addQuad(v, x + minX, y, z + minZ, x + maxX, y, z + minZ,
                    x + maxX, y, z + maxZ, x + minX, y, z + maxZ,
                    u0, lerp(uv[1], uv[3], 1.0f - far), u1, lerp(uv[1], uv[3], 1.0f - near), SHADE_BOTTOM, color);
        }
    }

    /** Dựng một thành bên từ độ cao bắt đầu thấy cho đến mép trên của khối. */
    private static void addSideFace(FloatArray v, Face face, float x, float y, float z,
            float inset, float visibleFrom, float height, float[] uv, float color) {
        float x0 = x + inset;
        float x1 = x + 1.0f - inset;
        float z0 = z + inset;
        float z1 = z + 1.0f - inset;
        float y0 = y + visibleFrom;
        float y1 = y + height;
        float vBottom = lerp(uv[3], uv[1], visibleFrom / height);
        switch (face) {
            case SOUTH -> addQuad(v, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                    uv[0], uv[1], uv[2], vBottom, SHADE_NORTH_SOUTH, color);
            case NORTH -> addQuad(v, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                    uv[0], uv[1], uv[2], vBottom, SHADE_NORTH_SOUTH, color);
            case EAST -> addQuad(v, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                    uv[0], uv[1], uv[2], vBottom, SHADE_EAST_WEST, color);
            case WEST -> addQuad(v, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                    uv[0], uv[1], uv[2], vBottom, SHADE_EAST_WEST, color);
            default -> throw new IllegalArgumentException("Thành bên cần mặt theo trục ngang");
        }
    }

    /**
     * Tạo hai tấm chéo nằm trong hộp chọn để vùng vẽ và vùng ngắm cùng kích thước.
     */
    private static void addCross(FloatArray v, float x, float y, float z, Block.Bounds bounds, float[] uv,
            float lightFactor, float color) {
        float minX = x + bounds.minX();
        float maxX = x + bounds.maxX();
        float minZ = z + bounds.minZ();
        float maxZ = z + bounds.maxZ();
        float bottom = y + bounds.minY();
        float top = y + bounds.maxY();
        float shade = SHADE_TOP * lightFactor;
        addPlantPlane(v, minX, bottom, minZ, maxX, top, maxZ, uv, shade, color);
        addPlantPlane(v, maxX, bottom, minZ, minX, top, maxZ, uv, shade, color);
    }

    /** Thêm hai mặt quay ngược nhau của một tấm chéo để vẫn dùng culling chung. */
    private static void addPlantPlane(FloatArray v, float x0, float bottom, float z0,
            float x1, float top, float z1, float[] uv, float shade, float color) {
        addQuad(v, x0, bottom, z0, x1, bottom, z1, x1, top, z1, x0, top, z0,
                uv[0], uv[1], uv[2], uv[3], shade, color);
        addQuad(v, x1, bottom, z1, x0, bottom, z0, x0, top, z0, x1, top, z1,
                uv[2], uv[1], uv[0], uv[3], shade, color);
    }

    /** Nội suy UV để phần hình đã bị cắt vẫn giữ đúng tỷ lệ chất liệu gốc. */
    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    /** Nối bốn góc theo hai tam giác cùng chiều để OpenGL có thể bỏ mặt sau. */
    private static void addQuad(FloatArray v, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1, float shade, float color) {
        putVertex(v, ax, ay, az, u0, v1, shade, color);
        putVertex(v, bx, by, bz, u1, v1, shade, color);
        putVertex(v, cx, cy, cz, u1, v0, shade, color);
        putVertex(v, ax, ay, az, u0, v1, shade, color);
        putVertex(v, cx, cy, cz, u1, v0, shade, color);
        putVertex(v, dx, dy, dz, u0, v0, shade, color);
    }

    /**
     * Ghi một đỉnh theo thứ tự thuộc tính 7 floats của shader chunk (không cấp phát
     * varargs).
     */
    private static void putVertex(FloatArray vertices, float x, float y, float z,
            float u, float v, float shade, float color) {
        vertices.add(x, y, z);
        vertices.add(u, v, shade, color);
    }
}
