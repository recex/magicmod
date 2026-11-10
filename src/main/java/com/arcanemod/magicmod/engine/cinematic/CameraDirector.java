package com.arcanemod.magicmod.engine.cinematic;

import com.arcanemod.magicmod.client.config.VfxConfig;
import com.arcanemod.magicmod.engine.math.MathUtil;
import com.arcanemod.magicmod.engine.math.Noise;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Diretor de camera: tremor, zoom e inclinacao (sempre pelos eventos do
 * Forge, sem tocar na camera do jogo) e planos cinematograficos curtos.
 *
 * <p>Num plano, a visao passa para uma entidade-camera invisivel que existe
 * so no cliente; o jogador aparece em terceira pessoa, a interface some e
 * entram as barras de cinema. Ao terminar - ou se o jogador apertar uma tecla
 * de movimento, abrir uma tela ou morrer - a visao volta ao jogador.
 */
public final class CameraDirector {

    /** Entidade-camera: so existe no cliente e nunca entra no mundo. */
    private static final class Rig extends Marker {
        private float eye;

        Rig(Level level) {
            super(EntityType.MARKER, level);
        }

        void setEye(float eye) {
            this.eye = eye;
            refreshDimensions();
        }

        @Override
        protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
            return eye;
        }
    }

    private float shakeTarget;
    private float shake;
    private float fovTarget;
    private float fov;
    private long lastNanos;
    private float clock;

    private CameraShot shot;
    private float shotStart;
    private float shotTime;
    private float baseYaw;
    private Rig rig;
    private CameraType savedType;
    private float shotFov;
    private float shotRoll;
    private float shotWeight;
    private final double[] scratch = new double[3];

    /** Pedidos de tremor e zoom vindos dos efeitos (valem a partir do proximo quadro). */
    public void accept(float shakeAmount, float fovKick) {
        this.shakeTarget = MathUtil.saturate(shakeAmount);
        this.fovTarget = MathUtil.clamp(fovKick, -0.35f, 0.45f);
    }

    public boolean isShotActive() {
        return shot != null;
    }

    /** Fracao da tela ocupada pelas barras de cinema neste instante. */
    public float letterbox() {
        return shot == null ? 0.0f : shot.letterbox * shotWeight;
    }

    /**
     * Comeca um plano. Devolve falso (e nao faz nada) se a camera
     * cinematografica esta desligada ou se o momento nao permite.
     */
    public boolean play(CameraShot newShot, float now) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!VfxConfig.cinematicCamera || shot != null || player == null || mc.level == null || mc.screen != null
                || !player.isAlive() || player.isSpectator() || player.isSleeping() || mc.getCameraEntity() != player
                || player.isPassenger()) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        baseYaw = (float) Math.toDegrees(Math.atan2(eye.x - newShot.x, eye.z - newShot.z));
        rig = new Rig(mc.level);
        rig.setEye(player.getEyeHeight());
        place(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot());
        savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(rig);
        shot = newShot;
        shotStart = now;
        shotTime = 0.0f;
        shotWeight = 0.0f;
        return true;
    }

    /** Encerra o plano e devolve a visao ao jogador. Seguro chamar a qualquer momento. */
    public void stop() {
        if (shot == null) {
            return;
        }
        shot = null;
        shotFov = 0.0f;
        shotRoll = 0.0f;
        shotWeight = 0.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() == rig && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (savedType != null) {
            mc.options.setCameraType(savedType);
        }
        rig = null;
        savedType = null;
    }

    private void place(double x, double y, double z, float yaw, float pitch) {
        double feet = y - rig.getEyeHeight();
        rig.setPos(x, feet, z);
        rig.xo = x;
        rig.yo = feet;
        rig.zo = z;
        rig.xOld = x;
        rig.yOld = feet;
        rig.zOld = z;
        rig.setYRot(yaw);
        rig.setXRot(pitch);
        rig.yRotO = yaw;
        rig.xRotO = pitch;
    }

    /** Atualiza a camera do plano. Chamado no inicio de cada quadro, antes de o jogo montar a camera. */
    public void updateShot(float now, float partialTick) {
        if (shot == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isAlive() || mc.screen != null || mc.getCameraEntity() != rig
                || rig.level() != mc.level) {
            stop();
            return;
        }
        shotTime = now - shotStart;
        boolean skip = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown() || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
        if (shotTime >= shot.duration || shotTime < 0.0f || (skip && shotTime > 0.25f)) {
            stop();
            return;
        }
        float w = shot.weight(shotTime);
        shotWeight = w;
        shot.position(shotTime, baseYaw, scratch);
        Vec3 target = new Vec3(shot.x, shot.y + shot.lookHeight.sample(shotTime), shot.z);
        Vec3 desired = new Vec3(scratch[0], scratch[1], scratch[2]);
        // Nao deixa a camera entrar em blocos: recua ate o primeiro obstaculo.
        BlockHitResult hit = mc.level.clip(new ClipContext(target, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, rig));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 back = target.subtract(hit.getLocation());
            double len = back.length();
            desired = len > 0.4 ? hit.getLocation().add(back.scale(0.35 / len)) : target;
        }
        Vec3 look = target.subtract(desired);
        double flat = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, flat));
        Vec3 eye = player.getEyePosition(partialTick);
        float playerYaw = player.getViewYRot(partialTick);
        float playerPitch = player.getViewXRot(partialTick);
        place(Mth.lerp(w, eye.x, desired.x), Mth.lerp(w, eye.y, desired.y), Mth.lerp(w, eye.z, desired.z),
                MathUtil.lerpAngle(playerYaw, yaw, w), Mth.lerp(w, playerPitch, pitch));
        shotFov = shot.fov.sample(shotTime) * w;
        shotRoll = shot.roll.sample(shotTime) * w;
    }

    @SubscribeEvent
    public void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long nanos = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (nanos - lastNanos) / 1.0e9f);
        lastNanos = nanos;
        clock = (clock + dt) % 600.0f;
        // O tremor sobe na hora e decai rapido; o zoom acompanha com suavidade.
        shake = Math.max(shakeTarget, MathUtil.damp(shake, shakeTarget, 0.07f, dt));
        fov = MathUtil.damp(fov, fovTarget, 0.05f, dt);
        if (shake > 0.003f) {
            float amp = shake * shake;
            event.setYaw(event.getYaw() + Noise.noise1(clock * 23.0f, 11) * amp * 3.2f);
            event.setPitch(event.getPitch() + Noise.noise1(clock * 27.0f, 47) * amp * 2.6f);
            event.setRoll(event.getRoll() + Noise.noise1(clock * 19.0f, 83) * amp * 2.4f);
        }
        if (shot != null && Math.abs(shotRoll) > 0.01f) {
            event.setRoll(event.getRoll() + shotRoll);
        }
    }

    @SubscribeEvent
    public void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return;
        }
        float k = fov + (shot != null ? shotFov : 0.0f);
        if (Math.abs(k) > 0.002f) {
            event.setFOV(MathUtil.clamp(event.getFOV() * (1.0 + k), 20.0, 150.0));
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && shot != null && Minecraft.getInstance().level == null) {
            stop();
        }
    }

    @SubscribeEvent
    public void onHand(RenderHandEvent event) {
        if (shot != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (shot != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (shot != null) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }
}
