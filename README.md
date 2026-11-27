# PROPERTY.md — Đặc Tả Tính Chất Dự Án VoxelGame

Phần A mô tả **100% tính chất hiện có** của dự án **VoxelGame**. Sau mỗi thay đổi, rà soát và cập nhật các mục bị ảnh hưởng để đặc tả phản ánh đúng và đủ trạng thái mới nhất của dự án.

---

## PHẦN A: ĐẶC TẢ 100% TÍNH CHẤT HIỆN CÓ CỦA DỰ ÁN

### 1. Thông Tin Dự Án, Build & Tài Nguyên
- **Tên dự án / Gradle:** `VoxelGame` (`group = 'com.voxelgame'`, `version = '1.0-SNAPSHOT'`).
- **Java & Biên dịch:** Java 17 (`sourceCompatibility = JavaVersion.VERSION_17`, `targetCompatibility = JavaVersion.VERSION_17`), ép mã hóa nguồn `UTF-8` cho `JavaCompile`.
- **Entry Point & Cửa sổ:** `com.voxelgame.Main` khởi tạo `Lwjgl3ApplicationConfiguration` (kích thước mặc định `1280 x 720`, V-Sync `true`, Back Buffer RGBA 8-8-8-8/Depth 24 bit, Resizable `true`) và chạy `new Lwjgl3Application(new VoxelGameApp(), config)`. Tiêu đề cửa sổ là `Ourcraft`. Trên macOS tự động truyền tham số JVM `-XstartOnFirstThread`.
- **Thư viện phụ thuộc (`build.gradle`):**
  - **libGDX `1.13.1`** (`com.badlogicgames.gdx`): `gdx` (Core API), `gdx-backend-lwjgl3` (Backend Desktop LWJGL 3), `gdx-platform:natives-desktop` (Native tự động cho Windows/Linux/macOS, x64/arm64).
  - Đã loại bỏ hoàn toàn: LWJGL BOM 3.3.3, JOML 1.10.5, JLayer 1.0.1 (libGDX đã tích hợp sẵn LWJGL 3, GLFW, OpenGL, STB, OpenAL và thư viện toán học).
- **Tài nguyên (`src/main/resources/`):**
  - **Âm thanh:** `audio/music/` chứa 5 bản Ogg Vorbis từ `import.zip`. `audio/sfx/` chứa **135 mẫu OGG** môi trường, chiến đấu và tương tác (gồm 93 mẫu nền cũ, 31 mẫu bước chân 6 vật liệu, 2 mẫu shared cho cung/giáo và bộ SFX **Minecraft 1.21.11** tải nguyên byte từ Mojang Asset Index: `fire/{fire,ignite}`, `entity/player/hurt/{fire_hurt1..3,drown1..4}`, `damage/{fallbig,fallsmall,hit1..3}`, `random/{fizz,eat1..3,burp,drink,bow,bowhit1..4}`, `item/goat_horn/call0..7`, `entity/player/attack/{crit1..3,knockback1..2}`, `dig/cloth1..4`, `mob/sheep/shear`). Tài nguyên được tìm theo đường dẫn cố định trong classpath.
  - **Texture khối, nhân vật & hiệu ứng:** `textures/` đóng gói bộ ảnh gốc của dự án, 13 PNG địa hình 1.21.11, `water_still.png` (`16×512`, 32 frame), `lava_still.png` (`16×320`, 20 frame), bộ sprite trích trực tiếp từ tài nguyên Minecraft 1.21.11 gồm `stone.png`, `bedrock.png` (được chuẩn hóa thang xám đúng kênh độ sáng qua `Texture.readNormalizedPixel`), `fire_0.png` & `fire_1.png` (`16×512`, 32 frame lửa cháy), `destroy_stage_0..9.png` (10 giai đoạn nứt block), skin người chơi `entity/player/steve.png` (`64×64`), và **117 PNG giao diện** trong `textures/gui/` (`container/inventory.png`, `sprites/hud/*`, `sprites/widget/*`).
  - **Rương trang trí:** Ba tile pixel art mặt có khóa, mặt bên và nắp được tạo khi dựng atlas. Cây sồi và thông dùng tile thân, lá chuẩn của dự án.
  - **Tài nguyên môi trường:** `textures/environment/rain.png` và `snow.png` giữ nguyên ảnh 64 × 256 có sẵn. `celestial/sun.png` lấy từ `1.21.11.zip`, tám ảnh `celestial/moon/` lấy từ `Blue-Moon.zip` và `clouds.png` lấy từ `Cloudier Clouds.zip`. `audio/weather/rain.wav` là đoạn lặp nối chồng 0,65 giây từ bản thu `Kao - Minecraft Vanilla Rain Sounds Sample.mp3` đi kèm.
  - **Mob và đạn đạo:** 41 PNG mob nhập nguyên byte từ `minecraft_entities_assets.zip` dưới `textures/entity/<nhóm>/<tên>.png` (riêng `skeleton/skeleton.png` 64 × 32 dùng trực tiếp ảnh gốc từ Minecraft 1.21.11), gồm 33 ảnh nền và 8 ảnh lớp phủ, mắt hoặc hoa văn. Ảnh mob có kích thước từ 48 × 32 đến 128 × 128. Ba ảnh đạn chuẩn Minecraft gồm `projectile/arrow.png` (32 × 32, thân tên, đầu sắt và lông đuôi chữ thập), `projectile/potion.png` (16 × 16 từ sprite `potion_healing.png`) và `projectile/ender_pearl.png` (16 × 16 từ sprite `ender_pearl.png`). Cung, giáo và lọ cầm tay dùng chung `equipment/held.png` (128 × 80), gồm bốn khung kéo cung của Java 26.3 ở hàng dưới và hai sprite 16 × 16 chuẩn Minecraft của giáo sắt (`iron_spear.png`) và lọ thuốc (`potion_healing.png`). `textures/item/` chứa 56 PNG 16 × 16 lấy chuẩn Minecraft 1.21.11 (gồm vật phẩm rơi của mob, công cụ tương tác, 6 khối len, 2 mũi tên tẩm thuốc và 4 lọ thuốc Witch được ghép nhuộm màu). `audio/sfx/mob/<id>/<sự_kiện>{1..4}.wav` chứa 205 tệp mono PCM 16 bit ở 22.050 Hz, trong đó `skeleton/` dùng đầy đủ 3 mẫu `ambient`, 4 mẫu `hurt`, `death` và 4 mẫu `step` nguyên bản từ chỉ mục tài nguyên Mojang. Hai mẫu bổ sung trong thư mục shared dùng OGG nguyên bản từ chỉ mục tài nguyên Mojang của Java 26.3. Bảng dưới ghi các đường dẫn classpath theo nhóm.
- **Kiểm thử:** Gradle `regressionChecks` chạy **29 file kiểm thử CPU** (không cần OpenGL) bao gồm 24 bộ cũ (`MobLootChecks`, `ChunkMeshRegressionChecks`, `TerrainGenerationChecks`, `EnvironmentChecks`, v.v.), các kiểm tra Task 0 (`checkWoolFoundation`, `checkGrayscaleBlockTextures`), 4 bộ kiểm thử tính năng (`CombatItemsChecks`, `VisualsLightingChecks`, `SurvivalWorldChecks`, `GameModeChecks`) và bộ kiểm tra kiến trúc OOP (`OopArchitectureChecks`). `check` gọi task này. Trình biên dịch dùng `options.release = 17`.

| Nhóm dưới `textures/entity/` | Tên PNG (bỏ đuôi `.png`) |
| --- | --- |
| `zombie`, `zombie_villager`, `skeleton` | `zombie`, `husk`; `zombie_villager`; `skeleton`, `stray`, `stray_overlay`, `parched` |
| `creeper`, `spider`, `enderman`, `witch` | `creeper`; `spider`, `spider_eyes`; `enderman`, `enderman_eyes`; `witch` |
| `cow`, `sheep`, `pig`, `chicken` | `temperate_cow`; `sheep`, `sheep_fur`; `temperate_pig`; `temperate_chicken` |
| `horse` | `donkey`; `horse_white`, `horse_creamy`, `horse_chestnut`, `horse_brown`, `horse_black`, `horse_gray`, `horse_darkbrown`; `horse_markings_white`, `horse_markings_whitefield`, `horse_markings_whitedots`, `horse_markings_blackdots` |
| `rabbit`, `camel`, `goat`, `bear` | `gold`, `white`, `white_splotched`; `camel`, `camel_husk`; `goat`; `polarbear` |
| `wolf`, `fox`, `projectile` | `wolf_snowy`, `wolf_snowy_angry`; `snow_fox`, `snow_fox_sleep`; `arrow`, `potion`, `ender_pearl` |
| `equipment` | `held` |

- **Đối chiếu ZIP tham chiếu:** Texture 1.21.11 có các biến thể trên và trang bị ở nhánh `assets/minecraft/textures/`. Các đường dẫn khác tên đích gồm `entity/sheep/sheep_wool.png`, `entity/spider_eyes.png`, `entity/witch.png`, `entity/projectiles/arrow.png`, `item/potion.png`, `item/bow*.png` và `item/iron_spear_in_hand.png`. Âm thanh mob nhập từ các thư mục `<tên mob>/sfx/` trong `minecraft_entities_assets.zip`, gồm cả nguồn bổ sung cho Camel, Camel Husk và Parched. Mỗi sự kiện dùng tối đa hai mẫu nguồn đầu theo thứ tự đánh số. Nếu ZIP chỉ có một mẫu, cả hai tệp WAV dùng mẫu đó. Java 26.3 cung cấp random/bow cho tiếng bắn và item/spear/use cho lúc hạ giáo chuẩn bị lao. Creeper im lặng lúc chờ. Creeper, Enderman, cáo tuyết và Witch phát bước theo nhóm vật liệu của khối dưới chân với âm lượng cơ sở 0,15.

---

### 2. Kiến Trúc Mã Nguồn Hiện Tại (173 File Java Chính, 29 File Kiểm Thử)
Mã nguồn được tổ chức theo kiến trúc phân tầng chuẩn Minecraft Java Edition (`com.voxelgame.core`, `com.voxelgame.world.*`, `com.voxelgame.client.*`):

- **`com.voxelgame` (2 file):** `Main.java` khởi chạy desktop (LWJGL3). `VoxelGameApp.java` đóng vai trò **Application Orchestrator** điều phối vòng đời (`create`, `render`, `resize`, `dispose`), mặc định đặt người chơi vào `CreativeGameMode` khi khởi chạy, và ủy quyền `InputProcessor` sang `GameInputController` (giữ trường `player`, `isPaused` đồng bộ với `PauseScreen` và bộ kiểm thử reflection).
- **`com.voxelgame.core` (3 file):** Các giao diện hợp đồng OOP dùng chung gồm `Tickable` (`tick(World, float)`), `Renderable` (`render(ShaderProgram)`) và `Interactable` (`onInteract(World, Player, Item)`).
- **`com.voxelgame.world.*` (135 file — Tầng Logic Thế Giới):**
  - `world.gamemode` (3 file): Hệ phân cấp OOP Flyweight zero-GC gồm `abstract GameMode` cùng 2 lớp con bất biến `CreativeGameMode` và `SurvivalGameMode` điều phối đa hình toàn bộ quy tắc bay, công cụ địa hình (`F1`/`Ctrl+C`), bất tử, tiêu hao vật phẩm, phá khối (gồm Bedrock), mục tiêu mob, hiển thị HUD và chọn màn hình kho đồ.
  - `world.level` (8 file): `World` (hỗ trợ cả `breakBlockAndSpawnDrop` cho Survival và `breakBlockWithoutDrop` cho Creative), `Chunk`, `ChunkLighting` (Sky/Block Light 4-bit BFS zero-GC), `WorldTime`, `BlockUpdateQueue`, `LeafDecaySystem` (`implements Tickable`), `LiquidFlowSimulator` (`implements Tickable`), `TranslucentChunkSort`.
  - `world.level.block` (32 file): `abstract Block` tách hoàn toàn khỏi `Entity` (Flyweight registry 33 ID `0..32`), 6 lớp trừu tượng trung gian (`SolidBlock`, `GravityBlock`, `LiquidBlock`, `LogBlock`, `LeavesBlock`, `PlantBlock`) cùng 25 lớp khối cụ thể (`AirBlock`, `DirtBlock`, `GrassBlock`, `StoneBlock`, `BedrockBlock`, `SandBlock`, `GravelBlock`, `SandstoneBlock`, `WaterBlock`, `LavaBlock`, `OakLogBlock`, `SpruceLogBlock`, `OakLeavesBlock`, `SpruceLeavesBlock`, `ShortGrassBlock`, `TallGrassBlock`, `FlowerBlock`, `DeadBushBlock`, `SugarCaneBlock`, `CactusBlock`, `SnowLayerBlock`, `SnowBlock`, `IceBlock`, `DecorativeChestBlock`, `WoolBlock`).
  - `world.level.biome` (9 file): `abstract Biome` cùng 5 lớp con Flyweight (`PlainsBiome`, `ForestBiome`, `FlowerForestBiome`, `DesertBiome`, `SnowyPlainsBiome`), `BiomeType` (khóa O(1) ánh xạ qua `toBiome()`), `BiomeTint` (nội suy 3×3 đọc màu từ `Biome`) và `SnowSubZone`.
  - `world.level.weather` (5 file): `abstract Weather` (State/Strategy Pattern) cùng 3 lớp con Flyweight (`ClearWeather`, `RainWeather`, `SnowWeather`) và `WeatherManager`.
  - `world.level.gen` (3 file): `TerrainNoise`, `TerrainGenerator`, `DesertPyramid`.
  - `world.item` (17 file): `abstract Item` (56 vật phẩm độc lập + 32 `BlockItem` đặt được, giữ liên kết 2 chiều `Item.*_WOOL ↔ Block.*_WOOL`) cùng 13 lớp con đa hình (`BlockItem`, `SimpleItem`, `abstract ConsumableItem`, `FoodItem`, `PotionItem`, `BowItem`, `SpearItem`, `ThrowableItem`, `abstract ToolItem`, `ShearsItem`, `FlintAndSteelItem`, `BucketItem`, `GoatHornItem`), `FoodRegistry` (19 thức ăn đồng bộ từ `FoodItem`), `ItemUseHandler` (ủy quyền đa hình zero-GC) và `CreativeCatalog` (danh mục tĩnh bất biến zero-GC gom toàn bộ khối & vật phẩm chia 5 tab cho Kho Creative).
  - `world.entity` (5 file): `abstract Entity` (`implements Tickable, Renderable`), `abstract LivingEntity`, `ItemEntity`, `FallingBlockEntity`, `DamageSource`.
  - `world.entity.player` (3 file): `Player` (kế thừa `LivingEntity`, sở hữu `GameMode`, `PlayerInventory` 36 ô & `Hotbar` 9 ô theo Composition), `PlayerInventory`, `Hotbar`.
  - `world.entity.mob` (13 file): `abstract MobEntity` (`implements MobVisual`), các lớp trừu tượng trung gian `abstract HostileMob`, `abstract PassiveMob`, `abstract NeutralMob`, `abstract MountableMob` (`implements Interactable`), `abstract EquineMob`, `abstract RangedMob`, cùng `MobType` (Factory), `MobCategory`, `MobAttitude`, `MobLootTable`, `MobSpawner`, `MobSpawnTable`.
  - `world.entity.mob.ai` (4 file): Strategy Pattern gồm `MobGoal`, `MobGoalSelector`, `MobGoals`, `SimpleGridNavigator`.
  - `world.entity.mob.monster` (11 file): `Zombie`, `ZombieVillager`, `Husk`, `Skeleton`, `Stray`, `Parched`, `Creeper`, `Spider`, `Enderman`, `Witch`, `CamelHusk`.
  - `world.entity.mob.animal` (12 file): `Cow`, `Sheep`, `Pig`, `Chicken`, `Rabbit`, `Horse`, `Donkey`, `Camel`, `Goat`, `PolarBear`, `Wolf`, `SnowFox`.
  - `world.entity.projectile` (7 file): `abstract ProjectileEntity`, `ArrowEntity`, `ArrowType`, `ThrownPotionEntity`, `PotionType`, `ThrownEnderPearlEntity`, `ThrownEggEntity`.
  - `world.effect` (2 file): `StatusEffectType`, `StatusEffectInstance`.
  - `world.physics` (1 file): `Raycast` (DDA voxel + giao cắt AABB thực thể tái sử dụng kết quả).
- **`com.voxelgame.client.*` (33 file — Tầng Hiển Thị, Giao Diện, Đầu Vào & Âm Thanh):**
  - `client.input` (1 file): `GameInputController` (`implements InputProcessor`) đóng gói xử lý bàn phím, chuột, đào/đặt block (hỗ trợ phá tức thì Creative), Pause/Inventory, `F2` (đổi Creative ↔ Survival), `Ctrl+C` và `F1`, `F3`, `F5..F8`.
  - `client.gui` (9 file): `abstract Screen` (`implements Disposable`), `PauseScreen` (`pausesWorld() -> true`), `InventoryScreen` (`pausesWorld() -> false`, túi đồ 36 ô kéo-thả cho Survival), `CreativeInventoryScreen` (`pausesWorld() -> false`, Kho Creative 5 tab 9×5 cuộn + Hotbar 9 ô + Thùng rác `[X]`), `BitmapFont` (font 5×7 dùng chung duy nhất), `GuiTextureAtlas`, `HudRenderer` (ẩn thanh sinh tồn ở Creative, vẽ thông báo toast chuyển chế độ), `DebugOverlayRenderer` (`F3` 8 dòng zero-GC gồm `MODE`) và `UIRenderer` (Facade mỏng kết nối `HudRenderer` + `PauseScreen`).
  - `client.render` (11 file): `Camera`, `ShaderProgram`, `Texture`, `ItemTextureAtlas`, `EnvironmentMesh`, `SkyRenderer`, `WeatherRenderer`, `BlockOutlineRenderer`, `FirstPersonRenderer`, `LiquidAnimator`, `ParticleManager`.
  - `client.render.entity` (6 file): `MobRenderer`, `MobAppearance`, `MobTextureKey`, `MobTextureManager`, `MobVisual`, `MobVisualState`.
  - `client.model` (3 file): `ModelPart` (Composite), `MobModel`, `MobModels`.
  - `client.audio` (3 file): `AudioManager`, `MobSoundBank`, `MobSoundEvent`.
- **Kiểm thử CPU (29 file):** Toàn bộ 24 bộ kiểm thử cũ trong `src/test/java/com/voxelgame/engine/` giữ nguyên qua `VoxelRegressionChecks.java`, bổ sung `CombatItemsChecks.java`, `VisualsLightingChecks.java`, `SurvivalWorldChecks.java`, `GameModeChecks.java` và `OopArchitectureChecks.java` (xác nhận toàn bộ cây kế thừa, tính trừu tượng và đa hình OOP).

---

### 3. Hệ Thống Block (33 Loại) & Cơ Chế Đào / Rơi Item

| ID | Lớp | Độ cứng (giây) | Hình học và lớp vẽ | Tile theo mặt | Hành vi |
| --- | --- | ---: | --- | --- | --- |
| 0 | `AirBlock` | −1 | Bỏ qua | 0 | Không khí, thay thế được. |
| 1 | `DirtBlock` | 0,75 | Khối rắn đục | 0 | Rơi Dirt. |
| 2 | `GrassBlock` | 0,75 | Khối rắn đục | Trên 1, đáy 0, cạnh 2 | Rơi Dirt. Khi tuyết phủ, trên dùng 19 và cạnh 11. Khối đục đầy, chất lỏng hoặc tuyết dày phía trên làm cỏ tàn thành Dirt. Cỏ lộ sáng ban ngày có thể lan sang Dirt gần đó. |
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
| 26 | `DecorativeChestBlock` | 2,5 | Khối rắn đục | Nam 33, cạnh và đáy 34, trên 35 | Rương trang trí dùng âm thanh gỗ, đào rơi rương và đặt được qua Hotbar. |
| 27–32 | `WoolBlock` | 0,8 | Khối rắn đục | 36–41 | Sáu màu len (`White`, `Light Gray`, `Gray`, `Black`, `Brown`, `Pink`) nối hai chiều với `Item.*_WOOL`. Cắt hoặc đào bằng `Shears` nhanh gấp `5×`. |

- Metadata dùng `0–7` cho tám lớp tuyết, `0/1` cho nửa dưới/trên của Tall Grass, bit `1` cho lá cây do người chơi đặt (`PERSISTENT_LEAF_STATE`, không tự rụng), và `0–7` cho mức dòng chảy chất lỏng (`Water` lan ngang tối đa 7 ô, `Lava` lan ngang tối đa 3 ô; gặp nhau tạo `Block.STONE` kèm âm `FIZZ`). ID cũ 0–8 giữ nguyên. Ô mặc định không cần mảng metadata riêng. Chiều cao vẽ tuyết là số lớp × 0,125. Va chạm của lớp 2–7 thấp hơn hình vẽ một lớp. Tám lớp va chạm cao 1 block.
- Mỗi 50 ms, World lấy ba mẫu ngẫu nhiên trên mỗi section có dữ liệu trong bán kính mô phỏng 128 block theo phương ngang: `Grass Block` lan sang Dirt gần đó hoặc tàn khi bị che; `LeafDecaySystem` kiểm tra lá tự nhiên qua BFS bán kính 6 (rụng thành `AIR` kèm `2%` rơi `Item.STICK` khi không còn nối thân gỗ). `LiquidFlowSimulator` cập nhật tối đa `64` ô/tick (`0,25s` cho Water, `1,50s` cho Lava) khi có thay đổi địa hình gameplay.
- Khi sửa block, hàng đợi xử lý ô vừa đổi, sáu ô chung mặt và các phụ thuộc ngoài nhóm này. Mía kiểm tra nước ở cao độ nền. Snow Layer tan thành Air và Ice tan thành Water khi có Lava trong bán kính Manhattan 2. Cát/sỏi tạo thực thể đúng một lần khi mất nền, rồi đặt lại hoặc rơi item tùy mặt đáp.
- Tia chọn được mọi block trừ Air, Water và Lava. Chỉ block có hộp va chạm thực mới cản chuyển động. Chữ X của cây vẽ hai phía và không có hộp vật lý.

---

### 4. Gameplay & Hệ Thống Điều Khiển
- **Chế độ chơi Creative & Survival (`F2`, mặc định Creative khi mở game):**
  - Phím `F2` (khi không tạm dừng) chuyển đổi tức thì qua lại giữa **`CreativeGameMode`** và **`SurvivalGameMode`**, tự đóng màn hình túi đồ đang mở để đồng bộ UI và hiển thị thông báo ngắn `MODE: CREATIVE` / `MODE: SURVIVAL` trong 2,5 giây trên HUD.
  - **Creative (mặc định khi khởi chạy):** Bay tự do (`Space` ×2), dùng `F1` (tốc độ địa hình) và `Ctrl + C` (ghé biome), bất tử hoàn toàn (miễn sát thương, không tụt đói/Oxy, tự dập lửa, ẩn thanh tim/đùi thịt/Oxy), mob thù địch/trung lập bỏ qua và tự nhả mục tiêu, đào vỡ **tức thì** mọi khối kể cả `Bedrock` (nhịp hồi 0,18s khi giữ chuột trái, không rơi rác `ItemEntity`), đặt khối/bắn cung (không cần mũi tên)/ném đạn/ăn uống không tiêu hao số lượng, và phím `E` mở **Kho Creative** (`CreativeInventoryScreen`) lấy được toàn bộ khối & vật phẩm trong game.
  - **Survival:** Khóa hoàn toàn chế độ bay (`Space` ×2), `F1` và `Ctrl + C` (khi chuyển từ Creative sang Survival tự động tắt bay và đặt tốc độ địa hình về `1x`); giữ đầy đủ 20 HP, 20 đói, Oxy, mob tấn công, đào theo độ cứng (không phá Bedrock) và rơi item, trừ vật phẩm khi đặt/dùng, phím `E` mở túi đồ 36 ô (`InventoryScreen`).
- **Di chuyển & Xoay góc nhìn:** `W, A, S, D` di chuyển 4 hướng trên mặt phẳng ngang XZ theo hướng `yaw`; di chuột xoay góc nhìn 360° với độ nhạy `MOUSE_SENSITIVITY = 0.12f` và giới hạn `pitch` trong `[-89.0°, 89.0°]`[cite: 1].
- **Nhảy & Bơi (`Space` 1 lần):** Nhảy khi `onGround == true` (`JUMP_VELOCITY = 8.6f`)[cite: 1]; khi đang trong Nước hoặc Lava sẽ bơi nổi lên với vận tốc dọc `4.2f`[cite: 1].
- **Chế độ Bay (nhấn đúp `Space` trong tối đa `0.30s` — chỉ trong Creative):** Bật/tắt Fly Mode. Khi bay, trọng lực tắt, giữ `Space` để bay lên với tốc độ cơ bản `7.0` block/s nhân với mức `F1`. Giữ cả hai phím thì ưu tiên `Space`. Thả cả `Space` và `Left Shift` để giữ độ cao. Tốc độ ngang là `1.8x`, nhân thêm `1.35x` khi chạy nhanh. Chạm mặt đất sẽ tự thoát Fly Mode.
- **Hạ độ cao, thoát bay và rón rén (`Left Shift`):**
  - Khi đang bay, giữ `Left Shift` để hạ độ cao với tốc độ cơ bản `4.0` block/s nhân với mức `F1`. Nhấn đúp `Left Shift` trong tối đa `0.30s` để thoát bay và bật lại trọng lực.
  - Khi đứng/đi trên mặt đất: bật **Sneak**, giảm tốc độ còn `0.5x`, nội suy mượt tầm mắt Camera từ `1.62f` xuống `1.45f` (tốc độ nội suy `deltaTime * 14.0f`), và kích hoạt **giữ mép block** (`preventFallingOffEdgeX/Z` theo bước `0.02f`) không cho rơi xuống vực[cite: 1].
- **Chạy nhanh (`Left Ctrl`):** Bật **Sprint** khi đang di chuyển ngang và không Sneak; tăng `1.5x` tốc độ đi bộ kèm hiệu ứng mở rộng Dynamic FOV[cite: 1].
- **Đổi tốc độ kiểm tra địa hình (`F1` — chỉ trong Creative):** Mặc định `1x`. Mỗi lần bấm chuyển đúng một mức theo vòng `1x → 3x → 5x → 10x → 25x → 50x → 1x`, kể cả trong Pause Menu. Mức đã chọn giữ nguyên khi nhả phím hoặc giữ phím và chỉ đổi ở lần bấm tiếp theo. Hệ số áp dụng cho mọi hướng di chuyển ngang và cả tốc độ bay lên, hạ độ cao trong Fly Mode. Khi chuyển sang Survival tự động đặt lại `1x`.
- **Ghé tâm biome (`Ctrl+C` khi đang chơi — chỉ trong Creative):** Dịch chuyển lần lượt qua Snowy Plains, Forest, Plains, Flower Forest, Desert rồi lặp lại. Tìm mặt đỡ khô ngay tâm hoặc trong phạm vi 32 block gần tâm của cùng biome, đặt chân trên mặt đỡ, xóa vận tốc và tắt chế độ Bay. Nếu không có nền hợp lệ, người chơi giữ nguyên vị trí và lần nhấn sau chuyển sang biome kế tiếp. Phím tắt dùng được với Ctrl trái hoặc phải.
- **Túi đồ 36 Ô & Kho Creative (`E`), Hotbar 9 Ô (`1`–`9` / Cuộn chuột) & Vứt vật phẩm (`Q` / `Ctrl+Q`):**
  - `PlayerInventory` gồm `36` ô (`0..8` đồng bộ trực tiếp với `Hotbar`, `9..35` là 3 hàng × 9 cột balo), hỗ trợ nhặt đồ tràn từ Hotbar sang balo.
  - Phím `E` bật/tắt màn hình kho đồ theo `GameMode` (`pausesWorld() = false` nên thế giới vẫn chạy nền): ở **Survival** mở `InventoryScreen` 36 ô (Left-click nhấc/đặt/gộp, Right-click chia đôi/thả 1, Shift+Click chuyển nhanh giữa Hotbar và balo); ở **Creative** mở `CreativeInventoryScreen` gồm 5 tab (`ALL`, `BLOCKS`, `COMBAT`, `FOOD`, `ITEMS`) tổng hợp toàn bộ khối (gồm `Bedrock`, 6 màu len) và 56 vật phẩm độc lập qua lưới 9×5 cuộn, hàng 9 ô Hotbar bên dưới và ô Thùng rác `[X]` (Left-click lấy full stack, Right-click lấy 1, Shift+Click đưa thẳng vào Hotbar, Shift+Click `[X]` dọn sạch Hotbar). Tự đóng khi nhấn `E`/`ESC`/`F2`.
  - Phím `Q` vứt 1 vật phẩm từ ô đang chọn (`Ctrl+Q` vứt cả stack) ra trước mặt với `pickupDelay = 1,0s`.
  - Trang bị khởi đầu: toàn bộ 9 ô `Hotbar` và 27 ô balo khởi tạo trống hoàn toàn (người chơi không giữ sẵn vật phẩm nào khi mới vào game).
  - **Chiến đấu & Sử dụng Vật phẩm (`ItemUseHandler`):**
    - **Cung (`Bow`):** giữ Chuột Phải kéo dây (tối đa `1,0s`, giảm tốc `0,4×`, zoom FOV tối đa `-8,0°`); nhả bắn `ArrowEntity` (lấy `ARROW`, `TIPPED_ARROW_SLOWNESS`, `TIPPED_ARROW_WEAKNESS` từ kho) kèm `BOW_SHOOT`.
    - **Giáo sắt (`Iron Spear`):** Chuột Trái đâm Jab tầm `4,2` block (`5,0 HP`, ×1,5 khi crit); giữ Chuột Phải Charge lao tới (khi `speed >= 4,0` gây `5,0 + min(8,0, speed × 0,8) HP` kèm đẩy lùi).
    - **19 Thức ăn (`FoodRegistry`) & 4 Thuốc Witch:** giữ Chuột Phải `1,6s` để ăn (hồi đói & `saturation`, kèm hiệu ứng phụ chuẩn của `Rotten Flesh`/`Chicken`/`Spider Eye`) hoặc uống thuốc (`Healing`, `Fire Resistance`, `Water Breathing`, `Swiftness`) trả lại `Glass Bottle`.
    - **Đạn ném & Tiện ích:** Chuột Phải ném `Ender Pearl` (dịch chuyển tới điểm chạm an toàn, xóa `fallDistance`, trừ `5 HP`), `Egg` (`1/8` nở 1 gà con, `1/32` trong số đó nở 4 gà con), thổi `Goat Horn` (cooldown `7,0s`), thuần hóa `Wolf` bằng `Bone` (`1/3`, ngồi/đứng, theo chủ & đánh trả mục tiêu của chủ), dụ `Pig` bằng `Carrot`/`Potato`, dụ `Rabbit` bằng `Dandelion`/`Carrot`.
  - **Tương tác đặc biệt & Knockback:** `Flint and Steel` đốt mob (8s, hiển thị ngọn lửa hoạt ảnh 3D); `Shears` cắt 1–3 len `Sheep` và đào lá/len nhanh `5×`; `Bucket` lấy sữa từ `Cow`/`Goat`; `Milk Bucket` uống xóa hiệu ứng trả `Bucket`. Mọi đòn đánh hợp lệ giữa Entity ↔ Entity tạo lực đẩy lùi bật nảy lên trời (`velocity.y >= 4,8f` ở mob, `4,5f` ở người chơi khi chạm đất) và phát `damage/hit1..3.ogg`.
- **Sinh tồn, Oxy & Sprint-Swimming:** Người chơi có 20 HP, 20 điểm đói, `saturation` khởi đầu `5,0` và `AIR_MAX_TICKS = 300` (15 giây thở dưới nước). Khi mắt ngập `Water` và không có `Water Breathing`, Oxy tụt 20 tick/s rồi mất `2 HP/s`. Giữ `Left Ctrl` khi bơi trong `Water` kích hoạt **Sprint-Swimming** (`1,55×` tốc độ bơi, lặn/trồi theo góc nhìn). Khi đói ≥ 18, hồi 1 HP mỗi 4s. Cháy kích hoạt lớp phủ lửa góc nhìn 1 (`FIRE_RESISTANCE` miễn mọi sát thương Lửa/Lava).
- **Góc nhìn (`F5`) & Kiểm thử môi trường:** `F5` xoay vòng `FIRST_PERSON → THIRD_BACK → THIRD_FRONT` (hiển thị mô hình `steve.png` ở góc nhìn thứ 3 có raycast chống xuyên tường). `F3` bật/tắt bảng thông số gỡ lỗi (`FPS`, `XYZ`, `CHUNK`, `FACING`, `BIOME`, `LIGHT`, `ENTITIES`). `F6` đổi thời tiết, `F7` qua mốc giờ, `F8` tua đồng hồ 20×.

---

### 5. Vật Lý & Thuật Toán Sinh Thế Giới
- **Vật lý Người chơi (`Player`):**
  - Spawn tại tâm ngang `(x = 2000, z = 1024)`, trên nền cỏ Y=30. Khi chân rơi dưới Y=−25, hồi sinh quanh `(2000, 1600)` trên mặt đỡ rắn cao nhất có hai ô trống cho dấu chân, đặt lại vận tốc và tắt bay. Nếu vị trí đó bị xây kín hoặc không đủ chỗ, tìm vị trí hợp lệ quanh đó rồi tới vùng spawn.
  - Hộp va chạm AABB: `PLAYER_HALF_WIDTH = 0.3f` (rộng `0.6f`), `PLAYER_HEIGHT = 1.8f`[cite: 1].
  - Thông số vận tốc: `MOVE_SPEED = 5.5f`, `GRAVITY = -26.0f`, `TERMINAL_VELOCITY = -45.0f`[cite: 1].
  - Nhận diện chất lỏng (`isInLiquid`): kiểm tra block tại chân (`y + 0.1f`) và thắt lưng (`y + 0.9f`)[cite: 1]. Trong chất lỏng (không bay): tốc độ ngang `0.65x`, trọng lực `0.25x`, vận tốc chìm tối đa `-3.5f`[cite: 1].
  - Quét AABB theo thứ tự X → Z → Y trên hộp thực. Bước lên bậc tuyết thấp tối đa 0,5 block khi đủ chỗ. Ice giữ đà ngang với hệ số 0,98. Chạm xương rồng đẩy nhẹ và nháy đỏ 0,25 s, hồi chiêu 0,5 s.
  - Khi tiếp đất, quãng rơi tính từ đỉnh quỹ đạo trừ 3 block và hệ số giảm rơi của thực thể cho ra lượng HP mất, với mức thấp nhất là 0. Chạm nước, bay hoặc dịch chuyển đặt lại bộ theo dõi quãng rơi.
- **Raycast 3D DDA (`Raycast`):**
  - DDA duyệt voxel dọc tia, giao cắt hộp chọn trong đoạn đi qua mỗi ô. Tia xuyên Air, Water, Lava và phần ngoài hộp chọn của xương rồng, tuyết hoặc cây.
  - Tia thực thể đo giao điểm đầu tiên với AABB của thực thể còn sống, bỏ qua người phát tia. Tia dùng hộp va chạm block để giới hạn khoảng cách, cho phép chọn xuyên qua cây chữ X.
- **Vật lý Vật phẩm Rơi (`ItemEntity`):**
  - Kích thước `ITEM_SCALE = 0.28f` (`HALF_SIZE = 0.14f`)[cite: 1].
  - Vận tốc văng ban đầu ngẫu nhiên: `vx, vz ∈ [-0.7, 0.7]`, `vy = 3.2f`[cite: 1].
  - Trọng lực `ITEM_GRAVITY = -18.0f` (tối đa `-25.0f`), ma sát ngang `0.82f` (trên đất) / `0.96f` (trên không), va chạm 3 trục với khối rắn[cite: 1].
  - Sau `PICKUP_DELAY = 0.25s`: kích hoạt lực hút nam châm trong bán kính `MAGNET_DISTANCE = 1.85f` (kéo về tâm cơ thể người chơi với tốc độ `9.5f`), tự động nhặt vào `Hotbar` khi khoảng cách `<= PICKUP_DISTANCE (0.85f)`[cite: 1].
  - Tự biến mất sau `MAX_LIFETIME = 300.0s`[cite: 1].
  - Hoạt ảnh 3D: nhấp nhô `sin(age * 3.8f) * 0.07f + 0.08f` và tự xoay quanh trục Y theo góc `age * 110.0f` độ[cite: 1].
- **Thế giới & Sinh Địa hình (`World`, seed `20261114L`):**
  - Bản đồ có **`4000 × 256 × 2048` block**, dùng lưới `250 × 16 × 128` chunk (`Chunk.SIZE = 16`). Tọa độ hợp lệ là `x = 0..3999`, `y = 0..255`, `z = 0..2047`. Ô lưới chỉ chứa không khí dùng `null`. Đặt block đầu tiên tạo chunk và xóa block cuối cùng giải phóng chunk cùng mesh của nó.
  - Nền vùng thấp khoảng Y=26–34, núi tuyết của seed mặc định đạt ít nhất Y=140. Giới hạn nền địa chất là Y=154. Noise lục địa, Perlin xói mòn và ridged multifractal được ánh xạ bằng spline phân đoạn. Sa mạc có tầng cồn cát phân bậc hai block.
  - Từ tây sang đông là 5 dải biome rộng 800 block: `Snowy Plains`, `Forest`, `Plains`, `Flower Forest` và `Desert`. Perlin làm uốn ranh giới quanh X=800, 1600, 2400 và 3200. Địa tầng, cây, tuyết, HUD và thời tiết dùng cùng công thức phân loại biome.
  - **Plains, Forest & Flower Forest:** Bedrock → Stone → 3–4 Dirt → Grass Block ở chỗ thoải. Dốc vừa lộ đá xen sỏi, dốc lớn lộ đá. Sinh cây và thảm thực vật theo từng chunk `16×16` chuẩn Minecraft 1.21.11:
    - `Plains`: `0,05` cây/chunk (`33%` sồi cổ thụ, `67%` sồi thường); cỏ ngắn phủ `~25–30%` mặt cỏ trống (`4` cụm/chunk × `32` lần thử), cỏ cao rarity `1/5` chunk (`36` lần thử, tỉ lệ Tall:Short ≈ `1 : 12`); hoa `~3–5` bông/chunk (`50%` chunk × `10` lần thử) theo trọng số `Dandelion 2 : Poppy 1 : Cornflower 1 : Oxeye Daisy 1`.
    - `Forest`: `10` lần thử cây/chunk (`90%` sồi thường, `10%` sồi cổ thụ); cỏ ngắn `2` cụm/chunk × `12` lần thử (`~6–10%` mặt cỏ), không sinh cỏ cao; hoa thưa `~1–2` bông/chunk (`25%` chunk × `7` lần thử) chỉ gồm `Dandelion 2 : Poppy 1`.
    - `Flower Forest`: `6` lần thử cây/chunk (`90%` sồi thường, `10%` sồi cổ thụ); cỏ ngắn `2` cụm/chunk × `12` lần thử, không sinh cỏ cao; thảm hoa dày `~15–25` bông/chunk (`3` cụm/chunk × `10` lần thử) chuyển dải mượt 4 loài theo trường Perlin 2D.
  - **Desert:** Bedrock → Stone → 3–4 Sandstone → 3–5 Sand ở chỗ thoải. Dốc vừa và dốc lớn lộ sandstone, bờ sông sa mạc dùng cát có lớp đệm sandstone. Các cột Desert không sinh sỏi, tuyết, băng hay cây gỗ. Sinh `Cactus` rarity `1/5` chunk với chiều cao `biased_to_bottom` (`56%` cao 1, `28%` cao 2, `16%` cao 3), `Dead Bush` `2` cụm/chunk và `Sugar Cane` dày `1` cụm/chunk dọc bờ sông ấm. Một kim tự tháp giật cấp có nền móng sandstone, lối vào, sảnh cột, cầu thang và phòng ngầm có rương trang trí.
  - **Snowy Plains:** Nền đá và sỏi lộ trên dốc; **100%** cột địa hình lộ trời và **100%** mặt trên tán lá vân sam lộ trời (trừ vùng sát Lava) phủ đúng **1 lớp `Snow Layer`** (đỉnh trên Y=110 giữ `Snow Block`, mặt sông đóng `Ice`). Sinh cây theo phân vùng: đồng bằng `Flat` `0,1` cây/chunk (`Spruce`), rừng thông `Grove` (nhận diện bằng trường Perlin ở `Y = 30..61`) `10` lần thử/chunk (`2/3` `Spruce`, `1/3` `Pine`), vùng `Slopes/Peaks` (`Y ≥ 62`) không mọc cây.
  - **Sông và hồ:** Bỏ hoàn toàn hồ nước nhỏ trên mặt đất theo chuẩn Minecraft 1.18+. Sông uốn theo dải noise, có mực nước Y=28, bờ cát hoặc sỏi có nền đỡ và đóng băng mặt sông ở `Snowy Plains`. Mía sinh dọc bờ sông hợp lệ theo cụm `20` lần thử với rarity `1/6` chunk ở biome mưa ôn hòa và `1` cụm/chunk ở `Desert`, chiều cao `biased_to_bottom` (`50%` cao 2, `33%` cao 3, `17%` cao 4). Hồ dung nham mặt đất sinh theo chunk `16×16` với rarity chuẩn **`1/200` chunk** (`0,5%`), mực hồ tối đa Y=48 và chênh cao toàn dấu chân tối đa 3 block, tránh dải sông, công trình và vùng an toàn của người chơi.
  - Hồ dung nham chọn vật liệu theo biome ở từng cột. Desert có bờ Sand và lót Sandstone. Plains và Snowy Plains có bờ Grass Block và lót Dirt. Quy tắc tránh băng và tuyết gần dung nham áp dụng cả trên bờ.
  - Bộ đệm độ cao cột dùng `short[]`, mã biome và cờ sinh dùng `byte[]`. Bộ sinh tính độ cao trước, chọn dấu chân kim tự tháp, ghi địa tầng và nước theo dải cột được cắt tại biên chunk, rồi sinh hồ, băng/tuyết, công trình, cây và thực vật. Hai bộ đệm cột được bỏ sau khởi tạo. ID block trong chunk tiếp tục chiếm một byte.

#### Vật lý, AI và lượt sinh mob

- **Cập nhật cá thể.** `MobEntity` quét AABB theo X → Z → Y, nhảy qua bậc tối đa 1 block khi có khoảng trống và có thể được cấu hình bước thẳng tối đa 1,5 block cho thú cưỡi. Điều hướng kiểm tra vài bước quanh hướng đích, tránh Lava, Cactus và mép rơi sâu hơn 3 block. Chicken có ngưỡng điều hướng 8 block và Goat có ngưỡng 13 block. Mob có thể tự nổi trong Water. Mob nhạy nắng bắt lửa với thời lượng tối thiểu 2 giây khi `sunY() > 0,05`, lộ thiên, khô và không có mưa tại cột hiện tại. Hoạt ảnh theo hướng thân, hướng đầu và quãng di chuyển thực.
- **Mục tiêu AI.** Số ưu tiên thấp được chọn trước. Các kênh di chuyển, nhìn và nhảy điều phối riêng. Bộ dùng chung gồm nổi, tìm nước hoặc mái che, hoảng 3–5 giây ở tốc độ ×1,25, lánh thực thể, đuổi và đánh cận chiến theo hồi chiêu, đi theo vật phẩm trên Hotbar, đi lang thang trong bán kính 9 block và nhìn người chơi hoặc nhìn quanh. Goal trú nắng nhả kênh ngay khi mob vào bóng râm, chạm nước hoặc hết phơi nắng. Zombie và Zombie Villager ưu tiên mục tiêu sống trong 8 block khi đang cháy ngoài nắng. Skeleton và Stray tránh Wolf trong 6 block. Các loài chỉ sinh tự nhiên khi factory được đăng ký.
- **Bảng sinh.** Monster có tổng trọng số 415 trong mỗi biome (`Forest` và `Flower Forest` dùng chung bảng quái với `Plains`). Creature dùng tổng 46 ở `Plains`, 45 ở `Forest`, 44 ở `Flower Forest`, 4 ở `Desert` và 11/20/9 lần lượt ở đồng bằng tuyết, Grove và sườn núi tuyết. Chọn theo trọng số rồi thử đặt các thành viên ở điểm gần nhau, cùng biome và phân vùng thú tuyết. Snowy Slopes/Peaks ưu tiên Y ≥ 62, hoặc nền đá, sỏi hay Snow Block từ Y=48 với chênh cao tối thiểu hai block ở cột kề. Grove nhận diện vân sam trong cầu bán kính 10 block ở Y từ 30 đến 61. Phần còn lại là Snowy Plains Flat.
- **Vị trí sinh.** Vành hoạt động cách chân người chơi 24–128 block theo khoảng cách 3D. Monster cần tick 13.000–23.000, mặt trời thấp hơn −0,15, hoặc mái che rắn. Husk, Parched, Stray và Camel Husk cần lộ thiên. Creature ở `Plains`, `Forest`, `Flower Forest` cần Grass Block ban ngày. Thỏ Desert dùng Sand hoặc Grass Block. `Camel` sinh cố định 1 cá thể trưởng thành hiền lành (được đánh dấu persistent) trên nền Sand/Sandstone gần Kim tự tháp sa mạc khi người chơi tiến vào trong 128 block lần đầu. Snowy Plains dùng nền theo loài. Nước, Lava, Cactus, lá và Bedrock bị loại. Chỉ Polar Bear dùng nền Ice. Cả hộp va chạm phải nằm trong bản đồ và có khoảng trống. Một lượt sinh Husk ở mặt đất Desert ban đêm có 10% cơ hội tạo Camel Husk, Husk cầm giáo sắt (luôn là người lớn) và Parched khi còn ít nhất ba suất quái và đủ khoảng trống cho cả cụm.

| Cấu hình | Plains | Forest | Flower Forest | Desert | Snowy Plains |
| --- | --- | --- | --- | --- | --- |
| Sheep | 12 (4) | 12 (4) | 12 (4) | — | — |
| Pig | 10 (4) | 10 (4) | 10 (4) | — | — |
| Chicken | 10 (4) | 10 (4) | 10 (4) | — | — |
| Cow | 8 (4) | 8 (4) | 8 (4) | — | — |
| Horse | 5 (2–6) | — | — | — | — |
| Donkey | 1 (1–3) | — | — | — | — |
| Rabbit | — | — | — | 4 (2–3) | — |
| Camel | — | — | — | 1 cố định gần Kim tự tháp | — |
| Snow Rabbit | — | — | 4 (2–3) | — | Flat 10, Grove 4, Slopes/Peaks 4 (2–3) |
| Polar Bear | — | — | — | — | Flat 1 (1–2) |
| Wolf | — | 5 (4) | — | — | Grove 8 (4) |
| Snow Fox | — | — | — | — | Grove 8 (2–4) |
| Goat | — | — | — | — | Slopes/Peaks 5 (1–3) |
| Spider | 100 (4) | 100 (4) | 100 (4) | 100 (4) | 100 (4) |
| Creeper | 100 (4) | 100 (4) | 100 (4) | 100 (4) | 100 (4) |
| Zombie | 95 (4) | 95 (4) | 95 (4) | 19 (4) | 95 (4) |
| Skeleton | 100 (4) | 100 (4) | 100 (4) | 50 (4) | 20 (4) |
| Enderman | 10 (1–4) | 10 (1–4) | 10 (1–4) | 10 (1–4) | 10 (1–4) |
| Zombie Villager | 5 (1) | 5 (1) | 5 (1) | 1 (1) | 5 (1) |
| Witch | 5 (1) | 5 (1) | 5 (1) | 5 (1) | 5 (1) |
| Husk | — | — | — | 80 (4) | — |
| Parched | — | — | — | 50 (4) | — |
| Stray | — | — | — | — | 80 (4) |
| Camel Husk | — | — | — | 10% trên lượt Husk đủ điều kiện (cụm 3 cá thể) | — |

Mỗi ô ghi trọng số rồi đến cỡ bầy trong ngoặc. Tỷ lệ chọn tính trên tổng trọng số trong cùng nhóm và phân vùng.
- **Dân số, tỉ lệ con non và chi phí.** Bộ sinh tách chu kỳ: `MONSTER` thử 1 điểm gốc mỗi tick mô phỏng `0,05` giây (1 tick); quét chunk khám phá mới chạy mỗi `0,25` giây (tối đa 6 cột chunk quanh người chơi, xác suất `10%` sinh bầy bỏ qua cap 10, kèm sinh 1 `Camel` gần Kim tự tháp sa mạc); và `CREATURE` bổ sung định kỳ chạy mỗi `20,0` giây (400 tick, thử tối đa 4 điểm gốc). Mỗi bầy đã chọn thử tối đa 16 ô lân cận. Cap định kỳ trong vùng hoạt động là 70 Monster và 10 Creature. `MobEntity.setBaby(boolean)` đồng bộ thu nhỏ hitbox còn `0,5×` (khôi phục `1,0×` khi tắt) và giữ tỉ lệ hình `0,6×` (giữ `1,06×` cho `Husk` trưởng thành). Tỉ lệ sinh con non tự nhiên là `5%` cho `Zombie`, `Zombie Villager`, `Husk`, `Cow`, `Sheep`, `Pig`, `Chicken`, `Wolf`, `Goat`, `Snow Fox`; `10%` cho `Rabbit` và `Snow Rabbit`; `20%` cho `Horse` và `Donkey`; con thứ 2 trong bầy 2 con `Polar Bear`. Trạng thái khám phá dùng bit theo tọa độ chunk. Mob ngoài phạm vi 128 block tạm dừng AI nếu được giữ lại. Monster không persistent biến mất ngay khi vượt 128 block, hoặc có xác suất 1/800 mỗi tick mô phỏng 20 Hz sau 30 giây liên tục ở khoảng 32–128 block. Creature và cá thể persistent được giữ lại.

#### Đạn đạo và các loài thù địch

- **Đạn đạo.** Tên (tuổi thọ bay 8 giây), ngọc Ender (`projectile/ender_pearl.png`, 10 giây) và lọ thuốc (6 giây) chịu trọng lực, quét hình va chạm của voxel và thực thể dọc đoạn bay, chọn va chạm gần nhất, có xét bán kính đạn. Chủ đạn, đồng loại và cá thể chung đội cưỡi được bảo vệ. Tên gây sát thương vật lý theo cấu hình, đẩy lùi và có thể gây Slowness I hoặc Weakness I trong 30 giây. Khi chạm khối rắn, mũi tên cắm cố định vào địa hình tối đa 60 giây, tự rơi tiếp theo trọng lực nếu khối đỡ bị đào mất, và cho phép người chơi đi lại gần nhặt lại mũi tên do mình bắn vào túi đồ. Khi bắn trúng mob còn sống, mũi tên cắm vào thân mob và xoay/di chuyển theo mob tối đa 30 giây (tự dọn khi mob chết). Đạn lạc trúng quái ngoài đội khiến cả hai đổi mục tiêu. Lọ vỡ trong bán kính 4 block, áp dụng Slowness 90 giây, Poison 45 giây hoặc Weakness 90 giây. Harming gây tối đa 6 HP, Healing hồi tối đa 4 HP, với tác dụng đảo trên undead. Tác dụng tức thời giảm theo khoảng cách, còn trúng trực tiếp nhận toàn lực.
- **Zombie và Zombie Villager.** Có 20 HP, giáp 2, đi 2,3 block/s, đòn cận chiến 3 HP và phát hiện người chơi trong 35 block. Khi bị đánh, chúng gọi đồng loại trong 16 block. Có 5% cơ hội là con non, với nửa kích thước và tốc độ 3,45 block/s. Cả con non lẫn con trưởng thành cháy dưới nắng.
- **Husk.** Có chỉ số cận chiến của Zombie, 5% cơ hội sinh con non (trừ Husk cầm giáo cưỡi Camel Husk luôn là người lớn), hiển thị lớn hơn 6% (`1,06×`), chịu nắng và gây Hunger I trong 7–14 giây. Sau 30 giây ngập đầu liên tục, Husk rung trong 15 giây rồi chuyển thành Zombie cùng độ tuổi tại cùng vị trí.
- **Hình dáng và nhịp bước của mob.** Mô hình trưởng thành dùng đơn vị 16 pixel mỗi block với kích thước hộp và tỷ lệ texel vuông 1:1 chuẩn Minecraft (`heightScale = 1`, thỏ dùng tỉ lệ đồng đều 3 trục `0,6×`). Zombie và Skeleton cao 32 pixel, Creeper cao 26 pixel, Cow cao 25 pixel và Camel cao 44 pixel từ bàn chân tới đỉnh. Các cấu trúc đặc trưng dùng đúng hình học Minecraft (tay khoanh & mũ nghiêng 4 tầng Witch, lớp băng quấn `4×12×4` và sọ phụ của Parched, áo `4×12×4` của Stray, 8 chân 1 đốt `16×2×2` của Spider, đầu ngoài trượt lên lộ hộp hàm trong của Enderman, tai thỏ cao 5 pixel, đầu nghiêng `-55°` của Goat, cổ nghiêng `-30°` của Horse/Donkey). Biên độ chân theo quãng đường mỗi tick và lấy 40% giá trị mới sau mỗi 0,05 giây. Cung có hình nghỉ và ba hình kéo dây, đổi mức ở tick 13 và 18, trở về hình nghỉ ngay khi bắn. Hai vai nâng hoặc hạ trong 0,15 giây khi đổi trạng thái ngắm.
- **Skeleton, Stray và Parched.** Di chuyển 2,5 block/s, phát hiện trong 16 block, giương cung trong 20 tick khi đi ngang và lùi khi người chơi tới gần hơn 4 block. Skeleton và Stray có 20 HP, cháy nắng, dùng tên 3–4 HP và bắn khoảng mỗi 2 giây. Stray dùng tên Slowness. Parched có 16 HP, chịu nắng, miễn Weakness, dùng tên Weakness 2–3 HP và bắn khoảng mỗi 3,5 giây. Nhịp bắn rút ngắn khi ở gần.
- **Creeper.** Có 20 HP và tốc độ 2,5 block/s. Tiếp cận trong 16 block, bắt đầu phồng và chớp khi ở trong 3 block, rồi nổ sau 1,5 giây. Nếu mục tiêu vượt 7 block trong lúc chờ thì hủy. Vụ nổ bán kính 3 block gây tối đa 43 HP và đẩy lùi theo khoảng cách. Các ô phá được đổi qua hàng đợi cập nhật gameplay, giữ Bedrock và chất lỏng, kích hoạt cát hoặc sỏi mất nền và cây mất điều kiện bám.
- **Spider.** Có 16 HP, tốc độ 3 block/s và đòn 2 HP. Trung lập ngoài trời ban ngày, tấn công trong 16 block vào ban đêm hoặc dưới mái. Nhện bám tường khi va chạm ngang và vồ từ 2–4 block. Có 1% cơ hội sinh kèm cung thủ nếu đủ chỗ và cap, gồm Skeleton thông thường, Stray ngoài trời vùng tuyết hoặc Parched ngoài trời Desert.
- **Enderman.** Có 40 HP, tốc độ 3 block/s, tăng lên 4,1 khi giận và đánh 7 HP. Bị kích động khi trúng đòn hoặc khi tia ngắm của người chơi chạm vùng mắt không bị che trong 64 block. Chạm Water hoặc mưa gây 1 HP theo nhịp, và Enderman tìm điểm đứng để dịch chuyển trong 32 block. Mũi tên được né trước khi gây sát thương. Enderman cũng có thể dịch chuyển trong trận, và mỗi 2 tick (`0,10` giây) khi không giận sẽ thử 1 ô ngẫu nhiên quanh người để nhặt (`1/20`) hoặc đặt lại (`1/2000`) Dirt, Grass Block, Sand, Gravel và hoa.
- **Witch.** Có 26 HP, tốc độ 2,5 block/s và kháng 85% sát thương phép, gồm sát thương theo nhịp của Poison. Ném thuốc trong 10 block với nhịp khoảng 3 giây, ưu tiên Slowness ở khoảng từ 8 block, Poison nếu mục tiêu còn từ 8 HP, 25% cơ hội Weakness trong 3 block khi phù hợp, sau đó Harming. Tự uống thuốc trong 1,5 giây để hồi 4 HP, kháng lửa và Lava, thở dưới nước hoặc tăng tốc khi mục tiêu xa hơn 11 block.

#### Thú hiền lành và trung lập

| Loài | Nơi sinh, cỡ bầy | HP | Hitbox trưởng thành (rộng × cao, block) | Tốc độ cơ sở (block/s) |
| --- | --- | ---: | --- | ---: |
| Cow | Plains, Forest, Flower Forest, 4 | 10 | 0,9 × 1,4 | 2,0 |
| Sheep | Plains, Forest, Flower Forest, 4 | 8 | 0,9 × 1,3 | 2,3 |
| Pig | Plains, Forest, Flower Forest, 4 | 10 | 0,9 × 0,9 | 2,5 |
| Chicken | Plains, Forest, Flower Forest, 4 | 4 | 0,4 × 0,7 | 2,5 |
| Rabbit | Desert (2–3), Flower Forest (Snow Rabbit, 2–3) và ba phân vùng Snowy Plains (2–3) | 3 | 0,4 × 0,5 | 2,7 |
| Goat | Snowy Slopes/Peaks, 1–3 | 10 | 0,9 × 1,3 | 2,5 |
| Polar Bear | Snowy Plains Flat trên tuyết hoặc băng, 1–2 | 30 | 1,4 × 1,4 | 2,5 |
| Wolf | Forest và Snowy Grove, 4 | 8 | 0,6 × 0,85 | 3,0 |
| Snow Fox | Snowy Grove, 2–4 | 10 | 0,6 × 0,7 | 3,5 |

- **Cow, Sheep, Pig và Chicken.** Bốn loài hiền lành chạy hoảng khi bị đánh và có 5% sinh con non (hitbox `0,5×`). Bò tìm về đàn khi đi xa, dùng điều hướng tránh vách sâu và Lava, và cho vắt sữa bằng `Bucket`. Lợn dùng tiếng kêu theo nhịp. Cừu cúi đầu ăn Short Grass hoặc đổi Grass Block dưới chân thành Dirt qua cập nhật block của World với xác suất `1/1000` mỗi tick (trưởng thành) hoặc `1/50` mỗi tick (con non), đồng thời mọc lại lông khi ăn xong; khi cắt bằng `Shears` rơi 1–3 len đúng màu và ẩn lớp len trên mô hình. Tỷ lệ màu len là trắng 81,836%, xám nhạt 5%, xám đậm 5%, đen 5%, nâu 3% và hồng 0,164%. Gà vỗ cánh khi rơi, giới hạn tốc độ xuống 1,7 block/s trong không khí, miễn sát thương rơi và gà trưởng thành đẻ 1 `Egg` mỗi 300–600 giây.
- **Rabbit.** Thỏ hiền lành sa mạc luôn vàng cát. Thỏ tuyết có 80% lông trắng mắt đỏ và 20% lông trắng đốm đen. Thỏ nhảy cóc cao gần hai block, tránh người chơi trong 8 block, sói trong 10 block và quái thù địch trong 4 block. Dandelion ở ô Hotbar đang chọn dụ thỏ theo người chơi.
- **Goat.** Dê trung lập chịu rơi thêm 10 block trước khi tính sát thương, cho vắt sữa bằng `Bucket` thành `Milk Bucket`. Dê chọn chỗ đáp để nhảy vượt địa hình đến 10 block và lấy đà húc mục tiêu đứng yên hoặc di chuyển chậm, gây 2 HP và đẩy lùi mạnh; khi húc trúng `Stone`, `Oak Log`, `Spruce Log` hoặc `Ice`, dê trưởng thành rơi 1 `Goat Horn` (tối đa 2 sừng). Nhịp húc thông thường 30–300 giây. 2% cá thể là dê hét, dùng âm thanh riêng và nhịp 5–15 giây.
- **Polar Bear.** Bầy hai con gồm một trưởng thành và một con non với hộp 0,7 × 0,7 block. Gấu bơi với đầy đủ tốc độ ngang. Khi bị người chơi đánh, gấu trưởng thành quanh đó nổi giận và gấu con chạy. Người chơi tới gần gấu con trong 4 block kích thích gấu trưởng thành dựng lên, gầm và lao tới. Đòn đánh gây 6 HP. Gấu đôi khi săn Snow Fox.
- **Wolf.** Sói tuyết đổi sang mắt đỏ, gầm và gọi sói trong 16 block cùng tấn công người chơi đã đánh trúng bằng tay hoặc tên. Mỗi đòn trúng cho cá thể bị đánh một thời gian giận mới từ 20–39 giây. Sói đuổi người chơi tối đa 32 block và thôi đuổi khi mục tiêu chết, thoát tầm hoặc hết giận. Sói săn thỏ, cáo, cừu, Skeleton và Stray với đòn 4 HP. Sói rũ nước khi lên bờ và nghiêng đầu nhìn người chơi gần.
- **Snow Fox.** Cáo lông trắng, 5% là con non. Ban ngày cáo tìm tuyết hoặc tán vân sam để cuộn ngủ, ban đêm hoạt động. Người chơi đứng thường đến gần có thể đánh thức cáo và khiến cáo chạy trong 12 block. Người chơi rón rén có thể tiếp cận mà cáo vẫn ngủ. Cáo lấy đà và vồ thỏ hay gà trong 16 block, gây 2 HP và có thể chúi vào tuyết mỏng khi hụt. Cáo nhặt một vật phẩm khối gần để ngậm và nhả ra khi chết.

- **Bảng rơi vật phẩm khi mob chết (`MobLootTable`).** Cá thể non không rơi vật phẩm tiêu chuẩn (trừ khối/vật phẩm đang ngậm của cáo hoặc Enderman). Khi mob chết trong lúc đang cháy hoặc bởi lửa/dung nham, các loại thịt sống và khoai tây hiếm chuyển sang phiên bản chín (`Steak`, `Cooked Mutton`, `Cooked Porkchop`, `Cooked Chicken`, `Cooked Rabbit`, `Cooked Cod`, `Cooked Salmon`, `Baked Potato`):
  - **Cow:** 0–2 `Leather`, 1–3 `Raw Beef` / `Steak`. **Sheep:** 1 len đúng màu (nếu chưa cắt lông), 1–2 `Raw Mutton` / `Cooked Mutton`. **Pig:** 1–3 `Raw Porkchop` / `Cooked Porkchop`. **Chicken:** 0–2 `Feather`, 1 `Raw Chicken` / `Cooked Chicken`. **Rabbit / Snow Rabbit:** 0–1 `Rabbit Hide`, 0–1 `Raw Rabbit` / `Cooked Rabbit`, 10% `Rabbit Foot` nếu bị người chơi giết. **Polar Bear:** 75% rơi 0–2 `Raw Cod` / `Cooked Cod`, 25% rơi 0–2 `Raw Salmon` / `Cooked Salmon`. **Horse / Donkey:** 0–2 `Leather`. **Camel Husk:** 2–3 `Rotten Flesh`. **Goat, Wolf, Camel:** không rơi đồ khi chết.
  - **Zombie / Zombie Villager / Husk:** 0–2 `Rotten Flesh`; 2,5% rơi `Iron Ingot`, `Carrot` hoặc `Potato`/`Baked Potato` khi bị người chơi giết; `Husk` cầm giáo sắt có thêm 8,5% rơi `Iron Spear` khi bị người chơi giết. **Skeleton / Stray / Parched:** 0–2 `Bone`, 0–2 `Arrow`, 8,5% rơi `Bow` khi bị người chơi giết; `Stray` thêm 50% rơi `Arrow of Slowness`, `Parched` thêm 50% rơi `Arrow of Weakness`. **Creeper:** 0–2 `Gunpowder` khi bị giết (không rơi khi tự nổ), và rơi 1 `Music Disc 13` hoặc `Music Disc Cat` nếu bị `Skeleton`/`Stray`/`Parched` bắn chết. **Spider:** 0–2 `String`, 1/3 rơi `Spider Eye` nếu bị người chơi giết. **Enderman:** 0–1 `Ender Pearl` và khối đang cầm. **Witch:** luôn rơi 4–8 `Redstone Dust`, cộng 1–3 lượt rơi 0–2 món chọn đều từ `{Glass Bottle, Glowstone Dust, Gunpowder, Spider Eye, Sugar, Stick}`, cộng 8,5% rơi đúng lọ thuốc đang uống (`Healing`, `Fire Resistance`, `Water Breathing`, `Swiftness`) nếu bị người chơi giết đúng lúc đang uống.

#### Thú cưỡi và kỵ sĩ sa mạc

- **Điều khiển.** Chuột phải để lên ghế trống nếu đủ khoảng trống. Người ngồi ghế trước dùng `WASD` theo hướng nhìn, `Ctrl trái` để chạy nhanh trên lạc đà và giữ rồi nhả `Space` để nhảy hoặc Dash. `Shift trái` tìm điểm đặt chân trống bên cạnh thú. Camera theo ghế sau mỗi lượt cập nhật vật lý. Thú chở người chìm và lội ở 40% tốc độ ngang, với trọng lực giảm trong nước. Khi nước ngập hơn 2 block với ngựa/lừa hoặc qua chiều cao 2,38 block của lạc đà, các ghế tự tách tại chỗ. Người chơi có thể tự bơi và thú mới nổi lên sau khi trống ghế.
- **Horse và Donkey.** Cả hai loài có một ghế, bước thẳng qua bậc 1 block, 15–30 HP và 20% cơ hội sinh con non. Cá thể hoang kiểm tra xác suất `random.nextInt(100) < temper` sau mỗi lượt cưỡi khoảng 2,2–3,5 giây; nếu trượt sẽ hất người chơi xuống (khi có điểm đứng an toàn) và cộng `+5` điểm thiện cảm. Cá thể thuần hóa ở 100 điểm và được giữ trong World. Ngựa dùng hitbox 1,3965 × 1,6, tốc độ 4,8–14,5 block/s và nhảy cao 1,25–5,25 block. Một đàn có chung màu lông nền và từng cá thể chọn hoa văn độc lập. Lừa dùng hitbox 1,3965 × 1,5, tốc độ 7,5 block/s, nhảy cao 2,1 block, tai dài và bộ âm thanh riêng.
- **Camel.** Có hai ghế, 32 HP, hitbox 1,7 × 2,38 và bước thẳng qua bậc 1,5 block. Khi ngồi nghỉ trên cát, hitbox hạ còn 0,945 block. Camel đi 4,0 và chạy 6,8 block/s. Dash đầy lực lao khoảng 12 block, nâng chân khoảng 1 block, phát `CAMEL_DASH` và hồi sau 2,75 giây. Camel chịu thêm 4 block rơi an toàn, đi theo người cầm Cactus và hồi 2 HP khi được cho ăn.
- **Camel Husk.** Dùng chỉ số, cơ chế bước, ghế, ngồi và Dash của lạc đà. Cả lạc đà xác sống và hai kỵ sĩ đều chịu được nắng. Husk ghế trước lấy đà, hạ giáo với âm `SPEAR_CHARGE`, rồi gây sát thương và đẩy lùi theo tốc độ lao. Parched ghế sau bắn Weakness I trong 30 giây và tiếp quản điều khiển để giữ cự ly bắn khi mất Husk. Khi cả hai kỵ sĩ rời ghế, Camel Husk chuyển hiền lành. Chuột phải lên ghế khi đó sẽ thuần hóa và giữ cá thể trong World.

---

### 6. Đồ Họa, Shader, Texture & Hiệu Năng 60 FPS
- **OpenGL & Vòng lặp:** OpenGL 3.3 Core Profile trong backend LWJGL 3 của libGDX, bật `GL_DEPTH_TEST`, `GL_CULL_FACE` (`GL_BACK`, `GL_CCW`), bật V-Sync `config.useVsync(true)` với mục tiêu 60 FPS, giới hạn `deltaTime <= 0.05f`.
- **Màu trời & Ống kính:**
  - Vòm trời xoay theo camera, dùng xanh sáng vào ban ngày, xanh đen vào ban đêm và ánh cam theo hướng mọc/lặn. Mưa và tuyết chuyển bầu trời sang xám xanh.
  - Khi mắt camera nằm dưới mặt thoáng của Water hoặc Lava, địa hình đục và trong suốt, mob, mây và giáng thủy dùng chung sương khoảng cách. Water có sương xanh từ 1 đến 22 block. Lava có sương đỏ cam từ 0,25 đến 1,6 block. Nền trời và màu xóa khớp với sương, thiên thể ẩn và lớp phủ chất lỏng nằm dưới HUD.
  - Camera: `BASE_FOV_DEG = 70.0f` (`+8.0f` khi Sprint, `+5.0f` khi Fly, nội suy `deltaTime * 10.0f`), `NEAR_PLANE = 0.08f`, `FAR_PLANE = 1024.0f`, ma trận tính qua `Matrix4.setToProjection` và `setToLookAt`.
- **Định dạng Đỉnh & Shader (`ShaderProgram`):**
  - Hai định dạng đỉnh tương thích hoàn toàn trong một shader: 6 floats/đỉnh (`aPos`, `aTexCoord`, `aShade`) cho Mob, UI, Viewmodel, Hạt và Môi trường (`uUseVertexColor = 0`); và 7 floats/đỉnh (`+ aColor` gói `Usage.ColorPacked`) cho Chunk khi bật `enableChunkColorMode(true)`.
  - Fragment Shader nhân màu đỉnh với `uTexture * uTintColor` và độ sáng từ `ChunkLighting`/`vShade`/`uWorldLight`. Dấu âm `vShade` đánh dấu tự phát sáng (dung nham, mắt mob, lửa).
- **Ánh sáng, BiomeTint & Texture Atlas (`Chunk`, `ChunkLighting`, `BiomeTint`, `Texture`):**
  - `ChunkLighting`: mỗi chunk cấp phát lười mảng `byte[4096]` lưu Sky Light và Block Light 4-bit (0–15) lan truyền BFS zero-GC (`LavaBlock` phát sáng `15`, lá làm suy giảm `-1`); độ sáng mặt đỉnh là `max(MIN_LIGHT_LEVEL, max(sky/15f * skyFactor, block/15f))` kết hợp Directional Shading và AO.
  - `BiomeTint`: nội suy `3×3` theo `World.getBiome(x, z)` cho mặt trên `Grass Block` không tuyết, `Short Grass`, `Tall Grass` và `Oak Leaves` với tỉ lệ chia bù theo `PLAINS_TINT` để ở `PLAINS` giữ nguyên hệ số `(1, 1, 1)`.
  - `Texture`: lưới **8 × 6** ô 16 × 16 (`128 × 96` RGBA, `TILE_WHITE_WOOL..TILE_PINK_WOOL` ở chỉ số `36..41`), đọc qua `Texture.readNormalizedPixel` khắc phục triệt để ảnh PNG Grayscale 8-bit (`stone.png`, `bedrock.png`...).
- **Hoạt ảnh chất lỏng, Mây 3D, Hạt & Hiệu ứng Cháy:**
  - `LiquidAnimator`: cập nhật `TILE_WATER` (32 frame) và `TILE_LAVA` (20 frame) mỗi `0,10s` qua `glTexSubImage2D` mà không rebuild chunk.
  - `SkyRenderer`: dựng mây 3D khối dày 4 block (`Y = 140..144`) từ `clouds.png` có loại mặt tiếp giáp.
  - `BlockOutlineRenderer`: vẽ khung wireframe `GL_LINES` quanh `Block.Bounds` thực và overlay vết nứt 10 giai đoạn từ `destroy_stage_0..9.png`.
  - `ParticleManager`: pool tĩnh `MAX_PARTICLES = 512` hạt billboard zero-GC cho vỡ block, lửa, bắn nước và vỡ thuốc.
  - `MobRenderer` & `VoxelGameApp`: vẽ ngọn lửa chữ X tự phát sáng hoạt ảnh từ `fire_0.png`/`fire_1.png` quanh mob đang cháy và lớp phủ lửa 2D cạnh dưới màn hình ở góc nhìn thứ nhất khi người chơi đang cháy.
  - `FirstPersonRenderer`: kết xuất tay (`steve.png`), khối/vật phẩm/4 khung cung/giáo sắt góc nhìn thứ nhất (sau khi xóa Depth Buffer, kèm View Bobbing, Swing, Pull/Charge, Consume) và mô hình người chơi góc nhìn thứ 3 (`F5`).
- **Tối ưu & Giải phóng tài nguyên:** Tái sử dụng mảng đỉnh, `Matrix4`, `Vector3`, `StringBuilder`; toàn bộ tài nguyên GPU/Off-heap (`GuiTextureAtlas`, `HudRenderer`, `PauseScreen`, `InventoryScreen`, `DebugOverlayRenderer`, `UIRenderer`, `FirstPersonRenderer`, `BlockOutlineRenderer`, `ParticleManager`, `LiquidAnimator`, `Texture` v.v.) triển khai và gọi `Disposable.dispose()` đầy đủ khi đóng game.

---

### 7. Giao Diện Người Dùng (UI/HUD) & Font 5x7 (`Screen`, `HudRenderer`, `PauseScreen`, `InventoryScreen`, `CreativeInventoryScreen`, `BitmapFont`, `UIRenderer`, `GuiTextureAtlas`)
- **Kiến trúc màn hình chuẩn Minecraft:** `abstract Screen` định nghĩa vòng đời chung (`open`, `close`, `isOpen`, `pausesWorld`, `dispose`) cho `PauseScreen` (`pausesWorld = true`), `InventoryScreen` (`pausesWorld = false`) và `CreativeInventoryScreen` (`pausesWorld = false`, 5 tab `ALL`/`BLOCKS`/`COMBAT`/`FOOD`/`ITEMS`, lưới 9×5 cuộn, Hotbar 9 ô và Thùng rác `[X]`). `HudRenderer` đảm nhiệm HUD trong lúc chơi (tự ẩn thanh tim/đói/Oxy trong Creative, vẽ thông báo toast chuyển chế độ `MODE: CREATIVE` / `MODE: SURVIVAL` trong 2,5 giây) và lớp phủ 3D; `BitmapFont` quản lý duy nhất bảng glyph 5×7 dùng chung; `UIRenderer` đóng vai trò Facade điều phối.
- **Texture chuẩn Minecraft 1.21.11 (`GuiTextureAtlas`):** Toàn bộ Hotbar (`182×22` + khung chọn `24×23`), Crosshair (`15×15`), 10 tim (gồm tim trúng độc `poisoned` và tim thú cưỡi `vehicle`), 10 đùi thịt (gồm hiệu ứng `hunger`), 10 bọt khí Oxy (`air`/`air_bursting`/`air_empty`), thanh Nhảy/Dash thú cưỡi (`jump_bar_*` `182×5`), màn hình Balo 36 ô (`inventory.png` `176×166`) và nút/thanh trượt Pause Menu (`widget/button*`, `widget/slider*`) đều kết xuất pixel-perfect từ bộ PNG Minecraft 1.21.11 với `Nearest` + `ClampToEdge` (có fallback hình học khi chạy kiểm thử CPU không có OpenGL).
- **Màn hình thông số `F3` (`DebugOverlayRenderer`):** Hiển thị 8 dòng (`FPS`, `XYZ`, `CHUNK`, `FACING`, `BIOME`, `LIGHT`, `ENTITIES`, `MODE`) bằng `StringBuilder` tái sử dụng (zero-GC) và font bitmap 5×7 tích hợp (`A–Z`, `0–9`, `:`, `%`, `-`, `+`, `.`, `/`, `,`, `(`, `)`, `=`).

---

### 8. Hệ Thống Âm Thanh (`AudioManager`)
- **Vòng nhạc nền:** libGDX `Music` phát luồng Ogg Vorbis trên OpenAL. Mỗi vòng xáo Fisher–Yates và phát mỗi bài `calm1`, `calm3`, `hal3`, `piano2`, `piano3` đúng một lần. Bài đầu vòng mới khác bài cuối vòng trước. Sau mỗi bài nghỉ 3 giây, kể cả giữa hai vòng. Game bỏ qua tài nguyên thiếu hoặc không mở được.
- **Pause Menu và âm lượng:** Nhạc và khoảng nghỉ tiếp tục khi mở Pause Menu. Nút `[-]`, `[+]` và thanh trượt điều khiển ngay âm lượng nhạc và mưa từ 0% đến 100%, mặc định 70%. Các nút phát mẫu click khi được bấm.
- **Tiếng mưa:** `Music` phát lặp luồng WAV độc lập với nhạc nền. Âm lượng bằng mức trong Pause Menu nhân cường độ mưa cục bộ nhân hệ số mái che. Cường độ âm thanh trở về 0 khi rời các vùng có mưa (`Plains`, `Forest`, `Flower Forest`) hoặc mưa tạnh. Mái che bằng khối rắn hay lá giảm âm lượng về 40%. Luồng mưa dừng tại vị trí đang phát khi mở Pause Menu và tiếp tục từ vị trí đó khi bấm Resume.
- **Hiệu ứng vật liệu:** Các khối dùng nhóm mẫu cỏ/thực vật, sỏi/đất, cát, tuyết, đá, gỗ hoặc tiếng kính khi băng vỡ. Đào lâu phát tiếng gõ theo nhịp 0,25 giây. Phá và đặt thành công, bước chân, tiếp đất và cát/sỏi đáp đều chọn mẫu theo vật liệu. Bước chân dùng quãng đường đã đi và giảm âm lượng khi rón rén. Tiếng phá và cát/sỏi đáp giảm theo khoảng cách trong bán kính 16 block.
- **Hiệu ứng tương tác & chiến đấu mới:** Nhặt đồ phát tiếng khi `PlayerInventory` nhận vật phẩm. Va xương rồng phát theo hồi chiêu của phản hồi va chạm. Có tiếng chạm nước, bơi, chìm/nổi qua mặt nước và âm nền khi mắt ở dưới nước. Âm nền dưới nước dừng trong Pause Menu. Nước và dung nham gần người chơi có mẫu âm định kỳ giảm theo khoảng cách; khi `Water` gặp `Lava` tạo `Block.STONE` phát `FIZZ` (`random/fizz.ogg`). Bổ sung nhóm âm thanh chuẩn Minecraft 1.21.11 cho `BOW_SHOOT`/`THROW` (`random/bow`), `SPEAR_CHARGE`, `EAT` (`eat1..3` + `burp`), `DRINK` (`random/drink`), `GOAT_HORN` (`item/goat_horn/call0..7`) và `ENTITY_HIT` (`damage/hit1..3.ogg` giảm âm tuyến tính 16 block cho mọi cặp Entity ↔ Entity).
- **Hiệu ứng mob:** 23 ID dùng 2–4 tệp WAV cho mỗi sự kiện riêng của loài thuộc nhóm `AMBIENT`, `HURT`, `DEATH`, `STEP` (riêng `skeleton` dùng 3 `ambient`, 4 `hurt`, 2 `death` và 4 `step` chuẩn Minecraft gốc). Các nhóm đặc thù gồm mồi nổ, nổ, bắn cung, ba tiếng Enderman, ném/uống thuốc của Witch, dê chuẩn bị húc, sói gầm, lạc đà lướt, Husk chuyển dạng và lao giáo. Nổ, cung và lao giáo nằm dưới `shared/`. Camel và Camel Husk có tiếng lướt riêng. Camel Husk dùng mẫu Camel nếu bộ mẫu riêng không nạp được. Âm lượng giảm tuyến tính từ tâm người chơi tới bán kính mặc định 16 block, 28 block cho dê hét và 32 block cho tiếng nổ hoặc Enderman hét. Cao độ nhận hệ số cá thể, ví dụ 1,5 cho con non, cùng độ lệch ngẫu nhiên nhẹ. Mẫu được nạp một lần từ classpath và giải phóng cùng `AudioManager`.
- **Vòng đời tài nguyên:** Mẫu ngắn được nạp một lần vào `Sound` và dùng lại khi phát. Âm nền dưới nước, tiếng mưa và bài nhạc hiện tại dùng `Music`. Bộ đếm, thứ tự bài và mẫu đã nạp được tái sử dụng trong vòng lặp. `dispose()` giải phóng các luồng và bộ đệm âm thanh. `cleanup()` ủy quyền cho `dispose()`.

---

### 9. Chu Kỳ Ngày–Đêm & Thời Tiết

- **Đồng hồ:** Một ngày có 24.000 tick, chạy 20 tick/giây và bắt đầu ở tick 1.000 (07:00). Bình minh, trưa, hoàng hôn và nửa đêm lần lượt ở tick 0, 6.000, 12.000 và 18.000. Trời tối hẳn quanh tick 13.000, sáng lại từ vùng tick 23.000–24.000. Pause đóng băng đồng hồ, mây và thời tiết.
- **Thiên thể và ánh sáng:** Mặt trời mọc ở Đông (`+X`), qua đỉnh `+Y` rồi lặn ở Tây (`-X`). Mặt trăng đối diện 180° và chọn một trong tám pha theo bộ đếm ngày. Hai ảnh hòa trộn cộng sáng và mờ dần khi giáng thủy tăng. Ánh sáng môi trường chuyển từ trắng ban ngày qua ấm nhẹ lúc mặt trời gần chân trời đến xanh đêm. Mưa/tuyết làm giảm tối đa 24% cường độ ánh sáng ban ngày. Mây trôi từ Đông sang Tây và hiện cả ban ngày lẫn ban đêm, với màu thay đổi theo giờ và cường độ giáng thủy.
- **Chu kỳ giáng thủy:** Quang đãng tự động kéo dài 10–150 phút (`600–9.000s` = `12.000–180.000` tick), đợt mưa/tuyết kéo dài 10–20 phút (`600–1.200s` = `12.000–24.000` tick) chuẩn Minecraft Java. Cường độ chuyển dần trong 5 giây. Mỗi cột trong bán kính 14 block dùng biome khí hậu để quyết định mưa ở `Plains`, `Forest`, `Flower Forest`, tuyết ở `Snowy Plains` hoặc không giáng thủy ở `Desert`. Khi đạt cường độ tối đa, lấy mẫu khoảng 35% cột mưa và 29% cột tuyết để giảm số dải bán trong suốt chồng lên nhau. Hạt mờ dần khi sát mắt hoặc gần rìa vùng vẽ. Đáy mỗi dải dừng trên bề mặt cao nhất của khối rắn, lá, tuyết hoặc chất lỏng. Mưa cuộn nhanh theo chiều dọc, tuyết rơi chậm và lắc ngang.
- **Tích tuyết:** Khi cường độ vượt 50%, chạy theo nhịp random tick (`0,05` giây) trên các cột chunk trong bán kính mô phỏng 128 block với xác suất `1/16` mỗi chunk/tick. Cột `Snowy Plains` lộ thiên đóng băng `Water` mặt trên thành `Ice` hoặc phủ tối đa 1 lớp `Snow Layer` trên nền hợp lệ (`snowAccumulationHeight = 1`, không tự chồng dày thêm). Mái che, hộp người chơi và Lava gần đó được kiểm tra trước khi thay block.
