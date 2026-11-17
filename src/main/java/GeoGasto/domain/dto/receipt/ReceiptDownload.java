package GeoGasto.domain.dto.receipt;

import org.springframework.core.io.Resource;

public record ReceiptDownload(Resource resource, String nombreOriginal, String contentType) {
}
