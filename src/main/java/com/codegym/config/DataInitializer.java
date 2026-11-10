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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Configuration
public class DataInitializer {

    @Bean
    @Order(-1)
    CommandLineRunner migrateManagementColumns(JdbcTemplate jdbcTemplate) {
        return args -> {
            addColumnIfMissing(jdbcTemplate, "doctors", "active",
                    "ALTER TABLE doctors ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing(jdbcTemplate, "doctors", "image_url",
                    "ALTER TABLE doctors ADD COLUMN image_url VARCHAR(500) NULL");
            addColumnIfMissing(jdbcTemplate, "specialties", "active",
                    "ALTER TABLE specialties ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing(jdbcTemplate, "users", "doctor_id",
                    "ALTER TABLE users ADD COLUMN doctor_id BIGINT NULL UNIQUE");
            addColumnIfMissing(jdbcTemplate, "users", "email",
                    "ALTER TABLE users ADD COLUMN email VARCHAR(100) NULL");
            addColumnIfMissing(jdbcTemplate, "appointments", "patient_user_id",
                    "ALTER TABLE appointments ADD COLUMN patient_user_id BIGINT NULL");
            addColumnIfMissing(jdbcTemplate, "appointments", "clinical_result",
                    "ALTER TABLE appointments ADD COLUMN clinical_result TEXT NULL");
            addColumnIfMissing(jdbcTemplate, "appointments", "payment_method",
                    "ALTER TABLE appointments ADD COLUMN payment_method VARCHAR(20) DEFAULT 'CASH'");
            addColumnIfMissing(jdbcTemplate, "appointments", "payment_status",
                    "ALTER TABLE appointments ADD COLUMN payment_status VARCHAR(20) DEFAULT 'UNPAID'");
            addColumnIfMissing(jdbcTemplate, "appointments", "payment_amount",
                    "ALTER TABLE appointments ADD COLUMN payment_amount DECIMAL(10,2) DEFAULT 0.00");
            addColumnIfMissing(jdbcTemplate, "appointments", "payment_transaction_no",
                    "ALTER TABLE appointments ADD COLUMN payment_transaction_no VARCHAR(50) NULL");
            addColumnIfMissing(jdbcTemplate, "appointments", "payment_time",
                    "ALTER TABLE appointments ADD COLUMN payment_time DATETIME NULL");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS payment_transactions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        appointment_id BIGINT NOT NULL,
                        txn_ref VARCHAR(50) NOT NULL UNIQUE,
                        amount DECIMAL(10, 2) NOT NULL,
                        bank_code VARCHAR(20) NULL,
                        transaction_no VARCHAR(50) NULL,
                        card_type VARCHAR(20) NULL,
                        order_info VARCHAR(255) NULL,
                        response_code VARCHAR(10) NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        payment_time DATETIME NULL,
                        CONSTRAINT fk_payment_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);
            jdbcTemplate.update("""
                    UPDATE appointments a
                    JOIN doctors d ON a.doctor_id = d.id
                    SET a.payment_amount = d.consultation_fee
                    WHERE a.payment_amount IS NULL OR a.payment_amount = 0.00
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS password_reset_tokens (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        token VARCHAR(100) NOT NULL UNIQUE,
                        user_id BIGINT NOT NULL,
                        expiry_date DATETIME NOT NULL,
                        used TINYINT(1) NOT NULL DEFAULT 0,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);
            addColumnIfMissing(jdbcTemplate, "time_slots", "active",
                    "ALTER TABLE time_slots ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1");
            Integer emailConstraintCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints " +
                            "WHERE constraint_schema = DATABASE() " +
                            "AND table_name = 'users' AND constraint_name = 'uq_user_email'",
                    Integer.class);
            if (emailConstraintCount != null && emailConstraintCount == 0) {
                jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT uq_user_email UNIQUE (email)");
            }
            Integer patientForeignKeyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints " +
                            "WHERE constraint_schema = DATABASE() " +
                            "AND table_name = 'appointments' AND constraint_name = 'fk_appointment_patient_user'",
                    Integer.class);
            if (patientForeignKeyCount != null && patientForeignKeyCount == 0) {
                jdbcTemplate.execute("ALTER TABLE appointments ADD CONSTRAINT fk_appointment_patient_user " +
                        "FOREIGN KEY (patient_user_id) REFERENCES users(id) ON DELETE SET NULL");
            }
            hideExtraSampleDoctors(jdbcTemplate);
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
            jdbcTemplate.execute("INSERT INTO roles (name) VALUES ('ROLE_DOCTOR') " +
                    "ON DUPLICATE KEY UPDATE name = VALUES(name)");
            // Ensure any user linked to a doctor has ROLE_DOCTOR
            jdbcTemplate.update(
                    "INSERT INTO user_roles (user_id, role_id) " +
                            "SELECT u.id, doc_role.id " +
                            "FROM users u " +
                            "JOIN roles doc_role ON doc_role.name = 'ROLE_DOCTOR' " +
                            "WHERE u.doctor_id IS NOT NULL " +
                            "ON DUPLICATE KEY UPDATE role_id = VALUES(role_id)");
        };
    }

    private void hideExtraSampleDoctors(JdbcTemplate jdbcTemplate) {
        String migrationKey = "hide-extra-sample-doctors-v1";
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_migrations WHERE migration_key = ?",
                Integer.class, migrationKey);
        if (applied != null && applied > 0) {
            return;
        }

        jdbcTemplate.update("""
                UPDATE doctors SET active = 0
                WHERE (id = 6 AND name = 'ThS. BS Trần Ngọc Lan')
                   OR (id = 7 AND name = 'BS. Lê Hoàng Phúc')
                   OR (id = 9 AND name = 'ThS. BS Nguyễn Gia Hân')
                   OR (id = 10 AND name = 'BS. Trần Quốc Huy')
                   OR (id = 12 AND name = 'ThS. BS Đỗ Anh Tuấn')
                   OR (id = 13 AND name = 'BS. Nguyễn Bảo Trâm')
                   OR (id = 15 AND name = 'ThS. BS Lương Mai Phương')
                   OR (id = 16 AND name = 'BS. Nguyễn Thành Đạt')
                """);
        jdbcTemplate.update("""
                UPDATE users SET enabled = 0
                WHERE doctor_id IN (
                    SELECT id FROM doctors
                    WHERE (id = 6 AND name = 'ThS. BS Trần Ngọc Lan')
                       OR (id = 7 AND name = 'BS. Lê Hoàng Phúc')
                       OR (id = 9 AND name = 'ThS. BS Nguyễn Gia Hân')
                       OR (id = 10 AND name = 'BS. Trần Quốc Huy')
                       OR (id = 12 AND name = 'ThS. BS Đỗ Anh Tuấn')
                       OR (id = 13 AND name = 'BS. Nguyễn Bảo Trâm')
                       OR (id = 15 AND name = 'ThS. BS Lương Mai Phương')
                       OR (id = 16 AND name = 'BS. Nguyễn Thành Đạt')
                )
                """);
        jdbcTemplate.update(
                "INSERT INTO application_migrations (migration_key) VALUES (?)",
                migrationKey);
    }

    @Bean
    @Order(1)
    CommandLineRunner assignSampleDoctorImages(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.update("""
                    UPDATE doctors SET image_url = '/images/doctors/male-doctor-1.jpg'
                    WHERE id = 1 AND name = 'BS. CKI Nguyễn Thanh Tùng' AND image_url IS NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE doctors SET image_url = '/images/doctors/male-doctor-2.jpg'
                    WHERE id = 5 AND name = 'BS. Nguyễn Minh Khôi' AND image_url IS NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE doctors SET image_url = '/images/doctors/fmale-doctor-1.jpg'
                    WHERE id = 2 AND name = 'ThS. BS Lê Thị Mai' AND image_url IS NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE doctors SET image_url = '/images/doctors/fmale-doctor-2.jpg'
                    WHERE id = 8 AND name = 'BS. CKI Phạm Thu Hà' AND image_url IS NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE doctors SET image_url = '/images/doctors/male-doctor-3.jpg'
                    WHERE id = 3 AND name = 'TS. BS Phạm Hoàng Nam' AND image_url IS NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE doctors SET image_url = '/images/doctors/fmale-doctor-3.jpg'
                    WHERE id = 11 AND name = 'BS. CKI Võ Thanh Thảo' AND image_url IS NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE doctors SET image_url = '/images/doctors/male-doctor-4.jpg'
                    WHERE id = 4 AND name = 'BS. CKII Đặng Quốc Bảo' AND image_url IS NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE doctors SET image_url = '/images/doctors/fmale-doctor-4.jpg'
                    WHERE id = 14 AND name = 'BS. CKI Phan Đức Long' AND image_url IS NULL
                    """);
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner updateSampleDoctorBios(JdbcTemplate jdbcTemplate) {
        return args -> {
            String migrationKey = "professional-sample-doctor-bios-v1";
            Integer applied = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM application_migrations WHERE migration_key = ?",
                    Integer.class, migrationKey);
            if (applied != null && applied > 0) {
                return;
            }

            jdbcTemplate.update("""
                    UPDATE doctors SET bio = ?
                    WHERE id = 1 AND name = 'BS. CKI Nguyễn Thanh Tùng'
                    """,
                    "Với hơn 12 năm kinh nghiệm trong chuyên ngành Tai Mũi Họng, bác sĩ tập trung thăm khám và điều trị viêm xoang mạn tính cùng các bệnh lý tai mũi họng thường gặp ở trẻ em. Bác sĩ chú trọng đánh giá kỹ triệu chứng, xây dựng hướng điều trị phù hợp và hướng dẫn người bệnh theo dõi, chăm sóc sức khỏe tại nhà.");
            jdbcTemplate.update("""
                    UPDATE doctors SET bio = ?
                    WHERE id = 2 AND name = 'ThS. BS Lê Thị Mai'
                    """,
                    "Bác sĩ có 15 năm công tác tại Bệnh viện Nhi Trung ương, chuyên chăm sóc sức khỏe trẻ em theo từng giai đoạn phát triển. Nội dung tư vấn tập trung vào theo dõi thể chất, dinh dưỡng, tiêm chủng và các bệnh lý thường gặp ở trẻ; đồng thời hỗ trợ phụ huynh nhận biết dấu hiệu cần đưa trẻ đi khám.");
            jdbcTemplate.update("""
                    UPDATE doctors SET bio = ?
                    WHERE id = 3 AND name = 'TS. BS Phạm Hoàng Nam'
                    """,
                    "Trên cương vị Trưởng khoa Da liễu, bác sĩ có chuyên môn sâu về mụn nội tiết và phục hồi da tổn thương. Bác sĩ thăm khám các vấn đề da liễu phổ biến, giải thích rõ tình trạng da và tư vấn kế hoạch chăm sóc phù hợp với từng người, hướng đến hiệu quả điều trị bền vững và an toàn.");
            jdbcTemplate.update("""
                    UPDATE doctors SET bio = ?
                    WHERE id = 4 AND name = 'BS. CKII Đặng Quốc Bảo'
                    """,
                    "Từng đảm nhiệm vị trí Phó khoa Tim mạch, bác sĩ tập trung vào đánh giá và điều trị tăng huyết áp, rối loạn nhịp tim cùng các nguy cơ tim mạch thường gặp. Người bệnh được tư vấn theo dõi chỉ số sức khỏe, sử dụng thuốc đúng hướng dẫn và điều chỉnh lối sống để hỗ trợ kiểm soát bệnh lâu dài.");
            jdbcTemplate.update("""
                    UPDATE doctors SET bio = ?
                    WHERE id = 5 AND name = 'BS. Nguyễn Minh Khôi'
                    """,
                    "Bác sĩ tiếp nhận thăm khám viêm tai giữa, viêm xoang và các bệnh lý đường hô hấp trên. Trong quá trình khám, bác sĩ đánh giá triệu chứng, tư vấn phương án chăm sóc phù hợp và hướng dẫn người bệnh cách theo dõi diễn tiến để chủ động tái khám khi cần thiết.");
            jdbcTemplate.update("""
                    UPDATE doctors SET bio = ?
                    WHERE id = 8 AND name = 'BS. CKI Phạm Thu Hà'
                    """,
                    "Bác sĩ chuyên theo dõi sức khỏe trẻ em, tư vấn tiêm chủng và dinh dưỡng theo độ tuổi. Bác sĩ trao đổi cùng gia đình về các mốc phát triển, hướng dẫn chăm sóc trẻ và giải đáp những vấn đề sức khỏe thường gặp, giúp phụ huynh có thêm thông tin để chăm sóc trẻ chủ động.");
            jdbcTemplate.update("""
                    UPDATE doctors SET bio = ?
                    WHERE id = 11 AND name = 'BS. CKI Võ Thanh Thảo'
                    """,
                    "Bác sĩ thăm khám các bệnh da liễu phổ biến và tư vấn chăm sóc da phù hợp với tình trạng của từng người. Người bệnh được giải thích về các yếu tố có thể ảnh hưởng đến sức khỏe làn da, cách sử dụng sản phẩm chăm sóc hợp lý và những lưu ý trong quá trình theo dõi, điều trị.");
            jdbcTemplate.update("""
                    UPDATE doctors SET bio = ?
                    WHERE id = 14 AND name = 'BS. CKI Phan Đức Long'
                    """,
                    "Bác sĩ tập trung thăm khám tim mạch, tư vấn kiểm soát huyết áp và chăm sóc sức khỏe tim. Nội dung tư vấn bao gồm theo dõi các chỉ số cần thiết, nhận biết dấu hiệu bất thường và xây dựng thói quen sinh hoạt lành mạnh nhằm hỗ trợ phòng ngừa nguy cơ tim mạch.");
            jdbcTemplate.update(
                    "INSERT INTO application_migrations (migration_key) VALUES (?)",
                    migrationKey);
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
                               com.codegym.repository.IDoctorRepository doctorRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            // Ensure every supported account role exists before authentication or registration.
            Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ADMIN)));

            Role staffRole = roleRepository.findByName(RoleName.ROLE_STAFF)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_STAFF)));
            roleRepository.findByName(RoleName.ROLE_DOCTOR)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_DOCTOR)));
            roleRepository.findByName(RoleName.ROLE_PATIENT)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_PATIENT)));

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

            // Seed demo doctor accounts (doctor1, doctor2, ...) for all active doctors
            Role doctorRole = roleRepository.findByName(RoleName.ROLE_DOCTOR)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_DOCTOR)));
            List<com.codegym.model.Doctor> activeDoctors = doctorRepository.findAll().stream()
                    .filter(com.codegym.model.Doctor::isActive)
                    .sorted(java.util.Comparator.comparing(com.codegym.model.Doctor::getId))
                    .toList();
            for (com.codegym.model.Doctor doc : activeDoctors) {
                String docUsername = "doctor" + doc.getId();
                if (userRepository.findByUsername(docUsername).isEmpty()
                        && userRepository.findByDoctorId(doc.getId()).isEmpty()) {
                    User doctorUser = new User();
                    doctorUser.setUsername(docUsername);
                    doctorUser.setPassword(passwordEncoder.encode("123456"));
                    doctorUser.setFullName(doc.getName());
                    doctorUser.setEnabled(true);
                    doctorUser.setDoctor(doc);
                    doctorUser.setRoles(new HashSet<>(Collections.singletonList(doctorRole)));
                    userRepository.save(doctorUser);
                }
            }
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner cleanLegacySampleAppointments(JdbcTemplate jdbcTemplate) {
        return args -> {
            String cleanupKey = "clean-legacy-sample-appointments-v1";
            Integer applied = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM application_migrations WHERE migration_key = ?",
                    Integer.class, cleanupKey);
            if (applied == null || applied == 0) {
                // Delete legacy sample appointments created by old demo seeds
                jdbcTemplate.update("DELETE FROM appointments WHERE booking_code LIKE 'MC-DEMO-%'");
                jdbcTemplate.update("INSERT INTO application_migrations (migration_key) VALUES (?)", cleanupKey);
            }
        };
    }

    @Bean
    @Order(3)
    CommandLineRunner seedSampleDoctorDateSchedules(JdbcTemplate jdbcTemplate) {
        return args -> {
            String migrationKey = "sample-doctor-date-schedules-oct-nov-2026-v1";
            Integer applied = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM application_migrations WHERE migration_key = ?",
                    Integer.class,
                    migrationKey);
            if (applied != null && applied > 0) {
                return;
            }

            List<Long> doctorIds = jdbcTemplate.queryForList(
                    "SELECT d.id FROM doctors d JOIN specialties s ON s.id = d.specialty_id " +
                            "WHERE d.active = 1 AND s.active = 1 ORDER BY d.id",
                    Long.class);
            List<Object[]> schedules = new ArrayList<>();
            Random random = new Random(20261026L);
            for (Long doctorId : doctorIds) {
                for (LocalDate date = LocalDate.of(2026, 10, 1);
                     !date.isAfter(LocalDate.of(2026, 11, 30));
                     date = date.plusDays(1)) {
                    if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        schedules.add(new Object[]{doctorId, date, "OFF"});
                        continue;
                    }
                    int selection = random.nextInt(100);
                    if (selection < 10) {
                        schedules.add(new Object[]{doctorId, date, "OFF"});
                    } else if (selection < 52) {
                        schedules.add(new Object[]{doctorId, date, "MORNING"});
                    } else if (selection < 90) {
                        schedules.add(new Object[]{doctorId, date, "AFTERNOON"});
                    } else {
                        schedules.add(new Object[]{doctorId, date, "MORNING"});
                        schedules.add(new Object[]{doctorId, date, "AFTERNOON"});
                    }
                }
            }
            jdbcTemplate.batchUpdate(
                    "INSERT INTO doctor_date_schedules (doctor_id, schedule_date, shift) VALUES (?, ?, ?)",
                    schedules);
            jdbcTemplate.update(
                    "INSERT INTO application_migrations (migration_key) VALUES (?)",
                    migrationKey);
        };
    }

    @Bean
    @Order(4)
    CommandLineRunner seedSampleAppointmentsForCurrentWeek() {
        return args -> {
            // Disabled: Do not seed dummy sample appointments so system starts with clean appointments
        };
    }
}