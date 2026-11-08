package es.gastos.repositorio;

import es.gastos.dominio.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findByEmpresaIdAndFechaGastoBetweenOrderByFechaGastoAscIdAsc(
            Long empresaId, LocalDate desde, LocalDate hasta);

    Optional<Gasto> findByIdAndEmpresaId(Long id, Long empresaId);
}