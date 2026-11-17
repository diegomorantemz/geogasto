package GeoGasto.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;

@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@BatchSize(size = 50)
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false, unique = true)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    @Column(name = "nombre_archivo", nullable = false, unique = true, length = 255)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long tamano;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
