package com.voxelgame.engine;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.voxelgame.entity.Block;

/**
 * Quản lý một phân vùng không gian 16x16x16 Blocks (Chunk) và bộ đệm lưới đỉnh (libGDX Mesh),
 * triển khai giao diện {@link Disposable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Đóng gói cấp Package (Package-Private Encapsulation):
 *    - Các phương thức thao tác nội bộ (`getBlockLocal`, `setBlockLocal`, `isDirty`, `markDirty`,
 *      `rebuildMesh`, `render`) được giới hạn ở mức truy cập **package-private** (không có `public`).
 *    - Nhờ đó, chỉ lớp điều phối `World` nằm cùng package `com.voxelgame.engine` mới được phép can thiệp
 *      vào mảng dữ liệu và quá trình tái tạo lưới đỉnh Mesh của Chunk.
 * 2. Tính Đa hình (Polymorphism):
 *    - Trong `rebuildMesh(World)`, Chunk gọi trực tiếp phương thức đa hình `block.getTopHeight(world, wx, wy, wz)`
 *      và `block.getTileIndex(Block.Face.*)` để mỗi loại khối tự quyết định hình học và chất liệu.
 */
public class Chunk implements Disposable {

    public static final int SIZE = 16;
    // Bán kích thước hộp bao của chunk theo từng trục.
    private static final float HALF_SIZE = SIZE / 2.0f;
    private static final int FLOATS_PER_VERTEX = 6; // [x, y, z, u, v, shade]
    // Tọa độ UV dùng chung cho tất cả chunk, tính một lần theo từng ô Atlas.
    private static final float[][] TILE_UVS = createTileUvs();

    // Định nghĩa cấu trúc thuộc tính đỉnh của libGDX: vị trí vec3 (0), uv vec2 (1), shade float (2)
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade")
    );

    // Tọa độ góc nhỏ nhất của chunk trong thế giới.
    private final int baseWorldX;
    private final int baseWorldY;
    private final int baseWorldZ;

    // Mảng liên tục lưu một byte mỗi block. Số ô có block dùng để nhận diện chunk rỗng.
    private final byte[] blocks = new byte[SIZE * SIZE * SIZE];
    private int blockCount;

    private Mesh mesh;
    private int vertexCount = 0;
    private boolean dirty = true;
    // Khung hình gần nhất mà hộp bao chunk giao khung nhìn.
    long lastVisibleFrame;

    // Hệ số độ sáng theo hướng mặt (Directional Face Shading)
    private static final float SHADE_TOP = 1.00f;
    private static final float SHADE_BOTTOM = 0.55f;
    private static final float SHADE_NORTH_SOUTH = 0.85f;
    private static final float SHADE_EAST_WEST = 0.72f;

    /** Khởi tạo chunk theo chỉ số lưới và tính trước tọa độ dùng để dựng, kiểm tra mesh. */
    public Chunk(int chunkX, int chunkY, int chunkZ) {
        baseWorldX = chunkX * SIZE;
        baseWorldY = chunkY * SIZE;
        baseWorldZ = chunkZ * SIZE;
    }

    /** Tính chỉ số mảng phẳng với trục Z nằm liền kề trong bộ nhớ. */
    private static int blockIndex(int x, int y, int z) {
        return (x * SIZE + y) * SIZE + z;
    }

    /** Chuẩn bị bảng UV từ công thức Atlas dùng chung của các loại block. */
    private static float[][] createTileUvs() {
        float[][] tileUvs = new float[Texture.ATLAS_COLS * Texture.ATLAS_ROWS][];
        for (int tile = 0; tile < tileUvs.length; tile++) {
            tileUvs[tile] = Block.getTileUV(tile);
        }
        return tileUvs;
    }

    byte getBlockLocal(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            return 0;
        }
        return blocks[blockIndex(x, y, z)];
    }

    /** Ghi mã block theo tọa độ cục bộ hợp lệ và trả về trạng thái có thay đổi. */
    boolean setBlockLocal(int x, int y, int z, byte blockId) {
        int index = blockIndex(x, y, z);
        byte previous = blocks[index];
        if (previous == blockId) {
            return false;
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

    /** Kiểm tra chunk đã trở lại trạng thái chỉ chứa không khí hay chưa. */
    boolean isEmpty() {
        return blockCount == 0;
    }

    /** Giữ cả các chunk cắt qua rìa khung nhìn để tránh làm mất mặt block đang thấy. */
    boolean intersectsFrustum(Frustum frustum) {
        return frustum.boundsInFrustum(baseWorldX + HALF_SIZE, baseWorldY + HALF_SIZE, baseWorldZ + HALF_SIZE,
                HALF_SIZE, HALF_SIZE, HALF_SIZE);
    }

    /** Tính khoảng cách bình phương từ camera đến hộp bao để ưu tiên dựng mesh gần nhất. */
    float distanceSquaredTo(Vector3 point) {
        float dx = Math.max(0, Math.max(baseWorldX - point.x, point.x - (baseWorldX + SIZE)));
        float dy = Math.max(0, Math.max(baseWorldY - point.y, point.y - (baseWorldY + SIZE)));
        float dz = Math.max(0, Math.max(baseWorldZ - point.z, point.z - (baseWorldZ + SIZE)));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Cho biết chunk đang giữ tài nguyên mesh. */
    boolean hasMesh() {
        return mesh != null;
    }

    /** Thu hồi mesh ngoài khung nhìn và cho phép dựng lại từ dữ liệu block khi quay lại. */
    void releaseCachedMesh() {
        dispose();
        markDirty();
    }

    boolean isDirty() {
        return dirty;
    }

    void markDirty() {
        this.dirty = true;
    }

    /**
     * Xây dựng lại lưới đỉnh (Mesh Rebuilding) cho toàn bộ Chunk dựa trên đối tượng đa hình {@link Block}.
     * Chỉ cho phép gọi trong nội bộ package `com.voxelgame.engine`.
     *
     * @param world thế giới voxel cung cấp dữ liệu các khối lân cận
     * @param vertices bộ đệm đỉnh dùng chung, được xóa trước khi dựng chunk kế tiếp
     */
    void rebuildMesh(World world, FloatArray vertices) {
        vertices.clear();

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    byte blockId = blocks[blockIndex(x, y, z)];
                    if (blockId == 0) {
                        continue;
                    }

                    Block block = Block.fromId(blockId);
                    if (block == Block.AIR) {
                        continue;
                    }

                    int wx = baseWorldX + x;
                    int wy = baseWorldY + y;
                    int wz = baseWorldZ + z;

                    // Gọi đa hình chiều cao bề mặt khối (1.0f cho khối rắn, 0.88f cho mặt thoáng Nước/Lava)
                    float topHeight = block.getTopHeight(world, wx, wy, wz);

                    // 1. Mặt Trên (+Y)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx, wy + 1, wz)))) {
                        addTopFace(vertices, wx, wy, wz, topHeight, TILE_UVS[block.getTileIndex(Block.Face.TOP)]);
                    }

                    // 2. Mặt Dưới (-Y)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx, wy - 1, wz)))) {
                        addBottomFace(vertices, wx, wy, wz, TILE_UVS[block.getTileIndex(Block.Face.BOTTOM)]);
                    }

                    // 3. Mặt Trước / Nam (+Z)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx, wy, wz + 1)))) {
                        addFrontFace(vertices, wx, wy, wz, topHeight, TILE_UVS[block.getTileIndex(Block.Face.SOUTH)]);
                    }

                    // 4. Mặt Sau / Bắc (-Z)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx, wy, wz - 1)))) {
                        addBackFace(vertices, wx, wy, wz, topHeight, TILE_UVS[block.getTileIndex(Block.Face.NORTH)]);
                    }

                    // 5. Mặt Phải / Đông (+X)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx + 1, wy, wz)))) {
                        addRightFace(vertices, wx, wy, wz, topHeight, TILE_UVS[block.getTileIndex(Block.Face.EAST)]);
                    }

                    // 6. Mặt Trái / Tây (-X)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx - 1, wy, wz)))) {
                        addLeftFace(vertices, wx, wy, wz, topHeight, TILE_UVS[block.getTileIndex(Block.Face.WEST)]);
                    }
                }
            }
        }

        this.vertexCount = vertices.size / FLOATS_PER_VERTEX;

        if (vertexCount > 0) {
            if (mesh == null || mesh.getMaxVertices() < vertexCount) {
                dispose();
                mesh = new Mesh(true, vertexCount, 0, VERTEX_ATTRIBUTES);
            }
            mesh.setVertices(vertices.items, 0, vertices.size);
        } else {
            dispose();
        }

        this.dirty = false;
    }

    private boolean shouldRenderFace(Block current, Block neighbor) {
        if (current.isLiquid()) {
            return neighbor == Block.AIR;
        }
        return !neighbor.isSolid();
    }

    void render(ShaderProgram shader) {
        if (mesh != null && vertexCount > 0) {
            mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 0, vertexCount);
        }
    }

    @Override
    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
    }

    /** Ghi một đỉnh vào bộ đệm số thực theo đúng thứ tự thuộc tính của shader. */
    private void putVertex(FloatArray vertices, float x, float y, float z, float u, float v, float shade) {
        vertices.add(x, y, z);
        vertices.add(u, v, shade);
    }

    private void addTopFace(FloatArray v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x,     y + h, z + 1, u0, v1, SHADE_TOP);
        putVertex(v, x + 1, y + h, z + 1, u1, v1, SHADE_TOP);
        putVertex(v, x + 1, y + h, z,     u1, v0, SHADE_TOP);

        putVertex(v, x,     y + h, z + 1, u0, v1, SHADE_TOP);
        putVertex(v, x + 1, y + h, z,     u1, v0, SHADE_TOP);
        putVertex(v, x,     y + h, z,     u0, v0, SHADE_TOP);
    }

    private void addBottomFace(FloatArray v, float x, float y, float z, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x,     y, z,     u0, v1, SHADE_BOTTOM);
        putVertex(v, x + 1, y, z,     u1, v1, SHADE_BOTTOM);
        putVertex(v, x + 1, y, z + 1, u1, v0, SHADE_BOTTOM);

        putVertex(v, x,     y, z,     u0, v1, SHADE_BOTTOM);
        putVertex(v, x + 1, y, z + 1, u1, v0, SHADE_BOTTOM);
        putVertex(v, x,     y, z + 1, u0, v0, SHADE_BOTTOM);
    }

    private void addFrontFace(FloatArray v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x,     y,     z + 1, u0, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x + 1, y,     z + 1, u1, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x + 1, y + h, z + 1, u1, v0, SHADE_NORTH_SOUTH);

        putVertex(v, x,     y,     z + 1, u0, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x + 1, y + h, z + 1, u1, v0, SHADE_NORTH_SOUTH);
        putVertex(v, x,     y + h, z + 1, u0, v0, SHADE_NORTH_SOUTH);
    }

    private void addBackFace(FloatArray v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x + 1, y,     z, u0, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x,     y,     z, u1, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x,     y + h, z, u1, v0, SHADE_NORTH_SOUTH);

        putVertex(v, x + 1, y,     z, u0, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x,     y + h, z, u1, v0, SHADE_NORTH_SOUTH);
        putVertex(v, x + 1, y + h, z, u0, v0, SHADE_NORTH_SOUTH);
    }

    private void addRightFace(FloatArray v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x + 1, y,     z + 1, u0, v1, SHADE_EAST_WEST);
        putVertex(v, x + 1, y,     z,     u1, v1, SHADE_EAST_WEST);
        putVertex(v, x + 1, y + h, z,     u1, v0, SHADE_EAST_WEST);

        putVertex(v, x + 1, y,     z + 1, u0, v1, SHADE_EAST_WEST);
        putVertex(v, x + 1, y + h, z,     u1, v0, SHADE_EAST_WEST);
        putVertex(v, x + 1, y + h, z + 1, u0, v0, SHADE_EAST_WEST);
    }

    private void addLeftFace(FloatArray v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x, y,     z,     u0, v1, SHADE_EAST_WEST);
        putVertex(v, x, y,     z + 1, u1, v1, SHADE_EAST_WEST);
        putVertex(v, x, y + h, z + 1, u1, v0, SHADE_EAST_WEST);

        putVertex(v, x, y,     z,     u0, v1, SHADE_EAST_WEST);
        putVertex(v, x, y + h, z + 1, u1, v0, SHADE_EAST_WEST);
        putVertex(v, x, y + h, z,     u0, v0, SHADE_EAST_WEST);
    }
}
