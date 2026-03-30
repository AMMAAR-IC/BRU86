package BRU86;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 8086 Assembly Simulator
 * Interactive REPL and file execution mode
 */
public class Simulator {
    private final CPU cpu = new CPU();
    private final Parser parser = new Parser();
    private String currentSource = null;

    public static void main(String[] args) throws Exception {
        Simulator sim = new Simulator();

        if (args.length > 0) {
            // File mode
            String filename = args[0];
            String source = Files.readString(Path.of(filename));
            System.out.println("=== 8086 Simulator - Running: " + filename + " ===\n");
            sim.runSource(source, true);
        } else {
            // Interactive mode
            sim.repl();
        }
    }

    public void runSource(String source, boolean verbose) {
        try {
            Parser parser = new Parser();
            Parser.ParseResult result = parser.parse(source);
            cpu.reset();
            cpu.load(result);

            if (verbose) {
                System.out.println("Parsed " + result.instructions.size() + " instructions");
                System.out.println("Labels: " + result.labels);
                System.out.println("Constants: " + result.constants);
                System.out.println("--- Output ---");
            }

            cpu.run();

            if (verbose) {
                System.out.println("\n--- Final Register State ---");
                System.out.println(cpu.regs);
            }
        } catch (SimulatorException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void repl() throws IOException {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║          8086 Assembly Simulator  v1.0                 ║");
        System.out.println("║       Type 'help' for commands, 'exit' to quit         ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder codeBuffer = new StringBuilder();
        boolean multilineMode = false;

        while (true) {
            System.out.print(multilineMode ? "asm> " : "> ");
            String line = reader.readLine();
            if (line == null)
                break;
            line = line.trim();

            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye.");
                break;
            }

            if (line.equalsIgnoreCase("help")) {
                printHelp();
                continue;
            }

            if (line.equalsIgnoreCase("regs") || line.equalsIgnoreCase("r")) {
                System.out.println(cpu.regs);
                continue;
            }

            if (line.toLowerCase().startsWith("mem ")) {
                handleMem(line.substring(4).trim());
                continue;
            }

            if (line.equalsIgnoreCase("stack")) {
                handleStack();
                continue;
            }

            if (line.equalsIgnoreCase("reset")) {
                cpu.reset();
                codeBuffer.setLength(0);
                System.out.println("CPU reset.");
                continue;
            }

            if (line.equalsIgnoreCase("log")) {
                cpu.getExecutionLog().forEach(System.out::println);
                continue;
            }

            if (line.toLowerCase().startsWith("load ")) {
                handleLoad(line.substring(5).trim());
                continue;
            }

            if (line.equalsIgnoreCase("begin") || line.equalsIgnoreCase("{")) {
                multilineMode = true;
                codeBuffer.setLength(0);
                System.out.println("Enter assembly code. Type 'run' or 'end' to execute.");
                continue;
            }

            if (multilineMode) {
                if (line.equalsIgnoreCase("run") || line.equalsIgnoreCase("end") || line.equalsIgnoreCase("}")) {
                    multilineMode = false;
                    String src = codeBuffer.toString();
                    currentSource = src;
                    System.out.println("--- Running ---");
                    runSource(src, false);
                    System.out.println("\nRegisters:");
                    System.out.println(cpu.regs);
                    codeBuffer.setLength(0);
                } else {
                    codeBuffer.append(line).append("\n");
                }
                continue;
            }

            // Single instruction execution
            if (!line.isEmpty()) {
                try {
                    Parser parser = new Parser();
                    Parser.ParseResult result = parser.parse(line);
                    if (!result.instructions.isEmpty()) {
                        cpu.load(result);
                        cpu.step();
                        // Don't print regs for single instructions unless verbose
                    }
                } catch (SimulatorException e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }
        }
    }

    private void handleMem(String args) {
        String[] parts = args.split("\\s+");
        try {
            int addr = Parser.parseImmediate(parts[0]);
            int len = parts.length > 1 ? Parser.parseImmediate(parts[1]) : 64;
            System.out.print(cpu.memory.dump(addr, len));
        } catch (Exception e) {
            System.err.println("Usage: mem <address> [length]   e.g. mem 0200 64");
        }
    }

    private void handleStack() {
        int sp = cpu.regs.getSP();
        System.out.printf("SP = %04X%n", sp);
        System.out.println("Stack contents (top to bottom):");
        for (int i = 0; i < 16; i++) {
            int addr = (sp + i * 2) & 0xFFFF;
            if (addr > 0xFFFE)
                break;
            System.out.printf("  [%04X] = %04X%n", addr, cpu.memory.readWord(addr));
        }
    }

    private void handleLoad(String filename) {
        try {
            String source = Files.readString(Path.of(filename));
            System.out.println("Loaded: " + filename);
            runSource(source, true);
        } catch (IOException e) {
            System.err.println("Cannot load file: " + filename);
        }
    }

    private void printHelp() {
        System.out.println("""
                Commands:
                  begin / {          Start multi-line assembly input
                  end / run / }      Execute the entered code
                  regs / r           Show all registers and flags
                  mem <addr> [len]   Dump memory (hex address)
                  stack              Show stack contents
                  log                Show execution log
                  load <file>        Load and run a .asm file
                  reset              Reset CPU to initial state
                  exit / quit        Exit the simulator

                Single instruction mode:
                  Just type one instruction and press Enter
                  e.g.: MOV AX, 42

                Address formats: 0FFh, 0xFF, 255
                Number formats: 42, 0FFh, 0xFF, 1010b, 'A'

                Supported instructions:
                  Data transfer:  MOV, XCHG, PUSH, POP, PUSHF, POPF, LEA
                  Arithmetic:     ADD, SUB, ADC, SBB, INC, DEC, NEG, CMP
                                  MUL, IMUL, DIV, IDIV
                                  DAA, DAS, AAA, AAS, AAM, AAD, CBW, CWD
                  Logic:          AND, OR, XOR, NOT, TEST
                  Shifts:         SHL/SAL, SHR, SAR, ROL, ROR, RCL, RCR
                  Control:        JMP, Jcc, LOOP, LOOPE, LOOPNE, CALL, RET
                                  INT (21h, 10h), IRET, HLT, NOP
                  Flags:          CLC, STC, CMC, CLD, STD, CLI, STI
                """);
    }
}
