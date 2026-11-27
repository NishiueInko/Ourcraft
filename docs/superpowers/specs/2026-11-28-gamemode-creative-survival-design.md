# Thiết Kế Hệ Thống Chế Độ Chơi (Creative & Survival) + Kho Vật Phẩm Creative

**Ngày:** 2026-11-28  
**Phạm vi:** Dự án VoxelGame (`com.voxelgame`)  
**Trạng thái:** Chờ người dùng phê duyệt

---

## 1. Mục Đích & Ý Định

Bổ sung cơ chế chuyển đổi tức thì bằng tổ hợp phím **`Ctrl + M`** giữa 2 chế độ **Creative** và **Survival** nhằm đạt 2 mục tiêu chính:
1. **Tăng độ đa dạng gameplay:** Tách bạch rõ trải nghiệm sinh tồn thực thụ (Survival) với chế độ sáng tạo tự do (Creative) thay vì trạng thái "lai" hiện tại.
2. **Hỗ trợ minh họa toàn bộ vật phẩm trong game:** Khi ở Creative, người chơi mở túi đồ (`E`) để lấy trực tiếp mọi khối và vật phẩm hiện có trong game (~80 món) vào Hotbar mà không cần đi tìm/đánh mob.

---

## 2. Mục Tiêu, Phi Mục Tiêu & Tiêu Chí Tránh

### Mục tiêu
- Mặc định vào game (`VoxelGameApp.create()`) ở chế độ **Creative**.
- Nhấn `Ctrl + M` (Ctrl trái hoặc phải) khi không tạm dừng để chuyển đổi qua lại tức thì `Creative ↔ Survival`, kèm thông báo ngắn trên HUD.
- **Creative:** Bay (`Space` ×2), dùng `F1` (tốc độ địa hình) và `Ctrl + C` (ghé 5 biome), bất tử (không mất máu/đói/Oxy, ẩn thanh tim/đùi thịt/Oxy), mob thù địch bỏ qua người chơi, đào vỡ tức thì mọi block (kể cả **Bedrock**) không rơi rác `ItemEntity`, đặt block và dùng vật phẩm không tiêu hao số lượng, nhấn `E` mở **Kho Creative** lấy được mọi khối & vật phẩm.
- **Survival:** Khóa hẳn chế độ bay (`Space` ×2), khóa `F1` và `Ctrl + C`; khi chuyển Creative → Survival tự động tắt bay và đặt tốc độ địa hình về `1x`. Giữ nguyên 100% cơ chế sinh tồn hiện tại (20 HP, 20 đói, Oxy, mob tấn công, đào theo độ cứng không phá Bedrock, trừ đồ khi đặt/dùng, nhấn `E` mở túi đồ 36 ô cũ).
- Nổi bật rõ 4 tính chất OOP theo `AGENTS.md` (Đóng gói, Kế thừa, Trừu tượng, Đa hình), zero-GC trong Game Loop và ổn định 60 FPS.

### Phi mục tiêu
- Không thêm chế độ Adventure hay Spectator trong đợt này (nhưng kiến trúc mở sẵn để thêm sau).
- Không lưu/tải file thế giới ra đĩa (chưa có hệ thống save/load).
- Không thêm khối hay vật phẩm gameplay mới ngoài các khối/vật phẩm đã có sẵn trong game.

### Tiêu chí tránh (Anti-goals)
- Không làm vỡ bất kỳ bài nào trong **28 bài kiểm thử hồi quy CPU (`regressionChecks`)** hiện có.
- Không dùng chuỗi `if (mode == CREATIVE) ... else ...` rải rác khắp codebase — mọi phân nhánh hành vi phải điều phối đa hình qua `GameMode`.
- Không tạo rác GC (`new` object/String mỗi khung hình) trong Game Loop hay rò rỉ tài nguyên GPU (`Mesh`/`Texture`).

---

## 3. Các Hướng Thiết Kế Đã Cân Nhắc

### Hướng 1 (Đã chọn): `abstract GameMode` đa hình + `CreativeInventoryScreen` riêng kế thừa `Screen`
- Tạo package `com.voxelgame.world.gamemode` gồm `abstract GameMode`, `CreativeGameMode` và `SurvivalGameMode` (Flyweight bất biến, zero-GC).
- Tạo `CreativeCatalog` (danh mục toàn bộ vật phẩm/khối chia theo tab) và `CreativeInventoryScreen extends Screen` tách biệt khỏi `InventoryScreen` 36 ô cũ.
- **Ưu điểm:** Tách trách nhiệm sạch sẽ (SRP), thể hiện đầy đủ 4 tính chất OOP ở cả tầng Logic (`GameMode`) lẫn tầng UI (`Screen`), không làm phình `InventoryScreen` (929 dòng) và không ảnh hưởng logic kéo-thả của túi đồ Survival.
- **Đánh đổi:** Thêm ~5 file mới trong codebase.

### Hướng 2 (Không chọn): `abstract GameMode` + Gộp kho Creative vào `InventoryScreen` cũ
- Dùng chung `InventoryScreen` cho cả 2 chế độ bằng cách chuyển đổi vùng 27 ô balo thành danh sách cuộn khi ở Creative.
- **Lý do không chọn:** Làm `InventoryScreen` phình trên 1.200 dòng, trộn lẫn bố cục tab/thùng rác Creative với logic kéo-thả 36 ô Survival và dễ gây lỗi hồi quy.

---

## 4. Kiến Trúc Chi Tiết

### 4.1. Hệ phân cấp OOP `com.voxelgame.world.gamemode`
- **`public abstract class GameMode`** (Flyweight bất biến, không cấp phát khi chuyển chế độ):
  - `getId()` / `getDisplayName()` (`"CREATIVE"` / `"SURVIVAL"`)
  - `canFly()` — Creative `true`, Survival `false`
  - `canUseTerrainTools()` (`F1`, `Ctrl+C`) — Creative `true`, Survival `false`
  - `isInvulnerable()` — Creative `true`, Survival `false`
  - `consumesItems()` — Creative `false`, Survival `true`
  - `breaksInstantly()` — Creative `true`, Survival `false`
  - `canBreakBlock(Block block)` — Creative: mọi block khác `AIR` và không phải chất lỏng (bao gồm cả **`Block.BEDROCK`**); Survival: `block.isBreakable()`
  - `dropsBlockItems()` — Creative `false`, Survival `true`
  - `showsSurvivalHud()` — Creative `false`, Survival `true`
  - `isTargetableByMobs()` — Creative `false`, Survival `true`
  - `usesCreativeInventory()` — Creative `true`, Survival `false`
  - `onEnter(Player player)` — hook đa hình chuẩn hóa trạng thái khi vừa chuyển sang chế độ này
  - `toggle()` — trả về chế độ đối lập (`CreativeGameMode.INSTANCE` ↔ `SurvivalGameMode.INSTANCE`)
- **`public final class SurvivalGameMode extends GameMode`** (`INSTANCE`):
  - `onEnter(Player)`: tắt bay (`setFlying(false)`), đưa tốc độ địa hình `F1` về `1x`, xóa đà rơi và bộ đếm double-tap.
- **`public final class CreativeGameMode extends GameMode`** (`INSTANCE`):
  - `onEnter(Player)`: hồi đầy máu/đói/saturation/Oxy và dập lửa nếu đang cháy.

### 4.2. Mặc định khi khởi tạo & Bảo toàn 28 bộ kiểm thử cũ
- `new Player(...)` mặc định gán `gameMode = SurvivalGameMode.INSTANCE` để toàn bộ 28 file test hồi quy CPU hiện có (test sát thương, Oxy, đói, mob tấn công...) hoạt động nguyên vẹn 100%.
- `VoxelGameApp.create()` gọi `player.setGameMode(CreativeGameMode.INSTANCE)` ngay sau khi tạo `Player`, đảm bảo khi người dùng chạy game sẽ mặc định ở chế độ **Creative**.

### 4.3. Phím tắt `Ctrl + M` & Quy tắc tương tác
- **`GameInputController.keyDown`:**
  - Khi `!host.isPaused()` và nhấn `M` cùng `leftControlDown || rightControlDown`:
    - Đóng màn hình túi đồ đang mở (nếu có) để đồng bộ UI.
    - Gọi `player.toggleGameMode()`, `resetMiningProgress()`, hủy phiên `ItemUseHandler` nếu đang kéo cung/ăn/uống.
    - Kích hoạt thông báo ngắn 2,5 giây trên HUD (`MODE: CREATIVE` / `MODE: SURVIVAL`).
  - `F1` và `Ctrl + C`: kiểm tra `player.getGameMode().canUseTerrainTools()` trước khi thực thi.
- **`Player`:**
  - `handleInput`: chỉ cho nhận diện double-tap `Space` bật bay khi `gameMode.canFly()`.
  - `damage(...)`: trả `false` ngay khi `gameMode.isInvulnerable()`.
  - `updateOxygen(...)`, `updateRegeneration(...)`, `updateLavaContact(...)`, `onHungerEffect(...)`, `touchCactus(...)`, `ignoresFall()`: bỏ qua tiêu hao/sát thương khi `gameMode.isInvulnerable()`.
- **Đào & Đặt block (`GameInputController` + `World`):**
  - Trong Creative (`breaksInstantly()`): nhấn chuột trái vào block (khi không đánh trúng mob) sẽ phá block ngay lập tức (kể cả `Bedrock`), phát hạt + âm thanh phá block + kích hoạt hàng đợi cập nhật block lân cận nhưng **không** sinh `ItemEntity` (qua `World.breakBlockWithoutDrop(x, y, z)`). Có cooldown ngắn (~0,18s) khi giữ chuột trái để tránh phá nhầm nhiều lớp quá nhanh.
  - Khi đặt block (`finishBlockPlacement`) hoặc dùng vật phẩm (`BowItem`, `ThrowableItem`, `FoodItem`, `PotionItem`, `Milk Bucket`): chỉ trừ vật phẩm khi `player.getGameMode().consumesItems()`. Riêng `BowItem` trong Creative cho phép bắn mũi tên thường kể cả khi không có mũi tên trong túi.
- **AI Mob (`MobEntity` / `MobGoals`):**
  - Mọi nhánh tìm mục tiêu người chơi đều kiểm tra `player.getGameMode().isTargetableByMobs()`.
  - Khi người chơi chuyển sang Creative giữa trận, các mob đang nhắm vào người chơi sẽ tự nhả mục tiêu ở lượt tick kế tiếp.

### 4.4. HUD & Debug Overlay
- **`HudRenderer`:**
  - Chỉ vẽ thanh tim, đùi thịt và bọt khí Oxy khi `player.getGameMode().showsSurvivalHud()` trả `true`.
  - Hiển thị thông báo ngắn (toast) phía trên Hotbar trong 2,5 giây mỗi khi đổi chế độ qua `Ctrl + M` (zero-GC dùng chuỗi hằng).
- **`DebugOverlayRenderer` (`F3`):**
  - Bổ sung chế độ hiện tại (`MODE: CREATIVE` / `MODE: SURVIVAL`) vào bảng thông số F3 (tái sử dụng `StringBuilder`, zero-GC).

### 4.5. Kho Vật Phẩm Creative (`CreativeCatalog` & `CreativeInventoryScreen`)
- **`CreativeCatalog` (`com.voxelgame.world.item`):**
  - Tự động tổng hợp toàn bộ khối và vật phẩm hợp lệ trong game một lần lúc khởi tạo (mảng tĩnh bất biến, zero-GC):
    - Toàn bộ 24 khối không phải chất lỏng/len (bao gồm `Bedrock`, `Dirt`, `Grass`, `Stone`, `Oak/Spruce Log & Leaves`, `Sand`, `Gravel`, `Sandstone`, `Cactus`, `Dead Bush`, `Short/Tall Grass`, 4 `Flower`, `Sugar Cane`, `Snow Layer`, `Snow Block`, `Ice`, `Decorative Chest`).
    - Toàn bộ 56 vật phẩm độc lập (gồm 6 khối len `Item.*_WOOL` liên kết 2 chiều với `Block.*_WOOL`, vũ khí/công cụ, 19 thức ăn, 4 thuốc Witch + Milk Bucket, vật phẩm rơi mob, đĩa nhạc, sừng dê...).
  - Phân nhóm thành 5 tab: **`ALL`**, **`BLOCKS`**, **`COMBAT`**, **`FOOD`**, **`ITEMS`**.
- **`CreativeInventoryScreen extends Screen` (`com.voxelgame.client.gui`):**
  - `pausesWorld()` trả `false` (giống `InventoryScreen`).
  - Bố cục: 5 tab danh mục phía trên, lưới **9 cột × 5 hàng** có thanh cuộn (hỗ trợ cuộn chuột & bấm nút `▲`/`▼`), hàng **9 ô Hotbar** phía dưới (đồng bộ trực tiếp với `player.getHotbar()`), và 1 ô **Thùng rác `[X]`** (click khi đang cầm vật phẩm để xóa; Shift+Click vào `[X]` để xóa sạch Hotbar).
  - Tương tác chuột:
    - Click trái vào ô trong Kho: nhặt full stack (`item.getMaxStackSize()`) lên con trỏ.
    - Click phải vào ô trong Kho: nhặt 1 đơn vị lên con trỏ.
    - Shift + Click vào ô trong Kho: thêm trực tiếp 1 full stack vào ô trống/cùng loại trên Hotbar.
    - Click vào 9 ô Hotbar phía dưới: đặt/hoán đổi/gộp bình thường.
    - Hover chuột hiển thị tooltip tên vật phẩm bằng `BitmapFont` dùng chung.
  - Giải phóng toàn bộ `Mesh` trong `dispose()`.

---

## 5. Phạm Vi Kiểm Thử & Đồng Bộ Tài Liệu

- Giữ xanh 100% 28 file kiểm thử CPU cũ (`./gradlew check`).
- Thêm bộ kiểm thử CPU **`GameModeChecks.java`** kiểm chứng:
  - Đa hình `GameMode`, chuyển đổi `Creative ↔ Survival`, trạng thái mặc định và hook `onEnter` (tắt bay + reset `F1` về `1x`).
  - Khóa `F1`, `Ctrl+C`, bay ở Survival; bất tử, miễn đói/Oxy, mob không nhắm mục tiêu ở Creative.
  - Phá Bedrock & phá tức thì không rơi đồ ở Creative; không tiêu hao vật phẩm khi đặt/bắn/ném ở Creative.
  - `CreativeCatalog` bao phủ đủ 100% khối và vật phẩm độc lập; thao tác click/Shift+Click/thùng rác trên `CreativeInventoryScreen`.
- Cập nhật `OopArchitectureChecks.java` xác nhận cây kế thừa `GameMode` và `CreativeInventoryScreen extends Screen`.
- Cập nhật **Phần A của `README.md`** đồng bộ 100% tính chất mới theo `AGENTS.md`.
