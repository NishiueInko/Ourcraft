package com.voxelgame.world.level;

import com.voxelgame.core.Tickable;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.block.WoolBlock;

import com.badlogic.gdx.utils.IntSet;
import java.util.Arrays;
import java.util.Random;

/**
 * Hệ thống quản lý phân hủy lá cây tự nhiên ({@code Oak Leaves}, {@code Spruce Leaves})
 * và hệ số tốc độ đào của công cụ {@link Item#SHEARS} trên lá và 6 khối len {@link WoolBlock}.
 *
 * Thiết kế Zero-GC:
 * - Thuật toán BFS kiểm tra kết nối tới thân gỗ ({@link #isConnectedToLog}) dùng chung hai mảng
 *   kích thước cố định ({@code 13×13×13 = 2197} ô) cấp phát sẵn một lần duy nhất, kết hợp bộ đếm
 *   nhãn {@code currentVisitStamp} để tránh cấp phát rác hoặc dọn toàn mảng mỗi lần gọi.
 * - Hàng đợi kiểm tra trễ khi chặt gỗ/lá dùng ring-buffer cố định và {@link IntSet} cấp phát dư
 *   dung lượng ngay khi khởi tạo để bảo đảm ổn định 60 FPS.
 */
public final class LeafDecaySystem implements Tickable {

    /** Bit metadata đánh dấu lá do người chơi tự đặt, không bao giờ tự phân hủy. */
    public static final int PERSISTENT_LEAF_STATE = 1;

    /** Khoảng cách Manhattan tối đa (6 ô) từ lá tới thân gỗ để duy trì tán lá. */
    public static final int MAX_LOG_DISTANCE = 6;

    /** Hệ số tăng tốc độ đào khi dùng kéo (Shears) trên lá cây và 6 màu len cừu. */
    public static final float SHEARS_MINING_MULTIPLIER = 5.0f;

    /** Kích thước cạnh lưới cục bộ (2 × 6 + 1 = 13) và thể tích lưới BFS (2197 ô). */
    private static final int GRID_SPAN = MAX_LOG_DISTANCE * 2 + 1;
    private static final int GRID_VOLUME = GRID_SPAN * GRID_SPAN * GRID_SPAN;

    /** Xác suất rơi gậy gỗ (2%) khi một khối lá tự nhiên phân hủy. */
    private static final float STICK_DROP_CHANCE = 0.02f;

    /** Chu kỳ xử lý hàng đợi lá chờ kiểm tra và giới hạn số ô mỗi nhịp để giữ 60 FPS. */
    private static final float DECAY_STEP_SECONDS = 0.15f;
    private static final int MAX_CHECKS_PER_STEP = 16;
    private static final int PENDING_CAPACITY = 2048;

    /** 6 hướng kề mặt theo trục X, Y, Z dùng chung cho BFS và lan truyền hàng xóm. */
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    /** Hàng đợi BFS cố định: mỗi phần tử đóng gói (localIndex << 4) | distance. */
    private static final int[] BFS_QUEUE = new int[GRID_VOLUME];

    /** Mảng đóng dấu lượt duyệt theo {@code currentVisitStamp} để kiểm tra đã thăm trong O(1). */
    private static final int[] VISITED_STAMP = new int[GRID_VOLUME];
    private static int currentVisitStamp = 1;

    /** Bộ sinh số ngẫu nhiên dùng lại cho tỷ lệ rơi Stick khi lá phân hủy. */
    private static final Random DROP_RANDOM = new Random(World.WORLD_SEED ^ 0x4C454146L);

    /** Ring-buffer cố định lưu tọa độ đóng gói (x << 19) | (z << 8) | y các lá chờ kiểm tra. */
    private final int[] pendingQueue = new int[PENDING_CAPACITY];
    private final IntSet pendingSet = new IntSet(PENDING_CAPACITY * 2);
    private int head;
    private int tail;
    private int count;
    private float elapsed;

    /**
     * Kiểm tra một khối có phải là khối lá hỗ trợ phân hủy ({@code OAK_LEAVES} / {@code SPRUCE_LEAVES}).
     *
     * @param block thực thể Block cần kiểm tra
     * @return true nếu là lá sồi hoặc lá vân sam
     */
    public static boolean isLeaf(Block block) {
        return block == Block.OAK_LEAVES || block == Block.SPRUCE_LEAVES;
    }

    /**
     * Kiểm tra mã byte ID có phải khối lá sồi hoặc lá vân sam.
     *
     * @param blockId mã ID block
     * @return true nếu là lá
     */
    public static boolean isLeafId(byte blockId) {
        return blockId == Block.OAK_LEAVES.getId() || blockId == Block.SPRUCE_LEAVES.getId();
    }

    /**
     * Kiểm tra mã byte ID có phải khối thân gỗ ({@code OAK_LOG} / {@code SPRUCE_LOG}).
     *
     * @param blockId mã ID block
     * @return true nếu là thân gỗ
     */
    public static boolean isLogId(byte blockId) {
        return blockId == Block.OAK_LOG.getId() || blockId == Block.SPRUCE_LOG.getId();
    }

    /**
     * Kiểm tra tại tọa độ thế giới có phải là lá tự nhiên (không mang bit {@link #PERSISTENT_LEAF_STATE}).
     *
     * @param world thế giới voxel
     * @param x     tọa độ X
     * @param y     tọa độ Y
     * @param z     tọa độ Z
     * @return true nếu ô là lá và có thể phân hủy
     */
    public static boolean isDecayableLeaf(World world, int x, int y, int z) {
        if (world == null || !world.isInsideWorld(x, y, z)) {
            return false;
        }
        return isLeafId(world.getBlock(x, y, z))
                && (world.getBlockState(x, y, z) & PERSISTENT_LEAF_STATE) == 0;
    }

    /**
     * Tính hệ số nhân tốc độ đào dựa trên vật phẩm đang cầm và khối mục tiêu.
     * Cầm {@link Item#SHEARS} đào lá cây hoặc 6 loại {@link WoolBlock} đạt tốc độ {@code 5.0f};
     * mọi trường hợp khác giữ nguyên {@code 1.0f}.
     *
     * @param heldItem vật phẩm trên tay người chơi (có thể là {@link Item#NONE})
     * @param target   khối đang bị đào
     * @return hệ số nhân tốc độ đào ({@code 5.0f} hoặc {@code 1.0f})
     */
    public static float miningSpeedMultiplier(Item heldItem, Block target) {
        return heldItem != null ? heldItem.getMiningSpeedMultiplier(target) : 1.0f;
    }

    /**
     * Duyệt BFS trong bán kính Manhattan tối đa {@value #MAX_LOG_DISTANCE} ô quanh vị trí lá
     * để kiểm tra lá có còn kết nối qua chuỗi lá liền kề tới thân gỗ ({@code OAK_LOG} / {@code SPRUCE_LOG}) không.
     *
     * @param world  thế giới voxel
     * @param startX tọa độ X của lá cần kiểm tra
     * @param startY tọa độ Y của lá cần kiểm tra
     * @param startZ tọa độ Z của lá cần kiểm tra
     * @return true nếu tìm thấy đường nối tới thân gỗ trong bán kính 6 ô
     */
    public static boolean isConnectedToLog(World world, int startX, int startY, int startZ) {
        if (world == null || !world.isInsideWorld(startX, startY, startZ)) {
            return false;
        }
        if (!isLeafId(world.getBlock(startX, startY, startZ))) {
            return false;
        }

        // Tăng nhãn lượt duyệt để đánh dấu đã thăm mà không cấp phát mảng boolean mới
        currentVisitStamp++;
        if (currentVisitStamp <= 0) {
            Arrays.fill(VISITED_STAMP, 0);
            currentVisitStamp = 1;
        }
        int stamp = currentVisitStamp;

        int centerIdx = (MAX_LOG_DISTANCE * GRID_SPAN + MAX_LOG_DISTANCE) * GRID_SPAN + MAX_LOG_DISTANCE;
        VISITED_STAMP[centerIdx] = stamp;

        int qHead = 0;
        int qTail = 0;
        BFS_QUEUE[qTail++] = centerIdx << 4;

        while (qHead < qTail) {
            int entry = BFS_QUEUE[qHead++];
            int idx = entry >>> 4;
            int dist = entry & 0xF;

            // Giải mã tọa độ cục bộ trong hộp 13×13×13
            int lz = idx % GRID_SPAN;
            int tmp = idx / GRID_SPAN;
            int ly = tmp % GRID_SPAN;
            int lx = tmp / GRID_SPAN;

            int wx = startX + (lx - MAX_LOG_DISTANCE);
            int wy = startY + (ly - MAX_LOG_DISTANCE);
            int wz = startZ + (lz - MAX_LOG_DISTANCE);
            int nextDist = dist + 1;

            for (int dir = 0; dir < 6; dir++) {
                int nlx = lx + DX[dir];
                int nly = ly + DY[dir];
                int nlz = lz + DZ[dir];
                if (nlx < 0 || nlx >= GRID_SPAN || nly < 0 || nly >= GRID_SPAN || nlz < 0 || nlz >= GRID_SPAN) {
                    continue;
                }

                int nIdx = (nlx * GRID_SPAN + nly) * GRID_SPAN + nlz;
                if (VISITED_STAMP[nIdx] == stamp) {
                    continue;
                }

                int nwx = wx + DX[dir];
                int nwy = wy + DY[dir];
                int nwz = wz + DZ[dir];
                if (!world.isInsideWorld(nwx, nwy, nwz)) {
                    continue;
                }

                byte neighborId = world.getBlock(nwx, nwy, nwz);
                if (isLogId(neighborId)) {
                    return true;
                }

                VISITED_STAMP[nIdx] = stamp;
                // Chỉ mở rộng tiếp qua lá khi bước kế tiếp vẫn còn khả năng chạm gỗ trong <= 6 bước
                if (isLeafId(neighborId) && nextDist < MAX_LOG_DISTANCE) {
                    BFS_QUEUE[qTail++] = (nIdx << 4) | nextDist;
                }
            }
        }
        return false;
    }

    /**
     * Thực hiện kiểm tra phân hủy cho một khối lá tại {@code (x, y, z)}.
     * Nếu lá không phải lá đặt tay ({@link #PERSISTENT_LEAF_STATE}) và không nối tới gỗ trong
     * bán kính 6 ô, khối lá biến thành {@link Block#AIR} và có 2% cơ hội rơi {@link Item#STICK}.
     *
     * @param world thế giới voxel
     * @param x     tọa độ X
     * @param y     tọa độ Y
     * @param z     tọa độ Z
     */
    public static void tickLeaf(World world, int x, int y, int z) {
        if (world == null || !world.isInsideWorld(x, y, z)) {
            return;
        }
        byte blockId = world.getBlock(x, y, z);
        if (!isLeafId(blockId)) {
            return;
        }
        if ((world.getBlockState(x, y, z) & PERSISTENT_LEAF_STATE) != 0) {
            return;
        }
        if (isConnectedToLog(world, x, y, z)) {
            return;
        }

        // Xóa lá qua đường cập nhật gameplay để đánh dấu dirty mesh và lan truyền hàng xóm
        world.setBlock(x, y, z, Block.AIR.getId());
        if (DROP_RANDOM.nextFloat() < STICK_DROP_CHANCE) {
            world.spawnDroppedItem(new ItemEntity(x + 0.5f, y + 0.5f, z + 0.5f, Item.STICK));
        }
    }

    /**
     * Thông báo 6 ô kề mặt quanh khối gỗ hoặc lá vừa bị phá để xếp lịch kiểm tra phân hủy
     * cho các khối lá tự nhiên lân cận mà không gây tụt khung hình hoặc đệ quy tức thời.
     *
     * @param world thế giới voxel
     * @param x     tọa độ X của khối vừa bị phá
     * @param y     tọa độ Y của khối vừa bị phá
     * @param z     tọa độ Z của khối vừa bị phá
     */
    public static void notifyNeighbors(World world, int x, int y, int z) {
        if (world == null) {
            return;
        }
        for (int dir = 0; dir < 6; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            int nz = z + DZ[dir];
            if (isDecayableLeaf(world, nx, ny, nz)) {
                world.scheduleLeafDecayCheck(nx, ny, nz);
            }
        }
    }

    /**
     * Đưa tọa độ lá vào hàng đợi vòng kích thước cố định (không trùng lặp, không cấp phát rác).
     *
     * @param x tọa độ X thế giới
     * @param y tọa độ Y thế giới
     * @param z tọa độ Z thế giới
     */
    void schedule(int x, int y, int z) {
        if (x < 0 || x >= World.WORLD_SIZE_X || y < 0 || y >= World.WORLD_SIZE_Y
                || z < 0 || z >= World.WORLD_SIZE_Z) {
            return;
        }
        int packed = (x << 19) | (z << 8) | y;
        if (count < pendingQueue.length && pendingSet.add(packed)) {
            pendingQueue[tail] = packed;
            tail = (tail + 1) % pendingQueue.length;
            count++;
        }
    }

    /**
     * Rút dần tối đa {@value #MAX_CHECKS_PER_STEP} ô lá chờ kiểm tra sau mỗi chu kỳ
     * {@value #DECAY_STEP_SECONDS} giây để tán cây phân hủy tự nhiên mượt mà ở 60 FPS.
     *
     * @param world     thế giới voxel
     * @param deltaTime khoảng thời gian giữa hai khung hình (giây)
     */
    /** Triển khai hợp đồng {@link Tickable} để đồng bộ nhịp cập nhật phân hủy lá cây. */
    @Override
    public void tick(World world, float deltaTime) {
        update(world, deltaTime);
    }

    void update(World world, float deltaTime) {
        if (world == null || count == 0) {
            elapsed = 0.0f;
            return;
        }
        elapsed += deltaTime;
        while (elapsed >= DECAY_STEP_SECONDS && count > 0) {
            elapsed -= DECAY_STEP_SECONDS;
            int processed = 0;
            while (count > 0 && processed < MAX_CHECKS_PER_STEP) {
                int packed = pendingQueue[head];
                head = (head + 1) % pendingQueue.length;
                count--;
                pendingSet.remove(packed);

                int x = packed >>> 19;
                int y = packed & 0xFF;
                int z = (packed >>> 8) & 0x7FF;
                tickLeaf(world, x, y, z);
                processed++;
            }
        }
        if (count == 0) {
            elapsed = 0.0f;
        }
    }
}
