package com.voxelgame.world.gamemode;

import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.block.Block;

/**
 * Lớp trừu tượng Flyweight bất biến đại diện cho một Chế độ Chơi ({@code GameMode}) trong VoxelGame.
 *
 * <p>THỂ HIỆN ĐẦY ĐỦ 4 TÍNH CHẤT OOP CHUẨN:</p>
 * <ul>
 *   <li><b>Đóng gói (Encapsulation):</b> Che giấu mã định danh ({@code id}) và tên hiển thị ({@code displayName})
 *       bằng {@code private final}, chỉ cấp quyền đọc an toàn qua getter.</li>
 *   <li><b>Trừu tượng (Abstraction):</b> Định nghĩa hợp đồng hành vi rõ ràng cho khả năng bay, công cụ địa hình,
 *       bất tử, tiêu hao vật phẩm, phá khối, hiển thị HUD sinh tồn, mục tiêu của mob và màn hình kho đồ.</li>
 *   <li><b>Kế thừa (Inheritance):</b> Các chế độ cụ thể ({@link CreativeGameMode}, {@link SurvivalGameMode})
 *       kế thừa chung nền tảng này và có thể mở rộng thêm chế độ mới mà không sửa lớp gọi.</li>
 *   <li><b>Đa hình (Polymorphism):</b> Mọi hệ thống ({@link Player}, {@code GameInputController},
 *       {@code HudRenderer}, {@code MobEntity}, {@code Item}) đều gọi trực tiếp phương thức trên {@code GameMode}
 *       thay vì phân nhánh {@code if/else} hay {@code switch} theo loại chế độ.</li>
 * </ul>
 *
 * <p>Thiết kế Zero-GC: mỗi chế độ là một thể hiện bất biến dùng lại xuyên suốt vòng đời ứng dụng.</p>
 */
public abstract class GameMode {

    /** Mã định danh ngắn gọn bất biến của chế độ (ví dụ: {@code "creative"}, {@code "survival"}). */
    private final String id;

    /** Tên hiển thị in hoa tương thích trực tiếp với font bitmap 5×7 (zero-GC). */
    private final String displayName;

    /** Chuỗi thông báo ngắn dựng sẵn khi chuyển sang chế độ này (không cấp phát String trong Game Loop). */
    private final String toastLabel;

    /**
     * Khởi tạo siêu dữ liệu bất biến cho một chế độ chơi.
     *
     * @param id          mã định danh nội bộ
     * @param displayName tên hiển thị in hoa (tương thích {@code BitmapFont})
     */
    protected GameMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.toastLabel = "MODE: " + displayName;
    }

    /** Trả về mã định danh nội bộ của chế độ chơi. */
    public final String getId() {
        return id;
    }

    /** Trả về tên hiển thị in hoa của chế độ chơi (ví dụ: {@code "CREATIVE"}, {@code "SURVIVAL"}). */
    public final String getDisplayName() {
        return displayName;
    }

    /** Trả về chuỗi thông báo HUD dựng sẵn khi chuyển chế độ (zero-GC). */
    public final String getToastLabel() {
        return toastLabel;
    }

    /** Cho biết chế độ này có cho phép người chơi bật/duy trì chế độ bay ({@code Space} ×2) hay không. */
    public abstract boolean canFly();

    /** Cho biết chế độ này có cho phép dùng các công cụ kiểm tra địa hình ({@code F1} tốc độ, {@code Ctrl+C} ghé biome) hay không. */
    public abstract boolean canUseTerrainTools();

    /** Cho biết người chơi có miễn nhiễm sát thương, đói, Oxy và lửa trong chế độ này hay không. */
    public abstract boolean isInvulnerable();

    /** Cho biết đặt khối, bắn cung, ăn/uống và ném vật phẩm có tiêu hao số lượng trong túi đồ hay không. */
    public abstract boolean consumesItems();

    /** Cho biết đập vỡ khối có xảy ra tức thì thay vì tính theo độ cứng hay không. */
    public abstract boolean breaksInstantly();

    /**
     * Kiểm tra một loại {@link Block} có thể bị người chơi phá vỡ trong chế độ này hay không.
     *
     * @param block khối mục tiêu đang ngắm
     * @return {@code true} nếu được phép phá vỡ
     */
    public abstract boolean canBreakBlock(Block block);

    /** Cho biết khối bị người chơi phá vỡ có rơi thực thể vật phẩm ({@code ItemEntity}) hay không. */
    public abstract boolean dropsBlockItems();

    /** Cho biết HUD có hiển thị các thanh sinh tồn (máu, đói, Oxy) hay không. */
    public abstract boolean showsSurvivalHud();

    /** Cho biết mob thù địch hoặc trung lập có được phép nhắm người chơi làm mục tiêu tấn công hay không. */
    public abstract boolean isTargetableByMobs();

    /** Cho biết phím {@code E} mở Kho Creative chứa toàn bộ vật phẩm thay vì túi đồ 36 ô sinh tồn hay không. */
    public abstract boolean usesCreativeInventory();

    /**
     * Hook vòng đời được gọi mỗi khi người chơi vừa chuyển sang chế độ này,
     * dùng để chuẩn hóa trạng thái bay, tốc độ hoặc chỉ số sinh tồn.
     *
     * @param player người chơi vừa chuyển chế độ
     */
    public abstract void onEnter(Player player);

    /**
     * Trả về chế độ chơi kế tiếp khi người chơi bấm phím chuyển đổi {@code F2}.
     *
     * @return thể hiện {@link GameMode} đối lập (zero-GC)
     */
    public abstract GameMode toggle();

    @Override
    public String toString() {
        return displayName;
    }
}
