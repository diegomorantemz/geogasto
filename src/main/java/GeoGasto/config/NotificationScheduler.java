package GeoGasto.config;

import GeoGasto.domain.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final NotificationService notificationService;

    /**
     * Genera alertas de presupuesto y recordatorios de recurrentes.
     * La generación también se hace al abrir la campana, por lo que la demo no depende del cron.
     */
    @Scheduled(cron = "${app.notifications.cron:0 20 3 * * *}")
    public void generarAlertas() {
        try {
            notificationService.generarParaTodos();
        } catch (Exception e) {
            log.error("Error generando notificaciones", e);
        }
    }
}
