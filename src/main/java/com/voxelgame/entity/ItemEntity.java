package com.voxelgame.entity;

import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

/**
 * Thực thể Vật phẩm 3D nhỏ rơi ra ngoài thế giới sau khi người chơi đập vỡ một Block, kế thừa từ {@link Entity}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Kế thừa (Inheritance): Kế thừa `position`, `velocity`, `halfWidth`, `height`, `onGround` từ `Entity`.
 * 2. Đa hình lúc biên dịch (Method Overloading): Cung cấp 2 constructor nạp chồng — một tự sinh lực văng
 *    ngẫu nhiên và một nhận vector vận tốc khởi tạo tùy chỉnh.
 * 3. Đa hình lúc chạy (Method Overriding): Ghi đè `update(World, float)` và `render(ShaderProgram)` của `Entity`.
 * 4. Đóng gói & Quản lý bộ nhớ GPU: Sử dụng `Map<Block, MeshData> SHARED_MESHES = new HashMap<>()` để dùng chung
 *    VAO/VBO cho từng loại `Block` thay vì cấp phát trùng lặp trên VRAM.
 */
public class ItemEntity extends Entity {

    // Kích thước cạnh của khối Item thu nhỏ (0.28 block ~ bằng 1/3 kích thước khối thật)
    private static final float ITEM_SCALE = 0.28f;
    private static final float HALF_SIZE = ITEM_SCALE * 0.5f;

    // Các hằng số vật lý của Item rơi
    private static final float ITEM_GRAVITY = -18.0f;
    private static final float PICKUP_DELAY = 0.25f;
    private static final float MAGNET_DISTANCE = 1.85f;
    private static final float PICKUP_DISTANCE = 0.85f;
    private static final float MAX_LIFETIME = 300.0f;

    private final Block block;
    private float age = 0.0f;
    private boolean collected = false;

    private final Matrix4f modelMatrix = new Matrix4f();

    // Bộ nhớ đệm lưu trữ lưới đa giác (VAO/VBO) dùng chung cho từng thực thể Block (thay thế EnumMap bằng HashMap)
    private static final Map<Block, MeshData> SHARED_MESHES = new HashMap<>();

    private static class MeshData {
        final int vaoId;
        final int vboId;
        final int vertexCount;

        MeshData(int vaoId, int vboId, int vertexCount) {
            this.vaoId = vaoId;
            this.vboId = vboId;
            this.vertexCount = vertexCount;
        }
    }

    /**
     * Constructor 1: Tự động tạo vận tốc nảy nhẹ ngẫu nhiên khi khối vừa vỡ ra.
     */
    public ItemEntity(float startX, float startY, float startZ, Block block) {
        this(
                startX,
                startY,
                startZ,
                new Vector3f(
                        (float) ((Math.random() - 0.5) * 1.4),
                        3.2f,
                        (float) ((Math.random() - 0.5) * 1.4)
                ),
                block
        );
    }

    /**
     * Constructor 2 (Overloaded Constructor): Cho phép chỉ định vector vận tốc văng cụ thể.
     */
    public ItemEntity(float startX, float startY, float startZ, Vector3f initialVelocity, Block block) {
        super(startX, startY, startZ, HALF_SIZE, ITEM_SCALE);
        if (initialVelocity != null) {
            this.velocity.set(initialVelocity);
        }
        this.block = (block != null) ? block : Block.AIR;
    }

    /**
     * Ghi đè phương thức trừu tượng `update` của `Entity`:
     * Cập nhật vật lý rơi, hiệu ứng lực hút nam châm về phía `Player` và tự động cộng vào `Hotbar`.
     */
    @Override
    public void update(World world, float deltaTime) {
        if (world == null) {
            return;
        }
        Player player = world.getPlayer();
        Hotbar hotbar = (player != null) ? player.getHotbar() : null;
        update(world, player, hotbar, deltaTime);
    }

    /**
     * Phương thức nạp chồng hỗ trợ cập nhật trực tiếp với đối tượng `Player` và `Hotbar`.
     */
    public void update(World world, Player player, Hotbar hotbar, float deltaTime) {
        if (collected) {
            return;
        }

        age += deltaTime;
        if (age >= MAX_LIFETIME) {
            collected = true;
            return;
        }

        // 1. Kiểm tra khoảng cách tới tâm cơ thể người chơi để kích hoạt lực hút nam châm
        if (player != null && hotbar != null) {
            Vector3f playerCenter = player.getCenterPosition();
            float distSq = position.distanceSquared(playerCenter);

            if (age >= PICKUP_DELAY && distSq <= MAGNET_DISTANCE * MAGNET_DISTANCE) {
                if (distSq <= PICKUP_DISTANCE * PICKUP_DISTANCE) {
                    if (hotbar.addItem(block, 1)) {
                        collected = true;
                        return;
                    }
                }

                Vector3f pullDir = new Vector3f(playerCenter).sub(position);
                if (pullDir.lengthSquared() > 0.0001f) {
                    pullDir.normalize().mul(9.5f);
                    position.add(pullDir.x * deltaTime, pullDir.y * deltaTime, pullDir.z * deltaTime);
                    return;
                }
            }
        }

        // 2. Áp dụng trọng lực kéo xuống và ma sát ngang
        velocity.y += ITEM_GRAVITY * deltaTime;
        if (velocity.y < -25.0f) {
            velocity.y = -25.0f;
        }

        float damping = onGround ? 0.82f : 0.96f;
        velocity.x *= damping;
        velocity.z *= damping;

        // 3. Cập nhật tọa độ và kiểm tra va chạm với các Block rắn trong World
        position.x += velocity.x * deltaTime;
        if (world.isSolidBlock((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z))) {
            position.x -= velocity.x * deltaTime;
            velocity.x = 0.0f;
        }

        position.z += velocity.z * deltaTime;
        if (world.isSolidBlock((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z))) {
            position.z -= velocity.z * deltaTime;
            velocity.z = 0.0f;
        }

        onGround = false;
        position.y += velocity.y * deltaTime;

        int blockX = (int) Math.floor(position.x);
        int blockYBelow = (int) Math.floor(position.y - HALF_SIZE);
        int blockZ = (int) Math.floor(position.z);

        if (velocity.y <= 0.0f && world.isSolidBlock(blockX, blockYBelow, blockZ)) {
            position.y = blockYBelow + 1.0f + HALF_SIZE;
            velocity.y = 0.0f;
            onGround = true;
        }
    }

    /**
     * Ghi đè phương thức trừu tượng `render` của `Entity`:
     * Vẽ khối 3D thu nhỏ của Item tại vị trí hiện tại kèm hiệu ứng tự xoay tròn và nhấp nhô.
     */
    @Override
    public void render(ShaderProgram shader) {
        if (collected || block == null || block == Block.AIR) {
            return;
        }

        MeshData mesh = getOrCreateMesh(block);

        float bobOffset = (float) Math.sin(age * 3.8f) * 0.07f + 0.08f;
        float rotationRadians = (float) Math.toRadians(age * 110.0f);

        modelMatrix.identity()
                .translate(position.x, position.y + bobOffset, position.z)
                .rotateY(rotationRadians)
                .scale(ITEM_SCALE);

        shader.setUniformMatrix4f("uModel", modelMatrix);

        glBindVertexArray(mesh.vaoId);
        glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
        glBindVertexArray(0);
    }

    public boolean isCollected() {
        return collected;
    }

    public Block getBlock() {
        return block;
    }

    public Block getBlockType() {
        return block;
    }

    private static MeshData getOrCreateMesh(Block type) {
        return SHARED_MESHES.computeIfAbsent(type, t -> {
            float[] topUV = t.getFaceUV(Block.Face.TOP);
            float[] botUV = t.getFaceUV(Block.Face.BOTTOM);
            float[] sideUV = t.getFaceUV(Block.Face.NORTH);

            float[] vertices = new float[216];
            int idx = 0;

            // 1. Mặt Trên (+Y)
            idx = putFace(vertices, idx,
                    -0.5f,  0.5f,  0.5f, topUV[0], topUV[3],
                     0.5f,  0.5f,  0.5f, topUV[2], topUV[3],
                     0.5f,  0.5f, -0.5f, topUV[2], topUV[1],
                    -0.5f,  0.5f, -0.5f, topUV[0], topUV[1], 1.0f);

            // 2. Mặt Dưới (-Y)
            idx = putFace(vertices, idx,
                    -0.5f, -0.5f, -0.5f, botUV[0], botUV[3],
                     0.5f, -0.5f, -0.5f, botUV[2], botUV[3],
                     0.5f, -0.5f,  0.5f, botUV[2], botUV[1],
                    -0.5f, -0.5f,  0.5f, botUV[0], botUV[1], 0.55f);

            // 3. Mặt Trước (+Z)
            idx = putFace(vertices, idx,
                    -0.5f, -0.5f,  0.5f, sideUV[0], sideUV[3],
                     0.5f, -0.5f,  0.5f, sideUV[2], sideUV[3],
                     0.5f,  0.5f,  0.5f, sideUV[2], sideUV[1],
                    -0.5f,  0.5f,  0.5f, sideUV[0], sideUV[1], 0.85f);

            // 4. Mặt Sau (-Z)
            idx = putFace(vertices, idx,
                     0.5f, -0.5f, -0.5f, sideUV[0], sideUV[3],
                    -0.5f, -0.5f, -0.5f, sideUV[2], sideUV[3],
                    -0.5f,  0.5f, -0.5f, sideUV[2], sideUV[1],
                     0.5f,  0.5f, -0.5f, sideUV[0], sideUV[1], 0.85f);

            // 5. Mặt Phải (+X)
            idx = putFace(vertices, idx,
                     0.5f, -0.5f,  0.5f, sideUV[0], sideUV[3],
                     0.5f, -0.5f, -0.5f, sideUV[2], sideUV[3],
                     0.5f,  0.5f, -0.5f, sideUV[2], sideUV[1],
                     0.5f,  0.5f,  0.5f, sideUV[0], sideUV[1], 0.72f);

            // 6. Mặt Trái (-X)
            putFace(vertices, idx,
                    -0.5f, -0.5f, -0.5f, sideUV[0], sideUV[3],
                    -0.5f, -0.5f,  0.5f, sideUV[2], sideUV[3],
                    -0.5f,  0.5f,  0.5f, sideUV[2], sideUV[1],
                    -0.5f,  0.5f, -0.5f, sideUV[0], sideUV[1], 0.72f);

            FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
            buffer.put(vertices).flip();

            int vao = glGenVertexArrays();
            int vbo = glGenBuffers();

            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

            int stride = 6 * Float.BYTES;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
            glEnableVertexAttribArray(2);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
            MemoryUtil.memFree(buffer);

            return new MeshData(vao, vbo, 36);
        });
    }

    private static int putFace(
            float[] arr, int offset,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float x4, float y4, float z4, float u4, float v4,
            float shade
    ) {
        offset = putVert(arr, offset, x1, y1, z1, u1, v1, shade);
        offset = putVert(arr, offset, x2, y2, z2, u2, v2, shade);
        offset = putVert(arr, offset, x3, y3, z3, u3, v3, shade);

        offset = putVert(arr, offset, x1, y1, z1, u1, v1, shade);
        offset = putVert(arr, offset, x3, y3, z3, u3, v3, shade);
        offset = putVert(arr, offset, x4, y4, z4, u4, v4, shade);
        return offset;
    }

    private static int putVert(float[] arr, int offset, float x, float y, float z, float u, float v, float shade) {
        arr[offset++] = x;
        arr[offset++] = y;
        arr[offset++] = z;
        arr[offset++] = u;
        arr[offset++] = v;
        arr[offset++] = shade;
        return offset;
    }

    /**
     * Giải phóng toàn bộ các VAO/VBO dùng chung của ItemEntity khi đóng game.
     */
    public static void cleanupSharedMeshes() {
        for (MeshData mesh : SHARED_MESHES.values()) {
            glDeleteBuffers(mesh.vboId);
            glDeleteVertexArrays(mesh.vaoId);
        }
        SHARED_MESHES.clear();
    }
}