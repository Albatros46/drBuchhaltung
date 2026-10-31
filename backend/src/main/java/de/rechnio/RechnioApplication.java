package de.rechnio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RechnioApplication {

    public static void main(String[] args) {
        SpringApplication.run(RechnioApplication.class, args);
    }
}
