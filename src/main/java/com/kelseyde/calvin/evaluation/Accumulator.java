package com.kelseyde.calvin.evaluation;

import com.kelseyde.calvin.board.Colour;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * The accumulator keeps track of the activations of the hidden layer of the neural network. It is incrementally updated
 * during search to avoid recomputing the entire network each time evaluation is called. The activations are accumulated
 * from both white's and black's perspective, so that during evaluation the 'side to move' and 'not side to move' can be
 * easily flipped.
 * </p>
 * The Java Vector API is used to give the accumulator updates a performance boost via SIMD instructions.
 */
public class Accumulator {

    private static final int HIDDEN_SIZE = NNUE.NETWORK.hiddenSize();
    private static final short[] BIASES = NNUE.NETWORK.inputBiases();
    private static final VectorSpecies<Short> SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final int LOOP_LENGTH = SPECIES.loopBound(HIDDEN_SIZE);

    public short[] whiteFeatures;
    public short[] blackFeatures;
    public final boolean[] mirrored;

    public Accumulator(int featureCount) {
        this.whiteFeatures = new short[featureCount];
        this.blackFeatures = new short[featureCount];
        this.mirrored = new boolean[2];
    }

    public void add(short[] weights, Feature feature, boolean whitePerspective) {
        // Add a single feature to the accumulator.
        boolean mirror = mirrored[Colour.index(whitePerspective)];
        int offset = feature.index(whitePerspective, mirror) * HIDDEN_SIZE;
        short[] features = whitePerspective ? whiteFeatures : blackFeatures;

        for (int i = 0; i < LOOP_LENGTH; i += SPECIES.length()) {

            ShortVector.fromArray(SPECIES, features, i)
                    .add(ShortVector.fromArray(SPECIES, weights, i + offset))
                    .intoArray(features, i);

        }
    }

    public void sub(short[] weights, Feature feature, boolean whitePerspective) {
        // Subtract a single feature from the accumulator.
        boolean mirror = mirrored[Colour.index(whitePerspective)];
        int offset = feature.index(whitePerspective, mirror) * HIDDEN_SIZE;
        short[] features = whitePerspective ? whiteFeatures : blackFeatures;

        for (int i = 0; i < LOOP_LENGTH; i += SPECIES.length()) {

            ShortVector.fromArray(SPECIES, features, i)
                    .sub(ShortVector.fromArray(SPECIES, weights, i + offset))
                    .intoArray(features, i);

        }
    }

    public void addAddAddAdd(short[] weights, Feature feat1, Feature feat2, Feature feat3, Feature feat4, boolean whitePerspective) {
        // Add a quartet of features to the accumulator.
        boolean mirror = mirrored[Colour.index(whitePerspective)];
        int offset1 = feat1.index(whitePerspective, mirror) * HIDDEN_SIZE;
        int offset2 = feat2.index(whitePerspective, mirror) * HIDDEN_SIZE;
        int offset3 = feat3.index(whitePerspective, mirror) * HIDDEN_SIZE;
        int offset4 = feat4.index(whitePerspective, mirror) * HIDDEN_SIZE;
        short[] features = whitePerspective ? whiteFeatures : blackFeatures;

        for (int i = 0; i < LOOP_LENGTH; i += SPECIES.length()) {

            ShortVector.fromArray(SPECIES, features, i)
                    .add(ShortVector.fromArray(SPECIES, weights, i + offset1))
                    .add(ShortVector.fromArray(SPECIES, weights, i + offset2))
                    .add(ShortVector.fromArray(SPECIES, weights, i + offset3))
                    .add(ShortVector.fromArray(SPECIES, weights, i + offset4))
                    .intoArray(features, i);

        }
    }

    public void subSubSubSub(short[] weights, Feature feat1, Feature feat2, Feature feat3, Feature feat4, boolean whitePerspective) {
        // Subtract a quartet of features from the accumulator.
        boolean mirror = mirrored[Colour.index(whitePerspective)];
        int offset1 = feat1.index(whitePerspective, mirror) * HIDDEN_SIZE;
        int offset2 = feat2.index(whitePerspective, mirror) * HIDDEN_SIZE;
        int offset3 = feat3.index(whitePerspective, mirror) * HIDDEN_SIZE;
        int offset4 = feat4.index(whitePerspective, mirror) * HIDDEN_SIZE;
        short[] features = whitePerspective ? whiteFeatures : blackFeatures;

        for (int i = 0; i < LOOP_LENGTH; i += SPECIES.length()) {

            ShortVector.fromArray(SPECIES, features, i)
                    .sub(ShortVector.fromArray(SPECIES, weights, i + offset1))
                    .sub(ShortVector.fromArray(SPECIES, weights, i + offset2))
                    .sub(ShortVector.fromArray(SPECIES, weights, i + offset3))
                    .sub(ShortVector.fromArray(SPECIES, weights, i + offset4))
                    .intoArray(features, i);

        }
    }

    public void apply(Accumulator prev, AccumulatorUpdate update, short[] whiteWeights, short[] blackWeights) {
        // Accumulator updates are 'fused' together, so that multiple feature updates can be applied in a single pass.
        switch (update.getUpdateType()) {
            case ADD -> add(prev, update, whiteWeights, blackWeights);
            case ADD_SUB -> addSub(prev, update, whiteWeights, blackWeights);
            case ADD_SUB_SUB -> addSubSub(prev, update, whiteWeights, blackWeights);
            case ADD_ADD_SUB_SUB -> addAddSubSub(prev, update, whiteWeights, blackWeights);
        }
    }

    public void add(Accumulator prev, AccumulatorUpdate update, short[] whiteWeights, short[] blackWeights) {

        Feature add1 = update.adds[0];

        boolean whiteMirror = mirrored[Colour.WHITE];
        boolean blackMirror = mirrored[Colour.BLACK];

        int wOffset = add1.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset = add1.index(false, blackMirror) * HIDDEN_SIZE;

        for (int i = 0; i < LOOP_LENGTH; i += SPECIES.length()) {

            ShortVector.fromArray(SPECIES, prev.whiteFeatures, i)
                    .add(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset))
                    .intoArray(whiteFeatures, i);

            ShortVector.fromArray(SPECIES, prev.blackFeatures, i)
                    .add(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset))
                    .intoArray(blackFeatures, i);

        }
    }

    public void addSub(Accumulator prev, AccumulatorUpdate update, short[] whiteWeights, short[] blackWeights) {

        Feature add1 = update.adds[0];
        Feature sub1 = update.subs[0];

        boolean whiteMirror = mirrored[Colour.WHITE];
        boolean blackMirror = mirrored[Colour.BLACK];

        int wOffset1 = add1.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset1 = add1.index(false, blackMirror) * HIDDEN_SIZE;
        int wOffset2 = sub1.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset2 = sub1.index(false, blackMirror) * HIDDEN_SIZE;

        for (int i = 0; i < LOOP_LENGTH; i += SPECIES.length()) {

            ShortVector.fromArray(SPECIES, prev.whiteFeatures, i)
                    .add(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset1))
                    .sub(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset2))
                    .intoArray(whiteFeatures, i);

            ShortVector.fromArray(SPECIES, prev.blackFeatures, i)
                    .add(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset1))
                    .sub(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset2))
                    .intoArray(blackFeatures, i);

        }
    }

    public void addSubSub(Accumulator prev, AccumulatorUpdate update, short[] whiteWeights, short[] blackWeights) {

        Feature add1 = update.adds[0];
        Feature sub1 = update.subs[0];
        Feature sub2 = update.subs[1];

        boolean whiteMirror = mirrored[Colour.WHITE];
        boolean blackMirror = mirrored[Colour.BLACK];

        int wOffset1 = add1.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset1 = add1.index(false, blackMirror) * HIDDEN_SIZE;
        int wOffset2 = sub1.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset2 = sub1.index(false, blackMirror) * HIDDEN_SIZE;
        int wOffset3 = sub2.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset3 = sub2.index(false, blackMirror) * HIDDEN_SIZE;

        for (int i = 0; i < LOOP_LENGTH; i += SPECIES.length()) {

            ShortVector.fromArray(SPECIES, prev.whiteFeatures, i)
                    .add(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset1))
                    .sub(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset2))
                    .sub(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset3))
                    .intoArray(whiteFeatures, i);

            ShortVector.fromArray(SPECIES, prev.blackFeatures, i)
                    .add(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset1))
                    .sub(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset2))
                    .sub(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset3))
                    .intoArray(blackFeatures, i);

        }
    }

    public void addAddSubSub(Accumulator prev, AccumulatorUpdate update, short[] whiteWeights, short[] blackWeights) {

        Feature add1 = update.adds[0];
        Feature add2 = update.adds[1];
        Feature sub1 = update.subs[0];
        Feature sub2 = update.subs[1];

        boolean whiteMirror = mirrored[Colour.WHITE];
        boolean blackMirror = mirrored[Colour.BLACK];

        int wOffset1 = add1.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset1 = add1.index(false, blackMirror) * HIDDEN_SIZE;
        int wOffset2 = add2.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset2 = add2.index(false, blackMirror) * HIDDEN_SIZE;
        int wOffset3 = sub1.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset3 = sub1.index(false, blackMirror) * HIDDEN_SIZE;
        int wOffset4 = sub2.index(true, whiteMirror) * HIDDEN_SIZE;
        int bOffset4 = sub2.index(false, blackMirror) * HIDDEN_SIZE;

        for (int i = 0; i < LOOP_LENGTH; i += SPECIES.length()) {

            ShortVector.fromArray(SPECIES, prev.whiteFeatures, i)
                    .add(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset1))
                    .add(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset2))
                    .sub(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset3))
                    .sub(ShortVector.fromArray(SPECIES, whiteWeights, i + wOffset4))
                    .intoArray(whiteFeatures, i);

            ShortVector.fromArray(SPECIES, prev.blackFeatures, i)
                    .add(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset1))
                    .add(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset2))
                    .sub(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset3))
                    .sub(ShortVector.fromArray(SPECIES, blackWeights, i + bOffset4))
                    .intoArray(blackFeatures, i);

        }
    }

    public void copyFrom(short[] features, boolean whitePerspective) {
        if (whitePerspective) {
            vectorCopy(features, whiteFeatures, features.length);
        } else {
            vectorCopy(features, blackFeatures, features.length);
        }
    }

    public void copyFrom(Accumulator accumulator) {
        vectorCopy(accumulator.whiteFeatures, whiteFeatures, whiteFeatures.length);
        vectorCopy(accumulator.blackFeatures, blackFeatures, blackFeatures.length);
        System.arraycopy(accumulator.mirrored, 0, mirrored, 0, mirrored.length);
    }

    public static void vectorCopy(short[] src, short[] dest, int length) {
        for (int i = 0; i <= length - SPECIES.length(); i += SPECIES.length()) {
            ShortVector.fromArray(SPECIES, src, i).intoArray(dest, i);
        }
    }

    public static class AccumulatorUpdate {

        public final Feature[] adds = new Feature[2];
        public final Feature[] subs = new Feature[2];

        public int addCount = 0;
        public int subCount = 0;

        public void pushAdd(Feature add) {
            adds[addCount++] = add;
        }

        public void pushSub(Feature sub) {
            subs[subCount++] = sub;
        }

        public UpdateType getUpdateType() {
            if (addCount == 1 && subCount == 0) {
                return UpdateType.ADD;
            }
            else if (addCount == 1 && subCount == 1) {
                return UpdateType.ADD_SUB;
            }
            else if (addCount == 1 && subCount == 2) {
                return UpdateType.ADD_SUB_SUB;
            }
            else if (addCount == 2 && subCount == 2) {
                return UpdateType.ADD_ADD_SUB_SUB;
            }
            else {
                throw new IllegalStateException("Unexpected update type");
            }
        }

    }

    public enum UpdateType {
        ADD,
        ADD_SUB,
        ADD_SUB_SUB,
        ADD_ADD_SUB_SUB
    }

}