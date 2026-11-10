package com.arcanemod.magicmod.engine;

import com.arcanemod.magicmod.engine.math.ColorRamp;
import com.arcanemod.magicmod.engine.math.CubicBezier;
import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.math.Easing;
import com.arcanemod.magicmod.engine.math.MathUtil;
import com.arcanemod.magicmod.engine.math.Noise;
import com.arcanemod.magicmod.engine.math.SplinePath;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathTest {

    @Test
    void everyEasingStartsAtZeroAndEndsAtOne() {
        for (Easing e : Easing.values()) {
            assertEquals(0.0f, e.apply(0.0f), 1.0e-3f, e + " em 0");
            assertEquals(1.0f, e.apply(1.0f), 1.0e-3f, e + " em 1");
            for (int i = 0; i <= 20; i++) {
                assertTrue(Float.isFinite(e.apply(i / 20.0f)), e + " finito");
            }
        }
    }

    @Test
    void easingClampsItsInputAndIsFoundByName() {
        assertEquals(0.0f, Easing.OUT_CUBIC.apply(-3.0f), 1.0e-6f);
        assertEquals(1.0f, Easing.OUT_CUBIC.apply(7.0f), 1.0e-6f);
        assertSame(Easing.OUT_CUBIC, Easing.byName("out_cubic"));
        assertSame(Easing.LINEAR, Easing.byName("nao existe"));
        assertEquals(15.0f, Easing.LINEAR.lerp(10.0f, 20.0f, 0.5f), 1.0e-5f);
    }

    @Test
    void cubicBezierMatchesItsEndpointsAndIsMonotonic() {
        CubicBezier b = CubicBezier.EASE_IN_OUT;
        assertEquals(0.0f, b.evaluate(0.0f), 1.0e-4f);
        assertEquals(1.0f, b.evaluate(1.0f), 1.0e-4f);
        assertEquals(0.5f, b.evaluate(0.5f), 1.0e-3f);
        float last = 0.0f;
        for (int i = 1; i <= 50; i++) {
            float v = b.evaluate(i / 50.0f);
            assertTrue(v >= last - 1.0e-4f, "monotonica em " + i);
            last = v;
        }
    }

    @Test
    void curveInterpolatesAndClampsOutsideItsKeys() {
        Curve c = Curve.of(0.0f, 1.0f, 1.0f, 3.0f, 2.0f, -1.0f);
        assertEquals(3, c.keyCount());
        assertEquals(2.0f, c.duration(), 1.0e-6f);
        assertEquals(1.0f, c.sample(-5.0f), 1.0e-6f);
        assertEquals(2.0f, c.sample(0.5f), 1.0e-5f);
        assertEquals(3.0f, c.sample(1.0f), 1.0e-5f);
        assertEquals(1.0f, c.sample(1.5f), 1.0e-5f);
        assertEquals(-1.0f, c.sample(99.0f), 1.0e-6f);
        assertEquals(6.0f, c.scaled(2.0f).sample(1.0f), 1.0e-5f);
        assertEquals(3.0f, c.sample(1.0f), 1.0e-5f, "scaled nao altera a original");
    }

    @Test
    void curveFactoriesBehaveAsDocumented() {
        assertEquals(4.5f, Curve.constant(4.5f).sample(0.37f), 1.0e-6f);
        assertEquals(15.0f, Curve.line(10.0f, 20.0f).sample(0.5f), 1.0e-5f);
        Curve pulse = Curve.pulse(0.2f, 0.4f);
        assertEquals(0.0f, pulse.sample(0.0f), 1.0e-5f);
        assertEquals(1.0f, pulse.sample(0.2f), 1.0e-5f);
        assertEquals(1.0f, pulse.sample(0.5f), 1.0e-5f);
        assertEquals(0.0f, pulse.sample(1.0f), 1.0e-5f);
        assertTrue(pulse.sample(0.8f) > 0.0f && pulse.sample(0.8f) < 1.0f);
        Curve eased = Curve.builder().key(0.0f, 0.0f).key(1.0f, 10.0f, Easing.IN_QUAD).build();
        assertEquals(2.5f, eased.sample(0.5f), 1.0e-4f);
    }

    @Test
    void mathUtilHelpers() {
        assertEquals(0.5f, MathUtil.smoothstep(0.0f, 1.0f, 0.5f), 1.0e-6f);
        assertEquals(0.0f, MathUtil.smoothstep(2.0f, 4.0f, 1.0f), 1.0e-6f);
        assertEquals(1.0f, MathUtil.saturate(3.0f), 0.0f);
        assertEquals(5.0f, MathUtil.damp(0.0f, 10.0f, 1.0f, 1.0f), 1.0e-4f, "meia-vida");
        assertEquals(10.0f, MathUtil.damp(0.0f, 10.0f, 0.0f, 0.016f), 0.0f);
        assertEquals(20.0f, MathUtil.angleDelta(350.0f, 10.0f), 1.0e-4f, "pelo caminho curto");
        assertEquals(-20.0f, MathUtil.angleDelta(10.0f, 350.0f), 1.0e-4f);
        assertEquals(360.0f, MathUtil.lerpAngle(350.0f, 10.0f, 0.5f), 1.0e-4f);
        assertEquals(0.25f, MathUtil.inverseLerp(0.0f, 8.0f, 2.0f), 1.0e-6f);
        float[] rgb = MathUtil.rgb(0xFF8000);
        assertEquals(1.0f, rgb[0], 1.0e-6f);
        assertEquals(128.0f / 255.0f, rgb[1], 1.0e-6f);
        assertEquals(0.0f, rgb[2], 1.0e-6f);
    }

    @Test
    void noiseIsDeterministicBoundedAndVaries() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < 2000; i++) {
            float x = i * 0.173f;
            float v = Noise.noise3(x, x * 0.7f, -x * 1.3f, 42);
            assertEquals(v, Noise.noise3(x, x * 0.7f, -x * 1.3f, 42), 0.0f);
            min = Math.min(min, v);
            max = Math.max(max, v);
            float r = Noise.random(i, 7);
            assertTrue(r >= 0.0f && r < 1.0f, "random em [0,1)");
        }
        assertTrue(min >= -1.001f && max <= 1.001f, "ruido limitado: " + min + ".." + max);
        assertTrue(max - min > 0.5f, "ruido varia");
        assertNotEquals(Noise.noise1(3.3f, 1), Noise.noise1(3.3f, 2), "sementes diferentes");
    }

    @Test
    void splineIsParameterisedByArcLength() {
        SplinePath line = new SplinePath(0, 0, 0, 1, 0, 0, 4, 0, 0, 10, 0, 0);
        assertEquals(10.0f, line.length(), 0.05f);
        Vector3d p = new Vector3d();
        assertEquals(0.0, line.position(0.0f, p).x, 1.0e-3);
        assertEquals(5.0, line.position(0.5f, p).x, 0.08, "metade do comprimento, apesar dos pontos desiguais");
        assertEquals(10.0, line.position(1.0f, p).x, 1.0e-3);
        assertEquals(1.0, line.tangent(0.5f, p).x, 1.0e-2);
    }

    @Test
    void colorRampInterpolatesBetweenStops() {
        ColorRamp ramp = ColorRamp.of(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 2.0f, 4.0f, 6.0f, 0.0f);
        float[] out = ramp.sample(0.5f, new float[4]);
        assertEquals(1.0f, out[0], 1.0e-5f);
        assertEquals(2.0f, out[1], 1.0e-5f);
        assertEquals(3.0f, out[2], 1.0e-5f);
        assertEquals(0.5f, out[3], 1.0e-5f);
        assertEquals(2.0f, ramp.sample(5.0f, out)[0], 1.0e-5f);
    }
}
