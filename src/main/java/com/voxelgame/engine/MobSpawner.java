package com.voxelgame.engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.MobCategory;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobType;

import java.util.Random;

/** Điều phối lượt sinh tự nhiên và loại quái quá xa trên luồng cập nhật World. */
public final class MobSpawner {
    /** So sánh bình phương khoảng cách 3D để tránh căn bậc hai khi kiểm tra. */
    private static final int MIN_SPAWN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 128;
    private static final int MIN_SPAWN_DISTANCE_SQUARED = MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE;
    static final int MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;
    private static final int MIN_DESPAWN_DISTANCE_SQUARED = 32 * 32;
    /** Giới hạn số điểm gốc, cột khám phá và ô thử cho mỗi bầy. */
    private static final float SPAWN_INTERVAL = 0.25f;
    private static final int MONSTER_ATTEMPTS = 4;
    private static final int POPULATION_COLUMNS = 6;
    private static final int POPULATION_ATTEMPTS = 4;
    private static final int PACK_ATTEMPTS = 16;
    private static final int CHUNK_SPAN = (MAX_DISTANCE / Chunk.SIZE + 1) * 2 + 1;
    /** Dung sai để chạm mặt đất mà không tính là đâm vào mặt đất. */
    private static final float EPSILON = 0.001f;
    /** Giới hạn các tầng thử ngầm và tầng tìm nền bên dưới tán. */
    private static final int UNDERGROUND_SPAN = 19;
    private static final int CANOPY_DEPTH = 14;
    /** Chiều cao trống gồm lưng lạc đà xác sống và không gian cho kỵ sĩ. */
    private static final float JOCKEY_HEIGHT = 4.38f;
    private static final int TICKS_PER_SECOND = 20;
    private static final int DESPAWN_DELAY_TICKS = 600;
    private static final int DESPAWN_CHANCE_DIVISOR = 800;

    private final long[] populated = new long[(World.WORLD_CHUNKS_X * World.WORLD_CHUNKS_Z + 63) >>> 6];
    private final Random spawnRandom;
    private final Random despawnRandom;
    /** Bộ đệm vị trí người chơi dùng lại trên luồng World. */
    private final Vector3 playerPosition = new Vector3();
    private int populationCursor;
    private float spawnElapsed;

    /** Tách ngẫu nhiên biến mất khỏi lượt sinh và bầy khám phá. */
    MobSpawner(long seed) {
        spawnRandom = new Random(seed);
        despawnRandom = new Random(seed ^ 0x643B7935L);
    }

    /** Thử số điểm hữu hạn mỗi nhịp, bỏ qua nhóm chưa đăng ký hàm tạo. */
    void update(World world, WorldTime time, float dt) {
        Player player = world.getPlayer();
        if (player == null || !player.isAlive() || !(dt > 0) || !Float.isFinite(dt)) {
            return;
        }
        spawnElapsed += dt;
        if (spawnElapsed < SPAWN_INTERVAL) {
            return;
        }
        spawnElapsed %= SPAWN_INTERVAL;
        boolean creatures = MobType.anyImplemented(MobCategory.CREATURE);
        boolean monsters = MobType.anyImplemented(MobCategory.MONSTER);
        if (!creatures && !monsters) {
            return;
        }

        int creatureCount = 0;
        int monsterCount = 0;
        for (int i = 0; i < world.entityCount(); i++) {
            if (world.entityAt(i) instanceof MobEntity mob && mob.isAlive()
                    && distanceSquared(mob, player) <= MAX_DISTANCE_SQUARED) {
                if (mob.type().category() == MobCategory.MONSTER) {
                    monsterCount++;
                } else {
                    creatureCount++;
                }
            }
        }
        int monsterRoom = MobCategory.MONSTER.cap() - monsterCount;
        for (int i = 0; monsters && i < MONSTER_ATTEMPTS && monsterRoom > 0; i++) {
            monsterRoom -= sampleAroundPlayer(world, time, MobCategory.MONSTER, monsterRoom);
        }
        if (creatures) {
            creatureCount += populateNearbyChunks(world, time);
            int creatureRoom = MobCategory.CREATURE.cap() - creatureCount;
            if (creatureRoom > 0) {
                sampleAroundPlayer(world, time, MobCategory.CREATURE, creatureRoom);
            }
        }
    }

    /** Duyệt dần tọa độ chunk mới trong vành hoạt động, ghi một bit sau lần xét đầu tiên. */
    private int populateNearbyChunks(World world, WorldTime time) {
        Vector3 player = world.getPlayer().copyPosition(playerPosition);
        int centerChunkX = Math.floorDiv(MathUtils.floor(player.x), Chunk.SIZE);
        int centerChunkZ = Math.floorDiv(MathUtils.floor(player.z), Chunk.SIZE);
        int created = 0;
        for (int i = 0; i < POPULATION_COLUMNS; i++) {
            int chunkX = centerChunkX + populationCursor / CHUNK_SPAN - CHUNK_SPAN / 2;
            int chunkZ = centerChunkZ + populationCursor % CHUNK_SPAN - CHUNK_SPAN / 2;
            populationCursor = (populationCursor + 1) % (CHUNK_SPAN * CHUNK_SPAN);
            if (chunkX < 0 || chunkX >= World.WORLD_CHUNKS_X || chunkZ < 0 || chunkZ >= World.WORLD_CHUNKS_Z) {
                continue;
            }
            int x0 = chunkX * Chunk.SIZE;
            int z0 = chunkZ * Chunk.SIZE;
            int centerX = Math.min(World.WORLD_SIZE_X - 1, x0 + Chunk.SIZE / 2);
            int centerZ = z0 + Chunk.SIZE / 2;
            float dx = centerX + 0.5f - player.x;
            float dz = centerZ + 0.5f - player.z;
            float horizontal = dx * dx + dz * dz;
            if (horizontal > MAX_DISTANCE_SQUARED) {
                continue;
            }
            int bit = chunkX * World.WORLD_CHUNKS_Z + chunkZ;
            int index = bit >>> 6;
            long mask = 1L << (bit & 63);
            if ((populated[index] & mask) != 0L) {
                continue;
            }
            float surface = world.getPrecipitationSurface(centerX, centerZ);
            float dy = surface - player.y;
            float distance = horizontal + dy * dy;
            if (surface <= 0 || distance < MIN_SPAWN_DISTANCE_SQUARED || distance > MAX_DISTANCE_SQUARED) {
                continue;
            }
            BiomeType biome = world.getBiome(centerX, centerZ);
            if (!MobSpawnTable.anyImplementedCreatures(biome) || biome == BiomeType.PLAINS && !isDaylight(time)) {
                continue;
            }
            populated[index] |= mask;
            if (spawnRandom.nextInt(10) != 0) {
                continue;
            }
            for (int attempt = 0; attempt < POPULATION_ATTEMPTS; attempt++) {
                int x = x0 + spawnRandom.nextInt(Chunk.SIZE);
                int z = z0 + spawnRandom.nextInt(Chunk.SIZE);
                int count = spawnPack(world, time, MobCategory.CREATURE, x, z, Integer.MAX_VALUE);
                if (count > 0) {
                    created += count;
                    break;
                }
            }
        }
        return created;
    }

    /** Chọn ô ngang bằng lấy mẫu đều trên hình vuông rồi loại điểm ngoài vành. */
    private int sampleAroundPlayer(World world, WorldTime time, MobCategory category, int room) {
        Vector3 player = world.getPlayer().copyPosition(playerPosition);
        int dx = spawnRandom.nextInt(2 * MAX_DISTANCE + 1) - MAX_DISTANCE;
        int dz = spawnRandom.nextInt(2 * MAX_DISTANCE + 1) - MAX_DISTANCE;
        return spawnPack(world, time, category, MathUtils.floor(player.x) + dx, MathUtils.floor(player.z) + dz, room);
    }

    /** Chọn loài một lần và thử sinh các thành viên gần điểm gốc cùng biome và phân vùng. */
    private int spawnPack(World world, WorldTime time, MobCategory category, int x, int z, int room) {
        if (!world.isInsideWorld(x, 0, z)) {
            return 0;
        }
        Vector3 player = world.getPlayer().copyPosition(playerPosition);
        float dx = x + 0.5f - player.x;
        float dz = z + 0.5f - player.z;
        if (dx * dx + dz * dz > MAX_DISTANCE_SQUARED) {
            return 0;
        }
        BiomeType biome = world.getBiome(x, z);
        if (category == MobCategory.CREATURE && biome == BiomeType.PLAINS && !isDaylight(time)) {
            return 0;
        }
        float surface = world.getPrecipitationSurface(x, z);
        boolean nearSurface = category == MobCategory.CREATURE || isNightDark(time) && spawnRandom.nextInt(4) != 0;
        int topY = MathUtils.floor(nearSurface ? surface - EPSILON
                : Math.min(surface - 1, MathUtils.floor(player.y) + spawnRandom.nextInt(11)));
        if (topY < 0) {
            return 0;
        }
        if (category == MobCategory.CREATURE) {
            topY = terrainBelowCanopy(world, x, topY, z);
        }
        SnowSubZone zone = category == MobCategory.CREATURE && biome == BiomeType.SNOWY_PLAINS
                ? SnowSubZone.classify(world, x, topY + 1, z) : null;
        MobSpawnTable table = MobSpawnTable.forBiome(biome, category, zone);
        MobSpawnTable.Entry entry = table.select(spawnRandom.nextInt(table.totalWeight()));
        MobType type = entry.type();
        if (!type.implemented()) {
            return 0;
        }

        float feet = findSpawnFeet(world, time, type, x, z, topY, nearSurface ? CANOPY_DEPTH : UNDERGROUND_SPAN);
        if (feet < 0 || !matchesSnowZone(world, zone, x, feet, z)) {
            return 0;
        }
        int desired = entry.minPackSize() + spawnRandom.nextInt(entry.maxPackSize() - entry.minPackSize() + 1);
        int first = spawnMember(world, type, x + 0.5f, feet, z + 0.5f, room);
        int created = first;
        int members = first == 0 ? 0 : 1;
        for (int attempt = 0; attempt < PACK_ATTEMPTS && members < desired && created < room; attempt++) {
            int packX = x + spawnRandom.nextInt(11) - 5;
            int packZ = z + spawnRandom.nextInt(11) - 5;
            if (!world.isInsideWorld(packX, 0, packZ) || world.getBiome(packX, packZ) != biome) {
                continue;
            }
            int packTop = MathUtils.floor(feet + 2 - EPSILON);
            float packFeet = findSpawnFeet(world, time, type, packX, packZ, packTop, 5);
            if (packFeet < 0 || !matchesSnowZone(world, zone, packX, packFeet, packZ)) {
                continue;
            }
            int added = spawnMember(world, type, packX + 0.5f, packFeet, packZ + 0.5f, room - created);
            if (added > 0) {
                created += added;
                members++;
            }
        }
        return created;
    }

    /** Bỏ qua tán hoặc thân cây khi lấy cao độ để phân vùng địa hình sinh thú. */
    private static int terrainBelowCanopy(World world, int x, int topY, int z) {
        for (int y = topY; y >= Math.max(0, topY - CANOPY_DEPTH); y--) {
            Block block = Block.fromId(world.getBlock(x, y, z));
            if (block != Block.OAK_LEAVES && block != Block.SPRUCE_LEAVES
                    && block != Block.OAK_LOG && block != Block.SPRUCE_LOG
                    && (block.isLiquid() || world.getCollisionBounds(x, y, z) != null)) {
                return y;
            }
        }
        return topY;
    }

    /** Chỉ áp dụng phân vùng tuyết cho các loại thú có bảng riêng. */
    private static boolean matchesSnowZone(World world, SnowSubZone zone, int x, float feet, int z) {
        return zone == null || zone == SnowSubZone.classify(world, x, MathUtils.ceil(feet), z);
    }

    /** Duyệt một đoạn cột ngắn cho mặt cỏ, dưới tán hoặc dải tầng hầm đã chọn. */
    private float findSpawnFeet(World world, WorldTime time, MobType type, int x, int z, int topY, int depth) {
        int bottom = Math.max(0, topY - depth + 1);
        for (int y = Math.min(topY, World.WORLD_SIZE_Y - 1); y >= bottom; y--) {
            Block ground = Block.fromId(world.getBlock(x, y, z));
            Block.Bounds bounds = ground.getCollisionBounds(world.getBlockState(x, y, z));
            if (bounds != null) {
                float feet = y + bounds.maxY();
                int feetCell = MathUtils.floor(feet + EPSILON);
                Block.Bounds above = feetCell == y ? null : world.getCollisionBounds(x, feetCell, z);
                if ((above == null || above.minY() > EPSILON) && canSpawn(world, type, x + 0.5f, feet, z + 0.5f, time)) {
                    return feet;
                }
            }
        }
        return -1;
    }

    /** Ghi một cá thể hoặc ba thành viên kỵ sĩ sau khi kiểm tra khoảng trống và hạn mức. */
    private int spawnMember(World world, MobType type, float x, float y, float z, int room) {
        if (type == MobType.HUSK && room >= 3 && MobType.CAMEL_HUSK.implemented()
                && MobType.PARCHED.implemented() && spawnRandom.nextInt(10) == 0
                && hasClearance(world, x, y, z, MobType.CAMEL_HUSK.width(), JOCKEY_HEIGHT)
                && spawnJockey(world, x, y, z)) {
            return 3;
        }
        if (type == MobType.SPIDER && room >= 2 && spawnRandom.nextInt(100) == 0
                && spawnSpiderJockey(world, x, y, z)) {
            return 2;
        }
        MobEntity mob = type.create(x, y, z);
        if (mob == null) {
            return 0;
        }
        world.addEntity(mob);
        return 1;
    }

    /** Chọn cung thủ bản địa trên bề mặt và Skeleton thường trong phòng kín. */
    boolean spawnSpiderJockey(World world, float x, float y, float z) {
        int bx = MathUtils.floor(x);
        int bz = MathUtils.floor(z);
        MobType riderType = MobType.SKELETON;
        if (!world.hasWeatherRoof(bx, y + 1f, bz)) {
            riderType = switch (world.getBiome(bx, bz)) {
                case PLAINS -> MobType.SKELETON;
                case DESERT -> MobType.PARCHED;
                case SNOWY_PLAINS -> MobType.STRAY;
            };
        }
        float riderY = y + MobType.SPIDER.height();
        if (!riderType.implemented() || !hasClearance(world, x, y, z, MobType.SPIDER.width(),
                riderY - y + riderType.height())) {
            return false;
        }
        MobEntity mount = MobType.SPIDER.create(x, y, z);
        MobEntity rider = riderType.create(x, riderY, z);
        if (mount == null || rider == null || !rider.tryMount(mount, 0)) {
            return false;
        }
        world.addEntity(mount);
        world.addEntity(rider);
        return true;
    }

    /** Dựng bộ kỵ sĩ đồng thời, chỉ đăng ký vào World khi cả hai ghế đã gắn thành công. */
    private static boolean spawnJockey(World world, float x, float y, float z) {
        MobEntity mount = MobType.CAMEL_HUSK.create(x, y, z);
        if (mount == null || mount.getSeatCount() < 2) {
            return false;
        }
        float seatY = y + MobType.CAMEL_HUSK.height();
        MobEntity front = MobType.HUSK.create(x, seatY, z);
        MobEntity back = MobType.PARCHED.create(x, seatY, z);
        if (front == null || back == null || !front.tryMount(mount, 0) || !back.tryMount(mount, 1)) {
            return false;
        }
        world.addEntity(mount);
        world.addEntity(front);
        world.addEntity(back);
        return true;
    }

    /** Kiểm tra khoảng cách, ánh sáng, nền và hộp va chạm ở vị trí chân được đề xuất. */
    boolean canSpawn(World world, MobType type, float x, float y, float z, WorldTime time) {
        Player player = world.getPlayer();
        if (player == null) {
            return false;
        }
        float distance = player.copyPosition(playerPosition).dst2(x, y, z);
        if (distance < MIN_SPAWN_DISTANCE_SQUARED || distance > MAX_DISTANCE_SQUARED) {
            return false;
        }
        int blockX = MathUtils.floor(x);
        int blockY = MathUtils.floor(y - EPSILON);
        int blockZ = MathUtils.floor(z);
        if (!world.isInsideWorld(blockX, blockY, blockZ)) {
            return false;
        }
        Block ground = Block.fromId(world.getBlock(blockX, blockY, blockZ));
        Block.Bounds support = ground.getCollisionBounds(world.getBlockState(blockX, blockY, blockZ));
        if (support == null || Math.abs(blockY + support.maxY() - y) >= EPSILON) {
            return false;
        }
        Block base = ground == Block.SNOW_LAYER ? Block.fromId(world.getBlock(blockX, blockY - 1, blockZ)) : ground;
        if (base == Block.BEDROCK || base == Block.CACTUS || base == Block.OAK_LEAVES || base == Block.SPRUCE_LEAVES
                || base == Block.ICE && type != MobType.POLAR_BEAR) {
            return false;
        }
        if (type.category() == MobCategory.MONSTER) {
            if (!isNightDark(time) && !world.hasWeatherRoof(blockX, y + 0.5f, blockZ)) {
                return false;
            }
            if (requiresOpenSky(type) && world.hasWeatherRoof(blockX, y + 1, blockZ)) {
                return false;
            }
        } else if (!allowsCreatureGround(type, ground, world, blockX, blockY, blockZ, time)) {
            return false;
        }
        return hasClearance(world, x, y, z, type.width(), type.height());
    }

    /** Các biến thể khí hậu đêm chỉ sinh trực tiếp dưới bầu trời. */
    private static boolean requiresOpenSky(MobType type) {
        return type == MobType.HUSK || type == MobType.PARCHED || type == MobType.STRAY || type == MobType.CAMEL_HUSK;
    }

    /** Kiểm tra nền của thú theo loài và khí hậu, kể cả tuyết mỏng nằm trên nền cứng. */
    private static boolean allowsCreatureGround(MobType type, Block ground, World world, int x, int y, int z,
                                                WorldTime time) {
        BiomeType biome = world.getBiome(x, z);
        if (biome == BiomeType.PLAINS) {
            return ground == Block.GRASS && isDaylight(time);
        }
        if (biome == BiomeType.DESERT) {
            return ground == Block.SAND || ground == Block.GRASS;
        }
        boolean snow = ground == Block.SNOW_BLOCK || ground == Block.SNOW_LAYER
                || world.getBlock(x, y + 1, z) == Block.SNOW_LAYER.getId();
        if (type == MobType.POLAR_BEAR) {
            return snow || ground == Block.ICE;
        }
        if (type == MobType.GOAT) {
            return snow || ground == Block.STONE || ground == Block.GRAVEL || ground == Block.GRASS;
        }
        return snow || ground == Block.GRASS;
    }

    /** Quét hộp va chạm nguyên chiều rộng và cao, chặn cả chất lỏng và thực thể sống. */
    private boolean hasClearance(World world, float x, float y, float z, float width, float height) {
        float half = width * 0.5f;
        float minX = x - half;
        float maxX = x + half;
        float minZ = z - half;
        float maxZ = z + half;
        float top = y + height;
        if (minX < 0 || maxX > World.WORLD_SIZE_X || minZ < 0 || maxZ > World.WORLD_SIZE_Z || top > World.WORLD_SIZE_Y) {
            return false;
        }
        int y1 = MathUtils.floor(top - EPSILON);
        int z0 = MathUtils.floor(minZ + EPSILON);
        int z1 = MathUtils.floor(maxZ - EPSILON);
        int x1 = MathUtils.floor(maxX - EPSILON);
        for (int bx = MathUtils.floor(minX + EPSILON); bx <= x1; bx++) {
            for (int by = MathUtils.floor(y + EPSILON); by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    Block block = Block.fromId(world.getBlock(bx, by, bz));
                    Block.Bounds bounds = block.getCollisionBounds(world.getBlockState(bx, by, bz));
                    if (block.isLiquid() || block == Block.CACTUS || bounds != null
                            && bx + bounds.maxX() > minX + EPSILON && bx + bounds.minX() < maxX - EPSILON
                            && bz + bounds.maxZ() > minZ + EPSILON && bz + bounds.minZ() < maxZ - EPSILON
                            && by + bounds.maxY() > y + EPSILON && by + bounds.minY() < top - EPSILON) {
                        return false;
                    }
                }
            }
        }
        return !world.hasLivingOverlap(x, y, z, half, height, null);
    }

    /** Đánh giá bóng đêm theo tick và cao độ mặt trời hiện có, không thêm lưới ánh sáng. */
    public static boolean isNightDark(WorldTime time) {
        int tick = time.tickOfDay();
        return (tick >= 13_000 && tick <= 23_000) || time.sunY() < -0.15f;
    }

    /** Ban ngày từ bình minh tới hoàng hôn là điều kiện sinh thú đồng bằng. */
    private static boolean isDaylight(WorldTime time) {
        return time.tickOfDay() < 12_000;
    }

    /** Tính khoảng cách chân trong vành hoạt động mà không tạo bản sao tọa độ. */
    float distanceSquared(MobEntity mob, Player player) {
        // Các ghế cùng vùng hoạt động với thú cưỡi, kể cả ở sát ranh 128 block.
        MobEntity origin = mob.getMount() instanceof MobEntity mount ? mount : mob;
        return player.copyPosition(playerPosition).dst2(origin.x(), origin.y(), origin.z());
    }

    /** Hai loại thú cưỡi tự nhiên được thu hồi cùng kỵ sĩ chưa lưu giữ. */
    static boolean isNaturalJockeyMount(MobEntity mob) {
        return mob.type() == MobType.CAMEL_HUSK || mob.type() == MobType.SPIDER;
    }

    /** Kiểm tra tức thì ở xa và rút thăm ở từng tick 20 Hz đã trôi sau 30 giây. */
    boolean shouldDespawn(MobEntity mob, float distance, float dt) {
        if (!mob.isAlive() || mob.type().category() != MobCategory.MONSTER || mob.isPersistent()) {
            return false;
        }
        // Lạc đà xác sống quyết định khi nào thu hồi cả bộ kỵ sĩ.
        if (mob.getMount() instanceof MobEntity mount && isNaturalJockeyMount(mount)) {
            return false;
        }
        if (distance > MAX_DISTANCE_SQUARED) {
            return true;
        }
        if (distance < MIN_DESPAWN_DISTANCE_SQUARED) {
            mob.setNoActionTime(0);
            return false;
        }
        if (!(dt > 0) || !Float.isFinite(dt)) {
            return false;
        }
        float previous = mob.noActionTime();
        float current = previous + dt;
        mob.setNoActionTime(current);
        int from = Math.max(DESPAWN_DELAY_TICKS, (int) (previous * TICKS_PER_SECOND));
        int to = (int) (current * TICKS_PER_SECOND);
        for (int tick = from; tick < to; tick++) {
            if (despawnRandom.nextInt(DESPAWN_CHANCE_DIVISOR) == 0) {
                return true;
            }
        }
        return false;
    }
}
