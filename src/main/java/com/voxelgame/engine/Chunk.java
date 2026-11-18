package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

/**
 * Quản lý một phân vùng không gian 16x16x16 Blocks (Chunk) và bộ đệm lưới đỉnh OpenGL (VAO/VBO),
 * triển khai giao diện {@link Cleanable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Đóng gói cấp Package (Package-Private Encapsulation):
 *    - Các phương thức thao tác nội bộ (`getBlockLocal`, `setBlockLocal`, `isDirty`, `markDirty`,
 *      `rebuildMesh`, `render`) được giới hạn ở mức truy cập **package-private** (không có `public`).
 *    - Nhờ đó, chỉ lớp điều phối `World` nằm cùng package `com.voxelgame.engine` mới được phép can thiệp
 *      vào mảng dữ liệu và quá trình tái tạo lưới đỉnh OpenGL của Chunk.
 * 2. Tính Đa hình (Polymorphism):
 *    - Trong `rebuildMesh(World)`, Chunk không cần kiểm tra thủ công loại khối là Nước hay Lava để hạ
 *      độ cao mặt thoáng, mà gọi trực tiếp phương thức đa hình `block.getTopHeight(world, wx, wy, wz)`
 *      và `block.getFaceUV(Block.Face.*)`.
 */
public class Chunk implements Cleanable {

    public static final int SIZE = 16;
    private static final int FLOATS_PER_VERTEX = 6; // [x, y, z, u, v, shade]

    private final int chunkX;
    private final int chunkY;
    private final int chunkZ;

    // Mảng 3D lưu trữ mã ID (1 byte/ô) của các Block trong Chunk
    private final byte[][][] blocks = new byte[SIZE][SIZE][SIZE];

    private final int vaoId;
    private final int vboId;
    private int vertexCount;
    private boolean dirty = true;

    // Hệ số độ sáng theo hướng mặt (Directional Face Shading)
    private static final float SHADE_TOP = 1.00f;
    private static final float SHADE_BOTTOM = 0.55f;
    private static final float SHADE_NORTH_SOUTH = 0.85f;
    private static final float SHADE_EAST_WEST = 0.72f;

    public Chunk(int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();
    }

    byte getBlockLocal(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            return 0;
        }
        return blocks[x][y][z];
    }

    void setBlockLocal(int x, int y, int z, byte blockId) {
        if (x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE) {
            blocks[x][y][z] = blockId;
            dirty = true;
        }
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
     */
    void rebuildMesh(World world) {
        List<Float> vertices = new ArrayList<>(4096);

        int baseWorldX = chunkX * SIZE;
        int baseWorldY = chunkY * SIZE;
        int baseWorldZ = chunkZ * SIZE;

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    byte blockId = blocks[x][y][z];
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
                        addTopFace(vertices, wx, wy, wz, topHeight, block.getFaceUV(Block.Face.TOP));
                    }

                    // 2. Mặt Dưới (-Y)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx, wy - 1, wz)))) {
                        addBottomFace(vertices, wx, wy, wz, block.getFaceUV(Block.Face.BOTTOM));
                    }

                    // 3. Mặt Trước / Nam (+Z)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx, wy, wz + 1)))) {
                        addFrontFace(vertices, wx, wy, wz, topHeight, block.getFaceUV(Block.Face.SOUTH));
                    }

                    // 4. Mặt Sau / Bắc (-Z)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx, wy, wz - 1)))) {
                        addBackFace(vertices, wx, wy, wz, topHeight, block.getFaceUV(Block.Face.NORTH));
                    }

                    // 5. Mặt Phải / Đông (+X)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx + 1, wy, wz)))) {
                        addRightFace(vertices, wx, wy, wz, topHeight, block.getFaceUV(Block.Face.EAST));
                    }

                    // 6. Mặt Trái / Tây (-X)
                    if (shouldRenderFace(block, Block.fromId(world.getBlock(wx - 1, wy, wz)))) {
                        addLeftFace(vertices, wx, wy, wz, topHeight, block.getFaceUV(Block.Face.WEST));
                    }
                }
            }
        }

        this.vertexCount = vertices.size() / FLOATS_PER_VERTEX;

        FloatBuffer vertexBuffer = MemoryUtil.memAllocFloat(vertices.size());
        for (int i = 0; i < vertices.size(); i++) {
            vertexBuffer.put(vertices.get(i));
        }
        vertexBuffer.flip();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW);

        int stride = FLOATS_PER_VERTEX * Float.BYTES;

        // Location 0: aPos (x, y, z)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);

        // Location 1: aTexCoord (u, v)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Location 2: aShade (shade)
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        MemoryUtil.memFree(vertexBuffer);
        this.dirty = false;
    }

    private boolean shouldRenderFace(Block current, Block neighbor) {
        if (current.isLiquid()) {
            return neighbor == Block.AIR;
        }
        return !neighbor.isSolid();
    }

    void render() {
        if (vertexCount <= 0) {
            return;
        }
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    @Override
    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }

    private void putVertex(List<Float> list, float x, float y, float z, float u, float v, float shade) {
        list.add(x);
        list.add(y);
        list.add(z);
        list.add(u);
        list.add(v);
        list.add(shade);
    }

    private void addTopFace(List<Float> v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x,     y + h, z + 1, u0, v1, SHADE_TOP);
        putVertex(v, x + 1, y + h, z + 1, u1, v1, SHADE_TOP);
        putVertex(v, x + 1, y + h, z,     u1, v0, SHADE_TOP);

        putVertex(v, x,     y + h, z + 1, u0, v1, SHADE_TOP);
        putVertex(v, x + 1, y + h, z,     u1, v0, SHADE_TOP);
        putVertex(v, x,     y + h, z,     u0, v0, SHADE_TOP);
    }

    private void addBottomFace(List<Float> v, float x, float y, float z, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x,     y, z,     u0, v1, SHADE_BOTTOM);
        putVertex(v, x + 1, y, z,     u1, v1, SHADE_BOTTOM);
        putVertex(v, x + 1, y, z + 1, u1, v0, SHADE_BOTTOM);

        putVertex(v, x,     y, z,     u0, v1, SHADE_BOTTOM);
        putVertex(v, x + 1, y, z + 1, u1, v0, SHADE_BOTTOM);
        putVertex(v, x,     y, z + 1, u0, v0, SHADE_BOTTOM);
    }

    private void addFrontFace(List<Float> v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x,     y,     z + 1, u0, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x + 1, y,     z + 1, u1, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x + 1, y + h, z + 1, u1, v0, SHADE_NORTH_SOUTH);

        putVertex(v, x,     y,     z + 1, u0, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x + 1, y + h, z + 1, u1, v0, SHADE_NORTH_SOUTH);
        putVertex(v, x,     y + h, z + 1, u0, v0, SHADE_NORTH_SOUTH);
    }

    private void addBackFace(List<Float> v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x + 1, y,     z, u0, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x,     y,     z, u1, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x,     y + h, z, u1, v0, SHADE_NORTH_SOUTH);

        putVertex(v, x + 1, y,     z, u0, v1, SHADE_NORTH_SOUTH);
        putVertex(v, x,     y + h, z, u1, v0, SHADE_NORTH_SOUTH);
        putVertex(v, x + 1, y + h, z, u0, v0, SHADE_NORTH_SOUTH);
    }

    private void addRightFace(List<Float> v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x + 1, y,     z + 1, u0, v1, SHADE_EAST_WEST);
        putVertex(v, x + 1, y,     z,     u1, v1, SHADE_EAST_WEST);
        putVertex(v, x + 1, y + h, z,     u1, v0, SHADE_EAST_WEST);

        putVertex(v, x + 1, y,     z + 1, u0, v1, SHADE_EAST_WEST);
        putVertex(v, x + 1, y + h, z,     u1, v0, SHADE_EAST_WEST);
        putVertex(v, x + 1, y + h, z + 1, u0, v0, SHADE_EAST_WEST);
    }

    private void addLeftFace(List<Float> v, float x, float y, float z, float h, float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        putVertex(v, x, y,     z,     u0, v1, SHADE_EAST_WEST);
        putVertex(v, x, y,     z + 1, u1, v1, SHADE_EAST_WEST);
        putVertex(v, x, y + h, z + 1, u1, v0, SHADE_EAST_WEST);

        putVertex(v, x, y,     z,     u0, v1, SHADE_EAST_WEST);
        putVertex(v, x, y + h, z + 1, u1, v0, SHADE_EAST_WEST);
        putVertex(v, x, y + h, z,     u0, v0, SHADE_EAST_WEST);
    }
}