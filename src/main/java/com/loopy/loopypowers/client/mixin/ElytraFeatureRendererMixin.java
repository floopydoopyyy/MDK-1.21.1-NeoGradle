package com.loopy.loopypowers.client.mixin;

import com.loopy.loopypowers.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraLayer.class)
public class ElytraFeatureRendererMixin {

    @Unique
    private static final ResourceLocation WINGS_OF_VALOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("loopypowers", "textures/entity/wings_of_valor.png");

    @Unique
    private static final ThreadLocal<LivingEntity> CURRENT_ENTITY = new ThreadLocal<>();

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD")
    )
    private void loopypowers$captureEntity(
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, LivingEntity entity,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
    ) {
        CURRENT_ENTITY.set(entity);
    }

    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"),
            ordinal = 0
    )
    private ResourceLocation loopypowers$swapElytraTexture(ResourceLocation originalTexture) {
        LivingEntity entity = CURRENT_ENTITY.get();

        if (entity != null) {
            ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.is(ModItems.WINGS_OF_VALOR.get())) {
                return WINGS_OF_VALOR_TEXTURE;
            }
        }
        return originalTexture;
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("RETURN")
    )
    private void loopypowers$clearEntity(
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, LivingEntity entity,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
    ) {
        CURRENT_ENTITY.remove();
    }
}