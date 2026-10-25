package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/** 炮弹的程序化几何：弹体 + 黄铜箍 + 风帽 + 尾翼。局部 +Z 为弹头方向。 */
public final class CannonShellModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CannonWorks.MOD_ID, "cannon_shell"), "main");

    private CannonShellModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(2, 2)
                .addBox(-5.0f, -5.0f, -10.0f, 10.0f, 10.0f, 14.0f), PartPose.ZERO);
        root.addOrReplaceChild("band_rear", CubeListBuilder.create().texOffs(52, 2)
                .addBox(-6.0f, -6.0f, -7.0f, 12.0f, 12.0f, 1.0f), PartPose.ZERO);
        root.addOrReplaceChild("band_front", CubeListBuilder.create().texOffs(80, 2)
                .addBox(-6.0f, -6.0f, 1.0f, 12.0f, 12.0f, 1.0f), PartPose.ZERO);
        root.addOrReplaceChild("rune_band", CubeListBuilder.create().texOffs(30, 28)
                .addBox(-5.5f, -5.5f, 2.0f, 11.0f, 11.0f, 1.0f), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(2, 28)
                .addBox(-4.0f, -4.0f, 4.0f, 8.0f, 8.0f, 5.0f), PartPose.ZERO);
        root.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(38, 43)
                .addBox(-2.0f, -2.0f, 9.0f, 4.0f, 4.0f, 3.0f), PartPose.ZERO);
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(56, 28)
                .addBox(-4.0f, -4.0f, -12.0f, 8.0f, 8.0f, 2.0f), PartPose.ZERO);
        root.addOrReplaceChild("fin_top", CubeListBuilder.create().texOffs(78, 28)
                .addBox(-1.0f, 5.0f, -11.0f, 2.0f, 2.0f, 6.0f), PartPose.ZERO);
        root.addOrReplaceChild("fin_bottom", CubeListBuilder.create().texOffs(96, 28)
                .addBox(-1.0f, -7.0f, -11.0f, 2.0f, 2.0f, 6.0f), PartPose.ZERO);
        root.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(2, 43)
                .addBox(-7.0f, -1.0f, -11.0f, 2.0f, 2.0f, 6.0f), PartPose.ZERO);
        root.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(20, 43)
                .addBox(5.0f, -1.0f, -11.0f, 2.0f, 2.0f, 6.0f), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }
}
