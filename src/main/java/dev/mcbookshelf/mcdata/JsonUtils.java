package dev.mcbookshelf.mcdata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonUtils {

    public static void writeJsonToFile(Path file, JsonObject data, boolean prettyPrint) throws IOException {
        Gson gson = prettyPrint ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
        Files.writeString(file, gson.toJson(data));
    }
}
