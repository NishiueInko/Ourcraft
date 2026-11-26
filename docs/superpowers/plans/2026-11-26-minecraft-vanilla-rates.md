# Minecraft Vanilla Rates & 5-Biome Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mở rộng bản đồ VoxelGame lên `4000 × 256 × 2048` (5 biome: `Snowy Plains`, `Forest`, `Plains`, `Flower Forest`, `Desert`) và chuẩn hóa 100% các tỉ lệ thời tiết, tích tuyết, địa hình/thực vật, sinh/hủy mob, biến thể non, hành vi AI và bảng rơi đồ theo Minecraft Java Edition 1.21.11.

**Architecture:** Mở rộng `BiomeType` thành 5 dải theo trục X (`BIOME_WIDTH = 800`), dùng 3 bit thấp của mảng `byte[] biomes` trong `TerrainGenerator` để lưu mã biome mà không tốn thêm RAM, rồi lần lượt chuẩn hóa thời tiết/tích tuyết (Giai đoạn 1), sinh thế giới (Giai đoạn 2), sinh mob/AI/loot (Giai đoạn 3), và đồng bộ Phần A `README.md` (Giai đoạn 4). Thực thi theo **Cách A (3 phiên sạch)**: Phiên 1 hoàn thành Giai đoạn 1, Phiên 2 hoàn thành Giai đoạn 2, Phiên 3 hoàn thành Giai đoạn 3 & 4.

**Tech Stack:** Java 17 (UTF-8), libGDX 1.13.1, Gradle `./gradlew check` (`regressionChecks`).

**Spec:** `docs/superpowers/specs/2026-11-26-minecraft-vanilla-rates-design.md`

## Global Constraints
- Tuân thủ `AGENTS.md`: bảo toàn 100% tính năng gameplay, đồ họa, vật lý, UI/HUD, âm thanh ngoài phạm vi đổi; giữ vững **60 FPS** (không cấp phát rác GC trong Game Loop, không rò rỉ GPU/Off-heap/Thread).
- 100% mã nguồn đầy đủ, biên dịch chạy ngay trên Java 17 (UTF-8), chú thích tiếng Việt rõ ràng; cấm tuyệt đối code rút gọn hoặc `// TODO`.
- Đồng bộ 100% Phần A của `README.md` sau mỗi thay đổi.
- Chạy `.\gradlew.bat check` (và `regressionChecks`) phải pass 100% ở cuối mỗi giai đoạn.

## Review Focus
1. **Tràn bit mã biome trong `byte[] biomes`:** 5 biome (`0..4`) cần 3 bit thấp (`BIOME_MASK = 7`), dịch cờ sang `NEAR_LAVA = 8`, `RIVER = 16`, `LAKE = 32`, cập nhật `DesertPyramid.BIOME_AND_RIVER_MASK = 0x17` và `DesertPyramidChecks` (hiện đang gán cứng `|= 8`).
2. **Mưa & cháy nắng ở `FOREST` / `FLOWER_FOREST`:** Đổi mọi kiểm tra `== BiomeType.PLAINS` ở `WeatherManager`, `WeatherRenderer`, `World.isRainingAt` sang `biome.hasRain()`.
3. **Khôi phục hitbox khi đổi `setBaby(true → false)`:** `MobEntity.setBaby(boolean)` phải đồng bộ thu nhỏ hitbox `0,5×` khi `true` và khôi phục `1,0×` khi `false` (giữ nguyên `1,06×` visual scale của Husk trưởng thành).
4. **Husk 5% con non & Camel Husk Jockey:** Husk cầm giáo cưỡi Camel Husk luôn là người lớn (`new Husk(x, y, z, true, false)`); test cố định dùng constructor tường minh `baby = false`, bổ sung test riêng cho Baby Husk `5%`.
5. **Tách nhịp `CREATURE` (`20s`) không làm chậm khám phá chunk:** Chỉ chu kỳ sinh bổ sung định kỳ của `CREATURE` chạy mỗi `20s` (`400` tick), còn `MONSTER` chạy mỗi `0,05s` (`1` tick) và quét chunk khám phá mới (`10%`) vẫn chạy đều đặn khi người chơi di chuyển.

---

## Giai đoạn 1: Bản đồ `4000 × 2048` (5 Biome) & Thời tiết / Tích tuyết chuẩn Minecraft

### Task 1.1: Mở rộng `BiomeType`, `World`, `TerrainGenerator` (5 biome & bitmask), `DesertPyramid`
**Files:**
- Modify: `src/main/java/com/voxelgame/engine/BiomeType.java`
- Modify: `src/main/java/com/voxelgame/engine/World.java`
- Modify: `src/main/java/com/voxelgame/engine/TerrainGenerator.java`
- Modify: `src/main/java/com/voxelgame/engine/DesertPyramid.java`

- [x] **Step 1:** Cập nhật `BiomeType.java` với 5 giá trị `SNOWY_PLAINS, FOREST, PLAINS, FLOWER_FOREST, DESERT` và các phương thức `hasRain()`, `hasSnow()`, `isDry()`.
- [x] **Step 2:** Cập nhật `World.java`: `BIOME_WIDTH = 800`, `WORLD_SIZE_X = 5 * BIOME_WIDTH` (`4000`), `WORLD_CHUNKS_Z = 128` (`2048`), `CENTER_X = 2000`, `CENTER_Z = 1024`, `RESPAWN_X = 2000`, `RESPAWN_Z = 1600`; lưu `desertPyramid` + getter `desertPyramid()`; cập nhật `setBiomeCenterPosition` cho 5 biome; `isRainingAt` dùng `hasRain()`; chuyển tích tuyết/đóng băng vào `updateRandomBlocks` với xác suất `1/16` mỗi chunk/tick và `accumulateSnow` chỉ đặt tối đa 1 lớp (`snowAccumulationHeight = 1`).
- [x] **Step 3:** Cập nhật `TerrainGenerator.java`: `BIOME_MASK = 7`, `NEAR_LAVA = 8`, `RIVER = 16`, `LAKE = 32`; `biomeAt` dùng `& BIOME_MASK`; `getBiome` chia 5 dải; `surfaceY` và `generateLakes` theo tọa độ 5 dải; cập nhật tạm cây/thực vật dùng `biome.hasRain()` để tương thích ở Giai đoạn 1.
- [x] **Step 4:** Cập nhật `DesertPyramid.java`: `BIOME_AND_RIVER_MASK = TerrainGenerator.BIOME_MASK | TerrainGenerator.RIVER` (`0x17`), `boundary = 4 * BIOME_WIDTH` (`3200`).

### Task 1.2: Cập nhật `WeatherManager`, `WeatherRenderer`, `UIRenderer`, `VoxelGameApp` & tương thích tạm `MobSpawnTable`/`MobSpawner`
**Files:**
- Modify: `src/main/java/com/voxelgame/engine/WeatherManager.java`
- Modify: `src/main/java/com/voxelgame/engine/WeatherRenderer.java`
- Modify: `src/main/java/com/voxelgame/engine/UIRenderer.java`
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java`
- Modify: `src/main/java/com/voxelgame/engine/MobSpawnTable.java`
- Modify: `src/main/java/com/voxelgame/engine/MobSpawner.java`

- [x] **Step 1:** Cập nhật `WeatherManager.java`: `CLEAR_MIN_SECONDS = 600`, `CLEAR_MAX_SECONDS = 9000`, `PRECIP_MIN_SECONDS = 600`, `PRECIP_MAX_SECONDS = 1200`, getter `remainingSeconds()`, bỏ timer `0,35s`, dùng `biome.isDry()` và `biome.hasRain()`.
- [x] **Step 2:** Cập nhật `WeatherRenderer.java`, `UIRenderer.java`, `VoxelGameApp.java` hỗ trợ đủ 5 biome.
- [x] **Step 3:** Cập nhật tạm `MobSpawnTable.java` và `MobSpawner.java` để nhận diện `FOREST`, `FLOWER_FOREST` qua `biome.hasRain()`.

### Task 1.3: Cập nhật kiểm thử & Đồng bộ `README.md` cho Giai đoạn 1
**Files:**
- Modify: `src/test/java/com/voxelgame/engine/EnvironmentChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/DesertPyramidChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/MobPhysicsChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/MobSpawnerChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/MountAndCamelHuskChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/TerrainGenerationChecks.java`
- Modify: `README.md`

- [x] **Step 1:** Cập nhật 6 file test theo Spec Giai đoạn 1 (bao gồm test thời lượng thời tiết `600..9000` / `600..1200`, tích tuyết 1 lớp qua random-tick `1/16`, mưa bảo vệ cháy nắng ở cả `FOREST`/`FLOWER_FOREST`/`PLAINS`, và kiểm tra 5 dải biome).
- [x] **Step 2:** Đồng bộ Phần A của `README.md` cho bản đồ `4000 × 256 × 2048`, 5 biome, `Ctrl+C`, thời tiết & tích tuyết.
- [x] **Step 3:** Chạy `.\gradlew.bat check` — kỳ vọng: `BUILD SUCCESSFUL` và `Các kiểm tra hồi quy voxel đã qua.`

---

## Giai đoạn 2: Sinh Thế Giới chuẩn 100% Minecraft 1.21.11 (`TerrainGenerator`) — *(Phiên 2)*

### Task 2.1: Chuẩn hóa Cây, Hồ & Tuyết địa hình theo chunk `16×16`
**Files:**
- Modify: `src/main/java/com/voxelgame/engine/TerrainGenerator.java`

- [ ] **Step 1:** Bỏ hồ nước nhỏ (`waterChance`), bỏ `placeLandmarkLake` và `plantOasisPalms`; giữ hồ dung nham `lake_lava_surface` với rarity `1/200` chunk (`0,5%` mỗi chunk).
- [ ] **Step 2:** Chuẩn hóa `generateIceAndSnow` & `addCanopySnow`: 100% cột lộ trời ở `SNOWY_PLAINS` (gồm 100% tán lá vân sam lộ trời) phủ đúng 1 lớp `Snow Layer` (giữ `Snow Block` trên đỉnh `Y > 110` và `Ice` trên sông).
- [ ] **Step 3:** Chuyển `generateTrees` sang cơ chế theo từng chunk `16×16`: `PLAINS` `0,05` cây/chunk (`33%` `OLD_OAK`, `67%` `OAK`, bỏ `OAK_BUSH`); `FOREST` `10` lần thử/chunk (`90%` `OAK`, `10%` `OLD_OAK`); `FLOWER_FOREST` `6` lần thử/chunk (`90%` `OAK`, `10%` `OLD_OAK`); `SNOWY_PLAINS` phân vùng `Flat` `0,1` cây/chunk (`SPRUCE`), `Grove` (theo noise rừng ở `Y = 30..61`) `10` lần thử/chunk (`2/3` `SPRUCE`, `1/3` `PINE`), `Slopes/Peaks` (`Y ≥ 62`) `0` cây; `DESERT` `0` cây.

### Task 2.2: Chuẩn hóa Hoa, Cỏ ngắn, Cỏ cao, Xương rồng, Bụi khô & Mía theo chuẩn Minecraft 1.21.11
**Files:**
- Modify: `src/main/java/com/voxelgame/engine/TerrainGenerator.java`
- Modify: `src/test/java/com/voxelgame/engine/TerrainGenerationChecks.java`
- Modify: `README.md`

- [ ] **Step 1:** Chuyển `generatePlants` sang cơ chế theo chunk chuẩn Minecraft:
  - `PLAINS`: cỏ ngắn `~25–30%` mặt cỏ, cỏ cao rarity `1/5` chunk, hoa `~3–5` bông/chunk trọng số `Dandelion 2 : Poppy 1 : Cornflower 1 : Oxeye Daisy 1`.
  - `FOREST`: cỏ ngắn `2` patch/chunk, hoa `~1–2` bông/chunk chỉ `Dandelion 2 : Poppy 1`.
  - `FLOWER_FOREST`: cỏ ngắn `2` patch/chunk, thảm hoa `flower_forest` dày đặc theo gradient 2D (~15–25 bông/chunk, đủ 4 loài).
  - `DESERT`: `Cactus` rarity `1/5` chunk (chiều cao `56%` cao 1, `28%` cao 2, `16%` cao 3), `Dead Bush` `2` patch/chunk.
  - `Sugar Cane`: rarity `1/6` chunk ở biome thường, dày gấp `~6×` ở `DESERT`, chiều cao `50%` cao 2, `33%` cao 3, `17%` cao 4.
- [ ] **Step 2:** Cập nhật `TerrainGenerationChecks.java` kiểm tra mật độ tương đối giữa các biome (`FOREST` nhiều cây hơn `PLAINS`, `FLOWER_FOREST` nhiều hoa hơn `FOREST`/`PLAINS`, `DESERT` nhiều mía hơn `PLAINS`, có đủ mọi block và tính tất định).
- [ ] **Step 3:** Đồng bộ Phần A của `README.md` cho sinh địa hình/thực vật/hồ/tuyết và chạy `.\gradlew.bat check` — kỳ vọng PASS 100%.

---

## Giai đoạn 3: Mob Spawner, Trọng số, Baby, Hành vi AI & Loot chuẩn 100% Minecraft — *(Phiên 3)*

### Task 3.1: Chuẩn hóa `MobSpawnTable`, `MobSpawner` (nhịp 1 tick / 400 tick, Camel Kim tự tháp) & Tỉ lệ Baby
**Files:**
- Modify: `src/main/java/com/voxelgame/engine/MobSpawnTable.java`
- Modify: `src/main/java/com/voxelgame/engine/MobSpawner.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/MobEntity.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/Husk.java`

- [ ] **Step 1:** Cập nhật `MobSpawnTable.java`: thêm `FOREST_CREATURES` (`Sheep 12, Pig 10, Chicken 10, Cow 8, Wolf 5`), `FLOWER_FOREST_CREATURES` (`Sheep 12, Pig 10, Chicken 10, Cow 8, Snow Rabbit 4`), sửa `DESERT_CREATURES` (`Rabbit 4 (2–3)`), sửa `SNOW_GROVE_CREATURES` (`Wolf 8, Snow Fox 8, Snow Rabbit 4`).
- [ ] **Step 2:** Cập nhật `MobEntity.setBaby(boolean)` thu nhỏ hitbox `0,5×` khi `true` và khôi phục `1,0×` khi `false` (giữ `scale = 1.06f` cho Husk trưởng thành). Cập nhật `Husk.java`: `5%` baby tự nhiên; giữ kỵ sĩ cầm giáo là người lớn; thêm constructor `Husk(x, y, z, ironSpear, baby)`.
- [ ] **Step 3:** Cập nhật `MobSpawner.java`: tách `MONSTER_INTERVAL = 0.05f` (1 tick) và `CREATURE_INTERVAL = 20.0f` (400 tick); bỏ lọc `75%` cát phẳng của Camel; sinh cố định 1 `Camel` hiền lành gần `world.desertPyramid()` khi khám phá lần đầu; áp dụng `5%` baby cho `Cow, Sheep, Pig, Chicken, Wolf, Goat` và `10%` baby cho `Rabbit, Snow Rabbit`.

### Task 3.2: Chuẩn hóa Hành vi AI (`Sheep`, `Enderman`, `MountableMob`) & Loot (`MobLootTable`)
**Files:**
- Modify: `src/main/java/com/voxelgame/entity/mob/Sheep.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/Enderman.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/MountableMob.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/MobLootTable.java`

- [ ] **Step 1:** `Sheep.GrazeGoal`: đổi sang xác suất `1/1000` mỗi tick (trưởng thành) và `1/50` mỗi tick (con non).
- [ ] **Step 2:** `Enderman.java`: đổi nhặt block sang `1/20` mỗi 2 tick (`0,1s`, thử 1 ô ngẫu nhiên) và đặt block sang `1/2000` mỗi 2 tick.
- [ ] **Step 3:** `MountableMob.java`: thuần hóa ngẫu nhiên `random.nextInt(100) < temper`, trượt thì hất xuống và cộng `temper += 5`.
- [ ] **Step 4:** `MobLootTable.java`: Witch rơi cố định `4–8 Redstone Dust` + `1–3` lượt × `0–2` món từ 6 món còn lại (bỏ Redstone khỏi pool ngẫu nhiên).

### Task 3.3: Cập nhật kiểm thử Giai đoạn 3
**Files:**
- Modify: `src/test/java/com/voxelgame/engine/MobSpawnTableChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/MobSpawnerChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/CloseHostileChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/PlainsAnimalChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/EquineMountChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/MobLootChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/PassiveAndNeutralMobChecks.java`

- [ ] **Step 1:** Cập nhật các file test kiểm tra đủ bảng 5 biome, chu kỳ 1 tick / 400 tick, Camel Kim tự tháp, Baby Husk + Baby thú hiền lành, `Sheep` graze `1/1000` & `1/50`, `Enderman` pickup/place, `MountableMob` temper `+5`, `Witch` luôn rơi `4–8` Redstone Dust.
- [ ] **Step 2:** Chạy `.\gradlew.bat check` — kỳ vọng PASS 100%.

---

## Giai đoạn 4: Rà soát 60 FPS / Bộ nhớ & Đồng bộ hoàn chỉnh `README.md` — *(Phiên 3)*

**Files:**
- Modify: `README.md`

- [ ] **Step 1:** Rà soát toàn bộ file đã sửa: không cấp phát rác GC trong Game Loop, không rò rỉ GPU/Off-heap/Thread, đủ chú thích tiếng Việt UTF-8, không có `// TODO`.
- [ ] **Step 2:** Cập nhật toàn bộ Phần A của `README.md` khớp 100% mọi thông số sau Giai đoạn 1–3.
- [ ] **Step 3:** Chạy `.\gradlew.bat check` lần cuối — kỳ vọng `BUILD SUCCESSFUL` và `Các kiểm tra hồi quy voxel đã qua.`
