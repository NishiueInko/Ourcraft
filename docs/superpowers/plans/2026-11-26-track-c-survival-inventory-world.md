# Track C — Survival, Inventory & World Systems Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện hệ thống sinh tồn và thế giới: Túi đồ 36 ô (`E`) + vứt đồ (`Q`/`Ctrl+Q`), Thanh Oxy 10 bọt khí + Sprint-Swimming, Phân hủy lá cây (`Leaf Decay`) + tốc độ đào `Shears`, Chất lỏng chảy (`Flowing Water/Lava` tạo `Stone`) và Màn hình thông số `F3`.

**Architecture:** Tách toàn bộ nghiệp vụ mới vào 5 lớp độc lập (`PlayerInventory`, `InventoryScreenRenderer`, `DebugOverlayRenderer`, `LiquidFlowSimulator`, `LeafDecaySystem`); nối qua `Hotbar` (giữ nguyên toàn bộ API cũ), vùng hook Track C của `Player`, `UIRenderer`, `World`, `VoxelGameApp`.

**Tech Stack:** Java 17 (UTF-8), libGDX 1.13.1, OpenGL 3.3 Core, Font 5×7 tích hợp sẵn.

**Spec:** `docs/superpowers/specs/2026-11-26-minecraft-parity-upgrade-design.md`

## Global Constraints
- Tuân thủ Task 0 đã có sẵn (`WoolBlock` ID `27..32`, `Item.*_WOOL`, vùng `// === HOOK: TRACK C ===`).
- Không sửa `ShaderProgram.java`, `Chunk.java`, `SkyRenderer.java`, `Texture.java`, `Wolf/Pig/Rabbit` để tránh xung đột với Track A và Track B.
- Java 17 UTF-8, chú thích tiếng Việt đầy đủ, viết 100% mã nguồn, không `// TODO`, không cấp phát rác GC trong Game Loop, giữ 60 FPS.
- Tất cả 24 file test cũ và `SurvivalWorldChecks` mới phải xanh khi chạy `.\gradlew.bat regressionChecks`.

## Review Focus
- Thêm 27 ô túi đồ làm gãy API `player.getHotbar()` đang được hơn 15 file test/gameplay gọi trực tiếp → kiểm tra `Hotbar` ủy quyền trực tiếp vào 9 ô đầu của `PlayerInventory` và toàn bộ khởi tạo mặc định (`Dirt x16..Bucket x1`) giữ nguyên trong Task C1.
- Đánh rơi item bằng `Q` bị nam châm hút ngược lại ngay ở frame sau → kiểm tra `ItemEntity` ném bằng `Q` có `pickupDelay >= 1.0f` và bay ra phía trước mặt trong Task C1.
- Mở túi đồ `E` vẫn cho phép xoay camera hoặc đào block khi click chuột trong giao diện → kiểm tra khi `inventoryOpen == true` con trỏ mở (`setCursorCatched(false)`), không xoay camera và click chuột chuyển vào `InventoryScreenRenderer` trong Task C2.
- Người chơi tự đặt `Oak Leaves` / `Spruce Leaves` để xây nhà bị `LeafDecaySystem` xóa mất → kiểm tra bit metadata `1` đánh dấu lá đặt tay không bao giờ tự phân hủy trong Task C4.
- Phá bờ hồ lớn làm `LiquidFlowSimulator` chạy đệ quy vô hạn tụt FPS → kiểm tra giới hạn `MAX_UPDATES_PER_TICK = 64` và khoảng cách chảy chuẩn (Water `7` ô, Lava `3` ô) trong Task C5.

---

### Task C1: `PlayerInventory` (36 ô), đồng bộ `Hotbar` & vứt vật phẩm `Q` / `Ctrl+Q`

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/entity/PlayerInventory.java`
- Modify: `src/main/java/com/voxelgame/entity/Hotbar.java`
- Modify: `src/main/java/com/voxelgame/entity/ItemEntity.java` (thêm overload/setter cho `pickupDelay` tuỳ chỉnh nếu chưa có)
- Modify: `src/main/java/com/voxelgame/entity/Player.java` (vùng HOOK: TRACK C)
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (vùng HOOK: TRACK C)
- Create: `src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java`

**Interfaces:**
- Consumes: `Hotbar` (`SLOT_COUNT = 9`, `MAX_STACK_SIZE = 64`), `Item`, `ItemEntity`.
- Produces:
  - `PlayerInventory` (`TOTAL_SLOTS = 36`, `0..8` Hotbar, `9..35` Balo): `getItem(int slot)`, `getCount(int slot)`, `setSlot(int slot, Item, int)`, `addItem(Item, int) -> boolean`, `consumeFromSlot(int slot, int amount) -> int`, `getHotbar() -> Hotbar`.
  - `Player.getInventory() -> PlayerInventory`, `Player.dropSelectedItem(World world, boolean wholeStack) -> ItemEntity`.

- [ ] **Step 1: Tạo `SurvivalWorldChecks.java` với test `checkInventoryAndDrop()` bị lỗi**
  - Lấp đầy 9 ô Hotbar, gọi `player.getHotbar().addItem(Item.IRON_INGOT, 10)` → xác nhận tràn sang ô `9` của `PlayerInventory`, tổng 36 ô hoạt động đúng `maxStackSize`.
  - Gọi `player.dropSelectedItem(world, false)` trên ô có `16 Dirt` → ô còn `15`, sinh `ItemEntity` trước mặt với `pickupDelay >= 1.0f`; gọi tiếp với `wholeStack = true` → ô về `Item.NONE` (`0`), sinh `ItemEntity` count `15`.
  - Đăng ký `SurvivalWorldChecks.run();` vào `VoxelRegressionChecks.main`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `PlayerInventory` / `dropSelectedItem`.

- [ ] **Step 3: Triển khai `PlayerInventory`, nối `Hotbar` & phím `Q`**
  - `Hotbar` giữ nguyên constructor và mọi phương thức public, nhưng đọc/ghi trực tiếp vào 9 ô đầu của `PlayerInventory` (hoặc `PlayerInventory` bọc `Hotbar` cho 9 ô đầu và mảng `27` ô sau; lưu ý `addItem` của `Hotbar` chuyển tiếp sang `PlayerInventory.addItem` nếu có gắn owner, giữ nguyên hành vi khi `new Hotbar()` đứng lẻ trong test cũ).
  - Trong `VoxelGameApp` vùng Track C: nhấn `Input.Keys.Q` (khi không pause và không mở Inventory) gọi `player.dropSelectedItem(world, isControlDown())`.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task C1**

```bash
git add src/main/java/com/voxelgame/entity/PlayerInventory.java src/main/java/com/voxelgame/entity/Hotbar.java src/main/java/com/voxelgame/entity/ItemEntity.java src/main/java/com/voxelgame/entity/Player.java src/main/java/com/voxelgame/VoxelGameApp.java src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java
git commit -m "feat(track-c): them PlayerInventory 36 o va phim Q vut vat pham"
```

---

### Task C2: Giao diện Túi đồ `E` (`InventoryScreenRenderer`)

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/InventoryScreenRenderer.java`
- Modify: `src/main/java/com/voxelgame/engine/UIRenderer.java` (vùng HOOK: TRACK C)
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (vùng HOOK: TRACK C)
- Modify: `src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java`

**Interfaces:**
- Consumes: `PlayerInventory`, `Texture`, `ItemTextureAtlas`, `UIRenderer` (Font 5×7 và helper vẽ ô/icon).
- Produces:
  - `InventoryScreenRenderer`: `isOpen()`, `toggle(PlayerInventory)`, `close(Player, World)`, `handleClick(PlayerInventory inv, float mouseX, float mouseY, int screenW, int screenH, int button, boolean shiftDown) -> boolean`, `render(ShaderProgram shader, PlayerInventory inv, Texture blockAtlas, ItemTextureAtlas itemAtlas, int screenW, int screenH, float mouseX, float mouseY)`.

- [ ] **Step 1: Thêm test `checkInventorySlotInteractions()` vào `SurvivalWorldChecks.java`**
  - Kiểm tra logic hoán đổi ô (`handleSlotInteraction(inv, slotA, LEFT_CLICK, false)` rồi `slotB`), chia đôi stack bằng `RIGHT_CLICK` (`16 -> cầm 8, để lại 8`), thả `1` bằng `RIGHT_CLICK`, và `SHIFT + LEFT_CLICK` chuyển nhanh từ ô balo `9` về Hotbar `0..8` và ngược lại.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `InventoryScreenRenderer`.

- [ ] **Step 3: Triển khai `InventoryScreenRenderer` & nối phím `E` trong `VoxelGameApp` / `UIRenderer`**
  - Dùng `Mesh` động tái sử dụng (hoặc dựng lại khi dirty/kích thước cửa sổ đổi, giống bộ đệm Hotbar của `UIRenderer`) vẽ bảng 3×9 balo + 1×9 Hotbar ở giữa màn hình + item đang bám theo con trỏ chuột.
  - Trong `VoxelGameApp`: phím `E` bật/tắt `InventoryScreenRenderer` (không đóng băng thế giới; chỉ mở khóa chuột để thao tác, thoát bằng `E` hoặc `ESC`; nếu đóng khi còn item trên con trỏ thì trả về kho hoặc thả xuống đất).

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task C2**

```bash
git add src/main/java/com/voxelgame/engine/InventoryScreenRenderer.java src/main/java/com/voxelgame/engine/UIRenderer.java src/main/java/com/voxelgame/VoxelGameApp.java src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java
git commit -m "feat(track-c): them giao dien tui do 27 o mo bang phim E"
```

---

### Task C3: Thanh Oxy 10 bọt khí, ngạt nước & Sprint-Swimming (`Ctrl` trong nước)

**Recommended Model:** `flash`

**Files:**
- Modify: `src/main/java/com/voxelgame/entity/Player.java` (vùng HOOK: TRACK C)
- Modify: `src/main/java/com/voxelgame/engine/UIRenderer.java` (vùng HOOK: TRACK C)
- Modify: `src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java`

**Interfaces:**
- Consumes: `World.getBlock(x, y, z)`, `Block.WATER`, `StatusEffectType.WATER_BREATHING`, `Player.hurt(...)`.
- Produces:
  - `Player.AIR_MAX_TICKS = 300`, `Player.getAirTicks() -> int`, `Player.isEyeInWater(World) -> boolean`, `Player.isSwimSprinting() -> boolean`.
  - `UIRenderer`: vẽ hàng 10 bọt khí (`full`/`popping`/`empty`) phía trên thanh đói khi `player.isEyeInWater(world) || player.getAirTicks() < Player.AIR_MAX_TICKS`.

- [ ] **Step 1: Thêm test `checkOxygenAndSprintSwimming()` vào `SurvivalWorldChecks.java`**
  - Đặt `Player` ngập hoàn toàn trong bể `Block.WATER` (`World.empty()`): sau `15.0f` giây (`300` tick), `getAirTicks() == 0`; chạy thêm `2.0f` giây → HP giảm từ `20` xuống `16` (`2 HP/s`).
  - Khi người chơi có `StatusEffectType.WATER_BREATHING`: `getAirTicks()` giữ nguyên `300` và không mất HP.
  - Khi lên khỏi nước: `getAirTicks()` hồi nhanh về `300`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `Player.getAirTicks()`.

- [ ] **Step 3: Triển khai thanh Oxy & Sprint-Swimming**
  - Trong `Player`: kiểm tra block tại cao độ mắt (`y + eyeHeight`) là `Block.WATER`; trừ `airTicks` 20/s nếu không có `WATER_BREATHING`; khi `airTicks <= 0` tích lũy `drownTimer` mỗi `1.0s` trừ `2.0f` HP.
  - Sprint-Swimming: khi `isInLiquid()` trên `Block.WATER` và giữ `Left Ctrl` + di chuyển tiến, nhân tốc độ bơi `1.55x` và hỗ trợ lặn/nổi theo góc `pitch` khi giữ tiến.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task C3**

```bash
git add src/main/java/com/voxelgame/entity/Player.java src/main/java/com/voxelgame/engine/UIRenderer.java src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java
git commit -m "feat(track-c): them thanh Oxy 10 bot khi va Sprint-Swimming"
```

---

### Task C4: `LeafDecaySystem` & Tốc độ đào `Shears` trên Lá và Len

**Recommended Model:** `flash`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/LeafDecaySystem.java`
- Modify: `src/main/java/com/voxelgame/engine/World.java` (vùng HOOK: TRACK C)
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (vùng HOOK: TRACK C)
- Modify: `src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java`

**Interfaces:**
- Consumes: `Block.OAK_LEAVES`, `Block.SPRUCE_LEAVES`, `Block.OAK_LOG`, `Block.SPRUCE_LOG`, `WoolBlock`, `Item.SHEARS`, `Item.STICK`.
- Produces:
  - `LeafDecaySystem.PERSISTENT_LEAF_STATE = 1` (metadata `1` cho lá người chơi tự đặt).
  - `LeafDecaySystem.isConnectedToLog(World world, int x, int y, int z) -> boolean` (BFS cố định bán kính `6`, zero-GC).
  - `LeafDecaySystem.tickLeaf(World world, int x, int y, int z)` (phân hủy thành `AIR` nếu không nối tới gỗ và không phải `PERSISTENT_LEAF_STATE`, `2%` rơi `Item.STICK`).
  - `VoxelGameApp`: khi người chơi đặt `OAK_LEAVES`/`SPRUCE_LEAVES` ghi `state = 1`; khi cầm `Item.SHEARS` đào lá hoặc 6 `WoolBlock` nhân tốc độ đào `5.0x`.

- [ ] **Step 1: Thêm test `checkLeafDecayAndShearsSpeed()` vào `SurvivalWorldChecks.java`**
  - Đặt một khối `OAK_LEAVES` (`state = 0`) lơ lửng không có `OAK_LOG` gần → `isConnectedToLog(...) == false`, gọi `tickLeaf(...)` → đổi thành `Block.AIR`.
  - Đặt một khối `OAK_LEAVES` (`state = 1` — người chơi đặt tay) lơ lửng → gọi `tickLeaf(...)` → vẫn giữ nguyên `Block.OAK_LEAVES`.
  - Đặt `OAK_LOG` cách `3` ô lá nối liền → `isConnectedToLog(...) == true`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `LeafDecaySystem`.

- [ ] **Step 3: Triển khai `LeafDecaySystem`, nối `World` & `VoxelGameApp`**
  - `LeafDecaySystem` dùng mảng `short[]` / `int[]` kích thước cố định cấp phát sẵn cho vùng `(13×13×13)` quanh lá đang kiểm tra (zero-GC).
  - Nối vào lượt random tick (`50 ms`) và `BlockUpdateQueue` trong `World`.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task C4**

```bash
git add src/main/java/com/voxelgame/engine/LeafDecaySystem.java src/main/java/com/voxelgame/engine/World.java src/main/java/com/voxelgame/VoxelGameApp.java src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java
git commit -m "feat(track-c): them LeafDecaySystem va tang toc dao Shears tren la len"
```

---

### Task C5: `LiquidFlowSimulator` (Chảy & tạo `Stone`) + `DebugOverlayRenderer` (`F3`)

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/LiquidFlowSimulator.java`
- Create: `src/main/java/com/voxelgame/engine/DebugOverlayRenderer.java`
- Modify: `src/main/java/com/voxelgame/engine/World.java` (vùng HOOK: TRACK C)
- Modify: `src/main/java/com/voxelgame/engine/UIRenderer.java` (vùng HOOK: TRACK C)
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (vùng HOOK: TRACK C)
- Modify: `src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java`

**Interfaces:**
- Consumes: `Block.WATER`, `Block.LAVA`, `Block.STONE`, `World.setBlock(...)`, `AudioManager`, `World.getSkyLight`/`getBlockLight` (nếu chưa merge Track B thì dùng `DebugOverlayRenderer` đọc an toàn qua helper kiểm tra `World` hoặc mặc định `15/0` đến khi merge).
- Produces:
  - `LiquidFlowSimulator`: `scheduleUpdate(int x, int y, int z, Block liquid)`, `tick(World world, float dt)` (Water mỗi `0.25s`, Lava mỗi `1.50s`, tối đa `64` ô/tick bằng ring-buffer cố định), xử lý Water + Lava -> `Block.STONE`.
  - `DebugOverlayRenderer`: `isVisible()`, `toggle()`, `render(ShaderProgram shader, UIRenderer ui, World world, Player player, Camera camera, int screenW, int screenH)` (zero-GC dùng `StringBuilder` thành viên).

- [ ] **Step 1: Thêm test `checkLiquidFlowAndStoneReaction()` vào `SurvivalWorldChecks.java`**
  - Đặt 1 nguồn `Block.WATER` (`state = 0`) trên sàn đá dài `12` block trong `World.empty()` → chạy `tick` `4.0f` giây → nước lan tối đa `7` block ngang (ô thứ `8` vẫn là `AIR`), mực `state` tăng dần `0..7`.
  - Đặt 1 nguồn `Block.LAVA` (`state = 0`) sát dòng chảy `Block.WATER` → chạy `tick` → vị trí tiếp xúc tạo `Block.STONE`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `LiquidFlowSimulator`.

- [ ] **Step 3: Triển khai `LiquidFlowSimulator`, `DebugOverlayRenderer` & nối `F3`**
  - `LiquidFlowSimulator`: dùng metadata `0` cho source, `1..7` cho dòng chảy; nếu bên dưới là `AIR` hoặc cây chữ X (`CROSS_PLANT`) thì chảy thẳng xuống với mức `1` (rửa trôi cây thành item rơi); bỏ qua cập nhật hàng loạt trong giai đoạn sinh thế giới ban đầu (chỉ kích hoạt khi người chơi đào/đặt hoặc vụ nổ phá bờ).
  - `DebugOverlayRenderer`: phím `Input.Keys.F3` bật/tắt thông số `FPS`, `XYZ`, `CHUNK`, `FACING`, `BIOME`, `ENTITIES` bằng font 5×7 sẵn có của `UIRenderer`.

- [ ] **Step 4: Chạy toàn bộ regressionChecks**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task C5**

```bash
git add src/main/java/com/voxelgame/engine/LiquidFlowSimulator.java src/main/java/com/voxelgame/engine/DebugOverlayRenderer.java src/main/java/com/voxelgame/engine/World.java src/main/java/com/voxelgame/engine/UIRenderer.java src/main/java/com/voxelgame/VoxelGameApp.java src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java
git commit -m "feat(track-c): them LiquidFlowSimulator va man hinh thong so F3"
```

---

### Task C6: `GuiTextureAtlas` — Nâng cấp toàn bộ UX/UI bằng Texture Minecraft 1.21.11 nguyên bản

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/GuiTextureAtlas.java`
- Modify: `src/main/java/com/voxelgame/engine/UIRenderer.java` (vùng HOOK: TRACK C)
- Modify: `src/main/java/com/voxelgame/engine/InventoryScreenRenderer.java`
- Modify: `src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java`

**Interfaces:**
- Consumes: Toàn bộ PNG trong `src/main/resources/textures/gui/` đã giải nén ở Task 0.3 (`sprites/hud/hotbar.png`, `hotbar_selection.png`, `crosshair.png`, `heart/{container,full,half,poisoned_full,poisoned_half,vehicle_container,vehicle_full,vehicle_half}.png`, `food_{empty,full,half,empty_hunger,full_hunger,half_hunger}.png`, `air.png`, `air_bursting.png`, `air_empty.png`, `jump_bar_{background,progress,cooldown}.png`, `container/inventory.png`, `sprites/widget/{button,button_highlighted,slider,slider_handle}.png`).
- Produces:
  - `GuiTextureAtlas implements Disposable`: đóng gói các sprite HUD & Widget thành atlas Nearest-neighbor (giữ riêng `inventory.png` 256×256 hoặc đóng gói vào atlas lớn hơn với UV chính xác từng pixel), cung cấp `uvOf(SpriteKey key) -> float[]`, `getInventoryTexture()`, `bindHudAtlas()`, `dispose()`.
  - `UIRenderer` & `InventoryScreenRenderer`: vẽ pixel-perfect 100% các thành phần UX/UI bằng `GuiTextureAtlas` (Hotbar 9 ô chuẩn `182×22`, khung chọn `24×23`, 10 tim chuyển sang `poisoned` khi trúng độc & tim thú cưỡi `vehicle`, 10 đùi thịt chuyển `hunger` khi trúng hiệu ứng Hunger, 10 bọt khí oxy, thanh Jump/Dash thú cưỡi `182×5`, lưới 36 ô Balo cắt từ `inventory.png` 176×166, và nút Pause Menu từ `widget/button*.png`).

- [ ] **Step 1: Thêm test `checkGuiTexturesPresent()` vào `SurvivalWorldChecks.java`**
  - Kiểm tra toàn bộ danh sách 24+ tệp PNG trong `textures/gui/` tồn tại trong tài nguyên project và giải mã được qua `Pixmap` với kích thước hợp lệ (`hotbar.png` 182×22, `inventory.png` 256×256, `heart/full.png` 9×9, `food_full.png` 9×9, `air.png` 9×9).

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `GuiTextureAtlas`.

- [ ] **Step 3: Triển khai `GuiTextureAtlas`, cập nhật `UIRenderer` & `InventoryScreenRenderer`**
  - `GuiTextureAtlas`: dùng `Nearest` + `ClampToEdge`, đóng gói sạch các sprite HUD + Widget vào 1 `com.badlogic.gdx.graphics.Texture` (512×256 hoặc 256×256) và giữ `inventoryTexture` riêng để vẽ nền container `176×166` phóng `scale = 2` (hoặc `3` theo độ phân giải cửa sổ), giải phóng triệt để trong `dispose()`.
  - Trong `UIRenderer`: giữ nguyên tọa độ hit-test của Pause Menu (hoặc đồng bộ khung hit-test với kích thước nút `widget/button`) để click/kéo thanh volume vẫn hoạt động chuẩn xác 100%; chuyển dải tim/đùi/bọt/hotbar sang vẽ quad UV từ `GuiTextureAtlas` với `GL_BLEND`.

- [ ] **Step 4: Chạy toàn bộ regressionChecks**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task C6**

```bash
git add src/main/java/com/voxelgame/engine/GuiTextureAtlas.java src/main/java/com/voxelgame/engine/UIRenderer.java src/main/java/com/voxelgame/engine/InventoryScreenRenderer.java src/test/java/com/voxelgame/engine/SurvivalWorldChecks.java
git commit -m "feat(track-c): nang cap UX/UI dung texture Minecraft 1.21.11 chuan"
```
