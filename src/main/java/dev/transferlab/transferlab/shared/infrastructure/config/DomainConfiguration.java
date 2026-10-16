package dev.transferlab.transferlab.shared.infrastructure.config;

import dev.transferlab.transferlab.transfers.domain.TransferPlanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TransferPlanner transferPlanner() {
        return new TransferPlanner();
    }
}