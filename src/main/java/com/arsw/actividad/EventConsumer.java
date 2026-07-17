package com.arsw.actividad;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);
    private static final String STREAM = "banco.transferencias";
    private static final String[] GRUPOS = {"fraude", "notificaciones", "auditoria"};
    private static final String AUDITORIA_HASH = "auditoria:transferencias";
    private final StringRedisTemplate redisTemplate;

    public EventConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        if (Boolean.FALSE.equals(redisTemplate.hasKey(STREAM))) {
            redisTemplate.opsForStream().add(STREAM, Map.of("eventType", "StreamCreado.v1"));
        }
        for (String grupo : GRUPOS) {
            try {
                redisTemplate.opsForStream().createGroup(STREAM, grupo + "-group");
                log.info("Grupo creado: {}-group", grupo);
            } catch (Exception e) {
                log.debug("El grupo {}-group ya existe", grupo);
            }
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void consumirEventosNuevos() {
        for (String grupo : GRUPOS) {
            consumir(grupo, ReadOffset.lastConsumed(), false);
        }
    }

    /** Relee los mensajes pendientes asignados al consumidor auditoria-1. */
    public int reprocesarAuditoria() {
        return consumir("auditoria", ReadOffset.from("0"), true);
    }

    private int consumir(String grupo, ReadOffset offset, boolean esReintento) {
        String grupoName = grupo + "-group";
        String consumidorName = grupo + "-1";
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(grupoName, consumidorName),
                    StreamReadOptions.empty().count(10).block(Duration.ofMillis(esReintento ? 1 : 300)),
                    StreamOffset.create(STREAM, offset));

            if (records == null) {
                return 0;
            }
            int procesados = 0;
            for (MapRecord<String, Object, Object> record : records) {
                if (procesarRegistro(grupo, grupoName, record, esReintento)) {
                    procesados++;
                }
            }
            return procesados;
        } catch (Exception e) {
            log.error("Error en consumidor {}: {}", grupo, e.getMessage());
            return 0;
        }
    }

    private boolean procesarRegistro(String grupo, String grupoName,
                                     MapRecord<String, Object, Object> record,
                                     boolean esReintento) {
        Map<Object, Object> data = record.getValue();
        String eventId = valor(data, "eventId");
        if (eventId == null) {
            redisTemplate.opsForStream().acknowledge(STREAM, grupoName, record.getId());
            return false;
        }

        String llaveProcesados = "procesados:" + grupoName;
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(llaveProcesados, eventId))) {
            redisTemplate.opsForStream().acknowledge(STREAM, grupoName, record.getId());
            log.info("[{}] Evento duplicado {}: se omite de forma idempotente", grupoName, eventId);
            return false;
        }

        log.info("[{}] Procesando {} - transferencia {}", grupoName, eventId,
                valor(data, "transferId"));

        // La primera lectura de auditoría falla deliberadamente antes del ACK.
        if ("auditoria".equals(grupo) && !esReintento) {
            log.warn("[auditoria-group] FALLA SIMULADA en {}: queda PENDIENTE sin ACK", eventId);
            return false;
        }

        switch (grupo) {
            case "fraude" -> log.info("[fraude-group] Análisis de fraude completado para {}",
                    valor(data, "transferId"));
            case "notificaciones" -> log.info("[notificaciones-group] Notificación enviada para {}",
                    valor(data, "transferId"));
            case "auditoria" -> {
                redisTemplate.opsForHash().put(AUDITORIA_HASH, eventId, serializar(data));
                log.info("[auditoria-group] Evento {} guardado en {}", eventId, AUDITORIA_HASH);
            }
            default -> throw new IllegalArgumentException("Grupo desconocido: " + grupo);
        }

        redisTemplate.opsForSet().add(llaveProcesados, eventId);
        redisTemplate.opsForStream().acknowledge(STREAM, grupoName, record.getId());
        log.info("[{}] ACK confirmado para {}", grupoName, record.getId());
        return true;
    }

    public Map<String, Long> obtenerPendientes() {
        Map<String, Long> pendientes = new LinkedHashMap<>();
        for (String grupo : GRUPOS) {
            String grupoName = grupo + "-group";
            try {
                PendingMessagesSummary summary = redisTemplate.opsForStream().pending(STREAM, grupoName);
                pendientes.put(grupoName, summary == null ? 0 : summary.getTotalPendingMessages());
            } catch (Exception e) {
                pendientes.put(grupoName, -1L);
            }
        }
        return pendientes;
    }

    public Map<Object, Object> obtenerAuditoria() {
        return redisTemplate.opsForHash().entries(AUDITORIA_HASH);
    }

    private static String valor(Map<Object, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    private static String serializar(Map<Object, Object> data) {
        return data.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .map(value -> "{" + value + "}")
                .orElse("{}");
    }
}
