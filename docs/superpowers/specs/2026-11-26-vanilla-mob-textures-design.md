# Thiết Kế: Chuẩn Hóa 100% Texture & Mô Hình Mob Giống Minecraft

**Ngày:** 2026-11-26  
**Trạng thái:** Đã duyệt (Phương án A)

## 1. Mục tiêu & Phạm vi
- **Mục tiêu:** Đưa toàn bộ **23 loài mob** và **3 tài nguyên đạn/trang bị** (`arrow.png`, `potion.png`, `equipment/held.png`) hiển thị đúng **Minecraft Java Edition (1.21.11 / 26.x)** về chiều texture, tỷ lệ pixel vuông 1:1, cấu trúc bộ phận, độ trong suốt và tư thế mặc định.
- **Không thuộc phạm vi (Non-goals):** Không thay đổi AI, vật lý, hitbox va chạm, bảng sinh (spawn table), bảng rơi đồ (loot table), âm thanh hay tốc độ di chuyển của mob.
- **Ràng buộc:** Bảo toàn 100% tính năng gameplay hiện có và giữ ổn định 60 FPS (không cấp phát rác GC trong vòng lặp vẽ/cập nhật, không rò rỉ VRAM/Mesh/Texture). Toàn bộ kiểm thử hồi quy (`regressionChecks`) phải xanh; Phần A của `README.md` phải được đồng bộ đầy đủ.

## 2. Phương án đã cân nhắc & lựa chọn
- **Phương án A (Đã chọn):** Chuẩn hóa trực tiếp trên đường ống `ModelPart` / `MobModels` / `MobRenderer` / `MobAppearance` hiện có. Giữ kiến trúc tĩnh gọn nhẹ, không thêm bước đổi atlas thừa trong vòng lặp vẽ, bảo toàn hiệu năng 60 FPS.
- **Phương án B (Loại):** Bind trực tiếp `ItemTextureAtlas` cho giáo và lọ thuốc cầm tay — gây đổi texture thừa mỗi khung hình và tăng phụ thuộc cho `MobRenderer`.
- **Phương án C (Loại):** Tách mô hình 23 loài sang JSON ngoài — dư thừa độ phức tạp cho nhu cầu hiện tại.

## 3. Thiết kế chi tiết
1. **`ModelPart.java`:**
   - Sửa ánh xạ UV 6 mặt của các chế độ `MINECRAFT`, `HORIZONTAL`, `MIRRORED` cho đúng hệ trục `+Y` lên, `-Z` trước (khắc phục lật gương trái–phải và ngược chiều V mặt đáy).
   - Bổ sung góc nghỉ cơ sở (`baseRotX/Y/Z` được `resetPose()` khôi phục), cờ `doubleSided` (tắt face culling cục bộ), hình học `FLAT_SPRITE` (mặt phẳng 2 mặt cho cung/giáo/lọ cầm tay) và `CROSSED_PLANES` (chữ X cho đạn tên và lọ thuốc ném).
2. **`MobRenderer.java` & `Enderman.java`:**
   - Bật `GL_BLEND (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` khi vẽ lớp `OVERLAY` để 4 hoa văn đốm ngựa bán trong suốt hòa mượt; bật/tắt `GL_CULL_FACE` theo cờ `doubleSided`; hỗ trợ số đỉnh linh hoạt theo loại hình học và khôi phục sạch trạng thái OpenGL.
   - Bổ sung hiển thị khối đang cầm trên tay Enderman thông qua `MobVisualState.setHeldBlock(...)`.
3. **`MobAppearance.java`:**
   - Trả `heightScale()` về `1.0f` đồng nhất (loại bỏ kéo giãn dọc gây méo texel) và thêm `modelScale()` đồng nhất 3 trục (`0.6f` cho 3 biến thể thỏ).
4. **`MobModels.java`:**
   - Chuẩn hóa kích thước hộp 1:1 khớp UV, điểm xoay và tư thế nghỉ cho toàn bộ 23 loài mob (bao gồm tay khoanh & mũ nghiêng 4 tầng của Witch, lớp băng quấn `4×12×4` + sọ phụ `(32,0)` của Parched, áo `4×12×4` của Stray, chân 1 đốt `16×2×2` của Spider, đầu trượt hàm trong của Enderman, chân 2 mặt của Chicken, tai cao `5` của Rabbit, đầu nghiêng & râu 2 mặt của Goat, cổ/tai chuẩn của Horse & Donkey, v.v.).
5. **3 tài nguyên đạn/trang bị:**
   - `projectile/arrow.png`: Thay sang ảnh chuẩn Minecraft 32×32 (thân tên, mũi sắt, lông đuôi chữ thập).
   - `projectile/potion.png`: Thay bằng sprite lọ thuốc ném chuẩn Minecraft 16×16.
   - `equipment/held.png`: Giữ 4 khung kéo dây cung Minecraft gốc ở hàng dưới, thay vùng giáo và lọ thuốc bằng sprite 16×16 chuẩn Minecraft từ `textures/item/iron_spear.png` và `textures/item/potion_healing.png`.
6. **Kiểm thử & Tài liệu:**
   - Mở rộng `MobAssetAndModelChecks.java` kiểm tra chiều UV không lật gương, tỷ lệ texel 1:1, kích thước 3 tài nguyên mới và toàn bộ cầu bao hoạt ảnh.
   - Chạy `./gradlew regressionChecks` và đồng bộ Phần A trong `README.md`.
