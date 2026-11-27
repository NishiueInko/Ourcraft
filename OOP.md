# Ứng Dụng 4 Tính Chất Lập Trình Hướng Đối Tượng (OOP) Trong VoxelGame

Tài liệu này tổng hợp đầy đủ **4 tính chất cốt lõi của OOP** (Đóng gói, Kế thừa, Trừu tượng, Đa hình), các nguyên lý thiết kế (**LSP**, **Composition**) và **Mẫu thiết kế (Design Patterns)** được áp dụng trên 6 trụ cột miền nghiệp vụ (Domain) của dự án **VoxelGame**:

> **Phạm vi trình bày:** `Block`, `Entity`, `Mob`, `Item`, `Biome`, `Weather` (thuộc tầng `com.voxelgame.core` và `com.voxelgame.world.*`).

---

## 1. Tính Đóng Gói (Encapsulation)

Tính đóng gói giúp ẩn giấu trạng thái nội bộ của đối tượng, chỉ cho phép truy xuất hoặc biến đổi qua các phương thức được kiểm soát chặt chẽ, tránh phá vỡ bất biến (invariant) của dữ liệu.

| Trụ cột | Cách áp dụng cụ thể trong mã nguồn | Lợi ích thực tế |
| --- | --- | --- |
| **Block** | Toàn bộ thông số bất biến trong `Block` (`id`, `name`, `hardness`, `solid`, `liquid`, `opaque`, `iconColor`) đều khai báo `private final`. Hàm `getIconColor()` trả về **bản sao phòng thủ (defensive copy)** `iconColor.clone()`. Mảng đăng ký `BY_ID[256]` được ẩn `private static final`, chỉ tra cứu qua `Block.fromId(byte)`. | Ngăn mã bên ngoài sửa đè mã ID hoặc màu biểu tượng của các singleton `Block` dùng chung toàn thế giới. |
| **Entity & Player** | Vị trí `position` và vận tốc `velocity` trong `Entity` là `protected final Vector3`; các getter công khai `getPosition()`, `getVelocity()` trả về bản sao mới (`new Vector3(...)`) hoặc ghi vào bộ đệm ngoài (`copyPosition(out)`). Trong `LivingEntity`, máu `health`, giáp `armor`, hiệu ứng trạng thái `effects` đều là `private`, chỉ thay đổi qua `damage(...)`, `heal(...)`, `applyEffect(...)` có kiểm tra biên `[0..maxHealth]`. | Chống thay đổi trực tiếp tọa độ hay máu vượt ngưỡng hợp lệ; đảm bảo `fallDistance` và `invulnerableTimer` luôn nhất quán. |
| **PlayerInventory & Hotbar** | `PlayerInventory` đóng gói mảng 36 ô (`items[36]`, `counts[36]`) ở mức `private`; mọi thao tác thêm/trừ/gộp stack đi qua `addItem(...)`, `consumeSlot(...)`, `setSlot(...)` có giới hạn `maxStackSize`. `Hotbar` ủy quyền đồng bộ 9 ô đầu (`0..8`) về `PlayerInventory`. | Không bao giờ xảy ra số lượng âm, vượt `maxStackSize`, hay lệch dữ liệu giữa Hotbar và Balo. |
| **Mob** | Các hook kế thừa nội bộ trong `MobEntity`, `HostileMob`, `PassiveMob`, `NeutralMob`, `MountableMob`, `EquineMob`, `RangedMob` được giới hạn bằng `protected` thay vì `public`. Mục tiêu AI `MobGoalSelector` đóng gói danh sách goal và khóa từng kênh (`MOVE`, `LOOK`, `JUMP`). | Chỉ lớp con hợp lệ mới được tinh chỉnh hành vi nội bộ của mob; các lớp ngoài gói không thể gọi bừa bãi. |
| **Item** | `Item` đóng gói `id`, `displayName`, `maxStackSize`, `atlasTileIndex` là `private final`. `FoodItem` đóng gói trọn vẹn `FoodSpec` (`nutrition`, `saturation`, hiệu ứng phụ & xác suất); `PotionItem` đóng gói `StatusEffectType`, thời lượng và vật phẩm hoàn trả (`GLASS_BOTTLE` / `BUCKET`). | Mỗi vật phẩm là một nguồn chân lý bất biến duy nhất (Single Source of Truth); `FoodRegistry` đồng bộ tự động từ chính các `FoodItem`. |
| **Biome & Weather** | `Biome` đóng gói tên hiển thị, mã màu cỏ/lá, khí hậu và bảng sinh mob theo từng lớp con Flyweight bất biến. `WeatherManager` đóng gói bộ đếm `remaining`, `strength`, `precipitation` ở mức `private` và chỉ cập nhật qua `update(...)` / `toggle()`. | Tránh trạng thái thời tiết không hợp lệ (ví dụ: mưa ở sa mạc) và giữ nguyên ngưỡng chu kỳ chuẩn Minecraft (`600..9000s` quang, `600..1200s` giáng thủy). |

---

## 2. Tính Kế Thừa (Inheritance)

Dự án xây dựng các cây kế thừa nhiều tầng sâu, rõ ràng theo đúng quan hệ **"is-a"** để tái sử dụng mã nguồn chung và tổ chức phân loại tự nhiên.

### 2.1. Hệ phân cấp `Block` (33 loại khối, 3 tầng)
```text
abstract Block (Flyweight registry 0..32)
├── AirBlock, CactusBlock, SnowLayerBlock, IceBlock
├── abstract SolidBlock (solid=true, opaque=true, tile chuẩn 6 mặt)
│   ├── DirtBlock, StoneBlock, BedrockBlock, SandstoneBlock, SnowBlock, WoolBlock, GrassBlock, DecorativeChestBlock
│   ├── abstract GravityBlock (isGravityBlock() = true)
│   │   ├── SandBlock
│   │   └── GravelBlock
│   └── abstract LogBlock (gom logic 2 tile top/bottom & side)
│       ├── OakLogBlock
│       └── SpruceLogBlock
├── abstract LiquidBlock (solid=false, liquid=true, hạ mặt thoáng 0.88f)
│   ├── WaterBlock
│   └── LavaBlock
├── abstract LeavesBlock (RenderLayer.CUTOUT, cờ PERSISTENT_LEAF_STATE)
│   ├── OakLeavesBlock
│   └── SpruceLeavesBlock
└── abstract PlantBlock (hình học chữ X khoét alpha, kiểm tra nền đỡ)
    ├── ShortGrassBlock, TallGrassBlock, FlowerBlock, DeadBushBlock, SugarCaneBlock
```

### 2.2. Hệ phân cấp `Entity`, `LivingEntity`, `MobEntity` & `ProjectileEntity` (lên tới 7 tầng)
```text
abstract Entity (implements Tickable)
├── ItemEntity (vật phẩm rơi, lực hút nam châm)
├── FallingBlockEntity (cát/sỏi rơi theo trọng lực)
├── abstract ProjectileEntity (đạn đạo chịu trọng lực & quét va chạm)
│   ├── ArrowEntity (Arrow, Slowness, Weakness)
│   ├── ThrownPotionEntity (Splash Potion của Witch)
│   ├── ThrownEnderPearlEntity (dịch chuyển tức thời)
│   └── ThrownEggEntity (nở gà con 1/8)
└── abstract LivingEntity (HP, giáp, sát thương, hiệu ứng trạng thái, knockback)
    ├── Player (sở hữu PlayerInventory & Hotbar theo Composition)
    └── abstract MobEntity (điều hướng lưới, AI MobGoalSelector, cháy nắng)
        ├── abstract HostileMob (quái thù địch chủ động)
        │   ├── Zombie
        │   │   ├── ZombieVillager
        │   │   └── Husk (chịu nắng, gây Hunger, chuyển thành Zombie khi ngập nước)
        │   ├── Creeper (phồng & nổ phá địa hình)
        │   └── abstract RangedMob (tấn công tầm xa theo nhịp)
        │       ├── Skeleton
        │       │   ├── Stray (bắn tên Slowness)
        │       │   └── Parched (chịu nắng, bắn tên Weakness)
        │       └── Witch (ném & tự uống thuốc)
        ├── abstract PassiveMob (hiền lành, hoảng loạn khi bị đánh)
        │   └── Cow, Sheep, Pig, Chicken, Rabbit
        ├── abstract NeutralMob (trung lập, nổi giận khi bị kích động)
        │   └── Spider, Enderman, Goat, PolarBear, Wolf, SnowFox
        └── abstract MountableMob (thú cưỡi có ghế, implements Interactable)
            ├── abstract EquineMob (họ ngựa dùng chung phân bố HP & nhảy/cưỡi)
            │   ├── Horse
            │   └── Donkey
            ├── Camel (2 ghế, Dash)
            └── CamelHusk (kỵ sĩ sa mạc Husk + Parched)
```

> **Ví dụ chuỗi kế thừa 7 tầng tiêu biểu:**  
> `Entity` → `LivingEntity` → `MobEntity` → `HostileMob` → `RangedMob` → `Skeleton` → `Stray`.

### 2.3. Hệ phân cấp `Item` (56 vật phẩm độc lập + 32 BlockItem, 3 tầng)
```text
abstract Item (Flyweight registry)
├── BlockItem (bao bọc 32 Block đặt được, gồm cả 6 màu len)
├── SimpleItem (nguyên liệu, loot, mũi tên, đĩa nhạc)
├── abstract ConsumableItem (Template Method giữ chuột phải 1.6s)
│   ├── FoodItem (19 loại thức ăn chuẩn Minecraft)
│   └── PotionItem (4 thuốc Witch + Milk Bucket)
├── BowItem (kéo dây 1.0s, zoom FOV, tìm & bắn ArrowEntity)
├── SpearItem (đâm Jab tầm 4.2 + tụ lực Charge)
├── ThrowableItem (Ender Pearl, Egg dùng ProjectileFactory)
└── abstract ToolItem (công cụ tương tác đặc biệt)
    ├── ShearsItem (đào lá/len nhanh 5×)
    ├── FlintAndSteelItem (đốt cháy mọi MobEntity 8s)
    ├── BucketItem (lấy sữa từ Cow/Goat)
    └── GoatHornItem (thổi tù và với cooldown 7.0s)
```

### 2.4. Hệ phân cấp `Biome` & `Weather`
- **`abstract Biome`** → `PlainsBiome`, `ForestBiome`, `FlowerForestBiome`, `DesertBiome`, `SnowyPlainsBiome`.
- **`abstract Weather`** → `ClearWeather`, `RainWeather`, `SnowWeather`.

---

## 3. Tính Trừu Tượng (Abstraction)

Tính trừu tượng tách biệt **hợp đồng hành vi ("làm gì")** khỏi **chi tiết cài đặt ("làm như thế nào")** thông qua **lớp trừu tượng (`abstract class`)** và **giao diện (`interface`)**.

### 3.1. Lớp trừu tượng (`abstract class`) — Quan hệ "is-a"
- **`Block`**, **`SolidBlock`**, **`GravityBlock`**, **`LiquidBlock`**, **`LogBlock`**, **`LeavesBlock`**, **`PlantBlock`**: Không thể khởi tạo một "khối vô định"; lớp cha định nghĩa khung `onNeighborChange`, `onBreak`, `getTileIndex` để lớp con điền chi tiết.
- **`Entity`**, **`LivingEntity`**, **`MobEntity`**, **`HostileMob`**, **`PassiveMob`**, **`NeutralMob`**, **`MountableMob`**, **`EquineMob`**, **`RangedMob`**, **`ProjectileEntity`**: `MobEntity` là `abstract` nên không ai có thể `new MobEntity(...)` tạo một con mob không rõ loài. `RangedMob` khai báo phương thức trừu tượng tấn công tầm xa để `Skeleton`/`Stray`/`Parched`/`Witch` cài đặt riêng.
- **`Item`**, **`ConsumableItem`**, **`ToolItem`**: `ConsumableItem` trừu tượng hóa quy trình giữ chuột phải `1.6s` (Template Method), để `FoodItem` và `PotionItem` tự định nghĩa `onConsumeComplete(...)`.
- **`Biome`**: Khai báo các phương thức trừu tượng `getType()`, `getDisplayName()`, `getGrassColorRgb()`, `getFoliageColorRgb()`, `hasRain()`, `hasSnow()`, `isDry()`, `resolvePrecipitationWeather()`, `getSpawnTable(...)`.
- **`Weather`**: Khai báo các phương thức trừu tượng `getLabel()`, `targetSkyStrength(float)`, `targetRainGain(float)`, `extinguishesFire()`, `accumulatesSnow()`.

### 3.2. Giao diện (`interface`) — Quan hệ "can-do"
- **`Tickable`** (`com.voxelgame.core`): Hợp đồng cập nhật theo thời gian `tick(World, float)`, được triển khai bởi `Entity`, `LeafDecaySystem` và `LiquidFlowSimulator`.
- **`Interactable`** (`com.voxelgame.core`): Hợp đồng tương tác chuột phải `onInteract(World, Player, Item)`. Chỉ những loài có khả năng tương tác đặc thù mới triển khai (`Sheep` cắt len, `Cow`/`Goat` vắt sữa, `Wolf` thuần hóa & ngồi/đứng, `MountableMob` lên yên), trong khi `Zombie` hay `Creeper` **không** triển khai. Điều này minh họa rất rõ sự khác biệt giữa **kế thừa lớp trừu tượng ("is-a")** và **triển khai giao diện ("can-do")**.
- **`MobGoal`** (`com.voxelgame.world.entity.mob.ai`): Hợp đồng trừu tượng hóa từng mục tiêu AI (`canStart`, `shouldContinue`, `start`, `stop`, `tick`, `controls`) độc lập với từng loài mob.

---

## 4. Tính Đa Hình (Polymorphism)

Tính đa hình cho phép mã nguồn gọi cùng một phương thức trên kiểu tham chiếu cha/giao diện, nhưng hành vi thực thi tự động thay đổi theo đối tượng thực tế lúc chạy (Runtime Dynamic Dispatch) — giúp **xóa bỏ các chuỗi `if/else` và `switch` rườm rà**.

| Tình huống trong game | Lời gọi đa hình | Hành vi thực tế theo từng lớp con |
| --- | --- | --- |
| **Cập nhật mọi thực thể trong thế giới** | `World` duyệt `List<Entity>` và gọi `entity.update(world, dt)` | `Player` đọc điều khiển & va chạm; `ItemEntity` xoay & hút nam châm; `FallingBlockEntity` rơi & đặt block; `ArrowEntity` bay parabol & gây hiệu ứng; từng `MobEntity` chạy AI riêng. |
| **Sử dụng vật phẩm trên tay (`ItemUseHandler`)** | `item.getUseAction()`, `item.onInstantUse(...)`, `item.onConsumeComplete(...)`, `item.getMeleeReach()`, `item.getMiningSpeedMultiplier(block)` | `BowItem` kéo dây & bắn tên; `SpearItem` đâm tầm 4.2 & lao Charge; `FoodItem` hồi đói/saturation; `PotionItem` cấp hiệu ứng & trả chai/xô; `ThrowableItem` ném `EnderPearl`/`Egg`; `GoatHornItem` thổi tù và; `ShearsItem` tăng tốc đào lá & len lên `5×`. |
| **Tương tác chuột phải vào Mob (2 lượt đa hình)** | Lượt 1: `heldItem.onInteractMob(world, player, target)`<br>Lượt 2: `interactable.onInteract(world, player, heldItem)` | Lượt 1 cho phép `FlintAndSteelItem` đốt cháy **mọi** `MobEntity` (kể cả `Zombie`, `CamelHusk`). Nếu không phải công cụ phổ quát, Lượt 2 gọi đa hình sang `Sheep` (cắt len), `Cow`/`Goat` (vắt sữa), `Wolf` (thuần hóa bằng xương), `MountableMob` (lên yên). |
| **Chuyển trạng thái thời tiết theo Biome** | `weather = biome.resolvePrecipitationWeather()` và gọi `weather.targetSkyStrength(s)`, `weather.targetRainGain(s)`, `weather.extinguishesFire()`, `weather.accumulatesSnow()` | Cùng một cơn bão toàn cục (`strength > 0`): `PlainsBiome`/`ForestBiome`/`FlowerForestBiome` trả về `RainWeather` (dập lửa, có tiếng mưa); `SnowyPlainsBiome` trả về `SnowWeather` (tích tuyết, không tiếng mưa); `DesertBiome` trả về `ClearWeather` (trời quang khô ráo). |
| **Phản ứng khi khối lân cận thay đổi** | `Block.fromId(id).onNeighborChange(world, x, y, z, state)` | `SandBlock`/`GravelBlock` hóa thành `FallingBlockEntity`; `CactusBlock`/`SugarCaneBlock`/`FlowerBlock` tự gãy rơi item khi mất nền đỡ; `IceBlock`/`SnowLayerBlock` tan chảy khi gần Lava. |
| **Chọn màu cỏ/lá và bảng sinh mob theo Biome** | `Biome.of(type).getGrassColorRgb()`, `Biome.of(type).getSpawnTable(category, zone)` | Mỗi lớp con `Biome` tự cung cấp bảng màu cỏ/lá và quần thể mob riêng (ví dụ `SnowyPlainsBiome` phân nhánh theo 3 phân vùng `SnowSubZone`) thay cho câu lệnh `switch (biomeType)`. |

---

## 5. Điểm Nhấn Thiết Kế OOP Khi Thuyết Trình (Khắc Phục Lỗi LSP & Design Patterns)

### 5.1. Hai ví dụ sửa lỗi vi phạm Nguyên lý Thay thế Liskov (LSP)
1. **Tách `Block` ra khỏi `Entity`:**
   - *Trước tái cấu trúc:* `Block extends Entity` chỉ để dùng chung vài trường kích thước, buộc `Block` phải cài đặt 2 hàm rỗng giả `update()` và `render()` vì một khối tĩnh trong lưới voxel không có vận tốc hay vòng đời thực thể.
   - *Sau tái cấu trúc:* `Block` là một cây kế thừa độc lập hoàn toàn (`!Entity.class.isAssignableFrom(Block.class)`), chỉ lưu dữ liệu Flyweight tĩnh của loại khối.
2. **Tách `Horse` và `Donkey` ngang hàng dưới `abstract EquineMob`:**
   - *Tránh bẫy LSP:* Không cho `Donkey extends Horse` vì `Horse` có biến thể màu lông/hoa văn và tốc độ/độ nhảy ngẫu nhiên, còn `Donkey` có thông số cố định. Cả hai cùng kế thừa `abstract EquineMob extends MountableMob` (tương tự `AbstractHorse` của Minecraft Java Edition) để dùng chung cơ chế máu 15–30 HP, thuần hóa và cưỡi mà không vi phạm LSP.

### 5.2. Các mẫu thiết kế (Design Patterns) áp dụng trên Domain
| Design Pattern | Vị trí áp dụng | Mục đích |
| --- | --- | --- |
| **Flyweight** | `Block` (33 singleton), `Item` (56+32 singleton), `Biome` (5 singleton), `Weather` (3 singleton) | Chia sẻ 1 thực thể bất biến duy nhất cho hàng triệu ô voxel/vật phẩm, đảm bảo **Zero-GC** ở 60 FPS. |
| **Strategy** | `MobGoal` + `MobGoalSelector`, `Biome` | Thay đổi linh hoạt hành vi AI từng loài mob và quy tắc sinh học/khí hậu của từng biome lúc chạy. |
| **State** | `Weather` (`ClearWeather` / `RainWeather` / `SnowWeather`) trong `WeatherManager` | Chuyển đổi hành vi thời tiết cục bộ mượt mà không cần nhánh `if/else` rải rác. |
| **Template Method** | `ConsumableItem` (`FoodItem`/`PotionItem`), `RangedMob`, `Block.onNeighborChange` | Định nghĩa khung thuật toán chung ở lớp cha, để lớp con điền các bước đặc thù. |
| **Factory** | `MobType.Factory`, `ThrowableItem.ProjectileFactory` | Tách logic khởi tạo 24 cấu hình loài mob và các loại đạn ném khỏi nơi gọi. |
| **Composition ("has-a")** | `Player` sở hữu `PlayerInventory` & `Hotbar`; `MobEntity` sở hữu `MobGoalSelector` | Ưu tiên hợp thành thay vì kế thừa gượng ép giữa các thành phần độc lập. |
