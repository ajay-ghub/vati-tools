package com.ajay.bio.tool.impl;

import java.util.concurrent.Callable;

import com.ajay.bio.exception.ToolExecutionException;
import picocli.CommandLine;

public interface BaseTool extends Callable<Integer> {
    void execute() throws ToolExecutionException;

    @Override
    default Integer call() {
        try {
            execute();
        } catch (CommandLine.ParameterException e) {
            throw e;
        } catch (Exception e) {
            return 1;
        }

        return 0;
    }
}
