package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.ModList;

/**
 * Xaero 小地图/世界地图的软依赖桥。
 *
 * <p>Xaero 没有公开 API，这里全部走反射，并且<b>任何异常都直接静默降级</b>——
 * 宁可地图上没有标记，也不能让游戏崩。</p>
 *
 * <p>每条弹道最多保留 {@link #MAX_TRAIL} 个路径点：新点进来时挤掉最旧的，
 * 落地时把这条弹道剩下的点全部删掉。</p>
 */
public final class XaeroBridge {

    private static final int MAX_TRAIL = 3;
    private static final String SESSION_CLASS = "xaero.common.XaeroMinimapSession";
    private static final String WAYPOINT_CLASS = "xaero.common.minimap.waypoints.Waypoint";

    private static final Map<Integer, Deque<Object>> TRAILS = new HashMap<>();
    private static boolean disabled;

    private XaeroBridge() {
    }

    /** 飞行中：加一个路径点，超出上限就删最旧的。 */
    public static void onTrail(int flightId, int x, int y, int z) {
        Object waypoint = create(x, y, z, "Shell", 0xFF6600);
        if (waypoint == null) {
            return;
        }
        add(waypoint);
        Deque<Object> trail = TRAILS.computeIfAbsent(flightId, key -> new ArrayDeque<>());
        trail.addLast(waypoint);
        int maxTrail = com.cannonworks.config.CannonConfig.TRAIL_MAX_POINTS.get();
        while (trail.size() > maxTrail) {
            remove(trail.removeFirst());
        }
    }

    /** 落地：清掉这条弹道的所有路径点，再加一个落点。 */
    public static void onImpact(int flightId, int x, int y, int z) {
        Deque<Object> trail = TRAILS.remove(flightId);
        if (trail != null) {
            trail.forEach(XaeroBridge::remove);
        }
        add(create(x, y, z, "Impact " + x + ", " + z, 0xFFAA00));
    }

    private static Object create(int x, int y, int z, String name, int color) {
        if (disabled || !ModList.get().isLoaded("xaerominimap")) {
            return null;
        }
        try {
            return waypointClass()
                    .getConstructor(int.class, int.class, int.class, String.class, String.class, int.class)
                    .newInstance(x, y, z, name, "", color);
        } catch (Throwable throwable) {
            disable(throwable);
            return null;
        }
    }

    private static void add(Object waypoint) {
        if (waypoint == null) {
            return;
        }
        try {
            List<?> list = currentList();
            if (list != null) {
                @SuppressWarnings("unchecked")
                List<Object> raw = (List<Object>) list;
                raw.add(waypoint);
            }
        } catch (Throwable throwable) {
            disable(throwable);
        }
    }

    private static void remove(Object waypoint) {
        if (waypoint == null || disabled) {
            return;
        }
        try {
            List<?> list = currentList();
            if (list != null) {
                list.remove(waypoint);
            }
        } catch (Throwable ignored) {
            // 删不掉就留着，不影响游戏
        }
    }

    private static void disable(Throwable throwable) {
        if (!disabled) {
            disabled = true;
            CannonWorks.LOGGER.warn("Xaero minimap integration disabled: {}", throwable.toString());
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
