package dev.mcbookshelf.mcdata;

import dev.mcbookshelf.mcdata.VoxelShape.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

/**
 * Rewrites a list of boxes as the smallest possible list of boxes covering exactly the same volume.
 * <p>
 * Output boxes may overlap: the goal is to minimise their count, not to partition the volume. The
 * whole thing runs on a grid built from the shape's own coordinates, so no resolution is lost and
 * coordinates outside {@code [0, 1]} (fence and wall collision shapes reach {@code y = 1.5}) are
 * handled like any other.
 * <p>
 * The pipeline is:
 * <ol>
 *     <li>compress each axis to its distinct coordinates, giving a grid of a few hundred cells;</li>
 *     <li>enumerate every <i>maximal</i> filled box, i.e. every filled box that cannot grow;</li>
 *     <li>cover the filled cells with as few of those boxes as possible.</li>
 * </ol>
 * Step 3 is minimum set cover, which is NP-hard, so a greedy cover gives an upper bound and an
 * iterative-deepening search tries to beat it under a node budget. On vanilla shapes the search
 * closes in a handful of nodes.
 */
final class VoxelShapeOptimizer {

    private static final double EPSILON = 1.0e-7;
    private static final int MAX_CELLS = 1 << 16;
    private static final int MAX_BOXES = 8192;
    private static final int MAX_NODES = 50_000;

    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final int nx;
    private final int ny;
    private final int nz;
    private final int words;
    private final boolean[] filled;
    private final int[] prefix;

    private final List<Box> boxes;
    private final long[][] boxCells;
    private final int[][] cellBoxes;
    private final int[] antichain;

    private int nodes;

    private record Box(int x0, int y0, int z0, int x1, int y1, int z1) {}

    static List<AABB> optimize(List<AABB> input) {
        if (input.size() <= 1) return input;
        // A flat box has no cells on the grid and would silently vanish.
        for (AABB box : input) if (box.isDegenerate()) return input;

        double[] xs = planes(input, AABB::minX, AABB::maxX);
        double[] ys = planes(input, AABB::minY, AABB::maxY);
        double[] zs = planes(input, AABB::minZ, AABB::maxZ);
        long cells = (long) (xs.length - 1) * (ys.length - 1) * (zs.length - 1);
        if (cells <= 0 || cells > MAX_CELLS) return input;

        List<AABB> result = new VoxelShapeOptimizer(xs, ys, zs, input).run();
        return result.size() < input.size() ? result : input;
    }

    private VoxelShapeOptimizer(double[] xs, double[] ys, double[] zs, List<AABB> input) {
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.nx = xs.length - 1;
        this.ny = ys.length - 1;
        this.nz = zs.length - 1;
        this.words = (this.nx * this.ny * this.nz + 63) >>> 6;
        this.filled = new boolean[this.nx * this.ny * this.nz];
        for (AABB box : input) this.fill(box);
        this.prefix = this.buildPrefix();

        this.boxes = this.maximalBoxes();
        this.boxCells = this.buildBoxCells();
        this.cellBoxes = this.buildCellBoxes();
        this.antichain = this.buildAntichain();
    }

    private List<AABB> run() {
        if (this.boxes.isEmpty()) return List.of();

        long[] target = new long[this.words];
        for (int cell = 0; cell < this.filled.length; cell++)
            if (this.filled[cell]) target[cell >>> 6] |= 1L << cell;

        List<Integer> cover = this.greedyCover(target);
        if (this.boxes.size() <= MAX_BOXES) {
            List<Integer> exact = this.exactCover(target, cover.size() - 1);
            if (exact != null) cover = exact;
        }
        cover = this.prune(cover);

        return cover.stream().map(index -> this.toAABB(this.boxes.get(index))).toList();
    }

    private static double[] planes(List<AABB> boxes, ToDoubleFunction<AABB> min, ToDoubleFunction<AABB> max) {
        double[] values = new double[boxes.size() * 2];
        int index = 0;
        for (AABB box : boxes) {
            values[index++] = min.applyAsDouble(box);
            values[index++] = max.applyAsDouble(box);
        }
        Arrays.sort(values);

        int size = 0;
        for (double value : values)
            if (size == 0 || value - values[size - 1] > EPSILON) values[size++] = value;
        return Arrays.copyOf(values, size);
    }

    private static int plane(double[] planes, double value) {
        int index = Arrays.binarySearch(planes, value);
        if (index >= 0) return index;

        int insert = -index - 1;
        if (insert == planes.length) return insert - 1;
        if (insert > 0 && value - planes[insert - 1] <= planes[insert] - value) return insert - 1;
        return insert;
    }

    private void fill(AABB box) {
        int x0 = plane(this.xs, box.minX());
        int y0 = plane(this.ys, box.minY());
        int z0 = plane(this.zs, box.minZ());
        int x1 = plane(this.xs, box.maxX());
        int y1 = plane(this.ys, box.maxY());
        int z1 = plane(this.zs, box.maxZ());

        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                for (int z = z0; z < z1; z++)
                    this.filled[this.cell(x, y, z)] = true;
    }

    private int cell(int x, int y, int z) {
        return (x * this.ny + y) * this.nz + z;
    }

    private int corner(int x, int y, int z) {
        return (x * (this.ny + 1) + y) * (this.nz + 1) + z;
    }

    private int[] buildPrefix() {
        int[] prefix = new int[(this.nx + 1) * (this.ny + 1) * (this.nz + 1)];
        for (int x = 1; x <= this.nx; x++)
            for (int y = 1; y <= this.ny; y++)
                for (int z = 1; z <= this.nz; z++)
                    prefix[this.corner(x, y, z)] = (this.filled[this.cell(x - 1, y - 1, z - 1)] ? 1 : 0)
                        + prefix[this.corner(x - 1, y, z)]
                        + prefix[this.corner(x, y - 1, z)]
                        + prefix[this.corner(x, y, z - 1)]
                        - prefix[this.corner(x - 1, y - 1, z)]
                        - prefix[this.corner(x - 1, y, z - 1)]
                        - prefix[this.corner(x, y - 1, z - 1)]
                        + prefix[this.corner(x - 1, y - 1, z - 1)];
        return prefix;
    }

    private boolean isFilled(int x0, int y0, int z0, int x1, int y1, int z1) {
        int sum = this.prefix[this.corner(x1 + 1, y1 + 1, z1 + 1)]
            - this.prefix[this.corner(x0, y1 + 1, z1 + 1)]
            - this.prefix[this.corner(x1 + 1, y0, z1 + 1)]
            - this.prefix[this.corner(x1 + 1, y1 + 1, z0)]
            + this.prefix[this.corner(x0, y0, z1 + 1)]
            + this.prefix[this.corner(x0, y1 + 1, z0)]
            + this.prefix[this.corner(x1 + 1, y0, z0)]
            - this.prefix[this.corner(x0, y0, z0)];
        return sum == (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
    }

    private AABB toAABB(Box box) {
        return new AABB(
            this.xs[box.x0()], this.ys[box.y0()], this.zs[box.z0()],
            this.xs[box.x1() + 1], this.ys[box.y1() + 1], this.zs[box.z1() + 1]
        );
    }

    private long[][] buildBoxCells() {
        long[][] result = new long[this.boxes.size()][];
        for (int index = 0; index < this.boxes.size(); index++) {
            Box box = this.boxes.get(index);
            long[] bits = new long[this.words];
            for (int x = box.x0(); x <= box.x1(); x++)
                for (int y = box.y0(); y <= box.y1(); y++)
                    for (int z = box.z0(); z <= box.z1(); z++) {
                        int cell = this.cell(x, y, z);
                        bits[cell >>> 6] |= 1L << cell;
                    }
            result[index] = bits;
        }
        return result;
    }

    private int[][] buildCellBoxes() {
        int[] counts = new int[this.filled.length];
        for (Box box : this.boxes)
            for (int x = box.x0(); x <= box.x1(); x++)
                for (int y = box.y0(); y <= box.y1(); y++)
                    for (int z = box.z0(); z <= box.z1(); z++)
                        counts[this.cell(x, y, z)]++;

        int[][] result = new int[this.filled.length][];
        for (int cell = 0; cell < counts.length; cell++) result[cell] = new int[counts[cell]];

        int[] cursor = new int[this.filled.length];
        for (int index = 0; index < this.boxes.size(); index++) {
            Box box = this.boxes.get(index);
            for (int x = box.x0(); x <= box.x1(); x++)
                for (int y = box.y0(); y <= box.y1(); y++)
                    for (int z = box.z0(); z <= box.z1(); z++) {
                        int cell = this.cell(x, y, z);
                        result[cell][cursor[cell]++] = index;
                    }
        }
        return result;
    }

    private List<Box> maximalBoxes() {
        List<Box> result = new ArrayList<>();
        for (int x0 = 0; x0 < this.nx; x0++)
            for (int x1 = x0; x1 < this.nx; x1++)
                for (int y0 = 0; y0 < this.ny; y0++)
                    for (int y1 = y0; y1 < this.ny; y1++)
                        for (int z = 0; z < this.nz; z++) {
                            if (!this.isFilled(x0, y0, z, x1, y1, z)) continue;
                            int start = z;
                            while (z + 1 < this.nz && this.isFilled(x0, y0, z + 1, x1, y1, z + 1)) z++;
                            if (this.isMaximal(x0, y0, start, x1, y1, z)) {
                                result.add(new Box(x0, y0, start, x1, y1, z));
                            }
                        }
        return result;
    }

    private boolean isMaximal(int x0, int y0, int z0, int x1, int y1, int z1) {
        return !(x0 > 0 && this.isFilled(x0 - 1, y0, z0, x0 - 1, y1, z1))
            && !(y0 > 0 && this.isFilled(x0, y0 - 1, z0, x1, y0 - 1, z1))
            && !(z0 > 0 && this.isFilled(x0, y0, z0 - 1, x1, y1, z0 - 1))
            && !(x1 + 1 < this.nx && this.isFilled(x1 + 1, y0, z0, x1 + 1, y1, z1))
            && !(y1 + 1 < this.ny && this.isFilled(x0, y1 + 1, z0, x1, y1 + 1, z1))
            && !(z1 + 1 < this.nz && this.isFilled(x0, y0, z1 + 1, x1, y1, z1 + 1));
    }

    private List<Integer> greedyCover(long[] target) {
        long[] remaining = target.clone();
        PriorityQueue<int[]> queue = new PriorityQueue<>(
            Comparator.<int[]>comparingInt(entry -> -entry[0]).thenComparingInt(entry -> -entry[1])
        );
        for (int index = 0; index < this.boxes.size(); index++) {
            int size = cardinality(this.boxCells[index]);
            queue.add(new int[]{size, size, index});
        }

        List<Integer> chosen = new ArrayList<>();
        while (!isEmpty(remaining)) {
            int[] best;
            while (true) {
                best = queue.poll();
                if (best == null) throw new IllegalStateException("uncovered cell outside every maximal box");

                int gain = intersectionCount(this.boxCells[best[2]], remaining);
                if (gain == 0) continue;
                if (gain == best[0]) break;

                best[0] = gain;
                int[] head = queue.peek();
                if (head == null || gain > head[0] || (gain == head[0] && best[1] >= head[1])) break;
                queue.add(best);
            }
            chosen.add(best[2]);
            andNot(remaining, this.boxCells[best[2]]);
        }
        return chosen;
    }

    private @Nullable List<Integer> exactCover(long[] target, int limit) {
        for (int depth = this.lowerBound(target); depth <= limit; depth++) {
            this.nodes = 0;
            List<Integer> chosen = new ArrayList<>();
            if (this.search(target.clone(), depth, chosen)) return chosen;
            if (this.nodes >= MAX_NODES) return null;
        }
        return null;
    }

    private boolean search(long[] remaining, int depth, List<Integer> chosen) {
        if (isEmpty(remaining)) return true;
        if (depth == 0 || ++this.nodes >= MAX_NODES) return false;
        if (this.lowerBound(remaining) > depth) return false;

        for (int index : this.cellBoxes[this.mostConstrained(remaining)]) {
            long[] next = remaining.clone();
            andNot(next, this.boxCells[index]);
            chosen.add(index);
            if (this.search(next, depth - 1, chosen)) return true;
            chosen.removeLast();
        }
        return false;
    }

    private int mostConstrained(long[] remaining) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int word = 0; word < remaining.length; word++) {
            long bits = remaining[word];
            while (bits != 0) {
                int cell = (word << 6) + Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int count = this.cellBoxes[cell].length;
                if (count < bestCount) {
                    best = cell;
                    bestCount = count;
                    if (count == 1) return best;
                }
            }
        }
        return best;
    }

    private int[] buildAntichain() {
        List<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < this.filled.length; cell++) if (this.filled[cell]) candidates.add(cell);
        candidates.sort(Comparator.comparingInt(cell -> this.cellBoxes[cell].length));

        List<Integer> chosen = new ArrayList<>();
        for (int cell : candidates) {
            boolean independent = true;
            for (int other : chosen) {
                if (this.shareBox(cell, other)) {
                    independent = false;
                    break;
                }
            }
            if (independent) chosen.add(cell);
        }
        return chosen.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean shareBox(int a, int b) {
        int ax = a / (this.ny * this.nz);
        int ay = a / this.nz % this.ny;
        int az = a % this.nz;
        int bx = b / (this.ny * this.nz);
        int by = b / this.nz % this.ny;
        int bz = b % this.nz;
        return this.isFilled(
            Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
            Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz)
        );
    }

    private int lowerBound(long[] remaining) {
        int bound = 0;
        for (int cell : this.antichain) if ((remaining[cell >>> 6] & 1L << cell) != 0) bound++;
        return bound;
    }

    private List<Integer> prune(List<Integer> chosen) {
        int[] cover = new int[this.filled.length];
        for (int index : chosen) this.addCover(cover, index, 1);

        List<Integer> result = new ArrayList<>(chosen);
        for (int position = result.size() - 1; position >= 0; position--) {
            int index = result.get(position);
            if (!this.isRedundant(cover, index)) continue;
            this.addCover(cover, index, -1);
            result.remove(position);
        }
        return result;
    }

    private void addCover(int[] cover, int index, int delta) {
        long[] bits = this.boxCells[index];
        for (int word = 0; word < bits.length; word++) {
            long value = bits[word];
            while (value != 0) {
                cover[(word << 6) + Long.numberOfTrailingZeros(value)] += delta;
                value &= value - 1;
            }
        }
    }

    private boolean isRedundant(int[] cover, int index) {
        long[] bits = this.boxCells[index];
        for (int word = 0; word < bits.length; word++) {
            long value = bits[word];
            while (value != 0) {
                if (cover[(word << 6) + Long.numberOfTrailingZeros(value)] < 2) return false;
                value &= value - 1;
            }
        }
        return true;
    }

    private static boolean isEmpty(long[] bits) {
        for (long word : bits) if (word != 0) return false;
        return true;
    }

    private static int cardinality(long[] bits) {
        int count = 0;
        for (long word : bits) count += Long.bitCount(word);
        return count;
    }

    private static int intersectionCount(long[] bits, long[] other) {
        int count = 0;
        for (int word = 0; word < bits.length; word++) count += Long.bitCount(bits[word] & other[word]);
        return count;
    }

    private static void andNot(long[] bits, long[] other) {
        for (int word = 0; word < bits.length; word++) bits[word] &= ~other[word];
    }
}
