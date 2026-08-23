package com.web.backend.config;

import com.web.backend.jwt.JwtAccessDeniedHandler;
import com.web.backend.jwt.JwtAuthenticationEntryPoint;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.oauth2.OAuth2AuthenticationSuccessHandler;
import com.web.backend.oauth2.OAuth2AuthenticationFailureHandler;
import com.web.backend.service.CustomOAuth2UserService;
import com.web.backend.service.UserServiceDetail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
@Slf4j(topic = "SECURITY-CONFIG")
public class SecurityConfig {

        private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

        private final OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

        private final CustomOAuth2UserService customOAuth2UserService;

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        private final UserServiceDetail userServiceDetail;

        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

        private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

        private final PasswordEncoder passwordEncoder;

        @Value("${app.cors.allowed-origins}")
        private String allowedOrigins;

        @Bean
        @SuppressWarnings("java:S4502")
        public SecurityFilterChain configure(HttpSecurity http) throws Exception {
                http.csrf(AbstractHttpConfigurer::disable)
                                .headers(headers -> headers.frameOptions(FrameOptionsConfig::disable))
                                .cors(cors -> cors.configurationSource(addConfigurationSource()))
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                                                .accessDeniedHandler(jwtAccessDeniedHandler))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/auth/logout", "/api/auth/logout-all-devices").authenticated()
                                                .requestMatchers("/ws/**", "/oauth2/**", "/login/oauth2/**", "/api/auth/**").permitAll()
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authenticationProvider(authenticationProvider())
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                 .userService(customOAuth2UserService))
                                                .successHandler(oAuth2AuthenticationSuccessHandler)
                                                .failureHandler(oauth2AuthenticationFailureHandler));

                return http.build();
        }

        @Bean
        public AuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(userServiceDetail);
                daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);

                return daoAuthenticationProvider;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
                        throws Exception {
                return authenticationConfiguration.getAuthenticationManager();
        }

        @Bean
        public WebSecurityCustomizer ignoreResources() {
                return webSecurity -> webSecurity
                                .ignoring()
                                .requestMatchers("/actuator/**", "/v3/**", "/webjars/**", "/swagger-ui/**",
                                                "/favicon.ico",
                                                "/swagger-ui*/*swagger-initializer.js");
        }

        @Bean
        public CorsConfigurationSource addConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("*"));
                configuration.setAllowCredentials(true);
                configuration.setExposedHeaders(Arrays.asList("Set-Cookie"));

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

}
