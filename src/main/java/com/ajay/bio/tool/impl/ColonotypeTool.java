package com.ajay.bio.tool.impl;

import com.ajay.bio.exception.ToolExecutionException;
import com.ajay.bio.util.ValidationUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "COLONOTYPE", mixinStandardHelpOptions = true, version = "31-Aug-2024",
        description = "Colonotype categorization of sequences")
@Log4j2
public class ColonotypeTool implements BaseTool {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec; // injected by picocli

    @CommandLine.Option(names = {"--input-file"}, required = true,
            description = "Input file to create category")
    private File inputFile;

    @CommandLine.Option(names = {"--v-col-num"}, required = true,
            description = "Index of V column in excel sheet; first column = 1, second column = 2 etc.")
    private int vColNum;

    @CommandLine.Option(names = {"--j-col-num"}, required = true,
            description = "Index of J column in excel sheet; first column = 1, second column = 2 etc.")
    private int jColNum;

    @CommandLine.Option(names = {"--cdr3-col-num"}, required = true,
            description = "Index of CDR3 column in excel sheet; first column = 1, second column = 2 etc.")
    private int cdr3ColNum;

    @Override
    public void execute() throws ToolExecutionException {
        validateInput();

        try {
            executeTool();
        } catch (Exception e) {
            log.error("Exception - ", e);
            throw new ToolExecutionException(e);
        }
    }

    private void executeTool() throws IOException {
        final InputStream fis = Files.newInputStream(inputFile.toPath());
        try (Workbook workbook = new HSSFWorkbook(fis)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                processSheet(sheet);
            }

            fis.close();
            log.info("Updated all sheets; writing updates to file");

            try (final OutputStream os = Files.newOutputStream(inputFile.toPath())) {
                workbook.write(os);
            }

            log.info("Updated file");
        }
    }

    private void processSheet(Sheet sheet) {
        final Map<String, List<Row>> rowMap = new HashMap<>();

        int headerCellNum = sheet.getRow(0).getLastCellNum() + 1;
        sheet.getRow(0).createCell(headerCellNum, CellType.STRING).setCellValue("Group");

        for (int i = 1; i < sheet.getLastRowNum(); i++) {

            final Row row = sheet.getRow(i);

            if (row == null) {
                continue;
            }
            
            if (!(vColNum < row.getLastCellNum() && jColNum < row.getLastCellNum() && cdr3ColNum < row.getLastCellNum())) {
                log.warn("Invalid row, sheet - {}, row number - {}", sheet.getSheetName(), i + 1);
                continue;
            }

            final Cell vCell = sheet.getRow(i).getCell(vColNum);
            final Cell jCell = sheet.getRow(i).getCell(jColNum);
            final Cell cdr3Cell = sheet.getRow(i).getCell(cdr3ColNum);

            // main logic by fuzu
            final String key = getStringValue(vCell) + ":" + getStringValue(jCell) + ":" + getStringValue(cdr3Cell).length();

            if (rowMap.containsKey(key)) {
                rowMap.get(key).add(row);
            } else {
                rowMap.put(key, new ArrayList<>());
                rowMap.get(key).add(row);
            }
        }

        int groupNum = 1;
        for (Map.Entry<String, List<Row>> entry : rowMap.entrySet()) {
            for (Row row : entry.getValue()) {
                if (row.getCell(headerCellNum) == null) {
                    row.createCell(headerCellNum, CellType.STRING).setCellValue(String.format("Group-%s", groupNum));
                } else {
                    row.getCell(headerCellNum).setCellValue(String.format("Group-%s", groupNum));
                }
            }

            groupNum++;
        }
    }

    private String getStringValue(Cell cell) {
        if (cell == null || StringUtils.isEmpty(cell.getStringCellValue())) {
            return "";
        }

        return cell.getStringCellValue();
    }

    private void validateInput() throws CommandLine.ParameterException {
        ValidationUtil.validateFile(spec, inputFile);

        ValidationUtil.validateRange(spec, vColNum, 1, 1000);
        ValidationUtil.validateRange(spec, jColNum, 1, 1000);
        ValidationUtil.validateRange(spec, cdr3ColNum, 1, 1000);
    }
}
