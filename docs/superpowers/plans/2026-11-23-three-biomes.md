# Kế hoạch triển khai ba biome cho VoxelGame

> Khi thực thi, dùng `executing-plans` để chạy lần lượt hoặc `subagent-driven-development` nếu người dùng chọn giao việc cho các agent. Đánh dấu các bước bằng ô kiểm.

**Mục tiêu** — Đưa Plains, Desert và Snowy Plains vào VoxelGame cùng các block, tương tác và hiệu ứng đã duyệt trong [đặc tả](../specs/2026-11-23-three-biomes-design.md).

**Kiến trúc** — Giữ lưu trữ chunk thưa và ID cũ. Dùng chung trạng thái block và hình học giữa gameplay và kết xuất. Xử lý thay đổi lân cận bằng hàng đợi, vẽ mesh theo hai pha và sinh địa hình tất định từ seed hiện tại.

**Công nghệ** — Java 17, Gradle Wrapper, libGDX 1.13.1, LWJGL3, OpenGL 3.3 và Java Flight Recorder.

## Chuẩn bị và phạm vi tệp

| Khu vực | Tệp và trách nhiệm |
| --- | --- |
| Danh mục block | `src/main/java/com/voxelgame/entity/Block.java` và các lớp block cùng package. Định nghĩa ID, hình học, hộp chọn/va chạm, điều kiện tồn tại, tile và luật rơi. |
| Dữ liệu và chỉnh sửa | `src/main/java/com/voxelgame/engine/Chunk.java`, `World.java` và `BlockUpdateQueue.java` mới cùng package. Lưu ID/metadata, cập nhật lân cận và đánh dấu mesh. |
| Tương tác | `src/main/java/com/voxelgame/engine/Raycast.java`, `src/main/java/com/voxelgame/entity/Entity.java`, `Player.java`, `ItemEntity.java`, `FallingBlockEntity.java` mới cùng package và `src/main/java/com/voxelgame/VoxelGameApp.java`. |
| Đồ họa | `src/main/java/com/voxelgame/engine/Texture.java`, `ShaderProgram.java`, `Chunk.java`, `World.java`, `UIRenderer.java`, `src/main/java/com/voxelgame/entity/ItemEntity.java` và 13 PNG trong `src/main/resources/textures/`. |
| Sinh thế giới | Tạo `src/main/java/com/voxelgame/engine/BiomeType.java`, `TerrainNoise.java`, `TerrainGenerator.java`; nối vào `World.java`. |
| Kiểm chứng và tài liệu | `build.gradle`, `src/test/java/com/voxelgame/engine/VoxelRegressionChecks.java` và `README.md`. |

- [ ] Đọc `AGENTS.md`, Phần A của `README.md` và đặc tả đã duyệt. Dùng `git status --short` và `git diff -- src/main/java/com/voxelgame/engine/Texture.java README.md` để nhận diện công việc hiện có. Nếu thực thi trong worktree riêng, mang đúng baseline texture và đặc tả người dùng đang dùng sang đó. Chọn phạm vi commit theo từng đường dẫn và tránh đưa phần xóa đang stage của `VoxelGame_FullProgram.txt` vào commit.
- [ ] Tìm JDK 17, đặt `JAVA_HOME` và đưa `$env:JAVA_HOME\bin` lên đầu `PATH`. Kiểm tra `java -version` và `.\gradlew.bat --version` trước khi build. Khi lập kế hoạch, Java mặc định trên PATH là 26.0.2.1. Nếu bản Gradle Wrapper hiện tại cần JVM chạy Gradle khác Java 17, dùng JVM host phù hợp và yêu cầu Gradle Java toolchain 17 cho biên dịch, kiểm thử và chạy game. Xác nhận đường dẫn thực tế của JDK thay vì suy ra từ `sourceCompatibility`.
- [ ] Chạy `.\gradlew.bat compileJava build` làm baseline. Ghi thời gian từ lúc bắt đầu tạo world tới khung hình đầu tiên và frametime khi đứng yên, đi, bay. Giữ cùng JVM, cấu hình cửa sổ, V-Sync và máy khi đo lại.
- [ ] Thêm task `regressionChecks` trong `build.gradle` và lớp chạy kiểm thử CPU `VoxelRegressionChecks`. Dùng `World` trống kích thước bình thường qua factory package-private bỏ qua sinh địa hình và tạo mesh; giữ constructor công khai chạy chế độ game. Tách kiểm thử GPU sang chạy desktop. Tích hợp runner với `check` để `build` chạy hồi quy.

```groovy
tasks.register('regressionChecks', JavaExec) {
    description = 'Chạy kiểm thử hồi quy block, vật lý và địa hình không cần OpenGL.'
    dependsOn tasks.named('testClasses')
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'com.voxelgame.engine.VoxelRegressionChecks'
}
tasks.named('check') {
    dependsOn tasks.named('regressionChecks')
}
```

Với mỗi nhóm bên dưới, thêm ca kiểm thử trước, chạy `.\gradlew.bat regressionChecks` để quan sát lỗi đúng điều kiện, triển khai thay đổi rồi chạy lại. Runner ném `AssertionError` kèm tọa độ, giá trị mong muốn và giá trị thực tế để Gradle báo lỗi rõ ràng. Bước thêm tài nguyên và thử OpenGL có lệnh kiểm tra riêng.

## Mốc 1 — Block, trạng thái và tương tác

### 1. Danh mục block và metadata

- [ ] Trong `VoxelRegressionChecks.java`, duyệt 9 singleton cũ theo thứ tự ID, kiểm tra `Block.fromId(id)` trả đúng đối tượng. Tại `(15, 20, 15)` ghi tuyết 3 lớp, đổi thành Stone rồi ghi lại tuyết mặc định. Mẫu mong đợi là 3 lớp, metadata 0 và 1 lớp. Lặp ở `(16, 20, 15)` và kiểm tra xóa ô cuối đưa chunk về trạng thái rỗng.
- [ ] Mở `BY_ID` lên 256, dùng `Byte.toUnsignedInt`; giữ Air cho ID chưa đăng ký. Đăng ký ID 9–23 lần lượt cho Sand, Gravel, Sandstone, Cactus, Dead Bush, Short Grass, Tall Grass, Dandelion, Poppy, Cornflower, Oxeye Daisy, Sugar Cane, Snow Layer, Snow Block, Ice; ID 24–25 cho Spruce Log và Spruce Leaves. Tạo các lớp tương ứng trong `src/main/java/com/voxelgame/entity/`. Bốn hoa dùng chung `FlowerBlock.java` với tile, ID và tên từ constructor.
- [ ] Đặt `Block.RenderLayer` và `Block.GeometryType` trong `Block.java`. Cung cấp `getCollisionBounds(int state)` và `getSelectionBounds(int state)` với dữ liệu giới hạn cục bộ bất biến dùng chung. Tạo trước các hộp tuyết theo lớp, dùng giới hạn riêng cho cây và xương rồng. `getTileIndex(Face)` còn dùng được cho Hotbar; bổ sung overload nhận trạng thái và block phía trên cho mesher.
- [ ] Trong `Chunk.java`, giữ `blocks`, thêm `byte[] metadata` cấp phát khi ghi giá trị khác 0. Thống nhất 0 là trạng thái mặc định của từng loại: tuyết một lớp, cỏ cao nửa dưới; 1–7 mã hóa tuyết 2–8 lớp; 1 mã hóa nửa trên cỏ cao. `World.getBlockState(int x, int y, int z)` đọc metadata dưới dạng không dấu. `World.setBlock(x, y, z, id, state)` bổ sung đường ghi trạng thái; overload hiện có dùng mặc định 0. Khi thay block xóa trạng thái cũ, kiểm tra dirty cả khi chỉ đổi metadata và giữ quy tắc giải phóng chunk rỗng.

### 2. Hàng đợi, luật cây, tuyết và dung nham

- [ ] Tạo fixture bắc qua biên chunk `x=15/16` và biên đứng `y=15/16`. Kiểm tra cột Cactus gãy khi Stone hoặc Lava được đặt kề, Dead Bush mất nền, tháo một nửa Tall Grass làm mất cả hai và mất nước tại cùng cao độ nền làm toàn bộ cột mía biến mất. Kiểm tra chính xác số item cactus, hoa và mía.
- [ ] Tạo `BlockUpdateQueue.java` với vòng đệm FIFO tọa độ đóng gói và bảng tra đang chờ dùng kiểu nguyên thủy. Có thể đóng gói tọa độ bằng `(x << 19) | (z << 8) | y` sau kiểm tra biên; giải mã với mask `0x7ff` cho X/Z, `0xff` cho Y. Bỏ cờ đang chờ khi lấy ô ra để thay đổi kế tiếp có thể đưa lại ô đó vào hàng đợi. Khởi tạo bộ đệm một lần và tăng dung lượng khi lô cập nhật gameplay vượt sức chứa.
- [ ] Trong `World.java`, tách đường ghi ID/metadata khỏi bước drain. Mỗi lần ghi có thay đổi đánh dấu chunk và biên, đưa ô ghi và sáu ô kề vào FIFO. Ghi cả hai ô Tall Grass trước khi drain. Tại chỗ cỏ bị thay hoặc phá, kiểm tra cặp dựa trên hướng nửa trên/dưới và chỉ dọn ô đối ứng đúng loại/metadata. Luồng sinh hàng loạt vẫn dùng ghi nội bộ.
- [ ] Dùng `Block.canPlaceAt(World, x, y, z)` và `onNeighborChange(World, x, y, z)` cho luật tồn tại trong bảng đặc tả. Tại ô vừa đổi, đưa gốc mía nằm trên các nền kề ngang vào hàng đợi. Khi Lava được đặt, kiểm tra các offset `abs(dx)+abs(dy)+abs(dz) <= 2`. Khi Ice hoặc Snow Layer được đặt, kiểm tra vùng tương tự để có cùng kết quả dù đặt theo thứ tự ngược.
- [ ] Kiểm tra tuyết trên Spruce Leaves, lớp tuyết 1 và 8, nước dưới Ice, Lava cách Manhattan 2 và 3 và tuyết chọn tile đúng trên Grass. Đưa luật chọn mặt phủ tuyết lên trước luật Grass chuyển Dirt. Chặn block thay thế vòng lặp bằng so sánh ID và metadata thật trước khi ghi và gọi luật tiếp.

### 3. Thực thể rơi và luồng đào/đặt

- [ ] Thêm ca Sand và Gravel mất nền chỉ sinh một entity. Cập nhật qua nhiều bước thời gian để kiểm tra đáp trên Stone, thay cây cỏ hợp lệ hoặc rơi item khi ô đáp bị chiếm. Kiểm tra Ice bị đập thành Water trên Stone/Water, thành Air trên Air và không tạo item.
- [ ] Tạo `FallingBlockEntity.java` kế thừa Entity, giữ ID nguồn, vận tốc dọc và cờ hoàn tất. Quét đoạn di chuyển dọc qua các mức Y để gặp nền ngay cả ở bước lớn. Khi đáp, thử đặt có kiểm tra vùng trống và luật ghi đè; trả item đúng một lần nếu thất bại. Dùng quy tắc dọn entity hoàn tất của `World.updateEntities` cho vật phẩm đã nhặt và khối rơi đã kết thúc. Giải phóng thực thể rơi khỏi danh sách nếu vượt đáy thế giới.
- [ ] Trong `World.breakBlockAndSpawnDrop`, chọn trạng thái thay thế theo block trước khi kích hoạt cập nhật lân cận; Ice tự trả kết quả Water/Air theo nền và bỏ lần rơi item. Trong `VoxelGameApp.java`, chuyển xử lý đào sang API này. Bổ sung `World.tryPlaceBlock` nhận tọa độ đích, block và người chơi, trả `boolean` sau khi kiểm tra đầy đủ và ghi thành công. Ứng dụng chỉ trừ Hotbar khi nhận `true`.
- [ ] Kiểm tra thứ tự chọn ô: xếp tuyết đang ngắm khi còn dưới 8, ghi đè ô đang ngắm hợp lệ, rồi chọn ô kề mặt trúng. Thử ô đích Water/Lava, giới hạn thế giới, cây cần nền đặc thù, đủ hai ô cho Tall Grass và trường hợp block mới giao cắt Player.

### 4. Raycast và va chạm

- [ ] Thêm tia đi qua phần trống của Cactus hoặc phía trên Snow Layer thấp phải đến block phía sau. Tia đi qua hộp chọn cây phải hit dù cây không rắn. Kiểm tra Water/Lava xuyên được, tọa độ mặt trúng và giới hạn tầm với cũ. Kiểm tra các thành phần hướng bằng 0 để tránh NaN trong phép cắt slab.
- [ ] Dùng DDA hiện có trong `Raycast.java`, kiểm tra AABB trong khoảng `tEnter..tExit` của từng ô và tiếp tục khi hộp không trúng. Lấy mặt từ slab đầu tiên trúng để xác định ô đặt. Giữ đường gọi hiện có và thêm đường ghi kết quả vào đối tượng tái sử dụng cho lần cast mỗi frame nếu cần tránh cấp phát.
- [ ] Trong `Entity.java`, bổ sung kiểm tra giao cắt hộp cục bộ dịch theo tọa độ block, rồi dùng ở `Player.java` và `ItemEntity.java`. Giữ X → Z → Y. Với vật cản tuyết, thử nhấc đúng độ cao cần thiết tối đa một nửa block khi đang trên mặt đất, có khoảng trống phía trên và bước thử tăng được quãng ngang. Sneak dùng bề mặt va chạm thực để nhận diện mép. Kiểm tra bước lên tuyết và không bước khi có trần thấp.
- [ ] Với Ice, lưu vận tốc ngang giữa bước cập nhật khi còn trên băng, dùng hệ số 0.98 chuẩn hóa theo `deltaTime` và đặt lại nhánh vận tốc cũ khi bay hoặc rời băng. Kiểm tra quãng trượt dài hơn Stone khi nhả phím và về nhánh cũ sau khi rời băng. Ghi pháp tuyến tiếp xúc Cactus khi giải va chạm cạnh/đỉnh, áp dụng đẩy nhẹ kèm cooldown 0,5 s; xuất cường độ hiệu ứng cho HUD trong 0,25 s. Kiểm tra Bay, Bơi, Sprint, Sneak và nhặt đồ trong game ở cuối mốc.
- [ ] Chạy `.\gradlew.bat regressionChecks compileJava build` và đồng bộ Phần A `README.md` về ID, block, va chạm và đường đào/đặt. Ghi nhận thay đổi trực quan của block mới cần hoàn tất ở mốc 2.

## Mốc 2 — Nhập PNG và kết xuất

### 5. Nhập đúng 13 PNG và xây atlas

- [ ] Từ gốc repo, dùng PowerShell nhập các mục được liệt kê. Kiểm tra toàn bộ mục trước khi ghi, giữ các tệp có sẵn và so SHA-256 từng đích với byte trong archive. Nếu tệp cùng tên đã có với hash khác, báo tên xung đột để đối chiếu trước khi thay tệp đó.

```powershell
$archivePath = 'D:/bt-lớn-OOP/more texture added/1.21.11.zip'
$names = @(
    'sandstone', 'sandstone_top', 'sandstone_bottom', 'spruce_log_top',
    'short_grass', 'tall_grass_bottom', 'tall_grass_top', 'dandelion',
    'poppy', 'cornflower', 'oxeye_daisy', 'sugar_cane', 'gravel'
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($archivePath)
$hash = [Security.Cryptography.SHA256]::Create()
try {
    foreach ($name in $names) {
        if ($null -eq $zip.GetEntry("assets/minecraft/textures/block/$name.png")) {
            throw "Thiếu $name.png trong archive."
        }
    }
    foreach ($name in $names) {
        $entry = $zip.GetEntry("assets/minecraft/textures/block/$name.png")
        $target = Join-Path (Resolve-Path 'src/main/resources/textures').Path "$name.png"
        $stream = $entry.Open()
        try {
            $expected = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '')
        } finally {
            $stream.Dispose()
        }
        if (-not (Test-Path -LiteralPath $target)) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target)
        }
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expected) {
            throw "PNG khác archive: $target"
        }
    }
    "Đã xác minh $($names.Count) PNG khớp archive."
} finally {
    $hash.Dispose()
    $zip.Dispose()
}
```

- [ ] Trong `Texture.java`, mở `8 × 5`, giữ tile 0–19 và dùng 20–32 theo thứ tự của mảng `$names`. Ở mốc 1 có thể đặt trước các hằng tile mới, nhưng đợi atlas mở rộng mới đưa chúng vào đường dựng mesh/UV. Dùng tên tệp thật qua `loadTile`; thêm chế độ nhuộm RGB giữ nguyên alpha, tái dùng `blitResizedToTile`. Với nước và băng, giữ độ bán trong suốt, giữ xử lý khung đầu của nước và dung nham. Đặt fallback cho từng tile sau khi loader PNG báo thất bại. Giữ lọc Nearest/ClampToEdge, cập nhật UV và giải phóng Pixmap bằng `finally`.
- [ ] Kiểm tra atlas 128 × 80, UV tile đầu/cuối và các mặt khác nhau của Sandstone/Spruce Log, tile lá có alpha 0 ở khe và nước/băng có pixel alpha trung gian. Khởi chạy với bộ tài nguyên thử thiếu một PNG để xác nhận fallback mà không thay/xóa tệp người dùng đang dùng. Chạy `.\gradlew.bat processResources` và xác minh 13 PNG được đóng gói.

### 6. Mesher, thứ tự trong suốt và HUD

- [ ] Viết kiểm tra mesher CPU bằng dữ liệu đỉnh cho hai Water kề nhau, Ice giáp Water, khối Stone sau lá, Cactus thu hẹp, cây chữ X hai mặt và hai lớp tuyết không cùng cao độ. Với trường hợp qua biên chunk, sửa ô giáp biên và xác nhận hai mesh được đánh dấu dựng lại. Thử với dữ liệu mép ngoài bản đồ.
- [ ] Trong `Chunk.rebuildMesh`, chia đỉnh vào hai `FloatArray` tái sử dụng do World giữ, tải lên mesh đục/khoét và mesh trong suốt riêng. Dùng hình học thực, chỉ cắt phần mặt bị khối bên phủ kín. Water giữ cao độ mặt thoáng 0,88; Ice khối đầy; Lava giữ hình học và hành vi hiện có. Giữ alpha discard cho pha khoét, bảo đảm pixel bán trong suốt đi tới pha blend.
- [ ] Trong `World.renderVisibleChunks`, duyệt chunk bằng frustum hiện có, dựng tối đa 16 chunk ưu tiên gần camera và giữ chính sách thu hồi sau 120 frame. Giữ danh sách chunk có mesh trong suốt trong mảng tham chiếu cấp phát trước; dùng mảng `float[]` cho khoảng cách bình phương và khóa tọa độ nguyên để phá hòa. Chọn vị trí camera thực từ `VoxelGameApp` bằng dữ liệu đã có hoặc vector đích tái sử dụng; tránh sử dụng tâm mặt phẳng near cho khóa nếu nó làm đổi thứ tự khi camera chỉ xoay.
- [ ] Dùng heapsort tại chỗ trên phần mảng đang dùng, với thứ tự khoảng cách giảm dần và tọa độ tăng dần khi hòa. Hoán đổi tham chiếu và khóa cùng nhau. Kiểm tra đầu vào rỗng, một phần tử, khóa hòa và tập đã sắp xếp; ví dụ khóa khoảng cách `{4, 25, 25, 1}` và ID `{4, 9, 2, 1}` cho thứ tự ID `{2, 9, 4, 1}`. Xóa các ô tham chiếu từng được dùng khi danh sách thu ngắn hoặc world bị dispose. Tránh boxing, clone, comparator, stream và buffer cấp phát theo frame.
- [ ] Vẽ mesh đục/khoét cho toàn bộ chunk và thực thể đục trước khi blend. Trong pha nước/băng, giữ depth test, tắt depth write, bật blend chuẩn, rồi khôi phục trạng thái trước lớp phủ/HUD. Kiểm tra đường gọi `World.render`, `VoxelGameApp.render` và tài nguyên khi chunk bị dựng lại, bị thu hồi hoặc world đóng. Hai mesh cùng dùng chung vòng đời của chunk. Trật tự theo chunk xấp xỉ đối với mặt nước/băng trong cùng chunk.
- [ ] Trong `UIRenderer.java`, dùng hộp chọn cho khung mục tiêu, giữ thanh tiến trình đào và vẽ viền đỏ theo trạng thái Player. Trong `ItemEntity.java` và hotbar, dùng mặt cỏ, hình xương rồng và tuyết phù hợp; dùng chung mesh vật phẩm theo block và giữ hình chuẩn cho các item cũ. Xác nhận thực thể rơi được vẽ bằng cùng quy ước hình học chuyển động.
- [ ] Chạy `.\gradlew.bat regressionChecks compileJava build`. Chạy desktop, đứng ở ranh giới hai chunk có nước/băng và quay camera để quan sát thứ tự và vệt viền alpha. Đồng bộ atlas, đồ họa, tài nguyên và cách thu hồi mesh trong Phần A của `README.md`.

## Mốc 3 — Sinh thế giới và nghiệm thu

### 7. Noise, biome và phân bố

- [ ] Thêm kiểm tra `BiomeType` có `PLAINS`, `DESERT`, `SNOWY_PLAINS`. Với seed `20261114L`, quét toàn bộ vành 40–100 quanh `(896, 896)`, khẳng định gặp cả Desert và Snowy Plains. Kiểm tra tâm và vùng an toàn liên tục là Plains, cùng seed cho cùng kết quả và thay đổi seed ảnh hưởng mẫu ngoài vùng bảo đảm spawn.
- [ ] Tạo `TerrainNoise.java` với value noise 2D: hash bốn góc ô từ `seed, x, z`, nội suy theo `t * t * (3 - 2 * t)`, cộng ba octave với biên độ `1, 0.5, 0.25` và chuẩn hóa tổng. Dùng trường nhiệt độ tần số thấp cộng hiệu chỉnh địa phương quanh spawn để tạo hai vùng ấm/lạnh hữu hạn. Đặt tâm Plains và một vùng ấm, một vùng lạnh trong vành yêu cầu; chốt ngưỡng và offset bằng kiểm thử seed cố định.
- [ ] Tạo `TerrainGenerator.java` phân loại biome từ nhiệt độ và dùng dải chuyển tiếp quanh ngưỡng để trộn độ cao. Chọn Bedrock ở Y=0, nội suy độ cao có giới hạn, dùng lớp nền và độ dày theo bảng đặc tả. So sánh độ cao của cặp cột sát ranh giới trước khi đục hồ để phát hiện bước nhảy do ngưỡng biome.

### 8. Sinh hồ, thực vật và spawn

- [ ] Sinh theo các lượt địa tầng → hồ Lava/Water → Ice/tuyết → cây → thực vật. Dùng hash tọa độ và seed riêng cho mỗi lượt, giữ cấu trúc chunk thưa. Chọn hồ theo các điểm ứng viên thưa và quy tắc biome; trộn đáy hồ Plains, tạo ốc đảo hiếm, đóng mặt hồ Snowy Plains với lớp nước bên dưới. Khi đặt băng/tuyết trong lúc sinh, loại ứng viên gần Lava trong bán kính Manhattan 2.
- [ ] Sinh cây Oak thưa, Spruce dạng tầng hình nón, tuyết trên nền và trên lá lộ thiên, xương rồng mảnh theo điều kiện nền và bốn ô ngang, mía theo nước cạnh ô nền, Tall Grass hai ô và các cụm hoa. Kiểm tra lại điều kiện sống khi ghi ứng viên để xử lý giao nhau giữa hai cấu trúc và ở biên chunk. Chừa vùng spawn theo dấu chân Player, đủ chiều cao và tránh nước, lava và cây.
- [ ] Gọi bộ sinh mới trong constructor `World()`, giữ kích thước thế giới, seed và warm-up mesh quanh spawn. Đo bộ nhớ và thời gian khởi động trước khi cân nhắc thay đổi lớn như streaming. Dùng bộ đệm chiều cao kiểu nguyên thủy, tra biome một lần trên mỗi cột trong các lượt cần thiết. Sinh hai lần cùng seed trong test, so hash mẫu địa tầng và cây theo vùng, kiểm tra ít nhất một hồ đóng băng có nước phía dưới và spawn đứng trên mặt đất an toàn. Dùng test có world trống cho các trường hợp tương tác, tránh sinh nhiều bản đồ lớn cho các test đó.

### 9. Nghiệm thu game và tài liệu

- [ ] Chạy `.\gradlew.bat compileJava build` trên Java 17 và xác minh SHA-256 của 13 PNG đã đóng gói. Dùng cùng máy và cấu hình baseline để chạy desktop, di chuyển và bay qua ba biome. So thời gian khởi động, frametime trung vị và p95 theo cùng lộ trình hoặc các cảnh có mật độ mesh tương đương. Đánh giá theo mục tiêu 60 FPS (ngân sách khoảng 16,67 ms/frame) và ghi cấu hình cùng kết quả đo.
- [ ] Dùng Java Flight Recorder sau khi làm ấm, ghi các sự kiện heap allocation trong phiên đứng yên rồi quay camera. Dùng stack trace để kiểm tra thu thập, sắp xếp và vẽ translucent. Kết hợp kiểm tra mã nguồn hoặc bộ đếm byte cấp phát trên render thread quanh riêng pha này để xác nhận trường hợp mẫu JFR không quan sát thấy cấp phát. Kiểm tra cả lúc bay để phân biệt chi phí dựng mesh với chi phí sắp xếp. Quan sát số mesh sau khi rời vùng và tài nguyên sau khi đóng world.
- [ ] Chơi thử đào/đặt, vật phẩm rơi/nhặt, Fly, Sneak, Sprint, Pause Menu và âm thanh. Rà các thử nghiệm cát/sỏi, cactus, ice, tuyết, cỏ cao và mía trong [đặc tả](../specs/2026-11-23-three-biomes-design.md). Ghi kết quả đo thực tế trước khi xác nhận hiệu năng.
- [ ] Đồng bộ Phần A của `README.md` theo phiên bản đã chạy, gồm số file Java, bảng ID, atlas, biome, va chạm, đồ họa, HUD, ngưỡng mesh và quy trình sinh map. Kiểm tra `git diff --check` và commit từng nhóm đã xác minh bằng danh sách tệp cụ thể. Giữ các chỉnh sửa đang có của người dùng trong worktree nguồn.
