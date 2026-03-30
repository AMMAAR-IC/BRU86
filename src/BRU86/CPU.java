package BRU86;

import java.util.*;
import java.io.PrintStream;

/**
 * 8086 CPU Execution Engine
 * Executes parsed instructions with accurate flag behavior.
 */
public class CPU {
    public final Registers regs;
    public final Memory memory;
    private List<Instruction> instructions;
    private Map<String, Integer> labels;      // label -> instruction index
    private Map<String, Integer> constants;   // EQU / data addresses
    private int ip; // instruction pointer (index into instructions list)
    private boolean halted;
    private final Deque<Integer> callStack = new ArrayDeque<>();
    private PrintStream output;
    private final List<String> executionLog = new ArrayList<>();
    private int maxSteps = 100_000; // prevent infinite loops

    public CPU() {
        regs = new Registers();
        memory = new Memory();
        output = System.out;
    }

    public void setOutput(PrintStream out) { this.output = out; }
    public void setMaxSteps(int n) { this.maxSteps = n; }
    public boolean isHalted() { return halted; }
    public List<String> getExecutionLog() { return executionLog; }

    public void load(Parser.ParseResult result) {
        this.instructions = result.instructions;
        this.labels = result.labels;
        this.constants = result.constants;
        // Load data into memory
        for (Parser.DataEntry entry : result.dataEntries) {
            int addr = entry.address;
            for (int val : entry.values) {
                if (entry.isWord) {
                    memory.writeWord(addr, val);
                    addr += 2;
                } else {
                    memory.writeByte(addr, val);
                    addr++;
                }
            }
        }
        ip = 0;
        halted = false;
        callStack.clear();
        executionLog.clear();
    }

    public void reset() {
        regs.reset();
        memory.reset();
        ip = 0;
        halted = false;
        callStack.clear();
        executionLog.clear();
    }

    public void run() {
        int steps = 0;
        while (!halted && ip < instructions.size() && steps < maxSteps) {
            step();
            steps++;
        }
        if (steps >= maxSteps) {
            throw new SimulatorException("Execution limit reached (" + maxSteps + " steps). Possible infinite loop.");
        }
    }

    public void step() {
        if (halted || ip >= instructions.size()) return;
        Instruction instr = instructions.get(ip);
        String log = String.format("[%04d] %s", ip, instr.original);
        executionLog.add(log);
        ip++; // advance before execution (for jumps to work correctly)
        execute(instr);
    }

    private void execute(Instruction instr) {
        String mn = instr.mnemonic;
        switch (mn) {
            case "MOV"  -> execMOV(instr);
            case "XCHG" -> execXCHG(instr);
            case "PUSH" -> execPUSH(instr);
            case "POP"  -> execPOP(instr);
            case "PUSHF"-> push16(regs.getFlags());
            case "POPF" -> regs.setFlags(pop16());
            case "LEA"  -> execLEA(instr);
            case "ADD"  -> execADD(instr);
            case "SUB"  -> execSUB(instr);
            case "ADC"  -> execADC(instr);
            case "SBB"  -> execSBB(instr);
            case "INC"  -> execINC(instr);
            case "DEC"  -> execDEC(instr);
            case "NEG"  -> execNEG(instr);
            case "CMP"  -> execCMP(instr);
            case "MUL"  -> execMUL(instr);
            case "IMUL" -> execIMUL(instr);
            case "DIV"  -> execDIV(instr);
            case "IDIV" -> execIDIV(instr);
            case "AND"  -> execAND(instr);
            case "OR"   -> execOR(instr);
            case "XOR"  -> execXOR(instr);
            case "NOT"  -> execNOT(instr);
            case "TEST" -> execTEST(instr);
            case "SHL", "SAL" -> execSHL(instr);
            case "SHR"  -> execSHR(instr);
            case "SAR"  -> execSAR(instr);
            case "ROL"  -> execROL(instr);
            case "ROR"  -> execROR(instr);
            case "RCL"  -> execRCL(instr);
            case "RCR"  -> execRCR(instr);
            case "JMP"  -> execJMP(instr);
            case "JE", "JZ"   -> { if (regs.ZF) jump(instr.getOp1()); }
            case "JNE", "JNZ" -> { if (!regs.ZF) jump(instr.getOp1()); }
            case "JG", "JNLE" -> { if (!regs.ZF && regs.SF == regs.OF) jump(instr.getOp1()); }
            case "JGE", "JNL" -> { if (regs.SF == regs.OF) jump(instr.getOp1()); }
            case "JL", "JNGE" -> { if (regs.SF != regs.OF) jump(instr.getOp1()); }
            case "JLE", "JNG" -> { if (regs.ZF || regs.SF != regs.OF) jump(instr.getOp1()); }
            case "JA", "JNBE" -> { if (!regs.CF && !regs.ZF) jump(instr.getOp1()); }
            case "JAE", "JNB", "JNC" -> { if (!regs.CF) jump(instr.getOp1()); }
            case "JB", "JC", "JNAE"  -> { if (regs.CF) jump(instr.getOp1()); }
            case "JBE", "JNA" -> { if (regs.CF || regs.ZF) jump(instr.getOp1()); }
            case "JS"   -> { if (regs.SF) jump(instr.getOp1()); }
            case "JNS"  -> { if (!regs.SF) jump(instr.getOp1()); }
            case "JO"   -> { if (regs.OF) jump(instr.getOp1()); }
            case "JNO"  -> { if (!regs.OF) jump(instr.getOp1()); }
            case "JP", "JPE"  -> { if (regs.PF) jump(instr.getOp1()); }
            case "JNP", "JPO" -> { if (!regs.PF) jump(instr.getOp1()); }
            case "JCXZ" -> { if (regs.getCX() == 0) jump(instr.getOp1()); }
            case "LOOP" -> execLOOP(instr, false, false);
            case "LOOPE","LOOPZ"  -> execLOOP(instr, true, true);
            case "LOOPNE","LOOPNZ"-> execLOOP(instr, true, false);
            case "CALL" -> execCALL(instr);
            case "RET"  -> execRET(instr);
            case "INT"  -> execINT(instr);
            case "IRET" -> execIRET();
            case "NOP"  -> {} // no operation
            case "HLT"  -> halted = true;
            case "CLC"  -> regs.CF = false;
            case "STC"  -> regs.CF = true;
            case "CMC"  -> regs.CF = !regs.CF;
            case "CLD"  -> regs.DF = false;
            case "STD"  -> regs.DF = true;
            case "CLI"  -> regs.IF = false;
            case "STI"  -> regs.IF = true;
            case "CBW"  -> execCBW();
            case "CWD"  -> execCWD();
            case "XLAT" -> execXLAT();
            case "DAA"  -> execDAA();
            case "DAS"  -> execDAS();
            case "AAA"  -> execAAA();
            case "AAS"  -> execAAS();
            case "AAM"  -> execAAM();
            case "AAD"  -> execAAD();
            case "FCTRL" -> execFCTRL();
            default -> throw new SimulatorException("Unknown instruction: " + mn + " at line " + instr.lineNumber);
        }
    }

    // ===================== MOV / XCHG / LEA =====================

    private void execMOV(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int value = getOperandValue(src, is8bit);
        setOperandValue(dst, value, is8bit);
    }

    private void execXCHG(Instruction instr) {
        String op1 = instr.getOp1();
        String op2 = instr.getOp2();
        boolean is8bit = isOperand8bit(op1, op2);
        int v1 = getOperandValue(op1, is8bit);
        int v2 = getOperandValue(op2, is8bit);
        setOperandValue(op1, v2, is8bit);
        setOperandValue(op2, v1, is8bit);
    }

    private void execLEA(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        int addr = calcEffectiveAddress(src);
        regs.set(dst, addr);
    }

    // ===================== PUSH / POP =====================

    private void execPUSH(Instruction instr) {
        String op = instr.getOp1();
        int value = getOperandValue(op, false);
        push16(value);
    }

    private void execPOP(Instruction instr) {
        String op = instr.getOp1();
        int value = pop16();
        setOperandValue(op, value, false);
    }

    private void push16(int value) {
        int sp = regs.getSP() - 2;
        regs.setSP(sp);
        memory.writeWord(sp, value & 0xFFFF);
    }

    private int pop16() {
        int sp = regs.getSP();
        int value = memory.readWord(sp);
        regs.setSP(sp + 2);
        return value;
    }

    // ===================== ARITHMETIC =====================

    private void execADD(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int a = getOperandValue(dst, is8bit);
        int b = getOperandValue(src, is8bit);
        int result = a + b;
        setFlagsAdd(a, b, result, is8bit);
        setOperandValue(dst, result, is8bit);
    }

    private void execSUB(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int a = getOperandValue(dst, is8bit);
        int b = getOperandValue(src, is8bit);
        int result = a - b;
        setFlagsSub(a, b, result, is8bit);
        setOperandValue(dst, result, is8bit);
    }

    private void execADC(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int a = getOperandValue(dst, is8bit);
        int b = getOperandValue(src, is8bit) + (regs.CF ? 1 : 0);
        int result = a + b;
        setFlagsAdd(a, b, result, is8bit);
        setOperandValue(dst, result, is8bit);
    }

    private void execSBB(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int a = getOperandValue(dst, is8bit);
        int b = getOperandValue(src, is8bit) + (regs.CF ? 1 : 0);
        int result = a - b;
        setFlagsSub(a, b, result, is8bit);
        setOperandValue(dst, result, is8bit);
    }

    private void execINC(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int a = getOperandValue(dst, is8bit);
        int result = a + 1;
        boolean savedCF = regs.CF;
        setFlagsAdd(a, 1, result, is8bit);
        regs.CF = savedCF; // INC doesn't affect CF
        setOperandValue(dst, result, is8bit);
    }

    private void execDEC(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int a = getOperandValue(dst, is8bit);
        int result = a - 1;
        boolean savedCF = regs.CF;
        setFlagsSub(a, 1, result, is8bit);
        regs.CF = savedCF; // DEC doesn't affect CF
        setOperandValue(dst, result, is8bit);
    }

    private void execNEG(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int a = getOperandValue(dst, is8bit);
        int result = 0 - a;
        regs.CF = (a != 0);
        setFlagsSub(0, a, result, is8bit);
        regs.CF = (a != 0); // CF per spec for NEG
        setOperandValue(dst, result, is8bit);
    }

    private void execCMP(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int a = getOperandValue(dst, is8bit);
        int b = getOperandValue(src, is8bit);
        int result = a - b;
        setFlagsSub(a, b, result, is8bit);
    }

    private void execMUL(Instruction instr) {
        String src = instr.getOp1();
        boolean is8bit = isOperand8bit(src, null);
        int a, b;
        if (is8bit) {
            a = regs.getAL();
            b = getOperandValue(src, true);
            int result = a * b;
            regs.setAX(result);
            regs.CF = regs.OF = ((result & 0xFF00) != 0);
        } else {
            a = regs.getAX();
            b = getOperandValue(src, false);
            long result = (long) a * b;
            regs.setAX((int)(result & 0xFFFF));
            regs.setDX((int)((result >> 16) & 0xFFFF));
            regs.CF = regs.OF = ((result & 0xFFFF0000L) != 0);
        }
        regs.ZF = regs.SF = regs.PF = false; // undefined but often cleared
    }

    private void execIMUL(Instruction instr) {
        String src = instr.getOp1();
        boolean is8bit = isOperand8bit(src, null);
        if (is8bit) {
            int a = signExtend8(regs.getAL());
            int b = signExtend8(getOperandValue(src, true));
            int result = a * b;
            regs.setAX(result & 0xFFFF);
            int hi = (result >> 8) & 0xFF;
            regs.CF = regs.OF = (hi != 0 && hi != 0xFF);
        } else {
            int a = signExtend16(regs.getAX());
            int b = signExtend16(getOperandValue(src, false));
            long result = (long) a * b;
            regs.setAX((int)(result & 0xFFFF));
            regs.setDX((int)((result >> 16) & 0xFFFF));
            regs.CF = regs.OF = (result != signExtend16((int)(result & 0xFFFF)));
        }
    }

    private void execDIV(Instruction instr) {
        String src = instr.getOp1();
        boolean is8bit = isOperand8bit(src, null);
        if (is8bit) {
            int divisor = getOperandValue(src, true);
            if (divisor == 0) throw new SimulatorException("Division by zero (INT 0)");
            int dividend = regs.getAX();
            regs.setAL(dividend / divisor);
            regs.setAH(dividend % divisor);
        } else {
            int divisor = getOperandValue(src, false);
            if (divisor == 0) throw new SimulatorException("Division by zero (INT 0)");
            long dividend = ((long) regs.getDX() << 16) | regs.getAX();
            regs.setAX((int)((dividend / divisor) & 0xFFFF));
            regs.setDX((int)((dividend % divisor) & 0xFFFF));
        }
    }

    private void execIDIV(Instruction instr) {
        String src = instr.getOp1();
        boolean is8bit = isOperand8bit(src, null);
        if (is8bit) {
            int divisor = signExtend8(getOperandValue(src, true));
            if (divisor == 0) throw new SimulatorException("Division by zero");
            int dividend = signExtend16(regs.getAX());
            regs.setAL(dividend / divisor);
            regs.setAH(dividend % divisor);
        } else {
            int divisor = signExtend16(getOperandValue(src, false));
            if (divisor == 0) throw new SimulatorException("Division by zero");
            long dividend = (long)(int)(((long)regs.getDX() << 16) | regs.getAX());
            regs.setAX((int)((dividend / divisor) & 0xFFFF));
            regs.setDX((int)((dividend % divisor) & 0xFFFF));
        }
    }

    // ===================== LOGIC =====================

    private void execAND(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int result = getOperandValue(dst, is8bit) & getOperandValue(src, is8bit);
        setFlagsLogic(result, is8bit);
        setOperandValue(dst, result, is8bit);
    }

    private void execOR(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int result = getOperandValue(dst, is8bit) | getOperandValue(src, is8bit);
        setFlagsLogic(result, is8bit);
        setOperandValue(dst, result, is8bit);
    }

    private void execXOR(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int result = getOperandValue(dst, is8bit) ^ getOperandValue(src, is8bit);
        setFlagsLogic(result, is8bit);
        setOperandValue(dst, result, is8bit);
    }

    private void execNOT(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int result = ~getOperandValue(dst, is8bit);
        setOperandValue(dst, result, is8bit);
        // NOT does not affect flags
    }

    private void execTEST(Instruction instr) {
        String dst = instr.getOp1();
        String src = instr.getOp2();
        boolean is8bit = isOperand8bit(dst, src);
        int result = getOperandValue(dst, is8bit) & getOperandValue(src, is8bit);
        setFlagsLogic(result, is8bit);
    }

    // ===================== SHIFTS / ROTATES =====================

    private void execSHL(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int count = getShiftCount(instr.getOp2());
        int val = getOperandValue(dst, is8bit);
        int mask = is8bit ? 0xFF : 0xFFFF;
        int result = (val << count) & mask;
        if (count > 0) {
            regs.CF = ((val << (count - 1)) & (is8bit ? 0x80 : 0x8000)) != 0;
            if (count == 1) regs.OF = (((val ^ result) & (is8bit ? 0x80 : 0x8000)) != 0);
        }
        setFlagsLogic(result, is8bit);
        regs.CF = count > 0 && ((val >>> (is8bit ? 8 - count : 16 - count)) & 1) != 0;
        setOperandValue(dst, result, is8bit);
    }

    private void execSHR(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int count = getShiftCount(instr.getOp2());
        int val = getOperandValue(dst, is8bit);
        if (count > 0) regs.CF = ((val >>> (count - 1)) & 1) != 0;
        int result = (val >>> count) & (is8bit ? 0xFF : 0xFFFF);
        setFlagsLogic(result, is8bit);
        setOperandValue(dst, result, is8bit);
    }

    private void execSAR(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int count = getShiftCount(instr.getOp2());
        int val = is8bit ? signExtend8(getOperandValue(dst, is8bit)) : signExtend16(getOperandValue(dst, is8bit));
        if (count > 0) regs.CF = ((val >>> (count - 1)) & 1) != 0;
        int result = (val >> count) & (is8bit ? 0xFF : 0xFFFF);
        setFlagsLogic(result, is8bit);
        regs.OF = false; // SAR always clears OF
        setOperandValue(dst, result, is8bit);
    }

    private void execROL(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int count = getShiftCount(instr.getOp2()) % (is8bit ? 8 : 16);
        int val = getOperandValue(dst, is8bit);
        int bits = is8bit ? 8 : 16;
        int result = ((val << count) | (val >>> (bits - count))) & (is8bit ? 0xFF : 0xFFFF);
        regs.CF = (result & 1) != 0;
        setOperandValue(dst, result, is8bit);
    }

    private void execROR(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int count = getShiftCount(instr.getOp2()) % (is8bit ? 8 : 16);
        int val = getOperandValue(dst, is8bit);
        int bits = is8bit ? 8 : 16;
        int result = ((val >>> count) | (val << (bits - count))) & (is8bit ? 0xFF : 0xFFFF);
        regs.CF = ((result >> (bits - 1)) & 1) != 0;
        setOperandValue(dst, result, is8bit);
    }

    private void execRCL(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int count = getShiftCount(instr.getOp2());
        int val = getOperandValue(dst, is8bit);
        int bits = is8bit ? 8 : 16;
        for (int i = 0; i < count; i++) {
            int newCF = (val >>> (bits - 1)) & 1;
            val = ((val << 1) | (regs.CF ? 1 : 0)) & (is8bit ? 0xFF : 0xFFFF);
            regs.CF = newCF != 0;
        }
        setOperandValue(dst, val, is8bit);
    }

    private void execRCR(Instruction instr) {
        String dst = instr.getOp1();
        boolean is8bit = isOperand8bit(dst, null);
        int count = getShiftCount(instr.getOp2());
        int val = getOperandValue(dst, is8bit);
        int bits = is8bit ? 8 : 16;
        for (int i = 0; i < count; i++) {
            int newCF = val & 1;
            val = ((regs.CF ? 1 : 0) << (bits - 1)) | (val >>> 1);
            regs.CF = newCF != 0;
        }
        setOperandValue(dst, val, is8bit);
    }

    private int getShiftCount(String op) {
        if (op == null) return 1;
        if (op.equalsIgnoreCase("CL")) return regs.getCL() & 0x1F;
        if (op.equals("1")) return 1;
        try { return Parser.parseImmediate(op) & 0x1F; } catch (Exception e) { return 1; }
    }

    // ===================== CONTROL FLOW =====================

    private void execJMP(Instruction instr) {
        String target = instr.getOp1();
        jump(target);
    }

    private void jump(String target) {
        if (target == null) throw new SimulatorException("Jump with no target");
        // Check if it's a label
        if (labels.containsKey(target.toUpperCase())) {
            ip = labels.get(target.toUpperCase());
            return;
        }
        // Relative offset
        try {
            int offset = Parser.parseImmediate(target);
            ip = ip + offset; // ip already incremented, this is relative
            if (ip < 0) ip = 0;
            return;
        } catch (Exception ignored) {}
        throw new SimulatorException("Undefined label: " + target);
    }

    private void execLOOP(Instruction instr, boolean checkZF, boolean zfValue) {
        int cx = (regs.getCX() - 1) & 0xFFFF;
        regs.setCX(cx);
        if (cx != 0 && (!checkZF || regs.ZF == zfValue)) {
            jump(instr.getOp1());
        }
    }

    private void execCALL(Instruction instr) {
        push16(ip); // save return address (next instruction)
        callStack.push(ip);
        jump(instr.getOp1());
    }

    private void execRET(Instruction instr) {
        ip = pop16();
        if (!callStack.isEmpty()) callStack.pop();
    }

    private void execINT(Instruction instr) {
        int vector = Parser.parseImmediate(instr.getOp1());
        if (vector == 0x21) handleDosInt21();
        else if (vector == 0x20) halted = true;
        else if (vector == 0x10) handleBiosVideo();
        else throw new SimulatorException("Unhandled interrupt: INT " + String.format("%02Xh", vector));
    }

    private void execIRET() {
        ip = pop16();
        int cs = pop16();
        regs.setFlags(pop16());
    }

    /** DOS INT 21h handler */
    private void handleDosInt21() {
        int ah = regs.getAH();
        switch (ah) {
            case 0x01 -> { // Read char
                try {
                    int c = System.in.read();
                    regs.setAL(c);
                } catch (Exception e) { regs.setAL(0); }
            }
            case 0x02 -> { // Print char
                output.print((char) regs.getDL());
            }
            case 0x06 -> { // Direct console I/O
                int dl = regs.getDL();
                if (dl != 0xFF) output.print((char) dl);
            }
            case 0x09 -> { // Print string ending with '$'
                int addr = regs.getDX();
                StringBuilder sb = new StringBuilder();
                int maxLen = 1000;
                while (maxLen-- > 0) {
                    int c = memory.readByte(addr++);
                    if (c == '$') break;
                    sb.append((char) c);
                }
                output.print(sb);
            }
            case 0x0A -> { // Buffered keyboard input
                // Simplified: just set string length to 0
                int addr = regs.getDX();
                memory.writeByte(addr + 1, 0);
            }
            case 0x0C -> { // Clear keyboard buffer and invoke function
                handleDosInt21(); // re-invoke with AL = new function
            }
            case 0x4C -> { // Terminate with exit code
                halted = true;
            }
            default -> throw new SimulatorException("Unhandled DOS INT 21h AH=" + String.format("%02X", ah));
        }
    }

    /** BIOS INT 10h - video */
    private void handleBiosVideo() {
        int ah = regs.getAH();
        if (ah == 0x0E) { // Teletype output
            output.print((char) regs.getAL());
        }
    }

    // ===================== MISC =====================

    private void execCBW() {
        regs.setAX(signExtend8(regs.getAL()) & 0xFFFF);
    }

    private void execCWD() {
        int val = signExtend16(regs.getAX());
        regs.setDX(val < 0 ? 0xFFFF : 0x0000);
    }

    private void execXLAT() {
        int addr = (regs.getBX() + regs.getAL()) & 0xFFFF;
        regs.setAL(memory.readByte(addr));
    }

    private void execDAA() {
        int al = regs.getAL();
        int oldAL = al;
        boolean oldCF = regs.CF;
        if ((al & 0x0F) > 9 || regs.AF) {
            al = (al + 6) & 0xFF;
            regs.AF = true;
        } else regs.AF = false;
        if (oldAL > 0x99 || oldCF) {
            al = (al + 0x60) & 0xFF;
            regs.CF = true;
        } else regs.CF = false;
        regs.setAL(al);
        setZSP(al, true);
    }

    private void execDAS() {
        int al = regs.getAL();
        int oldAL = al;
        boolean oldCF = regs.CF;
        if ((al & 0x0F) > 9 || regs.AF) {
            al = (al - 6) & 0xFF;
            regs.AF = true;
        } else regs.AF = false;
        if (oldAL > 0x99 || oldCF) {
            al = (al - 0x60) & 0xFF;
            regs.CF = true;
        } else regs.CF = false;
        regs.setAL(al);
        setZSP(al, true);
    }

    private void execAAA() {
        if ((regs.getAL() & 0x0F) > 9 || regs.AF) {
            regs.setAL((regs.getAL() + 6) & 0xFF);
            regs.setAH((regs.getAH() + 1) & 0xFF);
            regs.AF = regs.CF = true;
        } else regs.AF = regs.CF = false;
        regs.setAL(regs.getAL() & 0x0F);
    }

    private void execAAS() {
        if ((regs.getAL() & 0x0F) > 9 || regs.AF) {
            regs.setAL((regs.getAL() - 6) & 0xFF);
            regs.setAH((regs.getAH() - 1) & 0xFF);
            regs.AF = regs.CF = true;
        } else regs.AF = regs.CF = false;
        regs.setAL(regs.getAL() & 0x0F);
    }

    private void execAAM() {
        int al = regs.getAL();
        regs.setAH(al / 10);
        regs.setAL(al % 10);
        setZSP(regs.getAL(), true);
    }

    private void execAAD() {
        int al = regs.getAH() * 10 + regs.getAL();
        regs.setAL(al & 0xFF);
        regs.setAH(0);
        setZSP(regs.getAL(), true);
    }

    private void execFCTRL(){

    }

    // ===================== FLAG HELPERS =====================

    private void setFlagsAdd(int a, int b, int result, boolean is8bit) {
        int mask = is8bit ? 0xFF : 0xFFFF;
        int signBit = is8bit ? 0x80 : 0x8000;
        int masked = result & mask;
        regs.CF = is8bit ? (result > 0xFF || result < 0) : (result > 0xFFFF || result < 0);
        setZSP(masked, is8bit);
        regs.AF = ((a ^ b ^ result) & 0x10) != 0;
        regs.OF = ((~(a ^ b) & (a ^ result)) & signBit) != 0;
    }

    private void setFlagsSub(int a, int b, int result, boolean is8bit) {
        int mask = is8bit ? 0xFF : 0xFFFF;
        int signBit = is8bit ? 0x80 : 0x8000;
        int masked = result & mask;
        regs.CF = (b > a); // unsigned borrow
        setZSP(masked, is8bit);
        regs.AF = ((a ^ b ^ result) & 0x10) != 0;
        regs.OF = (((a ^ b) & (a ^ result)) & signBit) != 0;
    }

    private void setFlagsLogic(int result, boolean is8bit) {
        int mask = is8bit ? 0xFF : 0xFFFF;
        regs.CF = false;
        regs.OF = false;
        setZSP(result & mask, is8bit);
    }

    private void setZSP(int result, boolean is8bit) {
        int mask = is8bit ? 0xFF : 0xFFFF;
        int signBit = is8bit ? 0x80 : 0x8000;
        int masked = result & mask;
        regs.ZF = (masked == 0);
        regs.SF = (masked & signBit) != 0;
        regs.PF = calcParity(masked & 0xFF);
    }

    private boolean calcParity(int val) {
        int count = Integer.bitCount(val & 0xFF);
        return (count % 2) == 0;
    }

    // ===================== OPERAND RESOLUTION =====================

    private boolean isOperand8bit(String op1, String op2) {
        // Check op1 for 8-bit registers
        if (op1 != null) {
            if (regs.is8bit(op1)) return true;
            // Memory access with BYTE PTR hint
            if (op1.toUpperCase().contains("BYTE")) return true;
        }
        if (op2 != null) {
            if (regs.is8bit(op2)) return true;
            if (op2.toUpperCase().contains("BYTE")) return true;
        }
        return false;
    }

    private int getOperandValue(String op, boolean is8bit) {
        if (op == null) throw new SimulatorException("Null operand");
        String upper = op.toUpperCase().trim();

        // Strip BYTE PTR / WORD PTR
        if (upper.startsWith("BYTE PTR ")) return getOperandValue(op.substring(9).trim(), true);
        if (upper.startsWith("WORD PTR ")) return getOperandValue(op.substring(9).trim(), false);
        if (upper.startsWith("BYTE ")) return getOperandValue(op.substring(5).trim(), true);
        if (upper.startsWith("WORD ")) return getOperandValue(op.substring(5).trim(), false);

        // Memory access [...]
        if (upper.startsWith("[")) {
            int addr = calcEffectiveAddress(op);
            return is8bit ? memory.readByte(addr) : memory.readWord(addr);
        }

        // Register
        try { return regs.get(op); } catch (SimulatorException ignored) {}

        // Constant / label
        if (constants.containsKey(upper)) return constants.get(upper);

        // Immediate value
        try { return Parser.parseImmediate(op); } catch (Exception ignored) {}

        throw new SimulatorException("Cannot resolve operand: " + op);
    }

    private void setOperandValue(String op, int value, boolean is8bit) {
        if (op == null) throw new SimulatorException("Null destination");
        String upper = op.toUpperCase().trim();

        // Strip PTR hints
        if (upper.startsWith("BYTE PTR ")) { setOperandValue(op.substring(9).trim(), value, true); return; }
        if (upper.startsWith("WORD PTR ")) { setOperandValue(op.substring(9).trim(), value, false); return; }
        if (upper.startsWith("BYTE ")) { setOperandValue(op.substring(5).trim(), value, true); return; }
        if (upper.startsWith("WORD ")) { setOperandValue(op.substring(5).trim(), value, false); return; }

        // Memory access
        if (upper.startsWith("[")) {
            int addr = calcEffectiveAddress(op);
            if (is8bit) memory.writeByte(addr, value);
            else memory.writeWord(addr, value);
            return;
        }

        // Register
        try { regs.set(op, value); return; } catch (SimulatorException ignored) {}

        throw new SimulatorException("Cannot set operand: " + op);
    }

    /**
     * Calculates effective address for [expression].
     * Supports: [reg], [reg+reg], [reg+offset], [reg+reg*scale+offset], labels
     */
    private int calcEffectiveAddress(String op) {
        op = op.trim();
        if (op.startsWith("[") && op.endsWith("]")) {
            op = op.substring(1, op.length() - 1).trim();
        }

        int addr = 0;
        // Tokenize: split on + and -
        // Simple approach: evaluate expression
        addr = evalAddrExpr(op);
        return addr & 0xFFFF;
    }

    private int evalAddrExpr(String expr) {
        // Replace minus with +-
        expr = expr.replaceAll("\\s*-\\s*", "+-");
        String[] parts = expr.split("\\+");
        int result = 0;
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            boolean negative = false;
            if (part.startsWith("-")) {
                negative = true;
                part = part.substring(1).trim();
            }
            int val;
            // Check for register
            try { val = regs.get(part); }
            catch (SimulatorException e) {
                // Check constant/label
                if (constants.containsKey(part.toUpperCase())) val = constants.get(part.toUpperCase());
                else {
                    try { val = Parser.parseImmediate(part); }
                    catch (Exception ex) { throw new SimulatorException("Cannot evaluate address expression: " + part); }
                }
            }
            result += negative ? -val : val;
        }
        return result;
    }

    // ===================== SIGN EXTENSION =====================

    private int signExtend8(int val) {
        val = val & 0xFF;
        return (val & 0x80) != 0 ? val | 0xFFFFFF00 : val;
    }

    private int signExtend16(int val) {
        val = val & 0xFFFF;
        return (val & 0x8000) != 0 ? val | 0xFFFF0000 : val;
    }

    public int getIp() { return ip; }
}
