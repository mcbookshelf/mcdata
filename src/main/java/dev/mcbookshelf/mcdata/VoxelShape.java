package dev.mcbookshelf.mcdata;

import com.google.gson.JsonArray;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class VoxelShape {

    public final List<AABB> shape;

    private static final ConcurrentHashMap<VoxelShape, VoxelShape> cache = new ConcurrentHashMap<>();

    public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public AABB(net.minecraft.world.phys.AABB box) {
            this(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }

        public boolean isDegenerate() {
            return this.maxX <= this.minX || this.maxY <= this.minY || this.maxZ <= this.minZ;
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
        this.shape = shape.toAabbs().stream().map(AABB::new).toList();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.shape);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VoxelShape other && this.shape.equals(other.shape);
    }

    public JsonArray toJson() {
        return this.shape.stream()
            .map(AABB::toJson)
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }

    public VoxelShape optimize() {
        VoxelShape cached = cache.get(this);
        if (cached != null) return cached;

        List<AABB> optimized = VoxelShapeOptimizer.optimize(this.shape);
        VoxelShape result = optimized == this.shape ? this : new VoxelShape(optimized);

        VoxelShape existing = cache.putIfAbsent(this, result);
        return existing != null ? existing : result;
    }
}