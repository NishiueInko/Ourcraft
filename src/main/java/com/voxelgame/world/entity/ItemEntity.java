package com.voxelgame.world.entity;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import java.util.HashMap;
import java.util.Map;

/**
 * Thực thể Vật phẩm 3D nhỏ rơi ra ngoài thế giới sau khi người chơi đập vỡ Block hoặc tiêu diệt/tương tác Mob,
 * kế thừa từ {@link Entity}.
 */
public class ItemEntity extends Entity {

    /** Kích thước cạnh của vật phẩm thu nhỏ (0.28 block). */
    private static final float ITEM_SCALE = 0.28f;
    private static final float HALF_SIZE = ITEM_SCALE * 0.5f;

    /** Các hằng số vật lý của Item rơi. */
    private static final float ITEM_GRAVITY = -18.0f;
    private static final float DEFAULT_PICKUP_DELAY = 0.25f;
    private static final float MAGNET_DISTANCE = 1.85f;
    private static final float PICKUP_DISTANCE = 0.85f;
    private static final float MAX_LIFETIME = 300.0f;

    /** Bộ đệm cho tâm người chơi và hướng hút vật phẩm. */
    private final Vector3 pullDirection = new Vector3();

    private final Item item;
    private int count = 1;
    private float pickupDelay = DEFAULT_PICKUP_DELAY;
    private float age = 0.0f;
    private boolean collected = false;

    private final Matrix4 modelMatrix = new Matrix4();

    /** Bộ nhớ đệm lưu trữ lưới Mesh dùng chung cho từng loại Block và Item độc lập. */
    private static final Map<Block, Mesh> SHARED_MESHES = new HashMap<>();
    private static final Map<Item, Mesh> SHARED_ITEM_MESHES = new HashMap<>();

    /** Thuộc tính đỉnh của Mesh ItemEntity: aPos (3), aTexCoord (2), aShade (1). */
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade")
    );

    /**
     * Constructor 1: Tự động tạo vận tốc nảy nhẹ ngẫu nhiên khi khối vừa vỡ ra.
     */
    public ItemEntity(float startX, float startY, float startZ, Block block) {
        this(startX, startY, startZ, Item.of(block));
    }

    /**
     * Constructor 2: Tự động tạo vận tốc nảy nhẹ ngẫu nhiên cho một {@link Item} bất kỳ.
     */
    public ItemEntity(float startX, float startY, float startZ, Item item) {
        this(
                startX,
                startY,
                startZ,
                new Vector3(
                        (float) ((Math.random() - 0.5) * 1.4),
                        3.2f,
                        (float) ((Math.random() - 0.5) * 1.4)
                ),
                item
        );
    }

    /**
     * Constructor 3: Cho phép chỉ định vector vận tốc văng cụ thể cho Block.
     */
    public ItemEntity(float startX, float startY, float startZ, Vector3 initialVelocity, Block block) {
        this(startX, startY, startZ, initialVelocity, Item.of(block));
    }

    /**
     * Constructor 4: Cho phép chỉ định vector vận tốc văng cụ thể cho {@link Item}.
     */
    public ItemEntity(float startX, float startY, float startZ, Vector3 initialVelocity, Item item) {
        this(startX, startY, startZ, initialVelocity, item, 1, DEFAULT_PICKUP_DELAY);
    }

    /**
     * Constructor 5: Cho phép chỉ định đầy đủ vận tốc, số lượng stack và thời gian trễ trước khi nhặt.
     */
    public ItemEntity(float startX, float startY, float startZ, Vector3 initialVelocity,
                      Item item, int count, float pickupDelay) {
        super(startX, startY, startZ, HALF_SIZE, ITEM_SCALE);
        if (initialVelocity != null) {
            this.velocity.set(initialVelocity);
        }
        this.item = (item != null) ? item : Item.NONE;
        this.count = Math.max(1, count);
        this.pickupDelay = Math.max(0.0f, pickupDelay);
    }

    /**
     * Cập nhật vật lý rơi, hiệu ứng lực hút nam châm về phía {@link Player} và tự động cộng vào {@link Hotbar}.
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
     * Phương thức nạp chồng hỗ trợ cập nhật trực tiếp với đối tượng {@link Player} và {@link Hotbar}.
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
            Vector3 playerCenter = player.copyCenterPosition(pullDirection);
            float distSq = position.dst2(playerCenter);

            if (age >= pickupDelay && distSq <= MAGNET_DISTANCE * MAGNET_DISTANCE) {
                if (distSq <= PICKUP_DISTANCE * PICKUP_DISTANCE) {
                    int added = hotbar.addItemUpTo(item, count);
                    if (added > 0) {
                        count -= added;
                        if (count <= 0) {
                            count = 0;
                            collected = true;
                        }
                        AudioManager audio = world == null ? null : world.getAudio();
                        if (audio != null) {
                            audio.playEffect(AudioManager.Effect.PICKUP, 0.3f);
                        }
                        if (collected) {
                            return;
                        }
                    }
                }

                Vector3 pullDir = playerCenter.sub(position);
                if (pullDir.len2() > 0.0001f) {
                    pullDir.nor().scl(9.5f);
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

        // Tọa độ vẽ ở tâm vật phẩm, còn quét va chạm dùng đáy hộp.
        position.y -= HALF_SIZE;
        if (moveAxis(world, 0, velocity.x * deltaTime) != null) {
            velocity.x = 0.0f;
        }
        if (moveAxis(world, 2, velocity.z * deltaTime) != null) {
            velocity.z = 0.0f;
        }
        onGround = false;
        if (moveAxis(world, 1, velocity.y * deltaTime) != null) {
            onGround = velocity.y < 0;
            velocity.y = 0;
        }
        position.y += HALF_SIZE;
    }

    /**
     * Vẽ vật phẩm 3D thu nhỏ tại vị trí hiện tại kèm hiệu ứng tự xoay tròn và nhấp nhô.
     */
    @Override
    public void render(ShaderProgram shader) {
        if (collected || item == null || item.isNone() || count <= 0) {
            return;
        }

        float bobOffset = (float) Math.sin(age * 3.8f) * 0.07f + 0.08f;
        float rotationDegrees = age * 110.0f;

        if (item.isBlock()) {
            modelMatrix.idt()
                    .translate(position.x, position.y + bobOffset, position.z)
                    .rotate(Vector3.Y, rotationDegrees)
                    .scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            renderBlockMesh(shader, item.getBlock(), modelMatrix);
        } else {
            float scale = item.isCubeItem() ? ITEM_SCALE : 0.40f;
            modelMatrix.idt()
                    .translate(position.x, position.y + bobOffset, position.z)
                    .rotate(Vector3.Y, rotationDegrees)
                    .scale(scale, scale, scale);
            shader.setUniformMatrix4f("uModel", modelMatrix);
            getOrCreateItemMesh(item).render(shader.getGdxShader(), GL20.GL_TRIANGLES);
        }
    }

    /** Chia sẻ hình block giữa vật phẩm nhỏ và thực thể trọng lực kích thước thật. */
    public static void renderBlockMesh(ShaderProgram shader, Block block, Matrix4 model) {
        shader.setUniformMatrix4f("uModel", model);
        getOrCreateMesh(block).render(shader.getGdxShader(), GL20.GL_TRIANGLES);
    }

    public boolean isCollected() {
        return collected;
    }

    /** Trả về số lượng vật phẩm còn lại trong thực thể rơi này. */
    public int getCount() {
        return count;
    }

    /** Thiết lập số lượng vật phẩm trong thực thể rơi (tối thiểu 1). */
    public void setCount(int count) {
        this.count = Math.max(1, count);
    }

    /** Trả về thời gian chờ (giây) trước khi người chơi có thể nhặt vật phẩm này. */
    public float getPickupDelay() {
        return pickupDelay;
    }

    /** Thiết lập thời gian chờ (giây) trước khi người chơi có thể nhặt vật phẩm này. */
    public void setPickupDelay(float pickupDelay) {
        this.pickupDelay = Math.max(0.0f, pickupDelay);
    }

    /** Chuyển vật phẩm sang thực thể nhặt nếu đó là một Block, giữ tương thích cho các luật cũ. */
    public Block collectForMob() {
        if (collected || !item.isBlock()) {
            return null;
        }
        count--;
        if (count <= 0) {
            count = 0;
            collected = true;
        }
        return item.getBlock();
    }

    /** Chuyển vật phẩm bất kỳ (Block hoặc Item độc lập) sang thực thể nhặt. */
    public Item collectItemForMob() {
        if (collected || item.isNone()) {
            return null;
        }
        count--;
        if (count <= 0) {
            count = 0;
            collected = true;
        }
        return item;
    }

    public Item getItem() {
        return item;
    }

    public boolean isStandaloneItem() {
        return !item.isNone() && !item.isBlock();
    }

    public Block getBlock() {
        return item.getBlock();
    }

    public Block getBlockType() {
        return item.getBlock();
    }

    private static Mesh getOrCreateMesh(Block type) {
        return SHARED_MESHES.computeIfAbsent(type, t -> {
            if (t.getGeometryType() == Block.GeometryType.CROSS_PLANT) {
                return createPlantMesh(t);
            }
            float[] topUV = t.getFaceUV(Block.Face.TOP);
            float[] botUV = t.getFaceUV(Block.Face.BOTTOM);
            float[] sideUV = t.getFaceUV(Block.Face.NORTH);

            float[] vertices = createCubeVertices(topUV, botUV, sideUV);

            // Giữ mặt cắt của cactus và chiều cao mặc định của một lớp tuyết.
            Block.Bounds shape = t.getSelectionBounds(0);
            if (shape != null && shape != Block.FULL_BOUNDS) {
                for (int i = 0; i < vertices.length; i += 6) {
                    vertices[i] *= shape.maxX() - shape.minX();
                    vertices[i + 1] = (vertices[i + 1] + 0.5f) * (shape.maxY() - shape.minY()) - 0.5f;
                    vertices[i + 2] *= shape.maxZ() - shape.minZ();
                }
            }
            Mesh mesh = new Mesh(true, 36, 0, VERTEX_ATTRIBUTES);
            mesh.setVertices(vertices);
            return mesh;
        });
    }

    /** Dựng lưới dùng chung cho vật phẩm độc lập (khối lập phương mini với len, mặt phẳng 2 mặt với item 2D). */
    private static Mesh getOrCreateItemMesh(Item standaloneItem) {
        return SHARED_ITEM_MESHES.computeIfAbsent(standaloneItem, t -> {
            float[] uv = t.getTileUV();
            if (t.isCubeItem()) {
                float[] vertices = createCubeVertices(uv, uv, uv);
                Mesh mesh = new Mesh(true, 36, 0, VERTEX_ATTRIBUTES);
                mesh.setVertices(vertices);
                return mesh;
            }
            // Vật phẩm phẳng: hai mặt trước sau để nhìn rõ ở mọi góc xoay
            float[] vertices = new float[12 * 6];
            int idx = putFace(vertices, 0,
                    -0.5f, -0.5f, 0.0f, uv[0], uv[3],
                     0.5f, -0.5f, 0.0f, uv[2], uv[3],
                     0.5f,  0.5f, 0.0f, uv[2], uv[1],
                    -0.5f,  0.5f, 0.0f, uv[0], uv[1], 0.95f);
            putFace(vertices, idx,
                     0.5f, -0.5f, 0.0f, uv[0], uv[3],
                    -0.5f, -0.5f, 0.0f, uv[2], uv[3],
                    -0.5f,  0.5f, 0.0f, uv[2], uv[1],
                     0.5f,  0.5f, 0.0f, uv[0], uv[1], 0.85f);
            Mesh mesh = new Mesh(true, 12, 0, VERTEX_ATTRIBUTES);
            mesh.setVertices(vertices);
            return mesh;
        });
    }

    private static float[] createCubeVertices(float[] topUV, float[] botUV, float[] sideUV) {
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

        return vertices;
    }

    /** Dựng hai mặt chéo với mặt trước và mặt sau để cây hiện ở mọi góc xoay. */
    private static Mesh createPlantMesh(Block block) {
        float[] uv = block.getFaceUV(Block.Face.NORTH);
        float[] vertices = new float[24 * 6];
        int offset = plantPlane(vertices, 0, -0.35f, -0.35f, 0.35f, 0.35f, uv);
        offset = plantPlane(vertices, offset, 0.35f, 0.35f, -0.35f, -0.35f, uv);
        offset = plantPlane(vertices, offset, -0.35f, 0.35f, 0.35f, -0.35f, uv);
        plantPlane(vertices, offset, 0.35f, -0.35f, -0.35f, 0.35f, uv);
        Mesh mesh = new Mesh(true, 24, 0, VERTEX_ATTRIBUTES);
        mesh.setVertices(vertices);
        return mesh;
    }

    /** Ghi hai tam giác của một phía mặt phẳng thẳng đứng. */
    private static int plantPlane(float[] vertices, int offset, float x1, float z1, float x2, float z2, float[] uv) {
        return putFace(vertices, offset, x1, -0.5f, z1, uv[0], uv[3], x2, -0.5f, z2, uv[2], uv[3],
                x2, 0.5f, z2, uv[2], uv[1], x1, 0.5f, z1, uv[0], uv[1], 1.0f);
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
     * Giải phóng toàn bộ các Mesh dùng chung của ItemEntity khi đóng game.
     */
    public static void cleanupSharedMeshes() {
        for (Mesh mesh : SHARED_MESHES.values()) {
            if (mesh != null) {
                mesh.dispose();
            }
        }
        SHARED_MESHES.clear();
        for (Mesh mesh : SHARED_ITEM_MESHES.values()) {
            if (mesh != null) {
                mesh.dispose();
            }
        }
        SHARED_ITEM_MESHES.clear();
    }
}
