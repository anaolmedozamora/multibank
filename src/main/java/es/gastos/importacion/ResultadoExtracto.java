package es.gastos.importacion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ResultadoExtracto implements Serializable {

    private final List<MovimientoImportado> movimientos = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();
    private int ignoradas;
    private Long cuentaId;
    private String nombreFichero;
    private String hashFichero;

    public List<MovimientoImportado> getMovimientos() {
        return movimientos;
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public int getIgnoradas() {
        return ignoradas;
    }

    public void sumarIgnorada() {
        ignoradas++;
    }

    public void addAviso(String a) {
        avisos.add(a);
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getNombreFichero() {
        return nombreFichero;
    }

    public void setNombreFichero(String nombreFichero) {
        this.nombreFichero = nombreFichero;
    }

    public String getHashFichero() {
        return hashFichero;
    }

    public void setHashFichero(String hashFichero) {
        this.hashFichero = hashFichero;
    }
}