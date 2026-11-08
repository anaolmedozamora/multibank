package es.gastos.web;

import es.gastos.dominio.CuentaBancaria;
import es.gastos.importacion.ExtractoService;
import es.gastos.importacion.LectorExtracto;
import es.gastos.importacion.MovimientoImportado;
import es.gastos.importacion.ResultadoExtracto;
import es.gastos.repositorio.CuentaBancariaRepository;
import es.gastos.repositorio.ImportacionRepository;
import es.gastos.repositorio.MovimientoBancarioRepository;
import es.gastos.seguridad.EmpresaActual;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/extractos")
public class ExtractoController {

    private static final String SESION = "extractoPendiente";

    private final LectorExtracto lector;
    private final ExtractoService servicio;
    private final CuentaBancariaRepository cuentas;
    private final MovimientoBancarioRepository movimientos;
    private final ImportacionRepository importaciones;
    private final EmpresaActual empresaActual;

    public ExtractoController(LectorExtracto lector, ExtractoService servicio,
            CuentaBancariaRepository cuentas, MovimientoBancarioRepository movimientos,
            ImportacionRepository importaciones, EmpresaActual empresaActual) {
        this.lector = lector;
        this.servicio = servicio;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.importaciones = importaciones;
        this.empresaActual = empresaActual;
    }

    @GetMapping
    public String formulario(Authentication auth, Model model) {
        cargarFormulario(model, empresaActual.id(auth));
        return "extractos";
    }

    @PostMapping("/previsualizar")
    public String previsualizar(@RequestParam("cuentaId") Long cuentaId,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication auth, HttpSession session, Model model) {
        Long empresaId = empresaActual.id(auth);

        Optional<CuentaBancaria> cuenta = cuentas.findByIdAndEmpresaId(cuentaId, empresaId);
        if (cuenta.isEmpty()) {
            return errorFormulario("Selecciona una cuenta válida.", empresaId, model);
        }

        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename();
        String n = nombre.toLowerCase(Locale.ROOT);
        if (archivo.isEmpty() || !(n.endsWith(".xlsx") || n.endsWith(".xls")
                || n.endsWith(".csv") || n.endsWith(".txt"))) {
            return errorFormulario("Sube un fichero .xlsx, .xls o .csv.", empresaId, model);
        }

        ResultadoExtracto res;
        try {
            res = lector.leer(archivo.getBytes(), nombre, cuentaId);
        } catch (Exception e) {
            return errorFormulario("No se pudo leer el fichero. ¿Está protegido con contraseña o dañado?",
                    empresaId, model);
        }

        if (importaciones.existsByEmpresaIdAndHashFichero(empresaId, res.getHashFichero())) {
            return errorFormulario("Este fichero ya se importó anteriormente.", empresaId, model);
        }

        int duplicados = 0;
        BigDecimal cargos = BigDecimal.ZERO;
        BigDecimal abonos = BigDecimal.ZERO;
        for (MovimientoImportado m : res.getMovimientos()) {
            boolean dup = movimientos.existsByCuentaIdAndHuella(cuentaId, m.getHuella());
            m.setDuplicado(dup);
            if (dup) {
                duplicados++;
            }
            if (m.getImporte().signum() < 0) {
                cargos = cargos.add(m.getImporte());
            } else {
                abonos = abonos.add(m.getImporte());
            }
        }

        session.setAttribute(SESION, res);
        model.addAttribute("resultado", res);
        model.addAttribute("cuentaNombre", cuenta.get().getNombre());
        model.addAttribute("duplicados", duplicados);
        model.addAttribute("totalCargos", cargos);
        model.addAttribute("totalAbonos", abonos);
        return "extractos-previa";
    }

    @PostMapping("/confirmar")
    public String confirmar(@RequestParam(name = "seleccion", required = false) List<Integer> seleccion,
            Authentication auth, HttpSession session, RedirectAttributes ra) {
        ResultadoExtracto res = (ResultadoExtracto) session.getAttribute(SESION);
        if (res == null) {
            ra.addFlashAttribute("mensaje", "La importación ha caducado. Vuelve a subir el fichero.");
            return "redirect:/extractos";
        }
        Set<Integer> elegidas = seleccion == null ? Set.of() : new HashSet<>(seleccion);
        int guardados = servicio.guardar(empresaActual.id(auth), res, elegidas);
        session.removeAttribute(SESION);

        ra.addFlashAttribute("mensaje", guardados == 0
                ? "No se ha importado ningún movimiento."
                : "Se han importado " + guardados + " movimientos.");
        return "redirect:/extractos";
    }

    // ---------- auxiliares ----------

    private String errorFormulario(String error, Long empresaId, Model model) {
        model.addAttribute("error", error);
        cargarFormulario(model, empresaId);
        return "extractos";
    }

    private void cargarFormulario(Model model, Long empresaId) {
        List<CuentaBancaria> todas = cuentas.findByEmpresaIdOrderByNombre(empresaId);
        Map<Long, String> nombres = todas.stream()
                .collect(Collectors.toMap(CuentaBancaria::getId, CuentaBancaria::getNombre));
        model.addAttribute("cuentasActivas", todas.stream().filter(CuentaBancaria::isActiva).toList());
        model.addAttribute("nombresCuenta", nombres);
        model.addAttribute("movimientos", todas.isEmpty() ? List.of()
                : movimientos.findTop100ByCuentaIdInOrderByFechaOperacionDescIdDesc(nombres.keySet()));
    }
}