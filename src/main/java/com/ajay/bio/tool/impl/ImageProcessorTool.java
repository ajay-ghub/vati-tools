package com.ajay.bio.tool.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.ajay.bio.exception.ToolExecutionException;
import com.ajay.bio.exception.ValidationException;
import com.ajay.bio.util.ImageProcessingStrategyTwo;
import com.ajay.bio.util.ValidationUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import picocli.CommandLine;

@CommandLine.Command(name = "IMAGE_PROCESSOR", mixinStandardHelpOptions = true, version = "14-Aug-2022",
        description = "Image processor to highlight and count high contrast points")
@Log4j2
public class ImageProcessorTool implements BaseTool {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec; // injected by picocli

    @CommandLine.Option(names = {"--input-dir"}, required = true,
            description = "Directory with input files")
    private File inputDir;

    @CommandLine.Option(names = {"--output-dir"}, required = true,
            description = "Directory where output files should be saved")
    private File outputDir;

    @Override
    public void execute() throws ToolExecutionException {
        validateInput();
        try {
            executeTool(inputDir.toPath(), outputDir.toPath());
        } catch (IOException e) {
            throw new ToolExecutionException(e);
        }
    }

    private void validateInput() {
        final Path inputDirPath = inputDir.toPath();
        final Path outputDirPath = outputDir.toPath();

        ValidationUtil.validateDir(spec, inputDirPath, outputDirPath);
    }

    private void executeTool(final Path inputDirPath, final Path outputDirPath) throws IOException, ToolExecutionException {
        final File outputCsvFile = new File(outputDirPath.toFile(), "Summary.csv");
        final List<String> outputCsvLines = readPreviousContent(outputCsvFile);

        try {
            for (final File inputFile : FileUtils.listFiles(inputDirPath.toFile(), TrueFileFilter.INSTANCE, null)) {
                final String inputFileName = inputFile.getName();
                if (inputFileName.endsWith(".tif") || inputFileName.endsWith(".TIF") || inputFileName.endsWith(".tiff")
                            || inputFileName.endsWith(".TIFF")) {

                    if (isOutputPresent(inputFile.toPath(), outputDirPath)) {
                        log.info("File already processed, ignoring - {}", inputFileName);
                    } else {
                        log.info("Processing file - {}", inputFileName);
                        try {
                            int count = ImageProcessingStrategyTwo.processTifFile(inputFile.toPath(), outputDirPath);
                            outputCsvLines.add(String.format("%s,%d", inputFileName, count));
                            log.info("Successfully processed file - {}, count - {}", inputFileName, count);
                        } catch (Exception e) {
                            log.error("Failed to process file - {}", inputFileName);
                            throw new ToolExecutionException("Failed to process file", e);
                        }
                    }
                }
            }
        } finally {
            FileUtils.writeLines(outputCsvFile, outputCsvLines);
        }
    }

    private List<String> readPreviousContent(final File outputCsvFile) throws IOException {
        if (Files.exists(outputCsvFile.toPath())) {
            return FileUtils.readLines(outputCsvFile, StandardCharsets.UTF_8);
        }

        return new ArrayList<>();
    }

    private boolean isOutputPresent(final Path inputFile, final Path outputDirPath) {
        final File output = new File(outputDirPath.toFile(), inputFile.toFile().getName() + "-processed.png");
        return Files.exists(output.toPath());
    }
}
