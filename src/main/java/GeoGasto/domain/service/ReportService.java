package GeoGasto.domain.service;

import GeoGasto.domain.dto.budget.BudgetResponse;
import GeoGasto.domain.dto.budget.BudgetSummaryResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.Income;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.IncomeRepository;
import GeoGasto.persistence.repository.UserRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final BudgetService budgetService;

    @Transactional(readOnly = true)
    public byte[] generarMensual(String email, String mes) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        YearMonth periodo = parseMes(mes, user);
        LocalDate desde = periodo.atDay(1);
        LocalDate hasta = periodo.atEndOfMonth();

        List<Expense> gastos = expenseRepository.findByUserIdAndFechaBetween(user.getId(), desde, hasta);
        List<Income> ingresos = incomeRepository.findByUserIdAndFechaBetween(user.getId(), desde, hasta);
        BudgetSummaryResponse presupuestos = budgetService.resumen(email, periodo.toString());

        BigDecimal totalGastos = gastos.stream().map(Expense::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIngresos = ingresos.stream().map(Income::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalIngresos.subtract(totalGastos);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 40, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(30, 58, 138));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(100, 116, 139));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(37, 99, 235));
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
            Font whiteBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

            Paragraph title = new Paragraph("GeoGasto", titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            document.add(title);

            Paragraph subtitle = new Paragraph(
                    "Reporte mensual · " + periodo.format(DateTimeFormatter.ofPattern("MMMM yyyy",
                            new Locale("es", "PE"))), subtitleFont);
            subtitle.setSpacingAfter(18);
            document.add(subtitle);

            document.add(new Paragraph("Resumen", sectionFont));
            document.add(new Paragraph("Usuario: " + user.getNombre() + " · " + user.getEmail(), normal));

            PdfPTable resumen = table(4);
            header(resumen, whiteBold, "Ingresos", "Gastos", "Balance", "Tasa de ahorro");
            addRow(resumen, bold,
                    money(totalIngresos, user.getMoneda()),
                    money(totalGastos, user.getMoneda()),
                    money(balance, user.getMoneda()),
                    tasaAhorro(balance, totalIngresos));
            resumen.setSpacingBefore(8);
            resumen.setSpacingAfter(18);
            document.add(resumen);

            if (presupuestos.getPresupuestoGeneral() != null) {
                document.add(new Paragraph(
                        "Presupuesto general: " + money(presupuestos.getPresupuestoGeneral(), user.getMoneda())
                                + " · Gastado " + money(presupuestos.getGastadoGeneral(), user.getMoneda())
                                + " · " + estado(presupuestos.getEstadoGeneral()), normal));
                document.add(new Paragraph(" ", normal));
            }

            if (presupuestos.getPresupuestos() != null && !presupuestos.getPresupuestos().isEmpty()) {
                document.add(new Paragraph("Presupuestos", sectionFont));
                PdfPTable budgetTable = table(5);
                header(budgetTable, whiteBold, "Categoría", "Presupuesto", "Gastado", "Disponible", "Estado");
                for (BudgetResponse b : presupuestos.getPresupuestos()) {
                    addRow(budgetTable, normal,
                            b.getCategoryNombre(),
                            money(b.getMonto(), user.getMoneda()),
                            money(b.getGastado(), user.getMoneda()),
                            money(b.getDisponible(), user.getMoneda()),
                            estado(b.getEstado()));
                }
                budgetTable.setSpacingBefore(8);
                budgetTable.setSpacingAfter(18);
                document.add(budgetTable);
            }

            document.add(new Paragraph("Gastos por categoría", sectionFont));
            PdfPTable categorias = table(3);
            header(categorias, whiteBold, "Categoría", "Gastos", "Total");
            for (Map.Entry<String, CategoryTotal> entry : porCategoria(gastos).entrySet()) {
                addRow(categorias, normal, entry.getKey(), String.valueOf(entry.getValue().cantidad),
                        money(entry.getValue().total, user.getMoneda()));
            }
            categorias.setSpacingBefore(8);
            categorias.setSpacingAfter(18);
            document.add(categorias);

            document.add(new Paragraph("Detalle de gastos", sectionFont));
            PdfPTable gastosTable = table(5);
            header(gastosTable, whiteBold, "Fecha", "Descripción", "Categoría", "Ubicación", "Monto");
            gastos.stream()
                    .sorted(Comparator.comparing(Expense::getFecha).reversed())
                    .forEach(e -> addRow(gastosTable, normal,
                            e.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            e.getDescripcion() != null ? e.getDescripcion() : "-",
                            e.getCategory() != null ? e.getCategory().getNombre() : "Sin categoría",
                            e.getLocation() != null && e.getLocation().getDireccion() != null
                                    ? e.getLocation().getDireccion() : "-",
                            money(e.getMonto(), user.getMoneda())));
            gastosTable.setSpacingBefore(8);
            document.add(gastosTable);

            Paragraph footer = new Paragraph(
                    "Generado por GeoGasto · " + LocalDate.now() + " · " + user.getMoneda(), subtitleFont);
            footer.setSpacingBefore(20);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el reporte PDF", e);
        }
    }

    private static PdfPTable table(int columns) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        return table;
    }

    private static void header(PdfPTable table, Font font, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, font));
            cell.setBackgroundColor(new Color(37, 99, 235));
            cell.setPadding(6);
            cell.setBorderColor(new Color(219, 234, 254));
            table.addCell(cell);
        }
    }

    private static void addRow(PdfPTable table, Font font, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value == null ? "-" : value, font));
            cell.setPadding(5);
            cell.setBorderColor(new Color(226, 232, 240));
            table.addCell(cell);
        }
    }

    private static Map<String, CategoryTotal> porCategoria(List<Expense> gastos) {
        Map<String, CategoryTotal> map = new LinkedHashMap<>();
        for (Expense e : gastos) {
            String nombre = e.getCategory() != null ? e.getCategory().getNombre() : "Sin categoría";
            CategoryTotal total = map.computeIfAbsent(nombre, k -> new CategoryTotal());
            total.total = total.total.add(e.getMonto());
            total.cantidad++;
        }
        return map;
    }

    private static String money(BigDecimal value, String moneda) {
        return moneda + " " + value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String tasaAhorro(BigDecimal balance, BigDecimal ingresos) {
        if (ingresos.signum() == 0) {
            return "—";
        }
        return balance.multiply(BigDecimal.valueOf(100))
                .divide(ingresos, 1, RoundingMode.HALF_UP) + " %";
    }

    private static String estado(String estado) {
        return switch (estado) {
            case "EXCEDIDO" -> "Excedido";
            case "ALERTA" -> "Cerca del límite";
            default -> "En orden";
        };
    }

    private static YearMonth parseMes(String mes, User user) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now(zonaDe(user));
        }
        try {
            return YearMonth.parse(mes);
        } catch (Exception e) {
            throw new IllegalArgumentException("El mes debe tener formato YYYY-MM");
        }
    }

    private static ZoneId zonaDe(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return ZoneId.of("America/Lima");
        }
    }

    private static final class CategoryTotal {
        private BigDecimal total = BigDecimal.ZERO;
        private long cantidad;
    }
}
