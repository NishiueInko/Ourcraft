# Minecraft-Style OOP Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` (Native execution, chia 3 session sạch ngữ cảnh theo bảng lộ trình bên dưới). Steps use checkbox (`- [ ]`) syntax for tracking; sau mỗi Task hoàn thành bắt buộc đánh dấu `[x]` và commit trước khi sang Task kế tiếp.

## Lộ Trình Thực Thi 3 Session (Native)

| Session | Phạm vi Task | Trọng tâm | Trạng thái |
| --- | --- | --- | --- |
| **Session 1** | Task 1 → Task 3 | Gói `core`, di chuyển package chuẩn Minecraft (`world.*`/`client.*`), tách `Block` khỏi `Entity`, `Biome` (5 lớp con), `Weather` (3 lớp con), khởi tạo `OopArchitectureChecks` | `[x]` Hoàn tất |
| **Session 2** | Task 4 → Task 5 | Hệ phân cấp `Mob` (`abstract MobEntity`, `HostileMob`, `PassiveMob`, `NeutralMob`, `MountableMob`, `EquineMob`, `RangedMob`, `Interactable`) & hệ phân cấp `Item` đa hình (13 lớp con) + dọn `ItemUseHandler` | `[x]` Hoàn tất |
| **Session 3** | Task 6 → Task 8 | Tầng Client/GUI (`Screen`, `BitmapFont`, `HudRenderer`, `PauseScreen`, `InventoryScreen`, `UIRenderer` Facade), tách `GameInputController` khỏi `VoxelGameApp`, đồng bộ `README.md`, viết `OOP.md` & `DIAGRAM.md`, chạy `.\gradlew.bat clean check` | `[ ]` Chưa chạy |

**Quy ước bàn giao giữa các Session:**
1. Đầu mỗi session, đọc `AGENTS.md`, `README.md`, spec và file plan này, kiểm tra `git status` + `git log -n 5` cùng các checkbox `[x]` để nắm đúng điểm tiếp nối.
2. Cuối mỗi session, chạy `.\gradlew.bat check` xanh 100%, đánh dấu `[x]` vào các Task đã xong và cập nhật cột **Trạng thái** ở bảng trên, rồi `git commit` sạch working tree trước khi dừng.

**Goal:** Tái cấu trúc toàn bộ 128 file nguồn và 27 bộ kiểm thử của `VoxelGame` sang kiến trúc phân gói chuẩn Minecraft Java Edition (`core`, `world.*`, `client.*`), làm nổi bật toàn diện 4 tính chất OOP trên `Block`, `Entity`/`Mob`, `Item`, `Biome`, `Weather` và tầng `Client`/`GUI`/`Input`, kèm bộ kiểm thử `OopArchitectureChecks`, đồng bộ `README.md` và 2 tài liệu thuyết trình `OOP.md`, `DIAGRAM.md`.

**Architecture:** Chuyển đổi từng bước an toàn (mỗi task kết thúc bằng `.\gradlew.bat check` xanh 100%). Bắt đầu từ việc tạo gói `core` và di chuyển package thuần túy để ổn định biên dịch, sau đó lần lượt tái thiết kế từng trụ cột OOP (`Block` → `Biome`/`Weather` → `Entity`/`Mob` → `Item` → `Client GUI`/`Input`), và kết thúc bằng tài liệu hóa. Áp dụng tách gói theo trách nhiệm (`world.*` / `client.*`) kết hợp chạy headless-safe (không viết lại lõi OpenGL của `Chunk`/`World`/`ItemEntity`).

**Tech Stack:** Java 17 (`options.release = 17`, UTF-8), Gradle (`.\gradlew.bat check` / `.\gradlew.bat regressionChecks`), libGDX `1.13.1`, Mermaid (cho `DIAGRAM.md`).

**Spec:** `docs/superpowers/specs/2026-11-27-minecraft-oop-refactor-design.md`

## Global Constraints
- Java 17 (`options.release = 17`), mã hóa nguồn `UTF-8`, `libGDX 1.13.1`, OpenGL 3.3 Core, V-Sync 60 FPS.
- Chú thích tiếng Việt đầy đủ cho mọi class, hằng số, phương thức và thuật toán quan trọng; cấm tuyệt đối code rút gọn, `// TODO`, `// FIXME`.
- Không thay đổi thư mục tài nguyên `src/main/resources/` (`textures/`, `audio/`).
- Bảo toàn 100% tính năng gameplay, vật lý, địa hình (seed `20261114L`, bản đồ `4000 × 256 × 2048`), ánh sáng BFS, đồ họa, giao diện, âm thanh và toàn bộ 27 bộ kiểm thử hồi quy hiện có.
- Zero-GC trên hot path (không cấp phát object mới mỗi frame ở `Item`, `Biome`, `Weather`, `Screen`, `HudRenderer`, `GameInputController`).
- Bảo toàn 33 ID block `0..32`, 56 vật phẩm độc lập (`Item.allStandaloneItems().size() == 56`), liên kết 2 chiều `Item.*_WOOL ↔ Block.*_WOOL`, và 19 thức ăn `FoodRegistry.count() == 19`.

## Review Focus
1. **Lệch thứ tự khởi tạo tĩnh `Block` ↔ `Item` ↔ `FoodRegistry`:** Khi đổi `Item` thành `abstract class` với lớp con (`BlockItem`, `FoodItem`, `PotionItem`...), sai thứ tự static init có thể làm `Item.of(Block.WHITE_WOOL) != Item.WHITE_WOOL` hoặc `Item.allStandaloneItems().size() != 56` hoặc `FoodRegistry.count() != 19` → khóa chặt bằng `checkItemHierarchyAndDispatch()` trong Task 5 và `checkWoolFoundation()` sẵn có.
2. **Mất quyền ưu tiên `Flint and Steel` hoặc vỡ tương thích test trực tiếp `mob.onPlayerInteract(world, player)`:** Nếu đẩy toàn bộ tương tác vào `Interactable` mà quên Lượt 1 `Item.onInteractMob`, người chơi sẽ không đốt được `Zombie`/`CamelHusk` → khóa chặt bằng `checkEntityAndMobHierarchy()` trong Task 4 + `CamelHuskBehaviorChecks`/`VisualsLightingChecks`.
3. **Bẫy vi phạm LSP giữa `Horse` và `Donkey`:** Nếu cho `Donkey extends Horse` sẽ làm `donkey instanceof Horse == true` dù `Donkey` không có biến thể màu lông/hoa văn → khóa chặt bằng assertion `!Horse.class.isAssignableFrom(Donkey.class)` và `EquineMob.class.isAssignableFrom(Donkey.class)` trong Task 4.
4. **Reflection trong `VoxelRegressionChecks.checkTerrainSpeedShortcut()` gãy khi tách `GameInputController`:** Test hiện dùng reflection truy cập trường `player` và `isPaused` trên `VoxelGameApp` và truyền `app` vào `InputEventQueue.drain(app)` → khóa chặt bằng việc giữ `VoxelGameApp implements InputProcessor` (ủy quyền sang `GameInputController`) và giữ đồng bộ `player`/`isPaused` trong Task 7.
5. **Tạo rác GC hoặc sai màu/chu kỳ khi thay `BiomeType`/`WeatherManager` sang đa hình:** `BiomeTint` 3×3 và `WeatherManager.update` chạy liên tục trong vòng lặp; mọi lớp con `Biome`/`Weather` phải là singleton Flyweight bất biến, không dùng `new` trong `update()` hay `getBiome()` → khóa chặt bằng kiểm tra tham chiếu singleton `Biome.of(...) == Biome.of(...)` và `WeatherManager` trong Task 3 + `EnvironmentChecks`.

---

### Task 1: Tạo gói `core` và di chuyển toàn bộ cấu trúc package sang chuẩn Minecraft

**Files:**
- Create: `src/main/java/com/voxelgame/core/Tickable.java`
- Create: `src/main/java/com/voxelgame/core/Renderable.java`
- Create: `src/main/java/com/voxelgame/core/Interactable.java`
- Move & Update package/imports: 126 file nguồn trong `src/main/java/com/voxelgame/{engine,entity}/**` sang cây `world/**` và `client/**` đúng theo Mục 5 của Spec (`Main.java` và `VoxelGameApp.java` giữ ở `com.voxelgame`).
- Update: 27 file kiểm thử trong `src/test/java/com/voxelgame/` (giữ runner `com.voxelgame.VoxelRegressionChecks` hoặc giữ nguyên gói `com.voxelgame.engine` cập nhật import; cập nhật đồng bộ `build.gradle` nếu đổi gói runner).
- Modify: `build.gradle` (nếu đổi `mainClass` của `regressionChecks`).

**Interfaces:**
- Produces:
  - `com.voxelgame.core.Tickable`: `void tick(World world, float deltaTime);`
  - `com.voxelgame.core.Renderable`: `void render(ShaderProgram shader);`
  - `com.voxelgame.core.Interactable`: `boolean onInteract(World world, Player player, Item heldItem);`
  - Toàn bộ cấu trúc package mới theo Spec Mục 5 biên dịch sạch mà chưa thay đổi logic nghiệp vụ.

- [x] **Step 1: Tạo 3 giao diện hợp đồng trong `com.voxelgame.core`**
  - `Tickable.java`: khai báo `void tick(World world, float deltaTime);` với chú thích tiếng Việt nêu rõ vai trò chuẩn hóa vòng cập nhật.
  - `Renderable.java`: khai báo `void render(ShaderProgram shader);`.
  - `Interactable.java`: khai báo `boolean onInteract(World world, Player player, Item heldItem);`.

- [x] **Step 2: Di chuyển toàn bộ 126 file nguồn vào đúng package chuẩn Minecraft (`world.*` và `client.*`)**
  - Ánh xạ chính xác:
    - `world.level`: `World`, `Chunk`, `ChunkLighting`, `WorldTime`, `BlockUpdateQueue`, `LeafDecaySystem`, `LiquidFlowSimulator`, `TranslucentChunkSort`
    - `world.level.block`: `Block`, `PlantBlock`, `FlowerBlock`, `WoolBlock` và toàn bộ 21 lớp `*Block` còn lại
    - `world.level.biome`: `BiomeType`, `BiomeTint`, `SnowSubZone`
    - `world.level.weather`: `WeatherManager`
    - `world.level.gen`: `TerrainNoise`, `TerrainGenerator`, `DesertPyramid`
    - `world.item`: `Item`, `FoodRegistry`, `ItemUseHandler`
    - `world.entity`: `Entity`, `LivingEntity`, `ItemEntity`, `FallingBlockEntity`, `DamageSource`
    - `world.entity.player`: `Player`, `PlayerInventory`, `Hotbar`
    - `world.entity.mob`: `MobEntity`, `MountableMob`, `RangedMob`, `MobType`, `MobCategory`, `MobAttitude`, `MobLootTable`, `MobSpawner`, `MobSpawnTable`
    - `world.entity.mob.ai`: `MobGoal`, `MobGoalSelector`, `MobGoals`, `SimpleGridNavigator`
    - `world.entity.mob.monster`: `Zombie`, `ZombieVillager`, `Husk`, `Skeleton`, `Stray`, `Parched`, `Creeper`, `Spider`, `Enderman`, `Witch`, `CamelHusk`
    - `world.entity.mob.animal`: `Cow`, `Sheep`, `Pig`, `Chicken`, `Rabbit`, `Horse`, `Donkey`, `Camel`, `Goat`, `PolarBear`, `Wolf`, `SnowFox`
    - `world.entity.projectile`: `ProjectileEntity`, `ArrowEntity`, `ArrowType`, `ThrownPotionEntity`, `PotionType`, `ThrownEnderPearlEntity`, `ThrownEggEntity`
    - `world.effect`: `StatusEffectType`, `StatusEffectInstance`
    - `world.physics`: `Raycast`
    - `client.render`: `Camera`, `ShaderProgram`, `Texture`, `ItemTextureAtlas`, `EnvironmentMesh`, `SkyRenderer`, `WeatherRenderer`, `BlockOutlineRenderer`, `FirstPersonRenderer`, `LiquidAnimator`, `ParticleManager`
    - `client.render.entity`: `MobRenderer`, `MobAppearance`, `MobTextureKey`, `MobTextureManager`, `MobVisual`, `MobVisualState`
    - `client.model`: `ModelPart`, `MobModel`, `MobModels`
    - `client.gui`: `GuiTextureAtlas`, `UIRenderer`, `InventoryScreenRenderer`, `DebugOverlayRenderer`
    - `client.audio`: `AudioManager`, `MobSoundBank`, `MobSoundEvent`
  - Nâng cấp quyền truy cập (`protected` cho các hook kế thừa xuyên gói của `MobEntity`, `MountableMob`, `RangedMob`, `LivingEntity`; `public` cho các API liên tầng cần gọi từ gói khác như `WeatherManager.CLEAR_MIN_SECONDS`, `WeatherManager.remainingSeconds()`, `WeatherManager.update(float)`, `MobSpawnTable.anyImplementedCreatures(...)`, `World.BIOME_WIDTH`, `World.RESPAWN_X`, `World.RESPAWN_Z`...).
  - Xóa sạch thư mục rỗng cũ `src/main/java/com/voxelgame/engine` và `src/main/java/com/voxelgame/entity`.

- [x] **Step 3: Cập nhật import cho 27 file kiểm thử trong `src/test/java/com/voxelgame/`**
  - Cập nhật toàn bộ câu lệnh `import` trong 27 file test trỏ tới các package mới (giữ nguyên mọi assertion).

- [x] **Step 4: Biên dịch và chạy toàn bộ 27 bộ kiểm thử hồi quy**
  - Run: `.\gradlew.bat check`
  - Expected: `BUILD SUCCESSFUL` và thông báo `Các kiểm tra hồi quy voxel đã qua.`

- [x] **Step 5: Commit mốc di chuyển package**
  - Run: `git add -A && git commit -m "refactor: reorganize packages to Minecraft world/client architecture"`

---

### Task 2: Tái cấu trúc Trụ cột #1 — Hệ phân cấp `Block` (Tách khỏi `Entity`, thêm lớp trừu tượng trung gian) & triển khai `Tickable`/`Renderable`

**Files:**
- Create: `src/main/java/com/voxelgame/world/level/block/SolidBlock.java`
- Create: `src/main/java/com/voxelgame/world/level/block/GravityBlock.java`
- Create: `src/main/java/com/voxelgame/world/level/block/LiquidBlock.java`
- Create: `src/main/java/com/voxelgame/world/level/block/LogBlock.java`
- Create: `src/main/java/com/voxelgame/world/level/block/LeavesBlock.java`
- Modify: `src/main/java/com/voxelgame/world/level/block/Block.java` và các lớp con `*Block.java`
- Modify: `src/main/java/com/voxelgame/world/entity/Entity.java`
- Modify: `src/main/java/com/voxelgame/world/level/LeafDecaySystem.java`
- Modify: `src/main/java/com/voxelgame/world/level/LiquidFlowSimulator.java`
- Create: `src/test/java/com/voxelgame/engine/OopArchitectureChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java`

**Interfaces:**
- Consumes: `core.Tickable`, `core.Renderable`.
- Produces:
  - `public abstract class Block` (không còn `extends Entity`, giữ nguyên 33 singleton `Block.*`, `fromId(byte)`, `Bounds`, `Face`, `RenderLayer`, `GeometryType`).
  - `SolidBlock`, `GravityBlock`, `LiquidBlock`, `LogBlock`, `LeavesBlock`, `PlantBlock` (`public abstract class`).
  - `public abstract class Entity implements Tickable, Renderable` (xóa constructor `Entity(float, float)` dùng riêng cho `Block`, triển khai `tick(World, float)` gọi `update(World, float)`).
  - `LeafDecaySystem implements Tickable`, `LiquidFlowSimulator implements Tickable`.

- [x] **Step 1: Viết kiểm thử OOP mới `OopArchitectureChecks.checkBlockHierarchy()` và nối vào `VoxelRegressionChecks`**
  - Kiểm tra:
    - `!Entity.class.isAssignableFrom(Block.class)`
    - `Tickable.class.isAssignableFrom(Entity.class)` và `Renderable.class.isAssignableFrom(Entity.class)`
    - `Modifier.isAbstract(...)` cho `Block`, `SolidBlock`, `GravityBlock`, `LiquidBlock`, `LogBlock`, `LeavesBlock`, `PlantBlock`
    - `SandBlock`, `GravelBlock` là `GravityBlock`; `WaterBlock`, `LavaBlock` là `LiquidBlock`; `OakLogBlock`, `SpruceLogBlock` là `LogBlock`; `OakLeavesBlock`, `SpruceLeavesBlock` là `LeavesBlock`
    - Toàn bộ 33 ID `0..32` giữ nguyên ánh xạ `Block.fromId((byte) id)`.

- [x] **Step 2: Chạy test xác nhận fail trước khi sửa**
  - Run: `.\gradlew.bat check`
  - Expected: FAIL (biên dịch lỗi do chưa có các lớp trung gian và `Block` vẫn kế thừa `Entity`).

- [x] **Step 3: Tách `Block` khỏi `Entity`, tạo 5 lớp trừu tượng trung gian và cập nhật các lớp `*Block`**
  - `Block.java`: bỏ `extends Entity`, bỏ `super(0.5f, 1.0f)`, bỏ 2 hàm rỗng `update` và `render`.
  - `Entity.java`: thêm `implements Tickable, Renderable`, xóa constructor `Entity(float halfWidth, float height)`, thêm `@Override public void tick(World world, float deltaTime) { update(world, deltaTime); }`.
  - `SolidBlock`: `protected SolidBlock(byte id, String name, float hardness, int uniformTile, float[] iconColor)` và nạp chồng cho khối nhiều mặt.
  - `GravityBlock extends SolidBlock`: ghi đè `isGravityBlock() -> true` → áp dụng cho `SandBlock`, `GravelBlock`.
  - `LiquidBlock extends Block`: gom chung `solid=false, liquid=true, opaque=false`, `getTopHeight(...)` hạ mặt thoáng `0.88f`, `onBreak` không rơi item → áp dụng cho `WaterBlock`, `LavaBlock`.
  - `LogBlock extends SolidBlock`: gom logic 2 tile `top/bottom` và `side` → áp dụng cho `OakLogBlock`, `SpruceLogBlock`.
  - `LeavesBlock extends Block`: gom `RenderLayer.CUTOUT`, `opaque=false`, cờ `PERSISTENT_LEAF_STATE` → áp dụng cho `OakLeavesBlock`, `SpruceLeavesBlock`.
  - `DirtBlock`, `StoneBlock`, `BedrockBlock`, `SandstoneBlock`, `SnowBlock`, `WoolBlock`, `GrassBlock`, `DecorativeChestBlock` kế thừa `SolidBlock`.
  - `LeafDecaySystem` và `LiquidFlowSimulator` triển khai `Tickable`.

- [x] **Step 4: Chạy toàn bộ kiểm thử xác nhận xanh 100%**
  - Run: `.\gradlew.bat check`
  - Expected: `BUILD SUCCESSFUL` và pass cả 27 bộ cũ + `OopArchitectureChecks`.

- [x] **Step 5: Commit**
  - Run: `git add -A && git commit -m "refactor(block): decouple Block from Entity and add abstract block hierarchy"`

---

### Task 3: Tái cấu trúc Trụ cột #2 & #3 — Hệ phân cấp `Biome` (5 lớp con) và `Weather` (3 lớp con, State/Strategy)

**Files:**
- Create: `src/main/java/com/voxelgame/world/level/biome/Biome.java`
- Create: `src/main/java/com/voxelgame/world/level/biome/PlainsBiome.java`
- Create: `src/main/java/com/voxelgame/world/level/biome/ForestBiome.java`
- Create: `src/main/java/com/voxelgame/world/level/biome/FlowerForestBiome.java`
- Create: `src/main/java/com/voxelgame/world/level/biome/DesertBiome.java`
- Create: `src/main/java/com/voxelgame/world/level/biome/SnowyPlainsBiome.java`
- Modify: `src/main/java/com/voxelgame/world/level/biome/BiomeType.java`
- Modify: `src/main/java/com/voxelgame/world/level/biome/BiomeTint.java`
- Modify: `src/main/java/com/voxelgame/world/entity/mob/MobSpawnTable.java`
- Create: `src/main/java/com/voxelgame/world/level/weather/Weather.java`
- Create: `src/main/java/com/voxelgame/world/level/weather/ClearWeather.java`
- Create: `src/main/java/com/voxelgame/world/level/weather/RainWeather.java`
- Create: `src/main/java/com/voxelgame/world/level/weather/SnowWeather.java`
- Modify: `src/main/java/com/voxelgame/world/level/weather/WeatherManager.java`
- Modify: `src/test/java/com/voxelgame/engine/OopArchitectureChecks.java`

**Interfaces:**
- Produces:
  - `public abstract class Biome`: `getType()`, `getDisplayName()`, `getGrassColorRgb()`, `getFoliageColorRgb()`, `hasRain()`, `hasSnow()`, `isDry()`, `resolvePrecipitationWeather()`, `getSpawnTable(MobCategory, SnowSubZone)`, `public static Biome of(BiomeType)`.
  - `BiomeType.toBiome()` trả về singleton `Biome` tương ứng (O(1), zero-GC).
  - `public abstract class Weather`: `getLabel()`, `targetSkyStrength(float globalStrength)`, `targetRainGain(float globalStrength)`, `extinguishesFire()`, `accumulatesSnow()`. Singleton: `ClearWeather.INSTANCE`, `RainWeather.INSTANCE`, `SnowWeather.INSTANCE`.
  - `WeatherManager.currentWeather()` trả về `Weather` hiện hành theo biome & cường độ.

- [x] **Step 1: Bổ sung `checkBiomeAndWeatherHierarchy()` vào `OopArchitectureChecks.java`**
  - Kiểm tra:
    - `Modifier.isAbstract(Biome.class.getModifiers())` và `Modifier.isAbstract(Weather.class.getModifiers())`.
    - Đủ 5 lớp con `Biome` ánh xạ 1-1 với `BiomeType.values()`, gọi `Biome.of(type) == type.toBiome()` (singleton zero-GC).
    - Màu cỏ/lá của từng lớp con khớp chính xác bảng chuẩn (`PLAINS: 0x91BD59/0x77AB2F`, `FOREST: 0x79C05A/0x59AE30`, `FLOWER_FOREST: 0x88BB67/0x6BA941`, `DESERT: 0xBFB755/0xAEA42A`, `SNOWY_PLAINS: 0x80B497/0x60A17B`).
    - `resolvePrecipitationWeather()`: `PLAINS`/`FOREST`/`FLOWER_FOREST` → `RainWeather.INSTANCE`; `SNOWY_PLAINS` → `SnowWeather.INSTANCE`; `DESERT` → `ClearWeather.INSTANCE`.
    - `WeatherManager.currentWeather()` chuyển đa hình đúng giữa `ClearWeather`, `RainWeather`, `SnowWeather` theo cường độ và biome.

- [x] **Step 2: Chạy test xác nhận fail trước khi cài đặt**
  - Run: `.\gradlew.bat check`
  - Expected: FAIL do chưa tồn tại `Biome`, `Weather` và các lớp con.

- [x] **Step 3: Cài đặt hệ phân cấp `Weather` và nâng cấp `WeatherManager`**
  - Viết `Weather`, `ClearWeather`, `RainWeather`, `SnowWeather` dạng Flyweight bất biến.
  - Cập nhật `WeatherManager`: lưu trường `private Weather currentWeather = ClearWeather.INSTANCE;`, trong `update(seconds, world, eye, player)` tính `currentWeather = strength > 0f ? biome.toBiome().resolvePrecipitationWeather() : ClearWeather.INSTANCE;` và dùng đa hình `currentWeather.targetSkyStrength(strength)`, `currentWeather.targetRainGain(strength)`, `currentWeather.getLabel()`.

- [x] **Step 4: Cài đặt hệ phân cấp `Biome` và nối `BiomeType`, `BiomeTint`, `MobSpawnTable`**
  - Viết `Biome` và 5 lớp con (`PlainsBiome`, `ForestBiome`, `FlowerForestBiome`, `DesertBiome`, `SnowyPlainsBiome`).
  - Trong `BiomeTint`: nạp `GRASS_COLORS`/`FOLIAGE_COLORS` trực tiếp từ `Biome.of(type).getGrassColorRgb()` / `getFoliageColorRgb()` (loại bỏ hardcode trùng lặp, giữ nguyên tốc độ tra mảng O(1) zero-GC).
  - Trong `MobSpawnTable`: `forBiome(biome, category, zone)` ủy quyền sang `Biome.of(biome).getSpawnTable(category, zone)`.
  - Trong `BiomeType`: ủy quyền `hasRain()`, `hasSnow()`, `isDry()` qua `toBiome()`.

- [x] **Step 5: Chạy toàn bộ kiểm thử xác nhận xanh 100%**
  - Run: `.\gradlew.bat check`
  - Expected: `BUILD SUCCESSFUL` (pass cả `EnvironmentChecks`, `MobSpawnTableChecks`, `TerrainGenerationChecks`, `OopArchitectureChecks`).

- [x] **Step 6: Commit**
  - Run: `git add -A && git commit -m "refactor(world): add polymorphic Biome and Weather class hierarchies"`

---

### Task 4: Tái cấu trúc Trụ cột #4 — Hệ phân cấp `Mob` (`abstract MobEntity`, `HostileMob`, `PassiveMob`, `NeutralMob`, `MountableMob`, `EquineMob`, `RangedMob`) & `Interactable`

**Files:**
- Create: `src/main/java/com/voxelgame/world/entity/mob/HostileMob.java`
- Create: `src/main/java/com/voxelgame/world/entity/mob/PassiveMob.java`
- Create: `src/main/java/com/voxelgame/world/entity/mob/NeutralMob.java`
- Create: `src/main/java/com/voxelgame/world/entity/mob/EquineMob.java`
- Modify: `src/main/java/com/voxelgame/world/entity/mob/MobEntity.java`
- Modify: `src/main/java/com/voxelgame/world/entity/mob/MountableMob.java`
- Modify: `src/main/java/com/voxelgame/world/entity/mob/RangedMob.java`
- Modify: 23 lớp loài mob trong `world/entity/mob/monster/` và `world/entity/mob/animal/`
- Modify: `src/test/java/com/voxelgame/engine/OopArchitectureChecks.java`

**Interfaces:**
- Consumes: `core.Interactable`, `world.item.Item`.
- Produces:
  - `public abstract class MobEntity extends LivingEntity implements MobVisual`
  - `public abstract class HostileMob extends MobEntity`
  - `public abstract class PassiveMob extends MobEntity`
  - `public abstract class NeutralMob extends MobEntity`
  - `public abstract class MountableMob extends MobEntity implements Interactable`
  - `public abstract class EquineMob extends MountableMob` (gom `randomHealth` và cơ chế chung của họ ngựa cho `Horse` và `Donkey`)
  - `public abstract class RangedMob extends HostileMob`
  - `Sheep`, `Cow`, `Goat`, `Wolf`, `MountableMob` triển khai `Interactable.onInteract(World, Player, Item)`.
  - `MobEntity.onPlayerInteract(World, Player)` giữ vai trò facade gọi Lượt 1 (đốt cháy bởi `Flint and Steel`) trước, sau đó nếu `this instanceof Interactable interactable` gọi `interactable.onInteract(world, player, heldItem)`.

- [x] **Step 1: Bổ sung `checkEntityAndMobHierarchy()` vào `OopArchitectureChecks.java`**
  - Kiểm tra:
    - `Modifier.isAbstract(...)` cho `Entity`, `LivingEntity`, `MobEntity`, `HostileMob`, `PassiveMob`, `NeutralMob`, `MountableMob`, `EquineMob`, `RangedMob`, `ProjectileEntity`.
    - `HostileMob` là cha của `Zombie`, `ZombieVillager`, `Husk`, `Creeper`, `RangedMob`, `Skeleton`, `Stray`, `Parched`, `Witch`.
    - `PassiveMob` là cha của `Cow`, `Sheep`, `Pig`, `Chicken`, `Rabbit`.
    - `NeutralMob` là cha của `Spider`, `Enderman`, `Goat`, `PolarBear`, `Wolf`, `SnowFox`.
    - `EquineMob` là cha của `Horse` và `Donkey`; đồng thời `!Horse.class.isAssignableFrom(Donkey.class)` (đảm bảo LSP).
    - `MountableMob` là cha của `EquineMob`, `Camel`, `CamelHusk`.
    - `Interactable.class.isAssignableFrom(...)` đúng cho `Sheep`, `Cow`, `Goat`, `Wolf`, `MountableMob` và `false` cho `Zombie`, `Creeper`.

- [x] **Step 2: Chạy test xác nhận fail**
  - Run: `.\gradlew.bat check`
  - Expected: FAIL do chưa có các lớp trừu tượng trung gian.

- [x] **Step 3: Tạo `HostileMob`, `PassiveMob`, `NeutralMob`, `EquineMob` và chuyển `MobEntity` thành `abstract`**
  - Chuyển `MobEntity`, `MountableMob`, `RangedMob` sang `public abstract class`; cho `RangedMob extends HostileMob`, `EquineMob extends MountableMob` (chuyển helper chung `randomHealth` từ `Horse` sang `EquineMob`).
  - Cập nhật `extends` của 23 loài mob về đúng lớp cha trừu tượng theo bảng trên.
  - Triển khai `Interactable.onInteract(World, Player, Item)` trên `Sheep`, `Cow`, `Goat`, `Wolf`, `MountableMob` và nối với facade `MobEntity.onPlayerInteract(World, Player)` (bảo toàn 100% ưu tiên `Flint and Steel` đốt mọi mob trước khi cắt/vắt sữa/thuần hóa/lên yên).

- [x] **Step 4: Chạy toàn bộ kiểm thử xác nhận xanh 100%**
  - Run: `.\gradlew.bat check`
  - Expected: `BUILD SUCCESSFUL` (toàn bộ mob tests + `OopArchitectureChecks` pass).

- [x] **Step 5: Commit**
  - Run: `git add -A && git commit -m "refactor(mob): build abstract Mob hierarchy and Interactable dispatch"`

---

### Task 5: Tái cấu trúc Trụ cột #5 — Hệ phân cấp `Item` đa hình & dọn `ItemUseHandler`

**Files:**
- Modify: `src/main/java/com/voxelgame/world/item/Item.java`
- Create: `src/main/java/com/voxelgame/world/item/BlockItem.java`
- Create: `src/main/java/com/voxelgame/world/item/SimpleItem.java`
- Create: `src/main/java/com/voxelgame/world/item/ConsumableItem.java`
- Create: `src/main/java/com/voxelgame/world/item/FoodItem.java`
- Create: `src/main/java/com/voxelgame/world/item/PotionItem.java`
- Create: `src/main/java/com/voxelgame/world/item/BowItem.java`
- Create: `src/main/java/com/voxelgame/world/item/SpearItem.java`
- Create: `src/main/java/com/voxelgame/world/item/ThrowableItem.java`
- Create: `src/main/java/com/voxelgame/world/item/ToolItem.java`
- Create: `src/main/java/com/voxelgame/world/item/ShearsItem.java`
- Create: `src/main/java/com/voxelgame/world/item/FlintAndSteelItem.java`
- Create: `src/main/java/com/voxelgame/world/item/BucketItem.java`
- Create: `src/main/java/com/voxelgame/world/item/GoatHornItem.java`
- Modify: `src/main/java/com/voxelgame/world/item/FoodRegistry.java`
- Modify: `src/main/java/com/voxelgame/world/item/ItemUseHandler.java`
- Modify: `src/main/java/com/voxelgame/world/entity/mob/MobEntity.java` (gọi `heldItem.onInteractMob(...)` ở Lượt 1)
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (gọi `heldItem.getMiningSpeedMultiplier(block)` thay cho kiểm tra cứng `Item.SHEARS`)
- Modify: `src/test/java/com/voxelgame/engine/OopArchitectureChecks.java`

**Interfaces:**
- Consumes: `world.level.block.Block`, `world.entity.mob.MobEntity`, `world.entity.player.Player`, `world.effect.StatusEffectType`.
- Produces:
  - `public abstract class Item` với các phương thức đa hình:
    - `public UseAction getUseAction()` (`NONE`, `BOW`, `SPEAR`, `EAT`, `DRINK`)
    - `public float getUseDurationSeconds()`
    - `public float getMeleeReach()`, `public float getMeleeDamage()`, `public float getMeleeCooldown()`
    - `public float getMiningSpeedMultiplier(Block block)`
    - `public boolean onInteractMob(World world, Player player, MobEntity target)`
    - `public boolean onInstantUse(World world, Player player, Camera camera, ItemUseHandler handler)`
    - `public void onConsumeComplete(World world, Player player)`
  - Giữ nguyên 100% tên hằng số `Item.*`, `Item.allStandaloneItems().size() == 56`, `Item.of(Block)`, `Item.*_WOOL ↔ Block.*_WOOL`, và `FoodRegistry.count() == 19`.

- [x] **Step 1: Bổ sung `checkItemHierarchyAndDispatch()` vào `OopArchitectureChecks.java`**
  - Kiểm tra:
    - `Modifier.isAbstract(...)` cho `Item`, `ConsumableItem`, `ToolItem`.
    - `Item.BOW instanceof BowItem`, `Item.IRON_SPEAR instanceof SpearItem`, `Item.ENDER_PEARL instanceof ThrowableItem`, `Item.EGG instanceof ThrowableItem`, `Item.SHEARS instanceof ShearsItem`, `Item.FLINT_AND_STEEL instanceof FlintAndSteelItem`, `Item.BUCKET instanceof BucketItem`, `Item.GOAT_HORN instanceof GoatHornItem`.
    - Đủ 19 thức ăn đều `instanceof FoodItem` và khớp 100% thông số với `FoodRegistry.of(item)`.
    - `Item.POTION_HEALING`, `POTION_FIRE_RESISTANCE`, `POTION_WATER_BREATHING`, `POTION_SWIFTNESS`, `MILK_BUCKET` đều `instanceof PotionItem`.
    - `Item.WHITE_WOOL instanceof BlockItem` và `Item.of(Block.STONE) instanceof BlockItem`.
    - `Item.SHEARS.getMiningSpeedMultiplier(Block.WHITE_WOOL) == 5.0f` và `Item.SHEARS.getMiningSpeedMultiplier(Block.OAK_LEAVES) == 5.0f` và `Item.STONE` trả `1.0f`.
    - `Item.allStandaloneItems().size() == 56`.

- [x] **Step 2: Chạy test xác nhận fail**
  - Run: `.\gradlew.bat check`
  - Expected: FAIL do `Item` vẫn là `final class` chưa có các lớp con.

- [x] **Step 3: Chuyển `Item` thành `abstract class` và tạo đầy đủ 13 lớp con**
  - Lưu ý giữ nguyên **chính xác thứ tự khai báo 56 standalone items** trong `Item.java` để `nextAtlasTileIndex` không bị lệch UV trên `ItemTextureAtlas`!
  - `FoodItem` chứa `FoodRegistry.FoodSpec` trực tiếp và `FoodRegistry` đọc/đăng ký trực tiếp từ danh sách `FoodItem` (nguồn chân lý duy nhất).
  - `PotionItem` tự định nghĩa hiệu ứng & vật phẩm hoàn trả (`GLASS_BOTTLE` hoặc `BUCKET`) trong `onConsumeComplete(World, Player)`.
  - `FlintAndSteelItem` ghi đè `onInteractMob(World, Player, MobEntity)` đốt cháy mọi `MobEntity` 8s + phát âm `IGNITE`.
  - `ShearsItem` ghi đè `getMiningSpeedMultiplier(Block)` trả `5.0f` cho `LeavesBlock` và `WoolBlock`.
  - `ThrowableItem` dùng `@FunctionalInterface ProjectileFactory` tạo `ThrownEnderPearlEntity` / `ThrownEggEntity`.
  - `GoatHornItem` ghi đè `onInstantUse` gọi cooldown `7.0s` và phát SFX `GOAT_HORN`.

- [x] **Step 4: Tái cấu trúc `ItemUseHandler`, `MobEntity.onPlayerInteract` và `VoxelGameApp` gọi đa hình qua `Item`**
  - Thay chuỗi `if (item == Item.BOW) ... else if (item == Item.IRON_SPEAR) ... else if (FoodRegistry.isFood(item)) ...` bằng `switch (item.getUseAction())` và các lời gọi `item.onInstantUse(...)`, `item.onConsumeComplete(...)`, `item.getMeleeReach()`, `item.getMeleeDamage()`, `item.getMeleeCooldown()`.
  - Bảo toàn 100% mảng tĩnh & vector đệm zero-GC trong `ItemUseHandler`.

- [x] **Step 5: Chạy toàn bộ kiểm thử xác nhận xanh 100%**
  - Run: `.\gradlew.bat check`
  - Expected: `BUILD SUCCESSFUL` (pass `CombatItemsChecks`, `MobLootChecks`, `VoxelRegressionChecks`, `OopArchitectureChecks`).

- [x] **Step 6: Commit**
  - Run: `git add -A && git commit -m "refactor(item): convert Item to polymorphic class hierarchy and simplify ItemUseHandler"`

---

### Task 6: Tái cấu trúc tầng Client/GUI (`Screen`, `BitmapFont`, `HudRenderer`, `PauseScreen`, `InventoryScreen`, `UIRenderer` Facade)

**Files:**
- Create: `src/main/java/com/voxelgame/client/gui/Screen.java`
- Create: `src/main/java/com/voxelgame/client/gui/BitmapFont.java`
- Create: `src/main/java/com/voxelgame/client/gui/HudRenderer.java`
- Create: `src/main/java/com/voxelgame/client/gui/PauseScreen.java`
- Rename/Upgrade: `src/main/java/com/voxelgame/client/gui/InventoryScreenRenderer.java` → `src/main/java/com/voxelgame/client/gui/InventoryScreen.java`
- Modify: `src/main/java/com/voxelgame/client/gui/UIRenderer.java` (rút gọn thành Facade kết nối `HudRenderer` và `PauseScreen`)
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` và các file test tham chiếu `InventoryScreenRenderer`
- Modify: `src/test/java/com/voxelgame/engine/OopArchitectureChecks.java`

**Interfaces:**
- Produces:
  - `public abstract class Screen implements Disposable`: `isOpen()`, `open()`, `close()`, `pausesWorld()`, `dispose()`.
  - `public final class BitmapFont`: bảng `GLYPHS` 5×7 dùng chung duy nhất (xóa bản sao trùng lặp giữa `UIRenderer` và `InventoryScreen`).
  - `public class PauseScreen extends Screen` (`pausesWorld() -> true`, chứa toàn bộ logic hit-test nút Resume, `-/+`, thanh âm lượng và vẽ khung Pause Menu).
  - `public class InventoryScreen extends Screen` (`pausesWorld() -> false`, bảo toàn 100% kéo-thả 36 ô, Shift+Click, `getSlotAt`, fallback không OpenGL).
  - `public class HudRenderer implements Disposable` (Crosshair, Hotbar 9 ô, Vitals/Oxy/Mount HP, Nhãn đồng hồ/thời tiết/hiệu ứng, Overlay lửa/cactus, Overlay 3D block mục tiêu).
  - `UIRenderer`: Facade mỏng ủy quyền về `HudRenderer` và `PauseScreen` (giữ nguyên toàn bộ API công khai hiện có).

- [x] **Step 1: Bổ sung `checkClientScreenHierarchy()` vào `OopArchitectureChecks.java`**
  - Kiểm tra:
    - `Modifier.isAbstract(Screen.class.getModifiers())`
    - `PauseScreen` và `InventoryScreen` đều kế thừa `Screen`
    - `new PauseScreen().pausesWorld() == true` và `new InventoryScreen().pausesWorld() == false`
    - `BitmapFont` cung cấp glyph hợp lệ cho chữ cái, chữ số và ký tự đặc biệt dùng trên HUD.

- [x] **Step 2: Chạy test xác nhận fail**
  - Run: `.\gradlew.bat check`
  - Expected: FAIL do chưa có `Screen`, `BitmapFont`, `PauseScreen`, `InventoryScreen`, `HudRenderer`.

- [x] **Step 3: Tách `BitmapFont`, `Screen`, `PauseScreen`, `HudRenderer`, nâng cấp `InventoryScreen` và rút gọn `UIRenderer`**
  - Tách bảng `GLYPHS` 5×7 và phương thức đo chiều rộng/dựng đỉnh chuỗi sang `BitmapFont` (zero-GC).
  - Tách phần Pause Menu (layout, hit-test nút Resume/Volume, render) sang `PauseScreen extends Screen`.
  - Tách phần HUD 2D & Target Block Overlay 3D sang `HudRenderer`.
  - Đổi tên/nâng cấp `InventoryScreenRenderer` thành `InventoryScreen extends Screen` (dùng chung `BitmapFont`), cập nhật tham chiếu ở `VoxelGameApp` và test `SurvivalWorldChecks`/`VisualsLightingChecks` (nếu có).
  - Rút gọn `UIRenderer` làm Facade mỏng gọi `HudRenderer` và `PauseScreen`, bảo toàn 100% bố cục pixel và cơ chế `dispose()`.

- [x] **Step 4: Chạy toàn bộ kiểm thử xác nhận xanh 100%**
  - Run: `.\gradlew.bat check`
  - Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit**
  - Run: `git add -A && git commit -m "refactor(client): extract Screen hierarchy, BitmapFont, HudRenderer, and PauseScreen"`

---

### Task 7: Tách `GameInputController` khỏi `VoxelGameApp`

**Files:**
- Create: `src/main/java/com/voxelgame/client/input/GameInputController.java`
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java`

**Interfaces:**
- Consumes: `VoxelGameApp` (hoặc callback/trạng thái ứng dụng), `PauseScreen`, `InventoryScreen`, `Player`, `World`, `ItemUseHandler`, `WeatherManager`, `WorldTime`, `DebugOverlayRenderer`.
- Produces:
  - `public class GameInputController implements InputProcessor`: gom toàn bộ xử lý bàn phím (`E`, `ESC`, `Q`/`Ctrl+Q`, `F1`, `F3`, `F5..F8`, `Ctrl+C`, `1..9`), chuột (cuộn, bấm/kéo/nhả ở PauseScreen, InventoryScreen, và trong game) và điều phối tương tác đào/đặt/đánh.
  - `VoxelGameApp` vẫn `implements InputProcessor` (ủy quyền trực tiếp sang `inputController`), giữ đồng bộ trường `player` và `isPaused` để tương thích 100% với reflection trong `VoxelRegressionChecks.checkTerrainSpeedShortcut()`.

- [ ] **Step 1: Tạo `GameInputController` và tách logic đầu vào ra khỏi `VoxelGameApp`**
  - Di chuyển toàn bộ trạng thái phím (`leftControlDown`, `rightControlDown`, `nextBiomeIndex`, `isDraggingVolumeSlider`, trạng thái giữ chuột đào block) và các hàm `keyDown`, `keyUp`, `touchDown`, `touchUp`, `touchDragged`, `mouseMoved`, `scrolled`, `teleportToNextBiome` sang `GameInputController`.
  - `VoxelGameApp` khởi tạo `GameInputController`, ủy quyền các phương thức `InputProcessor` (tự khởi tạo lười nếu test gọi `keyDown` trước `create()`, giống cách `checkTerrainSpeedShortcut` khởi tạo `new VoxelGameApp()` không gọi `create()`!).

- [ ] **Step 2: Chạy toàn bộ kiểm thử xác nhận xanh 100% (bao gồm `checkTerrainSpeedShortcut`)**
  - Run: `.\gradlew.bat check`
  - Expected: `BUILD SUCCESSFUL` và pass `checkTerrainSpeedShortcut` cùng toàn bộ 28 bộ kiểm thử.

- [ ] **Step 3: Commit**
  - Run: `git add -A && git commit -m "refactor(client): extract GameInputController from VoxelGameApp"`

---

### Task 8: Đồng bộ `README.md` và viết tài liệu thuyết trình `OOP.md` & `DIAGRAM.md`

**Files:**
- Modify: `README.md` (cập nhật Phần A Mục 1 & Mục 2 cùng các tham chiếu lớp/package bị ảnh hưởng)
- Create: `OOP.md` (tài liệu tổng hợp 4 tính chất OOP phục vụ thuyết trình — chỉ tập trung `Entity`, `Mob`, `Item`, `Block`, `Biome`, `Weather`, **không đưa phần Engine/Client**)
- Create: `DIAGRAM.md` (sơ đồ kiến trúc package tổng quát chuẩn Minecraft + sơ đồ lớp Mermaid của toàn bộ dự án)

**Interfaces:**
- Produces:
  - `README.md` Phần A phản ánh chính xác 100% số lượng file Java, cấu trúc package `core`/`world.*`/`client.*`, hệ phân cấp lớp mới và 28 bộ kiểm thử CPU.
  - `OOP.md`: cấu trúc mạch lạc 4 chương tương ứng 4 tính chất OOP (**1. Đóng gói**, **2. Kế thừa**, **3. Trừu tượng**, **4. Đa hình**) kèm bảng tổng hợp và dẫn chứng chính xác tên lớp/phương thức trong `Block`, `Entity`, `Mob`, `Item`, `Biome`, `Weather` (kèm mục điểm nhấn sửa lỗi LSP tách `Block` khỏi `Entity` và `EquineMob`); tuyệt đối không đưa chi tiết Engine/Render/OpenGL.
  - `DIAGRAM.md`: chứa các sơ đồ Mermaid hợp lệ (`flowchart TD` cho kiến trúc package tổng quát; `classDiagram` cho `Block`, `Entity & Mob`, `Item`, `Biome & Weather`, và tổng quan `Client/GUI`).

- [ ] **Step 1: Rà soát số file thực tế và cập nhật Phần A của `README.md`**
  - Đếm chính xác số file `.java` trong `src/main/java` và `src/test/java` bằng lệnh PowerShell trước khi ghi vào `README.md`.
  - Cập nhật Mục 1 (Kiểm thử: 28 file gồm `OopArchitectureChecks`) và Mục 2 (Kiến trúc mã nguồn mới theo chuẩn Minecraft).

- [ ] **Step 2: Viết `OOP.md` (tập trung 4 tính chất OOP trên Domain, không kèm Engine)**
  - Viết rõ ràng, chuẩn thuật ngữ OOP bằng tiếng Việt, dễ chuyển thẳng thành slide thuyết trình.

- [ ] **Step 3: Viết `DIAGRAM.md` (sơ đồ tổng quát dự án bằng Mermaid)**
  - Đảm bảo cú pháp Mermaid chuẩn (không dùng ký tự gây lỗi parser trong nhãn).

- [ ] **Step 4: Chạy kiểm thử sạch toàn dự án lần cuối**
  - Run: `.\gradlew.bat clean check`
  - Expected: `BUILD SUCCESSFUL` và thông báo `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit hoàn tất**
  - Run: `git add -A && git commit -m "docs: sync README and add OOP.md and DIAGRAM.md for presentation"`
