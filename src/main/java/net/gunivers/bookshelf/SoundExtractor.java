package net.gunivers.bookshelf;

import static net.gunivers.bookshelf.Extractor.writeJsonToFile;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.world.level.block.SoundType;

public class SoundExtractor {

    public static void generateBlockSounds(String path, String fileName) throws IOException {
        System.out.println("Generating block sounds...");
        Files.createDirectories(Path.of(path));
        JsonObject blockSounds = extractBlocksSounds();
        writeJsonToFile(path + fileName + ".json", blockSounds, true);
        writeJsonToFile(path + fileName + ".min.json", blockSounds, false);
    }

    private static JsonObject extractBlocksSounds() {
        JsonObject blocksJson = new JsonObject();
        Registry<Block> blockRegistry = BuiltInRegistries.BLOCK;

        for (var entry : blockRegistry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Block block = entry.getValue();
            blocksJson.add(id.toString(), extractBlockSounds(block));

        }
        return blocksJson;
    }

    private static JsonObject extractBlockSounds(Block block) {
        JsonObject blockJson = new JsonObject();
        SoundType sound = block.defaultBlockState().getSoundType();
        blockJson.addProperty("break", sound.getBreakSound().location().toString());
        blockJson.addProperty("hit", sound.getHitSound().location().toString());
        blockJson.addProperty("fall", sound.getFallSound().location().toString());
        blockJson.addProperty("place", sound.getPlaceSound().location().toString());
        blockJson.addProperty("step", sound.getStepSound().location().toString());
        return blockJson;
    }

}