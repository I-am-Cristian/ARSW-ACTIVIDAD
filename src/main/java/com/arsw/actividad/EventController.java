package com.arsw.actividad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class EventController {

    private final EventPublisher publisher;
    private final EventConsumer consumer;

    public EventController(EventPublisher publisher, EventConsumer consumer) {
        this.publisher = publisher;
        this.consumer = consumer;
    }

    @PostMapping("/transferencia")
    public Map<String, String> crearTransferencia(@RequestBody Map<String, Object> data) {
        String eventId = publisher.publicarTransferencia(
                Double.valueOf(data.get("monto").toString()),
                data.get("desde").toString(),
                data.get("hacia").toString());
        return Map.of("status", "success", "message", "Transferencia publicada", "eventId", eventId);
    }

    @GetMapping("/pendientes")
    public Map<String, Long> verPendientes() {
        return consumer.obtenerPendientes();
    }

    @PostMapping("/reprocesar/auditoria")
    public Map<String, Object> reprocesarAuditoria() {
        int cantidad = consumer.reprocesarAuditoria();
        return Map.of("status", "ok", "eventosReprocesados", cantidad);
    }

    @GetMapping("/auditoria")
    public Map<Object, Object> verAuditoria() {
        return consumer.obtenerAuditoria();
    }
}
