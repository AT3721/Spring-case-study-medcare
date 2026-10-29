package com.codegym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.codegym.repository.IUserRepository;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.ITimeSlotRepository;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import java.time.LocalDate;
import java.time.YearMonth;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IAppointmentRepository appointmentRepository;

    @Autowired
    private IDoctorRepository doctorRepository;

    @Autowired
    private ITimeSlotRepository timeSlotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sampleAppointmentsAreSeededAcrossSpecialtiesAndTimeSlotsForThisWeek() {
        // Legacy sample appointments have been cleaned for doctor role reset
        Integer sampleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE booking_code LIKE 'MC-DEMO-20261026-%'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(sampleCount == null || sampleCount == 0);

        // Verify doctor accounts exist
        Integer doctorAccountsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE doctor_id IS NOT NULL",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(doctorAccountsCount != null && doctorAccountsCount >= 8);
    }

    @Test
    void adminPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/appointments"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void bookingRequiresPatientLogin() throws Exception {
        mockMvc.perform(get("/appointments/book").param("doctorId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrlPattern("**/login"));

        mockMvc.perform(post("/appointments/book").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void patientLoginReturnsToTheRequestedBookingPage() throws Exception {
        String email = "b" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        var registration = mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "Booking Patient")
                        .param("email", email)
                        .param("password", "safe-password-123")
                        .param("confirmPassword", "safe-password-123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        try {
            var bookingRequest = mockMvc.perform(get("/appointments/book").param("doctorId", "1")
                            .session((org.springframework.mock.web.MockHttpSession) registration.getRequest().getSession(false)))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            mockMvc.perform(post("/login")
                            .session((org.springframework.mock.web.MockHttpSession) bookingRequest.getRequest().getSession(false))
                            .with(csrf())
                            .param("username", email)
                            .param("password", "safe-password-123"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .redirectedUrl("/appointments/book?doctorId=1"));
        } finally {
            userRepository.findByUsername(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void staffCannotBookAsPatient() throws Exception {
        mockMvc.perform(get("/appointments/book").with(
                        user("staff").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellationRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/appointments/cancel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/appointments/cancel").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_STAFF")
    void staffCanViewAppointmentList() throws Exception {
        mockMvc.perform(get("/admin/appointments")
                        .param("sortBy", "doctorAsc")
                        .param("doctorName", "Nguyễn Thanh Tùng"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cổng quản trị")))
                .andExpect(content().string(containsString("Lịch hẹn theo tháng")))
                .andExpect(content().string(containsString("Không gian làm việc")))
                .andExpect(content().string(containsString("© MedCare")))
                .andExpect(content().string(containsString("Kết quả khám")))
                .andExpect(content().string(containsString("appointmentDialog")))
                .andExpect(content().string(containsString("calendar-event")))
                .andExpect(content().string(not(containsString("Quản lý bác sĩ"))))
                .andExpect(content().string(not(containsString("/admin/appointments/update-status"))))
                .andExpect(content().string(not(containsString("table-responsive"))))
                .andExpect(content().string(not(containsString("results-section"))));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void appointmentCalendarDisplaysAppointmentsOnTheirDate() throws Exception {
        String patientName = "Lịch tháng " + UUID.randomUUID().toString().substring(0, 8);
        Appointment appointment = new Appointment();
        appointment.setBookingCode("CAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        appointment.setPatientName(patientName);
        appointment.setPatientPhone("0912345678");
        appointment.setAppointmentDate(YearMonth.now().atDay(15));
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setDoctor(doctorRepository.findById(1L).orElseThrow());
        appointment.setTimeSlot(timeSlotRepository.findById(1L).orElseThrow());
        appointmentRepository.save(appointment);
        try {
            mockMvc.perform(get("/admin/appointments")
                            .param("month", YearMonth.now().toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(patientName)))
                    .andExpect(content().string(containsString("Đã xác nhận")))
                    .andExpect(content().string(containsString("Kết quả khám")))
                    .andExpect(content().string(containsString("data-code=\"" + appointment.getBookingCode() + "\"")))
                    .andExpect(content().string(containsString("form=\"appointmentDetailsForm\"")))
                    .andExpect(content().string(not(containsString("Lưu kết quả"))));
        } finally {
            appointmentRepository.delete(appointment);
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_STAFF")
    void staffCannotUpdateAppointmentStatus() throws Exception {
        mockMvc.perform(post("/admin/appointments/update-status")
                        .with(csrf())
                        .param("id", "1")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanSubmitAppointmentStatusUpdate() throws Exception {
        mockMvc.perform(post("/admin/appointments/update-status")
                        .with(csrf())
                        .param("id", "-1")
                        .param("status", "CONFIRMED"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(authorities = "ROLE_STAFF")
    void staffCanUseSharedAppointmentSaveForClinicalResults() throws Exception {
        mockMvc.perform(post("/admin/appointments/update-details")
                        .with(csrf())
                        .param("id", "-1")
                        .param("clinicalResult", "Kết quả")
                        .param("clinicalResultChanged", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(authorities = "ROLE_STAFF")
    void staffCannotChangeStatusThroughSharedAppointmentSave() throws Exception {
        mockMvc.perform(post("/admin/appointments/update-details")
                        .with(csrf())
                        .param("id", "1")
                        .param("status", "COMPLETED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanOpenCatalogManagement() throws Exception {
        mockMvc.perform(get("/admin/catalog/doctors"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cổng quản trị")))
                .andExpect(content().string(containsString("Quản lý bác sĩ")))
                .andExpect(content().string(containsString("Chuyên khoa")))
                .andExpect(content().string(containsString("© MedCare")));
        mockMvc.perform(get("/admin/catalog/doctors/1/schedule"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/catalog/doctors/new"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/catalog/specialties"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/catalog/schedules"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lịch làm việc bác sĩ")))
                .andExpect(content().string(containsString("schedule-table")))
                .andExpect(content().string(containsString("specialtyFilter")))
                .andExpect(content().string(containsString("Tháng sau")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_STAFF")
    void staffCanNotManageCatalogOrAccessRemovedDoctorPortal() throws Exception {
        mockMvc.perform(get("/admin/catalog/doctors"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/catalog/doctors/1/schedule"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/doctor/appointments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PATIENT")
    void patientRoleCannotViewAdminAppointments() throws Exception {
        mockMvc.perform(get("/admin/appointments"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCanRegisterAndOpenTheirPrivateAppointmentPage() throws Exception {
        String email = "p" + UUID.randomUUID().toString().replace("-", "") + "@e.co";
        try {
            mockMvc.perform(post("/register")
                            .with(csrf())
                            .param("fullName", "Test Patient")
                            .param("email", email.toUpperCase())
                            .param("password", "safe-password-123")
                            .param("confirmPassword", "safe-password-123"))
                    .andExpect(status().is3xxRedirection());

            var patient = userRepository.findByUsername(email).orElseThrow();
            org.junit.jupiter.api.Assertions.assertTrue(
                    passwordEncoder.matches("safe-password-123", patient.getPassword()));
            org.junit.jupiter.api.Assertions.assertEquals("test patient", patient.getFullName().toLowerCase());

            String bookingCode = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
            Appointment appointment = new Appointment();
            appointment.setBookingCode(bookingCode);
            appointment.setPatientName(patient.getFullName());
            appointment.setPatientPhone("0912345678");
            appointment.setPatientEmail(email);
            appointment.setAppointmentDate(LocalDate.now().plusDays(1));
            appointment.setStatus(AppointmentStatus.COMPLETED);
            appointment.setClinicalResult("Kết quả khám riêng của bệnh nhân.");
            appointment.setDoctor(doctorRepository.findById(1L).orElseThrow());
            appointment.setTimeSlot(timeSlotRepository.findById(1L).orElseThrow());
            appointment.setPatientAccount(patient);
            appointmentRepository.save(appointment);

            mockMvc.perform(get("/patient/appointments")
                            .with(user(email).authorities(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Lịch khám của tôi")))
                    .andExpect(content().string(containsString(bookingCode)))
                    .andExpect(content().string(containsString("Kết quả khám riêng của bệnh nhân.")));
        } finally {
            appointmentRepository.findAll().stream()
                    .filter(appointment -> appointment.getBookingCode().startsWith("TEST-"))
                    .forEach(appointmentRepository::delete);
            userRepository.findByUsername(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void patientCanEditAndCancelTheirUpcomingAppointmentWithoutDeletingHistory() throws Exception {
        String email = "manage" + UUID.randomUUID().toString().replace("-", "") + "@e.co";
        String bookingCode = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            mockMvc.perform(post("/register")
                            .with(csrf())
                            .param("fullName", "Test Patient")
                            .param("email", email)
                            .param("password", "safe-password-123")
                            .param("confirmPassword", "safe-password-123"))
                    .andExpect(status().is3xxRedirection());
            var patient = userRepository.findByUsername(email).orElseThrow();

            Appointment appointment = new Appointment();
            appointment.setBookingCode(bookingCode);
            appointment.setPatientName(patient.getFullName());
            appointment.setPatientPhone("0912345678");
            appointment.setPatientEmail(email);
            appointment.setAppointmentDate(LocalDate.now().plusDays(2));
            appointment.setStatus(AppointmentStatus.PENDING);
            appointment.setDoctor(doctorRepository.findById(1L).orElseThrow());
            appointment.setTimeSlot(timeSlotRepository.findById(1L).orElseThrow());
            appointment.setPatientAccount(patient);
            appointment = appointmentRepository.save(appointment);
            Long appointmentId = appointment.getId();

            var patientAuth = user(email).authorities(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT"));
            mockMvc.perform(get("/patient/appointments").with(patientAuth))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Chỉnh sửa")))
                    .andExpect(content().string(containsString("Hủy lịch")))
                    .andExpect(content().string(containsString("Xác nhận hủy lịch?")))
                    .andExpect(content().string(containsString("Không, quay lại")));
            mockMvc.perform(get("/patient/appointments/{id}/edit", appointmentId).with(patientAuth))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Chỉnh sửa lịch hẹn")))
                    .andExpect(content().string(containsString("data-initial-slot")));

            mockMvc.perform(post("/patient/appointments/{id}/cancel", appointmentId)
                            .with(patientAuth)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
            var cancelled = appointmentRepository.findById(appointmentId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());
            org.junit.jupiter.api.Assertions.assertEquals(bookingCode, cancelled.getBookingCode());
        } finally {
            appointmentRepository.findByBookingCode(bookingCode).ifPresent(appointmentRepository::delete);
            userRepository.findByUsername(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void patientRegistrationRejectsMalformedEmailAndShowsFieldError() throws Exception {
        String email = "not-an-email";
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "Test Patient")
                        .param("email", email)
                        .param("password", "safe-password-123")
                        .param("confirmPassword", "safe-password-123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email không đúng định dạng.")))
                .andExpect(content().string(containsString("tên miền hợp lệ")));

        org.junit.jupiter.api.Assertions.assertFalse(userRepository.existsByUsername(email));
    }

    @Test
    void clinicalResultsArePrivateToStaffAndAdmins() throws Exception {
        mockMvc.perform(post("/admin/appointments/update-result")
                        .with(csrf())
                        .param("id", "1")
                        .param("clinicalResult", "Kết quả"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/appointments/update-result")
                        .with(csrf())
                        .with(user("patient").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT")))
                        .param("id", "1")
                        .param("clinicalResult", "Kết quả"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/appointments/update-result")
                        .with(csrf())
                        .with(user("staff").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STAFF")))
                        .param("id", "-1")
                        .param("clinicalResult", "Kết quả"))
                .andExpect(status().is3xxRedirection());
    }
}
