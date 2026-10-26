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
import com.codegym.repository.ITimeSlotRepository;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .andExpect(content().string(containsString("Phí khám thấp trước")))
                .andExpect(content().string(containsString("href=\"/doctors#doctor-results\"")));
    }

    @Test
    void sampleDataIncludesSixteenDoctors() {
        org.junit.jupiter.api.Assertions.assertTrue(doctorRepository.count() >= 16);
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
        mockMvc.perform(get("/appointments/book"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/doctors\" class=\"btn-cancel\">Hủy</a>")));
    }

    @Test
    void bookingFormLoadsSlotsFromAvailabilityApiAndResetsSlotOnSelectionChange() throws Exception {
        mockMvc.perform(get("/appointments/book"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/appointments/available-slots")))
                .andExpect(content().string(containsString("url.searchParams.set('doctorId', doctor.value)")))
                .andExpect(content().string(containsString("url.searchParams.set('date', date.value)")))
                .andExpect(content().string(containsString("doctor.addEventListener('change', selectionChanged)")))
                .andExpect(content().string(containsString("date.addEventListener('change', selectionChanged)")))
                .andExpect(content().string(containsString("initialSlot = '';")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<option value=\"1\">08:00</option>"))));
    }
}
