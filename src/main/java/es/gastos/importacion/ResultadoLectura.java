package es.gastos.importacion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ResultadoLectura implements Serializable {

    private final List<FilaImportada> filas = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();
    private int ignoradas;

    public List<FilaImportada> getFilas() {
        return filas;
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public int getIgnoradas() {
        return ignoradas;
    }

    public void addFila(FilaImportada f) {
        filas.add(f);
    }

    public void addAviso(String a) {
        avisos.add(a);
    }

    public void sumarIgnorada() {
        ignoradas++;
    }
}