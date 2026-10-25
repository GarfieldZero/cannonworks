package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.ModList;

/**
 * Xaero 小地图/世界地图的软依赖桥。
 *
 * <p>Xaero 没有公开 API，这里全部走反射，并且<b>任何异常都直接静默降级</b>——
 * 宁可地图上没有标记，也不能让游戏崩。</p>
 *
 * <p>反射链（已在 25.2.10 上核对过签名）：</p>
 * <pre>
 * XaeroMinimapSession.getCurrentSession()
 *   .getWaypointsManager().getCurrentWorld().getCurrentSet().getList()
 * </pre>
 */
public final class XaeroBridge {

    private static final String SESSION_CLASS = "xaero.common.XaeroMinimapSession";
    private static final String WAYPOINT_CLASS = "xaero.common.minimap.waypoints.Waypoint";

    private static final List<Object> TRAIL = new ArrayList<>();
    private static boolean disabled;

    private XaeroBridge() {
    }

    public static void onMarker(boolean impact, int x, int y, int z) {
        if (impact) {
            clearTrail();
            addWaypoint(x, y, z, "Impact " + x + ", " + z, 0xFFAA00, false);
        } else {
            addWaypoint(x, y, z, "Path", 0xFF6600, true);
        }
    }

    private static void addWaypoint(int x, int y, int z, String name, int color, boolean trail) {
        if (disabled || !ModList.get().isLoaded("xaerominimap")) {
            return;
        }
        try {
            List<?> list = currentList();
            if (list == null) {
                return;
            }
            Object waypoint = waypointClass()
                    .getConstructor(int.class, int.class, int.class, String.class, String.class, int.class)
                    .newInstance(x, y, z, name, "", color);
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) list;
            raw.add(waypoint);
            if (trail) {
                TRAIL.add(waypoint);
            }
        } catch (Throwable throwable) {
            disabled = true;
            CannonWorks.LOGGER.warn("Xaero minimap integration disabled: {}", throwable.toString());
        }
    }

    private static void clearTrail() {
        if (TRAIL.isEmpty() || disabled) {
            return;
        }
        try {
            List<?> list = currentList();
            if (list != null) {
                list.removeAll(TRAIL);
            }
        } catch (Throwable ignored) {
            // 清不掉就算了，只是地图上多几个点
        } finally {
            TRAIL.clear();
        }
    }

    private static List<?> currentList() throws Exception {
        Class<?> sessionClass = Class.forName(SESSION_CLASS);
        Object session = sessionClass.getMethod("getCurrentSession").invoke(null);
        if (session == null) {
            return null;
        }
        Object manager = invoke(session, "getWaypointsManager");
        if (manager == null) {
            return null;
        }
        Object world = invoke(manager, "getCurrentWorld");
        if (world == null) {
            return null;
        }
        Object set = invoke(world, "getCurrentSet");
        if (set == null) {
            return null;
        }
        return (List<?>) invoke(set, "getList");
    }

    private static Object invoke(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private static Class<?> waypointClass() throws ClassNotFoundException {
        return Class.forName(WAYPOINT_CLASS);
    }
}
