%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared bb = @(cx16.r0)
    }
}
