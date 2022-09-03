package com.ajay.bio.util;

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
}
