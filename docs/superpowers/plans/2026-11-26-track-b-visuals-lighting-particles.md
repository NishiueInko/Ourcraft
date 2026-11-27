# Track B — Visuals, Lighting & Particles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nâng cấp chất lượng đồ họa sát Minecraft hiện đại: Sky Light & Block Light (0–15 BFS), Biome Tint động theo 5 biome, hoạt ảnh nước (32 frame) & dung nham (20 frame), mây 3D dày 4 block, khung ngắm Wireframe + vết nứt 10 giai đoạn, và hệ thống hạt (Particles) zero-GC.

**Architecture:** Tách toàn bộ thuật toán ánh sáng, bảng màu biome, bộ lặp frame chất lỏng, viền chọn block và pool hạt thành 5 lớp độc lập (`ChunkLighting`, `BiomeTint`, `LiquidAnimator`, `BlockOutlineRenderer`, `ParticleManager`); giữ độc quyền chỉnh sửa `ShaderProgram`, `Chunk`, `SkyRenderer` và vùng hook Track B của `VoxelGameApp`.

**Tech Stack:** Java 17 (UTF-8), libGDX 1.13.1, OpenGL 3.3 Core, `water_still.png` (`16×512`), `lava_still.png` (`16×320`), `clouds.png` (`256×256`).

**Spec:** `docs/superpowers/specs/2026-11-26-minecraft-parity-upgrade-design.md`

## Global Constraints
- Tuân thủ Task 0 đã có sẵn (`Texture` `8×6`, `WoolBlock`, vùng `// === HOOK: TRACK B ===`).
- Không sửa `Player.java`, `Hotbar.java`, `UIRenderer.java`, `WoolBlock.java`, `Item.java`, `Wolf/Pig/Rabbit` để không xung đột với Track A và Track C.
- Java 17 UTF-8, chú thích tiếng Việt đầy đủ, viết 100% mã nguồn, không `// TODO`, không cấp phát rác GC trong Game Loop, giải phóng toàn bộ GPU resource qua `Disposable.dispose()`, giữ 60 FPS.
- Tất cả 24 file test cũ (đặc biệt `ChunkMeshRegressionChecks`, `EnvironmentChecks`) và `VisualsLightingChecks` mới phải xanh khi chạy `.\gradlew.bat regressionChecks`.

## Review Focus
- Thêm màu đỉnh (hoặc uniform tint) vào `ShaderProgram.java` làm hỏng `MobRenderer`, `UIRenderer`, `EnvironmentMesh` vốn chỉ có 6 floats/đỉnh → bắt buộc tách `VertexAttributes` riêng hoặc dùng `uTintColor`/`aShade` tương thích 100% với 6 floats mặc định, kiểm tra toàn bộ `ChunkMeshRegressionChecks` và `MobAssetAndModelChecks`.
- `ChunkLighting` cấp phát `int[]` queue mới mỗi lần cập nhật block gây GC spike khi phá/đặt liên tục → kiểm tra dùng hàng đợi vòng cố định tái sử dụng trong Task B1.
- `BiomeTint` nhân màu lên tile vốn đã nhuộm `PLAINS_TINT` trong `Texture.java` làm cỏ `PLAINS` bị tối sạm 2 lần → kiểm tra tỉ lệ bù so với `PLAINS_TINT` sao cho tại `PLAINS` hệ số nhân bằng `(1, 1, 1)` trong Task B2.
- `LiquidAnimator` nạp 52 frame nhưng rò rỉ `Pixmap` không `dispose()` → kiểm tra `dispose()` giải phóng sạch tất cả buffer trong Task B3.
- `ParticleManager` vượt quá `MAX_PARTICLES = 512` khi đào nhanh hoặc đứng cạnh nhiều Lava gây `ArrayIndexOutOfBoundsException` → kiểm tra test tràn pool ghi đè hạt cũ nhất trong Task B5.

---

### Task B1: `ChunkLighting` (Sky Light + Block Light 4-bit BFS) & tích hợp `Chunk`/`World`

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/ChunkLighting.java`
- Modify: `src/main/java/com/voxelgame/engine/Chunk.java`
- Modify: `src/main/java/com/voxelgame/engine/World.java` (chỉ phần cập nhật ánh sáng khi sinh/sửa block)
- Create: `src/test/java/com/voxelgame/engine/VisualsLightingChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java`

**Interfaces:**
- Consumes: `Chunk.SIZE = 16`, `Block.isOpaque()`, `Block.LAVA`, `World.getBlock(x, y, z)`.
- Produces:
  - `ChunkLighting`: `getSkyLight(x, y, z) -> int`, `getBlockLight(x, y, z) -> int`, `setSkyLight(...)`, `setBlockLight(...)`, `recomputeColumnSkyLight(...)`, `propagateLight(World, int wx, int wy, int wz)` (dùng ring-buffer `int[]` tĩnh).
  - `World.getSkyLight(int x, int y, int z) -> int`, `World.getBlockLight(int x, int y, int z) -> int` (để Track C `F3` đọc được khi merge).
  - `Chunk` áp dụng `max(sky/15f * skyFactor, block/15f)` kết hợp Directional Shade & AO lên ánh sáng đỉnh.

- [ ] **Step 1: Tạo `VisualsLightingChecks.java` với test `checkSkyAndBlockLighting()` bị lỗi**
  - Trong `World.empty()` có mái đá kín ở `Y=20` và sàn đá ở `Y=10`: kiểm tra ô `Y=15` dưới mái có `skyLight < 15` (tối ở giữa phòng kín), sau khi đục lỗ mái ở `Y=20` thì cột dưới lỗ nhận `skyLight == 15` và lan ngang giảm 1 mỗi bước.
  - Đặt `Block.LAVA` tại `(8, 12, 8)`: kiểm tra `getBlockLight(8, 12, 8) == 15`, `(9, 12, 8) == 14`, `(12, 12, 8) == 11`.
  - Đăng ký `VisualsLightingChecks.run();` vào `VoxelRegressionChecks.main`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `ChunkLighting` và `World.getSkyLight`/`getBlockLight`.

- [ ] **Step 3: Triển khai `ChunkLighting` & nối vào `Chunk` / `World`**
  - Cấp phát lười mảng `byte[4096]` trong `Chunk` khi chunk có dữ liệu ánh sáng khác mặc định.
  - Lưu ring-buffer tĩnh/thread-local cho BFS ánh sáng để zero-GC; khi ánh sáng lan sang chunk lân cận thì đánh dấu `dirty = true` trên chunk đó để dựng lại mesh.
  - Giữ tương thích tuyệt đối với `ChunkMeshRegressionChecks`.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task B1**

```bash
git add src/main/java/com/voxelgame/engine/ChunkLighting.java src/main/java/com/voxelgame/engine/Chunk.java src/main/java/com/voxelgame/engine/World.java src/test/java/com/voxelgame/engine/VisualsLightingChecks.java src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java
git commit -m "feat(track-b): them he thong Sky Light va Block Light BFS 4-bit"
```

---

### Task B2: `BiomeTint` động theo 5 Biome & hỗ trợ màu đỉnh an toàn trong `ShaderProgram`

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/BiomeTint.java`
- Modify: `src/main/java/com/voxelgame/engine/ShaderProgram.java`
- Modify: `src/main/java/com/voxelgame/engine/Chunk.java`
- Modify: `src/main/java/com/voxelgame/engine/World.java` (truyền biome sampler cho `Chunk` hoặc đọc qua `World`)
- Modify: `src/test/java/com/voxelgame/engine/VisualsLightingChecks.java`

**Interfaces:**
- Consumes: `BiomeType` (`SNOWY_PLAINS`, `FOREST`, `FLOWER_FOREST`, `PLAINS`, `DESERT`), `World.getBiome(x, z)`.
- Produces:
  - `BiomeTint.grassMultiplier(World world, int x, int z, float[] outRgb)` và `BiomeTint.foliageMultiplier(World world, int x, int z, float[] outRgb)` (trả hệ số nhân bù so với `PLAINS_TINT` sao cho tại trung tâm `PLAINS` kết quả gần `(1, 1, 1)` và pha mượt 3×3 ở ranh giới biome).
  - `ShaderProgram.enableChunkColorMode(boolean enabled)`: bật đọc màu RGB từ `aColor` cho mesh chunk, giữ `UIRenderer` & `MobRenderer` dùng trắng mặc định không đổi định dạng đỉnh 6 floats.

- [ ] **Step 1: Thêm test `checkBiomeTintRatios()` vào `VisualsLightingChecks.java`**
  - Kiểm tra `BiomeTint` tại `PLAINS` trả về xấp xỉ `(1.0f, 1.0f, 1.0f)`, tại `DESERT` có kênh đỏ lớn hơn kênh xanh dương (tông vàng úa), tại `SNOWY_PLAINS` có kênh xanh lục/lam lạnh hơn `PLAINS`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `BiomeTint`.

- [ ] **Step 3: Triển khai `BiomeTint`, `ShaderProgram` & `Chunk`**
  - `Chunk` sử dụng `VertexAttribute(Usage.ColorPacked, 4, "aColor")` (chỉ thêm `1` float đóng gói vào đỉnh chunk để giữ băng thông VRAM thấp); khi không phải cỏ (`GRASS` top/side overlay, `SHORT_GRASS`, `TALL_GRASS`) hoặc `OAK_LEAVES`, ghi màu trắng `0xFFFFFFFF`.
  - `ShaderProgram`: trong Vertex Shader khai báo `layout (location = 3) in vec4 aColor;`, trong Java khi `uUseVertexColor == 0` luôn dùng `vec4(1.0)` nên các `Mesh` chỉ có `location 0..2` (`MobRenderer`, `UIRenderer`, `EnvironmentMesh`) vẫn vẽ chuẩn 100%.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task B2**

```bash
git add src/main/java/com/voxelgame/engine/BiomeTint.java src/main/java/com/voxelgame/engine/ShaderProgram.java src/main/java/com/voxelgame/engine/Chunk.java src/main/java/com/voxelgame/engine/World.java src/test/java/com/voxelgame/engine/VisualsLightingChecks.java
git commit -m "feat(track-b): them BiomeTint dong theo 5 biome cho co va la"
```

---

### Task B3: `LiquidAnimator` (Hoạt ảnh 32 frame Nước & 20 frame Dung nham)

**Recommended Model:** `flash`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/LiquidAnimator.java`
- Modify: `src/main/java/com/voxelgame/engine/Texture.java` (thêm getter `getGdxTexture()` nếu cần hoặc cập nhật qua `Texture.updateTile(int tileIndex, Pixmap frame)`)
- Modify: `src/test/java/com/voxelgame/engine/VisualsLightingChecks.java`

**Interfaces:**
- Consumes: `textures/water_still.png` (`16×512` = 32 frame), `textures/lava_still.png` (`16×320` = 20 frame), `Texture.TILE_WATER`, `Texture.TILE_LAVA`.
- Produces:
  - `LiquidAnimator implements Disposable`: `update(float dt, Texture atlas)` (mỗi `0.10s` tăng frame index modulo `32` cho Water và `20` cho Lava, gọi `glTexSubImage2D` khi có GL context), `getWaterFrameCount() -> int` (`32`), `getLavaFrameCount() -> int` (`20`), `getCurrentWaterFrame() -> int`, `getCurrentLavaFrame() -> int`, `dispose()`.

- [ ] **Step 1: Thêm test `checkLiquidAnimatorFrames()` vào `VisualsLightingChecks.java`**
  - Khởi tạo `LiquidAnimator` ở chế độ CPU (nạp pixmap từ resource mà chưa cần GL upload), kiểm tra `getWaterFrameCount() == 32`, `getLavaFrameCount() == 20`; bước `update(0.10f, null)` 32 lần → `getCurrentWaterFrame()` quay về `0`; gọi `dispose()` sạch sẽ.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `LiquidAnimator`.

- [ ] **Step 3: Triển khai `LiquidAnimator` & helper upload tile ở `Texture.java`**
  - Nạp sẵn mảng `Pixmap[]` (`32` frame Water đã áp dụng style xanh giống `Texture.TileStyle.WATER` và `20` frame Lava).
  - Trong `update(float dt, Texture atlas)`: cộng dồn timer, mỗi `0.10s` đổi frame và nếu `atlas != null && Gdx.gl != null` thì gọi `Gdx.gl.glTexSubImage2D` cập nhật đúng ô `(col*16, row*16)` trên GPU, không tạo buffer mới.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task B3**

```bash
git add src/main/java/com/voxelgame/engine/LiquidAnimator.java src/main/java/com/voxelgame/engine/Texture.java src/test/java/com/voxelgame/engine/VisualsLightingChecks.java
git commit -m "feat(track-b): them LiquidAnimator cho nuoc 32 frame va lava 20 frame"
```

---

### Task B4: Mây 3D khối dày (`SkyRenderer`) & `BlockOutlineRenderer` (Wireframe + Vết nứt 10 cấp)

**Recommended Model:** `flash`

**Files:**
- Modify: `src/main/java/com/voxelgame/engine/SkyRenderer.java`
- Create: `src/main/java/com/voxelgame/engine/BlockOutlineRenderer.java`
- Modify: `src/test/java/com/voxelgame/engine/VisualsLightingChecks.java`

**Interfaces:**
- Consumes: `textures/environment/clouds.png`, `Block.Bounds`, `ShaderProgram`, `EnvironmentMesh`.
- Produces:
  - `SkyRenderer`: dựng mesh mây 3D khối dày `4` block (`CLOUD_THICKNESS = 4.0f`) từ lưới `clouds.png`, tịnh tiến mượt theo `cloudOffset` quanh camera.
  - `BlockOutlineRenderer implements Disposable`: `render(ShaderProgram shader, int bx, int by, int bz, Block block, int state, float miningProgress)` (vẽ khung dây đen `GL_LINES` quanh `Block.getSelectionBounds(state)` và overlay vết nứt 10 giai đoạn procedurally khi `miningProgress > 0.001f`).

- [ ] **Step 1: Thêm test `checkBreakStageCalculation()` vào `VisualsLightingChecks.java`**
  - Kiểm tra hàm tĩnh `BlockOutlineRenderer.stageForProgress(float progress)`: `0.0f -> -1`, `0.05f -> 0`, `0.50f -> 4` hoặc `5`, `0.99f -> 9`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `BlockOutlineRenderer`.

- [ ] **Step 3: Triển khai mây 3D trong `SkyRenderer` & `BlockOutlineRenderer`**
  - `SkyRenderer`: chỉ đọc `clouds.png` 1 lần khi tạo, với mỗi pixel có `alpha > 128` trong vùng cửa sổ quanh người chơi tạo hộp dày 4 block (cull mặt trong giữa 2 ô mây liền kề) và upload một `EnvironmentMesh` tĩnh; khi vẽ chỉ dịch `uModel`.
  - `BlockOutlineRenderer`: nạp trực tiếp 10 ảnh gốc `textures/block/destroy_stage_0.png` .. `destroy_stage_9.png` (trích từ `texture.zip` ở Task 0.3) đóng gói thành texture `160×16` lúc khởi tạo; cấp phát 1 `Mesh` cho 24 đỉnh `GL_LINES` và 1 `Mesh` cho 24 đỉnh 6 mặt vết nứt, cập nhật `vertices` in-place (zero-GC), `dispose()` giải phóng cả 2 `Mesh` và texture vết nứt.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task B4**

```bash
git add src/main/java/com/voxelgame/engine/SkyRenderer.java src/main/java/com/voxelgame/engine/BlockOutlineRenderer.java src/test/java/com/voxelgame/engine/VisualsLightingChecks.java
git commit -m "feat(track-b): them may 3D day 4 block va BlockOutlineRenderer wireframe vet nut"
```

---

### Task B5: `ParticleManager` (Pool tĩnh 512 hạt) & nối hook Track B vào `VoxelGameApp`

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/ParticleManager.java`
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (vùng HOOK: TRACK B)
- Modify: `src/test/java/com/voxelgame/engine/VisualsLightingChecks.java`

**Interfaces:**
- Consumes: `Texture` (lấy mảnh UV `4×4` của block), `Camera`, `ShaderProgram`, `LiquidAnimator`, `BlockOutlineRenderer`.
- Produces:
  - `ParticleManager implements Disposable`: `MAX_PARTICLES = 512`, `spawnBlockBreak(int x, int y, int z, Block block, int count)`, `spawnFlame(float x, float y, float z)`, `spawnSplash(float x, float y, float z, int count)`, `spawnPotionSplash(float x, float y, float z, int rgb)`, `update(float dt)`, `render(ShaderProgram shader, Camera camera, Texture blockAtlas)`, `activeCount() -> int`.

- [ ] **Step 1: Thêm test `checkParticlePoolLimits()` vào `VisualsLightingChecks.java`**
  - Sinh `600` hạt liên tiếp vào `ParticleManager`: xác nhận `activeCount() <= 512` (không văng lỗi tràn mảng và ghi đè vòng); cập nhật `update(10.0f)` → toàn bộ hạt hết tuổi thọ và `activeCount() == 0`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `ParticleManager`.

- [ ] **Step 3: Triển khai `ParticleManager` & nối vùng HOOK: TRACK B trong `VoxelGameApp`**
  - Toàn bộ dữ liệu 512 hạt lưu trong mảng nguyên thủy cấp phát 1 lần lúc tạo; billboard mỗi hạt xoay đối mặt `Camera` bằng vector right/up tính từ ma trận view.
  - Trong `VoxelGameApp`: khởi tạo & giải phóng `LiquidAnimator`, `BlockOutlineRenderer`, `ParticleManager` ở vùng Track B; gọi `BlockOutlineRenderer.render(...)` thay cho `uiRenderer.renderBlockHighlight(...)` (giữ nguyên `UIRenderer` không cần xóa hàm cũ để không đụng Track C); sinh hạt khi đang đào/phá block, va chạm nước và quanh người/mob cháy.

- [ ] **Step 4: Chạy toàn bộ regressionChecks**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task B5**

```bash
git add src/main/java/com/voxelgame/engine/ParticleManager.java src/main/java/com/voxelgame/VoxelGameApp.java src/test/java/com/voxelgame/engine/VisualsLightingChecks.java
git commit -m "feat(track-b): them ParticleManager pool 512 hat va noi hook do hoa"
```

---

### Task B6: Hiệu ứng Lửa cháy trên Mob (`MobRenderer`) & Lớp phủ Lửa góc nhìn 1 (`fire_0/1.png`)

**Recommended Model:** `flash`

**Files:**
- Modify: `src/main/java/com/voxelgame/engine/MobRenderer.java`
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (vùng HOOK: TRACK B)
- Modify: `src/test/java/com/voxelgame/engine/VisualsLightingChecks.java`

**Interfaces:**
- Consumes: `textures/block/fire_0.png` & `textures/block/fire_1.png` (dải hoạt ảnh 16×512 trích từ `texture.zip`), `LivingEntity.isOnFire()`, `MobEntity.getWidth()`, `MobEntity.getHeight()`.
- Produces:
  - `MobRenderer`: nạp `fire_0.png` và `fire_1.png` (32 frame mỗi tờ, tự phát sáng giống `EYES`, `GL_CULL_FACE` tắt cục bộ), vẽ 2 mặt lửa chéo nhau dạng chữ X ôm sát hitbox (`width × height`) cho bất kỳ `MobEntity` nào có `isOnFire() == true` (khi người chơi đốt bằng `Flint and Steel`, Zombie/Skeleton/Stray/Zombie Villager cháy nắng, hoặc mob chạm Lava).
  - `VoxelGameApp` (vùng HOOK: TRACK B): khi `player.isOnFire() == true`, vẽ 2 dải lửa `fire_0`/`fire_1` hoạt ảnh bán trong suốt ở cạnh dưới màn hình 2D dưới lớp HUD.

- [ ] **Step 1: Thêm test `checkFireAnimationFrameSelection()` vào `VisualsLightingChecks.java`**
  - Kiểm tra hàm tính UV frame lửa theo thời gian `fireFrameUV(double timeSeconds, int frameCount)` cuộn đều `0..frameCount-1` mỗi `0.05s` và không vượt biên `[0, 1]`.
  - Xác nhận khi đốt `Sheep` bằng `Flint and Steel` hoặc đặt `Zombie` dưới nắng (`sunY > 0.05`) thì `mob.isOnFire() == true` (sẵn sàng kích hoạt lượt vẽ lửa trong `MobRenderer`).

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có helper `fireFrameUV`.

- [ ] **Step 3: Triển khai vẽ lửa cháy trên `MobRenderer` & lớp phủ cháy góc nhìn 1**
  - Trong `MobRenderer`: khởi tạo 1 `Mesh` tĩnh chữ X chuẩn đơn vị `[ -0.5..0.5, 0..1, -0.5..0.5 ]` và nạp texture `fire_0.png` / `fire_1.png`; trong vòng vẽ thực thể, nếu `entity.isOnFire()`, scale `rootTransform` theo `(width * 1.15f, height * 1.05f, width * 1.15f)` và vẽ không bật fog tối (tự phát sáng `uWorldLight = (1, 1, 1)`), rồi khôi phục trạng thái cũ. Giải phóng mesh & texture lửa trong `MobRenderer.dispose()`.
  - Trong `VoxelGameApp` vùng Track B: tái sử dụng `Mesh` quad 2D (hoặc helper UI) vẽ lửa cạnh dưới màn hình khi `player.isOnFire()`.

- [ ] **Step 4: Chạy toàn bộ regressionChecks**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task B6**

```bash
git add src/main/java/com/voxelgame/engine/MobRenderer.java src/main/java/com/voxelgame/VoxelGameApp.java src/test/java/com/voxelgame/engine/VisualsLightingChecks.java
git commit -m "feat(track-b): them hieu ung lua chay hoat anh tren mob va goc nhin 1"
```
