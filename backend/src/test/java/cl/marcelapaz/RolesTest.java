package cl.marcelapaz;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
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
    void separaLosPermisosEnLosDosServicios() throws IOException {
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
        var reserva = new ReservasController.Reserva(UUID.randomUUID(), creada.id(), creada.nombre(),
            "2026-11-27", "09:00", "10:00");
        var jsonAnterior = mapper.createArrayNode();
        jsonAnterior.addObject().put("propietario", docente.toString()).set("reserva", mapper.valueToTree(reserva));
        java.nio.file.Files.writeString(directorio.resolve("reservas.json"), mapper.writeValueAsString(jsonAnterior));
        assertTrue(reservas.listar(null).getFirst().aprobada());
        rechazado(() -> reservas.modificar(reserva.id(), null, docente));
        reservas.cancelar(reserva.id(), inspector);
        assertTrue(reservas.listar(docente).isEmpty());

        var cuenta = UUID.randomUUID();
        var cambio = new SalasController.CambioRol(cuenta, Roles.Rol.DOCENTE);
        rechazado(() -> salas.asignarRol(cambio, docente));
        salas.asignarRol(cambio, inspector);
        assertEquals(Roles.Rol.DOCENTE, new Roles(directorio.toString(), mapper).consultar(cuenta));
        var autoRevocacion = new SalasController.CambioRol(inspector, Roles.Rol.SIN_ROL);
        var error = assertThrows(ResponseStatusException.class, () -> salas.asignarRol(autoRevocacion, inspector));
        assertEquals(409, error.getStatusCode().value());
    }

    private void rechazado(Runnable accion) {
        var error = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void altaDocenteEsIdempotenteYRespetaRevocaciones() {
        var mapper = new ObjectMapper();
        var inspector = UUID.randomUUID();
        var cuenta = UUID.randomUUID();
        var permisos = new ArchivoJson<>(directorio.resolve("roles.json"), Roles.Permiso[].class, mapper);
        permisos.guardar(List.of(new Roles.Permiso(Roles.huella(inspector), Roles.Rol.INSPECTOR)));
        var roles = new Roles(directorio.toString(), mapper);
        assertEquals(Roles.Rol.DOCENTE, roles.registrarDocente(cuenta));
        assertEquals(Roles.Rol.DOCENTE, roles.registrarDocente(cuenta));
        assertEquals(Roles.Rol.INSPECTOR, roles.registrarDocente(inspector));
        roles.asignar(inspector, cuenta, Roles.Rol.SIN_ROL);
        assertEquals(Roles.Rol.SIN_ROL, new Roles(directorio.toString(), mapper).registrarDocente(cuenta));
    }

    @Test
    void inspectorReprogramaSinCambiarElPropietario() throws IOException {
        var mapper = new ObjectMapper();
        var docente = UUID.randomUUID();
        var inspector = UUID.randomUUID();
        var sala = UUID.randomUUID();
        var permisos = new ArchivoJson<>(directorio.resolve("roles.json"), Roles.Permiso[].class, mapper);
        permisos.guardar(List.of(new Roles.Permiso(Roles.huella(docente), Roles.Rol.DOCENTE),
            new Roles.Permiso(Roles.huella(inspector), Roles.Rol.INSPECTOR)));

        var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/salas/" + sala, exchange -> {
            var body = ("{\"id\":\"" + sala + "\",\"nombre\":\"Biblioteca\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var respuesta = exchange.getResponseBody()) {
                respuesta.write(body);
            }
        });
        servidor.start();
        try {
            var roles = new Roles(directorio.toString(), mapper);
            var reservas = new ReservasController(directorio.toString(),
                "http://127.0.0.1:" + servidor.getAddress().getPort(), mapper, roles);
            var fecha = LocalDate.of(2026, 11, 30);
            var entrada = new ReservasController.NuevaReserva(sala, fecha, LocalTime.of(9, 0), LocalTime.of(10, 0));
            var original = reservas.crear(entrada, docente);
            assertFalse(reservas.listar(docente).getFirst().aprobada());
            assertTrue(reservas.listar(null).isEmpty());
            rechazado(() -> reservas.aprobar(original.id(), docente));
            var otra = reservas.crear(entrada, docente);
            rechazado(() -> reservas.rechazar(otra.id(), docente));
            reservas.aprobar(original.id(), inspector);
            assertTrue(reservas.listar(null).getFirst().aprobada());
            var cruce = assertThrows(ResponseStatusException.class, () -> reservas.aprobar(otra.id(), inspector));
            assertEquals(409, cruce.getStatusCode().value());
            reservas.rechazar(otra.id(), inspector);
            assertTrue(reservas.listar(docente).stream().anyMatch(r -> r.id().equals(otra.id()) && r.rechazada()));
            assertEquals(1, reservas.listar(null).size());
            reservas.cancelar(otra.id(), docente);
            var cambio = new ReservasController.NuevaReserva(sala, fecha, LocalTime.of(9, 30), LocalTime.of(10, 30));
            var reprogramada = reservas.modificar(original.id(), cambio, inspector);
            assertEquals(original.id(), reprogramada.id());
            assertEquals("09:30", reprogramada.inicio());
            assertTrue(reservas.listar(docente).getFirst().propia());
            assertFalse(reservas.listar(inspector).getFirst().propia());

            var bloqueSiguiente = new ReservasController.NuevaReserva(sala, fecha, LocalTime.of(10, 30), LocalTime.of(11, 0));
            var siguiente = reservas.crear(bloqueSiguiente, docente);
            reservas.aprobar(siguiente.id(), inspector);
            var conflicto = assertThrows(ResponseStatusException.class,
                () -> reservas.modificar(original.id(), bloqueSiguiente, inspector));
            assertEquals(409, conflicto.getStatusCode().value());
            var conservada = reservas.listar(docente).stream().filter(r -> r.id().equals(original.id())).findFirst().orElseThrow();
            assertEquals("09:30", conservada.inicio());
            assertEquals("10:30", conservada.fin());
            assertTrue(conservada.aprobada());
            assertTrue(conservada.propia());
        } finally {
            servidor.stop(0);
        }
    }
}

