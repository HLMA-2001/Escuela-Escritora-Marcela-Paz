package cl.marcelapaz;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Profile("reservas")
@RestController
// Publica el calendario compartido desde el perfil reservas.
@RequestMapping("/api/reservas")
class ReservasController {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    record NuevaReserva(UUID salaId, LocalDate fecha, LocalTime inicio, LocalTime fin) {}
    record Sala(UUID id, String nombre) {}
    record Reserva(UUID id, UUID salaId, String sala, String fecha, String inicio, String fin) {}
    // Persiste el token con la reserva. La respuesta pública omite el token.
    record Registro(Reserva reserva, UUID propietario) {}
    record ReservaVisible(UUID id, UUID salaId, String sala, String fecha, String inicio, String fin, boolean propia) {}

    private final ArchivoJson<Registro> archivo;
    private final RestClient salas;
    private final Roles roles;

    ReservasController(@Value("${app.data-dir}") String directorio,
                       @Value("${app.salas-url}") String salasUrl, ObjectMapper mapper, Roles roles) {
        this.roles = roles;
        archivo = new ArchivoJson<>(Path.of(directorio, "reservas.json"), Registro[].class, mapper);
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(4));
        salas = RestClient.builder().baseUrl(salasUrl).requestFactory(factory).build();
    }

    // Muestra los bloques y marca como propios los que coinciden con la cabecera del usuario.
    @GetMapping
    synchronized List<ReservaVisible> listar(@RequestHeader(value = "X-Reserva-Token", required = false) UUID token) {
        return archivo.leer().stream().map(registro -> {
            var reserva = registro.reserva();
            return new ReservaVisible(reserva.id(), reserva.salaId(), reserva.sala(), reserva.fecha(),
                reserva.inicio(), reserva.fin(), registro.propietario().equals(token));
        }).toList();
    }

    // Consulta el catálogo de salas y guarda el bloque solo cuando la misma fecha está libre.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    synchronized Reserva crear(@RequestBody NuevaReserva entrada, @RequestHeader(value = "X-Reserva-Token", required = false) UUID token) {
        roles.exigir(token, Roles.Rol.DOCENTE);
        if (entrada.salaId() == null || entrada.fecha() == null || entrada.inicio() == null
            || entrada.fin() == null || !entrada.inicio().isBefore(entrada.fin())
            || !entrada.inicio().equals(entrada.inicio().withSecond(0).withNano(0))
            || !entrada.fin().equals(entrada.fin().withSecond(0).withNano(0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa fecha y un horario válido");
        }
        Sala sala;
        try {
            sala = salas.get().uri("/api/salas/{id}", entrada.salaId()).retrieve().body(Sala.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La sala no existe");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de salas no está disponible");
        }
        if (sala == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de salas devolvió una respuesta vacía");
        }
        var reservas = new ArrayList<>(archivo.leer());
        if (reservas.stream().map(Registro::reserva).anyMatch(reserva -> cruza(entrada, reserva))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La sala ya está reservada en ese bloque");
        }
        var reserva = new Reserva(UUID.randomUUID(), sala.id(), sala.nombre(),
            entrada.fecha().toString(), entrada.inicio().format(HORA), entrada.fin().format(HORA));
        reservas.add(new Registro(reserva, token));
        archivo.guardar(reservas);
        return reserva;
    }

    // Exige la credencial asociada a la reserva para cancelar.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    synchronized void cancelar(@PathVariable UUID id, @RequestHeader(value = "X-Reserva-Token", required = false) UUID token) {
        roles.exigir(token, Roles.Rol.DOCENTE);
        var reservas = new ArrayList<>(archivo.leer());
        boolean eliminada = reservas.removeIf(registro ->
            registro.reserva().id().equals(id) && registro.propietario().equals(token));
        if (!eliminada) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una reserva propia con ese identificador");
        }
        archivo.guardar(reservas);
    }

    // Solo compara bloques de la misma sala y fecha. Permite que dos bloques sean contiguos.
    static boolean cruza(NuevaReserva nueva, Reserva existente) {
        return nueva.salaId().equals(existente.salaId())
            && nueva.fecha().toString().equals(existente.fecha())
            && nueva.inicio().isBefore(LocalTime.parse(existente.fin()))
            && nueva.fin().isAfter(LocalTime.parse(existente.inicio()));
    }
}





