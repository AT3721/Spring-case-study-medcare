# Tổng quan và đánh giá dự án MedCare

## 1. Mục đích và công nghệ

MedCare là ứng dụng web đặt và quản lý lịch khám. Dự án dùng Java 21, Spring Boot 3.2, Spring MVC, Thymeleaf, Spring Security, Spring Data JPA và MySQL. Giao diện được render từ các template Thymeleaf; thao tác tra cứu và hủy lịch hẹn dùng AJAX.

## 2. Tính năng hiện tại

- Liệt kê bác sĩ theo trang, tìm theo tên, lọc theo chuyên khoa và sắp xếp theo tên/phí khám.
- Lọc chuyên khoa, tìm kiếm và chuyển trang trong danh sách bác sĩ tải kết quả bất đồng bộ, không làm tải lại toàn trang; URL và lịch sử điều hướng trình duyệt vẫn được cập nhật.
- Trang chủ có banner giới thiệu phòng khám MedCare và liên kết nhanh tới danh sách bác sĩ.
- Các trang dùng chung header điều hướng cố định khi cuộn và footer nhất quán, gồm danh sách/chi tiết bác sĩ, đặt lịch và portal bệnh nhân.
- Trong form đặt lịch, tải tức thời các mốc giờ bắt đầu còn trống, cách nhau 30 phút trong giờ hành chính (08:00–12:00, 13:00–17:00); server kiểm tra lại trước khi lưu.
- Chỉ bệnh nhân đăng nhập mới được đặt lịch; người chưa đăng nhập được chuyển tới trang đăng nhập trước khi đặt. Sau đăng nhập, hệ thống quay lại trang đặt lịch đã chọn.
- Đặt lịch khám với bác sĩ, ngày, ca khám, số điện thoại và ghi chú; họ tên/email lấy từ tài khoản.
- Validate trường bắt buộc, định dạng số điện thoại/email và yêu cầu ngày khám nằm trong tương lai.
- Kiểm tra ca khám đã được đặt trước khi lưu; tạo mã đặt lịch để tra cứu.
- Tra cứu lịch hẹn theo mã; hiển thị bệnh nhân, bác sĩ, chuyên khoa, ngày, ca và trạng thái.
- Hủy lịch hẹn nếu lịch chưa bị hủy và chưa hoàn thành.
- Người bệnh đăng ký/đăng nhập bằng email và mật khẩu; mật khẩu được mã hóa BCrypt. Google OAuth chưa được bật.
- Tính năng Quên mật khẩu: cho phép người dùng nhập email/username để nhận liên kết đặt lại mật khẩu. Token bảo mật có thời hạn hiệu lực chính xác 15 phút, được lưu trong bảng `password_reset_tokens` và đánh dấu đã sử dụng ngay sau khi cập nhật mật khẩu mới thành công.
- Trang danh sách bác sĩ (trang chủ) có trợ lý chat hướng dẫn theo dữ liệu bác sĩ/chuyên khoa/khung giờ hiện có, gợi ý chuyên khoa tham khảo, hướng dẫn đặt lịch và nhắc cấp cứu khi nhận diện dấu hiệu nguy hiểm. Phiên bản hiện tại dùng luật/FAQ cục bộ, không gọi AI bên ngoài và không lưu nội dung chat; không chẩn đoán hay kê đơn.
- Banner trang chủ giới thiệu các chuyên khoa đang hoạt động, mô tả và danh sách bác sĩ từng khoa; chọn bác sĩ mở hồ sơ, chọn “Xem bác sĩ chuyên khoa” lọc đội ngũ tương ứng ngay tại danh sách mà không tải lại toàn trang.
- Trang “Lịch của tôi” hiển thị mã đặt lịch, phí khám, trạng thái thanh toán và kết quả sau khi hoàn thành; cung cấp nút thanh toán trực tuyến qua VNPay cho các lịch chưa thanh toán.
- Tích hợp cổng thanh toán trực tuyến VNPay Sandbox: hỗ trợ chọn thanh toán tại quầy hoặc thanh toán ngay qua thẻ ATM nội địa/QR Pay; tự động chuyển lịch sang `CONFIRMED` khi thanh toán thành công và lưu vết giao dịch trong `payment_transactions`.
- Bệnh nhân có thể chỉnh sửa bác sĩ/ngày/giờ hoặc hủy lịch tương lai đang chờ/xác nhận; lịch hủy vẫn được lưu trong lịch sử, kết quả khám không bị xóa.
- Phân quyền chuyên biệt: Bác sĩ (`ROLE_DOCTOR`) là người duy nhất ghi nhận kết quả khám và kê đơn thuốc thông qua Cổng Bác Sĩ (`/doctor/appointments`), Admin và Staff chỉ quản lý danh mục bác sĩ, chuyên khoa và theo dõi lịch hẹn bệnh nhân.
- Khởi tạo tài khoản tự động cho toàn bộ đội ngũ bác sĩ: Hệ thống tự động cấp tài khoản đăng nhập (`doctor1`, `doctor2`,... với mật khẩu `123456`) liên kết chính xác với từng bác sĩ đang hoạt động để bác sĩ vào tiếp nhận bệnh nhân.
- Xóa bỏ dữ liệu mẫu lịch hẹn cũ: Bảng lịch hẹn (`appointments`) được làm sạch để người dùng và bác sĩ trải nghiệm luồng khám bệnh thực tế.
- Luồng khám bệnh và phiếu khám khép kín:
  + Người dùng đặt lịch và thanh toán thành công -> Lịch hẹn chuyển sang trạng thái đã thanh toán (`PAID`), được xác nhận (`CONFIRMED`) và sinh Phiếu khám bệnh có Mã QR Check-in.
  + Bác sĩ đăng nhập cổng bác sĩ, mở lịch khám của bệnh nhân, tiến hành khám -> Nhập kết luận chẩn đoán (Phiếu khám bệnh) và kê đơn thuốc (tên thuốc, hàm lượng, số lượng, cách dùng, lời dặn, ngày tái khám) -> Đánh dấu hoàn thành buổi khám (`COMPLETED`).
  + Toàn bộ kết quả chẩn đoán và đơn thuốc đã kê được lưu vĩnh viễn trong tài khoản người bệnh (`/patient/appointments`) để người bệnh tra cứu bất cứ lúc nào.
- Tính năng Đơn thuốc (Prescription): Quản lý bảng `prescriptions` gồm tên thuốc, hàm lượng, số lượng, cách dùng, lời dặn bác sĩ, ngày tái khám. Bác sĩ có thể thêm/xóa thuốc trong đơn; bệnh nhân xem chi tiết đơn thuốc ngay dưới kết quả khám trong trang "Lịch của tôi".
- Đăng nhập nhanh bằng Google (Google OAuth2 Client): Tích hợp `spring-boot-starter-oauth2-client`, nút đăng nhập Google nổi bật tại trang đăng nhập, tự động liên kết hoặc khởi tạo tài khoản bệnh nhân (`ROLE_PATIENT`) an toàn.
- Tính năng 2 Luồng Đặt Lịch (2 Entry Points):
  + **Luồng 1 (Đặt từ card bác sĩ trang chủ/danh sách bác sĩ)**: Click "Đặt lịch" tại card bác sĩ sẽ tự động gắn cứng thông tin Bác sĩ & Chuyên khoa, hiển thị banner thông tin bác sĩ. Người dùng chọn ngày khám thì hệ thống chỉ hiển thị các ca còn trống của riêng bác sĩ đó (`GET /api/appointments/available-slots`).
  + **Luồng 2 (Đặt từ nút "Đăng ký khám" trên thanh menu)**: Quy trình 3 bước tuần tự (Step 1: Chọn chuyên khoa -> Step 2: Chọn ngày và khung giờ khám mong muốn -> Step 3: Hệ thống tự động truy vấn và hiển thị danh sách các bác sĩ còn ca trống trong khung giờ đó qua API `GET /api/appointments/available-doctors` để người dùng chọn).
- Thanh toán & Xác nhận:
  + Bắt buộc thanh toán chuyển khoản trực tuyến (bỏ hoàn toàn hình thức tiền mặt tại quầy). Sau khi điền form đặt lịch, hệ thống tự động chuyển hướng ngay sang cổng thanh toán VNPay Sandbox.
  + Sau khi thanh toán thành công, hệ thống tự động sinh mã QR Check-in (`ZXing QR Base64`) nhúng vào email thông báo và gửi ngay tới email người bệnh (`sendAppointmentConfirmationEmail`).
  + Lịch hẹn đã thanh toán (`paymentStatus == PAID`) được bảo vệ nghiêm ngặt: vô hiệu hóa (disable/ẩn) nút Hủy trên giao diện Portal bệnh nhân và Tra cứu, đồng thời chặn triệt để tại Service và API `POST /api/appointments/cancel` kèm thông báo lỗi cụ thể.
- Trạng thái lịch hẹn gồm `PENDING`, `CONFIRMED`, `CANCELLED` và `COMPLETED`. Người bệnh có portal xem lịch và đơn thuốc gắn tài khoản.

## 3. Cấu trúc và luồng xử lý

- `controller`: nhận HTTP request, nạp dữ liệu giao diện và chuyển tiếp đến service.
- `service`: xử lý nghiệp vụ đặt/tra cứu/cập nhật lịch hẹn và truy vấn bác sĩ.
- `repository`: Spring Data JPA truy cập các bảng.
- `model`: các entity `Appointment`, `Doctor`, `DoctorSchedule`, `Specialty`, `TimeSlot`, `User`, `Role`, `Prescription`, `PaymentTransaction`.
- `dto`: `AppointmentDTO` làm model form và khai báo validation.
- `security`: `CustomUserDetailsService` nạp tài khoản và role từ database; `SecurityConfig` cấu hình đăng nhập và quyền truy cập.
- `resources/templates`: các trang danh sách bác sĩ, đặt lịch, tra cứu, thành công, đăng nhập, portal bác sĩ và quản trị.
- `schema.sql` / `data.sql`: DDL và dữ liệu khởi tạo.

### Luồng đặt lịch (2 Entry Points)

1. **Luồng 1 (Card Bác Sĩ)**:
   - Bệnh nhân bấm "Đặt lịch" tại thẻ bác sĩ -> `GET /appointments/book?doctorId={id}`.
   - Giao diện khóa cố định bác sĩ, chọn ngày khám gọi API `GET /api/appointments/available-slots?doctorId={id}&date={date}` để hiển thị các ca trống của bác sĩ.
2. **Luồng 2 (Menu Đăng Ký Khám)**:
   - Bệnh nhân bấm "Đăng ký khám" trên Header Menu -> `GET /appointments/book`.
   - **Bước 1**: Chọn Chuyên khoa.
   - **Bước 2**: Chọn Ngày khám và Khung giờ mong muốn (`GET /api/appointments/all-slots`).
   - **Bước 3**: Giao diện tự động gọi `GET /api/appointments/available-doctors?specialtyId={id}&date={date}&timeSlotId={slotId}` và hiển thị danh sách các bác sĩ còn ca trống trong khung giờ để người dùng tích chọn.
3. **Thanh toán & Chặn Hủy Lịch**:
   - Khi gửi form `POST /appointments/book`, hình thức thanh toán bắt buộc là `VNPAY`.
   - Hệ thống tự động chuyển hướng sang trang thanh toán `/payment/pay/{bookingCode}`.
   - Sau khi thanh toán thành công, hệ thống sinh mã QR check-in và gửi email xác nhận.
   - Lịch hẹn đã thanh toán (`paymentStatus == PAID`) bị cấm hủy trên giao diện lẫn API.

### Luồng tài khoản bệnh nhân và kết quả khám

1. Bệnh nhân đăng ký tại `/register` với họ tên, email và mật khẩu; email được chuẩn hóa, mật khẩu mã hóa BCrypt và tài khoản nhận role `ROLE_PATIENT`.
2. Truy cập trang đặt lịch yêu cầu đăng nhập bệnh nhân; sau xác thực quay lại đúng trang đặt lịch. Đăng nhập chủ động chuyển tới `/patient/appointments`; trang này chỉ dành cho `ROLE_PATIENT`.
3. Tên/email khi lưu lịch lấy từ user đã xác thực; lịch được gắn user ID đó. Không có luồng đặt lịch ẩn danh.
4. ADMIN cập nhật lịch sang `COMPLETED`; ADMIN/STAFF có thể nhập kết quả. Kết quả chỉ được hiển thị trong trang lịch của chính tài khoản bệnh nhân đã đặt.

### Luồng tra cứu và hủy lịch

1. Mở `GET /appointments/search`, nhập mã đặt lịch.
2. Giao diện gửi `GET /api/appointments/search?bookingCode=...`; API chuẩn hóa mã, tìm lịch và trả thông tin hẹn.
3. Nếu người dùng xác nhận hủy, giao diện gửi `POST /api/appointments/cancel`.
4. API từ chối nếu không tìm thấy, đã hủy hoặc đã hoàn thành; nếu không thì đổi trạng thái sang `CANCELLED`.

### Luồng quản trị

1. Chưa đăng nhập, truy cập `/admin/appointments` sẽ được yêu cầu đăng nhập tại `/login`.
2. ADMIN và STAFF có thể xem danh sách, lọc và phân trang lịch hẹn; STAFF chỉ có quyền đọc.
3. Chỉ ADMIN được POST `/admin/appointments/update-status` để cập nhật trạng thái; giao diện ẩn thao tác này đối với STAFF.
4. Các route quản trị khác chỉ dành cho ADMIN. POST `/logout` kết thúc session và chuyển về trang đăng nhập.
5. ADMIN mở lịch làm việc từ danh sách bác sĩ, chọn ca sáng và/hoặc ca chiều cho từng ngày trong lịch tuần. Lịch ngày cụ thể (nếu có) ghi đè lịch tuần; hệ thống ánh xạ ca đã chọn thành các khung giờ 30 phút cho luồng đặt lịch. Bác sĩ chưa được cấu hình ca sẽ không có khung giờ trống và chỉnh sửa lịch không xóa các cuộc hẹn đã lưu.

## 4. Các endpoint đang có

| Method | Đường dẫn | Mục đích |
|---|---|---|
| GET | `/doctors` | Danh sách bác sĩ, lọc chuyên khoa và phân trang |
| GET | `/doctors/{id}` | Mở chi tiết bác sĩ |
| GET, POST | `/appointments/book` | Bệnh nhân đăng nhập đặt lịch và lưu lịch vào tài khoản |
| GET | `/appointments/success` | Hiện kết quả đặt lịch |
| GET | `/appointments/search` | Hiện trang tra cứu |
| GET | `/api/appointments/search` | Tra cứu lịch theo booking code |
| GET | `/api/appointments/available-slots` | Lấy ca khám còn trống theo bác sĩ/ngày |
| POST | `/api/assistant/chat` | Trợ lý hướng dẫn MedCare, không lưu tin nhắn |
| POST | `/api/appointments/cancel` | Hủy lịch theo booking code |
| GET | `/login` | Hiện trang đăng nhập |
| GET, POST | `/register` | Đăng ký tài khoản bệnh nhân bằng email/mật khẩu |
| GET | `/patient/appointments` | Lịch sử và kết quả khám riêng của bệnh nhân đã đăng nhập |
| GET | `/admin/appointments` | Danh sách quản trị, lọc và phân trang |
| POST | `/admin/appointments/update-status` | Cập nhật trạng thái lịch |
| POST | `/admin/appointments/update-result` | ADMIN/STAFF lưu kết quả lịch đã hoàn thành |
| GET | `/admin/catalog/doctors` | Quản lý bác sĩ và tài khoản |
| GET | `/admin/catalog/doctors/new`, `/admin/catalog/doctors/{id}/edit` | Mở form thêm/sửa bác sĩ |
| POST | `/admin/catalog/doctors/save`, `/admin/catalog/doctors/{id}/toggle` | Lưu hoặc ẩn/hiện bác sĩ |
| GET, POST | `/admin/catalog/doctors/{id}/schedule` | Xem và lưu lịch làm việc tuần cho bác sĩ (ADMIN) |
| GET, POST | `/admin/catalog/specialties` và `/admin/catalog/specialties/save` | Liệt kê, thêm/sửa chuyên khoa |
| POST | `/admin/catalog/specialties/{id}/toggle` | Ẩn/hiện chuyên khoa |
| GET | `/payment/pay/{bookingCode}` | Tạo URL và chuyển hướng bệnh nhân tới cổng VNPay Sandbox |
| GET | `/payment/vnpay-return` | Xác thực checksum HMAC-SHA512 và cập nhật giao dịch/lịch hẹn từ callback VNPay |
| GET | `/payment/result` | Trang biên nhận kết quả thanh toán |
| GET, POST | `/forgot-password` | Trang yêu cầu gửi liên kết đặt lại mật khẩu |
| GET, POST | `/reset-password` | Xác thực token 15 phút và đặt lại mật khẩu mới |
| POST | `/logout` | Đăng xuất |

## 5. Các lỗi đã khắc phục và điểm còn lại

- SQL initialization được bật; schema dùng `CREATE TABLE IF NOT EXISTS` và không còn xóa bảng khi khởi động.
- Lịch `CANCELLED` không còn chiếm ca; đặt lịch khóa dòng bác sĩ trong transaction để tuần tự hóa các booking đồng thời. Ứng dụng tự gỡ index cũ `uq_doctor_date_slot` khi khởi động.
- Mã lịch mới có 80 bit entropy; tra cứu và hủy yêu cầu cả mã lịch lẫn số điện thoại đã đăng ký.
- CSRF được bật lại; request AJAX hủy gửi token bảo vệ. STAFF xem `/admin/appointments` và chỉ được lưu kết quả khám cho lịch đã hoàn thành; ADMIN có quyền quản trị lịch và danh mục.
- Tài khoản bootstrap không còn mật khẩu mặc định trong source; có thể tạo/cập nhật bằng `MEDCARE_ADMIN_PASSWORD` và `MEDCARE_STAFF_PASSWORD`.
- Đã bổ sung trang chi tiết bác sĩ, chuyển hướng `/` về `/doctors`, và giới hạn các bước chuyển trạng thái lịch.
- Dữ liệu mẫu lịch hẹn có mã ngắn và thông tin cá nhân đã được bỏ khỏi `data.sql`.
- Sửa regex số điện thoại để chỉ nhận số di động Việt Nam 10 chữ số đúng định dạng; lựa chọn bác sĩ/ca khám không tồn tại được trả lỗi validation trên form.
- Thêm API ca còn trống theo bác sĩ/ngày và lọc trạng thái hiển thị cho hồ sơ bác sĩ/chuyên khoa.
- Tài khoản được tạo từ hồ sơ bác sĩ dùng role `ROLE_STAFF`, cùng quyền xem lịch như nhân viên; không có role hoặc dashboard bác sĩ riêng.
- Danh sách lịch hẹn có sắp xếp theo tên bác sĩ A–Z/Z–A để bác sĩ dùng tài khoản STAFF tìm lịch của mình; migration đưa tài khoản cũ `ROLE_DOCTOR` về `ROLE_STAFF` và xóa role cũ.
- Bộ test dọn các bản ghi giả được tạo bởi test portal trước đây để chúng không xuất hiện trong danh sách bác sĩ/lịch hẹn.
- Đặt lịch yêu cầu `ROLE_PATIENT`; ADMIN/STAFF không thể tạo lịch với tư cách bệnh nhân. Các liên kết tra cứu lịch đã được bỏ khỏi trang chủ và header/footer dùng chung.

**Còn lại:** Google OAuth và xác minh email chưa được cấu hình; tài khoản đăng ký hiện có thể đăng nhập ngay sau khi tạo. Các luồng trình duyệt đầu-cuối chưa có test tự động. Nếu database đang có tài khoản quản trị cũ, cần đặt lại mật khẩu bằng các biến môi trường trên; ứng dụng không tự đoán hay xóa thông tin đăng nhập cũ.

## 6. Cài đặt và vận hành

- Yêu cầu Java 21 và MySQL.
- Cấu hình kết nối database nằm trong `src/main/resources/application.properties`; không đưa thông tin đăng nhập thật vào source control. Nên lấy credentials từ biến môi trường/secret manager.
- Ứng dụng tự tạo schema và nạp dữ liệu chuyên khoa/bác sĩ/ca khám khi khởi động; migration bổ sung cột một cách an toàn mà không xóa dữ liệu hiện có.
- Chạy ứng dụng bằng `gradlew.bat bootRun`; mặc định server lắng nghe cổng 8080.
- Đặt `MEDCARE_ADMIN_PASSWORD` và/hoặc `MEDCARE_STAFF_PASSWORD` trước khi khởi động để tạo hoặc đặt lại mật khẩu tài khoản tương ứng. Nếu không đặt các biến này, ứng dụng không tạo tài khoản đăng nhập mặc định.

## 7. Kiểm tra đã thực hiện

`gradlew.bat test` chạy thành công (66/66 test), gồm đăng ký bệnh nhân, quên/đặt lại mật khẩu với token 15 phút (kiểm tra hạn dùng, token đã dùng, mật khẩu khớp, mã hóa BCrypt), truy cập portal, hiển thị mã/kết quả theo tài khoản, liên kết lịch khi đặt, quyền ADMIN/STAFF, lưu kết quả sau khi hoàn thành, sinh URL thanh toán VNPay có ký số HMAC-SHA512, xác thực checksum callback và cập nhật trạng thái thanh toán tự động.
