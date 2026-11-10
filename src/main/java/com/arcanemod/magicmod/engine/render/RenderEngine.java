package com.arcanemod.magicmod.engine.render;

import com.arcanemod.magicmod.engine.particle.GpuParticleSystem;
import com.arcanemod.magicmod.engine.perf.Quality;
import com.arcanemod.magicmod.engine.shader.ShaderLibrary;
import com.arcanemod.magicmod.engine.shader.ShaderProgram;
import com.arcanemod.magicmod.engine.vfx.ProxyElement;
import com.arcanemod.magicmod.engine.vfx.StripElement;
import com.arcanemod.magicmod.engine.vfx.VfxElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renderizador do motor. A cada quadro com efeitos ativos, depois que o jogo
 * termina de desenhar o mundo:
 * <ol>
 *   <li>desenha a luz dos efeitos num buffer HDR proprio, testando contra a
 *       profundidade do jogo com borda suave;</li>
 *   <li>desenha a distorcao espacial (lentes, ondas de choque) em meia resolucao;</li>
 *   <li>gera o bloom por uma piramide de reducoes e ampliacoes;</li>
 *   <li>compoe tudo com a imagem do jogo (distorcao, aberracao cromatica,
 *       desfoque radial, luzes dinamicas, gradacao) e devolve ao jogo.</li>
 * </ol>
 * O estado do OpenGL usado pelo Minecraft e restaurado no fim.
 */
public final class RenderEngine {

    private static final String[] FULLSCREEN_ATTRIBUTES = {"aPosition"};
    private static final String FULLSCREEN_VS = "fullscreen.vsh";

    private final ShaderLibrary shaders;
    private final VfxTarget radiance = new VfxTarget(true);
    private final VfxTarget distortion = new VfxTarget(true);
    private final VfxTarget output = new VfxTarget(false);
    private final VfxTarget[] bloom = new VfxTarget[6];
    private final DynamicMesh mesh = new DynamicMesh();
    private final Map<String, ShaderProgram[]> proxyPrograms = new HashMap<>();
    private final float[] points = new float[StripElement.MAX_POINTS * 3];
    private final float[] rows = new float[StripElement.MAX_POINTS * 8];
    private ShaderProgram stripProgram;
    private ShaderProgram particleProgram;
    private boolean fixedProgramsLoaded;
    private int proxySteps = -1;
    private int proxyGeneration = -1;
    private int fullscreenVao;
    private int fullscreenVbo;
    private long passStamp;
    private int passWidth;
    private int passHeight;
    private long framesRendered;
    private int glErrors;
    private int lastGlError;
    private boolean checkErrors;

    public RenderEngine(ShaderLibrary shaders) {
        this.shaders = shaders;
    }

    /** Liga a verificacao de glGetError por quadro (diagnostico e autoteste). */
    public void setCheckErrors(boolean check) {
        this.checkErrors = check;
    }

    public long framesRendered() {
        return framesRendered;
    }

    public int glErrorCount() {
        return glErrors;
    }

    public int lastGlError() {
        return lastGlError;
    }

    public boolean isHdr() {
        return radiance.isHdr();
    }

    /** Desenha o quadro. Deve ser chamado com o framebuffer principal do jogo ativo. */
    public void render(FrameContext ctx, List<VfxElement<?>> elements, boolean anyDistortion, GpuParticleSystem particles,
                       PostState post, float bloomStrength, boolean distortionEnabled) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        Quality q = ctx.quality;
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try {
            if (proxyGeneration != shaders.generation() || proxySteps != q.raySteps) {
                proxyPrograms.clear();
                proxyGeneration = shaders.generation();
                proxySteps = q.raySteps;
                fixedProgramsLoaded = false;
            }
            if (!fixedProgramsLoaded) {
                fixedProgramsLoaded = true;
                stripProgram = shaders.get("strip.vsh", "strip.fsh", DynamicMesh.ATTRIBUTES);
                particleProgram = shaders.get("particle.vsh", "particle.fsh", GpuParticleSystem.ATTRIBUTES);
            }
            int rw = Math.max(1, Math.round(w * q.bufferScale));
            int rh = Math.max(1, Math.round(h * q.bufferScale));
            radiance.ensure(rw, rh);
            distortion.ensure(Math.max(1, w / 2), Math.max(1, h / 2));
            output.ensure(w, h);

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(main.getDepthTextureId());

            // 1. Luz dos efeitos (premultiplicada: alfa 0 = aditivo, alfa > 0 tambem encobre).
            radiance.bind();
            RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            beginPass(rw, rh);
            for (int i = 0; i < elements.size(); i++) {
                elements.get(i).render(ctx, this, false);
            }
            if (!particles.isIdle(ctx.time)) {
                ShaderProgram p = particleProgram;
                if (p != null) {
                    p.use();
                    frameUniforms(p, ctx);
                    particles.draw(ctx, p);
                }
            }

            // 2. Distorcao espacial (deslocamentos de UV somados).
            boolean distort = distortionEnabled && q.distortion && anyDistortion;
            distortion.bind();
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            if (distort) {
                RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
                beginPass(distortion.width, distortion.height);
                for (int i = 0; i < elements.size(); i++) {
                    elements.get(i).render(ctx, this, true);
                }
            }

            // 3. Bloom.
            RenderSystem.disableBlend();
            int levels = 0;
            if (bloomStrength > 0.001f) {
                levels = renderBloom(main, rw, rh, q.bloomLevels, post.sceneBloom);
            }

            // 4. Composicao com a imagem do jogo.
            ShaderProgram c = shaders.get(FULLSCREEN_VS, "post/composite.fsh", FULLSCREEN_ATTRIBUTES);
            if (c != null) {
                output.bind();
                c.use();
                bind(0, main.getColorTextureId());
                bind(1, radiance.texture);
                bind(2, levels > 0 ? bloom[0].texture : radiance.texture);
                bind(3, distortion.texture);
                bind(4, main.getDepthTextureId());
                c.set1i("uScene", 0);
                c.set1i("uVfx", 1);
                c.set1i("uBloom", 2);
                c.set1i("uDistort", 3);
                c.set1i("uSceneDepth", 4);
                c.setMatrix("uInvViewProj", ctx.invViewProj);
                c.setMatrix("uPrevViewProj", ctx.prevViewProj);
                c.set3f("uCamDelta", ctx.camDelta);
                c.set2f("uAspect", w / (float) Math.max(1, h), 1.0f);
                c.set1f("uBloomStrength", levels > 0 ? bloomStrength : 0.0f);
                c.set1f("uDistortStrength", distort ? 1.0f : 0.0f);
                c.set1f("uChroma", post.chroma);
                c.set4f("uRadialBlur", post.radialX, post.radialY, post.radialStrength, 0.0f);
                c.set1f("uMotionBlur", post.motionBlur);
                c.set4f("uFlash", post.flashR, post.flashG, post.flashB, post.flashA);
                c.set4f("uGrade", post.exposure, post.saturation, post.contrast, post.vignette);
                c.set4f("uTint", post.tintR, post.tintG, post.tintB, post.tintA);
                c.set4f("uImpact", post.impact, post.impactR, post.impactG, post.impactB);
                c.set1f("uLetterbox", post.letterbox);
                c.set1f("uFrameSeed", (ctx.frameId % 1024L) * 0.37f);
                c.set1i("uLightCount", post.lightCount);
                if (post.lightCount > 0) {
                    c.set4fv("uLightPos", post.lightPos);
                    c.set4fv("uLightColor", post.lightColor);
                }
                fullscreen();

                // 5. Devolve o resultado ao framebuffer do jogo.
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, output.fbo);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
                GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            }
            framesRendered++;
        } finally {
            restoreState(main, previousProgram);
        }
        if (checkErrors) {
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                glErrors++;
                lastGlError = error;
            }
        }
    }

    private int renderBloom(RenderTarget main, int rw, int rh, int maxLevels, float sceneBloom) {
        ShaderProgram pre = shaders.get(FULLSCREEN_VS, "post/bloom_prefilter.fsh", FULLSCREEN_ATTRIBUTES);
        ShaderProgram down = shaders.get(FULLSCREEN_VS, "post/bloom_down.fsh", FULLSCREEN_ATTRIBUTES);
        ShaderProgram up = shaders.get(FULLSCREEN_VS, "post/bloom_up.fsh", FULLSCREEN_ATTRIBUTES);
        if (pre == null || down == null || up == null) {
            return 0;
        }
        int levels = 0;
        int bw = rw / 2;
        int bh = rh / 2;
        while (levels < Math.min(maxLevels, bloom.length) && bw >= 8 && bh >= 8) {
            if (bloom[levels] == null) {
                bloom[levels] = new VfxTarget(true);
            }
            bloom[levels].ensure(bw, bh);
            levels++;
            bw /= 2;
            bh /= 2;
        }
        if (levels == 0) {
            return 0;
        }
        bloom[0].bind();
        pre.use();
        bind(0, radiance.texture);
        bind(1, main.getColorTextureId());
        pre.set1i("uSource", 0);
        pre.set1i("uScene", 1);
        pre.set2f("uTexel", 1.0f / rw, 1.0f / rh);
        pre.set1f("uSceneBloom", sceneBloom);
        fullscreen();
        down.use();
        down.set1i("uSource", 0);
        for (int i = 1; i < levels; i++) {
            bloom[i].bind();
            bind(0, bloom[i - 1].texture);
            down.set2f("uTexel", 1.0f / bloom[i - 1].width, 1.0f / bloom[i - 1].height);
            fullscreen();
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        up.use();
        up.set1i("uSource", 0);
        up.set1f("uWeight", 1.0f);
        for (int i = levels - 1; i > 0; i--) {
            bloom[i - 1].bind();
            bind(0, bloom[i].texture);
            up.set2f("uTexel", 1.0f / bloom[i].width, 1.0f / bloom[i].height);
            fullscreen();
        }
        RenderSystem.disableBlend();
        return levels;
    }

    private void restoreState(RenderTarget main, int previousProgram) {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        BufferUploader.invalidate();
        GL20.glUseProgram(previousProgram);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        main.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private void bind(int unit, int texture) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        RenderSystem.bindTexture(texture);
    }

    private void fullscreen() {
        if (fullscreenVao == 0) {
            fullscreenVao = GL30.glGenVertexArrays();
            fullscreenVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(fullscreenVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, fullscreenVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {-1.0f, -1.0f, 3.0f, -1.0f, -1.0f, 3.0f}, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L);
        }
        GL30.glBindVertexArray(fullscreenVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private void beginPass(int width, int height) {
        passStamp++;
        passWidth = width;
        passHeight = height;
        bind(0, Minecraft.getInstance().getMainRenderTarget().getDepthTextureId());
    }

    /** Uniforms comuns do quadro; enviados uma vez por programa e por passe. */
    private void frameUniforms(ShaderProgram p, FrameContext ctx) {
        if (p.frameStamp == passStamp) {
            return;
        }
        p.frameStamp = passStamp;
        p.setMatrix("uViewProj", ctx.viewProj);
        p.setMatrix("uInvViewProj", ctx.invViewProj);
        p.set2f("uScreenSize", passWidth, passHeight);
        p.set3f("uCamRight", ctx.right);
        p.set3f("uCamUp", ctx.up);
        p.set3f("uCamForward", ctx.forward);
        p.set1i("uSceneDepth", 0);
    }

    private ShaderProgram proxyProgram(String shader, boolean distortPass) {
        ShaderProgram[] pair = proxyPrograms.get(shader);
        if (pair == null) {
            pair = new ShaderProgram[2];
            String steps = "VFX_STEPS " + proxySteps;
            pair[0] = shaders.get("proxy.vsh", "volume/" + shader + ".fsh", DynamicMesh.ATTRIBUTES, steps);
            proxyPrograms.put(shader, pair);
        }
        if (distortPass && pair[1] == null && pair[0] != null) {
            pair[1] = shaders.get("proxy.vsh", "volume/" + shader + ".fsh", DynamicMesh.ATTRIBUTES, "VFX_STEPS " + proxySteps, "DISTORT_PASS");
        }
        return pair[distortPass ? 1 : 0];
    }

    /** Desenha um efeito volumetrico: quadrilatero que cobre o volume na tela + shader do efeito. */
    public void drawProxy(FrameContext ctx, ProxyElement e, boolean distortPass) {
        ShaderProgram p = proxyProgram(e.shader, distortPass);
        if (p == null) {
            return;
        }
        p.use();
        frameUniforms(p, ctx);
        float cx = (float) (e.x + e.offX - ctx.camX);
        float cy = (float) (e.y + e.offY - ctx.camY);
        float cz = (float) (e.z + e.offZ - ctx.camZ);
        float radius = e.currentRadius;
        p.set3f("uCenter", cx, cy, cz);
        p.set1f("uRadius", radius);
        p.set3f("uAxis", e.axis);
        p.set1f("uAge", e.progress());
        p.set1f("uTime", e.age);
        p.set1f("uSeed", e.seed);
        p.set4f("uColorA", e.colorA[0], e.colorA[1], e.colorA[2], e.colorA[3] * e.currentIntensity);
        p.set4f("uColorB", e.colorB);
        p.set4f("uParams0", e.params0);
        p.set4f("uParams1", e.params1);

        float br = radius * e.bound;
        mesh.begin();
        boolean full;
        if (e.axisLength > 0.0f) {
            // Coluna: retangulo que contem o eixo e fica de frente para a camera.
            float ax = e.axis.x;
            float ay = e.axis.y;
            float az = e.axis.z;
            // lado = eixo x (camera -> base); o comprimento e a distancia da camera ate a reta do eixo.
            float sx = ay * cz - az * cy;
            float sy = az * cx - ax * cz;
            float sz = ax * cy - ay * cx;
            float perp = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            full = perp < br * 1.5f;
            if (!full) {
                float s = br * perp / (float) Math.sqrt(perp * perp - br * br) * 1.05f;
                float k = s / perp;
                sx *= k;
                sy *= k;
                sz *= k;
                float bx = cx - ax * s;
                float by = cy - ay * s;
                float bz = cz - az * s;
                float ex = cx + ax * (e.axisLength + s);
                float ey = cy + ay * (e.axisLength + s);
                float ez = cz + az * (e.axisLength + s);
                quad(bx - sx, by - sy, bz - sz, bx + sx, by + sy, bz + sz, ex + sx, ey + sy, ez + sz, ex - sx, ey - sy, ez - sz);
            }
        } else {
            float d = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            full = d < br * 1.25f;
            if (!full) {
                float s = br * d / (float) Math.sqrt(d * d - br * br) * 1.05f;
                float dx = cx / d;
                float dy = cy / d;
                float dz = cz / d;
                // direita = dir x cima(0,1,0); se a camera olha na vertical, usa o eixo X.
                float rx = -dz;
                float ry = 0.0f;
                float rz = dx;
                float rl = (float) Math.sqrt(rx * rx + rz * rz);
                if (rl < 1.0e-4f) {
                    rx = 1.0f;
                    rz = 0.0f;
                    rl = 1.0f;
                }
                rx = rx / rl * s;
                rz = rz / rl * s;
                float ux = (ry * dz - rz * dy);
                float uy = (rz * dx - rx * dz);
                float uz = (rx * dy - ry * dx);
                quad(cx - rx - ux, cy - uy, cz - rz - uz, cx + rx - ux, cy - uy, cz + rz - uz,
                        cx + rx + ux, cy + uy, cz + rz + uz, cx - rx + ux, cy + uy, cz - rz + uz);
            }
        }
        if (full) {
            quad(-1.0f, -1.0f, 0.0f, 1.0f, -1.0f, 0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f);
        }
        p.set1f("uFullscreen", full ? 1.0f : 0.0f);
        mesh.draw();
    }

    private void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        mesh.position(x0, y0, z0);
        mesh.position(x1, y1, z1);
        mesh.position(x2, y2, z2);
        mesh.position(x0, y0, z0);
        mesh.position(x2, y2, z2);
        mesh.position(x3, y3, z3);
    }

    /** Desenha uma fita voltada para a camera ao longo do caminho do elemento. */
    public void drawStrip(FrameContext ctx, StripElement e) {
        ShaderProgram p = stripProgram;
        if (p == null) {
            return;
        }
        int n = Math.min(e.path.build(e, points), StripElement.MAX_POINTS);
        if (n < 2) {
            return;
        }
        p.use();
        frameUniforms(p, ctx);
        p.set1f("uTime", ctx.time % 3600.0f);
        float ox = (float) (e.x - ctx.camX);
        float oy = (float) (e.y - ctx.camY);
        float oz = (float) (e.z - ctx.camZ);
        // Por ponto: posicao (3), lado (3), meia largura (1), distancia acumulada (1).
        float total = 0.0f;
        for (int i = 0; i < n; i++) {
            float px = ox + points[i * 3];
            float py = oy + points[i * 3 + 1];
            float pz = oz + points[i * 3 + 2];
            int a = Math.max(i - 1, 0) * 3;
            int b = Math.min(i + 1, n - 1) * 3;
            float tx = points[b] - points[a];
            float ty = points[b + 1] - points[a + 1];
            float tz = points[b + 2] - points[a + 2];
            // lado = tangente x direcao da camera ate o ponto
            float sx = ty * pz - tz * py;
            float sy = tz * px - tx * pz;
            float sz = tx * py - ty * px;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0e-6f) {
                sx = 1.0f;
                sy = 0.0f;
                sz = 0.0f;
                sl = 1.0f;
            }
            if (i > 0) {
                float dx = points[i * 3] - points[i * 3 - 3];
                float dy = points[i * 3 + 1] - points[i * 3 - 2];
                float dz = points[i * 3 + 2] - points[i * 3 - 1];
                total += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            float f = i / (float) (n - 1);
            float half = e.width * e.widthAlong.sample(f) * 0.5f;
            int r = i * 8;
            rows[r] = px;
            rows[r + 1] = py;
            rows[r + 2] = pz;
            rows[r + 3] = sx / sl;
            rows[r + 4] = sy / sl;
            rows[r + 5] = sz / sl;
            rows[r + 6] = half;
            rows[r + 7] = total;
        }
        if (total <= 1.0e-5f) {
            return;
        }
        float cr = e.color[0] * e.currentIntensity;
        float cg = e.color[1] * e.currentIntensity;
        float cb = e.color[2] * e.currentIntensity;
        float ca = e.color[3] * Math.min(1.0f, e.currentIntensity);
        float shaderAge = e.shaderAge.sample(e.progress());
        mesh.begin();
        for (int i = 0; i + 1 < n; i++) {
            int a = i * 8;
            int b = a + 8;
            stripVertex(a, -1.0f, cr, cg, cb, ca, e.style, shaderAge, e.seed, total);
            stripVertex(a, 1.0f, cr, cg, cb, ca, e.style, shaderAge, e.seed, total);
            stripVertex(b, 1.0f, cr, cg, cb, ca, e.style, shaderAge, e.seed, total);
            stripVertex(a, -1.0f, cr, cg, cb, ca, e.style, shaderAge, e.seed, total);
            stripVertex(b, 1.0f, cr, cg, cb, ca, e.style, shaderAge, e.seed, total);
            stripVertex(b, -1.0f, cr, cg, cb, ca, e.style, shaderAge, e.seed, total);
        }
        mesh.draw();
    }

    private void stripVertex(int row, float side, float r, float g, float b, float a, int style, float age, float seed, float total) {
        float w = rows[row + 6] * side;
        mesh.vertex(rows[row] + rows[row + 3] * w, rows[row + 1] + rows[row + 4] * w, rows[row + 2] + rows[row + 5] * w,
                rows[row + 7], side, r, g, b, a, style, age, seed, total);
    }

    /** Libera todos os recursos de GPU (troca de mundo, recarregamento). Chamar na thread de render. */
    public void releaseTargets() {
        radiance.delete();
        distortion.delete();
        output.delete();
        for (VfxTarget t : bloom) {
            if (t != null) {
                t.delete();
            }
        }
    }
}
