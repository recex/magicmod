package com.arcanemod.magicmod.engine.render;

import com.arcanemod.magicmod.engine.perf.Quality;
import org.joml.FrustumIntersection;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Tudo o que um efeito precisa saber sobre o quadro atual: camera, matrizes,
 * relogio e qualidade. As posicoes enviadas a GPU sao sempre relativas a
 * camera, para manter a precisao em coordenadas distantes da origem do mundo.
 */
public final class FrameContext {

    public double camX;
    public double camY;
    public double camZ;
    public final Matrix4f viewProj = new Matrix4f();
    public final Matrix4f invViewProj = new Matrix4f();
    public final Matrix4f prevViewProj = new Matrix4f();
    public final Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
    public final Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
    public final Vector3f forward = new Vector3f(0.0f, 0.0f, -1.0f);
    /** Deslocamento da camera desde o quadro anterior. */
    public final Vector3f camDelta = new Vector3f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f view = new Matrix4f();
    private boolean hasPrevious;
    private double prevX;
    private double prevY;
    private double prevZ;

    /** Segundos de jogo desde que o motor foi ligado (para quando o jogo pausa). */
    public float time;
    /** Segundos desde o quadro anterior. */
    public float dt;
    public float partialTick;
    public int width;
    public int height;
    public long frameId;
    public Quality quality = Quality.HIGH;

    /**
     * Atualiza as matrizes. {@code inverseViewRotation} e a rotacao da camera
     * que o jogo publica; {@code projection} ja inclui o balanco da caminhada.
     */
    public void setCamera(double x, double y, double z, Matrix4f projection, Matrix3f inverseViewRotation) {
        if (hasPrevious) {
            prevViewProj.set(viewProj);
            camDelta.set((float) (x - prevX), (float) (y - prevY), (float) (z - prevZ));
        }
        camX = x;
        camY = y;
        camZ = z;
        view.identity().set3x3(new Matrix3f(inverseViewRotation).transpose());
        projection.mul(view, viewProj);
        viewProj.invert(invViewProj);
        inverseViewRotation.transform(right.set(1.0f, 0.0f, 0.0f));
        inverseViewRotation.transform(up.set(0.0f, 1.0f, 0.0f));
        inverseViewRotation.transform(forward.set(0.0f, 0.0f, -1.0f));
        frustum.set(viewProj);
        if (!hasPrevious) {
            prevViewProj.set(viewProj);
            camDelta.zero();
            hasPrevious = true;
        }
        prevX = x;
        prevY = y;
        prevZ = z;
    }

    /** Esquece o quadro anterior (troca de mundo, teleporte). */
    public void resetHistory() {
        hasPrevious = false;
    }

    /** Verdadeiro se a esfera (em coordenadas do mundo) pode aparecer na tela. */
    public boolean sphereVisible(double x, double y, double z, float radius) {
        return frustum.testSphere((float) (x - camX), (float) (y - camY), (float) (z - camZ), radius);
    }

    public float distanceTo(double x, double y, double z) {
        double dx = x - camX;
        double dy = y - camY;
        double dz = z - camZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
