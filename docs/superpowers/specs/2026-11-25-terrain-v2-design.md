# Đặc tả nâng cấp địa hình VoxelGame

Ngày 25-11-2026

## Phạm vi

Nâng cấp địa hình theo `more.md` trên bản đồ hữu hạn 3000 × 256 × 1792 và seed mặc định hiện tại. Giữ ba biome, các luật đào và đặt block, trọng lực cát và sỏi, vật lý người chơi, chu kỳ ngày–đêm, thời tiết, âm thanh và giao diện hiện có. Phần A của `README.md` sẽ được cập nhật theo hành vi đã triển khai và kiểm chứng.

## Địa hình, nước và hồi sinh

- Phân loại Snowy Plains, Plains và Desert theo thứ tự tây sang đông. Dùng Perlin tần số thấp để uốn tọa độ quanh hai ranh giới. Bộ sinh địa tầng, thực vật và `World.getBiome` đọc cùng kết quả phân loại theo seed và tọa độ. Thời tiết, tích tuyết và HUD tiếp tục dùng `World.getBiome`.
- Kết hợp trường lục địa, xói mòn và ridged multifractal. Dùng spline phân đoạn để chuyển từ vùng thấp khoảng Y=26–34 sang đồi khoảng Y=35–60, rồi tăng nhanh ở vùng núi. Đặt các đỉnh cao nhất trong Snowy Plains và kiểm tra ít nhất một đỉnh tự nhiên đạt Y=140 trở lên trên seed mặc định.
- Tính độ cao của các cột trước khi ghi voxel, lưu trong `short[]` và lấy độ dốc bằng chênh lệch lớn nhất tới bốn cột ngang liền kề. Dốc tối đa 1 có cỏ trên 3–4 lớp đất ở Plains và Snowy Plains, hoặc 3–5 lớp cát trên sandstone ở Desert. Dốc 2 có sỏi xen đá trên nền rắn. Dốc từ 3 lộ đá hoặc sandstone tùy biome. Đá gốc giữ ở Y=0. Phủ Snow Layer hoặc Snow Block trên bề mặt lạnh hợp lệ, dùng Snow Block cho lớp phủ đỉnh cao hơn Y=110 và giữ quy tắc tránh dung nham.
- Giữ mô hình chunk hiện tại. `TerrainGenerator` gửi các dải vật liệu theo cột cho `World`, `World` chia dải tại biên Y của chunk, và `Chunk` ghi vào mảng voxel. Duy trì số ô khác Air, metadata và trạng thái mesh nhất quán. Dùng đường ghi này trong lượt sinh địa tầng ban đầu. Các thay đổi block khi chơi tiếp tục qua cơ chế cập nhật lân cận hiện có.
- Dùng dải hẹp quanh đường đồng mức của noise để tạo sông uốn lượn. Hạ nền lòng sông xuống dưới Y=28, chuyển độ sâu dần ra hai bờ, điền nước đến Y=28 và thay mặt nước bằng Ice trong Snowy Plains. Chọn bờ cát hoặc sỏi có nền đỡ, và chỉ trồng mía ở ô có nguồn nước hợp lệ. Giữ hồ nước, ốc đảo và hồ dung nham trong lượt sinh hồ. Kiểm tra chênh cao tối đa 3 block trên toàn dấu chân hồ và loại vị trí giao sông, công trình hoặc vùng an toàn của người chơi.
- Giữ tâm xuất phát tại `(1500, 896)`. Bố trí nền an toàn cho cả điểm xuất phát và điểm hồi sinh, tránh sông, hồ, tán cây và vật trang trí tại hai vị trí. Khi rơi khỏi thế giới, tìm mặt đỡ rắn cao nhất dưới toàn bộ dấu chân tại `(1500, 1500)`. Đặt chân ngay trên mặt đỡ ở vị trí đủ hai ô trống, kiểm tra hộp va chạm rồi đặt lại vận tốc và chế độ bay. Nếu người chơi đã xây kín cột chính, tìm vị trí hợp lệ gần tọa độ hồi sinh trước khi dùng điểm xuất phát an toàn.

## Thực vật và công trình

- Dùng noise tần số thấp cho mật độ cây và giữ khoảng trống giữa các cụm rừng. Plains có sồi thường, sồi cổ thụ và bụi sồi. Snowy Plains có vân sam tán rộng ở vùng thấp, thông nhọn ở sườn và thông lùn thưa gần Y=75. Sinh cọ theo dáng thân và tán tự thiết kế ở bờ ốc đảo hợp lệ. Kiểm tra nền, khoảng trống và biên trước khi ghi các phần của cây.
- Dùng Simplex Noise theo seed để gom dandelion, cornflower, poppy và oxeye daisy theo loài. Giữ các điều kiện tồn tại của cỏ, hoa, mía, xương rồng, bụi khô và tuyết trên tán.
- Đặt ít nhất một kim tự tháp trong Desert, ưu tiên gần Plains. Chọn dấu chân hợp lệ trước khi sinh hồ và cây. Dùng đáy vuông giật cấp bằng sandstone, lối vào có hoa văn riêng, sảnh có cột và tầng bí mật dưới lòng đất. Làm sàn, vách và móng sandstone liên tục tới nền đá chắc. Cát quanh công trình nằm trên nền đỡ và các ô đỡ không bị khoét khi làm lối xuống phòng.
- Rương là khối trang trí trong phòng bí mật. Dùng pixel art tự thiết kế. Rương đào, rơi vật phẩm và đặt theo cơ chế khối thông thường. Dùng Snow Block, Sandstone và các tài nguyên hiện có cho những bề mặt đã được chốt.

## Kết xuất và tiêu chí nghiệm thu

- Giữ lớp mây ở Y=140. Tính fog từ khoảng cách camera đến tọa độ thế giới, nội suy từ đầu đến cuối vùng fog và hòa với màu chân trời lấy theo giờ và cường độ giáng thủy. Áp dụng cho mặt block và thực thể thế giới. Giữ alpha, các lượt vẽ thời tiết và giao diện, ánh sáng dung nham và quy tắc depth/blend hiện hành.
- Giữ định dạng đỉnh hiện tại. Khi dựng mặt khối, lấy mẫu hai ô cạnh và một ô chéo ở từng góc rồi nhân mức AO vào `aShade`. Đọc ô qua biên chunk, chọn đường chéo tam giác phù hợp độ sáng và đánh dấu lại các mesh phụ thuộc khi block ở biên thay đổi. Áp dụng cả khi đường cập nhật hiện có dùng mesh có chỉ số hoặc mesh sáu đỉnh mỗi mặt.
- Cập nhật `TerrainGenerationChecks.java` để kiểm tra hai lần sinh cùng seed, đỉnh tuyết Y≥140, nền thấp Plains/Desert khoảng Y=26–34, vật liệu dốc, dải cát/sỏi có nền đỡ, biome trùng bề mặt, sông thường và sông đóng băng, dấu chân hồ, điểm hồi sinh, móng và phòng rương. Giữ các kiểm tra hiện hành về vật phẩm, va chạm và thời tiết. Bổ sung ca mesh kiểm tra AO qua biên chunk.
- Đo thời gian sinh toàn bản đồ trước và sau bằng cùng seed và cấu hình Java 17. Chạy `regressionChecks` và `check`. Kiểm tra desktop ở độ phân giải mặc định với V-Sync, theo dõi nhịp khung hình ở vùng đồi núi và qua các pha mưa/tuyết. Mục tiêu là giữ nhịp ổn định 60 FPS trên cấu hình kiểm tra. Ghi rõ thời gian sinh, cấu hình và kết quả đo thực tế vì `more.md` chưa quy định ngưỡng thời gian khởi tạo cụ thể.
