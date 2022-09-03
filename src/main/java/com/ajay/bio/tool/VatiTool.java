package com.ajay.bio.tool;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import com.ajay.bio.tool.impl.ClustalAlignmentTool;
import com.ajay.bio.tool.impl.FileConverterTool;
import com.ajay.bio.tool.impl.IGCategorisationTool;
import com.ajay.bio.tool.impl.IMGTAnalysisTool;
import com.ajay.bio.tool.impl.ImageProcessorTool;
import org.jline.reader.LineReader;
import picocli.CommandLine;

@CommandLine.Command(name = "vati",
        mixinStandardHelpOptions = true,
        version = "14-Aug-2022",
        description = {"Vati Tools - a set of commands for Bio Informatics related data processing",
                       "Hit @|magenta <TAB>|@ to see available commands.",
                       "Hit @|magenta ALT-S|@ to toggle tailtips."},
        footer = {"", "Press Ctl-D to exit."},
        subcommands = {
                FileConverterTool.class,
                ImageProcessorTool.class,
                IMGTAnalysisTool.class,
                IGCategorisationTool.class,
                ClustalAlignmentTool.class
        })
public class VatiTool implements Callable<Integer> {
    private PrintWriter out;

    public void setReader(LineReader reader) {
        out = reader.getTerminal().writer();
    }

    @Override
    public Integer call() {
        out.println(new CommandLine(this).getUsageMessage());
        return 0;
    }
}
