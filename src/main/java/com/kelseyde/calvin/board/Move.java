package com.kelseyde.calvin.board;

import java.util.Arrays;
import java.util.Optional;

/**
 * Represents a single chess move. The move is encoded as a 16-bit integer. Bits 0 - 6 represent the start square,
 * bits 7 - 13 represent the end square, and bits 14 - 31 represent special move flags.
 *
 * @see <a href="https://www.chessprogramming.org/Encoding_Moves">Chess Programming Wiki</a>.
 */
public record Move(int value) {

    // Special move flags
    public static final short NO_FLAG = 0b0000;
    // 特殊移动标记 - 吃过路兵
    public static final short EN_PASSANT_FLAG = 0b0001;
    // 特殊移动标记 - 王车易位
    public static final short CASTLE_FLAG = 0b0010;
    // 特殊移动标记 - 兵前进两格
    public static final short PAWN_DOUBLE_MOVE_FLAG = 0b0011;
    // 特殊移动标记 - 兵升变为皇后
    public static final short PROMOTE_TO_QUEEN_FLAG = 0b0100;
    // 特殊移动标记 - 兵升变为骑士
    public static final short PROMOTE_TO_KNIGHT_FLAG = 0b0101;
    // 特殊移动标记 - 兵升变为车
    public static final short PROMOTE_TO_ROOK_FLAG = 0b0110;
    // 特殊移动标记 - 兵升变为主教
    public static final short PROMOTE_TO_BISHOP_FLAG = 0b0111;

    // 用于提取移动的起始位置的位掩码(0-6位)
    public static final int FROM_MASK = 0b0000000001111111;
    // 用于提取移动的目标位置的位掩码(7-13位)
    public static final int TO_MASK = 0b0011111110000000;

    // 移动标志位定义（14位开始）
    public static final int QUIET_FLAG = 0b0000000000000000;        // 安静移动（无特殊标志）
    public static final int CAPTURE_FLAG = 0b01000000000000000;      // 吃子移动（第14位）

    public Move(int from, int to) {
        this((short) (from | to << 7));
    }

    public Move(int from, int to, int flag) {
        this((short) (from | (to << 7) | (flag << 14)));
    }

    public int from() {
        return value & FROM_MASK;
    }

    public int to() {
        return (value & TO_MASK) >>> 7;
    }

    public int flag() {
        return value >>> 14;
    }

    public Piece promoPiece() {
        return switch (flag()) {
            case PROMOTE_TO_QUEEN_FLAG -> Piece.ADVISOR;
            case PROMOTE_TO_ROOK_FLAG -> Piece.ROOK;
            case PROMOTE_TO_BISHOP_FLAG -> Piece.BISHOP;
            case PROMOTE_TO_KNIGHT_FLAG -> Piece.KNIGHT;
            default -> null;
        };
    }

    public boolean isPromotion() {
        return flag() >= PROMOTE_TO_QUEEN_FLAG;
    }

    public boolean isEnPassant() {
        return flag() == EN_PASSANT_FLAG;
    }

    public boolean isCastling() {
        return flag() == CASTLE_FLAG;
    }

    public boolean isPawnDoubleMove() {
        return flag() == PAWN_DOUBLE_MOVE_FLAG;
    }

    /**
     * Checks if this move matches another move, excluding the special move flag.
     */
    public boolean matches(Move move) {
        if (move == null) return false;
        boolean squareMatch = from() == move.from() && to() == move.to();
        boolean promotionMatch = Optional.ofNullable(promoPiece())
                .map(piece -> piece.equals(move.promoPiece()))
                .orElse(true);
        return squareMatch && promotionMatch;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Move move)) return false;
        return this.from() == move.from() && this.to() == move.to() && this.flag() == move.flag();
    }

    /**
     * Generate a {@link Move} from combined algebraic notation (e.g. "e2e4"), as used in the UCI protocol.
     * Special case promotion: "a2a1q" - values 'q' | 'b' | 'r' | 'n'
     */
    public static Move fromUCI(String uci) {
        int from = Bits.Square.fromNotation(uci.substring(0, 2));
        int to = Bits.Square.fromNotation(uci.substring(2, 4));

        int flag = NO_FLAG;
        if (uci.length() == 5) {
            String pieceCode = uci.substring(4, 5);
            Piece promotionPieceType = Arrays.stream(Piece.values())
                    .filter(entry -> entry.code().equalsIgnoreCase(pieceCode))
                    .findAny().orElseThrow();
            flag = Piece.promoFlag(promotionPieceType);
        }
        return new Move(from, to, flag);
    }

    public static Move fromUCI(String uci, int flag) {
        int from = Bits.Square.fromNotation(uci.substring(0, 2));
        int to = Bits.Square.fromNotation(uci.substring(2, 4));
        return new Move(from, to, flag);
    }

    public static String toUCI(Move move) {
        if (move == null) return "-";
        String notation = Bits.Square.toNotation(move.from()) + Bits.Square.toNotation(move.to());
        return notation;
    }

}
