package es.gastos.web;

import es.gastos.dominio.CuentaBancaria;
import es.gastos.dominio.Gasto;
import es.gastos.repositorio.CuentaBancariaRepository;
import es.gastos.repositorio.GastoRepository;
import es.gastos.seguridad.EmpresaActual;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/gastos")
public class GastoController {

    private final GastoRepository gastos;
    private final CuentaBancariaRepository cuentas;
    private final EmpresaActual empresaActual;

    public GastoController(GastoRepository gastos,
            CuentaBancariaRepository cuentas,
            EmpresaActual empresaActual) {
        this.gastos = gastos;
        this.cuentas = cuentas;
        this.empresaActual = empresaActual;
    }

    @GetMapping
    public String semana(@RequestParam(required = false) String semana,
            Authentication auth, Model model) {
        LocalDate lunes = parseSemana(semana);
        LocalDate hoy = LocalDate.now();

        GastoForm form = new GastoForm();
        form.setFechaGasto(lunesDe(hoy).equals(lunes) ? hoy : lunes);

        cargarSemana(model, empresaActual.id(auth), lunes);
        model.addAttribute("gastoForm", form);
        return "gastos";
    }

    @PostMapping
    public String crear(@RequestParam(required = false) String semana,
            Authentication auth,
            @Valid @ModelAttribute("gastoForm") GastoForm form,
            BindingResult result,
            Model model) {
        Long empresaId = empresaActual.id(auth);

        if (form.getCuentaId() != null
                && cuentas.findByIdAndEmpresaId(form.getCuentaId(), empresaId).isEmpty()) {
            result.rejectValue("cuentaId", "invalida", "Cuenta no válida");
        }
        if (result.hasErrors()) {
            cargarSemana(model, empresaId, parseSemana(semana));
            return "gastos";
        }

        Gasto g = new Gasto();
        g.setEmpresaId(empresaId);
        g.setFechaGasto(form.getFechaGasto());
        g.setConcepto(form.getConcepto().trim());
        g.setImporte(form.getImporte());
        g.setCuentaId(form.getCuentaId());
        g.setFechaVencimiento(form.getFechaVencimiento());
        if (form.isPagado()) {
            g.setEstado("PAGADO");
            g.setFechaPago(form.getFechaGasto());
        }
        gastos.save(g);

        return "redirect:/gastos?semana=" + lunesDe(form.getFechaGasto());
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable Long id,
            @RequestParam(required = false) String semana,
            Authentication auth) {
        gastos.findByIdAndEmpresaId(id, empresaActual.id(auth)).ifPresent(g -> {
            if ("PENDIENTE".equals(g.getEstado())) {
                g.setEstado("PAGADO");
                g.setFechaPago(LocalDate.now());
            } else {
                g.setEstado("PENDIENTE");
                g.setFechaPago(null);
            }
            gastos.save(g);
        });
        return "redirect:/gastos?semana=" + parseSemana(semana);
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id,
            @RequestParam(required = false) String semana,
            Authentication auth) {
        gastos.findByIdAndEmpresaId(id, empresaActual.id(auth)).ifPresent(gastos::delete);
        return "redirect:/gastos?semana=" + parseSemana(semana);
    }

    // ---------- auxiliares ----------

    private void cargarSemana(Model model, Long empresaId, LocalDate lunes) {
        LocalDate domingo = lunes.plusDays(6);
        List<Gasto> lista = gastos
                .findByEmpresaIdAndFechaGastoBetweenOrderByFechaGastoAscIdAsc(empresaId, lunes, domingo);

        List<CuentaBancaria> todas = cuentas.findByEmpresaIdOrderByNombre(empresaId);
        Map<Long, String> nombres = todas.stream()
                .collect(Collectors.toMap(CuentaBancaria::getId, CuentaBancaria::getNombre));

        BigDecimal pendiente = suma(lista, "PENDIENTE");
        BigDecimal pagado = suma(lista, "PAGADO");

        model.addAttribute("gastos", lista);
        model.addAttribute("nombresCuenta", nombres);
        model.addAttribute("cuentasActivas", todas.stream().filter(CuentaBancaria::isActiva).toList());
        model.addAttribute("lunes", lunes);
        model.addAttribute("domingo", domingo);
        model.addAttribute("anterior", lunes.minusWeeks(1));
        model.addAttribute("siguiente", lunes.plusWeeks(1));
        model.addAttribute("totalPendiente", pendiente);
        model.addAttribute("totalPagado", pagado);
        model.addAttribute("total", pendiente.add(pagado));
    }

    private static BigDecimal suma(List<Gasto> lista, String estado) {
        return lista.stream()
                .filter(g -> estado.equals(g.getEstado()))
                .map(Gasto::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate lunesDe(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate parseSemana(String s) {
        try {
            return s == null ? lunesDe(LocalDate.now()) : lunesDe(LocalDate.parse(s));
        } catch (DateTimeParseException e) {
            return lunesDe(LocalDate.now());
        }
    }
}