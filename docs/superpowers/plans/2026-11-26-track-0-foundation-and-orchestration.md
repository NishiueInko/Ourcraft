# Track 0 — Foundation & Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chốt hạ tầng dùng chung (`Texture` atlas `8×6`, 6 `WoolBlock`, liên kết `Item.*_WOOL` và vùng hook tối giản ở các lớp lõi) và cung cấp runbook điều phối 3 git worktree song song không xung đột.

**Architecture:** Mở rộng `Texture` từ `8×5` lên `8×6` giữ nguyên chỉ số tile `0..35`, đăng ký 6 `WoolBlock` (ID `27..32`) nối vào `Item.WHITE_WOOL..PINK_WOOL` mà giữ nguyên danh sách 56 `STANDALONE_ITEMS` của `ItemTextureAtlas`, sau đó đánh dấu các vùng hook rõ ràng tại `VoxelGameApp`, `Player`, `UIRenderer` trước khi rẽ 3 worktree.

**Tech Stack:** Java 17 (UTF-8), Gradle (`.\gradlew.bat regressionChecks`), libGDX 1.13.1, Git Worktrees.

**Spec:** `docs/superpowers/specs/2026-11-26-minecraft-parity-upgrade-design.md`

## Global Constraints
- Java 17 (`options.release = 17`), mã hóa nguồn `UTF-8`, `libGDX 1.13.1`, OpenGL 3.3 Core, V-Sync 60 FPS (`deltaTime <= 0.05f`).
- Chú thích tiếng Việt đầy đủ cho mọi class, hằng số, phương thức và bước xử lý quan trọng; cấm tuyệt đối code rút gọn, `// TODO`, `// FIXME`.
- Không thêm bất kỳ file PNG/OGG/WAV mới nào; chỉ dùng tài nguyên có sẵn trong `src/main/resources/`.
- Bảo toàn 100% hành vi cũ và toàn bộ 24 file kiểm thử trong `.\gradlew.bat regressionChecks` (kể cả `MobLootChecks` kiểm tra 56 vật phẩm độc lập).
- Không cấp phát rác GC trong vòng lặp game và giải phóng triệt để tài nguyên qua `Disposable.dispose()`.

## Review Focus
- `Item.allStandaloneItems().size()` bị lệch khỏi `56` khi liên kết `Item.*_WOOL` với `WoolBlock` → kiểm tra bằng `assertStandaloneCountAndWoolBlockLinks` trong Task 0.1.
- Chỉ số UV của tile cũ `0..35` bị dịch khi đổi `ATLAS_ROWS` từ `5` sang `6` (vì `Chunk` và `Block.getTileUV` dùng `ATLAS_ROWS`) → kiểm tra tỷ lệ `v0/v1` của tile cũ và `TILE_WHITE_WOOL..TILE_PINK_WOOL` (`36..41`) trong Task 0.1.
- Cắt lông `Sheep` hoặc rơi đồ `WoolBlock` trả về 2 object `Item` khác nhau không cộng dồn được trong `Hotbar` → kiểm tra `Item.of(Block.WHITE_WOOL) == Item.WHITE_WOOL` và ngược lại `Item.WHITE_WOOL.getBlock() == Block.WHITE_WOOL` trong Task 0.1.
- Đánh rơi `WoolBlock` làm vỡ quy tắc `isCubeItem()` của `ItemEntity` → kiểm tra `Item.WHITE_WOOL.isCubeItem() == true` trong Task 0.1.
- Thêm vùng hook vào `VoxelGameApp`, `Player`, `UIRenderer` làm thay đổi chữ ký hoặc hành vi input hiện tại → chạy trọn vẹn `.\gradlew.bat regressionChecks` trong Task 0.2.

---

### Task 0.1: Mở rộng `Texture` Atlas (`8×6`), tạo `WoolBlock` (ID `27..32`) và liên kết `Item.*_WOOL`

**Recommended Model:** `flash`

**Files:**
- Create: `src/main/java/com/voxelgame/entity/WoolBlock.java`
- Modify: `src/main/java/com/voxelgame/engine/Texture.java`
- Modify: `src/main/java/com/voxelgame/entity/Block.java`
- Modify: `src/main/java/com/voxelgame/entity/Item.java`
- Modify: `src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java`

**Interfaces:**
- Consumes: `Texture.ATLAS_COLS = 8`, `Block(byte id, String displayName, float hardness, boolean solid, boolean liquid, boolean opaque, float[] iconColor)`, `Item.STANDALONE_ITEMS` (56 items).
- Produces:
  - `Texture.ATLAS_ROWS = 6`, `Texture.ATLAS_HEIGHT = 96`, `Texture.TILE_WHITE_WOOL = 36` .. `Texture.TILE_PINK_WOOL = 41`
  - `Block.WHITE_WOOL` (`27`), `Block.LIGHT_GRAY_WOOL` (`28`), `Block.GRAY_WOOL` (`29`), `Block.BLACK_WOOL` (`30`), `Block.BROWN_WOOL` (`31`), `Block.PINK_WOOL` (`32`)
  - `Item.WHITE_WOOL.isBlock() == true` (tương tự cho 5 màu còn lại) và `Item.of(Block.*_WOOL) == Item.*_WOOL`

- [ ] **Step 1: Viết test lỗi trong `VoxelRegressionChecks.java`**

```java
private static void checkWoolFoundation() {
    require(Texture.ATLAS_ROWS == 6 && Texture.ATLAS_HEIGHT == 96, "Atlas khối phải mở rộng lên 8x6 (128x96)");
    require(Item.allStandaloneItems().size() == 56, "Phải giữ nguyên 56 standalone items trong ItemTextureAtlas");
    Block[] wools = {Block.WHITE_WOOL, Block.LIGHT_GRAY_WOOL, Block.GRAY_WOOL,
                     Block.BLACK_WOOL, Block.BROWN_WOOL, Block.PINK_WOOL};
    Item[] woolItems = {Item.WHITE_WOOL, Item.LIGHT_GRAY_WOOL, Item.GRAY_WOOL,
                        Item.BLACK_WOOL, Item.BROWN_WOOL, Item.PINK_WOOL};
    for (int i = 0; i < wools.length; i++) {
        require(Byte.toUnsignedInt(wools[i].getId()) == 27 + i, "ID len phải nối tiếp 27..32");
        require(woolItems[i].isBlock() && woolItems[i].getBlock() == wools[i], "Item len phải trỏ đúng WoolBlock");
        require(Item.of(wools[i]) == woolItems[i], "Item.of(WoolBlock) phải trả về đúng Item len");
        require(woolItems[i].isCubeItem(), "Len rơi phải giữ dạng khối mini");
    }
}
```
Thêm lời gọi `checkWoolFoundation();` vào `VoxelRegressionChecks.main`.

- [ ] **Step 2: Chạy test xác nhận biên dịch lỗi**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `Block.WHITE_WOOL..PINK_WOOL` và `Texture.ATLAS_ROWS` vẫn là `5`.

- [ ] **Step 3: Triển khai `WoolBlock`, mở rộng `Texture` và liên kết `Block`/`Item`**
  - Trong `Texture.java`: đổi `ATLAS_ROWS = 6`, thêm 6 hằng số `TILE_WHITE_WOOL = 36` .. `TILE_PINK_WOOL = 41`, nạp từ `"textures/item/white_wool.png"` .. `"textures/item/pink_wool.png"` với `TileStyle.OPAQUE`.
  - Tạo `WoolBlock.java`: kế thừa `Block`, `hardness = 0.8f`, `solid = true`, `liquid = false`, `opaque = true`, `getFaceTile(Face)` trả `tileIndex`, `getRenderLayer()` trả `RenderLayer.OPAQUE`, `getDropItem()` trả `Item.of(this)`.
  - Trong `Block.java`: đăng ký 6 hằng số `WHITE_WOOL..PINK_WOOL` (ID `27..32`) sau `CHEST`.
  - Trong `Item.java`: giữ `STANDALONE_ITEMS` nguyên 56 phần tử (nhờ `wool(...)` vẫn gọi `STANDALONE_ITEMS.add(item)`), nhưng gán trường `block` của 6 `Item.*_WOOL` trỏ về `Block.*_WOOL` (dùng phương thức helper `wool(String, String, int, Block)`), đồng thời trong khối `static` sau khi tạo `Block.*_WOOL` ánh xạ `BY_BLOCK_ID[Byte.toUnsignedInt(block.getId())] = woolItem`.

- [ ] **Step 4: Chạy lại bộ kiểm thử**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS với dòng cuối `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task 0.1**

```bash
git add src/main/java/com/voxelgame/entity/WoolBlock.java src/main/java/com/voxelgame/engine/Texture.java src/main/java/com/voxelgame/entity/Block.java src/main/java/com/voxelgame/entity/Item.java src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java
git commit -m "feat: mo rong atlas 8x6 va dang ky 6 WoolBlock"
```

---

### Task 0.2: Đánh dấu vùng Hook tối giản cho 3 Track trong các lớp lõi

**Recommended Model:** `flash`

**Files:**
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java`
- Modify: `src/main/java/com/voxelgame/entity/Player.java`
- Modify: `src/main/java/com/voxelgame/engine/UIRenderer.java`

**Interfaces:**
- Produces: Các vùng comment mốc tách biệt theo Track (`// === HOOK: TRACK A ===`, `// === HOOK: TRACK B ===`, `// === HOOK: TRACK C ===`) ở phần khai báo trường, vòng `update`/`render` và xử lý `keyDown`/`touchDown`/`touchUp` để 3 worktree chỉnh sửa ở các dải dòng tách biệt nhau.

- [ ] **Step 1: Xác định các vị trí chèn hook (không đổi logic)**
  - `VoxelGameApp.java`: tách 3 khối comment mốc riêng biệt cho Track A (input chiến đấu/viewmodel/F5), Track B (ParticleManager/LiquidAnimator/BlockOutlineRenderer), Track C (Inventory `E`/`Q`, `F3` Debug, tốc độ `Shears`).
  - `Player.java`: tách 3 khối comment mốc riêng biệt cho Track A (trạng thái dùng item/F5/view bobbing), Track C (Oxy/Sprint-swim/PlayerInventory).
  - `UIRenderer.java`: tách 2 khối comment mốc riêng biệt cho Track B (ẩn/tùy biến crosshair hoặc thông báo) và Track C (thanh Oxy bọt khí, InventoryScreenRenderer, DebugOverlayRenderer).

- [ ] **Step 2: Chèn các khối mốc chú thích tiếng Việt thuần túy**
  - Không thay đổi bất kỳ biểu thức, điều kiện, hay lời gọi hàm nào đang chạy.

- [ ] **Step 3: Chạy toàn bộ regressionChecks xác nhận không hồi quy**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 4: Commit mốc Foundation**

```bash
git add src/main/java/com/voxelgame/VoxelGameApp.java src/main/java/com/voxelgame/entity/Player.java src/main/java/com/voxelgame/engine/UIRenderer.java
git commit -m "chore: danh dau vung hook doc lap cho 3 track song song"
```

---

### Task 0.3: Sửa lỗi `Stone` & `Bedrock` bị trắng, giải nén sprite từ `texture.zip` và tải 8 nhóm SFX `1.21.11`

**Recommended Model:** `flash`

**Files:**
- Modify: `src/main/java/com/voxelgame/engine/Texture.java`
- Extract into: `src/main/resources/textures/` (`stone.png`, `bedrock.png`, `fire_0.png`, `fire_1.png`, `destroy_stage_0..9.png`, `entity/player/steve.png`, `gui/container/inventory.png`, `gui/sprites/hud/**`, `gui/sprites/widget/**`)
- Download into: `src/main/resources/audio/sfx/` (`fire/{fire,ignite}.ogg`, `damage/{burn1..3,drown1..4,fallbig,fallsmall}.ogg`, `random/{fizz,eat1..3,burp,drink,bowhit1..4}.ogg`, `item/horn/call0.ogg`, `entity/player/attack/{crit,knockback1..2}.ogg`, `dig/cloth1..4.ogg`, `mob/sheep/shear.ogg`)
- Modify: `src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java`

**Interfaces:**
- Consumes: Archive 7z tại `C:\Users\Admin\.gemini\antigravity\brain\505e56c1-4506-413e-a523-45dea8f3a875\.user_uploaded\media_1795711540126_d8019cf4.zip` (giải nén bằng `tar -xf`) và Mojang Asset Index phiên bản `1.21.11` (`piston-meta.mojang.com` → `resources.download.minecraft.net`, chạy một lệnh mạng ngoài sandbox duy nhất).
- Produces:
  - `Texture.readNormalizedPixel(Pixmap src, int sx, int sy)` (xử lý chuẩn `Pixmap.Format.Alpha` / `LuminanceAlpha` / `Intensity` sinh ra từ PNG Grayscale `colorType=0` như `stone.png`, `bedrock.png`, `spruce_leaves.png`, `grass_block_side_overlay.png`: khôi phục `r = g = b = luminance` thay vì `255,255,255`).
  - Đầy đủ PNG & OGG trong `src/main/resources/` để 3 worktree cùng có sẵn ngay sau khi rẽ nhánh.

- [ ] **Step 1: Viết test lỗi `checkGrayscaleBlockTextures()` trong `VoxelRegressionChecks.java`**
  - Kiểm tra tồn tại các file trích xuất mới (`fire_0.png`, `destroy_stage_0.png`, `entity/player/steve.png`, `gui/sprites/hud/hotbar.png`, `gui/container/inventory.png`) và các SFX mới tải (`fire/fire.ogg`, `fire/ignite.ogg`, `random/fizz.ogg`, `random/eat1.ogg`, `random/drink.ogg`, `random/bowhit1.ogg`, `item/horn/call0.ogg`, `damage/drown1.ogg`, `entity/player/attack/crit.ogg`, `dig/cloth1.ogg`, `mob/sheep/shear.ogg`).
  - Nạp `stone.png` và `bedrock.png` qua `Pixmap` + gọi `Texture.readNormalizedPixel(...)` → xác nhận có sự khác biệt độ sáng xám thực sự (`r == g && g == b && r < 220`, không bị trắng tinh `255,255,255`).

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL vì chưa có file trích xuất, chưa có file SFX mới và chưa có `Texture.readNormalizedPixel`.

- [ ] **Step 3: Giải nén chọn lọc từ `texture.zip`, tải 8 nhóm SFX từ Mojang & sửa `Texture.blitResizedToTile`**
  - Chạy `tar -xf` vào thư mục tạm rồi chép đúng các nhánh cần thiết vào `src/main/resources/textures/` (`block/stone.png` → `textures/stone.png`, `block/bedrock.png` → `textures/bedrock.png`, `block/fire_0.png`, `block/fire_1.png`, `block/destroy_stage_0..9.png`, `entity/player/wide/steve.png` → `textures/entity/player/steve.png`, `gui/container/inventory.png`, `gui/sprites/hud/**`, `gui/sprites/widget/**`), xóa thư mục tạm sau khi xong.
  - Chạy script PowerShell/Python (ngoài sandbox) tra cứu manifest `1.21.11` từ `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` → tải đúng hash của `minecraft/sounds/...` cho 8 nhóm SFX trên về `src/main/resources/audio/sfx/`.
  - Cập nhật `Texture.blitResizedToTile`: tách helper `readNormalizedPixel(Pixmap src, int sx, int sy)` kiểm tra `src.getFormat()`: nếu là `Pixmap.Format.Alpha` hoặc `Intensity`, lấy `lum = pixel & 0xFF` gán thành `(lum << 24) | (lum << 16) | (lum << 8) | 0xFF`; nếu `LuminanceAlpha`, tách kênh độ sáng làm `r = g = b` giữ nguyên `a`.

- [ ] **Step 4: Chạy toàn bộ regressionChecks**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task 0.3**

```bash
git add src/main/java/com/voxelgame/engine/Texture.java src/main/resources/textures/ src/main/resources/audio/sfx/ src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java
git commit -m "fix(task-0): sua loi stone bedrock mau trang, giai nen sprite va tai sfx 1.21.11"
```

---

## Runbook Điều Phối Song Song (Cho Phiên Thực Thi Tiếp Theo)

Sau khi Task 0 hoàn tất và xanh `regressionChecks`, Orchestrator thực hiện đúng trình tự sau:

### 1. Tạo 3 Git Worktree từ commit mốc Task 0
```powershell
git worktree add ..\VoxelGame-track-a -b feat/combat-items-viewmodel
git worktree add ..\VoxelGame-track-b -b feat/visuals-lighting-particles
git worktree add ..\VoxelGame-track-c -b feat/survival-inventory-world
```

### 2. Thực thi song song theo Superpowers (`subagent-driven-development`)
Mỗi worktree chạy tuần tự các Task của riêng mình (mỗi Task = 1 Implementer + 1 Task Reviewer):

| Worktree / Track | Plan File | Model cho Implementer | Model cho Reviewer |
| --- | --- | --- | --- |
| **Track A** | `2026-11-26-track-a-combat-items-viewmodel.md` | Task A1, A2, A3, A6: `flash` · Task A4, A5: `inherit` (Medium) | A1–A3, A6: `flash` · A4–A5: `inherit` |
| **Track B** | `2026-11-26-track-b-visuals-lighting-particles.md` | Task B3, B4, B6: `flash` · Task B1, B2, B5: `inherit` (Medium) | B3–B4, B6: `flash` · B1, B2, B5: `inherit` |
| **Track C** | `2026-11-26-track-c-survival-inventory-world.md` | Task C3, C4: `flash` · Task C1, C2, C5, C6: `inherit` (Medium) | C3–C4: `flash` · C1, C2, C5, C6: `inherit` |

### 3. Thứ tự Gộp Nhánh & Tích Hợp Cuối
1. Merge **Track B** vào nhánh chính → chạy `.\gradlew.bat regressionChecks`.
2. Merge **Track C** vào nhánh chính → giải quyết nếu có xung đột nhỏ ở `VoxelGameApp.java`/`Player.java`/`UIRenderer.java` (nằm ở các vùng hook riêng) → chạy `.\gradlew.bat regressionChecks`.
3. Merge **Track A** vào nhánh chính → kiểm tra hook nối `FirstPersonRenderer`, `ItemUseHandler` → đảm bảo `VoxelRegressionChecks.main` gọi đủ `CombatItemsChecks.run()`, `VisualsLightingChecks.run()`, `SurvivalWorldChecks.run()` → chạy `.\gradlew.bat regressionChecks`.
4. Chạy **Final Whole-Branch Review** bằng model `pro` kiểm tra GC churn, giải phóng tài nguyên (`Disposable`), và tuân thủ `AGENTS.md`.
5. Cập nhật đầy đủ **Phần A của `README.md`** phản ánh 100% tính năng mới → chạy `.\gradlew.bat regressionChecks` lần cuối → dọn 3 worktree (`git worktree remove ...`).
