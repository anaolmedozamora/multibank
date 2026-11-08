package es.gastos.repositorio;

import es.gastos.dominio.Importacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacionRepository extends JpaRepository<Importacion, Long> {
    boolean existsByEmpresaIdAndHashFichero(Long empresaId, String hashFichero);
}