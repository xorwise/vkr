package ru.vkr.transport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ru.vkr.transport")
public class TransportOntologyApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransportOntologyApplication.class, args);
    }
}
