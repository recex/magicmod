package com.arcanemod.magicmod.engine.anim;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Transformacao 3D (translacao, rotacao, escala) de um objeto animado. A
 * translacao e relativa a ancora do efeito, entao cabe em float sem perder
 * precisao longe da origem do mundo.
 */
public final class Transform3D {

    public final Vector3f position = new Vector3f();
    public final Quaternionf rotation = new Quaternionf();
    public final Vector3f scale = new Vector3f(1.0f);

    public Transform3D identity() {
        position.zero();
        rotation.identity();
        scale.set(1.0f);
        return this;
    }

    public Transform3D set(Transform3D other) {
        position.set(other.position);
        rotation.set(other.rotation);
        scale.set(other.scale);
        return this;
    }

    public Transform3D translate(float x, float y, float z) {
        position.add(x, y, z);
        return this;
    }

    /** Acrescenta uma rotacao de {@code radians} em torno do eixo (normalizado internamente). */
    public Transform3D rotate(float radians, float ax, float ay, float az) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len > 1.0e-6f) {
            rotation.rotateAxis(radians, ax / len, ay / len, az / len);
        }
        return this;
    }

    public Transform3D scale(float uniform) {
        scale.mul(uniform);
        return this;
    }

    public Transform3D scale(float x, float y, float z) {
        scale.mul(x, y, z);
        return this;
    }

    /** Interpola entre {@code a} e {@code b}: lerp na posicao/escala e slerp na rotacao. */
    public Transform3D lerp(Transform3D a, Transform3D b, float t) {
        a.position.lerp(b.position, t, position);
        a.scale.lerp(b.scale, t, scale);
        a.rotation.slerp(b.rotation, t, rotation);
        return this;
    }

    /** Matriz modelo = T * R * S. */
    public Matrix4f toMatrix(Matrix4f dest) {
        return dest.translationRotateScale(position, rotation, scale);
    }
}
