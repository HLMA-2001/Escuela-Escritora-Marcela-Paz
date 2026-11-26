package cl.marcelapaz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Arranca Spring Boot con el perfil del microservicio elegido en la terminal.
@SpringBootApplication
public class GestionEscolar {
    public static void main(String[] args) {
        SpringApplication.run(GestionEscolar.class, args);
    }
}

