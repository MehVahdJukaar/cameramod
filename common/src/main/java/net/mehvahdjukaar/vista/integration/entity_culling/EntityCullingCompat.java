package net.mehvahdjukaar.vista.integration.entity_culling;


import java.lang.reflect.Field;

public class EntityCullingCompat {

    private static final Field CULLING_ENABLED_FIELD;

    static {
        try {
            var entityCullingModBase = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
            CULLING_ENABLED_FIELD = entityCullingModBase.getField("enabled");
            CULLING_ENABLED_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Runnable decorateRenderWithoutCulling(Runnable task) {

        return () -> {
            //EntityCullingModBase.enabled = false;
            try {
                boolean oldState = CULLING_ENABLED_FIELD.getBoolean(null);
                CULLING_ENABLED_FIELD.set(null, false);
                task.run();
                CULLING_ENABLED_FIELD.set(null, oldState);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            //EntityCullingModBase.enabled = false;

        };
    }
}
