# Creative & Survival GameMode (`Ctrl + M`) + Creative Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm hệ thống chế độ chơi Creative & Survival chuẩn OOP chuyển đổi tức thì bằng `Ctrl + M` (mặc định Creative khi khởi chạy game), khóa bay/`F1`/`Ctrl+C` ở Survival, và bổ sung Kho Creative (`E`) chứa toàn bộ khối & vật phẩm trong game để minh họa.

**Architecture:** Thiết kế tầng logic `com.voxelgame.world.gamemode` gồm `abstract GameMode` và 2 lớp con Flyweight bất biến `CreativeGameMode`, `SurvivalGameMode` điều phối toàn bộ hành vi bằng đa hình (zero-GC, không `if/else` theo loại chế độ). Tầng giao diện bổ sung `CreativeInventoryScreen extends Screen` đọc từ `CreativeCatalog` tĩnh bất biến, tách biệt hoàn toàn với `InventoryScreen` 36 ô của Survival.

**Tech Stack:** Java 17 (UTF-8), Gradle (`./gradlew check` / `regressionChecks`), libGDX 1.13.1 (OpenGL 3.3 Core, zero-GC Game Loop).

**Spec:** [2026-11-28-gamemode-creative-survival-design.md](file:///d:/VoxelGame/docs/superpowers/specs/2026-11-28-gamemode-creative-survival-design.md)

## Global Constraints

- Bảo toàn 100% tính năng gameplay, đồ họa, vật lý, UI/HUD, âm thanh và 60 FPS V-Sync (trừ các điểm thay đổi được yêu cầu trực tiếp).
- Không cấp phát rác GC trong Game Loop (`render`/`update`) và không rò rỉ tài nguyên GPU (`Mesh`/`Texture` phải giải phóng trong `dispose()`).
- Làm nổi bật rõ 4 tính chất OOP (Đóng gói, Kế thừa, Trừu tượng, Đa hình) theo `AGENTS.md` mục 1.4.
- Viết đủ 100% logic từng file, chú thích tiếng Việt UTF-8 rõ ràng, tuyệt đối không có `// TODO` hay code rút gọn.
- Biên dịch sạch trên Java 17 và chạy xanh toàn bộ `./gradlew check`.
- Đồng bộ đầy đủ Phần A của `README.md` sau khi hoàn tất.

## Review Focus

- **Chuyển Creative → Survival khi đang bay và tốc độ `F1 = 50x`:** `SurvivalGameMode.onEnter(Player)` phải tắt `isFlying`, đặt `terrainSpeedIndex = 0` (`1x`), xóa đà rơi cũ để không kẹt tốc độ 50× hay rơi bất thường.
- **Nhấn `Ctrl + M` khi đang mở `CreativeInventoryScreen` hoặc `InventoryScreen`:** Phải đóng màn hình đang mở, trả đồ trên con trỏ (nếu ở Survival), khóa lại chuột và tránh lệch UI với chế độ mới.
- **Giữ chuột trái trong Creative:** Phá tức thì (kể cả Bedrock) cần có cooldown ngắn (`0.18f` giây) giữa các lần phá liên tiếp để không đục xuyên nhiều lớp đất trong 1 cú nhấn.
- **Chuyển Survival → Creative giữa trận khi mob đang đuổi:** `MobEntity` phải xóa `target` ngay ở lượt tick kế tiếp và từ chối nhận mục tiêu mới khi `!player.getGameMode().isTargetableByMobs()`.
- **Dùng `Bow` trong Creative khi túi đồ không có mũi tên:** `BowItem` phải cho phép kéo dây và bắn `ArrowEntity` thường mà không cần tìm/trừ mũi tên trong Hotbar.

---

### File Structure Map

| File | Hành động | Trách nhiệm |
| --- | --- | --- |
| `src/main/java/com/voxelgame/world/gamemode/GameMode.java` | Tạo mới | Lớp trừu tượng Flyweight định nghĩa hợp đồng đa hình cho chế độ chơi |
| `src/main/java/com/voxelgame/world/gamemode/CreativeGameMode.java` | Tạo mới | Triển khai chế độ Creative (bay, bất tử, phá tức thì gồm Bedrock, đồ vô hạn, kho Creative) |
| `src/main/java/com/voxelgame/world/gamemode/SurvivalGameMode.java` | Tạo mới | Triển khai chế độ Survival (khóa bay/`F1`/`Ctrl+C`, sinh tồn đầy đủ) |
| `src/main/java/com/voxelgame/world/item/CreativeCatalog.java` | Tạo mới | Danh mục tĩnh zero-GC toàn bộ ~80 khối & vật phẩm chia theo 5 tab |
| `src/main/java/com/voxelgame/client/gui/CreativeInventoryScreen.java` | Tạo mới | Màn hình Kho Creative kế thừa `Screen` (5 tab, lưới 9×5 cuộn, Hotbar 9 ô, Thùng rác `[X]`) |
| `src/main/java/com/voxelgame/world/entity/player/Player.java` | Sửa | Sở hữu `GameMode`, kiểm tra đa hình cho bay/sát thương/đói/Oxy/tốc độ/`Ctrl+C` |
| `src/main/java/com/voxelgame/world/entity/mob/MobEntity.java` (+ AI targeting) | Sửa | Bỏ qua người chơi không thể nhắm mục tiêu (`!isTargetableByMobs()`) |
| `src/main/java/com/voxelgame/world/level/World.java` | Sửa | Thêm `breakBlockWithoutDrop(int, int, int)` cho Creative (phá được Bedrock, không rơi `ItemEntity`) |
| `src/main/java/com/voxelgame/world/item/{Bow,Food,Potion,Throwable}Item.java` | Sửa | Kiểm tra `player.getGameMode().consumesItems()` trước khi trừ vật phẩm / đòi mũi tên |
| `src/main/java/com/voxelgame/client/input/GameInputController.java` | Sửa | Bắt `Ctrl+M`, khóa `F1`/`Ctrl+C` theo `GameMode`, phá tức thì Creative, điều phối `E` theo `GameMode` |
| `src/main/java/com/voxelgame/client/gui/{HudRenderer,UIRenderer,DebugOverlayRenderer}.java` | Sửa | Ẩn tim/đói/Oxy ở Creative, hiển thị toast đổi chế độ 2,5s, bổ sung dòng `MODE:` vào `F3` |
| `src/main/java/com/voxelgame/VoxelGameApp.java` | Sửa | Đặt mặc định `CreativeGameMode.INSTANCE` khi khởi chạy, quản lý `CreativeInventoryScreen` và `dispose()` |
| `src/test/java/com/voxelgame/engine/GameModeChecks.java` | Tạo mới | Bộ kiểm thử CPU toàn diện cho `GameMode`, `Ctrl+M`, Bedrock, Creative Inventory |
| `src/test/java/com/voxelgame/engine/{OopArchitectureChecks,VoxelRegressionChecks}.java` | Sửa | Đăng ký `GameModeChecks`, kiểm tra OOP `GameMode`/`CreativeInventoryScreen`, cập nhật test `F1`/Fly |
| `README.md` | Sửa | Đồng bộ Phần A với 100% tính chất mới |

---

### Task 1: Hệ phân cấp OOP `GameMode`, Tích hợp `Player` & Mob Targeting

**Files:**
- Create: `src/main/java/com/voxelgame/world/gamemode/GameMode.java`
- Create: `src/main/java/com/voxelgame/world/gamemode/CreativeGameMode.java`
- Create: `src/main/java/com/voxelgame/world/gamemode/SurvivalGameMode.java`
- Modify: `src/main/java/com/voxelgame/world/entity/player/Player.java`
- Modify: `src/main/java/com/voxelgame/world/entity/mob/MobEntity.java`
- Modify: `src/main/java/com/voxelgame/world/entity/mob/ai/MobGoals.java`
- Test: `src/test/java/com/voxelgame/engine/GameModeChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/OopArchitectureChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java`

**Interfaces:**
- Produces:
  - `GameMode`: `getId()`, `getDisplayName()`, `canFly()`, `canUseTerrainTools()`, `isInvulnerable()`, `consumesItems()`, `breaksInstantly()`, `canBreakBlock(Block)`, `dropsBlockItems()`, `showsSurvivalHud()`, `isTargetableByMobs()`, `usesCreativeInventory()`, `onEnter(Player)`, `toggle()`.
  - `CreativeGameMode.INSTANCE`, `SurvivalGameMode.INSTANCE`.
  - `Player`: `getGameMode()`, `setGameMode(GameMode)`, `toggleGameMode()`, `resetTerrainSpeed()`, `setFlying(boolean)`, `restoreCreativeVitals()`.

- [ ] **Step 1: Tạo `GameMode`, `CreativeGameMode`, `SurvivalGameMode`**
  - `GameMode` là `public abstract class` với các trường `private final String id`, `private final String displayName` (Encapsulation), các phương thức hợp đồng trừu tượng/đa hình, và `toggle()` trừu tượng.
  - `CreativeGameMode.INSTANCE` và `SurvivalGameMode.INSTANCE` là Singleton Flyweight bất biến (zero-GC).
  - `CreativeGameMode.canBreakBlock(Block b)` trả `b != null && b != Block.AIR && !b.isLiquid()` (bao gồm `Block.BEDROCK`).
  - `SurvivalGameMode.canBreakBlock(Block b)` trả `b != null && b.isBreakable()`.
  - `SurvivalGameMode.onEnter(Player p)` gọi `p.setFlying(false)` và `p.resetTerrainSpeed()`.
  - `CreativeGameMode.onEnter(Player p)` gọi `p.restoreCreativeVitals()`.

- [ ] **Step 2: Tích hợp `GameMode` vào `Player` và `MobEntity` / `MobGoals`**
  - `Player` mặc định `private GameMode gameMode = SurvivalGameMode.INSTANCE;` (giữ nguyên toàn bộ 28 bài test hồi quy cũ).
  - `Player.setGameMode(GameMode mode)` gán chế độ (bỏ qua `null`) và gọi `mode.onEnter(this)`.
  - `Player.toggleGameMode()` gọi `setGameMode(gameMode.toggle())` và trả về chế độ mới.
  - `Player.cycleTerrainSpeed()` chỉ tăng mức khi `gameMode.canUseTerrainTools()`.
  - `Player.teleportToBiome(...)` trả `false` khi `!gameMode.canUseTerrainTools()`.
  - `Player.handleInput(...)`: chỉ xử lý double-tap `Space` bật bay khi `gameMode.canFly()`.
  - `Player.damage(...)`: trả `false` khi `gameMode.isInvulnerable()`.
  - `Player.ignoresFall()`: trả `true` khi `gameMode.isInvulnerable() || isFlying || super.ignoresFall()`.
  - `Player.updateOxygen(...)`, `updateLavaContact(...)`, `touchCactus(...)`, `onHungerEffect(...)`: bỏ qua trừ máu/đói/Oxy/cháy khi `gameMode.isInvulnerable()`.
  - `MobEntity.setTarget(LivingEntity)` và `MobEntity.updateReactionTimers(float)`: từ chối / xóa `target` nếu `target instanceof Player p && !p.getGameMode().isTargetableByMobs()`.
  - `MobGoals` (và các lớp monster tìm `world.getPlayer()`): kiểm tra `player.getGameMode().isTargetableByMobs()`.

- [ ] **Step 3: Viết kiểm thử Task 1 trong `GameModeChecks.java` & cập nhật `OopArchitectureChecks.java`**
  - Kiểm tra `GameMode` là `abstract`, `CreativeGameMode`/`SurvivalGameMode` kế thừa `GameMode`, `toggle()` xoay vòng zero-GC.
  - Kiểm tra `Creative` bất tử (không mất máu khi rơi/lava/mob/ngạt nước), không tụt Oxy, `Zombie` không nhắm mục tiêu và tự nhả mục tiêu khi người chơi đổi sang Creative.
  - Kiểm tra chuyển `Creative → Survival` tắt `isFlying` và đưa tốc độ `F1` về `1x`, khóa `F1`/`Ctrl+C`/bay ở Survival.

- [ ] **Step 4: Chạy `./gradlew check` để xác minh Task 1**
  - Lưu ý: cập nhật `VoxelRegressionChecks.checkTerrainSpeedShortcut()` gán `player.setGameMode(CreativeGameMode.INSTANCE)` trước khi test `F1`/Fly.
  - Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Phím tắt `Ctrl + M`, Đào tức thì (gồm Bedrock) & Không tiêu hao đồ trong Creative

**Files:**
- Modify: `src/main/java/com/voxelgame/world/level/World.java`
- Modify: `src/main/java/com/voxelgame/client/input/GameInputController.java`
- Modify: `src/main/java/com/voxelgame/world/item/BowItem.java`
- Modify: `src/main/java/com/voxelgame/world/item/FoodItem.java`
- Modify: `src/main/java/com/voxelgame/world/item/PotionItem.java`
- Modify: `src/main/java/com/voxelgame/world/item/ThrowableItem.java`
- Modify: `src/main/java/com/voxelgame/world/entity/player/Player.java`
- Test: `src/test/java/com/voxelgame/engine/GameModeChecks.java`

**Interfaces:**
- Consumes: `GameMode.canBreakBlock(Block)`, `breaksInstantly()`, `dropsBlockItems()`, `consumesItems()`, `canUseTerrainTools()`, `Player.toggleGameMode()`.
- Produces:
  - `World.breakBlockWithoutDrop(int worldX, int worldY, int worldZ)` — phá mọi block hợp lệ (kể cả `Block.BEDROCK`), phát âm phá block, cập nhật block lân cận, không sinh `ItemEntity`.
  - `GameInputController`: phím `Ctrl + M` gọi `toggleGameMode()`, trả `true`.

- [ ] **Step 1: Thêm `World.breakBlockWithoutDrop(int worldX, int worldY, int worldZ)`**
  - Cho phép phá mọi khối khác `Block.AIR` và không phải chất lỏng (kể cả `Block.BEDROCK`).
  - Ghi `Block.AIR` qua `writeGameplayBlock(...)`, phát `audio.playBreak(...)`, gọi `drainBlockUpdates()` mà không gọi `target.onBreak(...)` (để không rơi `ItemEntity`).

- [ ] **Step 2: Cập nhật `GameInputController` cho `Ctrl + M`, khóa `F1`/`Ctrl+C`, đào tức thì Creative và đặt block không trừ đồ**
  - Thêm `creativeBreakCooldown` (`0.18f` giây) để giữ chuột trái trong Creative phá từng block nhịp nhàng, không đục xuyên nhiều lớp trong 1 frame.
  - Trong `updateMiningProgress`: dùng `mode.canBreakBlock(targetBlock)`; nếu `mode.breaksInstantly()` thì phá ngay qua `world.breakBlockWithoutDrop(...)` (kèm hạt `particleManager.spawnBlockBreak(...)`) và đặt `creativeBreakCooldown = 0.18f`.
  - Trong `finishBlockPlacement`: chỉ gọi `hotbar.consumeSelectedItem()` khi `player.getGameMode().consumesItems()`.
  - Trong `keyDown`:
    - `F1`: chỉ chạy `player.cycleTerrainSpeed()` khi `player.getGameMode().canUseTerrainTools()`.
    - `Ctrl + C`: chỉ chạy `teleportToNextBiome(...)` khi `player.getGameMode().canUseTerrainTools()`.
    - `Ctrl + M`: đóng túi đồ đang mở (nếu có), hủy `itemUseHandler`, gọi `player.toggleGameMode()`, `resetMiningProgress()`, báo `uiRenderer.showGameModeToast(newMode)`.

- [ ] **Step 3: Cập nhật tiêu hao vật phẩm ở `BowItem`, `FoodItem`, `PotionItem`, `ThrowableItem`, `Player`**
  - `BowItem`: khi `!player.getGameMode().consumesItems()`, cho phép kéo dây và bắn `ArrowEntity` thường ngay cả khi không có mũi tên, và không trừ mũi tên nếu có.
  - `ThrowableItem`, `FoodItem`, `PotionItem`, `Player.consumePotion`, `GameInputController` (`Milk Bucket`): chỉ trừ stack khi `player.getGameMode().consumesItems()`.

- [ ] **Step 4: Bổ sung test vào `GameModeChecks.java` và chạy `./gradlew check`**
  - Kiểm tra `Ctrl + M` đổi chế độ qua `VoxelGameApp.keyDown`, phá `Block.BEDROCK` trong Creative thành `Block.AIR` không sinh `ItemEntity`, đặt block/ném `Egg`/bắn `Bow` không giảm số lượng ở Creative.

---

### Task 3: `CreativeCatalog` & Màn hình `CreativeInventoryScreen`

**Files:**
- Create: `src/main/java/com/voxelgame/world/item/CreativeCatalog.java`
- Create: `src/main/java/com/voxelgame/client/gui/CreativeInventoryScreen.java`
- Test: `src/test/java/com/voxelgame/engine/GameModeChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/OopArchitectureChecks.java`

**Interfaces:**
- Produces:
  - `CreativeCatalog.Tab` enum: `ALL`, `BLOCKS`, `COMBAT`, `FOOD`, `ITEMS` (mỗi tab có `getLabel()`).
  - `CreativeCatalog`: `getItems(Tab)`, `getItemCount(Tab)`, `getItem(Tab, int)`.
  - `CreativeInventoryScreen extends Screen`: `pausesWorld() -> false`, `getActiveTab()`, `setActiveTab(Tab)`, `getScrollRow()`, `scroll(float)`, `handleClick(PlayerInventory, float, float, int, int, int, boolean)`, `render(ShaderProgram, PlayerInventory, Texture, ItemTextureAtlas, GuiTextureAtlas, int, int, float, float)`, `getCursorItem()`, `getCursorCount()`, `dispose()`.

- [ ] **Step 1: Tạo `CreativeCatalog`**
  - Khởi tạo một lần trong khối `static` toàn bộ danh sách khối & vật phẩm:
    - Mọi khối `id = 1..255` hợp lệ trừ `AIR`, `WATER`, `LAVA`, và 6 `WoolBlock` (để không trùng với 6 `Item.*_WOOL` vốn đã gắn 2 chiều với `Block.*_WOOL`). Bao gồm cả `Block.BEDROCK`.
    - Toàn bộ 56 vật phẩm trong `Item.getStandaloneItems()`.
  - Phân nhóm rõ ràng vào 5 mảng tĩnh `Item[]` bất biến (`ALL`, `BLOCKS`, `COMBAT`, `FOOD`, `ITEMS`) để truy cập O(1) zero-GC.

- [ ] **Step 2: Tạo `CreativeInventoryScreen extends Screen`**
  - Kế thừa `Screen`, `pausesWorld() -> false`.
  - Bố cục căn giữa màn hình (scale pixel-perfect `1..4` như `InventoryScreen`):
    - Hàng 5 tab phía trên (`ALL`, `BLOCKS`, `COMBAT`, `FOOD`, `ITEMS`).
    - Lưới **9 cột × 5 hàng** (`45` ô hiển thị) + cột thanh cuộn bên phải (nút `▲`, thanh kéo, nút `▼`).
    - Hàng **9 ô Hotbar** phía dưới (đồng bộ trực tiếp với ô `0..8` của `PlayerInventory`) + 1 ô **Thùng rác `[X]`** bên phải hàng Hotbar.
  - Tương tác:
    - Click vào Tab: đổi tab và đặt `scrollRow = 0`.
    - Cuộn chuột / click `▲` hoặc `▼`: đổi `scrollRow` trong `[0, maxScrollRow]`.
    - Click trái ô Kho: đặt `cursorItem = item`, `cursorCount = item.getMaxStackSize()`.
    - Click phải ô Kho: đặt `cursorItem = item`, `cursorCount = 1`.
    - Shift + Click ô Kho: thêm 1 full stack trực tiếp vào Hotbar (ưu tiên gộp hoặc ô trống `0..8`).
    - Click vào 9 ô Hotbar: đặt/hoán đổi/gộp với `cursorItem` giống `InventoryScreen` (nếu Shift+Click vào ô Hotbar thì xóa nhanh ô đó).
    - Click vào `[X]`: xóa `cursorItem`; Shift + Click vào `[X]`: xóa sạch cả 9 ô Hotbar.
  - Dùng chung `BitmapFont` vẽ chữ/tooltip/số lượng, dùng `Texture` và `ItemTextureAtlas` để vẽ icon khối 3D & vật phẩm 2D (có fallback màu khi chạy headless), giải phóng `Mesh` trong `dispose()`.

- [ ] **Step 3: Bổ sung test vào `GameModeChecks.java` & `OopArchitectureChecks.java` và chạy `./gradlew check`**
  - Xác nhận `CreativeInventoryScreen extends Screen`, `pausesWorld() == false`.
  - Xác nhận `CreativeCatalog.getItems(Tab.ALL)` chứa đủ mọi khối không phải chất lỏng (gồm `BEDROCK`, `CHEST`, 6 màu len) và đủ 56 vật phẩm độc lập, không trùng lặp.
  - Xác nhận click lấy full stack, click phải lấy 1, Shift+Click đưa vào Hotbar, cuộn trang và nút Thùng rác `[X]`.

---

### Task 4: HUD, Toast chuyển chế độ, Debug `F3` & Đấu nối `VoxelGameApp`

**Files:**
- Modify: `src/main/java/com/voxelgame/client/gui/HudRenderer.java`
- Modify: `src/main/java/com/voxelgame/client/gui/UIRenderer.java`
- Modify: `src/main/java/com/voxelgame/client/gui/DebugOverlayRenderer.java`
- Modify: `src/main/java/com/voxelgame/client/input/GameInputController.java`
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java`
- Test: `src/test/java/com/voxelgame/engine/GameModeChecks.java`

**Interfaces:**
- Consumes: `GameMode.showsSurvivalHud()`, `usesCreativeInventory()`, `getDisplayName()`, `CreativeInventoryScreen`.
- Produces:
  - `HudRenderer.showGameModeToast(GameMode)` & `updateToast(float deltaTime)` + `UIRenderer` Facade wrappers.
  - `GameInputController.Host.getCreativeInventoryScreen()`.
  - `DebugOverlayRenderer.LINE_COUNT = 8` (thêm dòng `MODE: CREATIVE` / `MODE: SURVIVAL`).

- [ ] **Step 1: Cập nhật `HudRenderer` và `UIRenderer`**
  - Trong `HudRenderer.renderBaseHud`: chỉ gọi `renderVitals(...)` khi `player.getGameMode().showsSurvivalHud()`.
  - Trong `HudRenderer.renderInGameOverlays`: chỉ gọi `renderOxygenBar(...)` khi `player.getGameMode().showsSurvivalHud()`.
  - Thêm `setCreativeInventoryScreen(CreativeInventoryScreen)` và vẽ `creativeInventoryScreen` nếu đang mở.
  - Thêm thông báo ngắn `modeToastText` + `modeToastTimer` (`2.5f` giây) vẽ phía trên Hotbar bằng `BitmapFont` (zero-GC, dùng chuỗi hằng `"MODE: CREATIVE"` / `"MODE: SURVIVAL"`).

- [ ] **Step 2: Cập nhật `DebugOverlayRenderer` (`F3`)**
  - Tăng `LINE_COUNT` từ `7` lên `8` (tương thích hoàn toàn với `require(lineCount >= 7)` trong `SurvivalWorldChecks`).
  - Dòng 8 ghi `MODE: CREATIVE` hoặc `MODE: SURVIVAL` bằng `StringBuilder` thành viên tái sử dụng (zero-GC).

- [ ] **Step 3: Đấu nối hoàn chỉnh trong `GameInputController` và `VoxelGameApp`**
  - `VoxelGameApp.create()`:
    - Gọi `player.setGameMode(CreativeGameMode.INSTANCE);` (mặc định Creative khi khởi chạy game).
    - Khởi tạo `creativeInventoryScreen = new CreativeInventoryScreen();`, truyền sang `uiRenderer.setCreativeInventoryScreen(creativeInventoryScreen)`.
  - `VoxelGameApp.render()`: cập nhật `uiRenderer.updateToast(deltaTime)`.
  - `VoxelGameApp.dispose()`: gọi `creativeInventoryScreen.dispose()`.
  - `GameInputController`:
    - `isInventoryOpen()` kiểm tra cả `inventoryScreen.isOpen()` lẫn `creativeInventoryScreen.isOpen()`.
    - `toggleInventory()` mở `CreativeInventoryScreen` khi `player.getGameMode().usesCreativeInventory()`, ngược lại mở `InventoryScreen`.
    - `closeInventory()` đóng màn hình túi đồ nào đang mở.
    - `touchDown` & `scrolled` định tuyến sự kiện click/cuộn sang `creativeInventoryScreen` khi nó đang mở.

- [ ] **Step 4: Bổ sung test tích hợp vào `GameModeChecks.java` và chạy `./gradlew check`**
  - Kiểm tra `DebugOverlayRenderer` dòng 8 hiển thị đúng `MODE: CREATIVE` / `MODE: SURVIVAL` và tái sử dụng `StringBuilder`.
  - Kiểm tra `E` ở Creative mở `CreativeInventoryScreen`, `Ctrl+M` tự đóng màn hình Creative rồi `E` ở Survival mở `InventoryScreen`.

---

### Task 5: Đồng bộ `README.md` & Xác minh cuối cùng

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Cập nhật Phần A của `README.md`**
  - Mục 2 (Kiến trúc mã nguồn): cập nhật tổng số file Java chính & kiểm thử, bổ sung `world.gamemode` (3 file), `CreativeCatalog`, `CreativeInventoryScreen`, `GameModeChecks`.
  - Mục 4 (Gameplay & Điều khiển): cập nhật mặc định Creative khi mở game, phím tắt `Ctrl + M` chuyển `Creative ↔ Survival`, quy tắc khóa bay/`F1`/`Ctrl+C` ở Survival, đào tức thì gồm Bedrock & không trừ đồ ở Creative, Kho Creative (`E`).
  - Mục 7 (UI/HUD & `F3`): cập nhật ẩn thanh tim/đói/Oxy ở Creative, thông báo chuyển chế độ và dòng `MODE` trên `F3`.

- [ ] **Step 2: Chạy xác minh toàn bộ hệ thống**
  - Run: `.\gradlew.bat check`
  - Expected: biên dịch sạch trên Java 17 (UTF-8), toàn bộ kiểm thử CPU (28 bộ cũ + `GameModeChecks`) pass 100%, `BUILD SUCCESSFUL`.
