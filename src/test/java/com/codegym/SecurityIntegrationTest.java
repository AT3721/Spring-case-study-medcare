package com.codegym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void adminPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/appointments"))
                .andExpect(status().is3xxRedirection());
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
                .andExpect(content().string(not(containsString("/admin/appointments/update-status"))))
                .andExpect(content().string(containsString("value=\"doctorAsc\" selected=\"selected\"")))
                .andExpect(content().string(containsString("value=\"Nguyễn Thanh Tùng\"")));
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
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanOpenCatalogManagement() throws Exception {
        mockMvc.perform(get("/admin/catalog/doctors"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/catalog/doctors/1/schedule"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/catalog/doctors/new"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/catalog/specialties"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_STAFF")
    void staffCanNotManageCatalogOrAccessRemovedDoctorPortal() throws Exception {
        mockMvc.perform(get("/admin/catalog/doctors"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/catalog/doctors/1/schedule"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/doctor/appointments"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PATIENT")
    void patientRoleCannotViewAdminAppointments() throws Exception {
        mockMvc.perform(get("/admin/appointments"))
                .andExpect(status().isForbidden());
    }
}
