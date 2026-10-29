package com.codegym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.codegym.repository.IDoctorRepository;
import java.time.LocalDate;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import com.codegym.repository.ISpecialtyRepository;
import com.codegym.repository.IUserRepository;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.model.User;
import com.codegym.repository.ITimeSlotRepository;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DoctorDirectoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IDoctorRepository doctorRepository;

    @Autowired private ISpecialtyRepository specialtyRepository;
    @Autowired private IUserRepository userRepository;
    @Autowired private IAppointmentRepository appointmentRepository;
    @Autowired private ITimeSlotRepository timeSlotRepository;

    @Test
    void homepageIncludesPrivateAndSafetyAwareClinicAssistant() throws Exception {
        mockMvc.perform(get("/doctors"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trợ lý MedCare")))
                .andExpect(content().string(containsString("Tin nhắn không được lưu")))
                .andExpect(content().string(containsString("không chẩn đoán/kê đơn")))
                .andExpect(content().string(containsString("Chuyên khoa tại MedCare")))
                .andExpect(content().string(containsString("Tai Mũi Họng")))
                .andExpect(content().string(containsString("Nguyễn Thanh Tùng")))
                .andExpect(content().string(containsString("banner-specialty-filter")));
    }

    @Test
    void clinicAssistantAnswersBookingAndUrgentSafetyQuestions() throws Exception {
        mockMvc.perform(post("/api/assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Cách đặt lịch khám?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/register")));

        mockMvc.perform(post("/api/assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Tôi đang khó thở\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tel:115")));
    }

    @Test
    void clinicAssistantValidatesInputAndCanRecommendSpecialty() throws Exception {
        mockMvc.perform(post("/api/assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Tai Mũi Họng\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("specialtyId=1")))
                .andExpect(content().string(containsString("\"links\"")));

        mockMvc.perform(post("/api/assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @BeforeEach
    void cleanLeakedCatalogTestFixtures() {
        var fixtureSpecialties = specialtyRepository.findAll().stream()
                .filter(specialty -> specialty.getName().startsWith("Portal "))
                .toList();
        var fixtureSpecialtyIds = fixtureSpecialties.stream().map(specialty -> specialty.getId()).toList();
        var fixtureDoctors = doctorRepository.findAll().stream()
                .filter(doctor -> doctor.getName().startsWith("Doctor Own ")
                        || doctor.getName().startsWith("Doctor Other ")
                        || doctor.getName().startsWith("New Doctor ")
                        || fixtureSpecialtyIds.contains(doctor.getSpecialty().getId()))
                .toList();
        var fixtureDoctorIds = fixtureDoctors.stream().map(doctor -> doctor.getId()).toList();

        appointmentRepository.findAll().stream()
                .filter(appointment -> fixtureDoctorIds.contains(appointment.getDoctor().getId())
                        || appointment.getBookingCode().startsWith("OWN-")
                        || appointment.getBookingCode().startsWith("OTHER-"))
                .forEach(appointmentRepository::delete);
        userRepository.findAll().stream()
                .filter(user -> (user.getDoctor() != null && fixtureDoctorIds.contains(user.getDoctor().getId()))
                        || user.getUsername().startsWith("portal-")
                        || user.getUsername().startsWith("doctor-"))
                .forEach(userRepository::delete);
        doctorRepository.deleteAll(fixtureDoctors);
        specialtyRepository.deleteAll(fixtureSpecialties);
    }

    @Test
    void directoryRendersWithSearchAndSortFilters() throws Exception {
        mockMvc.perform(get("/doctors").param("q", "Tung").param("sortBy", "feeAsc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tìm bác sĩ")))
                .andExpect(content().string(containsString("Phòng khám MedCare đồng hành cùng sức khỏe gia đình bạn")))
                .andExpect(content().string(containsString(".container { width: min(1120px, calc(100% - 40px));")))
                .andExpect(content().string(containsString("Phí khám thấp trước")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("href=\"/appointments/search\""))))
                .andExpect(content().string(containsString("site-header")))
                .andExpect(content().string(containsString("site-footer")));
    }

    @Test
    void doctorFiltersAndPaginationCanLoadResultsWithoutReplacingThePage() throws Exception {
        mockMvc.perform(get("/doctors"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"doctor-filter-form\"")))
                .andExpect(content().string(containsString("form.elements.specialtyId.addEventListener(\"change\"")))
                .andExpect(content().string(containsString("window.addEventListener(\"popstate\"")))
                .andExpect(content().string(containsString("chip.classList.toggle(\"active\", isActive)")));

        mockMvc.perform(get("/doctors").param("specialtyId", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"chip active\" href=\"/doctors?specialtyId=2")))
                .andExpect(content().string(containsString("data-specialty-id=\"2\" aria-current=\"true\"")));

        mockMvc.perform(get("/doctors/results")
                        .param("page", "0")
                        .param("sortBy", "name"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"doctor-results-content\"")))
                .andExpect(content().string(containsString("Đội ngũ bác sĩ")))
                .andExpect(content().string(containsString("Phân trang danh sách bác sĩ")));
    }

    @Test
    void publicPagesShareClinicHeaderAndFooter() throws Exception {
        for (String path : java.util.List.of(
                "/doctors/14",
                "/appointments/search",
                "/appointments/success")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("site-header")))
                    .andExpect(content().string(containsString("site-footer")));
        }
    }

    @Test
    void bookingFooterAppearsAfterTheCompleteBookingForm() throws Exception {
        String patientUsername = createPatientFixture();
        try {
            String page = mockMvc.perform(get("/appointments/book")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                    .user(patientUsername).authorities(
                                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            int footerPosition = page.indexOf("<footer class=\"site-footer\"");

            org.junit.jupiter.api.Assertions.assertTrue(page.indexOf("Ngày Khám:") < footerPosition);
            org.junit.jupiter.api.Assertions.assertTrue(page.indexOf("Xác Nhận Đặt Lịch") < footerPosition);
        } finally {
            userRepository.findByUsername(patientUsername).ifPresent(userRepository::delete);
        }
    }

    @Test
    void appointmentSearchFooterAppearsAfterSearchAndResultContent() throws Exception {
        String page = mockMvc.perform(get("/appointments/search"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int footerPosition = page.indexOf("<footer class=\"site-footer\"");

        org.junit.jupiter.api.Assertions.assertTrue(
                page.indexOf("Tra Cứu &amp; Hủy Lịch Khám") < footerPosition);
        org.junit.jupiter.api.Assertions.assertTrue(
                page.indexOf("Quay lại trang chủ") < footerPosition);
    }

    @Test
    void seededDoctorProfilesRenderTheirPhotoAssets() throws Exception {
        mockMvc.perform(get("/doctors"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/images/doctors/male-doctor-1.jpg")))
                .andExpect(content().string(containsString("object-fit: contain")));
        mockMvc.perform(get("/doctors").param("q", "Lê Thị Mai"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/images/doctors/fmale-doctor-1.jpg")));
        mockMvc.perform(get("/doctors/14"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nhận biết dấu hiệu bất thường")))
                .andExpect(content().string(containsString("object-fit: contain")));
    }

    @Test
    void sampleDataIncludesTwoActiveDoctorsPerSpecialty() {
        var activeDoctors = doctorRepository.findAllActive(org.springframework.data.domain.Pageable.unpaged())
                .getContent();
        org.junit.jupiter.api.Assertions.assertEquals(8, activeDoctors.size());
        org.junit.jupiter.api.Assertions.assertEquals(4, specialtyRepository.findAll().stream()
                .map(specialty -> activeDoctors.stream()
                        .filter(doctor -> doctor.getSpecialty().getId().equals(specialty.getId()))
                        .count())
                .filter(count -> count == 2)
                .count());
        org.junit.jupiter.api.Assertions.assertFalse(specialtyRepository.findAll().stream()
                .anyMatch(specialty -> specialty.getName().startsWith("Portal ")));
        org.junit.jupiter.api.Assertions.assertFalse(doctorRepository.findAll().stream()
                .anyMatch(doctor -> doctor.getName().startsWith("Doctor Own ")
                        || doctor.getName().startsWith("Doctor Other ")
                        || doctor.getName().startsWith("New Doctor ")));
    }

    @Test
    void availableTimeSlotsEndpointReturnsJsonForFutureDate() throws Exception {
        mockMvc.perform(get("/api/appointments/available-slots")
                        .param("doctorId", "1")
                        .param("date", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void activeTimeSlotsAreUniqueBusinessStartTimesInThirtyMinuteSteps() {
        var slots = timeSlotRepository.findByActiveTrueOrderByStartTimeAsc();
        var startTimes = slots.stream()
                .map(slot -> slot.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of("08:00", "08:30", "09:00", "09:30", "10:00", "10:30",
                        "11:00", "11:30", "13:00", "13:30", "14:00", "14:30",
                        "15:00", "15:30", "16:00", "16:30"),
                startTimes);
        org.junit.jupiter.api.Assertions.assertTrue(slots.stream()
                .allMatch(slot -> slot.getDisplayText().equals(
                        slot.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")))));
    }

    @Test
    void bookingFormHasCancelLinkToDoctorHome() throws Exception {
        String patientUsername = createPatientFixture();
        try {
            mockMvc.perform(get("/appointments/book")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                    .user(patientUsername).authorities(
                                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"/doctors\" class=\"btn-cancel\">Hủy</a>")));
        } finally {
            userRepository.findByUsername(patientUsername).ifPresent(userRepository::delete);
        }
    }

    @Test
    void bookingFormLoadsSlotsFromAvailabilityApiAndResetsSlotOnSelectionChange() throws Exception {
        String patientUsername = createPatientFixture();
        try {
            mockMvc.perform(get("/appointments/book")
                            .param("doctorId", "1")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                    .user(patientUsername).authorities(
                                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/api/appointments/available-slots")))
                    .andExpect(content().string(containsString("url.searchParams.set('doctorId', docId)")))
                    .andExpect(content().string(containsString("url.searchParams.set('date', dateInput.value)")))
                    .andExpect(content().string(containsString("dateInput.addEventListener('change', refreshDoctorSlots)")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<option value=\"1\">08:00</option>"))));
        } finally {
            userRepository.findByUsername(patientUsername).ifPresent(userRepository::delete);
        }
    }

    private String createPatientFixture() {
        String username = "b" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        userRepository.save(new User(username, "test-password", "Booking Test Patient"));
        return username;
    }
}
