package com.loopy.loopypowers.client.render;

import com.loopy.loopypowers.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class WingsOfValorLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    // texture
    private static final ResourceLocation WINGS_LOCATION =
            ResourceLocation.fromNamespaceAndPath("loopypowers", "textures/entity/wings_of_valor.png");

    private final ElytraModel<T> elytraModel;

    public WingsOfValorLayer(RenderLayerParent<T, M> parent, EntityModelSet modelSet) {
        super(parent);
        this.elytraModel = new ElytraModel<>(modelSet.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {

        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);

        // ONLY render if they are wearing power wings
        if (chest.is(ModItems.WINGS_OF_VALOR.get())) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.125F);

            this.getParentModel().copyPropertiesTo(this.elytraModel);
            this.elytraModel.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            var vertexConsumer = ItemRenderer.getArmorFoilBuffer(buffer, RenderType.armorCutoutNoCull(WINGS_LOCATION), chest.hasFoil());

            // 0xFFFFFFFF is full white (no tinting) in standard ARGB integer format
            this.elytraModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

            poseStack.popPose();
        }
    }
}