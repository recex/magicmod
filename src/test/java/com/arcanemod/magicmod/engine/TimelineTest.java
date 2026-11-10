package com.arcanemod.magicmod.engine;

import com.arcanemod.magicmod.engine.anim.Timeline;
import com.arcanemod.magicmod.engine.math.Curve;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTest {

    @Test
    void eventsFireOnceInTimeOrder() {
        List<String> log = new ArrayList<>();
        Timeline t = new Timeline().event(0.5f, () -> log.add("b")).event(0.0f, () -> log.add("a")).event(1.0f, () -> log.add("c"));
        t.advance(0.0f);
        assertEquals(List.of("a"), log, "eventos em t=0 disparam na primeira chamada");
        t.advance(0.4f);
        assertEquals(List.of("a"), log);
        t.advance(0.2f);
        assertEquals(List.of("a", "b"), log);
        assertFalse(t.isFinished());
        t.advance(10.0f);
        assertEquals(List.of("a", "b", "c"), log);
        assertTrue(t.isFinished());
        t.advance(10.0f);
        assertEquals(3, log.size(), "nada dispara depois do fim");
    }

    @Test
    void oneBigStepFiresEverythingCrossed() {
        List<Integer> log = new ArrayList<>();
        Timeline t = new Timeline();
        for (int i = 0; i < 5; i++) {
            int n = i;
            t.event(i * 0.1f, () -> log.add(n));
        }
        t.advance(3.0f);
        assertEquals(List.of(0, 1, 2, 3, 4), log);
    }

    @Test
    void tracksFollowTimeAndSpeed() {
        Timeline t = new Timeline().track("x", Curve.line(0.0f, 10.0f)).duration(2.0f).speed(2.0f);
        assertTrue(t.hasTrack("x"));
        assertEquals(-1.0f, t.value("nao existe", -1.0f), 0.0f);
        t.advance(0.25f);
        assertEquals(0.5f, t.time(), 1.0e-5f);
        assertEquals(0.25f, t.progress(), 1.0e-5f);
        assertEquals(5.0f, t.value("x", 0.0f), 1.0e-4f);
    }

    @Test
    void loopingRestartsAndRefiresEvents() {
        int[] count = {0};
        Timeline t = new Timeline().event(0.5f, () -> count[0]++).duration(1.0f).looping(true);
        t.advance(0.6f);
        assertEquals(1, count[0]);
        t.advance(1.0f);
        assertEquals(2, count[0]);
        assertFalse(t.isFinished());
        t.stop();
        assertTrue(t.isFinished());
    }

    @Test
    void aFailingEventDoesNotSwallowTheOthers() {
        List<String> log = new ArrayList<>();
        boolean[] finished = {false};
        Timeline t = new Timeline().event(0.1f, () -> {
            throw new IllegalStateException("falha de proposito");
        }).event(0.2f, () -> log.add("depois")).onFinish(() -> finished[0] = true);
        assertThrows(IllegalStateException.class, () -> t.advance(1.0f));
        assertEquals(List.of("depois"), log);
        assertTrue(finished[0]);
    }
}
