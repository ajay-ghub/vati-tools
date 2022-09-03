package com.ajay.bio.tool.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.ajay.bio.enums.FileType;
import com.ajay.bio.exception.ToolExecutionException;
import com.ajay.bio.exception.ValidationException;
import com.ajay.bio.util.ValidationUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.AmbiguityDNACompoundSet;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.io.ABITrace;
import org.biojava.nbio.core.sequence.io.FastaWriterHelper;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import picocli.CommandLine;

@CommandLine.Command(name = "FILE_CONVERTER", mixinStandardHelpOptions = true, version = "14-Aug-2022",
        description = "File format converter")
@Log4j2
public class FileConverterTool implements BaseTool {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec; // injected by picocli

    @CommandLine.Option(names = {"--input-dir"}, required = true,
            description = "Directory with input files")
    private File inputDir;

    @CommandLine.Option(names = {"--output-dir"}, required = true,
            description = "Directory where output files should be saved")
    private File outputDir;

    @CommandLine.Option(names = {"--create-single-file"},
            description = "Create a single (merged) output file")
    private boolean createSingleFile;

    @CommandLine.Option(names = {"--input-file-type"}, required = true,
            description = "Type to convert from")
    private FileType inputFileType;

    @CommandLine.Option(names = {"--output-file-type"}, required = true,
            description = "Type to convert to")
    private FileType outputFileType;

    @Override
    public void execute() throws ToolExecutionException {
        validateInput();

        final Map<String, String> convertedSeqMap;
        try {
            convertedSeqMap = convertSequence(inputDir.toPath(),
                                              inputFileType.getTypeString(),
                                              outputFileType.getTypeString());
        } catch (IOException e) {
            throw new ToolExecutionException("Failed to execute tool", e);
        }

        writeOutput(outputDir.toPath(), convertedSeqMap, createSingleFile, outputFileType.getTypeString());
    }

    private void validateInput() {
        final Path inputDirPath = inputDir.toPath();
        final Path outputDirPath = outputDir.toPath();

        ValidationUtil.validateDir(spec, inputDirPath, outputDirPath);
    }

    private static void writeOutput(final Path outputDirPath, final Map<String, String> convertedSeqMap,
                                    final boolean createSingleFile, final String outputFileType) throws ToolExecutionException {

        final File outputDir = outputDirPath.toFile();

        if (createSingleFile) {
            final File outputFile = new File(outputDir, "singleMergedFile" + outputFileType);
            try {
                FileUtils.writeLines(outputFile, convertedSeqMap.values());
            } catch (IOException e) {
                log.error("Failed to create merged file, please retry");
                throw new ToolExecutionException("Failed to create file", e);
            }

            log.info("Successfully created single output file");
        } else {
            for (Map.Entry<String, String> entry : convertedSeqMap.entrySet()) {
                String outputFileName = entry.getKey();
                String sequence = entry.getValue();
                final File outputFile = new File(outputDir, outputFileName);
                try {
                    FileUtils.writeStringToFile(outputFile, sequence, StandardCharsets.UTF_8.name());
                } catch (IOException e) {
                    log.error("Failed to create output file - {}", outputFileName);
                    throw new ToolExecutionException("Failed to create file", e);
                }
            }

            log.info("Successfully created all output files");
        }
    }

    /**
     * Returns Map of outputFileName to convertedSeq
     */
    private static Map<String, String> convertSequence(final Path inputDirPath, final String inputFileType,
                                                       final String outputFileType) throws IOException, ToolExecutionException {

        final Map<String, String> convertedSeqMap = new HashMap<>();

        int totalCount = 0;
        for (final File inputFile : FileUtils.listFiles(inputDirPath.toFile(), TrueFileFilter.INSTANCE, null)) {
            final String inputFileName = inputFile.getName();
            final String inputFileNameWithoutExtension = inputFileName.replace(inputFileType, "");
            if (inputFileName.endsWith(inputFileType)) {
                log.info("Converting input file - {}", inputFileName);

                final String outputFileName = inputFileName.replace(inputFileType, outputFileType);

                try {
                    ABITrace abiTrace = new ABITrace(inputFile);
                    final AbstractSequence<NucleotideCompound> abstractSeq = abiTrace.getSequence();
                    final AmbiguityDNACompoundSet ambiguityDNACompoundSet = AmbiguityDNACompoundSet.getDNACompoundSet();
                    final DNASequence dnaSequence = new DNASequence(abstractSeq.getSequenceAsString(), ambiguityDNACompoundSet);
                    dnaSequence.setOriginalHeader(inputFileNameWithoutExtension);

                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    FastaWriterHelper.writeNucleotideSequence(baos, Collections.singleton(dnaSequence));

                    convertedSeqMap.put(outputFileName, baos.toString("UTF-8"));

                    log.info("Successfully converted input file - {}", inputFileName);
                } catch (Throwable e) {
                    log.error("Failed to convert input file - {}, reason - {}", inputFile.getName(), e.getMessage());
                } finally {
                    ++totalCount;
                }
            }
        }

        log.info("Successfully converted {}/{} files", convertedSeqMap.size(), totalCount);
        return convertedSeqMap;
    }
}
