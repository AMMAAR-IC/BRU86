.DATA
    msg DB "Hello, World!", 13, 10, '$'

.CODE

    MOV AH, 09h      ; DOS function: Print string
    LEA DX, msg      ; Load address of msg (resolved to 0200h)
    INT 21h          ; Call DOS interrupt

    MOV AH, 4Ch      ; DOS function: Terminate program
    INT 21h