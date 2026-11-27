# Bản Thiết Kế: Nâng Cấp VoxelGame Sát Minecraft 1.21.11 Bằng 100% Asset Sẵn Có

## 1. Mục đích & Lý do
- Đưa trải nghiệm chơi, chiến đấu, đồ họa và sinh tồn của **VoxelGame** tiến sát **Minecraft Java Edition 1.21.11 (Mounts of Mayhem)** bằng cách khai thác triệt để **100% tài nguyên đã đóng gói sẵn** trong `src/main/resources/` và bộ sprite chính chủ Minecraft 1.21.11 từ tệp `texture.zip` (`C:\Users\Admin\.gemini\antigravity\brain\505e56c1-4506-413e-a523-45dea8f3a875\.user_uploaded\media_1795711540126_d8019cf4.zip`, định dạng 7z giải nén bằng `tar -xf`), gồm:
  - Tài nguyên sẵn có: `56` texture vật phẩm 16×16, `equipment/held.png`, `water_still.png` (`16×512`, 32 frame), `lava_still.png` (`16×320`, 20 frame), `clouds.png` (`256×256`), `bow_shoot.ogg`, `spear_charge.ogg`, `underwater/*`, `liquid/*`, `damage/hit1..3.ogg`.
  - Tài nguyên trích từ `texture.zip`: `textures/block/stone.png`, `textures/block/bedrock.png`, `textures/block/fire_0.png`, `textures/block/fire_1.png`, `textures/block/destroy_stage_0..9.png`, `textures/entity/player/wide/steve.png`, toàn bộ HUD sprites (`textures/gui/sprites/hud/*`), `textures/gui/container/inventory.png` và `textures/gui/sprites/widget/*`.
  - **8 nhóm SFX `.ogg` tải nguyên byte từ Mojang Asset Index (`1.21.11`, `piston-meta.mojang.com` → `resources.download.minecraft.net`) vào `src/main/resources/audio/sfx/`:**
    - Lửa & Đốt: `fire/fire.ogg`, `fire/ignite.ogg`, `damage/burn1..3.ogg`
    - Nước gặp Lava & Dập lửa: `random/fizz.ogg`
    - Ăn & Uống: `random/eat1..3.ogg`, `random/burp.ogg`, `random/drink.ogg`
    - Mũi tên trúng đích: `random/bowhit1..4.ogg`
    - Thổi `Goat Horn`: `item/horn/call0.ogg`
    - Ngạt nước & Rơi vỡ: `damage/drown1..4.ogg`, `damage/fallbig.ogg`, `damage/fallsmall.ogg`
    - Đánh cận chiến: `entity/player/attack/crit.ogg`, `entity/player/attack/knockback1..2.ogg`
    - Len & `Shears`: `dig/cloth1..4.ogg`, `mob/sheep/shear.ogg`
- Tổ chức kiến trúc theo mô hình **module hóa tách file**, cho phép chạy **3 git worktree song song** qua quy trình Superpowers (`subagent-driven-development`) trong một phiên làm việc sau khi hồi phục hạn mức token mà giảm thiểu xung đột khi gộp nhánh.

---

## 2. Mục tiêu, Phi mục tiêu & Điều cần tránh

### 2.1. Mục tiêu (Goals)
Triển khai đầy đủ **22 hạng mục** thuộc Task 0 và 3 nhóm chức năng chính (không cần tách Track D vì 4 yêu cầu bổ sung trùng khớp hoàn toàn phạm vi sở hữu file của Task 0, Track A, B, C):
- **Task 0 — Nền tảng & Sửa lỗi Texture gốc:**
  - Sửa triệt để lỗi **`Stone` và `Bedrock` hiển thị màu trắng** (do PNG Grayscale `colorType=0` bị libGDX `Pixmap` nạp vào kênh Alpha và bị `TileStyle.OPAQUE` ghi đè `a=255` thành trắng tinh `255,255,255`) + cập nhật `stone.png`/`bedrock.png` từ `texture.zip`.
  - Trích xuất bộ texture lửa (`fire_0/1`), vết nứt (`destroy_stage_0..9`), skin người chơi (`steve.png`) và toàn bộ HUD/GUI Minecraft 1.21.11 từ `texture.zip` vào `src/main/resources/textures/`, đồng thời tải bổ sung **8 nhóm SFX `.ogg` chuẩn `1.21.11`** từ Mojang Asset Index vào `src/main/resources/audio/sfx/`.
- **Nhóm A — Chiến đấu, Vật phẩm, Knockback & Viewmodel:**
  1. Người chơi kéo & bắn **Cung (`Bow`)** với 3 loại mũi tên (`Arrow`, `Arrow of Slowness`, `Arrow of Weakness`), hiệu ứng zoom FOV và âm thanh `bow_shoot.ogg`.
  2. Người chơi sử dụng **Giáo sắt (`Iron Spear`)**: đòn đâm tầm xa (Jab) và giữ chuột phải hạ giáo lao tới (Charge) nhân sát thương theo tốc độ di chuyển kèm `spear_charge.ogg`.
  3. Ăn toàn bộ **19 loại thức ăn** trong `textures/item/` để hồi thanh đói & saturation (kèm hiệu ứng phụ chuẩn Minecraft).
  4. Uống **4 lọ thuốc Witch** (`Healing`, `Fire Resistance`, `Water Breathing`, `Swiftness`) nhận hiệu ứng tương ứng và trả lại `Glass Bottle`.
  5. Ném **`Ender Pearl`** (dịch chuyển tới điểm chạm, trừ 5 HP) và **`Egg`** (tỉ lệ `1/8` nở `Chicken` con).
  6. Thuần hóa **`Wolf`** bằng `Bone`, dụ **`Pig`/`Rabbit`** bằng `Carrot`/`Potato`, và thổi **`Goat Horn`** có hồi chiêu.
  7. **First-Person Viewmodel & View Bobbing** (dùng skin `steve.png`, tay/khối/item góc nhìn 1, vung tay, kéo cung 4 frame, nâng đồ ăn/thuốc) + **Góc nhìn thứ 3 (`F5`)** dùng mô hình humanoid `steve.png`.
  8. **Knockback bật nảy & SFX hit giữa mọi cặp Entity ↔ Entity:** bật nảy lên trời (`vy ~ 4.8` block/s khi chạm đất) + đẩy lùi ngang mượt chuẩn Minecraft và phát `damage/hit1..3.ogg` khi bất kỳ thực thể nào (Player ↔ Mob và Mob ↔ Mob) đánh trúng mục tiêu.
- **Nhóm B — Đồ họa, Ánh sáng, Cháy & Hạt:**
  9. **Sky Light & Block Light (0–15)** lan truyền BFS theo chunk (`LavaBlock` phát sáng `15`, hang/phòng ngầm Kim tự tháp tối tự nhiên vào ban ngày).
  10. **Biome Tint động** cho cỏ và lá cây theo 5 `BiomeType` (`Snowy Plains`, `Forest`, `Plains`, `Flower Forest`, `Desert`).
  11. **Animated Water (32 frame) & Lava (20 frame)** theo thời gian thực không sinh rác GC.
  12. **Mây 3D khối dày (Fancy Clouds)** cao 4 block ở `Y=140` từ `clouds.png`.
  13. **Wireframe Block Outline & Vết nứt đào 10 giai đoạn** dùng trực tiếp `destroy_stage_0..9.png` từ `texture.zip`.
  14. **Hệ thống Hạt (Particles)** dùng pool tĩnh tái sử dụng UV (hạt vỡ block, lửa, bọt nước, vỡ lọ thuốc).
  15. **Hiệu ứng Cháy trên Mob & Góc nhìn 1:** vẽ ngọn lửa hoạt ảnh 2 mặt chéo từ `fire_0.png` / `fire_1.png` ôm sát hitbox bất kỳ mob nào đang `isOnFire()` (đốt bằng `Flint and Steel`, Zombie/Skeleton/Stray/Zombie Villager cháy nắng, hoặc dính Lava) + lớp phủ lửa hoạt ảnh ở cạnh dưới màn hình khi `Player` đang cháy.
- **Nhóm C — Sinh tồn, Túi đồ, UX/UI & Môi trường:**
  16. **6 màu Len (`WoolBlock`)** đặt được thành block từ 6 texture `*_wool.png` sẵn có.
  17. **Túi đồ mở rộng 27 ô (`E`) & Vứt vật phẩm (`Q`)** đồng bộ với 9 ô Hotbar hiện có.
  18. **Thanh Oxy (10 bọt khí) & Sprint-Swimming** (`Ctrl` khi ở dưới nước).
  19. **Phân hủy lá cây (`Leaf Decay`)** khi mất thân gỗ gần đó (có tỉ lệ rơi `Stick`) & **tốc độ đào `Shears`** nhanh hơn trên lá và len.
  20. **Chất lỏng chảy (`Flowing Water / Lava`)** tạo `StoneBlock` khi nước gặp dung nham + **Màn hình thông số `F3`**.
  21. **UX/UI Texture chuẩn Minecraft 1.21.11 (`GuiTextureAtlas`):** kết xuất pixel-perfect bằng sprite PNG gốc từ `texture.zip` cho Hotbar 9 ô, Balo 36 ô (`inventory.png`), 10 tim (gồm `poisoned` & `vehicle`), 10 đùi thịt (gồm `hunger`), 10 bọt khí, thanh Nhảy/Dash thú cưỡi, crosshair + attack indicator và nút Pause Menu.

### 2.2. Phi mục tiêu (Non-Goals)
- Không thêm bất kỳ file ảnh `.png` hay âm thanh `.ogg`/`.wav` mới nào từ bên ngoài; giữ nguyên tài nguyên sẵn có trong `src/main/resources/`.
- Không xây dựng bàn chế tạo (Crafting Table), lò nung (Furnace/Smelting), Redstone circuit hay hệ thống lưu thế giới ra đĩa.
- Không thay đổi hay vô hiệu hóa các phím tắt kiểm thử và điều khiển cũ (`F1`, `F6–F8`, `Ctrl+C`, `ESC`, `1–9`, Fly Mode).

### 2.3. Điều cần tránh (Anti-Goals)
- Tuyệt đối không rò rỉ VRAM (VAO/VBO/Texture/Mesh), bộ nhớ Off-heap (`MemoryUtil` / `Pixmap`) hay luồng nền.
- Tuyệt đối không cấp phát rác (GC churn) trong vòng lặp cập nhật/vẽ (`render()`, `update()`); giữ ổn định **60 FPS** (V-Sync).
- Không làm hỏng bất kỳ ca kiểm thử nào trong 24 file của `regressionChecks`.
- Không để lại `// TODO`, `// FIXME` hoặc mã rút gọn (tuân thủ `AGENTS.md`).

---

## 3. Ràng buộc kỹ thuật & Chuẩn dự án (Global Constraints)
- **Môi trường & Stack:** Java 17 (`options.release = 17`), mã hóa nguồn `UTF-8`, `libGDX 1.13.1` (`gdx-backend-lwjgl3`), OpenGL 3.3 Core Profile, V-Sync `true`, giới hạn `deltaTime <= 0.05f`.
- **Chú thích & Độ hoàn thiện:** Toàn bộ class, phương thức, hằng số, thuật toán mới hoặc chỉnh sửa phải có chú thích tiếng Việt rõ ràng và viết đủ 100% logic.
- **Bảo toàn hành vi cũ:** Toàn bộ hệ thống block (27 block cũ), 24 cấu hình mob, vật lý rơi/cưỡi thú, sinh địa hình 5 biome, thời tiết và bảng loot 23 loài mob trong `README.md` phải giữ nguyên 100% trừ phần được mở rộng theo thiết kế này.
- **Đồng bộ tài liệu & Test:** Mọi thay đổi phải có kiểm thử CPU mới nằm trong `regressionChecks`, và Phần A của `README.md` phải được cập nhật khớp 100% sau bước gộp nhánh cuối cùng.

---

## 4. Các phương án đã cân nhắc & Lựa chọn

| Phương án | Cách tổ chức | Đánh đổi | Quyết định |
| --- | --- | --- | --- |
| **Approach 1** | Tách module mới ngay trên 3 worktree song song | Giảm ~80% xung đột, nhưng vẫn đụng chéo `Texture.java`, `Item.java` và `VoxelGameApp.java` | Không chọn |
| **Approach 2** | Sửa trực tiếp vào 5 file lõi (`World`, `Chunk`, `Player`, `UIRenderer`, `VoxelGameApp`) trên 3 worktree | Gọn danh sách file, nhưng gộp 3 nhánh sẽ xung đột nặng, tốn rất nhiều token merge và dễ lỗi hồi quy | Không chọn |
| **Approach 3** | **Task 0 Foundation** chốt điểm giao dùng chung → rẽ **3 Worktree song song** sở hữu file riêng biệt | Tốn 1 bước nền nhỏ trước khi tách nhánh, đổi lại khi gộp 3 nhánh gần như không xung đột và tối ưu quota token | **Đã chọn** |

---

## 5. Kiến Trúc & Hợp Đồng Sở Hữu File (File Ownership Contract)

### 5.0. Task 0 — Foundation (Thực hiện trên nhánh tích hợp trước khi tạo 3 worktree)
Mục tiêu của Task 0 là khóa cứng mọi tài nguyên dùng chung (`Texture` atlas, `WoolBlock`, `Item`, và các điểm cắm tối giản) để 3 nhánh sau không bao giờ sửa đè cùng dòng dữ liệu.

- **`Texture.java`:**
  - Mở rộng atlas từ `ATLAS_COLS = 8, ATLAS_ROWS = 5` (`128 × 80`) lên `ATLAS_COLS = 8, ATLAS_ROWS = 6` (`128 × 96`).
  - Giữ nguyên tuyệt đối chỉ số tile `0..35` cũ.
  - Thêm 6 hằng số tile mới cho len đọc trực tiếp từ `textures/item/*_wool.png`:
    - `TILE_WHITE_WOOL = 36`
    - `TILE_LIGHT_GRAY_WOOL = 37`
    - `TILE_GRAY_WOOL = 38`
    - `TILE_BLACK_WOOL = 39`
    - `TILE_BROWN_WOOL = 40`
    - `TILE_PINK_WOOL = 41`
- **`WoolBlock.java` (tạo mới) & `Block.java`:**
  - Tạo `WoolBlock` (kế thừa `Block`, `RenderLayer.OPAQUE`, `GeometryType.FULL_CUBE`, độ cứng `0.8f`, âm thanh nhóm len/cỏ, đào rơi đúng `Item` len tương ứng).
  - Đăng ký 6 hằng số block cuối bảng cũ trong `Block.java` (ID `27..32`): `WHITE_WOOL` (`27`), `LIGHT_GRAY_WOOL` (`28`), `GRAY_WOOL` (`29`), `BLACK_WOOL` (`30`), `BROWN_WOOL` (`31`), `PINK_WOOL` (`32`).
- **`Item.java`:**
  - Giữ nguyên danh sách `STANDALONE_ITEMS` (đủ 56 món để `ItemTextureAtlas` 8×8 và `MobLootChecks` không thay đổi chỉ số hay thất bại).
  - Liên kết 6 hằng số `Item.WHITE_WOOL..PINK_WOOL` với `Block.WHITE_WOOL..PINK_WOOL` tương ứng và ghi đè vào `BY_BLOCK_ID[blockId]` sao cho:
    - `Item.WHITE_WOOL.isBlock() == true` và `Item.WHITE_WOOL.getBlock() == Block.WHITE_WOOL`.
    - `Item.of(Block.WHITE_WOOL) == Item.WHITE_WOOL` (nhặt len rơi từ `WoolBlock` hoặc từ cắt lông `Sheep` đều dùng chung một `Item` duy nhất và đặt được ra thế giới).
- **Hook tối giản ở `VoxelGameApp.java`, `Player.java`, `UIRenderer.java`:**
  - Định nghĩa rõ ranh giới các điểm gọi mở rộng (ví dụ `onPrimaryAction()`, `onSecondaryPress()`, `onSecondaryRelease()`, `renderOverlayExtensions()`, `renderScreenExtensions()`) và vùng đặt hook tách biệt theo từng track.
- **Xác minh Task 0:** Chạy `./gradlew regressionChecks` phải xanh 100% trước khi tạo 3 git worktree.

---

### 5.1. Track A (`feat/combat-items-viewmodel`) — Chiến đấu, Vật phẩm & Viewmodel
**Phạm vi sở hữu file:**
- **Tạo mới:**
  - `src/main/java/com/voxelgame/entity/FoodRegistry.java`
  - `src/main/java/com/voxelgame/engine/ItemUseHandler.java`
  - `src/main/java/com/voxelgame/engine/FirstPersonRenderer.java`
  - `src/main/java/com/voxelgame/entity/projectile/ThrownEnderPearlEntity.java`
  - `src/main/java/com/voxelgame/entity/projectile/ThrownEggEntity.java`
  - `src/test/java/com/voxelgame/engine/CombatItemsChecks.java`
- **Được phép sửa:**
  - `Player.java` (trạng thái dùng vật phẩm, hồi đói/saturation, `F5` camera mode, view bobbing phase, hiệu ứng `FIRE_RESISTANCE`/`SWIFTNESS`/`WATER_BREATHING`)
  - `Wolf.java` (thuần hóa bằng `Bone`, ngồi/đứng, đi theo chủ & tấn công mục tiêu của chủ)
  - `Pig.java` (thêm `TemptFollowGoal` với `Item.CARROT`, `Item.POTATO`)
  - `Rabbit.java` (mở rộng `holdsTemptItem` thêm `Item.CARROT` bên cạnh `Block.DANDELION`)
  - `VoxelGameApp.java` (chỉ trong vùng hook Track A: giữ/nhả chuột phải, phím `F5`, gọi `FirstPersonRenderer`)

**Thông số thiết kế chi tiết Track A:**
1. **Cung (`Item.BOW`) & Mũi tên:**
   - Giữ chuột phải để kéo dây cung (tối đa `1.0s` đạt lực tối đa, 4 trạng thái khung `0..3` cắt từ hàng dưới `equipment/held.png`).
   - Khi đang kéo cung: tốc độ đi bộ giảm còn `0.4x`, FOV thu hẹp dần tối đa `-8.0f` (`Camera`).
   - Nhả chuột phải (khi kéo `>= 0.15s`): tìm mũi tên trong kho theo thứ tự ô từ trái sang phải (`ARROW`, `TIPPED_ARROW_SLOWNESS`, `TIPPED_ARROW_WEAKNESS`), trừ 1 mũi tên, tạo `ArrowEntity` với vận tốc tỉ lệ theo lực kéo (`12..42` block/s), sát thương cơ sở `2..9 HP` và hiệu ứng tẩm tên tương ứng (`Slowness I` hoặc `Weakness I` 30s), phát `audio/sfx/mob/shared/bow_shoot.ogg`.
2. **Giáo sắt (`Item.IRON_SPEAR`):**
   - **Jab (Chuột trái):** Khi đang cầm `IRON_SPEAR`, tăng tầm cận chiến từ `3.0` lên `4.2` block, sát thương cơ sở `5.0 HP` (nhân `1.5x` nếu crit khi rơi), hồi chiêu `0.65s`.
   - **Charge (Giữ Chuột phải):** Hạ giáo về phía trước (animation nâng/nghiêng trong `FirstPersonRenderer`), phát `audio/sfx/mob/shared/spear_charge.ogg`; khi lao vào thực thể sống trong tầm `3.5` block với vận tốc ngang `speed >= 4.0f` (đang Sprint hoặc cưỡi Horse/Camel), gây `5.0f + min(8.0f, speed * 0.8f)` HP kèm đẩy lùi mạnh và miễn nhiễm lặp `0.4s` cho cùng mục tiêu.
3. **19 loại Thức ăn (`FoodRegistry`):**
   - Giữ chuột phải `1.6s` (chỉ ăn được khi `hunger < 20`, trừ `ROTTEN_FLESH` & `SPIDER_EYE` ăn được cả khi đầy hoặc theo chuẩn Minecraft đều kiểm tra `hunger < 20` để đơn giản/nhất quán; dùng bảng chuẩn dưới đây):
     - `BEEF` (`+3` đói, `1.8` sat), `COOKED_BEEF` (`+8`, `12.8`), `PORKCHOP` (`+3`, `1.8`), `COOKED_PORKCHOP` (`+8`, `12.8`), `MUTTON` (`+2`, `1.2`), `COOKED_MUTTON` (`+6`, `9.6`), `CHICKEN` (`+2`, `1.2`, `30%` `HUNGER I` 30s), `COOKED_CHICKEN` (`+6`, `7.2`), `RABBIT` (`+3`, `1.8`), `COOKED_RABBIT` (`+5`, `6.0`), `COD` (`+2`, `0.4`), `COOKED_COD` (`+5`, `6.0`), `SALMON` (`+2`, `0.4`), `COOKED_SALMON` (`+6`, `9.6`), `CARROT` (`+3`, `3.6`), `POTATO` (`+1`, `0.6`), `BAKED_POTATO` (`+5`, `6.0`), `ROTTEN_FLESH` (`+4`, `0.8`, `80%` `HUNGER I` 30s), `SPIDER_EYE` (`+2`, `3.2`, `100%` `POISON I` 4s).
   - Tiêu thụ 1 item khi hoàn thành `1.6s`, hồi `hunger` (tối đa `20`) và `saturation` (tối đa bằng `hunger`), ưu tiên tiêu saturation trước khi giảm đói.
4. **4 lọ thuốc Witch:**
   - Uống khi giữ chuột phải `1.6s` (hoặc kích hoạt hoàn tất tương tự `Milk Bucket`), trừ 1 lọ thuốc và trả `Item.GLASS_BOTTLE` vào kho:
     - `POTION_HEALING`: hồi ngay `+4.0 HP` (không vượt `maxHp = 20`).
     - `POTION_FIRE_RESISTANCE`: cấp `StatusEffectType.FIRE_RESISTANCE` cấp `0` trong `180s` (miễn mọi sát thương lửa và Lava ở `LivingEntity`/`Player`).
     - `POTION_SWIFTNESS`: cấp `StatusEffectType.SWIFTNESS` cấp `0` trong `180s` (`+20%` tốc độ di chuyển ngang).
     - `POTION_WATER_BREATHING`: cấp `StatusEffectType.WATER_BREATHING` cấp `0` trong `180s` (không giảm thanh Oxy khi ở dưới nước).
5. **`Ender Pearl`, `Egg`, `Goat Horn`, `Wolf`, `Pig`, `Rabbit`:**
   - `ThrownEnderPearlEntity`: ném vận tốc `22` block/s; khi va chạm block rắn hoặc thực thể sống, dịch chuyển `Player` lên mặt an toàn tại điểm chạm, xóa `fallDistance`, gây `5.0 HP` (`DamageSource.FALL` hoặc `GENERIC`) và phát âm va chạm.
   - `ThrownEggEntity`: ném vận tốc `22` block/s; khi chạm đích, xác suất `1/8` sinh 1 `Chicken` con (và `1/32` trong trường hợp trúng sẽ sinh thêm 3 thành 4 con con).
   - `GOAT_HORN`: click chuột phải phát âm sừng/dê tầm `32` block, hồi chiêu `7.0s`.
   - `Wolf`: chuột phải bằng `Item.BONE` khi sói chưa thuần hóa → tiêu 1 xương, `1/3` xác suất thuần hóa thành công (`tamed = true`, `persistent = true`, hồi lên `20 HP`); sói đã thuần hóa đi theo chủ khi cách `> 10` block (dịch chuyển tới cạnh chủ nếu `> 24` block), ngồi/đứng khi chủ chuột phải tay không, và tấn công mục tiêu chủ đánh hoặc kẻ tấn công chủ (bỏ qua chủ và không nổi giận với chủ).
6. **`FirstPersonRenderer` & Camera `F5`:**
   - Vẽ trong pha overlay 3D riêng sau khi xóa Depth Buffer (để tay/item không xuyên tường), dùng lại `Matrix4` tĩnh (zero-GC).
   - Hiển thị tay trần (khi cầm `Item.NONE`), khối 3D thu nhỏ (khi cầm `Block`), sprite 2D nghiêng (khi cầm `Item`), và 4 khung cung hoặc giáo sắt từ `equipment/held.png`.
   - Hoạt ảnh: **View Bobbing** theo quãng bước chân trên mặt đất; **Swing** khi đào hoặc đánh; **Pull/Charge** khi kéo cung/hạ giáo; **Consume** rung nhẹ đưa về miệng khi ăn/uống.
   - Phím `F5` xoay vòng `FIRST_PERSON → THIRD_BACK → THIRD_FRONT`: khi ở góc nhìn thứ 3, tính vị trí camera lùi/tiến `4.0` block có raycast tránh kẹt trong block rắn, và render nhân vật người chơi bằng hình học humanoid từ `MobModels` với skin `textures/entity/player/steve.png`.
7. **Knockback bật nảy & SFX Hit (`damage/hit1..3.ogg`) cho mọi cặp Entity ↔ Entity:**
   - Khi bất kỳ `LivingEntity` (Player ↔ Mob hoặc Mob ↔ Mob) chịu đòn có vector đẩy lùi và đang đứng trên mặt đất (`onGround`), bật nảy dọc (`velocity.y >= 4.8f` ở mob, `4.5f` ở người chơi) kèm giảm hệ số tắt đẩy ngang (`pushDecay` `6.5f`) để giữ đà nảy mượt chuẩn Minecraft.
   - Phát ngẫu nhiên `damage/hit1..3.ogg` (giảm âm tuyến tính trong `16` block) cho mọi va chạm đòn đánh hợp lệ giữa các thực thể (`AudioManager.playEntityHit`).

---

### 5.2. Track B (`feat/visuals-lighting-particles`) — Đồ họa, Ánh sáng & Hạt
**Phạm vi sở hữu file:**
- **Tạo mới:**
  - `src/main/java/com/voxelgame/engine/ChunkLighting.java`
  - `src/main/java/com/voxelgame/engine/BiomeTint.java`
  - `src/main/java/com/voxelgame/engine/LiquidAnimator.java`
  - `src/main/java/com/voxelgame/engine/BlockOutlineRenderer.java`
  - `src/main/java/com/voxelgame/engine/ParticleManager.java`
  - `src/test/java/com/voxelgame/engine/VisualsLightingChecks.java`
- **Được phép sửa:**
  - `ShaderProgram.java` (bổ sung uniform/thuộc tính an toàn cho tint màu, ánh sáng sky/block, giữ tương thích tuyệt đối `enable2DUIMode`, `MobRenderer`, `SkyRenderer`)
  - `Chunk.java` (lưu `ChunkLighting` lười, áp dụng Sky/Block Light + Biome Tint vào đỉnh/màu khi dựng mesh, đánh dấu chunk lân cận khi ánh sáng truyền qua biên)
  - `SkyRenderer.java` (nâng cấp `renderCloudLayer` sang mây 3D khối dày 4 block từ `clouds.png`)
  - `MobRenderer.java` (vẽ ngọn lửa hoạt ảnh 2 mặt chéo từ `fire_0.png`/`fire_1.png` ôm sát hitbox mọi mob đang `isOnFire()`)
  - `Texture.java` (chỉ thêm đường dẫn hỗ trợ `LiquidAnimator` cập nhật tile Nước/Lava và giữ grayscale gốc cho cỏ/lá nếu chuyển sang nhuộm động, hoặc cung cấp hệ số bù để không phá `ItemEntity` / `Hotbar`; phần sửa lỗi grayscale `stone.png`/`bedrock.png` hoàn tất từ Task 0.3)
  - `VoxelGameApp.java` (chỉ trong vùng hook Track B: cập nhật `LiquidAnimator` & `ParticleManager`, thay thế call overlay chọn block sang `BlockOutlineRenderer`, vẽ lớp phủ lửa góc nhìn 1 khi `player.isOnFire()`)

**Thông số thiết kế chi tiết Track B:**
1. **`ChunkLighting` (Sky Light & Block Light 4-bit):**
   - Mỗi chunk cấp phát lười mảng `byte[4096]` lưu `(skyLight << 4) | blockLight` (tối đa `15` mỗi kênh).
   - **Sky Light:** Chiếu thẳng đứng từ `Y = 255` xuống (`15`), không suy giảm qua `Air` hoặc các khối không cản sáng, tắt/suy giảm qua `Leaves` (`-1`), dừng khi gặp khối đục. Khi mở hang/phá trần hoặc đặt mái che (Kim tự tháp), chạy BFS cục bộ dùng hàng đợi vòng (primitive `int[]` cấp phát trước, zero-GC) lan truyền qua biên chunk.
   - **Block Light:** `Block.LAVA` phát sáng mức `15`. Lan truyền BFS 6 hướng giảm `1` mỗi bước qua block không đục.
   - **Kết hợp trong `Chunk` & `ShaderProgram`:** Độ sáng cuối của mặt đỉnh lấy `max(skyLight / 15f * daylightFactor, blockLight / 15f)` kết hợp với Directional Face Shading + AO sẵn có. Đảm bảo `floor >= 0.04f` để ban đêm/trong hầm vẫn thấy rõ đường nét khối.
2. **`BiomeTint` (Nhuộm màu cỏ & lá theo Biome):**
   - Định nghĩa bảng màu RGB chuẩn theo 5 `BiomeType`:
     - `PLAINS`: Cỏ `0x91BD59`, Lá sồi `0x77AB2F`
     - `FOREST`: Cỏ `0x79C05A`, Lá sồi `0x59AE30`
     - `FLOWER_FOREST`: Cỏ `0x88BB67`, Lá sồi `0x6BA941`
     - `DESERT`: Cỏ `0xBFB755`, Lá sồi `0xAEA42A`
     - `SNOWY_PLAINS`: Cỏ `0x80B497`, Lá sồi `0x60A17B`
   - **Cách truyền màu an toàn cho `Chunk` & Shader:** Dùng `VertexAttribute(Usage.ColorPacked, 4, "aColor")` trong một `VertexAttributes` dành cho World Chunk (hoặc bổ sung attribute màu mặc định trắng `1,1,1,1` cho shader qua thuộc tính tĩnh/uniform `uUseVertexColor` để `UIRenderer`, `MobRenderer`, `EnvironmentMesh` không phải sửa định dạng đỉnh 6 floats hiện tại). *Lưu ý:* Nếu `Texture.java` giữ ảnh `grass_block_top`, `short_grass`, `tall_grass`, `oak_leaves` không nhuộm cứng (hoặc tách hệ số trắng), `BiomeTint` áp màu mượt trực tiếp theo tọa độ `(x, z)` kể cả ở ranh giới biome (pha 3×3 cột). *Để `Hotbar` và `ItemEntity` không bị mất màu xanh cỏ*, giữ tile atlas hoặc chỉ bù màu theo tỉ lệ `biomeColor / PLAINS_TINT` trong `Chunk` — cách này giữ 100% tương thích với `Texture.java` Task 0, `Hotbar`, `ItemEntity` và `UIRenderer`!
3. **`LiquidAnimator` (Hoạt ảnh Nước & Dung nham):**
   - Đọc một lần `water_still.png` (`16×512` = `32` frame) và `lava_still.png` (`16×320` = `20` frame) khi khởi tạo, giữ `Pixmap` từng frame đã chuẩn hóa màu sẵn trong bộ nhớ CPU nhẹ (`32 + 20 = 52 KB` tổng cộng).
   - Mỗi `0.10s` (2 tick), dùng `glTexSubImage2D` cập nhật đúng ô `16×16` tại `TILE_WATER` và `TILE_LAVA` trên `Texture` atlas GPU. Không rebuild mesh chunk và không cấp phát rác mỗi frame.
4. **Mây 3D khối dày (`SkyRenderer`):**
   - Đọc mẫu alpha từ `clouds.png` (`256×256`) một lần khi tạo `SkyRenderer`, nhóm các ô mây kề nhau thành các hộp dày `4` block (trần `Y=144`, đáy `Y=140`, 4 mặt bên có shading phân biệt) vào `EnvironmentMesh` tĩnh di chuyển theo `cloudOffset` quanh camera (bằng tịnh tiến `uModel`), bỏ phần mặt trong bị che để giữ frame rate ổn định.
5. **`BlockOutlineRenderer` (Wireframe + Vết nứt đào):**
   - Vẽ khung dây đen mảnh (`GL_LINES`, `alpha = 0.65f`) bao quanh `Block.Bounds` thực (nới `0.004f`) của ô đang ngắm.
   - Khi `miningProgress > 0.001f`, vẽ overlay vết nứt 10 cấp (`stage = (int)(progress * 9.99f)`) lấy trực tiếp từ 10 ảnh gốc `textures/block/destroy_stage_0..9.png` đóng gói thành texture `160×16`, phủ lên 6 mặt hộp chọn với `GL_BLEND`.
6. **`ParticleManager` (Hạt tái sử dụng UV) & Hiệu ứng Cháy (`fire_0/1.png`):**
   - Pool cố định `MAX_PARTICLES = 512` struct mảng nguyên thủy (`float[] x, y, z, vx, vy, vz, life, maxLife, u0, v0, u1, v1, size, r, g, b`), cấp phát 1 `Mesh` động + `float[]` đệm đỉnh một lần duy nhất (zero-GC) cho hạt vỡ block, lửa, bọt nước, vỡ lọ thuốc.
   - **Hiệu ứng cháy trên mob & góc nhìn 1:** `MobRenderer` nạp `fire_0.png`/`fire_1.png` (32 frame mỗi tờ), vẽ 2 mặt chữ X tự phát sáng quanh hitbox của mọi mob đang `isOnFire()` (kể cả đốt bằng `Flint and Steel` hoặc cháy nắng). Khi `Player.isOnFire()`, vẽ lớp phủ lửa hoạt ảnh ở cạnh dưới màn hình dưới HUD.

---

### 5.3. Track C (`feat/survival-inventory-world`) — Sinh tồn, Túi đồ, UX/UI & Môi trường
**Phạm vi sở hữu file:**
- **Tạo mới:**
  - `src/main/java/com/voxelgame/entity/PlayerInventory.java`
  - `src/main/java/com/voxelgame/engine/GuiTextureAtlas.java`
  - `src/main/java/com/voxelgame/engine/InventoryScreenRenderer.java`
  - `src/main/java/com/voxelgame/engine/DebugOverlayRenderer.java`
  - `src/main/java/com/voxelgame/engine/LiquidFlowSimulator.java`
  - `src/main/java/com/voxelgame/engine/LeafDecaySystem.java`
  - `src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java`
- **Được phép sửa:**
  - `Hotbar.java` (giữ nguyên toàn bộ public API cũ và hành vi khởi tạo 9 ô, nhưng liên kết trực tiếp với 9 ô đầu của `PlayerInventory` để `addItem` tràn sang 27 ô túi đồ khi Hotbar đầy)
  - `Player.java` (thanh Oxy `AIR_MAX_TICKS = 300` / 15 giây, ngạt trừ `2 HP/s`, `WATER_BREATHING` giữ đầy Oxy, Sprint-Swimming `Ctrl` khi mắt/thân dưới nước: vận tốc bơi `1.55x` và tư thế ngang hạ tầm mắt mượt)
  - `UIRenderer.java` (nâng cấp toàn bộ HUD dùng `GuiTextureAtlas`: Hotbar `hotbar.png`/`hotbar_selection.png`, 10 tim thường/độc/thú cưỡi, 10 đùi thịt thường/đói, 10 bọt khí, thanh Jump/Dash thú cưỡi, crosshair, nút Pause Menu; gọi `InventoryScreenRenderer` & `DebugOverlayRenderer`)
  - `World.java` (kích hoạt `LiquidFlowSimulator` theo tick và `LeafDecaySystem` trong random tick / `BlockUpdateQueue`)
  - `VoxelGameApp.java` (chỉ trong vùng hook Track C: phím `E` bật/tắt Inventory & mở khóa con trỏ chuột không pause game, phím `Q` vứt 1 item hoặc `Ctrl+Q` cả stack thành `ItemEntity` trước mặt, phím `F3` bật/tắt debug HUD, `Shears` nhân `5x` tốc độ đào `OAK_LEAVES`, `SPRUCE_LEAVES` và 6 `WoolBlock`)

**Thông số thiết kế chi tiết Track C:**
1. **`PlayerInventory` (36 ô = 9 Hotbar + 27 Balo) & Giao diện `E` + Vứt đồ `Q` + `GuiTextureAtlas`:**
   - Tổng `36` ô (`0..8` là Hotbar, `9..35` là 3 hàng × 9 cột Inventory chính), giao diện cắt trực tiếp từ `textures/gui/container/inventory.png` (khung `176×166` pixel-perfect).
   - Toàn bộ HUD (9 ô Hotbar, 10 tim, 10 đùi thịt, 10 bọt khí, thanh Jump/Dash thú cưỡi, crosshair, nút Pause Menu) vẽ qua `GuiTextureAtlas` dùng bộ PNG Minecraft 1.21.11 trích từ `texture.zip`.
   - Phím `E`: bật/tắt màn hình túi đồ (vẫn giữ vòng lặp thế giới chạy nền giống Minecraft Singleplayer, mở con trỏ chuột để kéo/click đổi ô, hỗ trợ **Left-click** nhấc/đặt/gộp cả stack, **Right-click** chia đôi / thả 1 item, **Shift+Click** chuyển nhanh giữa Hotbar và 27 ô balo).
   - Phím `Q`: vứt 1 đơn vị từ ô đang chọn (`Ctrl+Q` vứt cả stack) thành `ItemEntity` ném về phía trước mặt theo hướng nhìn với `pickupDelay = 1.0s` để không hút lại ngay lập tức.
2. **Thanh Oxy (Air Bubbles) & Sprint-Swimming:**
   - Khi mắt người chơi nằm trong `Block.WATER` và không có `StatusEffectType.WATER_BREATHING`: giảm `airTicks` từ `300` xuống `0` theo `20 tick/s`. Khi `airTicks == 0`, cứ mỗi `1.0s` gây `2.0 HP` sát thương ngạt (`DamageSource.DROWN` hoặc `GENERIC`).
   - Khi mắt ra khỏi nước: hồi Oxy nhanh (`+4` bọt/s) hoặc đặt lại mượt về `300`.
   - `UIRenderer` vẽ 10 biểu tượng bọt khí xanh phía trên thanh đói bên phải bất cứ khi nào đang dưới nước hoặc `airTicks < 300`.
   - **Sprint-Swimming:** Nhấn `Left Ctrl` khi đang ngập trong nước (`isInLiquid` & là `WATER`) kích hoạt bơi nhanh `1.55x` tốc độ bơi, giảm nhẹ lực chìm theo hướng nhìn để lặn/trồi linh hoạt theo góc `pitch` và tạo cảm giác Update Aquatic.
3. **`LeafDecaySystem` (Phân hủy lá cây) & Tốc độ đào `Shears`:**
   - Phân biệt lá tự nhiên và lá do người chơi đặt (dùng bit metadata `1` ở `OakLeavesBlock`/`SpruceLeavesBlock` khi người chơi đặt thủ công để lá đặt bằng tay **không bao giờ tự rụng**).
   - Khi chặt thân gỗ (`OAK_LOG`/`SPRUCE_LOG`) hoặc trong random tick (`50 ms`), kiểm tra lá tự nhiên trong bán kính Manhattan/Chebyshev `4` block xem còn nối tới bất kỳ thân gỗ nào qua lá trong tối đa `6` bước không (dùng bảng cố định BFS cục bộ, zero-GC). Nếu mồ côi gỗ, chuyển thành `Block.AIR` và có `2%` xác suất rơi `Item.STICK` (hoặc `5%` rơi chính lá không, giữ chuẩn tối giản với asset có sẵn).
   - Khi người chơi cầm `Item.SHEARS` đào `OAK_LEAVES`, `SPRUCE_LEAVES` hoặc 6 `WoolBlock`: tốc độ tích lũy `miningElapsedTime` nhân `5.0x` (đập lá gần như tức thì), và phá lá bằng `Shears` luôn rơi đúng block lá đó.
4. **`LiquidFlowSimulator` (Chất lỏng chảy & Phản ứng tạo `Stone`):**
   - Dùng metadata `0..7` cho mức chất lỏng (`0` = nguồn / source block, `1..7` = dòng chảy giảm dần).
   - Nước chảy lan ngang tối đa `7` block (mức `1..7`), Lava chảy ngang tối đa `3` block (mức `1, 2, 3` với bước nhảy +2), ưu tiên chảy thẳng xuống dưới trước (`fallback`) khi ô bên dưới là `AIR` hoặc cây cỏ phá được (rửa trôi cây chữ X thành item rơi).
   - Cập nhật theo nhịp tick chuẩn: Water mỗi `0.25s` (`5` tick), Lava mỗi `1.50s` (`30` tick), xử lý giới hạn số ô mỗi tick (`MAX_UPDATES_PER_TICK = 64`) qua hàng đợi vòng cấp phát trước để không bao giờ gây tụt FPS khi đào vỡ bờ hồ lớn.
   - **Phản ứng Nước + Dung nham:** Khi Water và Lava chạm kề nhau hoặc chảy đè lên nhau, chuyển ô chạm thành `Block.STONE` (nếu là dòng chảy hoặc nước đè lên lava nguồn/ngược lại vì dự án không thêm Obsidian/Cobblestone mới, dùng `Block.STONE` theo đúng bảng block sẵn có) và phát âm `liquid/lava.ogg`.
5. **`DebugOverlayRenderer` (`F3`):**
   - Nhấn `F3` để bật/tắt bảng thông số góc trên trái/phải dùng font 5×7 và `StringBuilder` tái sử dụng (zero-GC):
     - `FPS`, `XYZ` (tọa độ chính xác 2 chữ số thập phân), `CHUNK` (`cx, cy, cz`), `FACING` (`N/S/E/W` + `yaw/pitch`), `BIOME`, `LIGHT` (`sky/block` tại chân), `ENTITIES` (số thực thể sống đang hoạt động).

---

## 6. Chiến Lược Điều Phối Song Song & Quy Trình Phiên Tiếp Theo

Khi bắt đầu phiên làm việc thực thi (hạn mức 5h ≥ 80%):
1. **Chạy Task 0 (Foundation)** trực tiếp trên nhánh làm việc chính → chạy `./gradlew regressionChecks` xác nhận xanh 100% → tạo commit mốc.
2. **Tạo 3 Git Worktree song song** (dùng `superpowers:using-git-worktrees`) từ commit mốc Task 0:
   - Worktree A (`feat/combat-items-viewmodel`)
   - Worktree B (`feat/visuals-lighting-particles`)
   - Worktree C (`feat/survival-inventory-world`)
3. **Phân tầng Model cho Subagent (`subagent-driven-development`) để tiết kiệm quota:**
   - **Orchestrator (Controller):** Gemini 4 Neon (Medium).
   - **Implementer Task gọn / rõ spec** *(FoodRegistry, Potion/Pearl/Egg, Wolf/Pig/Rabbit/Horn, LiquidAnimator, BlockOutlineRenderer, DebugOverlayRenderer, LeafDecaySystem)*: **Flash** (`flash`).
   - **Implementer Task tích hợp lõi** *(FirstPersonRenderer + Bow/Spear + F5, ChunkLighting + BiomeTint, ParticleManager, PlayerInventory + UI, LiquidFlowSimulator)*: **Medium** (`inherit`).
   - **Task Reviewer:** **Flash** (`flash`) cho task gọn; **Medium** (`inherit`) cho task tích hợp lõi.
   - **Final Whole-Branch Review:** **Pro** (`pro`).
4. **Thứ tự Gộp Nhánh & Tích hợp cuối:**
   - Merge lần lượt **Track B → Track C → Track A** (do Track B sửa shader/chunk sâu nhất, Track C nối UI/World, Track A nối gameplay/viewmodel).
   - Chạy đầy đủ `./gradlew regressionChecks` sau mỗi lần merge và sau khi gộp đủ 3 nhánh.
   - Đồng bộ toàn bộ **Phần A của `README.md`** theo đúng `AGENTS.md` và chạy Final Review kết thúc.

---

## 7. Kế hoạch Kiểm thử & Nghiệm thu (Verification)
- **Kiểm thử tự động CPU (`./gradlew regressionChecks`):**
  - Toàn bộ 24 file kiểm thử hiện có vẫn pass 100% không sửa đổi hoặc nới lỏng assertion.
  - Bổ sung 3 bộ test mới:
    - `CombatItemsChecks.java` (Task A): kiểm tra bảng 19 thức ăn, 4 thuốc trả `Glass Bottle`, tầm/sát thương `Iron Spear` theo tốc độ, tiêu thụ mũi tên & `ArrowEntity` của `Bow`, dịch chuyển `Ender Pearl`, nở gà `Egg`, thuần hóa `Wolf` bằng `Bone`, hồi chiêu `Goat Horn`.
    - `VisualsLightingChecks.java` (Task B): kiểm tra lan truyền Sky Light qua trần mở/kín, Block Light từ `LavaBlock`, bảng `BiomeTint` 5 biome, chỉ số frame của `LiquidAnimator` (32 water / 20 lava), giới hạn pool `ParticleManager` không vượt trần và zero-leak.
    - `SurvivalWorldChecks.java` (Task C): kiểm tra `WoolBlock` ID `27..32` liên kết `Item.WHITE_WOOL..PINK_WOOL`, gộp/chuyển 36 ô `PlayerInventory`, tụt Oxy 300 tick & miễn tụt khi có `Water Breathing`, `LeafDecaySystem` phân biệt lá đặt tay vs lá tự nhiên, `LiquidFlowSimulator` lan 7 ô Water / 3 ô Lava và tạo `Stone` khi gặp nhau.
- **Kiểm tra thủ công / 60 FPS:** Rà soát không còn `new` đối tượng hoặc `FloatArray` tạm trong vòng lặp `render()`/`update()`, kiểm tra `dispose()` giải phóng toàn bộ tài nguyên OpenGL/Pixmap mới tạo.
