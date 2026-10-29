package com.codegym.config;

import com.codegym.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import java.net.URI;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Autowired
    private com.codegym.security.CustomOAuth2UserService oAuth2UserService;

    @Autowired
    private com.codegym.repository.IUserRepository userRepository;

    @Bean
    public org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            java.util.Set<org.springframework.security.core.GrantedAuthority> mapped = new java.util.HashSet<>(authorities);
            for (org.springframework.security.core.GrantedAuthority auth : authorities) {
                if (auth instanceof org.springframework.security.oauth2.core.user.OAuth2UserAuthority oauth2Auth) {
                    java.util.Map<String, Object> attributes = oauth2Auth.getAttributes();
                    String email = (String) attributes.get("email");
                    if (email == null || email.isBlank()) {
                        email = (String) attributes.get("sub");
                    }
                    if (email != null) {
                        final String finalIdentifier = email;
                        userRepository.findByEmail(finalIdentifier)
                                .or(() -> userRepository.findByUsername(finalIdentifier))
                                .ifPresent(u -> u.getRoles().forEach(r ->
                                        mapped.add(new SimpleGrantedAuthority(r.getName().name()))
                                ));
                    }
                }
            }
            // Ensure ROLE_PATIENT is present if no staff/admin/doctor role is present
            boolean hasStaffOrDoctor = mapped.stream().anyMatch(a ->
                    a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_STAFF")
                            || a.getAuthority().equals("ROLE_DOCTOR"));
            if (!hasStaffOrDoctor) {
                mapped.add(new SimpleGrantedAuthority("ROLE_PATIENT"));
            }
            return mapped;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        AuthenticationSuccessHandler successHandler = (request, response, authentication) -> {
            if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_DOCTOR"))) {
                response.sendRedirect("/doctor/appointments");
            } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    || authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STAFF"))) {
                response.sendRedirect("/admin/appointments");
            } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_PATIENT"))) {
                SavedRequest savedRequest = requestCache.getRequest(request, response);
                String bookingUri = request.getContextPath() + "/appointments/book";
                URI savedUri = savedRequest == null ? null : URI.create(savedRequest.getRedirectUrl());
                if (savedUri != null && bookingUri.equals(savedUri.getPath())) {
                    requestCache.removeRequest(request, response);
                    String[] doctorIds = savedRequest.getParameterValues("doctorId");
                    String doctorId = doctorIds != null && doctorIds.length == 1
                            && doctorIds[0].matches("[0-9]{1,19}") ? doctorIds[0] : null;
                    response.sendRedirect(bookingUri + (doctorId == null ? "" : "?doctorId=" + doctorId));
                } else {
                    response.sendRedirect("/patient/appointments");
                }
            } else {
                response.sendRedirect("/doctors");
            }
        };
        http
                .authenticationProvider(authenticationProvider())
                .requestCache(cache -> cache.requestCache(requestCache))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", "/error",
                                "/doctors/**", "/api/appointments/**",
                                "/api/assistant/**", "/css/**", "/js/**", "/images/**",
                                "/payment/vnpay-return", "/payment/result").permitAll()
                        .requestMatchers("/payment/**").hasAuthority("ROLE_PATIENT")
                        .requestMatchers("/appointments/book").hasAuthority("ROLE_PATIENT")
                        .requestMatchers("/appointments/**").permitAll()
                        .requestMatchers("/doctor/**").hasAuthority("ROLE_DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/admin/appointments")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/admin/appointments/update-result")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/admin/appointments/update-details")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/patient/**").hasAuthority("ROLE_PATIENT")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .permitAll()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)
                                .userAuthoritiesMapper(userAuthoritiesMapper())
                        )
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        return http.build();
    }
}