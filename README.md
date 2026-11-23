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
  - **Âm thanh:** `audio/music/` chứa 5 bản Ogg Vorbis từ `import.zip`. `audio/sfx/` chứa 93 mẫu OGG được chọn từ gói hiệu ứng đi kèm, gồm biến thể vật liệu và mẫu dùng cho các tương tác hiện có. Tài nguyên được tìm theo đường dẫn cố định trong classpath.
  - **Texture khối:** `textures/` đóng gói bộ ảnh gốc của dự án và 13 PNG nhập nguyên byte từ `assets/minecraft/textures/block/` trong `1.21.11.zip`. Các tệp mới gồm `sandstone.png`, `sandstone_top.png`, `sandstone_bottom.png`, `spruce_log_top.png`, `short_grass.png`, `tall_grass_bottom.png`, `tall_grass_top.png`, `dandelion.png`, `poppy.png`, `cornflower.png`, `oxeye_daisy.png`, `sugar_cane.png` và `gravel.png`. Game nạp ảnh đã đóng gói và dùng bộ nạp dự phòng khi thiếu tài nguyên.
  - **Tài nguyên môi trường:** `textures/environment/rain.png` và `snow.png` giữ nguyên ảnh 64 × 256 có sẵn. `celestial/sun.png` lấy từ `1.21.11.zip`, tám ảnh `celestial/moon/` lấy từ `Blue-Moon.zip` và `clouds.png` lấy từ `Cloudier Clouds.zip`. `audio/weather/rain.wav` là đoạn lặp nối chồng 0,65 giây từ bản thu `Kao - Minecraft Vanilla Rain Sounds Sample.mp3` đi kèm.
- **Kiểm thử:** Gradle `regressionChecks` chạy bộ kiểm tra CPU về ID block, vòng đời thực thể rơi, cập nhật lân cận, va chạm, raycast, hình học chunk, sắp xếp mesh, sinh địa hình tất định, các mốc ngày–đêm, chuyển thời tiết và mái che. `check` gọi task này. Trình biên dịch dùng `options.release = 17`.

---

### 2. Kiến Trúc Mã Nguồn Hiện Tại (50 File Java)
- **`com.voxelgame` (2 file):** `Main.java` khởi chạy desktop. `VoxelGameApp.java` quản lý Game Loop, sự kiện đầu vào, Pause Menu, đào/đặt block và vòng đời tài nguyên.
- **`com.voxelgame.engine` (18 file):**
  - `Camera.java` quản lý góc nhìn, Dynamic FOV và ma trận. `Raycast.java` duyệt DDA và kiểm tra hộp chọn có thể tái sử dụng kết quả.
  - `Texture.java` dựng atlas 128 × 80 bằng Pixmap. `ShaderProgram.java` quản lý GLSL 330 cho 3D và UI. `Chunk.java` lưu ID và metadata cấp phát theo nhu cầu, dựng mesh đục/khoét alpha và mesh trong suốt.
  - `World.java` quản lý chunk, thực thể, gọi bộ sinh và điều phối kết xuất. `BlockUpdateQueue.java` quản lý FIFO tọa độ lân cận. `TranslucentChunkSort.java` sắp xếp mesh nước/băng theo khoảng cách.
  - `BiomeType.java`, `TerrainNoise.java`, `TerrainGenerator.java` định nghĩa biome, noise tất định và các lượt sinh thế giới.
  - `UIRenderer.java` vẽ khung ngắm, HUD, hiệu ứng xương rồng và Pause Menu. `AudioManager.java` phát nhạc nền theo vòng xáo, chọn hiệu ứng âm thanh và giải phóng tài nguyên OpenAL.
  - `WorldTime.java` theo dõi đồng hồ và quỹ đạo thiên thể. `WeatherManager.java` điều khiển chu kỳ, cường độ cục bộ, âm lượng mưa và các lượt tích tuyết. `SkyRenderer.java` vẽ vòm trời, thiên thể và mây. `WeatherRenderer.java` vẽ hai lô mưa và tuyết. `EnvironmentMesh.java` chia sẻ định dạng đỉnh và bộ đệm VAO/VBO cho các lượt vẽ này.
- **`com.voxelgame.entity` (30 file):**
  - `Entity.java` chứa tọa độ, vận tốc và quét AABB theo trục. `Player.java` xử lý điều khiển, bảo vệ mép, bước tuyết, quán tính trên băng và phản hồi xương rồng.
  - `Hotbar.java` quản lý 9 ô. `ItemEntity.java` quản lý vật phẩm rơi và mesh dùng chung. `FallingBlockEntity.java` quản lý trạng thái cát/sỏi đang rơi.
  - `Block.java` đăng ký 26 singleton vào bảng 256 ID tra theo byte không dấu. `PlantBlock.java` chia sẻ hình học và luật nền thực vật. Các lớp block còn lại được liệt kê trong bảng bên dưới. Bốn hoa dùng chung `FlowerBlock.java`.
- **Kiểm thử CPU (4 file):** `VoxelRegressionChecks.java`, `ChunkMeshRegressionChecks.java`, `TerrainGenerationChecks.java` và `EnvironmentChecks.java` thuộc `src/test/java/com/voxelgame/engine/`.

---

### 3. Hệ Thống Block (26 Loại) & Cơ Chế Đào / Rơi Item

| ID | Lớp | Độ cứng (giây) | Hình học và lớp vẽ | Tile theo mặt | Hành vi |
| --- | --- | ---: | --- | --- | --- |
| 0 | `AirBlock` | −1 | Bỏ qua | 0 | Không khí, thay thế được. |
| 1 | `DirtBlock` | 0,75 | Khối rắn đục | 0 | Rơi Dirt. |
| 2 | `GrassBlock` | 0,75 | Khối rắn đục | Trên 1, đáy 0, cạnh 2 | Rơi Dirt. Khi tuyết phủ, trên dùng 19 và cạnh 11. Khối đục đầy khác phía trên chuyển cỏ thành Dirt. |
| 3 | `StoneBlock` | 5,0 | Khối rắn đục | 3 | Rơi Stone. |
| 4 | `BedrockBlock` | −1 | Khối rắn đục | 4 | Nền Y=0, không phá được. |
| 5 | `OakLogBlock` | 3,0 | Khối rắn đục | Đầu 6, cạnh 5 | Rơi Oak Log. |
| 6 | `OakLeavesBlock` | 0,30 | Khối rắn khoét alpha | 7 | Rơi Oak Leaves. |
| 7 | `WaterBlock` | −1 | Chất lỏng trong suốt | 8 | Cho tia đi xuyên, thay thế được. |
| 8 | `LavaBlock` | −1 | Chất lỏng ở pha đầu | 9 | Cho tia đi xuyên, thay thế được. |
| 9 | `SandBlock` | 0,75 | Khối rắn đục | 10 | Rơi theo trọng lực khi mất nền. |
| 10 | `GravelBlock` | 0,90 | Khối rắn đục | 32 | Rơi theo trọng lực khi mất nền. |
| 11 | `SandstoneBlock` | 4,0 | Khối rắn đục | Trên 21, đáy 22, cạnh 20 | Địa tầng sa mạc. |
| 12 | `CactusBlock` | 0,60 | Khối rắn thu hẹp, pha đầu | Trên 14, đáy 15, cạnh 13 | Nền Sand/Cactus, gãy và rơi item khi mất nền hoặc khối cản/Lava ở ô ngang. |
| 13 | `DeadBushBlock` | 0,05 | Chữ X khoét alpha | 12 | Cần Sand hoặc Dirt. |
| 14 | `ShortGrassBlock` | 0,05 | Chữ X khoét alpha | 24 | Cần Grass Block hoặc Dirt, có thể ghi đè. |
| 15 | `TallGrassBlock` | 0,05 | Chữ X hai ô khoét alpha | Dưới 25, trên 26 | Cần Grass Block hoặc Dirt, dọn hai nửa khi thiếu một nửa hoặc mất nền. |
| 16–19 | `FlowerBlock` | 0,05 | Chữ X khoét alpha | 27–30 | Dandelion, Poppy, Cornflower, Oxeye Daisy. Cần Grass Block hoặc Dirt và rơi đúng hoa khi gãy. |
| 20 | `SugarCaneBlock` | 0,05 | Chữ X khoét alpha | 31 | Cần mía hoặc Grass Block/Dirt/Sand có nước cạnh nền, cả cột gãy khi mất điều kiện. |
| 21 | `SnowLayerBlock` | 0,25 | Lớp thấp đến đầy, pha đầu | 19 | Tám lớp, một lớp đi xuyên và ghi đè được. Cần khối rắn đầy, kể cả lá vân sam. |
| 22 | `SnowBlock` | 0,35 | Khối rắn đục | 19 | Rơi Snow Block. |
| 23 | `IceBlock` | 0,75 | Khối rắn trong suốt | 18 | Trơn. Đào tay không thành Water nếu dưới có nền rắn hoặc Water, thành Air nếu thiếu nền. |
| 24 | `SpruceLogBlock` | 3,0 | Khối rắn đục | Đầu 23, cạnh 16 | Rơi Spruce Log. |
| 25 | `SpruceLeavesBlock` | 0,30 | Khối rắn khoét alpha | 17 | Có thể đỡ tuyết. |

- Metadata dùng 0–7 cho một đến tám lớp tuyết, 0/1 cho nửa dưới/trên của Tall Grass. ID cũ 0–8 giữ nguyên. Ô mặc định không cần mảng metadata riêng. Chiều cao vẽ tuyết là số lớp × 0,125. Va chạm của lớp 2–7 thấp hơn hình vẽ một lớp. Tám lớp va chạm cao 1 block.
- Khi sửa block, hàng đợi xử lý ô vừa đổi, sáu ô chung mặt và các phụ thuộc ngoài nhóm này. Mía kiểm tra nước ở cao độ nền. Snow Layer tan thành Air và Ice tan thành Water khi có Lava trong bán kính Manhattan 2. Cát/sỏi tạo thực thể đúng một lần khi mất nền, rồi đặt lại hoặc rơi item tùy mặt đáp.
- Tia chọn được mọi block trừ Air, Water và Lava. Chỉ block có hộp va chạm thực mới cản chuyển động. Chữ X của cây vẽ hai phía và không có hộp vật lý.

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
  - Tích lũy `miningElapsedTime` cho đến khi `>= hardness` sẽ đổi ô thành `Block.AIR` hoặc Water khi đào Ice có nền phù hợp, sau đó gọi luật rơi vật phẩm tại tâm block `(x + 0.5, y + 0.5, z + 0.5)`[cite: 1].
  - Nhả chuột, nhìn sang ô khác, hoặc ngắm vào khối không phá được sẽ reset tiến trình về `0`[cite: 1].
- **Đặt Block (Click Chuột Phải):**
  - Ưu tiên tăng lớp tuyết đang ngắm, đặt đè ô có thể thay thế, rồi thử ô kề mặt trúng. Kiểm tra nền, biên, ô phụ và giao cắt hộp người chơi. Chỉ trừ 1 item sau khi đặt thành công.
- **Tạm dừng & Thoát (`ESC`):**
  - Nhấn `ESC` lần 1: bật `isPaused`, reset tiến trình đào, mở con trỏ chuột (`GLFW_CURSOR_NORMAL`), đóng băng cập nhật thực thể[cite: 1].
  - Nhấn `ESC` lần 2 khi đang Pause: đặt cờ đóng cửa sổ và thoát hoàn toàn khỏi game[cite: 1].
- **Kiểm thử môi trường khi đang chơi:** `F6` đảo quang đãng và giáng thủy, `F7` tới bình minh ở lần bấm đầu rồi lần lượt tới trưa, hoàng hôn, nửa đêm và bình minh hôm sau. `F8` bật/tắt tua đồng hồ 20×.

---

### 5. Vật Lý & Thuật Toán Sinh Thế Giới
- **Vật lý Người chơi (`Player`):**
  - Spawn tại tâm ngang `(x = 1500, z = 896)`. Độ cao chân bằng đỉnh cao nhất trong bốn cột block giáp tâm. Nếu rơi xuống dưới `y < -25.0f`, tự động đưa về `(1500, 22, 896)`, reset vận tốc và tắt bay.
  - Hộp va chạm AABB: `PLAYER_HALF_WIDTH = 0.3f` (rộng `0.6f`), `PLAYER_HEIGHT = 1.8f`[cite: 1].
  - Thông số vận tốc: `MOVE_SPEED = 5.5f`, `GRAVITY = -26.0f`, `TERMINAL_VELOCITY = -45.0f`[cite: 1].
  - Nhận diện chất lỏng (`isInLiquid`): kiểm tra block tại chân (`y + 0.1f`) và thắt lưng (`y + 0.9f`)[cite: 1]. Trong chất lỏng (không bay): tốc độ ngang `0.65x`, trọng lực `0.25x`, vận tốc chìm tối đa `-3.5f`[cite: 1].
  - Quét AABB theo thứ tự X → Z → Y trên hộp thực. Bước lên bậc tuyết thấp tối đa 0,5 block khi đủ chỗ. Ice giữ đà ngang với hệ số 0,98. Chạm xương rồng đẩy nhẹ và nháy đỏ 0,25 s, hồi chiêu 0,5 s.
- **Raycast 3D DDA (`Raycast`):**
  - DDA duyệt voxel dọc tia, giao cắt hộp chọn trong đoạn đi qua mỗi ô. Tia xuyên Air, Water, Lava và phần ngoài hộp chọn của xương rồng, tuyết hoặc cây.
- **Vật lý Vật phẩm Rơi (`ItemEntity`):**
  - Kích thước `ITEM_SCALE = 0.28f` (`HALF_SIZE = 0.14f`)[cite: 1].
  - Vận tốc văng ban đầu ngẫu nhiên: `vx, vz ∈ [-0.7, 0.7]`, `vy = 3.2f`[cite: 1].
  - Trọng lực `ITEM_GRAVITY = -18.0f` (tối đa `-25.0f`), ma sát ngang `0.82f` (trên đất) / `0.96f` (trên không), va chạm 3 trục với khối rắn[cite: 1].
  - Sau `PICKUP_DELAY = 0.25s`: kích hoạt lực hút nam châm trong bán kính `MAGNET_DISTANCE = 1.85f` (kéo về tâm cơ thể người chơi với tốc độ `9.5f`), tự động nhặt vào `Hotbar` khi khoảng cách `<= PICKUP_DISTANCE (0.85f)`[cite: 1].
  - Tự biến mất sau `MAX_LIFETIME = 300.0s`[cite: 1].
  - Hoạt ảnh 3D: nhấp nhô `sin(age * 3.8f) * 0.07f + 0.08f` và tự xoay quanh trục Y theo góc `age * 110.0f` độ[cite: 1].
- **Thế giới & Sinh Địa hình (`World`, seed `20261114L`):**
  - Bản đồ có **`3000 × 256 × 1792` block**, dùng lưới `188 × 16 × 112` chunk (`Chunk.SIZE = 16`). Tọa độ hợp lệ là `x = 0..2999`, `y = 0..255`, `z = 0..1791`. Chunk cuối theo X chỉ chứa tám cột hợp lệ. Ô lưới chỉ chứa không khí dùng `null`. Đặt block đầu tiên tạo chunk và xóa block cuối cùng giải phóng chunk cùng mesh của nó.
  - Giới hạn chiều cao địa hình tự nhiên: `MAX_TERRAIN_LAYERS = 20` (`MAX_TERRAIN_Y = 19`)[cite: 1].
  - Từ tây sang đông là Snowy Plains, Plains và Desert. Mỗi vùng khí hậu rộng 1.000 block theo X và trải dài 1.792 block theo Z. Quanh các mốc X=1000 và X=2000 có dải chuyển tiếp 64 block, dùng tỷ lệ nội suy và noise theo seed để xen mảng cỏ với tuyết hoặc cát. Địa tầng, lớp phủ, cây và thực vật cùng dùng kiểu bề mặt đã trộn. Hồ lạnh và ốc đảo nằm gần giữa biome tương ứng.
  - `TerrainNoise` dùng value noise 2D ba tầng và hash theo seed/tọa độ. Nhiệt độ dựa trên khoảng cách đến ranh giới và phân loại ở ngưỡng ±0,31. Độ cao trộn qua vùng đệm quanh ngưỡng. Nền địa chất có đỉnh Y=9–19. Nền ngay spawn ở Plains là cỏ phẳng ở Y=12, chân người chơi bắt đầu Y=13.
  - **Plains:** Bedrock → Stone → 2–3 Dirt → Grass Block. Có sồi thưa, cỏ, hoa và mía, hồ nước có đáy trộn Dirt/Sand/Gravel.
  - **Desert:** Bedrock → Stone → 3–4 Sandstone → 3–4 Sand. Có cồn cát, cactus cao 1–3 block, bụi khô, ốc đảo và hồ Lava.
  - **Snowy Plains:** Địa tầng tương tự Plains, phủ Snow Layer thường 1 lớp, có vùng 2–3 lớp và Snow Block. Hồ có mặt Ice và nước bên dưới, cây vân sam có tán hình nón và tuyết trên lá thoáng. Băng và tuyết tránh vùng gần dung nham khi sinh.
  - Hồ dung nham chọn vật liệu theo kiểu bề mặt ở tâm hồ. Desert có bờ Sand và lót Sandstone. Plains và Snowy Plains có bờ Grass Block và lót Dirt. Quy tắc tránh băng và tuyết gần dung nham áp dụng cả trên bờ.
  - Các lượt sinh là địa tầng, hồ, băng/tuyết, cây và thực vật. Hai bộ đệm cột kiểu byte phục vụ các lượt sinh và được bỏ sau khi khởi tạo.

---

### 6. Đồ Họa, Shader, Texture & Hiệu Năng 60 FPS
- **OpenGL & Vòng lặp:** OpenGL 3.3 Core Profile trong backend LWJGL 3 của libGDX, bật `GL_DEPTH_TEST`, `GL_CULL_FACE` (`GL_BACK`, `GL_CCW`), bật V-Sync `config.useVsync(true)` với mục tiêu 60 FPS, giới hạn `deltaTime <= 0.05f`.
- **Màu trời & Ống kính:**
  - Vòm trời xoay theo camera, dùng xanh sáng vào ban ngày, xanh đen vào ban đêm và ánh cam theo hướng mọc/lặn. Mưa và tuyết chuyển bầu trời sang xám xanh.
  - Camera: `BASE_FOV_DEG = 70.0f` (`+8.0f` khi Sprint, `+5.0f` khi Fly, nội suy `deltaTime * 10.0f`), `NEAR_PLANE = 0.08f`, `FAR_PLANE = 1024.0f`, ma trận tính qua `Matrix4.setToProjection` và `setToLookAt`.
- **Định dạng Đỉnh & Shader (`ShaderProgram`):**
  - 6 floats/đỉnh: `[x, y, z, u, v, shade]` (`location 0: vec3 aPos`, `location 1: vec2 aTexCoord`, `location 2: float aShade`) quản lý qua `VertexAttributes` của libGDX.
  - Fragment Shader chọn màu từ `uTexture * uTintColor` hoặc `uTintColor`. Hình học thế giới và UI loại pixel có `alpha < 0.05`. Màu khối và thực thể nhân với độ lớn `vShade` và `uWorldLight`. Dấu âm của `vShade` đánh dấu dung nham tự phát sáng. `enable2DUIMode` đặt `uWorldLight = (1, 1, 1)`. `uEnvironmentMode` chọn xử lý vòm trời, mây, thiên thể hoặc giáng thủy.
  - Xóa tiền tố `prependVertexCode`/`prependFragmentCode` để giữ `#version 330 core` ở dòng đầu tiên.
- **Ánh sáng & Face Culling (`Chunk`):**
  - Directional Face Shading: `SHADE_TOP = 1.00f`, `SHADE_BOTTOM = 0.55f`, `SHADE_NORTH_SOUTH = 0.85f`, `SHADE_EAST_WEST = 0.72f`.
  - Khối đục che phần mặt khối kề được phủ kín. Lá khoét alpha cho thấy bề mặt phía sau khe. Xương rồng có bốn mặt ngang lùi 1/16. Mặt bên tuyết chỉ giữ phần vượt chiều cao lớp kề. Cây hình chữ X vẽ cả hai phía. Nước/băng bỏ mặt trong cùng loại và giữ phần lộ ra cạnh loại không đục khác. Mặt thoáng Water/Lava cao 0,88 nếu phía trên là Air.
  - Sửa ở biên đánh dấu mesh kề bị ảnh hưởng. Đổi độ cao bề mặt chất lỏng tại giao biên ngang và đứng còn làm mới chunk bên cạnh mặt chất lỏng.
  - Khối xương rồng, vật phẩm rơi và biểu tượng Hotbar dùng chung vùng UV đã bỏ viền trong suốt. Mặt bên lấy vùng 14 × 16 pixel, mặt trên và dưới lấy vùng 14 × 14 pixel từ PNG gốc.
- **Hai pha vẽ:** Chunk đục/khoét alpha và thực thể ghi depth trước. Nước/băng giữ depth test, tắt depth write, dùng blend `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` rồi khôi phục trạng thái. Heapsort tại chỗ sắp xa đến gần theo bình phương khoảng cách mắt–tâm chunk và phá hòa theo tọa độ. Mảng chunk, khoảng cách và khóa được cấp phát trước. Thứ tự giữa các mặt trong một chunk là xấp xỉ.
  - Vòm trời và thiên thể vẽ trước các chunk. Mây ở Y=140 vẽ sau địa hình đục và trước nước/băng. Mưa và tuyết vẽ sau thế giới với depth test và alpha blend. Các lượt môi trường tắt ghi độ sâu, khôi phục trạng thái sau khi vẽ và dùng lại mảng đỉnh.
- **Frustum culling và bộ nhớ đệm:** Dựng trước 5 × 5 cột quanh spawn, mỗi frame cập nhật tối đa 16 chunk giao khung nhìn, ưu tiên gần camera. Thu hồi cả hai mesh sau 120 frame ngoài khung nhìn và giữ dữ liệu voxel. Hai bộ đệm đỉnh và bảng UV dùng lại.
- **Texture Atlas:** Lưới 8 × 5 ô 16 × 16, kích thước 128 × 80 RGBA. Dùng Nearest, ClampToEdge và biên UV 0,0008. Các tile 0–19 giữ chỉ số cũ; tile 20–32 tương ứng thứ tự PNG mới ở mục 1. Xử lý một lần khi dựng atlas: nhuộm tông Plains cho cỏ/lá sồi/mía, tông xanh thông cho lá vân sam, giữ alpha nguồn của texture khoét và độ trong của Ice/Water. Nước và dung nham lấy khung đầu. Dùng pixel art dự phòng khi nguồn thiếu hoặc lỗi đọc và giải phóng các Pixmap sau dựng atlas.

- **Tối ưu Hiệu năng & Quản lý Bộ nhớ:**
  - Mesh chunk dùng VAO qua OpenGL 3.3. Với tối đa 16.384 mặt, mesh nén tại chỗ còn bốn đỉnh mỗi mặt và dùng chung mẫu chỉ số 16 bit cho hai tam giác. Mesh lớn hơn tải đủ sáu đỉnh mỗi mặt.
  - Tái sử dụng các `Matrix4` làm trường thành viên để tránh sinh rác GC trong Game Loop.
  - Dùng chung `Mesh` theo loại `Block` cho mọi `ItemEntity` (`SHARED_MESHES`).
  - Giải phóng triệt để tài nguyên GPU qua `Disposable.dispose()` cho mọi lớp `Chunk`, `World`, `Texture`, `ShaderProgram`, `ItemEntity`.

---

### 7. Giao Diện Người Dùng (UI/HUD) & Font 5x7 (`UIRenderer`)
- **Kết xuất qua libGDX:** Triển khai `com.badlogic.gdx.utils.Disposable`, sử dụng `com.badlogic.gdx.graphics.Mesh` động (thuộc tính đỉnh 6 floats: `aPos`, `aTexCoord`, `aShade`) và ma trận biến đổi mô hình `com.badlogic.gdx.math.Matrix4`, tái sử dụng mảng đỉnh, bitmap font, nhãn số, hộp kiểm tra chuột và ma trận. Hiệu ứng xương rồng vẽ viền đỏ trước Hotbar.
- **Overlay 3D quanh Block mục tiêu:**
  - Dùng hộp chọn thực, phóng rộng `expand = 0.004f` chống Z-Fighting, bật `GL_BLEND`, tắt `GL_CULL_FACE`[cite: 1].
  - Khi chỉ ngắm: phủ lớp sáng trắng mờ `alpha = 0.09f`[cite: 1].
  - Khi đang đập (`miningProgress > 0.001f`): phủ lớp tối dần `(0.08, 0.08, 0.08)` với `alpha = min(0.68f, 0.18f + progress * 0.50f)` kèm lõi vàng `(1.0, 0.92, 0.25, 0.35)` nở dần từ tâm theo tiến trình[cite: 1].
- **Giao diện 2D Orthographic (`ortho(0, w, h, 0, -1, 1)`):**
  - Tắt Depth Test & Cull Face, bật `GL_BLEND (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`, vẽ xong tự khôi phục về chế độ 3D[cite: 1].
  - **Crosshair (khi không Pause):** Chữ thập trung tâm có viền bóng đen (`20x4`, `4x20`, `alpha = 0.65`) và lõi trắng (`16x2`, `2x16`, `alpha = 0.92`)[cite: 1].
  - **Thanh tiến trình đập Block (khi đang đập):** Kích thước `110 x 10` nằm dưới tâm ngắm `20px`, viền tối `2px`, thanh màu chuyển từ vàng cam sang xanh theo phần trăm tiến trình[cite: 1].
  - **Thanh 9 ô Hotbar (luôn hiển thị):** Căn giữa cạnh dưới (cách đáy `16px`), mỗi ô `50 x 50` px, đệm `4px`; ô đang chọn có viền vàng `3px` và nền sáng hơn; góc trên-trái hiển thị số phím `1..9`; chính giữa vẽ icon texture `28 x 28` lấy từ `Block.Face.SOUTH`; góc dưới-phải hiển thị số lượng có bóng đổ; phía trên thanh Hotbar hiển thị tên Block in hoa màu vàng nhạt[cite: 1].
  - **Thông tin môi trường:** Khi đang chơi, góc trên trái hiện tên biome, `CLEAR`/`RAIN`/`SNOW` và hai chỉ số ngày, giờ theo dạng `DAY 1 - 07:00`. Chữ dùng font 5 × 7 và bộ đệm ký tự dùng lại.
  - **Pause Menu (khi `isPaused == true`):**
    - Phủ màn tối bán trong suốt toàn màn hình (`alpha = 0.68`)[cite: 1].
    - Hộp thoại trung tâm `420 x 270`, tiêu đề `GAME PAUSED` (màu vàng, có bóng)[cite: 1].
    - Nút `RESUME GAME` (`280 x 46`, màu xanh lá có hiệu ứng hover, bấm để tiếp tục chơi và khóa lại chuột)[cite: 1].
    - Cụm chỉnh âm lượng `VOLUME: XX%` cho nhạc và mưa gồm nút `[-]` (`42 x 42`, giảm `10%`), thanh trượt (`176 x 24`, thanh xanh và con trượt vàng `12px`, hỗ trợ click và kéo), nút `[+]` (`42 x 42`, tăng `10%`)[cite: 1].
    - Dòng hướng dẫn chân hộp thoại: `PRESS ESC AGAIN TO QUIT`[cite: 1].
  - **Font Bitmap 5x7 tích hợp:** Tự vẽ bằng hình học tam giác OpenGL cho `A–Z`, `0–9`, `:`, `%`, `-`, `+`, không phụ thuộc font hệ điều hành[cite: 1].

---

### 8. Hệ Thống Âm Thanh (`AudioManager`)
- **Vòng nhạc nền:** libGDX `Music` phát luồng Ogg Vorbis trên OpenAL. Mỗi vòng xáo Fisher–Yates và phát mỗi bài `calm1`, `calm3`, `hal3`, `piano2`, `piano3` đúng một lần. Bài đầu vòng mới khác bài cuối vòng trước. Sau mỗi bài nghỉ 3 giây, kể cả giữa hai vòng. Game bỏ qua tài nguyên thiếu hoặc không mở được.
- **Pause Menu và âm lượng:** Nhạc và khoảng nghỉ tiếp tục khi mở Pause Menu. Nút `[-]`, `[+]` và thanh trượt điều khiển ngay âm lượng nhạc và mưa từ 0% đến 100%, mặc định 70%. Các nút phát mẫu click khi được bấm.
- **Tiếng mưa:** `Music` phát lặp luồng WAV độc lập với nhạc nền. Âm lượng bằng mức trong Pause Menu nhân cường độ mưa cục bộ nhân hệ số mái che. Cường độ âm thanh trở về 0 khi rời Plains hoặc mưa tạnh. Mái che bằng khối rắn hay lá giảm âm lượng về 40%. Luồng mưa dừng tại vị trí đang phát khi mở Pause Menu và tiếp tục từ vị trí đó khi bấm Resume.
- **Hiệu ứng vật liệu:** Các khối dùng nhóm mẫu cỏ/thực vật, sỏi/đất, cát, tuyết, đá, gỗ hoặc tiếng kính khi băng vỡ. Đào lâu phát tiếng gõ theo nhịp 0,25 giây. Phá và đặt thành công, bước chân, tiếp đất và cát/sỏi đáp đều chọn mẫu theo vật liệu. Bước chân dùng quãng đường đã đi và giảm âm lượng khi rón rén. Tiếng phá và cát/sỏi đáp giảm theo khoảng cách trong bán kính 16 block.
- **Hiệu ứng tương tác:** Nhặt đồ phát tiếng khi Hotbar nhận vật phẩm. Va xương rồng phát theo hồi chiêu của phản hồi va chạm. Có tiếng chạm nước, bơi, chìm/nổi qua mặt nước và âm nền khi mắt ở dưới nước. Âm nền dưới nước dừng trong Pause Menu. Nước và dung nham gần người chơi có một mẫu âm định kỳ, giảm theo khoảng cách.
- **Vòng đời tài nguyên:** Mẫu ngắn được nạp một lần vào `Sound` và dùng lại khi phát. Âm nền dưới nước, tiếng mưa và bài nhạc hiện tại dùng `Music`. Bộ đếm, thứ tự bài và mẫu đã nạp được tái sử dụng trong vòng lặp. `dispose()` giải phóng các luồng và bộ đệm âm thanh. `cleanup()` ủy quyền cho `dispose()`.

---

### 9. Chu Kỳ Ngày–Đêm & Thời Tiết

- **Đồng hồ:** Một ngày có 24.000 tick, chạy 20 tick/giây và bắt đầu ở tick 1.000 (07:00). Bình minh, trưa, hoàng hôn và nửa đêm lần lượt ở tick 0, 6.000, 12.000 và 18.000. Trời tối hẳn quanh tick 13.000, sáng lại từ vùng tick 23.000–24.000. Pause đóng băng đồng hồ, mây và thời tiết.
- **Thiên thể và ánh sáng:** Mặt trời mọc ở Đông (`+X`), qua đỉnh `+Y` rồi lặn ở Tây (`-X`). Mặt trăng đối diện 180° và chọn một trong tám pha theo bộ đếm ngày. Hai ảnh hòa trộn cộng sáng và mờ dần khi giáng thủy tăng. Ánh sáng môi trường chuyển từ trắng ban ngày qua ấm nhẹ lúc mặt trời gần chân trời đến xanh đêm. Mưa/tuyết làm giảm tối đa 24% cường độ ánh sáng ban ngày. Mây trôi từ Đông sang Tây và hiện cả ban ngày lẫn ban đêm, với màu thay đổi theo giờ và cường độ giáng thủy.
- **Chu kỳ giáng thủy:** Quang đãng tự động kéo dài 3–7 phút, đợt mưa/tuyết kéo dài 1–3 phút. Cường độ chuyển dần trong 5 giây. Mỗi cột trong bán kính 14 block dùng biome khí hậu để quyết định mưa ở Plains, tuyết ở Snowy Plains hoặc không giáng thủy ở Desert. Khi đạt cường độ tối đa, lấy mẫu khoảng 35% cột mưa và 29% cột tuyết để giảm số dải bán trong suốt chồng lên nhau. Hạt mờ dần khi sát mắt hoặc gần rìa vùng vẽ. Đáy mỗi dải dừng trên bề mặt cao nhất của khối rắn, lá, tuyết hoặc chất lỏng. Mưa cuộn nhanh theo chiều dọc, tuyết rơi chậm và lắc ngang.
- **Tích tuyết:** Khi cường độ vượt 50%, mỗi 0,35 giây thử một cột quanh người chơi. Cột Snowy Plains lộ thiên có thể nhận một lớp tuyết trên nền hợp lệ, đôi khi tăng lớp hoặc đóng băng Water mặt trên. Mái che, hộp người chơi và Lava gần đó được kiểm tra trước khi thay block.
