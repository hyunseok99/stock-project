package org.team4.sol_server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//import org.team4.sol_server.config.jwt.JwtAuthenticateFilter;
//import org.team4.sol_server.config.jwt.JwtTokenProvider;
//import org.team4.sol_server.domain.login.handler.OAuth2LoginSuccessHandler;
//import org.team4.sol_server.domain.login.service.CustomOAuth2UserService;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

//    private final CustomOAuth2UserService customOAuth2UserService;
//    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
//    private final JwtTokenProvider jwtTokenProvider;
//    private final DefaultOAuth2UserService defaultOAuth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) //CORS 설정을 SecurityFilterChain 내에서 적용
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/oauth2/**", "/oauth2/user", "/login/kakao").permitAll()
                        .requestMatchers("/auth/**","/api/**", "/actuator/prometheus", "/error").permitAll()
                        .anyRequest().authenticated()
                )
//                .addFilterBefore(new JwtAuthenticateFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class) //JWT 필터 추가
//                .logout(logout -> logout
//                        .logoutSuccessUrl("/login")
//                        .permitAll()
//                )
//                .oauth2Login(oauth2 -> oauth2
//                        .successHandler(oAuth2LoginSuccessHandler)
//                        .userInfoEndpoint(endpoint -> endpoint.userService(defaultOAuth2UserService))
//                        .successHandler(oAuth2LoginSuccessHandler)
//                        .userInfoEndpoint(userInfo -> userInfo
//                                .userService(customOAuth2UserService)
//                        )
//                )
                .csrf(csrf -> csrf.disable()) //CSRF 비활성화
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 로컬 테스트 용
//        configuration.setAllowedOrigins(List.of("http://localhost:5173","https://localhost:5173","http://localhost:3000","http://localhost:8090")); //프론트엔드 도메인 추가

//         배포용
        configuration.setAllowedOrigins(List.of(
                "https://shinhan.me",          // 1. 브라우저 접속용 (필수)
                "http://shinhan.me",           // 2. HTTP 접속 대응
                "http://java-backend:8090"    // 3. 컨테이너 간 내부 통신용 (stock_consumer 등)
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE","PATCH", "OPTIONS")); //OPTIONS 추가
        configuration.setAllowCredentials(true); //쿠키 허용
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type")); //헤더 노출 추가

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}