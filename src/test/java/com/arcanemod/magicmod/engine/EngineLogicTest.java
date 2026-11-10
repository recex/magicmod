package com.arcanemod.magicmod.engine;

import com.arcanemod.magicmod.engine.cinematic.CameraShot;
import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.perf.PerfMonitor;
import com.arcanemod.magicmod.engine.perf.Quality;
import com.arcanemod.magicmod.engine.render.PostState;
import com.arcanemod.magicmod.engine.shader.ShaderPreprocessor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineLogicTest {

    private static ShaderPreprocessor.SourceLoader loader(Map<String, String> files) {
        return files::get;
    }

    @Test
    void includesAreExpandedOnceEvenWhenRepeatedOrNested() throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("main.fsh", "#include \"lib/a.glsl\"\n#include \"lib/b.glsl\"\n  #  include \"lib/a.glsl\"\nvoid main() {}\n");
        files.put("lib/a.glsl", "float a;\n");
        files.put("lib/b.glsl", "#include \"lib/a.glsl\"\r\nfloat b;\r\n");
        String out = ShaderPreprocessor.process("main.fsh", loader(files));
        assertEquals("float a;\n\nfloat b;\n\nvoid main() {}\n\n", out);
    }

    @Test
    void missingIncludeAndCyclesAndVersionAreRejected() {
        Map<String, String> files = new HashMap<>();
        files.put("missing.fsh", "#include \"nope.glsl\"\n");
        files.put("version.fsh", "#version 330\nvoid main() {}\n");
        files.put("self.fsh", "#include \"self.fsh\"\nfloat x;\n");
        assertThrows(IOException.class, () -> ShaderPreprocessor.process("missing.fsh", loader(files)));
        assertThrows(IOException.class, () -> ShaderPreprocessor.process("version.fsh", loader(files)));
        assertEquals("float x;\n\n", assertDoesNotThrowAndGet(files));
    }

    private static String assertDoesNotThrowAndGet(Map<String, String> files) {
        try {
            return ShaderPreprocessor.process("self.fsh", loader(files));
        } catch (IOException e) {
            throw new AssertionError("um arquivo que inclui a si mesmo deve ser ignorado, nao falhar", e);
        }
    }

    @Test
    void headerListsVersionThenDefines() {
        assertEquals("#version 150\n#define VFX_STEPS 26\n#define DISTORT_PASS\n", ShaderPreprocessor.header("150", "VFX_STEPS 26", "", null, "DISTORT_PASS"));
        assertEquals("#version 150\n", ShaderPreprocessor.header("150"));
    }

    @Test
    void qualityLevelsAreOrderedByCost() {
        Quality[] all = Quality.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(all[i].raySteps > all[i - 1].raySteps);
            assertTrue(all[i].bufferScale >= all[i - 1].bufferScale);
            assertTrue(all[i].particleScale > all[i - 1].particleScale);
            assertTrue(all[i].particleCapacity > all[i - 1].particleCapacity);
        }
        assertSame(Quality.LOW, Quality.LOW.lower());
        assertSame(Quality.ULTRA, Quality.ULTRA.higher());
        assertSame(Quality.MEDIUM, Quality.byName(" Medium ", Quality.LOW));
        assertSame(Quality.LOW, Quality.byName("auto", Quality.LOW));
        assertSame(null, Quality.byName(null, null));
    }

    @Test
    void perfMonitorDropsQuicklyAndRecoversSlowly() {
        PerfMonitor perf = new PerfMonitor(Quality.HIGH, Quality.HIGH);
        // Quadros de 50 ms (20 fps): tem de cair de nivel em poucos segundos.
        for (int i = 0; i < 60; i++) {
            perf.frame(0.05f, true);
        }
        assertTrue(perf.current().ordinal() < Quality.HIGH.ordinal(), "caiu: " + perf.current());
        Quality afterDrop = perf.current();
        // Quadros ociosos (sem efeitos) nao contam para nada.
        for (int i = 0; i < 2000; i++) {
            perf.frame(0.2f, false);
        }
        assertSame(afterDrop, perf.current());
        // Quadros de 5 ms: sobe de volta, mas nunca acima do teto.
        for (int i = 0; i < 20000; i++) {
            perf.frame(0.005f, true);
        }
        assertSame(Quality.HIGH, perf.current());
    }

    @Test
    void perfMonitorHoldsSteadyInTheComfortZone() {
        PerfMonitor perf = new PerfMonitor(Quality.ULTRA, Quality.HIGH);
        assertSame(Quality.HIGH, perf.current(), "comeca limitado ao teto");
        for (int i = 0; i < 5000; i++) {
            perf.frame(0.0167f, true);
        }
        assertSame(Quality.HIGH, perf.current(), "60 fps nao muda nada");
    }

    @Test
    void cameraShotBlendsInAndOutAndOrbitsItsTarget() {
        CameraShot shot = new CameraShot(10.0, 64.0, -5.0, 3.0f).distance(Curve.constant(8.0f)).height(Curve.constant(2.0f))
                .orbit(Curve.of(0.0f, 0.0f, 3.0f, 90.0f)).blend(0.5f, 0.5f);
        assertEquals(0.0f, shot.weight(0.0f), 1.0e-5f);
        assertEquals(1.0f, shot.weight(1.5f), 1.0e-5f);
        assertEquals(0.0f, shot.weight(3.0f), 1.0e-5f);
        assertTrue(shot.weight(0.25f) > 0.0f && shot.weight(0.25f) < 1.0f);
        double[] p = new double[3];
        shot.position(0.0f, 0.0f, p);
        assertEquals(10.0, p[0], 1.0e-4);
        assertEquals(66.0, p[1], 1.0e-4);
        assertEquals(3.0, p[2], 1.0e-4, "angulo 0 = lado +Z do alvo");
        shot.position(3.0f, 0.0f, p);
        assertEquals(18.0, p[0], 1.0e-4, "depois de girar 90 graus = lado +X");
        assertEquals(-5.0, p[2], 1.0e-4);
        for (float t = 0.0f; t <= 3.0f; t += 0.1f) {
            shot.position(t, 37.0f, p);
            double d = Math.hypot(p[0] - 10.0, p[2] + 5.0);
            assertEquals(8.0, d, 1.0e-3, "mantem a distancia ao alvo");
        }
    }

    @Test
    void postStateKeepsTheMostRelevantLightsWhenOverBudget() {
        PostState post = new PostState();
        post.reset(2);
        assertTrue(post.isNeutral());
        post.light(0.0f, 0.0f, -5.0f, 6.0f, 1.0f, 1.0f, 1.0f);
        post.light(0.0f, 0.0f, -5.0f, 6.0f, 0.2f, 0.2f, 0.2f);
        post.light(1.0f, 0.0f, -5.0f, 6.0f, 9.0f, 5.0f, 2.0f);
        assertEquals(2, post.lightCount);
        assertFalse(post.isNeutral());
        float strongest = Math.max(post.lightColor.get(0), post.lightColor.get(4));
        float weakest = Math.min(post.lightColor.get(0), post.lightColor.get(4));
        assertEquals(9.0f, strongest, 0.0f, "a luz forte entrou");
        assertEquals(1.0f, weakest, 0.0f, "a mais fraca saiu");
        post.light(0.0f, 0.0f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f);
        assertEquals(2, post.lightCount, "luz apagada e ignorada");
        post.reset(0);
        post.light(0.0f, 0.0f, -5.0f, 6.0f, 1.0f, 1.0f, 1.0f);
        assertEquals(0, post.lightCount, "luzes desligadas");
        assertTrue(post.isNeutral());
    }

    @Test
    void postStateKeepsTheStrongestFlashAndResets() {
        PostState post = new PostState();
        post.reset(8);
        post.flash(1.0f, 0.0f, 0.0f, 0.3f);
        post.flash(0.0f, 1.0f, 0.0f, 0.8f);
        post.flash(0.0f, 0.0f, 1.0f, 0.5f);
        assertEquals(0.8f, post.flashA, 0.0f);
        assertEquals(1.0f, post.flashG, 0.0f);
        post.exposure *= 0.5f;
        post.radialBlur(0.2f, 0.7f, 0.4f);
        assertFalse(post.isNeutral());
        post.reset(8);
        assertTrue(post.isNeutral());
        assertEquals(1.0f, post.exposure, 0.0f);
        assertEquals(0.5f, post.radialX, 0.0f);
    }
}
