package dev.rsvpfactory;

import org.springframework.boot.SpringApplication;

public class TestRsvpFactoryApplication {

    public static void main(String[] args) {
        SpringApplication.from(RsvpFactoryApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
