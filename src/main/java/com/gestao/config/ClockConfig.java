package com.gestao.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import java.time.*;
@Configuration
public class ClockConfig {
    @Bean public Clock clock(@Value("${app.time-zone}") String zone) { return Clock.system(ZoneId.of(zone)); }
    @Bean public ValidationConfigurationCustomizer validationClock(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
