package com.kelseyde.calvin.board;

import com.kelseyde.calvin.board.Bits.Square;

import java.util.Arrays;
import java.util.Random;

/**
 * Utility class for generating Zobrist keys, which are 64-bit values that (almost uniquely) represent a chess position.
 * It is used to quickly identify positions that have already been examined by the move generator/evaluator, cutting
 * down on a lot of double-work.
 *
 * @see <a href="https://www.chessprogramming.org/Zobrist_Hashing">Chess Programming Wiki</a>
 */
// TODO: try only generating key once for each update
public class Key {

    private static final int CASTLING_RIGHTS_COUNT = 16;
    private static final int EN_PASSANT_FILES_COUNT = 9;

    private static final long[][][][] PIECE_SQUARE_HASH = new long[Square.COUNT][2][Piece.COUNT][2];
    private static final long[] SIDE_TO_MOVE = new long[2];
    private static final int WHITE = 0;
    private static final int BLACK = 1;

    static {

        Random random = new Random(18061995);

        // Generate random Zobrist keys for each piece on each square
        for (int square = 0; square < Square.COUNT; square++) {
            for (int pieceIndex : Arrays.stream(Piece.values()).map(Piece::index).toList()) {
                PIECE_SQUARE_HASH[square][WHITE][pieceIndex][0] = random.nextLong();
                PIECE_SQUARE_HASH[square][WHITE][pieceIndex][1] = random.nextLong();
                PIECE_SQUARE_HASH[square][BLACK][pieceIndex][0] = random.nextLong();
                PIECE_SQUARE_HASH[square][BLACK][pieceIndex][1] = random.nextLong();
            }
        }

        // Generate random key for side to move
        SIDE_TO_MOVE[0] = random.nextLong();
        SIDE_TO_MOVE[1] = random.nextLong();
    }

    public static long generateKey(Board board) {
        long key = 0L;

        // Define arrays for each piece type and color
        long[][][] pieces = {
                { board.getPawns(true), board.getPawns(false) },
                { board.getKnights(true), board.getKnights(false) },
                { board.getBishops(true), board.getBishops(false) },
                { board.getRooks(true), board.getRooks(false) },
                { board.getAdvisors(true), board.getAdvisors(false) },
                { board.getCannons(true), board.getCannons(false) },
                { board.getKing(true), board.getKing(false) }
        };

        // Loop through each square and piece type
        for (int square = 0; square < Square.COUNT; square++) {
            for (int pieceType = 0; pieceType < Piece.COUNT; pieceType++) {
                key = updateKeyForPiece(key, pieces[pieceType][WHITE], pieces[pieceType][BLACK], square, pieceType);
            }
        }

        if (board.isWhite()) {
            key ^= SIDE_TO_MOVE[0];
            key ^= SIDE_TO_MOVE[1];
        }

        return key;
    }

    public static long generatePawnKey(Board board) {
        long key = 0L;

        // Get the bitboards for white and black pawns
        long[] whitePawns = board.getPawns(true);
        long[] blackPawns = board.getPawns(false);

        // Loop through each square
        if (!(Bits.isEmpty(whitePawns)  && Bits.isEmpty(blackPawns))) {  // Early exit optimization if no pawns
            for (int square = 0; square < Square.COUNT; square++) {
                key = updateKeyForPiece(key, whitePawns, blackPawns, square, Piece.PAWN.index());
            }
        }

        return key;
    }

    private static long updateKeyForPiece(long key, long[] whiteBitboard, long[] blackBitboard, int square, int pieceIndex) {
        if (((whiteBitboard[0] >>> square) & 1) == 1) {
            key ^= PIECE_SQUARE_HASH[square][WHITE][pieceIndex][0];
        } else if (((whiteBitboard[1] >>> square) & 1) == 1) {
            key ^= PIECE_SQUARE_HASH[square][WHITE][pieceIndex][1];
        } else if (((blackBitboard[0] >>> square) & 1) == 1) {
            key ^= PIECE_SQUARE_HASH[square][BLACK][pieceIndex][0];
        } else if (((blackBitboard[1] >>> square) & 1) == 1) {
            key ^= PIECE_SQUARE_HASH[square][BLACK][pieceIndex][1];
        }
        return key;
    }

    public static long[] piece(int from, int to, Piece pieceType, boolean white) {
        return Bits.xor(PIECE_SQUARE_HASH[from][Colour.index(white)][pieceType.index()],
                PIECE_SQUARE_HASH[to][Colour.index(white)][pieceType.index()]);
    }

    public static long[] piece(int square, Piece pieceType, boolean white) {
        return PIECE_SQUARE_HASH[square][Colour.index(white)][pieceType.index()];
    }

    public static long[] sideToMove() {
        return SIDE_TO_MOVE;
    }


}