package com.codegym.config;

import com.codegym.model.Role;
import com.codegym.model.RoleName;
import com.codegym.model.User;
import com.codegym.repository.IRoleRepository;
import com.codegym.repository.IUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.HashSet;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class DataInitializer {

    @Bean
    @Order(-1)
    CommandLineRunner migrateManagementColumns(JdbcTemplate jdbcTemplate) {
        return args -> {
            addColumnIfMissing(jdbcTemplate, "doctors", "active",
                    "ALTER TABLE doctors ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing(jdbcTemplate, "specialties", "active",
                    "ALTER TABLE specialties ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing(jdbcTemplate, "users", "doctor_id",
                    "ALTER TABLE users ADD COLUMN doctor_id BIGINT NULL UNIQUE");
            addColumnIfMissing(jdbcTemplate, "time_slots", "active",
                    "ALTER TABLE time_slots ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1");
            Integer doctorForeignKeyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints " +
                            "WHERE constraint_schema = DATABASE() " +
                            "AND table_name = 'users' AND constraint_name = 'fk_user_doctor'",
                    Integer.class);
            if (doctorForeignKeyCount != null && doctorForeignKeyCount == 0) {
                jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT fk_user_doctor " +
                        "FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE SET NULL");
            }
            jdbcTemplate.execute("INSERT INTO roles (name) VALUES ('ROLE_STAFF') " +
                    "ON DUPLICATE KEY UPDATE name = VALUES(name)");
            jdbcTemplate.update(
                    "INSERT INTO user_roles (user_id, role_id) " +
                            "SELECT ur.user_id, staff_role.id " +
                            "FROM user_roles ur " +
                            "JOIN roles old_role ON old_role.id = ur.role_id " +
                            "JOIN roles staff_role ON staff_role.name = 'ROLE_STAFF' " +
                            "WHERE old_role.name = 'ROLE_DOCTOR' " +
                            "ON DUPLICATE KEY UPDATE role_id = VALUES(role_id)");
            jdbcTemplate.update(
                    "DELETE ur FROM user_roles ur " +
                            "JOIN roles old_role ON old_role.id = ur.role_id " +
                            "WHERE old_role.name = 'ROLE_DOCTOR'");
            jdbcTemplate.update("DELETE FROM roles WHERE name = 'ROLE_DOCTOR'");
        };
    }

    @Bean
    @Order(1)
    CommandLineRunner normalizeTimeSlots(JdbcTemplate jdbcTemplate) {
        return args -> {
            Map<String, Long> canonicalIds = new LinkedHashMap<>();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, TIME_FORMAT(start_time, '%H:%i') AS start_time FROM time_slots ORDER BY id");
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                String start = (String) row.get("start_time");
                if (isBusinessStart(start)) {
                    Long canonicalId = canonicalIds.putIfAbsent(start, id);
                    if (canonicalId != null) {
                        jdbcTemplate.update("UPDATE appointments SET time_slot_id = ? WHERE time_slot_id = ?",
                                canonicalId, id);
                        jdbcTemplate.update("DELETE FROM time_slots WHERE id = ?", id);
                    }
                } else {
                    Integer appointmentCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM appointments WHERE time_slot_id = ?", Integer.class, id);
                    if (appointmentCount != null && appointmentCount > 0) {
                        jdbcTemplate.update("UPDATE time_slots SET active = 0 WHERE id = ?", id);
                    } else {
                        jdbcTemplate.update("DELETE FROM time_slots WHERE id = ?", id);
                    }
                }
            }

            for (LocalTime start : businessStartTimes()) {
                String key = start.format(DateTimeFormatter.ofPattern("HH:mm"));
                Long id = canonicalIds.get(key);
                if (id == null) {
                    jdbcTemplate.update(
                            "INSERT INTO time_slots (start_time, end_time, display_text, active) VALUES (?, ?, ?, 1)",
                            start, start.plusMinutes(30), key);
                } else {
                    jdbcTemplate.update(
                            "UPDATE time_slots SET end_time = ?, display_text = ?, active = 1 WHERE id = ?",
                            start.plusMinutes(30), key, id);
                }
            }
        };
    }

    private boolean isBusinessStart(String value) {
        return businessStartTimes().stream()
                .anyMatch(time -> time.format(DateTimeFormatter.ofPattern("HH:mm")).equals(value));
    }

    private List<LocalTime> businessStartTimes() {
        List<LocalTime> starts = new ArrayList<>();
        addStartTimes(starts, LocalTime.of(8, 0), LocalTime.NOON);
        addStartTimes(starts, LocalTime.of(13, 0), LocalTime.of(17, 0));
        return starts;
    }

    private void addStartTimes(List<LocalTime> starts, LocalTime start, LocalTime endExclusive) {
        for (LocalTime time = start; time.isBefore(endExclusive); time = time.plusMinutes(30)) {
            starts.add(time);
        }
    }

    private void addColumnIfMissing(JdbcTemplate jdbcTemplate, String table, String column, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        if (count != null && count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    @Bean
    @Order(0)
    CommandLineRunner removeLegacyAppointmentSlotConstraint(JdbcTemplate jdbcTemplate) {
        return args -> {
            Integer legacyIndexCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() " +
                            "AND table_name = 'appointments' " +
                            "AND index_name = 'uq_doctor_date_slot'",
                    Integer.class);
            if (legacyIndexCount != null && legacyIndexCount > 0) {
                Integer doctorIndexCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.statistics " +
                                "WHERE table_schema = DATABASE() " +
                                "AND table_name = 'appointments' " +
                                "AND column_name = 'doctor_id' " +
                                "AND seq_in_index = 1 " +
                                "AND index_name <> 'uq_doctor_date_slot'",
                        Integer.class);
                if (doctorIndexCount == null || doctorIndexCount == 0) {
                    jdbcTemplate.execute("CREATE INDEX idx_appointments_doctor_id ON appointments (doctor_id)");
                }
                jdbcTemplate.execute("ALTER TABLE appointments DROP INDEX uq_doctor_date_slot");
            }
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner initData(IUserRepository userRepository,
                               IRoleRepository roleRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Khởi tạo Role ADMIN và STAFF nếu chưa có trong DB
            Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ADMIN)));

            Role staffRole = roleRepository.findByName(RoleName.ROLE_STAFF)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_STAFF)));

            String adminPassword = System.getenv("MEDCARE_ADMIN_PASSWORD");
            if (adminPassword != null && !adminPassword.isBlank()) {
                User admin = userRepository.findByUsername("admin").orElseGet(User::new);
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode(adminPassword));
                if (admin.getFullName() == null) {
                    admin.setFullName("Nguyễn Văn Quản Trị");
                }
                admin.setEnabled(true);
                if (admin.getRoles().isEmpty()) {
                    admin.setRoles(new HashSet<>(Collections.singletonList(adminRole)));
                }
                userRepository.save(admin);
            }

            String staffPassword = System.getenv("MEDCARE_STAFF_PASSWORD");
            if (staffPassword != null && !staffPassword.isBlank()) {
                User staff = userRepository.findByUsername("staff").orElseGet(User::new);
                staff.setUsername("staff");
                staff.setPassword(passwordEncoder.encode(staffPassword));
                if (staff.getFullName() == null) {
                    staff.setFullName("Trần Thị Lễ Tân");
                }
                staff.setEnabled(true);
                if (staff.getRoles().isEmpty()) {
                    staff.setRoles(new HashSet<>(Collections.singletonList(staffRole)));
                }
                userRepository.save(staff);
            }
        };
    }
}