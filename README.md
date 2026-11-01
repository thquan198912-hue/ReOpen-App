# Game Watchdog

Ứng dụng Android theo dõi một game. Khi game bị thoát hoặc văng, app tự mở lại game, chờ game tải xong, rồi chạm lần lượt các điểm bạn đã cài đặt.

## Cách hoạt động
- Dịch vụ trợ năng (AccessibilityService) nghe sự kiện đổi cửa sổ để biết app nào đang ở trên cùng.
- Nếu game không còn ở trên cùng quá N giây → mở lại bằng launch intent.
- Sau thời gian chờ tải, chạm các điểm theo `dispatchGesture`.
- Toạ độ lưu theo % màn hình nên không phụ thuộc độ phân giải.

## Cài đặt trên điện thoại
1. Cài APK (cho phép cài từ nguồn ngoài).
2. Mở app → bấm **Bật dịch vụ trợ năng** → bật *Game Watchdog*.
   - Android 13+ nếu nút bị mờ: **Mở thông tin ứng dụng** → ⋮ → *Cho phép cài đặt bị hạn chế*, rồi bật lại.
3. **Chọn game**, nhập các điểm chạm, bấm **Lưu**.
4. Bấm **Thử ngay** để kiểm tra, sau đó bật công tắc **Bật giám sát**.
5. Bấm **Tắt tối ưu pin** để hệ thống không dừng dịch vụ.

## Định dạng điểm chạm
Mỗi dòng: `x,y,chờ_ms`
```
# chờ 3 giây rồi chạm nút "Bắt đầu"
50,80,3000
# chờ 1.5 giây rồi chạm nút "Đóng quảng cáo"
92,8,1500
```
Mẹo lấy toạ độ: bật *Tuỳ chọn nhà phát triển → Hiện vị trí con trỏ*, chạm vào điểm cần chạm, lấy X/Y chia cho chiều rộng/cao màn hình × 100.

## Build APK
Đẩy code lên GitHub nhánh `main`. Workflow `.github/workflows/build.yml` tự build và đưa `app-debug.apk` vào:
- **Releases → Bản build mới nhất** (tải thẳng trên điện thoại)
- hoặc tab **Actions → artifact `GameWatchdog-apk`**

## Giới hạn
- Không phân biệt được game *văng* hay người dùng *chủ động thoát* — khi bật giám sát, rời game quá N giây là bị mở lại. Tắt công tắc khi muốn dùng máy bình thường.
- Game có anti-cheat có thể phát hiện dịch vụ trợ năng/auto-click. Hãy đọc điều khoản của game và thử trên tài khoản phụ.
- Chạm theo tọa độ cố định có thể lệch nếu game đổi giao diện hoặc tải chậm hơn thời gian đã đặt.
