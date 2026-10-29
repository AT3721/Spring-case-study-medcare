package com.codegym.service.auth;

import com.codegym.model.Role;
import com.codegym.model.RoleName;
import com.codegym.model.User;
import com.codegym.repository.IRoleRepository;
import com.codegym.repository.IUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthenticatedUserService {
    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;

    public AuthenticatedUserService(IUserRepository userRepository, IRoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public User findPatientForBooking(Authentication authentication) {
        if (!isAuthenticatedPatient(authentication)) {
            return null;
        }
        User user = findUser(authentication, true);
        if (user == null) {
            return null;
        }
        Role patientRole = roleRepository.findByName(RoleName.ROLE_PATIENT)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_PATIENT)));
        if (user.getRoles() == null) {
            user.setRoles(new HashSet<>());
        }
        if (user.getRoles().stream().noneMatch(role -> role.getName() == RoleName.ROLE_PATIENT)) {
            user.getRoles().add(patientRole);
            user = userRepository.save(user);
        }
        return user;
    }

    @Transactional
    public User findPatientForPortal(Authentication authentication) {
        User user = findUser(authentication, true);
        if (user == null) {
            throw new IllegalStateException("Không tìm thấy tài khoản bệnh nhân.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User findCurrentUser(Authentication authentication) {
        return findUser(authentication, false);
    }

    private User findUser(Authentication authentication, boolean createMissingPatient) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String principalName = authentication.getName();
        User user = userRepository.findByUsername(principalName)
                .or(() -> userRepository.findByEmail(principalName))
                .orElse(null);
        if (user != null || !(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            return user;
        }

        String email = oauthUser.getAttribute("email");
        if (email == null || email.isBlank()) {
            return null;
        }
        user = userRepository.findByEmail(email)
                .or(() -> userRepository.findByUsername(email))
                .orElse(null);
        if (user != null || !createMissingPatient) {
            return user;
        }

        String username = email.length() > 50 ? email.substring(0, 50) : email;
        String name = oauthUser.getAttribute("name");
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setFullName(name != null && !name.isBlank() ? name : email);
        newUser.setPassword("{noop}" + UUID.randomUUID());
        newUser.setEnabled(true);
        newUser.setRoles(new HashSet<>());
        return userRepository.save(newUser);
    }

    private boolean isAuthenticatedPatient(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_PATIENT".equals(authority.getAuthority()));
    }
}
