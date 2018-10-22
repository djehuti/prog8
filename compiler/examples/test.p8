; %import c64utils
%option enable_floats
%output raw
%launcher none

~ main  {

    str str1 = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789abcde"
    str_p str2 = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789abcde"
    str_s str3 = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789abcde"
    str_ps str4 = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789abcde"

    byte[256] barr2 = 2   ; @todo weird init error
    byte[256] barr3 = 3   ; @todo weird init error
    byte[256] barr2b  ; @todo weird init error
    byte[256] barr3b  ; @todo weird init error

    ubyte[256] ubarr2 = 2  ; @todo weird init error
    ubyte[256] ubarr3 = 3  ; @todo weird init error
    ubyte[256] ubarr2b     ; @todo weird init error
    ubyte[256] ubarr3b     ; @todo weird init error


    word[128] warr2 = 2   ; @todo weird init error
    word[128] warr3 = 3   ; @todo weird init error
    word[128] warr2b  ; @todo weird init error
    word[128] warr3b  ; @todo weird init error

    uword[128] wbarr2 = 2  ; @todo weird init error
    uword[128] wbarr3 = 3  ; @todo weird init error
    uword[128] wbarr2b     ; @todo weird init error
    uword[128] wbarr3b     ; @todo weird init error


    byte[256,257] bmatrix      ; @todo weird init error
    ubyte[256,257] ubmatrix    ; @todo weird init error


    ; @todo later: allow for arrays/matrixes with length > 256 (uword index)


sub start() {
    return
}

}
