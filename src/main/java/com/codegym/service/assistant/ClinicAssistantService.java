package com.codegym.service.assistant;

import com.codegym.dto.assistant.AssistantChatResponse;
import com.codegym.model.Doctor;
import com.codegym.model.Specialty;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.ISpecialtyRepository;
import com.codegym.service.appointment.IAppointmentService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ClinicAssistantService {
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern LOCAL_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");

    private final IDoctorRepository doctorRepository;
    private final ISpecialtyRepository specialtyRepository;
    private final IAppointmentService appointmentService;

    public ClinicAssistantService(IDoctorRepository doctorRepository,
                                  ISpecialtyRepository specialtyRepository,
                                  IAppointmentService appointmentService) {
        this.doctorRepository = doctorRepository;
        this.specialtyRepository = specialtyRepository;
        this.appointmentService = appointmentService;
    }

    @Transactional(readOnly = true)
    public AssistantChatResponse reply(String message) {
        String normalized = normalize(message);
        if (isUrgent(normalized)) {
            return response("Các dấu hiệu bạn mô tả có thể cần được xử lý khẩn cấp. Trợ lý không thể đánh giá tình trạng qua chat; hãy gọi 115 hoặc đến cơ sở cấp cứu gần nhất ngay. Đừng chờ phản hồi trực tuyến.", List.of(
                    new AssistantChatResponse.AssistantLink("Gọi 115", "tel:115")));
        }

        if (isGreeting(normalized)) {
            return response("Xin chào! Tôi là Trợ lý MedCare, có thể giúp tìm chuyên khoa/bác sĩ, xem khung giờ còn trống và hướng dẫn đặt lịch. Tôi chỉ cung cấp thông tin tham khảo, không chẩn đoán hay kê đơn. Bạn không nên gửi thông tin định danh hoặc hồ sơ khám trong chat.", List.of(
                    link("Tìm bác sĩ", "/doctors"),
                    link("Cách đặt lịch", "/appointments/book"),
                    link("Đăng ký tài khoản", "/register")));
        }

        LocalDate requestedDate = parseDate(message);
        Specialty specialty = findSpecialty(normalized);
        if (requestedDate != null) {
            return findAvailability(requestedDate, specialty, normalized);
        }
        if (specialty != null) {
            return recommendDoctors(specialty);
        }

        if (containsAny(normalized, "dat lich", "hen kham", "booking", "dat hen")) {
            return response("Để đặt lịch, hãy đăng nhập hoặc tạo tài khoản bệnh nhân, chọn bác sĩ, ngày và khung giờ còn trống rồi xác nhận. Lịch đã đặt sẽ được lưu trong mục “Lịch của tôi”.", List.of(
                    link("Đăng nhập", "/login"),
                    link("Tạo tài khoản", "/register"),
                    link("Xem bác sĩ", "/doctors")));
        }
        if (containsAny(normalized, "lich cua toi", "lich da dat", "xem lich", "ket qua kham")) {
            return response("Bạn có thể xem mã đặt lịch và kết quả khám trong “Lịch của tôi” sau khi đăng nhập tài khoản bệnh nhân đã dùng để đặt lịch.", List.of(
                    link("Đăng nhập", "/login"),
                    link("Lịch của tôi", "/patient/appointments")));
        }
        if (containsAny(normalized, "phi", "gia kham", "chi phi")) {
            return response("Phí khám tham khảo được hiển thị trên hồ sơ từng bác sĩ và có thể khác nhau theo bác sĩ. Hãy xem danh sách để so sánh trước khi đặt lịch.", List.of(
                    link("Xem bác sĩ và phí khám", "/doctors")));
        }
        if (containsAny(normalized, "gio lam", "gio mo cua", "khung gio", "gio kham", "con trong")) {
            return response("Khung giờ còn trống phụ thuộc lịch làm việc của từng bác sĩ và ngày khám. Hãy chọn bác sĩ rồi chọn ngày để xem các giờ khả dụng cập nhật.", List.of(
                    link("Tìm bác sĩ và xem lịch", "/doctors")));
        }
        if (containsAny(normalized, "trieu chung", "toi bi", "dau ", "ngua", "sot", "ho ", "nen kham")) {
            return recommendBySymptoms(normalized);
        }

        return response("Tôi có thể giúp tìm bác sĩ/chuyên khoa, xem giờ còn trống theo ngày (ví dụ 28/10/2026), giải thích cách đặt lịch hoặc hướng dẫn xem kết quả khám. Tôi không chẩn đoán bệnh hay đề xuất thuốc.", List.of(
                link("Danh sách chuyên khoa và bác sĩ", "/doctors"),
                link("Đặt lịch khám", "/appointments/book")));
    }

    private AssistantChatResponse findAvailability(LocalDate date, Specialty specialty, String normalizedMessage) {
        if (!date.isAfter(LocalDate.now())) {
            return response("Vui lòng chọn một ngày trong tương lai để tra cứu khung giờ còn trống.", List.of(
                    link("Tìm bác sĩ", "/doctors")));
        }
        List<Doctor> activeDoctors = doctorRepository.findAllActive(Pageable.unpaged()).getContent();
        List<Doctor> mentionedDoctors = activeDoctors.stream()
                .filter(doctor -> normalizedMessage.contains(normalize(removeTitles(doctor.getName()))))
                .toList();
        List<Doctor> doctors = activeDoctors.stream()
                .filter(doctor -> specialty == null || doctor.getSpecialty().getId().equals(specialty.getId()))
                .filter(doctor -> mentionedDoctors.isEmpty() || mentionedDoctors.contains(doctor))
                .limit(8)
                .toList();
        if (doctors.isEmpty()) {
            return response("Tôi chưa xác định được bác sĩ phù hợp để tra cứu. Hãy chọn chuyên khoa hoặc tìm bác sĩ trong danh sách.", List.of(
                    link("Danh sách bác sĩ", "/doctors")));
        }

        List<String> availableDoctors = new ArrayList<>();
        List<AssistantChatResponse.AssistantLink> links = new ArrayList<>();
        for (Doctor doctor : doctors) {
            List<String> slots = appointmentService.findAvailableTimeSlots(doctor.getId(), date).stream()
                    .map(slot -> slot.getDisplayText())
                    .toList();
            if (!slots.isEmpty()) {
                availableDoctors.add(doctor.getName() + " (" + doctor.getSpecialty().getName() + "): "
                        + String.join(", ", slots.subList(0, Math.min(4, slots.size())))
                        + (slots.size() > 4 ? "…" : ""));
                links.add(link("Đặt với " + doctor.getName(),
                        "/appointments/book?doctorId=" + doctor.getId() + "&appointmentDate=" + date));
            }
        }
        if (availableDoctors.isEmpty()) {
            return response("Tôi chưa tìm thấy khung giờ còn trống cho ngày " + date
                    + (specialty == null ? "." : " ở chuyên khoa " + specialty.getName() + ".")
                    + " Bạn có thể chọn ngày khác hoặc xem lịch từng bác sĩ.", List.of(
                    link("Xem bác sĩ", specialty == null ? "/doctors"
                            : "/doctors?specialtyId=" + specialty.getId())));
        }
        return response("Khung giờ tham khảo ngày " + date + ":\n"
                + String.join("\n", availableDoctors)
                + "\nChọn “Đặt với…” để xem lại và xác nhận lịch. Trợ lý không tự đặt lịch.", links);
    }

    private AssistantChatResponse recommendDoctors(Specialty specialty) {
        List<Doctor> doctors = doctorRepository.findAllActive(Pageable.unpaged()).getContent().stream()
                .filter(doctor -> doctor.getSpecialty().getId().equals(specialty.getId()))
                .limit(5)
                .toList();
        String names = doctors.isEmpty() ? "Hiện chưa có bác sĩ đang nhận lịch trong chuyên khoa này."
                : "Một số bác sĩ: " + String.join(", ", doctors.stream().map(Doctor::getName).toList()) + ".";
        return response("Chuyên khoa " + specialty.getName() + " có thể là điểm bắt đầu phù hợp, nhưng đây không phải chẩn đoán. "
                + names + " Hãy xem hồ sơ và chọn bác sĩ phù hợp.", List.of(
                link("Xem bác sĩ " + specialty.getName(), "/doctors?specialtyId=" + specialty.getId())));
    }

    private AssistantChatResponse recommendBySymptoms(String message) {
        String specialtyName = null;
        if (containsAny(message, "tai", "mui", "hong", "xoang", "nghet mui", "viem hong", "viem tai")) {
            specialtyName = "tai mui hong";
        } else if (containsAny(message, "tre em", "tre nho", "em be", "nhi khoa", "con toi", "be bi")) {
            specialtyName = "nhi khoa";
        } else if (containsAny(message, "mun", "phat ban", "noi man", "da lieu", "ngua da", "noi mun")) {
            specialtyName = "da lieu";
        } else if (containsAny(message, "huyet ap", "hoi hop", "tim mach", "tim dap", "dau nguc")) {
            specialtyName = "tim mach";
        }
        if (specialtyName != null) {
            String target = specialtyName;
            return specialtyRepository.findByActiveTrueOrderByNameAsc().stream()
                    .filter(item -> normalize(item.getName()).contains(target))
                    .findFirst()
                    .map(this::recommendDoctors)
                    .orElseGet(() -> generalSymptomGuidance());
        }
        return generalSymptomGuidance();
    }

    private AssistantChatResponse generalSymptomGuidance() {
        return response("Tôi không thể xác định bệnh từ triệu chứng. Bạn có thể mô tả vấn đề ở mức khái quát để tôi gợi ý nơi bắt đầu, hoặc chọn chuyên khoa trong danh sách. Nếu triệu chứng nặng, mới xuất hiện đột ngột hay đang xấu đi, hãy liên hệ nhân viên y tế.", List.of(
                link("Xem chuyên khoa", "/doctors")));
    }

    private Specialty findSpecialty(String normalizedMessage) {
        return specialtyRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(specialty -> normalizedMessage.contains(normalize(specialty.getName())))
                .findFirst()
                .orElse(null);
    }

    private LocalDate parseDate(String message) {
        Matcher isoMatcher = ISO_DATE.matcher(message);
        if (isoMatcher.find()) {
            try {
                return LocalDate.parse(isoMatcher.group(1));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        Matcher localMatcher = LOCAL_DATE.matcher(message);
        if (localMatcher.find()) {
            try {
                return LocalDate.of(Integer.parseInt(localMatcher.group(3)),
                        Integer.parseInt(localMatcher.group(2)),
                        Integer.parseInt(localMatcher.group(1)));
            } catch (DateTimeParseException | NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isUrgent(String message) {
        return containsAny(message, "kho tho", "khong tho duoc", "bat tinh", "mat y thuc",
                "dau nguc du doi", "liet nua nguoi", "meo mieng", "co giat", "chay mau khong cam");
    }

    private boolean isGreeting(String message) {
        return containsAny(message, "xin chao", "chao", "hello", "hi medcare", "bat dau");
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String removeTitles(String doctorName) {
        return doctorName.replaceFirst("^(?:(?:PGS|GS|TS|ThS|BS|CKI|CKII)\\.?\\s*)+", "");
    }

    private AssistantChatResponse response(String message, List<AssistantChatResponse.AssistantLink> links) {
        return new AssistantChatResponse(message, links);
    }

    private AssistantChatResponse.AssistantLink link(String label, String url) {
        return new AssistantChatResponse.AssistantLink(label, url);
    }
}
