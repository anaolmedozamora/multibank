package es.gastos.repositorio;

import es.gastos.dominio.CuentaBancaria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CuentaBancariaRepository extends JpaRepository<CuentaBancaria, Long> {
    List<CuentaBancaria> findByEmpresaIdOrderByNombre(Long empresaId);

    boolean existsByEmpresaIdAndNombre(Long empresaId, String nombre);

    Optional<CuentaBancaria> findByIdAndEmpresaId(Long id, Long empresaId);
}