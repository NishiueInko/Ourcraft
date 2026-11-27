# Thiết Kế Tái Cấu Trúc Hướng Đối Tượng Theo Kiến Trúc Minecraft — VoxelGame

**Ngày lập:** 2026-11-27  
**Phạm vi:** Dự án `VoxelGame` (`com.voxelgame`, Java 17, libGDX 1.13.1, 128 file nguồn chính + 27 file kiểm thử CPU)  
**Hướng đã chọn:** **Hướng C** — Tái cấu trúc OOP toàn diện cả tầng World (Domain) lẫn tầng Client/Engine theo kiến trúc chuẩn Minecraft Java Edition.

---

## 1. Mục Đích & Lý Do (Intent & Why)

Dự án **VoxelGame** được dùng để **thuyết trình đồ án môn Lập trình Hướng đối tượng (OOP)**. Người trình bày cần một kiến trúc:
1. **Làm nổi bật rõ ràng 4 tính chất cốt lõi của OOP** (Đóng gói, Kế thừa, Trừu tượng, Đa hình) trên các nhóm đối tượng tự nhiên có nhiều biến thể: **Block, Entity, Mob, Item, Biome, Weather**.
2. **Tuân theo cấu trúc phân tầng & phân gói của Minecraft Java Edition** (`world.*` tách biệt trách nhiệm logic thế giới khỏi `client.*` đồ họa/giao diện/âm thanh), giúp kiến trúc mang tính thực tiễn cao và giữ nguyên cơ chế chạy headless an toàn cho 27 bộ kiểm thử thuần CPU mà không cần viết lại lõi kết xuất OpenGL.
3. **An toàn khi giảng viên hỏi sâu sang phần Client/Engine:** Các lớp lớn ở phía Client (`UIRenderer`, `VoxelGameApp`, `InventoryScreenRenderer`) cũng được phân tách gọn gàng bằng các mô hình OOP dễ trình bày (`abstract Screen`, `BitmapFont`, `HudRenderer`, `GameInputController`, `Tickable`, `Renderable`), không để lại God class khó giải thích.

---

## 2. Mục Tiêu, Phi Mục Tiêu & Phản Mục Tiêu

### Mục tiêu (Goals)
- Tổ chức lại 100% mã nguồn theo cây package chuẩn Minecraft (`core`, `world.*`, `client.*`).
- Khắc phục các lỗi thiết kế OOP hiện có:
  - Tách `Block` ra khỏi `Entity` (sửa vi phạm nguyên lý thay thế Liskov — LSP).
  - Chuyển `Item` từ `final class` + `if/else` trong `ItemUseHandler` sang hệ phân cấp đa hình `abstract class Item`.
  - Nâng cấp `Biome` từ `enum` thuần sang `abstract class Biome` + 5 lớp con cụ thể.
  - Nâng cấp `Weather` từ cờ `boolean` trong `WeatherManager` sang `abstract class Weather` + 3 lớp con (State/Strategy).
  - Biến `MobEntity` thành `abstract class` và phân lớp trung gian rõ ràng (`HostileMob`, `PassiveMob`, `NeutralMob`, `MountableMob`, `RangedMob`).
  - Làm sạch tầng Client bằng `abstract class Screen` (`PauseScreen`, `InventoryScreen`), tách `HudRenderer`, `BitmapFont` (xóa trùng lặp glyph 5×7), và tách `GameInputController` khỏi `VoxelGameApp`.
- Bổ sung bộ kiểm thử CPU mới `OopArchitectureChecks` xác nhận toàn bộ cây kế thừa và hành vi đa hình mới.
- Đồng bộ đầy đủ Phần A của `README.md` sau khi hoàn tất tái cấu trúc.

### Phi mục tiêu (Non-goals)
- Không thêm block mới, mob mới, biome mới hay tính năng gameplay mới ngoài các thay đổi tái cấu trúc.
- Không thay đổi thuật toán sinh địa hình (`TerrainGenerator`/`TerrainNoise`), ánh sáng BFS (`ChunkLighting`), thuật toán dựng mesh `Chunk`, hay shader GLSL 330.
- Không thay đổi cấu trúc thư mục tài nguyên trong `src/main/resources/` (`textures/`, `audio/`).

### Phản mục tiêu (Anti-goals — coi là thất bại nếu vi phạm)
- Làm thay đổi dù chỉ một hành vi gameplay, vật lý, âm thanh, địa hình hay giao diện đang được đặc tả trong `README.md`.
- Tạo rác bộ nhớ (GC churn) trong vòng lặp cập nhật/kết xuất (Game Loop) làm sụt giảm hiệu năng dưới 60 FPS hoặc rò rỉ `Mesh`/`Texture`/`Audio`.
- Làm hỏng bất kỳ ca nào trong 27 bộ kiểm thử hồi quy CPU (`./gradlew regressionChecks` / `./gradlew check`).
- Lược bỏ code hoặc để lại chú thích kiểu `// TODO`, `// giữ nguyên phần còn lại`.

---

## 3. Ràng Buộc Kỹ Thuật (Constraints)

1. **Nền tảng:** Java 17 (`options.release = 17`), biên dịch UTF-8, libGDX `1.13.1` (`gdx`, `gdx-backend-lwjgl3`, `natives-desktop`).
2. **Bảo toàn dữ liệu & định danh cố định:**
   - Giữ nguyên 33 mã ID block `0..32` (`Block.fromId(byte)` O(1), không đổi ID cũ).
   - Giữ nguyên 56 vật phẩm độc lập (`Item.allStandaloneItems().size() == 56`), liên kết 2 chiều `Item.*_WOOL ↔ Block.*_WOOL`, và `FoodRegistry.count() == 19` với đúng thông số dinh dưỡng/hiệu ứng.
   - Giữ nguyên seed thế giới `20261114L`, kích thước `4000 × 256 × 2048`, các ngưỡng thời tiết `600..9000s` (trời quang) và `600..1200s` (giáng thủy).
   - Giữ nguyên toàn bộ đường dẫn tài nguyên trong `src/main/resources/`.
3. **Zero-GC trên hot path:** Các lệnh gọi đa hình mới (`Item`, `Biome`, `Weather`, `Screen`, `Tickable`) **tuyệt đối không** cấp phát đối tượng mới mỗi khung hình; dùng singleton/Flyweight bất biến và tái sử dụng bộ đệm (`Vector3`, `float[]`, `StringBuilder`).
4. **Hợp đồng kiểm thử:** Toàn bộ 27 file kiểm thử hiện tại được cập nhật import/package đồng bộ và chạy qua lệnh `./gradlew check`. Riêng `VoxelGameApp` vẫn `implements InputProcessor` (ủy quyền logic sang `GameInputController`) và giữ 2 trường `player`, `isPaused` (hoặc cập nhật đồng bộ với `VoxelRegressionChecks.checkTerrainSpeedShortcut`).
5. **Quy tắc chú thích & tài liệu (`AGENTS.md`):** Chú thích tiếng Việt đầy đủ ở mọi class, hằng số, phương thức và thuật toán quan trọng; cập nhật Phần A của `README.md` phản ánh đúng 100% kiến trúc mới.

---

## 4. Các Hướng Tiếp Cận Đã Cân Nhắc (Approaches)

| Hướng | Mô tả ngắn gọn | Đánh đổi (Trade-offs) |
| --- | --- | --- |
| **Hướng A** — Chuẩn Minecraft + OOP sâu cho 5 trụ cột Domain | Chia `world.*` / `client.*`, tái cấu trúc OOP sâu cho Block, Entity/Mob, Item, Biome, Weather; phía Client chủ yếu chỉ chuyển package. | Cân bằng tốt giữa thuyết trình và rủi ro, nhưng nếu giảng viên mở `UIRenderer` (1.773 dòng) hoặc `VoxelGameApp` (1.499 dòng) vẫn còn God class lớn. |
| **Hướng B** — Sắp xếp Package chuẩn Minecraft + OOP nhẹ | Chuyển package, tách `Block` khỏi `Entity`, thêm vỏ bọc mỏng cho Biome/Weather; giữ `Item` là `final class` + `ItemUseHandler` như cũ. | Rủi ro thấp nhất và nhanh nhất, nhưng phần `Item` không có đa hình/kế thừa, làm yếu bài thuyết trình OOP. |
| **Hướng C *(Đã chọn)*** — Tái cấu trúc OOP toàn diện cả World lẫn Client/Engine | Thực hiện toàn bộ Hướng A, **cộng thêm** tách sạch tầng Client/Engine: `core` (`Tickable`, `Renderable`, `Interactable`), `abstract Screen` (`PauseScreen`, `InventoryScreen`), `HudRenderer`, `BitmapFont` dùng chung, và `GameInputController`. | Phạm vi chỉnh sửa lớn nhất và cần rà soát kỹ đường vẽ OpenGL, nhưng mang lại độ an toàn tối đa khi thuyết trình: mọi lớp từ World đến Client đều sạch, đúng chuẩn Minecraft và dễ giải thích khi bị hỏi vặn. |

**Lý do chọn Hướng C:** Người dùng ưu tiên độ an toàn cao nhất cho buổi thuyết trình OOP — cả phần trọng tâm (Entity, Mob, Item, Block, Biome, Weather) lẫn phần phụ (Client/Engine nếu giảng viên hỏi đến) đều có cấu trúc OOP rõ ràng, không còn God class hay lỗi thiết kế.

---

## 5. Kiến Trúc Tổng Thể Chuẩn Minecraft (`com.voxelgame.*`)

Dự án được tổ chức lại thành 3 nhánh rõ rệt lấy cảm hứng trực tiếp từ Minecraft Java Edition (`net.minecraft.*`):
- **`com.voxelgame.core`** — Các giao diện hợp đồng OOP dùng chung (`Tickable`, `Renderable`, `Interactable`).
- **`com.voxelgame.world`** — Toàn bộ tầng logic thế giới (tương đương *Logical Server* của Minecraft): Block, Biome, Weather, Terrain Gen, Item, Entity, Mob, AI, Projectile, Effect, Physics.
- **`com.voxelgame.client`** — Toàn bộ tầng hiển thị & tương tác người dùng (tương đương *Client* của Minecraft): Input, Render, Model, GUI (`Screen`/`Hud`), Audio.

> **Quyết định ranh giới phụ thuộc `world.*` ↔ `client.*` (đã chốt theo phạm vi bảo toàn lõi Engine):**  
> Dự án áp dụng **tách tầng theo trách nhiệm gói (package-level separation)** kết hợp cơ chế **headless-safe lúc chạy** (các lời gọi đồ họa/âm thanh chỉ kích hoạt khi có ngữ cảnh OpenGL/AudioManager nên 27 bài test CPU vẫn chạy không cần cửa sổ game). Không tách triệt để 0-import (không viết lại `WorldRenderer`/`ChunkRenderer`/`ItemEntityRenderer` hay tháo bỏ `Entity.render(ShaderProgram)`, `MobVisualState`, `Texture.TILE_*`, `AudioManager` khỏi `world.*`) để tránh rủi ro vỡ lõi OpenGL và giữ vững hiệu năng 60 FPS đã kiểm chứng.

```text
com.voxelgame
├── Main.java                                  # Điểm khởi chạy Desktop (LWJGL3)
├── VoxelGameApp.java                          # Điều phối vòng đời ứng dụng (Application Orchestrator)
│
├── core/
│   ├── Tickable.java                          # Interface cập nhật logic theo deltaTime
│   ├── Renderable.java                        # Interface kết xuất bằng ShaderProgram
│   └── Interactable.java                      # Interface tương tác chuột phải bởi Player
│
├── world/
│   ├── level/
│   │   ├── World.java                         # Quản lý Chunk, Entity, Time, Weather, Random Tick
│   │   ├── Chunk.java                         # Lưu voxel 16×16×16 và dựng mesh
│   │   ├── ChunkLighting.java                 # Ánh sáng Sky/Block Light 4-bit BFS
│   │   ├── WorldTime.java                     # Chu kỳ ngày/đêm 24.000 tick
│   │   ├── BlockUpdateQueue.java              # Hàng đợi FIFO cập nhật block lân cận
│   │   ├── LeafDecaySystem.java               # Phân hủy lá cây (implements Tickable)
│   │   ├── LiquidFlowSimulator.java           # Dòng chảy Water/Lava & tạo Stone (implements Tickable)
│   │   ├── TranslucentChunkSort.java          # Sắp xếp mặt trong suốt theo khoảng cách
│   │   ├── block/                             # Hệ phân cấp 33 Block (độc lập khỏi Entity)
│   │   ├── biome/                             # Hệ phân cấp 5 Biome + BiomeType + BiomeTint + SnowSubZone
│   │   ├── weather/                           # Hệ phân cấp 3 Weather + WeatherManager
│   │   └── gen/                               # TerrainNoise, TerrainGenerator, DesertPyramid
│   ├── item/                                  # Hệ phân cấp Item đa hình + FoodRegistry + ItemUseHandler
│   ├── entity/
│   │   ├── Entity.java, LivingEntity.java, ItemEntity.java, FallingBlockEntity.java, DamageSource.java
│   │   ├── player/                            # Player, PlayerInventory, Hotbar
│   │   ├── mob/
│   │   │   ├── MobEntity.java, HostileMob.java, PassiveMob.java, NeutralMob.java, MountableMob.java, EquineMob.java, RangedMob.java
│   │   │   ├── MobType.java, MobCategory.java, MobAttitude.java, MobLootTable.java, MobSpawner.java, MobSpawnTable.java
│   │   │   ├── ai/                            # MobGoal, MobGoalSelector, MobGoals, SimpleGridNavigator
│   │   │   ├── monster/                       # Zombie, ZombieVillager, Husk, Skeleton, Stray, Parched, Creeper, Spider, Enderman, Witch, CamelHusk
│   │   │   └── animal/                        # Cow, Sheep, Pig, Chicken, Rabbit, Horse, Donkey, Camel, Goat, PolarBear, Wolf, SnowFox
│   │   └── projectile/                        # ProjectileEntity, ArrowEntity, ArrowType, ThrownPotionEntity, PotionType, ThrownEnderPearlEntity, ThrownEggEntity
│   ├── effect/                                # StatusEffectType, StatusEffectInstance
│   └── physics/                               # Raycast
│
└── client/
    ├── input/                                 # GameInputController
    ├── render/
    │   ├── Camera.java, ShaderProgram.java, Texture.java, ItemTextureAtlas.java
    │   ├── EnvironmentMesh.java, SkyRenderer.java, WeatherRenderer.java
    │   ├── BlockOutlineRenderer.java, FirstPersonRenderer.java, LiquidAnimator.java, ParticleManager.java
    │   └── entity/                            # MobRenderer, MobAppearance, MobTextureKey, MobTextureManager, MobVisual, MobVisualState
    ├── model/                                 # ModelPart, MobModel, MobModels
    ├── gui/                                   # Screen, BitmapFont, GuiTextureAtlas, HudRenderer, PauseScreen, InventoryScreen, DebugOverlayRenderer, UIRenderer
    └── audio/                                 # AudioManager, MobSoundBank, MobSoundEvent
```

---

## 6. Thiết Kế Chi Tiết Từng Trụ Cột OOP

### 6.1. Hợp đồng cốt lõi (`com.voxelgame.core`)
- **`Tickable`:** `void tick(World world, float deltaTime)` — chuẩn hóa vòng cập nhật cho hệ thống thế giới (`LeafDecaySystem`, `LiquidFlowSimulator`) và thực thể (`Entity` triển khai `tick` ủy quyền sang `update(World, float)` hoặc ngược lại, giữ tương thích chữ ký cũ `update(World, float)`).
- **`Renderable`:** `void render(ShaderProgram shader)` — chuẩn hóa khả năng kết xuất cho `Entity` và các bộ vẽ tương thích.
- **`Interactable`:** `boolean onInteract(World world, Player player, Item heldItem)` — chuẩn hóa tương tác chuột phải đặc thù của từng loài thực thể (`Sheep` cắt len, `Cow`/`Goat` vắt sữa, `Wolf` thuần hóa & ngồi/đứng, `MountableMob` lên yên).
  - **Quy tắc phân quyền & thứ tự điều phối tương tác Mob (đã chốt):**  
    1. **Lượt 1 — Hành vi công cụ phổ quát phía `Item` (`Item.onInteractMob(World, Player, MobEntity)`):** Dành cho vật phẩm tác động lên *mọi* loài mob bất kể mob đó có triển khai `Interactable` hay không — cụ thể là `FlintAndSteelItem` đốt cháy bất kỳ `MobEntity` nào trong 8 giây (giữ đúng thứ tự ưu tiên trước khi cưỡi/cho ăn/cắt len, kể cả khi châm lửa `CamelHusk` hay `Zombie`).  
    2. **Lượt 2 — Hành vi đặc thù phía Mob (`Interactable.onInteract(World, Player, Item)`):** Nếu Lượt 1 trả `false` và mob triển khai `Interactable`, mob tự xử lý tương tác của mình (`Sheep` kiểm tra `heldItem instanceof ShearsItem`, `Cow`/`Goat` kiểm tra `heldItem instanceof BucketItem`, `Wolf` kiểm tra `Item.BONE`, `MountableMob` cho người chơi lên yên). Nhờ đó `ToolItem` không phải viết `instanceof Sheep`/`instanceof Cow`, còn `Interactable` thực sự nắm logic đặc thù từng loài.  
    3. **Facade tương thích test:** `MobEntity.onPlayerInteract(World, Player)` giữ nguyên chữ ký công khai, gọi tuần tự Lượt 1 (`heldItem.onInteractMob(...)`) rồi Lượt 2 (`Interactable.onInteract(...)`), bảo toàn 100% các bài test hồi quy gọi trực tiếp `mob.onPlayerInteract(world, player)`.

### 6.2. Trụ cột #1 — Hệ phân cấp Block (`com.voxelgame.world.level.block`)
- **Sửa lỗi OOP trọng yếu:** Loại bỏ `extends Entity` khỏi `Block`. Xóa bỏ 2 hàm rỗng giả (`update`, `render`) vốn chỉ tồn tại để lấp chữ ký của `Entity`. Đồng thời xóa constructor nạp chồng `protected Entity(float halfWidth, float height)` chỉ dùng cho `Block` trong `Entity.java`.
- **Phân tầng kế thừa sâu & có ý nghĩa thực tế:**
  - `abstract class Block` (Flyweight singleton registry `BY_ID[256]`, giữ nguyên ID `0..32`, đóng gói `private final` toàn bộ thông số bất biến, defensive copy cho `iconColor`, Template Method cho `onNeighborChange` / `onBreak` / `getFaceUV`).
  - Các lớp trừu tượng trung gian gom hành vi chung (giảm lặp code ở các lớp lá):
    - `abstract class SolidBlock extends Block`: mặc định `solid = true, liquid = false, opaque = true`, nhận 1 tile chung cho 6 mặt (dùng cho `DirtBlock`, `StoneBlock`, `BedrockBlock`, `SandstoneBlock`, `SnowBlock`, `WoolBlock`, và có thể ghi đè mặt riêng như `GrassBlock`, `DecorativeChestBlock`).
    - `abstract class GravityBlock extends SolidBlock`: ghi đè `isGravityBlock() -> true` (`SandBlock`, `GravelBlock`).
    - `abstract class LiquidBlock extends Block`: dùng chung thuộc tính `solid = false, liquid = true, opaque = false`, `getTopHeight(...) -> 0.88f` khi phía trên không cùng chất lỏng, `onBreak` không rơi item (`WaterBlock`, `LavaBlock`).
    - `abstract class LogBlock extends SolidBlock`: gom logic 2 tile `top/bottom` và `side` (`OakLogBlock`, `SpruceLogBlock`).
    - `abstract class LeavesBlock extends Block`: gom logic `RenderLayer.CUTOUT`, `opaque = false`, metadata `PERSISTENT_LEAF_STATE` (`OakLeavesBlock`, `SpruceLeavesBlock`).
    - `abstract class PlantBlock extends Block`: giữ nguyên vai trò nền cho thực vật chữ X khoét alpha (`ShortGrassBlock`, `TallGrassBlock`, `FlowerBlock`, `DeadBushBlock`, `SugarCaneBlock`).
  - Các khối đặc thù giữ kế thừa trực tiếp từ `Block` hoặc `SolidBlock`: `AirBlock`, `CactusBlock`, `SnowLayerBlock`, `IceBlock`.

### 6.3. Trụ cột #2 — Hệ phân cấp Entity, Mob & AI (`com.voxelgame.world.entity.*`)
- **Chuỗi kế thừa gốc:**
  ```text
  abstract Entity (implements Tickable, Renderable)
  ├── ItemEntity
  ├── FallingBlockEntity
  ├── abstract ProjectileEntity
  │   ├── ArrowEntity
  │   ├── ThrownPotionEntity
  │   ├── ThrownEnderPearlEntity
  │   └── ThrownEggEntity
  └── abstract LivingEntity (HP, giáp, sát thương, hiệu ứng trạng thái, knockback, fallDistance)
      ├── Player (sở hữu PlayerInventory & Hotbar theo Composition)
      └── abstract MobEntity (implements MobVisual)
          ├── abstract HostileMob (quái thù địch chủ động: Zombie [→ ZombieVillager, Husk], Creeper)
          │   └── abstract RangedMob (Skeleton [→ Stray, Parched], Witch)
          ├── abstract PassiveMob (thú hiền lành hoảng loạn khi bị đánh: Cow, Sheep, Pig, Chicken, Rabbit)
          ├── abstract NeutralMob (trung lập, nổi giận khi bị kích động: Spider, Enderman, Goat, PolarBear, Wolf, SnowFox)
          └── abstract MountableMob (thú cưỡi yên/ghế, implements Interactable)
              ├── abstract EquineMob (nhóm họ ngựa dùng chung phân bố máu & cơ chế nhảy/cưỡi, giống AbstractHorse của Minecraft: Horse, Donkey — ngang hàng để giữ đúng LSP)
              ├── Camel
              └── CamelHusk
  ```
- **Điểm nhấn OOP:**
  - Chuyển `MobEntity` từ `public class` thành `public abstract class` (không thể khởi tạo một "con mob chung chung" không rõ loài).
  - Các lớp `HostileMob`, `PassiveMob`, `NeutralMob`, `MountableMob`, `EquineMob`, `RangedMob` là `public abstract class` để minh họa phân lớp nhiều tầng rõ ràng. Riêng `Horse` và `Donkey` cùng kế thừa `EquineMob` (thay vì `Donkey extends Horse`) vì `Horse` có bộ lông/hoa văn/tốc độ/độ nhảy ngẫu nhiên còn `Donkey` có thông số cố định — tránh bẫy vi phạm LSP nếu giảng viên vặn hỏi. Đồng thời triển khai `Interactable` ở các loài có tương tác chuột phải (`Sheep`, `Cow`, `Goat`, `Wolf`, `MountableMob`) nhằm làm nổi bật sự khác nhau giữa **kế thừa lớp trừu tượng ("is-a")** và **triển khai giao diện ("can-do")**.
  - Lưu ý kỹ thuật về `RangedMob`: Hiện `RangedMob` là package-private trong `entity.mob`. Khi tách loài vào `monster/` và `animal/`, các constructor/hook kế thừa cần thiết của `MobEntity`, `HostileMob`, `PassiveMob`, `NeutralMob`, `MountableMob`, `RangedMob` được đặt `protected` đúng chuẩn đóng gói OOP (bên ngoài gói không gọi bừa được, chỉ lớp con kế thừa được phép truy cập).
  - `MobType` vẫn đóng vai trò **Factory Pattern** (đăng ký `Factory` tạo 24 cấu hình loài).
  - Gói `world.entity.mob.ai` đóng gói trọn vẹn **Strategy Pattern** (`MobGoal`, `MobGoalSelector`, `MobGoals`, `SimpleGridNavigator`).

### 6.4. Trụ cột #3 — Hệ phân cấp Item đa hình (`com.voxelgame.world.item`)
- **Chuyển từ `final class Item` sang `abstract class Item`:**
  ```text
  abstract Item (Flyweight registry, giữ nguyên toàn bộ hằng số static Item.* & Item.of(Block))
  ├── BlockItem           # Bao bọc 32 Block đặt được (kể cả 6 loại WoolBlock)
  ├── SimpleItem          # Vật phẩm nguyên liệu / loot / mũi tên / đĩa nhạc không có hành vi dùng chủ động
  ├── abstract ConsumableItem  # Mẫu Template Method cho hành vi giữ chuột phải 1.6s (ăn/uống)
  │   ├── FoodItem        # 19 thức ăn: tự đóng gói nutrition, saturation, statusEffect, chance
  │   └── PotionItem      # 4 thuốc Witch + MilkBucket: tự định nghĩa hiệu ứng & vật phẩm trả lại (GlassBottle / Bucket)
  ├── BowItem             # Kéo dây (1.0s), giảm tốc 0.4x, zoom FOV -8°, tìm mũi tên và bắn ArrowEntity
  ├── SpearItem           # Đòn đâm Jab (tầm 4.2, 5 HP) và tụ lực Charge (chuột phải)
  ├── ThrowableItem       # Ném EnderPearl / Egg với Factory tạo ProjectileEntity tương ứng
  └── abstract ToolItem   # Công cụ tương tác đặc biệt
      ├── ShearsItem      # Đào lá/len nhanh 5× (ghi đè getMiningSpeedMultiplier), hỗ trợ Sheep.onInteract
      ├── FlintAndSteelItem # Ghi đè onInteractMob: đốt cháy mọi MobEntity 8s
      ├── BucketItem      # Vật phẩm chứa rỗng, hỗ trợ Cow/Goat.onInteract trả về MilkBucket
      └── GoatHornItem    # Ghi đè onInstantUse: thổi tù và kèm cooldown 7.0s
  ```
- **Tích hợp với `ItemUseHandler` & Zero-GC:**
  - `Item` khai báo các phương thức đa hình có cài đặt mặc định an toàn (ví dụ: `getUseAction()`, `getUseDurationSeconds()`, `getMeleeDamage()`, `getMeleeReach()`, `getMeleeCooldown()`, `getMiningSpeedMultiplier(Block)`, `onInstantUse(...)`, `onConsumeComplete(...)`, `onReleaseUse(...)`, `onInteractMob(...)`).
  - `ItemUseHandler` giữ trách nhiệm duy nhất: quản lý **trạng thái phiên sử dụng** (đang giữ bao lâu, bộ đệm vector zero-GC, hồi chiêu Spear Charge & Goat Horn), và **ủy quyền đa hình** sang `activeItem` thay vì chuỗi `if (item == Item.BOW) ... else if (item == Item.IRON_SPEAR) ...`.
  - **Bảo toàn tương thích 100%:** `FoodRegistry` vẫn giữ API tĩnh (`FoodRegistry.count()`, `FoodRegistry.of(Item)`) nhưng dữ liệu được đồng bộ tự động từ chính các thực thể `FoodItem` khi đăng ký (nguồn chân lý duy nhất, không lệch dữ liệu!). Toàn bộ hằng số `Item.BOW`, `Item.IRON_SPEAR`, `Item.WHITE_WOOL`... giữ nguyên tên và thứ tự atlas `56` vật phẩm độc lập.

### 6.5. Trụ cột #4 — Hệ phân cấp Biome (`com.voxelgame.world.level.biome`)
- **Kiến trúc song hành `Biome` (OOP) + `BiomeType` (khóa hiệu năng O(1)):**
  ```text
  abstract Biome (Flyweight bất biến — 5 thực thể duy nhất)
  ├── PlainsBiome         # Ôn hòa, mưa, màu cỏ 0x91BD59, màu lá 0x77AB2F, bảng mob Plains
  ├── ForestBiome         # Rừng sồi, mưa, màu cỏ 0x79C05A, màu lá 0x59AE30, bảng mob Forest
  ├── FlowerForestBiome   # Rừng hoa, mưa, màu cỏ 0x88BB67, màu lá 0x6BA941, bảng mob FlowerForest
  ├── DesertBiome         # Khô hạn, không mưa/tuyết, màu cỏ 0xBFB755, màu lá 0xAEA42A, bảng mob Desert
  └── SnowyPlainsBiome    # Lạnh, tuyết rơi & đóng băng, màu cỏ 0x80B497, màu lá 0x60A17B, bảng mob theo SnowSubZone
  ```
- **Phương thức trừu tượng & đa hình của `Biome`:**
  - `public abstract BiomeType getType()`
  - `public abstract String getDisplayName()`
  - `public abstract int getGrassColorRgb()` và `public abstract int getFoliageColorRgb()` (thay cho bảng mảng hardcode tách rời trong `BiomeTint`)
  - `public abstract boolean hasRain()`, `public abstract boolean hasSnow()`, `public abstract boolean isDry()`
  - `public abstract Weather resolvePrecipitationWeather()` (đa hình chọn `RainWeather` / `SnowWeather` / `ClearWeather` khi trời chuyển giáng thủy!)
  - `public abstract MobSpawnTable getSpawnTable(MobCategory category, SnowSubZone zone)` (đẩy bảng chọn loài về đúng từng lớp con thay cho `switch (biome)` trong `MobSpawnTable`!)
  - `public static Biome of(BiomeType type)` — tra cứu O(1) không cấp phát bộ nhớ.
- **Bảo toàn hiệu năng:** `World` vẫn lưu mã biome theo mảng `byte[]` dùng `BiomeType.ordinal()` như cũ; `BiomeType` ủy quyền các hàm `hasRain()`, `hasSnow()`, `isDry()` và cung cấp `toBiome()` cho lời gọi OOP.

### 6.6. Trụ cột #5 — Hệ phân cấp Weather (`com.voxelgame.world.level.weather`)
- **Áp dụng State / Strategy Pattern với `abstract class Weather`:**
  ```text
  abstract Weather (Flyweight bất biến — 3 trạng thái thời tiết cục bộ)
  ├── ClearWeather        # label="CLEAR", targetSkyStrength=0, targetRainGain=0, extinguishesFire=false, accumulatesSnow=false
  ├── RainWeather         # label="RAIN",  targetSkyStrength=strength, targetRainGain=strength, extinguishesFire=true, accumulatesSnow=false
  └── SnowWeather         # label="SNOW",  targetSkyStrength=strength, targetRainGain=0, extinguishesFire=false, accumulatesSnow=true
  ```
- **Cách hoạt động với `WeatherManager`:**
  - `WeatherManager` quản lý chu kỳ thời gian toàn cục (`precipitation`, `remaining`, `strength`, `animationSeconds`) theo đúng hằng số Minecraft (`600..9000s` quang, `600..1200s` giáng thủy).
  - Tại mỗi frame, `WeatherManager` xác định đối tượng `Weather currentWeather` đa hình thông qua `strength > 0 ? biome.resolvePrecipitationWeather() : ClearWeather.INSTANCE`.
  - Các chỉ số `skyStrength`, `rainGain`, `label()`, kiểm tra dập lửa hay tích tuyết đều gọi đa hình qua `currentWeather` (`currentWeather.targetSkyStrength(strength)`, `currentWeather.targetRainGain(strength)`, `currentWeather.getLabel()`, `currentWeather.extinguishesFire()`, `currentWeather.accumulatesSnow()`).
  - Giữ nguyên mọi API hiện có (`toggle()`, `update(...)`, `strength()`, `skyStrength()`, `rainVolume()`, `biome()`, `label()`, `animationSeconds()`, `remainingSeconds()`, `CLEAR_MIN_SECONDS`...) và bổ sung `currentWeather()` để lớp khác và bài test khai thác trực tiếp tính đa hình.

### 6.7. Tầng Client & Engine (`com.voxelgame.client.*`) — Điểm nâng cấp của Hướng C
1. **Tách giao diện 2D theo mô hình `Screen` chuẩn Minecraft (`client.gui`):**
   - `public abstract class Screen implements Disposable`: định nghĩa khung vòng đời chung của mọi màn hình bật/tắt trên game (`isOpen()`, `open()`, `close()`, `render(...)`, `handleMouseClick(...)`, `handleMouseDrag(...)`, `handleMouseRelease(...)`, `handleKeyDown(...)`, `pausesWorld()`).
   - `BitmapFont`: tách bảng `GLYPHS` 5×7 và thuật toán dựng đỉnh ký tự dùng chung (loại bỏ đoạn code glyph 5×7 đang bị sao chép giống hệt nhau giữa `UIRenderer` và `InventoryScreenRenderer`!).
   - `PauseScreen extends Screen`: tách toàn bộ phần Pause Menu (khung bán trong suốt, nút Resume, nút `-/+` và thanh trượt âm lượng nhạc nền) ra khỏi `UIRenderer`.
   - `InventoryScreen extends Screen`: nâng cấp từ `InventoryScreenRenderer` (túi đồ 36 ô, kéo-thả, Shift+Click, tooltip), triển khai đầy đủ hợp đồng `Screen` (`pausesWorld() -> false`).
   - `HudRenderer implements Disposable`: tách phần vẽ HUD trong lúc chơi (Crosshair, Hotbar 9 ô, Tim/Đói/Oxy/Máu thú cưỡi, Nhãn thời tiết/giờ/hiệu ứng, Overlay lửa/cactus, Overlay 3D block mục tiêu) ra khỏi `UIRenderer`.
   - `UIRenderer implements Disposable`: giữ vai trò **Facade** gọn nhẹ kết nối `HudRenderer` và `PauseScreen` (hoặc điều phối chung), vừa thu gọn từ 1.773 dòng thành các lớp chuyên trách rõ ràng, vừa giữ giao diện gọn cho `VoxelGameApp` và test.
2. **Tách điều khiển đầu vào khỏi `VoxelGameApp` (`client.input`):**
   - `GameInputController`: đóng gói toàn bộ xử lý bàn phím & chuột (phím `E` mở/đóng `InventoryScreen`, `ESC` mở/đóng `PauseScreen`, `Q`/`Ctrl+Q` vứt vật phẩm, `F1` đổi tốc độ, `F3` Debug Overlay, `F5` CameraMode, `F6`/`F7`/`F8` môi trường, `Ctrl+C` ghé tâm biome, đào/đặt block & tấn công/sử dụng vật phẩm).
   - `VoxelGameApp`: rút gọn thành **Application Orchestrator** rõ ràng gồm 5 khối dễ thuyết trình: `create()` (khởi tạo World + Client), `render()` (Game Loop: Input → Update World → Render 3D → Render GUI), `resize()`, `dispose()`, và các hàm ủy quyền `InputProcessor` sang `GameInputController`.
   - Giữ trường `player` và `isPaused` trong `VoxelGameApp` đồng bộ với trạng thái `PauseScreen` để đảm bảo kiểm thử reflection trong `VoxelRegressionChecks` hoạt động chính xác (hoặc cập nhật đồng bộ).
3. **Sắp xếp lại nhóm Render, Model & Audio (`client.render`, `client.model`, `client.audio`):**
   - Giữ nguyên lõi thuật toán OpenGL/Mesh/Shader/OpenAL của `Camera`, `ShaderProgram`, `Texture`, `ItemTextureAtlas`, `SkyRenderer`, `WeatherRenderer`, `MobRenderer`, `FirstPersonRenderer`, `BlockOutlineRenderer`, `ParticleManager`, `LiquidAnimator`, `MobModels`, `ModelPart`, `AudioManager`, `MobSoundBank`.
   - Đặt vào đúng package chuẩn Minecraft để người thuyết trình có thể mở cây thư mục và giải thích rành mạch vai trò từng tầng nếu giảng viên hỏi về Engine.

---

## 7. Bản Đồ Minh Họa OOP Phục Vụ Thuyết Trình

| Khái niệm OOP / Pattern | Vị trí minh họa tiêu biểu trong dự án sau tái cấu trúc |
| --- | --- |
| **Đóng gói (Encapsulation)** | `private final` thuộc tính bất biến trong `Block`/`Item`/`Biome`; sao chép phòng thủ (defensive copy) trong `Entity.getPosition()` và `Block.getIconColor()`; `protected` cho hook kế thừa trong `MobEntity`/`LivingEntity`; `PlayerInventory` đóng gói mảng 36 slot. |
| **Kế thừa (Inheritance)** | Cây 4–5 tầng: `Entity → LivingEntity → MobEntity → HostileMob → RangedMob → Skeleton → Stray`; `Block → SolidBlock → GravityBlock → SandBlock`; `Item → ConsumableItem → FoodItem`; `Screen → PauseScreen / InventoryScreen`. |
| **Trừu tượng (Abstraction)** | Các lớp `abstract`: `Block`, `Entity`, `LivingEntity`, `MobEntity`, `ProjectileEntity`, `Item`, `Biome`, `Weather`, `Screen`; các giao diện `Tickable`, `Renderable`, `Interactable`, `MobGoal`, `MobVisual`. |
| **Đa hình (Polymorphism)** | `World` duyệt `List<Entity>` gọi `entity.update(...)`; `ItemUseHandler` gọi đa hình qua `Item` thay vì `if/else`; `WeatherManager` chuyển trạng thái qua `Weather`; `BiomeTint`/`MobSpawnTable` hỏi trực tiếp `Biome`; `VoxelGameApp` điều phối `Screen` và `Interactable`. |
| **Sửa lỗi thiết kế (LSP & SRP)** | So sánh trước/sau: tách `Block` khỏi `Entity` (sửa LSP), tách `UIRenderer` & `VoxelGameApp` thành `HudRenderer`/`PauseScreen`/`BitmapFont`/`GameInputController` (đạt SRP). |
| **Design Patterns** | **Flyweight** (`Block`, `Item`, `Biome`, `Weather`), **Strategy** (`MobGoal`, `Biome`), **State** (`Weather`, `ItemUseHandler`), **Template Method** (`ConsumableItem`, `RangedMob`, `Block.onNeighborChange`), **Factory** (`MobType.Factory`, `ThrowableItem`), **Facade** (`UIRenderer`), **Composite** (`ModelPart` phân khớp). |

---

## 8. Chiến Lược Kiểm Thử & Chuyển Đổi An Toàn

1. **Cập nhật bộ kiểm thử CPU hiện có (27 file):**
   - Chuyển package test tương ứng (giữ điểm vào chính trong `build.gradle`, ví dụ `com.voxelgame.VoxelRegressionChecks` hoặc giữ `com.voxelgame.engine.VoxelRegressionChecks` làm runner gốc gọi các bộ test theo package mới — cập nhật đồng bộ trong `build.gradle`).
   - Sau **mỗi giai đoạn (phase)** tái cấu trúc, bắt buộc chạy `./gradlew check` phải pass 100% trước khi chuyển sang giai đoạn tiếp theo.
2. **Bổ sung bộ kiểm thử OOP mới (`OopArchitectureChecks.java`):**
   - Xác nhận `!Entity.class.isAssignableFrom(Block.class)` (Block đã tách khỏi Entity).
   - Xác nhận `Modifier.isAbstract(...)` cho `Block`, `Entity`, `LivingEntity`, `MobEntity`, `HostileMob`, `PassiveMob`, `NeutralMob`, `MountableMob`, `EquineMob`, `RangedMob`, `ProjectileEntity`, `Item`, `ConsumableItem`, `ToolItem`, `Biome`, `Weather`, `Screen`.
   - Xác nhận đủ 5 lớp con `Biome` ánh xạ 1-1 với `BiomeType` và trả đúng màu cỏ/lá, khí hậu, bảng sinh mob.
   - Xác nhận đủ 3 lớp con `Weather` và hành vi đa hình của `WeatherManager`.
   - Xác nhận hệ phân cấp `Item` (56 standalone items, 19 `FoodItem` khớp `FoodRegistry`, 5 `PotionItem`, `BowItem`, `SpearItem`, `ThrowableItem`, `ToolItem`, `BlockItem`).
3. **Đồng bộ `README.md`:** Cập nhật toàn bộ Mục 2 (Kiến trúc mã nguồn) và các tham chiếu tên lớp/package ở Phần A của `README.md`.

---

## 9. Những Phần Để Mở Cho Giai Đoạn Lập Kế Hoạch & Triển Khai (Left to the Builder)

- Thứ tự chia các phase thực thi chi tiết (ví dụ: Phase 1 Core & Package Migration → Phase 2 Block & Biome & Weather → Phase 3 Entity/Mob Hierarchy → Phase 4 Polymorphic Item → Phase 5 Client/GUI/Input Split → Phase 6 OopArchitectureChecks & README sync) và các mốc chạy `./gradlew check`.
- Tên cụ thể của các helper private nội bộ khi tách `UIRenderer` → `BitmapFont` + `HudRenderer` + `PauseScreen` và `VoxelGameApp` → `GameInputController`, miễn bảo toàn 100% pixel layout, tọa độ hit-test chuột và zero-GC.
- Có giữ `UIRenderer` làm lớp Facade mỏng hay để `VoxelGameApp` giữ trực tiếp `HudRenderer` + `PauseScreen` (ưu tiên phương án gọn nhất mà không làm phức tạp thêm test).
- Chi tiết điều chỉnh `access modifier` (`public` / `protected` / package-private) giữa các sub-package mới (`world.level`, `world.entity.mob.*`, `client.gui`) nhằm tối ưu tính đóng gói mà vẫn biên dịch sạch trên Java 17.
