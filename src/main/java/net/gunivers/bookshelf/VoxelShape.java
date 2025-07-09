package net.gunivers.bookshelf;

import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class VoxelShape {

    public final List<AABB> shape;

    private static final int GRID_RESOLUTION = 64;
    private static final double INV_GRID_RESOLUTION = 1.0 / GRID_RESOLUTION;
    private static final ConcurrentHashMap<VoxelShape, VoxelShape> cache = new ConcurrentHashMap<>();

    public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public AABB(net.minecraft.world.phys.AABB box) {
            this(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }

        public JsonArray toJson() {
            JsonArray box = new JsonArray();
            box.add(this.minX);
            box.add(this.minY);
            box.add(this.minZ);
            box.add(this.maxX);
            box.add(this.maxY);
            box.add(this.maxZ);
            return box;
        }
    }

    public VoxelShape(List<AABB> shape) {
        this.shape = List.copyOf(shape);
    }

    public VoxelShape(net.minecraft.world.phys.shapes.VoxelShape shape) {
        this.shape = shape.toAabbs().stream().map(AABB::new).collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(shape);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VoxelShape other && shape.equals(other.shape);
    }

    public JsonArray toJson() {
        return this.shape.stream()
            .map(AABB::toJson)
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }

    public VoxelShape optimize() {
        return cache.computeIfAbsent(this, unused -> {
            boolean[][][] voxels = new boolean[GRID_RESOLUTION][GRID_RESOLUTION][GRID_RESOLUTION];
            boolean[][][] visited = new boolean[GRID_RESOLUTION][GRID_RESOLUTION][GRID_RESOLUTION];
            for (AABB box : shape) fillVolume(voxels, box);

            List<AABB> shape = new ArrayList<>();
            for (int x = 0; x < GRID_RESOLUTION; x++) {
                for (int y = 0; y < GRID_RESOLUTION; y++) {
                    for (int z = 0; z < GRID_RESOLUTION; z++) {
                        if (voxels[x][y][z] && !visited[x][y][z]) {
                            shape.add(greedyMergeAABB(voxels, visited, x, y, z));
                        }
                    }
                }
            }
            return new VoxelShape(shape);
        });
    }

    private AABB greedyMergeAABB(boolean[][][] voxels, boolean[][][] visited, int startX, int startY, int startZ) {
        int maxX = startX;
        int maxY = startY;
        int maxZ = startZ;

        for (int y = startY + 1; y < GRID_RESOLUTION; y++) {
            if (isVolumeFilled(voxels, startX, startY, startZ, maxX, y, maxZ)) {
                maxY = y;
            } else {
                break;
            }
        }

        for (int x = startX + 1; x < GRID_RESOLUTION; x++) {
            if (isVolumeFilled(voxels, startX, startY, startZ, x, maxY, maxZ)) {
                maxX = x;
            } else {
                break;
            }
        }

        for (int z = startZ + 1; z < GRID_RESOLUTION; z++) {
            if (isVolumeFilled(voxels, startX, startY, startZ, maxX, maxY, z)) {
                maxZ = z;
            } else {
                break;
            }
        }

        for (int x = startX; x <= maxX; x++) {
            for (int y = startY; y <= maxY; y++) {
                for (int z = startZ; z <= maxZ; z++) {
                    visited[x][y][z] = true;
                }
            }
        }

        return new AABB(
            (double) startX * INV_GRID_RESOLUTION,
            (double) startY * INV_GRID_RESOLUTION,
            (double) startZ * INV_GRID_RESOLUTION,
            (double) (maxX + 1) * INV_GRID_RESOLUTION,
            (double) (maxY + 1) * INV_GRID_RESOLUTION,
            (double) (maxZ + 1) * INV_GRID_RESOLUTION
        );
    }

    private void fillVolume(boolean[][][] voxels, AABB box) {
        int minX = Math.max((int) (box.minX * GRID_RESOLUTION), 0);
        int minY = Math.max((int) (box.minY * GRID_RESOLUTION), 0);
        int minZ = Math.max((int) (box.minZ * GRID_RESOLUTION), 0);
        int maxX = Math.min((int) (box.maxX * GRID_RESOLUTION), GRID_RESOLUTION);
        int maxY = Math.min((int) (box.maxY * GRID_RESOLUTION), GRID_RESOLUTION);
        int maxZ = Math.min((int) (box.maxZ * GRID_RESOLUTION), GRID_RESOLUTION);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    voxels[x][y][z] = true;
                }
            }
        }
    }

    private boolean isVolumeFilled(boolean[][][] voxels, int startX, int startY, int startZ, int maxX, int maxY, int maxZ) {
        for (int x = startX; x <= maxX; x++) {
            for (int y = startY; y <= maxY; y++) {
                for (int z = startZ; z <= maxZ; z++) {
                    if (!voxels[x][y][z]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
