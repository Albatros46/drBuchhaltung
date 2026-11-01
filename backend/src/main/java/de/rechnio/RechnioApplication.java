package de.rechnio;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RechnioApplication {

    public static void main(String[] args) {
        // Gestartet im Projektordner (z. B. IntelliJ ohne Arbeitsverzeichnis "backend"): dieselbe Datenbank
        // wie bei "cd backend && mvn spring-boot:run" verwenden statt einer neuen, leeren unter ./data.
        if (System.getProperty("rechnio.data-dir") == null && Files.exists(Path.of("backend", "pom.xml"))) {
            // H2 verlangt absolute Pfade oder ./name
            System.setProperty("rechnio.data-dir",
                    Path.of("backend", "data").toAbsolutePath().toString().replace('\\', '/'));
        }
        SpringApplication.run(RechnioApplication.class, args);
    }
}
