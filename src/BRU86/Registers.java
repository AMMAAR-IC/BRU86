package BRU86;

/**
 * 8086 CPU Registers - exact 1:1 representation
 * 16-bit general registers with 8-bit high/low access
 */
public class Registers {
    // General purpose registers (stored as int, masked to 16-bit)
    private int AX, BX, CX, DX;
    // Pointer and index registers
    private int SP, BP, SI, DI;
    // Segment registers
    private int CS, DS, SS, ES;
    // Instruction pointer
    private int IP;

    // Flags register bits
    public boolean CF;  // Carry Flag
    public boolean PF;  // Parity Flag
    public boolean AF;  // Auxiliary Carry Flag
    public boolean ZF;  // Zero Flag
    public boolean SF;  // Sign Flag
    public boolean TF;  // Trap Flag
    public boolean IF;  // Interrupt Enable Flag
    public boolean DF;  // Direction Flag
    public boolean OF;  // Overflow Flag

    public Registers() {
        reset();
    }

    public void reset() {
        AX = BX = CX = DX = 0;
        SP = 0xFFFE; // Stack starts at top of segment
        BP = SI = DI = 0;
        CS = 0; DS = 0; SS = 0; ES = 0;
        IP = 0;
        CF = PF = AF = ZF = SF = TF = IF = DF = OF = false;
    }

    // ---- 16-bit getters ----
    public int getAX() { return AX & 0xFFFF; }
    public int getBX() { return BX & 0xFFFF; }
    public int getCX() { return CX & 0xFFFF; }
    public int getDX() { return DX & 0xFFFF; }
    public int getSP() { return SP & 0xFFFF; }
    public int getBP() { return BP & 0xFFFF; }
    public int getSI() { return SI & 0xFFFF; }
    public int getDI() { return DI & 0xFFFF; }
    public int getCS() { return CS & 0xFFFF; }
    public int getDS() { return DS & 0xFFFF; }
    public int getSS() { return SS & 0xFFFF; }
    public int getES() { return ES & 0xFFFF; }
    public int getIP() { return IP & 0xFFFF; }

    // ---- 16-bit setters ----
    public void setAX(int v) { AX = v & 0xFFFF; }
    public void setBX(int v) { BX = v & 0xFFFF; }
    public void setCX(int v) { CX = v & 0xFFFF; }
    public void setDX(int v) { DX = v & 0xFFFF; }
    public void setSP(int v) { SP = v & 0xFFFF; }
    public void setBP(int v) { BP = v & 0xFFFF; }
    public void setSI(int v) { SI = v & 0xFFFF; }
    public void setDI(int v) { DI = v & 0xFFFF; }
    public void setCS(int v) { CS = v & 0xFFFF; }
    public void setDS(int v) { DS = v & 0xFFFF; }
    public void setSS(int v) { SS = v & 0xFFFF; }
    public void setES(int v) { ES = v & 0xFFFF; }
    public void setIP(int v) { IP = v & 0xFFFF; }

    // ---- 8-bit high byte getters/setters ----
    public int getAH() { return (AX >> 8) & 0xFF; }
    public int getBH() { return (BX >> 8) & 0xFF; }
    public int getCH() { return (CX >> 8) & 0xFF; }
    public int getDH() { return (DX >> 8) & 0xFF; }

    public void setAH(int v) { AX = (AX & 0x00FF) | ((v & 0xFF) << 8); }
    public void setBH(int v) { BX = (BX & 0x00FF) | ((v & 0xFF) << 8); }
    public void setCH(int v) { CX = (CX & 0x00FF) | ((v & 0xFF) << 8); }
    public void setDH(int v) { DX = (DX & 0x00FF) | ((v & 0xFF) << 8); }

    // ---- 8-bit low byte getters/setters ----
    public int getAL() { return AX & 0xFF; }
    public int getBL() { return BX & 0xFF; }
    public int getCL() { return CX & 0xFF; }
    public int getDL() { return DX & 0xFF; }

    public void setAL(int v) { AX = (AX & 0xFF00) | (v & 0xFF); }
    public void setBL(int v) { BX = (BX & 0xFF00) | (v & 0xFF); }
    public void setCL(int v) { CX = (CX & 0xFF00) | (v & 0xFF); }
    public void setDL(int v) { DX = (DX & 0xFF00) | (v & 0xFF); }

    /** Generic get by register name */
    public int get(String reg) {
        return switch (reg.toUpperCase()) {
            case "AX" -> getAX(); case "BX" -> getBX();
            case "CX" -> getCX(); case "DX" -> getDX();
            case "SP" -> getSP(); case "BP" -> getBP();
            case "SI" -> getSI(); case "DI" -> getDI();
            case "CS" -> getCS(); case "DS" -> getDS();
            case "SS" -> getSS(); case "ES" -> getES();
            case "IP" -> getIP();
            case "AH" -> getAH(); case "AL" -> getAL();
            case "BH" -> getBH(); case "BL" -> getBL();
            case "CH" -> getCH(); case "CL" -> getCL();
            case "DH" -> getDH(); case "DL" -> getDL();
            default -> throw new SimulatorException("Unknown register: " + reg);
        };
    }

    /** Generic set by register name */
    public void set(String reg, int value) {
        switch (reg.toUpperCase()) {
            case "AX" -> setAX(value); case "BX" -> setBX(value);
            case "CX" -> setCX(value); case "DX" -> setDX(value);
            case "SP" -> setSP(value); case "BP" -> setBP(value);
            case "SI" -> setSI(value); case "DI" -> setDI(value);
            case "CS" -> setCS(value); case "DS" -> setDS(value);
            case "SS" -> setSS(value); case "ES" -> setES(value);
            case "IP" -> setIP(value);
            case "AH" -> setAH(value); case "AL" -> setAL(value);
            case "BH" -> setBH(value); case "BL" -> setBL(value);
            case "CH" -> setCH(value); case "CL" -> setCL(value);
            case "DH" -> setDH(value); case "DL" -> setDL(value);
            default -> throw new SimulatorException("Unknown register: " + reg);
        }
    }

    public boolean is8bit(String reg) {
        return reg.length() == 2 && "AH AL BH BL CH CL DH DL".contains(reg.toUpperCase());
    }

    public boolean is16bit(String reg) {
        return !is8bit(reg);
    }

    /** Get flags register as 16-bit word */
    public int getFlags() {
        int flags = 0;
        if (CF) flags |= 0x0001;
        if (PF) flags |= 0x0004;
        if (AF) flags |= 0x0010;
        if (ZF) flags |= 0x0040;
        if (SF) flags |= 0x0080;
        if (TF) flags |= 0x0100;
        if (IF) flags |= 0x0200;
        if (DF) flags |= 0x0400;
        if (OF) flags |= 0x0800;
        return flags | 0xF002; // reserved bits always 1
    }

    public void setFlags(int flags) {
        CF = (flags & 0x0001) != 0;
        PF = (flags & 0x0004) != 0;
        AF = (flags & 0x0010) != 0;
        ZF = (flags & 0x0040) != 0;
        SF = (flags & 0x0080) != 0;
        TF = (flags & 0x0100) != 0;
        IF = (flags & 0x0200) != 0;
        DF = (flags & 0x0400) != 0;
        OF = (flags & 0x0800) != 0;
    }

    @Override
    public String toString() {
        return String.format(
            "AX=%04X  BX=%04X  CX=%04X  DX=%04X\n" +
            "SP=%04X  BP=%04X  SI=%04X  DI=%04X\n" +
            "CS=%04X  DS=%04X  SS=%04X  ES=%04X  IP=%04X\n" +
            "Flags: CF=%b ZF=%b SF=%b OF=%b PF=%b AF=%b DF=%b IF=%b",
            getAX(), getBX(), getCX(), getDX(),
            getSP(), getBP(), getSI(), getDI(),
            getCS(), getDS(), getSS(), getES(), getIP(),
            CF, ZF, SF, OF, PF, AF, DF, IF
        );
    }
}
