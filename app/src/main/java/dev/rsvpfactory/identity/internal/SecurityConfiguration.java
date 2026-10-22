package dev.rsvpfactory.identity.internal;

import dev.rsvpfactory.problems.RuleBrokenException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Sign-in is required by default. The public endpoints are the explicit list below: a visitor can view a group and ask
 * whether the application is healthy, nothing else.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        return http.authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.GET, "/groups/{groupId}")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(notAuthenticated(exceptionResolver)))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(notAuthenticated(exceptionResolver)))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    private static AuthenticationEntryPoint notAuthenticated(HandlerExceptionResolver exceptionResolver) {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"rsvp-factory\"");
            exceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new RuleBrokenException("not-authenticated", HttpStatus.UNAUTHORIZED, "Sign in to do this."));
        };
    }
}
