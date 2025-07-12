package com.kelseyde.calvin.board;

import java.util.List;
import java.util.Map;

/**
 * 位操作工具类
 * 提供了一系列用于操作棋盘位图的方法
 */
public class Bits {

    // 中国象棋90格棋盘的总格子数
    public static final int TOTAL_SQUARES = 90;

    /**
     * 获取二进制数中最右边的1的位置
     * @param board 要检查的二进制数组
     * @return 最右边1的位置索引,如果第一个long为0则返回第二个long中1的位置+64
     */
    public static int next(long[] board) {
        return board[0] != 0 ? Long.numberOfTrailingZeros(board[0]) : 64 + Long.numberOfTrailingZeros(board[1]);
    }

    /**
     * 清除最右边的1位
     * @param board 输入的位图
     * @return 清除最右边1位后的位图
     */
    public static long pop(long board) {
        return board & (board - 1);
    }

    /**
     * 在指定位置清除1位
     * @param bb 输入的位图
     * @param sq 要清除的位置
     * @return 清除指定位置后的位图
     */
    public static long pop(long bb, int sq) {
        return bb ^ ofSingle(sq);
    }

    /**
     * 计算位图中1的个数
     */
    public static int count(long board) {
        return Long.bitCount(board);
    }

    /**
     * 在指定位置设置1
     */
    public static long ofSingle(int sq) {
        return 1L << sq;
    }

    /**
     * 检查位图指定位置是否为1
     */
    public static boolean contains(long[] bb, int sq) {
        return Bits.isEmpty(Bits.and(bb, of(sq)));
    }

    /**
     * 检查位图是否为空(全0)
     */
    public static boolean isEmpty(long[] board) {
        return board[0] == 0 && board[1] == 0;
    }


    /**
     * 位图向北移动8位(向左移位)
     */
    public static long north(long board) {
        return board << 8;
    }

    /**
     * 位图向南移动8位(向右移位)
     */
    public static long south(long board) {
        return board >>> 8;
    }

    /**
     * 位图向东移动1位,并清除A列
     */
    public static long east(long board) {
        return (board << 1) & ~File.A;
    }

    /**
     * 位图向西移动1位,并清除H列
     */
    public static long west(long board) {
        return (board >>> 1) & ~File.H;
    }

    /**
     * 位图向东北移动9位,并清除A列
     */
    public static long northEast(long board) {
        return (board << 9) & ~File.A;
    }

    /**
     * 位图向东南移动7位,并清除A列
     */
    public static long southEast(long board) {
        return (board >>> 7) & ~File.A;
    }

    /**
     * 位图向西北移动7位,并清除H列
     */
    public static long northWest(long board) {
        return (board << 7) & ~File.H;
    }

    /**
     * 位图向西南移动9位,并清除H列
     */
    public static long southWest(long board) {
        return (board >>> 9) & ~File.H;
    }

    /**
     * 两个位图数组进行与操作
     */
    public static long[] and(long[] bitboard, long[] mask) {
        return new long[]{bitboard[0] & mask[0], bitboard[1] & mask[1]};
    }

    /**
     * 两个位图数组进行或操作
     */
    public static long[] or(long[] bitboard, long[] mask) {
        return new long[]{bitboard[0] | mask[0], bitboard[1] | mask[1]};
    }

    /**
     * 创建空位图数组
     */
    public static long[] emptyBitBoard() {
        return new long[2];
    }

    /**
     * 在指定位置创建位图数组
     */
    public static long[] of(int square) {
        long[] result = new long[2];
        if (square < 64) {
            result[0] = 1L << square;
        } else {
            result[1] = 1L << (square - 64);
        }
        return result;
    }

    /**
     * 两个位图数组进行异或操作
     */
    public static long[] xor(long[] a, long[] b) {
        return new long[]{a[0] ^ b[0], a[1] ^ b[1]};
    }

    /**
     * 测试位图数组指定位置的位值
     */
    public static boolean testBit(long[] bits, int square) {
        if (square < 64) {
            return (bits[0] & (1L << square)) != 0;
        } else {
            return (bits[1] & (1L << (square - 64))) != 0;
        }
    }

    /**
     * 设置位图数组指定位置为1
     */
    public static long[] setBit(long[] bits, int square) {
        long[] result = {bits[0], bits[1]};
        if (square < 64) {
            result[0] |= 1L << square;
        } else {
            result[1] |= 1L << (square - 64);
        }
        return result;
    }

    /**
     * 计算位图数组中1的总数
     */
    public static int popCount(long[] bits) {
        return Long.bitCount(bits[0]) + Long.bitCount(bits[1]);
    }

    /**
     * 从指定位置开始查找下一个为1的位
     */
    public static int nextSetBit(long[] bits, int fromIndex) {
        if (fromIndex < 64) {
            int bit = Long.numberOfTrailingZeros(bits[0] & (-1L << fromIndex));
            if (bit < 64) return bit;
            fromIndex = 64;
        }
        if (fromIndex < TOTAL_SQUARES) {
            int bit = Long.numberOfTrailingZeros(bits[1] & (-1L << (fromIndex - 64)));
            if (bit < 26) return bit + 64;
        }
        return -1;
    }

    /**
     * 收集位图中所有为1的位置
     */
    public static int[] collect(long bb) {
        int size = count(bb);
        int[] squares = new int[size];
        for (int i = 0; i < size; ++i) {
            squares[i] = Long.numberOfTrailingZeros(bb);
            bb = pop(bb);
        }
        return squares;
    }

    /**
     * 清除指定位置的位
     * @param board 表示棋盘位置的long[2]数组
     * @param square 要清除的位置（0-89）
     * @return 新的long[2]数组，指定位置的位被清除为0
     */
    public static long[] clearBit(long[] board, int square) {
        long[] result = {board[0], board[1]};

        if (square < 64) {
            // 清除前64位中的指定位置
            result[0] &= ~(1L << square);
        } else {
            // 清除后26位中的指定位置
            result[1] &= ~(1L << (square - 64));
        }

        return result;
    }

    /**
     * 打印位图的棋盘表示
     */
    public static void print(long[] bb) {
        for (int rank = 7; rank >= 0; --rank) {
            System.out.print(" +---+---+---+---+---+---+---+---+\n");

            for (int file = 0; file < 8; ++file) {
                boolean piece = Bits.isEmpty(Bits.and(bb, Bits.of(Square.of(rank, file))));
                System.out.print(" | " + (piece ? '1' : ' '));
            }

            System.out.print(" | "  + (rank + 1) + "\n");
        }
        System.out.print(" +---+---+---+---+---+---+---+---+\n");
        System.out.print("   a   b   c   d   e   f   g   h\n\n");
    }

    /**
     * 棋盘格子工具类
     */
    public static class Square {

        public static final int COUNT = 64;
        public static final long ALL = ~0L;
        public static final long NONE = 0L;

        /**
         * 根据行列号计算格子索引
         */
        public static int of(int rank, int file) {
            return (rank << 3) + file;
        }

        /**
         * 翻转行号
         */
        public static int flipRank(int sq) {
            return sq ^ 56;
        }

        /**
         * 翻转列号
         */
        public static int flipFile(int sq) {
            return sq ^ 7;
        }

        /**
         * 检查格子索引是否有效
         */
        public static boolean isValid(int sq) {
            return sq >= 0 && sq < Square.COUNT;
        }

        /**
         * 转换为代数记号
         */
        public static String toNotation(int sq) {
            return File.toNotation(sq) + Rank.toRankNotation(sq);
        }

        /**
         * 从代数记号转换为格子索引
         */
        public static int fromNotation(String algebraic) {
            int xOffset = List.of('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h').indexOf(algebraic.charAt(0));
            int yAxis = (Integer.parseInt(Character.valueOf(algebraic.charAt(1)).toString()) - 1) * 8;
            return yAxis + xOffset;
        }
    }

    /**
     * 列操作工具类
     */
    public static class File {

        // 各列的位图表示
        public static final long A = 0b0000000100000001000000010000000100000001000000010000000100000001L;
        public static final long B = 0b0000001000000010000000100000001000000010000000100000001000000010L;
        public static final long C = 0b0000010000000100000001000000010000000100000001000000010000000100L;
        public static final long D = 0b0000100000001000000010000000100000001000000010000000100000001000L;
        public static final long E = 0b0001000000010000000100000001000000010000000100000001000000010000L;
        public static final long F = 0b0010000000100000001000000010000000100000001000000010000000100000L;
        public static final long G = 0b0100000001000000010000000100000001000000010000000100000001000000L;
        public static final long H = 0b1000000010000000100000001000000010000000100000001000000010000000L;

        // 列号到字母的映射
        public static final Map<Integer, String> FILE_CHAR_MAP = Map.of(
                0, "a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h"
        );

        /**
         * 获取格子所在列号
         */
        public static int of(int sq) {
            return sq & 7;
        }

        /**
         * 获取指定列的位图表示
         */
        public static long toBitboard(int file) {
            return 0x0101010101010101L << file;
        }

        /**
         * 获取格子的列字母表示
         */
        public static String toNotation(int sq) {
            return FILE_CHAR_MAP.get(of(sq));
        }

        /**
         * 从列字母获取列号
         */
        public static int fromNotation(char file) {
            return FILE_CHAR_MAP.entrySet().stream()
                    .filter(entry -> entry.getValue().equalsIgnoreCase(Character.toString(file)))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
        }
    }

    /**
     * 行操作工具类
     */
    public static class Rank {

        // 各行的位图表示
        public static final long FIRST  = 0b0000000000000000000000000000000000000000000000000000000011111111L;
        public static final long SECOND = 0b0000000000000000000000000000000000000000000000001111111100000000L;
        public static final long THIRD  = 0b0000000000000000000000000000000000000000111111110000000000000000L;
        public static final long FOURTH = 0b0000000000000000000000000000000011111111000000000000000000000000L;
        public static final long FIFTH  = 0b0000000000000000000000001111111100000000000000000000000000000000L;
        public static final long SIXTH  = 0b0000000000000000111111110000000000000000000000000000000000000000L;
        public static final long SEVENTH = 0b0000000011111111000000000000000000000000000000000000000000000000L;
        public static final long EIGHTH  = 0b1111111100000000000000000000000000000000000000000000000000000000L;

        // 行号到数字的映射
        public static final Map<Integer, String> RANK_CHAR_MAP = Map.of(
                0, "1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7, "8"
        );

        /**
         * 获取格子所在行号
         */
        public static int of(int sq) {
            return sq >>> 3;
        }

        /**
         * 获取格子的行数字表示
         */
        public static String toRankNotation(int sq) {
            return RANK_CHAR_MAP.get(of(sq));
        }
    }

    /**
     * 射线操作工具类
     */
    public static class Ray {

        /**
         * 计算两个格子之间的射线位图
         */
        public static long[] between(int from, int to) {
            if (!Square.isValid(from) || !Square.isValid(to) || (from == to)) {
                return Bits.emptyBitBoard();
            }
            int offset = direction(from, to);
            if (offset == 0) return Bits.emptyBitBoard();
            long[] ray = Bits.emptyBitBoard();
            int sq = from + offset;
            while (Square.isValid(sq) && sq != to) {
                ray = Bits.or(ray, Bits.of(sq)) ;
                sq += offset;
            }
            return ray;
        }

        /**
         * 确定两个格子之间的方向偏移量
         */
        private static int direction(int from, int to) {
            int startRank = Rank.of(from);
            int endRank = Rank.of(to);
            int startFile = File.of(from);
            int endFile = File.of(to);
            if (startRank == endRank) {
                return from > to ? -1 : 1;
            }
            else if (startFile == endFile) {
                return from > to ? -8 : 8;
            }
            else if (Math.abs(startRank - endRank) == Math.abs(startFile - endFile)) {
                return from > to ? (from - to) % 9 == 0 ? -9 : -7 : (to - from) % 9 == 0 ? 9 : 7;
            }
            else if (startRank + startFile == endRank + endFile) {
                return from > to ? -9 : 9;
            }
            return 0;
        }
    }
}
