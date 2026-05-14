package com.loopy.loopypowers.client.mixin;

import com.loopy.loopypowers.Loopypowers;
import com.loopy.loopypowers.item.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ElytraLayer.class)
public class ElytraFeatureRendererMixin {

    @Unique
    private static final ResourceLocation WINGS_OF_VALOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Loopypowers.MOD_ID, "textures/entity/wings_of_valor.png");

    // NOTE: The @Redirect for "stack.isOf(Items.ELYTRA)" was removed here because NeoForge
    // natively uses stack.canElytraFly(entity) in the renderer! Your WingsOfValorItem handles it!

    /**
     * replaces the elytra with the new texture
     * fuck this.
     */
    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("STORE"),
            ordinal = 0
    )
    private ResourceLocation loopypowers$swapElytraTexture(
            ResourceLocation original,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);

        if (chest.is(ModItems.WINGS_OF_VALOR.get())) {
            return WINGS_OF_VALOR_TEXTURE;
        }
        return original;
    }
}