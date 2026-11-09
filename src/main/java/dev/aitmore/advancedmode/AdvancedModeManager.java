package dev.aitmore.advancedmode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import dev.amble.ait.core.blockentities.ConsoleBlockEntity;
import dev.amble.ait.core.entities.ConsoleControlEntity;
import dev.amble.ait.core.tardis.Tardis;
import dev.amble.ait.core.tardis.control.Control;

/**
 * Server-side gate behind Advanced Mode: when a TARDIS has it enabled, pressing a control marks
 * a fixed combo of OTHER controls on the same console with AIT's own native in-world sequence
 * markers (ConsoleControlEntity's partOfSequence/sequenceIndex/sequenceLength/wasSequenced
 * tracked data - the same fields AIT's own random in-flight events use, so the hover marker and
 * its red-to-green progress tint are entirely native rendering, nothing custom here) - and
 * cancels the control's own action. Pressing the marked controls in that exact order advances
 * the combo; any other control press abandons it. Completing the combo replays the original
 * control's activation for real.
 *
 * One active combo per player at a time - a wrong press (or a fresh control press while nothing
 * matches) abandons whatever was in progress and, if that press's own control has a resolvable
 * combo on this console, starts a new one for it.
 */
public class AdvancedModeManager {

    /**
     * Never gated, and never used as a combo step for anything else (see
     * AdvancedModeSequences) - pressing these always behaves exactly as if Advanced Mode were
     * off, even mid-combo for some other control.
     */
    private static final Set<Identifier> EXEMPT_CONTROLS = Set.of(
            Identifier.of("ait", "handbrake"),
            Identifier.of("ait", "throttle"),
            Identifier.of("ait", "monitor"));

    private static final Map<UUID, ActiveSequence> ACTIVE = new ConcurrentHashMap<>();
    private static final Set<BypassKey> BYPASSED = ConcurrentHashMap.newKeySet();

    /** @return true if the caller should cancel this activation. */
    public static boolean shouldGate(Control control, Tardis tardis, ServerPlayerEntity player, ServerWorld world,
            BlockPos pos, boolean inside) {
        if (EXEMPT_CONTROLS.contains(control.id()))
            return false;

        UUID playerId = player.getUuid();

        if (BYPASSED.remove(new BypassKey(playerId, control.id(), pos)))
            return false;

        if (!AdvancedModeState.isEnabled(tardis.getUuid()))
            return false;

        ActiveSequence active = ACTIVE.get(playerId);

        if (active != null) {
            ConsoleControlEntity expected = active.nextExpected();

            if (expected.getControl().id().equals(control.id()) && expected.getConsoleBlockPos().equals(pos)) {
                expected.setWasSequenced(true);
                active.advance();

                if (active.isComplete()) {
                    ACTIVE.remove(playerId);
                    clearSequence(active);
                    replay(playerId, active.target());
                }

                return true;
            }

            ACTIVE.remove(playerId);
            clearSequence(active);
        }

        return beginSequence(control, tardis, player, world, pos, inside);
    }

    private static boolean beginSequence(Control control, Tardis tardis, ServerPlayerEntity player, ServerWorld world,
            BlockPos pos, boolean inside) {
        List<Identifier> comboIds = AdvancedModeSequences.getSequence(control.id());
        List<ConsoleControlEntity> entities = resolveEntities(world, pos, comboIds);

        // Nothing on this console to build the combo from - let it through rather than softlock.
        if (entities.isEmpty())
            return false;

        for (int i = 0; i < entities.size(); i++) {
            ConsoleControlEntity entity = entities.get(i);
            entity.setPartOfSequence(true);
            entity.setSequenceIndex(i);
            entity.setSequenceLength(entities.size());
            entity.setWasSequenced(false);
        }

        PendingControlActivation target = new PendingControlActivation(control, tardis, player, world, pos, inside);
        ACTIVE.put(player.getUuid(), new ActiveSequence(entities, target));
        return true;
    }

    private static List<ConsoleControlEntity> resolveEntities(ServerWorld world, BlockPos pos, List<Identifier> comboIds) {
        if (!(world.getBlockEntity(pos) instanceof ConsoleBlockEntity console))
            return List.of();

        List<ConsoleControlEntity> result = new ArrayList<>();

        for (Identifier comboId : comboIds) {
            for (ConsoleControlEntity entity : console.controlEntities) {
                if (entity.getControl().id().equals(comboId)) {
                    result.add(entity);
                    break;
                }
            }
        }

        return result;
    }

    private static void replay(UUID playerId, PendingControlActivation target) {
        BYPASSED.add(new BypassKey(playerId, target.control().id(), target.pos()));
        target.control().handleRun(target.tardis(), target.player(), target.world(), target.pos(), target.inside());
    }

    private static void clearSequence(ActiveSequence active) {
        for (ConsoleControlEntity entity : active.markedEntities()) {
            entity.setPartOfSequence(false);
            entity.setWasSequenced(false);
        }
    }

    /** Called on player disconnect so a half-finished combo doesn't leave markers stuck on the console forever. */
    public static void onPlayerDisconnect(UUID playerId) {
        ActiveSequence active = ACTIVE.remove(playerId);

        if (active != null)
            clearSequence(active);
    }
}
