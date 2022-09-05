package com.ajay.bio.tool.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ajay.bio.exception.ToolExecutionException;
import com.ajay.bio.util.ValidationUtil;
import com.google.common.collect.Lists;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import picocli.CommandLine;

@CommandLine.Command(name = "IG_CATEGORISATION", mixinStandardHelpOptions = true, version = "14-Aug-2022",
        description = "Categorise IMGT analysis of sequence files based on IG categories")
@Log4j2
public class IGCategorisationTool implements BaseTool {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec; // injected by picocli

    @CommandLine.Option(names = {"--input-dir"}, required = true,
            description = "Directory with input files (IMGT Analysis Excel files)")
    private File inputDir;

    @CommandLine.Option(names = {"--output-dir"}, required = true,
            description = "Directory where output files should be saved")
    private File outputDir;

    @CommandLine.Option(names = {"--create-ig-subcategories"},
            description = "Create IGH, IGK, IGL sub-category files")
    private boolean createIGSubCategories;

    private static final String COMBINED_IG_FILE_NAME = "IG_Combined.xls";
    private static final String COMBINED_IG_FILE_IGH_SHEET_NAME = "IGH";
    private static final String COMBINED_IG_FILE_IGK_SHEET_NAME = "IGK";
    private static final String COMBINED_IG_FILE_IGL_SHEET_NAME = "IGL";
    private static final String COMBINED_IG_FILE_UNKNOWN_SHEET_NAME = "Unknown";

    private static final String IGH_DIR_NAME = "IGH";
    private static final String IGK_DIR_NAME = "IGK";
    private static final String IGL_DIR_NAME = "IGL";

    @Override
    public void execute() throws ToolExecutionException {
        final Path inputDirPath = inputDir.toPath();
        final Path outputDirPath = outputDir.toPath();
        ValidationUtil.validateDir(spec, inputDirPath, outputDirPath);

        final Path combinedIGFilePath = createCombinedOutput(inputDirPath, outputDirPath);

        if (createIGSubCategories) {
            try {
                createIGSubCategory(combinedIGFilePath, outputDirPath);
            } catch (IOException e) {
                log.debug("IO Exception", e);
                log.error("Input/Output Exception, please retry");
                throw new ToolExecutionException(e);
            }
        }
    }

    private Path createCombinedOutput(final Path imgtOutputDir, final Path igOutputDir) throws ToolExecutionException {
        final Path igSeqExcelFile = Paths.get(igOutputDir.toAbsolutePath().toString(), COMBINED_IG_FILE_NAME);
        log.info("Creating combined IG file - {}", igSeqExcelFile);
        try (final Workbook igWorkbook = new HSSFWorkbook()) {
            final Sheet ighSheet = igWorkbook.createSheet(COMBINED_IG_FILE_IGH_SHEET_NAME);
            final Sheet igkSheet = igWorkbook.createSheet(COMBINED_IG_FILE_IGK_SHEET_NAME);
            final Sheet iglSheet = igWorkbook.createSheet(COMBINED_IG_FILE_IGL_SHEET_NAME);
            final Sheet unknownSheet = igWorkbook.createSheet(COMBINED_IG_FILE_UNKNOWN_SHEET_NAME);

            addIGHeaderRow(ighSheet);
            addIGHeaderRow(igkSheet);
            addIGHeaderRow(iglSheet);
            addIGHeaderRow(unknownSheet);

            for (final File inputFile : FileUtils.listFiles(imgtOutputDir.toFile(), TrueFileFilter.INSTANCE, null)) {
                if (inputFile.getName().endsWith(".xls")) {

                    log.info("Processing Excel File - {}", inputFile.getName());
                    try {
                        final Workbook workbook = openExcelWorkbook(inputFile.toPath());
                        final Sheet aaSequenceSheet = workbook.getSheet("AA-sequences");
                        final String vGene = getCellValueOrDefault(aaSequenceSheet.getRow(1).getCell(3),
                                                                   "EmptyVGene");

                        log.debug("Found VGene - {} for sequence - {}", vGene, inputFile.getName());
                        if (vGene.contains("IGH")) {
                            updateSheetWithInfo(ighSheet, aaSequenceSheet, workbook.getSheet("Nt-sequences"),
                                                workbook.getSheet("Summary"));
                        } else if (vGene.contains("IGK")) {
                            updateSheetWithInfo(igkSheet, aaSequenceSheet, workbook.getSheet("Nt-sequences"),
                                                workbook.getSheet("Summary"));
                        } else if (vGene.contains("IGL")) {
                            updateSheetWithInfo(iglSheet, aaSequenceSheet, workbook.getSheet("Nt-sequences"),
                                                workbook.getSheet("Summary"));
                        } else {
                            log.error("Invalid VGene found for sequence - {}, vGene - {}", inputFile.getName(), vGene);
                            updateSheetWithInfo(unknownSheet, aaSequenceSheet, workbook.getSheet("Nt-sequences"),
                                                workbook.getSheet("Summary"));
                        }
                    } catch (IOException e) {
                        log.debug("IO Exception while processing file", e);
                        throw new ToolExecutionException("Input/Output Error");
                    }
                }
            }

            final FileOutputStream ighExcelFile = new FileOutputStream(igSeqExcelFile.toFile());
            igWorkbook.write(ighExcelFile);

            log.info("Created combined IG Seq output file");
            return igSeqExcelFile;
        } catch (IOException e) {
            log.debug("IO Exception while processing file", e);
            throw new ToolExecutionException("Input/Output Error");
        }
    }

    private void createIGSubCategory(final Path igFile, final Path igOutputDir) throws IOException {
        final Path ighSubCategoryDir = Paths.get(igOutputDir.toAbsolutePath().toString(), IGH_DIR_NAME);
        ighSubCategoryDir.toFile().mkdir();
        final Path igkSubCategoryDir = Paths.get(igOutputDir.toAbsolutePath().toString(), IGK_DIR_NAME);
        igkSubCategoryDir.toFile().mkdir();
        final Path iglSubCategoryDir = Paths.get(igOutputDir.toAbsolutePath().toString(), IGL_DIR_NAME);
        iglSubCategoryDir.toFile().mkdir();

        try (final Workbook igExcelFile = openExcelWorkbook(igFile)) {
            log.info("Creating IGH subcategory files");
            Map<String, List<Row>> ighSubCategoryRowMap = createIgSubCategoryMap(igExcelFile.getSheet(COMBINED_IG_FILE_IGH_SHEET_NAME));
            createIGSubCategoryFiles(ighSubCategoryRowMap, ighSubCategoryDir);

            log.info("Creating IGK subcategory files");
            Map<String, List<Row>> igkSubCategoryRowMap = createIgSubCategoryMap(igExcelFile.getSheet(COMBINED_IG_FILE_IGK_SHEET_NAME));
            createIGSubCategoryFiles(igkSubCategoryRowMap, igkSubCategoryDir);

            log.info("Creating IGL subcategory files");
            Map<String, List<Row>> iglSubCategoryRowMap = createIgSubCategoryMap(igExcelFile.getSheet(COMBINED_IG_FILE_IGL_SHEET_NAME));
            createIGSubCategoryFiles(iglSubCategoryRowMap, iglSubCategoryDir);

            log.info("Created all IG subcategory files");
        }
    }

    private Map<String, List<Row>> createIgSubCategoryMap(final Sheet igCategorySheet) {
        final Map<String, List<Row>> igSubCategoryRowMap = new HashMap<>();

        igCategorySheet.rowIterator().forEachRemaining(row -> {
            if (row.getRowNum() > 0) {
                // V-Gene value, e.g., "Homsap IGHV3-30*03 F, or Homsap IGHV3-30*18 F or Homsap IGHV3-30-5*01 F"
                final String vGeneValue = getCellValueOrDefault(row.getCell(4), "An EmptyVGene");
                final String category = vGeneValue.split(" ")[1];
                final String sanitizedCategory = category.replaceAll("\\*", "_");

                log.debug("Found VGene subcategory - " + category + " for VGene - " + vGeneValue);

                if (!igSubCategoryRowMap.containsKey(sanitizedCategory)) {
                    igSubCategoryRowMap.put(sanitizedCategory, Lists.newArrayList());
                }

                igSubCategoryRowMap.get(sanitizedCategory).add(row);
            }
        });

        return igSubCategoryRowMap;
    }

    /**
     * Creates IG Sub Category files.
     */
    private void createIGSubCategoryFiles(final Map<String, List<Row>> igSubcategory, final Path igSubCategoryDir) throws IOException {
        for (Map.Entry<String, List<Row>> entry : igSubcategory.entrySet()) {
            final String category = entry.getKey();
            final List<Row> rows = entry.getValue();

            log.info("Processing subcategory - {}", category);

            final Path subCategoryFile = Paths.get(igSubCategoryDir.toAbsolutePath().toString(), category + ".xls");

            try (final Workbook workbook = new HSSFWorkbook()) {
                final Sheet sheet = workbook.createSheet("Sequences");
                addIGHeaderRow(sheet);
                final Row headerRow = sheet.getRow(0);
                headerRow.createCell(headerRow.getLastCellNum(), CellType.STRING).setCellValue("Category");

                int rowNum = 1;
                for (Row r : rows) {
                    final Row newRow = sheet.createRow(rowNum++);
                    r.cellIterator().forEachRemaining(cell -> {
                        if (cell.getCellType() == CellType.NUMERIC) {
                            newRow.createCell(cell.getColumnIndex(),
                                              cell.getCellType()).setCellValue(cell.getNumericCellValue());
                        } else {
                            newRow.createCell(cell.getColumnIndex(),
                                              cell.getCellType()).setCellValue(getCellStringValue(cell));
                        }
                    });

                    final Cell categoryCell = newRow.createCell(newRow.getLastCellNum(), CellType.STRING);
                    final String vDomainFunctionality = getCellStringValue(newRow.getCell(2));
                    final String junction = getCellStringValue(newRow.getCell(3));

                    if ("productive".equalsIgnoreCase(vDomainFunctionality) && "in-frame".equalsIgnoreCase(junction)) {
                        categoryCell.setCellValue("Yes");
                    } else {
                        categoryCell.setCellValue("No");
                    }
                }

                final FileOutputStream outputStream = new FileOutputStream(subCategoryFile.toFile());
                workbook.write(outputStream);
            }
        }
    }

    private void addIGHeaderRow(final Sheet sheet) {
        final Row row = sheet.createRow(0);
        row.createCell(0, CellType.STRING).setCellValue("Sequence Number");
        row.createCell(1, CellType.STRING).setCellValue("Sequence ID");

        row.createCell(2, CellType.STRING).setCellValue("V-DOMAIN Functionality");
        row.createCell(3, CellType.STRING).setCellValue("JUNCTION frame");

        row.createCell(4, CellType.STRING).setCellValue("V-GENE and allele");
        row.createCell(5, CellType.STRING).setCellValue("J-GENE and allele");
        row.createCell(6, CellType.STRING).setCellValue("D-GENE and allele");

        row.createCell(7, CellType.STRING).setCellValue("AA - FR1-IMGT");
        row.createCell(8, CellType.STRING).setCellValue("AA - CDR1-IMGT");
        row.createCell(9, CellType.STRING).setCellValue("AA - FR2-IMGT");
        row.createCell(10, CellType.STRING).setCellValue("AA - CDR2-IMGT");
        row.createCell(11, CellType.STRING).setCellValue("AA - FR3-IMGT");
        row.createCell(12, CellType.STRING).setCellValue("AA - CDR3-IMGT");
        row.createCell(13, CellType.STRING).setCellValue("AA - FR4-IMGT");

        row.createCell(14, CellType.STRING).setCellValue("AA - Merged");

        row.createCell(15, CellType.STRING).setCellValue("NT - FR1-IMGT");
        row.createCell(16, CellType.STRING).setCellValue("NT - CDR1-IMGT");
        row.createCell(17, CellType.STRING).setCellValue("NT - FR2-IMGT");
        row.createCell(18, CellType.STRING).setCellValue("NT - CDR2-IMGT");
        row.createCell(19, CellType.STRING).setCellValue("NT - FR3-IMGT");
        row.createCell(20, CellType.STRING).setCellValue("NT - CDR3-IMGT");
        row.createCell(21, CellType.STRING).setCellValue("NT - FR4-IMGT");

        row.createCell(22, CellType.STRING).setCellValue("NT - Merged");
    }

    private void updateSheetWithInfo(final Sheet sheetToUpdate, final Sheet aaSequenceSheet,
                                     final Sheet ntSequenceSheet, final Sheet summarySheet) {

        final Row summarySheetRow = summarySheet.getRow(1);
        final Row aaSheetRow = aaSequenceSheet.getRow(1);
        final Row ntSheetRow = ntSequenceSheet.getRow(1);

        final int newRowNum = sheetToUpdate.getLastRowNum() + 1;
        final Row newRow = sheetToUpdate.createRow(newRowNum);

        newRow.createCell(0, CellType.NUMERIC).setCellValue(newRowNum);
        // sequence ID
        newRow.createCell(1, CellType.STRING).setCellValue(getCellStringValue(summarySheetRow.getCell(1)));
        // V-Domain Functionality
        newRow.createCell(2, CellType.STRING).setCellValue(getCellStringValue(summarySheetRow.getCell(2)));
        // JUNCTION frame
        newRow.createCell(3, CellType.STRING).setCellValue(getCellStringValue(summarySheetRow.getCell(19)));

        // V-GENE and allele
        newRow.createCell(4, CellType.STRING).setCellValue(getCellStringValue(summarySheetRow.getCell(3)));
        // J-GENE and allele
        newRow.createCell(5, CellType.STRING).setCellValue(getCellStringValue(summarySheetRow.getCell(7)));
        // D-GENE and allele
        newRow.createCell(6, CellType.STRING).setCellValue(getCellStringValue(summarySheetRow.getCell(11)));

        // AA FR1-IMGT
        final String aaFr1 = getCellStringValue(aaSheetRow.getCell(9));
        newRow.createCell(7, CellType.STRING).setCellValue(aaFr1);
        // AA CDR1-IMGT
        final String aaCdr1 = getCellStringValue(aaSheetRow.getCell(10));
        newRow.createCell(8, CellType.STRING).setCellValue(aaCdr1);
        // AA - FR2-IMGT
        final String aaFr2 = getCellStringValue(aaSheetRow.getCell(11));
        newRow.createCell(9, CellType.STRING).setCellValue(aaFr2);
        // AA - CDR2-IMGT
        final String aaCdr2 = getCellStringValue(aaSheetRow.getCell(12));
        newRow.createCell(10, CellType.STRING).setCellValue(aaCdr2);
        // AA - FR3-IMGT
        final String aaFr3 = getCellStringValue(aaSheetRow.getCell(13));
        newRow.createCell(11, CellType.STRING).setCellValue(aaFr3);
        // AA - CDR3-IMGT
        final String aaCdr3 = getCellStringValue(aaSheetRow.getCell(14));
        newRow.createCell(12, CellType.STRING).setCellValue(aaCdr3);
        // AA - FR4-IMGT
        final String aaFr4 = getCellStringValue(aaSheetRow.getCell(17));
        newRow.createCell(13, CellType.STRING).setCellValue(aaFr4);
        // manually merge above AA columns
        final String aaMerged = aaFr1 + aaCdr1 + aaFr2 + aaCdr2 + aaFr3 + aaCdr3 + aaFr4;
        newRow.createCell(14, CellType.STRING).setCellValue(aaMerged);

        // NT - FR1-IMGT
        final String ntFr1 = getCellStringValue(ntSheetRow.getCell(9));
        newRow.createCell(15, CellType.STRING).setCellValue(ntFr1);
        // NT - CDR1-IMGT
        final String ntCdr1 = getCellStringValue(ntSheetRow.getCell(10));
        newRow.createCell(16, CellType.STRING).setCellValue(ntCdr1);
        // NT - FR2-IMGT
        final String ntFr2 = getCellStringValue(ntSheetRow.getCell(11));
        newRow.createCell(17, CellType.STRING).setCellValue(ntFr2);
        // NT - CDR2-IMGT
        final String ntCdr2 = getCellStringValue(ntSheetRow.getCell(12));
        newRow.createCell(18, CellType.STRING).setCellValue(ntCdr2);
        // NT - FR3-IMGT
        final String ntFr3 = getCellStringValue(ntSheetRow.getCell(13));
        newRow.createCell(19, CellType.STRING).setCellValue(ntFr3);
        // NT - CDR3-IMGT
        final String ntCdr3 = getCellStringValue(ntSheetRow.getCell(14));
        newRow.createCell(20, CellType.STRING).setCellValue(ntCdr3);
        // NT - FR4-IMGT
        final String ntFr4 = getCellStringValue(ntSheetRow.getCell(41));
        newRow.createCell(21, CellType.STRING).setCellValue(ntFr4);
        // manually merge above NT columns
        final String ntMerged = ntFr1 + ntCdr1 + ntFr2 + ntCdr2 + ntFr3 + ntCdr3 + ntFr4;
        newRow.createCell(22, CellType.STRING).setCellValue(ntMerged);
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

    private static Workbook openExcelWorkbook(final Path igFile) throws IOException {
        //return WorkbookFactory.create(igFile.toFile());
        return new HSSFWorkbook(new FileInputStream(igFile.toFile()));
    }
}
