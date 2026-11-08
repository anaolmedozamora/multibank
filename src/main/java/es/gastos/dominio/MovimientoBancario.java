package es.gastos.dominio;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "movimiento_bancario")
public class MovimientoBancario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(name = "importacion_id")
    private Long importacionId;

    @Column(name = "fecha_operacion", nullable = false)
    private LocalDate fechaOperacion;

    @Column(name = "fecha_valor")
    private LocalDate fechaValor;

    private String concepto;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal importe;

    @Column(precision = 14, scale = 2)
    private BigDecimal saldo;

    @Column(nullable = false)
    private String huella;

    public Long getId() {
        return id;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public Long getImportacionId() {
        return importacionId;
    }

    public void setImportacionId(Long importacionId) {
        this.importacionId = importacionId;
    }

    public LocalDate getFechaOperacion() {
        return fechaOperacion;
    }

    public void setFechaOperacion(LocalDate fechaOperacion) {
        this.fechaOperacion = fechaOperacion;
    }

    public LocalDate getFechaValor() {
        return fechaValor;
    }

    public void setFechaValor(LocalDate fechaValor) {
        this.fechaValor = fechaValor;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public String getHuella() {
        return huella;
    }

    public void setHuella(String huella) {
        this.huella = huella;
    }
}