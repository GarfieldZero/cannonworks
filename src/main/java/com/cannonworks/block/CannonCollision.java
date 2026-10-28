package com.cannonworks.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 底座碰撞几何：把 {@code CannonModel} 的 14 个底座立方体从模型坐标换算到世界坐标，
 * 再按当前方位角旋转。这样炮台转到任意角度时，碰撞都跟着走，不会出现“转到一半
 * 就有一块模型能穿过去”的情况。
 *
 * <p>这里只做底座，不碰炮管：炮管在高仰角时本来就在人头顶上方，低仰角时又被
 * 底座遮住；如果给炮管也做碰撞，反而会把炮口附近变成一堵看不见的墙。</p>
 *
 * <p>每个 {@link CannonPartBlock} 只取落进自己 1×1 格子内的那部分，空网格返回
 * {@link Shapes#empty()}，所以不会再有整格空气墙。</p>
 */
public final class CannonCollision {

    private static final double MODEL_SCALE = CannonGeometry.MODEL_SCALE / 16.0;

    /**
     * 底座 14 个立方体，格式 {x, y, z, w, h, d}，单位仍是 1/16 格。
     * 顺序与 CannonModel 的 base 组一致；不要在这里包含炮管组。
     */
    private static final double[][] BASE_BOXES = {
            {-6, 0, -8, 12, 2, 10},   // foundation
            {-6, 0, 2, 12, 3, 2},     // front_beam
            {-6, 2, -6, 2, 7, 8},     // side_left
            {4, 2, -6, 2, 7, 8},      // side_right
            {-6, 9, -4, 2, 1, 3},     // top_left
            {4, 9, -4, 2, 1, 3},      // top_right
            {-6, 2, -6, 12, 1, 1},    // cross_brace
            {-8, 0, -8, 2, 8, 8},     // wheel_left
            {6, 0, -8, 2, 8, 8},      // wheel_right
            {-4, 2, -8, 8, 5, 1},     // rear_plate
            {-4.5, 5.9, -3.5, 1, 2, 2}, // trunnion_left
            {3.5, 5.9, -3.5, 1, 2, 2},  // trunnion_right
            {-8, 0, 2, 2, 3, 2},      // leg_left
            {6, 0, 2, 2, 3, 2},       // leg_right
    };

    private CannonCollision() {
    }

    public static VoxelShape shapeFor(BlockPos anchor, BlockPos part, int yaw) {
        double radians = Math.toRadians(-yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        List<AABB> boxes = new ArrayList<>();

        for (double[] box : BASE_BOXES) {
            // 模型坐标：渲染器先 translate(0.5,0,0.5)，再按 -yaw 旋转。
            double minX = box[0] * MODEL_SCALE + 0.5;
            double minY = anchor.getY() + box[1] * MODEL_SCALE;
            double minZ = box[2] * MODEL_SCALE + 0.5;
            double maxX = (box[0] + box[3]) * MODEL_SCALE + 0.5;
            double maxY = anchor.getY() + (box[1] + box[4]) * MODEL_SCALE;
            double maxZ = (box[2] + box[5]) * MODEL_SCALE + 0.5;

            // 旋转后的 AABB：取 8 个角点的最小/最大包围盒。比精确 OBB 略胖，
            // 但比整格方块精确得多，也不会在角上漏碰撞。
            double worldMinX = Double.POSITIVE_INFINITY;
            double worldMinZ = Double.POSITIVE_INFINITY;
            double worldMaxX = Double.NEGATIVE_INFINITY;
            double worldMaxZ = Double.NEGATIVE_INFINITY;
            for (int xi = 0; xi < 2; xi++) {
                double x = (xi == 0 ? minX : maxX) - 0.5;
                for (int zi = 0; zi < 2; zi++) {
                    double z = (zi == 0 ? minZ : maxZ) - 0.5;
                    double rotatedX = x * cos + z * sin;
                    double rotatedZ = -x * sin + z * cos;
                    worldMinX = Math.min(worldMinX, anchor.getX() + rotatedX + 0.5);
                    worldMaxX = Math.max(worldMaxX, anchor.getX() + rotatedX + 0.5);
                    worldMinZ = Math.min(worldMinZ, anchor.getZ() + rotatedZ + 0.5);
                    worldMaxZ = Math.max(worldMaxZ, anchor.getZ() + rotatedZ + 0.5);
                }
            }

            // 只保留落在该占位块 1×1 格子内的部分；空网格自然不会有碰撞。
            double localMinX = (worldMinX - part.getX()) * 16.0;
            double localMaxX = (worldMaxX - part.getX()) * 16.0;
            double localMinY = (minY - part.getY()) * 16.0;
            double localMaxY = (maxY - part.getY()) * 16.0;
            double localMinZ = (worldMinZ - part.getZ()) * 16.0;
            double localMaxZ = (worldMaxZ - part.getZ()) * 16.0;

            localMinX = Math.max(0.0, localMinX);
            localMinY = Math.max(0.0, localMinY);
            localMinZ = Math.max(0.0, localMinZ);
            localMaxX = Math.min(16.0, localMaxX);
            localMaxY = Math.min(16.0, localMaxY);
            localMaxZ = Math.min(16.0, localMaxZ);
            if (localMinX >= localMaxX || localMinY >= localMaxY || localMinZ >= localMaxZ) {
                continue;
            }
            boxes.add(new AABB(localMinX / 16.0, localMinY / 16.0, localMinZ / 16.0,
                    localMaxX / 16.0, localMaxY / 16.0, localMaxZ / 16.0));
        }

        VoxelShape shape = Shapes.empty();
        for (AABB box : boxes) {
            shape = Shapes.or(shape, Shapes.create(box));
        }
        return shape;
    }
}
