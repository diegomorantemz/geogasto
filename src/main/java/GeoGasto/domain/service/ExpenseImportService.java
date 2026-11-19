package GeoGasto.domain.service;

import GeoGasto.domain.dto.importcsv.ImportPreviewResponse;
import GeoGasto.domain.dto.importcsv.ImportResultResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Category;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.CategoryRepository;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseImportService {

    private static final int MAX_FILAS = 2000;
    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ImportPreviewResponse previsualizar(String email, MultipartFile file,
                                               Long categoryId, String delimiter) {
        List<ParsedRow> rows = parsear(email, file, categoryId, delimiter);
        int validas = 0;
        int duplicadas = 0;
        int errores = 0;
        List<ImportPreviewResponse.Fila> filas = new ArrayList<>();

        for (ParsedRow row : rows) {
            if (row.duplicada) {
                duplicadas++;
            } else if (!row.valida) {
                errores++;
            } else {
                validas++;
            }

            if (filas.size() < 100) {
                filas.add(ImportPreviewResponse.Fila.builder()
                        .numero(row.numero)
                        .fecha(row.fecha != null ? row.fecha.toString() : row.fechaOriginal)
                        .monto(row.monto != null ? row.monto.toPlainString() : row.montoOriginal)
                        .descripcion(row.descripcion)
                        .categoria(row.categoria != null ? row.categoria.getNombre() : "")
                        .valida(row.valida)
                        .duplicada(row.duplicada)
                        .error(row.error)
                        .build());
            }
        }

        return ImportPreviewResponse.builder()
                .total(rows.size())
                .validas(validas)
                .duplicadas(duplicadas)
                .errores(errores)
                .filas(filas)
                .build();
    }

    @Transactional
    public ImportResultResponse importar(String email, MultipartFile file,
                                         Long categoryId, String delimiter) {
        User user = obtenerUsuario(email);
        List<ParsedRow> rows = parsear(email, file, categoryId, delimiter);
        List<Expense> aGuardar = new ArrayList<>();
        int omitidos = 0;
        int errores = 0;

        for (ParsedRow row : rows) {
            if (row.duplicada) {
                omitidos++;
                continue;
            }
            if (!row.valida) {
                errores++;
                continue;
            }

            Expense expense = new Expense();
            expense.setUser(user);
            expense.setMonto(row.monto);
            expense.setFecha(row.fecha);
            expense.setDescripcion(row.descripcion);
            expense.setCategory(row.categoria);
            aGuardar.add(expense);
        }

        expenseRepository.saveAll(aGuardar);

        return ImportResultResponse.builder()
                .importados(aGuardar.size())
                .omitidos(omitidos)
                .errores(errores)
                .mensaje("Importación completada")
                .build();
    }

    private List<ParsedRow> parsear(String email, MultipartFile file,
                                    Long categoryId, String delimiterParam) {
        User user = obtenerUsuario(email);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Adjunta un archivo CSV");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("El CSV no puede superar 2 MB");
        }

        Category categoriaDefecto = categoryId != null ? obtenerCategoria(user, categoryId) : null;
        Map<String, Category> categorias = new HashMap<>();
        for (Category category : categoryRepository.findByUserId(user.getId())) {
            categorias.put(norm(category.getNombre()), category);
        }

        String content = leerContenido(file);
        char delimiter = detectarDelimiter(content, delimiterParam);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<ParsedRow> rows = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        LocalDate hoy = LocalDate.now(zonaDe(user));

        try (CSVParser parser = CSVParser.parse(content, format)) {
            Map<String, String> headers = new HashMap<>();
            parser.getHeaderMap().keySet().forEach(h -> headers.put(norm(h), h));

            String fechaHeader = buscarHeader(headers, "fecha", "date", "fecha movimiento", "fecha operacion");
            String montoHeader = buscarHeader(headers, "monto", "importe", "amount", "valor", "total");
            String descripcionHeader = buscarHeader(headers, "descripcion", "description", "concepto",
                    "detalle", "glosa", "referencia");
            String categoriaHeader = buscarHeader(headers, "categoria", "category");

            if (fechaHeader == null || montoHeader == null) {
                throw new IllegalArgumentException(
                        "El CSV debe incluir columnas de fecha y monto (por ejemplo: fecha,monto,descripcion)");
            }

            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_FILAS) {
                    throw new IllegalArgumentException("El CSV supera el máximo de " + MAX_FILAS + " filas");
                }

                int numero = (int) record.getRecordNumber() + 1;
                String fechaOriginal = valor(record, fechaHeader);
                String montoOriginal = valor(record, montoHeader);
                String descripcion = valor(record, descripcionHeader);
                String categoriaCsv = valor(record, categoriaHeader);

                LocalDate fecha = parseFecha(fechaOriginal);
                BigDecimal monto = parseMonto(montoOriginal);

                String error = null;
                Category categoria = categoriaDefecto;

                if (fecha == null) {
                    error = "Fecha inválida";
                } else if (fecha.isAfter(hoy)) {
                    error = "La fecha no puede ser futura";
                } else if (monto == null) {
                    error = "Monto inválido";
                } else if (monto.signum() <= 0) {
                    error = "El monto debe ser mayor a 0";
                }

                if (categoriaCsv != null && !categoriaCsv.isBlank()) {
                    Category encontrada = categorias.get(norm(categoriaCsv));
                    if (encontrada == null) {
                        error = error != null ? error : "Categoría no encontrada: " + categoriaCsv;
                    } else {
                        categoria = encontrada;
                    }
                }

                if (descripcion == null || descripcion.isBlank()) {
                    descripcion = "Importado";
                }
                if (descripcion.length() > 255) {
                    descripcion = descripcion.substring(0, 255);
                }

                boolean duplicada = false;
                if (error == null) {
                    String key = fecha + "|" + monto.setScale(2, RoundingMode.HALF_UP)
                            + "|" + descripcion.toLowerCase(Locale.ROOT);
                    if (!vistos.add(key)) {
                        duplicada = true;
                        error = "Duplicado dentro del archivo";
                    } else if (expenseRepository.existeDuplicado(user.getId(), fecha, monto, descripcion)) {
                        duplicada = true;
                        error = "Ya existe un gasto igual";
                    }
                }

                rows.add(new ParsedRow(numero, fechaOriginal, montoOriginal, fecha, monto,
                        descripcion, categoria, error == null, duplicada, error));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el CSV. Revisa el formato y las columnas");
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("El CSV no contiene filas para importar");
        }
        return rows;
    }

    private String leerContenido(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return content.startsWith("\uFEFF") ? content.substring(1) : content;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el archivo CSV");
        }
    }

    private static char detectarDelimiter(String content, String delimiterParam) {
        if (delimiterParam != null && !delimiterParam.isBlank() && !"auto".equalsIgnoreCase(delimiterParam)) {
            return switch (delimiterParam.toLowerCase(Locale.ROOT)) {
                case "semicolon", "punto y coma", ";" -> ';';
                case "tab", "tabulador", "\\t" -> '\t';
                default -> ',';
            };
        }

        String firstLine = content.lines().findFirst().orElse("");
        long commas = firstLine.chars().filter(c -> c == ',').count();
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long tabs = firstLine.chars().filter(c -> c == '\t').count();
        if (semicolons > commas && semicolons >= tabs) return ';';
        if (tabs > commas && tabs > semicolons) return '\t';
        return ',';
    }

    private static String buscarHeader(Map<String, String> headers, String... aliases) {
        for (String alias : aliases) {
            String found = headers.get(norm(alias));
            if (found != null) return found;
        }
        return null;
    }

    private static String valor(CSVRecord record, String header) {
        if (header == null) return null;
        try {
            return record.isMapped(header) ? record.get(header) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseFecha(String value) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter formatter : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // siguiente formato
            }
        }
        return null;
    }

    private static BigDecimal parseMonto(String value) {
        if (value == null || value.isBlank()) return null;

        String limpio = value.trim()
                .replace("S/", "")
                .replace("$", "")
                .replace("€", "")
                .replace(" ", "")
                .replace("'", "");

        try {
            if (limpio.contains(",") && limpio.contains(".")) {
                if (limpio.lastIndexOf(',') > limpio.lastIndexOf('.')) {
                    limpio = limpio.replace(".", "").replace(",", ".");
                } else {
                    limpio = limpio.replace(",", "");
                }
            } else if (limpio.contains(",")) {
                int decimals = limpio.length() - limpio.lastIndexOf(',') - 1;
                limpio = decimals <= 2 ? limpio.replace(",", ".") : limpio.replace(",", "");
            }
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Category obtenerCategoria(User user, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
        if (!category.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Categoría no encontrada");
        }
        return category;
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private static ZoneId zonaDe(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return ZoneId.of("America/Lima");
        }
    }

    private static String norm(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private record ParsedRow(int numero, String fechaOriginal, String montoOriginal,
                             LocalDate fecha, BigDecimal monto, String descripcion,
                             Category categoria, boolean valida, boolean duplicada, String error) {
    }
}
