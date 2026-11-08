package es.gastos.repositorio;

import es.gastos.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);

    @Query("select u.empresa.id from Usuario u where u.email = :email")
    Long findEmpresaIdByEmail(@Param("email") String email);
}