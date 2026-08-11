package dev.mcbookshelf.mcdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;

public class EntityExtractor {

    public static void generateEntityData(Level level, Path output) throws IOException {
        System.out.println("Generating entity data...");
        Files.createDirectories(output);

        JsonObject data = extractEntities(level, new JsonObject());
        Main.writeJsonToFile(output.resolve("data.json"), data, true);
        Main.writeJsonToFile(output.resolve("data.min.json"), data, false);
    }

    private static JsonObject extractEntities(Level level, JsonObject data) {
        Registry<EntityType<?>> entityTypeRegistry = BuiltInRegistries.ENTITY_TYPE;

        entityTypeRegistry.listElements().forEach(reference -> data.add(
            reference.getRegisteredName(),
            extractEntityData(level, reference.value(), new JsonObject())
        ));

        return data;
    }

    private static JsonObject extractEntityData(Level level, EntityType<?> type, JsonObject data) {
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);

        if (entity == null || hasUnsupportedDimensions(entity)) {
            return data;
        }

        JsonArray variants = new JsonArray();

        switch (entity) {
            case Salmon fish       -> addSalmonVariants(fish, variants);
            case Pufferfish fish   -> addPufferfishVariants(fish, variants);
            case ArmorStand stand  -> addArmorStandVariants(stand, variants);
            // baby exists on the class but the value is clamped to 0, so adult only
            case WanderingTrader _ -> addPoseVariants(entity, variants, new JsonObject());
            case AgeableMob mob    -> addBabyVariants(mob, variants, AgeableMob::setBaby);
            case Zombie mob        -> addBabyVariants(mob, variants, Zombie::setBaby);
            case Piglin mob        -> addBabyVariants(mob, variants, Piglin::setBaby);
            case Zoglin mob        -> addBabyVariants(mob, variants, Zoglin::setBaby);
            default                -> addPoseVariants(entity, variants, new JsonObject());
        }

        if (variants.size() == 1) {
            variants.forEach(e -> e.getAsJsonObject().remove("poses"));
        }
        data.add("dimensions", variants);
        return data;
    }

    private static boolean hasUnsupportedDimensions(Entity entity) {
        return entity instanceof AbstractCubeMob
            || entity instanceof AreaEffectCloud
            || entity instanceof HangingEntity
            || entity instanceof Phantom
            || entity instanceof Interaction;
    }

    private static JsonObject extra(Object... keyValues) {
        JsonObject obj = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            switch (keyValues[i + 1]) {
                case Boolean b -> obj.addProperty(key, b);
                case Number n  -> obj.addProperty(key, n);
                case String s  -> obj.addProperty(key, s);
                default -> throw new IllegalArgumentException("Unsupported extra value for " + key);
            }
        }
        return obj;
    }

    private static void addSalmonVariants(Salmon salmon, JsonArray variants) {
        for (Salmon.Variant variant : Salmon.Variant.values()) {
            salmon.setVariant(variant);
            addPoseVariants(salmon, variants, extra("type", variant.getSerializedName()));
        }
    }

    private static void addPufferfishVariants(Pufferfish fish, JsonArray variants) {
        for (int state = 0; state <= 2; state++) {
            fish.setPuffState(state);
            addPoseVariants(fish, variants, extra("puff_state", state));
        }
    }

    private static void addArmorStandVariants(ArmorStand stand, JsonArray variants) {
        // marker wins over small, so its size is the same either way
        stand.setMarker(true);
        addPoseVariants(stand, variants, extra("marker", true));

        stand.setMarker(false);
        stand.setSmall(true);
        addPoseVariants(stand, variants, extra("marker", false, "small", true));

        stand.setSmall(false);
        addPoseVariants(stand, variants, extra("marker", false, "small", false));
    }

    private static <T extends Entity> void addBabyVariants(T mob, JsonArray variants, BiConsumer<T, Boolean> setBaby) {
        setBaby.accept(mob, false);
        addPoseVariants(mob, variants, extra("baby", false));
        setBaby.accept(mob, true);
        addPoseVariants(mob, variants, extra("baby", true));
    }

    private static void addPoseVariants(Entity entity, JsonArray variants, JsonObject extra) {
        // Get STANDING dimensions as baseline for filtering
        EntityDimensions standing = entity.getDimensions(Pose.STANDING);

        Map<String, JsonObject> group = new LinkedHashMap<>();

        for (Pose pose : Pose.values()) {
            EntityDimensions dims = entity.getDimensions(pose);

            // Skip poses that don't change dimensions (except STANDING)
            if (pose != Pose.STANDING
                && Float.compare(dims.width(), standing.width()) == 0
                && Float.compare(dims.height(), standing.height()) == 0) {
                continue;
            }

            String key = dims.width() + ":" + dims.height();

            JsonObject entry = group.computeIfAbsent(key, k -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("width", dims.width());
                obj.addProperty("height", dims.height());
                extra.entrySet().forEach(e -> obj.add(e.getKey(), e.getValue().deepCopy()));
                obj.add("poses", new JsonArray());
                return obj;
            });

            entry.getAsJsonArray("poses").add(pose.name().toLowerCase(Locale.ROOT));
        }

        group.values().forEach(variants::add);
    }
}
