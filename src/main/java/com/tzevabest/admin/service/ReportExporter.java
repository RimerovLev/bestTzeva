package com.tzevabest.admin.service;

import com.tzevabest.admin.dto.Report;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Экспорт отчётов в XLSX и CSV (T-13.2). Заголовки на локали запроса; для иврита лист RTL. */
@Component
public class ReportExporter {

    public byte[] toXlsx(Report report, boolean rtl) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Report");
            if (rtl) {
                sheet.setRightToLeft(true); // ивритский лист справа налево
            }
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < report.headers().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(report.headers().get(c));
                cell.setCellStyle(headerStyle);
            }
            int r = 1;
            for (List<Object> row : report.rows()) {
                Row xr = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) {
                    writeCell(xr.createCell(c), row.get(c));
                }
            }
            for (int c = 0; c < report.headers().size(); c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] toCsv(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // BOM — чтобы Excel корректно открыл UTF-8 (иврит)
        sb.append(String.join(",", report.headers().stream().map(ReportExporter::csv).toList())).append("\r\n");
        for (List<Object> row : report.rows()) {
            sb.append(String.join(",", row.stream().map(v -> csv(String.valueOf(v))).toList())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void writeCell(Cell cell, Object value) {
        if (value instanceof Number num) {
            cell.setCellValue(num.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private static String csv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
