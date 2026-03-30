; =============================================
; Fibonacci - compute first 10 Fibonacci numbers
; Result stored in memory starting at 0300h
; =============================================

.CODE
    MOV CX, 10       ; count = 10
    MOV AX, 0        ; fib(0) = 0
    MOV BX, 1        ; fib(1) = 1
    MOV DI, 0300h    ; output address

LOOP_START:
    MOV [DI], AX     ; store current fib
    ADD DI, 2        ; advance pointer
    MOV DX, BX       ; temp = BX
    ADD BX, AX       ; BX = AX + BX
    MOV AX, DX       ; AX = old BX
    DEC CX
    JNZ LOOP_START   ; loop if CX != 0

    HLT
