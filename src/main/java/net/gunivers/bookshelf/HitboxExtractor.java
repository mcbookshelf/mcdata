package net.gunivers.bookshelf;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.Vec3;
import static net.gunivers.bookshelf.Extractor.writeJsonToFile;

public class HitboxExtractor {

    public static void generateBlockShapes(String path, String fileName) throws IOException {
        System.out.println("Generating block shapes...");
        Files.createDirectories(Path.of(path));
        JsonObject blockShapes = extractBlockShapes();
        writeJsonToFile(path + fileName + ".json", blockShapes, true);
        writeJsonToFile(path + fileName + ".min.json", blockShapes, false);
    }

    /**
     * Extracts block shapes from the Minecraft Blocks registry.
     */
    private static JsonObject extractBlockShapes() {
        JsonObject blocksJson = new JsonObject();

        for (Field blockField : Blocks.class.getFields()) {
            try {
                Block block = (Block) blockField.get(null);
                String blockID = block.toString().substring(6, block.toString().length() - 1);
                blocksJson.add(blockID, extractSingleBlockShapes(block));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return blocksJson;
    }

    /**
     * Extracts shapes from a Minecraft Block.
     */
    private static JsonArray extractSingleBlockShapes(Block block) {
        JsonArray states = new JsonArray();

        block.getStateDefinition().getPossibleStates().forEach(state -> {
            JsonObject stateJson = new JsonObject();
            JsonObject properties = new JsonObject();
    
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet())
                properties.addProperty(entry.getKey().getName(), String.valueOf(entry.getValue()).toLowerCase());
            stateJson.add("properties", properties);

            net.minecraft.world.phys.shapes.VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);

            if (shape.toString().equals(state.getShape(EmptyBlockGetter.INSTANCE, new BlockPos(1, 0, 1)).toString())) {
                stateJson.addProperty("has_offset", false);
            } else {
                Vec3 offset = null;
                try {
                    Method method = getMethod(state.getClass(), "getOffset");
                    if (method != null) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length == 2) {
                            offset = (Vec3) method.invoke(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                        } else if (paramTypes.length == 1) {
                            offset = (Vec3) method.invoke(state, BlockPos.ZERO);
                        }
                    } else {
                        System.out.println("Method getOffset not found.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                shape = shape.move(-offset.x, -offset.y, -offset.z);
                stateJson.addProperty("has_offset", true);
            }
            
            stateJson.add("shape", new VoxelShape(shape).optimize().toJson());
            states.add(stateJson);
        });

        return states;
    }

    /**
     * Find a method by its name using reflection.
     */
    private static Method getMethod(Class<?> clazz, String methodName) {
        while (clazz != null) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return method;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
