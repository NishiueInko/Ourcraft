package com.voxelgame.engine;

import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quản lý toàn bộ thế giới Voxel, thuật toán Sinh Địa Hình Tự Nhiên (Procedural World Generation)
 * và danh sách đa hình các Thực thể động (`List<Entity> entities`), triển khai {@link Disposable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Đa hình (Runtime Polymorphism):
 *    - Quản lý chung cả `Player` lẫn mọi `ItemEntity` trong một danh sách duy nhất `List<Entity> entities`.
 *    - Trong `updateEntities` và `render`, `World` duyệt qua
 *      danh sách `entities` và gọi đa hình `entity.update(this, deltaTime)` và `entity.render(shader)`.
 *    - Trong `breakBlockAndSpawnDrop(int, int, int)`, `World` gọi đa hình `target.onBreak(this, worldX, worldY, worldZ)`
 *      để mỗi loại `Block` tự quyết định vật phẩm rơi ra (ví dụ: `GrassBlock` rơi ra `Block.DIRT`).
 * 2. Tính Đóng gói (Encapsulation):
 *    - Đóng gói mảng `chunks`, `surfaceHeights` và danh sách `entities` ở phạm vi `private final`,
 *      chỉ cung cấp các phương thức giao tiếp an toàn (`registerPlayer`, `spawnDroppedItem`, `getBlock`, `setBlock`).
 */
public class World implements Disposable {

    // Kích thước thế giới: 112 × 16 × 112 chunk, tương ứng 1792 × 256 × 1792 block.
    public static final int WORLD_CHUNKS_X = 112;
    public static final int WORLD_CHUNKS_Y = 16;
    public static final int WORLD_CHUNKS_Z = 112;

    public static final int WORLD_SIZE_X = WORLD_CHUNKS_X * Chunk.SIZE;
    public static final int WORLD_SIZE_Y = WORLD_CHUNKS_Y * Chunk.SIZE;
    public static final int WORLD_SIZE_Z = WORLD_CHUNKS_Z * Chunk.SIZE;

    // Tâm hình học của bản đồ theo phương ngang, dùng chung cho spawn và hồi vị trí.
    public static final int CENTER_X = WORLD_SIZE_X / 2;
    public static final int CENTER_Z = WORLD_SIZE_Z / 2;
    // Dựng sẵn khu vực 5 × 5 cột chunk quanh spawn.
    private static final int SPAWN_MESH_RADIUS = 2;
    // Giới hạn công việc dựng mesh mỗi khung hình và thời gian giữ mesh ngoài khung nhìn.
    private static final int MESH_BUILDS_PER_FRAME = 16;
    private static final int MESH_RETENTION_FRAMES = 120;

    // Giới hạn nghiêm ngặt: Tổng số lớp địa hình từ đáy (y = 0) đến đỉnh cao nhất <= 20 lớp (tức y <= 19)
    public static final int MAX_TERRAIN_LAYERS = 20;
    public static final int MAX_TERRAIN_Y = MAX_TERRAIN_LAYERS - 1; // y = 19

    // Một ô null biểu thị chunk chỉ chứa không khí, chưa cần mảng block hay mesh.
    private final Chunk[][][] chunks = new Chunk[WORLD_CHUNKS_X][WORLD_CHUNKS_Y][WORLD_CHUNKS_Z];
    private final int[][] surfaceHeights = new int[WORLD_SIZE_X][WORLD_SIZE_Z];

    // Tham chiếu tới thực thể người chơi và danh sách đa hình chứa mọi Entity hoạt động trong thế giới
    private Player player;
    private final List<Entity> entities = new ArrayList<>();

    private final Matrix4 identityMatrix = new Matrix4();
    // Khung nhìn và bộ đệm dựng mesh được tái sử dụng trên luồng kết xuất.
    private final Matrix4 inverseViewProjection = new Matrix4();
    private final Frustum frustum = new Frustum();
    private final FloatArray meshVertices = new FloatArray(4096);
    private final Vector3 nearCenter = new Vector3();
    // Hàng đợi nhỏ tái sử dụng để ưu tiên các chunk gần camera.
    private final Chunk[] pendingMeshes = new Chunk[MESH_BUILDS_PER_FRAME];
    private final float[] pendingDistances = new float[MESH_BUILDS_PER_FRAME];
    private int pendingMeshCount;
    // Theo dõi riêng các chunk có mesh để thu hồi tài nguyên mà không quét cả lưới lần nữa.
    private final Array<Chunk> residentMeshes = new Array<>(false, WORLD_CHUNKS_X * WORLD_CHUNKS_Z);
    private long renderFrame;
    private final int spawnY;

    /** Sinh địa hình và dựng trước mesh quanh điểm xuất phát. */
    public World() {
        generateTerrainAndEcosystem();
        // Tâm nằm trên giao điểm bốn cột block. Lấy cột cao nhất dưới chân người chơi.
        int groundY = 0;
        for (int x = CENTER_X - 1; x <= CENTER_X; x++) {
            for (int z = CENTER_Z - 1; z <= CENTER_Z; z++) {
                groundY = Math.max(groundY, findHighestBlockY(x, z));
            }
        }
        spawnY = groundY + 1;

        int spawnChunkX = CENTER_X / Chunk.SIZE;
        int spawnChunkZ = CENTER_Z / Chunk.SIZE;
        for (int cx = spawnChunkX - SPAWN_MESH_RADIUS; cx <= spawnChunkX + SPAWN_MESH_RADIUS; cx++) {
            for (int cz = spawnChunkZ - SPAWN_MESH_RADIUS; cz <= spawnChunkZ + SPAWN_MESH_RADIUS; cz++) {
                for (int cy = 0; cy < WORLD_CHUNKS_Y; cy++) {
                    Chunk chunk = chunks[cx][cy][cz];
                    if (chunk != null) {
                        rebuildChunkMesh(chunk);
                    }
                }
            }
        }
    }

    /** Trả về vị trí chân người chơi ở tâm bản đồ, ngay trên mặt đất khi tạo thế giới. */
    public Vector3 getSpawnPosition() {
        return new Vector3(CENTER_X, spawnY, CENTER_Z);
    }

    /**
     * Đăng ký thực thể `Player` vào thế giới và đưa vào danh sách cập nhật đa hình `List<Entity>`.
     *
     * @param player thực thể người chơi
     */
    public void registerPlayer(Player player) {
        this.player = player;
        if (player != null && !entities.contains(player)) {
            entities.add(player);
        }
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Thêm một thực thể vật phẩm rơi (`ItemEntity`) vào danh sách đa hình `List<Entity>`.
     *
     * @param item vật phẩm rơi
     */
    public void spawnDroppedItem(ItemEntity item) {
        if (item != null) {
            entities.add(item);
        }
    }

    /**
     * Cập nhật đa hình toàn bộ các thực thể (`Player` và `ItemEntity`) trong thế giới
     * và tự động dọn dẹp các `ItemEntity` đã được nhặt vào Hotbar.
     *
     * @param deltaTime khoảng thời gian giữa hai khung hình (giây)
     */
    public void updateEntities(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            entity.update(this, deltaTime);

            if (entity instanceof ItemEntity item && item.isCollected()) {
                entities.remove(i);
                i--;
            }
        }
    }

    /**
     * Phương thức tương thích hỗ trợ cập nhật riêng các vật phẩm rơi nếu cần gọi tường minh.
     */
    public void updateDroppedItems(Player player, Hotbar hotbar, float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (entity instanceof ItemEntity item) {
                item.update(this, player, hotbar, deltaTime);
                if (item.isCollected()) {
                    entities.remove(i);
                    i--;
                }
            }
        }
    }

    private void generateTerrainAndEcosystem() {
        Random rng = new Random(20261114L);

        // GIAI ĐOẠN 1: PHÂN TẦNG ĐỊA CHẤT (BEDROCK -> STONE -> DIRT -> GRASS)
        for (int wx = 0; wx < WORLD_SIZE_X; wx++) {
            for (int wz = 0; wz < WORLD_SIZE_Z; wz++) {
                double wave1 = Math.sin(wx * 0.085) * Math.cos(wz * 0.085) * 2.2;
                double wave2 = Math.sin((wx + wz) * 0.05) * 1.4;
                int surfaceY = (int) Math.round(10.0 + wave1 + wave2);

                surfaceY = Math.max(5, Math.min(MAX_TERRAIN_Y, surfaceY));
                surfaceHeights[wx][wz] = surfaceY;

                int dirtThickness = 2 + ((wx * 13 + wz * 7) & 1);
                int stoneTopY = Math.max(1, surfaceY - dirtThickness);

                for (int wy = 0; wy <= surfaceY; wy++) {
                    if (wy == 0) {
                        setBlockInternal(wx, wy, wz, Block.BEDROCK.getId());
                    } else if (wy < stoneTopY) {
                        setBlockInternal(wx, wy, wz, Block.STONE.getId());
                    } else if (wy < surfaceY) {
                        setBlockInternal(wx, wy, wz, Block.DIRT.getId());
                    } else {
                        setBlockInternal(wx, wy, wz, Block.GRASS.getId());
                    }
                }
            }
        }

        // GIAI ĐOẠN 2: TẠO HỒ NƯỚC (WATER LAKES) & HỒ DUNG NHAM (LAVA POOLS)
        carveLiquidLake(22, 24, 6.5f, 2, Block.WATER, Block.DIRT);
        carveLiquidLake(52, 28, 7.5f, 2, Block.WATER, Block.DIRT);
        carveLiquidLake(26, 62, 7.0f, 2, Block.WATER, Block.DIRT);
        carveLiquidLake(68, 66, 8.0f, 3, Block.WATER, Block.DIRT);

        carveLiquidLake(42, 20, 4.8f, 2, Block.LAVA, Block.STONE);
        carveLiquidLake(20, 44, 4.5f, 2, Block.LAVA, Block.STONE);
        carveLiquidLake(64, 46, 5.2f, 2, Block.LAVA, Block.STONE);
        carveLiquidLake(46, 72, 5.0f, 2, Block.LAVA, Block.STONE);

        // GIAI ĐOẠN 3: TRỒNG CÂY SỒI TỰ NHIÊN (OAK TREES) TRÊN CÁC KHỐI CỎ
        for (int wx = 6; wx < WORLD_SIZE_X - 6; wx += 4) {
            for (int wz = 6; wz < WORLD_SIZE_Z - 6; wz += 4) {
                int tx = wx + rng.nextInt(3) - 1;
                int tz = wz + rng.nextInt(3) - 1;

                if (Math.abs(tx - CENTER_X) <= 4 && Math.abs(tz - CENTER_Z) <= 4) {
                    continue;
                }

                if (rng.nextInt(100) < 28) {
                    int groundY = findHighestBlockY(tx, tz);
                    if (groundY >= 4 && groundY <= MAX_TERRAIN_Y &&
                        getBlock(tx, groundY, tz) == Block.GRASS.getId() &&
                        !hasLiquidNearby(tx, groundY, tz, 2)) {

                        int trunkHeight = 4 + rng.nextInt(3);
                        generateOakTree(tx, groundY + 1, tz, trunkHeight, rng);
                    }
                }
            }
        }
    }

    private void carveLiquidLake(
            int centerX,
            int centerZ,
            float radius,
            int depth,
            Block liquidType,
            Block basinBlockType
    ) {
        int liquidLevel = Integer.MAX_VALUE;
        int rCeil = (int) Math.ceil(radius + 1);

        for (int x = centerX - rCeil; x <= centerX + rCeil; x++) {
            for (int z = centerZ - rCeil; z <= centerZ + rCeil; z++) {
                if (isInsideWorld(x, 1, z)) {
                    float dx = x - centerX;
                    float dz = z - centerZ;
                    if (dx * dx + dz * dz <= radius * radius) {
                        liquidLevel = Math.min(liquidLevel, surfaceHeights[x][z]);
                    }
                }
            }
        }

        liquidLevel = Math.max(4, Math.min(MAX_TERRAIN_Y - 1, liquidLevel));

        for (int x = centerX - rCeil - 1; x <= centerX + rCeil + 1; x++) {
            for (int z = centerZ - rCeil - 1; z <= centerZ + rCeil + 1; z++) {
                if (!isInsideWorld(x, 1, z)) {
                    continue;
                }

                float dx = x - centerX;
                float dz = z - centerZ;
                float waveRadius = radius + (float) (Math.sin(x * 0.7) * Math.cos(z * 0.7) * 0.85);
                float dist = (float) Math.sqrt(dx * dx + dz * dz);

                if (dist <= waveRadius) {
                    float normalizedDist = dist / Math.max(1.0f, waveRadius);
                    int localDepth = Math.max(1, Math.round(depth * (1.0f - normalizedDist * 0.65f)));
                    int bottomY = Math.max(2, liquidLevel - localDepth + 1);

                    for (int y = liquidLevel + 1; y <= MAX_TERRAIN_Y + 4; y++) {
                        setBlockInternal(x, y, z, Block.AIR.getId());
                    }

                    for (int y = bottomY; y <= liquidLevel; y++) {
                        setBlockInternal(x, y, z, liquidType.getId());
                    }

                    if (bottomY - 1 > 0) {
                        setBlockInternal(x, bottomY - 1, z, basinBlockType.getId());
                    }
                    surfaceHeights[x][z] = liquidLevel;
                } else if (dist <= waveRadius + 1.3f) {
                    for (int y = 1; y <= liquidLevel; y++) {
                        byte existing = getBlock(x, y, z);
                        if (existing == Block.AIR.getId()) {
                            Block topShore = (liquidType == Block.LAVA)
                                    ? Block.STONE
                                    : (y == liquidLevel ? Block.GRASS : Block.DIRT);
                            setBlockInternal(x, y, z, topShore.getId());
                        }
                    }
                    if (surfaceHeights[x][z] > liquidLevel + 1) {
                        for (int y = liquidLevel + 2; y <= surfaceHeights[x][z]; y++) {
                            setBlockInternal(x, y, z, Block.AIR.getId());
                        }
                        setBlockInternal(x, liquidLevel + 1, z,
                                (liquidType == Block.LAVA) ? Block.STONE.getId() : Block.GRASS.getId());
                        surfaceHeights[x][z] = liquidLevel + 1;
                    }
                }
            }
        }
    }

    private void generateOakTree(int baseX, int baseY, int baseZ, int trunkHeight, Random rng) {
        if (baseY - 1 > 0) {
            setBlockInternal(baseX, baseY - 1, baseZ, Block.DIRT.getId());
        }

        int topTrunkY = Math.min(WORLD_SIZE_Y - 3, baseY + trunkHeight - 1);

        int leafStart = topTrunkY - 2;
        int leafEnd = topTrunkY + 1;

        for (int ly = leafStart; ly <= leafEnd; ly++) {
            int dy = ly - topTrunkY;
            int radius = (dy <= -1) ? 2 : 1;

            for (int lx = baseX - radius; lx <= baseX + radius; lx++) {
                for (int lz = baseZ - radius; lz <= baseZ + radius; lz++) {
                    int distX = Math.abs(lx - baseX);
                    int distZ = Math.abs(lz - baseZ);

                    if (distX == radius && distZ == radius) {
                        if (dy == 1 || rng.nextBoolean()) {
                            continue;
                        }
                    }

                    if (isInsideWorld(lx, ly, lz) && getBlock(lx, ly, lz) == Block.AIR.getId()) {
                        setBlockInternal(lx, ly, lz, Block.OAK_LEAVES.getId());
                    }
                }
            }
        }

        for (int y = baseY; y <= topTrunkY; y++) {
            if (isInsideWorld(baseX, y, baseZ)) {
                setBlockInternal(baseX, y, baseZ, Block.OAK_LOG.getId());
            }
        }
    }

    /** Tìm mặt đất khi sinh cây, bỏ qua các tầng chunk còn trống phía trên. */
    private int findHighestBlockY(int wx, int wz) {
        int cx = wx / Chunk.SIZE;
        int cz = wz / Chunk.SIZE;
        int lx = wx % Chunk.SIZE;
        int lz = wz % Chunk.SIZE;
        for (int cy = WORLD_CHUNKS_Y - 1; cy >= 0; cy--) {
            Chunk chunk = chunks[cx][cy][cz];
            if (chunk == null) {
                continue;
            }
            for (int y = Chunk.SIZE - 1; y >= 0; y--) {
                if (chunk.getBlockLocal(lx, y, lz) != Block.AIR.getId()) {
                    return cy * Chunk.SIZE + y;
                }
            }
        }
        return 0;
    }

    private boolean hasLiquidNearby(int wx, int wy, int wz, int radius) {
        for (int x = wx - radius; x <= wx + radius; x++) {
            for (int z = wz - radius; z <= wz + radius; z++) {
                for (int y = wy - 1; y <= wy + 1; y++) {
                    Block t = Block.fromId(getBlock(x, y, z));
                    if (t.isLiquid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ghi block và cấp phát hoặc giải phóng chunk khi nó chuyển trạng thái rỗng. */
    private boolean setBlockInternal(int worldX, int worldY, int worldZ, byte blockId) {
        if (!isInsideWorld(worldX, worldY, worldZ)) {
            return false;
        }
        int cx = worldX / Chunk.SIZE;
        int cy = worldY / Chunk.SIZE;
        int cz = worldZ / Chunk.SIZE;

        int lx = worldX % Chunk.SIZE;
        int ly = worldY % Chunk.SIZE;
        int lz = worldZ % Chunk.SIZE;

        Chunk chunk = chunks[cx][cy][cz];
        if (chunk == null) {
            if (blockId == Block.AIR.getId()) {
                return false;
            }
            chunk = new Chunk(cx, cy, cz);
            chunks[cx][cy][cz] = chunk;
        }
        if (!chunk.setBlockLocal(lx, ly, lz, blockId)) {
            return false;
        }
        if (chunk.isEmpty()) {
            if (chunk.hasMesh()) {
                residentMeshes.removeValue(chunk, true);
            }
            chunk.dispose();
            chunks[cx][cy][cz] = null;
        }
        return true;
    }

    public boolean isInsideWorld(int worldX, int worldY, int worldZ) {
        return worldX >= 0 && worldX < WORLD_SIZE_X &&
               worldY >= 0 && worldY < WORLD_SIZE_Y &&
               worldZ >= 0 && worldZ < WORLD_SIZE_Z;
    }

    public byte getBlock(int worldX, int worldY, int worldZ) {
        if (!isInsideWorld(worldX, worldY, worldZ)) {
            return 0;
        }
        int cx = worldX / Chunk.SIZE;
        int cy = worldY / Chunk.SIZE;
        int cz = worldZ / Chunk.SIZE;

        int lx = worldX % Chunk.SIZE;
        int ly = worldY % Chunk.SIZE;
        int lz = worldZ % Chunk.SIZE;

        Chunk chunk = chunks[cx][cy][cz];
        return chunk == null ? Block.AIR.getId() : chunk.getBlockLocal(lx, ly, lz);
    }

    public boolean isSolidBlock(int worldX, int worldY, int worldZ) {
        Block block = Block.fromId(getBlock(worldX, worldY, worldZ));
        return block.isSolid();
    }

    /** Ghi block khi chơi và đánh dấu các chunk đã có dữ liệu nếu chung mặt biên. */
    public void setBlock(int worldX, int worldY, int worldZ, byte blockId) {
        if (!setBlockInternal(worldX, worldY, worldZ, blockId)) {
            return;
        }

        int cx = worldX / Chunk.SIZE;
        int cy = worldY / Chunk.SIZE;
        int cz = worldZ / Chunk.SIZE;

        int lx = worldX % Chunk.SIZE;
        int ly = worldY % Chunk.SIZE;
        int lz = worldZ % Chunk.SIZE;

        if (lx == 0 && cx > 0) {
            markChunkDirty(cx - 1, cy, cz);
        } else if (lx == Chunk.SIZE - 1 && cx < WORLD_CHUNKS_X - 1) {
            markChunkDirty(cx + 1, cy, cz);
        }

        if (ly == 0 && cy > 0) {
            markChunkDirty(cx, cy - 1, cz);
        } else if (ly == Chunk.SIZE - 1 && cy < WORLD_CHUNKS_Y - 1) {
            markChunkDirty(cx, cy + 1, cz);
        }

        if (lz == 0 && cz > 0) {
            markChunkDirty(cx, cy, cz - 1);
        } else if (lz == Chunk.SIZE - 1 && cz < WORLD_CHUNKS_Z - 1) {
            markChunkDirty(cx, cy, cz + 1);
        }
    }

    /** Đánh dấu mesh lân cận cần dựng lại, giữ nguyên các ô chỉ chứa không khí. */
    private void markChunkDirty(int cx, int cy, int cz) {
        Chunk chunk = chunks[cx][cy][cz];
        if (chunk != null) {
            chunk.markDirty();
        }
    }

    /**
     * Đập vỡ hoàn toàn một Block tại `(worldX, worldY, worldZ)` và kích hoạt hành vi đa hình `target.onBreak(...)`.
     *
     * @param worldX tọa độ X thế giới
     * @param worldY tọa độ Y thế giới
     * @param worldZ tọa độ Z thế giới
     */
    public void breakBlockAndSpawnDrop(int worldX, int worldY, int worldZ) {
        Block target = Block.fromId(getBlock(worldX, worldY, worldZ));
        if (!target.isBreakable()) {
            return;
        }

        // 1. Đổi ô tại vị trí đó thành Không khí (Block.AIR)
        setBlock(worldX, worldY, worldZ, Block.AIR.getId());

        // 2. Kích hoạt đa hình hành vi khi vỡ (GrassBlock rơi ra Block.DIRT, các khối khác rơi ra chính nó)
        target.onBreak(this, worldX, worldY, worldZ);
    }

    /**
     * Vẽ các chunk giao khung nhìn và duyệt danh sách đa hình để vẽ các thực thể 3D.
     *
     * @param shader chương trình Shader đang kích hoạt
     * @param projection ma trận chiếu phối cảnh đã truyền vào shader
     * @param view ma trận nhìn đã truyền vào shader
     */
    public void render(ShaderProgram shader, Matrix4 projection, Matrix4 view) {
        // libGDX dùng nghịch đảo của Projection × View để dựng sáu mặt phẳng khung nhìn.
        inverseViewProjection.set(projection).mul(view).inv();
        frustum.update(inverseViewProjection);
        // Tâm mặt phẳng gần xấp xỉ vị trí mắt và ưu tiên được cả các chunk theo độ cao.
        nearCenter.set(0, 0, -1).prj(inverseViewProjection);
        renderFrame++;
        pendingMeshCount = 0;

        shader.setUniformMatrix4f("uModel", identityMatrix);
        renderVisibleChunks(shader);
        for (int i = 0; i < pendingMeshCount; i++) {
            Chunk chunk = pendingMeshes[i];
            pendingMeshes[i] = null;
            boolean alreadyDrawn = chunk.hasMesh();
            rebuildChunkMesh(chunk);
            if (!alreadyDrawn) {
                chunk.render(shader);
            }
        }
        releaseOffscreenMeshes();

        // Duyệt danh sách đa hình List<Entity> và gọi entity.render(shader)
        for (int i = 0; i < entities.size(); i++) {
            entities.get(i).render(shader);
        }

        shader.setUniformMatrix4f("uModel", identityMatrix);
    }

    /** Thu hẹp vùng quét bằng hộp bao khung nhìn, rồi kiểm tra chính xác từng chunk. */
    private void renderVisibleChunks(ShaderProgram shader) {
        float minX = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (Vector3 corner : frustum.planePoints) {
            minX = Math.min(minX, corner.x);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxZ = Math.max(maxZ, corner.z);
        }
        // Mở rộng thêm một ô ở mép dưới để tính cả chunk tiếp xúc mặt hộp bao.
        int firstX = Math.max(0, (int) Math.floor(minX / Chunk.SIZE) - 1);
        int firstZ = Math.max(0, (int) Math.floor(minZ / Chunk.SIZE) - 1);
        int lastX = Math.min(WORLD_CHUNKS_X - 1, (int) Math.floor(maxX / Chunk.SIZE));
        int lastZ = Math.min(WORLD_CHUNKS_Z - 1, (int) Math.floor(maxZ / Chunk.SIZE));
        for (int cx = firstX; cx <= lastX; cx++) {
            for (int cy = 0; cy < WORLD_CHUNKS_Y; cy++) {
                for (int cz = firstZ; cz <= lastZ; cz++) {
                    Chunk chunk = chunks[cx][cy][cz];
                    if (chunk != null && chunk.intersectsFrustum(frustum)) {
                        chunk.lastVisibleFrame = renderFrame;
                        if (chunk.isDirty()) {
                            queueMeshBuild(chunk);
                        }
                        chunk.render(shader);
                    }
                }
            }
        }
    }

    /** Giữ danh sách hữu hạn các mesh cần dựng, sắp theo khoảng cách tới camera. */
    private void queueMeshBuild(Chunk chunk) {
        float distance = chunk.distanceSquaredTo(nearCenter);
        int index = pendingMeshCount;
        if (index == pendingMeshes.length) {
            index--;
            if (distance >= pendingDistances[index]) {
                return;
            }
        } else {
            pendingMeshCount++;
        }
        while (index > 0 && distance < pendingDistances[index - 1]) {
            pendingMeshes[index] = pendingMeshes[index - 1];
            pendingDistances[index] = pendingDistances[index - 1];
            index--;
        }
        pendingMeshes[index] = chunk;
        pendingDistances[index] = distance;
    }

    /** Dựng mesh và đồng bộ danh sách tài nguyên đang được giữ. */
    private void rebuildChunkMesh(Chunk chunk) {
        boolean hadMesh = chunk.hasMesh();
        chunk.rebuildMesh(this, meshVertices);
        if (!hadMesh && chunk.hasMesh()) {
            residentMeshes.add(chunk);
        } else if (hadMesh && !chunk.hasMesh()) {
            residentMeshes.removeValue(chunk, true);
        }
    }

    /** Thu hồi mesh đã ở ngoài khung nhìn đủ lâu, giữ dữ liệu block và các sửa đổi. */
    private void releaseOffscreenMeshes() {
        for (int i = residentMeshes.size - 1; i >= 0; i--) {
            Chunk chunk = residentMeshes.get(i);
            if (renderFrame - chunk.lastVisibleFrame >= MESH_RETENTION_FRAMES) {
                chunk.releaseCachedMesh();
                residentMeshes.removeIndex(i);
            }
        }
    }

    /** Giải phóng các mesh đang còn trong bộ nhớ và tài nguyên dùng chung của vật phẩm. */
    @Override
    public void dispose() {
        for (int i = 0; i < residentMeshes.size; i++) {
            residentMeshes.get(i).dispose();
        }
        residentMeshes.clear();
        ItemEntity.cleanupSharedMeshes();
    }
}
