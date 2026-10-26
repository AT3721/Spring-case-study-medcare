# Tổng quan và đánh giá dự án MedCare

## 1. Mục đích và công nghệ

MedCare là ứng dụng web đặt và quản lý lịch khám. Dự án dùng Java 21, Spring Boot 3.2, Spring MVC, Thymeleaf, Spring Security, Spring Data JPA và MySQL. Giao diện được render từ các template Thymeleaf; thao tác tra cứu và hủy lịch hẹn dùng AJAX.

## 2. Tính năng hiện tại

- Liệt kê bác sĩ theo trang, tìm theo tên, lọc theo chuyên khoa và sắp xếp theo tên/phí khám.
- Trong form đặt lịch, tải tức thời các mốc giờ bắt đầu còn trống, cách nhau 30 phút trong giờ hành chính (08:00–12:00, 13:00–17:00); server kiểm tra lại trước khi lưu.
- Đặt lịch khám với bác sĩ, ngày, ca khám, tên, số điện thoại, email tùy chọn và ghi chú.
- Validate trường bắt buộc, định dạng số điện thoại/email và yêu cầu ngày khám nằm trong tương lai.
- Kiểm tra ca khám đã được đặt trước khi lưu; tạo mã đặt lịch để tra cứu.
- Tra cứu lịch hẹn theo mã; hiển thị bệnh nhân, bác sĩ, chuyên khoa, ngày, ca và trạng thái.
- Hủy lịch hẹn nếu lịch chưa bị hủy và chưa hoàn thành.
- Đăng nhập bằng tài khoản trong database; ADMIN được cập nhật lịch, STAFF chỉ xem danh sách lịch quản trị.
- Quản trị xem danh sách lịch hẹn, tìm theo tên bác sĩ, lọc theo trạng thái/ngày, phân trang, sắp xếp theo tên bác sĩ và cập nhật trạng thái (ADMIN).
- ADMIN thêm/sửa/ẩn bác sĩ và chuyên khoa trên giao diện; có thể tạo tài khoản bác sĩ liên kết trực tiếp với hồ sơ.
- ADMIN cấu hình lịch tuần cho từng bác sĩ bằng cách chọn ca theo từng ngày; ca trống và thao tác đặt lịch đều được kiểm tra theo lịch đã lưu.
- Tài khoản bác sĩ được ADMIN cấp dùng role STAFF như tài khoản nhân viên, có quyền xem danh sách lịch và sắp xếp theo tên bác sĩ.
- Khởi tạo vai trò khi ứng dụng chạy; tài khoản quản trị/lễ tân chỉ được tạo hoặc đặt lại mật khẩu nếu có cấu hình biến môi trường tương ứng. `data.sql` nạp dữ liệu demo gồm 4 chuyên khoa, 16 bác sĩ và các ca khám; không chứa lịch mẫu có dữ liệu cá nhân.

Trạng thái lịch hẹn gồm `PENDING`, `CONFIRMED`, `CANCELLED` và `COMPLETED`. Chưa có đăng ký/portal bệnh nhân hoặc quản lý ca khám từ giao diện.

## 3. Cấu trúc và luồng xử lý

- `controller`: nhận HTTP request, nạp dữ liệu giao diện và chuyển tiếp đến service.
- `service`: xử lý nghiệp vụ đặt/tra cứu/cập nhật lịch hẹn và truy vấn bác sĩ.
- `repository`: Spring Data JPA truy cập các bảng.
- `model`: các entity `Appointment`, `Doctor`, `DoctorSchedule`, `Specialty`, `TimeSlot`, `User`, `Role`.
- `dto`: `AppointmentDTO` làm model form và khai báo validation.
- `security`: `CustomUserDetailsService` nạp tài khoản và role từ database; `SecurityConfig` cấu hình đăng nhập và quyền truy cập.
- `resources/templates`: các trang danh sách bác sĩ, đặt lịch, tra cứu, thành công, đăng nhập và quản trị.
- `schema.sql` / `data.sql`: DDL và dữ liệu khởi tạo; các thay đổi cột cho tài khoản bác sĩ và trạng thái hiển thị được migration idempotent xử lý khi ứng dụng khởi động.

### Luồng đặt lịch

1. Bệnh nhân vào `GET /doctors`, có thể lọc theo `specialtyId` và chuyển trang.
2. Chọn bác sĩ để mở `GET /appointments/book?doctorId=...`; chọn ngày khám để giao diện gọi `GET /api/appointments/available-slots` và hiển thị ca bác sĩ đã đăng ký làm, còn trống.
3. Gửi form tới `POST /appointments/book`. DTO được validate; service kiểm tra bác sĩ/ca khám tồn tại, lịch làm việc theo thứ và ca đã bị đặt trong transaction.
4. Nếu hợp lệ, service tạo `Appointment` trạng thái `PENDING`, sinh mã đặt lịch và lưu database.
5. Chuyển hướng tới `GET /appointments/success` và hiện mã vừa tạo.

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
5. ADMIN mở lịch làm việc từ danh sách bác sĩ, chọn các ca 30 phút cho từng ngày và lưu. Bác sĩ chưa được cấu hình lịch sẽ không có ca trống để bệnh nhân đặt; chỉnh sửa lịch không xóa các cuộc hẹn đã lưu.

## 4. Các endpoint đang có

| Method | Đường dẫn | Mục đích |
|---|---|---|
| GET | `/doctors` | Danh sách bác sĩ, lọc chuyên khoa và phân trang |
| GET | `/doctors/{id}` | Mở chi tiết bác sĩ |
| GET, POST | `/appointments/book` | Hiện form và xử lý đặt lịch |
| GET | `/appointments/success` | Hiện kết quả đặt lịch |
| GET | `/appointments/search` | Hiện trang tra cứu |
| GET | `/api/appointments/search` | Tra cứu lịch theo booking code |
| GET | `/api/appointments/available-slots` | Lấy ca khám còn trống theo bác sĩ/ngày |
| POST | `/api/appointments/cancel` | Hủy lịch theo booking code |
| GET | `/login` | Hiện trang đăng nhập |
| GET | `/admin/appointments` | Danh sách quản trị, lọc và phân trang |
| POST | `/admin/appointments/update-status` | Cập nhật trạng thái lịch |
| GET | `/admin/catalog/doctors` | Quản lý bác sĩ và tài khoản |
| GET | `/admin/catalog/doctors/new`, `/admin/catalog/doctors/{id}/edit` | Mở form thêm/sửa bác sĩ |
| POST | `/admin/catalog/doctors/save`, `/admin/catalog/doctors/{id}/toggle` | Lưu hoặc ẩn/hiện bác sĩ |
| GET, POST | `/admin/catalog/doctors/{id}/schedule` | Xem và lưu lịch làm việc tuần cho bác sĩ (ADMIN) |
| GET, POST | `/admin/catalog/specialties` và `/admin/catalog/specialties/save` | Liệt kê, thêm/sửa chuyên khoa |
| POST | `/admin/catalog/specialties/{id}/toggle` | Ẩn/hiện chuyên khoa |
| POST | `/logout` | Đăng xuất |

## 5. Các lỗi đã khắc phục và điểm còn lại

- SQL initialization được bật; schema dùng `CREATE TABLE IF NOT EXISTS` và không còn xóa bảng khi khởi động.
- Lịch `CANCELLED` không còn chiếm ca; đặt lịch khóa dòng bác sĩ trong transaction để tuần tự hóa các booking đồng thời. Ứng dụng tự gỡ index cũ `uq_doctor_date_slot` khi khởi động.
- Mã lịch mới có 80 bit entropy; tra cứu và hủy yêu cầu cả mã lịch lẫn số điện thoại đã đăng ký.
- CSRF được bật lại; request AJAX hủy gửi token bảo vệ. STAFF chỉ đọc `/admin/appointments`, ADMIN được phép cập nhật và truy cập các route `/admin/**` khác.
- Tài khoản bootstrap không còn mật khẩu mặc định trong source; có thể tạo/cập nhật bằng `MEDCARE_ADMIN_PASSWORD` và `MEDCARE_STAFF_PASSWORD`.
- Đã bổ sung trang chi tiết bác sĩ, chuyển hướng `/` về `/doctors`, và giới hạn các bước chuyển trạng thái lịch.
- Dữ liệu mẫu lịch hẹn có mã ngắn và thông tin cá nhân đã được bỏ khỏi `data.sql`.
- Sửa regex số điện thoại để chỉ nhận số di động Việt Nam 10 chữ số đúng định dạng; lựa chọn bác sĩ/ca khám không tồn tại được trả lỗi validation trên form.
- Thêm API ca còn trống theo bác sĩ/ngày và lọc trạng thái hiển thị cho hồ sơ bác sĩ/chuyên khoa.
- Tài khoản được tạo từ hồ sơ bác sĩ dùng role `ROLE_STAFF`, cùng quyền xem lịch như nhân viên; không có role hoặc dashboard bác sĩ riêng.
- Danh sách lịch hẹn có sắp xếp theo tên bác sĩ A–Z/Z–A để bác sĩ dùng tài khoản STAFF tìm lịch của mình; migration đưa tài khoản cũ `ROLE_DOCTOR` về `ROLE_STAFF` và xóa role cũ.
- Bộ test dọn các bản ghi giả được tạo bởi test portal trước đây để chúng không xuất hiện trong danh sách bác sĩ/lịch hẹn.
- Role `ROLE_PATIENT` hiện chỉ là enum/dữ liệu; chưa có đăng ký/portal bệnh nhân. Khách không đăng nhập vẫn có thể dùng các chức năng công khai; có mã lịch và số điện thoại mới xem/hủy được một lịch.

**Còn lại:** đã bổ sung test cho đặt ca, mã đặt lịch, chuyển trạng thái, xác minh số điện thoại, CSRF và quyền truy cập trang quản trị. Các luồng trình duyệt đầu-cuối chưa có test tự động. Nếu database đang có tài khoản quản trị cũ, cần đặt lại mật khẩu bằng các biến môi trường trên; ứng dụng không tự đoán hay xóa thông tin đăng nhập cũ.

## 6. Cài đặt và vận hành

- Yêu cầu Java 21 và MySQL.
- Cấu hình kết nối database nằm trong `src/main/resources/application.properties`; không đưa thông tin đăng nhập thật vào source control. Nên lấy credentials từ biến môi trường/secret manager.
- Ứng dụng tự tạo schema và nạp dữ liệu chuyên khoa/bác sĩ/ca khám khi khởi động; migration bổ sung cột một cách an toàn mà không xóa dữ liệu hiện có.
- Chạy ứng dụng bằng `gradlew.bat bootRun`; mặc định server lắng nghe cổng 8080.
- Đặt `MEDCARE_ADMIN_PASSWORD` và/hoặc `MEDCARE_STAFF_PASSWORD` trước khi khởi động để tạo hoặc đặt lại mật khẩu tài khoản tương ứng. Nếu không đặt các biến này, ứng dụng không tạo tài khoản đăng nhập mặc định.

## 7. Kiểm tra đã thực hiện

`gradlew.bat test` chạy thành công, gồm test Spring context, ca trống, tạo tài khoản/hồ sơ bác sĩ với role STAFF, sắp xếp danh sách lịch, cập nhật trạng thái và bảo mật MockMvc.
