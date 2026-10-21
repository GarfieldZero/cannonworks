package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * 大炮的几何体：一个底座 + 一根可俯仰的炮管。
 * 尺寸单位是 1/16 格，整体约 1.6 格长、1 格高。
 */
public final class CannonModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CannonWorks.MOD_ID, "cannon"), "main");

    private CannonModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 底座（炮架）
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8.0f, 0.0f, -8.0f, 16.0f, 8.0f, 16.0f), PartPose.ZERO);

        // 炮管：支点在底座顶部，向 +Z 伸出；BER 里直接改 xRot 做俯仰
        root.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 24)
                .addBox(-4.0f, -4.0f, -2.0f, 8.0f, 8.0f, 26.0f), PartPose.offset(0.0f, 10.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 64);
    }
}
