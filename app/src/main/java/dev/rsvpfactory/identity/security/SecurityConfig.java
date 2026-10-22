package dev.rsvpfactory.identity.security;

import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.problems.Refusal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** HTTP Basic against the seeded members. Reads are public; every write needs a signed-in member. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.GET, "/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .build();
    }

    /** Hands the 401 to Spring MVC, so it is rendered by the same handler as every other refusal. */
    @Bean
    AuthenticationEntryPoint unauthenticatedEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (request, response, exception) -> resolver.resolveException(
                request,
                response,
                null,
                new Refusal(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to do this."));
    }

    @Bean
    UserDetailsService memberDetails(JdbcClient jdbc) {
        return username -> jdbc.sql("SELECT id, username, password_hash FROM member WHERE username = :username")
                .param("username", username)
                .query((rs, row) -> new MemberPrincipal(
                        new Member(rs.getLong("id"), rs.getString("username")), rs.getString("password_hash")))
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("No member " + username));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
