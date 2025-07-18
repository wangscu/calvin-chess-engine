package com.kelseyde.calvin.search.ordering;

import com.kelseyde.calvin.board.Board;
import com.kelseyde.calvin.board.Move;
import com.kelseyde.calvin.board.Piece;
import com.kelseyde.calvin.engine.EngineConfig;
import com.kelseyde.calvin.search.SearchHistory;
import com.kelseyde.calvin.search.SearchStack;
import com.kelseyde.calvin.search.ordering.MovePicker.MoveType;
import com.kelseyde.calvin.search.ordering.MovePicker.Stage;
import com.kelseyde.calvin.search.SearchStack.SearchStackEntry;

/**
 * Assigns a score to a {@link Move} to determine the order in which moves are tried during search. The score is based on several
 * heuristics, and those heuristics differ depending on whether the move is a noisy move - such as a capture, check, or
 * promotion - or else a quieter, positional move.
 */
public class MoveScorer {

    private final EngineConfig config;
    private final SearchHistory history;
    private final SearchStack ss;
    private final int seeNoisyDivisor;
    private final int seeNoisyOffset;
    private Stage stage;

    public MoveScorer(EngineConfig config,
                      SearchHistory history,
                      SearchStack ss,
                      int seeNoisyDivisor,
                      int seeNoisyOffset) {
        this.config = config;
        this.history = history;
        this.ss = ss;
        this.seeNoisyDivisor = seeNoisyDivisor;
        this.seeNoisyOffset = seeNoisyOffset;
    }

    // Assign a move a score and type. The scoring heuristics used depend on the type of move, with different
    // heuristics for quiets, captures and promotions.
    public ScoredMove score(Board board, Move move, int ply, Stage stage) {

        this.stage = stage;
        Piece piece = board.pieceAt(move.from());
        Piece captured = board.captured(move);
        boolean capture = captured != null;
        boolean promotion = move.isPromotion();

        if (capture)
            return scoreCapture(board, move, piece, captured);
        else
            return scoreQuiet(board, move, piece, ply);

    }

    // Captures are scored based on the value of the captured piece (MVV, Most Valuable Victim), and their score in the
    // capture history table. They are separated into 'good' and 'bad' noisies based on whether they pass a SEE threshold
    // that is determined by their MVV + capthist score.
    private ScoredMove scoreCapture(Board board, Move move, Piece piece, Piece captured) {

        int historyScore = history.captureHistory().get(piece, move.to(), captured, board.isWhite());
        //int score = SEE.value(config, captured) + historyScore / 4;
        int score = historyScore / 4;
        int threshold = -score / seeNoisyDivisor + seeNoisyOffset;
        //MoveType type = SEE.see(config, board, move, threshold) ? MoveType.GOOD_NOISY : MoveType.BAD_NOISY;
        MoveType type = MoveType.GOOD_NOISY;
        return new ScoredMove(move, piece, captured, score, historyScore, type);

    }

    // Quiets are scored based on their score in the quiet and continuation history tables. They are separated into 'good'
    // and 'bad' quiets based on whether their history score exceeds a configurable threshold - except for quiet checks
    // that are generated during the noisy stage, which are considered 'bad noisies' regardless of score.
    private ScoredMove scoreQuiet(Board board, Move move, Piece piece, int ply) {

        boolean white = board.isWhite();
        int historyScore = history.quietHistory().get(move, piece, white);
        int contHistScore = history.continuationHistory().get(move, piece, white, ply, config.contHistPlies(), ss);
        int score = historyScore + contHistScore;
        MoveType type = stage == Stage.GEN_NOISY
                ? MoveType.BAD_NOISY
                : (score >= config.goodQuietThreshold() ? MoveType.GOOD_QUIET : MoveType.BAD_QUIET);
        return new ScoredMove(move, piece, null, score, score, type);

    }

}
