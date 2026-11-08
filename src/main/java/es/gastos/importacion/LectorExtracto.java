package es.gastos.importacion;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Component
public class LectorExtracto {

    private static final int MAX_MOVIMIENTOS = 10000;
    private static final int MAX_FILAS_HOJA = 20000;
    private static final int FILAS_BUSQUEDA_CABECERA = 40;
    private static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("999999999999.99");

    /** El orden importa: si un título encaja en dos campos, gana el primero. */
    private enum Campo {
        FECHA_VALOR("valor"),
        FECHA("fecha", "f", "date"),
        IMPORTE("importe", "cantidad", "monto"),
        CARGO("cargo", "cargos", "debe"),
        ABONO("abono", "abonos", "haber"),
        SALDO("saldo", "disponible"),
        CONCEPTO("concepto", "descripcion", "detalle", "movimiento", "observaciones", "referencia");

        final List<String> claves;

        Campo(String... claves) {
            this.claves = List.of(claves);
        }
    }

    private record Tabla(String nombre, List<List<Object>> filas) {
    }

    private record Cabecera(int fila, Map<Campo, Integer> columnas, Map<Campo, String> titulos) {
    }

    public ResultadoExtracto leer(byte[] datos, String nombreFichero, Long cuentaId) throws IOException {
        ResultadoExtracto res = new ResultadoExtracto();
        res.setCuentaId(cuentaId);
        res.setNombreFichero(nombreFichero);
        res.setHashFichero(Valores.sha256(datos));

        List<Tabla> tablas = new ArrayList<>();
        String nombre = nombreFichero.toLowerCase(Locale.ROOT);
        if (nombre.endsWith(".csv") || nombre.endsWith(".txt")) {
            tablas.add(tablaDeCsv(datos));
        } else {
            try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(datos))) {
                FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
                for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                    if (!wb.isSheetHidden(s) && !wb.isSheetVeryHidden(s)) {
                        tablas.add(tablaDeHoja(wb.getSheetAt(s), ev));
                    }
                }
            }
        }

        for (Tabla tabla : tablas) {
            Cabecera cab = buscarCabecera(tabla);
            if (cab == null) {
                res.addAviso("«" + tabla.nombre() + "» ignorada: no se encontraron columnas de fecha e importe "
                        + "(o cargo/abono).");
                continue;
            }
            res.addAviso(describir(tabla.nombre(), cab));
            leerFilas(tabla, cab, res);
            if (res.getMovimientos().size() >= MAX_MOVIMIENTOS) {
                res.addAviso("Se han leído solo los primeros " + MAX_MOVIMIENTOS + " movimientos.");
                break;
            }
        }

        asignarHuellas(res, cuentaId);
        return res;
    }

    // ---------- de fichero a tabla ----------

    private Tabla tablaDeHoja(Sheet hoja, FormulaEvaluator ev) {
        List<List<Object>> filas = new ArrayList<>();
        int ultima = Math.min(hoja.getLastRowNum(), MAX_FILAS_HOJA);
        for (int r = 0; r <= ultima; r++) {
            Row row = hoja.getRow(r);
            List<Object> celdas = new ArrayList<>();
            if (row != null && row.getLastCellNum() > 0) {
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    celdas.add(valor(row.getCell(c), ev));
                }
            }
            filas.add(celdas);
        }
        return new Tabla("Hoja «" + hoja.getSheetName() + "»", filas);
    }

    private Object valor(Cell cell, FormulaEvaluator ev) {
        if (cell == null) {
            return null;
        }
        try {
            CellType tipo = cell.getCellType();
            if (tipo == CellType.FORMULA) {
                tipo = ev.evaluateFormulaCell(cell);
            }
            return switch (tipo) {
                case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate()
                        : BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String s = cell.getStringCellValue().trim();
                    yield s.isEmpty() ? null : s;
                }
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Tabla tablaDeCsv(byte[] datos) {
        String texto = decodificar(datos);
        if (texto.startsWith("\uFEFF")) {
            texto = texto.substring(1);
        }
        List<String> lineas = texto.lines().limit(MAX_FILAS_HOJA).toList();
        char separador = detectarSeparador(lineas);
        List<List<Object>> filas = new ArrayList<>();
        for (String linea : lineas) {
            filas.add(partirLinea(linea, separador));
        }
        return new Tabla("CSV", filas);
    }

    private String decodificar(byte[] datos) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(datos)).toString();
        } catch (CharacterCodingException e) {
            return new String(datos, StandardCharsets.ISO_8859_1); // típico de los bancos españoles
        }
    }

    private char detectarSeparador(List<String> lineas) {
        int[] cuentas = new int[3];
        char[] candidatos = { ';', ',', '\t' };
        int usadas = 0;
        for (String linea : lineas) {
            if (linea.isBlank()) {
                continue;
            }
            for (char ch : linea.toCharArray()) {
                for (int i = 0; i < candidatos.length; i++) {
                    if (ch == candidatos[i]) {
                        cuentas[i]++;
                    }
                }
            }
            if (++usadas >= 15) {
                break;
            }
        }
        int mejor = 0;
        for (int i = 1; i < cuentas.length; i++) {
            if (cuentas[i] > cuentas[mejor]) {
                mejor = i;
            }
        }
        return cuentas[mejor] == 0 ? ';' : candidatos[mejor];
    }

    private List<Object> partirLinea(String linea, char separador) {
        List<Object> campos = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (comillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    comillas = !comillas;
                }
            } else if (c == separador && !comillas) {
                campos.add(vacioANull(sb));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        campos.add(vacioANull(sb));
        return campos;
    }

    private String vacioANull(StringBuilder sb) {
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    // ---------- cabecera ----------

    private Cabecera buscarCabecera(Tabla tabla) {
        int limite = Math.min(tabla.filas().size(), FILAS_BUSQUEDA_CABECERA);
        for (int r = 0; r < limite; r++) {
            Map<Campo, Integer> cols = new EnumMap<>(Campo.class);
            Map<Campo, String> titulos = new EnumMap<>(Campo.class);
            List<Object> fila = tabla.filas().get(r);

            for (int c = 0; c < fila.size(); c++) {
                String texto = texto(fila.get(c));
                Campo campo = clasificar(texto);
                if (campo != null && !cols.containsKey(campo)) {
                    cols.put(campo, c);
                    titulos.put(campo, texto);
                }
            }
            boolean hayImporte = cols.containsKey(Campo.IMPORTE)
                    || cols.containsKey(Campo.CARGO) || cols.containsKey(Campo.ABONO);
            if (cols.containsKey(Campo.FECHA) && hayImporte) {
                return new Cabecera(r, cols, titulos);
            }
        }
        return null;
    }

    private Campo clasificar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        List<String> palabras = Arrays.asList(Valores.normalizar(texto).split("[^a-z0-9]+"));
        for (Campo campo : Campo.values()) {
            for (String clave : campo.claves) {
                if (palabras.contains(clave)) {
                    return campo;
                }
            }
        }
        return null;
    }

    private String describir(String nombre, Cabecera cab) {
        StringJoiner sj = new StringJoiner(", ");
        for (Campo campo : Campo.values()) {
            Integer col = cab.columnas().get(campo);
            if (col != null) {
                sj.add(campo.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " → columna "
                        + CellReference.convertNumToColString(col) + " («" + cab.titulos().get(campo) + "»)");
            }
        }
        return nombre + ": " + sj;
    }

    // ---------- filas ----------

    private void leerFilas(Tabla tabla, Cabecera cab, ResultadoExtracto res) {
        Map<Campo, Integer> cols = cab.columnas();

        for (int r = cab.fila() + 1; r < tabla.filas().size(); r++) {
            if (res.getMovimientos().size() >= MAX_MOVIMIENTOS) {
                return;
            }
            List<Object> fila = tabla.filas().get(r);
            if (filaVacia(fila)) {
                continue;
            }

            LocalDate fecha = Valores.aFecha(celda(fila, cols.get(Campo.FECHA)));
            BigDecimal importe = calcularImporte(fila, cols);

            if (fecha == null || importe == null || importe.signum() == 0
                    || importe.abs().compareTo(IMPORTE_MAXIMO) > 0
                    || fecha.getYear() < 2000 || fecha.getYear() > 2100) {
                res.sumarIgnorada(); // cabeceras repetidas, totales, notas...
                continue;
            }

            MovimientoImportado m = new MovimientoImportado();
            m.setHoja(tabla.nombre());
            m.setNumFila(r + 1);
            m.setFechaOperacion(fecha);
            m.setFechaValor(Valores.aFecha(celda(fila, cols.get(Campo.FECHA_VALOR))));
            m.setImporte(importe);
            m.setSaldo(Valores.aImporte(celda(fila, cols.get(Campo.SALDO))));

            String concepto = texto(celda(fila, cols.get(Campo.CONCEPTO)));
            if (concepto == null) {
                concepto = "(sin concepto)";
            } else if (concepto.length() > 500) {
                concepto = concepto.substring(0, 500);
            }
            m.setConcepto(concepto);

            res.getMovimientos().add(m);
        }
    }

    /** Columna de importe con signo, o abonos menos cargos si vienen separados. */
    private BigDecimal calcularImporte(List<Object> fila, Map<Campo, Integer> cols) {
        if (cols.containsKey(Campo.IMPORTE)) {
            return Valores.aImporte(celda(fila, cols.get(Campo.IMPORTE)));
        }
        BigDecimal cargo = Valores.aImporte(celda(fila, cols.get(Campo.CARGO)));
        BigDecimal abono = Valores.aImporte(celda(fila, cols.get(Campo.ABONO)));
        if (cargo == null && abono == null) {
            return null;
        }
        BigDecimal entradas = abono == null ? BigDecimal.ZERO : abono.abs();
        BigDecimal salidas = cargo == null ? BigDecimal.ZERO : cargo.abs();
        return entradas.subtract(salidas);
    }

    /**
     * La huella identifica un movimiento; el orden separa dos movimientos idénticos
     * el mismo día.
     */
    private void asignarHuellas(ResultadoExtracto res, Long cuentaId) {
        Map<String, Integer> vistos = new HashMap<>();
        for (MovimientoImportado m : res.getMovimientos()) {
            String base = m.getFechaOperacion() + "|" + m.getImporte().toPlainString() + "|"
                    + Valores.normalizar(m.getConcepto()) + "|"
                    + (m.getSaldo() == null ? "" : m.getSaldo().toPlainString());
            int orden = vistos.merge(base, 1, Integer::sum);
            m.setHuella(Valores.sha256(cuentaId + "|" + base + "|" + orden));
        }
    }

    // ---------- auxiliares ----------

    private Object celda(List<Object> fila, Integer col) {
        return col == null || col >= fila.size() ? null : fila.get(col);
    }

    private boolean filaVacia(List<Object> fila) {
        for (Object o : fila) {
            if (texto(o) != null) {
                return false;
            }
        }
        return true;
    }

    private String texto(Object o) {
        if (o == null) {
            return null;
        }
        String s = o instanceof BigDecimal b ? b.toPlainString() : o.toString();
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}