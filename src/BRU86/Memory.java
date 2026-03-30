package BRU86;

/**
 * 8086 Memory - 64KB flat address space (segment * 16 + offset)
 * Also supports full 1MB with segmented addressing
 */
public class Memory {
    private static final int SIZE = 0x10000; // 64KB per segment (we use flat 64KB for simplicity)
    private final byte[] mem;

    public Memory() {
        mem = new byte[SIZE];
    }

    public void reset() {
        java.util.Arrays.fill(mem, (byte) 0);
    }

    // Physical address from segment:offset
    public static int physAddr(int segment, int offset) {
        return ((segment << 4) + offset) & 0xFFFF;
    }

    public int readByte(int address) {
        return mem[address & 0xFFFF] & 0xFF;
    }

    public int readWord(int address) {
        int lo = mem[address & 0xFFFF] & 0xFF;
        int hi = mem[(address + 1) & 0xFFFF] & 0xFF;
        return (hi << 8) | lo; // Little-endian
    }

    public void writeByte(int address, int value) {
        mem[address & 0xFFFF] = (byte) (value & 0xFF);
    }

    public void writeWord(int address, int value) {
        mem[address & 0xFFFF] = (byte) (value & 0xFF);
        mem[(address + 1) & 0xFFFF] = (byte) ((value >> 8) & 0xFF);
    }

    /** Load bytes into memory starting at address */
    public void load(int startAddress, byte[] data) {
        for (int i = 0; i < data.length; i++) {
            mem[(startAddress + i) & 0xFFFF] = data[i];
        }
    }

    /** Dump memory region as hex */
    public String dump(int start, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i += 16) {
            sb.append(String.format("%04X: ", (start + i) & 0xFFFF));
            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < 16 && i + j < length; j++) {
                int b = mem[(start + i + j) & 0xFFFF] & 0xFF;
                sb.append(String.format("%02X ", b));
                ascii.append(b >= 32 && b < 127 ? (char) b : '.');
            }
            // Pad if last row
            int rem = 16 - Math.min(16, length - i);
            for (int j = 0; j < rem; j++) sb.append("   ");
            sb.append(" ").append(ascii).append("\n");
        }
        return sb.toString();
    }

    public byte[] getRawMemory() { return mem; }
}
