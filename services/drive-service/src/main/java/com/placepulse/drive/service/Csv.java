package com.placepulse.drive.service;

import java.util.List;

/** Minimal CSV writer: quotes every cell, and defuses cells a spreadsheet would run as a formula. */
final class Csv {

    private Csv() {
    }

    static String write(List<String[]> rows) {
        StringBuilder out = new StringBuilder("\uFEFF");
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(cell(row[i]));
            }
            out.append("\r\n");
        }
        return out.toString();
    }

    static String cell(String value) {
        String v = value == null ? "" : value;
        // a leading = + - @ (or tab/CR) makes Excel treat the cell as a formula: a student typed this, so don't trust it
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
