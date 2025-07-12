package com.kelseyde.calvin.movegen;

import com.kelseyde.calvin.board.Bits;
import com.kelseyde.calvin.board.Bits.Square;

/**
 * 中国象棋Magic Bitboards实现 使用long[2]表示90格棋盘，支持车、马、炮、兵、相、士、将
 */
public class ChineseAttacks {

    // Magic数据结构
    public static class MagicEntry {

        long[] mask;      // 相关位掩码
        long magic0, magic1;  // magic数字（两个long）
        long[][] attacks; // 攻击表
        int shift;        // 右移位数

        public MagicEntry(long[] mask, long magic0, long magic1, int shift) {
            this.mask = mask;
            this.magic0 = magic0;
            this.magic1 = magic1;
            this.shift = shift;
            this.attacks = new long[1 << (Bits.popCount(mask))][];
        }

        public int getIndex(long[] occupied) {
            long[] masked = Bits.and(occupied, mask);
            long hash = (masked[0] * magic0) ^ (masked[1] * magic1);
            return (int) (hash >>> shift);
        }
    }

    // 各棋子的Magic表
    private static MagicEntry[][] rookMagics = new MagicEntry[Square.COUNT][];
    private static MagicEntry[][] cannonMagics = new MagicEntry[Square.COUNT][];
    private static MagicEntry[][] horseMagics = new MagicEntry[Square.COUNT][];
    private static long[][][] pawnAttacks = new long[2][Square.COUNT][]; // [color][square]
    private static long[][] elephantAttacks = new long[Square.COUNT][];
    private static long[][] advisorAttacks = new long[Square.COUNT][];
    private static long[][] kingAttacks = new long[Square.COUNT][];

    // 位置工具方法
    public static int rank(int square) {
        return Bits.Rank.of(square);
    }

    public static int file(int square) {
        return Bits.File.of(square);
    }

    public static int square(int rank, int file) {
        return Bits.Square.of(rank, file);
    }

    public static boolean inBoard(int rank, int file) {
        return rank >= 0 && rank < Square.RANK_COUNT && file >= 0 && file < Square.FILE_COUNT;
    }

    public static boolean inPalace(int square) {
        int rank = rank(square), file = file(square);
        return file >= 3 && file <= 5 && (rank <= 2 || rank >= 7);
    }

    public static boolean crossRiver(int square, boolean red) {
        return red ? rank(square) > 4 : rank(square) < 5;
    }

    // 初始化所有Magic表
    public static void init() {
        initRookMagics();
        initCannonMagics();
        initHorseMagics();
        initPawnAttacks();
        initElephantAttacks();
        initAdvisorAttacks();
        initKingAttacks();
    }

    // 车的Magic初始化
    private static void initRookMagics() {
        rookMagics = new MagicEntry[Square.COUNT][];

        for (int square = 0; square < Square.COUNT; square++) {
            rookMagics[square] = new MagicEntry[1];

            // 计算相关位掩码（横竖线，去除边界）
            long[] mask = generateRookMask(square);

            // 生成magic数字（简化实现，实际应该搜索最优magic）
            long magic0 = 0x0080001020400080L * (square + 1);
            long magic1 = 0x0040080100200040L * (square + 1);
            int shift = 64 - Bits.popCount(mask);

            MagicEntry entry = new MagicEntry(mask, magic0, magic1, shift);

            // 填充攻击表
            int subsets = 1 << Bits.popCount(mask);
            // 遍历所有可能的子集
            for (int i = 0; i < subsets; i++) {
                // 根据掩码和索引生成当前子集对应的占位状态
                long[] occupied = generateSubset(mask, i);
                // 计算在当前占位状态下车能攻击到的所有位置
                long[] attacks = generateRookAttacks(square, occupied);
                // 通过magic映射计算攻击表的索引
                int index = entry.getIndex(occupied);
                // 将攻击位置存储到攻击表中
                entry.attacks[index] = attacks;
            }

            rookMagics[square][0] = entry;
        }
    }

    // 炮的Magic初始化
    private static void initCannonMagics() {
        cannonMagics = new MagicEntry[Square.COUNT][];

        for (int square = 0; square < Square.COUNT; square++) {
            cannonMagics[square] = new MagicEntry[1];

            long[] mask = generateRookMask(square); // 炮的移动掩码与车相同
            long magic0 = 0x0100080402001000L * (square + 1);
            long magic1 = 0x0080040201008000L * (square + 1);
            int shift = 64 - Bits.popCount(mask);

            MagicEntry entry = new MagicEntry(mask, magic0, magic1, shift);

            int subsets = 1 << Bits.popCount(mask);
            for (int i = 0; i < subsets; i++) {
                long[] occupied = generateSubset(mask, i);
                long[] attacks = generateCannonAttacks(square, occupied);
                int index = entry.getIndex(occupied);
                entry.attacks[index] = attacks;
            }

            cannonMagics[square][0] = entry;
        }
    }

    // 马的Magic初始化
    private static void initHorseMagics() {
        horseMagics = new MagicEntry[Square.COUNT][];

        for (int square = 0; square < Square.COUNT; square++) {
            horseMagics[square] = new MagicEntry[1];

            long[] mask = generateHorseMask(square);
            long magic0 = 0x0200100080402010L * (square + 1);
            long magic1 = 0x0100080040201008L * (square + 1);
            int shift = 64 - Bits.popCount(mask);

            MagicEntry entry = new MagicEntry(mask, magic0, magic1, shift);

            int subsets = 1 << Bits.popCount(mask);
            for (int i = 0; i < subsets; i++) {
                long[] occupied = generateSubset(mask, i);
                long[] attacks = generateHorseAttacks(square, occupied);
                int index = entry.getIndex(occupied);
                entry.attacks[index] = attacks;
            }

            horseMagics[square][0] = entry;
        }
    }

    // 兵的攻击初始化
    private static void initPawnAttacks() {
        pawnAttacks = new long[2][Square.COUNT][];

        for (int square = 0; square < Square.COUNT; square++) {
            // 红兵（向上移动）
            pawnAttacks[0][square] = generatePawnAttacks(square, true);
            // 黑兵（向下移动）
            pawnAttacks[1][square] = generatePawnAttacks(square, false);
        }
    }

    // 相的攻击初始化
    private static void initElephantAttacks() {
        elephantAttacks = new long[Square.COUNT][];

        for (int square = 0; square < Square.COUNT; square++) {
            elephantAttacks[square] = generateElephantAttacks(square);
        }
    }

    // 士的攻击初始化
    private static void initAdvisorAttacks() {
        advisorAttacks = new long[Square.COUNT][];

        for (int square = 0; square < Square.COUNT; square++) {
            advisorAttacks[square] = generateAdvisorAttacks(square);
        }
    }

    // 将的攻击初始化
    private static void initKingAttacks() {
        kingAttacks = new long[Square.COUNT][];

        for (int square = 0; square < Square.COUNT; square++) {
            kingAttacks[square] = generateKingAttacks(square);
        }
    }

    // 生成车的掩码
    private static long[] generateRookMask(int square) {
        long[] mask = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        // 横向掩码（去除边界）
        for (int f = 1; f < Square.FILE_COUNT - 1; f++) {
            if (f != file) {
                mask = Bits.or(mask, Bits.of(square(rank, f)));
            }
        }

        // 纵向掩码（去除边界）
        for (int r = 1; r < Square.RANK_COUNT - 1; r++) {
            if (r != rank) {
                mask = Bits.or(mask, Bits.of(square(r, file)));
            }
        }

        return mask;
    }

    // 生成马的掩码（马腿位置）
    private static long[] generateHorseMask(int square) {
        long[] mask = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        // 马腿位置
        int[][] legs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] leg : legs) {
            int newRank = rank + leg[0];
            int newFile = file + leg[1];
            if (inBoard(newRank, newFile)) {
                mask = Bits.or(mask, Bits.of(square(newRank, newFile)));
            }
        }

        return mask;
    }

    // 生成子集

    /**
     * 根据掩码和索引生成对应的子集
     *
     * @param mask 原始掩码，表示可能的位置集合
     * @param index 子集的索引值，用于确定哪些位需要被设置
     * @return 生成的子集，包含了根据index选择的位置
     */
    private static long[] generateSubset(long[] mask, int index) {
        // 初始化一个空的子集
        long[] subset = Bits.emptyBitBoard();
        // 用于追踪当前处理的位数
        int bit = 0;

        // 遍历所有可能的方格位置
        for (int square = 0; square < Square.COUNT; square++) {
            // 检查当前位置是否在掩码中被设置
            if (Bits.testBit(mask, square)) {
                // 根据index的对应位决定是否将该位置加入子集
                if ((index & (1 << bit)) != 0) {
                    subset = Bits.setBit(subset, square);
                }
                // 移动到下一个位
                bit++;
            }
        }

        return subset;
    }

    // 生成车的攻击

    /**
     * 生成车的攻击位置 车可以在横向和纵向移动任意格数，直到遇到棋子或棋盘边界
     *
     * @param square 当前车的位置
     * @param occupied 棋盘上所有棋子的位置
     * @return 所有可能的攻击位置
     */
    private static long[] generateRookAttacks(int square, long[] occupied) {
        // 初始化攻击位置为空
        long[] attacks = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        // 定义四个方向:上、下、左、右
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        // 遍历四个方向
        for (int[] dir : directions) {
            // 在当前方向上遍历棋盘
            for (int i = 1; i < Math.max(Square.FILE_COUNT, Square.RANK_COUNT); i++) {
                int newRank = rank + dir[0] * i;
                int newFile = file + dir[1] * i;

                // 如果超出棋盘边界则停止当前方向的遍历
                if (!inBoard(newRank, newFile)) {
                    break;
                }

                int targetSquare = square(newRank, newFile);
                // 将目标位置添加到攻击位置集合中
                attacks = Bits.setBit(attacks, targetSquare);

                // 如果遇到棋子，停止当前方向的遍历
                if (Bits.testBit(occupied, targetSquare)) {
                    break;
                }
            }
        }

        return attacks;
    }

    // 生成炮的攻击

    /**
     * 生成炮的攻击位置 炮的移动规则：需要越过一个棋子才能攻击目标位置
     *
     * @param square 当前炮的位置
     * @param occupied 棋盘上所有棋子的位置
     * @return 所有可能的攻击位置
     */
    private static long[] generateCannonAttacks(int square, long[] occupied) {
        // 初始化攻击位置为空
        long[] attacks = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        // 定义四个方向:上、下、左、右
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        // 遍历四个方向
        for (int[] dir : directions) {
            // 标记是否已经找到第一个棋子(炮架)
            boolean foundFirst = false;

            // 在当前方向上遍历棋盘
            for (int i = 1; i < Math.max(Square.FILE_COUNT, Square.RANK_COUNT); i++) {
                int newRank = rank + dir[0] * i;
                int newFile = file + dir[1] * i;

                // 如果超出棋盘边界则停止当前方向的遍历
                if (!inBoard(newRank, newFile)) {
                    break;
                }

                int targetSquare = square(newRank, newFile);

                // 寻找炮架:第一个遇到的棋子
                if (!foundFirst) {
                    if (Bits.testBit(occupied, targetSquare)) {
                        foundFirst = true;
                    }
                } else {
                    // 找到炮架后,后续的空位都是可以攻击的位置
                    attacks = Bits.setBit(attacks, targetSquare);
                    // 如果遇到第二个棋子,则停止当前方向的遍历
                    if (Bits.testBit(occupied, targetSquare)) {
                        break;
                    }
                }
            }
        }

        return attacks;
    }

    /**
     * 生成马的攻击位置 马走日字，但需要检查马腿是否被阻挡
     *
     * @param square 当前马的位置
     * @param occupied 棋盘上所有棋子的位置
     * @return 所有可能的攻击位置
     */
    private static long[] generateHorseAttacks(int square, long[] occupied) {
        // 初始化攻击位置为空
        long[] attacks = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        // 马的8个可能位置和对应的马腿位置
        // 每个数组包含4个值: [目标位置的行位移, 目标位置的列位移, 马腿的行位移, 马腿的列位移]
        int[][] moves = {
                {2, 1, 1, 0}, {2, -1, 1, 0}, {-2, 1, -1, 0}, {-2, -1, -1, 0},  // 上下方向的日字移动
                {1, 2, 0, 1}, {-1, 2, 0, 1}, {1, -2, 0, -1}, {-1, -2, 0, -1}   // 左右方向的日字移动
        };

        // 遍历所有可能的移动位置
        for (int[] move : moves) {
            int newRank = rank + move[0];    // 计算目标位置的行
            int newFile = file + move[1];    // 计算目标位置的列
            int legRank = rank + move[2];    // 计算马腿位置的行
            int legFile = file + move[3];    // 计算马腿位置的列

            // 检查目标位置和马腿位置是否在棋盘内
            if (inBoard(newRank, newFile) && inBoard(legRank, legFile)) {
                // 只有当马腿位置没有棋子时，该移动才是有效的
                if (!Bits.testBit(occupied, square(legRank, legFile))) {
                    attacks = Bits.setBit(attacks, square(newRank, newFile));
                }
            }
        }

        return attacks;
    }

    // 生成兵的攻击
    private static long[] generatePawnAttacks(int square, boolean red) {
        long[] attacks = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        if (red) {
            // 红兵向上移动
            if (rank < Square.RANK_COUNT - 1) {
                attacks = Bits.setBit(attacks, square(rank + 1, file));
            }
            // 过河后可以左右移动
            if (crossRiver(square, red)) {
                if (file > 0) {
                    attacks = Bits.setBit(attacks, square(rank, file - 1));
                }
                if (file < Square.FILE_COUNT - 1) {
                    attacks = Bits.setBit(attacks, square(rank, file + 1));
                }
            }
        } else {
            // 黑兵向下移动
            if (rank > 0) {
                attacks = Bits.setBit(attacks, square(rank - 1, file));
            }
            // 过河后可以左右移动
            if (crossRiver(square, red)) {
                if (file > 0) {
                    attacks = Bits.setBit(attacks, square(rank, file - 1));
                }
                if (file < Square.FILE_COUNT - 1) {
                    attacks = Bits.setBit(attacks, square(rank, file + 1));
                }
            }
        }

        return attacks;
    }

    // 生成相的攻击
    private static long[] generateElephantAttacks(int square) {
        long[] attacks = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        // 相的4个田字位置
        int[][] moves = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};

        for (int[] move : moves) {
            int newRank = rank + move[0];
            int newFile = file + move[1];

            // 检查是否在己方半边且在棋盘内
            if (inBoard(newRank, newFile) && ((rank <= 4 && newRank <= 4) || (rank >= 5 && newRank >= 5))) {
                attacks = Bits.setBit(attacks, square(newRank, newFile));
            }
        }

        return attacks;
    }

    // 生成士的攻击
    private static long[] generateAdvisorAttacks(int square) {
        long[] attacks = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        // 士的4个斜向位置
        int[][] moves = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        for (int[] move : moves) {
            int newRank = rank + move[0];
            int newFile = file + move[1];
            int newSquare = square(newRank, newFile);

            if (inBoard(newRank, newFile) && inPalace(newSquare)) {
                attacks = Bits.setBit(attacks, newSquare);
            }
        }

        return attacks;
    }

    // 生成将的攻击
    private static long[] generateKingAttacks(int square) {
        long[] attacks = Bits.emptyBitBoard();
        int rank = rank(square);
        int file = file(square);

        // 将的4个正交位置
        int[][] moves = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] move : moves) {
            int newRank = rank + move[0];
            int newFile = file + move[1];
            int newSquare = square(newRank, newFile);

            if (inBoard(newRank, newFile) && inPalace(newSquare)) {
                attacks = Bits.setBit(attacks, newSquare);
            }
        }

        return attacks;
    }

    // 公共查询接口

    /**
     * 获取车在当前位置的攻击范围
     *
     * @param square 车所在的格子位置
     * @param occupied 当前棋盘的占用情况(用位图表示)
     * @return 返回车可以攻击到的所有格子的位图
     */
    public static long[] getRookAttacks(int square, long[] occupied) {
        return rookMagics[square][0].attacks[rookMagics[square][0].getIndex(occupied)];
    }

    public static long[] getCannonAttacks(int square, long[] occupied) {
        return cannonMagics[square][0].attacks[cannonMagics[square][0].getIndex(occupied)];
    }

    /**
     * 获取马在当前位置的攻击范围
     *
     * @param square 马所在的格子位置
     * @param occupied 当前棋盘的占用情况(用位图表示)
     * @return 返回马可以攻击到的所有格子的位图
     */
    public static long[] getHorseAttacks(int square, long[] occupied) {
        return horseMagics[square][0].attacks[horseMagics[square][0].getIndex(occupied)];
    }

    /**
     * 获取兵/卒在当前位置的攻击范围
     *
     * @param square 兵/卒所在的格子位置
     * @param red 是否为红方棋子(true表示红方,false表示黑方)
     * @return 返回兵/卒可以攻击到的所有格子的位图
     */
    public static long[] getPawnAttacks(int square, boolean red) {
        return pawnAttacks[red ? 0 : 1][square];
    }

    public static long[] getElephantAttacks(int square) {
        return elephantAttacks[square];
    }

    public static long[] getAdvisorAttacks(int square) {
        return advisorAttacks[square];
    }

    public static long[] getKingAttacks(int square) {
        return kingAttacks[square];
    }
}