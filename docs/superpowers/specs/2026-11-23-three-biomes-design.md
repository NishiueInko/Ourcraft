# Đặc tả ba biome cho VoxelGame

Ngày 23-11-2026

## Phạm vi

Thêm Plains, Desert và Snowy Plains cùng các block, tương tác và thay đổi kết xuất trong `prompt test.md`. Giữ Java 17, UTF-8, V-Sync, điều khiển, Bay, Sneak, Sprint, Hotbar, Pause Menu, nhạc nền và cơ chế nhặt vật phẩm. Các thay đổi hành vi chủ đích gồm địa hình, tương tác block, va chạm theo hình học, băng trơn và phản hồi khi chạm xương rồng. Mob, hệ thống máu, thời tiết chủ động và nhãn biome trên HUD nằm ngoài phạm vi này.

## Mốc 1 — Block, trạng thái và tương tác

### Lưu trữ và cập nhật

- Giữ nguyên ID 0–8, mở `Block.BY_ID` lên 256 và tra ID byte dưới dạng không dấu. Chunk tiếp tục lưu ID trong `byte[]`. Mảng metadata được cấp phát khi chunk cần lưu số lớp tuyết hoặc nửa trên của cỏ cao. Ghi đè hay xóa block đồng thời xóa metadata cũ.
- Block cung cấp lớp kết xuất, hình học, tính rắn, tính đục, khả năng chọn và thay thế, hệ số ma sát, hộp va chạm, hộp chọn và quy tắc tồn tại. Hộp dùng dữ liệu tái sử dụng hoặc giới hạn bất biến để truy vấn trong vòng lặp. Nền thường có hệ số mặc định `0.6f` và giữ tốc độ điều khiển hiện tại. Băng dùng `0.98f` để giữ quán tính ngang.
- Khi chơi, các thao tác thay block ghi ID và metadata trước, đánh dấu mesh bị ảnh hưởng rồi đưa các ô cần kiểm tra vào hàng đợi tọa độ tái sử dụng. Xử lý lặp theo FIFO, bỏ qua lần ghi không đổi và gộp thông báo trùng đang chờ. Các luật có thể thay đổi thêm ô và tiếp tục đưa công việc vào cùng hàng đợi. Thao tác hai ô như đặt cỏ cao công bố cả hai ô trước khi xử lý thông báo.
- Kiểm tra ô thay đổi và sáu ô liền kề, kể cả qua biên chunk. Khi mức nước quanh một ô nền thay đổi, kiểm tra gốc mía ở trên nền. Khi thêm dung nham hoặc thay đổi băng/tuyết, kiểm tra bán kính Manhattan 2 theo quy tắc tan chảy. Sinh hàng loạt dùng đường ghi nội bộ và đặt cấu trúc hợp lệ theo thứ tự, tránh phát thông báo cho từng ô địa tầng.

### Luật block

| Block | Hình dạng và độ cứng tay không | Tương tác |
| --- | --- | --- |
| Grass Block | Khối đầy; giữ độ cứng hiện tại | Snow Layer hoặc Snow Block ở trên bật mặt cỏ phủ tuyết và ưu tiên hơn luật chuyển thành Dirt. Khối đầy, đục khác ở trên chuyển cỏ thành Dirt ngay. |
| Sand, Gravel | Khối đầy, đục; 0,75 s và 0,90 s | Mất mặt đỡ thì chuyển thành `FallingBlockEntity`. Khi chạm nền, đặt lại ở ô hợp lệ. Thay thế ô có thể ghi đè như cây cỏ hoặc tuyết 1 lớp. Rơi thành vật phẩm nếu đáp xuống vị trí không thể đặt. Một ô nguồn chỉ tạo một thực thể rơi. |
| Sandstone | Khối đầy, đục; 4,0 s | Có tile riêng cho mặt bên, trên và dưới. |
| Cactus | Thu hẹp X và Z mỗi bên 1/16, cao 1 block; 0,60 s | Chỉ sống trên Sand hoặc Cactus và cần bốn ô ngang không có block rắn hoặc dung nham. Khi mất điều kiện, cột tự vỡ và rơi vật phẩm. Chạm cạnh hoặc đỉnh đẩy nhẹ người chơi, chớp đỏ 0,25 s với hồi chiêu 0,5 s. |
| Dead Bush | Hai mặt chéo khoét alpha; 0,05 s | Chọn được, không cản di chuyển. Cần Sand hoặc Dirt. |
| Short Grass, Tall Grass | Hai mặt chéo khoét alpha; 0,05 s | Chọn và ghi đè được. Cần Grass Block hoặc Dirt. Tall Grass chiếm hai ô. Phá, thay hoặc gỡ nền của một nửa sẽ dọn cả cặp. |
| Dandelion, Poppy, Cornflower, Oxeye Daisy | Hai mặt chéo khoét alpha; 0,05 s | Cần Grass Block hoặc Dirt. Khi bị phá hoặc mất nền, rơi chính bông hoa đó. |
| Sugar Cane | Hai mặt chéo khoét alpha; 0,05 s | Đặt trên mía hoặc trên Grass Block, Dirt, Sand có Water kề ngang ô nền. Ice đủ điều kiện sau khi tan thành Water. Mất nền hoặc mất nguồn nước hợp lệ làm cả cột tự vỡ và rơi mía. |
| Snow Layer | Cao hiển thị `layers × 0.125f`; 0,25 s | Một lớp đi xuyên được và cho block rắn ghi đè. Từ 2–7 lớp, va chạm cao `(layers - 1) × 0.125f`. Tám lớp giữ trạng thái Snow Layer và va chạm cao 1 block. Đặt thêm tuyết vào lớp đang ngắm để tăng số lớp tới 8. Cần mặt trên đầy, rắn, bao gồm lá vân sam. Mất nền thì tự vỡ. Gần Lava thì tan mất. |
| Snow Block | Khối đầy, đục; 0,35 s | Khi phá rơi Snow Block. |
| Ice | Khối đầy, rắn, bán trong suốt; 0,75 s | Giữ đà ngang khi đi trên băng và trở lại điều khiển thường khi rời băng hoặc bay. Đập tay không không rơi item. Ô băng thành Water nếu phía dưới là block rắn hoặc Water, thành Air trong các trường hợp còn lại. Gần Lava thì thành Water. |
| Spruce Log | Khối đầy, đục; 3,0 s | Dùng mặt cắt gỗ vân sam cho hai đầu và vỏ gỗ cho bốn mặt bên. |
| Spruce Leaves, Oak Leaves | Khối đầy, rắn, khoét alpha; 0,30 s | Giữ lỗ trong tán lá và cho thấy bề mặt liền kề qua các khe hở. |

Raycast DDA kiểm tra hộp chọn trong khoảng tia đi qua mỗi ô. Air, Water và Lava tiếp tục cho tia đi xuyên. Cây cỏ, tuyết và xương rồng chỉ chặn tia khi tia chạm hộp chọn của chúng. Việc chọn đích đặt ưu tiên xếp tuyết trên ô đang ngắm, sau đó ghi đè ô chọn được và có thể thay thế, rồi xét ô kề mặt trúng tia. Luồng này vẫn cho phép đặt vào Water hoặc Lava ở ô đích dù tia đi xuyên qua chúng. Kiểm tra biên, điều kiện tồn tại, ô phụ và giao cắt với người chơi trước khi ghi block và trừ Hotbar.

Player và ItemEntity dùng hộp va chạm thực. Player giữ thứ tự giải va chạm X → Z → Y, hỗ trợ bước ngắn trên tuyết nếu đủ khoảng trống cho thân và tiếp tục giữ mép khi Sneak. Phản lực xương rồng được áp dụng khi phát hiện tiếp xúc trong quá trình giải va chạm, kể cả khi người chơi bị đẩy ra khỏi hộp ngay trong cùng khung hình.

## Mốc 2 — Texture và kết xuất

### Nguồn texture, atlas và alpha

Archive đầu vào đã xác định là `D:/bt-lớn-OOP/more texture added/1.21.11.zip`. Khi triển khai, trích xuất trực tiếp 13 tệp PNG từ `assets/minecraft/textures/block/` vào `src/main/resources/textures/`, giữ tên và byte nguồn. Giữ nguyên các PNG đã có. Đường dẫn archive chỉ dùng lúc nhập tài nguyên. Sau khi đóng gói, game đọc PNG từ tài nguyên của dự án.

| Nhóm | Tệp PNG cần nhập |
| --- | --- |
| Đá cát và thân vân sam | `sandstone.png`, `sandstone_top.png`, `sandstone_bottom.png`, `spruce_log_top.png` |
| Cỏ và mía | `short_grass.png`, `tall_grass_bottom.png`, `tall_grass_top.png`, `sugar_cane.png` |
| Hoa | `dandelion.png`, `poppy.png`, `cornflower.png`, `oxeye_daisy.png` |
| Sỏi | `gravel.png` |

- Mở atlas lên `8 × 5` ô `16 × 16` (128 × 80 px), giữ chỉ số 20 tile cũ và tính UV từ kích thước atlas trong `Block` và `Chunk`. Giữ lọc Nearest và ClampToEdge. Chỉ sinh pixel art dự phòng khi thiếu PNG nguồn (hoặc không đọc được).
- Nhuộm RGB của lá sồi, cỏ ngắn, cỏ cao và mía theo tông Plains, nhuộm lá vân sam theo tông xanh thông `#619961`. Giữ alpha gốc của các texture khoét. Giữ màu tuyết trên mặt cỏ, RGBA của hoa, bụi khô và xương rồng, cùng độ bán trong suốt của nước và băng. Những biến đổi này chỉ diễn ra khi xây atlas. Giải phóng Pixmap nguồn và atlas trên cả đường thành công lẫn đường lỗi.

### Hai pha vẽ và thứ tự trong suốt

1. Vẽ toàn bộ mesh đục và khoét alpha của chunk nhìn thấy với depth test và depth write. Cây chữ X có hai mặt cho mỗi mặt phẳng. Dung nham giữ hành vi chất lỏng hiện tại. Culling chỉ bỏ mặt có vùng che kín tương ứng. Mặt lá, xương rồng thu hẹp và các lớp tuyết khác độ cao cần giữ phần lộ ra.
2. Vẽ mesh nước và băng sau pha đầu. Giữ depth test, tắt depth write, bật `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA`, rồi khôi phục trạng thái trước khi vẽ lớp phủ và HUD. Bỏ mặt giữa hai ô cùng loại tại phần tiếp xúc, giữ mặt giáp không khí hoặc khối không đục khác loại.
3. Sắp xếp **mesh trong suốt theo chunk** từ xa đến gần dựa trên bình phương khoảng cách từ camera đến tâm chunk. Dùng tọa độ chunk để phá hòa theo thứ tự cố định. Danh sách chỉ chứa các chunk thấy được và có mesh trong suốt. Tạo trước mảng tham chiếu chunk, mảng khóa kiểu nguyên thủy và vùng làm việc cho sắp xếp, hoặc dùng sắp xếp tại chỗ trên các mảng tái sử dụng. Mỗi frame chỉ cập nhật phần tử và số phần tử hợp lệ. Tránh tạo danh sách, vector, khóa boxing, comparator, stream hay mảng tạm trong pha thu thập, sắp xếp và vẽ. Giải phóng cả hai mesh khi thu hồi chunk hoặc đóng thế giới. Sắp xếp theo chunk là xấp xỉ đối với các mặt trong cùng chunk.

Giữ frustum culling, ưu tiên dựng mesh gần camera, ngân sách 16 lần dựng chunk mỗi khung hình và chính sách thu hồi ngoài khung nhìn hiện có. Khung ngắm lấy đúng hộp chọn. Vật phẩm và icon Hotbar dùng hình phù hợp cho cây, xương rồng và tuyết. HUD hiển thị hiệu ứng chạm xương rồng theo bộ đếm thời gian của Player.

## Mốc 3 — Sinh thế giới và kiểm chứng

Tách bộ sinh địa hình tất định khỏi luồng cập nhật lúc chơi. Giữ kích thước `112 × 16 × 112` chunk, seed `20261114L`, spawn Plains tại `(896, 896)` và chunk rỗng dạng `null`. Dùng noise 2D nhiều tầng trên kiểu nguyên thủy để tính nhiệt độ và độ cao. Trộn độ cao liên tục ở vùng chuyển tiếp, rồi dùng biome tại từng cột để chọn địa tầng. Sinh theo thứ tự địa tầng, nước/dung nham, băng/tuyết, cây và thực vật. Giữ vùng spawn thoáng và bảo đảm Desert lẫn Snowy Plains có cột nằm cách spawn 40–100 block.

| Biome | Địa tầng và cảnh quan |
| --- | --- |
| Plains | Bedrock, Stone, 2–3 lớp Dirt, Grass Block. Đồi thoai thoải, sồi thưa, cỏ và hoa theo cụm. Hồ có đáy trộn Dirt, Sand và Gravel, mía cao 1–3 block ven nước, hồ Lava có bờ Stone. |
| Desert | Bedrock, Stone, 3–4 lớp Sandstone, 3–4 lớp Sand. Có cồn cát, xương rồng cao 1–3 block, bụi khô, ốc đảo hiếm có mía và hồ Lava. |
| Snowy Plains | Bedrock, Stone, 2–3 lớp Dirt, Grass Block phủ tuyết. Chủ yếu tuyết một lớp, xen bãi 2–3 lớp và mảng Snow Block. Hồ có Ice ở mặt trên và Water bên dưới. Cây vân sam thưa có tán hình nón và có thể giữ tuyết trên lá lộ thiên. Vùng gần Lava áp dụng quy tắc tan băng và tuyết. |

### Tiêu chí chấp nhận

- Chạy `gradlew.bat compileJava` và `gradlew.bat build` với Java 17. Kiểm tra 13 PNG mới khớp byte với archive và kiểm tra fallback bằng cách mô phỏng thiếu một tệp trong bộ tài nguyên dùng cho kiểm thử.
- Kiểm tra cát và sỏi rơi, xương rồng tự gãy và phản hồi khi chạm, băng trượt và vỡ thành nước có điều kiện, tuyết xếp đủ 8 lớp và thay thế được ở một lớp, băng và tuyết tan trong bán kính quy định. Kiểm tra cỏ cao hai ô và mía mất nước ở cả trong chunk và qua biên chunk. Kiểm tra Hotbar chỉ trừ item khi đặt thành công.
- Sinh lại cùng seed để đối chiếu mẫu địa hình, lớp đá và vị trí thực vật. Kiểm tra spawn an toàn, ba biome gần spawn và độ cao hai bên ranh giới chuyển tiếp tự nhiên.
- Chạy game để kiểm tra hình học, chọn block cạnh lớp tuyết/cây và nước/băng ở ranh giới chunk. Lấy mẫu cấp phát bằng JVM profiler sau khi làm ấm và xác nhận pha thu thập, sắp xếp, vẽ trong suốt không cấp phát trên heap trong các khung hình ổn định. Đo thời gian khởi động và thời gian khung hình khi đi và bay qua cả ba biome trên cùng cấu hình trước và sau thay đổi, với mục tiêu 60 FPS khi bật V-Sync. Kiểm tra mesh, Pixmap, texture và thực thể rơi được giải phóng đúng vòng đời.
- Đồng bộ Phần A của `PROPERTY.md` sau mỗi mốc triển khai, gồm cấu trúc mã nguồn, danh sách block, atlas, va chạm, kết xuất và thuật toán sinh biome.
