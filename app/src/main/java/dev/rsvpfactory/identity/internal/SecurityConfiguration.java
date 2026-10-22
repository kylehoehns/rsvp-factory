package dev.rsvpfactory.identity.internal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Reads are public; every write needs a signed-in member (HTTP Basic). */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        // Refusals go through the shared problem-details advice, so a 401 looks like every other refusal.
        AuthenticationEntryPoint unauthenticated = (request, response, exception) -> {
            response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"rsvp-factory\"");
            exceptionResolver.resolveException(request, response, null, exception);
        };
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.GET, "/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthenticated))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthenticated))
                .build();
    }
}
