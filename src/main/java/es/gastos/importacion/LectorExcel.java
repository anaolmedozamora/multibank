package es.gastos.importacion;

import es.gastos.dominio.CuentaBancaria;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
public class LectorExcel {

    private static final int MAX_FILAS = 5000;
    private static final int FILAS_BUSQUEDA_CABECERA = 25;
    private static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("999999999999.99");

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("uuuu-M-d"),
            DateTimeFormatter.ofPattern("d/M/uu"));

    /** El orden importa: si un título encaja en dos campos, gana el primero. */
    private enum Campo {
        VENCIMIENTO("vencimiento", "vence"),
        FECHA("fecha", "dia", "date"),
        IMPORTE("importe", "total", "cantidad", "monto", "euros", "valor", "precio", "coste", "cargo"),
        ESTADO("estado", "pagado", "pagada", "situacion"),
        CUENTA("cuenta", "banco"),
        CONCEPTO("concepto", "descripcion", "detalle", "proveedor", "gasto",
                "observaciones", "nombre", "motivo");

        final List<String> claves;

        Campo(String... claves) {
            this.claves = List.of(claves);
        }
    }

    private record Cabecera(int fila, Map<Campo, Integer> columnas, Map<Campo, String> titulos) {
    }

    public ResultadoLectura leer(InputStream in, List<CuentaBancaria> cuentas) throws IOException {
        ResultadoLectura res = new ResultadoLectura();
        DataFormatter fmt = new DataFormatter(Locale.of("es", "ES"));

        try (Workbook wb = WorkbookFactory.create(in)) {
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                if (wb.isSheetHidden(s) || wb.isSheetVeryHidden(s)) {
                    continue;
                }
                Sheet hoja = wb.getSheetAt(s);
                Cabecera cab = buscarCabecera(hoja, fmt, ev);
                if (cab == null) {
                    res.addAviso("Hoja «" + hoja.getSheetName()
                            + "» ignorada: no se encontraron columnas de fecha e importe.");
                    continue;
                }
                res.addAviso(describir(hoja.getSheetName(), cab));
                leerFilas(hoja, cab, fmt, ev, cuentas, res);

                if (res.getFilas().size() >= MAX_FILAS) {
                    res.addAviso("Se han leído solo las primeras " + MAX_FILAS + " filas.");
                    break;
                }
            }
        }
        return res;
    }

    // ---------- cabecera ----------

    private Cabecera buscarCabecera(Sheet hoja, DataFormatter fmt, FormulaEvaluator ev) {
        int primera = hoja.getFirstRowNum();
        int limite = Math.min(hoja.getLastRowNum(), primera + FILAS_BUSQUEDA_CABECERA);

        for (int r = primera; r <= limite; r++) {
            Row fila = hoja.getRow(r);
            if (fila == null) {
                continue;
            }
            Map<Campo, Integer> cols = new EnumMap<>(Campo.class);
            Map<Campo, String> titulos = new EnumMap<>(Campo.class);

            for (Cell c : fila) {
                String texto = fmt.formatCellValue(c, ev);
                Campo campo = clasificar(texto);
                if (campo != null && !cols.containsKey(campo)) {
                    cols.put(campo, c.getColumnIndex());
                    titulos.put(campo, texto.trim());
                }
            }
            if (cols.containsKey(Campo.FECHA) && cols.containsKey(Campo.IMPORTE)) {
                return new Cabecera(r, cols, titulos);
            }
        }
        return null;
    }

    private Campo clasificar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        List<String> palabras = Arrays.asList(normalizar(texto).split("[^a-z0-9]+"));
        for (Campo campo : Campo.values()) {
            for (String clave : campo.claves) {
                if (palabras.contains(clave)) {
                    return campo;
                }
            }
        }
        return null;
    }

    private String describir(String hoja, Cabecera cab) {
        StringJoiner sj = new StringJoiner(", ");
        for (Campo campo : Campo.values()) {
            Integer col = cab.columnas().get(campo);
            if (col != null) {
                sj.add(campo.name().toLowerCase(Locale.ROOT) + " → columna "
                        + CellReference.convertNumToColString(col)
                        + " («" + cab.titulos().get(campo) + "»)");
            }
        }
        return "Hoja «" + hoja + "»: " + sj;
    }

    // ---------- filas ----------

    private void leerFilas(Sheet hoja, Cabecera cab, DataFormatter fmt, FormulaEvaluator ev,
            List<CuentaBancaria> cuentas, ResultadoLectura res) {
        Map<Campo, Integer> cols = cab.columnas();

        for (int r = cab.fila() + 1; r <= hoja.getLastRowNum(); r++) {
            if (res.getFilas().size() >= MAX_FILAS) {
                return;
            }
            Row fila = hoja.getRow(r);
            if (fila == null || filaVacia(fila, fmt, ev)) {
                continue;
            }

            LocalDate fecha = aFecha(valor(fila.getCell(cols.get(Campo.FECHA)), ev));
            BigDecimal importe = aImporte(valor(fila.getCell(cols.get(Campo.IMPORTE)), ev));

            if (fecha == null || importe == null || importe.signum() == 0
                    || importe.compareTo(IMPORTE_MAXIMO) > 0
                    || fecha.getYear() < 2000 || fecha.getYear() > 2100) {
                res.sumarIgnorada(); // totales, filas en blanco con texto, notas...
                continue;
            }

            FilaImportada f = new FilaImportada();
            f.setHoja(hoja.getSheetName());
            f.setNumFila(r + 1);
            f.setFecha(fecha);
            f.setImporte(importe);

            String concepto = texto(fila, cols.get(Campo.CONCEPTO), fmt, ev);
            if (concepto == null) {
                concepto = "(sin concepto)";
            } else if (concepto.length() > 250) {
                concepto = concepto.substring(0, 250);
            }
            f.setConcepto(concepto);

            Integer colEstado = cols.get(Campo.ESTADO);
            if (colEstado != null) {
                f.setEstado(aEstado(valor(fila.getCell(colEstado), ev)));
            }

            Integer colVenc = cols.get(Campo.VENCIMIENTO);
            if (colVenc != null) {
                f.setVencimiento(aFecha(valor(fila.getCell(colVenc), ev)));
            }

            CuentaBancaria cuenta = buscarCuenta(texto(fila, cols.get(Campo.CUENTA), fmt, ev), cuentas);
            if (cuenta != null) {
                f.setCuentaId(cuenta.getId());
                f.setCuentaNombre(cuenta.getNombre());
            }

            res.addFila(f);
        }
    }

    private boolean filaVacia(Row fila, DataFormatter fmt, FormulaEvaluator ev) {
        for (Cell c : fila) {
            if (!fmt.formatCellValue(c, ev).isBlank()) {
                return false;
            }
        }
        return true;
    }

    // ---------- conversión de valores ----------

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
                case BOOLEAN -> cell.getBooleanCellValue();
                default -> null;
            };
        } catch (RuntimeException e) {
            return null; // fórmula no soportada o celda con error
        }
    }

    private String texto(Row fila, Integer col, DataFormatter fmt, FormulaEvaluator ev) {
        if (col == null) {
            return null;
        }
        Cell c = fila.getCell(col);
        if (c == null) {
            return null;
        }
        String t = fmt.formatCellValue(c, ev).trim();
        return t.isEmpty() ? null : t;
    }

    private LocalDate aFecha(Object v) {
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof String s) {
            for (DateTimeFormatter f : FORMATOS_FECHA) {
                try {
                    return LocalDate.parse(s.trim(), f);
                } catch (DateTimeParseException ignorada) {
                    // probamos el siguiente formato
                }
            }
        }
        return null;
    }

    /**
     * Devuelve siempre un importe positivo: en gastos, -50 y 50 significan lo
     * mismo.
     */
    private BigDecimal aImporte(Object v) {
        if (v instanceof BigDecimal b) {
            return b.abs().setScale(2, RoundingMode.HALF_UP);
        }
        if (v instanceof String s) {
            String t = s.replace("€", "").replace("EUR", "").replace("eur", "")
                    .replace("\u00A0", "").replace(" ", "")
                    .replace("-", "").replace("(", "").replace(")", "");
            if (t.isEmpty()) {
                return null;
            }
            int punto = t.lastIndexOf('.');
            int coma = t.lastIndexOf(',');
            if (coma >= 0 && punto >= 0) {
                // el último separador es el decimal: 1.234,56 o 1,234.56
                t = coma > punto ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
            } else if (coma >= 0) {
                t = t.replace(',', '.');
            }
            try {
                return new BigDecimal(t).abs().setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String aEstado(Object v) {
        if (v instanceof Boolean b) {
            return b ? "PAGADO" : "PENDIENTE";
        }
        if (v instanceof BigDecimal n) {
            return n.compareTo(BigDecimal.ONE) == 0 ? "PAGADO" : "PENDIENTE";
        }
        if (v instanceof String s) {
            String t = normalizar(s);
            if (t.equals("no") || t.startsWith("no ") || t.contains("pend")) {
                return "PENDIENTE";
            }
            if (t.contains("pagad") || Set.of("si", "x", "ok", "true", "1").contains(t)) {
                return "PAGADO";
            }
        }
        return "PENDIENTE";
    }

    private CuentaBancaria buscarCuenta(String texto, List<CuentaBancaria> cuentas) {
        if (texto == null) {
            return null;
        }
        String t = normalizar(texto);
        if (t.isEmpty()) {
            return null;
        }
        for (CuentaBancaria c : cuentas) {
            String n = normalizar(c.getNombre());
            if (t.equals(n) || t.contains(n) || n.contains(t)) {
                return c;
            }
        }
        return null;
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}