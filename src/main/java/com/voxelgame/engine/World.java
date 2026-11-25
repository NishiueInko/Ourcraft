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
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.FallingBlockEntity;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.projectile.ProjectileEntity;

import java.util.ArrayList;
import java.util.Arrays;
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
 *    - Đóng gói mảng `chunks` và danh sách `entities` ở phạm vi `private final`,
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

    // Tâm hình học dùng cho lần xuất phát đầu tiên và tọa độ hồi sinh ưu tiên.
    public static final int CENTER_X = WORLD_SIZE_X / 2;
    public static final int CENTER_Z = WORLD_SIZE_Z / 2;
    static final int RESPAWN_X = 1500;
    static final int RESPAWN_Z = 1500;
    /** Số block tối đa tìm quanh tâm biome để tránh cột ngập nước hoặc bị che kín. */
    private static final int BIOME_LANDING_RADIUS = 32;
    // Dựng sẵn khu vực 5 × 5 cột chunk quanh spawn.
    private static final int SPAWN_MESH_RADIUS = 2;
    // Giới hạn công việc dựng mesh mỗi khung hình và thời gian giữ mesh ngoài khung nhìn.
    private static final int MESH_BUILDS_PER_FRAME = 16;
    private static final int MESH_RETENTION_FRAMES = 120;

    // Chừa phần không gian trên đỉnh tự nhiên cho tuyết, cây và xây dựng.
    public static final int MAX_TERRAIN_LAYERS = 155;
    public static final int MAX_TERRAIN_Y = MAX_TERRAIN_LAYERS - 1;
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
    /** Lượt vẽ mob do ứng dụng cung cấp khi có ngữ cảnh OpenGL. */
    private MobRenderer mobRenderer;
    /** Thời tiết dùng chung với bộ kết xuất để dập lửa ngoài trời. */
    private WeatherManager weather;
    /** Đồng hồ và bộ sinh dùng chung trên luồng cập nhật thực thể. */
    private WorldTime worldTime = new WorldTime();
    private final MobSpawner mobSpawner = new MobSpawner(WORLD_SEED);
    private final Vector3 audioListener = new Vector3();
    /** Bộ đệm tâm thực thể dùng chung cho các lượt ảnh hưởng của vụ nổ. */
    private final Vector3 blastTarget = new Vector3();
    /** Tọa độ tạm khi kiểm tra thực thể chiếm chỗ trong một vùng. */
    private final Vector3 occupiedPosition = new Vector3();
    /** Bộ đệm tìm vật phẩm cho thực thể trên luồng cập nhật. */
    private final Vector3 droppedItemPosition = new Vector3();
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
    /** Nhóm chunk đục theo từng dải khoảng cách để giảm overdraw ở rừng dày. */
    private static final int OPAQUE_BINS = 64;
    private final int[] opaqueHead = new int[OPAQUE_BINS];
    private final int[] opaqueTail = new int[OPAQUE_BINS];
    private final int[] opaqueNext = new int[CHUNK_CAPACITY];
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

    /** Tìm điểm đứng khô theo từng vành vuông quanh tâm biome. Chỉ ghi vị trí khi có nền hợp lệ. */
    public boolean setBiomeCenterPosition(Vector3 position, BiomeType biome) {
        int centerX = switch (biome) {
            case SNOWY_PLAINS -> CENTER_X - BIOME_WIDTH;
            case PLAINS -> CENTER_X;
            case DESERT -> CENTER_X + BIOME_WIDTH;
        };
        for (int radius = 0; radius <= BIOME_LANDING_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = CENTER_Z + dz;
                    float feet = biomeLandingFeetY(x, z);
                    if (feet > 0 && getBiome(x, z) == biome) {
                        // Dấu chân rộng 0,6 block nằm trọn trong cột khi đặt ở chính giữa ô.
                        position.set(x + 0.5f, feet, z + 0.5f);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Tìm mặt đỡ ngoài trời trong dữ liệu voxel hiện tại, kể cả tuyết và các block đã sửa. */
    private float biomeLandingFeetY(int x, int z) {
        for (int y = findHighestBlockY(x, z); y >= 0; y--) {
            Block block = Block.fromId(getBlock(x, y, z));
            if (block.isLiquid()) {
                return 0f;
            }
            Block.Bounds bounds = block.getCollisionBounds(getBlockState(x, y, z));
            if (bounds == null) {
                continue;
            }
            // Bỏ cột có mặt đỡ là băng, xương rồng hoặc cây, kể cả tuyết phủ trên tán lá.
            Block base = block == Block.SNOW_LAYER ? Block.fromId(getBlock(x, y - 1, z)) : block;
            if (base == Block.ICE || base == Block.CACTUS || base == Block.OAK_LOG || base == Block.SPRUCE_LOG
                    || base == Block.OAK_LEAVES || base == Block.SPRUCE_LEAVES || y + 2 >= WORLD_SIZE_Y) {
                return 0f;
            }
            return y + bounds.maxY();
        }
        return 0f;
    }

    /** Đưa người chơi tới cột hồi sinh thông thoáng, kể cả khi đã có người xây ở đó. */
    public void setRespawnPosition(Vector3 position) {
        if (tryRespawnNear(position, RESPAWN_X, RESPAWN_Z)
                || tryRespawnNear(position, CENTER_X, CENTER_Z)) {
            return;
        }
        // Cho phép người chơi di chuyển trở lại bản đồ nếu cả hai vùng đã bị xây kín.
        position.set(CENTER_X, WORLD_SIZE_Y, CENTER_Z);
    }

    /** Duyệt các vành vuông theo khoảng cách tăng dần quanh giao điểm bốn cột. */
    private boolean tryRespawnNear(Vector3 position, int centerX, int centerZ) {
        for (int radius = 0; radius <= 12; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    float feet = respawnFeetY(x, z);
                    if (feet > 0) {
                        position.set(x, feet, z);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Yêu cầu một nền rắn và hai ô Air trên toàn bộ bốn cột chạm dấu chân. */
    private float respawnFeetY(int x, int z) {
        float feet = 0f;
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int wx = x + dx;
                int wz = z + dz;
                for (int y = findHighestBlockY(wx, wz); y + 1 > feet; y--) {
                    if (y < 0) {
                        break;
                    }
                    Block ground = Block.fromId(getBlock(wx, y, wz));
                    Block.Bounds bounds = ground.getCollisionBounds(getBlockState(wx, y, wz));
                    if (bounds != null && ground != Block.CACTUS) {
                        feet = Math.max(feet, y + bounds.maxY());
                        break;
                    }
                }
            }
        }
        int clearanceY = (int) Math.ceil(feet);
        if (feet == 0 || clearanceY + 1 >= WORLD_SIZE_Y) {
            return 0;
        }
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int wx = x + dx;
                int wz = z + dz;
                int supportY = clearanceY - 1;
                Block.Bounds bounds = getCollisionBounds(wx, supportY, wz);
                if ((bounds != null && supportY + bounds.maxY() > feet)
                        || getBlock(wx, clearanceY, wz) != Block.AIR.getId()
                        || getBlock(wx, clearanceY + 1, wz) != Block.AIR.getId()) {
                    return 0;
                }
            }
        }
        return feet;
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
        addEntity(player);
        syncMobListener();
    }

    public Player getPlayer() {
        return player;
    }

    /** Đưa thực thể sống vào danh sách cập nhật và kết xuất đúng một lần. */
    public void addLivingEntity(LivingEntity entity) {
        addEntity(entity);
    }

    /** Đăng ký thực thể dùng đường cập nhật đa hình, kể cả đối tượng có hình vẽ đạn đạo. */
    public void addEntity(Entity entity) {
        if (entity != null && !entities.contains(entity)) {
            entities.add(entity);
        }
    }

    /** Thay một cá thể tại cùng chỉ số để vòng cập nhật không bỏ qua hoặc chạy hai lượt. */
    public void replaceLivingEntity(LivingEntity previous, LivingEntity replacement) {
        if (previous == player || replacement == null || previous == replacement || entities.contains(replacement)) {
            return;
        }
        int slot = entities.indexOf(previous);
        if (slot >= 0) {
            previous.discard();
            entities.set(slot, replacement);
        }
    }

    /** Trả số thực thể hiện diện để raycast duyệt theo chỉ số. */
    public int entityCount() {
        return entities.size();
    }

    /** Đọc một thực thể mà không công khai danh sách có thể sửa. */
    public Entity entityAt(int index) {
        return entities.get(index);
    }

    /** Trao khối rơi gần nhất cho mob một lần, giữ nguyên chỉ số trong lượt cập nhật đang chạy. */
    public Block takeNearbyDroppedBlock(float x, float y, float z, float radius) {
        float bestDistance = radius * radius;
        ItemEntity closest = null;
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i) instanceof ItemEntity item && !item.isCollected() && item.getBlock() != Block.AIR) {
                float distance = item.copyPosition(droppedItemPosition).dst2(x, y, z);
                if (distance <= bestDistance) {
                    closest = item;
                    bestDistance = distance;
                }
            }
        }
        return closest == null ? null : closest.collectForMob();
    }

    /** Kiểm tra hộp hành lang sinh hoặc dịch chuyển có bị một thực thể sống khác chiếm hay không. */
    public boolean hasLivingOverlap(float x, float y, float z, float halfWidth, float height, Entity excluded) {
        float top = y + height;
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i) instanceof LivingEntity living && living != excluded && living.isAlive()) {
                living.copyPosition(occupiedPosition);
                if (Math.abs(occupiedPosition.x - x) < halfWidth + living.getHalfWidth()
                        && Math.abs(occupiedPosition.z - z) < halfWidth + living.getHalfWidth()
                        && occupiedPosition.y < top && occupiedPosition.y + living.getHeight() > y) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Gắn đồng hồ thời tiết đang điều khiển giáng thủy của thế giới. */
    public void setWeather(WeatherManager weather) {
        this.weather = weather;
    }

    /** Trả đồng hồ được dùng cho ánh nắng, hành vi và điều kiện sinh mob. */
    public WorldTime worldTime() {
        return worldTime;
    }

    /** Trả giáng thủy hiện tại, hoặc null trong thế giới kiểm thử chưa gắn thời tiết. */
    public WeatherManager weatherManager() {
        return weather;
    }

    /** Kiểm tra vị trí lộ thiên ở cột mưa, có xét khí hậu tại thực thể. */
    public boolean isRainingAt(float x, float y, float z) {
        if (weather == null || weather.strength() <= 0f) {
            return false;
        }
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        return getBiome(blockX, blockZ) == BiomeType.PLAINS && !hasWeatherRoof(blockX, y, blockZ);
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
     * Cập nhật đa hình toàn bộ các thực thể và chạy lượt sinh sau khi đã dọn cá thể rời thế giới.
     *
     * @param deltaTime khoảng thời gian giữa hai khung hình (giây)
     */
    public void updateEntities(float deltaTime) {
        int frameEntityCount = entities.size();
        for (int i = 0; i < frameEntityCount; i++) {
            Entity entity = entities.get(i);
            float mobDistance = entity instanceof MobEntity mob && player != null
                    ? mobSpawner.distanceSquared(mob, player) : 0;
            if (entity instanceof MobEntity mob && player != null && mobSpawner.shouldDespawn(mob, mobDistance, deltaTime)) {
                if (MobSpawner.isNaturalJockeyMount(mob)) {
                    // Dọn bộ kỵ sĩ cùng lượt dù các ghế nằm trước hay sau thú cưỡi trong danh sách.
                    for (int seat = 0; seat < mob.getSeatCount(); seat++) {
                        if (mob.getPassenger(seat) instanceof MobEntity rider && !rider.isPersistent()) {
                            rider.discard();
                            int riderIndex = entities.indexOf(rider);
                            if (riderIndex >= 0) {
                                entities.remove(riderIndex);
                                if (riderIndex < frameEntityCount) {
                                    frameEntityCount--;
                                    if (riderIndex < i) {
                                        i--;
                                    }
                                }
                            }
                        }
                    }
                }
                mob.discard();
                entities.remove(i);
                frameEntityCount--;
                i--;
                continue;
            }
            // Mob sống ngoài vùng tương tác giữ trạng thái và tiếp tục AI khi người chơi quay lại.
            if (mobDistance > MobSpawner.MAX_DISTANCE_SQUARED && ((MobEntity) entity).isAlive()) {
                continue;
            }
            entity.update(this, deltaTime);
            if (entity == player) {
                syncMobListener();
            }

            if (entities.get(i) == entity && ((entity instanceof ItemEntity item && item.isCollected())
                    || (entity instanceof FallingBlockEntity falling && falling.isFinished())
                    || (entity instanceof ProjectileEntity projectile && projectile.isFinished())
                    || (entity instanceof LivingEntity living && living.isReadyToRemove()))) {
                entities.remove(i);
                frameEntityCount--;
                i--;
            }
        }
        mobSpawner.update(this, worldTime, deltaTime);
    }

    /** Dùng cùng đồng hồ và giáng thủy với Game Loop trước khi cập nhật thực thể. */
    public void updateEntities(float deltaTime, WorldTime worldTime, WeatherManager weather) {
        this.worldTime = worldTime;
        this.weather = weather;
        updateEntities(deltaTime);
    }

    /** Nhận hệ thống âm thanh sau khi thế giới và các tài nguyên đã sẵn sàng. */
    public void setAudio(AudioManager audio) {
        this.audio = audio;
        syncMobListener();
    }

    /** Cập nhật điểm nghe tại tâm người chơi ngay sau bước di chuyển. */
    private void syncMobListener() {
        if (audio != null && player != null) {
            player.copyCenterPosition(audioListener);
            audio.setMobListener(audioListener.x, audioListener.y, audioListener.z);
        }
    }

    /** Gắn bộ vẽ ngoại hình do ứng dụng sở hữu. */
    public void setMobRenderer(MobRenderer mobRenderer) {
        this.mobRenderer = mobRenderer;
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

    /** Tìm ô khác Air cao nhất của một cột trong bản đồ. */
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

    /** Chia dải địa tầng khác Air tại từng biên chunk; chỉ dùng khi khởi tạo thế giới. */
    void fillGeneratedColumn(int x, int z, int firstY, int endY, Block block) {
        int cx = x / Chunk.SIZE;
        int cz = z / Chunk.SIZE;
        int lx = x % Chunk.SIZE;
        int lz = z % Chunk.SIZE;
        for (int y = firstY; y < endY; ) {
            int cy = y / Chunk.SIZE;
            int originY = cy * Chunk.SIZE;
            int nextY = Math.min(endY, originY + Chunk.SIZE);
            Chunk chunk = chunks[cx][cy][cz];
            if (chunk == null) {
                chunk = new Chunk(cx, cy, cz);
                chunks[cx][cy][cz] = chunk;
            }
            chunk.fillGeneratedColumnSegment(lx, lz, y - originY, nextY - originY, block.getId());
            y = nextY;
        }
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
    public boolean hasWeatherRoof(int x, float eyeY, int z) {
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

        // AO và mặt chất lỏng có thể phụ thuộc ô ở chunk chung mặt, cạnh hoặc góc.
        int minX = lx == 0 && cx > 0 ? -1 : 0;
        int maxX = lx == Chunk.SIZE - 1 && cx + 1 < WORLD_CHUNKS_X ? 1 : 0;
        int minY = ly == 0 && cy > 0 ? -1 : 0;
        int maxY = ly == Chunk.SIZE - 1 && cy + 1 < WORLD_CHUNKS_Y ? 1 : 0;
        int minZ = lz == 0 && cz > 0 ? -1 : 0;
        int maxZ = lz == Chunk.SIZE - 1 && cz + 1 < WORLD_CHUNKS_Z ? 1 : 0;
        for (int dx = minX; dx <= maxX; dx++) {
            for (int dy = minY; dy <= maxY; dy++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        markChunkDirty(cx + dx, cy + dy, cz + dz);
                    }
                }
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

    /** Gây sát thương tỏa tròn và ghi các ô phá được bằng cùng đường cập nhật block lúc chơi. */
    public void explode(Entity source, float x, float y, float z, float radius, float maxDamage) {
        float radiusSquared = radius * radius;
        for (int i = 0, count = entities.size(); i < count; i++) {
            if (!(entities.get(i) instanceof LivingEntity living) || living == source || !living.isAlive()) {
                continue;
            }
            living.copyCenterPosition(blastTarget);
            float dx = blastTarget.x - x;
            float dz = blastTarget.z - z;
            float distanceSquared = blastTarget.dst2(x, y, z);
            if (distanceSquared < radiusSquared) {
                float power = 1f - (float) Math.sqrt(distanceSquared) / radius;
                float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
                float push = horizontal > 0.0001f ? 12f * power / horizontal : 0f;
                living.damage(this, maxDamage * power, DamageSource.EXPLOSION, source, dx * push, dz * push);
            }
        }
        int minX = Math.max(0, (int) Math.floor(x - radius));
        int minY = Math.max(0, (int) Math.floor(y - radius));
        int minZ = Math.max(0, (int) Math.floor(z - radius));
        int maxX = Math.min(WORLD_SIZE_X - 1, (int) Math.floor(x + radius));
        int maxY = Math.min(WORLD_SIZE_Y - 1, (int) Math.floor(y + radius));
        int maxZ = Math.min(WORLD_SIZE_Z - 1, (int) Math.floor(z + radius));
        for (int bx = minX; bx <= maxX; bx++) {
            float dx = bx + 0.5f - x;
            for (int by = minY; by <= maxY; by++) {
                float dy = by + 0.5f - y;
                for (int bz = minZ; bz <= maxZ; bz++) {
                    float dz = bz + 0.5f - z;
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared
                            && Block.fromId(getBlock(bx, by, bz)).isBreakable()) {
                        writeGameplayBlock(bx, by, bz, Block.AIR.getId(), 0);
                    }
                }
            }
        }
        // Chạy một đợt sau khi tất cả ô đã đổi để cát, sỏi và cây bên rìa phản ứng theo nền cuối.
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
        render(shader, projection, view, cameraPosition, beforeTranslucent, Float.POSITIVE_INFINITY);
    }

    /** Bỏ các chunk nằm hoàn toàn ngoài tầm nhìn màu sắc khi sương đạt độ đậm tối đa. */
    public void render(ShaderProgram shader, Matrix4 projection, Matrix4 view, Vector3 cameraPosition,
                       Runnable beforeTranslucent, float drawDistance) {
        // libGDX dùng nghịch đảo của Projection × View để dựng sáu mặt phẳng khung nhìn.
        inverseViewProjection.set(projection).mul(view).inv();
        frustum.update(inverseViewProjection);
        // Tâm mặt phẳng gần xấp xỉ vị trí mắt và ưu tiên được cả các chunk theo độ cao.
        nearCenter.set(0, 0, -1).prj(inverseViewProjection);
        renderFrame++;
        pendingMeshCount = 0;
        visibleChunkCount = 0;

        Vector3 eye = cameraPosition == null ? nearCenter : cameraPosition;
        collectVisibleChunks(eye, drawDistance);
        for (int i = 0; i < pendingMeshCount; i++) {
            Chunk chunk = pendingMeshes[i];
            pendingMeshes[i] = null;
            rebuildChunkMesh(chunk);
        }
        releaseOffscreenMeshes();

        int translucentCount = 0;
        Arrays.fill(opaqueHead, -1);
        for (int i = 0; i < visibleChunkCount; i++) {
            Chunk chunk = visibleChunks[i];
            float distance = chunk.centerDistanceSquaredTo(eye);
            if (chunk.hasOpaqueMesh()) {
                int bin = Math.min(OPAQUE_BINS - 1, (int) Math.sqrt(distance) / Chunk.SIZE);
                if (opaqueHead[bin] < 0) {
                    opaqueHead[bin] = i;
                } else {
                    opaqueNext[opaqueTail[bin]] = i;
                }
                opaqueTail[bin] = i;
                opaqueNext[i] = -1;
            }
            if (chunk.hasTranslucentMesh()) {
                translucentChunks[translucentCount] = chunk;
                translucentDistances[translucentCount] = distance;
                translucentKeys[translucentCount] = i;
                translucentCount++;
            }
        }
        shader.setUniformMatrix4f("uModel", identityMatrix);
        for (int head : opaqueHead) {
            for (int i = head; i >= 0; i = opaqueNext[i]) {
                visibleChunks[i].render(shader);
            }
        }
        Arrays.fill(visibleChunks, 0, visibleChunkCount, null);
        // Thực thể đục phải ghi chiều sâu trước khi vẽ nước và băng.
        boolean hasVisualMob = false;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (mobRenderer != null && entity instanceof MobVisual) {
                hasVisualMob = true;
            } else {
                entity.render(shader);
            }
        }
        if (hasVisualMob) {
            mobRenderer.render(entities, shader, frustum, eye, drawDistance);
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
    private void collectVisibleChunks(Vector3 eye, float range) {
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
        minX = Math.max(minX, eye.x - range);
        minZ = Math.max(minZ, eye.z - range);
        maxX = Math.min(maxX, eye.x + range);
        maxZ = Math.min(maxZ, eye.z + range);
        float rangeSquared = range * range;
        // Mở rộng thêm một ô ở mép dưới để tính cả chunk tiếp xúc mặt hộp bao.
        int firstX = Math.max(0, (int) Math.floor(minX / Chunk.SIZE) - 1);
        int firstZ = Math.max(0, (int) Math.floor(minZ / Chunk.SIZE) - 1);
        int lastX = Math.min(WORLD_CHUNKS_X - 1, (int) Math.floor(maxX / Chunk.SIZE));
        int lastZ = Math.min(WORLD_CHUNKS_Z - 1, (int) Math.floor(maxZ / Chunk.SIZE));
        for (int cx = firstX; cx <= lastX; cx++) {
            for (int cy = 0; cy < WORLD_CHUNKS_Y; cy++) {
                for (int cz = firstZ; cz <= lastZ; cz++) {
                    Chunk chunk = chunks[cx][cy][cz];
                    // Chunk sạch từng dựng mà không có mặt lộ chỉ cần xét lại khi được đánh dấu bẩn.
                    if (chunk != null && (chunk.isDirty() || chunk.hasMesh())
                            && chunk.distanceSquaredTo(eye) <= rangeSquared && chunk.intersectsFrustum(frustum)) {
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
