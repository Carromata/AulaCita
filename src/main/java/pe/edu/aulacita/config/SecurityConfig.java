package pe.edu.aulacita.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import pe.edu.aulacita.security.LoginRateLimitFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JdbcTemplate jdbcTemplate;

    public SecurityConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${aulacita.cookie-secure:false}") boolean secureCookies) throws Exception {
        AuthenticationSuccessHandler loginSuccessHandler = (request, response, authentication) -> {
            jdbcTemplate.update("UPDATE dbo.Usuario SET UltimoAcceso = SYSUTCDATETIME() WHERE Correo = ?",
                    authentication.getName());
            response.sendRedirect(request.getContextPath() + "/panel");
        };
        AuthenticationEntryPoint apiAuthenticationEntryPoint = (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":401,\"message\":\"Se requiere una sesión autenticada.\",\"details\":[]}");
        };
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Strict"));
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/sobre", "/verificar-alumno", "/css/**", "/js/login.js",
                                "/js/verificar.js", "/login", "/error", "/health",
                                "/recuperar-contrasena", "/restablecer-contrasena",
                                "/api/v1/auth/recuperar-contrasena", "/api/v1/auth/restablecer-contrasena",
                                "/media/institucion/**", "/api/v1/estudiantes/verificar", "/api/v1/institucion")
                        .permitAll()
                        .requestMatchers("/api/v1/reportes/**")
                        .hasAnyRole("ADMINISTRADOR", "SUPERADMINISTRADOR", "DIRECTOR", "SECRETARIA")
                        .requestMatchers("/gestion", "/api/v1/admin/**")
                        .hasAnyRole("ADMINISTRADOR", "SUPERADMINISTRADOR", "SECRETARIA")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(loginSuccessHandler)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(2))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(apiAuthenticationEntryPoint,
                                new AntPathRequestMatcher("/api/**"))
                        .accessDeniedHandler((request, response, denied) -> {
                            if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                                response.setStatus(HttpStatus.FORBIDDEN.value());
                                response.setContentType("application/json");
                                response.setCharacterEncoding("UTF-8");
                                response.getWriter().write("{\"status\":403,\"message\":\"No tiene permiso para esta operación.\",\"details\":[]}");
                            } else {
                                response.sendRedirect(request.getContextPath() + "/acceso-denegado");
                            }
                        }))
                .addFilterBefore(new LoginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
