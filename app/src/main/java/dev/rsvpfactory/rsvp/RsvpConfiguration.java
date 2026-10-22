package dev.rsvpfactory.rsvp;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RsvpConfiguration {

    /** The application's time; tests replace it with a fixed or movable clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
