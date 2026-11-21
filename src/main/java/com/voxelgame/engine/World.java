package com.voxelgame.engine;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
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
 *    - Trong `updateEntities(float deltaTime)` và `render(ShaderProgram shader)`, `World` duyệt qua
 *      danh sách `entities` và gọi đa hình `entity.update(this, deltaTime)` và `entity.render(shader)`.
 *    - Trong `breakBlockAndSpawnDrop(int, int, int)`, `World` gọi đa hình `target.onBreak(this, worldX, worldY, worldZ)`
 *      để mỗi loại `Block` tự quyết định vật phẩm rơi ra (ví dụ: `GrassBlock` rơi ra `Block.DIRT`).
 * 2. Tính Đóng gói (Encapsulation):
 *    - Đóng gói mảng `chunks`, `surfaceHeights` và danh sách `entities` ở phạm vi `private final`,
 *      chỉ cung cấp các phương thức giao tiếp an toàn (`registerPlayer`, `spawnDroppedItem`, `getBlock`, `setBlock`).
 */
public class World implements Disposable {

    // Kích thước thế giới: 20 x 2 x 20 Chunks = 320 x 32 x 320 Blocks
    public static final int WORLD_CHUNKS_X = 20;
    public static final int WORLD_CHUNKS_Y = 2;
    public static final int WORLD_CHUNKS_Z = 20;

    public static final int WORLD_SIZE_X = WORLD_CHUNKS_X * Chunk.SIZE;
    public static final int WORLD_SIZE_Y = WORLD_CHUNKS_Y * Chunk.SIZE;
    public static final int WORLD_SIZE_Z = WORLD_CHUNKS_Z * Chunk.SIZE;

    // Giới hạn nghiêm ngặt: Tổng số lớp địa hình từ đáy (y = 0) đến đỉnh cao nhất <= 20 lớp (tức y <= 19)
    public static final int MAX_TERRAIN_LAYERS = 20;
    public static final int MAX_TERRAIN_Y = MAX_TERRAIN_LAYERS - 1; // y = 19

    private final Chunk[][][] chunks = new Chunk[WORLD_CHUNKS_X][WORLD_CHUNKS_Y][WORLD_CHUNKS_Z];
    private final int[][] surfaceHeights = new int[WORLD_SIZE_X][WORLD_SIZE_Z];

    // Tham chiếu tới thực thể người chơi và danh sách đa hình chứa mọi Entity hoạt động trong thế giới
    private Player player;
    private final List<Entity> entities = new ArrayList<>();

    private final Matrix4 identityMatrix = new Matrix4();

    public World() {
        for (int cx = 0; cx < WORLD_CHUNKS_X; cx++) {
            for (int cy = 0; cy < WORLD_CHUNKS_Y; cy++) {
                for (int cz = 0; cz < WORLD_CHUNKS_Z; cz++) {
                    chunks[cx][cy][cz] = new Chunk(cx, cy, cz);
                }
            }
        }

        generateTerrainAndEcosystem();
        rebuildAllDirtyChunks();
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

                if (Math.abs(tx - 32) <= 4 && Math.abs(tz - 32) <= 4) {
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

    private int findHighestBlockY(int wx, int wz) {
        for (int y = WORLD_SIZE_Y - 1; y >= 0; y--) {
            if (getBlock(wx, y, wz) != Block.AIR.getId()) {
                return y;
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

    private void setBlockInternal(int worldX, int worldY, int worldZ, byte blockId) {
        if (!isInsideWorld(worldX, worldY, worldZ)) {
            return;
        }
        int cx = worldX / Chunk.SIZE;
        int cy = worldY / Chunk.SIZE;
        int cz = worldZ / Chunk.SIZE;

        int lx = worldX % Chunk.SIZE;
        int ly = worldY % Chunk.SIZE;
        int lz = worldZ % Chunk.SIZE;

        chunks[cx][cy][cz].setBlockLocal(lx, ly, lz, blockId);
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

        return chunks[cx][cy][cz].getBlockLocal(lx, ly, lz);
    }

    public boolean isSolidBlock(int worldX, int worldY, int worldZ) {
        Block block = Block.fromId(getBlock(worldX, worldY, worldZ));
        return block.isSolid();
    }

    public void setBlock(int worldX, int worldY, int worldZ, byte blockId) {
        if (!isInsideWorld(worldX, worldY, worldZ)) {
            return;
        }

        int cx = worldX / Chunk.SIZE;
        int cy = worldY / Chunk.SIZE;
        int cz = worldZ / Chunk.SIZE;

        int lx = worldX % Chunk.SIZE;
        int ly = worldY % Chunk.SIZE;
        int lz = worldZ % Chunk.SIZE;

        chunks[cx][cy][cz].setBlockLocal(lx, ly, lz, blockId);

        if (lx == 0 && cx > 0) {
            chunks[cx - 1][cy][cz].markDirty();
        } else if (lx == Chunk.SIZE - 1 && cx < WORLD_CHUNKS_X - 1) {
            chunks[cx + 1][cy][cz].markDirty();
        }

        if (ly == 0 && cy > 0) {
            chunks[cx][cy - 1][cz].markDirty();
        } else if (ly == Chunk.SIZE - 1 && cy < WORLD_CHUNKS_Y - 1) {
            chunks[cx][cy + 1][cz].markDirty();
        }

        if (lz == 0 && cz > 0) {
            chunks[cx][cy][cz - 1].markDirty();
        } else if (lz == Chunk.SIZE - 1 && cz < WORLD_CHUNKS_Z - 1) {
            chunks[cx][cy][cz + 1].markDirty();
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

    private void rebuildAllDirtyChunks() {
        for (int cx = 0; cx < WORLD_CHUNKS_X; cx++) {
            for (int cy = 0; cy < WORLD_CHUNKS_Y; cy++) {
                for (int cz = 0; cz < WORLD_CHUNKS_Z; cz++) {
                    Chunk chunk = chunks[cx][cy][cz];
                    if (chunk.isDirty()) {
                        chunk.rebuildMesh(this);
                    }
                }
            }
        }
    }

    /**
     * Vẽ toàn bộ các Chunk địa hình và duyệt danh sách đa hình `entities` để vẽ các thực thể 3D.
     *
     * @param shader chương trình Shader đang kích hoạt
     */
    public void render(ShaderProgram shader) {
        rebuildAllDirtyChunks();

        shader.setUniformMatrix4f("uModel", identityMatrix);
        for (int cx = 0; cx < WORLD_CHUNKS_X; cx++) {
            for (int cy = 0; cy < WORLD_CHUNKS_Y; cy++) {
                for (int cz = 0; cz < WORLD_CHUNKS_Z; cz++) {
                    chunks[cx][cy][cz].render(shader);
                }
            }
        }

        // Duyệt danh sách đa hình List<Entity> và gọi entity.render(shader)
        for (Entity entity : entities) {
            entity.render(shader);
        }

        shader.setUniformMatrix4f("uModel", identityMatrix);
    }

    @Override
    public void dispose() {
        for (int cx = 0; cx < WORLD_CHUNKS_X; cx++) {
            for (int cy = 0; cy < WORLD_CHUNKS_Y; cy++) {
                for (int cz = 0; cz < WORLD_CHUNKS_Z; cz++) {
                    if (chunks[cx][cy][cz] != null) {
                        chunks[cx][cy][cz].dispose();
                    }
                }
            }
        }
        ItemEntity.cleanupSharedMeshes();
    }
}
