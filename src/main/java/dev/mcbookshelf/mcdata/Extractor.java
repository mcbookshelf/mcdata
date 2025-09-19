package dev.mcbookshelf.mcdata;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.minecraft.SharedConstants;
import net.minecraft.data.Main;

public class Extractor {

    public static void main(String[] args) throws IOException {
        try {
            Main.main(new String[] { "--validate" });
        } catch (Exception e) {
            System.err.println("Failed to initialize Minecraft data generator.");
            e.printStackTrace();
            System.exit(1);
        }

        String version = SharedConstants.getCurrentVersion().id();
        Path output = Paths.get("generated", version);
        BlockExtractor.generateBlockData(output.resolve("blocks"));
    }
}