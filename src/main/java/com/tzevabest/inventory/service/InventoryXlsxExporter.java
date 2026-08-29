package com.tzevabest.inventory.service;

import com.tzevabest.inventory.dto.InventoryListItem;
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
import java.util.List;

/** Сборка XLSX-файла остатков (Apache POI). */
@Component
public class InventoryXlsxExporter {

    private static final String[] HEADERS = {
            "SKU", "Product", "Warehouse", "Qty", "Reserved", "Available", "Threshold", "Status"};

    public byte[] export(List<InventoryListItem> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Inventory");

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (InventoryListItem item : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(item.sku());
                row.createCell(1).setCellValue(item.productName() == null ? "" : item.productName());
                row.createCell(2).setCellValue(item.warehouseCode());
                row.createCell(3).setCellValue(item.qty());
                row.createCell(4).setCellValue(item.reservedQty());
                row.createCell(5).setCellValue(item.available());
                row.createCell(6).setCellValue(item.lowStockThreshold() == null ? "" : item.lowStockThreshold().toString());
                row.createCell(7).setCellValue(item.status().name());
            }
            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
