package com.ajay.bio.tool.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ajay.bio.client.ClustalOmegaClient;
import com.ajay.bio.exception.ToolExecutionException;
import com.ajay.bio.util.ValidationUtil;
import com.google.common.collect.Lists;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import picocli.CommandLine;

@CommandLine.Command(name = "CLUSTAL_ALIGNMENT", mixinStandardHelpOptions = true, version = "14-Aug-2022",
        description = "Clustal Alignment of AA/NT sequences from IG Categorisation Tool output")
@Log4j2
public class ClustalAlignmentTool implements BaseTool {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec; // injected by picocli

    @CommandLine.ArgGroup(multiplicity = "1")
    private ExclusiveOptions exclusiveOptions;

    static class ExclusiveOptions {
        @CommandLine.Option(names = {"--ig-file"}, required = true,
                description = "IG Excel file with IGH, IGK and IGL sequence sheets")
        private File igFile;

        @CommandLine.Option(names = {"--review-pending-jobs"}, required = true,
                description = "Review already submitted jobs instead of submitting new ones")
        private boolean reviewPendingJobs;
    }

    @CommandLine.Option(names = {"--output-dir"}, required = true,
            description = "Directory where output files should be saved")
    private File outputDir;


    private static final String COMBINED_IG_FILE_IGH_SHEET_NAME = "IGH";
    private static final String COMBINED_IG_FILE_IGK_SHEET_NAME = "IGK";
    private static final String COMBINED_IG_FILE_IGL_SHEET_NAME = "IGL";
    private static final String ANALYSIS_DIR_NAME_FORMAT = "%s-ClustalAnalysis";

    @Override
    public void execute() throws ToolExecutionException {
        ValidationUtil.validateDir(spec, outputDir.toPath());
        if (exclusiveOptions.reviewPendingJobs) {
            reviewClustalAlignmentJobs();
        } else {
            ValidationUtil.validateFile(spec, exclusiveOptions.igFile.toPath());
            submitJobs(exclusiveOptions.igFile.toPath());
        }
    }

    private void reviewClustalAlignmentJobs() throws ToolExecutionException {
        try {
            final Path ighOutputDir = Paths.get(outputDir.toPath().toAbsolutePath().toString(),
                                                getClustalAnalysisDirName(COMBINED_IG_FILE_IGH_SHEET_NAME));
            if (retryPendingJobs(ighOutputDir)) {
                log.info("All jobs completed for IGH category");
            }

            final Path igkOutputDir = Paths.get(outputDir.toPath().toAbsolutePath().toString(),
                                                getClustalAnalysisDirName(COMBINED_IG_FILE_IGK_SHEET_NAME));
            if (retryPendingJobs(igkOutputDir)) {
                log.info("All jobs completed for IGK category");
            }

            final Path iglOutputDir = Paths.get(outputDir.toPath().toAbsolutePath().toString(),
                                                getClustalAnalysisDirName(COMBINED_IG_FILE_IGL_SHEET_NAME));
            if (retryPendingJobs(iglOutputDir)) {
                log.info("All jobs completed for IGL category");
            }
        } catch (IOException e) {
            log.debug("IO Exception - ", e);
            throw new ToolExecutionException(e);
        }
    }

    private boolean retryPendingJobs(final Path igSubCategoryDir) throws ToolExecutionException, IOException {
        final Path pendingJobFilePath = getPendingJobFilePath(igSubCategoryDir);

        final Set<String> completedJobDetails = new HashSet<>();
        if (Files.exists(pendingJobFilePath)) {
            final Set<String> pendingJobDetails
                    = new HashSet<>(FileUtils.readLines(pendingJobFilePath.toFile(), StandardCharsets.UTF_8));

            for (String pendingJobInfo : pendingJobDetails) {
                final String jobId = pendingJobInfo.split(",")[1];
                final String outputFileName = pendingJobInfo.split(",")[0];
                final String jobStatus = ClustalOmegaClient.getJobStatus(jobId);
                if ("FINISHED".equals(jobStatus)) {
                    log.debug("Job - {} is completed, writing output to file - {}", jobId, outputFileName);
                    final String jobOutput = ClustalOmegaClient.getCompletedJobOutput(jobId);
                    final Path outputFilePath = Paths.get(igSubCategoryDir.toAbsolutePath().toString(), outputFileName);
                    try {
                        FileUtils.writeStringToFile(outputFilePath.toFile(), jobOutput, StandardCharsets.UTF_8);
                        completedJobDetails.add(pendingJobInfo);
                    } catch (IOException e) {
                        log.debug("IO Exception", e);
                        throw new ToolExecutionException(e);
                    }
                } else {
                    log.info("Job - {} is still in PENDING status..", jobId);
                }
            }

            pendingJobDetails.removeAll(completedJobDetails);

            if (pendingJobDetails.isEmpty()) {
                FileUtils.delete(pendingJobFilePath.toFile());
                return true;
            } else {
                // write updated pending job details to file
                FileUtils.writeLines(pendingJobFilePath.toFile(), pendingJobDetails);
                log.info("Updated pending job counts - {}", pendingJobDetails.size());
                return false;
            }
        }

        return true;
    }

    private void submitJobs(final Path igFilePath) throws ToolExecutionException {
        try (final Workbook igExcelFile = openExcelWorkbook(igFilePath)) {
            log.info("Submitting IGH Jobs");
            processSheet(igExcelFile, COMBINED_IG_FILE_IGH_SHEET_NAME);

            log.info("Submitting IGK Jobs");
            processSheet(igExcelFile, COMBINED_IG_FILE_IGK_SHEET_NAME);

            log.info("Submitting IGL Jobs");
            processSheet(igExcelFile, COMBINED_IG_FILE_IGL_SHEET_NAME);

            log.info("All Clustal Analysis Jobs submitted and noted for review");
        } catch (IOException e) {
            log.debug("IO Exception - ", e);
            throw new ToolExecutionException(e);
        }
    }

    private void processSheet(final Workbook igWorkbook, final String sheetName) throws ToolExecutionException,
                                                                                                IOException {
        Map<String, List<Row>> ighSubCategoryRowMap = createIgSubCategoryMap(igWorkbook.getSheet(sheetName));
        final Set<String> pendingJobDetails = submitClustalJobs(ighSubCategoryRowMap);

        final Path outputSubDir = Paths.get(outputDir.toPath().toAbsolutePath().toString(),
                                            getClustalAnalysisDirName(sheetName));
        outputSubDir.toFile().mkdir();

        final Path pendingJobFilePath = getPendingJobFilePath(outputSubDir);
        FileUtils.writeLines(pendingJobFilePath.toFile(), pendingJobDetails);
    }

    private Set<String> submitClustalJobs(final Map<String, List<Row>> ighSubCategoryRowMap) throws ToolExecutionException, IOException {
        final Set<String> pendingJobDetailSet = new HashSet<>();
        for (Map.Entry<String, List<Row>> entry : ighSubCategoryRowMap.entrySet()) {
            final String category = entry.getKey();
            final List<Row> rows = entry.getValue();

            log.debug("Processing subcategory - {}", category);

            final List<String> aaSequenceList = new ArrayList<>();
            final List<String> ntSequenceList = new ArrayList<>();

            for (Row r : rows) {
                final String sequenceId = r.getCell(1).getStringCellValue();
                // AA - Merged
                final String aaMergedSequence = r.getCell(14).getStringCellValue();
                aaSequenceList.add(String.format(">%s%n%s%n", sequenceId, aaMergedSequence));

                // NT - Merged
                final String ntMergedSequence = r.getCell(22).getStringCellValue();
                ntSequenceList.add(String.format(">%s%n%s%n", sequenceId, ntMergedSequence));
            }

            // Compute Clustal Omega Alignment
            try {
                if (aaSequenceList.size() > 1) {
                    log.debug("Computing clustal omega alignment for a total of {} AA sequences", aaSequenceList.size());
                    final String aaClustalOmegaFileName = String.format("%s-aa-clustal-omega.txt", category);
                    final String jobId = ClustalOmegaClient.submitProteinSequenceJob(aaSequenceList);
                    final String pendingJobDetail = aaClustalOmegaFileName + "," + jobId;
                    pendingJobDetailSet.add(pendingJobDetail);

                    log.debug("Submitted Clustal Alignment Job, details - {}", pendingJobDetail);
                } else {
                    log.info("AA Sequence Count for Category - {} is less than 2", category);
                }
            } catch (Exception e) {
                log.debug("Exception in AA sequencing", e);
                throw new ToolExecutionException(e);
            }


            try {
                if (ntSequenceList.size() > 1) {
                    log.debug("Computing clustal omega alignment for a total of {} NT sequences", ntSequenceList.size());
                    final String ntClustalOmegaFileName = String.format("%s-nt-clustal-omega.txt", category);
                    final String jobId = ClustalOmegaClient.submitDNASequenceJob(ntSequenceList);

                    final String pendingJobDetail = ntClustalOmegaFileName + "," + jobId;
                    pendingJobDetailSet.add(pendingJobDetail);

                    log.debug("Submitted Clustal Alignment Job - {}", pendingJobDetail);
                } else {
                    log.info("NT Sequence Count for Category - {} is less than 2", category);
                }
            } catch (Exception e) {
                log.debug("Exception in NT sequencing", e);
                throw new ToolExecutionException(e);
            }
        }

        return pendingJobDetailSet;
    }

    private String getClustalAnalysisDirName(final String igSubCategory) {
        return String.format(ANALYSIS_DIR_NAME_FORMAT, igSubCategory);
    }

    private Path getPendingJobFilePath(final Path igSubCategoryDir) {
        return Paths.get(igSubCategoryDir.toAbsolutePath().toString(), "PendingJobs.txt");
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
