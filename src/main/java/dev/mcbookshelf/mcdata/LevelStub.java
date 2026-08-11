package dev.mcbookshelf.mcdata;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.scores.Scoreboard;

import java.lang.invoke.MethodHandles;
import java.util.Map;

public abstract class LevelStub extends Level {

    public static Level create(RegistryAccess.Frozen registries) {
        try {
            return Generated.TYPE.getDeclaredConstructor(RegistryAccess.Frozen.class).newInstance(registries);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate the level stub", e);
        }
    }

    private static final WritableLevelData LEVEL_DATA = stub(WritableLevelData.class, Map.of("getDifficulty", Difficulty.NORMAL));
    private static final ChunkSource CHUNK_SOURCE = stub(ChunkSource.class, Map.of());

    private static Holder<DimensionType> dimensionType(RegistryAccess.Frozen registries) {
        return registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
    }

    public LevelStub(RegistryAccess.Frozen registries) {
        super(LEVEL_DATA, Level.OVERWORLD, registries, dimensionType(registries), false, false, 0L, 0);
    }

    @Override public ChunkSource getChunkSource() {return CHUNK_SOURCE;}
    @Override public WorldBorder getWorldBorder() {return new WorldBorder();}
    @Override public Scoreboard getScoreboard() {return new Scoreboard();}
    @Override public ClientClockManager clockManager() {return new ClientClockManager();}
    @Override public FeatureFlagSet enabledFeatures() {return FeatureFlags.REGISTRY.allFlags();}
    @Override public EnvironmentAttributeSystem environmentAttributes() {return EnvironmentAttributeSystem.builder().addDefaultLayers(this).build();}
    @Override public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {return registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);}

    private static final class Generated {
        static final Class<? extends LevelStub> TYPE = new ByteBuddy()
            .subclass(LevelStub.class, ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
            .method(ElementMatchers.isAbstract())
            .intercept(StubMethod.INSTANCE)
            .make()
            .load(LevelStub.class.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(MethodHandles.lookup()))
            .getLoaded();
    }

    private static <T> T stub(Class<T> type, Map<String, ?> fixed) {
        DynamicType.Builder<T> builder = new ByteBuddy().subclass(type);
        ElementMatcher.Junction<MethodDescription> stubbed = ElementMatchers.isAbstract();

        for (Map.Entry<String, ?> entry : fixed.entrySet()) {
            builder = builder.method(ElementMatchers.named(entry.getKey())).intercept(FixedValue.value(entry.getValue()));
            stubbed = stubbed.and(ElementMatchers.not(ElementMatchers.named(entry.getKey())));
        }

        try {
            return builder
                .method(stubbed)
                .intercept(StubMethod.INSTANCE)
                .make()
                .load(type.getClassLoader())
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to stub " + type.getName(), e);
        }
    }
}
