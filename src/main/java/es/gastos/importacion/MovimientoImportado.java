package es.gastos.importacion;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class MovimientoImportado implements Serializable {

    private String hoja;
    private int numFila;
    private LocalDate fechaOperacion;
    private LocalDate fechaValor;
    private String concepto;
    private BigDecimal importe;
    private BigDecimal saldo;
    private String huella;
    private boolean duplicado;

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

    public boolean isDuplicado() {
        return duplicado;
    }

    public void setDuplicado(boolean duplicado) {
        this.duplicado = duplicado;
    }
}