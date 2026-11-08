package es.gastos.importacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class Valores {

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("uuuu-M-d"),
            DateTimeFormatter.ofPattern("d/M/uu"));

    private Valores() {
    }

    static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    static LocalDate aFecha(Object v) {
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
     * Importe con signo. Admite 1.234,56 / 1,234.56 / (80,00) / 80,00- / -80 / 80
     * €.
     */
    static BigDecimal aImporte(Object v) {
        if (v instanceof BigDecimal b) {
            return b.setScale(2, RoundingMode.HALF_UP);
        }
        if (v instanceof String s) {
            String t = s.replace("€", "").replace("EUR", "").replace("eur", "")
                    .replace("\u00A0", "").replace(" ", "");
            boolean negativo = t.contains("-") || (t.startsWith("(") && t.endsWith(")"));
            t = t.replace("-", "").replace("(", "").replace(")", "").replace("+", "");
            if (t.isEmpty()) {
                return null;
            }
            int punto = t.lastIndexOf('.');
            int coma = t.lastIndexOf(',');
            if (coma >= 0 && punto >= 0) {
                t = coma > punto ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
            } else if (coma >= 0) {
                t = t.replace(',', '.');
            }
            try {
                BigDecimal n = new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
                return negativo ? n.negate() : n;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String sha256(String texto) {
        return sha256(texto.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}