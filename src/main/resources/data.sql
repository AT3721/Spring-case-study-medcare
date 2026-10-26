-- 1. Insert Roles
INSERT INTO roles (id, name) VALUES
                                 (1, 'ROLE_ADMIN'),
                                 (2, 'ROLE_STAFF'),
                                 (3, 'ROLE_PATIENT')
ON DUPLICATE KEY UPDATE name=VALUES(name);

/*-- 2. Insert Users (Password 123456 chuẩn BCrypt)
INSERT INTO users (id, username, password, full_name, enabled) VALUES
                                                                   (1, 'admin', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUvgkqRzgVym5p61d2d5D50.g6/5eS1a', 'Nguyễn Văn Quản Trị', 1),
                                                                   (2, 'staff', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUvgkqRzgVym5p61d2d5D50.g6/5eS1a', 'Trần Thị Lễ Tân', 1)
ON DUPLICATE KEY UPDATE password=VALUES(password), full_name=VALUES(full_name);

-- 3. Insert User Roles
INSERT INTO user_roles (user_id, role_id) VALUES
                                              (1, 1),
                                              (2, 2)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);*/

-- 2. Insert Specialties
INSERT INTO specialties (id, name, description, icon_url) VALUES
                                                              (1, 'Tai Mũi Họng', 'Chẩn đoán và điều trị các bệnh lý về tai, mũi, họng và vùng cổ.', '/images/specialties/ent.png'),
                                                              (2, 'Nhi Khoa', 'Chăm sóc sức khỏe toàn diện cho trẻ sơ sinh, trẻ em và thiếu niên.', '/images/specialties/pediatrics.png'),
                                                              (3, 'Da Liễu', 'Khám và điều trị các bệnh ngoài da, dị ứng và chăm sóc da thẩm mỹ.', '/images/specialties/dermatology.png'),
                                                              (4, 'Tim Mạch', 'Tầm soát và điều trị các bệnh lý tim mạch, huyết áp cao, bệnh mạch vành.', '/images/specialties/cardiology.png')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 3. Insert Doctors
INSERT INTO doctors (id, name, degree, consultation_fee, bio, specialty_id) VALUES
                                                                                (1, 'BS. CKI Nguyễn Thanh Tùng', 'Bác sĩ CKI', 300000.00, 'Hơn 12 năm kinh nghiệm trong điều trị viêm xoang mạn tính và bệnh lý tai mũi họng trẻ em.', 1),
                                                                                (2, 'ThS. BS Lê Thị Mai', 'Thạc sĩ Y Khoa', 350000.00, 'Chuyên gia Nhi khoa với 15 năm công tác tại Bệnh viện Nhi Trung ương.', 2),
                                                                                (3, 'TS. BS Phạm Hoàng Nam', 'Tiến sĩ Y Khoa', 500000.00, 'Trưởng khoa Da liễu, chuyên sâu về điều trị mụn nội tiết và phục hồi da tổn thương.', 3),
                                                                                (4, 'BS. CKII Đặng Quốc Bảo', 'Bác sĩ CKII', 450000.00, 'Nguyên Phó khoa Tim mạch, chuyên gia về điều trị tăng huyết áp và rối loạn nhịp tim.', 4),
                                                                                (5, 'BS. Nguyễn Minh Khôi', 'Bác sĩ CKI', 320000.00, 'Bác sĩ mẫu chuyên khám viêm tai giữa, viêm xoang và các bệnh lý đường hô hấp trên.', 1),
                                                                                (6, 'ThS. BS Trần Ngọc Lan', 'Thạc sĩ Y khoa', 380000.00, 'Bác sĩ mẫu có kinh nghiệm tư vấn sức khỏe tai mũi họng cho người lớn và trẻ em.', 1),
                                                                                (7, 'BS. Lê Hoàng Phúc', 'Bác sĩ CKI', 300000.00, 'Bác sĩ mẫu chuyên khám và tư vấn các bệnh lý tai mũi họng thường gặp.', 1),
                                                                                (8, 'BS. CKI Phạm Thu Hà', 'Bác sĩ CKI', 360000.00, 'Bác sĩ mẫu chuyên theo dõi sức khỏe trẻ em, tiêm chủng và tư vấn dinh dưỡng.', 2),
                                                                                (9, 'ThS. BS Nguyễn Gia Hân', 'Thạc sĩ Y khoa', 420000.00, 'Bác sĩ mẫu chuyên khám nhi tổng quát và theo dõi phát triển cho trẻ nhỏ.', 2),
                                                                                (10, 'BS. Trần Quốc Huy', 'Bác sĩ Nhi khoa', 330000.00, 'Bác sĩ mẫu tư vấn các bệnh hô hấp, tiêu hóa và chăm sóc sức khỏe trẻ em.', 2),
                                                                                (11, 'BS. CKI Võ Thanh Thảo', 'Bác sĩ CKI', 400000.00, 'Bác sĩ mẫu chuyên khám các bệnh da liễu phổ biến và tư vấn chăm sóc da.', 3),
                                                                                (12, 'ThS. BS Đỗ Anh Tuấn', 'Thạc sĩ Y khoa', 480000.00, 'Bác sĩ mẫu tư vấn điều trị mụn, viêm da và các vấn đề da liễu thường gặp.', 3),
                                                                                (13, 'BS. Nguyễn Bảo Trâm', 'Bác sĩ Da liễu', 390000.00, 'Bác sĩ mẫu chuyên tư vấn chăm sóc da và theo dõi các bệnh da liễu.', 3),
                                                                                (14, 'BS. CKI Phan Đức Long', 'Bác sĩ CKI', 430000.00, 'Bác sĩ mẫu chuyên khám tim mạch, tư vấn kiểm soát huyết áp và sức khỏe tim.', 4),
                                                                                (15, 'ThS. BS Lương Mai Phương', 'Thạc sĩ Y khoa', 490000.00, 'Bác sĩ mẫu chuyên theo dõi bệnh tim mạch và tư vấn phòng ngừa nguy cơ tim mạch.', 4),
                                                                                (16, 'BS. Nguyễn Thành Đạt', 'Bác sĩ Tim mạch', 410000.00, 'Bác sĩ mẫu tư vấn rối loạn nhịp tim, huyết áp và lối sống bảo vệ sức khỏe tim.', 4)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 4. Insert TimeSlots
INSERT INTO time_slots (id, start_time, end_time, display_text) VALUES
                                                                    (1, '08:00:00', '08:30:00', '08:00'),
                                                                    (2, '08:30:00', '09:00:00', '08:30'),
                                                                    (3, '09:00:00', '09:30:00', '09:00'),
                                                                    (4, '09:30:00', '10:00:00', '09:30'),
                                                                    (5, '10:00:00', '10:30:00', '10:00'),
                                                                    (6, '14:00:00', '14:30:00', '14:00'),
                                                                    (7, '14:30:00', '15:00:00', '14:30'),
                                                                    (8, '15:00:00', '15:30:00', '15:00')
ON DUPLICATE KEY UPDATE display_text=VALUES(display_text);
