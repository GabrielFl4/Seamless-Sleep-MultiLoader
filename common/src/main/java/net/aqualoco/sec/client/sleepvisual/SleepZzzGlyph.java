package net.aqualoco.sec.client.sleepvisual;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

// Represents one visible Z from spawn through drift, fade, and removal.
public final class SleepZzzGlyph {

    private static final float CARTOON_ROTATION_MIN_DEG = -30.0F;
    private static final float CARTOON_ROTATION_MAX_DEG = 30.0F;
    private static final int CARTOON_ROTATION_CHANGE_MIN_TICKS = 8;
    private static final int CARTOON_ROTATION_CHANGE_MAX_TICKS = 18;
    private static final float CARTOON_ROTATION_LERP = 0.25F;
    private static final double SEQUENTIAL_VERTICAL_SPEED = 0.018D;
    private static final double SEQUENTIAL_FORWARD_SPEED = 0.005D;

    private final SleepZzzStyle style;
    private final Vec3 origin;
    private final Vec3 forward;
    private final Vec3 side;
    private final int lifetime;
    private final float baseScale;
    private final double phase;
    private final double verticalSpeed;
    private final double forwardSpeed;
    private final double wobbleAmplitude;
    private final double wobbleFrequency;

    private int age;
    private Vec3 previousPosition;
    private Vec3 position;
    private float rotationDegrees;
    private float targetRotationDegrees;
    private int nextRotationChangeAge;

    public SleepZzzGlyph(SleepZzzStyle style, SleepZzzPositioning.Spawn spawn, Random random) {
        this.style = style;
        this.origin = spawn.origin();
        this.forward = spawn.forward();
        this.side = spawn.side();
        this.lifetime = style == SleepZzzStyle.SEQUENTIAL_TRAIL
                ? 110 + random.nextInt(31)
                : 100 + random.nextInt(36);
        this.baseScale = style == SleepZzzStyle.SEQUENTIAL_TRAIL
                ? 0.85F + random.nextFloat() * 0.30F
                : 0.85F + random.nextFloat() * 0.40F;
        this.phase = random.nextDouble() * Math.PI * 2.0D;
        this.verticalSpeed = style == SleepZzzStyle.SEQUENTIAL_TRAIL ? SEQUENTIAL_VERTICAL_SPEED : 0.012D;
        this.forwardSpeed = style == SleepZzzStyle.SEQUENTIAL_TRAIL ? SEQUENTIAL_FORWARD_SPEED : 0.005D;
        this.wobbleAmplitude = style == SleepZzzStyle.SEQUENTIAL_TRAIL ? 0.014D : 0.035D;
        this.wobbleFrequency = style == SleepZzzStyle.SEQUENTIAL_TRAIL ? 0.12D : 0.10D;
        this.rotationDegrees = style == SleepZzzStyle.SEQUENTIAL_TRAIL
                ? -5.0F + random.nextFloat() * 10.0F
                : randomRotation(random);
        this.targetRotationDegrees = this.rotationDegrees;
        this.nextRotationChangeAge = nextRotationAge(random);
        this.previousPosition = origin;
        this.position = origin;
    }

    public void tick(Random random) {
        this.previousPosition = this.position;
        this.age++;
        this.position = computePosition(this.age);

        if (this.style == SleepZzzStyle.CARTOON_DRIFT) {
            if (this.age >= this.nextRotationChangeAge) {
                this.targetRotationDegrees = randomRotation(random);
                this.nextRotationChangeAge = this.age + nextRotationAge(random);
            }
            this.rotationDegrees += Mth.wrapDegrees(this.targetRotationDegrees - this.rotationDegrees) * CARTOON_ROTATION_LERP;
        }
    }

    public boolean isAlive() {
        return this.age < this.lifetime;
    }

    public Vec3 renderPosition(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.previousPosition.x(), this.position.x()),
                Mth.lerp(partialTick, this.previousPosition.y(), this.position.y()),
                Mth.lerp(partialTick, this.previousPosition.z(), this.position.z())
        );
    }

    public float alpha(float partialTick) {
        float resolvedAge = this.age + partialTick;
        float fadeIn = Mth.clamp(resolvedAge / 8.0F, 0.0F, 1.0F);
        float fadeStart = this.lifetime * 0.68F;
        if (resolvedAge <= fadeStart) {
            return fadeIn;
        }
        float fadeOut = 1.0F - Mth.clamp((resolvedAge - fadeStart) / (this.lifetime - fadeStart), 0.0F, 1.0F);
        return fadeIn * fadeOut;
    }

    public float scale(float partialTick) {
        float resolvedAge = this.age + partialTick;
        float grow = 0.72F + Mth.clamp(resolvedAge / 12.0F, 0.0F, 1.0F) * 0.28F;
        return this.baseScale * grow;
    }

    public float rotationDegrees() {
        return this.rotationDegrees;
    }

    private Vec3 computePosition(int resolvedAge) {
        double vertical = resolvedAge * this.verticalSpeed;
        double forwardDrift = resolvedAge * this.forwardSpeed;
        double wobble = Math.sin(resolvedAge * this.wobbleFrequency + this.phase) * this.wobbleAmplitude;
        return this.origin
                .add(0.0D, vertical, 0.0D)
                .add(this.forward.scale(forwardDrift))
                .add(this.side.scale(wobble));
    }

    private static float randomRotation(Random random) {
        return CARTOON_ROTATION_MIN_DEG + random.nextFloat() * (CARTOON_ROTATION_MAX_DEG - CARTOON_ROTATION_MIN_DEG);
    }

    private static int nextRotationAge(Random random) {
        return CARTOON_ROTATION_CHANGE_MIN_TICKS
                + random.nextInt(CARTOON_ROTATION_CHANGE_MAX_TICKS - CARTOON_ROTATION_CHANGE_MIN_TICKS + 1);
    }
}
