# Hướng Dẫn Chi Tiết Cài Đặt, Tổ Chức Tài Nguyên & Chạy Thử (Phần 4)

## 1. Đóng Gói Sẵn Toàn Bộ Trong `VoxelGame.zip`

Trong file nén **`VoxelGame.zip`** đính kèm, mình đã **tích hợp sẵn 100% toàn bộ 14 class Java, file cấu hình `build.gradle`, bộ công cụ Gradle Wrapper, toàn bộ các file ảnh từ `texture.zip` và bản nhạc nền `1-08. Minecraft.mp3`** vào đúng thư mục chuẩn `src/main/resources/`. Bạn chỉ cần giải nén là có thể bấm **Run** chạy ngay mà không cần copy thủ công!

Nếu bạn muốn tự tay thêm hoặc thay đổi file nhạc/ảnh trong project hiện tại của mình, hãy đặt chúng theo đúng đường dẫn dưới đây:

```text
VoxelGame/
└── src/
    └── main/
        ├── java/com/voxelgame/                  <-- Chứa đầy đủ 14 file .java (Từ Phần 1 đến Phần 4)
        └── resources/
            ├── audio/
            │   └── 1-08. Minecraft.mp3          <-- Nhạc nền MP3 phát lặp lại (Loop)
            └── textures/
                ├── bedrock.png                  <-- Khối Bedrock (y = 0)
                ├── stone.png                    <-- Khối Đá (Stone)
                ├── grass-block.png              <-- Tấm sheet 32x32 tách tự động thành Mặt Cỏ Trên & Mặt Cỏ Bên
                ├── Minecraft Dirt Small by terridee, click to purchase fabric.jpg   <-- Khối Đất (Dirt)
                ├── Minecraft Oak Log Block Texture Pixel Art.jpg                    <-- Thân Gỗ Sồi (Oak Log)
                ├── water_still.png              <-- Mặt Nước (Water)
                └── download.jpg                 <-- Dung Nham (Lava)
```

## 2. Cách Mở & Chạy Project Trong IntelliJ IDEA

1. **Giải nén:** Tải file `VoxelGame.zip` về máy và giải nén ra một thư mục bất kỳ.
2. **Mở Project:** Mở **IntelliJ IDEA** -> Chọn **Open** -> Trỏ vào thư mục `VoxelGame` (nơi chứa file `build.gradle`) -> Nhấn **OK**.
3. **Đồng bộ Gradle (Load Gradle Changes):**
   - Đợi vài giây để IntelliJ tự động tải thêm 2 thư viện mới trong `build.gradle` là **`org.lwjgl:lwjgl-stb`** (đọc ảnh) và **`javazoom:jlayer:1.0.1`** (phát nhạc MP3).
   - Nếu thanh công cụ bên phải có biểu tượng con voi **Gradle**, bạn có thể nhấn nút **Reload All Gradle Projects** (biểu tượng 2 mũi tên xoay vòng) để đồng bộ ngay lập tức.
4. **Khởi chạy Game:**
   - Mở file `src/main/java/com/voxelgame/Main.java` và nhấn biểu tượng **Run (nút Play màu xanh lá)** ở dòng `public static void main`, hoặc mở tab **Gradle -> Tasks -> application -> run**.

## 3. Bảng Điều Khiển Toàn Bộ 6 Tính Năng Mới Trong Game

| Thao tác / Phím bấm | Chức năng trong Game |
| :--- | :--- |
| **`W, A, S, D` + Di chuột** | Di chuyển 4 hướng và xoay góc nhìn 360 độ. |
| **Nhấn `Space` 1 lần** | Nhảy lên khi đang đứng trên mặt đất (hoặc bơi nổi lên khi đang ở dưới Hồ Nước / Lava). |
| **Nhấn đúp `Space` 2 lần (`< 0.3s`)** | **Bật / Tắt Chế độ Bay (Fly Mode)**: Vô hiệu hóa trọng lực, giữ `Space` để bay lên cao. |
| **Nhấn `Left Shift` (khi đang Bay)** | **Thoát Chế độ Bay** ngay lập tức và bật lại trọng lực rơi tự nhiên. |
| **Giữ `Left Shift` (trên mặt đất)** | **Đi rón rén (Sneak)**: Giảm 50% tốc độ, hạ thấp tầm mắt Camera và **giữ mép block không bị rơi xuống vực**. |
| **Giữ `Left Ctrl` khi di chuyển** | **Chạy nhanh (Sprint)**: Tăng 1.5 lần tốc độ di chuyển kèm hiệu ứng mở rộng nhẹ góc nhìn (Dynamic FOV). |
| **Phím số `1` – `9` hoặc Cuộn chuột** | Chọn ô trên **9 Thanh Công Cụ Nhanh (Hotbar HUD)** ở cạnh dưới màn hình. |
| **Nhấn giữ Chuột Trái** | **Đập Block bằng tay không theo độ cứng**: Lá cây (`0.3s`), Đất/Cỏ (`0.75s`), Gỗ (`3.0s`), Đá (`5.0s`). Khi vỡ sẽ văng ra **Item 3D xoay tròn**, đi lại gần sẽ tự động hút vào Hotbar. (Tia nhìn xuyên qua Nước/Lava; Bedrock không thể phá hủy). |
| **Click Chuột Phải** | Đặt loại Block đang cầm ở ô Hotbar được chọn vào mặt phẳng đang nhìn (tự động trừ số lượng). |
| **Nhấn `ESC` lần 1** | **Tạm dừng Game (Pause Menu)**: Hiện con trỏ chuột để bấm ô **"RESUME GAME"** hoặc chỉnh **Volume (`[-]` / Thanh trượt / `[+]`)** từ `0%` đến `100%`. |
| **Nhấn `ESC` lần 2 (lúc đang Pause)** | Đóng cửa sổ và thoát hoàn toàn khỏi trò chơi. |
