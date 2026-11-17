package GeoGasto.domain.service;

import GeoGasto.domain.dto.receipt.ReceiptDownload;
import GeoGasto.domain.dto.receipt.ReceiptResponse;
import GeoGasto.domain.exception.ReceiptStorageException;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.Receipt;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.ReceiptRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

    private static final Map<String, String> TIPOS_PERMITIDOS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf"
    );

    private final ReceiptRepository receiptRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Value("${app.uploads.dir:uploads/receipts}")
    private String uploadsDir;

    @Value("${app.uploads.max-bytes:5242880}")
    private long maxBytes;

    @Transactional
    public ReceiptResponse subir(String email, Long expenseId, MultipartFile file) {
        User user = obtenerUsuario(email);
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));

        validarArchivo(file);
        String contentType = file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        String extension = TIPOS_PERMITIDOS.get(contentType);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ReceiptStorageException("No se pudo leer el comprobante", e);
        }
        validarFirma(contentType, bytes);

        String nombreArchivo = UUID.randomUUID() + "." + extension;
        Path destino = rutaBase().resolve(nombreArchivo);
        Path escrito = null;

        try {
            Files.createDirectories(rutaBase());
            Files.write(destino, bytes, StandardOpenOption.CREATE_NEW);
            escrito = destino;

            Receipt receipt = receiptRepository.findByExpenseId(expenseId)
                    .orElseGet(Receipt::new);

            String anterior = receipt.getNombreArchivo();
            receipt.setExpense(expense);
            receipt.setUser(user);
            receipt.setNombreOriginal(nombreOriginal(file.getOriginalFilename()));
            receipt.setNombreArchivo(nombreArchivo);
            receipt.setContentType(contentType);
            receipt.setTamano((long) bytes.length);
            receiptRepository.save(receipt);

            if (anterior != null && !anterior.equals(nombreArchivo)) {
                borrarArchivo(anterior);
            }

            return toResponse(receipt);
        } catch (IOException e) {
            if (escrito != null) {
                borrarArchivo(nombreArchivo);
            }
            throw new ReceiptStorageException("No se pudo guardar el comprobante", e);
        } catch (RuntimeException e) {
            if (escrito != null) {
                borrarArchivo(nombreArchivo);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ReceiptDownload descargar(String email, Long expenseId) {
        User user = obtenerUsuario(email);
        Receipt receipt = receiptRepository.findByExpenseIdAndUserId(expenseId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado"));

        try {
            Path archivo = rutaBase().resolve(receipt.getNombreArchivo()).normalize();
            Resource resource = new UrlResource(archivo.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Comprobante no encontrado");
            }
            return new ReceiptDownload(resource, receipt.getNombreOriginal(), receipt.getContentType());
        } catch (IOException e) {
            throw new ReceiptStorageException("No se pudo leer el comprobante", e);
        }
    }

    @Transactional
    public void eliminar(String email, Long expenseId) {
        User user = obtenerUsuario(email);
        Receipt receipt = receiptRepository.findByExpenseIdAndUserId(expenseId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado"));

        borrarArchivo(receipt.getNombreArchivo());
        receiptRepository.delete(receipt);
    }

    /**
     * Se invoca al eliminar un gasto para no dejar archivos huérfanos en disco.
     */
    @Transactional
    public void eliminarArchivoDeGasto(Long expenseId) {
        receiptRepository.findByExpenseId(expenseId).ifPresent(receipt -> {
            borrarArchivo(receipt.getNombreArchivo());
            receiptRepository.delete(receipt);
        });
    }

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Adjunta un archivo de comprobante");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("El comprobante no puede superar 5 MB");
        }
        if (file.getContentType() == null) {
            throw new IllegalArgumentException("El tipo de archivo no es válido");
        }
        String contentType = file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!TIPOS_PERMITIDOS.containsKey(contentType)) {
            throw new IllegalArgumentException("Solo se permiten imágenes JPG/PNG/WebP o PDF");
        }
    }

    private static void validarFirma(String contentType, byte[] bytes) {
        boolean valido = switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            case "application/pdf" -> bytes.length >= 4
                    && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
            default -> false;
        };

        if (!valido) {
            throw new IllegalArgumentException("El contenido del archivo no coincide con su tipo");
        }
    }

    private Path rutaBase() {
        return Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    private void borrarArchivo(String nombreArchivo) {
        if (nombreArchivo == null) return;
        try {
            Files.deleteIfExists(rutaBase().resolve(nombreArchivo).normalize());
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo de comprobante {}: {}", nombreArchivo, e.getMessage());
        }
    }

    private static String nombreOriginal(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "comprobante";
        }
        String limpio = Paths.get(nombre).getFileName().toString().replaceAll("[\\r\\n]", "");
        return limpio.length() > 255 ? limpio.substring(0, 255) : limpio;
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private ReceiptResponse toResponse(Receipt receipt) {
        return ReceiptResponse.builder()
                .id(receipt.getId())
                .expenseId(receipt.getExpense().getId())
                .nombreOriginal(receipt.getNombreOriginal())
                .contentType(receipt.getContentType())
                .tamano(receipt.getTamano())
                .createdAt(receipt.getCreatedAt())
                .build();
    }
}
