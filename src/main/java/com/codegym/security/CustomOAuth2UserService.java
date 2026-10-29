package com.codegym.security;

import com.codegym.model.Role;
import com.codegym.model.RoleName;
import com.codegym.model.User;
import com.codegym.repository.IRoleRepository;
import com.codegym.repository.IUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;

    public CustomOAuth2UserService(IUserRepository userRepository, IRoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        if (email == null || email.isBlank()) {
            email = oAuth2User.getName();
        }

        // Process or create user in local database
        User user = processOAuthUser(email, name);

        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority(r.getName().name()))
                .collect(Collectors.toSet());

        // Always use "email" as the principal name for Google if available, or fallback to "sub"
        String userNameAttributeName = "email";
        if (attributes.get("email") == null) {
            userNameAttributeName = userRequest.getClientRegistration()
                    .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
            if (userNameAttributeName == null || userNameAttributeName.isBlank()) {
                userNameAttributeName = "sub";
            }
        }

        return new DefaultOAuth2User(authorities, attributes, userNameAttributeName);
    }

    private User processOAuthUser(String email, String name) {
        Role patientRole = roleRepository.findByName(RoleName.ROLE_PATIENT)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_PATIENT)));

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User existing = userOptional.get();
            boolean updated = false;
            if (existing.getFullName() == null || existing.getFullName().isBlank()) {
                existing.setFullName(name != null ? name : email);
                updated = true;
            }
            if (existing.getRoles() == null || existing.getRoles().isEmpty()) {
                existing.setRoles(new HashSet<>(Collections.singletonList(patientRole)));
                updated = true;
            } else if (existing.getRoles().stream().noneMatch(r -> r.getName() == RoleName.ROLE_PATIENT
                    || r.getName() == RoleName.ROLE_ADMIN || r.getName() == RoleName.ROLE_STAFF || r.getName() == RoleName.ROLE_DOCTOR)) {
                existing.getRoles().add(patientRole);
                updated = true;
            }
            if (updated) {
                userRepository.save(existing);
            }
            return existing;
        }

        // Fallback check by username
        Optional<User> byUsername = userRepository.findByUsername(email);
        if (byUsername.isPresent()) {
            User existing = byUsername.get();
            boolean updated = false;
            if (existing.getEmail() == null || existing.getEmail().isBlank()) {
                existing.setEmail(email);
                updated = true;
            }
            if (existing.getRoles() == null || existing.getRoles().isEmpty()) {
                existing.setRoles(new HashSet<>(Collections.singletonList(patientRole)));
                updated = true;
            }
            if (updated) {
                userRepository.save(existing);
            }
            return existing;
        }

        // Create new patient account from Google login
        User newUser = new User();
        String candidateUsername = email.length() > 50 ? email.substring(0, 50) : email;
        if (userRepository.findByUsername(candidateUsername).isPresent()) {
            candidateUsername = "g_" + System.currentTimeMillis();
            if (candidateUsername.length() > 50) {
                candidateUsername = candidateUsername.substring(0, 50);
            }
        }
        newUser.setUsername(candidateUsername);
        newUser.setEmail(email);
        newUser.setFullName(name != null && !name.isBlank() ? name : email);
        newUser.setPassword("{noop}" + UUID.randomUUID());
        newUser.setEnabled(true);
        newUser.setRoles(new HashSet<>(Collections.singletonList(patientRole)));

        return userRepository.save(newUser);
    }
}
