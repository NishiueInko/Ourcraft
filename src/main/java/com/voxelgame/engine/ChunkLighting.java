package com.voxelgame.engine;

import com.voxelgame.entity.Block;

/**
 * Hệ thống tính toán ánh sáng 4-bit cho thế giới Voxel gồm hai kênh độc lập:
 * <ul>
 *   <li><b>Sky Light (0–15):</b> Ánh sáng bầu trời chiếu thẳng đứng từ đỉnh thế giới xuống,
 *       không suy giảm qua không khí, suy giảm -1 qua lá cây, bị chặn về 0 bởi khối đục
 *       ({@link Block#isOpaque()}), và lan truyền 6 hướng qua BFS.</li>
 *   <li><b>Block Light (0–15):</b> Ánh sáng phát ra từ nguồn sáng khối ({@link Block#LAVA} mức 15),
 *       lan truyền 6 hướng giảm 1 mỗi bước qua các ô không đục.</li>
 * </ul>
 * Thuật toán sử dụng hàng đợi vòng (ring-buffer) mảng nguyên thủy {@code int[]} cấp phát sẵn
 * và bộ đệm cột tái sử dụng để đạt <b>zero-GC</b> trong vòng lặp cập nhật gameplay.
 */
public final class ChunkLighting {

    /** Mức sáng tối đa của mỗi kênh (4-bit: 0..15). */
    public static final int MAX_LIGHT = 15;
    /** Ngưỡng sáng tối thiểu để các mặt trong hang tối vẫn nhìn rõ đường nét khối. */
    public static final float MIN_LIGHT_LEVEL = 0.04f;

    /** Dung lượng khởi tạo của hàng đợi vòng BFS (lũy thừa của 2 để chia lấy dư bằng bitmask). */
    private static final int INITIAL_QUEUE_CAPACITY = 65_536;

    // Hàng đợi vòng dùng chung cho pha lan truyền tăng sáng (Sky và Block chạy nối tiếp nhau).
    private int[] addQueue = new int[INITIAL_QUEUE_CAPACITY];
    private int addHead;
    private int addTail;
    private int addCount;

    // Hàng đợi vòng dùng cho pha xóa sáng khi đặt khối cản sáng hoặc phá nguồn sáng.
    private int[] removePosQueue = new int[INITIAL_QUEUE_CAPACITY];
    private byte[] removeValQueue = new byte[INITIAL_QUEUE_CAPACITY];
    private int removeHead;
    private int removeTail;
    private int removeCount;

    // Bộ đệm tái sử dụng khi quét ánh sáng dọc cho một cột chunk 16x256x16 (zero-GC).
    private final byte[][] columnScratch = new byte[World.WORLD_CHUNKS_Y][Chunk.SIZE * Chunk.SIZE * Chunk.SIZE];
    private final byte[] uniformLightValue = new byte[World.WORLD_CHUNKS_Y];
    private final boolean[] uniformChunkFlags = new boolean[World.WORLD_CHUNKS_Y];

    /** Theo dõi các cột chunk (cx, cz) đã được tính ánh sáng cột + nguồn sáng lân cận. */
    private final boolean[][] columnLit = new boolean[World.WORLD_CHUNKS_X][World.WORLD_CHUNKS_Z];

    /** Trả mức Sky Light (0..15) tại tọa độ thế giới, tự động khởi tạo lười cột chunk nếu cần. */
    public int getSkyLight(World world, int x, int y, int z) {
        if (y >= World.WORLD_SIZE_Y) {
            return MAX_LIGHT;
        }
        if (y < 0) {
            return 0;
        }
        if (x < 0 || x >= World.WORLD_SIZE_X || z < 0 || z >= World.WORLD_SIZE_Z) {
            return MAX_LIGHT;
        }
        ensureColumnLit(world, x / Chunk.SIZE, z / Chunk.SIZE);
        return getSkyLightRaw(world, x, y, z);
    }

    /** Trả mức Block Light (0..15) tại tọa độ thế giới, tự động khởi tạo lười cột chunk nếu cần. */
    public int getBlockLight(World world, int x, int y, int z) {
        if (!world.isInsideWorld(x, y, z)) {
            return 0;
        }
        ensureColumnLit(world, x / Chunk.SIZE, z / Chunk.SIZE);
        return getBlockLightRaw(world, x, y, z);
    }

    /** Ghi mức Sky Light (0..15) tại tọa độ thế giới và đánh dấu bẩn các chunk bị ảnh hưởng. */
    public boolean setSkyLight(World world, int x, int y, int z, int level) {
        if (!world.isInsideWorld(x, y, z)) {
            return false;
        }
        ensureColumnLit(world, x / Chunk.SIZE, z / Chunk.SIZE);
        return setSkyLightRaw(world, x, y, z, clampLight(level));
    }

    /** Ghi mức Block Light (0..15) tại tọa độ thế giới và đánh dấu bẩn các chunk bị ảnh hưởng. */
    public boolean setBlockLight(World world, int x, int y, int z, int level) {
        if (!world.isInsideWorld(x, y, z)) {
            return false;
        }
        ensureColumnLit(world, x / Chunk.SIZE, z / Chunk.SIZE);
        return setBlockLightRaw(world, x, y, z, clampLight(level));
    }

    /**
     * Tính lại Sky Light chiếu thẳng cho cột (wx, wz) từ đỉnh thế giới xuống và lan truyền BFS.
     */
    public void recomputeColumnSkyLight(World world, int wx, int wz) {
        if (!world.isInsideWorld(wx, 0, wz)) {
            return;
        }
        int cx = wx / Chunk.SIZE;
        int cz = wz / Chunk.SIZE;
        ensureColumnLit(world, cx, cz);
        int topCy = highestNonNullChunkY(world, cx, cz);
        if (topCy >= 0) {
            int startY = Math.min(World.WORLD_SIZE_Y - 1, (topCy + 1) * Chunk.SIZE - 1);
            updateSkyColumnAndBfs(world, wx, startY, wz);
        }
    }

    /**
     * Lan truyền lại ánh sáng xung quanh tọa độ thế giới (wx, wy, wz) bằng hàng đợi vòng BFS.
     */
    public void propagateLight(World world, int wx, int wy, int wz) {
        if (!world.isInsideWorld(wx, wy, wz)) {
            return;
        }
        int cx = wx / Chunk.SIZE;
        int cz = wz / Chunk.SIZE;
        ensureColumnLit(world, cx, cz);
        Block block = Block.fromId(world.getBlock(wx, wy, wz));
        int emit = emissionOf(block);
        if (emit > 0) {
            setBlockLightRaw(world, wx, wy, wz, emit);
            pushAdd(wx, wy, wz);
        } else if (!block.isOpaque()) {
            enqueueBrighterBlockNeighbors(world, wx, wy, wz);
        }
        drainBlockIncreaseQueue(world);
        updateSkyColumnAndBfs(world, wx, wy, wz);
    }

    /** Kiểm tra xem cột chunk (cx, cz) đã được khởi tạo dữ liệu ánh sáng hay chưa. */
    boolean isColumnLit(int cx, int cz) {
        return cx >= 0 && cx < World.WORLD_CHUNKS_X
                && cz >= 0 && cz < World.WORLD_CHUNKS_Z
                && columnLit[cx][cz];
    }

    /** Đánh dấu cột chunk cần tính lại ánh sáng khi dữ liệu bị sửa trực tiếp ngoài gameplay. */
    void invalidateColumn(int cx, int cz) {
        if (cx >= 0 && cx < World.WORLD_CHUNKS_X && cz >= 0 && cz < World.WORLD_CHUNKS_Z) {
            columnLit[cx][cz] = false;
        }
    }

    /**
     * Đảm bảo cột chunk (cx, cz) và vành đai 8 cột lân cận (cx±1, cz±1) đã khởi tạo ánh sáng
     * và hoàn tất lan truyền qua biên chunk. Vì tầm lan tối đa là 15 (< 16), vành đai 1 chunk
     * đảm bảo mọi nguồn sáng có thể chạm tới (cx, cz) đều đã hội tụ.
     */
    void ensureColumnLit(World world, int cx, int cz) {
        int minCx = Math.max(0, cx - 1);
        int maxCx = Math.min(World.WORLD_CHUNKS_X - 1, cx + 1);
        int minCz = Math.max(0, cz - 1);
        int maxCz = Math.min(World.WORLD_CHUNKS_Z - 1, cz + 1);

        boolean allLit = true;
        for (int nx = minCx; nx <= maxCx && allLit; nx++) {
            for (int nz = minCz; nz <= maxCz; nz++) {
                if (!columnLit[nx][nz]) {
                    allLit = false;
                    break;
                }
            }
        }
        if (allLit) {
            return;
        }

        // Bitmask 9 bit đánh dấu cột nào trong vùng 3x3 vừa được khởi tạo mới ở lượt này.
        int newlyLitMask = 0;
        for (int nx = minCx; nx <= maxCx; nx++) {
            for (int nz = minCz; nz <= maxCz; nz++) {
                if (!columnLit[nx][nz]) {
                    initColumnVerticalAndSources(world, nx, nz);
                    columnLit[nx][nz] = true;
                    int bit = (nx - minCx) * 3 + (nz - minCz);
                    newlyLitMask |= (1 << bit);
                }
            }
        }

        // Gieo hạt BFS Sky Light cho các cột vừa khởi tạo và mép tiếp giáp cột đã sáng trước đó.
        clearAddQueue();
        for (int nx = minCx; nx <= maxCx; nx++) {
            for (int nz = minCz; nz <= maxCz; nz++) {
                int bit = (nx - minCx) * 3 + (nz - minCz);
                if ((newlyLitMask & (1 << bit)) != 0) {
                    seedSkyBfsForColumn(world, nx, nz);
                }
            }
        }
        drainSkyIncreaseQueue(world);

        // Gieo hạt BFS Block Light từ các khối Lava và mép tiếp giáp cột đã sáng trước đó.
        clearAddQueue();
        for (int nx = minCx; nx <= maxCx; nx++) {
            for (int nz = minCz; nz <= maxCz; nz++) {
                int bit = (nx - minCx) * 3 + (nz - minCz);
                if ((newlyLitMask & (1 << bit)) != 0) {
                    seedBlockBfsForColumn(world, nx, nz);
                }
            }
        }
        drainBlockIncreaseQueue(world);
    }

    /**
     * Cập nhật ánh sáng tăng/giảm cục bộ khi một ô tại (wx, wy, wz) thay đổi loại block.
     */
    void onBlockChanged(World world, int wx, int wy, int wz, byte oldId, byte newId) {
        int cx = wx / Chunk.SIZE;
        int cz = wz / Chunk.SIZE;
        if (!isColumnLit(cx, cz)) {
            return;
        }
        ensureColumnLit(world, cx, cz);

        if (oldId != newId) {
            updateSkyColumnAndBfs(world, wx, wy, wz);
            updateBlockLightOnChange(world, wx, wy, wz, newId);
        }
    }

    /**
     * Khởi tạo chiếu thẳng Sky Light và phát hiện nguồn Lava trên toàn cột chunk (cx, cz),
     * giữ `light = null` cho các section đồng nhất để tiết kiệm bộ nhớ RAM.
     */
    private void initColumnVerticalAndSources(World world, int cx, int cz) {
        int topCy = highestNonNullChunkY(world, cx, cz);
        if (topCy < 0) {
            return;
        }

        for (int cy = 0; cy <= topCy; cy++) {
            uniformChunkFlags[cy] = true;
        }

        for (int lx = 0; lx < Chunk.SIZE; lx++) {
            for (int lz = 0; lz < Chunk.SIZE; lz++) {
                int sky = MAX_LIGHT;
                for (int cy = topCy; cy >= 0; cy--) {
                    Chunk chunk = world.getChunkRaw(cx, cy, cz);
                    for (int ly = Chunk.SIZE - 1; ly >= 0; ly--) {
                        byte id = chunk != null ? chunk.getBlockLocal(lx, ly, lz) : 0;
                        int emit = 0;
                        if (id != 0) {
                            Block block = Block.fromId(id);
                            if (block.isOpaque()) {
                                sky = 0;
                            } else if (isLeaves(block)) {
                                if (sky > 0) {
                                    sky--;
                                }
                            }
                            if (block == Block.LAVA) {
                                emit = MAX_LIGHT;
                            }
                        }
                        byte packed = (byte) ((sky << 4) | emit);
                        int index = (lx * Chunk.SIZE + ly) * Chunk.SIZE + lz;
                        columnScratch[cy][index] = packed;
                        if (lx == 0 && lz == 0 && ly == Chunk.SIZE - 1) {
                            uniformLightValue[cy] = packed;
                        } else if (packed != uniformLightValue[cy]) {
                            uniformChunkFlags[cy] = false;
                        }
                    }
                }
            }
        }

        for (int cy = 0; cy <= topCy; cy++) {
            Chunk chunk = world.getChunkRaw(cx, cy, cz);
            if (uniformChunkFlags[cy]) {
                if (chunk == null) {
                    if (uniformLightValue[cy] != Chunk.DEFAULT_LIGHT) {
                        chunk = world.getOrCreateLightChunk(cx, cy, cz, true);
                        chunk.setUniformLight(uniformLightValue[cy]);
                    }
                } else {
                    chunk.setUniformLight(uniformLightValue[cy]);
                }
            } else {
                if (chunk == null) {
                    chunk = world.getOrCreateLightChunk(cx, cy, cz, true);
                }
                chunk.applyLightArray(columnScratch[cy]);
            }
        }
    }

    /** Đưa các ô biên có độ sáng lớn hơn hàng xóm vào hàng đợi BFS Sky Light. */
    private void seedSkyBfsForColumn(World world, int cx, int cz) {
        int maxCy = maxLitNeighborhoodChunkY(world, cx, cz);
        if (maxCy < 0) {
            return;
        }
        int maxY = Math.min(World.WORLD_SIZE_Y - 1, (maxCy + 1) * Chunk.SIZE - 1);
        int baseX = cx * Chunk.SIZE;
        int baseZ = cz * Chunk.SIZE;
        int endX = Math.min(World.WORLD_SIZE_X, baseX + Chunk.SIZE);
        int endZ = Math.min(World.WORLD_SIZE_Z, baseZ + Chunk.SIZE);

        for (int wx = baseX; wx < endX; wx++) {
            for (int wz = baseZ; wz < endZ; wz++) {
                boolean onEdgeX = (wx == baseX || wx == endX - 1);
                boolean onEdgeZ = (wz == baseZ || wz == endZ - 1);
                for (int y = 0; y <= maxY; y++) {
                    int sky = getSkyLightRaw(world, wx, y, wz);
                    if (sky > 1 && hasDarkerSkyNeighbor(world, wx, y, wz, sky)) {
                        pushAdd(wx, y, wz);
                    }
                    // Nếu đang đứng ở mép cột mới khởi tạo, kiểm tra ánh sáng từ cột đã sáng kề bên tràn vào.
                    if ((onEdgeX || onEdgeZ) && !Block.fromId(world.getBlock(wx, y, wz)).isOpaque()) {
                        enqueueBrighterEdgeSkyNeighbor(world, wx, y, wz, sky, baseX, endX, baseZ, endZ);
                    }
                }
            }
        }
    }

    /** Đưa các nguồn Lava và ánh sáng Block Light ở biên cột đã sáng vào hàng đợi BFS. */
    private void seedBlockBfsForColumn(World world, int cx, int cz) {
        int maxCy = maxLitNeighborhoodChunkY(world, cx, cz);
        if (maxCy < 0) {
            return;
        }
        int maxY = Math.min(World.WORLD_SIZE_Y - 1, (maxCy + 1) * Chunk.SIZE - 1);
        int baseX = cx * Chunk.SIZE;
        int baseZ = cz * Chunk.SIZE;
        int endX = Math.min(World.WORLD_SIZE_X, baseX + Chunk.SIZE);
        int endZ = Math.min(World.WORLD_SIZE_Z, baseZ + Chunk.SIZE);

        for (int wx = baseX; wx < endX; wx++) {
            for (int wz = baseZ; wz < endZ; wz++) {
                boolean onEdgeX = (wx == baseX || wx == endX - 1);
                boolean onEdgeZ = (wz == baseZ || wz == endZ - 1);
                for (int y = 0; y <= maxY; y++) {
                    int blockLight = getBlockLightRaw(world, wx, y, wz);
                    if (blockLight > 1) {
                        pushAdd(wx, y, wz);
                    }
                    if ((onEdgeX || onEdgeZ) && !Block.fromId(world.getBlock(wx, y, wz)).isOpaque()) {
                        enqueueBrighterEdgeBlockNeighbor(world, wx, y, wz, blockLight, baseX, endX, baseZ, endZ);
                    }
                }
            }
        }
    }

    /** Kiểm tra 6 hướng xem có ô nào nhận được thêm Sky Light từ (x, y, z) không. */
    private boolean hasDarkerSkyNeighbor(World world, int x, int y, int z, int sky) {
        int threshold = sky - 1;
        return canAcceptSky(world, x - 1, y, z, threshold)
                || canAcceptSky(world, x + 1, y, z, threshold)
                || canAcceptSky(world, x, y - 1, z, sky == MAX_LIGHT ? MAX_LIGHT : threshold)
                || canAcceptSky(world, x, y + 1, z, threshold)
                || canAcceptSky(world, x, y, z - 1, threshold)
                || canAcceptSky(world, x, y, z + 1, threshold);
    }

    /** Kiểm tra ô lân cận nằm trong vùng đã khởi tạo và có thể nhận mức Sky Light mục tiêu. */
    private boolean canAcceptSky(World world, int nx, int ny, int nz, int targetSky) {
        if (!world.isInsideWorld(nx, ny, nz) || !isColumnLit(nx / Chunk.SIZE, nz / Chunk.SIZE)) {
            return false;
        }
        Block block = Block.fromId(world.getBlock(nx, ny, nz));
        if (block.isOpaque()) {
            return false;
        }
        int effectiveTarget = (targetSky == MAX_LIGHT && isLeaves(block)) ? MAX_LIGHT - 1 : targetSky;
        return getSkyLightRaw(world, nx, ny, nz) < effectiveTarget;
    }

    /** Gieo hạt từ hàng xóm ngoài biên cột chunk nếu hàng xóm có Sky Light lớn hơn. */
    private void enqueueBrighterEdgeSkyNeighbor(World world, int wx, int y, int wz, int sky,
                                                int baseX, int endX, int baseZ, int endZ) {
        int needed = sky + 2;
        if (wx == baseX && wx > 0 && isColumnLit((wx - 1) / Chunk.SIZE, wz / Chunk.SIZE)
                && getSkyLightRaw(world, wx - 1, y, wz) >= needed) {
            pushAdd(wx - 1, y, wz);
        }
        if (wx == endX - 1 && wx + 1 < World.WORLD_SIZE_X && isColumnLit((wx + 1) / Chunk.SIZE, wz / Chunk.SIZE)
                && getSkyLightRaw(world, wx + 1, y, wz) >= needed) {
            pushAdd(wx + 1, y, wz);
        }
        if (wz == baseZ && wz > 0 && isColumnLit(wx / Chunk.SIZE, (wz - 1) / Chunk.SIZE)
                && getSkyLightRaw(world, wx, y, wz - 1) >= needed) {
            pushAdd(wx, y, wz - 1);
        }
        if (wz == endZ - 1 && wz + 1 < World.WORLD_SIZE_Z && isColumnLit(wx / Chunk.SIZE, (wz + 1) / Chunk.SIZE)
                && getSkyLightRaw(world, wx, y, wz + 1) >= needed) {
            pushAdd(wx, y, wz + 1);
        }
    }

    /** Gieo hạt từ hàng xóm ngoài biên cột chunk nếu hàng xóm có Block Light lớn hơn. */
    private void enqueueBrighterEdgeBlockNeighbor(World world, int wx, int y, int wz, int blockLight,
                                                  int baseX, int endX, int baseZ, int endZ) {
        int needed = blockLight + 2;
        if (wx == baseX && wx > 0 && isColumnLit((wx - 1) / Chunk.SIZE, wz / Chunk.SIZE)
                && getBlockLightRaw(world, wx - 1, y, wz) >= needed) {
            pushAdd(wx - 1, y, wz);
        }
        if (wx == endX - 1 && wx + 1 < World.WORLD_SIZE_X && isColumnLit((wx + 1) / Chunk.SIZE, wz / Chunk.SIZE)
                && getBlockLightRaw(world, wx + 1, y, wz) >= needed) {
            pushAdd(wx + 1, y, wz);
        }
        if (wz == baseZ && wz > 0 && isColumnLit(wx / Chunk.SIZE, (wz - 1) / Chunk.SIZE)
                && getBlockLightRaw(world, wx, y, wz - 1) >= needed) {
            pushAdd(wx, y, wz - 1);
        }
        if (wz == endZ - 1 && wz + 1 < World.WORLD_SIZE_Z && isColumnLit(wx / Chunk.SIZE, (wz + 1) / Chunk.SIZE)
                && getBlockLightRaw(world, wx, y, wz + 1) >= needed) {
            pushAdd(wx, y, wz + 1);
        }
    }

    /** Cập nhật lại đường chiếu thẳng Sky Light của cột (wx, wz) từ wy trở xuống và chạy BFS xóa/tăng sáng. */
    private void updateSkyColumnAndBfs(World world, int wx, int wy, int wz) {
        clearAddQueue();
        clearRemoveQueue();

        // Tính lượng Sky Light chiếu thẳng từ đỉnh thế giới xuống tới ngay phía trên wy.
        int topCy = highestNonNullChunkY(world, wx / Chunk.SIZE, wz / Chunk.SIZE);
        int topY = topCy < 0 ? -1 : Math.min(World.WORLD_SIZE_Y - 1, (topCy + 1) * Chunk.SIZE - 1);
        int directSky = MAX_LIGHT;
        for (int y = topY; y > wy; y--) {
            Block b = Block.fromId(world.getBlock(wx, y, wz));
            if (b.isOpaque()) {
                directSky = 0;
                break;
            }
            if (isLeaves(b) && directSky > 0) {
                directSky--;
            }
        }

        // Quét từ wy đi xuống cho tới khi gặp khối đục nằm dưới wy.
        int lowestAffectedY = wy;
        int curDirectSky = directSky;
        for (int y = wy; y >= 0; y--) {
            Block b = Block.fromId(world.getBlock(wx, y, wz));
            boolean opaque = b.isOpaque();
            if (opaque) {
                curDirectSky = 0;
            } else if (isLeaves(b) && curDirectSky > 0) {
                curDirectSky--;
            }

            int oldSky = getSkyLightRaw(world, wx, y, wz);
            if (oldSky > curDirectSky) {
                pushRemove(wx, y, wz, oldSky);
                setSkyLightRaw(world, wx, y, wz, 0);
            } else if (curDirectSky > oldSky) {
                setSkyLightRaw(world, wx, y, wz, curDirectSky);
                pushAdd(wx, y, wz);
            }

            lowestAffectedY = y;
            if (opaque && y < wy) {
                break;
            }
        }

        // Chạy BFS xóa bỏ ánh sáng cũ phụ thuộc vào phần cột vừa bị che tối.
        drainSkyRemoveQueue(world);

        // Khôi phục lại mức chiếu thẳng (ví dụ khi đặt lá lên cột có sẵn ánh sáng yếu hơn 15).
        curDirectSky = directSky;
        for (int y = wy; y >= lowestAffectedY; y--) {
            Block b = Block.fromId(world.getBlock(wx, y, wz));
            if (b.isOpaque()) {
                break;
            }
            if (isLeaves(b) && curDirectSky > 0) {
                curDirectSky--;
            }
            if (curDirectSky > getSkyLightRaw(world, wx, y, wz)) {
                setSkyLightRaw(world, wx, y, wz, curDirectSky);
                pushAdd(wx, y, wz);
            }
        }

        // Nếu ô tại (wx, wy, wz) vừa mở thông và không nhận sáng trực tiếp, kéo sáng từ 6 hàng xóm vào.
        if (!Block.fromId(world.getBlock(wx, wy, wz)).isOpaque()) {
            enqueueBrighterSkyNeighbors(world, wx, wy, wz);
        }

        drainSkyIncreaseQueue(world);
    }

    /** Cập nhật Block Light khi khối tại (wx, wy, wz) thay đổi (đặt/phá Lava hoặc đặt khối chắn sáng). */
    private void updateBlockLightOnChange(World world, int wx, int wy, int wz, byte newId) {
        clearAddQueue();
        clearRemoveQueue();

        int oldLight = getBlockLightRaw(world, wx, wy, wz);
        Block newBlock = Block.fromId(newId);
        int newEmit = emissionOf(newBlock);

        if (oldLight > newEmit) {
            pushRemove(wx, wy, wz, oldLight);
            setBlockLightRaw(world, wx, wy, wz, 0);
            drainBlockRemoveQueue(world);
        }

        if (newEmit > 0) {
            setBlockLightRaw(world, wx, wy, wz, newEmit);
            pushAdd(wx, wy, wz);
        } else if (!newBlock.isOpaque()) {
            enqueueBrighterBlockNeighbors(world, wx, wy, wz);
        }

        drainBlockIncreaseQueue(world);
    }

    /** Rút cạn hàng đợi xóa Sky Light và ghi nhận các hàng xóm đủ sáng để chiếu bù lại. */
    private void drainSkyRemoveQueue(World world) {
        while (removeCount > 0) {
            int packed = popRemovePos();
            int oldVal = popRemoveVal();
            int x = packed >>> 19;
            int y = packed & 0xFF;
            int z = (packed >>> 8) & 0x7FF;

            removeSkyNeighbor(world, x - 1, y, z, oldVal);
            removeSkyNeighbor(world, x + 1, y, z, oldVal);
            removeSkyNeighbor(world, x, y - 1, z, oldVal);
            removeSkyNeighbor(world, x, y + 1, z, oldVal);
            removeSkyNeighbor(world, x, y, z - 1, oldVal);
            removeSkyNeighbor(world, x, y, z + 1, oldVal);
        }
    }

    /** Xử lý một ô lân cận trong pha xóa Sky Light. */
    private void removeSkyNeighbor(World world, int nx, int ny, int nz, int oldVal) {
        if (!world.isInsideWorld(nx, ny, nz) || !isColumnLit(nx / Chunk.SIZE, nz / Chunk.SIZE)) {
            return;
        }
        int nVal = getSkyLightRaw(world, nx, ny, nz);
        if (nVal == 0) {
            return;
        }
        if (nVal < oldVal) {
            setSkyLightRaw(world, nx, ny, nz, 0);
            pushRemove(nx, ny, nz, nVal);
        } else {
            pushAdd(nx, ny, nz);
        }
    }

    /** Rút cạn hàng đợi xóa Block Light và ghi nhận các nguồn sáng còn lại để lan truyền bù. */
    private void drainBlockRemoveQueue(World world) {
        while (removeCount > 0) {
            int packed = popRemovePos();
            int oldVal = popRemoveVal();
            int x = packed >>> 19;
            int y = packed & 0xFF;
            int z = (packed >>> 8) & 0x7FF;

            removeBlockNeighbor(world, x - 1, y, z, oldVal);
            removeBlockNeighbor(world, x + 1, y, z, oldVal);
            removeBlockNeighbor(world, x, y - 1, z, oldVal);
            removeBlockNeighbor(world, x, y + 1, z, oldVal);
            removeBlockNeighbor(world, x, y, z - 1, oldVal);
            removeBlockNeighbor(world, x, y, z + 1, oldVal);
        }
    }

    /** Xử lý một ô lân cận trong pha xóa Block Light. */
    private void removeBlockNeighbor(World world, int nx, int ny, int nz, int oldVal) {
        if (!world.isInsideWorld(nx, ny, nz) || !isColumnLit(nx / Chunk.SIZE, nz / Chunk.SIZE)) {
            return;
        }
        int nVal = getBlockLightRaw(world, nx, ny, nz);
        if (nVal == 0) {
            return;
        }
        if (nVal < oldVal) {
            setBlockLightRaw(world, nx, ny, nz, 0);
            pushRemove(nx, ny, nz, nVal);
        } else {
            pushAdd(nx, ny, nz);
        }
    }

    /** Lan truyền tăng Sky Light theo BFS 6 hướng dùng hàng đợi vòng. */
    private void drainSkyIncreaseQueue(World world) {
        while (addCount > 0) {
            int packed = popAdd();
            int x = packed >>> 19;
            int y = packed & 0xFF;
            int z = (packed >>> 8) & 0x7FF;
            int level = getSkyLightRaw(world, x, y, z);
            if (level <= 1) {
                continue;
            }
            propagateSkyNeighbor(world, x - 1, y, z, level, false);
            propagateSkyNeighbor(world, x + 1, y, z, level, false);
            propagateSkyNeighbor(world, x, y - 1, z, level, true);
            propagateSkyNeighbor(world, x, y + 1, z, level, false);
            propagateSkyNeighbor(world, x, y, z - 1, level, false);
            propagateSkyNeighbor(world, x, y, z + 1, level, false);
        }
    }

    /** Lan truyền Sky Light sang một ô kề nếu không bị khối đục cản lại. */
    private void propagateSkyNeighbor(World world, int nx, int ny, int nz, int sourceLevel, boolean downward) {
        if (!world.isInsideWorld(nx, ny, nz) || !isColumnLit(nx / Chunk.SIZE, nz / Chunk.SIZE)) {
            return;
        }
        Block block = Block.fromId(world.getBlock(nx, ny, nz));
        if (block.isOpaque()) {
            return;
        }
        int targetLevel;
        if (downward && sourceLevel == MAX_LIGHT && !isLeaves(block)) {
            targetLevel = MAX_LIGHT;
        } else {
            targetLevel = sourceLevel - 1;
        }
        if (targetLevel <= 0) {
            return;
        }
        if (getSkyLightRaw(world, nx, ny, nz) < targetLevel) {
            setSkyLightRaw(world, nx, ny, nz, targetLevel);
            pushAdd(nx, ny, nz);
        }
    }

    /** Lan truyền tăng Block Light theo BFS 6 hướng dùng hàng đợi vòng. */
    private void drainBlockIncreaseQueue(World world) {
        while (addCount > 0) {
            int packed = popAdd();
            int x = packed >>> 19;
            int y = packed & 0xFF;
            int z = (packed >>> 8) & 0x7FF;
            int level = getBlockLightRaw(world, x, y, z);
            if (level <= 1) {
                continue;
            }
            int nextLevel = level - 1;
            propagateBlockNeighbor(world, x - 1, y, z, nextLevel);
            propagateBlockNeighbor(world, x + 1, y, z, nextLevel);
            propagateBlockNeighbor(world, x, y - 1, z, nextLevel);
            propagateBlockNeighbor(world, x, y + 1, z, nextLevel);
            propagateBlockNeighbor(world, x, y, z - 1, nextLevel);
            propagateBlockNeighbor(world, x, y, z + 1, nextLevel);
        }
    }

    /** Lan truyền Block Light sang một ô kề nếu không bị khối đục cản lại. */
    private void propagateBlockNeighbor(World world, int nx, int ny, int nz, int targetLevel) {
        if (!world.isInsideWorld(nx, ny, nz) || !isColumnLit(nx / Chunk.SIZE, nz / Chunk.SIZE)) {
            return;
        }
        if (Block.fromId(world.getBlock(nx, ny, nz)).isOpaque()) {
            return;
        }
        if (getBlockLightRaw(world, nx, ny, nz) < targetLevel) {
            setBlockLightRaw(world, nx, ny, nz, targetLevel);
            pushAdd(nx, ny, nz);
        }
    }

    /** Đưa các hàng xóm có Sky Light > 1 vào hàng đợi khi vừa đục thông một ô. */
    private void enqueueBrighterSkyNeighbors(World world, int x, int y, int z) {
        enqueueSkyIfBrighter(world, x - 1, y, z);
        enqueueSkyIfBrighter(world, x + 1, y, z);
        enqueueSkyIfBrighter(world, x, y - 1, z);
        enqueueSkyIfBrighter(world, x, y + 1, z);
        enqueueSkyIfBrighter(world, x, y, z - 1);
        enqueueSkyIfBrighter(world, x, y, z + 1);
    }

    private void enqueueSkyIfBrighter(World world, int nx, int ny, int nz) {
        if (world.isInsideWorld(nx, ny, nz) && isColumnLit(nx / Chunk.SIZE, nz / Chunk.SIZE)
                && getSkyLightRaw(world, nx, ny, nz) > 1) {
            pushAdd(nx, ny, nz);
        }
    }

    /** Đưa các hàng xóm có Block Light > 1 vào hàng đợi khi vừa đục thông một ô. */
    private void enqueueBrighterBlockNeighbors(World world, int x, int y, int z) {
        enqueueBlockIfBrighter(world, x - 1, y, z);
        enqueueBlockIfBrighter(world, x + 1, y, z);
        enqueueBlockIfBrighter(world, x, y - 1, z);
        enqueueBlockIfBrighter(world, x, y + 1, z);
        enqueueBlockIfBrighter(world, x, y, z - 1);
        enqueueBlockIfBrighter(world, x, y, z + 1);
    }

    private void enqueueBlockIfBrighter(World world, int nx, int ny, int nz) {
        if (world.isInsideWorld(nx, ny, nz) && isColumnLit(nx / Chunk.SIZE, nz / Chunk.SIZE)
                && getBlockLightRaw(world, nx, ny, nz) > 1) {
            pushAdd(nx, ny, nz);
        }
    }

    /** Đọc trực tiếp Sky Light từ Chunk mà không kích hoạt khởi tạo lười. */
    private static int getSkyLightRaw(World world, int x, int y, int z) {
        Chunk chunk = world.getChunkRaw(x / Chunk.SIZE, y / Chunk.SIZE, z / Chunk.SIZE);
        return chunk == null ? MAX_LIGHT : chunk.getSkyLightLocal(x % Chunk.SIZE, y % Chunk.SIZE, z % Chunk.SIZE);
    }

    /** Đọc trực tiếp Block Light từ Chunk mà không kích hoạt khởi tạo lười. */
    private static int getBlockLightRaw(World world, int x, int y, int z) {
        Chunk chunk = world.getChunkRaw(x / Chunk.SIZE, y / Chunk.SIZE, z / Chunk.SIZE);
        return chunk == null ? 0 : chunk.getBlockLightLocal(x % Chunk.SIZE, y % Chunk.SIZE, z % Chunk.SIZE);
    }

    /** Ghi trực tiếp Sky Light vào Chunk và đánh dấu bẩn chunk cùng chunk biên kề. */
    private static boolean setSkyLightRaw(World world, int x, int y, int z, int level) {
        int cx = x / Chunk.SIZE;
        int cy = y / Chunk.SIZE;
        int cz = z / Chunk.SIZE;
        Chunk chunk = world.getOrCreateLightChunk(cx, cy, cz, level != MAX_LIGHT);
        if (chunk == null) {
            return false;
        }
        if (chunk.setSkyLightLocal(x % Chunk.SIZE, y % Chunk.SIZE, z % Chunk.SIZE, level)) {
            markLightDirty(world, x, y, z);
            return true;
        }
        return false;
    }

    /** Ghi trực tiếp Block Light vào Chunk và đánh dấu bẩn chunk cùng chunk biên kề. */
    private static boolean setBlockLightRaw(World world, int x, int y, int z, int level) {
        int cx = x / Chunk.SIZE;
        int cy = y / Chunk.SIZE;
        int cz = z / Chunk.SIZE;
        Chunk chunk = world.getOrCreateLightChunk(cx, cy, cz, level != 0);
        if (chunk == null) {
            return false;
        }
        if (chunk.setBlockLightLocal(x % Chunk.SIZE, y % Chunk.SIZE, z % Chunk.SIZE, level)) {
            markLightDirty(world, x, y, z);
            return true;
        }
        return false;
    }

    /** Đánh dấu chunk chứa (x, y, z) và các chunk lân cận qua biên cần dựng lại mesh. */
    private static void markLightDirty(World world, int x, int y, int z) {
        int cx = x / Chunk.SIZE;
        int cy = y / Chunk.SIZE;
        int cz = z / Chunk.SIZE;
        world.markChunkDirtyIfExists(cx, cy, cz);

        int lx = x % Chunk.SIZE;
        int ly = y % Chunk.SIZE;
        int lz = z % Chunk.SIZE;
        if (lx == 0 && cx > 0) {
            world.markChunkDirtyIfExists(cx - 1, cy, cz);
        } else if (lx == Chunk.SIZE - 1 && cx + 1 < World.WORLD_CHUNKS_X) {
            world.markChunkDirtyIfExists(cx + 1, cy, cz);
        }
        if (ly == 0 && cy > 0) {
            world.markChunkDirtyIfExists(cx, cy - 1, cz);
        } else if (ly == Chunk.SIZE - 1 && cy + 1 < World.WORLD_CHUNKS_Y) {
            world.markChunkDirtyIfExists(cx, cy + 1, cz);
        }
        if (lz == 0 && cz > 0) {
            world.markChunkDirtyIfExists(cx, cy, cz - 1);
        } else if (lz == Chunk.SIZE - 1 && cz + 1 < World.WORLD_CHUNKS_Z) {
            world.markChunkDirtyIfExists(cx, cy, cz + 1);
        }
    }

    /** Tìm section chunk cao nhất đang tồn tại trong cột (cx, cz). */
    private static int highestNonNullChunkY(World world, int cx, int cz) {
        for (int cy = World.WORLD_CHUNKS_Y - 1; cy >= 0; cy--) {
            if (world.getChunkRaw(cx, cy, cz) != null) {
                return cy;
            }
        }
        return -1;
    }

    /** Tìm section chunk cao nhất trong cột (cx, cz) và 4 cột kề mặt để quét đủ vùng giáp ranh. */
    private static int maxLitNeighborhoodChunkY(World world, int cx, int cz) {
        int maxCy = highestNonNullChunkY(world, cx, cz);
        if (cx > 0) {
            maxCy = Math.max(maxCy, highestNonNullChunkY(world, cx - 1, cz));
        }
        if (cx + 1 < World.WORLD_CHUNKS_X) {
            maxCy = Math.max(maxCy, highestNonNullChunkY(world, cx + 1, cz));
        }
        if (cz > 0) {
            maxCy = Math.max(maxCy, highestNonNullChunkY(world, cx, cz - 1));
        }
        if (cz + 1 < World.WORLD_CHUNKS_Z) {
            maxCy = Math.max(maxCy, highestNonNullChunkY(world, cx, cz + 1));
        }
        return maxCy;
    }

    private static int emissionOf(Block block) {
        return block == Block.LAVA ? MAX_LIGHT : 0;
    }

    private static boolean isLeaves(Block block) {
        return block == Block.OAK_LEAVES || block == Block.SPRUCE_LEAVES;
    }

    private static int clampLight(int level) {
        return Math.max(0, Math.min(MAX_LIGHT, level));
    }

    // =========================================================================
    // QUẢN LÝ HÀNG ĐỢI VÒNG (RING BUFFER) ZERO-GC
    // =========================================================================

    private static int pack(int x, int y, int z) {
        return (x << 19) | (z << 8) | y;
    }

    private void clearAddQueue() {
        addHead = 0;
        addTail = 0;
        addCount = 0;
    }

    private void pushAdd(int x, int y, int z) {
        if (addCount == addQueue.length) {
            growAddQueue();
        }
        addQueue[addTail] = pack(x, y, z);
        addTail = (addTail + 1) & (addQueue.length - 1);
        addCount++;
    }

    private int popAdd() {
        int value = addQueue[addHead];
        addHead = (addHead + 1) & (addQueue.length - 1);
        addCount--;
        return value;
    }

    /** Chỉ mở rộng gấp đôi trong tình huống cực hiếm vượt quá 65.536 ô cùng lúc. */
    private void growAddQueue() {
        int oldCap = addQueue.length;
        int[] expanded = new int[oldCap * 2];
        int firstPart = oldCap - addHead;
        System.arraycopy(addQueue, addHead, expanded, 0, firstPart);
        System.arraycopy(addQueue, 0, expanded, firstPart, addHead);
        addQueue = expanded;
        addHead = 0;
        addTail = oldCap;
    }

    private void clearRemoveQueue() {
        removeHead = 0;
        removeTail = 0;
        removeCount = 0;
    }

    private void pushRemove(int x, int y, int z, int oldVal) {
        if (removeCount == removePosQueue.length) {
            growRemoveQueue();
        }
        removePosQueue[removeTail] = pack(x, y, z);
        removeValQueue[removeTail] = (byte) oldVal;
        removeTail = (removeTail + 1) & (removePosQueue.length - 1);
        removeCount++;
    }

    private int popRemovePos() {
        return removePosQueue[removeHead];
    }

    private int popRemoveVal() {
        int value = Byte.toUnsignedInt(removeValQueue[removeHead]);
        removeHead = (removeHead + 1) & (removePosQueue.length - 1);
        removeCount--;
        return value;
    }

    private void growRemoveQueue() {
        int oldCap = removePosQueue.length;
        int[] expandedPos = new int[oldCap * 2];
        byte[] expandedVal = new byte[oldCap * 2];
        int firstPart = oldCap - removeHead;
        System.arraycopy(removePosQueue, removeHead, expandedPos, 0, firstPart);
        System.arraycopy(removePosQueue, 0, expandedPos, firstPart, removeHead);
        System.arraycopy(removeValQueue, removeHead, expandedVal, 0, firstPart);
        System.arraycopy(removeValQueue, 0, expandedVal, firstPart, removeHead);
        removePosQueue = expandedPos;
        removeValQueue = expandedVal;
        removeHead = 0;
        removeTail = oldCap;
    }
}
