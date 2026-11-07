package es.gastos.seguridad;

import es.gastos.dominio.Empresa;
import es.gastos.dominio.Usuario;
import es.gastos.repositorio.EmpresaRepository;
import es.gastos.repositorio.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InicializadorAdmin implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    public InicializadorAdmin(UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.count() > 0 || email.isBlank() || password.isBlank()) {
            return;
        }
        Empresa empresa = new Empresa();
        empresa.setNombre("Mi empresa");
        empresaRepository.save(empresa);

        Usuario admin = new Usuario();
        admin.setEmpresa(empresa);
        admin.setEmail(email);
        admin.setNombre("Administrador");
        admin.setRol("ADMIN");
        admin.setPasswordHash(passwordEncoder.encode(password));
        usuarioRepository.save(admin);
    }
}