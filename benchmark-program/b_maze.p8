%import textio
%import math

; Even though prog8 only has support for extremely limited recursion,
; you can write recursive algorithms with a bit of extra work by building your own explicit stack structure.
; This program shows a depth-first maze generation algorithm (1 possible path from start to finish),
; and a depth-first maze solver algorithm, both using a stack to store the path taken.

; Note: this program can be compiled for multiple target systems.

maze {
    uword score

    sub bench(uword max_time) -> uword {
        txt.nl()
        score=0
        math.rndseed(2345,44332)
        cbm.SETTIM(0,0,0)
        while cbm.RDTIM16()<max_time {
            maze.initialize()
            maze.drawStartFinish()
            if maze.generate(max_time) {
                maze.openpassages()
                maze.drawStartFinish()
                if maze.solve(max_time) {
                    maze.drawStartFinish()
                } else break
            } else break
        }

        return score
    }

    const uword screenwidth = 40
    const uword screenheight = 30

    const ubyte numCellsHoriz = (screenwidth-1) / 2
    const ubyte numCellsVert = (screenheight-1) / 2

    ; maze start and finish cells
    const ubyte startCx = 0
    const ubyte startCy = 0
    const ubyte finishCx = numCellsHoriz-1
    const ubyte finishCy = numCellsVert-1

    ; cell properties
    const ubyte STONE = 128
    const ubyte WALKED = 64
    const ubyte BACKTRACKED = 32
    const ubyte UP = 1
    const ubyte RIGHT = 2
    const ubyte DOWN = 4
    const ubyte LEFT = 8
    const ubyte WALLCOLOR = 12
    const ubyte EMPTYCOLOR = 0

    ; unfortunately on larger screens (cx16), the number of cells exceeds 256 and doesn't fit in a regular array anymore.
    uword cells = memory("cells", numCellsHoriz*numCellsVert, 0)

    ubyte[256] cx_stack
    ubyte[256] cy_stack
    ubyte stackptr

    ubyte[4] directionflags = [LEFT,RIGHT,UP,DOWN]

    sub generate(uword max_time) -> bool {
        ubyte cx = startCx
        ubyte cy = startCy

        stackptr = 0
        @(celladdr(cx,cy)) &= ~STONE
        drawCell(cx, cy)
        uword cells_to_carve = numCellsHoriz * numCellsVert - 1

        while cbm.RDTIM16()<max_time {
carve_restart_after_repath:
            ubyte direction = choose_uncarved_direction()
            if direction==0 {
                ;backtrack
                stackptr--
                if stackptr==255 {
                    ; stack empty.
                    ; repath if we are not done yet. (this is a workaround for the prog8 256 array lenght limit)
                    if cells_to_carve!=0 {
                        if repath()
                            goto carve_restart_after_repath
                    }
                    return true
                }
                cx = cx_stack[stackptr]
                cy = cy_stack[stackptr]
            } else {
                cx_stack[stackptr] = cx
                cy_stack[stackptr] = cy
                stackptr++
                if stackptr==0 {
                    ; stack overflow, we can't track our path any longer.
                    ; repath if we are not done yet. (this is a workaround for the prog8 256 array lenght limit)
                    if cells_to_carve!=0 {
                        if repath()
                            goto carve_restart_after_repath
                    }
                    return true
                }
                @(celladdr(cx,cy)) |= direction
                when direction {
                    UP -> {
                        cy--
                        @(celladdr(cx,cy)) |= DOWN
                    }
                    RIGHT -> {
                        cx++
                        @(celladdr(cx,cy)) |= LEFT

                        score++
                    }
                    DOWN -> {
                        cy++
                        @(celladdr(cx,cy)) |= UP
                    }
                    LEFT -> {
                        cx--
                        @(celladdr(cx,cy)) |= RIGHT
                    }
                }
                @(celladdr(cx,cy)) &= ~STONE
                cells_to_carve--
                drawCell(cx, cy)
            }
        }
        return false

        sub repath() -> bool {
            ; repath: try to find a new start cell with possible directions.
            ; we limit our number of searches so that the algorith doesn't get stuck
            ; for too long on bad rng... just accept a few unused cells in that case.
            repeat 255 {
                do {
                    cx = math.rnd() % numCellsHoriz
                    cy = math.rnd() % numCellsVert
                } until @(celladdr(cx, cy)) & STONE ==0
                if available_uncarved()!=0
                    return true
            }
            return false
        }

        sub available_uncarved() -> ubyte {
            ubyte candidates = 0
            if cx>0 and @(celladdr(cx-1, cy)) & STONE !=0
                candidates |= LEFT
            if cx<numCellsHoriz-1 and @(celladdr(cx+1, cy)) & STONE !=0
                candidates |= RIGHT
            if cy>0 and @(celladdr(cx, cy-1)) & STONE !=0
                candidates |= UP
            if cy<numCellsVert-1 and @(celladdr(cx, cy+1)) & STONE !=0
                candidates |= DOWN
            return candidates
        }

        sub choose_uncarved_direction() -> ubyte {
            ubyte candidates =  available_uncarved()
            if candidates==0
                return 0

            repeat {
                ubyte choice = candidates & directionflags[math.rnd() & 3]
                if choice!=0
                    return choice
            }
        }
    }

    sub openpassages() {
        ; open just a few extra passages, so that multiple routes are possible in theory.
        ubyte numpassages
        ubyte cx
        ubyte cy
        do {
            do {
                cx = math.rnd() % (numCellsHoriz-2) + 1
                cy = math.rnd() % (numCellsVert-2) + 1
            } until @(celladdr(cx, cy)) & STONE ==0
            ubyte direction = directionflags[math.rnd() & 3]
            if @(celladdr(cx, cy)) & direction == 0 {
                when direction {
                    LEFT -> {
                        if @(celladdr(cx-1,cy)) & STONE == 0 {
                            @(celladdr(cx,cy)) |= LEFT
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    RIGHT -> {
                        if @(celladdr(cx+1,cy)) & STONE == 0 {
                            @(celladdr(cx,cy)) |= RIGHT
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    UP -> {
                        if @(celladdr(cx,cy-1)) & STONE == 0 {
                            @(celladdr(cx,cy)) |= UP
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    DOWN -> {
                        if @(celladdr(cx,cy+1)) & STONE == 0 {
                            @(celladdr(cx,cy)) |= DOWN
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                }
            }
        } until numpassages==10
    }

    sub solve(uword max_time) -> bool {
        ubyte cx = startCx
        ubyte cy = startCy
        const uword max_path_length = 1024

        ; the path through the maze can be longer than 256 so doesn't fit in a regular array.... :(
        uword pathstack = memory("pathstack", max_path_length, 0)
        uword pathstackptr = 0

        @(celladdr(cx,cy)) |= WALKED
        ; txt.setcc(cx*2+1, cy*2+1, 81, 1)

        while cbm.RDTIM16()<max_time {
solve_loop:
            if cx==finishCx and cy==finishCy {
                ;txt.home()
                txt.print("found! path length: ")
                txt.print_uw(pathstackptr)
                txt.nl()
                return true
            }

            ubyte cell = @(celladdr(cx,cy))
            if cell & UP!=0 and @(celladdr(cx,cy-1)) & (WALKED|BACKTRACKED) ==0 {
                @(pathstack + pathstackptr) = UP
                ;txt.setcc(cx*2+1, cy*2, 81, 3)
                cy--
            }
            else if cell & DOWN !=0 and @(celladdr(cx,cy+1)) & (WALKED|BACKTRACKED) ==0 {
                @(pathstack + pathstackptr) = DOWN
                ;txt.setcc(cx*2+1, cy*2+2, 81, 3)
                cy++
            }
            else if cell & LEFT !=0 and @(celladdr(cx-1,cy)) & (WALKED|BACKTRACKED) ==0 {
                @(pathstack + pathstackptr) = LEFT
                ;txt.setcc(cx*2, cy*2+1, 81, 3)
                cx--
            }
            else if cell & RIGHT !=0 and @(celladdr(cx+1,cy)) & (WALKED|BACKTRACKED) ==0 {
                @(pathstack + pathstackptr) = RIGHT
                ;txt.setcc(cx*2+2, cy*2+1, 81, 3)
                cx++
            }
            else {
                ; dead end, pop stack
                pathstackptr--
                if pathstackptr==65535 {
                    txt.print("no solution?!\n")
                    return true
                }
                @(celladdr(cx,cy)) |= BACKTRACKED
                ;txt.setcc(cx*2+1, cy*2+1, 81, 2)
                when @(pathstack + pathstackptr) {
                    UP -> {
                        ;txt.setcc(cx*2+1, cy*2+2, 81, 9)
                        cy++
                    }
                    DOWN -> {
                        ;txt.setcc(cx*2+1, cy*2, 81, 9)
                        cy--
                    }
                    LEFT -> {
                        ;txt.setcc(cx*2+2, cy*2+1, 81, 9)
                        cx++
                    }
                    RIGHT -> {
                        ;txt.setcc(cx*2, cy*2+1, 81, 9)
                        cx--

                        score++
                    }
                }
                goto solve_loop
            }
            pathstackptr++
            if pathstackptr==max_path_length {
                txt.print("stack overflow, path too long\n")
                return true
            }
            @(celladdr(cx,cy)) |= WALKED
            ;txt.setcc(cx*2+1, cy*2+1, 81, 1)
        }
        return false
    }

    sub celladdr(ubyte cx, ubyte cy) -> uword {
        return cells+(numCellsHoriz as uword)*cy+cx
    }

    sub drawCell(ubyte cx, ubyte cy) {
        return
;        ubyte x = cx * 2 + 1
;        ubyte y = cy * 2 + 1
;        ubyte doors = @(celladdr(cx,cy))
;        if doors & UP !=0
;            txt.setcc(x, y-1, ' ', EMPTYCOLOR)
;        if doors & RIGHT !=0
;            txt.setcc(x+1, y, ' ', EMPTYCOLOR)
;        if doors & DOWN !=0
;            txt.setcc(x, y+1, ' ', EMPTYCOLOR)
;        if doors & LEFT !=0
;            txt.setcc(x-1, y, ' ', EMPTYCOLOR)
;        if doors & STONE !=0
;            txt.setcc(x, y, 160, WALLCOLOR)
;        else
;            txt.setcc(x, y, 32, EMPTYCOLOR)
;
;        if doors & WALKED !=0
;            txt.setcc(x, y, 81, 1)
;        if doors & BACKTRACKED !=0
;            txt.setcc(x, y, 81, 2)
    }

    sub initialize() {
        sys.memset(cells, numCellsHoriz*numCellsVert, STONE)
        ; txt.fill_screen(160, WALLCOLOR)
        drawStartFinish()
    }

    sub drawStartFinish() {
        ; txt.setcc(startCx*2+1,startCy*2+1,sc:'s',5)
        ; txt.setcc(finishCx*2+1, finishCy*2+1, sc:'f', 13)
    }
}
