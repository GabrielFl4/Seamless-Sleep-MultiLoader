package net.aqualoco.sec.mixin.client;

import net.neoforged.neoforge.client.gui.ModListScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// NeoForge 21.9.16-beta bugfix: ModListScreen.reloadMods() uses Stream#toList() (immutable) and tick() sorts in-place.
// We return a mutable list so clicking "Config" in the Mods screen does not crash with UnsupportedOperationException.
@Mixin(value = ModListScreen.class, remap = false)
public abstract class NeoForgeModListScreenMutableSortFixMixin {

    @Redirect(
            method = "reloadMods",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;toList()Ljava/util/List;"
            ),
            require = 0,
            remap = false
    )
    private List<?> seamlesssleep$reloadModsAsMutableList(Stream<?> stream) {
        return new ArrayList<>(stream.toList());
    }
}
