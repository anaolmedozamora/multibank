package es.gastos.repositorio;

import es.gastos.dominio.MovimientoBancario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface MovimientoBancarioRepository extends JpaRepository<MovimientoBancario, Long> {

    boolean existsByCuentaIdAndHuella(Long cuentaId, String huella);

    List<MovimientoBancario> findTop100ByCuentaIdInOrderByFechaOperacionDescIdDesc(Collection<Long> cuentaIds);
}