%import textio
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    %option align_word
    %option force_output

    sub start() {

        ; a "pixelshader":
        void syscall1(8, 0)      ; enable lo res creen
        ubyte shifter

        shifter >>= 1

        repeat {
            uword xx
            uword yy = 0
            repeat 240 {
                xx = 0
                repeat 320 {
                    syscall3(10, xx, yy, xx*yy + shifter)   ; plot pixel
                    xx++
                }
                yy++
            }
            shifter+=4
        }
    }
}
