# Track A — Combat, Items & Viewmodel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kích hoạt toàn bộ tính năng chiến đấu và vật phẩm cho người chơi (Cung + 3 loại mũi tên, Giáo sắt Jab/Charge, 19 thức ăn, 4 thuốc Witch, Ender Pearl, Egg, Goat Horn, thuần hóa Wolf, dụ Pig/Rabbit) cùng First-Person Viewmodel, View Bobbing và góc nhìn thứ 3 (`F5`).

**Architecture:** Tách toàn bộ logic sử dụng vật phẩm, bảng thức ăn và kết xuất tay/vật phẩm góc nhìn 1 vào các lớp mới (`FoodRegistry`, `ItemUseHandler`, `FirstPersonRenderer`, `ThrownEnderPearlEntity`, `ThrownEggEntity`); chỉ nối vào `Player`, `Wolf`, `Pig`, `Rabbit` và vùng hook Track A trong `VoxelGameApp`.

**Tech Stack:** Java 17 (UTF-8), libGDX 1.13.1, OpenGL 3.3 Core, `equipment/held.png`, `ItemTextureAtlas`, `MobModels`.

**Spec:** `docs/superpowers/specs/2026-11-26-minecraft-parity-upgrade-design.md`

## Global Constraints
- Tuân thủ Task 0 đã có sẵn (`WoolBlock` ID `27..32`, `Item.*_WOOL`, vùng `// === HOOK: TRACK A ===`).
- Không sửa `Chunk.java`, `ShaderProgram.java`, `SkyRenderer.java`, `Texture.java`, `Hotbar.java` (trừ dùng getter/setter công khai sẵn có) để tránh xung đột với Track B và Track C.
- Java 17 UTF-8, chú thích tiếng Việt đầy đủ, viết trọn vẹn 100% mã nguồn, không `// TODO`, không cấp phát rác GC trong Game Loop, giữ 60 FPS.
- Tất cả 24 file test cũ và `CombatItemsChecks` mới phải xanh khi chạy `.\gradlew.bat regressionChecks`.

## Review Focus
- Giữ chuột phải vào khối đặt được khi đang cầm `Food`/`Bow`/`Potion` có thể vô tình vừa đặt block/tương tác sai hoặc kẹt tiến trình khi đổi slot Hotbar → kiểm tra đổi slot Hotbar reset trạng thái kéo cung/ăn trong Task A4.
- `POTION_*` trả `Item.GLASS_BOTTLE` khi uống ở ô đầy hoặc khi ô lọ thuốc về `0` → kiểm tra thay thế đúng ô hoặc gọi `addItem(GLASS_BOTTLE, 1)` trong Task A1.
- `ThrownEnderPearlEntity` va chạm cạnh block khiến người chơi kẹt trong tường → kiểm tra dịch chuyển lên mặt trống an toàn và trừ `5.0 HP` + reset `fallDistance` trong Task A2.
- `Wolf` đã thuần hóa không tự tấn công chủ khi đánh nhầm và tấn công kẻ đánh chủ → kiểm tra `Wolf` thuần hóa trong Task A3.
- Giáo sắt `IRON_SPEAR` Charge cộng sát thương quá mức khi đứng yên hoặc spam không có vận tốc → kiểm tra công thức `5.0f + min(8.0f, speed * 0.8f)` chỉ kích hoạt khi `speed >= 4.0f` trong Task A4.

---

### Task A1: `FoodRegistry`, ăn 19 món hồi đói & uống 4 lọ thuốc Witch

**Recommended Model:** `flash`

**Files:**
- Create: `src/main/java/com/voxelgame/entity/FoodRegistry.java`
- Modify: `src/main/java/com/voxelgame/entity/LivingEntity.java`
- Modify: `src/main/java/com/voxelgame/entity/Player.java`
- Create: `src/test/java/com/voxelgame/engine/CombatItemsChecks.java`
- Modify: `src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java`

**Interfaces:**
- Consumes: `StatusEffectType` (`HUNGER`, `POISON`, `FIRE_RESISTANCE`, `WATER_BREATHING`, `SWIFTNESS`), `Player.hunger`, `LivingEntity.addEffect(...)`.
- Produces:
  - `FoodRegistry.FoodSpec(int nutrition, float saturation, StatusEffectType effect, float durationSeconds, int amplifier, float effectChance)`
  - `FoodRegistry.of(Item item) -> FoodSpec` (null nếu không phải 19 thức ăn)
  - `Player.consumeFood(Item item) -> boolean`
  - `Player.consumePotion(Item potionItem) -> boolean`
  - `Player.hasFireResistance() -> boolean`, `Player.getSpeedMultiplier() -> float` (tính `SWIFTNESS`), `LivingEntity.isImmuneToFire() -> boolean`

- [ ] **Step 1: Tạo `CombatItemsChecks.java` với test `checkFoodAndPotions()` bị lỗi**

```java
// Kiểm tra đủ 19 món ăn trong FoodRegistry (COOKED_BEEF hồi 8 đói, ROTTEN_FLESH có HUNGER 0.8f)
// Kiểm tra Player.consumeFood tăng hunger & saturation đúng trần (saturation <= hunger <= 20)
// Kiểm tra Player.consumePotion(POTION_HEALING) hồi +4 HP, FIRE_RESISTANCE/SWIFTNESS/WATER_BREATHING tồn tại 180s
```
Đăng ký `CombatItemsChecks.run();` vào `VoxelRegressionChecks.main`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `FoodRegistry` và các hàm tiêu thụ.

- [ ] **Step 3: Triển khai `FoodRegistry`, `LivingEntity` & `Player`**
  - `FoodRegistry`: đăng ký tĩnh (dùng `IdentityHashMap` hoặc switch theo hằng số `Item`) đủ 19 món với đúng thông số `nutrition`, `saturation`, hiệu ứng phụ (`ROTTEN_FLESH` 80% `HUNGER` 30s, `CHICKEN` 30% `HUNGER` 30s, `SPIDER_EYE` 100% `POISON` 4s).
  - `Player`: thêm trường `saturation` (khởi đầu `5.0f`), hàm `consumeFood(Item)` chỉ ăn khi `hunger < 20`, cộng `hunger = min(20, hunger + nutrition)`, `saturation = min(hunger, saturation + satDelta)`, áp dụng hiệu ứng phụ nếu trúng xác suất; trong logic trừ đói tự nhiên/hồi máu dùng `saturation` trước khi trừ `hunger`.
  - `Player.consumePotion(Item)`: xử lý 4 lọ thuốc Witch (`POTION_HEALING` +4 HP, `POTION_FIRE_RESISTANCE`/`POTION_SWIFTNESS`/`POTION_WATER_BREATHING` 180s); khi `FIRE_RESISTANCE` hoạt động thì `LivingEntity`/`Player` miễn sát thương lửa/Lava; khi `SWIFTNESS` hoạt động tăng tốc độ ngang `+20%`.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task A1**

```bash
git add src/main/java/com/voxelgame/entity/FoodRegistry.java src/main/java/com/voxelgame/entity/LivingEntity.java src/main/java/com/voxelgame/entity/Player.java src/test/java/com/voxelgame/engine/CombatItemsChecks.java src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java
git commit -m "feat(track-a): them FoodRegistry 19 mon an va hieu ung 4 lo thuoc"
```

---

### Task A2: Đạn ném `Ender Pearl`, `Egg` và sừng dê `Goat Horn`

**Recommended Model:** `flash`

**Files:**
- Create: `src/main/java/com/voxelgame/entity/projectile/ThrownEnderPearlEntity.java`
- Create: `src/main/java/com/voxelgame/entity/projectile/ThrownEggEntity.java`
- Modify: `src/test/java/com/voxelgame/engine/CombatItemsChecks.java`

**Interfaces:**
- Consumes: `ProjectileEntity`, `World`, `Player`, `Chicken` (`MobType.CHICKEN`).
- Produces:
  - `ThrownEnderPearlEntity(World world, LivingEntity owner, float x, float y, float z, float vx, float vy, float vz)` — `onImpact(...)` dịch chuyển chủ sở hữu lên điểm trống gần nhất và gây `5.0f` sát thương + xóa `fallDistance`.
  - `ThrownEggEntity(World world, LivingEntity owner, float x, float y, float z, float vx, float vy, float vz)` — `onImpact(...)` tung xác suất `1/8` nở `Chicken` con (`setBaby(true)`), trong đó `1/32` nở `4` con.
  - Hằng số cooldown sừng dê `GOAT_HORN_COOLDOWN = 7.0f` dùng lại ở `ItemUseHandler`.

- [ ] **Step 1: Thêm test `checkEnderPearlAndEgg()` vào `CombatItemsChecks.java`**
  - Ném `ThrownEnderPearlEntity` trong `World.empty()` có sàn đá tại `Y=10` → sau khi chạm sàn, vị trí `Player` chuyển tới quanh điểm rơi (`y >= 11`), `fallDistance == 0`, HP giảm từ `20` xuống `15`.
  - Kiểm tra `ThrownEggEntity.rollChickCount(int roll)` trả `0`, `1`, `4` theo đúng xác suất `1/8` và `1/32`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có 2 lớp đạn ném.

- [ ] **Step 3: Triển khai `ThrownEnderPearlEntity` & `ThrownEggEntity`**
  - Kế thừa `ProjectileEntity` (tái sử dụng trọng lực, quét va chạm voxel/thực thể có sẵn, vẽ bằng `Item.ENDER_PEARL` / `Item.EGG` hoặc sprite đạn phù hợp).
  - Với Ender Pearl: kiểm tra 2 ô trống cho chân/đầu người chơi tại vị trí va chạm (lùi nhẹ theo hướng bay nếu đâm mặt bên) trước khi `setPosition` và gây `5.0f` sát thương.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task A2**

```bash
git add src/main/java/com/voxelgame/entity/projectile/ThrownEnderPearlEntity.java src/main/java/com/voxelgame/entity/projectile/ThrownEggEntity.java src/test/java/com/voxelgame/engine/CombatItemsChecks.java
git commit -m "feat(track-a): them dan nem Ender Pearl va Egg"
```

---

### Task A3: Thuần hóa `Wolf` bằng `Bone` & Dụ `Pig` / `Rabbit` bằng `Carrot`/`Potato`

**Recommended Model:** `flash`

**Files:**
- Modify: `src/main/java/com/voxelgame/entity/mob/Wolf.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/Pig.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/Rabbit.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/MobGoals.java` (chỉ nếu `TemptFollowGoal` cần hỗ trợ `Predicate<Item>` hoặc giữ constructor cũ và thêm overload cho `Item`)
- Modify: `src/test/java/com/voxelgame/engine/CombatItemsChecks.java`

**Interfaces:**
- Consumes: `MobEntity`, `Item.BONE`, `Item.CARROT`, `Item.POTATO`, `Block.DANDELION`.
- Produces:
  - `Wolf.interactWithPlayer(Player player, Item heldItem) -> boolean` (thuần hóa bằng `Item.BONE` với xác suất `1/3` khi chưa thuần hóa; bật/tắt `sitting` khi đã thuần hóa và click tay không; `isTamed()`, `isSitting()`, `getOwner()`).
  - `Wolf.onOwnerAttackOrHurt(LivingEntity threat)` giúp sói đã thuần hóa đánh trả mục tiêu tấn công chủ hoặc bị chủ đánh (bỏ qua chủ).
  - `Pig` đi theo người chơi cầm `CARROT` hoặc `POTATO`; `Rabbit` đi theo `DANDELION` hoặc `CARROT`.

- [ ] **Step 1: Thêm test `checkWolfTamingAndTempt()` vào `CombatItemsChecks.java`**
  - Kiểm tra cho `Wolf` thử thuần hóa với seed/roll `< 1/3f` → `isTamed() == true`, `isPersistent() == true`, HP được nâng lên `20`; chuột phải tiếp chuyển trạng thái `isSitting()`; `Pig` nhận diện `CARROT`/`POTATO`; `Rabbit` nhận diện `CARROT`.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `Wolf.interactWithPlayer` / `isTamed`.

- [ ] **Step 3: Triển khai `Wolf`, `Pig`, `Rabbit` & overload `TemptFollowGoal`**
  - Giữ nguyên 100% hành vi hoang dã khi `tamed == false` để không làm gãy `NeutralSnowChecks`.
  - Khi `tamed == true`: không săn `Player` hoặc `Sheep` nếu không cần, ngồi đứng yên khi `sitting == true`, đi theo chủ khi khoảng cách `> 10` block (dịch chuyển tới ô trống gần chủ nếu `> 24` block và không ngồi).

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task A3**

```bash
git add src/main/java/com/voxelgame/entity/mob/Wolf.java src/main/java/com/voxelgame/entity/mob/Pig.java src/main/java/com/voxelgame/entity/mob/Rabbit.java src/main/java/com/voxelgame/entity/mob/MobGoals.java src/test/java/com/voxelgame/engine/CombatItemsChecks.java
git commit -m "feat(track-a): ho tro thuan hoa Wolf va du Pig Rabbit"
```

---

### Task A4: `ItemUseHandler` — Cung (`Bow`), Giáo sắt (`Iron Spear`), Ăn/Uống giữ chuột phải & Ném đạn

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/ItemUseHandler.java`
- Modify: `src/main/java/com/voxelgame/entity/Player.java` (vùng HOOK: TRACK A)
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (vùng HOOK: TRACK A)
- Modify: `src/test/java/com/voxelgame/engine/CombatItemsChecks.java`

**Interfaces:**
- Consumes: `FoodRegistry`, `ThrownEnderPearlEntity`, `ThrownEggEntity`, `ArrowEntity`, `AudioManager`, `Camera`, `Hotbar`.
- Produces:
  - `ItemUseHandler`: quản lý trạng thái dùng vật phẩm liên tục (`NONE`, `DRAW_BOW`, `CHARGE_SPEAR`, `EAT_FOOD`, `DRINK_POTION`), `onSecondaryPress(...)`, `onSecondaryRelease(...)`, `update(float dt, ...)`, `onPrimaryMelee(...)`, `getBowPullProgress() -> float`, `isChargingSpear() -> boolean`, `getUseProgress() -> float`, `getBowZoomOffset() -> float` (tối đa `-8.0f`).

- [ ] **Step 1: Thêm test `checkBowAndIronSpear()` vào `CombatItemsChecks.java`**
  - Kiểm tra cầm `BOW` + `ARROW` kéo đủ `1.0s` rồi nhả → tiêu 1 `ARROW`, sinh 1 `ArrowEntity` trong `World` với vận tốc lớn hơn kéo nhanh `0.2s`.
  - Kiểm tra cầm `IRON_SPEAR` Jab tầm `4.2f` trúng mob ở khoảng `3.8f` (ngoài tầm tay không `3.0f`), và Charge khi `Player` chạy ở tốc độ `6.0f` gây sát thương `>= 9.0f`.
  - Kiểm tra đổi ô Hotbar giữa chừng hủy trạng thái `DRAW_BOW`/`EAT_FOOD` sạch sẽ.

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `ItemUseHandler`.

- [ ] **Step 3: Triển khai `ItemUseHandler` và nối vùng HOOK: TRACK A ở `Player` & `VoxelGameApp`**
  - Thứ tự ưu tiên Click Chuột Phải: (1) Tương tác mob trước mặt (`Wolf` bằng `BONE` / tay không nếu đã thuần hóa, cùng các tương tác cũ `Flint and Steel`, `Shears`, `Bucket`), (2) Bắt đầu kéo `BOW` / hạ `IRON_SPEAR` / giữ ăn `FoodRegistry` / uống `Potion` hoặc `Milk Bucket` / ném `ENDER_PEARL` / `EGG` / thổi `GOAT_HORN`, (3) Nếu không rơi vào nhóm vật phẩm sử dụng thì đặt `Block` như cũ.
  - Với `Camera`: thêm cộng/trừ mượt `bowZoomOffset` (`-8.0f * pullProgress`) mà không ảnh hưởng Dynamic FOV của Sprint/Fly.

- [ ] **Step 4: Chạy test xác nhận pass**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task A4**

```bash
git add src/main/java/com/voxelgame/engine/ItemUseHandler.java src/main/java/com/voxelgame/entity/Player.java src/main/java/com/voxelgame/VoxelGameApp.java src/test/java/com/voxelgame/engine/CombatItemsChecks.java
git commit -m "feat(track-a): them ItemUseHandler cho Cung, Giao sat, Thuc an va Dan nem"
```

---

### Task A5: `FirstPersonRenderer`, View Bobbing & Góc nhìn thứ 3 (`F5`)

**Recommended Model:** `inherit`

**Files:**
- Create: `src/main/java/com/voxelgame/engine/FirstPersonRenderer.java`
- Modify: `src/main/java/com/voxelgame/entity/Player.java` (vùng HOOK: TRACK A)
- Modify: `src/main/java/com/voxelgame/VoxelGameApp.java` (vùng HOOK: TRACK A)
- Modify: `src/test/java/com/voxelgame/engine/CombatItemsChecks.java`

**Interfaces:**
- Consumes: `ItemTextureAtlas`, `Texture`, `MobTextureManager` (`equipment/held.png`, `zombie/zombie.png` hoặc skin tay/humanoid), `ItemUseHandler`, `Camera`.
- Produces:
  - `FirstPersonRenderer implements Disposable`: `render(ShaderProgram shader, Player player, ItemUseHandler useHandler, Texture blockAtlas, ItemTextureAtlas itemAtlas, float viewportAspect)`
  - `Player.CameraMode` (`FIRST_PERSON`, `THIRD_BACK`, `THIRD_FRONT`), `Player.cycleCameraMode()`, `Player.getCameraMode()`

- [ ] **Step 1: Thêm test `checkCameraModeCycle()` vào `CombatItemsChecks.java`**
  - Kiểm tra `Player` mặc định `FIRST_PERSON`, gọi `cycleCameraMode()` 3 lần xoay đúng `FIRST_PERSON -> THIRD_BACK -> THIRD_FRONT -> FIRST_PERSON`.
  - Kiểm tra công thức tính vị trí camera góc nhìn thứ 3 bị rút ngắn khi có tường rắn sau lưng (không xuyên vào block).

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL biên dịch vì chưa có `CameraMode` / `FirstPersonRenderer`.

- [ ] **Step 3: Triển khai `FirstPersonRenderer` và `F5` trong `VoxelGameApp`**
  - `FirstPersonRenderer`: dựng trước `Mesh` tĩnh cho tay phải (dùng skin `textures/entity/player/steve.png` trích từ Task 0.3), khối mini 3D và quad 2D (tái sử dụng UV từ `Block.Face`/`Item.getTileUV()` và `equipment/held.png` cho 4 khung cung + giáo sắt), chỉ đổi ma trận `Matrix4` tĩnh thành viên khi vẽ (zero-GC), xóa depth buffer trước khi vẽ góc nhìn 1 và khôi phục trạng thái OpenGL sau khi vẽ.
  - Tính **View Bobbing** từ quãng di chuyển ngang của `Player` khi `onGround && !isFlying` (dao động sin nhẹ trên camera & tay cầm).
  - Phím `F5` gọi `player.cycleCameraMode()`. Khi ở `THIRD_BACK`/`THIRD_FRONT`, ẩn `FirstPersonRenderer` và vẽ mô hình humanoid của người chơi bằng skin `textures/entity/player/steve.png` tại tọa độ `Player` (quay theo `yaw`/`pitch`, vung tay/chân theo bước đi).

- [ ] **Step 4: Chạy toàn bộ regressionChecks**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task A5**

```bash
git add src/main/java/com/voxelgame/engine/FirstPersonRenderer.java src/main/java/com/voxelgame/entity/Player.java src/main/java/com/voxelgame/VoxelGameApp.java src/test/java/com/voxelgame/engine/CombatItemsChecks.java
git commit -m "feat(track-a): them FirstPersonRenderer, View Bobbing va goc nhin F5"
```

---

### Task A6: Knockback bật nảy chuẩn Minecraft & SFX Hit (`damage/hit1..3.ogg`) cho mọi Entity ↔ Entity

**Recommended Model:** `flash`

**Files:**
- Modify: `src/main/java/com/voxelgame/entity/LivingEntity.java`
- Modify: `src/main/java/com/voxelgame/entity/mob/MobEntity.java`
- Modify: `src/main/java/com/voxelgame/entity/Player.java` (vùng HOOK: TRACK A)
- Modify: `src/main/java/com/voxelgame/engine/AudioManager.java`
- Modify: `src/test/java/com/voxelgame/engine/CombatItemsChecks.java`

**Interfaces:**
- Consumes: `audio/sfx/damage/hit1.ogg .. hit3.ogg`, `LivingEntity.damage(...)`, `MobEntity.applyKnockback(...)`, `Player.applyKnockback(...)`.
- Produces:
  - `LivingEntity.applyKnockback(float x, float z)`: ngoài xung đẩy ngang mượt (`pushDecay` giảm từ `12f` xuống `6.5f` ở `MobEntity` và áp dụng vector đẩy ngang chuẩn ở `Player`), khi `onGround` bật nảy thẳng đứng (`velocity.y = Math.max(velocity.y, 4.8f)` — tương đương nảy lên ~0.4 block chuẩn Minecraft).
  - `AudioManager.playEntityHit(float x, float y, float z)`: phát ngẫu nhiên `damage/hit1..3.ogg` giảm âm tuyến tính trong `16` block mỗi khi bất kỳ `LivingEntity` (Player ↔ Mob hoặc Mob ↔ Mob) nhận sát thương từ một `Entity` tấn công hoặc đạn đạo.

- [ ] **Step 1: Thêm test `checkEntityKnockbackAndHitSfx()` vào `CombatItemsChecks.java`**
  - Cho `Player` đánh `Zombie` đứng trên mặt đất → kiểm tra `zombie.velocity().y >= 4.0f` và vận tốc ngang đẩy lùi khác `0`.
  - Cho `Zombie` đánh trúng `MobEntity`/`Player` (Mob ↔ Mob hoặc Mob ↔ Player) → kiểm tra mục tiêu nhận đồng thời xung lực bật lên `vy > 0` lẫn đẩy lùi ngang và bộ đếm sự kiện hit SFX tăng lên (test trên CPU không cần OpenAL).

- [ ] **Step 2: Chạy test xác nhận fail**

Run: `.\gradlew.bat regressionChecks`
Expected: FAIL vì hiện tại `applyKnockback` chỉ tác động X/Z, không nâng `velocity.y` và chưa phát `damage/hit*.ogg` đồng bộ cho mọi entity.

- [ ] **Step 3: Triển khai Knockback bật nảy & SFX Hit trong `LivingEntity`, `MobEntity`, `Player` & `AudioManager`**
  - Trong `MobEntity.applyKnockback`: khi `onGround && !inWater && (x * x + z * z) > 0.0001f`, đặt `velocity.y = Math.max(velocity.y, 4.8f); onGround = false;` và nới xung đẩy ngang mượt hơn (`pushDecay = Math.max(0f, 1f - dt * 6.5f)`).
  - Trong `Player.applyKnockback`: thực hiện bật nảy tương tự khi bị mob hoặc đạn đánh trúng trên mặt đất (`velocity.y = Math.max(velocity.y, 4.5f)`).
  - Trong `MobEntity.damage(...)` & `Player`: khi `applied == true` và `attacker != null` (kể cả `source == MELEE` hoặc `PROJECTILE`), gọi `world.playEntityHitSound(x(), y(), z())` chuyển tiếp tới `AudioManager.playEntityHit(...)` (bảo đảm `World.empty()` không có AudioManager vẫn an toàn `null`).

- [ ] **Step 4: Chạy toàn bộ regressionChecks**

Run: `.\gradlew.bat regressionChecks`
Expected: PASS `Các kiểm tra hồi quy voxel đã qua.`

- [ ] **Step 5: Commit Task A6**

```bash
git add src/main/java/com/voxelgame/entity/LivingEntity.java src/main/java/com/voxelgame/entity/mob/MobEntity.java src/main/java/com/voxelgame/entity/Player.java src/main/java/com/voxelgame/engine/AudioManager.java src/test/java/com/voxelgame/engine/CombatItemsChecks.java
git commit -m "feat(track-a): them knockback bat nay chuan Minecraft va sfx hit giua cac entity"
```
```
