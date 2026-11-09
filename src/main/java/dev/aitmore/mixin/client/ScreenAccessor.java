package dev.aitmore.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;

/**
 * Exposes Screen's protected addDrawableChild/textRenderer to InteriorSettingsScreenMixin.
 * @Shadow can't be used for these from there: Mixin's shadow validator only resolves members
 * actually DECLARED on the mixin's target class (InteriorSettingsScreen), not ones merely
 * inherited from a superclass several levels up (Screen) - it fails at mixin-apply time with
 * "was not located in the target class", not at compile time, which otherwise looks like a
 * silent success until the screen is actually opened. @Invoker/@Accessor targeting Screen
 * directly, where these genuinely are declared, don't have that problem.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addDrawableChild")
    <T extends Element & Drawable & Selectable> T aitmore$addDrawableChild(T drawable);

    @Accessor("textRenderer")
    TextRenderer aitmore$getTextRenderer();
}
