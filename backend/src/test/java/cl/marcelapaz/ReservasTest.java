package cl.marcelapaz;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Ejercita los casos límite del cruce sin iniciar Spring ni hacer llamadas de red.
class ReservasTest {
    private final UUID sala = UUID.randomUUID();
    private final ReservasController.Reserva existente = new ReservasController.Reserva(
        UUID.randomUUID(), sala, "Laboratorio", "2026-12-10", "09:00", "10:00");

    @Test
    void bloqueSuperpuestoEnLaMismaSalaYFecha() {
        assertTrue(cruza(sala, "2026-12-10", "09:30", "10:30"));
    }

    @Test
    void aceptaBloquesContiguos() {
        assertFalse(cruza(sala, "2026-12-10", "10:00", "11:00"));
    }

    @Test
    void permiteElMismoBloqueEnOtraFecha() {
        assertFalse(cruza(sala, "2026-12-11", "09:00", "10:00"));
    }

    @Test
    void permiteElMismoBloqueEnOtraSala() {
        assertFalse(cruza(UUID.randomUUID(), "2026-12-10", "09:00", "10:00"));
    }

    private boolean cruza(UUID id, String fecha, String inicio, String fin) {
        return ReservasController.cruza(new ReservasController.NuevaReserva(
            id, LocalDate.parse(fecha), LocalTime.parse(inicio), LocalTime.parse(fin)), existente);
    }
}

