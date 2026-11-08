package es.gastos.seguridad;

import es.gastos.repositorio.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class EmpresaActual {

    private final UsuarioRepository usuarioRepository;

    public EmpresaActual(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Long id(Authentication auth) {
        return usuarioRepository.findEmpresaIdByEmail(auth.getName());
    }
}