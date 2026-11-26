package cl.marcelapaz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Component
class Roles {
    enum Rol { DOCENTE, INSPECTOR, SIN_ROL }
    record Permiso(String huella, Rol rol) {}
    private final ArchivoJson<Permiso> archivo;

    Roles(@Value("${app.data-dir}") String directorio, ObjectMapper mapper) {
        archivo = new ArchivoJson<>(Path.of(directorio, "roles.json"), Permiso[].class, mapper);
    }

    // La asignación se hace en el servidor. La app solo presenta su credencial.
    Rol consultar(UUID token) {
        if (token == null) return Rol.SIN_ROL;
        String huella = huella(token);
        return archivo.leer().stream().filter(permiso -> huella.equals(permiso.huella()))
            .map(Permiso::rol).findFirst().orElse(Rol.SIN_ROL);
    }

    void exigir(UUID token, Rol rol) {
        if (consultar(token) != rol) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta acción requiere el rol " + rol);
        }
    }

    static String huella(UUID token) {
        try {
            byte[] bytes = token.toString().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

