package es.gastos.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "importacion")
public class Importacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "cuenta_id")
    private Long cuentaId;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "nombre_fichero", nullable = false)
    private String nombreFichero;

    @Column(name = "hash_fichero", nullable = false)
    private String hashFichero;

    @Column(name = "filas_leidas", nullable = false)
    private int filasLeidas;

    @Column(name = "filas_nuevas", nullable = false)
    private int filasNuevas;

    public Long getId() {
        return id;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(Long empresaId) {
        this.empresaId = empresaId;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
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

    public int getFilasLeidas() {
        return filasLeidas;
    }

    public void setFilasLeidas(int filasLeidas) {
        this.filasLeidas = filasLeidas;
    }

    public int getFilasNuevas() {
        return filasNuevas;
    }

    public void setFilasNuevas(int filasNuevas) {
        this.filasNuevas = filasNuevas;
    }
}