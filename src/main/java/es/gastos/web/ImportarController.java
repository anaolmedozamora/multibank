package es.gastos.web;

import es.gastos.dominio.Gasto;
import es.gastos.importacion.FilaImportada;
import es.gastos.importacion.LectorExcel;
import es.gastos.importacion.ResultadoLectura;
import es.gastos.repositorio.CuentaBancariaRepository;
import es.gastos.repositorio.GastoRepository;
import es.gastos.seguridad.EmpresaActual;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/importar")
public class ImportarController {

    private static final String SESION = "importacionPendiente";

    private final LectorExcel lector;
    private final GastoRepository gastos;
    private final CuentaBancariaRepository cuentas;
    private final EmpresaActual empresaActual;

    public ImportarController(LectorExcel lector, GastoRepository gastos,
            CuentaBancariaRepository cuentas, EmpresaActual empresaActual) {
        this.lector = lector;
        this.gastos = gastos;
        this.cuentas = cuentas;
        this.empresaActual = empresaActual;
    }

    @GetMapping
    public String formulario() {
        return "importar";
    }

    @PostMapping("/previsualizar")
    public String previsualizar(@RequestParam("archivo") MultipartFile archivo,
            Authentication auth, HttpSession session, Model model) {
        String nombre = archivo.getOriginalFilename() == null
                ? ""
                : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (archivo.isEmpty() || !(nombre.endsWith(".xlsx") || nombre.endsWith(".xls"))) {
            model.addAttribute("error", "Sube un fichero Excel (.xlsx o .xls).");
            return "importar";
        }

        Long empresaId = empresaActual.id(auth);
        ResultadoLectura res;
        try (InputStream in = archivo.getInputStream()) {
            res = lector.leer(in, cuentas.findByEmpresaIdAndActivaTrueOrderByNombre(empresaId));
        } catch (Exception e) {
            model.addAttribute("error",
                    "No se pudo leer el fichero. ¿Está protegido con contraseña o dañado?");
            return "importar";
        }

        for (FilaImportada f : res.getFilas()) {
            f.setDuplicada(gastos.existsByEmpresaIdAndFechaGastoAndImporteAndConceptoIgnoreCase(
                    empresaId, f.getFecha(), f.getImporte(), f.getConcepto()));
        }

        session.setAttribute(SESION, res);
        model.addAttribute("resultado", res);
        model.addAttribute("nombreFichero", archivo.getOriginalFilename());
        return "importar-previa";
    }

    @PostMapping("/confirmar")
    public String confirmar(@RequestParam(name = "seleccion", required = false) List<Integer> seleccion,
            Authentication auth, HttpSession session, RedirectAttributes ra) {
        ResultadoLectura res = (ResultadoLectura) session.getAttribute(SESION);
        if (res == null) {
            ra.addFlashAttribute("mensaje", "La importación ha caducado. Vuelve a subir el fichero.");
            return "redirect:/importar";
        }

        Long empresaId = empresaActual.id(auth);
        Set<Integer> elegidas = seleccion == null ? Set.of() : new HashSet<>(seleccion);
        List<FilaImportada> filas = res.getFilas();
        List<Gasto> nuevos = new ArrayList<>();

        for (int i = 0; i < filas.size(); i++) {
            if (!elegidas.contains(i)) {
                continue;
            }
            FilaImportada f = filas.get(i);
            Gasto g = new Gasto();
            g.setEmpresaId(empresaId);
            g.setFechaGasto(f.getFecha());
            g.setConcepto(f.getConcepto());
            g.setImporte(f.getImporte());
            g.setCuentaId(f.getCuentaId());
            g.setFechaVencimiento(f.getVencimiento());
            g.setEstado(f.getEstado());
            if ("PAGADO".equals(f.getEstado())) {
                g.setFechaPago(f.getFecha());
            }
            nuevos.add(g);
        }

        session.removeAttribute(SESION);

        if (nuevos.isEmpty()) {
            ra.addFlashAttribute("mensaje", "No se ha importado ningún gasto.");
            return "redirect:/importar";
        }

        gastos.saveAll(nuevos);
        ra.addFlashAttribute("mensaje", "Se han importado " + nuevos.size() + " gastos.");
        LocalDate primera = nuevos.stream().map(Gasto::getFechaGasto).min(Comparator.naturalOrder()).get();
        return "redirect:/gastos?semana=" + primera;
    }
}