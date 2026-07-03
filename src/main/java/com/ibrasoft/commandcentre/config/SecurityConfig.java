package com.ibrasoft.commandcentre.config;

import com.ibrasoft.commandcentre.security.DiscordGuildFilter;
import com.ibrasoft.commandcentre.security.DiscordOAuthSuccessHandler;
import com.ibrasoft.commandcentre.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final DiscordGuildFilter discordGuildFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DiscordOAuthSuccessHandler discordOAuthSuccessHandler;
    
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configure(http)) // Enable CORS support in Spring Security
            .csrf(csrf -> csrf.disable()) // Disable CSRF for API usage
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login/**", "/oauth2/**", "/error", "/api/auth/**", "/api/workload/**", "/v3/api-docs").permitAll()

                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(discordOAuthSuccessHandler)
                .failureUrl(frontendUrl + "/unauthorized?reason=auth_failed")
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(discordGuildFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
