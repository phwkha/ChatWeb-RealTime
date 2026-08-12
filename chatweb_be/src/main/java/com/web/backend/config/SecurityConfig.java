package com.web.backend.config;

import com.web.backend.jwt.JwtAccessDeniedHandler;
import com.web.backend.jwt.JwtAuthenticationEntryPoint;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.oauth2.OAuth2AuthenticationSuccessHandler;
import com.web.backend.oauth2.OAuth2AuthenticationFailureHandler;
import com.web.backend.service.util.CustomOAuth2UserService;
import com.web.backend.service.util.UserServiceDetail;
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

        private static final String ACTUATOR_STRING = "/actuator/**";
        private static final String API_AUTH_LOGOUT_ALL_DEVICES_STRING = "/api/auth/logout-all-devices";
        private static final String API_AUTH_LOGOUT_STRING = "/api/auth/logout";
        private static final String API_AUTH_STRING = "/api/auth/**";
        private static final String DELETE_STRING = "DELETE";
        private static final String FAVICON_ICO_STRING = "/favicon.ico";
        private static final String GET_STRING = "GET";
        private static final String LOGIN_OAUTH2_STRING = "/login/oauth2/**";
        private static final String OAUTH2_STRING = "/oauth2/**";
        private static final String OPTIONS_STRING = "OPTIONS";
        private static final String POST_STRING = "POST";
        private static final String PUT_STRING = "PUT";
        private static final String SET_COOKIE_STRING = "Set-Cookie";
        private static final String SWAGGER_UI_STRING = "/swagger-ui/**";
        private static final String SWAGGER_UI_SWAGGER_INITIALIZER_JS_STRING = "/swagger-ui*/*swagger-initializer.js";
        private static final String V3_STRING = "/v3/**";
        private static final String WEBJARS_STRING = "/webjars/**";
        private static final String WS_STRING = "/ws/**";

        @Bean
        @SuppressWarnings("java:S4502")
        public SecurityFilterChain configure(HttpSecurity http) throws Exception {
                http.csrf(AbstractHttpConfigurer::disable)
                                .headers(headers -> headers.frameOptions(FrameOptionsConfig::disable))
                                .cors(cors -> cors.configurationSource(addConfigurationSource()))
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                                                .accessDeniedHandler(jwtAccessDeniedHandler))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(API_AUTH_LOGOUT_STRING).authenticated()
                                                .requestMatchers(API_AUTH_LOGOUT_ALL_DEVICES_STRING).authenticated()
                                                .requestMatchers(WS_STRING).permitAll()
                                                .requestMatchers(OAUTH2_STRING, LOGIN_OAUTH2_STRING).permitAll()
                                                .requestMatchers(API_AUTH_STRING).permitAll()
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
                                .requestMatchers(ACTUATOR_STRING, V3_STRING, WEBJARS_STRING, SWAGGER_UI_STRING,
                                                FAVICON_ICO_STRING,
                                                SWAGGER_UI_SWAGGER_INITIALIZER_JS_STRING);
        }

        @Bean
        public CorsConfigurationSource addConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
                configuration
                                .setAllowedMethods(Arrays.asList(GET_STRING, POST_STRING, PUT_STRING, DELETE_STRING,
                                                OPTIONS_STRING));
                configuration.setAllowedHeaders(Arrays.asList("*"));
                configuration.setAllowCredentials(true);
                configuration.setExposedHeaders(Arrays.asList(SET_COOKIE_STRING));

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

}
