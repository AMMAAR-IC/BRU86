.CODE
    MOV AX, 10       ; AX = 10
    MOV BX, 25       ; BX = 25
    CALL ADD_NUMS    ; call subroutine
    HLT

ADD_NUMS:            ; subroutine: AX = AX + BX
    ADD AX, BX
    RET
