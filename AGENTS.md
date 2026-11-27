# AGENTS.md — Bộ Quy Tắc & Cơ Chế Vận Hành Agent Cho Dự Án VoxelGame

File này là **bộ quy tắc tối thượng** điều khiển hành vi của AI Agent khi đọc, phát triển, mở rộng hoặc tái cấu trúc dự án **VoxelGame** (`com.voxelgame`)[cite: 1]. Trước mọi tác vụ, Agent **BẮT BUỘC** đọc đồng thời `AGENTS.md` và **`README.md`** (tài liệu nguồn chân lý mô tả 100% tính chất hiện tại của dự án).

---

## 1. CÁC QUY TẮC BẮT BUỘC (TUÂN THỦ 100%)

### 1.1. Bảo toàn 100% tính năng hiện có & hiệu năng 60 FPS
- Khi người dùng yêu cầu **thêm tính năng mới** hoặc **tái cấu trúc dự án**, Agent **BẮT BUỘC bảo toàn 100%** toàn bộ tính năng **gameplay, đồ họa, vật lý, giao diện (UI/HUD) và âm thanh** đang được ghi nhận trong `README.md`.
- **Không tự ý thay đổi hành vi cũ:** Chỉ khi người dùng **chỉ định trực tiếp** việc sửa hoặc thay thế một tính năng/thông số cũ thì Agent mới được phép thay đổi phần đó; mọi phần khác phải hoạt động y nguyên.
- **Bảo toàn hiệu năng 60 FPS:** Giữ ổn định 60 FPS (V-Sync `glfwSwapInterval(1)`)[cite: 1], không cấp phát rác (GC churn) trong vòng lặp cập nhật/vẽ, không rò rỉ bộ nhớ Off-heap (`MemoryUtil`), tài nguyên GPU (VAO/VBO/Texture/Shader) hay luồng nền[cite: 1].

### 1.2. Tự do tái cấu trúc kiến trúc dự án
- Agent **KHÔNG bị trói buộc** vào cấu trúc thư mục, cách chia package hay kiến trúc class cũ của dự án.
- Khi người dùng yêu cầu tái cấu trúc (refactor, tách module, đổi design pattern, tổ chức lại package/class), Agent được quyền thiết kế lại kiến trúc mới sạch hơn, dễ mở rộng hơn — miễn là **hành vi đầu ra bảo toàn 100% đặc tả trong `README.md` và giữ vững 60 FPS** (trừ những điểm người dùng yêu cầu đổi).

### 1.3. Chú thích tiếng Việt đầy đủ & Cấm tuyệt đối code rút gọn
- **Chú thích tiếng Việt rõ ràng (UTF-8):** Mọi class, phương thức, hằng số, thuật toán và bước xử lý quan trọng đều phải có chú thích tiếng Việt mạch lạc, chính xác.
- **Viết đủ 100% logic của từng file:** Mọi file tạo mới hoặc chỉnh sửa phải chứa **toàn bộ mã nguồn hoàn chỉnh**, biên dịch và chạy được ngay trên Java 17[cite: 1].
- **CẤM TUYỆT ĐỐI** các bình luận viết tắt, lược bỏ hoặc giữ chỗ như:
  - `// ... code tương tự`
  - `// ... giữ nguyên phần còn lại`
  - `// TODO` / `// FIXME`
  - `/* các hàm khác không đổi */`

### 1.4. Quy tắc mở rộng tính năng mới & Làm nổi bật OOP
Khi người dùng yêu cầu thêm một đối tượng, thực thể hoặc hệ thống mới vào dự án (ví dụ: mob mới, item mới, block mới, gamemode, biome, thời tiết...):
1. **Nếu đã có nhóm đối tượng cùng loại trong dự án:** **BẮT BUỘC** sắp xếp vào đúng package, kế thừa đúng lớp trừu tượng cha (`abstract class`), triển khai đúng giao diện (`interface`) và tổ chức cấu trúc giống nhất quán với các lớp cùng loại hiện có (ví dụ: thêm mob mới phải đặt đúng `monster/` hoặc `animal/` và kế thừa `HostileMob`/`PassiveMob`/`NeutralMob`/`MountableMob`/`EquineMob`/`RangedMob` tương ứng; thêm item mới phải kế thừa lớp con phù hợp của `Item`...).
2. **Nếu là một khái niệm/hệ thống mới hoàn toàn chưa có trong dự án (ví dụ: `GameMode`):** **BẮT BUỘC** thiết kế và triển khai sao cho tuân thủ chặt chẽ và **làm nổi bật rõ ràng nhất 4 tính chất của OOP**:
   - **Đóng gói (Encapsulation):** Che giấu trạng thái nội bộ bằng `private`/`protected`, kiểm soát truy cập an toàn.
   - **Kế thừa (Inheritance):** Phân tách lớp cha trừu tượng và các lớp con cụ thể khi đối tượng có nhiều biến thể/chế độ thay vì gom vào một lớp đơn lẻ.
   - **Trừu tượng (Abstraction):** Định nghĩa hợp đồng hành vi rõ ràng qua `abstract class` hoặc `interface` tách biệt khỏi chi tiết cài đặt.
   - **Đa hình (Polymorphism):** Điều phối hành vi thông qua ghi đè phương thức (overriding) ở các lớp con thay vì viết chuỗi `if/else` hoặc `switch` theo loại đối tượng; đồng thời đặt vào package phù hợp với cấu trúc chuẩn Minecraft (`core`, `world.*`, `client.*`) và giữ nguyên tắc zero-GC.

---

## 2. QUY TẮC BẮT BUỘC ĐỒNG BỘ ĐẶC TẢ TRONG `README.md`

Sau **MỌI** lần chỉnh sửa, thêm mới, xóa bỏ hoặc tái cấu trúc dự án, Agent **BẮT BUỘC** rà soát **Phần A của `README.md`** và cập nhật các mục bị ảnh hưởng (cấu trúc dự án, cấu hình build, block, điều khiển, gameplay, vật lý, thế giới, đồ họa, UI, âm thanh, hiệu năng). Trước khi hoàn tất, Agent phải bảo đảm Phần A phản ánh **đúng và đủ 100%** tính chất của phiên bản dự án sau khi sửa.

---

## 3. QUY TRÌNH THỰC HIỆN CỦA AGENT CHO MỌI TÁC VỤ

1. **Đọc `README.md`** để nắm toàn bộ tính chất hiện có của dự án.
2. **Lập phạm vi thay đổi:** Bảo toàn 100% các phần không nằm trong yêu cầu sửa đổi; tuân thủ quy tắc mở rộng theo chuẩn OOP ở mục 1.4 khi thêm tính năng/đối tượng mới; linh hoạt tái cấu trúc nếu người dùng yêu cầu.
3. **Triển khai mã nguồn đầy đủ 100%:** Viết trọn vẹn từng file, chú thích tiếng Việt chi tiết ở mọi bước quan trọng, không có `// TODO` hay code rút gọn.
4. **Kiểm tra an toàn & 60 FPS:** Rà soát không rò rỉ VRAM/Off-heap/Thread, không tạo rác GC trong Game Loop, đảm bảo biên dịch sạch trên Java 17 (UTF-8)[cite: 1].
5. **Đồng bộ Phần A của `README.md`** theo mục 2.
