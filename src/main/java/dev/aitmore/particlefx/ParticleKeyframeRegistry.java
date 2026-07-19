package dev.aitmore.particlefx;

import java.util.List;
import java.util.Map;

import net.minecraft.util.Identifier;

/**
 * Holds the currently-loaded particle keyframe lists, keyed by the demat/mat animation id
 * (e.g. "ait-more:flatline_mat") they belong to. Repopulated whenever datapacks reload, by
 * ParticleKeyframeLoader.
 */
public class ParticleKeyframeRegistry {

    private static volatile Map<Identifier, List<ParticleKeyframe>> keyframesByAnimation = Map.of();

    static void set(Map<Identifier, List<ParticleKeyframe>> loaded) {
        keyframesByAnimation = Map.copyOf(loaded);
    }

    public static List<ParticleKeyframe> get(Identifier animationId) {
        return keyframesByAnimation.getOrDefault(animationId, List.of());
    }
}
