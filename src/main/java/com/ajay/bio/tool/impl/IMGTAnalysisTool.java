package com.ajay.bio.tool.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.ajay.bio.client.IMGTClient;
import com.ajay.bio.exception.ToolExecutionException;
import com.ajay.bio.exception.ValidationException;
import com.ajay.bio.util.ValidationUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import picocli.CommandLine;

@CommandLine.Command(name = "IMGT_ANALYSIS", mixinStandardHelpOptions = true, version = "14-Aug-2022",
        description = "IMGT Analysis of FASTA files")
@Log4j2
public class IMGTAnalysisTool implements BaseTool {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec; // injected by picocli

    @CommandLine.Option(names = {"--input-dir"}, required = true,
            description = "Directory with input files")
    private File inputDir;

    @CommandLine.Option(names = {"--output-dir"}, required = true,
            description = "Directory where output files should be saved")
    private File outputDir;

    @CommandLine.Option(names = {"--parallelism"},
            description = {"Number of tasks to execute in parallel, value should be in range [1, 10]",
                           "More tasks in parallel would required more network bandwidth"})
    private int parallelism = 1;

    @Override
    public void execute() throws ToolExecutionException {
        validateInput();

        try {
            executeTool();
        } catch (Exception e) {
            log.debug("Exception - ", e);
            throw new ToolExecutionException(e);
        }
    }

    private static class ImgtExcelFileWorker implements Callable<Boolean> {
        private final String fastaSequence;
        private final Path excelFilePath;

        public ImgtExcelFileWorker(final String fastaSequence, final Path excelFilePath) {
            this.fastaSequence = fastaSequence;
            this.excelFilePath = excelFilePath;
        }

        @Override
        public Boolean call() {
            final String sequenceName = FilenameUtils.removeExtension(excelFilePath.toFile().getName());
            log.info("Processing sequence - {}", sequenceName);
            for (int i = 0; i < 3; i++) {
                try {
                    createImgtExcelFile(fastaSequence, excelFilePath);
                    return true;
                } catch (Exception e) {
                    log.debug("IMGT Analysis Failure - ", e);
                    log.warn("Retrying IMGT analysis for sequence - {}", sequenceName);
                }
            }

            log.error("IMGT Analysis FAILED for sequence - {}", sequenceName);
            return false;
        }

        private static void createImgtExcelFile(final String fastaSequence, final Path imgtExcelFilePath) throws Exception {
            log.debug("Invoking IMGT website..");
            final byte[] response = IMGTClient.getIMGTAnalysisResponse(fastaSequence);
            log.debug("Received response from IMGT website");
            FileUtils.writeByteArrayToFile(imgtExcelFilePath.toFile(), response);
            if (!isValidResponse(imgtExcelFilePath)) {
                final String errorHtml = FileUtils.readFileToString(imgtExcelFilePath.toFile(), StandardCharsets.UTF_8);

                final Path errorDirPath = Paths.get(imgtExcelFilePath.getParent().toAbsolutePath().toString(), "Error");
                errorDirPath.toFile().mkdir();

                final String errorFileName = FilenameUtils.removeExtension(imgtExcelFilePath.toFile().getName());
                final Path errorFile = Paths.get(errorDirPath.toAbsolutePath().toString(), errorFileName + ".html");
                FileUtils.writeStringToFile(errorFile.toFile(), errorHtml, StandardCharsets.UTF_8);

                FileUtils.delete(imgtExcelFilePath.toFile());

                log.error("Error response from IMGT for sequence - {}", errorFileName);
            } else {
                log.info("Saved IMGT analysis to file - {}", imgtExcelFilePath.getFileName());
            }
        }

        private static boolean isValidResponse(final Path imgtExcelFilePath) {
            try {
                final Workbook workbook = openExcelWorkbook(imgtExcelFilePath);
                workbook.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private static Workbook openExcelWorkbook(final Path igFile) throws IOException {
            //return WorkbookFactory.create(igFile.toFile());
            return new HSSFWorkbook(new FileInputStream(igFile.toFile()));
        }
    }

    private void executeTool() throws IOException, ExecutionException, InterruptedException, ToolExecutionException {
        final Path outputDirPath = outputDir.toPath();
        final Path imgOutputPath = Paths.get(outputDirPath.toAbsolutePath().toString(), "IMGT-Output");
        imgOutputPath.toFile().mkdir();

        log.info("IMGT Analysis will be saved at - {}", imgOutputPath.toAbsolutePath());

        final Set<Path> fastaFilesToProcess = getFilesToProcess(inputDir.toPath(), imgOutputPath);
        analyzeSequences(fastaFilesToProcess, imgOutputPath);
    }

    private void analyzeSequences(final Set<Path> filesToProcess, final Path imgOutputPath)
            throws InterruptedException, ExecutionException, ToolExecutionException {
        final ExecutorService executorService = Executors.newFixedThreadPool(parallelism);

        final List<ImgtExcelFileWorker> workers = new ArrayList<>();

        for (Path inputFile : filesToProcess) {
            if (inputFile.getFileName().toString().endsWith(".fasta")) {
                final String inputFileName = FilenameUtils.removeExtension(inputFile.getFileName().toString());
                try {
                    final String fastaSequence = FileUtils.readFileToString(inputFile.toFile(), StandardCharsets.UTF_8);
                    final Path imgtExcelFilePath = Paths.get(imgOutputPath.toAbsolutePath().toString(),
                                                             inputFileName + ".xls");

                    workers.add(new ImgtExcelFileWorker(fastaSequence, imgtExcelFilePath));
                } catch (Exception e) {
                    log.error("Failed to process IMGT file - {}", inputFileName);
                    throw new ToolExecutionException("Failed to process IMGT file", e);
                }
            }
        }

        final int totalCount = workers.size();
        int successCount = 0;
        try {
            final List<Future<Boolean>> futures = executorService.invokeAll(workers);

            for (final Future<Boolean> f : futures) {
                final Boolean result = f.get();
                if (Boolean.TRUE.equals(result)) {
                    ++successCount;
                }
            }
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to complete IMGT analysis, please retry");
            throw e;
        } finally {
            IMGTClient.shutDown();
        }

        log.info("IMGT Analysis Completed; {}/{} sequence succeeded", successCount, totalCount);
    }

    private Set<Path> getFilesToProcess(final Path inputDirPath, final Path imgtOutputDirPath) throws IOException {
        final Set<String> alreadyProcessedFileNames = new HashSet<>();
        try (final Stream<Path> fileList = Files.list(imgtOutputDirPath)) {
            fileList.forEach(inputFile -> {
                final String fileName = FilenameUtils.removeExtension(inputFile.toFile().getName());
                alreadyProcessedFileNames.add(fileName);
            });
        }

        final Set<Path> filesToProcess = new HashSet<>();
        try (final Stream<Path> fileList = Files.list(inputDirPath)) {
            fileList.forEach(inputFile -> {
                final String fileName = FilenameUtils.removeExtension(inputFile.toFile().getName());
                if (alreadyProcessedFileNames.contains(fileName)) {
                    log.info("File already processed, skipping - {}", fileName);
                } else {
                    filesToProcess.add(inputFile);
                }
            });
        }

        return filesToProcess;
    }

    private void validateInput() throws CommandLine.ParameterException {
        final Path inputDirPath = inputDir.toPath();
        final Path outputDirPath = outputDir.toPath();

        ValidationUtil.validateDir(spec, inputDirPath, outputDirPath);

        if (parallelism < 1 || parallelism > 10) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Parallelism should be in range [0, 10]");
        }
    }
}
