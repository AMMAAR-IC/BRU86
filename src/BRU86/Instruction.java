package BRU86;

import java.util.List;

/**
 * Represents a parsed 8086 assembly instruction.
 */
public class Instruction {
    public final int lineNumber;
    public final String original;   // Original source line
    public final String mnemonic;
    public final List<String> operands;
    public final String label;      // label defined on this line (or null)

    public Instruction(int lineNumber, String original, String label, String mnemonic, List<String> operands) {
        this.lineNumber = lineNumber;
        this.original = original;
        this.label = label;
        this.mnemonic = mnemonic.toUpperCase();
        this.operands = operands;
    }

    public String getOp1() { return operands.size() > 0 ? operands.get(0) : null; }
    public String getOp2() { return operands.size() > 1 ? operands.get(1) : null; }

    @Override
    public String toString() {
        return String.format("[%d] %s%s %s",
            lineNumber,
            label != null ? label + ": " : "",
            mnemonic,
            String.join(", ", operands));
    }
}
