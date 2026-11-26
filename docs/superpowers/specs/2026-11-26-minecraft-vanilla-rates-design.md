# Bản Thiết Kế: Chuẩn Hóa 100% Tỉ Lệ Theo Minecraft 1.21.11 & Mở Rộng Thế Giới 5 Biome

## 1. Mục đích & Lý do
- Đưa **toàn bộ các tỉ lệ gameplay** trong VoxelGame (sinh thế giới, thời tiết, tích tuyết, sinh/hủy mob, biến thể non, hành vi AI, rơi vật phẩm) về **đúng 100% chuẩn Minecraft Java Edition 1.21.11**.
- Giải quyết mâu thuẫn giữa **"đúng tỉ lệ chuẩn"** (vốn có nhiều đặc trưng rất thưa như Plains `0,05` cây/chunk, hồ Lava `1/200` chunk) và **"thế giới giàu đa dạng"** bằng cách **mở rộng bản đồ** và **tách thêm biome rừng chuẩn Minecraft** thay vì ép sai tỉ lệ trong 3 biome cũ.

## 2. Mục tiêu, Phi mục tiêu & Điều cần tránh
- **Mục tiêu:**
  - Mọi tỉ lệ trong 5 nhóm (Địa hình/thực vật, Thời tiết/tuyết, Sinh mob/trọng số/baby, Hành vi AI, Loot) khớp 100% thông số Minecraft Java 1.21.11.
  - Thế giới đa dạng rõ rệt nhờ **5 biome** (`Snowy Plains`, `Forest`, `Plains`, `Flower Forest`, `Desert`) với kích thước mở rộng **`4000 × 256 × 2048` block** (`32.000` cột chunk).
  - Mọi tính năng hiện có của `README.md` (27 block, 24 cấu hình mob, chiến đấu, thú cưỡi, âm thanh, HUD, phím tắt `F1/F6/F7/F8/Ctrl+C`) đều được bảo toàn.
- **Phi mục tiêu:**
  - Không đổi sang thế giới vô hạn (Dynamic Chunk Streaming).
  - Không thêm block, texture hay loài mob mới ngoài bộ tài nguyên đang có.
  - Không thêm cấu trúc Village hay hệ thống sấm sét (`thunder`).
- **Điều cần tránh (Anti-goals):**
  - Không làm tụt FPS dưới 60 FPS hoặc phát sinh GC churn trong Game Loop.
  - Không để thời gian khởi tạo thế giới quá lâu hay ngốn RAM vượt mức an toàn (~420 MB cho 32.000 chunk, tải ~2–2,5 giây).
  - Không để một loài mob sẵn có (như `Camel`) biến mất khỏi thế giới chơi.

## 3. Ràng buộc kỹ thuật
- Java 17 (UTF-8), libGDX `1.13.1`, giữ kiến trúc `Chunk[X][Y][Z]` cấp phát thưa theo nhu cầu.
- Mảng `byte[] biomes` trong `TerrainGenerator` dùng **3 bit thấp (`0..7`)** cho mã biome và dịch 3 cờ sang `NEAR_LAVA = 8`, `RIVER = 16`, `LAKE = 32` → mở rộng lên 5 biome với **0 byte RAM tăng thêm**.
- Bộ kiểm thử CPU (`./gradlew check` / `regressionChecks`) và Phần A của `README.md` phải đồng bộ 100%.

## 4. Các phương án đã cân nhắc
1. **Phương án 1 (Đã chọn):** Mở rộng bản đồ lên `4000 × 2048` + tách thêm biome chuẩn Minecraft (`Forest`, `Flower Forest`) + chuẩn hóa 100% tỉ lệ toàn dự án. *Đánh đổi:* Phải mở rộng `BiomeType`, cập nhật `TerrainGenerator`, `Ctrl+C`, HUD, test — nhưng là cách duy nhất vừa giữ 100% tỉ lệ Minecraft vừa tạo thế giới phong phú mà vẫn gọn nhẹ.
2. **Phương án 2 (Loại):** Giữ nguyên 3 biome và chỉ mở rộng bản đồ + sửa tỉ lệ. *Lý do loại:* Vùng `Plains` sẽ gần như trọc hoàn toàn (1 cây/20 chunk), mất sự đa dạng rừng sồi và thảm hoa.
3. **Phương án 3 (Loại):** Chuyển sang Chunk Streaming vô hạn + lưới biome 2D. *Lý do loại:* Phải viết lại gần như toàn bộ lõi `World.java` và vượt xa phạm vi chuẩn hóa tỉ lệ.

## 5. Thiết Kế Chi Tiết

### 5.1. Bản đồ mở rộng & 5 Biome (Bố cục 5 dải theo trục X)
- **Kích thước mới:** `BIOME_WIDTH = 800`, `WORLD_SIZE_X = 4000` (`250` chunk), `WORLD_SIZE_Z = 2048` (`128` chunk), `WORLD_SIZE_Y = 256` (`16` chunk). Mỗi biome rộng `800 × 2048` (`6.400` chunk).
- **Thứ tự 5 dải từ Tây sang Đông** (biên uốn lượn 2 tầng Perlin):
  1. `SNOWY_PLAINS` (`X ≈ 0..799`): núi tuyết cao ≥ Y=140, rừng thông Grove, đồng bằng tuyết, sông đóng băng. Tuyết rơi.
  2. `FOREST` (`X ≈ 800..1599`): rừng sồi rậm. Mưa.
  3. `PLAINS` (`X ≈ 1600..2399`): đồng bằng thoáng, **Spawn tại tâm `(2000, 1024)`**, **Respawn tại `(2000, 1600)`**. Mưa.
  4. `FLOWER_FOREST` (`X ≈ 2400..3199`): rừng hoa rực rỡ. Mưa.
  5. `DESERT` (`X ≈ 3200..3999`): cồn cát, kim tự tháp sa mạc, sông ấm có mía dày. Luôn khô.
- **Điều hướng & HUD:** `Ctrl+C` đi vòng đủ 5 biome; HUD góc trên trái hiển thị đúng tên 5 biome; mọi kiểm tra mưa (`WeatherManager`, `WeatherRenderer`, `World.isRainingAt`) đổi từ `== PLAINS` sang `biome.hasRain()` (`PLAINS`, `FOREST`, `FLOWER_FOREST`).

### 5.2. Tỉ lệ Sinh Thế Giới chuẩn Minecraft 1.21.11 (`TerrainGenerator`)
- **Cây (sinh theo từng chunk `16×16` giống Minecraft):**
  - `PLAINS`: **`0,05` cây/chunk** (`5%` thêm 1 cây/chunk); `33%` Fancy/Old Oak, `67%` Oak thường; bỏ Oak Bush.
  - `FOREST`: **`10` lần thử/chunk**; `90%` Oak thường, `10%` Fancy/Old Oak.
  - `FLOWER_FOREST`: **`6` lần thử/chunk**; `90%` Oak thường, `10%` Fancy/Old Oak.
  - `SNOWY_PLAINS`: đồng bằng `Flat` **`0,1` cây/chunk** (Spruce); vùng rừng `Grove` (X/Z theo trường noise ở `Y = 30..61`) **`10` lần thử/chunk** (`2/3` Spruce, `1/3` Pine); `Slopes/Peaks` (`Y ≥ 62`) không mọc cây.
  - `DESERT`: không mọc cây.
- **Hoa (`Dandelion`, `Poppy`, `Cornflower`, `Oxeye Daisy`):**
  - `PLAINS`: `flower_plains` trung bình `~3–5` bông/chunk; trọng số loài chuẩn **`Dandelion 2 : Poppy 1 : Cornflower 1 : Oxeye Daisy 1`**.
  - `FOREST`: `flower_default` thưa (`~1–2` bông/chunk); chỉ **`Dandelion 2 : Poppy 1`**.
  - `FLOWER_FOREST`: `flower_forest` dày đặc theo gradient noise 2D (`~15–25` bông/chunk), đủ 4 loài chuyển dải mượt.
- **Cỏ ngắn & Cỏ cao:**
  - `PLAINS`: cỏ ngắn phủ dày (`~25–30%` mặt cỏ trống); cỏ cao rarity **`1/5` chunk** (tỉ lệ Tall:Short ≈ `1 : 12`).
  - `FOREST` & `FLOWER_FOREST`: cỏ ngắn thưa hơn (`2` patch/chunk, `~6–10%` mặt cỏ).
- **Sa mạc (`Cactus`, `Dead Bush`, `Sugar Cane`):**
  - `Cactus`: rarity **`1/5` chunk** (`~0,15–0,2` cây/chunk); chiều cao `biased_to_bottom`: **`56%` cao 1, `28%` cao 2, `16%` cao 3**.
  - `Dead Bush`: **`2` patch/chunk** (`~1–2` bụi/chunk).
  - `Sugar Cane`: rarity **`1/6` chunk** ở biome thường, dày **gấp ~6 lần** ở `DESERT`; chiều cao `biased_to_bottom`: **`50%` cao 2, `33%` cao 3, `17%` cao 4**.
- **Hồ & Tuyết địa hình:**
  - **Hồ nước nhỏ trên mặt đất:** Bỏ hoàn toàn theo chuẩn Minecraft 1.18+ (chỉ còn sông chảy xuyên qua 5 biome, đóng băng ở `SNOWY_PLAINS`).
  - **Hồ dung nham mặt đất (`lake_lava_surface`):** Đúng rarity **`1/200` chunk** (`0,5%` mỗi chunk).
  - **Tuyết phủ sẵn (`freeze_top_layer`):** **100%** cột lộ trời ở `SNOWY_PLAINS` (gồm **100%** mặt trên tán lá vân sam lộ trời) phủ đúng **1 lớp `Snow Layer`** (đỉnh trên Y=110 vẫn giữ `Snow Block`, mặt sông đóng `Ice`).

### 5.3. Thời tiết & Tích tuyết (`WeatherManager`, `World`)
- **Chu kỳ tự động:** Trời quang **`10–150 phút`** (`600–9.000s` = `12.000–180.000` tick); Mưa/Tuyết **`10–20 phút`** (`600–1.200s` = `12.000–24.000` tick). Phím `F6` vẫn đảo thời tiết ngay lập tức.
- **Tích tuyết & đóng băng khi giáng thủy (`strength > 0,5`):** Chạy cùng nhịp tick `0,05s` trên các chunk trong bán kính mô phỏng `128` block với xác suất **`1/16`** mỗi chunk/tick chọn 1 cột ngẫu nhiên để đóng băng nước lộ thiên hoặc đặt tuyết; tuân thủ mặc định `snowAccumulationHeight = 1` (**chỉ phủ 1 lớp tuyết**, không tự chồng lên 2–8 lớp).

### 5.4. Sinh Mob, Trọng số & Cá thể non (`MobSpawner`, `MobSpawnTable`, `Husk`, `MobEntity`)
- **Tách chu kỳ sinh:** `MONSTER` chạy mỗi **`0,05s` (1 tick)**; `CREATURE` bổ sung định kỳ chạy mỗi **`20,0s` (400 tick)**. Khám phá chunk mới giữ nguyên **`10%`** (bỏ qua cap 10).
- **Bảng trọng số cập nhật:**
  - `PLAINS`: giữ nguyên (đã khớp 100%).
  - `FOREST` (mới): Creatures = Sheep `12 (4)`, Pig `10 (4)`, Chicken `10 (4)`, Cow `8 (4)`, **Wolf `5 (4)`**; Monsters = bảng chuẩn (tổng 415).
  - `FLOWER_FOREST` (mới): Creatures = Sheep `12 (4)`, Pig `10 (4)`, Chicken `10 (4)`, Cow `8 (4)`, **Snow Rabbit `4 (2–3)`**; Monsters = bảng chuẩn (tổng 415).
  - `DESERT`: Creatures = **Rabbit `4 (2–3)`** (sửa từ `12` → `4`); Monsters = giữ nguyên chuẩn 1.21.11 (tổng 415, Camel Husk Jockey `10%`).
  - `SNOWY_PLAINS`: `Flat` giữ nguyên (`10 / 1`); `Grove` sửa Snow Rabbit từ `8` → **`4`** (`Wolf 8, Snow Fox 8, Snow Rabbit 4`); `Slopes/Peaks` giữ nguyên (`5 / 4`); Monsters giữ nguyên (`Stray 80, Skeleton 20`).
- **Tỉ lệ Baby khi sinh tự nhiên:**
  - `Husk`: **`5%`** (trừ Husk cầm giáo cưỡi Camel Husk luôn là người lớn).
  - `Cow, Sheep, Pig, Chicken, Wolf, Goat`: **`5%`**.
  - `Rabbit / Snow Rabbit`: **`10%`**.
  - Giữ nguyên: `Zombie / ZV` (`5%`), `Snow Fox` (`5%`), `Horse / Donkey` (`20%`), `Polar Bear` (con thứ 2 trong bầy 2 con).
  - Cập nhật `MobEntity.setBaby(true)` đồng bộ thu nhỏ hitbox (`0,5×`) tương tự `Zombie`/`PolarBear`.

### 5.5. Hành vi AI & Loot (`Sheep`, `Enderman`, `MountableMob`, `MobLootTable`)
- **Cừu ăn cỏ (`Sheep.GrazeGoal`):** Xác suất **`1/1000` mỗi tick** (cừu lớn, tb ~`50s`) và **`1/50` mỗi tick** (cừu con, tb ~`2,5s`).
- **Enderman nhặt / đặt block:** Nhặt **`1/20` mỗi 2 tick** (`0,1s`, thử 1 ô ngẫu nhiên quanh người); Đặt **`1/2000` mỗi 2 tick** (tb ~`200s`).
- **Thuần hóa Ngựa / Lừa (`MountableMob`):** Mỗi lượt kiểm tra `random.nextInt(100) < temper`; trượt thì hất xuống và cộng **`temper += 5`** (thay vì cố định `+25`).
- **Witch Loot (chuẩn Java 1.21+):** Luôn rơi cố định **`4–8 Redstone Dust`**, cộng `1–3` lượt × `0–2` món từ 6 món còn lại (`Glass Bottle, Glowstone Dust, Gunpowder, Spider Eye, Sugar, Stick`), giữ `8,5%` rơi lọ thuốc đang uống.

## 6. Các quyết định đã chốt
1. **Bố cục 5 dải theo trục X:** giữ nguyên sự tiện lợi của phím `Ctrl+C` (nhảy thẳng tới tâm từng biome) và tính tất định, ranh giới mượt cho bộ kiểm thử.
2. **Cách giữ `Camel` ở Desert:** Sinh cố định **1 con Camel hiền lành ngay gần Kim tự tháp sa mạc** khi người chơi lần đầu khám phá vùng Kim tự tháp — giữ bảng `DESERT_CREATURES` chuẩn 100% `desert.json` (chỉ có Rabbit weight 4) mà người chơi vẫn luôn gặp được Camel.
3. **Bỏ hồ nước nhỏ & ốc đảo cây cọ:** Đúng chuẩn Minecraft 1.18+ không còn hồ nước nhỏ trên mặt đất và không có cây cọ (sông vẫn chảy qua 5 biome).
4. **Thỏ ở `FLOWER_FOREST`:** Dùng `MobType.SNOW_RABBIT` để tận dụng bộ da có sẵn (`white` / `white_splotched`).
5. **Mật độ vẽ mưa/tuyết trong `WeatherRenderer`:** Giữ nguyên `35%`/`29%` (tối ưu đồ họa 60 FPS).

## 7. Phần để bước triển khai xử lý
- Chọn hằng số seed phụ (`long` XOR) cho từng lượt feature mới (cây, hoa, bụi khô, xương rồng, mía, hồ lava) sao cho không trùng mẫu noise.
- Điều chỉnh các bài test hồi quy (`TerrainGenerationChecks`, `MobSpawnTableChecks`, `MobSpawnerChecks`, `EnvironmentChecks`, `MobLootChecks`, `MountAndCamelHuskChecks`, `PassiveAndNeutralMobChecks`) khớp đúng đặc tả mới.
- Cập nhật đầy đủ Phần A của `README.md`.
