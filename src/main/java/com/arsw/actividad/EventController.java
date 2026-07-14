package com.arsw.actividad;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
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
        Double monto = Double.valueOf(data.get("monto").toString());
        String desde = (String) data.get("desde");
        String hacia = (String) data.get("hacia");
        
        publisher.publicarTransferencia(monto, desde, hacia);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Transferencia publicada");
        return response;
    }
    
    @GetMapping("/pendientes")
    public Map<String, String> verPendientes() {
        consumer.mostrarPendientes();
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Ver logs para detalles de pendientes");
        return response;
    }
}
