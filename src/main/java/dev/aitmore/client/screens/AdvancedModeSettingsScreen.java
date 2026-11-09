package dev.aitmore.client.screens;

import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import dev.aitmore.AitMoreNetworking;
import dev.amble.ait.client.screens.ConsoleScreen;
import dev.amble.ait.client.tardis.ClientTardis;

/**
 * Reached via the "Advanced Mode Settings" button this mod adds under Sonic Settings in the
 * monitor's interior settings screen (see dev.aitmore.client.AitMoreClient's
 * TardisClientEvents.SETTINGS_SETUP hook). Lets a player flip Advanced Mode on/off for this
 * TARDIS - see dev.aitmore.advancedmode.AdvancedModeManager for what that actually does.
 */
public class AdvancedModeSettingsScreen extends ConsoleScreen {

    private final Screen parent;

    /** null until the server's state packet arrives - the toggle button stays disabled/"Loading..." until then. */
    private Boolean enabled;

    private ButtonWidget toggleButton;

    public AdvancedModeSettingsScreen(ClientTardis tardis, BlockPos console, Screen parent) {
        super(Text.translatable("screen.ait-more.advancedmode.title"), tardis, console);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.toggleButton = ButtonWidget.builder(Text.translatable("screen.ait-more.advancedmode.loading"), button -> this.toggle())
                .dimensions(centerX - 75, centerY - 10, 150, 20)
                .build();
        this.toggleButton.active = false;
        this.addDrawableChild(this.toggleButton);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.ait-more.advancedmode.back"),
                button -> this.client.setScreen(this.parent))
                .dimensions(centerX - 75, centerY + 16, 150, 20)
                .build());

        this.requestState();
    }

    private void requestState() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(this.tardis().getUuid());
        ClientPlayNetworking.send(AitMoreNetworking.ADVANCED_MODE_REQUEST_STATE_PACKET, buf);
    }

    private void toggle() {
        if (this.enabled == null)
            return;

        boolean next = !this.enabled;

        // Optimistic update - the server echoes the state back regardless (see onStateReceived),
        // so a dropped/slow packet self-corrects instead of leaving the button permanently wrong.
        this.setEnabled(next);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(this.tardis().getUuid());
        buf.writeBoolean(next);
        ClientPlayNetworking.send(AitMoreNetworking.ADVANCED_MODE_SET_PACKET, buf);
    }

    /** Called by AitMoreClient's packet receiver when this is the open screen. */
    public void onStateReceived(UUID tardisId, boolean enabled) {
        if (!tardisId.equals(this.tardis().getUuid()))
            return;

        this.setEnabled(enabled);
    }

    private void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.toggleButton.active = true;
        this.toggleButton.setMessage(Text.translatable(enabled
                ? "screen.ait-more.advancedmode.on"
                : "screen.ait-more.advancedmode.off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.ait-more.advancedmode.description"), this.width / 2, this.height / 2 - 24, 0xAAAAAA);
    }
}
