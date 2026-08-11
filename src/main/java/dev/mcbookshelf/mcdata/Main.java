package dev.mcbookshelf.mcdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;

public class Main {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        String version = SharedConstants.getCurrentVersion().id();
        Path output = Paths.get("generated", version);
        Bootstrap.bootStrap();

        LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(Paths.get("./run")).createAccess("world");
        PackRepository packRepository = ServerPacksSource.createPackRepository(storage);
        packRepository.reload();

        WorldLoader.InitConfig initConfig = getInitConfig(packRepository);
        WorldStem worldStem = Util.blockUntilDone(executor -> WorldLoader.load(
            initConfig,
            context -> new WorldLoader.DataLoadOutput<>(null, context.datapackDimensions()),
            WorldStem::new,
            Util.backgroundExecutor(),
            executor
        )).get();

        Level level = LevelStub.create(worldStem.registries().compositeAccess());

        BlockExtractor.generateBlockData(output.resolve("blocks"));
        EntityExtractor.generateEntityData(level, output.resolve("entities"));

        worldStem.close();
        storage.close();
    }

    public static void writeJsonToFile(Path file, JsonObject data, boolean prettyPrint) throws IOException {
        Gson gson = prettyPrint ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
        Files.writeString(file, gson.toJson(data));
    }

    private static WorldLoader.@NotNull InitConfig getInitConfig(PackRepository packRepository) {
        List<String> enabledPacks = new ArrayList<>(packRepository.getAvailableIds());
        enabledPacks.remove("vanilla");
        enabledPacks.addFirst("vanilla");
        WorldDataConfiguration dataConfig = new WorldDataConfiguration(
            new DataPackConfig(enabledPacks, List.of()),
            FeatureFlags.DEFAULT_FLAGS
        );

        WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, dataConfig, false, true);
        return new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, PermissionSet.ALL_PERMISSIONS);
    }
}
