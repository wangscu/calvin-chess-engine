package com.kelseyde.calvin.movegen;

import java.util.ArrayList;
import java.util.List;

import com.kelseyde.calvin.board.Bits;
import com.kelseyde.calvin.board.Bits.Square;
import com.kelseyde.calvin.board.Board;
import com.kelseyde.calvin.board.Move;
import com.kelseyde.calvin.board.Piece;

/**
 * Generates all the legal moves in a given position. Using a hybrid of pseudo-legal and legal move generation: first we calculate the bitboards for
 * checking pieces and pinned pieces. If there is a check, we filter out all moves that do not resolve the check. Finally, we filter out all moves
 * that leave the king in (a new) check.
 */
public class MoveGenerator {

    private int checkersCount;
    private long checkersMask;
    private long pinMask;
    private final long[] pinRayMasks = new long[64];
    private long captureMask;
    private long pushMask;
    private MoveFilter filter;
    private boolean white;

    private long[] pawns;
    private long[] knights;
    private long[] bishops;
    private long[] rooks;
    private long[] advisors;
    private long[] king;
    private long[] cannons;
    private List<Move> legalMoves;

    public List<Move> generateMoves(Board board) {
        return generateMoves(board, MoveFilter.ALL);
    }

    public List<Move> generateMoves(Board board, MoveFilter filter) {
        this.filter = filter;
        this.legalMoves = new ArrayList<>();

        // 获取当前行棋方
        white = board.isWhite();

        // 按照C++代码的顺序依次生成各种棋子的移动
        generatePawnMoves(board);      // 兵/卒
        generateCannonMoves(board);    // 炮
        generateRookMoves(board);      // 车
        generateKnightMoves(board);    // 马
        generateAdvisorMoves(board);   // 士
        generateElephantMoves(board);  // 相/象
        generateKingMoves(board);      // 将/帅

        // 处理将帅照面的特殊情况
        generateKingFaceKingMove(board);
        return legalMoves;
    }


    /**
     * Checks if the specified side is in check.
     *
     * @param board The current board state.
     * @param white Indicates whether the side in question is white.
     * @return True if the specified side is in check, otherwise false.
     */
    public boolean isCheck(Board board, boolean white) {
        long[] king = board.getKing(white);
        return isAttacked(board, white, king);
    }

    public boolean isCheck(Board board) {
        return isCheck(board, board.isWhite());
    }

    public boolean isPseudoLegal(Board board, Move move) {
        return true;
    }

    public boolean isLegal(Board board, Move move) {
        if (!isPseudoLegal(board, move)) {
            return false;
        }

        board.makeMove(move);
        boolean legal = !isCheck(board, !board.isWhite());
        board.unmakeMove();

        return legal;
    }

    /**
     * Checks if a move gives check, *before* the move is made on the board.
     */
    /**
     * 判断一个移动是否会造成将军
     *
     * @param board 当前棋盘状态
     * @param move 要判断的移动
     * @return 如果移动会造成将军返回true，否则返回false
     */
    public boolean givesCheck(Board board, Move move) {
        // 执行移动
        board.makeMove(move);
        // 检查是否造成将军
        boolean isCheck = isCheck(board, !white); // 检查对手是否被将军
        board.unmakeMove();
        return isCheck;
    }

    /**
     * 生成兵/卒的移动（中国象棋规则，不考虑将军情况）
     */
    private void generatePawnMoves(Board board) {
        long[] pawns = board.getPawns(white);
        if (Bits.isEmpty(pawns)) {
            return;
        }

        long[] opponents = board.getPieces(!white);
        long[] ourPieces = board.getPieces(white);

        // 遍历所有己方的兵
        long[] currentPawns = {pawns[0], pawns[1]};
        while (!Bits.isEmpty(currentPawns)) {
            int pawnSquare = Bits.next(currentPawns);

            // 获取该兵的所有可能移动位置
            long[] pawnAttacks = ChineseAttacks.getPawnAttacks(pawnSquare, white);

            // 生成该兵的所有移动
            generatePawnMovesFromSquare(board, pawnSquare, pawnAttacks, opponents, ourPieces);

            // 移除已处理的兵
            currentPawns = Bits.clearBit(currentPawns, pawnSquare);
        }
    }

    /**
     * 为单个兵生成所有可能的移动
     *
     * @param board 当前棋盘状态
     * @param fromSquare 兵的起始位置
     * @param attacks 兵可能的攻击位置数组
     * @param opponents 对手棋子的位置数组
     * @param ourPieces 己方棋子的位置数组
     */
    private void generatePawnMovesFromSquare(Board board, int fromSquare, long[] attacks, long[] opponents, long[] ourPieces) {

        // 复制攻击位置数组以便遍历
        long[] currentAttacks = {attacks[0], attacks[1]};

        // 遍历所有可能的目标位置
        while (!Bits.isEmpty(currentAttacks)) {
            // 获取下一个可能的目标位置
            int toSquare = Bits.next(currentAttacks);

            // 如果目标位置被己方棋子占据，跳过该位置
            if (Bits.testBit(ourPieces, toSquare)) {
                currentAttacks = Bits.clearBit(currentAttacks, toSquare);
                continue;
            }

            // 判断是否为吃子移动
            boolean isCapture = Bits.testBit(opponents, toSquare);

            // 根据移动过滤器检查是否应该生成该移动
            if (shouldGenerateMove(isCapture)) {
                // 创建新的移动并添加到合法移动列表中
                Move move = new Move(fromSquare, toSquare, isCapture ? Move.CAPTURE_FLAG : Move.QUIET_FLAG);
                legalMoves.add(move);
            }

            // 从当前攻击位置列表中移除已处理的位置
            currentAttacks = Bits.clearBit(currentAttacks, toSquare);
        }
    }

    /**
     * 生成马的移动（中国象棋规则） 马走日字，需要检查马腿是否被阻挡
     */
    private void generateKnightMoves(Board board) {
        long[] knights = board.getKnights(white);
        if (Bits.isEmpty(knights)) {
            return;
        }

        long[] opponents = board.getPieces(!white);
        long[] occupied = board.getOccupied();
        long[] ourPieces = board.getPieces(white);

        // 遍历所有己方的马
        long[] currentKnights = {knights[0], knights[1]};
        while (!Bits.isEmpty(currentKnights)) {
            int knightSquare = Bits.next(currentKnights);

            // 获取该马的所有可能移动位置（已经考虑了马腿阻挡）
            long[] knightAttacks = ChineseAttacks.getHorseAttacks(knightSquare, occupied);

            // 为该马生成所有可能的移动
            generateKnightMovesFromSquare(board, knightSquare, knightAttacks, opponents, ourPieces);

            // 移除已处理的马
            currentKnights = Bits.clearBit(currentKnights, knightSquare);
        }
    }

    /**
     * 为单个马生成所有可能的移动
     */
    private void generateKnightMovesFromSquare(Board board, int fromSquare, long[] attacks, long[] opponents, long[] ourPieces) {

        long[] currentAttacks = {attacks[0], attacks[1]};

        while (!Bits.isEmpty(currentAttacks)) {
            int toSquare = Bits.next(currentAttacks);

            // 检查目标位置是否被己方棋子占据
            if (Bits.testBit(ourPieces, toSquare)) {
                currentAttacks = Bits.clearBit(currentAttacks, toSquare);
                continue;
            }

            boolean isCapture = Bits.testBit(opponents, toSquare);

            // 根据移动过滤器决定是否生成该移动
            if (shouldGenerateMove(isCapture)) {
                // 创建并添加移动
                Move move = new Move(fromSquare, toSquare, isCapture ? Move.CAPTURE_FLAG : Move.QUIET_FLAG);
                ;
                legalMoves.add(move);
            }

            currentAttacks = Bits.clearBit(currentAttacks, toSquare);
        }
    }

    private boolean isKingFaceKing(Board board) {
        long[] ourKing = board.getKing(white);
        long[] opponentKing = board.getKing(!white);

        if (Bits.isEmpty(ourKing) || Bits.isEmpty(opponentKing)) {
            return false;
        }

        int ourKingSquare = Bits.next(ourKing);
        int opponentKingSquare = Bits.next(opponentKing);

        // 检查是否在同一列
        if (ChineseAttacks.file(ourKingSquare) != ChineseAttacks.file(opponentKingSquare)) {
            return false;
        }

        // 检查两将之间是否没有其他棋子（除了两个将之外只有2个棋子在该列）
        long[] occupied = board.getOccupied();
        long[] fileMask = getFileMask(ChineseAttacks.file(ourKingSquare));
        long[] piecesInFile = Bits.and(occupied, fileMask);

        // 如果该列只有2个棋子（两个将），则为照面
        return Bits.popCount(piecesInFile) == 2;
    }

    private long[] getFileMask(int file) {
        long[] mask = Bits.emptyBitBoard();
        for (int rank = 0; rank < Square.RANK_COUNT; rank++) {
            int square = ChineseAttacks.square(rank, file);
            mask = Bits.setBit(mask, square);
        }
        return mask;
    }

    /**
     * 生成车的移动（中国象棋规则）
     */
    private void generateRookMoves(Board board) {
        long[] rooks = board.getRooks(white);
        if (Bits.isEmpty(rooks)) {
            return;
        }

        long[] opponents = board.getPieces(!white);
        long[] occupied = board.getOccupied();
        long[] ourPieces = board.getPieces(white);

        // 遍历所有己方的车
        long[] currentRooks = {rooks[0], rooks[1]};
        while (!Bits.isEmpty(currentRooks)) {
            int rookSquare = Bits.next(currentRooks);

            // 获取该车的所有可能移动位置
            long[] rookAttacks = ChineseAttacks.getRookAttacks(rookSquare, occupied);

            // 生成该车的所有移动
            generateRookMovesFromSquare(board, rookSquare, rookAttacks, opponents, ourPieces);

            // 移除已处理的车
            currentRooks = Bits.clearBit(currentRooks, rookSquare);
        }
    }

    /**
     * 为单个车生成所有可能的移动
     */
    private void generateRookMovesFromSquare(Board board, int fromSquare, long[] attacks, long[] opponents, long[] ourPieces) {

        long[] currentAttacks = {attacks[0], attacks[1]};

        while (!Bits.isEmpty(currentAttacks)) {
            int toSquare = Bits.next(currentAttacks);

            // 检查目标位置是否被己方棋子占据
            if (Bits.testBit(ourPieces, toSquare)) {
                currentAttacks = Bits.clearBit(currentAttacks, toSquare);
                continue;
            }

            boolean isCapture = Bits.testBit(opponents, toSquare);

            // 根据移动过滤器决定是否生成该移动
            if (shouldGenerateMove(isCapture)) {
                Move move = createRookMove(fromSquare, toSquare, isCapture);
                legalMoves.add(move);
            }

            currentAttacks = Bits.clearBit(currentAttacks, toSquare);
        }
    }

    /**
     * 创建车的移动
     */
    private Move createRookMove(int fromSquare, int toSquare, boolean isCapture) {
        return new Move(fromSquare, toSquare, isCapture ? Move.CAPTURE_FLAG : Move.QUIET_FLAG);
    }

    /**
     * 生成炮的移动（中国象棋规则）
     */
    private void generateCannonMoves(Board board) {
        long[] cannons = board.getCannons(white);
        if (Bits.isEmpty(cannons)) {
            return;
        }

        long[] opponents = board.getPieces(!white);
        long[] occupied = board.getOccupied();
        long[] ourPieces = board.getPieces(white);

        // 遍历所有己方的炮
        long[] currentCannons = {cannons[0], cannons[1]};
        while (!Bits.isEmpty(currentCannons)) {
            int cannonSquare = Bits.next(currentCannons);

            // 获取该炮的所有可能移动位置（隔子攻击）
            long[] cannonAttacks = ChineseAttacks.getCannonAttacks(cannonSquare, occupied);

            // 生成该炮的所有移动
            generateCannonMovesFromSquare(board, cannonSquare, cannonAttacks, opponents, ourPieces);

            // 移除已处理的炮
            currentCannons = Bits.clearBit(currentCannons, cannonSquare);
        }
    }

    /**
     * 为单个炮生成所有可能的移动
     */
    private void generateCannonMovesFromSquare(Board board, int fromSquare, long[] attacks, long[] opponents, long[] ourPieces) {

        long[] currentAttacks = {attacks[0], attacks[1]};

        while (!Bits.isEmpty(currentAttacks)) {
            int toSquare = Bits.next(currentAttacks);

            // 检查目标位置是否被己方棋子占据
            if (Bits.testBit(ourPieces, toSquare)) {
                currentAttacks = Bits.clearBit(currentAttacks, toSquare);
                continue;
            }

            boolean isCapture = Bits.testBit(opponents, toSquare);

            // 根据移动过滤器决定是否生成该移动
            if (shouldGenerateMove(isCapture)) {
                Move move = createCannonMove(fromSquare, toSquare, isCapture);
                legalMoves.add(move);
            }

            currentAttacks = Bits.clearBit(currentAttacks, toSquare);
        }
    }

    /**
     * 创建炮的移动
     */
    private Move createCannonMove(int fromSquare, int toSquare, boolean isCapture) {
        return new Move(fromSquare, toSquare, isCapture ? Move.CAPTURE_FLAG : Move.QUIET_FLAG);
    }

    /**
     * 生成士的移动（中国象棋规则）
     */
    private void generateAdvisorMoves(Board board) {
        long[] advisors = board.getAdvisors(white);
        if (Bits.isEmpty(advisors)) {
            return;
        }

        long[] opponents = board.getPieces(!white);
        long[] ourPieces = board.getPieces(white);

        // 遍历所有己方的士
        long[] currentAdvisors = {advisors[0], advisors[1]};
        while (!Bits.isEmpty(currentAdvisors)) {
            int advisorSquare = Bits.next(currentAdvisors);

            // 获取该士的所有可能移动位置（九宫内斜走）
            long[] advisorAttacks = ChineseAttacks.getAdvisorAttacks(advisorSquare);

            // 生成该士的所有移动
            generateAdvisorMovesFromSquare(board, advisorSquare, advisorAttacks, opponents, ourPieces);

            // 移除已处理的士
            currentAdvisors = Bits.clearBit(currentAdvisors, advisorSquare);
        }
    }

    /**
     * 为单个士生成所有可能的移动
     */
    private void generateAdvisorMovesFromSquare(Board board, int fromSquare, long[] attacks, long[] opponents, long[] ourPieces) {

        long[] currentAttacks = {attacks[0], attacks[1]};

        while (!Bits.isEmpty(currentAttacks)) {
            int toSquare = Bits.next(currentAttacks);

            // 检查目标位置是否被己方棋子占据
            if (Bits.testBit(ourPieces, toSquare)) {
                currentAttacks = Bits.clearBit(currentAttacks, toSquare);
                continue;
            }

            boolean isCapture = Bits.testBit(opponents, toSquare);

            // 根据移动过滤器决定是否生成该移动
            if (shouldGenerateMove(isCapture)) {
                Move move = createAdvisorMove(fromSquare, toSquare, isCapture);
                legalMoves.add(move);
            }

            currentAttacks = Bits.clearBit(currentAttacks, toSquare);
        }
    }

    /**
     * 创建士的移动
     */
    private Move createAdvisorMove(int fromSquare, int toSquare, boolean isCapture) {
        return new Move(fromSquare, toSquare, isCapture ? Move.CAPTURE_FLAG : Move.QUIET_FLAG);
    }

    /**
     * 生成相/象的移动（中国象棋规则）
     */
    private void generateElephantMoves(Board board) {
        long[] elephants = board.getBishops(white); // 在Board中相对应Bishop
        if (Bits.isEmpty(elephants)) {
            return;
        }

        long[] opponents = board.getPieces(!white);
        long[] occupied = board.getOccupied();
        long[] ourPieces = board.getPieces(white);

        // 遍历所有己方的相/象
        long[] currentElephants = {elephants[0], elephants[1]};
        while (!Bits.isEmpty(currentElephants)) {
            int elephantSquare = Bits.next(currentElephants);

            // 获取该相的所有可能移动位置（田字走法，不过河，需检查相眼）
            long[] elephantAttacks = getElephantValidMoves(elephantSquare, occupied);

            // 生成该相的所有移动
            generateElephantMovesFromSquare(board, elephantSquare, elephantAttacks, opponents, ourPieces);

            // 移除已处理的相
            currentElephants = Bits.clearBit(currentElephants, elephantSquare);
        }
    }

    /**
     * 获取相的有效移动（考虑相眼阻挡）
     */
    private long[] getElephantValidMoves(int square, long[] occupied) {
        // 先获取所有理论移动位置
        long[] allMoves = ChineseAttacks.getElephantAttacks(square);
        long[] validMoves = Bits.emptyBitBoard();

        // 相的4个田字移动和对应的相眼位置
        int[][] moves = {{2, 2, 1, 1}, {2, -2, 1, -1}, {-2, 2, -1, 1}, {-2, -2, -1, -1}};
        int rank = ChineseAttacks.rank(square);
        int file = ChineseAttacks.file(square);

        for (int[] move : moves) {
            int newRank = rank + move[0];
            int newFile = file + move[1];
            int eyeRank = rank + move[2];
            int eyeFile = file + move[3];

            if (ChineseAttacks.inBoard(newRank, newFile) && ChineseAttacks.inBoard(eyeRank, eyeFile)) {

                int targetSquare = ChineseAttacks.square(newRank, newFile);
                int eyeSquare = ChineseAttacks.square(eyeRank, eyeFile);

                // 检查目标位置是否在理论移动范围内，且相眼没被阻挡
                if (Bits.testBit(allMoves, targetSquare) && !Bits.testBit(occupied, eyeSquare)) {
                    validMoves = Bits.setBit(validMoves, targetSquare);
                }
            }
        }

        return validMoves;
    }

    /**
     * 为单个相生成所有可能的移动
     */
    private void generateElephantMovesFromSquare(Board board, int fromSquare, long[] attacks, long[] opponents, long[] ourPieces) {

        long[] currentAttacks = {attacks[0], attacks[1]};

        while (!Bits.isEmpty(currentAttacks)) {
            int toSquare = Bits.next(currentAttacks);

            // 检查目标位置是否被己方棋子占据
            if (Bits.testBit(ourPieces, toSquare)) {
                currentAttacks = Bits.clearBit(currentAttacks, toSquare);
                continue;
            }

            boolean isCapture = Bits.testBit(opponents, toSquare);

            // 根据移动过滤器决定是否生成该移动
            if (shouldGenerateMove(isCapture)) {
                Move move = createElephantMove(fromSquare, toSquare, isCapture);
                legalMoves.add(move);
            }

            currentAttacks = Bits.clearBit(currentAttacks, toSquare);
        }
    }

    /**
     * 创建相的移动
     */
    private Move createElephantMove(int fromSquare, int toSquare, boolean isCapture) {
        return new Move(fromSquare, toSquare, isCapture ? Move.CAPTURE_FLAG : Move.QUIET_FLAG);
    }

    /**
     * 生成将/帅的移动（中国象棋规则） 将/帅只能在九宫内移动，且只能走直线（上下左右）
     */
    private void generateKingMoves(Board board) {
        long[] kings = board.getKing(white);
        if (Bits.isEmpty(kings)) {
            return;
        }

        long[] opponents = board.getPieces(!white);
        long[] ourPieces = board.getPieces(white);

        // 获取将/帅的位置（通常只有一个）
        int kingSquare = Bits.next(kings);

        // 获取该将/帅的所有可能移动位置
        long[] kingAttacks = ChineseAttacks.getKingAttacks(kingSquare);

        // 生成该将/帅的所有移动
        generateKingMovesFromSquare(board, kingSquare, kingAttacks, opponents, ourPieces);
    }

    /**
     * 为将/帅生成所有可能的移动
     */
    private void generateKingMovesFromSquare(Board board, int fromSquare, long[] attacks, long[] opponents, long[] ourPieces) {

        long[] currentAttacks = {attacks[0], attacks[1]};

        while (!Bits.isEmpty(currentAttacks)) {
            int toSquare = Bits.next(currentAttacks);

            // 检查目标位置是否被己方棋子占据
            if (Bits.testBit(ourPieces, toSquare)) {
                currentAttacks = Bits.clearBit(currentAttacks, toSquare);
                continue;
            }

            boolean isCapture = Bits.testBit(opponents, toSquare);

            // 根据移动过滤器决定是否生成该移动
            if (!shouldGenerateMove(isCapture)) {
                currentAttacks = Bits.clearBit(currentAttacks, toSquare);
                continue;
            }

            // 检查移动是否合法（不会让己方将军暴露或违反将帅照面规则）
            if (isLegalKingMove(board, fromSquare, toSquare)) {
                // 生成移动
                Move move = createKingMove(fromSquare, toSquare, isCapture);
                legalMoves.add(move);
            }

            currentAttacks = Bits.clearBit(currentAttacks, toSquare);
        }
    }

    /**
     * 创建将/帅的移动
     */
    private Move createKingMove(int fromSquare, int toSquare, boolean isCapture) {
        // 将/帅的移动标记
        int flags = isCapture ? Move.CAPTURE_FLAG : Move.QUIET_FLAG;
        return new Move(fromSquare, toSquare, flags);
    }

    /**
     * 检查将/帅的移动是否合法 需要检查：1. 移动后不会被攻击 2. 不会违反将帅照面规则
     */
    private boolean isLegalKingMove(Board board, int fromSquare, int toSquare) {
        // 临时执行移动
        Piece capturedPiece = board.pieceAt(toSquare);

        Move move = new Move(fromSquare, toSquare, null != capturedPiece ? Move.CAPTURE_FLAG : Move.QUIET_FLAG);
        board.makeMove(move);

        boolean isLegal = true;

        // 检查1：新位置是否会被敌方攻击
        long[] newKingPosition = Bits.of(toSquare);
        if (isSquareAttacked(board, newKingPosition, white)) {
            isLegal = false;
        }

        // 检查2：是否违反将帅照面规则
        if (isLegal && violatesKingFaceToFaceRule(board, toSquare)) {
            isLegal = false;
        }

        // 撤销移动
        board.unmakeMove();

        return isLegal;
    }

    /**
     * 检查是否违反将帅照面规则
     */
    private boolean violatesKingFaceToFaceRule(Board board, int ourKingSquare) {
        long[] opponentKing = board.getKing(!white);
        if (Bits.isEmpty(opponentKing)) {
            return false;
        }

        int opponentKingSquare = Bits.next(opponentKing);

        // 检查是否在同一列
        if (ChineseAttacks.file(ourKingSquare) != ChineseAttacks.file(opponentKingSquare)) {
            return false;
        }

        // 检查两将之间是否有其他棋子
        long[] occupied = board.getOccupied();
        long[] betweenMask = getBetweenMask(ourKingSquare, opponentKingSquare);

        // 如果两将之间没有棋子，则违反照面规则
        return Bits.isEmpty(Bits.and(betweenMask, occupied));
    }

    /**
     * 检查指定位置是否受到攻击
     */
    private boolean isSquareAttacked(Board board, long[] squareMask, boolean defendingSide) {
        // 调用中国象棋的攻击检测函数
        return isAttacked(board, defendingSide, squareMask);
    }

    private void generateKingFaceKingMove(Board board) {
        if (!isKingFaceKing(board)) {
            return;
        }

        long[] ourKing = board.getKing(white);
        long[] opponentKing = board.getKing(!white);

        if (Bits.isEmpty(ourKing) || Bits.isEmpty(opponentKing)) {
            return;
        }

        int fromSquare = Bits.next(ourKing);
        int toSquare = Bits.next(opponentKing);

        // 创建将帅照面的特殊移动（吃对方将帅）
        if (shouldGenerateMove(true)) { // 将帅照面是吃子移动
            Move move = new Move(fromSquare, toSquare, Move.CAPTURE_FLAG);
            legalMoves.add(move);
        }
    }

    /**
     * 检查指定位置是否被对手攻击（中国象棋规则）
     *
     * @param board 棋盘状态
     * @param white 当前方颜色
     * @param squareMask 需要检查的位置掩码（long[2]格式）
     * @return 如果被攻击返回true，否则返回false
     */
    public boolean isAttacked(Board board, boolean white, long[] squareMask) {
        // 获取棋盘上所有棋子的位置
        long[] occupied = board.getOccupied();

        // 遍历所有需要检查的位置
        long[] currentMask = {squareMask[0], squareMask[1]};

        while (!Bits.isEmpty(currentMask)) {
            int square = Bits.next(currentMask);

            // 检查对手的兵(卒)是否能攻击到目标位置
            if (isAttackedByPawn(board, square, white)) {
                return true;
            }

            // 检查对手的车是否能攻击到目标位置
            if (isAttackedByRook(board, square, occupied, white)) {
                return true;
            }

            // 检查对手的马是否能攻击到目标位置
            if (isAttackedByHorse(board, square, occupied, white)) {
                return true;
            }

            // 检查对手的炮是否能攻击到目标位置
            if (isAttackedByCannon(board, square, occupied, white)) {
                return true;
            }

            // 检查对手的相(象)是否能攻击到目标位置
            if (isAttackedByElephant(board, square, white)) {
                return true;
            }

            // 检查对手的士是否能攻击到目标位置
            if (isAttackedByAdvisor(board, square, white)) {
                return true;
            }

            // 检查对手的将(帅)是否能攻击到目标位置
            if (isAttackedByKing(board, square, white)) {
                return true;
            }

            // 移除已检查的位置
            currentMask = Bits.clearBit(currentMask, square);
        }

        return false;
    }

    /**
     * 检查是否被对手兵(卒)攻击
     */
    private boolean isAttackedByPawn(Board board, int square, boolean white) {
        long[] opponentPawns = board.getPawns(!white);
        if (Bits.isEmpty(opponentPawns)) {
            return false;
        }

        // 遍历所有对手的兵，检查它们是否能攻击到目标位置
        long[] currentPawns = {opponentPawns[0], opponentPawns[1]};

        while (!Bits.isEmpty(currentPawns)) {
            int pawnSquare = Bits.next(currentPawns);

            // 获取该兵的攻击位置
            long[] pawnAttacks = ChineseAttacks.getPawnAttacks(pawnSquare, !white);

            // 检查目标位置是否在攻击范围内
            if (Bits.testBit(pawnAttacks, square)) {
                return true;
            }

            // 移除已检查的兵
            currentPawns = Bits.clearBit(currentPawns, pawnSquare);
        }

        return false;
    }

    /**
     * 检查是否被对手车攻击
     */
    private boolean isAttackedByRook(Board board, int square, long[] occupied, boolean white) {
        long[] opponentRooks = board.getRooks(!white);
        if (Bits.isEmpty(opponentRooks)) {
            return false;
        }

        // 获取车的攻击位置
        long[] rookAttacks = ChineseAttacks.getRookAttacks(square, occupied);
        return !Bits.isEmpty(Bits.and(rookAttacks, opponentRooks));
    }

    /**
     * 检查是否被对手马攻击
     */
    private boolean isAttackedByHorse(Board board, int square, long[] occupied, boolean white) {
        long[] opponentHorses = board.getKnights(!white); // 在中国象棋中，马对应Knight
        if (Bits.isEmpty(opponentHorses)) {
            return false;
        }

        // 获取马的攻击位置（考虑马腿限制）
        long[] horseAttacks = ChineseAttacks.getHorseAttacks(square, occupied);
        return !Bits.isEmpty(Bits.and(horseAttacks, opponentHorses));
    }

    /**
     * 检查是否被对手炮攻击
     */
    private boolean isAttackedByCannon(Board board, int square, long[] occupied, boolean white) {
        long[] opponentCannons = board.getCannons(!white);
        if (Bits.isEmpty(opponentCannons)) {
            return false;
        }

        // 获取炮的攻击位置（隔子攻击）
        long[] cannonAttacks = ChineseAttacks.getCannonAttacks(square, occupied);
        return !Bits.isEmpty(Bits.and(cannonAttacks, opponentCannons));
    }

    /**
     * 检查是否被对手相(象)攻击
     */
    private boolean isAttackedByElephant(Board board, int square, boolean white) {
        long[] opponentElephants = board.getBishops(!white); // 在中国象棋中，相对应Bishop
        if (Bits.isEmpty(opponentElephants)) {
            return false;
        }

        // 获取相的攻击位置（田字走法，不过河，有相眼限制）
        long[] elephantAttacks = ChineseAttacks.getElephantAttacks(square);
        return !Bits.isEmpty(Bits.and(elephantAttacks, opponentElephants));
    }

    /**
     * 检查是否被对手士攻击
     */
    private boolean isAttackedByAdvisor(Board board, int square, boolean white) {
        long[] opponentAdvisors = board.getAdvisors(!white);
        if (Bits.isEmpty(opponentAdvisors)) {
            return false;
        }

        // 获取士的攻击位置（九宫内斜走）
        long[] advisorAttacks = ChineseAttacks.getAdvisorAttacks(square);
        return !Bits.isEmpty(Bits.and(advisorAttacks, opponentAdvisors));
    }

    /**
     * 检查是否被对手将(帅)攻击
     */
    private boolean isAttackedByKing(Board board, int square, boolean white) {
        long[] opponentKing = board.getKing(!white);
        if (Bits.isEmpty(opponentKing)) {
            return false;
        }

        // 获取将的攻击位置（九宫内直走）
        long[] kingAttacks = ChineseAttacks.getKingAttacks(square);

        // 还要检查"白脸将"规则（将帅不能照面）
        if (!Bits.isEmpty(Bits.and(kingAttacks, opponentKing))) {
            return true;
        }

        // 检查将帅照面
        return isKingFaceToFace(board, square, white);
    }

    /**
     * 检查将帅照面规则
     */
    private boolean isKingFaceToFace(Board board, int square, boolean white) {
        long[] ourKing = board.getKing(white);
        long[] opponentKing = board.getKing(!white);

        if (Bits.isEmpty(ourKing) || Bits.isEmpty(opponentKing)) {
            return false;
        }

        int ourKingSquare = Bits.next(ourKing);
        int opponentKingSquare = Bits.next(opponentKing);

        // 检查是否在同一列
        if (ChineseAttacks.file(ourKingSquare) == ChineseAttacks.file(opponentKingSquare)) {
            // 检查两将之间是否没有其他棋子
            long[] occupied = board.getOccupied();
            long[] betweenMask = getBetweenMask(ourKingSquare, opponentKingSquare);

            // 如果检查的位置在两将之间，且移除该位置后两将照面，则该位置被攻击
            if (ChineseAttacks.file(square) == ChineseAttacks.file(ourKingSquare)) {
                long[] occupiedWithoutSquare = Bits.clearBit(occupied, square);
                return Bits.isEmpty(Bits.and(betweenMask, occupiedWithoutSquare));
            }
        }

        return false;
    }

    /**
     * 获取两个位置之间的掩码
     */
    private long[] getBetweenMask(int square1, int square2) {
        long[] mask = Bits.emptyBitBoard();

        int file1 = ChineseAttacks.file(square1);
        int file2 = ChineseAttacks.file(square2);
        int rank1 = ChineseAttacks.rank(square1);
        int rank2 = ChineseAttacks.rank(square2);

        if (file1 == file2) {
            // 同一列
            int minRank = Math.min(rank1, rank2);
            int maxRank = Math.max(rank1, rank2);

            for (int rank = minRank + 1; rank < maxRank; rank++) {
                int square = ChineseAttacks.square(rank, file1);
                mask = Bits.setBit(mask, square);
            }
        }

        return mask;
    }

    /**
     * 检查是否应该生成该移动（基于移动过滤器）
     */
    private boolean shouldGenerateMove(boolean isCapture) {
        return switch (filter) {
            case ALL -> true;
            case CAPTURES -> isCapture;
            case QUIET -> !isCapture;
            default -> true;
        };
    }


    /**
     * 移动过滤器枚举类，用于筛选不同类型的棋子移动 包含以下几种移动类型: ALL: 生成所有可能的移动，包括吃子和非吃子移动 QUIET: 只生成安静移动，即不包含吃子的移动 CAPTURES: 只生成吃子移动 EVASIONS: 生成用于躲避将军的移动，当己方王被攻击时使用 LEGAL:
     * 生成完全合法的移动，确保不会导致己方王被吃
     * <p>
     * 该枚举类主要用于: 1. 移动生成器中筛选特定类型的移动 2. 优化搜索算法，通过只生成特定类型的移动来提高效率 3. 在不同的棋局阶段使用不同的移动过滤策略
     */
    public enum MoveFilter {
        ALL,            // 生成伪合法移动
        QUIET,          // 生成安静移动(不吃子)
        CAPTURES,       // 生成吃子移动
        EVASIONS,      // 生成躲避将军的移动
        LEGAL          // 生成完全合法移动
    }
}
