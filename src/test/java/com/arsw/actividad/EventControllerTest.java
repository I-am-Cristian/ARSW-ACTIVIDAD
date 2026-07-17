package com.arsw.actividad;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventControllerTest {

    @Test
    void publicaUnaTransferenciaYRetornaElEventId() {
        EventPublisher publisher = new EventPublisher(null) {
            @Override
            public String publicarTransferencia(Double monto, String desde, String hacia) {
                assertEquals(150000.0, monto);
                assertEquals("cta-101", desde);
                assertEquals("cta-202", hacia);
                return "evt-1001";
            }
        };
        EventController controller = new EventController(publisher, consumidorVacio());

        Map<String, String> response = controller.crearTransferencia(Map.of(
                "monto", 150000,
                "desde", "cta-101",
                "hacia", "cta-202"));

        assertEquals("success", response.get("status"));
        assertEquals("evt-1001", response.get("eventId"));
    }

    @Test
    void devuelvePendientesPorGrupo() {
        Map<String, Long> esperado = Map.of(
                "fraude-group", 0L,
                "notificaciones-group", 0L,
                "auditoria-group", 1L);
        EventConsumer consumer = new EventConsumer(null) {
            @Override
            public Map<String, Long> obtenerPendientes() {
                return esperado;
            }
        };
        EventController controller = new EventController(publicadorVacio(), consumer);

        assertEquals(esperado, controller.verPendientes());
    }

    @Test
    void permiteReprocesarLaAuditoria() {
        EventConsumer consumer = new EventConsumer(null) {
            @Override
            public int reprocesarAuditoria() {
                return 1;
            }
        };
        EventController controller = new EventController(publicadorVacio(), consumer);

        Map<String, Object> response = controller.reprocesarAuditoria();

        assertEquals("ok", response.get("status"));
        assertEquals(1, response.get("eventosReprocesados"));
    }

    private static EventPublisher publicadorVacio() {
        return new EventPublisher(null);
    }

    private static EventConsumer consumidorVacio() {
        return new EventConsumer(null);
    }
}
