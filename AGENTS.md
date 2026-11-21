# AGENTS.md — Bộ Quy Tắc & Cơ Chế Vận Hành Agent Cho Dự Án VoxelGame

File này là **bộ quy tắc tối thượng** điều khiển hành vi của AI Agent khi đọc, phát triển, mở rộng hoặc tái cấu trúc dự án **VoxelGame** (`com.voxelgame`)[cite: 1]. Trước mọi tác vụ, Agent **BẮT BUỘC** đọc đồng thời `AGENTS.md` và **`PROPERTY.md`** (tài liệu nguồn chân lý mô tả 100% tính chất hiện tại của dự án).

---

## 1. CÁC QUY TẮC BẮT BUỘC (TUÂN THỦ 100%)

### 1.1. Bảo toàn 100% tính năng hiện có & hiệu năng 60 FPS
- Khi người dùng yêu cầu **thêm tính năng mới** hoặc **tái cấu trúc dự án**, Agent **BẮT BUỘC bảo toàn 100%** toàn bộ tính năng **gameplay, đồ họa, vật lý, giao diện (UI/HUD) và âm thanh** đang được ghi nhận trong `PROPERTY.md`.
- **Không tự ý thay đổi hành vi cũ:** Chỉ khi người dùng **chỉ định trực tiếp** việc sửa hoặc thay thế một tính năng/thông số cũ thì Agent mới được phép thay đổi phần đó; mọi phần khác phải hoạt động y nguyên.
- **Bảo toàn hiệu năng 60 FPS:** Giữ ổn định 60 FPS (V-Sync `glfwSwapInterval(1)`)[cite: 1], không cấp phát rác (GC churn) trong vòng lặp cập nhật/vẽ, không rò rỉ bộ nhớ Off-heap (`MemoryUtil`), tài nguyên GPU (VAO/VBO/Texture/Shader) hay luồng nền[cite: 1].

### 1.2. Tự do tái cấu trúc kiến trúc dự án
- Agent **KHÔNG bị trói buộc** vào cấu trúc thư mục, cách chia package hay kiến trúc class cũ của dự án.
- Khi người dùng yêu cầu tái cấu trúc (refactor, tách module, đổi design pattern, tổ chức lại package/class), Agent được quyền thiết kế lại kiến trúc mới sạch hơn, dễ mở rộng hơn — miễn là **hành vi đầu ra bảo toàn 100% đặc tả trong `PROPERTY.md` và giữ vững 60 FPS** (trừ những điểm người dùng yêu cầu đổi).

### 1.3. Chú thích tiếng Việt đầy đủ & Cấm tuyệt đối code rút gọn
- **Chú thích tiếng Việt rõ ràng (UTF-8):** Mọi class, phương thức, hằng số, thuật toán và bước xử lý quan trọng đều phải có chú thích tiếng Việt mạch lạc, chính xác.
- **Viết đủ 100% logic của từng file:** Mọi file tạo mới hoặc chỉnh sửa phải chứa **toàn bộ mã nguồn hoàn chỉnh**, biên dịch và chạy được ngay trên Java 17[cite: 1].
- **CẤM TUYỆT ĐỐI** các bình luận viết tắt, lược bỏ hoặc giữ chỗ như:
  - `// ... code tương tự`
  - `// ... giữ nguyên phần còn lại`
  - `// TODO` / `// FIXME`
  - `/* các hàm khác không đổi */`

---

## 2. QUY TẮC BẮT BUỘC ĐỒNG BỘ & GHI NHẬT KÝ VÀO `PROPERTY.md`

Sau **MỌI** lần chỉnh sửa, thêm mới, xóa bỏ hoặc tái cấu trúc dự án, Agent **BẮT BUỘC** thực hiện đủ **cả 2 việc** sau trên file `PROPERTY.md`:

### Việc 1: Ghi nhật ký đầy đủ 100% thay đổi ở cuối file `PROPERTY.md`
Nối tiếp (append) vào cuối mục **Lịch sử Chỉnh sửa** trong `PROPERTY.md` theo đúng cấu trúc:

```text
------------------------------------------------------------------------------------
lần chỉnh sửa thứ `n`:
// chi tiết nội dung chỉnh sửa và lưu ý cần thiết
```

**Yêu cầu nội dung bên trong `lần chỉnh sửa thứ n`:**
1. **Tăng dần số thứ tự `n`:** Bắt đầu từ `1` và tăng `+1` ở mỗi lần chỉnh sửa sau. **Tuyệt đối không xóa hoặc sửa lịch sử các lần cũ.**
2. **Liệt kê 100% thay đổi:** Ghi đầy đủ danh sách file thêm mới / chỉnh sửa / di chuyển / xóa (kèm đường dẫn), chi tiết logic thay đổi và các lưu ý kỹ thuật quan trọng (phụ thuộc mới, cấu trúc, hiệu năng, bộ nhớ, cách chạy/build).
3. **BẮT BUỘC đối chiếu Trước & Sau khi có xóa và thêm (thay thế/sửa đổi):**
   Mỗi khi chỉnh sửa bằng cách **xóa một thứ cũ và thêm vào một thứ mới** (hoặc thay đổi logic/cấu trúc/tính năng hiện có), Agent **BẮT BUỘC** phải ghi chú rõ ràng 2 phần trong `lần chỉnh sửa thứ n`:
   - **Trước khi chỉnh sửa:** Mô tả chính xác trạng thái/hành vi/cấu trúc của chương trình trước lúc xóa/sửa.
   - **Sau khi chỉnh sửa:** Mô tả chính xác trạng thái/hành vi/cấu trúc của chương trình sau lúc thêm/sửa.

### Việc 2: Cập nhật đồng bộ phần mô tả tính chất ở phía trên của `PROPERTY.md`
- Ngay sau khi ghi `lần chỉnh sửa thứ n`, Agent **BẮT BUỘC** rà soát và cập nhật lại các mục tính chất ở **phần trên của `PROPERTY.md`** (cấu trúc dự án, cấu hình build, block, điều khiển, gameplay, vật lý, thế giới, đồ họa, UI, âm thanh, hiệu năng...).
- Mục tiêu: **Phần trên của `PROPERTY.md` luôn phản ánh đúng 100% toàn bộ tính chất của phiên bản dự án mới nhất**, không bao giờ để lại thông tin lỗi thời hoặc thiếu tính năng vừa thêm.

---

## 3. QUY TRÌNH THỰC HIỆN CỦA AGENT CHO MỌI TÁC VỤ

1. **Đọc `PROPERTY.md`:** Nắm toàn bộ tính chất hiện có của dự án và xác định số `n` mới nhất ở cuối file.
2. **Lập phạm vi thay đổi:** Bảo toàn 100% các phần không nằm trong yêu cầu sửa đổi; linh hoạt tái cấu trúc nếu người dùng yêu cầu.
3. **Triển khai mã nguồn đầy đủ 100%:** Viết trọn vẹn từng file, chú thích tiếng Việt chi tiết ở mọi bước quan trọng, không có `// TODO` hay code rút gọn.
4. **Kiểm tra an toàn & 60 FPS:** Rà soát không rò rỉ VRAM/Off-heap/Thread, không tạo rác GC trong Game Loop, đảm bảo biên dịch sạch trên Java 17 (UTF-8)[cite: 1].
5. **Cập nhật `PROPERTY.md` (2 bước):**
   - Cập nhật lại phần trên của `PROPERTY.md` khớp 100% với dự án sau khi sửa.
   - Nối tiếp mục `lần chỉnh sửa thứ (n+1):` ở cuối `PROPERTY.md`, ghi rõ **Trước khi chỉnh sửa** và **Sau khi chỉnh sửa** đối với mọi phần có xóa/thêm/thay đổi cùng toàn bộ lưu ý cần thiết.