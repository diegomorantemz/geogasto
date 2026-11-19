package GeoGasto.domain.service;

import GeoGasto.domain.dto.budget.BudgetResponse;
import GeoGasto.domain.dto.budget.BudgetSummaryResponse;
import GeoGasto.domain.dto.notification.NotificationResponse;
import GeoGasto.domain.dto.notification.NotificationSummaryResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.domain.util.UserTime;
import GeoGasto.persistence.entity.Notification;
import GeoGasto.persistence.entity.RecurringExpense;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.NotificationRepository;
import GeoGasto.persistence.repository.RecurringExpenseRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEmailService emailService;
    private final BudgetService budgetService;
    private final RecurringExpenseRepository recurringRepository;
    private final UserRepository userRepository;

    @Transactional
    public NotificationSummaryResponse listar(String email) {
        User user = obtenerUsuario(email);
        generarParaUsuario(user);

        List<NotificationResponse> items = notificationRepository
                .findTop50ByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).toList();

        return NotificationSummaryResponse.builder()
                .unread(notificationRepository.countByUserIdAndLeidaFalse(user.getId()))
                .notifications(items)
                .build();
    }

    @Transactional
    public void marcarLeida(String email, Long id) {
        User user = obtenerUsuario(email);
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada"));
        notification.setLeida(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void marcarTodas(String email) {
        User user = obtenerUsuario(email);
        notificationRepository.marcarTodasComoLeidas(user.getId());
    }

    @Transactional
    public int generarParaTodos() {
        int creadas = 0;
        for (User user : userRepository.findAll()) {
            creadas += generarParaUsuario(user);
        }
        return creadas;
    }

    private int generarParaUsuario(User user) {
        int creadas = 0;
        ZoneId zone = UserTime.zoneOf(user);
        LocalDate hoy = LocalDate.now(zone);
        String mes = YearMonth.from(hoy).toString();

        try {
            BudgetSummaryResponse resumen = budgetService.resumen(user.getEmail(), mes);
            for (BudgetResponse budget : resumen.getPresupuestos()) {
                if (!"ALERTA".equals(budget.getEstado()) && !"EXCEDIDO".equals(budget.getEstado())) {
                    continue;
                }

                String titulo = "EXCEDIDO".equals(budget.getEstado())
                        ? "Presupuesto excedido"
                        : "Presupuesto cerca del límite";
                String mensaje = "Categoría " + budget.getCategoryNombre()
                        + ": " + budget.getPorcentaje() + " % usado. "
                        + "Gastado " + budget.getGastado() + " de " + budget.getMonto() + ".";

                if (crearSiNoExiste(user, "PRESUPUESTO", titulo, mensaje,
                        "/budgets.html", "budget:" + budget.getId() + ":" + budget.getMes()
                                + ":" + budget.getEstado())) {
                    creadas++;
                }
            }

            if (resumen.getEstadoGeneral() != null && !"OK".equals(resumen.getEstadoGeneral())) {
                String titulo = "EXCEDIDO".equals(resumen.getEstadoGeneral())
                        ? "Presupuesto general excedido"
                        : "Presupuesto general cerca del límite";
                String mensaje = "Has usado " + resumen.getPorcentajeGeneral()
                        + " % del presupuesto general del mes.";

                if (crearSiNoExiste(user, "PRESUPUESTO", titulo, mensaje,
                        "/budgets.html", "budget:general:" + mes + ":" + resumen.getEstadoGeneral())) {
                    creadas++;
                }
            }
        } catch (Exception e) {
            log.warn("No se pudieron generar alertas de presupuesto para {}: {}",
                    user.getEmail(), e.getMessage());
        }

        try {
            for (RecurringExpense recurring : recurringRepository.findByUserIdOrderByProximaFechaAsc(user.getId())) {
                if (!Boolean.TRUE.equals(recurring.getActivo()) || recurring.getProximaFecha() == null) {
                    continue;
                }
                if (recurring.getProximaFecha().isAfter(hoy.plusDays(3))) {
                    continue;
                }

                boolean vencida = recurring.getProximaFecha().isBefore(hoy);
                String titulo = vencida ? "Recurrencia vencida" : "Próximo gasto recurrente";
                String mensaje = (recurring.getDescripcion() != null
                        ? recurring.getDescripcion()
                        : "Gasto recurrente")
                        + " por " + recurring.getMonto()
                        + (vencida
                        ? " está pendiente de generación."
                        : " vence el " + recurring.getProximaFecha() + ".");

                if (crearSiNoExiste(user, "RECURRENTE", titulo, mensaje,
                        "/recurring.html", "recurring:" + recurring.getId() + ":" + recurring.getProximaFecha())) {
                    creadas++;
                }
            }
        } catch (Exception e) {
            log.warn("No se pudieron generar alertas de recurrentes para {}: {}",
                    user.getEmail(), e.getMessage());
        }

        return creadas;
    }

    private boolean crearSiNoExiste(User user, String tipo, String titulo, String mensaje,
                                    String link, String dedupeKey) {
        if (notificationRepository.existsByUserIdAndDedupeKey(user.getId(), dedupeKey)) {
            return false;
        }

        Notification notification = Notification.builder()
                .user(user)
                .tipo(tipo)
                .titulo(titulo)
                .mensaje(mensaje)
                .link(link)
                .leida(false)
                .dedupeKey(dedupeKey)
                .build();

        notificationRepository.save(notification);
        emailService.enviar(notification, user.getEmail());
        return true;
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .tipo(notification.getTipo())
                .titulo(notification.getTitulo())
                .mensaje(notification.getMensaje())
                .link(notification.getLink())
                .leida(notification.getLeida())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
