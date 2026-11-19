package GeoGasto.domain.util;

import GeoGasto.persistence.entity.User;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Fechas y zonas horarias calculadas según el perfil del usuario.
 */
public final class UserTime {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Lima");

    private UserTime() {
    }

    public static ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return DEFAULT_ZONE;
        }
    }

    public static LocalDate today(User user) {
        return LocalDate.now(zoneOf(user));
    }

    public static void requireNotFuture(User user, LocalDate date) {
        if (date != null && date.isAfter(today(user))) {
            throw new IllegalArgumentException("La fecha no puede ser futura");
        }
    }
}
