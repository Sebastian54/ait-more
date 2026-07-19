package dev.aitmore.config;

/**
 * Plain data holder, loaded/saved as JSON by AitMoreConfigLoader. Field defaults here are what
 * gets written out the very first time the mod runs with no config file present yet - every
 * field is deliberately public with no validation, since the loader treats a missing or
 * unreadable file as "use defaults" rather than crashing.
 */
public class AitMoreConfig {

    // --- particlefx: demat/mat keyframe particles, screen shake, knockback ---

    public boolean particlesEnabled = true;
    public boolean shakeEnabled = true;
    public boolean knockbackEnabled = true;

    /** Keyframe-triggered fog/blindness effect (see dev.aitmore.mixin.client.BackgroundRendererMixin), replicating AIT's own power-off vision fog on demand. */
    public boolean visionEnabled = true;

    /** How long (seconds) an active vision override takes to fade back to normal whenever it's cancelled outright - animation ending naturally, or the handbrake cutting a flight short - rather than snapping back instantly. 0 = instant. */
    public float visionResetFadeSeconds = 0.5F;

    /** Multiplies every keyframe's own "shake" value - lets a server retune intensity globally without editing every particle_keyframes file. */
    public float shakeIntensityMultiplier = 1.0F;

    /** Multiplies every keyframe's own "knockback" value, same idea as shakeIntensityMultiplier. */
    public float knockbackStrengthMultiplier = 1.0F;

    /** Hard ceiling applied AFTER the multiplier above - there's no other limit anywhere else, so a typo in a keyframe's "knockback" value (or in this multiplier) can't send a player flying uncontrollably. */
    public float maxKnockbackStrength = 5.0F;

    // --- exterior light block (see dev.aitmore.light) ---

    public boolean exteriorLightEnabled = true;

    // --- misc ---

    public boolean jellyBabySoundEnabled = true;
}
