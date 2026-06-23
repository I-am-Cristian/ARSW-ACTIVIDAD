package com.arsw.actividad;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.stream.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventConsumer {
    
    private final StringRedisTemplate redisTemplate;
    private static final String STREAM = "banco.transferencias";
    private static final String[] GRUPOS = {"fraude", "notificaciones", "auditoria"};
    private final Random random = new Random();
    
    @PostConstruct
    public void init() {
        for (String grupo : GRUPOS) {
            try {
                String grupoName = grupo + "-group";
                // Intentar crear el grupo
                redisTemplate.opsForStream().createGroup(STREAM, grupoName);
                log.info("Grupo creado: {}", grupoName);
            } catch (Exception e) {
                log.info("Grupo ya existe o error: {}", grupo + "-group");
            }
        }
    }
    
    @Scheduled(fixedDelay = 3000)
    public void consumirEventos() {
        for (String grupo : GRUPOS) {
            try {
                String grupoName = grupo + "-group";
                String consumidor = grupo + "-1";
                
                // Crear el consumidor y leer eventos
                Consumer consumer = Consumer.from(grupoName, consumidor);
                StreamOffset<String> offset = StreamOffset.create(STREAM, ReadOffset.from(">"));
                
                List<MapRecord<String, Object, Object>> records = 
                    redisTemplate.opsForStream().read(
                        consumer,
                        StreamReadOptions.empty().count(1).block(Duration.ofMillis(1000)),
                        offset
                    );
                
                for (MapRecord<String, Object, Object> record : records) {
                    Map<Object, Object> data = record.getValue();
                    String eventId = (String) data.get("eventId");
                    String transferId = (String) data.get("transferId");
                    
                    log.info("[{}-group] Procesando evento: {} - Transferencia: {}", 
                        grupo, eventId, transferId);
                    
                    // SIMULAR FALLA en auditoría (1 de cada 3 eventos)
                    if (grupo.equals("auditoria") && random.nextInt(3) == 0) {
                        log.warn("[{}-group] FALLA SIMULADA en {} (NO SE HACE ACK)", grupo, eventId);
                        log.warn("El evento quedará PENDIENTE para reprocesar");
                        continue; // NO ACK - evento queda pendiente
                    }
                    
                    // Procesamiento según grupo
                    switch (grupo) {
                        case "fraude":
                            log.info("[{}-group] Análisis de fraude para transferencia: {}", 
                                grupo, transferId);
                            break;
                        case "notificaciones":
                            log.info("[{}-group] Enviando notificación para transferencia: {}", 
                                grupo, transferId);
                            break;
                        case "auditoria":
                            log.info("[{}-group] Guardando en auditoría: {} - Monto: ${}", 
                                grupo, eventId, data.get("amount"));
                            break;
                    }
                    
                    // Confirmar procesamiento exitoso
                    redisTemplate.opsForStream().acknowledge(grupoName, STREAM, record.getId());
                    log.info("[{}-group] ACK confirmado para: {}", grupo, record.getId());
                }
                
            } catch (Exception e) {
                log.error("Error en consumidor {}: {}", grupo, e.getMessage());
            }
        }
    }
    
    // Método para ver eventos pendientes
    public void mostrarPendientes() {
        for (String grupo : GRUPOS) {
            try {
                String grupoName = grupo + "-group";
                // Obtener información de pendientes
                PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(STREAM, grupoName);
                log.info("[{}-group] Eventos pendientes: {}", grupo, 
                    summary != null ? summary.getTotalPendingMessages() : 0);
            } catch (Exception e) {
                log.info("[{}-group] No se pudo obtener pendientes", grupo);
            }
        }
    }
}