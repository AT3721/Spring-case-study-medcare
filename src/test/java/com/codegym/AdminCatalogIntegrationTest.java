package com.codegym;

import com.codegym.model.RoleName;
import com.codegym.model.Doctor;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.IDoctorScheduleRepository;
import com.codegym.repository.IRoleRepository;
import com.codegym.repository.IUserRepository;
import com.codegym.repository.ISpecialtyRepository;
import com.codegym.repository.ITimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCatalogIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private IDoctorRepository doctorRepository;
    @Autowired private IDoctorScheduleRepository doctorScheduleRepository;
    @Autowired private ISpecialtyRepository specialtyRepository;
    @Autowired private ITimeSlotRepository timeSlotRepository;
    @Autowired private IRoleRepository roleRepository;
    @Autowired private IUserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private com.codegym.repository.IAppointmentRepository appointmentRepository;

    @AfterEach
    void removeCreatedCatalogTestData() {
        doctorRepository.findAll().stream()
                .filter(doctor -> doctor.getName().startsWith("New Doctor "))
                .forEach(doctor -> {
                    userRepository.findByDoctorId(doctor.getId()).ifPresent(userRepository::delete);
                    appointmentRepository.deleteAll(appointmentRepository.findAll().stream()
                            .filter(appointment -> appointment.getDoctor().getId().equals(doctor.getId()))
                            .toList());
                    doctorRepository.delete(doctor);
                });
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanCreateDoctorAndLinkedLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/admin/catalog/doctors/save")
                        .with(csrf())
                        .param("name", "New Doctor " + suffix)
                        .param("degree", "MD")
                        .param("consultationFee", "250000")
                        .param("bio", "Created by admin catalog test")
                        .param("specialtyId", "1")
                        .param("active", "true")
                        .param("username", "doctor-" + suffix)
                        .param("password", "StrongTestPassword123"))
                .andExpect(status().is3xxRedirection());

        assertTrue(doctorRepository.findAll().stream().anyMatch(doctor -> doctor.getName().equals("New Doctor " + suffix)));
        assertTrue(roleRepository.findByName(RoleName.ROLE_STAFF).isPresent());
        assertTrue(userRepository.findByUsername("doctor-" + suffix)
                .map(user -> user.getDoctor() != null && user.getRoles().stream()
                        .anyMatch(role -> role.getName() == RoleName.ROLE_STAFF))
                .orElse(false));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE name = 'ROLE_DOCTOR'", Integer.class) == 0);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanConfigureDoctorsWeeklySchedule() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setName("New Doctor Schedule " + UUID.randomUUID());
        doctor.setSpecialty(specialtyRepository.findById(1L).orElseThrow());
        doctor = doctorRepository.save(doctor);

        mockMvc.perform(get("/admin/catalog/doctors/{id}/schedule", doctor.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thứ 2")))
                .andExpect(content().string(containsString("Chủ Nhật")))
                .andExpect(content().string(containsString("Ca sáng")))
                .andExpect(content().string(containsString("Ca chiều")))
                .andExpect(content().string(containsString("Chọn tất cả")))
                .andExpect(content().string(containsString("Bỏ chọn")));

        mockMvc.perform(post("/admin/catalog/doctors/{id}/schedule", doctor.getId())
                        .with(csrf())
                        .param("slotSelections", "MONDAY-1", "WEDNESDAY-2"))
                .andExpect(status().is3xxRedirection());

        var schedules = doctorScheduleRepository.findByDoctorId(doctor.getId());
        assertEquals(2, schedules.size());
        assertTrue(schedules.stream().anyMatch(schedule -> schedule.getDayOfWeek() == DayOfWeek.MONDAY));
        assertTrue(schedules.stream().anyMatch(schedule -> schedule.getDayOfWeek() == DayOfWeek.WEDNESDAY));
        mockMvc.perform(get("/admin/catalog/doctors/{id}/schedule", doctor.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"MONDAY-1\" checked=\"checked\"")));

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Long firstSlotId = timeSlotRepository.findByActiveTrueOrderByStartTimeAsc().get(0).getId();
        mockMvc.perform(get("/api/appointments/available-slots")
                        .param("doctorId", doctor.getId().toString())
                        .param("date", nextMonday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(firstSlotId.intValue()));
    }
}
