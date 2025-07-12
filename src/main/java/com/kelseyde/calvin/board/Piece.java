package com.kelseyde.calvin.board;

import static com.kelseyde.calvin.board.Move.*;

/**
 * Stores basic information for each chess piece type.
 */
public enum Piece {

    //兵
    PAWN    (0, "p"),
    //马
    KNIGHT  (1, "n"),
    //相
    BISHOP  (2, "b"),
    //车
    ROOK    (3, "r"),
    //士
    ADVISOR   (4, "a"),
    //将
    KING    (5, "k"),
    //炮
    CANNON    (6, "c");

    public static final int COUNT = 7;
    public static final int WHITE_PIECES = 7;
    public static final int BLACK_PIECES = 8;

    final int index;

    final String code;

    Piece(int index, String code) {
        this.index = index;
        this.code = code;
    }

    public int index() {
        return index;
    }

    public String code() {
        return code;
    }

    public boolean isSlider() {
        return this == BISHOP || this == ROOK || this == ADVISOR;
    }

    public static short promoFlag(Piece piece) {
        if (piece == null) {
            return NO_FLAG;
        }
        return switch (piece) {
            case ADVISOR -> PROMOTE_TO_QUEEN_FLAG;
            case ROOK -> PROMOTE_TO_ROOK_FLAG;
            case BISHOP -> PROMOTE_TO_BISHOP_FLAG;
            case KNIGHT -> PROMOTE_TO_KNIGHT_FLAG;
            default -> NO_FLAG;
        };
    }

}
