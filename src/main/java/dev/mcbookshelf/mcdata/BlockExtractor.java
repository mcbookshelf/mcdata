package dev.mcbookshelf.mcdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class BlockExtractor {
    public static void generateBlockData(Path output) throws IOException {
        System.out.println("Generating block data...");
        Files.createDirectories(output);

        JsonObject data = extractBlocks();
        JsonUtils.writeJsonToFile(output.resolve("data.json"), data, true);
        JsonUtils.writeJsonToFile(output.resolve("data.min.json"), data, false);
    }

    private static JsonObject extractBlocks() {
        JsonObject data = new JsonObject();
        Registry<Block> blockRegistry = BuiltInRegistries.BLOCK;

        for (var entry : blockRegistry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Block block = entry.getValue();
            data.add(id.toString(), extractBlockData(block));
        }
        return data;
    }

    private static JsonObject extractBlockData(Block block) {
        JsonObject data = new JsonObject();
        BlockState state = block.defaultBlockState();
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        VoxelShape shape2 = state.getShape(EmptyBlockGetter.INSTANCE, new BlockPos(1, 0, 1));

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

        data.add("sounds", extractBlockSounds(state.getSoundType()));
        data.add("default_properties", extractStateProperties(state));
        data.add("possible_properties", extractBlockProperties(block));

        data.add("states", extractBlockStates(block));

        return data;
    }

    private static JsonObject extractStateProperties(BlockState state) {
        JsonObject properties = new JsonObject();
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet())
            properties.addProperty(entry.getKey().getName(), String.valueOf(entry.getValue()).toLowerCase());
        return properties;
    }

    private static JsonObject extractBlockProperties(Block block) {
        JsonObject properties = new JsonObject();
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            JsonArray jsonArray = new JsonArray();
            for(Comparable<?> comparable : property.getPossibleValues())
                jsonArray.add(Util.getPropertyName(property, comparable));
            properties.add(property.getName(), jsonArray);
        }
        return properties;
    }

    private static JsonObject extractBlockSounds(SoundType sound) {
        JsonObject sounds = new JsonObject();
        sounds.addProperty("break", sound.getBreakSound().location().toString());
        sounds.addProperty("hit", sound.getHitSound().location().toString());
        sounds.addProperty("fall", sound.getFallSound().location().toString());
        sounds.addProperty("place", sound.getPlaceSound().location().toString());
        sounds.addProperty("step", sound.getStepSound().location().toString());
        return sounds;
    }

    private static JsonArray extractBlockStates(Block block) {
        return extractBlockStates(block, block instanceof LightBlock ? new EntityCollisionContext(
                false,
                false,
                -Double.MAX_VALUE,
                ItemStack.EMPTY,
                (fluidState) -> false,
                null
        ) {
            public boolean isHoldingItem(Item item) {
                return true;
            }
        } : CollisionContext.empty());
    }

    private static JsonArray extractBlockStates(Block block, CollisionContext ctx) {
        JsonArray states = new JsonArray();
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

            data.addProperty("luminance", state.getLightEmission());
            data.addProperty("is_conductive", state.isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            data.addProperty("is_spawnable", state.isValidSpawn(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, EntityType.MARKER));

            data.add("shape", new dev.mcbookshelf.mcdata.VoxelShape(shape).optimize().toJson());
            data.add("collision_shape", new dev.mcbookshelf.mcdata.VoxelShape(collisionShape).optimize().toJson());
            data.add("properties", extractStateProperties(state));

            states.add(data);
        });
        return states;
    }
}
