package com.kelseyde.calvin.board;

import java.util.Arrays;

import com.kelseyde.calvin.board.Bits.Square;
import com.kelseyde.calvin.search.Search;
import com.kelseyde.calvin.uci.UCI;
import com.kelseyde.calvin.utils.notation.FEN;

/**
 * Represents the current state of the chess board, including the positions of the pieces, the side to move, en passant
 * rights, fifty-move counter, and the move counter. Includes functions to 'make' and 'unmake' moves on the board, which
 * are fundamental to both the search and evaluation algorithms. Uses bitboards to represent the pieces and 'toggling'
 * functions to set and unset pieces.
 *
 * @see <a href="https://www.chessprogramming.org/Board_Representation">Chess Programming Wiki</a>
 */
public class Board {

    private long[][] bitboards;
    private Piece[] pieces;
    private BoardState state;
    private BoardState[] states;
    private Move[] moves;
    private boolean white;
    private int ply;

    public Board() {
        this.bitboards   = new long[Piece.COUNT + 2][2];
        this.pieces      = new Piece[Square.COUNT];
        this.moves       = new Move[Search.MAX_DEPTH];
        this.states      = new BoardState[Search.MAX_DEPTH];
        this.state       = new BoardState();
        this.white       = true;
        this.ply         = 0;
    }

    // Updates the internal board representation with the move just made. Toggles the piece bitboards to
    // move the piece and remove the captured piece, plus special rules for pawn double-moves, castling,
    // promotion and en passant.
    public boolean makeMove(Move move) {

        int from = move.from();
        int to = move.to();
        Piece piece = pieces[from];
        if (piece == null) return false;
        Piece captured = move.isEnPassant() ? Piece.PAWN : pieces[to];
        states[ply] = state.copy();

        if (move.isPawnDoubleMove())  makePawnDoubleMove(from, to);
        else if (move.isCastling())   makeCastleMove(from, to);
        else if (move.isPromotion())  makePromotionMove(from, to, move.promoPiece(), captured);
        else if (move.isEnPassant())  makeEnPassantMove(from, to);
        else                          makeStandardMove(from, to, piece, captured);

        updateState(from, to, piece, captured, move);
        moves[ply++] = move;
        checkMaxPly();
        white = !white;

        return true;

    }

    // Reverts the internal board representation to the state before the last move. Toggles the piece bitboards
    // to move the piece and reinstate the captured piece, plus special rules for pawn double-moves, castling,
    // promotion and en passant.
    public void unmakeMove() {

        white = !white;
        Move move = moves[--ply];
        int from = move.from();
        int to = move.to();
        Piece piece = pieceAt(to);

        if (move.isCastling())        unmakeCastlingMove(from, to);
        else if (move.isPromotion())  unmakePromotionMove(from, to, move.promoPiece());
        else if (move.isEnPassant())  unmakeEnPassantMove(from, to);
        else                          unmakeStandardMove(from, to, piece);

        state = states[ply];

    }

    private void makePawnDoubleMove(int from, int to) {

        updateBitboards(from, to, Piece.PAWN, white);
        updateMailbox(from, to, Piece.PAWN);
        updateKeys(from, to, Piece.PAWN, white);

    }

    private void makeCastleMove(int from, int to) {

        if (UCI.Options.chess960)
            makeChess960CastleMove(from, to);
        else
            makeStandardCastleMove(from, to);

    }

    private void makeStandardCastleMove(int from, int to) {

        boolean kingside = Castling.isKingside(from, to);

        // Handle moving king
        updateBitboards(from, to, Piece.KING, white);
        updateMailbox(from, to, Piece.KING);

        // Handle moving rook
        int rookFrom = Castling.rookFrom(kingside, white);
        int rookTo = Castling.rookTo(kingside, white);
        updateBitboards(rookFrom, rookTo, Piece.ROOK, white);
        updateMailbox(rookFrom, rookTo, Piece.ROOK);

        updateKeys(from, to, Piece.KING, white);
        updateKeys(rookFrom, rookTo, Piece.ROOK, white);

    }

    private void makeChess960CastleMove(int from, int to) {

        boolean kingside = Castling.isKingside(from, to);

        // Unset king
        updateBitboard(from, Piece.KING, white);
        updateMailbox(from, null);

        // Unset rook
        // (in Chess960 the 'to' square of a castling move is the rook square)
        int rookTo = Castling.rookTo(kingside, white);
        updateBitboard(to, Piece.ROOK, white);
        updateMailbox(to, null);

        int kingTo = Castling.kingTo(kingside, white);

        // Set king
        updateBitboard(kingTo, Piece.KING, white);
        updateMailbox(kingTo, Piece.KING);

        // Set rook
        updateBitboard(rookTo, Piece.ROOK, white);
        updateMailbox(rookTo, Piece.ROOK);

        updateKeys(from, kingTo, Piece.KING, white);
        updateKeys(to, rookTo, Piece.ROOK, white);

    }

    private void makeEnPassantMove(int from, int to) {

        // Handle capturing pawn
        updateBitboards(from, to, Piece.PAWN, white);
        updateMailbox(from, to, Piece.PAWN);
        updateKeys(from, to, Piece.PAWN, white);
        // Handle captured pawn
        int pawnSquare = white ? to - 8 : to + 8;
        updateBitboard(pawnSquare, Piece.PAWN, !white);
        updateMailbox(pawnSquare, null);
        updateKeys(pawnSquare, Piece.PAWN, !white);

    }

    private void makePromotionMove(int from, int to, Piece promoted, Piece captured) {

        // Remove promoting pawn
        updateBitboard(from, Piece.PAWN, white);
        updateKeys(from, Piece.PAWN, white);
        // Add promoted piece
        updateBitboard(to, promoted, white);
        updateMailbox(from, to, promoted);
        updateKeys(to, promoted, white);
        if (captured != null) {
            // Handle captured piece
            updateBitboard(to, captured, !white);
            updateKeys(to, captured, !white);
        }

    }

    private void makeStandardMove(int from, int to, Piece piece, Piece captured) {

        // Handle moving piece
        updateBitboards(from, to, piece, white);
        updateKeys(from, to, piece, white);
        updateMailbox(from, to, piece);
        if (captured != null) {
            // Remove captured piece
            updateBitboard(to, captured, !white);
            updateKeys(to, captured, !white);
        }

    }

    private void updateState(int from, int to, Piece piece, Piece captured, Move move) {
        state.moved = piece;
        state.captured = captured;
        boolean resetClock = captured != null || Piece.PAWN.equals(piece);
        state.halfMoveClock = resetClock ? 0 : ++state.halfMoveClock;
        state.rights = 0;
        state.enPassantFile = 0;
        state.key ^= Key.sideToMove()[0];
        state.key ^= Key.sideToMove()[1];
    }

    private void unmakeCastlingMove(int from, int to) {

        if (UCI.Options.chess960)
            unmakeChess960CastleMove(from, to);
        else
            unmakeStandardCastleMove(from, to);

    }

    private void unmakeStandardCastleMove(int from, int to) {

        // Put back king
        updateBitboards(to, from, Piece.KING, white);
        updateMailbox(to, from, Piece.KING);
        // Put back rook
        boolean kingside = Castling.isKingside(from, to);
        int rookFrom = Castling.rookFrom(kingside, white);
        int rookTo = Castling.rookTo(kingside, white);
        updateBitboards(rookTo, rookFrom, Piece.ROOK, white);
        updateMailbox(rookTo, rookFrom, Piece.ROOK);

    }

    private void unmakeChess960CastleMove(int from, int to) {

        boolean kingside = Castling.isKingside(from, to);
        int kingTo = Castling.kingTo(kingside, white);
        // Unset king
        updateBitboard(kingTo, Piece.KING, white);
        updateMailbox(kingTo, null);
        // Unset rook
        int rookTo = Castling.rookTo(kingside, white);
        updateBitboard(rookTo, Piece.ROOK, white);
        updateMailbox(rookTo, null);
        // Set king
        updateBitboard(from, Piece.KING, white);
        updateMailbox(from, Piece.KING);
        // Set rook
        updateBitboard(to, Piece.ROOK, white);
        updateMailbox(to, Piece.ROOK);

    }

    private void unmakePromotionMove(int from, int to, Piece promotionPiece) {

        // Remove promoted piece
        updateBitboard(to, promotionPiece, white);
        // Put back promoting pawn
        updateMailbox(from, Piece.PAWN);
        updateBitboard(from, Piece.PAWN, white);
        // Put back captured piece
        if (state.getCaptured() != null) {
            updateBitboard(to, state.getCaptured(), !white);
        }
        // If no piece was captured, this correctly nullifies the promo square
        updateMailbox(to, state.getCaptured());

    }

    private void unmakeEnPassantMove(int from, int to) {

        // Put back capturing pawn
        updateBitboards(to, from, Piece.PAWN, white);
        updateMailbox(to, from, Piece.PAWN);
        // Add back captured pawn
        int captureSquare = white ? to - 8 : to + 8;
        updateBitboard(captureSquare, Piece.PAWN, !white);
        updateMailbox(captureSquare, Piece.PAWN);

    }

    private void unmakeStandardMove(int from, int to, Piece piece) {

        // Put back moving piece
        updateBitboards(to, from, piece, white);
        updateMailbox(to, from, piece);
        if (state.getCaptured() != null) {
            // Add back captured piece
            updateBitboard(to, state.getCaptured(), !white);
            updateMailbox(to, state.getCaptured());
        }

    }

    // Make a 'null' move, meaning the side to move passes their turn and gives the opponent a double-move.
    // Used exclusively for null-move pruning during search.
    public void makeNullMove() {
        white = !white;
        //long key = state.key ^ Key.nullMove(state.enPassantFile);
        long key = state.key;
        long[] nonPawnKeys = new long[] {state.nonPawnKeys[0], state.nonPawnKeys[1]};
        BoardState newState = new BoardState(key, state.pawnKey, nonPawnKeys, null, null, -1, state.getRights(), 0);
        states[ply++] = state;
        state = newState;
    }

    // Unmake the 'null' move used during null-move pruning to try and prove a beta cut-off.
    public void unmakeNullMove() {
        white = !white;
        state = states[--ply];
    }

    public void updateBitboards(int from, int to, Piece piece, boolean white) {
        long[] toggleMask =Bits.or(Bits.of(from), Bits.of(to));
        toggle(toggleMask, piece, white);
    }

    public void updateBitboard(int square, Piece piece, boolean white) {
        long[] toggleMask = Bits.of(square);
        toggle(toggleMask, piece, white);
    }

    /**
     * 中国象棋版本的toggle方法，支持long[2]表示90格棋盘
     * @param mask 要切换的位置掩码，long[2]格式
     * @param type 棋子类型
     * @param white 是否为白方（红方）
     */
    private void toggle(long[] mask, Piece type, boolean white) {
        int pieceIndex = type.index();
        // 对特定棋子类型的位板进行异或操作
        bitboards[pieceIndex][0] ^= mask[0];
        bitboards[pieceIndex][1] ^= mask[1];

        // 对特定颜色的位板进行异或操作
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        bitboards[colourIndex][0] ^= mask[0];
        bitboards[colourIndex][1] ^= mask[1];
    }


    private void updateKeys(int from, int to, Piece piece, boolean white) {
        long[] hash = Key.piece(from, to, piece, white);
        state.key ^= hash[0];
        state.key ^= hash[1];
    }

    private void updateKeys(int square, Piece piece, boolean white) {
        long[] hash = Key.piece(square, piece, white);
        state.key ^= hash[0];
        state.key ^= hash[1];
    }

    private void updateMailbox(int from, int to, Piece piece) {
        pieces[from] = null;
        pieces[to] = piece;
    }

    private void updateMailbox(int square, Piece piece) {
        pieces[square] = piece;
    }

/*    public void removeKing(boolean white) {
        int pieceIndex = Piece.KING.index;
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        long toggleMask = bitboards[pieceIndex] & bitboards[colourIndex];
        bitboards[pieceIndex] ^= toggleMask;
        bitboards[colourIndex] ^= toggleMask;
    }

    public void addKing(int kingSquare, boolean white) {
        long toggleMask = Bits.of(kingSquare);
        int pieceIndex = Piece.KING.index;
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        bitboards[pieceIndex] |= toggleMask;
        bitboards[colourIndex] |= toggleMask;
    }*/

    private int updateCastleRights(int from, int to, Piece pieceType) {
        int newRights = state.getRights();
        if (newRights == Castling.empty()) {
            // Both sides already lost castling rights, so nothing to calculate.
            return newRights;
        }
        // Any move by the king removes castling rights.
        if (Piece.KING.equals(pieceType)) {
            newRights = Castling.clearSide(newRights, white);
        }
        // Any move starting from/ending at a rook square removes castling rights for that corner.
        // Note: all of these cases need to be checked, to cover the scenario where a rook in starting position captures
        // another rook in starting position; in that case, both sides lose castling rights!
        int wk = Castling.getRook(newRights, true, true);
        if (from == wk || to == wk) {
            newRights = Castling.clearRook(newRights, true, true);
        }
        int wq = Castling.getRook(newRights, false, true);
        if (from == wq || to == wq) {
            newRights = Castling.clearRook(newRights, false, true);
        }
        int bk = Castling.getRook(newRights, true, false);
        if (from == bk || to == bk) {
            newRights = Castling.clearRook(newRights, true, false);
        }
        int bq = Castling.getRook(newRights, false, false);
        if (from == bq || to == bq) {
            newRights = Castling.clearRook(newRights, false, false);
        }
        return newRights;
    }

    public Piece pieceAt(int square) {
        return pieces[square];
    }

    public Piece captured(Move move) {
        return move.isEnPassant() ? Piece.PAWN : pieceAt(move.to());
    }

    public boolean isCapture(Move move) {
        return move.isEnPassant() || pieceAt(move.to()) != null;
    }

    public boolean isNoisy(Move move) {
        return move.isPromotion() || isCapture(move);
    }

    public boolean isQuiet(Move move) {
        return !move.isPromotion() && !isCapture(move);
    }

    public long[] us() {
        return bitboards[white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES];
    }

    public long[] them() {
        return bitboards[white ? Piece.BLACK_PIECES : Piece.WHITE_PIECES];
    }

    public long[] our(Piece piece) {
        return Bits.and(bitboards[piece.index], us());
    }

    public long[] their(Piece piece) {
        return Bits.and(bitboards[piece.index], them());
    }

    public long[] getPawns(boolean white) {
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        return Bits.and(bitboards[Piece.PAWN.index], bitboards[colourIndex]);
    }

    public long[] getKnights(boolean white) {
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        return Bits.and(bitboards[Piece.KNIGHT.index], bitboards[colourIndex]);
    }

    public long[] getBishops(boolean white) {
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        return Bits.and(bitboards[Piece.BISHOP.index], bitboards[colourIndex]);
    }

    public long[] getRooks(boolean white) {
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        return Bits.and(bitboards[Piece.ROOK.index], bitboards[colourIndex]);
    }

    public long[] getAdvisors(boolean white) {
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        return Bits.and(bitboards[Piece.ADVISOR.index], bitboards[colourIndex]);
    }

    public long[] getCannons(boolean white) {
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        return Bits.and(bitboards[Piece.CANNON.index], bitboards[colourIndex]);
    }

    public long[] getKing(boolean white) {
        int colourIndex = white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES;
        return Bits.and(bitboards[Piece.KING.index], bitboards[colourIndex]);
    }

    public long[] getPieces(boolean white) {
        return bitboards[white ? Piece.WHITE_PIECES : Piece.BLACK_PIECES];
    }

    public long[][] getBitboards() {
        return bitboards;
    }

    public void setPawns(long[] pawns) {
        this.bitboards[Piece.PAWN.index] = pawns;
    }

    public void setKnights(long[] knights) {
        this.bitboards[Piece.KNIGHT.index] = knights;
    }

    public void setBishops(long[] bishops) {
        this.bitboards[Piece.BISHOP.index] = bishops;
    }

    public void setRooks(long[] rooks) {
        this.bitboards[Piece.ROOK.index] = rooks;
    }

    public void setAdvisors(long[] queens) {
        this.bitboards[Piece.ADVISOR.index] = queens;
    }

    public void setCannons(long[] queens) {
        this.bitboards[Piece.CANNON.index] = queens;
    }

    public void setKings(long[] kings) {
        this.bitboards[Piece.KING.index] = kings;
    }

    public void setWhitePieces(long[] whitePieces) {
        this.bitboards[Piece.WHITE_PIECES] = whitePieces;
    }

    public void setBlackPieces(long[] blackPieces) {
        this.bitboards[Piece.BLACK_PIECES] = blackPieces;
    }

    public void setBitboards(long[][] bitboards) {
        this.bitboards = bitboards;
    }

    public void setPieces(Piece[] pieces) {
        this.pieces = pieces;
    }

    public void setWhite(boolean white) {
        this.white = white;
    }

    public void setState(BoardState state) {
        this.state = state;
    }

    public void setStates(BoardState[] states) {
        this.states = states;
    }

    public void setMoves(Move[] moves) {
        this.moves = moves;
    }

    public long[] getPawns() {
        return bitboards[Piece.PAWN.index];
    }

    public long[] getKnights() {
        return bitboards[Piece.KNIGHT.index];
    }

    public long[] getBishops() {
        return bitboards[Piece.BISHOP.index];
    }

    public long[] getRooks() {
        return bitboards[Piece.ROOK.index];
    }

    public long[] getCannons() {
        return bitboards[Piece.CANNON.index];
    }
    public long[] getAdvisors() {
        return bitboards[Piece.ADVISOR.index];
    }

    public long[] getKings() {
        return bitboards[Piece.KING.index];
    }

    public long[] getWhitePieces() {
        return bitboards[Piece.WHITE_PIECES];
    }

    public long[] getBlackPieces() {
        return bitboards[Piece.BLACK_PIECES];
    }

    public long[] getOccupied() {
        return Bits.or(bitboards[Piece.WHITE_PIECES], bitboards[Piece.BLACK_PIECES]);
    }

    public Piece[] getPieces() {
        return pieces;
    }

    public boolean isWhite() {
        return white;
    }

    public BoardState getState() {
        return state;
    }

    public BoardState[] getStates() {
        return states;
    }

    public Move[] getMoves() {
        return moves;
    }

    public int getPly() {
        return ply;
    }

    public long key() {
        return state.getKey();
    }

    public long pawnKey() {
        return state.getPawnKey();
    }

    public long[] nonPawnKeys() {
        return state.nonPawnKeys;
    }

    /**
     * 获取指定颜色方国王的位置
     * @param white true表示白方,false表示黑方
     * @return 返回国王所在的格子索引
     */
    public int kingSquare(boolean white) {
        // 获取指定颜色的国王位棋盘
        long[] kings = getKing(white);
        // 获取指定颜色的所有棋子位棋盘
        long[] pieces = getPieces(white);
        // 通过位运算找到国王所在位置并返回其索引
        return Bits.next(Bits.and(kings, pieces));
    }

    public long[] getPieces(Piece piece, boolean white) {
        return switch (piece) {
            case PAWN -> getPawns(white);
            case KNIGHT -> getKnights(white);
            case BISHOP -> getBishops(white);
            case ROOK -> getRooks(white);
            case ADVISOR -> getAdvisors(white);
            case CANNON -> getCannons(white);
            case KING -> getKing(white);
        };
    }

/*    public boolean hasNonPawnMaterial() {
        return (our(Piece.KING) | our(Piece.PAWN)) != us();
    }*/

    public static Board from(String fen) {
        return FEN.parse(fen).toBoard();
    }

    private void checkMaxPly() {
        if (ply >= states.length) {
            BoardState[] newStates = new BoardState[states.length + 64];
            System.arraycopy(states, 0, newStates, 0, states.length);

            Move[] newMoves = new Move[moves.length + 64];
            System.arraycopy(moves, 0, newMoves, 0, moves.length);

            states = newStates;
            moves = newMoves;
        }
    }

    public Board copy() {
        Board newBoard = new Board();
        newBoard.setPawns(this.getPawns());
        newBoard.setKnights(this.getKnights());
        newBoard.setBishops(this.getBishops());
        newBoard.setRooks(this.getRooks());
        newBoard.setAdvisors(this.getAdvisors());
        newBoard.setCannons(this.getCannons());
        newBoard.setKings(this.getKings());
        newBoard.setWhitePieces(this.getWhitePieces());
        newBoard.setBlackPieces(this.getBlackPieces());
        newBoard.setWhite(this.isWhite());
        newBoard.setState(this.getState().copy());
        BoardState[] newStates = new BoardState[this.getStates().length];
        for (int i = 0; i < this.getStates().length; i++) {
            if (this.getStates()[i] == null) {
                break;
            }
            newStates[i] = this.getStates()[i].copy();
        }
        newBoard.setStates(newStates);
        Move[] newMoves = new Move[this.getMoves().length];
        for (int i = 0; i < this.getMoves().length; i++) {
            if (this.getMoves()[i] == null) {
                break;
            }
            newMoves[i] = new Move(this.getMoves()[i].value());
        }
        newBoard.setMoves(newMoves);
        newBoard.setPieces(Arrays.copyOf(this.getPieces(), this.getPieces().length));
        return newBoard;
    }

    public void print() {

        for (int rank = 9; rank >= 0; --rank) {
            System.out.print(" +---+---+---+---+---+---+---+---+---+\n");

            for (int file = 0; file < 9; ++file) {
                int sq = Square.of(rank, file);
                Piece piece = pieceAt(sq);
                if (piece == null) {
                    System.out.print(" |  ");
                    continue;
                }
                boolean white = Bits.isEmpty(Bits.and(getWhitePieces(), Bits.of(sq)));
                System.out.print(" | " + (white ? piece.code().toUpperCase() : piece.code()));
            }
            System.out.print(" | " + (rank + 1) + "\n");
        }
        System.out.print(" +---+---+---+---+---+---+---+---+---+\n");
        System.out.print("   a   b   c   d   e   f   g   h   i\n\n");
        System.out.print((white ? "White" : "Black") + " to move\n");
    }


}
