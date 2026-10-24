package com.cannonworks.payload;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一发炮弹装了什么。
 *
 * <p>拆成两部分（对应界面上的两个槽）：</p>
 * <ul>
 *   <li><b>填充方块</b>：任意方块，落地后在弹坑里铺出来，相当于空投建材；</li>
 *   <li><b>货物</b>：有功能的物品（传送石碑 / TNT / 末影珍珠 / 传送板），最后执行。</li>
 * </ul>
 *
 * @param fillerState     填充方块，null 表示不填
 * @param fillerCount     填充数量
 * @param cargo           货物类型
 * @param cargoName       货物自定义名（目前只有石碑用）
 * @param cargoBlockState 货物的方块形态（石碑的材质、传送板等），null 表示默认
 */
public record CannonPayload(@Nullable BlockState fillerState, int fillerCount, Cargo cargo,
                            @Nullable String cargoName, @Nullable BlockState cargoBlockState) {

    public static final CannonPayload EMPTY = new CannonPayload(null, 0, Cargo.NONE, null, null);

    public enum Cargo {
        NONE,
        /** 落地放一座传送石碑并给发射者激活。 */
        WAYSTONE,
        /** 落地大爆破。 */
        TNT,
        /** 落地把发射者传送过去。 */
        ENDER_PEARL,
        /** 落地部署一块传送板。 */
        WARP_PLATE
    }

    public static CannonPayload of(@Nullable BlockState fillerState, int fillerCount, Cargo cargo,
                                   @Nullable String cargoName, @Nullable BlockState cargoBlockState) {
        int count = fillerState == null ? 0 : Math.max(0, Math.min(64, fillerCount));
        return new CannonPayload(count > 0 ? fillerState : null, count, cargo, cargoName, cargoBlockState);
    }

    public static CannonPayload of(@Nullable BlockState fillerState, int fillerCount, Cargo cargo,
                                   @Nullable String cargoName) {
        return of(fillerState, fillerCount, cargo, cargoName, null);
    }

    public static CannonPayload cargo(Cargo cargo, @Nullable String name, @Nullable BlockState blockState) {
        return new CannonPayload(null, 0, cargo, name, blockState);
    }

    public static CannonPayload filler(BlockState state, int count) {
        return of(state, count, Cargo.NONE, null, null);
    }

    public boolean hasFiller() {
        return fillerState != null && fillerCount > 0;
    }

    public boolean hasCargo() {
        return cargo != Cargo.NONE;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (hasFiller()) {
            sb.append("填充 ").append(fillerState.getBlock().getName().getString()).append(" ×").append(fillerCount);
        }
        if (hasCargo()) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(switch (cargo) {
                case NONE -> "";
                case WAYSTONE -> cargoName != null ? "传送石碑「" + cargoName + "」" : "传送石碑";
                case TNT -> "TNT";
                case ENDER_PEARL -> "末影珍珠";
                case WARP_PLATE -> "传送板";
            });
        }
        return sb.length() == 0 ? "空" : sb.toString();
    }
}
