package net.gunivers.bookshelf;

import java.io.IOException;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import net.minecraft.data.Main;
import static net.gunivers.bookshelf.HitboxExtractor.generateBlockShapes;
import static net.gunivers.bookshelf.SoundExtractor.generateBlockSounds;

public class Extractor {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Extractor <minecraft_version>");
            return;
        }

        try {
            Main.main(new String[] { "--validate" });
        } catch (Exception e) {
            System.out.println("Main call of the Minecraft client's init failed.");
            e.printStackTrace();
            return;
        }

        generateBlockShapes("generated/" + args[0] + "/blocks/", "shapes");
        generateBlockSounds("generated/" + args[0] + "/blocks/", "sounds");
    }

    /**
     * Writes a JsonObject to a JSON file.
     */
    public static void writeJsonToFile(String fileName, JsonObject data, boolean prettyPrint) {
        Gson gson = prettyPrint ? new GsonBuilder().setPrettyPrinting().create() : new Gson();

        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}