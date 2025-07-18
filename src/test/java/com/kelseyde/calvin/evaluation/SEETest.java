//package com.kelseyde.calvin.evaluation;
//
//import com.kelseyde.calvin.board.Board;
//import com.kelseyde.calvin.board.Move;
//import com.kelseyde.calvin.engine.EngineConfig;
//import com.kelseyde.calvin.movegen.MoveGenerator;
//import com.kelseyde.calvin.search.SEE;
//import com.kelseyde.calvin.utils.TestUtils;
//import org.junit.jupiter.api.Assertions;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.List;
//
//public class SEETest {
//
//    private static final MoveGenerator MOVEGEN = new MoveGenerator();
//
//    private static int passed = 0;
//
//    public static void testSeeSuite() throws IOException {
//
//        EngineConfig config = TestUtils.CONFIG;
//        int[] initialValues = config.seeValues();
//        config.setSeeValues(new int[] {100, 300, 300, 500, 900, 0});
//
//        Path path = Path.of("src/test/resources/see_suite.epd");
//        List<String> lines = Files.readAllLines(path);
//
//        passed = 0;
//        lines.forEach(line -> runTest(config, line));
//        if (passed != lines.size()) {
//            Assertions.fail("Passed " + passed + "/" + lines.size());
//        }
//
//        config.setSeeValues(initialValues);
//
//    }
//
//    private static void runTest(EngineConfig config, String line) {
//        String[] parts = line.split("\\|");
//        String fen = parts[0].trim();
//        Board board = Board.from(fen);
//        Move move = legalMove(board, Move.fromUCI(parts[1].trim()));
//        int threshold = Integer.parseInt(parts[2].trim());
//        if (SEE.see(config, board, move, threshold)
//                && !SEE.see(config, board, move, threshold + 1)) {
//            passed++;
//        } else {
//            int actualThreshold = findThreshold(config, board, move);
//            System.out.println("Failed: " + line + ", target: " + threshold + ", actual: " + actualThreshold);
//        }
//    }
//
//    private static Move legalMove(Board board, Move uciMove) {
//        return MOVEGEN.generateMoves(board).stream()
//                .filter(move -> move.matches(uciMove))
//                .findAny()
//                .orElseThrow();
//    }
//
//    private static int findThreshold(EngineConfig config, Board board, Move move) {
//        for (int i = 5000; i > -5000; i-= 10) {
//            if (SEE.see(config, board, move, i)) {
//                return i;
//            }
//        }
//        return Integer.MIN_VALUE;
//    }
//
//    public static void main(String[] args) throws IOException {
//        testSeeSuite();
//    }
//
//}