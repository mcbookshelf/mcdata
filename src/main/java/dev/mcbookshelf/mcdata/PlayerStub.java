package dev.mcbookshelf.mcdata;

import com.mojang.authlib.GameProfile;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

/**
 * The player entity type has no factory, so a survival player is built directly instead.
 */
public abstract class PlayerStub extends Player {

    public static Player create(Level level) {
        try {
            return Generated.TYPE.getDeclaredConstructor(Level.class, GameProfile.class)
                .newInstance(level, new GameProfile(UUID.randomUUID(), "mcdata"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate the player stub", e);
        }
    }

    public PlayerStub(Level level, GameProfile profile) {
        super(level, profile);
    }

    @Override public GameType gameMode() {return GameType.SURVIVAL;}

    private static final class Generated {
        static final Class<? extends PlayerStub> TYPE = new ByteBuddy()
            .subclass(PlayerStub.class, ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
            .method(ElementMatchers.isAbstract())
            .intercept(StubMethod.INSTANCE)
            .make()
            .load(PlayerStub.class.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(MethodHandles.lookup()))
            .getLoaded();
    }
}
