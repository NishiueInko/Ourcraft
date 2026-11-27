package com.voxelgame.client.gui;

import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.Chunk;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * Màn hình thông số gỡ lỗi {@code F3} (Debug Overlay) hiển thị {@code FPS}, {@code XYZ},
 * {@code CHUNK}, {@code FACING}, {@code BIOME}, {@code LIGHT} và {@code ENTITIES}.
 *
 * Thiết kế Zero-GC:
 * - Tái sử dụng mảng 7 {@link StringBuilder} thành viên và hai {@link Vector3} đệm cho mọi
 *   khung hình; định dạng số thực dấu phẩy động bằng số học nguyên để không tạo rác {@code String}.
 * - Kết xuất trực tiếp qua font 5×7 và hình chữ nhật đệm dùng lại của {@link UIRenderer}.
 */
public class DebugOverlayRenderer implements Disposable {

    /** Tổng số dòng thông số hiển thị trên màn hình F3 (gồm dòng MODE). */
    public static final int LINE_COUNT = 8;

    /** Thông số bố cục pixel tại góc trên-trái (ngay dưới dòng thời gian của HUD). */
    private static final float ORIGIN_X = 10.0f;
    private static final float ORIGIN_Y = 44.0f;
    private static final float TEXT_SCALE = 1.5f;
    private static final float LINE_STEP = 16.0f;
    private static final float CHAR_ADVANCE = 6.0f * TEXT_SCALE;
    private static final float TEXT_HEIGHT = 7.0f * TEXT_SCALE;
    private static final float BACKDROP_PAD_X = 3.0f;
    private static final float BACKDROP_PAD_Y = 2.0f;

    /** Bảy bộ đệm chuỗi thành viên dùng lại qua từng khung hình (zero-GC). */
    private final StringBuilder[] lineBuffers = new StringBuilder[LINE_COUNT];

    /** Hai vector đệm đọc tọa độ và hướng nhìn mà không cấp phát bản sao mới. */
    private final Vector3 posBuffer = new Vector3();
    private final Vector3 frontBuffer = new Vector3();

    /** Trạng thái hiển thị hiện tại của màn hình F3 (mặc định tắt). */
    private boolean visible = false;

    /** Khởi tạo sẵn 7 bộ đệm {@link StringBuilder} dung lượng 64 ký tự. */
    public DebugOverlayRenderer() {
        for (int i = 0; i < LINE_COUNT; i++) {
            lineBuffers[i] = new StringBuilder(64);
        }
    }

    /** Kiểm tra màn hình thông số F3 hiện có đang bật hay không. */
    public boolean isVisible() {
        return visible;
    }

    /** Đảo trạng thái bật/tắt của màn hình thông số F3. */
    public void toggle() {
        visible = !visible;
    }

    /** Đặt trạng thái bật/tắt tường minh cho màn hình F3. */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** Trả về số dòng thông số cố định của màn hình F3. */
    public int getLineCount() {
        return LINE_COUNT;
    }

    /** Truy cập trực tiếp {@link StringBuilder} thành viên của dòng thứ {@code index} (dùng cho test/vẽ). */
    public StringBuilder getLineBuffer(int index) {
        return lineBuffers[index];
    }

    /**
     * Kết xuất toàn bộ 7 dòng thông số F3 lên góc trên-trái màn hình nếu {@link #isVisible()} đang bật.
     *
     * @param shader  shader 2D đang kích hoạt
     * @param ui      bộ kết xuất {@link UIRenderer} sở hữu font 5×7 và bộ đệm chữ nhật
     * @param world   thế giới voxel hiện tại
     * @param player  người chơi hiện tại
     * @param camera  camera góc nhìn thứ nhất
     * @param screenW chiều rộng cửa sổ (pixel)
     * @param screenH chiều cao cửa sổ (pixel)
     */
    public void render(ShaderProgram shader, UIRenderer ui, World world, Player player, Camera camera,
                       int screenW, int screenH) {
        if (!visible || shader == null || ui == null) {
            return;
        }
        int fps = Gdx.graphics != null ? Gdx.graphics.getFramesPerSecond() : 0;
        formatLines(world, player, camera, fps);

        for (int i = 0; i < LINE_COUNT; i++) {
            StringBuilder line = lineBuffers[i];
            if (line.isEmpty()) {
                continue;
            }
            float y = ORIGIN_Y + i * LINE_STEP;
            float width = line.length() * CHAR_ADVANCE;
            ui.drawOverlayRect(shader, ORIGIN_X - BACKDROP_PAD_X, y - BACKDROP_PAD_Y,
                    width + BACKDROP_PAD_X * 2.0f, TEXT_HEIGHT + BACKDROP_PAD_Y * 2.0f,
                    0.06f, 0.08f, 0.12f, 0.52f);
            ui.drawOverlayText(shader, line, ORIGIN_X, y, TEXT_SCALE);
        }
    }

    /**
     * Cập nhật nội dung 7 dòng thông số F3 vào mảng {@link StringBuilder} thành viên mà không cấp phát rác.
     *
     * @param world  thế giới voxel
     * @param player người chơi
     * @param camera camera
     * @param fps    số khung hình/giây hiện tại
     */
    public void formatLines(World world, Player player, Camera camera, int fps) {
        readPosition(player, camera, posBuffer);
        int bx = (int) Math.floor(posBuffer.x);
        int by = (int) Math.floor(posBuffer.y);
        int bz = (int) Math.floor(posBuffer.z);

        // 1. FPS
        StringBuilder fpsLine = resetLine(0);
        fpsLine.append("FPS: ").append(fps);

        // 2. XYZ
        StringBuilder xyzLine = resetLine(1);
        xyzLine.append("XYZ: ");
        appendFixed2(xyzLine, posBuffer.x);
        xyzLine.append(" / ");
        appendFixed2(xyzLine, posBuffer.y);
        xyzLine.append(" / ");
        appendFixed2(xyzLine, posBuffer.z);

        // 3. CHUNK
        int cx = Math.floorDiv(bx, Chunk.SIZE);
        int cy = Math.floorDiv(by, Chunk.SIZE);
        int cz = Math.floorDiv(bz, Chunk.SIZE);
        int lx = Math.floorMod(bx, Chunk.SIZE);
        int ly = Math.floorMod(by, Chunk.SIZE);
        int lz = Math.floorMod(bz, Chunk.SIZE);
        StringBuilder chunkLine = resetLine(2);
        chunkLine.append("CHUNK: ").append(cx).append(' ').append(cy).append(' ').append(cz)
                .append(" (").append(lx).append(' ').append(ly).append(' ').append(lz).append(')');

        // 4. FACING
        StringBuilder facingLine = resetLine(3);
        facingLine.append("FACING: ").append(resolveFacingLabel(camera, frontBuffer));
        if (camera != null) {
            facingLine.append(" / YAW ");
            appendFixed1(facingLine, wrapDegrees(camera.getYaw()));
            facingLine.append(" / PITCH ");
            appendFixed1(facingLine, camera.getPitch());
        }

        // 5. BIOME
        StringBuilder biomeLine = resetLine(4);
        biomeLine.append("BIOME: ").append(resolveBiomeLabel(world, bx, bz));

        // 6. LIGHT (Đọc trực tiếp Sky Light & Block Light thực tế từ World của Track B)
        StringBuilder lightLine = resetLine(5);
        lightLine.append("LIGHT: ").append(readSkyLight(world, bx, by, bz))
                .append(" / ").append(readBlockLight(world, bx, by, bz));

        // 7. ENTITIES
        StringBuilder entitiesLine = resetLine(6);
        entitiesLine.append("ENTITIES: ").append(world != null ? world.entityCount() : 0);

        // 8. MODE (Chế độ chơi hiện tại: CREATIVE / SURVIVAL)
        StringBuilder modeLine = resetLine(7);
        modeLine.append("MODE: ").append(player != null ? player.getGameMode().getDisplayName() : "SURVIVAL");
    }

    /** Xóa sạch bộ đệm dòng thứ {@code index} để ghi lại nội dung mới. */
    private StringBuilder resetLine(int index) {
        StringBuilder sb = lineBuffers[index];
        sb.setLength(0);
        return sb;
    }

    /** Đọc vị trí ưu tiên từ Player (tọa độ chân), dự phòng từ Camera nếu Player null. */
    private static void readPosition(Player player, Camera camera, Vector3 out) {
        if (player != null) {
            player.copyPosition(out);
        } else if (camera != null) {
            camera.copyPosition(out);
        } else {
            out.setZero();
        }
    }

    /** Xác định tên hướng la bàn và trục tương ứng từ vector hướng nhìn của Camera. */
    private static String resolveFacingLabel(Camera camera, Vector3 front) {
        if (camera == null) {
            return "NORTH (-Z)";
        }
        camera.copyFront(front);
        if (Math.abs(front.x) >= Math.abs(front.z)) {
            return front.x >= 0.0f ? "EAST (+X)" : "WEST (-X)";
        }
        return front.z >= 0.0f ? "SOUTH (+Z)" : "NORTH (-Z)";
    }

    /** Ánh xạ BiomeType sang chuỗi hằng tĩnh (không tạo rác String). */
    private static String resolveBiomeLabel(World world, int bx, int bz) {
        if (world == null) {
            return "UNKNOWN";
        }
        return switch (world.getBiome(bx, bz)) {
            case SNOWY_PLAINS -> "SNOWY PLAINS";
            case FOREST -> "FOREST";
            case PLAINS -> "PLAINS";
            case FLOWER_FOREST -> "FLOWER FOREST";
            case DESERT -> "DESERT";
        };
    }

    /** Helper đọc ánh sáng bầu trời thực tế tại khối hiện tại từ {@link World}. */
    private static int readSkyLight(World world, int x, int y, int z) {
        return world != null ? world.getSkyLight(x, y, z) : 15;
    }

    /** Helper đọc ánh sáng khối nguồn sáng thực tế tại khối hiện tại từ {@link World}. */
    private static int readBlockLight(World world, int x, int y, int z) {
        return world != null ? world.getBlockLight(x, y, z) : 0;
    }

    /** Đưa góc yaw về đoạn [-180, 180] để hiển thị gọn trên màn hình F3. */
    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    /** Ghi số thực với đúng 2 chữ số thập phân vào {@link StringBuilder} mà không cấp phát String. */
    private static void appendFixed2(StringBuilder sb, float value) {
        if (!Float.isFinite(value)) {
            sb.append("0.00");
            return;
        }
        long scaled = Math.round(value * 100.0);
        if (scaled < 0L) {
            sb.append('-');
            scaled = -scaled;
        }
        long whole = scaled / 100L;
        long frac = scaled % 100L;
        sb.append(whole).append('.');
        if (frac < 10L) {
            sb.append('0');
        }
        sb.append(frac);
    }

    /** Ghi số thực với đúng 1 chữ số thập phân vào {@link StringBuilder} mà không cấp phát String. */
    private static void appendFixed1(StringBuilder sb, float value) {
        if (!Float.isFinite(value)) {
            sb.append("0.0");
            return;
        }
        long scaled = Math.round(value * 10.0);
        if (scaled < 0L) {
            sb.append('-');
            scaled = -scaled;
        }
        long whole = scaled / 10L;
        long frac = scaled % 10L;
        sb.append(whole).append('.').append(frac);
    }

    /** Giải phóng tài nguyên phụ thuộc (nếu có) theo giao ước {@link Disposable}. */
    @Override
    public void dispose() {
        visible = false;
    }
}
