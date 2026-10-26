-- Dynamic Database Schema Initialization for MedCare System
-- Target RDBMS: MySQL 8.0+ / H2 Compatible

-- 1. Specialty Table
CREATE TABLE IF NOT EXISTS specialties (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             name VARCHAR(100) NOT NULL,
                             description TEXT,
                             icon_url VARCHAR(255),
                             active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Doctor Table
CREATE TABLE IF NOT EXISTS doctors (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         name VARCHAR(100) NOT NULL,
                         degree VARCHAR(100),
                         consultation_fee DECIMAL(10, 2) DEFAULT 0.00,
                         bio TEXT,
                         active TINYINT(1) NOT NULL DEFAULT 1,
                         specialty_id BIGINT NOT NULL,
                         CONSTRAINT fk_doctor_specialty FOREIGN KEY (specialty_id) REFERENCES specialties(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. TimeSlot Table
CREATE TABLE IF NOT EXISTS time_slots (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            start_time TIME NOT NULL,
                            end_time TIME NOT NULL,
                            display_text VARCHAR(50) NOT NULL,
                            active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Doctor weekly availability
CREATE TABLE IF NOT EXISTS doctor_schedules (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  doctor_id BIGINT NOT NULL,
                                  day_of_week VARCHAR(10) NOT NULL,
                                  time_slot_id BIGINT NOT NULL,
                                  CONSTRAINT uq_doctor_schedule UNIQUE (doctor_id, day_of_week, time_slot_id),
                                  CONSTRAINT fk_doctor_schedule_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_doctor_schedule_slot FOREIGN KEY (time_slot_id) REFERENCES time_slots(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Appointment Table
CREATE TABLE IF NOT EXISTS appointments (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              booking_code VARCHAR(20) NOT NULL UNIQUE,
                              patient_name VARCHAR(100) NOT NULL,
                              patient_phone VARCHAR(15) NOT NULL,
                              patient_email VARCHAR(100),
                              appointment_date DATE NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              notes TEXT,
                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              doctor_id BIGINT NOT NULL,
                              time_slot_id BIGINT NOT NULL,
                              CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
                              CONSTRAINT fk_appointment_timeslot FOREIGN KEY (time_slot_id) REFERENCES time_slots(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Role Table
CREATE TABLE IF NOT EXISTS roles (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. User Table
CREATE TABLE IF NOT EXISTS users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       full_name VARCHAR(100),
                       enabled TINYINT(1) DEFAULT 1,
                       doctor_id BIGINT UNIQUE,
                       CONSTRAINT fk_user_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. User_Roles Join Table
CREATE TABLE IF NOT EXISTS user_roles (
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;