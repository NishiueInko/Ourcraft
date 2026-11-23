package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.FallingBlockEntity;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.Player;

import java.util.ArrayList;
import java.util.List;

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

    // Ba biome rộng 1.000 block theo trục X; chunk cuối chỉ chứa tám cột hợp lệ.
    static final int BIOME_WIDTH = 1_000;
    public static final int WORLD_SIZE_X = 3 * BIOME_WIDTH;
    public static final int WORLD_CHUNKS_X = (WORLD_SIZE_X + Chunk.SIZE - 1) / Chunk.SIZE;
    public static final int WORLD_CHUNKS_Y = 16;
    public static final int WORLD_CHUNKS_Z = 112;

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
    /** Seed ổn định dùng cho toàn bộ vùng khí hậu và đặc trưng địa hình. */
    public static final long WORLD_SEED = 20261114L;

    // Một ô null biểu thị chunk chỉ chứa không khí, chưa cần mảng block hay mesh.
    private final Chunk[][][] chunks = new Chunk[WORLD_CHUNKS_X][WORLD_CHUNKS_Y][WORLD_CHUNKS_Z];
    private final TerrainGenerator terrain = new TerrainGenerator(WORLD_SEED);

    // Tham chiếu tới thực thể người chơi và danh sách đa hình chứa mọi Entity hoạt động trong thế giới
    private Player player;
    private final List<Entity> entities = new ArrayList<>();
    /** Hệ thống âm thanh do ứng dụng sở hữu và vị trí nghe dùng lại khi tính độ xa. */
    private AudioManager audio;
    private final Vector3 audioListener = new Vector3();
    // Các luật có thể ghi thêm ô trong lúc hàng đợi đang được xử lý.
    private final BlockUpdateQueue blockUpdates = new BlockUpdateQueue();
    private boolean drainingBlockUpdates;

    private final Matrix4 identityMatrix = new Matrix4();
    // Khung nhìn và bộ đệm dựng mesh được tái sử dụng trên luồng kết xuất.
    private final Matrix4 inverseViewProjection = new Matrix4();
    private final Frustum frustum = new Frustum();
    private final FloatArray meshVertices = new FloatArray(4096);
    private final FloatArray translucentVertices = new FloatArray(4096);
    private final Vector3 nearCenter = new Vector3();
    /** Dung lượng chặn trên bởi tổng số ô chunk, kể cả thế giới đã được xây kín. */
    private static final int CHUNK_CAPACITY = WORLD_CHUNKS_X * WORLD_CHUNKS_Y * WORLD_CHUNKS_Z;
    private final Chunk[] visibleChunks = new Chunk[CHUNK_CAPACITY];
    private final Chunk[] translucentChunks = new Chunk[CHUNK_CAPACITY];
    private final float[] translucentDistances = new float[CHUNK_CAPACITY];
    private final int[] translucentKeys = new int[CHUNK_CAPACITY];
    private int visibleChunkCount;
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
        this(true);
    }

    /** Mở một thế giới trống để kiểm tra luật block mà không cần GPU. */
    static World empty() {
        return new World(false);
    }

    /** Tách sinh địa hình và khởi tạo mesh khỏi đường tạo bản đồ trống. */
    private World(boolean generate) {
        if (!generate) {
            spawnY = 1;
            return;
        }
        terrain.generate(this);
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

    /** Trả vùng khí hậu tại cột, kể cả khi chunk chưa có mesh. */
    public BiomeType getBiome(int x, int z) {
        return terrain.getBiome(x, z);
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
        int frameEntityCount = entities.size();
        for (int i = 0; i < frameEntityCount; i++) {
            Entity entity = entities.get(i);
            entity.update(this, deltaTime);

            if ((entity instanceof ItemEntity item && item.isCollected())
                    || (entity instanceof FallingBlockEntity falling && falling.isFinished())) {
                entities.remove(i);
                frameEntityCount--;
                i--;
            }
        }
    }

    /** Nhận hệ thống âm thanh sau khi thế giới và các tài nguyên đã sẵn sàng. */
    public void setAudio(AudioManager audio) {
        this.audio = audio;
    }

    /** Cho thực thể phát âm thanh tại điểm xảy ra tương tác gameplay. */
    public AudioManager getAudio() {
        return audio;
    }

    /** Hạ âm lượng theo khoảng cách đến người chơi, tắt ngoài bán kính 16 block. */
    public float getAudioGain(float x, float y, float z) {
        if (player == null) {
            return 0f;
        }
        float distance = player.copyCenterPosition(audioListener).dst(x, y, z);
        return Math.max(0f, 1f - distance / 16f);
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

    /** Ghi block và cấp phát hoặc giải phóng chunk khi nó chuyển trạng thái rỗng. */
    private boolean setBlockInternal(int worldX, int worldY, int worldZ, byte blockId) {
        return setBlockInternal(worldX, worldY, worldZ, blockId, 0);
    }

    /** Ghi trạng thái mà không chạy luật lân cận, dùng để sinh địa hình và ghi theo nhóm. */
    boolean setBlockInternal(int worldX, int worldY, int worldZ, byte blockId, int state) {
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
        if (!chunk.setBlockLocal(lx, ly, lz, blockId, state)) {
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
        return getCollisionBounds(worldX, worldY, worldZ) != null;
    }

    /** Trả độ cao bề mặt chặn giáng thủy, kể cả mặt chất lỏng và lớp tuyết thấp. */
    public float getPrecipitationSurface(int x, int z) {
        int y = findWeatherSurface(x, z, true);
        if (y < 0) {
            return 0f;
        }
        Block block = Block.fromId(getBlock(x, y, z));
        if (block.isLiquid()) {
            return y + block.getTopHeight(this, x, y, z);
        }
        return y + block.getSelectionBounds(getBlockState(x, y, z)).maxY();
    }

    /** Tán lá và các khối rắn che tai người chơi, kể cả khi mắt đang dưới nước. */
    boolean hasWeatherRoof(int x, float eyeY, int z) {
        int y = findWeatherSurface(x, z, false);
        return y >= 0 && y + Block.fromId(getBlock(x, y, z))
                .getSelectionBounds(getBlockState(x, y, z)).maxY() > eyeY;
    }

    /** Duyệt từ trên xuống, bỏ qua cả chunk rỗng trước khi xem các ô trong cột. */
    private int findWeatherSurface(int x, int z, boolean includeLiquids) {
        if (!isInsideWorld(x, 0, z)) {
            return -1;
        }
        Chunk[][] column = chunks[x / Chunk.SIZE];
        int localX = x % Chunk.SIZE;
        int localZ = z % Chunk.SIZE;
        int chunkZ = z / Chunk.SIZE;
        for (int chunkY = WORLD_CHUNKS_Y - 1; chunkY >= 0; chunkY--) {
            Chunk chunk = column[chunkY][chunkZ];
            if (chunk == null) {
                continue;
            }
            for (int localY = Chunk.SIZE - 1; localY >= 0; localY--) {
                Block block = Block.fromId(chunk.getBlockLocal(localX, localY, localZ));
                if (block.isSolid() || (includeLiquids && block.isLiquid())) {
                    return chunkY * Chunk.SIZE + localY;
                }
            }
        }
        return -1;
    }

    /** Thử đóng băng hoặc tích tuyết trên bề mặt lộ thiên, qua đường cập nhật block thông thường. */
    void accumulateSnow(int x, int z, Player player, boolean thicken) {
        if (!isInsideWorld(x, 0, z) || getBiome(x, z) != BiomeType.SNOWY_PLAINS) {
            return;
        }
        int y = findWeatherSurface(x, z, true);
        if (y < 0) {
            return;
        }
        Block top = Block.fromId(getBlock(x, y, z));
        if (top == Block.WATER) {
            if (getBlock(x, y + 1, z) == Block.AIR.getId() && !hasLavaNear(x, y, z)
                    && !intersectsPlayer(player, Block.ICE, 0, x, y, z)) {
                setBlock(x, y, z, Block.ICE.getId());
            }
        } else if (top == Block.SNOW_LAYER) {
            if (thicken && !hasLavaNear(x, y, z)) {
                tryStackSnow(x, y, z, player);
            }
        } else if (top != Block.SNOW_BLOCK && !hasLavaNear(x, y + 1, z)) {
            tryPlaceBlock(x, y + 1, z, Block.SNOW_LAYER, player);
        }
    }

    /** Đọc metadata dạng không dấu. Ô mặc định và chunk rỗng trả về 0. */
    public int getBlockState(int x, int y, int z) {
        if (!isInsideWorld(x, y, z)) {
            return 0;
        }
        Chunk chunk = chunks[x / Chunk.SIZE][y / Chunk.SIZE][z / Chunk.SIZE];
        return chunk == null ? 0 : chunk.getStateLocal(x % Chunk.SIZE, y % Chunk.SIZE, z % Chunk.SIZE);
    }

    /** Lấy hộp cục bộ đúng theo loại block và trạng thái hiện tại. */
    public Block.Bounds getCollisionBounds(int x, int y, int z) {
        return Block.fromId(getBlock(x, y, z)).getCollisionBounds(getBlockState(x, y, z));
    }

    /** Ghi block mặc định khi chơi rồi xử lý chuỗi thay đổi. */
    public void setBlock(int worldX, int worldY, int worldZ, byte blockId) {
        setBlock(worldX, worldY, worldZ, blockId, 0);
    }

    /** Ghi block có metadata rồi xử lý lân cận sau khi các ô đã nhất quán. */
    public void setBlock(int x, int y, int z, byte blockId, int state) {
        writeGameplayBlock(x, y, z, blockId, state);
        drainBlockUpdates();
    }

    /** Ghi một ô, dọn nửa cỏ cũ nếu có và xếp lịch các luật phụ thuộc. */
    private void writeGameplayBlock(int x, int y, int z, byte blockId, int state) {
        byte oldId = getBlock(x, y, z);
        int oldState = getBlockState(x, y, z);
        if (!setBlockInternal(x, y, z, blockId, Block.fromId(blockId).normalizeState(state))) {
            return;
        }
        scheduleChangedBlock(x, y, z, oldId, blockId);
        if (oldId == Block.TALL_GRASS.getId() && (blockId != oldId || oldState != state)) {
            int otherY = oldState == 0 ? y + 1 : y - 1;
            if (getBlock(x, otherY, z) == oldId && getBlockState(x, otherY, z) == 1 - oldState
                    && setBlockInternal(x, otherY, z, Block.AIR.getId())) {
                scheduleChangedBlock(x, otherY, z, oldId, Block.AIR.getId());
            }
        }
    }

    /** Đánh dấu mesh và đưa các ô phụ thuộc vào hàng đợi FIFO. */
    private void scheduleChangedBlock(int worldX, int worldY, int worldZ, byte oldId, byte newId) {
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

        // Mặt bên chất lỏng một ô dưới có thể đổi độ cao qua cả biên ngang lẫn biên đứng.
        if (ly == 0 && cy > 0 && Block.fromId(getBlock(worldX, worldY - 1, worldZ)).isLiquid()) {
            if (lx == 0 && cx > 0) {
                markChunkDirty(cx - 1, cy - 1, cz);
            } else if (lx == Chunk.SIZE - 1 && cx < WORLD_CHUNKS_X - 1) {
                markChunkDirty(cx + 1, cy - 1, cz);
            }
            if (lz == 0 && cz > 0) {
                markChunkDirty(cx, cy - 1, cz - 1);
            } else if (lz == Chunk.SIZE - 1 && cz < WORLD_CHUNKS_Z - 1) {
                markChunkDirty(cx, cy - 1, cz + 1);
            }
        }

        blockUpdates.add(worldX, worldY, worldZ);
        blockUpdates.add(worldX - 1, worldY, worldZ);
        blockUpdates.add(worldX + 1, worldY, worldZ);
        blockUpdates.add(worldX, worldY - 1, worldZ);
        blockUpdates.add(worldX, worldY + 1, worldZ);
        blockUpdates.add(worldX, worldY, worldZ - 1);
        blockUpdates.add(worldX, worldY, worldZ + 1);
        if (oldId == Block.WATER.getId() || newId == Block.WATER.getId()) {
            // Nước cạnh ô nền nằm thấp hơn gốc mía một ô.
            blockUpdates.add(worldX - 1, worldY + 1, worldZ);
            blockUpdates.add(worldX + 1, worldY + 1, worldZ);
            blockUpdates.add(worldX, worldY + 1, worldZ - 1);
            blockUpdates.add(worldX, worldY + 1, worldZ + 1);
        }
        if (newId == Block.LAVA.getId()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int reach = 2 - Math.abs(dx) - Math.abs(dy);
                    for (int dz = -reach; dz <= reach; dz++) {
                        blockUpdates.add(worldX + dx, worldY + dy, worldZ + dz);
                    }
                }
            }
        }
    }

    /** Xử lý cho đến khi các thay đổi dây chuyền kết thúc, tránh gọi đệ quy. */
    private void drainBlockUpdates() {
        if (drainingBlockUpdates) {
            return;
        }
        drainingBlockUpdates = true;
        try {
            while (blockUpdates.hasNext()) {
                int packed = blockUpdates.next();
                int x = packed >>> 19;
                int y = packed & 0xff;
                int z = (packed >>> 8) & 0x7ff;
                Block block = Block.fromId(getBlock(x, y, z));
                if ((block == Block.ICE || block == Block.SNOW_LAYER) && hasLavaNear(x, y, z)) {
                    Block melted = block == Block.ICE ? Block.WATER : Block.AIR;
                    writeGameplayBlock(x, y, z, melted.getId(), 0);
                } else if (block.isGravityBlock() && getCollisionBounds(x, y - 1, z) == null) {
                    writeGameplayBlock(x, y, z, Block.AIR.getId(), 0);
                    entities.add(new FallingBlockEntity(x, y, z, block));
                } else if (block != Block.AIR) {
                    block.onNeighborChange(this, x, y, z);
                }
            }
        } finally {
            blockUpdates.clear();
            drainingBlockUpdates = false;
        }
    }

    /** Tìm dung nham trong bán kính Manhattan hai ô. */
    boolean hasLavaNear(int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int reach = 2 - Math.abs(dx) - Math.abs(dy);
                for (int dz = -reach; dz <= reach; dz++) {
                    if (getBlock(x + dx, y + dy, z + dz) == Block.LAVA.getId()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Kiểm tra và cam kết thao tác đặt, kể cả hai ô đồng thời của cỏ cao. */
    public boolean tryPlaceBlock(int x, int y, int z, Block block, Player placingPlayer) {
        if (!isInsideWorld(x, y, z) || block == Block.AIR || !block.canPlaceAt(this, x, y, z)
                || !canReplace(x, y, z, block) || intersectsPlayer(placingPlayer, block, 0, x, y, z)) {
            return false;
        }
        if (block == Block.TALL_GRASS && (!isInsideWorld(x, y + 1, z)
                || getBlock(x, y + 1, z) != Block.AIR.getId())) {
            return false;
        }
        writeGameplayBlock(x, y, z, block.getId(), 0);
        if (block == Block.TALL_GRASS) {
            writeGameplayBlock(x, y + 1, z, block.getId(), 1);
        }
        drainBlockUpdates();
        return true;
    }

    /** Chỉ lớp tuyết mỏng và thực vật có thể nhường chỗ cho khối rắn. */
    private boolean canReplace(int x, int y, int z, Block placed) {
        Block existing = Block.fromId(getBlock(x, y, z));
        return existing == Block.AIR || existing != placed
                && existing.isReplaceable(getBlockState(x, y, z))
                && (existing.isLiquid() || placed.getCollisionBounds(0) != null);
    }

    /** Tăng độ dày của lớp tuyết hiện có nếu còn chỗ. */
    public boolean tryStackSnow(int x, int y, int z, Player placingPlayer) {
        if (getBlock(x, y, z) != Block.SNOW_LAYER.getId()) {
            return false;
        }
        int state = getBlockState(x, y, z);
        if (state == 7 || intersectsPlayer(placingPlayer, Block.SNOW_LAYER, state + 1, x, y, z)) {
            return false;
        }
        setBlock(x, y, z, Block.SNOW_LAYER.getId(), state + 1);
        return true;
    }

    /** Chỉ hộp va chạm của block mới có thể cản người chơi. */
    private boolean intersectsPlayer(Player placingPlayer, Block block, int state, int x, int y, int z) {
        Block.Bounds bounds = block.getCollisionBounds(state);
        return placingPlayer != null && bounds != null && placingPlayer.intersectsBlock(x, y, z, bounds);
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

        Block replacement = target.getBreakReplacement(this, worldX, worldY, worldZ);
        writeGameplayBlock(worldX, worldY, worldZ, replacement.getId(), 0);
        if (audio != null) {
            audio.playBreak(target, getAudioGain(worldX + 0.5f, worldY + 0.5f, worldZ + 0.5f));
        }
        target.onBreak(this, worldX, worldY, worldZ);
        drainBlockUpdates();
    }

    /**
     * Vẽ các chunk giao khung nhìn và duyệt danh sách đa hình để vẽ các thực thể 3D.
     *
     * @param shader chương trình Shader đang kích hoạt
     * @param projection ma trận chiếu phối cảnh đã truyền vào shader
     * @param view ma trận nhìn đã truyền vào shader
     */
    public void render(ShaderProgram shader, Matrix4 projection, Matrix4 view) {
        render(shader, projection, view, null);
    }

    /** Dựng các chunk gần camera rồi vẽ pha đục và pha trong suốt theo thứ tự. */
    public void render(ShaderProgram shader, Matrix4 projection, Matrix4 view, Vector3 cameraPosition) {
        render(shader, projection, view, cameraPosition, null);
    }

    /** Cho phép vẽ tầng mây sau pha ghi chiều sâu và trước khi hòa màu nước/băng. */
    public void render(ShaderProgram shader, Matrix4 projection, Matrix4 view, Vector3 cameraPosition,
                       Runnable beforeTranslucent) {
        // libGDX dùng nghịch đảo của Projection × View để dựng sáu mặt phẳng khung nhìn.
        inverseViewProjection.set(projection).mul(view).inv();
        frustum.update(inverseViewProjection);
        // Tâm mặt phẳng gần xấp xỉ vị trí mắt và ưu tiên được cả các chunk theo độ cao.
        nearCenter.set(0, 0, -1).prj(inverseViewProjection);
        renderFrame++;
        pendingMeshCount = 0;
        visibleChunkCount = 0;

        collectVisibleChunks();
        for (int i = 0; i < pendingMeshCount; i++) {
            Chunk chunk = pendingMeshes[i];
            pendingMeshes[i] = null;
            rebuildChunkMesh(chunk);
        }
        releaseOffscreenMeshes();

        int translucentCount = 0;
        Vector3 eye = cameraPosition == null ? nearCenter : cameraPosition;
        shader.setUniformMatrix4f("uModel", identityMatrix);
        for (int i = 0; i < visibleChunkCount; i++) {
            Chunk chunk = visibleChunks[i];
            visibleChunks[i] = null;
            chunk.render(shader);
            if (chunk.hasTranslucentMesh()) {
                translucentChunks[translucentCount] = chunk;
                translucentDistances[translucentCount] = chunk.centerDistanceSquaredTo(eye);
                translucentKeys[translucentCount] = i;
                translucentCount++;
            }
        }
        // Thực thể đục phải ghi chiều sâu trước khi vẽ nước và băng.
        for (int i = 0; i < entities.size(); i++) {
            entities.get(i).render(shader);
        }
        shader.setUniformMatrix4f("uModel", identityMatrix);
        if (beforeTranslucent != null) {
            beforeTranslucent.run();
        }
        renderTranslucent(shader, translucentCount);
    }

    /** Sắp xếp xa tới gần và vẽ pha trong suốt bằng các mảng dùng lại. */
    private void renderTranslucent(ShaderProgram shader, int count) {
        if (count == 0) {
            return;
        }
        TranslucentChunkSort.sort(translucentChunks, translucentDistances, translucentKeys, count);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        try {
            for (int i = 0; i < count; i++) {
                translucentChunks[i].renderTranslucent(shader);
            }
        } finally {
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_BLEND);
            for (int i = 0; i < count; i++) {
                translucentChunks[i] = null;
            }
        }
    }

    /** Duyệt khung nhìn theo thứ tự tọa độ X, Y, Z để khóa hòa ổn định. */
    private void collectVisibleChunks() {
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
                        visibleChunks[visibleChunkCount++] = chunk;
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
        chunk.rebuildMesh(this, meshVertices, translucentVertices);
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
