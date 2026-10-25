package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonGeometry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * 黑铁重炮 v2 的程序化几何。
 *
 * <p>整个模型由 31 个立方体组成：底座包含炮架、双轮、耳轴与前置支腿；
 * 炮管分为尾部、管身、炮口与箍环四组。炮管枢轴在尾端 {@link #PIVOT_Y} / {@link #PIVOT_Z}，
 * 局部 +Z 为炮口方向。尺寸单位是 1/16 格；建模保持 1× 尺度，
 * 渲染时由 {@link CannonGeometry#MODEL_SCALE} 放大到多格尺寸。
 *
 * <p>贴图由 128×128 图集承载；每个立方体的 UV 偏移见下方 {@code texOffs}，
 * 实现说明里的 UV 表与本文件由同一个离线生成脚本产出。
 */
public final class CannonModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CannonWorks.MOD_ID, "cannon"), "main");

    /** 炮管枢轴（炮尾）高度，模型单位。 */
    public static final float PIVOT_Y = CannonGeometry.PIVOT_Y;
    /** 炮管枢轴 Z 偏移，模型单位。 */
    public static final float PIVOT_Z = CannonGeometry.PIVOT_Z;
    /** 炮口端面到枢轴的距离（模型单位）。 */
    public static final float MUZZLE_Z = CannonGeometry.MUZZLE_Z;
    /** 整体渲染放大倍数。 */
    public static final float MODEL_SCALE = CannonGeometry.MODEL_SCALE;

    private CannonModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition base = root.addOrReplaceChild("base", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition carriage = base.addOrReplaceChild("carriage", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition wheels = base.addOrReplaceChild("wheels", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition details = base.addOrReplaceChild("details", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition barrel = root.addOrReplaceChild("barrel", CubeListBuilder.create(),
                PartPose.offset(0.0f, PIVOT_Y, PIVOT_Z));
        PartDefinition breech = barrel.addOrReplaceChild("breech", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition tube = barrel.addOrReplaceChild("tube", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition muzzle = barrel.addOrReplaceChild("muzzle", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition trim = barrel.addOrReplaceChild("trim", CubeListBuilder.create(), PartPose.ZERO);

        // base
        base.addOrReplaceChild("foundation", CubeListBuilder.create().texOffs(2, 20)
                .addBox(-6.0f, 0.0f, -8.0f, 12.0f, 2.0f, 10.0f), PartPose.ZERO);
        base.addOrReplaceChild("front_beam", CubeListBuilder.create().texOffs(16, 43)
                .addBox(-6.0f, 0.0f, 2.0f, 12.0f, 3.0f, 2.0f), PartPose.ZERO);

        // carriage
        carriage.addOrReplaceChild("side_left", CubeListBuilder.create().texOffs(46, 2)
                .addBox(-6.0f, 2.0f, -6.0f, 2.0f, 7.0f, 8.0f), PartPose.ZERO);
        carriage.addOrReplaceChild("side_right", CubeListBuilder.create().texOffs(68, 2)
                .addBox(4.0f, 2.0f, -6.0f, 2.0f, 7.0f, 8.0f), PartPose.ZERO);
        carriage.addOrReplaceChild("top_left", CubeListBuilder.create().texOffs(2, 51)
                .addBox(-6.0f, 9.0f, -4.0f, 2.0f, 1.0f, 3.0f), PartPose.ZERO);
        carriage.addOrReplaceChild("top_right", CubeListBuilder.create().texOffs(14, 51)
                .addBox(4.0f, 9.0f, -4.0f, 2.0f, 1.0f, 3.0f), PartPose.ZERO);
        carriage.addOrReplaceChild("cross_brace", CubeListBuilder.create().texOffs(58, 51)
                .addBox(-6.0f, 2.0f, -6.0f, 12.0f, 1.0f, 1.0f), PartPose.ZERO);

        // wheels
        wheels.addOrReplaceChild("wheel_left", CubeListBuilder.create().texOffs(2, 2)
                .addBox(-8.0f, 0.0f, -8.0f, 2.0f, 8.0f, 8.0f), PartPose.ZERO);
        wheels.addOrReplaceChild("wheel_right", CubeListBuilder.create().texOffs(24, 2)
                .addBox(6.0f, 0.0f, -8.0f, 2.0f, 8.0f, 8.0f), PartPose.ZERO);

        // details
        details.addOrReplaceChild("rear_plate", CubeListBuilder.create().texOffs(44, 34)
                .addBox(-4.0f, 2.0f, -8.0f, 8.0f, 5.0f, 1.0f), PartPose.ZERO);
        details.addOrReplaceChild("trunnion_left", CubeListBuilder.create().texOffs(36, 51)
                .addBox(-4.5f, 5.9f, -3.5f, 1.0f, 2.0f, 2.0f), PartPose.ZERO);
        details.addOrReplaceChild("trunnion_right", CubeListBuilder.create().texOffs(44, 51)
                .addBox(3.5f, 5.9f, -3.5f, 1.0f, 2.0f, 2.0f), PartPose.ZERO);
        details.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(98, 43)
                .addBox(-8.0f, 0.0f, 2.0f, 2.0f, 3.0f, 2.0f), PartPose.ZERO);
        details.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(108, 43)
                .addBox(6.0f, 0.0f, 2.0f, 2.0f, 3.0f, 2.0f), PartPose.ZERO);

        // breech
        breech.addOrReplaceChild("tail_cap", CubeListBuilder.create().texOffs(86, 43)
                .addBox(-2.0f, -2.0f, 0.0f, 4.0f, 4.0f, 1.0f), PartPose.ZERO);
        breech.addOrReplaceChild("breech_block", CubeListBuilder.create().texOffs(98, 20)
                .addBox(-3.5f, -2.5f, 1.0f, 7.0f, 5.0f, 3.0f), PartPose.ZERO);
        breech.addOrReplaceChild("breech_band", CubeListBuilder.create().texOffs(2, 34)
                .addBox(-3.5f, -3.0f, 3.0f, 7.0f, 6.0f, 1.0f), PartPose.ZERO);
        breech.addOrReplaceChild("sight_rear", CubeListBuilder.create().texOffs(52, 51)
                .addBox(-0.5f, 2.5f, 3.5f, 1.0f, 2.0f, 1.0f), PartPose.ZERO);

        // tube
        tube.addOrReplaceChild("shoulder", CubeListBuilder.create().texOffs(64, 34)
                .addBox(-3.0f, -2.5f, 4.0f, 6.0f, 5.0f, 1.0f), PartPose.ZERO);
        tube.addOrReplaceChild("tube_rear", CubeListBuilder.create().texOffs(48, 20)
                .addBox(-2.0f, -2.0f, 5.0f, 4.0f, 4.0f, 8.0f), PartPose.ZERO);
        tube.addOrReplaceChild("tube_front", CubeListBuilder.create().texOffs(74, 20)
                .addBox(-2.0f, -2.0f, 13.0f, 4.0f, 4.0f, 7.0f), PartPose.ZERO);
        tube.addOrReplaceChild("sight_front", CubeListBuilder.create().texOffs(86, 51)
                .addBox(-0.5f, 2.0f, 18.0f, 1.0f, 1.0f, 1.0f), PartPose.ZERO);

        // muzzle
        muzzle.addOrReplaceChild("muzzle_top", CubeListBuilder.create().texOffs(66, 43)
                .addBox(-2.5f, 1.5f, 20.0f, 5.0f, 1.0f, 4.0f), PartPose.ZERO);
        muzzle.addOrReplaceChild("muzzle_bottom", CubeListBuilder.create().texOffs(46, 43)
                .addBox(-2.5f, -2.5f, 20.0f, 5.0f, 1.0f, 4.0f), PartPose.ZERO);
        muzzle.addOrReplaceChild("muzzle_left", CubeListBuilder.create().texOffs(20, 34)
                .addBox(-2.5f, -1.5f, 20.0f, 1.0f, 3.0f, 4.0f), PartPose.ZERO);
        muzzle.addOrReplaceChild("muzzle_right", CubeListBuilder.create().texOffs(32, 34)
                .addBox(1.5f, -1.5f, 20.0f, 1.0f, 3.0f, 4.0f), PartPose.ZERO);
        muzzle.addOrReplaceChild("bore", CubeListBuilder.create().texOffs(26, 51)
                .addBox(-1.5f, -1.5f, 19.5f, 3.0f, 3.0f, 1.0f), PartPose.ZERO);

        // trim
        trim.addOrReplaceChild("hoop_rear", CubeListBuilder.create().texOffs(108, 34)
                .addBox(-2.5f, -2.5f, 5.0f, 5.0f, 5.0f, 1.0f), PartPose.ZERO);
        trim.addOrReplaceChild("hoop_mid", CubeListBuilder.create().texOffs(94, 34)
                .addBox(-2.5f, -2.5f, 12.0f, 5.0f, 5.0f, 1.0f), PartPose.ZERO);
        trim.addOrReplaceChild("hoop_front", CubeListBuilder.create().texOffs(80, 34)
                .addBox(-2.5f, -2.5f, 19.0f, 5.0f, 5.0f, 1.0f), PartPose.ZERO);
        trim.addOrReplaceChild("rune_band", CubeListBuilder.create().texOffs(2, 43)
                .addBox(-2.5f, -2.5f, 9.0f, 5.0f, 5.0f, 1.0f), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }
}
