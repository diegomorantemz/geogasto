package GeoGasto.config;

import GeoGasto.domain.service.RecurringExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringExpenseScheduler {

    private final RecurringExpenseService recurringExpenseService;

    /**
     * Genera los gastos recurrentes vencidos una vez al día.
     * La hora se puede cambiar con app.recurring.cron.
     */
    @Scheduled(cron = "${app.recurring.cron:0 10 3 * * *}")
    public void generarPendientes() {
        try {
            recurringExpenseService.generarTodosPendientes();
        } catch (Exception e) {
            log.error("Error generando gastos recurrentes", e);
        }
    }
}
