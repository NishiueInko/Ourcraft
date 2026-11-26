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
  - **Âm thanh:** `audio/music/` chứa 5 bản Ogg Vorbis từ `import.zip`. `audio/sfx/` chứa 93 mẫu OGG môi trường và tương tác, trong đó 31 mẫu bước chân của sáu nhóm vật liệu lấy nguyên byte từ Java 26.3. Thư mục mob bổ sung hai OGG cho cung và giáo. Tài nguyên được tìm theo đường dẫn cố định trong classpath.
  - **Texture khối:** `textures/` đóng gói bộ ảnh gốc của dự án và 13 PNG nhập nguyên byte từ `assets/minecraft/textures/block/` trong `1.21.11.zip`, gồm `sandstone.png`, `sandstone_top.png`, `sandstone_bottom.png`, `spruce_log_top.png`, `short_grass.png`, `tall_grass_bottom.png`, `tall_grass_top.png`, `dandelion.png`, `poppy.png`, `cornflower.png`, `oxeye_daisy.png`, `sugar_cane.png` và `gravel.png`. `dirt.png`, `grass_block_side.png`, `grass_block_side_overlay.png`, `grass_block_snow.png`, `water_still.png` và `lava_still.png` khớp nguyên byte với `minecraft_entities_assets.zip`. `grass_block_top.png` giữ nguyên độ sáng từng pixel của ảnh 16 × 16 từ cùng gói và lưu dưới dạng PNG RGB để libGDX đọc họa tiết vào các kênh màu. Game nạp ảnh đã đóng gói và dùng bộ nạp dự phòng khi thiếu tài nguyên.
  - **Rương trang trí:** Ba tile pixel art mặt có khóa, mặt bên và nắp được tạo khi dựng atlas. Cây cọ có hình dáng riêng và dùng tile thân, lá đã có của dự án.
  - **Tài nguyên môi trường:** `textures/environment/rain.png` và `snow.png` giữ nguyên ảnh 64 × 256 có sẵn. `celestial/sun.png` lấy từ `1.21.11.zip`, tám ảnh `celestial/moon/` lấy từ `Blue-Moon.zip` và `clouds.png` lấy từ `Cloudier Clouds.zip`. `audio/weather/rain.wav` là đoạn lặp nối chồng 0,65 giây từ bản thu `Kao - Minecraft Vanilla Rain Sounds Sample.mp3` đi kèm.
  - **Mob và đạn đạo:** 41 PNG mob nhập nguyên byte từ `minecraft_entities_assets.zip` dưới `textures/entity/<nhóm>/<tên>.png` (riêng `skeleton/skeleton.png` 64 × 32 dùng trực tiếp ảnh gốc từ Minecraft 1.21.11), gồm 33 ảnh nền và 8 ảnh lớp phủ, mắt hoặc hoa văn. Ảnh mob có kích thước từ 48 × 32 đến 128 × 128. Hai ảnh đạn chuẩn Minecraft gồm `projectile/arrow.png` (32 × 32, thân tên, đầu sắt và lông đuôi chữ thập) và `projectile/potion.png` (16 × 16 từ sprite `potion_healing.png`). Cung, giáo và lọ cầm tay dùng chung `equipment/held.png` (128 × 80), gồm bốn khung kéo cung của Java 26.3 ở hàng dưới và hai sprite 16 × 16 chuẩn Minecraft của giáo sắt (`iron_spear.png`) và lọ thuốc (`potion_healing.png`). `textures/item/` chứa 56 PNG 16 × 16 lấy chuẩn Minecraft 1.21.11 (gồm vật phẩm rơi của mob, công cụ tương tác, 6 khối len, 2 mũi tên tẩm thuốc và 4 lọ thuốc Witch được ghép nhuộm màu). `audio/sfx/mob/<id>/<sự_kiện>{1..4}.wav` chứa 205 tệp mono PCM 16 bit ở 22.050 Hz, trong đó `skeleton/` dùng đầy đủ 3 mẫu `ambient`, 4 mẫu `hurt`, `death` và 4 mẫu `step` nguyên bản từ chỉ mục tài nguyên Mojang. Hai mẫu bổ sung trong thư mục shared dùng OGG nguyên bản từ chỉ mục tài nguyên Mojang của Java 26.3. Bảng dưới ghi các đường dẫn classpath theo nhóm.
- **Kiểm thử:** Gradle `regressionChecks` chạy các ca CPU về block, vật lý, raycast, điều khiển, mesh, terrain, ngày đêm, thời tiết, sát thương, hiệu ứng, tài nguyên mob, chiều UV không lật gương, tỷ lệ texel 1:1, kích thước 3 tệp đạn/trang bị, cây khớp, âm thanh theo khoảng cách, bảng rơi vật phẩm và tương tác rơi đặc biệt của 23 loài mob (`MobLootChecks`). Các ca mob xác minh đủ 24 cấu hình, ưu tiên goal, cháy nắng, trọng số, phân vùng tuyết, cap, khám phá chunk, despawn, đạn, thú cưỡi và chuyển quyền điều khiển Camel Husk. `check` gọi task này. Trình biên dịch dùng `options.release = 17`.

| Nhóm dưới `textures/entity/` | Tên PNG (bỏ đuôi `.png`) |
| --- | --- |
| `zombie`, `zombie_villager`, `skeleton` | `zombie`, `husk`; `zombie_villager`; `skeleton`, `stray`, `stray_overlay`, `parched` |
| `creeper`, `spider`, `enderman`, `witch` | `creeper`; `spider`, `spider_eyes`; `enderman`, `enderman_eyes`; `witch` |
| `cow`, `sheep`, `pig`, `chicken` | `temperate_cow`; `sheep`, `sheep_fur`; `temperate_pig`; `temperate_chicken` |
| `horse` | `donkey`; `horse_white`, `horse_creamy`, `horse_chestnut`, `horse_brown`, `horse_black`, `horse_gray`, `horse_darkbrown`; `horse_markings_white`, `horse_markings_whitefield`, `horse_markings_whitedots`, `horse_markings_blackdots` |
| `rabbit`, `camel`, `goat`, `bear` | `gold`, `white`, `white_splotched`; `camel`, `camel_husk`; `goat`; `polarbear` |
| `wolf`, `fox`, `projectile` | `wolf_snowy`, `wolf_snowy_angry`; `snow_fox`, `snow_fox_sleep`; `arrow`, `potion` |
| `equipment` | `held` |

- **Đối chiếu ZIP tham chiếu:** Texture 1.21.11 có các biến thể trên và trang bị ở nhánh `assets/minecraft/textures/`. Các đường dẫn khác tên đích gồm `entity/sheep/sheep_wool.png`, `entity/spider_eyes.png`, `entity/witch.png`, `entity/projectiles/arrow.png`, `item/potion.png`, `item/bow*.png` và `item/iron_spear_in_hand.png`. Âm thanh mob nhập từ các thư mục `<tên mob>/sfx/` trong `minecraft_entities_assets.zip`, gồm cả nguồn bổ sung cho Camel, Camel Husk và Parched. Mỗi sự kiện dùng tối đa hai mẫu nguồn đầu theo thứ tự đánh số. Nếu ZIP chỉ có một mẫu, cả hai tệp WAV dùng mẫu đó. Java 26.3 cung cấp random/bow cho tiếng bắn và item/spear/use cho lúc hạ giáo chuẩn bị lao. Creeper im lặng lúc chờ. Creeper, Enderman, cáo tuyết và Witch phát bước theo nhóm vật liệu của khối dưới chân với âm lượng cơ sở 0,15.

---

### 2. Kiến Trúc Mã Nguồn Hiện Tại (111 File Java Chính, 24 File Kiểm Thử)
- **`com.voxelgame` (2 file):** `Main.java` khởi chạy desktop. `VoxelGameApp.java` quản lý Game Loop, sự kiện đầu vào, Pause Menu, đào/đặt block, uống `Milk Bucket` và vòng đời tài nguyên.
- **`com.voxelgame.engine` (34 file):**
  - `Camera.java` quản lý góc nhìn, Dynamic FOV và ma trận. `Raycast.java` duyệt DDA và kiểm tra hộp chọn có thể tái sử dụng kết quả.
  - `Texture.java` dựng atlas khối 128 × 80 bằng Pixmap. `ItemTextureAtlas.java` dựng atlas 128 × 128 (8 × 8 ô 16 × 16) cho 56 vật phẩm độc lập. `ShaderProgram.java` quản lý GLSL 330 cho 3D và UI. `Chunk.java` lưu ID và metadata cấp phát theo nhu cầu, dựng mesh đục/khoét alpha và mesh trong suốt.
  - `World.java` quản lý chunk, thực thể, gọi bộ sinh, chuyển atlas cho `ItemEntity` và điều phối kết xuất. `BlockUpdateQueue.java` quản lý FIFO tọa độ lân cận. `TranslucentChunkSort.java` sắp xếp mesh nước/băng theo khoảng cách.
  - `BiomeType.java`, `TerrainNoise.java`, `TerrainGenerator.java` định nghĩa biome, noise tất định và các lượt sinh thế giới. `DesertPyramid.java` chọn dấu chân khô và dựng kim tự tháp.
  - `UIRenderer.java` vẽ khung ngắm, HUD, hiệu ứng xương rồng và Pause Menu. `AudioManager.java` phát nhạc nền theo vòng xáo, chọn hiệu ứng âm thanh và giải phóng tài nguyên OpenAL.
  - `WorldTime.java` theo dõi đồng hồ và quỹ đạo thiên thể. `WeatherManager.java` điều khiển chu kỳ, cường độ cục bộ, âm lượng mưa và các lượt tích tuyết. `SkyRenderer.java` vẽ vòm trời, thiên thể và mây. `WeatherRenderer.java` vẽ hai lô mưa và tuyết. `EnvironmentMesh.java` chia sẻ định dạng đỉnh và bộ đệm VAO/VBO cho các lượt vẽ này.
  - `MobAppearance.java` đăng ký mẫu và các lớp ảnh. `MobTextureKey.java` định danh đường dẫn. `MobTextureManager.java` sở hữu và giải phóng cache ảnh. `MobModel.java`, `ModelPart.java` và `MobModels.java` dựng cây hộp với UV và hoạt ảnh. `MobVisual.java` và `MobVisualState.java` truyền hướng, bước, tư thế, trạng thái cắt lông cừu và biến thể của cá thể cho `MobRenderer.java`. `MobSoundEvent.java` và `MobSoundBank.java` cung cấp danh mục âm thanh hỗ trợ 2–4 biến thể mỗi sự kiện.
  - `MobSpawner.java` quản lý nhịp sinh bầy và despawn. `MobSpawnTable.java` lưu bảng trọng số và cỡ bầy. `SnowSubZone.java` phân vùng sinh thú theo địa hình ở Snowy Plains.
- **`com.voxelgame.entity` (36 file):**
  - `Entity.java` chứa tọa độ, vận tốc và quét AABB theo trục. `LivingEntity.java` thêm HP, giáp, hiệu ứng, lửa, sát thương rơi, vòng đời chết, xóa hiệu ứng và liên kết ghế hai chiều. `DamageSource.java`, `StatusEffectType.java` và `StatusEffectInstance.java` định nghĩa nguồn sát thương cùng các khe hiệu ứng. `Player.java` kế thừa `LivingEntity`, xử lý điều khiển, sinh tồn, đòn đánh tay không, bảo vệ mép, bước tuyết, quán tính trên băng và phản hồi xương rồng.
  - `Item.java` định nghĩa Flyweight cho `Item.NONE`, mọi `Block` và 56 vật phẩm độc lập chuẩn Minecraft 1.21.11. `Hotbar.java` quản lý 9 ô cho cả `Block` và `Item`. `ItemEntity.java` quản lý vật phẩm rơi và mesh dùng chung (`SHARED_MESHES`, `SHARED_ITEM_MESHES`). `FallingBlockEntity.java` quản lý trạng thái cát/sỏi đang rơi.
  - `Block.java` đăng ký 27 singleton vào bảng 256 ID tra theo byte không dấu. `PlantBlock.java` chia sẻ hình học và luật nền thực vật. Các lớp block còn lại được liệt kê trong bảng bên dưới. Bốn hoa dùng chung `FlowerBlock.java`.
- **`com.voxelgame.entity.mob` (34 file):** `MobEntity.java` mở rộng `LivingEntity` với vật lý, hoạt ảnh, phản ứng theo cá thể, kích hoạt bảng rơi đồ khi chết và tương tác đốt bằng `Flint and Steel`. `MobLootTable.java` hiện thực bảng rơi đồ chuẩn Minecraft 1.21.11 cho 23 loài. `MobType.java`, `MobCategory.java` và `MobAttitude.java` định nghĩa danh mục 24 cấu hình và 24 factory, gồm hai cấu hình sinh thỏ. `MobGoal.java`, `MobGoalSelector.java` và `MobGoals.java` điều phối hành vi. `SimpleGridNavigator.java` chọn hướng tránh vật cản. `MountableMob.java` xử lý ghế, điều khiển, thuần hóa và tìm chỗ xuống. `Horse.java`, `Donkey.java`, `Camel.java` và `CamelHusk.java` bổ sung chỉ số, hành vi và đòn tấn công riêng. Ba cung thủ và Witch dùng chung `RangedMob.java` để ngắm, kiểm tra tầm nhìn và tính vận tốc đạn.
- **`com.voxelgame.entity.projectile` (5 file):** `ProjectileEntity.java` xử lý trọng lực, tuổi thọ, quét va chạm và trạng thái vẽ. `ArrowEntity.java` cùng `ArrowType.java` xử lý tên. `ThrownPotionEntity.java` cùng `PotionType.java` xử lý vùng vỡ và hiệu lực thuốc.
- **Kiểm thử CPU (24 file):** `VoxelRegressionChecks.java` gọi `MobSpawnerChecks.java` để chạy `MobSpawnTableChecks.java`, `MobPhysicsChecks.java` và `MobGoalChecks.java`. `HostileMobChecks.java` gọi `ProjectileChecks.java`, `CloseHostileChecks.java` và `RangedHostileChecks.java`. `PassiveAndNeutralMobChecks.java` xác minh factory, đội hình gấu và gọi `PlainsAnimalChecks.java`, `RabbitFoxChecks.java` và `NeutralSnowChecks.java`. `MountAndCamelHuskChecks.java` kiểm tra factory, sinh đàn ngựa và cụm kỵ sĩ thật, rồi gọi `EquineMountChecks.java`, `CamelMountChecks.java` và `CamelHuskBehaviorChecks.java`. `MobLootChecks.java` xác minh 56 texture vật phẩm, texture/SFX Skeleton gốc và toàn bộ cơ chế rơi đồ của 23 loài mob. Mọi lớp đều nằm trong `src/test/java/com/voxelgame/engine/`.

---

### 3. Hệ Thống Block (27 Loại) & Cơ Chế Đào / Rơi Item

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

- Metadata dùng 0–7 cho một đến tám lớp tuyết, 0/1 cho nửa dưới/trên của Tall Grass. ID cũ 0–8 giữ nguyên. Ô mặc định không cần mảng metadata riêng. Chiều cao vẽ tuyết là số lớp × 0,125. Va chạm của lớp 2–7 thấp hơn hình vẽ một lớp. Tám lớp va chạm cao 1 block.
- Mỗi 50 ms, World lấy ba mẫu ngẫu nhiên trên mỗi section có dữ liệu trong bán kính mô phỏng 128 block theo phương ngang. Grass Block lộ sáng ban ngày thử tối đa bốn ô trong vùng ngang ±1 và dọc −3 đến +1. Dirt lộ sáng dưới cây thấp hoặc một lớp tuyết có thể mọc cỏ. Block đổi trạng thái dùng hàng đợi cập nhật và đánh dấu mesh qua biên chunk.
- Khi sửa block, hàng đợi xử lý ô vừa đổi, sáu ô chung mặt và các phụ thuộc ngoài nhóm này. Mía kiểm tra nước ở cao độ nền. Snow Layer tan thành Air và Ice tan thành Water khi có Lava trong bán kính Manhattan 2. Cát/sỏi tạo thực thể đúng một lần khi mất nền, rồi đặt lại hoặc rơi item tùy mặt đáp.
- Tia chọn được mọi block trừ Air, Water và Lava. Chỉ block có hộp va chạm thực mới cản chuyển động. Chữ X của cây vẽ hai phía và không có hộp vật lý.

---

### 4. Gameplay & Hệ Thống Điều Khiển
- **Di chuyển & Xoay góc nhìn:** `W, A, S, D` di chuyển 4 hướng trên mặt phẳng ngang XZ theo hướng `yaw`; di chuột xoay góc nhìn 360° với độ nhạy `MOUSE_SENSITIVITY = 0.12f` và giới hạn `pitch` trong `[-89.0°, 89.0°]`[cite: 1].
- **Nhảy & Bơi (`Space` 1 lần):** Nhảy khi `onGround == true` (`JUMP_VELOCITY = 8.6f`)[cite: 1]; khi đang trong Nước hoặc Lava sẽ bơi nổi lên với vận tốc dọc `4.2f`[cite: 1].
- **Chế độ Bay (nhấn đúp `Space` trong tối đa `0.30s`):** Bật/tắt Fly Mode. Khi bay, trọng lực tắt, giữ `Space` để bay lên với tốc độ cơ bản `7.0` block/s nhân với mức `F1`. Giữ cả hai phím thì ưu tiên `Space`. Thả cả `Space` và `Left Shift` để giữ độ cao. Tốc độ ngang là `1.8x`, nhân thêm `1.35x` khi chạy nhanh. Chạm mặt đất sẽ tự thoát Fly Mode.
- **Hạ độ cao, thoát bay và rón rén (`Left Shift`):**
  - Khi đang bay, giữ `Left Shift` để hạ độ cao với tốc độ cơ bản `4.0` block/s nhân với mức `F1`. Nhấn đúp `Left Shift` trong tối đa `0.30s` để thoát bay và bật lại trọng lực.
  - Khi đứng/đi trên mặt đất: bật **Sneak**, giảm tốc độ còn `0.5x`, nội suy mượt tầm mắt Camera từ `1.62f` xuống `1.45f` (tốc độ nội suy `deltaTime * 14.0f`), và kích hoạt **giữ mép block** (`preventFallingOffEdgeX/Z` theo bước `0.02f`) không cho rơi xuống vực[cite: 1].
- **Chạy nhanh (`Left Ctrl`):** Bật **Sprint** khi đang di chuyển ngang và không Sneak; tăng `1.5x` tốc độ đi bộ kèm hiệu ứng mở rộng Dynamic FOV[cite: 1].
- **Đổi tốc độ kiểm tra địa hình (`F1`):** Mặc định `1x`. Mỗi lần bấm chuyển đúng một mức theo vòng `1x → 3x → 5x → 10x → 25x → 50x → 1x`, kể cả trong Pause Menu. Mức đã chọn giữ nguyên khi nhả phím hoặc giữ phím và chỉ đổi ở lần bấm tiếp theo. Hệ số áp dụng cho mọi hướng di chuyển ngang và cả tốc độ bay lên, hạ độ cao trong Fly Mode.
- **Ghé tâm biome (`Ctrl+C` khi đang chơi):** Dịch chuyển lần lượt qua Snowy Plains, Forest, Plains, Flower Forest, Desert rồi lặp lại. Tìm mặt đỡ khô ngay tâm hoặc trong phạm vi 32 block gần tâm của cùng biome, đặt chân trên mặt đỡ, xóa vận tốc và tắt chế độ Bay. Nếu không có nền hợp lệ, người chơi giữ nguyên vị trí và lần nhấn sau chuyển sang biome kế tiếp. Phím tắt dùng được với Ctrl trái hoặc phải.
- **9 Ô Hotbar (`1`–`9` hoặc Cuộn chuột):**
  - `SLOT_COUNT = 9`, `MAX_STACK_SIZE = 64` (hỗ trợ cả `Block` lẫn `Item` độc lập với giới hạn stack riêng: 64, 16 cho `Egg`/`Ender Pearl`/`Bucket`, 1 cho công cụ/vũ khí/đĩa nhạc/lọ thuốc/sừng dê/`Milk Bucket`).
  - Cuộn chuột lên giảm chỉ số ô, cuộn xuống tăng chỉ số ô (xoay vòng modulo 9)[cite: 1].
  - Trang bị khởi đầu: Ô 1 `Dirt x16`, Ô 2 `Grass Block x16`, Ô 3 `Stone x16`, Ô 4 `Oak Log x12`, Ô 5 `Oak Leaves x16`, Ô 6 trống, Ô 7 `Shears x1`, Ô 8 `Flint and Steel x1`, Ô 9 `Bucket x1`.
  - Cơ chế nhặt đồ (`addItem`): ưu tiên cộng dồn vào các ô có sẵn cùng loại chưa đầy giới hạn stack, sau đó mới xếp vào ô trống đầu tiên từ trái sang phải[cite: 1].
  - Tương tác vật phẩm cầm tay (Click Chuột Phải): cầm `Flint and Steel` chuột phải vào mob bất kỳ để đốt cháy (8 giây); cầm `Shears` chuột phải vào `Sheep` chưa cắt lông để cắt 1–3 khối len; cầm `Bucket` chuột phải vào `Cow` hoặc `Goat` trưởng thành để lấy `Milk Bucket`; cầm `Milk Bucket` chuột phải để uống, xóa toàn bộ hiệu ứng trạng thái và nhận lại `Bucket`.
- **Đào Block (Giữ Chuột Trái):**
  - Tầm với `RAYCAST_REACH = 5.0f`[cite: 1].
  - Tích lũy `miningElapsedTime` cho đến khi `>= hardness` sẽ đổi ô thành `Block.AIR` hoặc Water khi đào Ice có nền phù hợp, sau đó gọi luật rơi vật phẩm tại tâm block `(x + 0.5, y + 0.5, z + 0.5)`[cite: 1].
  - Nhả chuột, nhìn sang ô khác, hoặc ngắm vào khối không phá được sẽ reset tiến trình về `0`[cite: 1].
- **Đặt Block (Click Chuột Phải):**
  - Trước khi đặt block, gọi điểm móc tương tác trên thực thể sống ngay trước mặt trong tầm 5 block, có xét vật cản. Khi tương tác không sử dụng cú bấm, lần lượt thử tăng lớp tuyết đang ngắm, đặt đè ô có thể thay thế rồi đặt ở ô kề mặt trúng. Kiểm tra nền, biên, ô phụ và giao cắt hộp người chơi. Chỉ trừ 1 item sau khi đặt thành công.
- **Đánh cận chiến (Click Chuột Trái):** Chọn thực thể sống gần nhất trong tầm 3 block và nằm trước hộp va chạm địa hình. Cú bấm trúng thực thể dùng cho chiến đấu. Đánh tay không gây 1 HP mỗi 0,6 giây và đẩy lùi theo hướng nhìn ngang. Đòn trong lúc đang rơi ngoài chất lỏng và ngoài Fly Mode nhân 1,5 lần sát thương. Khi cú bấm không trúng thực thể, thao tác giữ chuột bắt đầu đào theo quy tắc trên.
- **Sinh tồn:** Người chơi có 20 HP và 20 điểm đói. Khi đói còn ít nhất 18 điểm, mỗi 4 giây đang bị thương hồi 1 HP và tiêu 1 điểm đói. Va xương rồng gây 1 HP, chạm dung nham gây 4 HP theo cửa sổ miễn sát thương và bật cháy 15 giây. Hết HP hồi sinh tại vị trí an toàn, đầy HP và độ đói, xóa hiệu ứng, lửa và vận tốc, tắt bay.
- **Vòng đời chiến đấu:** Sát thương hợp lệ kích hoạt 0,5 giây miễn sát thương lặp và nháy bị thương. Giáp tự nhiên giảm sát thương vật lý tùy độ mạnh của đòn. Lửa gây 1 HP mỗi giây khi qua cửa sổ miễn sát thương và tắt khi chạm nước hoặc đứng ngoài trời mưa. Thực thể chết thông thường tồn tại 1 giây trước khi World dọn.
- **Hiệu ứng trạng thái:** Mỗi loại dùng một khe tái sử dụng. Hiệu ứng mới thay thế khi mạnh hơn hoặc dài hơn. Slowness giảm tốc ngang 15% mỗi cấp. Weakness trừ 4 HP cận chiến mỗi cấp, giới hạn dưới 0 HP. Poison gây tối đa 1 HP mỗi 1,25 giây và giữ mục tiêu ở ngưỡng tối thiểu 1 HP, undead miễn nhiễm. Hunger làm mất 0,5 điểm đói mỗi giây mỗi cấp.
- **Tạm dừng & Thoát (`ESC`):**
  - Nhấn `ESC` lần 1: bật `isPaused`, reset tiến trình đào, mở con trỏ chuột (`GLFW_CURSOR_NORMAL`), đóng băng cập nhật thực thể[cite: 1].
  - Nhấn `ESC` lần 2 khi đang Pause: đặt cờ đóng cửa sổ và thoát hoàn toàn khỏi game[cite: 1].
- **Kiểm thử môi trường khi đang chơi:** `F6` đảo quang đãng và giáng thủy, `F7` tới bình minh ở lần bấm đầu rồi lần lượt tới trưa, hoàng hôn, nửa đêm và bình minh hôm sau. `F8` bật/tắt tua đồng hồ 20×.

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
  - **Plains, Forest & Flower Forest:** Bedrock → Stone → 3–4 Dirt → Grass Block ở chỗ thoải. Dốc vừa lộ đá xen sỏi, dốc lớn lộ đá. Perlin quyết định trảng cỏ và cụm rừng với sồi thường, sồi cổ thụ, bụi sồi. Bốn trường Simplex tạo cụm riêng cho từng loài hoa. *(Ghi chú: tỉ lệ sinh cây, hoa, hồ và mob riêng của Forest/Flower Forest sẽ được chuẩn hóa hoàn chỉnh ở Giai đoạn 2 và 3.)*
  - **Desert:** Bedrock → Stone → 3–4 Sandstone → 3–5 Sand ở chỗ thoải. Dốc vừa và dốc lớn lộ sandstone, bờ sông sa mạc dùng cát có lớp đệm sandstone. Các cột Desert không sinh sỏi, tuyết hoặc băng. Có cactus cao 1–3 block, bụi khô, ốc đảo có cọ và hồ Lava. Một kim tự tháp giật cấp có nền móng sandstone, lối vào, sảnh cột, cầu thang và phòng ngầm có rương trang trí.
  - **Snowy Plains:** Nền đá và sỏi lộ trên dốc, cỏ thoải phủ Snow Layer thường 1 lớp, có vùng 2–3 lớp và Snow Block. Đỉnh trên Y=110 có lớp Snow Block. Vân sam tán rộng ở vùng thấp, thông nhọn trên sườn và thông lùn thưa gần giới hạn cây. Tuyết đọng trên tán lá thoáng và tránh dung nham.
  - **Sông và hồ:** Sông uốn theo dải noise, có mực nước Y=28, bờ cát hoặc sỏi có nền đỡ và mía ở ô sát nước hợp lệ. Mặt sông và hồ trong vùng lạnh đóng băng. Hồ chỉ sinh ở vùng thấp có mực hồ tối đa Y=48 và chênh cao toàn dấu chân tối đa 3 block, tránh dải sông, công trình và vùng an toàn của người chơi.
  - Hồ dung nham chọn vật liệu theo biome ở từng cột. Desert có bờ Sand và lót Sandstone. Plains và Snowy Plains có bờ Grass Block và lót Dirt. Quy tắc tránh băng và tuyết gần dung nham áp dụng cả trên bờ.
  - Bộ đệm độ cao cột dùng `short[]`, mã biome và cờ sinh dùng `byte[]`. Bộ sinh tính độ cao trước, chọn dấu chân kim tự tháp, ghi địa tầng và nước theo dải cột được cắt tại biên chunk, rồi sinh hồ, băng/tuyết, công trình, cây và thực vật. Hai bộ đệm cột được bỏ sau khởi tạo. ID block trong chunk tiếp tục chiếm một byte.

#### Vật lý, AI và lượt sinh mob

- **Cập nhật cá thể.** `MobEntity` quét AABB theo X → Z → Y, nhảy qua bậc tối đa 1 block khi có khoảng trống và có thể được cấu hình bước thẳng tối đa 1,5 block cho thú cưỡi. Điều hướng kiểm tra vài bước quanh hướng đích, tránh Lava, Cactus và mép rơi sâu hơn 3 block. Chicken có ngưỡng điều hướng 8 block và Goat có ngưỡng 13 block. Mob có thể tự nổi trong Water. Mob nhạy nắng bắt lửa với thời lượng tối thiểu 2 giây khi `sunY() > 0,05`, lộ thiên, khô và không có mưa tại cột hiện tại. Hoạt ảnh theo hướng thân, hướng đầu và quãng di chuyển thực.
- **Mục tiêu AI.** Số ưu tiên thấp được chọn trước. Các kênh di chuyển, nhìn và nhảy điều phối riêng. Bộ dùng chung gồm nổi, tìm nước hoặc mái che, hoảng 3–5 giây ở tốc độ ×1,25, lánh thực thể, đuổi và đánh cận chiến theo hồi chiêu, đi theo vật phẩm trên Hotbar, đi lang thang trong bán kính 9 block và nhìn người chơi hoặc nhìn quanh. Goal trú nắng nhả kênh ngay khi mob vào bóng râm, chạm nước hoặc hết phơi nắng. Zombie và Zombie Villager ưu tiên mục tiêu sống trong 8 block khi đang cháy ngoài nắng. Skeleton và Stray tránh Wolf trong 6 block. Các loài chỉ sinh tự nhiên khi factory được đăng ký.
- **Bảng sinh.** Monster có tổng trọng số 415 trong mỗi biome. Creature dùng tổng 46 ở Plains, 13 ở Desert và 11/24/9 lần lượt ở đồng bằng tuyết, Grove và sườn núi tuyết. Chọn theo trọng số rồi thử đặt các thành viên ở điểm gần nhau, cùng biome và phân vùng thú tuyết. Snowy Slopes/Peaks ưu tiên Y ≥ 62, hoặc nền đá, sỏi hay Snow Block từ Y=48 với chênh cao tối thiểu hai block ở cột kề. Grove nhận diện vân sam trong cầu bán kính 10 block ở Y từ 30 đến 61. Phần còn lại là Snowy Plains Flat.
- **Vị trí sinh.** Vành hoạt động cách chân người chơi 24–128 block theo khoảng cách 3D. Monster cần tick 13.000–23.000, mặt trời thấp hơn −0,15, hoặc mái che rắn. Husk, Parched, Stray và Camel Husk cần lộ thiên. Creature ở Plains cần Grass Block ban ngày. Thỏ Desert dùng Sand hoặc Grass Block. Camel dùng Sand và ưu tiên các cột cát liền kề cùng cao độ. Snowy Plains dùng nền theo loài. Nước, Lava, Cactus, lá và Bedrock bị loại. Chỉ Polar Bear dùng nền Ice. Cả hộp va chạm phải nằm trong bản đồ và có khoảng trống. Một lượt sinh Husk ở mặt đất Desert ban đêm có 10% cơ hội tạo Camel Husk, Husk cầm giáo sắt và Parched khi còn ít nhất ba suất quái và đủ khoảng trống cho cả cụm.

| Cấu hình | Plains | Desert | Snowy Plains |
| --- | --- | --- | --- |
| Sheep | 12 (4) | — | — |
| Pig | 10 (4) | — | — |
| Chicken | 10 (4) | — | — |
| Cow | 8 (4) | — | — |
| Horse | 5 (2–6) | — | — |
| Donkey | 1 (1–3) | — | — |
| Rabbit | — | 12 (2–3) | — |
| Camel | — | 1 (1) | — |
| Snow Rabbit | — | — | Flat 10, Grove 8, Slopes/Peaks 4 (2–3) |
| Polar Bear | — | — | Flat 1 (1–2) |
| Wolf | — | — | Grove 8 (4) |
| Snow Fox | — | — | Grove 8 (2–4) |
| Goat | — | — | Slopes/Peaks 5 (1–3) |
| Spider | 100 (4) | 100 (4) | 100 (4) |
| Creeper | 100 (4) | 100 (4) | 100 (4) |
| Zombie | 95 (4) | 19 (4) | 95 (4) |
| Skeleton | 100 (4) | 50 (4) | 20 (4) |
| Enderman | 10 (1–4) | 10 (1–4) | 10 (1–4) |
| Zombie Villager | 5 (1) | 1 (1) | 5 (1) |
| Witch | 5 (1) | 5 (1) | 5 (1) |
| Husk | — | 80 (4) | — |
| Parched | — | 50 (4) | — |
| Stray | — | — | 80 (4) |
| Camel Husk | — | 10% trên lượt Husk đủ điều kiện (cụm 3 cá thể) | — |

Mỗi ô ghi trọng số rồi đến cỡ bầy trong ngoặc. Tỷ lệ chọn tính trên tổng trọng số trong cùng nhóm và phân vùng.
- **Dân số và chi phí.** Mỗi 0,25 giây, bộ sinh thử tối đa bốn điểm gốc cho quái và một điểm sinh bổ sung cho thú, rồi thử tối đa 16 ô lân cận cho từng bầy đã chọn. Cap định kỳ trong vùng hoạt động là 70 Monster và 10 Creature. Mỗi nhịp xét tối đa sáu cột chunk quanh người chơi. Cột mới đủ gần mặt đất có một lần bốc 10% trong phiên World để sinh thú, bỏ qua cap 10. Trạng thái khám phá dùng bit theo tọa độ chunk. Mob ngoài phạm vi 128 block tạm dừng AI nếu được giữ lại. Monster không persistent biến mất ngay khi vượt 128 block, hoặc có xác suất 1/800 mỗi tick mô phỏng 20 Hz sau 30 giây liên tục ở khoảng 32–128 block. Creature và cá thể persistent được giữ lại.

#### Đạn đạo và các loài thù địch

- **Đạn đạo.** Tên (tuổi thọ 8 giây) và lọ thuốc (6 giây) chịu trọng lực, quét hình va chạm của voxel và thực thể dọc đoạn bay, chọn va chạm gần nhất, có xét bán kính đạn. Chủ đạn, đồng loại và cá thể chung đội cưỡi được bảo vệ. Tên gây sát thương vật lý theo cấu hình, đẩy lùi và có thể gây Slowness I hoặc Weakness I trong 30 giây. Đạn lạc trúng quái ngoài đội khiến cả hai đổi mục tiêu. Lọ vỡ trong bán kính 4 block, áp dụng Slowness 90 giây, Poison 45 giây hoặc Weakness 90 giây. Harming gây tối đa 6 HP, Healing hồi tối đa 4 HP, với tác dụng đảo trên undead. Tác dụng tức thời giảm theo khoảng cách, còn trúng trực tiếp nhận toàn lực.
- **Zombie và Zombie Villager.** Có 20 HP, giáp 2, đi 2,3 block/s, đòn cận chiến 3 HP và phát hiện người chơi trong 35 block. Khi bị đánh, chúng gọi đồng loại trong 16 block. Có 5% cơ hội là con non, với nửa kích thước và tốc độ 3,45 block/s. Cả con non lẫn con trưởng thành cháy dưới nắng.
- **Husk.** Có chỉ số cận chiến của Zombie, hiển thị lớn hơn 6%, chịu nắng và gây Hunger I trong 7–14 giây. Sau 30 giây ngập đầu liên tục, Husk rung trong 15 giây rồi chuyển thành Zombie trưởng thành tại cùng vị trí.
- **Hình dáng và nhịp bước của mob.** Mô hình trưởng thành dùng đơn vị 16 pixel mỗi block với kích thước hộp và tỷ lệ texel vuông 1:1 chuẩn Minecraft (`heightScale = 1`, thỏ dùng tỉ lệ đồng đều 3 trục `0,6×`). Zombie và Skeleton cao 32 pixel, Creeper cao 26 pixel, Cow cao 25 pixel và Camel cao 44 pixel từ bàn chân tới đỉnh. Các cấu trúc đặc trưng dùng đúng hình học Minecraft (tay khoanh & mũ nghiêng 4 tầng Witch, lớp băng quấn `4×12×4` và sọ phụ của Parched, áo `4×12×4` của Stray, 8 chân 1 đốt `16×2×2` của Spider, đầu ngoài trượt lên lộ hộp hàm trong của Enderman, tai thỏ cao 5 pixel, đầu nghiêng `-55°` của Goat, cổ nghiêng `-30°` của Horse/Donkey). Biên độ chân theo quãng đường mỗi tick và lấy 40% giá trị mới sau mỗi 0,05 giây. Cung có hình nghỉ và ba hình kéo dây, đổi mức ở tick 13 và 18, trở về hình nghỉ ngay khi bắn. Hai vai nâng hoặc hạ trong 0,15 giây khi đổi trạng thái ngắm.
- **Skeleton, Stray và Parched.** Di chuyển 2,5 block/s, phát hiện trong 16 block, giương cung trong 20 tick khi đi ngang và lùi khi người chơi tới gần hơn 4 block. Skeleton và Stray có 20 HP, cháy nắng, dùng tên 3–4 HP và bắn khoảng mỗi 2 giây. Stray dùng tên Slowness. Parched có 16 HP, chịu nắng, miễn Weakness, dùng tên Weakness 2–3 HP và bắn khoảng mỗi 3,5 giây. Nhịp bắn rút ngắn khi ở gần.
- **Creeper.** Có 20 HP và tốc độ 2,5 block/s. Tiếp cận trong 16 block, bắt đầu phồng và chớp khi ở trong 3 block, rồi nổ sau 1,5 giây. Nếu mục tiêu vượt 7 block trong lúc chờ thì hủy. Vụ nổ bán kính 3 block gây tối đa 43 HP và đẩy lùi theo khoảng cách. Các ô phá được đổi qua hàng đợi cập nhật gameplay, giữ Bedrock và chất lỏng, kích hoạt cát hoặc sỏi mất nền và cây mất điều kiện bám.
- **Spider.** Có 16 HP, tốc độ 3 block/s và đòn 2 HP. Trung lập ngoài trời ban ngày, tấn công trong 16 block vào ban đêm hoặc dưới mái. Nhện bám tường khi va chạm ngang và vồ từ 2–4 block. Có 1% cơ hội sinh kèm cung thủ nếu đủ chỗ và cap, gồm Skeleton thông thường, Stray ngoài trời vùng tuyết hoặc Parched ngoài trời Desert.
- **Enderman.** Có 40 HP, tốc độ 3 block/s, tăng lên 4,1 khi giận và đánh 7 HP. Bị kích động khi trúng đòn hoặc khi tia ngắm của người chơi chạm vùng mắt không bị che trong 64 block. Chạm Water hoặc mưa Plains gây 1 HP theo nhịp, và Enderman tìm điểm đứng để dịch chuyển trong 32 block. Mũi tên được né trước khi gây sát thương. Enderman cũng có thể dịch chuyển trong trận, nhặt và đặt lại Dirt, Grass Block, Sand, Gravel và hoa.
- **Witch.** Có 26 HP, tốc độ 2,5 block/s và kháng 85% sát thương phép, gồm sát thương theo nhịp của Poison. Ném thuốc trong 10 block với nhịp khoảng 3 giây, ưu tiên Slowness ở khoảng từ 8 block, Poison nếu mục tiêu còn từ 8 HP, 25% cơ hội Weakness trong 3 block khi phù hợp, sau đó Harming. Tự uống thuốc trong 1,5 giây để hồi 4 HP, kháng lửa và Lava, thở dưới nước hoặc tăng tốc khi mục tiêu xa hơn 11 block.

#### Thú hiền lành và trung lập

| Loài | Nơi sinh, cỡ bầy | HP | Hitbox trưởng thành (rộng × cao, block) | Tốc độ cơ sở (block/s) |
| --- | --- | ---: | --- | ---: |
| Cow | Plains, 4 | 10 | 0,9 × 1,4 | 2,0 |
| Sheep | Plains, 4 | 8 | 0,9 × 1,3 | 2,3 |
| Pig | Plains, 4 | 10 | 0,9 × 0,9 | 2,5 |
| Chicken | Plains, 4 | 4 | 0,4 × 0,7 | 2,5 |
| Rabbit | Desert và ba phân vùng Snowy Plains, 2–3 | 3 | 0,4 × 0,5 | 2,7 |
| Goat | Snowy Slopes/Peaks, 1–3 | 10 | 0,9 × 1,3 | 2,5 |
| Polar Bear | Snowy Plains Flat trên tuyết hoặc băng, 1–2 | 30 | 1,4 × 1,4 | 2,5 |
| Wolf | Snowy Grove, 4 | 8 | 0,6 × 0,85 | 3,0 |
| Snow Fox | Snowy Grove, 2–4 | 10 | 0,6 × 0,7 | 3,5 |

- **Cow, Sheep, Pig và Chicken.** Bốn loài hiền lành chạy hoảng khi bị đánh. Bò tìm về đàn khi đi xa, dùng điều hướng tránh vách sâu và Lava, và cho vắt sữa bằng `Bucket`. Lợn dùng tiếng kêu theo nhịp. Cừu cúi đầu ăn Short Grass hoặc đổi Grass Block dưới chân thành Dirt qua cập nhật block của World, đồng thời mọc lại lông khi ăn xong; khi cắt bằng `Shears` rơi 1–3 len đúng màu và ẩn lớp len trên mô hình. Tỷ lệ màu len là trắng 81,836%, xám nhạt 5%, xám đậm 5%, đen 5%, nâu 3% và hồng 0,164%. Gà vỗ cánh khi rơi, giới hạn tốc độ xuống 1,7 block/s trong không khí, miễn sát thương rơi và gà trưởng thành đẻ 1 `Egg` mỗi 300–600 giây.
- **Rabbit.** Thỏ hiền lành sa mạc luôn vàng cát. Thỏ tuyết có 80% lông trắng mắt đỏ và 20% lông trắng đốm đen. Thỏ nhảy cóc cao gần hai block, tránh người chơi trong 8 block, sói trong 10 block và quái thù địch trong 4 block. Dandelion ở ô Hotbar đang chọn dụ thỏ theo người chơi.
- **Goat.** Dê trung lập chịu rơi thêm 10 block trước khi tính sát thương, cho vắt sữa bằng `Bucket` thành `Milk Bucket`. Dê chọn chỗ đáp để nhảy vượt địa hình đến 10 block và lấy đà húc mục tiêu đứng yên hoặc di chuyển chậm, gây 2 HP và đẩy lùi mạnh; khi húc trúng `Stone`, `Oak Log`, `Spruce Log` hoặc `Ice`, dê trưởng thành rơi 1 `Goat Horn` (tối đa 2 sừng). Nhịp húc thông thường 30–300 giây. 2% cá thể là dê hét, dùng âm thanh riêng và nhịp 5–15 giây.
- **Polar Bear.** Bầy hai con gồm một trưởng thành và một con non với hộp 0,7 × 0,7 block. Gấu bơi với đầy đủ tốc độ ngang. Khi bị người chơi đánh, gấu trưởng thành quanh đó nổi giận và gấu con chạy. Người chơi tới gần gấu con trong 4 block kích thích gấu trưởng thành dựng lên, gầm và lao tới. Đòn đánh gây 6 HP. Gấu đôi khi săn Snow Fox.
- **Wolf.** Sói tuyết đổi sang mắt đỏ, gầm và gọi sói trong 16 block cùng tấn công người chơi đã đánh trúng bằng tay hoặc tên. Mỗi đòn trúng cho cá thể bị đánh một thời gian giận mới từ 20–39 giây. Sói đuổi người chơi tối đa 32 block và thôi đuổi khi mục tiêu chết, thoát tầm hoặc hết giận. Sói săn thỏ, cáo, cừu, Skeleton và Stray với đòn 4 HP. Sói rũ nước khi lên bờ và nghiêng đầu nhìn người chơi gần.
- **Snow Fox.** Cáo lông trắng, 5% là con non. Ban ngày cáo tìm tuyết hoặc tán vân sam để cuộn ngủ, ban đêm hoạt động. Người chơi đứng thường đến gần có thể đánh thức cáo và khiến cáo chạy trong 12 block. Người chơi rón rén có thể tiếp cận mà cáo vẫn ngủ. Cáo lấy đà và vồ thỏ hay gà trong 16 block, gây 2 HP và có thể chúi vào tuyết mỏng khi hụt. Cáo nhặt một vật phẩm khối gần để ngậm và nhả ra khi chết.

- **Bảng rơi vật phẩm khi mob chết (`MobLootTable`).** Cá thể non không rơi vật phẩm tiêu chuẩn (trừ khối/vật phẩm đang ngậm của cáo hoặc Enderman). Khi mob chết trong lúc đang cháy hoặc bởi lửa/dung nham, các loại thịt sống và khoai tây hiếm chuyển sang phiên bản chín (`Steak`, `Cooked Mutton`, `Cooked Porkchop`, `Cooked Chicken`, `Cooked Rabbit`, `Cooked Cod`, `Cooked Salmon`, `Baked Potato`):
  - **Cow:** 0–2 `Leather`, 1–3 `Raw Beef` / `Steak`. **Sheep:** 1 len đúng màu (nếu chưa cắt lông), 1–2 `Raw Mutton` / `Cooked Mutton`. **Pig:** 1–3 `Raw Porkchop` / `Cooked Porkchop`. **Chicken:** 0–2 `Feather`, 1 `Raw Chicken` / `Cooked Chicken`. **Rabbit / Snow Rabbit:** 0–1 `Rabbit Hide`, 0–1 `Raw Rabbit` / `Cooked Rabbit`, 10% `Rabbit Foot` nếu bị người chơi giết. **Polar Bear:** 75% rơi 0–2 `Raw Cod` / `Cooked Cod`, 25% rơi 0–2 `Raw Salmon` / `Cooked Salmon`. **Horse / Donkey:** 0–2 `Leather`. **Camel Husk:** 2–3 `Rotten Flesh`. **Goat, Wolf, Camel:** không rơi đồ khi chết.
  - **Zombie / Zombie Villager / Husk:** 0–2 `Rotten Flesh`; 2,5% rơi `Iron Ingot`, `Carrot` hoặc `Potato`/`Baked Potato` khi bị người chơi giết; `Husk` cầm giáo sắt có thêm 8,5% rơi `Iron Spear` khi bị người chơi giết. **Skeleton / Stray / Parched:** 0–2 `Bone`, 0–2 `Arrow`, 8,5% rơi `Bow` khi bị người chơi giết; `Stray` thêm 50% rơi `Arrow of Slowness`, `Parched` thêm 50% rơi `Arrow of Weakness`. **Creeper:** 0–2 `Gunpowder` khi bị giết (không rơi khi tự nổ), và rơi 1 `Music Disc 13` hoặc `Music Disc Cat` nếu bị `Skeleton`/`Stray`/`Parched` bắn chết. **Spider:** 0–2 `String`, 1/3 rơi `Spider Eye` nếu bị người chơi giết. **Enderman:** 0–1 `Ender Pearl` và khối đang cầm. **Witch:** 1–3 lượt rơi 0–2 món chọn đều từ `{Glass Bottle, Glowstone Dust, Gunpowder, Redstone Dust, Spider Eye, Sugar, Stick}`, cộng 8,5% rơi đúng lọ thuốc đang uống (`Healing`, `Fire Resistance`, `Water Breathing`, `Swiftness`) nếu bị người chơi giết đúng lúc đang uống.

#### Thú cưỡi và kỵ sĩ sa mạc

- **Điều khiển.** Chuột phải để lên ghế trống nếu đủ khoảng trống. Người ngồi ghế trước dùng `WASD` theo hướng nhìn, `Ctrl trái` để chạy nhanh trên lạc đà và giữ rồi nhả `Space` để nhảy hoặc Dash. `Shift trái` tìm điểm đặt chân trống bên cạnh thú. Camera theo ghế sau mỗi lượt cập nhật vật lý. Thú chở người chìm và lội ở 40% tốc độ ngang, với trọng lực giảm trong nước. Khi nước ngập hơn 2 block với ngựa/lừa hoặc qua chiều cao 2,38 block của lạc đà, các ghế tự tách tại chỗ. Người chơi có thể tự bơi và thú mới nổi lên sau khi trống ghế.
- **Horse và Donkey.** Cả hai loài có một ghế, bước thẳng qua bậc 1 block, 15–30 HP và 20% cơ hội sinh con non. Cá thể hoang tăng 25 điểm thiện cảm sau một lượt cưỡi khoảng 2,2–3,5 giây và có thể hất người chơi xuống nếu có điểm đứng an toàn. Cá thể thuần hóa ở 100 điểm và được giữ trong World. Ngựa dùng hitbox 1,3965 × 1,6, tốc độ 4,8–14,5 block/s và nhảy cao 1,25–5,25 block. Một đàn có chung màu lông nền và từng cá thể chọn hoa văn độc lập. Lừa dùng hitbox 1,3965 × 1,5, tốc độ 7,5 block/s, nhảy cao 2,1 block, tai dài và bộ âm thanh riêng.
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
  - 6 floats/đỉnh: `[x, y, z, u, v, shade]` (`location 0: vec3 aPos`, `location 1: vec2 aTexCoord`, `location 2: float aShade`) quản lý qua `VertexAttributes` của libGDX.
  - Fragment Shader chọn màu từ `uTexture * uTintColor` hoặc `uTintColor`. Hình học thế giới và UI loại pixel có `alpha < 0.05`. Màu khối và thực thể nhân với độ lớn `vShade` và `uWorldLight`. Dấu âm của `vShade` đánh dấu dung nham tự phát sáng. `enable2DUIMode` đặt `uWorldLight = (1, 1, 1)`. `uEnvironmentMode` chọn xử lý vòm trời, mây, thiên thể hoặc giáng thủy.
  - Sương xa hòa màu RGB của block và thực thể với màu khí quyển cùng hướng nhìn. Khoảng sương nội suy từ 288–640 block khi quang tới 96–384 block khi giáng thủy đạt cường độ tối đa. Bộ kết xuất bỏ qua chunk nằm hoàn toàn ngoài tầm sương cộng biên 32 block và ngoài khoảng có thể che mây. Các lượt vẽ HUD, overlay và môi trường giữ quy tắc màu và alpha riêng.
  - Xóa tiền tố `prependVertexCode`/`prependFragmentCode` để giữ `#version 330 core` ở dòng đầu tiên.
- **Ánh sáng & Face Culling (`Chunk`):**
  - Directional Face Shading: `SHADE_TOP = 1.00f`, `SHADE_BOTTOM = 0.55f`, `SHADE_NORTH_SOUTH = 0.85f`, `SHADE_EAST_WEST = 0.72f`.
  - AO ở mỗi góc mặt hộp đọc hai ô cạnh và một ô chéo, kể cả qua biên chunk. Khối đục và tuyết 8 lớp che đầy, lá che một phần. Độ sáng theo góc đi qua `aShade`. Cách chọn đường chéo thống nhất giữa mesh có chỉ số và mesh sáu đỉnh mỗi mặt.
  - Khối đục che phần mặt khối kề được phủ kín. Lá khoét alpha cho thấy bề mặt phía sau khe. Xương rồng có bốn mặt ngang lùi 1/16. Mặt bên tuyết chỉ giữ phần vượt chiều cao lớp kề. Cây hình chữ X vẽ cả hai phía. Nước/băng bỏ mặt trong cùng loại và giữ phần lộ ra cạnh loại không đục khác. Mặt thoáng Water/Lava cao 0,88 nếu phía trên là Air.
  - Sửa ở biên đánh dấu các mesh chung mặt, cạnh hoặc góc bị ảnh hưởng, gồm cả ô chéo dùng cho AO và mặt chất lỏng.
  - Khối xương rồng, vật phẩm rơi và biểu tượng Hotbar dùng chung vùng UV đã bỏ viền trong suốt. Mặt bên lấy vùng 14 × 16 pixel, mặt trên và dưới lấy vùng 14 × 14 pixel từ PNG gốc.
- **Hai pha vẽ:** Chunk đục/khoét alpha ghi chiều sâu theo dải từ gần ra xa để giới hạn overdraw ở vùng nhiều cây. Thực thể đục cũng ghi chiều sâu. Nước/băng giữ depth test, tắt ghi chiều sâu, dùng blend `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` rồi khôi phục trạng thái. Heapsort tại chỗ sắp pha trong suốt từ xa đến gần theo bình phương khoảng cách mắt–tâm chunk và phá hòa theo tọa độ. Các mảng được cấp phát trước. Thứ tự giữa các mặt trong một chunk là xấp xỉ.
  - Vòm trời và thiên thể vẽ trước các chunk. Mây ở Y=140 vẽ sau địa hình đục và trước nước/băng. Mưa và tuyết vẽ sau thế giới với depth test và alpha blend. Các lượt môi trường tắt ghi độ sâu, khôi phục trạng thái sau khi vẽ và dùng lại mảng đỉnh.
  - **Mô hình mob:** Thực thể có `MobVisual` được vẽ sau vật phẩm và khối đang rơi, trước mây, nước và băng. Cầu bao tính từ cây hình học, có dư địa cho chuyển động và trang bị, được thử với frustum và tầm vẽ. Mẫu hình người, thú bốn chân, nhện, gà, thỏ, sói, cáo, ngựa/lừa, lạc đà dùng các hộp phân cấp 1:1 với phép đổi 16 pixel trên 1 block (giữ góc nghỉ `baseRotation` khi đặt lại tư thế); đạn đạo (`arrow`, `potion`) và trang bị cầm tay (cung, giáo, lọ thuốc Witch) dùng hình học phẳng hoặc chữ X vẽ hai mặt (`doubleSided`). Góc dọc ở gốc hỗ trợ hướng bay. Mắt tự phát sáng, lớp phủ khoét alpha hoặc hòa trộn alpha (`GL_BLEND` cho `OVERLAY`), sát thương chớp đỏ, tiến độ Fuse phồng và chớp trắng, hoạt ảnh chết nghiêng tối đa 90 độ. Lượt vẽ khôi phục `GL_BLEND`, `GL_CULL_FACE`, trả màu về trắng, tắt chớp và bind lại atlas khối.
  - **Tài nguyên mob:** Cache PNG riêng dùng Nearest/ClampToEdge và kích thước thật khi chuẩn bị UV. Ảnh được tải lên GPU ở định dạng RGBA8888, kể cả bốn hoa văn đốm ngựa thang xám có alpha bán trong suốt. Mô hình Minecraft dùng trải UV chuẩn không lật gương (hệ trục `-Z` trước, `+X` phải mob, `+Y` trên), với chiều UV riêng cho thân nằm ngang (`HORIZONTAL`), chi trái đối xứng (`MIRRORED`) và sprite phẳng (`FLAT_SPRITE`). Các bộ phận phẳng hoặc có khe alpha (chân gà, râu dê, đuôi lạc đà, lồng ngực xương, mũi tên, lọ thuốc, trang bị cầm tay) tắt `GL_CULL_FACE` cục bộ khi vẽ. Mắt thỏ trắng lấy trực tiếp từ PNG nguồn. Màu len cừu là hệ số RGB trên lớp `sheep_fur`, dùng chung một texture cho sáu màu. Cáo ngậm khối ở miệng và Enderman cầm khối giữa hai tay bằng lưới khối sẵn có gắn theo ma trận khớp tương ứng. Bộ nạp báo đường dẫn khi tệp thiếu, lỗi giải mã hoặc thiếu diện tích UV. Lớp nền dùng ảnh màu 16 × 16 khi gặp lỗi và lớp phủ lỗi được ẩn. Pixmap được giải phóng ngay sau khi tải. Mesh tĩnh chia sẻ theo hình học, UV, dạng hình học và lớp phát sáng, dựng trước cùng ngăn xếp ma trận. Ứng dụng giải phóng cache ảnh và mesh khi đóng.
- **Frustum culling và bộ nhớ đệm:** Dựng trước 5 × 5 cột quanh spawn, mỗi frame cập nhật tối đa 16 chunk giao khung nhìn, ưu tiên gần camera. Với mỗi mặt phẳng, thử góc xa nhất của hộp chunk theo pháp tuyến. Chunk sạch không có mặt lộ được bỏ qua cho đến lần đánh dấu dựng lại. Thu hồi cả hai mesh sau 120 frame ngoài khung nhìn và giữ dữ liệu voxel. Hai bộ đệm đỉnh và bảng UV dùng lại.
- **Texture Atlas:** Lưới 8 × 5 ô 16 × 16, kích thước 128 × 80 RGBA. Dùng Nearest, ClampToEdge và biên UV 0,0008. Tile 0–19 giữ chỉ số cũ, tile 20–32 dùng PNG ở mục 1, tile 33–35 dành cho rương. Xử lý một lần khi dựng atlas: nhuộm tông Plains cho cỏ/lá sồi/mía, tông xanh thông cho lá vân sam, giữ alpha nguồn của texture khoét và độ trong của Ice/Water. Mặt trên Grass Block dùng họa tiết xanh đậm nhạt theo từng pixel và có alpha 255. Nước và dung nham lấy khung đầu. Dùng pixel art dự phòng khi nguồn thiếu hoặc lỗi đọc và giải phóng các Pixmap sau dựng atlas.

- **Tối ưu Hiệu năng & Quản lý Bộ nhớ:**
  - Mesh chunk dùng VAO qua OpenGL 3.3. Với tối đa 16.384 mặt, mesh nén tại chỗ còn bốn đỉnh mỗi mặt và dùng chung mẫu chỉ số 16 bit cho hai tam giác. Mesh lớn hơn tải đủ sáu đỉnh mỗi mặt.
  - Tái sử dụng các `Matrix4` làm trường thành viên để tránh sinh rác GC trong Game Loop.
  - Dùng chung `Mesh` theo loại `Block` (`SHARED_MESHES`) và từng `Item` độc lập (`SHARED_ITEM_MESHES`) cho mọi `ItemEntity`.
  - `ItemTextureAtlas` đóng gói 56 texture vật phẩm 16 × 16 thành một tấm 128 × 128 (8 × 8 ô) và giải phóng qua `Disposable.dispose()`.
  - Giải phóng triệt để tài nguyên GPU qua `Disposable.dispose()` cho mọi lớp `Chunk`, `World`, `Texture`, `ItemTextureAtlas`, `ShaderProgram`, `ItemEntity`.

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
  - **Chỉ số sinh tồn:** Phía trên tên block và Hotbar, 10 tim bên trái hiển thị HP và 10 đùi thịt bên phải hiển thị độ đói. Mỗi biểu tượng có trạng thái đầy, nửa đầy và trống. Góc trên phải hiện tên hiệu ứng và số giây còn lại khi đang chơi. Nền và hai dải biểu tượng chia sẻ một mesh GPU được cập nhật khi chỉ số hiển thị hoặc kích thước cửa sổ đổi. Nhãn tái sử dụng `StringBuilder` và bộ đệm đỉnh của font 5 × 7.
  - **Chỉ số thú cưỡi:** Khi đang cưỡi, hàng trên cùng của Hotbar hiển thị HP của thú và lực Nhảy hoặc Dash. Thanh Dash màu xanh hiển thị thời gian hồi còn lại bằng tiến độ lấp đầy. Các thanh dùng bộ đệm giao diện có sẵn.
  - **Bộ đệm Hotbar:** Ô, viền, biểu tượng và chữ dùng chung mesh GPU. Mesh cập nhật khi đổi kích thước cửa sổ, ô đang chọn, vật phẩm hoặc số lượng trong ô.
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
- **Tiếng mưa:** `Music` phát lặp luồng WAV độc lập với nhạc nền. Âm lượng bằng mức trong Pause Menu nhân cường độ mưa cục bộ nhân hệ số mái che. Cường độ âm thanh trở về 0 khi rời các vùng có mưa (`Plains`, `Forest`, `Flower Forest`) hoặc mưa tạnh. Mái che bằng khối rắn hay lá giảm âm lượng về 40%. Luồng mưa dừng tại vị trí đang phát khi mở Pause Menu và tiếp tục từ vị trí đó khi bấm Resume.
- **Hiệu ứng vật liệu:** Các khối dùng nhóm mẫu cỏ/thực vật, sỏi/đất, cát, tuyết, đá, gỗ hoặc tiếng kính khi băng vỡ. Đào lâu phát tiếng gõ theo nhịp 0,25 giây. Phá và đặt thành công, bước chân, tiếp đất và cát/sỏi đáp đều chọn mẫu theo vật liệu. Bước chân dùng quãng đường đã đi và giảm âm lượng khi rón rén. Tiếng phá và cát/sỏi đáp giảm theo khoảng cách trong bán kính 16 block.
- **Hiệu ứng tương tác:** Nhặt đồ phát tiếng khi Hotbar nhận vật phẩm. Va xương rồng phát theo hồi chiêu của phản hồi va chạm. Có tiếng chạm nước, bơi, chìm/nổi qua mặt nước và âm nền khi mắt ở dưới nước. Âm nền dưới nước dừng trong Pause Menu. Nước và dung nham gần người chơi có một mẫu âm định kỳ, giảm theo khoảng cách.
- **Hiệu ứng mob:** 23 ID dùng 2–4 tệp WAV cho mỗi sự kiện riêng của loài thuộc nhóm `AMBIENT`, `HURT`, `DEATH`, `STEP` (riêng `skeleton` dùng 3 `ambient`, 4 `hurt`, 2 `death` và 4 `step` chuẩn Minecraft gốc). Các nhóm đặc thù gồm mồi nổ, nổ, bắn cung, ba tiếng Enderman, ném/uống thuốc của Witch, dê chuẩn bị húc, sói gầm, lạc đà lướt, Husk chuyển dạng và lao giáo. Nổ, cung và lao giáo nằm dưới `shared/`. Camel và Camel Husk có tiếng lướt riêng. Camel Husk dùng mẫu Camel nếu bộ mẫu riêng không nạp được. Âm lượng giảm tuyến tính từ tâm người chơi tới bán kính mặc định 16 block, 28 block cho dê hét và 32 block cho tiếng nổ hoặc Enderman hét. Cao độ nhận hệ số cá thể, ví dụ 1,5 cho con non, cùng độ lệch ngẫu nhiên nhẹ. Mẫu được nạp một lần từ classpath và giải phóng cùng `AudioManager`.
- **Vòng đời tài nguyên:** Mẫu ngắn được nạp một lần vào `Sound` và dùng lại khi phát. Âm nền dưới nước, tiếng mưa và bài nhạc hiện tại dùng `Music`. Bộ đếm, thứ tự bài và mẫu đã nạp được tái sử dụng trong vòng lặp. `dispose()` giải phóng các luồng và bộ đệm âm thanh. `cleanup()` ủy quyền cho `dispose()`.

---

### 9. Chu Kỳ Ngày–Đêm & Thời Tiết

- **Đồng hồ:** Một ngày có 24.000 tick, chạy 20 tick/giây và bắt đầu ở tick 1.000 (07:00). Bình minh, trưa, hoàng hôn và nửa đêm lần lượt ở tick 0, 6.000, 12.000 và 18.000. Trời tối hẳn quanh tick 13.000, sáng lại từ vùng tick 23.000–24.000. Pause đóng băng đồng hồ, mây và thời tiết.
- **Thiên thể và ánh sáng:** Mặt trời mọc ở Đông (`+X`), qua đỉnh `+Y` rồi lặn ở Tây (`-X`). Mặt trăng đối diện 180° và chọn một trong tám pha theo bộ đếm ngày. Hai ảnh hòa trộn cộng sáng và mờ dần khi giáng thủy tăng. Ánh sáng môi trường chuyển từ trắng ban ngày qua ấm nhẹ lúc mặt trời gần chân trời đến xanh đêm. Mưa/tuyết làm giảm tối đa 24% cường độ ánh sáng ban ngày. Mây trôi từ Đông sang Tây và hiện cả ban ngày lẫn ban đêm, với màu thay đổi theo giờ và cường độ giáng thủy.
- **Chu kỳ giáng thủy:** Quang đãng tự động kéo dài 10–150 phút (`600–9.000s` = `12.000–180.000` tick), đợt mưa/tuyết kéo dài 10–20 phút (`600–1.200s` = `12.000–24.000` tick) chuẩn Minecraft Java. Cường độ chuyển dần trong 5 giây. Mỗi cột trong bán kính 14 block dùng biome khí hậu để quyết định mưa ở `Plains`, `Forest`, `Flower Forest`, tuyết ở `Snowy Plains` hoặc không giáng thủy ở `Desert`. Khi đạt cường độ tối đa, lấy mẫu khoảng 35% cột mưa và 29% cột tuyết để giảm số dải bán trong suốt chồng lên nhau. Hạt mờ dần khi sát mắt hoặc gần rìa vùng vẽ. Đáy mỗi dải dừng trên bề mặt cao nhất của khối rắn, lá, tuyết hoặc chất lỏng. Mưa cuộn nhanh theo chiều dọc, tuyết rơi chậm và lắc ngang.
- **Tích tuyết:** Khi cường độ vượt 50%, chạy theo nhịp random tick (`0,05` giây) trên các cột chunk trong bán kính mô phỏng 128 block với xác suất `1/16` mỗi chunk/tick. Cột `Snowy Plains` lộ thiên đóng băng `Water` mặt trên thành `Ice` hoặc phủ tối đa 1 lớp `Snow Layer` trên nền hợp lệ (`snowAccumulationHeight = 1`, không tự chồng dày thêm). Mái che, hộp người chơi và Lava gần đó được kiểm tra trước khi thay block.
