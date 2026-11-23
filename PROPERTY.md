# PROPERTY.md — Đặc Tả Tính Chất Dự Án VoxelGame

Phần A mô tả **100% tính chất hiện có** của dự án **VoxelGame**. Sau mỗi thay đổi, rà soát và cập nhật các mục bị ảnh hưởng để đặc tả phản ánh đúng và đủ trạng thái mới nhất của dự án.

---

## PHẦN A: ĐẶC TẢ 100% TÍNH CHẤT HIỆN CÓ CỦA DỰ ÁN

### 1. Thông Tin Dự Án, Build & Tài Nguyên
- **Tên dự án / Gradle:** `VoxelGame` (`group = 'com.voxelgame'`, `version = '1.0-SNAPSHOT'`).
- **Java & Biên dịch:** Java 17 (`sourceCompatibility = JavaVersion.VERSION_17`, `targetCompatibility = JavaVersion.VERSION_17`), ép mã hóa nguồn `UTF-8` cho `JavaCompile`.
- **Entry Point & Cửa sổ:** `com.voxelgame.Main` khởi tạo `Lwjgl3ApplicationConfiguration` (kích thước mặc định `1280 x 720`, V-Sync `true`, Back Buffer RGBA 8-8-8-8/Depth 24 bit, Resizable `true`) và chạy `new Lwjgl3Application(new VoxelGameApp(), config)`. Tiêu đề cửa sổ: `"Voxel Game 3D - Minecraft OOP Edition (Java & libGDX LWJGL3)"`. Trên macOS tự động truyền tham số JVM `-XstartOnFirstThread`.
- **Thư viện phụ thuộc (`build.gradle`):**
  - **libGDX `1.13.1`** (`com.badlogicgames.gdx`): `gdx` (Core API), `gdx-backend-lwjgl3` (Backend Desktop LWJGL 3), `gdx-platform:natives-desktop` (Native tự động cho Windows/Linux/macOS, x64/arm64).
  - Đã loại bỏ hoàn toàn: LWJGL BOM 3.3.3, JOML 1.10.5, JLayer 1.0.1 (libGDX đã tích hợp sẵn LWJGL 3, GLFW, OpenGL, STB, OpenAL và thư viện toán học).
- **Tài nguyên (`src/main/resources/`):**
  - **Âm thanh:** `audio/1-08. Minecraft.mp3` (hỗ trợ tìm linh hoạt trong classpath, `src/main/resources/`, `src/main/resources/audio/`, `audio/`, `music/`, thư mục gốc hoặc tự quét sâu tối đa 4 cấp tìm file `.mp3` đầu tiên).
  - **Texture:** `textures/` gồm `bedrock.png`, `stone.png`, `grass-block.png`, `Minecraft Dirt Small by terridee, click to purchase fabric.jpg`, `Minecraft Oak Log Block Texture Pixel Art.jpg`, `water_still.png`, `download.jpg` và tùy chọn `oak_leaves.png` (hỗ trợ tìm linh hoạt theo nhiều tên dự phòng, tìm trong classpath/thư mục gốc/quét sâu 5 cấp bỏ qua `.gradle`, `.git`, và có nền Procedural Pixel Art fallback).

---

### 2. Kiến Trúc Mã Nguồn Hiện Tại (23 File Java)
Dự án đã hoàn thành chuyển đổi 100% (23/23 file) từ LWJGL thuần sang framework libGDX (`gdx-backend-lwjgl3`). Tổ chức theo 4 tính chất OOP (Đóng gói, Kế thừa, Đa hình, Trừu tượng hóa) chia làm 3 vùng:
- **`com.voxelgame` (2 file):**
  - `Main.java`: Điểm khởi chạy duy nhất, cấu hình `Lwjgl3ApplicationConfiguration` và tạo `Lwjgl3Application(new VoxelGameApp(), config)`.
  - `VoxelGameApp.java`: Lớp ứng dụng chính kế thừa `ApplicationAdapter` và triển khai `InputProcessor`. Quản lý toàn bộ vòng lặp Game Loop (`render()`), trạng thái Pause Menu, xử lý sự kiện bàn phím/chuột, đào/đặt block, và giải phóng tài nguyên (`dispose()`). Thay thế hoàn toàn vai trò của `Window.java` cũ.
- **`com.voxelgame.engine` (8 file):** *(Đã hoàn thành chuyển đổi 100% 8/8 file sang libGDX)*
  - `Raycast.java`: Thuật toán bắn tia 3D DDA (Amanatides & Woo) dùng `com.badlogic.gdx.math.Vector3` và Value Object bất biến `HitResult`.
  - `Camera.java`: Camera góc nhìn thứ nhất, góc Euler (`yaw`, `pitch`), Dynamic FOV, ma trận View/Perspective/Orthographic bằng `Matrix4`/`Vector3`, áp dụng Defensive Copying và tái sử dụng ma trận tránh GC churn.
  - `Texture.java`: Nạp ảnh đa tầng và sinh Procedural Pixel Art qua `Pixmap`, bọc thành `com.badlogic.gdx.graphics.Texture` Atlas `64 x 64` duy nhất trên VRAM (`Nearest`, `ClampToEdge`), tự động giải phóng Pixmap trên RAM.
  - `ShaderProgram.java`: Bao bọc `com.badlogic.gdx.graphics.glutils.ShaderProgram`, biên dịch GLSL 330 Core không tiền tố, hỗ trợ chế độ 3D World (Texture, Directional Shading) và 2D UI.
  - `Chunk.java`: Quản lý phân vùng `16 x 16 x 16` block (mảng phẳng `byte[]`), kiểm tra hộp bao với khung nhìn, dựng và kết xuất `Mesh` (6 floats/đỉnh: `aPos`, `aTexCoord`, `aShade`), tô sáng theo hướng mặt và hạ mặt thoáng chất lỏng xuống `0.88f`.
  - `World.java`: Quản lý lưới thưa `Chunk[112][16][112]`, frustum culling và bộ nhớ đệm mesh cho chunk, sinh địa hình tự nhiên 3 giai đoạn seed `20261114L`, quản lý danh sách thực thể đa hình `List<Entity> entities` (gồm `Player` và các `ItemEntity`), triển khai `Disposable`.
  - `UIRenderer.java`: Vẽ overlay 3D quanh block mục tiêu và toàn bộ giao diện 2D (Crosshair, Mining Bar, Hotbar, Pause Menu, font bitmap 5x7, Hit-Testing chuột) qua `com.badlogic.gdx.graphics.Mesh` và `com.badlogic.gdx.math.Matrix4`, triển khai `Disposable`.
  - `AudioManager.java`: Quản lý phát lặp nhạc nền MP3 và chỉnh âm lượng thời gian thực thông qua `com.badlogic.gdx.audio.Music` (chạy trên OpenAL) và `com.badlogic.gdx.files.FileHandle`, nạp linh hoạt đa tầng, triển khai `Disposable`.
- **`com.voxelgame.entity` (13 file):** *(Đã hoàn thành chuyển đổi 100% 13/13 file sang libGDX)*
  - `Entity.java`: Lớp cha trừu tượng chứa `position` (Bottom-Center) và `velocity` bằng `com.badlogic.gdx.math.Vector3`, `halfWidth`, `height`, `onGround`, kiểm tra va chạm `intersectsBlock(...)`, Defensive Copying và 2 phương thức trừu tượng `update(World, float)`, `render(ShaderProgram)`.
  - `Player.java`: Kế thừa `Entity`, sở hữu `Hotbar` theo quan hệ Composition (`private final Hotbar hotbar`), đọc phím qua `Gdx.input.isKeyPressed(Input.Keys.*)`, bật/tắt bay khi nhấn đúp Space, hạ độ cao hoặc thoát bay bằng Left Shift, chạy nhanh bằng Left Ctrl, rón rén giữ mép block, bơi trong chất lỏng và giải quyết va chạm AABB theo thứ tự `X -> Z -> Y`.
  - `ItemEntity.java`: Kế thừa `Entity`, đại diện vật phẩm 3D thu nhỏ rơi ra khi đập block, dùng chung `com.badlogic.gdx.graphics.Mesh` theo từng `Block` qua `SHARED_MESHES` (`HashMap`), hoạt ảnh xoay 3D `110°/s`, nhấp nhô sin, nam châm hút và tự nhặt vào Hotbar.
  - `Hotbar.java`: Quản lý 9 ô thanh công cụ nhanh (tối đa 64 item/ô), logic cuộn chuột modulo 9, ưu tiên gộp ô cùng loại.
  - `Block.java` (Flyweight Pattern): Khai báo mảng tra cứu `BY_ID = new Block[16]` trước 9 hằng số singleton để constructor `super(...)` tự đăng ký O(1) không gặp `NullPointerException`.
  - **9 lớp con:** `AirBlock`, `DirtBlock`, `GrassBlock`, `StoneBlock`, `BedrockBlock`, `OakLogBlock`, `OakLeavesBlock`, `WaterBlock`, `LavaBlock`.

---

### 3. Hệ Thống Block (9 Loại) & Cơ Chế Đào / Rơi Item
| ID | Lớp | Tên hiển thị | Độ cứng (giây) | Rắn (`solid`) | Lỏng (`liquid`) | Đục (`opaque`) | Tile Atlas theo mặt | Hành vi đặc biệt |
| :-: | :--- | :--- | :-: | :-: | :-: | :-: | :--- | :--- |
| `0` | `AirBlock` | Air[cite: 1] | `-1.0f`[cite: 1] | `false`[cite: 1] | `false`[cite: 1] | `false`[cite: 1] | `0`[cite: 1] | Không thể phá hủy, không rơi item[cite: 1] |
| `1` | `DirtBlock` | Dirt[cite: 1] | `0.75f`[cite: 1] | `true`[cite: 1] | `false`[cite: 1] | `true`[cite: 1] | 6 mặt: `TILE_DIRT (0)`[cite: 1] | Đập vỡ rơi ra `Dirt`[cite: 1] |
| `2` | `GrassBlock` | Grass Block[cite: 1] | `0.75f`[cite: 1] | `true`[cite: 1] | `false`[cite: 1] | `true`[cite: 1] | `TOP=1`, `BOTTOM=0`, 4 mặt bên=`2`[cite: 1] | Đập vỡ bằng tay không rơi ra **`Block.DIRT`**[cite: 1] |
| `3` | `StoneBlock` | Stone[cite: 1] | `5.0f`[cite: 1] | `true`[cite: 1] | `false`[cite: 1] | `true`[cite: 1] | 6 mặt: `TILE_STONE (3)`[cite: 1] | Đập vỡ rơi ra `Stone`[cite: 1] |
| `4` | `BedrockBlock` | Bedrock[cite: 1] | `-1.0f`[cite: 1] | `true`[cite: 1] | `false`[cite: 1] | `true`[cite: 1] | 6 mặt: `TILE_BEDROCK (4)`[cite: 1] | Nằm ở đáy `y = 0`, không thể phá hủy[cite: 1] |
| `5` | `OakLogBlock` | Oak Log[cite: 1] | `3.0f`[cite: 1] | `true`[cite: 1] | `false`[cite: 1] | `true`[cite: 1] | `TOP/BOTTOM=6`, 4 mặt bên=`5`[cite: 1] | Đập vỡ rơi ra `Oak Log`[cite: 1] |
| `6` | `OakLeavesBlock` | Oak Leaves[cite: 1] | `0.30f`[cite: 1] | `true`[cite: 1] | `false`[cite: 1] | `true`[cite: 1] | 6 mặt: `TILE_OAK_LEAVES (7)`[cite: 1] | Đập rất nhanh, rơi ra `Oak Leaves`[cite: 1] |
| `7` | `WaterBlock` | Water[cite: 1] | `-1.0f`[cite: 1] | `false`[cite: 1] | `true`[cite: 1] | `false`[cite: 1] | 6 mặt: `TILE_WATER (8)`[cite: 1] | Raycast đi xuyên qua; `getTopHeight = 0.88f` nếu trên là `AIR`[cite: 1] |
| `8` | `LavaBlock` | Lava[cite: 1] | `-1.0f`[cite: 1] | `false`[cite: 1] | `true`[cite: 1] | `false`[cite: 1] | 6 mặt: `TILE_LAVA (9)`[cite: 1] | Raycast đi xuyên qua; `getTopHeight = 0.88f` nếu trên là `AIR`[cite: 1] |

---

### 4. Gameplay & Hệ Thống Điều Khiển
- **Di chuyển & Xoay góc nhìn:** `W, A, S, D` di chuyển 4 hướng trên mặt phẳng ngang XZ theo hướng `yaw`; di chuột xoay góc nhìn 360° với độ nhạy `MOUSE_SENSITIVITY = 0.12f` và giới hạn `pitch` trong `[-89.0°, 89.0°]`[cite: 1].
- **Nhảy & Bơi (`Space` 1 lần):** Nhảy khi `onGround == true` (`JUMP_VELOCITY = 8.6f`)[cite: 1]; khi đang trong Nước hoặc Lava sẽ bơi nổi lên với vận tốc dọc `4.2f`[cite: 1].
- **Chế độ Bay (nhấn đúp `Space` trong tối đa `0.30s`):** Bật/tắt Fly Mode. Khi bay, trọng lực tắt, giữ `Space` để bay lên với tốc độ `7.0` block/s. Giữ cả hai phím thì ưu tiên `Space`. Thả cả `Space` và `Left Shift` để giữ độ cao. Tốc độ ngang là `1.8x`, nhân thêm `1.35x` khi chạy nhanh. Chạm mặt đất sẽ tự thoát Fly Mode.
- **Hạ độ cao, thoát bay và rón rén (`Left Shift`):**
  - Khi đang bay, giữ `Left Shift` để hạ độ cao với tốc độ `4.0` block/s. Nhấn đúp `Left Shift` trong tối đa `0.30s` để thoát bay và bật lại trọng lực.
  - Khi đứng/đi trên mặt đất: bật **Sneak**, giảm tốc độ còn `0.5x`, nội suy mượt tầm mắt Camera từ `1.62f` xuống `1.45f` (tốc độ nội suy `deltaTime * 14.0f`), và kích hoạt **giữ mép block** (`preventFallingOffEdgeX/Z` theo bước `0.02f`) không cho rơi xuống vực[cite: 1].
- **Chạy nhanh (`Left Ctrl`):** Bật **Sprint** khi đang di chuyển ngang và không Sneak; tăng `1.5x` tốc độ đi bộ kèm hiệu ứng mở rộng Dynamic FOV[cite: 1].
- **9 Ô Hotbar (`1`–`9` hoặc Cuộn chuột):**
  - `SLOT_COUNT = 9`, `MAX_STACK_SIZE = 64`[cite: 1].
  - Cuộn chuột lên giảm chỉ số ô, cuộn xuống tăng chỉ số ô (xoay vòng modulo 9)[cite: 1].
  - Trang bị khởi đầu: Ô 1 `Dirt x16`, Ô 2 `Grass Block x16`, Ô 3 `Stone x16`, Ô 4 `Oak Log x12`, Ô 5 `Oak Leaves x16`, Ô 6–9 trống[cite: 1].
  - Cơ chế nhặt đồ (`addItem`): ưu tiên cộng dồn vào các ô có sẵn cùng loại chưa đầy 64, sau đó mới xếp vào ô trống đầu tiên từ trái sang phải[cite: 1].
- **Đào Block (Giữ Chuột Trái):**
  - Tầm với `RAYCAST_REACH = 5.0f`[cite: 1].
  - Tích lũy `miningElapsedTime` cho đến khi `>= hardness` sẽ đổi ô thành `Block.AIR` và gọi `target.onBreak(...)` sinh `ItemEntity` tại tâm block `(x + 0.5, y + 0.5, z + 0.5)`[cite: 1].
  - Nhả chuột, nhìn sang ô khác, hoặc ngắm vào khối không phá được sẽ reset tiến trình về `0`[cite: 1].
- **Đặt Block (Click Chuột Phải):**
  - Lấy block ở ô Hotbar đang chọn (nếu khác `AIR` và `count > 0`), đặt vào tọa độ `(prevBlockX, prevBlockY, prevBlockZ)` của Raycast nếu nằm trong biên thế giới và `!player.intersectsBlock(...)`, đồng thời trừ 1 item khỏi ô đang chọn[cite: 1].
- **Tạm dừng & Thoát (`ESC`):**
  - Nhấn `ESC` lần 1: bật `isPaused`, reset tiến trình đào, mở con trỏ chuột (`GLFW_CURSOR_NORMAL`), đóng băng cập nhật thực thể[cite: 1].
  - Nhấn `ESC` lần 2 khi đang Pause: đặt cờ đóng cửa sổ và thoát hoàn toàn khỏi game[cite: 1].

---

### 5. Vật Lý & Thuật Toán Sinh Thế Giới
- **Vật lý Người chơi (`Player`):**
  - Spawn tại tâm ngang `(x = 896, z = 896)`. Độ cao chân bằng đỉnh cao nhất trong bốn cột block giáp tâm. Nếu rơi xuống dưới `y < -25.0f`, tự động đưa về `(896, 22, 896)`, reset vận tốc và tắt bay.
  - Hộp va chạm AABB: `PLAYER_HALF_WIDTH = 0.3f` (rộng `0.6f`), `PLAYER_HEIGHT = 1.8f`[cite: 1].
  - Thông số vận tốc: `MOVE_SPEED = 5.5f`, `GRAVITY = -26.0f`, `TERMINAL_VELOCITY = -45.0f`[cite: 1].
  - Nhận diện chất lỏng (`isInLiquid`): kiểm tra block tại chân (`y + 0.1f`) và thắt lưng (`y + 0.9f`)[cite: 1]. Trong chất lỏng (không bay): tốc độ ngang `0.65x`, trọng lực `0.25x`, vận tốc chìm tối đa `-3.5f`[cite: 1].
  - Va chạm AABB tách trục theo đúng thứ tự `X -> Z -> Y` (đẩy ra với sai số `0.0001f`)[cite: 1].
- **Raycast 3D DDA (`Raycast`):**
  - Thuật toán Amanatides & Woo duyệt chính xác từng ô voxel dọc tia, bỏ qua `Air` và mọi khối lỏng (`!block.isSolid()`), chỉ dừng khi gặp khối rắn[cite: 1].
- **Vật lý Vật phẩm Rơi (`ItemEntity`):**
  - Kích thước `ITEM_SCALE = 0.28f` (`HALF_SIZE = 0.14f`)[cite: 1].
  - Vận tốc văng ban đầu ngẫu nhiên: `vx, vz ∈ [-0.7, 0.7]`, `vy = 3.2f`[cite: 1].
  - Trọng lực `ITEM_GRAVITY = -18.0f` (tối đa `-25.0f`), ma sát ngang `0.82f` (trên đất) / `0.96f` (trên không), va chạm 3 trục với khối rắn[cite: 1].
  - Sau `PICKUP_DELAY = 0.25s`: kích hoạt lực hút nam châm trong bán kính `MAGNET_DISTANCE = 1.85f` (kéo về tâm cơ thể người chơi với tốc độ `9.5f`), tự động nhặt vào `Hotbar` khi khoảng cách `<= PICKUP_DISTANCE (0.85f)`[cite: 1].
  - Tự biến mất sau `MAX_LIFETIME = 300.0s`[cite: 1].
  - Hoạt ảnh 3D: nhấp nhô `sin(age * 3.8f) * 0.07f + 0.08f` và tự xoay quanh trục Y theo góc `age * 110.0f` độ[cite: 1].
- **Thế giới & Sinh Địa hình (`World`, seed `20261114L`):**
  - Kích thước: `112 x 16 x 112` chunk (`Chunk.SIZE = 16`) = **`1792 x 256 x 1792` block**, với tọa độ hợp lệ `x, z = 0..1791`, `y = 0..255`. Ô lưới chỉ chứa không khí dùng `null`. Đặt block đầu tiên tạo chunk và xóa block cuối cùng giải phóng chunk cùng mesh của nó.
  - Giới hạn chiều cao địa hình tự nhiên: `MAX_TERRAIN_LAYERS = 20` (`MAX_TERRAIN_Y = 19`)[cite: 1].
  - **Giai đoạn 1 (Phân tầng địa chất):** `surfaceY = clamp(round(10.0 + sin(wx*0.085)*cos(wz*0.085)*2.2 + sin((wx+wz)*0.05)*1.4), 5, 19)`; độ dày đất `2` hoặc `3` lớp (`2 + ((wx*13 + wz*7) & 1)`); `y = 0` Bedrock, dưới lớp đất là Stone, lớp đệm là Dirt, đỉnh `surfaceY` là Grass[cite: 1].
  - **Giai đoạn 2 (Hồ Nước & Dung nham):**
    - 4 hồ nước (đáy `Dirt`, bờ `Grass/Dirt`): `(22, 24, r=6.5, sâu 2)`, `(52, 28, r=7.5, sâu 2)`, `(26, 62, r=7.0, sâu 2)`, `(68, 66, r=8.0, sâu 3)`[cite: 1].
    - 4 hồ dung nham (đáy & bờ `Stone`): `(42, 20, r=4.8, sâu 2)`, `(20, 44, r=4.5, sâu 2)`, `(64, 46, r=5.2, sâu 2)`, `(46, 72, r=5.0, sâu 2)`[cite: 1].
  - **Giai đoạn 3 (Cây Sồi tự nhiên):** Quét bước 4 trong `[6, WORLD_SIZE - 6)` với lệch ngẫu nhiên `[-1, 1]`. Bỏ qua gốc cây trong vùng cách tâm spawn `(896, 896)` tối đa 4 block trên cả hai trục. Xác suất 28% trên mặt `Grass` (`y ∈ [4, 19]`) không có chất lỏng trong bán kính 2. Thân `Oak Log` cao `4..6` block, ô ngay dưới gốc chuyển thành `Dirt`, tán `Oak Leaves` 4 tầng với bán kính 2 ở hai tầng dưới và bán kính 1 ở hai tầng trên, tỉa góc tự nhiên.

---

### 6. Đồ Họa, Shader, Texture & Hiệu Năng 60 FPS
- **OpenGL & Vòng lặp:** OpenGL 3.3 Core Profile trong backend LWJGL 3 của libGDX, bật `GL_DEPTH_TEST`, `GL_CULL_FACE` (`GL_BACK`, `GL_CCW`), bật V-Sync `config.useVsync(true)` đạt 60 FPS mượt mà, giới hạn `deltaTime <= 0.05f`.
- **Màu trời & Ống kính:**
  - `SKY_COLOR = (0.53f, 0.81f, 0.92f)` dùng cho màu xóa nền.
  - Camera: `BASE_FOV_DEG = 70.0f` (`+8.0f` khi Sprint, `+5.0f` khi Fly, nội suy `deltaTime * 10.0f`), `NEAR_PLANE = 0.08f`, `FAR_PLANE = 1024.0f`, ma trận tính qua `Matrix4.setToProjection` và `setToLookAt`.
- **Định dạng Đỉnh & Shader (`ShaderProgram`):**
  - 6 floats/đỉnh: `[x, y, z, u, v, shade]` (`location 0: vec3 aPos`, `location 1: vec2 aTexCoord`, `location 2: float aShade`) quản lý qua `VertexAttributes` của libGDX.
  - Fragment Shader chọn màu từ `uTexture * uTintColor` hoặc `uTintColor`, loại bỏ pixel có `alpha < 0.05`, rồi nhân màu RGB với `vShade`.
  - Xóa tiền tố `prependVertexCode`/`prependFragmentCode` để giữ `#version 330 core` ở dòng đầu tiên.
- **Ánh sáng & Face Culling (`Chunk`):**
  - Directional Face Shading: `SHADE_TOP = 1.00f`, `SHADE_BOTTOM = 0.55f`, `SHADE_NORTH_SOUTH = 0.85f`, `SHADE_EAST_WEST = 0.72f`.
  - Face Culling: Khối lỏng chỉ vẽ mặt tiếp giáp `Block.AIR`; khối rắn chỉ vẽ mặt tiếp giáp `!neighbor.isSolid()`.
  - Kết xuất thông qua `com.badlogic.gdx.graphics.Mesh`.
  - Khi `World.setBlock(...)` sửa block ở biên chunk (`0` hoặc `15`), mesh của các chunk lân cận đã có dữ liệu được đánh dấu `dirty`.
- **Frustum culling và bộ nhớ đệm mesh (`World`):** Giới hạn vùng quét bằng hộp bao khung nhìn, rồi kiểm tra hộp bao từng chunk bằng sáu mặt phẳng khung nhìn từ ma trận của shader. Khi tải, dựng trước mesh trong vùng 5 × 5 cột chunk quanh spawn. Trong lúc chơi, mỗi khung hình dựng hoặc cập nhật tối đa 16 mesh giao khung nhìn, ưu tiên chunk gần camera. Thu hồi mesh sau 120 khung hình liên tiếp ngoài khung nhìn, giữ dữ liệu block và các sửa đổi để dựng lại khi cần. Bộ đệm đỉnh `FloatArray` và bảng UV được dùng lại khi dựng mesh.
- **Texture Atlas (`Texture`):**
  - Lưới `4 x 4` tile, mỗi tile `16 x 16` pixel -> Atlas `64 x 64` RGBA, lọc `TextureFilter.Nearest`, bao biên `TextureWrap.ClampToEdge`, lùi biên UV `eps = 0.0008f` chống Texture Bleeding.
  - Thứ tự 10 tile đang dùng: `0=DIRT`, `1=GRASS_TOP`, `2=GRASS_SIDE`, `3=STONE`, `4=BEDROCK`, `5=OAK_LOG_SIDE`, `6=OAK_LOG_TOP`, `7=OAK_LEAVES`, `8=WATER`, `9=LAVA`.
  - Xử lý ảnh thông minh bằng `Pixmap`: tự tách sheet `32x32` của `grass-block.png` thành 3 tile (`GRASS_TOP`, `GRASS_SIDE`, `DIRT`); cắt khung vuông đầu tiên của `water_still.png` và nhuộm xanh nước biển (`alpha = 225`) nếu ảnh nguồn là thang xám; có đầy đủ 10 tile Procedural Pixel Art (seed `20261114L`) làm nền dự phòng; tự động `dispose()` Pixmap sau khi nạp lên GPU.
- **Tối ưu Hiệu năng & Quản lý Bộ nhớ:**
  - Tái sử dụng các `Matrix4` làm trường thành viên để tránh sinh rác GC trong Game Loop.
  - Dùng chung `Mesh` theo loại `Block` cho mọi `ItemEntity` (`SHARED_MESHES`).
  - Giải phóng triệt để tài nguyên GPU qua `Disposable.dispose()` cho mọi lớp `Chunk`, `World`, `Texture`, `ShaderProgram`, `ItemEntity`.

---

### 7. Giao Diện Người Dùng (UI/HUD) & Font 5x7 (`UIRenderer`)
- **Kết xuất qua libGDX:** Triển khai `com.badlogic.gdx.utils.Disposable`, sử dụng `com.badlogic.gdx.graphics.Mesh` động (thuộc tính đỉnh 6 floats: `aPos`, `aTexCoord`, `aShade`) và ma trận biến đổi mô hình `com.badlogic.gdx.math.Matrix4`, tái sử dụng biến thành viên triệt tiêu hoàn toàn GC churn.
- **Overlay 3D quanh Block mục tiêu:**
  - Phóng to khối bọc `expand = 0.004f` chống Z-Fighting, bật `GL_BLEND`, tắt `GL_CULL_FACE`[cite: 1].
  - Khi chỉ ngắm: phủ lớp sáng trắng mờ `alpha = 0.09f`[cite: 1].
  - Khi đang đập (`miningProgress > 0.001f`): phủ lớp tối dần `(0.08, 0.08, 0.08)` với `alpha = min(0.68f, 0.18f + progress * 0.50f)` kèm lõi vàng `(1.0, 0.92, 0.25, 0.35)` nở dần từ tâm theo tiến trình[cite: 1].
- **Giao diện 2D Orthographic (`ortho(0, w, h, 0, -1, 1)`):**
  - Tắt Depth Test & Cull Face, bật `GL_BLEND (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`, vẽ xong tự khôi phục về chế độ 3D[cite: 1].
  - **Crosshair (khi không Pause):** Chữ thập trung tâm có viền bóng đen (`20x4`, `4x20`, `alpha = 0.65`) và lõi trắng (`16x2`, `2x16`, `alpha = 0.92`)[cite: 1].
  - **Thanh tiến trình đập Block (khi đang đập):** Kích thước `110 x 10` nằm dưới tâm ngắm `20px`, viền tối `2px`, thanh màu chuyển từ vàng cam sang xanh theo phần trăm tiến trình[cite: 1].
  - **Thanh 9 ô Hotbar (luôn hiển thị):** Căn giữa cạnh dưới (cách đáy `16px`), mỗi ô `50 x 50` px, đệm `4px`; ô đang chọn có viền vàng `3px` và nền sáng hơn; góc trên-trái hiển thị số phím `1..9`; chính giữa vẽ icon texture `28 x 28` lấy từ `Block.Face.SOUTH`; góc dưới-phải hiển thị số lượng có bóng đổ; phía trên thanh Hotbar hiển thị tên Block in hoa màu vàng nhạt[cite: 1].
  - **Pause Menu (khi `isPaused == true`):**
    - Phủ màn tối bán trong suốt toàn màn hình (`alpha = 0.68`)[cite: 1].
    - Hộp thoại trung tâm `420 x 270`, tiêu đề `GAME PAUSED` (màu vàng, có bóng)[cite: 1].
    - Nút `RESUME GAME` (`280 x 46`, màu xanh lá có hiệu ứng hover, bấm để tiếp tục chơi và khóa lại chuột)[cite: 1].
    - Cụm chỉnh âm lượng `MUSIC VOLUME: XX%`: nút `[-]` (`42 x 42`, giảm `10%`), thanh trượt (`176 x 24`, thanh xanh + con trượt vàng `12px`, hỗ trợ click và kéo mượt theo thời gian thực), nút `[+]` (`42 x 42`, tăng `10%`)[cite: 1].
    - Dòng hướng dẫn chân hộp thoại: `PRESS ESC AGAIN TO QUIT`[cite: 1].
  - **Font Bitmap 5x7 tích hợp:** Tự vẽ bằng hình học tam giác OpenGL cho `A–Z`, `0–9`, `:`, `%`, `-`, `+`, không phụ thuộc font hệ điều hành[cite: 1].

---

### 8. Hệ Thống Âm Thanh (`AudioManager`)
- **libGDX Audio Stream trên OpenAL:** Sử dụng `com.badlogic.gdx.audio.Music` để stream trực tiếp file `.mp3`, không cần giải mã PCM thủ công, không tốn CPU và không cấp phát mảng đệm rác[cite: 1].
- **Cơ chế tìm kiếm linh hoạt (`findMp3FileHandleFlexible`):** Tự động phát hiện file nhạc thông qua `Gdx.files.internal`, `Gdx.files.classpath`, đường dẫn cục bộ hoặc quét sâu 4 cấp thư mục dự án tìm file `.mp3` đầu tiên.
- **Phát lặp & Điều chỉnh âm lượng:** Hỗ trợ lặp vô hạn `music.setLooping(true)`, âm lượng mặc định `0.70f` (`70%`), thay đổi âm lượng mượt mà theo thời gian thực `setVolume([0.0f, 1.0f])` khi người chơi bấm nút `[-]`/`[+]` hoặc kéo thanh trượt trên Pause Menu.
- **Giải phóng tài nguyên (`Disposable`):** Triển khai `com.badlogic.gdx.utils.Disposable`, dừng phát và giải phóng triệt để tài nguyên OpenAL qua `dispose()`, đồng thời cung cấp phương thức `cleanup()` tương thích ngược.
