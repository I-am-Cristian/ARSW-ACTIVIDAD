package com.arsw.actividad;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {
    
    private final StringRedisTemplate redisTemplate;
    private static final String STREAM = "banco.transferencias";
    
    public void publicarTransferencia(Double monto, String desde, String hacia) {
        String eventId = "evt-" + UUID.randomUUID().toString().substring(0, 8);
        String transferId = "tr-" + UUID.randomUUID().toString().substring(0, 6);
        
        Map<String, String> evento = new HashMap<>();
        evento.put("eventType", "TransferenciaCreada");
        evento.put("eventId", eventId);
        evento.put("transferId", transferId);
        evento.put("from", desde);
        evento.put("to", hacia);
        evento.put("amount", String.valueOf(monto));
        evento.put("currency", "COP");
        evento.put("createdAt", LocalDateTime.now().toString());
        
        var recordId = redisTemplate.opsForStream().add(STREAM, evento);
        log.info("Evento publicado: {} | ID Redis: {}", eventId, recordId.getValue());
    }
}