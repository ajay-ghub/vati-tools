package com.ajay.bio.tool.impl;

import com.ajay.bio.exception.ToolExecutionException;
import com.ajay.bio.util.ValidationUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(name = "IG_POST_PROCESSING", mixinStandardHelpOptions = true, version = "22-Jun-2024",
        description = "Process an IG categorization file created as part of IG_CATEGORIZATION tool")
@Log4j2
public class IGPostProcessing implements BaseTool {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec; // injected by picocli

    @CommandLine.Option(names = {"--create-fasta-columns"}, required = true,
            description = "Create NT Merged and AA merged FASTA columns in an existing IG categorization file")
    private boolean createFastaColumns;

    @CommandLine.Option(names = {"--ig-categorization-file"}, required = true,
            description = "Existing IG categorization file to process")
    private File igCategorizationFile;

    private static final String FASTA_COL_FORMAT = ">%s" + System.lineSeparator() + "%s";

    @Override
    public void execute() throws ToolExecutionException {
        if (createFastaColumns) {
            ValidationUtil.validateFile(spec, igCategorizationFile);

            log.info("Executing tool to create FASTA columns in existing IG categorization file");
            try {
                onlyCreateFastaColumns(igCategorizationFile.toPath());
                log.info("Tool completed successfully");
            } catch (Exception e) {
                log.debug("Exception", e);
                log.error("Tool execution failed, please check logs for details", e);
                throw new ToolExecutionException(e);
            }
        }
    }

    private void onlyCreateFastaColumns(Path igCategorizationFilePath) throws IOException {
        final InputStream fis = Files.newInputStream(igCategorizationFilePath);
        try (final Workbook igWorkbook = new HSSFWorkbook(fis)) {
            for (int i = 0; i < igWorkbook.getNumberOfSheets(); i++) {
                Sheet sheet = igWorkbook.getSheetAt(i);
                createFastaColumnsInSheet(sheet);
            }

            fis.close();
            log.info("Updated all sheets; writing updates to file");

            try (final OutputStream os = Files.newOutputStream(igCategorizationFile.toPath())) {
                igWorkbook.write(os);
            }

            log.info("Updated file");
        }
    }

    private void createFastaColumnsInSheet(Sheet sheet) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || row.getLastCellNum() < 22) {
                log.warn("Invalid row number - {} in sheet - {}, ignoring this row", i+1, sheet.getSheetName());
                continue;
            }

            String seqId = getCellStringValue(row.getCell(1));
            String ntMerged = getCellStringValue(row.getCell(22));
            String aaMerged = getCellStringValue(row.getCell(14));

            int ntMergedFastaCellIndex = row.getLastCellNum() + 1;
            row.createCell(ntMergedFastaCellIndex, CellType.STRING).setCellValue(String.format(FASTA_COL_FORMAT, seqId, ntMerged));

            int aaMergedFastaCellIndex = ntMergedFastaCellIndex + 1;
            row.createCell(aaMergedFastaCellIndex, CellType.STRING).setCellValue(String.format(FASTA_COL_FORMAT, seqId, aaMerged));
        }
    }

    private String getCellStringValue(final Cell cell) {
        return getCellValueOrDefault(cell, "");
    }

    private String getCellValueOrDefault(final Cell cell, final String defaultString) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return defaultString;
        }

        return cell.getStringCellValue();
    }
}
