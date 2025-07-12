package com.kelseyde.calvin.movegen;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.kelseyde.calvin.board.Board;
import com.kelseyde.calvin.board.Move;
import com.kelseyde.calvin.evaluation.NNUE;
import com.kelseyde.calvin.utils.notation.FEN;

/**
 * @author : wangyajun10
 * @version V1.0
 * @Description: com.kelseyde.calvin.movegen.MoveGeneratorTest
 * @date: 2025-07-12 下午11:58
 */
public class MoveGeneratorTest {

    @Test
    public void testMoveList() {
        ChineseAttacks.init();
        Board board = FEN.startpos().toBoard();
        List<Move> moves = new MoveGenerator().generateMoves(board);
        Assertions.assertNotNull(moves);
        for (Move move : moves) {
            //System.out.println(Move.toUCI(move));
            Board bb = FEN.startpos().toBoard();
            bb.makeMove(move);
            //bb.print();
        }
        String movesStr = moves.stream().filter(m -> m != null).map(Move::toUCI).collect(Collectors.joining(" "));
        Assertions.assertEquals("a3a4 c3c4 e3e4 g3g4 i3i4 b2i2 b2b5 h2a2 h2h6 a0a1 i0i1 i0i2 h0g2 h0i2 d0e1 f0e1 c0a2 c0e2 g0e2 g0i2 e0e1", movesStr);
    }

}
