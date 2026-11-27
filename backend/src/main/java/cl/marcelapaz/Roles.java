package cl.marcelapaz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Component
class Roles {
    private static final String CORREO_INSPECTOR_PRINCIPAL = "fa.canova@duocuc.cl";
    private static final String HUELLA_INSPECTOR_PRINCIPAL = "3b5d883c7a5e8fcaf1bf59299f522d3bc1effd496bcb50f2ebb04f198fb064dc";

    enum Rol { DOCENTE, INSPECTOR, SIN_ROL }
    record Permiso(String huella, Rol rol) {}
    private final ArchivoJson<Permiso> archivo;

    Roles(@Value("${app.data-dir}") String directorio, ObjectMapper mapper) {
        archivo = new ArchivoJson<>(Path.of(directorio, "roles.json"), Permiso[].class, mapper);
    }

    // La asignación se hace en el servidor. La app solo presenta su credencial.
    synchronized Rol consultar(UUID token) {
        if (token == null) return Rol.SIN_ROL;
        String huella = huella(token);
        if (HUELLA_INSPECTOR_PRINCIPAL.equals(huella)) return Rol.INSPECTOR;
        return archivo.leer().stream().filter(permiso -> huella.equals(permiso.huella()))
            .map(Permiso::rol).findFirst().orElse(Rol.SIN_ROL);
    }

    synchronized void asignar(UUID inspector, UUID cuenta, Rol rol) {
        exigir(inspector, Rol.INSPECTOR);
        if (cuenta == null || rol == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa una credencial y un rol");
        }
        if (inspector.equals(cuenta) && rol != Rol.INSPECTOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar tu propio rol de Inspector");
        }
        String huella = huella(cuenta);
        if (HUELLA_INSPECTOR_PRINCIPAL.equals(huella) && rol != Rol.INSPECTOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El Inspector principal (" + CORREO_INSPECTOR_PRINCIPAL + ") tiene un rol permanente");
        }
        var permisos = new ArrayList<>(archivo.leer());
        permisos.removeIf(permiso -> huella.equals(permiso.huella()));
        permisos.add(new Permiso(huella, rol));
        archivo.guardar(permisos);
    }

    synchronized Rol registrarDocente(UUID cuenta) {
        if (cuenta == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa la credencial de la cuenta");
        }
        String huella = huella(cuenta);
        if (HUELLA_INSPECTOR_PRINCIPAL.equals(huella)) return Rol.INSPECTOR;
        var permisos = new ArrayList<>(archivo.leer());
        for (var permiso : permisos) {
            if (huella.equals(permiso.huella())) return permiso.rol();
        }
        permisos.add(new Permiso(huella, Rol.DOCENTE));
        archivo.guardar(permisos);
        return Rol.DOCENTE;
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

