package com.codegym.service.auth;

import com.codegym.dto.auth.PatientRegistrationForm;
import com.codegym.model.Role;
import com.codegym.model.RoleName;
import com.codegym.model.User;
import com.codegym.repository.IRoleRepository;
import com.codegym.repository.IUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PatientRegistrationService {
    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public PatientRegistrationService(IUserRepository userRepository, IRoleRepository roleRepository,
                                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public List<RegistrationIssue> register(PatientRegistrationForm form) {
        String email = form.getEmail() == null ? "" : form.getEmail().trim().toLowerCase();
        List<RegistrationIssue> issues = new ArrayList<>();
        if (userRepository.existsByUsername(email) || userRepository.existsByEmailIgnoreCase(email)) {
            issues.add(new RegistrationIssue("email", "email.taken", "Email này đã được đăng ký."));
        }
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            issues.add(new RegistrationIssue("confirmPassword", "password.mismatch",
                    "Mật khẩu xác nhận không khớp."));
        }
        if (form.getPassword() != null
                && form.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            issues.add(new RegistrationIssue("password", "password.tooLong",
                    "Mật khẩu vượt quá giới hạn hỗ trợ."));
        }
        if (!issues.isEmpty()) {
            return issues;
        }

        Role patientRole = roleRepository.findByName(RoleName.ROLE_PATIENT)
                .orElseThrow(() -> new IllegalStateException("Vai trò bệnh nhân chưa được khởi tạo."));
        User user = new User(email, passwordEncoder.encode(form.getPassword()), form.getFullName().trim());
        user.setEmail(email);
        user.setRoles(Set.of(patientRole));
        userRepository.save(user);
        return List.of();
    }

    public record RegistrationIssue(String field, String code, String message) { }
}
