package org.spring.passhalo.user.config;

import org.spring.passhalo.user.repository.UserRepository;
import org.spring.passhalo.user.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var cors = new CorsConfiguration();
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cors.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173",
                frontendBaseUrl.replaceAll("/+$", "")));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmailIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    }
    @Bean
    @Lazy
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws AuthenticationException {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthFilter) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth


                        .requestMatchers("/user/login", "/user/register",
                                "/user/recover-password", "/user/reset-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/user/staff-signup", "/user/staff-register").denyAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/bookings", "/bookings/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/bookings/events/{eventId}/{uuid}/resend-qr").authenticated()
                        .requestMatchers(HttpMethod.POST, "/marketing/unsubscribe").permitAll()
                        .requestMatchers(HttpMethod.POST, "/marketing/brevo/webhook/{ownerId}").permitAll()
                        .requestMatchers("/marketing/brevo", "/marketing/brevo/").hasRole("ADMIN")
                        .requestMatchers("/staff-access/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/bookings/{uuid}",
                                "/bookings/events/{eventId}",
                                "/bookings/event/{eventId}/email/{email}",
                                "/bookings/bookingId/{bookingId}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/bookings/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/user/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/user/me/change-password").authenticated()
                        .requestMatchers(HttpMethod.POST, "/user/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/user", "/user/search", "/user/{id}").denyAll()
                        .requestMatchers(HttpMethod.DELETE, "/user/**").denyAll()
                        .requestMatchers(HttpMethod.PATCH, "/user/{id}/change-password").denyAll()
                        .requestMatchers(HttpMethod.POST, "/events", "/events/").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/events/{eventId}/invitations", "/invitations/accept").authenticated()
                        .requestMatchers(HttpMethod.POST, "/events/{eventId}/join-code", "/join-requests").authenticated()
                        .requestMatchers(HttpMethod.POST, "/events/{eventId}/staff-code").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/{eventId}/staff-requests").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/events/{eventId}/staff-requests/{requestId}/approve",
                                "/events/{eventId}/staff-requests/{requestId}/reject").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/{eventId}/join-requests", "/join-requests/my").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/events/{eventId}/join-requests/{requestId}/approve",
                                "/events/{eventId}/join-requests/{requestId}/reject").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/{eventId}/invitations").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/events/{eventId}/invitations/{invitationId}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/{eventId}/memberships").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/events/{eventId}/memberships/{membershipId}/revoke").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/events/{id}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/events/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/my-events", "/events/{id}/dashboard").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/bookings/{uuid}").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/events/{id}/walk-in", "/events/{id}/walk-in/decrement", "/events/{id}/close").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/bookings/check-in/{uuid}").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/bookings/events/{eventId}/check-in/{uuid}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events", "/events/", "/events/{id}").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
