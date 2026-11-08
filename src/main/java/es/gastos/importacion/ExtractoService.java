package es.gastos.importacion;

import es.gastos.dominio.Importacion;
import es.gastos.dominio.MovimientoBancario;
import es.gastos.repositorio.ImportacionRepository;
import es.gastos.repositorio.MovimientoBancarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ExtractoService {

    private final ImportacionRepository importaciones;
    private final MovimientoBancarioRepository movimientos;

    public ExtractoService(ImportacionRepository importaciones, MovimientoBancarioRepository movimientos) {
        this.importaciones = importaciones;
        this.movimientos = movimientos;
    }

    @Transactional
    public int guardar(Long empresaId, ResultadoExtracto res, Set<Integer> elegidas) {
        List<MovimientoImportado> lista = res.getMovimientos();
        List<MovimientoBancario> nuevos = new ArrayList<>();

        for (int i = 0; i < lista.size(); i++) {
            MovimientoImportado m = lista.get(i);
            if (!elegidas.contains(i) || movimientos.existsByCuentaIdAndHuella(res.getCuentaId(), m.getHuella())) {
                continue;
            }
            MovimientoBancario mb = new MovimientoBancario();
            mb.setCuentaId(res.getCuentaId());
            mb.setFechaOperacion(m.getFechaOperacion());
            mb.setFechaValor(m.getFechaValor());
            mb.setConcepto(m.getConcepto());
            mb.setImporte(m.getImporte());
            mb.setSaldo(m.getSaldo());
            mb.setHuella(m.getHuella());
            nuevos.add(mb);
        }
        if (nuevos.isEmpty()) {
            return 0;
        }

        String nombre = res.getNombreFichero();
        Importacion imp = new Importacion();
        imp.setEmpresaId(empresaId);
        imp.setCuentaId(res.getCuentaId());
        imp.setTipo("EXTRACTO");
        imp.setNombreFichero(nombre.length() > 255 ? nombre.substring(0, 255) : nombre);
        imp.setHashFichero(res.getHashFichero());
        imp.setFilasLeidas(lista.size());
        imp.setFilasNuevas(nuevos.size());
        imp = importaciones.save(imp);

        for (MovimientoBancario mb : nuevos) {
            mb.setImportacionId(imp.getId());
        }
        movimientos.saveAll(nuevos);
        return nuevos.size();
    }
}