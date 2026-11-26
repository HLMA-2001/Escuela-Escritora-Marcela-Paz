package cl.marcelapaz;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class RolesTest {
    @TempDir Path directorio;

    // Comprueba que una cuenta sin rol no pueda saltarse los botones de Android usando HTTP.
    @Test
    void separaLosPermisosEnLosDosServicios() {
        var mapper = new ObjectMapper();
        var docente = UUID.randomUUID();
        var inspector = UUID.randomUUID();
        var archivo = new ArchivoJson<>(directorio.resolve("roles.json"), Roles.Permiso[].class, mapper);
        archivo.guardar(List.of(new Roles.Permiso(Roles.huella(docente), Roles.Rol.DOCENTE),
            new Roles.Permiso(Roles.huella(inspector), Roles.Rol.INSPECTOR)));
        var roles = new Roles(directorio.toString(), mapper);
        var salas = new SalasController(directorio.toString(), mapper, roles);
        var reservas = new ReservasController(directorio.toString(), "http://127.0.0.1:8081", mapper, roles);
        var entrada = new SalasController.NuevaSala("Sala de prueba", 20, "Piso 1");
        assertEquals(Roles.Rol.SIN_ROL, salas.rol(UUID.randomUUID()));
        assertEquals(Roles.Rol.DOCENTE, salas.rol(docente));
        rechazado(() -> salas.crear(entrada, docente));
        rechazado(() -> salas.crear(entrada, null));
        var creada = salas.crear(entrada, inspector);
        rechazado(() -> salas.modificar(creada.id(), entrada, docente));
        var editada = salas.modificar(creada.id(), new SalasController.NuevaSala("Laboratorio", 24, "Piso 2"), inspector);
        assertEquals(creada.id(), editada.id());
        assertEquals(24, editada.capacidad());
        rechazado(() -> reservas.crear(null, inspector));
        rechazado(() -> reservas.crear(null, null));
        rechazado(() -> reservas.cancelar(UUID.randomUUID(), inspector));
    }

    private void rechazado(Runnable accion) {
        var error = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(403, error.getStatusCode().value());
    }
}

