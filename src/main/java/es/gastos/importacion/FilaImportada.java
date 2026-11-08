package es.gastos.importacion;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class FilaImportada implements Serializable {

    private String hoja;
    private int numFila;
    private LocalDate fecha;
    private String concepto;
    private BigDecimal importe;
    private String estado = "PENDIENTE";
    private Long cuentaId;
    private String cuentaNombre;
    private LocalDate vencimiento;
    private boolean duplicada;

    public String getHoja() {
        return hoja;
    }

    public void setHoja(String hoja) {
        this.hoja = hoja;
    }

    public int getNumFila() {
        return numFila;
    }

    public void setNumFila(int numFila) {
        this.numFila = numFila;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getCuentaNombre() {
        return cuentaNombre;
    }

    public void setCuentaNombre(String cuentaNombre) {
        this.cuentaNombre = cuentaNombre;
    }

    public LocalDate getVencimiento() {
        return vencimiento;
    }

    public void setVencimiento(LocalDate vencimiento) {
        this.vencimiento = vencimiento;
    }

    public boolean isDuplicada() {
        return duplicada;
    }

    public void setDuplicada(boolean duplicada) {
        this.duplicada = duplicada;
    }
}