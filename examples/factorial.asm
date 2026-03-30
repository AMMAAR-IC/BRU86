
.CODE
    MOV AX, 1      ; Result = 1
    MOV CX, 5      ; Number = 5

FACT_LOOP:
    MUL CX         ; AX = AX * CX
    DEC CX         ; CX = CX - 1
    JNZ FACT_LOOP  ; Loop until CX = 0

    HLT