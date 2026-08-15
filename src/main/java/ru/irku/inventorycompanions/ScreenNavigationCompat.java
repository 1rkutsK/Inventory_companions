package ru.irku.inventorycompanions;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Small 26.1/26.2 compatibility bridge for the screen-navigation API.
 * The reflective lookup is resolved once and cached instead of being repeated on every close.
 */
final class ScreenNavigationCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenNavigationCompat.class);
    private static final String MINECRAFT_CLASS = "net.minecraft.client.Minecraft";
    private static final String GUI_CLASS = "net.minecraft.client.gui.Gui";
    private static final String GUI_DESCRIPTOR = "Lnet/minecraft/client/gui/Gui;";
    private static final String SCREEN_DESCRIPTOR = "(Lnet/minecraft/client/gui/screens/Screen;)V";

    private static volatile ScreenOpener cachedOpener;

    private ScreenNavigationCompat() {
    }

    static void open(Minecraft minecraft, Screen target) {
        if (minecraft == null) {
            return;
        }

        ScreenOpener opener = cachedOpener;
        if (opener == null) {
            synchronized (ScreenNavigationCompat.class) {
                opener = cachedOpener;
                if (opener == null) {
                    opener = resolve(minecraft);
                    cachedOpener = opener;
                }
            }
        }

        try {
            opener.open(minecraft, target);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            cachedOpener = null;
            throw new IllegalStateException("Unable to switch Minecraft screen on this game version", unwrap(exception));
        }
    }

    private static ScreenOpener resolve(Minecraft minecraft) {
        MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
        String minecraftSetScreen = mappedMethodName(mappings, MINECRAFT_CLASS, "setScreen", SCREEN_DESCRIPTOR);
        Method minecraftMethod = findMethod(minecraft.getClass(), minecraftSetScreen, Screen.class);
        if (minecraftMethod == null && !"setScreen".equals(minecraftSetScreen)) {
            minecraftMethod = findMethod(minecraft.getClass(), "setScreen", Screen.class);
        }
        if (minecraftMethod != null) {
            minecraftMethod.setAccessible(true);
            Method method = minecraftMethod;
            return (client, target) -> method.invoke(client, target);
        }

        String guiFieldName = mappedFieldName(mappings, MINECRAFT_CLASS, "gui", GUI_DESCRIPTOR);
        Field guiField = findField(minecraft.getClass(), guiFieldName);
        if (guiField == null && !"gui".equals(guiFieldName)) {
            guiField = findField(minecraft.getClass(), "gui");
        }
        if (guiField != null) {
            guiField.setAccessible(true);
            Object gui;
            try {
                gui = guiField.get(minecraft);
            } catch (IllegalAccessException exception) {
                gui = null;
            }
            if (gui != null) {
                String guiSetScreen = mappedMethodName(mappings, GUI_CLASS, "setScreen", SCREEN_DESCRIPTOR);
                Method guiMethod = findMethod(gui.getClass(), guiSetScreen, Screen.class);
                if (guiMethod == null && !"setScreen".equals(guiSetScreen)) {
                    guiMethod = findMethod(gui.getClass(), "setScreen", Screen.class);
                }
                if (guiMethod != null) {
                    guiMethod.setAccessible(true);
                    Field field = guiField;
                    Method method = guiMethod;
                    return (client, target) -> {
                        Object currentGui = field.get(client);
                        if (currentGui == null) {
                            throw new IllegalStateException("Minecraft GUI is unavailable");
                        }
                        method.invoke(currentGui, target);
                    };
                }
            }
        }

        LOGGER.error("No compatible screen navigation method was found for Minecraft {}",
                FabricLoader.getInstance().getModContainer("minecraft")
                        .map(container -> container.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"));
        return (client, target) -> {
            throw new NoSuchMethodException("No compatible screen navigation method found");
        };
    }

    private static Field findField(Class<?> type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the class hierarchy.
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // Continue through the class hierarchy.
            }
        }
        return null;
    }

    private static String mappedFieldName(MappingResolver mappings, String owner, String name, String descriptor) {
        try {
            return mappings.mapFieldName("named", owner, name, descriptor);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not map field {}.{}; falling back to the named symbol", owner, name, exception);
            return name;
        }
    }

    private static String mappedMethodName(MappingResolver mappings, String owner, String name, String descriptor) {
        try {
            return mappings.mapMethodName("named", owner, name, descriptor);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not map method {}.{}; falling back to the named symbol", owner, name, exception);
            return name;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : throwable;
    }

    @FunctionalInterface
    private interface ScreenOpener {
        void open(Minecraft minecraft, Screen target) throws ReflectiveOperationException;
    }
}
