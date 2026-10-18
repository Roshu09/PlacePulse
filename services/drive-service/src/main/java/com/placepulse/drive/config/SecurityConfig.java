package com.placepulse.drive.config;

import com.placepulse.drive.security.JwtAuthFilter;
import com.placepulse.drive.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // missing/invalid token -> 401 (Spring's default would be 403)
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // company logos are loaded by <img> tags, which cannot send a token
                        .requestMatchers(HttpMethod.GET, "/api/drives/*/logo").permitAll()
                        // TPR only
                        .requestMatchers(HttpMethod.POST, "/api/drives").hasRole("TPR")
                        .requestMatchers(HttpMethod.PUT, "/api/drives/*").hasRole("TPR")
                        .requestMatchers(HttpMethod.DELETE, "/api/applications/*").hasRole("TPR")
                        .requestMatchers(HttpMethod.PATCH, "/api/drives/*/status").hasRole("TPR")
                        .requestMatchers(HttpMethod.GET, "/api/drives/*/applications").hasRole("TPR")
                        .requestMatchers(HttpMethod.GET, "/api/drives/*/applications.csv").hasRole("TPR")
                        .requestMatchers(HttpMethod.PATCH, "/api/applications/stage").hasRole("TPR")
                        .requestMatchers(HttpMethod.PATCH, "/api/applications/*/stage").hasRole("TPR")
                        // students only
                        .requestMatchers(HttpMethod.POST, "/api/drives/*/apply").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/applications/mine").hasRole("STUDENT")
                        // everything else needs a valid login
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Tokens come from auth-service, so there is no local user store. Defining this bean stops
    // Spring Boot from generating a random default password and logging it at startup.
    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Not used: authentication is JWT based");
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
