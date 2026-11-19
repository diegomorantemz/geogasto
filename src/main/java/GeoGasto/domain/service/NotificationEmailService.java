package GeoGasto.domain.service;

import GeoGasto.persistence.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.notifications.email.enabled:false}")
    private boolean enabled;

    @Value("${app.notifications.email.from:no-reply@geogasto.local}")
    private String from;

    public void enviar(Notification notification, String email) {
        if (!enabled) {
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Notificaciones por email activadas, pero no hay spring.mail.host configurado");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject("[GeoGasto] " + notification.getTitulo());
            message.setText(notification.getMensaje() + "\n\n-- GeoGasto");
            sender.send(message);
        } catch (Exception e) {
            log.warn("No se pudo enviar email de notificación a {}: {}", email, e.getMessage());
        }
    }
}
