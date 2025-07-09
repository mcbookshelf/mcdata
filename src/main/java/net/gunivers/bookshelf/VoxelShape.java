package net.gunivers.bookshelf;

import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;
import java.util.stream.Collectors;


public class VoxelShape {

    public List<AABB> shape;

    private static final int GRID_RESOLUTION = 64;
    private boolean[][][] voxelGrid = new boolean[GRID_RESOLUTION][GRID_RESOLUTION][GRID_RESOLUTION];

    public class AABB {
        double minX, minY, minZ, maxX, maxY, maxZ;

        public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public AABB(net.minecraft.world.phys.AABB box) {
            this.minX = box.minX;
            this.minY = box.minY;
            this.minZ = box.minZ;
            this.maxX = box.maxX;
            this.maxY = box.maxY;
            this.maxZ = box.maxZ;
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

        public void fillVoxelGrid(boolean[][][] grid) {
            int minX = Math.max((int) (this.minX * GRID_RESOLUTION), 0);
            int minY = Math.max((int) (this.minY * GRID_RESOLUTION), 0);
            int minZ = Math.max((int) (this.minZ * GRID_RESOLUTION), 0);
            int maxX = Math.min((int) (this.maxX * GRID_RESOLUTION), GRID_RESOLUTION);
            int maxY = Math.min((int) (this.maxY * GRID_RESOLUTION), GRID_RESOLUTION);
            int maxZ = Math.min((int) (this.maxZ * GRID_RESOLUTION), GRID_RESOLUTION);

            for (int x = minX; x < maxX; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        grid[x][y][z] = true;
                    }
                }
            }
        }
    }

    public VoxelShape(List<AABB> shape) {
        this.shape = shape;
    }

    public VoxelShape(net.minecraft.world.phys.shapes.VoxelShape shape) {
        this.shape = shape.toAabbs().stream().map(AABB::new).collect(Collectors.toList());
    }

    public JsonArray toJson() {
        return this.shape.stream()
            .map(AABB::toJson)
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }

    public VoxelShape optimize() {
        for (AABB box : this.shape) {
            box.fillVoxelGrid(this.voxelGrid);
        }
        
        List<AABB> shape = new ArrayList<>();
        boolean[][][] visited = new boolean[GRID_RESOLUTION][GRID_RESOLUTION][GRID_RESOLUTION];

        for (int x = 0; x < GRID_RESOLUTION; x++) {
            for (int y = 0; y < GRID_RESOLUTION; y++) {
                for (int z = 0; z < GRID_RESOLUTION; z++) {
                    if (voxelGrid[x][y][z] && !visited[x][y][z]) {
                        shape.add(findLargestAABB(x, y, z, visited));
                    }
                }
            }
        }
        return new VoxelShape(shape);
    }

    private AABB findLargestAABB(int startX, int startY, int startZ, boolean[][][] visited) {
        int maxX = startX;
        int maxY = startY;
        int maxZ = startZ;

        for (int y = startY + 1; y < GRID_RESOLUTION; y++) {
            if (isVolumeFilled(startX, startY, startZ, maxX, y, maxZ)) {
                maxY = y;
            } else {
                break;
            }
        }

        for (int x = startX + 1; x < GRID_RESOLUTION; x++) {
            if (isVolumeFilled(startX, startY, startZ, x, maxY, maxZ)) {
                maxX = x;
            } else {
                break;
            }
        }

        for (int z = startZ + 1; z < GRID_RESOLUTION; z++) {
            if (isVolumeFilled(startX, startY, startZ, maxX, maxY, z)) {
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
            (double) startX / GRID_RESOLUTION,
            (double) startY / GRID_RESOLUTION,
            (double) startZ / GRID_RESOLUTION,
            (double) (maxX + 1) / GRID_RESOLUTION,
            (double) (maxY + 1) / GRID_RESOLUTION,
            (double) (maxZ + 1) / GRID_RESOLUTION
        );
    }

    private boolean isVolumeFilled(int startX, int startY, int startZ, int maxX, int maxY, int maxZ) {
        for (int x = startX; x <= maxX; x++) {
            for (int y = startY; y <= maxY; y++) {
                for (int z = startZ; z <= maxZ; z++) {
                    if (!voxelGrid[x][y][z]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
