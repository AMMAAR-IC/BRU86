# 8086 Assembly Simulator in Java

A 1:1 simulation of the Intel 8086 processor written in Java.

## Project Structure

```
BRU86/
├── src/BRU86/
│   ├── Simulator.java      ← Main entry point + interactive REPL
│   ├── CPU.java            ← Execution engine (all instructions)
│   ├── Registers.java      ← All 8086 registers (AX/AH/AL etc.) + flags
│   ├── Memory.java         ← 64KB memory model
│   ├── Parser.java         ← Assembly source parser
│   ├── Instruction.java    ← Instruction data model
│   └── SimulatorException  ← Custom exception
├── examples/
│   ├── hello.asm           ← Hello World (INT 21h)
│   ├── fibonacci.asm       ← Fibonacci sequence
│   ├── factorial.asm       ← Factorial (5! = 120)
│   └── subroutine.asm      ← CALL/RET demo
├── run.sh                  ← Build + run script
└── README.md
```

## Building & Running

```bash
# Build + interactive REPL
bash run.sh

# Build + run a file
bash run.sh examples/factorial.asm
```

Or manually:
```bash
mkdir -p out
javac -d out $(find src -name "*.java")
java -cp out BRU86.Simulator
java -cp out BRU86.Simulator examples/factorial.asm
```

## Interactive REPL Commands

| Command           | Description                           |
|-------------------|---------------------------------------|
| `begin` or `{`    | Start multi-line code input           |
| `end` or `run`    | Execute entered code                  |
| `regs` / `r`      | Show all registers and flags          |
| `mem 0200 64`     | Dump memory at address 0200h, 64 bytes|
| `stack`           | Show stack contents                   |
| `log`             | Show execution log                    |
| `load <file>`     | Load and run a .asm file              |
| `reset`           | Reset CPU to initial state            |
| `exit`            | Quit                                  |

## Supported Instructions

### Data Transfer
`MOV`, `XCHG`, `PUSH`, `POP`, `PUSHF`, `POPF`, `LEA`

### Arithmetic
`ADD`, `SUB`, `ADC`, `SBB`, `INC`, `DEC`, `NEG`, `CMP`  
`MUL`, `IMUL`, `DIV`, `IDIV`  
`DAA`, `DAS`, `AAA`, `AAS`, `AAM`, `AAD`, `CBW`, `CWD`

### Logic
`AND`, `OR`, `XOR`, `NOT`, `TEST`

### Shifts & Rotates
`SHL`/`SAL`, `SHR`, `SAR`, `ROL`, `ROR`, `RCL`, `RCR`

### Control Flow
`JMP`, all conditional jumps (`JE`, `JNE`, `JG`, `JGE`, `JL`, `JLE`, `JA`, `JAE`, `JB`, `JBE`, `JS`, `JNS`, `JO`, `JNO`, `JP`, `JNP`, `JCXZ`)  
`LOOP`, `LOOPE`/`LOOPZ`, `LOOPNE`/`LOOPNZ`  
`CALL`, `RET`, `INT`, `IRET`, `HLT`, `NOP`

### Flag Operations
`CLC`, `STC`, `CMC`, `CLD`, `STD`, `CLI`, `STI`

### Interrupts
- `INT 21h` — DOS services (AH=01,02,06,09,0Ah,4Ch)
- `INT 10h` — BIOS video (AH=0Eh teletype output)

## Registers Simulated

**General Purpose (16/8-bit):**  
AX (AH/AL), BX (BH/BL), CX (CH/CL), DX (DH/DL)

**Index/Pointer:**  
SP, BP, SI, DI, IP

**Segment:**  
CS, DS, SS, ES

**Flags:**  
CF, PF, AF, ZF, SF, TF, IF, DF, OF

## Example Session

```
8086> begin
asm> MOV AX, 1
asm> MOV CX, 5
asm> FACT_LOOP:
asm> MUL CX
asm> DEC CX
asm> JNZ FACT_LOOP
asm> HLT
asm> run
--- Running ---

Registers:
AX=0078  BX=0000  CX=0000  DX=0000
...
```
AX = 0078h = 120 = 5! ✓

## Source Format

```asm
; Comments use semicolons
.DATA
    msg DB "Hello", '$'    ; define bytes
    arr DW 5 DUP(0)        ; 5 words initialized to 0

.CODE
LABEL:
    MOV AX, 42             ; immediate
    MOV BX, AX             ; register to register
    MOV [0300h], AX        ; register to memory
    MOV AX, [BX+SI]        ; indexed addressing
    INT 21h                ; interrupt
    HLT
```

## Address Formats
- Decimal: `255`
- Hex: `0FFh` or `0xFF`  
- Binary: `1010b`
- Character: `'A'`
