package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class SeamlessSleepYaclHelpButton {
    private static final String GUIDE_URL = "https://modrinth.com/mod/seamless-sleep";
    private static final String YACL_CATEGORY_TAB_CLASS = "dev.isxander.yacl3.gui.YACLScreen$CategoryTab";
    private static final String TAB_MANAGER_FIELD = "tabManager";
    private static final String TAB_NAVIGATION_BAR_FIELD = "tabNavigationBar";
    private static final String SAVE_BUTTON_FIELD = "saveFinishedButton";
    private static final String SEARCH_FIELD = "searchField";
    private static final String DESCRIPTION_WIDGET_FIELD = "descriptionWidget";
    private static final String DESCRIPTION_DIMENSIONS_FIELD = "dimensions";
    private static final int FOOTER_VERTICAL_GAP = 2;
    private static final int FALLBACK_BUTTON_HEIGHT = 20;
    private static final int FALLBACK_SEARCH_HEIGHT = 18;
    private static final int FALLBACK_ROW_GAP = 2;
    private static final int YACL_FOOTER_ROW_STEP = FALLBACK_BUTTON_HEIGHT + FALLBACK_ROW_GAP;
    private static final int FALLBACK_MAX_RIGHT_PANE_WIDTH = 400;

    private static Method addRenderableWidgetMethod;
    private static Field childrenField;
    private static Field renderablesField;
    private static Field narratablesField;
    private static final Map<Screen, Button> INSTALLED_BUTTONS = new WeakHashMap<>();
    private static final Map<Screen, SeamlessSleepButtonCornerHighlight> INSTALLED_HIGHLIGHTS = new WeakHashMap<>();
    private static final Map<Screen, Boolean> FAILURE_LOGGED = new WeakHashMap<>();

    private SeamlessSleepYaclHelpButton() {
    }

    public static void install(Screen screen) {
        if (screen == null || !isYaclScreen(screen)) {
            return;
        }

        Button helpButton = INSTALLED_BUTTONS.get(screen);
        ButtonLayout layout = findFooterLayout(screen);
        if (layout == null) {
            if (!Boolean.TRUE.equals(FAILURE_LOGGED.get(screen))) {
                Constants.debug("Unable to install Seamless Sleep YACL guide button: footer widgets and fallback geometry are unavailable for {}.", screen.getClass().getName());
                FAILURE_LOGGED.put(screen, true);
            }
            return;
        }

        if (helpButton == null) {
            helpButton = createButton(screen);
            INSTALLED_BUTTONS.put(screen, helpButton);
        }

        helpButton.setX(layout.x());
        helpButton.setY(layout.y());
        helpButton.setWidth(layout.width());
        helpButton.setHeight(layout.height());
        clampDescriptionWidgets(screen, layout);
        Constants.debug(
                "YACL guide button layout: strategy={}, button={}, search={}, done={}.",
                layout.strategy(),
                new WidgetBounds(layout.x(), layout.y(), layout.width(), layout.height()),
                layout.searchBounds(),
                layout.doneBounds()
        );

        if (screen.children().contains(helpButton)) {
            addHighlightRenderable(screen, helpButton);
            return;
        }

        if (addRenderableWidget(screen, helpButton)) {
            addHighlightRenderable(screen, helpButton);
        } else {
            if (!Boolean.TRUE.equals(FAILURE_LOGGED.get(screen))) {
                Constants.debug("Unable to install Seamless Sleep YACL guide button: addRenderableWidget is unavailable.");
                FAILURE_LOGGED.put(screen, true);
            }
        }
    }

    private static Button createButton(Screen screen) {
        return Button.builder(
                        Component.translatable("config.seamlesssleep.guide_button"),
                        button -> {
                            if (SeamlessSleepLocalClientHints.markYaclGuideClicked()
                                    && !SeamlessSleepLocalClientHints.shouldShowYaclGuideHighlight()) {
                                removeHighlightRenderable(screen);
                            }
                            Util.getPlatform().openUri(GUIDE_URL);
                        })
                .bounds(0, 0, 1, FALLBACK_BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("config.seamlesssleep.guide_button.tooltip")))
                .build();
    }

    private static boolean isYaclScreen(Screen screen) {
        return "dev.isxander.yacl3.gui.YACLScreen".equals(screen.getClass().getName());
    }

    private static ButtonLayout findFooterLayout(Screen screen) {
        Button saveButton = findFooterButton(screen);
        WidgetBounds searchField = findSearchField(screen);
        if (saveButton != null) {
            WidgetBounds doneBounds = widgetBounds(saveButton);
            WidgetBounds reflectedSearchField = reflectedWidgetBounds(screen, SEARCH_FIELD);
            if (searchField == null) {
                searchField = reflectedSearchField;
            }
            return layoutFromDoneAndSearch(
                    saveButton.getX(),
                    saveButton.getY(),
                    saveButton.getWidth(),
                    saveButton.getHeight(),
                    searchField,
                    doneBounds,
                    searchField == null ? "children_done_expected_search" : "children"
            );
        }

        AbstractWidget reflectedSaveButton = currentCategoryWidget(screen, SAVE_BUTTON_FIELD);
        WidgetBounds reflectedSearchField = reflectedWidgetBounds(screen, SEARCH_FIELD);
        if (reflectedSaveButton != null) {
            WidgetBounds doneBounds = widgetBounds(reflectedSaveButton);
            return layoutFromDoneAndSearch(
                    reflectedSaveButton.getX(),
                    reflectedSaveButton.getY(),
                    reflectedSaveButton.getWidth(),
                    reflectedSaveButton.getHeight(),
                    reflectedSearchField,
                    doneBounds,
                    reflectedSearchField == null ? "reflection_done_expected_search" : "reflection"
            );
        }

        return fallbackLayout(screen);
    }

    private static ButtonLayout layoutFromDoneAndSearch(int x,
                                                        int doneY,
                                                        int width,
                                                        int height,
                                                        WidgetBounds searchField,
                                                        WidgetBounds doneBounds,
                                                        String strategy) {
        WidgetBounds searchBounds = searchField == null
                ? expectedSearchBoundsFromDone(x, doneY, width)
                : searchField;
        return layoutAboveSearch(x, width, height, searchBounds, doneBounds, strategy);
    }

    private static ButtonLayout layoutAboveSearch(int x,
                                                  int width,
                                                  int preferredHeight,
                                                  WidgetBounds searchBounds,
                                                  WidgetBounds doneBounds,
                                                  String strategy) {
        int gap = searchBounds.y() >= preferredHeight + FOOTER_VERTICAL_GAP
                ? FOOTER_VERTICAL_GAP
                : 1;
        int y = searchBounds.y() - preferredHeight - gap;
        int height = preferredHeight;
        if (y < 0) {
            y = 0;
            if (y + height >= searchBounds.y()) {
                height = Math.max(1, searchBounds.y() - y - 1);
            }
        }
        return new ButtonLayout(x, y, width, height, strategy, searchBounds, doneBounds);
    }

    private static WidgetBounds expectedSearchBoundsFromDone(int x, int doneY, int width) {
        return new WidgetBounds(
                x,
                Math.max(0, doneY - YACL_FOOTER_ROW_STEP * 2),
                width,
                FALLBACK_SEARCH_HEIGHT
        );
    }

    private static Button findFooterButton(Screen screen) {
        List<? extends GuiEventListener> children = screen.children();
        return children.stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getX() >= screen.width / 2)
                .filter(button -> button.getY() >= screen.height / 2)
                .filter(button -> !button.getMessage().getString().equals(Component.translatable("config.seamlesssleep.guide_button").getString()))
                .max(Comparator
                        .comparingInt(Button::getY)
                        .thenComparingInt(Button::getX))
                .orElse(null);
    }

    private static WidgetBounds findSearchField(Screen screen) {
        return screen.children().stream()
                .filter(child -> child.getClass().getName().equals("dev.isxander.yacl3.gui.SearchFieldWidget"))
                .map(SeamlessSleepYaclHelpButton::widgetBounds)
                .filter(bounds -> bounds != null && bounds.x() >= screen.width / 2 && bounds.y() >= screen.height / 2)
                .max(Comparator.comparingInt(WidgetBounds::y))
                .orElse(null);
    }

    private static WidgetBounds widgetBounds(Object widget) {
        try {
            Method getX = widget.getClass().getMethod("getX");
            Method getY = widget.getClass().getMethod("getY");
            Method getWidth = widget.getClass().getMethod("getWidth");
            Method getHeight = widget.getClass().getMethod("getHeight");
            return new WidgetBounds(
                    (Integer) getX.invoke(widget),
                    (Integer) getY.invoke(widget),
                    (Integer) getWidth.invoke(widget),
                    (Integer) getHeight.invoke(widget)
            );
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static WidgetBounds reflectedWidgetBounds(Screen screen, String fieldName) {
        AbstractWidget widget = currentCategoryWidget(screen, fieldName);
        return widget == null ? null : widgetBounds(widget);
    }

    private static void clampDescriptionWidgets(Screen screen, ButtonLayout layout) {
        Object currentTab = currentTab(screen);
        if (isCategoryTab(currentTab)) {
            clampDescriptionWidget(screen, currentTab, layout);
        }

        Object tabNavigationBar = fieldValue(screen, TAB_NAVIGATION_BAR_FIELD);
        if (tabNavigationBar == null) {
            return;
        }

        try {
            Method getTabs = tabNavigationBar.getClass().getMethod("getTabs");
            Object tabs = getTabs.invoke(tabNavigationBar);
            if (tabs instanceof Iterable<?> iterable) {
                for (Object tab : iterable) {
                    if (tab != currentTab && isCategoryTab(tab)) {
                        clampDescriptionWidget(screen, tab, layout);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static boolean isCategoryTab(Object tab) {
        return tab != null && YACL_CATEGORY_TAB_CLASS.equals(tab.getClass().getName());
    }

    private static void clampDescriptionWidget(Screen screen, Object categoryTab, ButtonLayout layout) {
        Object descriptionWidget = fieldValue(categoryTab, DESCRIPTION_WIDGET_FIELD);
        if (descriptionWidget == null
                || !descriptionWidget.getClass().getName().equals("dev.isxander.yacl3.gui.OptionDescriptionWidget")) {
            return;
        }

        WidgetBounds searchBounds = layout.searchBounds();
        WidgetBounds doneBounds = layout.doneBounds();
        int rightThird = Math.max(1, screen.width / 3);
        int padding = Math.max(2, rightThird / 20);
        int x = searchBounds == null ? layout.x() : searchBounds.x();
        int y = 23 + padding;
        int width = searchBounds == null ? layout.width() : Math.max(1, searchBounds.width());
        int height = Math.max(1, layout.y() - 1 - y);
        Supplier<ScreenRectangle> dimensions = () -> new ScreenRectangle(x, y, width, height);

        if (setFieldValue(descriptionWidget, DESCRIPTION_DIMENSIONS_FIELD, dimensions)) {
            Constants.debug(
                    "YACL guide button clamped description: description={}, button={}, search={}, done={}.",
                    new WidgetBounds(x, y, width, height),
                    new WidgetBounds(layout.x(), layout.y(), layout.width(), layout.height()),
                    searchBounds,
                    doneBounds
            );
        }
    }

    private static ButtonLayout fallbackLayout(Screen screen) {
        if (screen.width <= 0 || screen.height <= 0) {
            return null;
        }
        int rightThird = Math.max(1, screen.width / 3);
        int margin = Math.max(2, rightThird / 20);
        int paneWidth = Math.min(rightThird, FALLBACK_MAX_RIGHT_PANE_WIDTH);
        int buttonWidth = Mth.clamp(paneWidth - margin * 2, 80, Math.max(80, rightThird - 4));
        int centerX = screen.width * 5 / 6;
        int x = Mth.clamp(centerX - buttonWidth / 2, screen.width / 2 + 1, Math.max(screen.width / 2 + 1, screen.width - buttonWidth - 2));
        int doneY = screen.height - margin - FALLBACK_BUTTON_HEIGHT;
        WidgetBounds doneBounds = new WidgetBounds(x, doneY, buttonWidth, FALLBACK_BUTTON_HEIGHT);
        WidgetBounds searchBounds = expectedSearchBoundsFromDone(x, doneY, buttonWidth);
        return layoutAboveSearch(x, buttonWidth, FALLBACK_BUTTON_HEIGHT, searchBounds, doneBounds, "geometry");
    }

    private static AbstractWidget currentCategoryWidget(Screen screen, String fieldName) {
        Object currentTab = currentTab(screen);
        if (currentTab == null || !YACL_CATEGORY_TAB_CLASS.equals(currentTab.getClass().getName())) {
            return null;
        }
        Object value = fieldValue(currentTab, fieldName);
        return value instanceof AbstractWidget widget ? widget : null;
    }

    private static Object currentTab(Screen screen) {
        try {
            Object tabManager = fieldValue(screen, TAB_MANAGER_FIELD);
            if (tabManager == null) {
                return null;
            }
            Method getCurrentTab;
            try {
                getCurrentTab = tabManager.getClass().getMethod("getCurrentTab");
            } catch (NoSuchMethodException ignored) {
                getCurrentTab = tabManager.getClass().getMethod("method_48614");
            }
            return getCurrentTab.invoke(tabManager);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object fieldValue(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean setFieldValue(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean addRenderableWidget(Screen screen, Button button) {
        if (invokeAddRenderableWidget(screen, button)) {
            return true;
        }
        return addRenderableWidgetToLists(screen, button);
    }

    private static void addHighlightRenderable(Screen screen, Button button) {
        if (!SeamlessSleepLocalClientHints.shouldShowYaclGuideHighlight()) {
            removeHighlightRenderable(screen);
            return;
        }

        try {
            SeamlessSleepButtonCornerHighlight highlight = INSTALLED_HIGHLIGHTS.computeIfAbsent(
                    screen,
                    ignored -> new SeamlessSleepButtonCornerHighlight(button)
            );
            List<Renderable> renderables = screenList(screen, "renderables", 2, renderablesField);
            renderables.remove(highlight);
            int buttonIndex = renderables.indexOf(button);
            if (buttonIndex >= 0) {
                renderables.add(buttonIndex, highlight);
            } else {
                renderables.add(highlight);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void removeHighlightRenderable(Screen screen) {
        SeamlessSleepButtonCornerHighlight highlight = INSTALLED_HIGHLIGHTS.remove(screen);
        if (highlight == null) {
            return;
        }

        try {
            List<Renderable> renderables = screenList(screen, "renderables", 2, renderablesField);
            renderables.remove(highlight);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static boolean invokeAddRenderableWidget(Screen screen, Button button) {
        try {
            Method method = addRenderableWidgetMethod;
            if (method == null) {
                method = findScreenMethod("addRenderableWidget", "method_37063");
                addRenderableWidgetMethod = method;
            }
            method.invoke(screen, button);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Method findScreenMethod(String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method method = Screen.class.getDeclaredMethod(name, GuiEventListener.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(String.join("/", names));
    }

    private static boolean addRenderableWidgetToLists(Screen screen, Button button) {
        try {
            List<GuiEventListener> children = screenList(screen, "children", 0, childrenField);
            List<Renderable> renderables = screenList(screen, "renderables", 2, renderablesField);
            List<NarratableEntry> narratables = screenList(screen, "narratables", 1, narratablesField);
            addIfMissing(children, button);
            addIfMissing(renderables, button);
            addIfMissing(narratables, button);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> screenList(Screen screen, String namedField, int listIndex, Field cachedField)
            throws ReflectiveOperationException {
        Field field = cachedField;
        if (field == null) {
            field = findScreenListField(namedField, listIndex);
            if ("children".equals(namedField)) {
                childrenField = field;
            } else if ("renderables".equals(namedField)) {
                renderablesField = field;
            } else if ("narratables".equals(namedField)) {
                narratablesField = field;
            }
        }
        return (List<T>) field.get(screen);
    }

    private static Field findScreenListField(String namedField, int listIndex) throws NoSuchFieldException {
        try {
            Field field = Screen.class.getDeclaredField(namedField);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
        }

        int seen = 0;
        for (Field field : Screen.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            if (seen == listIndex) {
                field.setAccessible(true);
                return field;
            }
            seen++;
        }
        throw new NoSuchFieldException(namedField);
    }

    private static <T> void addIfMissing(List<T> list, T value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private record ButtonLayout(int x,
                                int y,
                                int width,
                                int height,
                                String strategy,
                                WidgetBounds searchBounds,
                                WidgetBounds doneBounds) {
    }

    private record WidgetBounds(int x, int y, int width, int height) {
    }
}
