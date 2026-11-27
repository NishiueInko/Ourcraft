package com.voxelgame.world.level;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.core.Tickable;
import com.voxelgame.world.level.block.Block;

import com.badlogic.gdx.utils.IntSet;

/**
 * Hệ thống mô phỏng dòng chảy chất lỏng ({@link Block#WATER} và {@link Block#LAVA})
 * và phản ứng hóa đá {@link Block#STONE} khi hai chất lỏng tiếp xúc nhau.
 *
 * Thiết kế tối ưu 60 FPS (Zero-GC):
 * - Sử dụng hai hàng đợi vòng (ring-buffer) kích thước cố định tách biệt cho Nước và Dung nham,
 *   kết hợp {@link IntSet} cấp phát dư dung lượng một lần duy nhất để chống trùng lặp trong O(1).
 * - Giới hạn tối đa {@value #MAX_UPDATES_PER_TICK} ô xử lý mỗi nhịp cập nhật để bảo toàn
 *   tốc độ khung hình ngay cả khi người chơi phá vỡ bờ hồ lớn.
 * - Chỉ kích hoạt cho các ô thay đổi trong gameplay ({@link #onBlockChanged}), tuyệt đối không
 *   chạy hàng loạt khi sinh địa hình chunk ban đầu.
 */
public final class LiquidFlowSimulator implements Tickable {

    /** Số ô chất lỏng tối đa được xử lý trong mỗi bước tick để chống tụt FPS. */
    public static final int MAX_UPDATES_PER_TICK = 64;

    /** Chu kỳ lan truyền của nước (0.25 giây/bước) và bước tăng mức độ cản (tối đa 7 ô ngang). */
    public static final float WATER_FLOW_INTERVAL = 0.25f;
    public static final int WATER_FLOW_STEP = 1;

    /** Chu kỳ lan truyền của dung nham (1.50 giây/bước) và bước tăng +2 (tối đa 3 ô ngang: 2, 4, 6). */
    public static final float LAVA_FLOW_INTERVAL = 1.50f;
    public static final int LAVA_FLOW_STEP = 2;

    /** Mức metadata tối đa của dòng chảy (0 = nguồn, 1..7 = dòng chảy yếu dần). */
    public static final int MAX_FLOW_LEVEL = 7;

    /** Dung lượng cố định của mỗi hàng đợi vòng chất lỏng. */
    private static final int QUEUE_CAPACITY = 4096;

    /** Âm lượng cơ bản khi phát tiếng xì hơi nước lúc Water gặp Lava tạo Stone. */
    private static final float FIZZ_VOLUME = 0.45f;

    /** 6 hướng kề mặt dùng kiểm tra tiếp xúc Water/Lava và đánh thức hàng xóm. */
    private static final int[] DX = {0, 0, 1, -1, 0, 0};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    /** 4 hướng lan truyền trên mặt phẳng ngang XZ. */
    private static final int[] HDX = {1, -1, 0, 0};
    private static final int[] HDZ = {0, 0, 1, -1};

    /** Hàng đợi vòng và tập chống trùng cho dòng chảy Nước. */
    private final int[] waterQueue = new int[QUEUE_CAPACITY];
    private final IntSet waterSet = new IntSet(QUEUE_CAPACITY * 2);
    private int waterHead;
    private int waterTail;
    private int waterCount;
    private float waterElapsed;

    /** Hàng đợi vòng và tập chống trùng cho dòng chảy Dung nham. */
    private final int[] lavaQueue = new int[QUEUE_CAPACITY];
    private final IntSet lavaSet = new IntSet(QUEUE_CAPACITY * 2);
    private int lavaHead;
    private int lavaTail;
    private int lavaCount;
    private float lavaElapsed;

    /**
     * Xếp lịch cập nhật cho một ô chất lỏng cụ thể vào hàng đợi tương ứng (không cấp phát rác).
     *
     * @param x      tọa độ X thế giới
     * @param y      tọa độ Y thế giới
     * @param z      tọa độ Z thế giới
     * @param liquid loại chất lỏng ({@link Block#WATER} hoặc {@link Block#LAVA})
     */
    public void scheduleUpdate(int x, int y, int z, Block liquid) {
        if (x < 0 || x >= World.WORLD_SIZE_X || y < 0 || y >= World.WORLD_SIZE_Y
                || z < 0 || z >= World.WORLD_SIZE_Z) {
            return;
        }
        int packed = (x << 19) | (z << 8) | y;
        if (liquid == Block.WATER) {
            if (waterCount < waterQueue.length && waterSet.add(packed)) {
                waterQueue[waterTail] = packed;
                waterTail = (waterTail + 1) % waterQueue.length;
                waterCount++;
            }
        } else if (liquid == Block.LAVA) {
            if (lavaCount < lavaQueue.length && lavaSet.add(packed)) {
                lavaQueue[lavaTail] = packed;
                lavaTail = (lavaTail + 1) % lavaQueue.length;
                lavaCount++;
            }
        }
    }

    /**
     * Được gọi khi một ô trong thế giới thay đổi trong quá trình chơi (đặt/phá/nổ):
     * chỉ đánh thức các khối chất lỏng lân cận khi thay đổi có ảnh hưởng tới dòng chảy
     * (đặt/xóa chất lỏng, tạo khoảng trống {@code AIR}/{@code CROSS_PLANT}), tránh đánh thức
     * nhầm nước tĩnh sinh sẵn khi cỏ dưới đáy hồ chuyển thành đất.
     *
     * @param world thế giới voxel
     * @param x     tọa độ X của ô vừa đổi
     * @param y     tọa độ Y của ô vừa đổi
     * @param z     tọa độ Z của ô vừa đổi
     * @param oldId mã block trước khi đổi
     * @param newId mã block sau khi đổi
     */
    public void onBlockChanged(World world, int x, int y, int z, byte oldId, byte newId) {
        if (world == null) {
            return;
        }
        boolean involvesLiquid = isLiquidId(oldId) || isLiquidId(newId);
        boolean becameFlowable = canFlowInto(Block.fromId(newId));
        if (!involvesLiquid && !becameFlowable) {
            return;
        }
        scheduleIfLiquid(world, x, y, z);
        for (int dir = 0; dir < 6; dir++) {
            scheduleIfLiquid(world, x + DX[dir], y + DY[dir], z + DZ[dir]);
        }
    }

    /** Kiểm tra mã byte ID có phải Water hoặc Lava. */
    private static boolean isLiquidId(byte id) {
        return id == Block.WATER.getId() || id == Block.LAVA.getId();
    }

    /** Kiểm tra tọa độ hợp lệ và đưa vào hàng đợi nếu ô đang chứa Water hoặc Lava. */
    private void scheduleIfLiquid(World world, int x, int y, int z) {
        if (!world.isInsideWorld(x, y, z)) {
            return;
        }
        byte id = world.getBlock(x, y, z);
        if (id == Block.WATER.getId()) {
            scheduleUpdate(x, y, z, Block.WATER);
        } else if (id == Block.LAVA.getId()) {
            scheduleUpdate(x, y, z, Block.LAVA);
        }
    }

    /**
     * Cập nhật tiến trình chảy của Nước (mỗi 0.25s) và Dung nham (mỗi 1.50s) theo bước thời gian.
     * Mỗi nhịp chỉ xử lý tối đa {@value #MAX_UPDATES_PER_TICK} ô ở đầu hàng đợi; các ô mới sinh ra
     * sẽ nằm ở cuối hàng đợi để chờ tới chu kỳ kế tiếp.
     *
     * @param world thế giới voxel
     * @param dt    khoảng thời gian trôi qua (giây)
     */
    public void tick(World world, float dt) {
        if (world == null || dt <= 0.0f) {
            return;
        }

        // 1. Nhịp cập nhật dòng chảy Nước (0.25s)
        if (waterCount == 0) {
            waterElapsed = 0.0f;
        } else {
            waterElapsed += dt;
            while (waterElapsed >= WATER_FLOW_INTERVAL && waterCount > 0) {
                waterElapsed -= WATER_FLOW_INTERVAL;
                int batchCount = Math.min(waterCount, MAX_UPDATES_PER_TICK);
                for (int i = 0; i < batchCount; i++) {
                    int packed = waterQueue[waterHead];
                    waterHead = (waterHead + 1) % waterQueue.length;
                    waterCount--;
                    waterSet.remove(packed);

                    int x = packed >>> 19;
                    int y = packed & 0xFF;
                    int z = (packed >>> 8) & 0x7FF;
                    processLiquidCell(world, x, y, z, Block.WATER, WATER_FLOW_STEP);
                }
            }
            if (waterCount == 0) {
                waterElapsed = 0.0f;
            }
        }

        // 2. Nhịp cập nhật dòng chảy Dung nham (1.50s)
        if (lavaCount == 0) {
            lavaElapsed = 0.0f;
        } else {
            lavaElapsed += dt;
            while (lavaElapsed >= LAVA_FLOW_INTERVAL && lavaCount > 0) {
                lavaElapsed -= LAVA_FLOW_INTERVAL;
                int batchCount = Math.min(lavaCount, MAX_UPDATES_PER_TICK);
                for (int i = 0; i < batchCount; i++) {
                    int packed = lavaQueue[lavaHead];
                    lavaHead = (lavaHead + 1) % lavaQueue.length;
                    lavaCount--;
                    lavaSet.remove(packed);

                    int x = packed >>> 19;
                    int y = packed & 0xFF;
                    int z = (packed >>> 8) & 0x7FF;
                    processLiquidCell(world, x, y, z, Block.LAVA, LAVA_FLOW_STEP);
                }
            }
            if (lavaCount == 0) {
                lavaElapsed = 0.0f;
            }
        }
    }

    /** Xử lý phản ứng tạo Stone, suy giảm khi mất nguồn, chảy xuống dưới và lan ngang tại một ô. */
    private void processLiquidCell(World world, int x, int y, int z, Block liquid, int flowStep) {
        if (world.getBlock(x, y, z) != liquid.getId()) {
            return;
        }
        int state = liquid.normalizeState(world.getBlockState(x, y, z));
        Block opposite = (liquid == Block.WATER) ? Block.LAVA : Block.WATER;

        // 1. Kiểm tra tiếp xúc giữa Water và Lava ở 6 mặt kề nhau để tạo Block.STONE + tiếng xì
        for (int dir = 0; dir < 6; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            int nz = z + DZ[dir];
            if (!world.isInsideWorld(nx, ny, nz)) {
                continue;
            }
            if (world.getBlock(nx, ny, nz) == opposite.getId()) {
                if (DY[dir] > 0) {
                    // Chất lỏng đối nghịch ở ngay phía trên -> hóa đá tại chính ô đang xét
                    world.setBlock(x, y, z, Block.STONE.getId(), 0);
                    playFizzSound(world, x, y, z);
                    return;
                }
                world.setBlock(nx, ny, nz, Block.STONE.getId(), 0);
                playFizzSound(world, nx, ny, nz);
            }
        }

        // 2. Nếu là dòng chảy (state > 0), kiểm tra còn được cấp nước/nham từ trên hoặc từ ô nguồn gần hơn
        if (state > 0) {
            boolean hasAboveLiquid = y + 1 < World.WORLD_SIZE_Y && world.getBlock(x, y + 1, z) == liquid.getId();
            if (hasAboveLiquid) {
                if (state != 1) {
                    state = 1;
                    world.setBlock(x, y, z, liquid.getId(), 1);
                }
            } else {
                int minNeighborState = Integer.MAX_VALUE;
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + HDX[dir];
                    int nz = z + HDZ[dir];
                    if (!world.isInsideWorld(nx, y, nz) || world.getBlock(nx, y, nz) != liquid.getId()) {
                        continue;
                    }
                    int neighborState = liquid.normalizeState(world.getBlockState(nx, y, nz));
                    if (neighborState < minNeighborState) {
                        minNeighborState = neighborState;
                    }
                }
                int expectedState = (minNeighborState == Integer.MAX_VALUE)
                        ? (MAX_FLOW_LEVEL + 1)
                        : (minNeighborState + flowStep);
                if (expectedState > state) {
                    // Đã mất nguồn cấp đủ gần -> rút cạn ô này về AIR và lan truyền cho hạ nguồn
                    world.setBlock(x, y, z, Block.AIR.getId(), 0);
                    return;
                }
                if (expectedState < state) {
                    state = expectedState;
                    world.setBlock(x, y, z, liquid.getId(), state);
                }
            }
        }

        // 3. Ưu tiên chảy thẳng xuống dưới nếu ô dưới là AIR hoặc cây chữ X (CROSS_PLANT)
        if (y > 0) {
            Block below = Block.fromId(world.getBlock(x, y - 1, z));
            if (canFlowInto(below)) {
                flowInto(world, x, y - 1, z, liquid, 1, below);
                return;
            }
            if (below == liquid) {
                int belowState = liquid.normalizeState(world.getBlockState(x, y - 1, z));
                if (belowState > 1) {
                    world.setBlock(x, y - 1, z, liquid.getId(), 1);
                    return;
                }
                if (belowState == 1) {
                    // Đang là cột chảy thẳng xuống dưới -> không lan ngang giữa không trung
                    return;
                }
            }
        }

        // 4. Lan theo 4 hướng ngang trên bề mặt đỡ
        int nextState = state + flowStep;
        if (nextState > MAX_FLOW_LEVEL) {
            return;
        }
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + HDX[dir];
            int nz = z + HDZ[dir];
            if (!world.isInsideWorld(nx, y, nz)) {
                continue;
            }
            Block neighbor = Block.fromId(world.getBlock(nx, y, nz));
            if (canFlowInto(neighbor)) {
                flowInto(world, nx, y, nz, liquid, nextState, neighbor);
            } else if (neighbor == liquid && liquid.normalizeState(world.getBlockState(nx, y, nz)) > nextState) {
                world.setBlock(nx, y, nz, liquid.getId(), nextState);
            }
        }
    }

    /** Kiểm tra chất lỏng có thể chiếm chỗ ô mục tiêu hay không (AIR hoặc thực vật chữ X). */
    private static boolean canFlowInto(Block target) {
        return target == Block.AIR || target.getGeometryType() == Block.GeometryType.CROSS_PLANT;
    }

    /** Đổ chất lỏng vào ô mục tiêu; riêng Water sẽ rửa trôi cây chữ X thành ItemEntity rơi. */
    private static void flowInto(World world, int x, int y, int z, Block liquid, int nextState, Block replaced) {
        if (liquid == Block.WATER && replaced.getGeometryType() == Block.GeometryType.CROSS_PLANT) {
            replaced.onBreak(world, x, y, z);
        }
        world.setBlock(x, y, z, liquid.getId(), nextState);
    }

    /** Phát âm thanh xì hơi nước theo khoảng cách khi Water + Lava hóa thành Stone. */
    private static void playFizzSound(World world, int x, int y, int z) {
        AudioManager audio = world.getAudio();
        if (audio != null) {
            float gain = world.getAudioGain(x + 0.5f, y + 0.5f, z + 0.5f);
            if (gain > 0.0f) {
                audio.playEffect(AudioManager.Effect.FIZZ, FIZZ_VOLUME * gain);
            }
        }
    }
}
