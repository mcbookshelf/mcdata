package dev.mcbookshelf.mcdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidIds;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class BlockExtractor {

    public static void generateBlockData(Path output) throws IOException {
        System.out.println("Generating block data...");
        Files.createDirectories(output);

        JsonObject data = extractBlocks(new JsonObject());
        Main.writeJsonToFile(output.resolve("data.json"), data, true);
        Main.writeJsonToFile(output.resolve("data.min.json"), data, false);
    }

    private static JsonObject extractBlocks(JsonObject data) {
        BuiltInRegistries.BLOCK.listElements().forEach((reference) ->
            data.add(reference.getRegisteredName(), extractBlockData(reference.value()))
        );
        return data;
    }

    private static JsonObject extractBlockData(Block block) {
        JsonObject data = new JsonObject();
        BlockState state = block.defaultBlockState();
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        VoxelShape shape2 = state.getShape(EmptyBlockGetter.INSTANCE, new BlockPos(1, 0, 1));

        data.addProperty("item", block.asItem().toString());
        data.addProperty("can_occlude", state.canOcclude());
        data.addProperty("has_shape_offset", !shape.toString().equals(shape2.toString()));
        data.addProperty("has_visual_offset", state.hasOffsetFunction());
        data.addProperty("ignited_by_lava", state.ignitedByLava());

        data.addProperty("blast_resistance", block.getExplosionResistance());
        data.addProperty("friction", block.getFriction());
        data.addProperty("hardness", block.defaultDestroyTime());
        data.addProperty("jump_factor", block.getJumpFactor());
        data.addProperty("speed_factor", block.getSpeedFactor());
        data.addProperty("instrument", state.instrument().getSoundEvent().getRegisteredName());

        data.add("sounds", extractBlockSounds(state.getSoundType(), new JsonObject()));
        data.add("default_properties", extractStateProperties(state, new JsonObject()));
        data.add("possible_properties", extractBlockProperties(block, new JsonObject()));

        data.add("states", extractBlockStates(block, new JsonArray()));

        return data;
    }

    private static JsonObject extractStateProperties(StateHolder<?, ?> state, JsonObject properties) {
        state.getValues().forEach(v -> properties.addProperty(v.property().getName(), v.valueName()));
        return properties;
    }

    private static JsonObject extractBlockProperties(Block block, JsonObject properties) {
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            JsonArray jsonArray = new JsonArray();
            for(Comparable<?> comparable : property.getPossibleValues())
                jsonArray.add(Util.getPropertyName(property, comparable));
            properties.add(property.getName(), jsonArray);
        }
        return properties;
    }

    private static JsonObject extractBlockSounds(SoundType sound, JsonObject sounds) {
        sounds.addProperty("break", sound.getBreakSound().location().toString());
        sounds.addProperty("hit", sound.getHitSound().location().toString());
        sounds.addProperty("fall", sound.getFallSound().location().toString());
        sounds.addProperty("place", sound.getPlaceSound().location().toString());
        sounds.addProperty("step", sound.getStepSound().location().toString());
        return sounds;
    }

    private static JsonArray extractBlockStates(Block block, JsonArray states) {
        CollisionContext empty = CollisionContext.empty();
        CollisionContext light = (CollisionContext) Proxy.newProxyInstance(
                CollisionContext.class.getClassLoader(),
                new Class<?>[]{CollisionContext.class},
                (_, method, args) -> {
                    if (method.getName().equals("isHoldingItem")) return true;
                    return method.invoke(empty, args);
                }
        );
        return extractBlockStates(block, states, block instanceof LightBlock ? light : empty);
    }

    private static JsonArray extractBlockStates(Block block, JsonArray states, CollisionContext ctx) {
        block.properties().offsetType(BlockBehaviour.OffsetType.NONE);
        block.getStateDefinition().getPossibleStates().forEach(state -> {
            JsonObject data = new JsonObject();

            VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, ctx);
            VoxelShape shape2 = state.getShape(EmptyBlockGetter.INSTANCE, new BlockPos(1, 0, 1), ctx);
            VoxelShape collisionShape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, ctx);

            if (!shape.toString().equals(shape2.toString())) {
                Vec3 offset = state.getOffset(BlockPos.ZERO);
                shape = shape.move(-offset.x, -offset.y, -offset.z);
                collisionShape = collisionShape.move(-offset.x, -offset.y, -offset.z);
            }

            Optional<ResourceKey<Fluid>> key = BuiltInRegistries.FLUID.getResourceKey(state.getFluidState().getType());
            JsonObject fluid = new JsonObject();
            if (key.isPresent() && !key.get().equals(FluidIds.EMPTY)) {
                fluid.addProperty("id", key.get().identifier().toString());
                extractStateProperties(state.getFluidState(), fluid);
            }

            data.add("fluid", fluid);
            data.add("properties", extractStateProperties(state, new JsonObject()));
            data.add("shape", new dev.mcbookshelf.mcdata.VoxelShape(shape).optimize().toJson());
            data.add("collision_shape", new dev.mcbookshelf.mcdata.VoxelShape(collisionShape).optimize().toJson());

            data.addProperty("luminance", state.getLightEmission());
            data.addProperty("is_conductive", state.isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            data.addProperty("is_spawnable", state.isValidSpawn(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, EntityTypes.MARKER));

            states.add(data);
        });
        return states;
    }
}
