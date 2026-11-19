package GeoGasto.domain.service;

import GeoGasto.domain.dto.budget.BudgetResponse;
import GeoGasto.domain.dto.budget.BudgetSummaryResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.domain.util.UserTime;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.Income;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.IncomeRepository;
import GeoGasto.persistence.repository.UserRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Locale LOCALE_ES = new Locale("es", "PE");
    private static final float MARGIN = 42f;

    private static final Color INK = new Color(27, 26, 23);
    private static final Color MUTED = new Color(111, 106, 97);
    private static final Color BORDER = new Color(225, 221, 211);
    private static final Color SURFACE = new Color(251, 250, 247);
    private static final Color SOFT = new Color(242, 239, 233);
    private static final Color GREEN = new Color(63, 107, 58);
    private static final Color GREEN_BG = new Color(237, 243, 235);
    private static final Color AMBER = new Color(138, 106, 31);
    private static final Color AMBER_BG = new Color(247, 241, 226);
    private static final Color RED = new Color(163, 58, 43);
    private static final Color RED_BG = new Color(247, 236, 234);

    private static final Font BRAND_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, MUTED);
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.TIMES_ROMAN, 26, INK);
    private static final Font PERIOD_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, MUTED);
    private static final Font META_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
    private static final Font META_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, INK);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK);
    private static final Font SECTION_SUB_FONT = FontFactory.getFont(FontFactory.HELVETICA, 7.8f, MUTED);
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.2f, MUTED);
    private static final Font HINT_FONT = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, MUTED);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.2f, MUTED);
    private static final Font TABLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8.4f, INK);
    private static final Font TABLE_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.4f, INK);
    private static final Font TABLE_RED = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.4f, RED);
    private static final Font TABLE_SMALL = FontFactory.getFont(FontFactory.HELVETICA, 7.6f, MUTED);
    private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, 7.2f, MUTED);

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
        String periodoLegible = mesLegible(periodo);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, MARGIN, MARGIN, 40, 46);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterEvent(periodoLegible));

            document.addTitle("Reporte mensual GeoGasto · " + periodoLegible);
            document.addAuthor("GeoGasto");
            document.addCreator("GeoGasto");
            document.addSubject("Resumen de gastos, ingresos y presupuestos");
            document.open();

            addHeader(document, user, periodoLegible);
            addSummary(document, user, gastos, ingresos, totalGastos, totalIngresos, balance);
            addBudgets(document, user, presupuestos);
            addCategories(document, user, gastos, totalGastos);
            if (gastos.size() > 5) {
                Paragraph continuation = new Paragraph(
                        "El detalle de los movimientos continúa en la siguiente página.", HINT_FONT);
                continuation.setSpacingBefore(14);
                document.add(continuation);
                document.newPage();
            }
            addExpenseDetail(document, user, gastos, totalGastos);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el reporte PDF", e);
        }
    }

    private static void addHeader(Document document, User user, String periodo) throws DocumentException {
        PdfPTable header = new PdfPTable(new float[]{62, 38});
        header.setWidthPercentage(100);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        Paragraph brand = new Paragraph(new Chunk("GEOGASTO", BRAND_FONT).setCharacterSpacing(1.4f));
        brand.setSpacingAfter(8);
        left.addElement(brand);
        Paragraph title = new Paragraph("Reporte mensual", TITLE_FONT);
        title.setLeading(27);
        title.setSpacingAfter(2);
        left.addElement(title);
        left.addElement(new Paragraph(periodo, PERIOD_FONT));
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        Paragraph name = new Paragraph(safe(user.getNombre(), user.getEmail()), META_BOLD);
        name.setAlignment(Element.ALIGN_RIGHT);
        name.setSpacingAfter(2);
        right.addElement(name);
        Paragraph email = new Paragraph(safe(user.getEmail(), "-"), META_FONT);
        email.setAlignment(Element.ALIGN_RIGHT);
        email.setSpacingAfter(9);
        right.addElement(email);
        Paragraph generated = new Paragraph(
                "Generado el " + UserTime.today(user)
                        .format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", LOCALE_ES)), META_FONT);
        generated.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(generated);
        header.addCell(right);

        header.setSpacingAfter(6);
        document.add(header);
        document.add(rule());
    }

    private static void addSummary(Document document, User user, List<Expense> gastos,
                                   List<Income> ingresos, BigDecimal totalGastos,
                                   BigDecimal totalIngresos, BigDecimal balance) throws DocumentException {
        document.add(section("Resumen del mes", "Ingresos, gastos y balance del periodo."));

        PdfPTable cards = new PdfPTable(new float[]{23.5f, 2, 23.5f, 2, 23.5f, 2, 23.5f});
        cards.setWidthPercentage(100);
        cards.addCell(card("Ingresos", money(totalIngresos, user.getMoneda()),
                ingresos.size() + (ingresos.size() == 1 ? " movimiento" : " movimientos"), INK));
        cards.addCell(gap());
        cards.addCell(card("Gastos", money(totalGastos, user.getMoneda()),
                gastos.size() + (gastos.size() == 1 ? " movimiento" : " movimientos"), INK));
        cards.addCell(gap());
        cards.addCell(card("Balance", money(balance, user.getMoneda()),
                balance.signum() >= 0 ? "A favor" : "En contra",
                balance.signum() >= 0 ? GREEN : RED));
        cards.addCell(gap());
        cards.addCell(card("Tasa de ahorro", tasaAhorro(balance, totalIngresos),
                "Sobre ingresos", balance.signum() >= 0 ? INK : RED));
        cards.setSpacingAfter(7);
        document.add(cards);

        int categorias = porCategoria(gastos).size();
        long conUbicacion = gastos.stream().filter(e -> e.getLocation() != null).count();
        Paragraph insight = new Paragraph(
                gastos.size() + (gastos.size() == 1 ? " gasto" : " gastos")
                        + " · " + categorias + (categorias == 1 ? " categoría" : " categorías")
                        + " · " + conUbicacion + " con ubicación",
                HINT_FONT);
        insight.setSpacingAfter(2);
        document.add(insight);
    }

    private static void addBudgets(Document document, User user, BudgetSummaryResponse presupuestos)
            throws DocumentException {
        List<BudgetResponse> items = presupuestos.getPresupuestos() == null
                ? List.of()
                : presupuestos.getPresupuestos();
        boolean hasGeneral = presupuestos.getPresupuestoGeneral() != null;

        if (!hasGeneral && items.isEmpty()) {
            return;
        }

        document.add(section("Presupuesto del mes", "Límites y avance del periodo."));

        if (hasGeneral) {
            PdfPTable general = new PdfPTable(new float[]{23.5f, 2, 23.5f, 2, 23.5f, 2, 23.5f});
            general.setWidthPercentage(100);
            general.addCell(card("Presupuesto general",
                    money(presupuestos.getPresupuestoGeneral(), user.getMoneda()), null, INK));
            general.addCell(gap());
            general.addCell(card("Gastado",
                    money(presupuestos.getGastadoGeneral(), user.getMoneda()), null, INK));
            general.addCell(gap());
            general.addCell(card("Disponible",
                    money(presupuestos.getDisponibleGeneral(), user.getMoneda()), null,
                    amountColor(presupuestos.getDisponibleGeneral())));
            general.addCell(gap());
            general.addCell(statusCard(presupuestos.getEstadoGeneral(),
                    presupuestos.getPorcentajeGeneral()));
            general.setSpacingAfter(6);
            document.add(general);
            document.add(progressBar(presupuestos.getPorcentajeGeneral(),
                    statusColor(presupuestos.getEstadoGeneral())));

            Paragraph avance = new Paragraph(
                    "Avance del presupuesto: " + percent(presupuestos.getPorcentajeGeneral())
                            + " · " + estado(presupuestos.getEstadoGeneral()), HINT_FONT);
            avance.setSpacingBefore(4);
            avance.setSpacingAfter(11);
            document.add(avance);
        }

        if (!items.isEmpty()) {
            PdfPTable table = new PdfPTable(new float[]{25, 15, 15, 15, 12, 18});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            tableHeader(table,
                    new String[]{"Categoría", "Presupuesto", "Gastado", "Disponible", "Avance", "Estado"},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT,
                            Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_CENTER});

            int index = 0;
            for (BudgetResponse item : items) {
                boolean alternate = index++ % 2 == 1;
                categoryCell(table, item.getCategoryNombre(),
                        parseColor(item.getCategoryColor(), BORDER), alternate);
                textCell(table, money(item.getMonto(), user.getMoneda()), TABLE_FONT,
                        Element.ALIGN_RIGHT, alternate);
                textCell(table, money(item.getGastado(), user.getMoneda()), TABLE_FONT,
                        Element.ALIGN_RIGHT, alternate);
                Font disponibleFont = item.getDisponible() != null && item.getDisponible().signum() < 0
                        ? TABLE_RED : TABLE_FONT;
                textCell(table, money(item.getDisponible(), user.getMoneda()), disponibleFont,
                        Element.ALIGN_RIGHT, alternate);
                textCell(table, percent(item.getPorcentaje()), TABLE_FONT,
                        Element.ALIGN_RIGHT, alternate);
                statusCell(table, item.getEstado());
            }
            table.setSpacingAfter(4);
            document.add(table);
        }
    }

    private static void addCategories(Document document, User user, List<Expense> gastos,
                                      BigDecimal totalGastos) throws DocumentException {
        if (gastos.isEmpty()) {
            return;
        }

        document.add(section("Gastos por categoría", "Distribución del gasto del periodo."));

        List<Map.Entry<String, CategoryTotal>> entries =
                new ArrayList<>(porCategoria(gastos).entrySet());
        entries.sort((a, b) -> b.getValue().total.compareTo(a.getValue().total));

        PdfPTable table = new PdfPTable(new float[]{40, 14, 24, 22});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        tableHeader(table,
                new String[]{"Categoría", "Gastos", "Total", "Participación"},
                new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT,
                        Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});

        int index = 0;
        for (Map.Entry<String, CategoryTotal> entry : entries) {
            boolean alternate = index++ % 2 == 1;
            categoryCell(table, entry.getKey(), entry.getValue().color, alternate);
            textCell(table, String.valueOf(entry.getValue().cantidad), TABLE_FONT,
                    Element.ALIGN_RIGHT, alternate);
            textCell(table, money(entry.getValue().total, user.getMoneda()), TABLE_BOLD,
                    Element.ALIGN_RIGHT, alternate);
            textCell(table, percent(entry.getValue().total, totalGastos), TABLE_FONT,
                    Element.ALIGN_RIGHT, alternate);
        }
        table.setSpacingAfter(4);
        document.add(table);
    }

    private static void addExpenseDetail(Document document, User user, List<Expense> gastos,
                                         BigDecimal totalGastos) throws DocumentException {
        document.add(section("Detalle de gastos", "Movimientos del periodo, del más reciente al más antiguo."));

        if (gastos.isEmpty()) {
            PdfPTable empty = new PdfPTable(1);
            empty.setWidthPercentage(100);
            PdfPCell cell = new PdfPCell(new Phrase("Sin gastos registrados en este periodo.", TABLE_SMALL));
            cell.setBackgroundColor(SURFACE);
            cell.setBorderColor(BORDER);
            cell.setBorderWidth(0.8f);
            cell.setPadding(12);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.addCell(cell);
            document.add(empty);
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{11, 37, 17, 22, 13});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        tableHeader(table,
                new String[]{"Fecha", "Descripción", "Categoría", "Ubicación", "Monto"},
                new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT,
                        Element.ALIGN_LEFT, Element.ALIGN_RIGHT});

        List<Expense> ordenados = gastos.stream()
                .sorted(Comparator.comparing(Expense::getFecha).reversed())
                .toList();

        int index = 0;
        for (Expense gasto : ordenados) {
            boolean alternate = index++ % 2 == 1;
            textCell(table, gasto.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    TABLE_FONT, Element.ALIGN_LEFT, alternate);
            textCell(table, safe(gasto.getDescripcion(), "Sin descripción"),
                    TABLE_FONT, Element.ALIGN_LEFT, alternate);
            categoryCell(table,
                    gasto.getCategory() != null ? gasto.getCategory().getNombre() : "Sin categoría",
                    gasto.getCategory() != null ? parseColor(gasto.getCategory().getColor(), BORDER) : MUTED,
                    alternate);
            textCell(table, gasto.getLocation() != null
                            ? safe(gasto.getLocation().getDireccion(), "-") : "-",
                    TABLE_FONT, Element.ALIGN_LEFT, alternate);
            textCell(table, money(gasto.getMonto(), user.getMoneda()), TABLE_BOLD,
                    Element.ALIGN_RIGHT, alternate);
        }

        PdfPCell totalLabel = new PdfPCell(new Phrase("Total del periodo", TABLE_BOLD));
        totalLabel.setColspan(4);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabel.setBackgroundColor(SOFT);
        totalLabel.setBorder(Rectangle.TOP);
        totalLabel.setBorderWidth(0.8f);
        totalLabel.setBorderColor(INK);
        totalLabel.setPadding(6);
        table.addCell(totalLabel);

        PdfPCell totalValue = new PdfPCell(new Phrase(money(totalGastos, user.getMoneda()), TABLE_BOLD));
        totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalValue.setBackgroundColor(SOFT);
        totalValue.setBorder(Rectangle.TOP);
        totalValue.setBorderWidth(0.8f);
        totalValue.setBorderColor(INK);
        totalValue.setPadding(6);
        table.addCell(totalValue);

        document.add(table);
    }

    private static PdfPTable rule() {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell line = new PdfPCell();
        line.setBorder(Rectangle.BOTTOM);
        line.setBorderWidth(0.9f);
        line.setBorderColor(INK);
        line.setFixedHeight(1);
        line.setPadding(0);
        rule.addCell(line);
        rule.setSpacingAfter(10);
        return rule;
    }

    private static Paragraph section(String title, String subtitle) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(title, SECTION_FONT));
        if (subtitle != null) {
            paragraph.add(new Chunk("\n" + subtitle, SECTION_SUB_FONT));
        }
        paragraph.setLeading(14);
        paragraph.setSpacingBefore(11);
        paragraph.setSpacingAfter(8);
        return paragraph;
    }

    private static PdfPCell card(String label, String value, String hint, Color valueColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(SURFACE);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.8f);
        cell.setPadding(10);

        Paragraph labelParagraph = new Paragraph(label.toUpperCase(LOCALE_ES), LABEL_FONT);
        labelParagraph.setSpacingAfter(5);
        cell.addElement(labelParagraph);

        Paragraph valueParagraph = new Paragraph(value,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14.5f, valueColor));
        valueParagraph.setSpacingAfter(hint == null ? 0 : 3);
        cell.addElement(valueParagraph);

        if (hint != null) {
            cell.addElement(new Paragraph(hint, HINT_FONT));
        }
        return cell;
    }

    private static PdfPCell statusCard(String estado, BigDecimal porcentaje) {
        return card("Estado", estadoCorto(estado), "Avance " + percent(porcentaje),
                statusColor(estado));
    }

    private static PdfPCell gap() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private static PdfPTable progressBar(BigDecimal porcentaje, Color fill) {
        float pct = porcentaje == null ? 0 : porcentaje.floatValue();
        pct = Math.max(0, Math.min(100, pct));

        PdfPTable bar;
        if (pct <= 0) {
            bar = new PdfPTable(1);
            bar.addCell(barCell(SOFT));
        } else if (pct >= 100) {
            bar = new PdfPTable(1);
            bar.addCell(barCell(fill));
        } else {
            bar = new PdfPTable(new float[]{Math.max(pct, 0.8f), Math.max(100 - pct, 0.8f)});
            bar.addCell(barCell(fill));
            bar.addCell(barCell(SOFT));
        }
        bar.setWidthPercentage(100);
        return bar;
    }

    private static PdfPCell barCell(Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(color);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(5);
        cell.setPadding(0);
        return cell;
    }

    private static void tableHeader(PdfPTable table, String[] labels, int[] alignments) {
        for (int i = 0; i < labels.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(labels[i].toUpperCase(LOCALE_ES), TABLE_HEADER_FONT));
            cell.setBackgroundColor(SOFT);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderWidth(0.8f);
            cell.setBorderColor(BORDER);
            cell.setPadding(6);
            cell.setHorizontalAlignment(alignments[i]);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }
    }

    private static void categoryCell(PdfPTable table, String name, Color color, boolean alternate) {
        PdfPCell cell = new PdfPCell(new Phrase(
                name == null || name.isBlank() ? "Sin categoría" : name, TABLE_FONT));
        cell.setUseVariableBorders(true);
        cell.setBorder(Rectangle.BOTTOM | Rectangle.LEFT);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(BORDER);
        cell.setBorderWidthLeft(3);
        cell.setBorderColorLeft(color == null ? MUTED : color);
        cell.setPadding(5);
        cell.setPaddingLeft(8);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (alternate) {
            cell.setBackgroundColor(SURFACE);
        }
        table.addCell(cell);
    }

    private static void textCell(PdfPTable table, String text, Font font,
                                 int alignment, boolean alternate) {
        PdfPCell cell = new PdfPCell(new Phrase(
                text == null || text.isBlank() ? "-" : text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(BORDER);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (alternate) {
            cell.setBackgroundColor(SURFACE);
        }
        table.addCell(cell);
    }

    private static void statusCell(PdfPTable table, String estado) {
        PdfPCell cell = new PdfPCell(new Phrase(
                estado(estado),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.8f, statusColor(estado))));
        cell.setBackgroundColor(statusBackground(estado));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(BORDER);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private static Map<String, CategoryTotal> porCategoria(List<Expense> gastos) {
        Map<String, CategoryTotal> map = new LinkedHashMap<>();
        for (Expense gasto : gastos) {
            String nombre = gasto.getCategory() != null
                    ? gasto.getCategory().getNombre() : "Sin categoría";
            CategoryTotal total = map.computeIfAbsent(nombre, k -> new CategoryTotal());
            total.total = total.total.add(gasto.getMonto());
            total.cantidad++;
            if (total.color == null && gasto.getCategory() != null) {
                total.color = parseColor(gasto.getCategory().getColor(), MUTED);
            }
        }
        return map;
    }

    private static String money(BigDecimal value, String moneda) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        String sign = amount.signum() < 0 ? "-" : "";
        String symbol = symbol(moneda);
        return sign + (symbol.isEmpty() ? "" : symbol + " ") + decimal(amount.abs());
    }

    private static String decimal(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    private static String symbol(String moneda) {
        if (moneda == null) {
            return "";
        }
        return switch (moneda) {
            case "PEN" -> "S/";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "MXN", "COP", "ARS", "CLP" -> "$";
            case "BRL" -> "R$";
            default -> moneda;
        };
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "0 %";
        }
        return value.setScale(0, RoundingMode.HALF_UP) + " %";
    }

    private static String percent(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return "0 %";
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP) + " %";
    }

    private static String tasaAhorro(BigDecimal balance, BigDecimal ingresos) {
        if (ingresos.signum() == 0) {
            return "—";
        }
        return balance.multiply(BigDecimal.valueOf(100))
                .divide(ingresos, 1, RoundingMode.HALF_UP) + " %";
    }

    private static String estado(String estado) {
        return switch (estado == null ? "" : estado) {
            case "EXCEDIDO" -> "Excedido";
            case "ALERTA" -> "Cerca del límite";
            default -> "En orden";
        };
    }

    private static String estadoCorto(String estado) {
        return switch (estado == null ? "" : estado) {
            case "EXCEDIDO" -> "Excedido";
            case "ALERTA" -> "Alerta";
            default -> "En orden";
        };
    }

    private static Color statusColor(String estado) {
        return switch (estado == null ? "" : estado) {
            case "EXCEDIDO" -> RED;
            case "ALERTA" -> AMBER;
            default -> GREEN;
        };
    }

    private static Color amountColor(BigDecimal value) {
        return value != null && value.signum() < 0 ? RED : INK;
    }

    private static Color statusBackground(String estado) {
        return switch (estado == null ? "" : estado) {
            case "EXCEDIDO" -> RED_BG;
            case "ALERTA" -> AMBER_BG;
            default -> GREEN_BG;
        };
    }

    private static Color parseColor(String value, Color fallback) {
        if (value == null || !value.matches("^#[0-9A-Fa-f]{6}$")) {
            return fallback;
        }
        try {
            return Color.decode(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String mesLegible(YearMonth periodo) {
        String text = periodo.format(DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_ES));
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static YearMonth parseMes(String mes, User user) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now(UserTime.zoneOf(user));
        }
        try {
            return YearMonth.parse(mes);
        } catch (Exception e) {
            throw new IllegalArgumentException("El mes debe tener formato YYYY-MM");
        }
    }

    private static final class CategoryTotal {
        private BigDecimal total = BigDecimal.ZERO;
        private long cantidad;
        private Color color;
    }

    private static final class FooterEvent extends PdfPageEventHelper {
        private final String periodo;

        private FooterEvent(String periodo) {
            this.periodo = periodo;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float left = MARGIN;
            float right = PageSize.A4.getWidth() - MARGIN;
            float center = PageSize.A4.getWidth() / 2;

            canvas.saveState();
            canvas.setColorStroke(BORDER);
            canvas.setLineWidth(0.6f);
            canvas.moveTo(left, 40);
            canvas.lineTo(right, 40);
            canvas.stroke();
            canvas.restoreState();

            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                    new Phrase("GeoGasto", FOOTER_FONT), left, 30, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                    new Phrase("Reporte mensual · " + periodo, FOOTER_FONT), center, 30, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), FOOTER_FONT), right, 30, 0);
        }
    }
}
