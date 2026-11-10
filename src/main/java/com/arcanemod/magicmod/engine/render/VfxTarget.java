package com.arcanemod.magicmod.engine.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Alvo de renderizacao do motor: um framebuffer com uma textura de cor, em
 * ponto flutuante de 16 bits (HDR) quando possivel. Nao tem profundidade
 * propria: os efeitos consultam a profundidade do proprio jogo.
 */
public final class VfxTarget {

    private final boolean wantHdr;
    private boolean hdr;
    public int fbo;
    public int texture;
    public int width;
    public int height;

    public VfxTarget(boolean hdr) {
        this.wantHdr = hdr;
        this.hdr = hdr;
    }

    /** Garante o tamanho pedido, recriando a textura se a janela mudou. */
    public void ensure(int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (fbo != 0 && w == width && h == height) {
            return;
        }
        delete();
        width = w;
        height = h;
        hdr = wantHdr;
        if (!allocate() && hdr) {
            // Placa sem framebuffer em ponto flutuante: cai para 8 bits.
            delete();
            width = w;
            height = h;
            hdr = false;
            if (!allocate()) {
                delete();
                throw new IllegalStateException("framebuffer incompleto (" + w + "x" + h + ")");
            }
        }
    }

    private boolean allocate() {
        texture = TextureUtil.generateTextureId();
        RenderSystem.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (hdr) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, (ByteBuffer) null);
        } else {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        fbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    /** Passa a desenhar neste alvo, com a viewport do tamanho dele. */
    public void bind() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        RenderSystem.viewport(0, 0, width, height);
    }

    public boolean isHdr() {
        return hdr;
    }

    public boolean isAllocated() {
        return fbo != 0;
    }

    public void delete() {
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (texture != 0) {
            // Pelo gerenciador do jogo, para o cache de texturas dele nao ficar apontando para um id morto.
            TextureUtil.releaseTextureId(texture);
            texture = 0;
        }
        width = 0;
        height = 0;
    }
}
