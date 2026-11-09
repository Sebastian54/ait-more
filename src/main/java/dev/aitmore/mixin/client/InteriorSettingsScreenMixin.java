package dev.aitmore.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.text.Text;

import dev.aitmore.client.screens.AdvancedModeSettingsScreen;
import dev.amble.ait.client.screens.interior.InteriorSettingsScreen;

/**
 * Adds the "Advanced Mode Settings" row under Sonic Settings in the monitor.
 *
 * This deliberately does NOT use AIT's own TardisClientEvents.SETTINGS_SETUP + createTextButton
 * (what an earlier version of this did) - InteriorSettingsScreen#render() draws the background
 * textures for its own icon buttons (apply/arrows/lock) by indexing straight into its internal
 * `buttons` list with hardcoded offsets (3, or 4 with the gravity compat addon), assuming
 * exactly that many text-row buttons were added first. createTextButton appends to that same
 * list, so adding a 4th text row via SETTINGS_SETUP shifts every icon button's index by one -
 * AIT then paints an icon button's background texture at whatever button now sits at the index
 * it expected, which in practice meant one of its own grey icon textures rendering on top of our
 * new row. Injecting at TAIL and using addDrawableChild (not AIT's own addButton, which inserts
 * into that same indexed list) sidesteps the whole indexing scheme - this widget renders and
 * receives clicks like any other, AIT just never counts it.
 *
 * addDrawableChild and textRenderer are reached through ScreenAccessor rather than @Shadow -
 * see that class for why.
 */
@Mixin(InteriorSettingsScreen.class)
public abstract class InteriorSettingsScreenMixin {

    @Shadow(remap = false)
    int left;

    @Shadow(remap = false)
    int top;

    @Shadow(remap = false)
    int bgWidth;

    @Shadow(remap = false)
    int bgHeight;

    @Inject(method = "createButtons()V", at = @At("TAIL"), remap = false)
    private void aitmore$addAdvancedModeButton(CallbackInfo ci) {
        InteriorSettingsScreen self = (InteriorSettingsScreen) (Object) this;
        ScreenAccessor accessor = (ScreenAccessor) (Object) this;
        TextRenderer textRenderer = accessor.aitmore$getTextRenderer();
        Text text = Text.translatable("screen.ait-more.advancedmode.button");
        int x = this.left + Math.round(this.bgWidth * 0.06F);
        int y = this.top + Math.round(this.bgHeight * 0.1F * 4F);
        int width = textRenderer.getWidth(text);

        accessor.aitmore$addDrawableChild(new PressableTextWidget(x, y, width, 10, text,
                button -> MinecraftClient.getInstance().setScreen(
                        new AdvancedModeSettingsScreen(self.tardis(), self.getConsole(), self)),
                textRenderer));
    }
}
