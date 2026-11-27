# Kiến Trúc Tổng Quát & Sơ Đồ Lớp Dự Án VoxelGame

Tài liệu này mô tả kiến trúc phân gói chuẩn **Minecraft Java Edition** của dự án **VoxelGame** (`168` file Java chính, `28` bộ kiểm thử CPU) cùng các sơ đồ lớp Mermaid chi tiết cho từng phân hệ.

---

## 1. Sơ Đồ Kiến Trúc Phân Gói Tổng Quát

```mermaid
flowchart TD
    Main["com.voxelgame (Main, VoxelGameApp)"]

    subgraph Core["com.voxelgame.core (Hợp đồng OOP)"]
        Tickable["Tickable"]
        Renderable["Renderable"]
        Interactable["Interactable"]
    end

    subgraph WorldLayer["com.voxelgame.world.* (Tầng Logic Thế Giới)"]
        Level["world.level (World, Chunk, ChunkLighting, WorldTime, LeafDecaySystem, LiquidFlowSimulator)"]
        BlockPkg["world.level.block (32 file: abstract Block + 6 lớp trung gian + 25 khối)"]
        BiomePkg["world.level.biome (9 file: abstract Biome + 5 Biome + BiomeType + BiomeTint)"]
        WeatherPkg["world.level.weather (5 file: abstract Weather + 3 Weather + WeatherManager)"]
        GenPkg["world.level.gen (TerrainNoise, TerrainGenerator, DesertPyramid)"]
        ItemPkg["world.item (16 file: abstract Item + 13 lớp con + FoodRegistry + ItemUseHandler)"]
        EntityPkg["world.entity (Entity, LivingEntity, ItemEntity, FallingBlockEntity, DamageSource)"]
        PlayerPkg["world.entity.player (Player, PlayerInventory, Hotbar)"]
        MobPkg["world.entity.mob (MobEntity, Hostile/Passive/Neutral/Mountable/Equine/Ranged + Spawner)"]
        MobAiPkg["world.entity.mob.ai (MobGoal, MobGoalSelector, MobGoals, SimpleGridNavigator)"]
        MobSpecies["world.entity.mob.monster (11) & animal (12)"]
        ProjPkg["world.entity.projectile (7 file: ProjectileEntity + 4 đạn đạo)"]
        EffectPhys["world.effect (2) & world.physics (Raycast)"]
    end

    subgraph ClientLayer["com.voxelgame.client.* (Tầng Hiển Thị, GUI, Input & Audio)"]
        InputPkg["client.input (GameInputController)"]
        GuiPkg["client.gui (Screen, PauseScreen, InventoryScreen, HudRenderer, BitmapFont, UIRenderer, DebugOverlay, GuiTextureAtlas)"]
        RenderPkg["client.render (Camera, ShaderProgram, Texture, ItemTextureAtlas, Sky/Weather/FirstPerson/BlockOutline/Particle/Liquid)"]
        RenderEntityPkg["client.render.entity (MobRenderer, MobTextureManager, MobVisual, MobVisualState)"]
        ModelPkg["client.model (ModelPart, MobModel, MobModels)"]
        AudioPkg["client.audio (AudioManager, MobSoundBank, MobSoundEvent)"]
    end

    Main --> WorldLayer
    Main --> ClientLayer
    WorldLayer --> Core
    ClientLayer --> WorldLayer
    Level --> BlockPkg
    Level --> BiomePkg
    Level --> WeatherPkg
    Level --> GenPkg
    Level --> EntityPkg
    EntityPkg --> PlayerPkg
    EntityPkg --> MobPkg
    EntityPkg --> ProjPkg
    MobPkg --> MobAiPkg
    MobPkg --> MobSpecies
    PlayerPkg --> ItemPkg
    GuiPkg --> InputPkg
    RenderPkg --> RenderEntityPkg
    RenderEntityPkg --> ModelPkg
```

---

## 2. Sơ Đồ Lớp Hệ Phân Cấp `Block` (`world.level.block`)

```mermaid
classDiagram
    class Block {
        <<abstract>>
        -byte id
        -String name
        -float hardness
        -boolean solid
        -boolean liquid
        -boolean opaque
        +fromId(byte id)$ Block
        +getTileIndex(Face face) int
        +onNeighborChange(World, int, int, int, int) void
        +onBreak(World, int, int, int, int) void
    }

    class SolidBlock {
        <<abstract>>
    }
    class GravityBlock {
        <<abstract>>
        +isGravityBlock() boolean
    }
    class LiquidBlock {
        <<abstract>>
        +getTopHeight(World, int, int, int, int) float
    }
    class LogBlock {
        <<abstract>>
    }
    class LeavesBlock {
        <<abstract>>
    }
    class PlantBlock {
        <<abstract>>
    }

    Block <|-- AirBlock
    Block <|-- CactusBlock
    Block <|-- SnowLayerBlock
    Block <|-- IceBlock
    Block <|-- SolidBlock
    Block <|-- LiquidBlock
    Block <|-- LeavesBlock
    Block <|-- PlantBlock

    SolidBlock <|-- DirtBlock
    SolidBlock <|-- GrassBlock
    SolidBlock <|-- StoneBlock
    SolidBlock <|-- BedrockBlock
    SolidBlock <|-- SandstoneBlock
    SolidBlock <|-- SnowBlock
    SolidBlock <|-- DecorativeChestBlock
    SolidBlock <|-- WoolBlock
    SolidBlock <|-- GravityBlock
    SolidBlock <|-- LogBlock

    GravityBlock <|-- SandBlock
    GravityBlock <|-- GravelBlock

    LogBlock <|-- OakLogBlock
    LogBlock <|-- SpruceLogBlock

    LiquidBlock <|-- WaterBlock
    LiquidBlock <|-- LavaBlock

    LeavesBlock <|-- OakLeavesBlock
    LeavesBlock <|-- SpruceLeavesBlock

    PlantBlock <|-- ShortGrassBlock
    PlantBlock <|-- TallGrassBlock
    PlantBlock <|-- FlowerBlock
    PlantBlock <|-- DeadBushBlock
    PlantBlock <|-- SugarCaneBlock
```

---

## 3. Sơ Đồ Lớp Hệ Phân Cấp `Entity`, `Mob` & `Projectile` (`world.entity.*`)

```mermaid
classDiagram
    class Tickable {
        <<interface>>
        +tick(World, float) void
    }
    class Renderable {
        <<interface>>
        +render(ShaderProgram) void
    }
    class Interactable {
        <<interface>>
        +onInteract(World, Player, Item) boolean
    }

    class Entity {
        <<abstract>>
        #Vector3 position
        #Vector3 velocity
        +update(World, float)* void
        +render(ShaderProgram)* void
        +tick(World, float) void
    }

    class LivingEntity {
        <<abstract>>
        -float health
        -float maxHealth
        +damage(World, DamageSource, float) boolean
        +heal(float) void
        +applyEffect(StatusEffectType, float, int) void
    }

    class Player {
        -PlayerInventory inventory
        -Hotbar hotbar
        +getInventory() PlayerInventory
        +getHotbar() Hotbar
    }

    class PlayerInventory {
        -Item[] items
        -int[] counts
    }

    class Hotbar {
        -PlayerInventory inventory
        -int selectedSlot
    }

    class MobEntity {
        <<abstract>>
        #MobGoalSelector goals
        +onPlayerInteract(World, Player) boolean
    }

    class HostileMob {
        <<abstract>>
    }
    class RangedMob {
        <<abstract>>
    }
    class PassiveMob {
        <<abstract>>
    }
    class NeutralMob {
        <<abstract>>
    }
    class MountableMob {
        <<abstract>>
        +onInteract(World, Player, Item) boolean
    }
    class EquineMob {
        <<abstract>>
    }
    class ProjectileEntity {
        <<abstract>>
    }

    Tickable <|.. Entity
    Renderable <|.. Entity
    Entity <|-- ItemEntity
    Entity <|-- FallingBlockEntity
    Entity <|-- ProjectileEntity
    Entity <|-- LivingEntity

    ProjectileEntity <|-- ArrowEntity
    ProjectileEntity <|-- ThrownPotionEntity
    ProjectileEntity <|-- ThrownEnderPearlEntity
    ProjectileEntity <|-- ThrownEggEntity

    LivingEntity <|-- Player
    LivingEntity <|-- MobEntity

    Player *-- PlayerInventory : Composition
    Player *-- Hotbar : Composition
    Hotbar --> PlayerInventory : delegates

    MobEntity <|-- HostileMob
    MobEntity <|-- PassiveMob
    MobEntity <|-- NeutralMob
    MobEntity <|-- MountableMob

    HostileMob <|-- Zombie
    Zombie <|-- ZombieVillager
    Zombie <|-- Husk
    HostileMob <|-- Creeper
    HostileMob <|-- RangedMob
    RangedMob <|-- Skeleton
    Skeleton <|-- Stray
    Skeleton <|-- Parched
    RangedMob <|-- Witch

    PassiveMob <|-- Cow
    PassiveMob <|-- Sheep
    PassiveMob <|-- Pig
    PassiveMob <|-- Chicken
    PassiveMob <|-- Rabbit

    NeutralMob <|-- Spider
    NeutralMob <|-- Enderman
    NeutralMob <|-- Goat
    NeutralMob <|-- PolarBear
    NeutralMob <|-- Wolf
    NeutralMob <|-- SnowFox

    MountableMob <|-- EquineMob
    EquineMob <|-- Horse
    EquineMob <|-- Donkey
    MountableMob <|-- Camel
    MountableMob <|-- CamelHusk

    Interactable <|.. MountableMob
    Interactable <|.. Sheep
    Interactable <|.. Cow
    Interactable <|.. Goat
    Interactable <|.. Wolf
```

---

## 4. Sơ Đồ Lớp Hệ Phân Cấp `Item` Đa Hình (`world.item`)

```mermaid
classDiagram
    class Item {
        <<abstract>>
        -String id
        -String displayName
        -int maxStackSize
        +getUseAction() UseAction
        +getUseDurationSeconds() float
        +getMeleeReach() float
        +getMeleeDamage() float
        +getMiningSpeedMultiplier(Block) float
        +onInteractMob(World, Player, MobEntity) boolean
        +onInstantUse(World, Player, Camera, ItemUseHandler) boolean
        +onConsumeComplete(World, Player) void
    }

    class ConsumableItem {
        <<abstract>>
        +getUseDurationSeconds() float
        +onConsumeComplete(World, Player)* void
    }

    class ToolItem {
        <<abstract>>
    }

    class FoodItem {
        -FoodSpec spec
        +onConsumeComplete(World, Player) void
    }

    class PotionItem {
        -StatusEffectType effect
        -Item returnContainer
        +onConsumeComplete(World, Player) void
    }

    class BowItem {
        +getUseAction() UseAction
    }

    class SpearItem {
        +getMeleeReach() float
        +getMeleeDamage() float
    }

    class ThrowableItem {
        -ProjectileFactory factory
        +onInstantUse(World, Player, Camera, ItemUseHandler) boolean
    }

    class ShearsItem {
        +getMiningSpeedMultiplier(Block) float
    }

    class FlintAndSteelItem {
        +onInteractMob(World, Player, MobEntity) boolean
    }

    class BucketItem
    class GoatHornItem {
        +onInstantUse(World, Player, Camera, ItemUseHandler) boolean
    }

    class ItemUseHandler {
        +onPrimaryMelee(World, Player, Camera) boolean
        +onSecondaryPress(World, Player, Camera) boolean
        +update(float, World, Player, Camera) void
    }

    Item <|-- BlockItem
    Item <|-- SimpleItem
    Item <|-- ConsumableItem
    Item <|-- BowItem
    Item <|-- SpearItem
    Item <|-- ThrowableItem
    Item <|-- ToolItem

    ConsumableItem <|-- FoodItem
    ConsumableItem <|-- PotionItem

    ToolItem <|-- ShearsItem
    ToolItem <|-- FlintAndSteelItem
    ToolItem <|-- BucketItem
    ToolItem <|-- GoatHornItem

    ItemUseHandler --> Item : polymorphic dispatch
```

---

## 5. Sơ Đồ Lớp `Biome` & `Weather` (State / Strategy Pattern)

```mermaid
classDiagram
    class Biome {
        <<abstract>>
        +of(BiomeType type)$ Biome
        +getType()* BiomeType
        +getDisplayName()* String
        +getGrassColorRgb()* int
        +getFoliageColorRgb()* int
        +hasRain()* boolean
        +hasSnow()* boolean
        +isDry()* boolean
        +resolvePrecipitationWeather()* Weather
        +getSpawnTable(MobCategory, SnowSubZone)* MobSpawnTable
    }

    class Weather {
        <<abstract>>
        +getLabel()* String
        +targetSkyStrength(float)* float
        +targetRainGain(float)* float
        +extinguishesFire()* boolean
        +accumulatesSnow()* boolean
    }

    class WeatherManager {
        -Weather currentWeather
        -BiomeType biome
        -float strength
        +currentWeather() Weather
        +update(float, World, Vector3, Player) void
        +toggle() void
    }

    Biome <|-- PlainsBiome
    Biome <|-- ForestBiome
    Biome <|-- FlowerForestBiome
    Biome <|-- DesertBiome
    Biome <|-- SnowyPlainsBiome

    Weather <|-- ClearWeather
    Weather <|-- RainWeather
    Weather <|-- SnowWeather

    WeatherManager --> Weather : delegates state
    WeatherManager --> Biome : resolves weather
    Biome ..> Weather : returns singleton
```

---

## 6. Sơ Đồ Tổng Quan Tầng Client, GUI & Input (`client.gui`, `client.input`)

```mermaid
classDiagram
    class Screen {
        <<abstract>>
        #boolean open
        +isOpen() boolean
        +open() void
        +close() void
        +pausesWorld()* boolean
        +dispose() void
    }

    class PauseScreen {
        +pausesWorld() boolean
        +isInsideResumeButton(...) boolean
        +getSliderVolumeAtCursor(...) float
        +render(...) void
    }

    class InventoryScreen {
        +pausesWorld() boolean
        +getSlotAt(...) int
        +handleClick(...) boolean
        +handleSlotInteraction(...) boolean
        +render(...) void
    }

    class HudRenderer {
        +renderTargetBlockOverlay3D(...) void
        +renderScreenEffects(...) void
        +dispose() void
    }

    class BitmapFont {
        <<utility>>
        +hasGlyph(char)$ boolean
        +measureWidth(CharSequence, float)$ float
        +appendTextVertices(...)$ int
    }

    class UIRenderer {
        <<Facade>>
        -HudRenderer hudRenderer
        -PauseScreen pauseScreen
        +render2DHUD(...) void
        +dispose() void
    }

    class GameInputController {
        -Host host
        +processMouseLook() void
        +updateMiningProgress(HitResult, float) float
        +keyDown(int) boolean
        +touchDown(int, int, int, int) boolean
    }

    class VoxelGameApp {
        <<Orchestrator>>
        -Player player
        -boolean isPaused
        -UIRenderer uiRenderer
        -GameInputController inputController
        +create() void
        +render() void
        +dispose() void
    }

    Screen <|-- PauseScreen
    Screen <|-- InventoryScreen
    UIRenderer *-- HudRenderer
    UIRenderer *-- PauseScreen
    HudRenderer ..> BitmapFont
    PauseScreen ..> BitmapFont
    InventoryScreen ..> BitmapFont
    VoxelGameApp *-- UIRenderer
    VoxelGameApp *-- InventoryScreen
    VoxelGameApp *-- GameInputController
```
