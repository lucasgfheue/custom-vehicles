package com.vehiclestudio.client.render;

import com.vehiclestudio.data.VehicleGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.entity.CustomVehicle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Dibuja los vehiculos: el modelo girado hacia donde mira, con las ruedas rodando y las luces encendidas. */
public class VehicleRenderer extends EntityRenderer<CustomVehicle> {

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8f;
    }

    @Override
    public ResourceLocation getTextureLocation(CustomVehicle entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(CustomVehicle entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        VehicleDefinition d = entity.def();
        if (d != null && !d.parts.isEmpty()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
            // golpe: se sacude un poco como las barcas
            float hurt = entity.getHurtTime() - partialTick;
            if (hurt > 0f) {
                float damage = Math.max(0f, entity.getDamage() - partialTick);
                float amount = Mth.sin(hurt) * hurt * Math.min(damage, 10f) / 40.0f * entity.getHurtDir();
                poseStack.mulPose(Axis.ZP.rotationDegrees(amount));
            }
            float[] l = entity.layout(d);
            float k = l[3] / 16f;
            // +X del modelo es la izquierda del vehiculo (como los modelos de Minecraft)
            poseStack.scale(-k, k, k);
            poseStack.translate(-l[0], -l[1], -l[2]);
            VehicleGeometry.Motion motion = new VehicleGeometry.Motion(entity.getRolled(partialTick),
                    entity.getSteerShown(partialTick), entity.lightsOn(), entity.braking());
            Matrix4f[] mats = VehicleGeometry.pose(d, motion);
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            VehicleModelRenderer.renderPosed(d, mats, poseStack.last(), vc, packedLight, OverlayTexture.NO_OVERLAY,
                    -1, motion, false);
            if (d.hasPaint()) {
                PaintAtlas atlas = PaintCache.get(d);
                if (atlas != null) {
                    VertexConsumer paint = buffer.getBuffer(RenderType.entityCutoutNoCull(atlas.location()));
                    VehicleModelRenderer.renderPaint(d, mats, poseStack.last(), paint, packedLight,
                            OverlayTexture.NO_OVERLAY, -1, atlas);
                }
            }
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
