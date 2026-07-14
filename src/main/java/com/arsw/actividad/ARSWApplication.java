package com.arsw.actividad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ARSWApplication {
    public static void main(String[] args) {
        SpringApplication.run(ARSWApplication.class, args);
        System.out.println("ARSW-ACTIVIDAD iniciado en http://localhost:8080");
        System.out.println("Usando Redis Stream: banco.transferencias");
        System.out.println("Grupos: fraude, notificaciones, auditoria");
        System.out.println("Auditoría falla 1 de cada 3 eventos (simulación)");
    }
}