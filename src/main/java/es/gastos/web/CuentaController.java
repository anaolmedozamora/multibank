package es.gastos.web;

import es.gastos.dominio.CuentaBancaria;
import es.gastos.repositorio.CuentaBancariaRepository;
import es.gastos.seguridad.EmpresaActual;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/cuentas")
public class CuentaController {

    private final CuentaBancariaRepository cuentas;
    private final EmpresaActual empresaActual;

    public CuentaController(CuentaBancariaRepository cuentas, EmpresaActual empresaActual) {
        this.cuentas = cuentas;
        this.empresaActual = empresaActual;
    }

    @GetMapping
    public String listar(Authentication auth, Model model) {
        model.addAttribute("cuentas", cuentas.findByEmpresaIdOrderByNombre(empresaActual.id(auth)));
        model.addAttribute("cuentaForm", new CuentaForm());
        return "cuentas";
    }

    @PostMapping
    public String crear(Authentication auth,
            @Valid @ModelAttribute("cuentaForm") CuentaForm form,
            BindingResult result,
            Model model) {
        Long empresaId = empresaActual.id(auth);
        String nombre = form.getNombre() == null ? "" : form.getNombre().trim();

        if (!result.hasFieldErrors("nombre") && cuentas.existsByEmpresaIdAndNombre(empresaId, nombre)) {
            result.rejectValue("nombre", "duplicado", "Ya existe una cuenta con ese nombre");
        }
        if (result.hasErrors()) {
            model.addAttribute("cuentas", cuentas.findByEmpresaIdOrderByNombre(empresaId));
            return "cuentas";
        }

        CuentaBancaria c = new CuentaBancaria();
        c.setEmpresaId(empresaId);
        c.setNombre(nombre);
        c.setBanco(blankToNull(form.getBanco()));
        c.setIban(form.getIban() == null || form.getIban().isBlank()
                ? null
                : form.getIban().replace(" ", "").toUpperCase());
        cuentas.save(c);
        return "redirect:/cuentas";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, Authentication auth) {
        cuentas.findByIdAndEmpresaId(id, empresaActual.id(auth)).ifPresent(c -> {
            c.setActiva(!c.isActiva());
            cuentas.save(c);
        });
        return "redirect:/cuentas";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}