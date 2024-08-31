package com.ajay.bio.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import com.ajay.bio.exception.ValidationException;
import picocli.CommandLine;

public final class ValidationUtil {
    private ValidationUtil() {
        // hide constructor
    }

    public static void validateDir(final CommandLine.Model.CommandSpec spec, final Path... dirPaths) {
        for (final Path dirPath : dirPaths) {
            if (Files.notExists(dirPath)) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                                                         "Specified directory does not exist - " + dirPath);
            }

            if (!Files.isDirectory(dirPath)) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Specified directory path is not a " +
                                                                                     "directory - " + dirPath);
            }
        }
    }

    public static void validateFile(final CommandLine.Model.CommandSpec spec, final Path... filePaths) {
        for (final Path filePath : filePaths) {
            if (Files.notExists(filePath)) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                                                         "Specified file does not exist - " + filePath);
            }

            if (Files.isDirectory(filePath)) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                                                         "Specified file path is a directory - " + filePath);
            }
        }
    }

    public static void validateFile(final CommandLine.Model.CommandSpec spec, final File file) {
            if (file == null || Files.notExists(file.toPath())) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Specified file does not exist - " + file);
            }

            if (Files.isDirectory(file.toPath())) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Specified file path is a directory - " + file);
            }

    }

    public static void validateRange(final CommandLine.Model.CommandSpec spec, final int value,
                                     final int startRangeInclusive, final int endRangeInclusive) {
        if (value < startRangeInclusive || value > endRangeInclusive) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    String.format(
                            "Invalid value - %s, value should be in range [%s, %s]",
                            value, startRangeInclusive, endRangeInclusive
                    )
            );
        }
    }
}
