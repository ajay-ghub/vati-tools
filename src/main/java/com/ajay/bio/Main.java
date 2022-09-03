package com.ajay.bio;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.ajay.bio.tool.VatiTool;
import com.google.common.collect.Lists;
import org.fusesource.jansi.AnsiConsole;
import org.jline.builtins.Options;
import org.jline.console.CmdDesc;
import org.jline.console.CmdLine;
import org.jline.console.CommandRegistry;
import org.jline.console.impl.Builtins;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.SystemCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.widget.TailTipWidgets;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;

public class Main {
    /**
     * Provide command descriptions for JLine TailTipWidgets
     * to be displayed in the status bar.
     */
    private static class DescriptionGenerator {
        Builtins builtins;
        PicocliCommands picocli;

        public DescriptionGenerator(Builtins builtins, PicocliCommands picocli) {
            this.builtins = builtins;
            this.picocli = picocli;
        }

        private CmdDesc commandDescription(CmdLine line) {
            CmdDesc out = null;
            switch (line.getDescriptionType()) {
                case COMMAND:
                    String cmd = new DefaultParser().getCommand(line.getArgs().get(0));
                    if (builtins.hasCommand(cmd)) {
                        out = builtins.commandDescription(Lists.newArrayList(cmd));
                    } else if (picocli.hasCommand(cmd)) {
                        out = picocli.commandDescription(cmd);
                    }
                    break;
                default:
                    break;
            }
            return out;
        }
    }

    public static void main(String[] args) {
        AnsiConsole.systemInstall();
        try {
            // set up JLine built-in commands
            Path workDir = Paths.get("");
            Builtins builtins = new Builtins(workDir, null, null);
            builtins.rename(Builtins.Command.TTOP, "top");
            builtins.alias("zle", "widget");
            builtins.alias("bindkey", "keymap");
            SystemCompleter systemCompleter = builtins.compileCompleters();
            // set up picocli commands
            VatiTool commands = new VatiTool();
            CommandLine cmd = new CommandLine(commands);
            PicocliCommands picocliCommands = new PicocliCommands(cmd);
            systemCompleter.add(picocliCommands.compileCompleters());
            systemCompleter.compile();
            Terminal terminal = TerminalBuilder.builder().build();
            CommandRegistry.CommandSession commandSession = new CommandRegistry.CommandSession(terminal);
            LineReader reader = LineReaderBuilder.builder()
                                                 .terminal(terminal)
                                                 .completer(systemCompleter)
                                                 .parser(new DefaultParser())
                                                 .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                                                 .build();
            builtins.setLineReader(reader);
            commands.setReader(reader);
            DescriptionGenerator descriptionGenerator = new DescriptionGenerator(builtins, picocliCommands);
            new TailTipWidgets(reader, descriptionGenerator::commandDescription, 5, TailTipWidgets.TipType.COMPLETER);

            String prompt = "vati> ";
            String rightPrompt = null;

            // start the shell and process input until the user quits with Ctl-D
            String line;
            while (true) {
                try {
                    line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
                    if (line.matches("^\\s*#.*")) {
                        continue;
                    }
                    ParsedLine pl = reader.getParser().parse(line, 0);
                    String[] arguments = pl.words().toArray(new String[0]);
                    String command = new DefaultParser().getCommand(pl.word());
                    if (builtins.hasCommand(command)) {
                        builtins.invoke(commandSession, command, Arrays.copyOfRange(arguments, 1, arguments.length));
                    } else {
                        new CommandLine(commands).execute(arguments);
                    }
                } catch (Options.HelpException e) {
                    Options.HelpException.highlight(e.getMessage(), Options.HelpException.defaultStyle()).print(terminal);
                } catch (UserInterruptException e) {
                    // Ignore
                } catch (EndOfFileException e) {
                    return;
                } catch (Exception e) {
                    AttributedStringBuilder asb = new AttributedStringBuilder();
                    asb.append(e.getMessage(), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
                    asb.toAttributedString().println(terminal);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
