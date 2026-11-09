package com.vehiclestudio.client.screen;

import com.vehiclestudio.data.VehicleGeometry;
import com.vehiclestudio.VehicleStudio;
import com.vehiclestudio.client.ClientFiles;
import com.vehiclestudio.client.EditorSettings;
import com.vehiclestudio.client.render.PaintAtlas;
import com.vehiclestudio.client.render.Materials;
import com.vehiclestudio.data.Voxels;
import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehicleJson;
import com.vehiclestudio.data.VehiclePart;
import com.vehiclestudio.data.DefCodec;
import com.vehiclestudio.data.EngineSound;
import com.vehiclestudio.data.HornSound;
import com.vehiclestudio.data.LightMode;
import com.vehiclestudio.data.PartKind;
import com.vehiclestudio.data.ThrusterStyle;
import com.vehiclestudio.data.PropAxis;
import com.vehiclestudio.data.FlightMode;
import com.vehiclestudio.data.RimStyle;
import com.vehiclestudio.data.SeatStyle;
import com.vehiclestudio.data.SteeringStyle;
import com.vehiclestudio.data.Tread;
import com.vehiclestudio.data.VehicleParts;
import com.vehiclestudio.data.WheelPreset;
import com.vehiclestudio.data.MaterialIds;
import com.vehiclestudio.data.PaintLayout;
import com.vehiclestudio.data.PoseSolver;
import com.vehiclestudio.data.SkinLayout;
import com.vehiclestudio.data.Templates;
import com.vehiclestudio.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Editor de vehiculos. La vista 3D ocupa casi toda la pantalla (como en un programa de CAD) y a la derecha
 * hay un panel con una fila de pestanas con iconos: Plantillas, Cuerpo, Piezas, Forma, Pintar, Animar,
 * Atributos, Conducta y Aparicion. El panel se puede plegar (tecla H) y su ancho se cambia arrastrando el
 * borde. En el modo facil solo se ven Cuerpo, Plantillas y Pintar. No hay ventanas emergentes: el color y las
 * texturas se eligen dentro de su pestana y las listas largas se abren como desplegables.
 */
public class EditorScreen extends Screen {

    /** Pestanas del editor, en el orden en que se ven (teclas 1, 2, 3...). */
    private enum Tab {
        TEMPLATE("template"),
        PARTS("parts"),
        SHAPE("shape"),
        WHEELS("wheels"),
        SEATS("seats"),
        PAINT("paint"),
        DRIVE("drive"),
        FLIGHT("flight");

        private final String key;
        @Nullable
        private ItemStack icon;

        Tab(String key) {
            this.key = key;
        }

        /** Pestanas que trabajan sobre la pieza seleccionada. */
        boolean usesPart() {
            return this == PARTS || this == SHAPE || this == WHEELS || this == SEATS || this == PAINT || this == FLIGHT;
        }

        MutableComponent title() {
            return Ui.tr("tab." + key);
        }

        MutableComponent description() {
            return Ui.tr("tab." + key + ".desc");
        }

        ItemStack icon() {
            if (icon == null) {
                icon = new ItemStack(switch (this) {
                    case TEMPLATE -> Items.BOOK;
                    case PARTS -> Items.CRAFTING_TABLE;
                    case SHAPE -> Items.SCAFFOLDING;
                    case WHEELS -> Items.MINECART;
                    case SEATS -> Items.SADDLE;
                    case PAINT -> Items.BRUSH;
                    case DRIVE -> Items.COMPASS;
                    case FLIGHT -> Items.ELYTRA;
                });
            }
            return icon;
        }
    }

    /** Herramientas del raton en la vista 3D (boton "Raton"). */
    private enum Tool {
        MOVE("move"),
        RESIZE("resize"),
        BRUSH("brush"),
        ERASER("eraser"),
        PICKER("picker");

        private final String key;

        Tool(String key) {
            this.key = key;
        }

        boolean paints() {
            return this == BRUSH || this == ERASER || this == PICKER;
        }

        MutableComponent title() {
            return Ui.tr("tool." + key);
        }

        MutableComponent help() {
            return Ui.tr("tool." + key + ".help");
        }
    }

    private static final Tab[] TABS = Tab.values();
    /** Opciones Si/No para los grupos de botones. */
    private static final Boolean[] NO_YES = {Boolean.FALSE, Boolean.TRUE};
    private static final Boolean[] YES_NO = {Boolean.TRUE, Boolean.FALSE};
    private static final Tool[] SHAPE_TOOLS = {Tool.MOVE, Tool.RESIZE};
    private static final Tool[] PAINT_TOOLS = {Tool.BRUSH, Tool.ERASER, Tool.PICKER};
    private static final Integer[] BRUSH_SIZES = {1, 2, 3, 4, 6, 8};
    private static final int[] AXIS_COLORS = {0xFFFF5A5A, 0xFF5AE05A, 0xFF5A9CFF};
    /** Eje (0=X, 1=Y, 2=Z) y sentido de cada cara de {@link PaintLayout}. */
    private static final int[] FACE_AXIS = {2, 2, 0, 0, 1, 1};
    private static final int[] FACE_SIGN = {-1, 1, 1, -1, 1, -1};
    private static final int HEADER_H = 20;
    /** Fila de iconos de las pestanas, encima del panel. */
    private static final int TAB_H = 20;
    /** Titulo de la pestana, debajo de los iconos. */
    private static final int TITLE_H = 14;
    /** Panel plegado: ancho de la columna de iconos y alto de cada icono. */
    private static final int STRIP_W = 22;
    private static final int STRIP_CELL = 20;
    /** Boton de plegar el panel, a la izquierda de la fila de pestanas. */
    private static final int FOLD_W = 12;
    private static final int MIN_PANEL = 190;
    private static final int PAD = 6;
    private static final int SCROLLBAR = 4;
    private static final int ROW = 18;
    private static final int FIELD_H = 16;
    private static final int CHIP_H = 14;
    private static final int LIST_ROW = 12;
    private static final int GAP = 3;
    private static final int CELL = 18;
    private static final int CARD_H = 24;
    private static final int HINT = 0xFF7D8491;
    private static final int WARN = 0xFFE0B050;
    private static final int UNSAVED = 0xFFFFA040;
    private static final int[] PALETTE = {
            0xFFFFFF, 0xD9D9D9, 0x7F7F7F, 0x2B2B2B, 0xE53935, 0xFB8C00, 0xFDD835, 0x7CB342,
            0x2E7D32, 0x26A69A, 0x29B6F6, 0x1E5BD8, 0x7E57C2, 0xD81B60, 0x8D6E63, 0xF2C79B
    };

    private final Screen parent;
    private VehicleDefinition def;
    private boolean isNew;
    private boolean dirty;
    private boolean nameEdited;
    private int selected;
    private Tab tab;
    private final int[] tabScroll = new int[TABS.length];
    private int partsScroll;
    /** Vista 3D: las ruedas ruedan y giran, las luces se encienden (para ver como queda en marcha). */
    private boolean testDrive;
    private boolean testLights;

    private final Preview3D preview = new Preview3D();
    private final Preview3D templatePreview = new Preview3D();
    private boolean viewFitted;
    private int hoveredTemplate = -1;
    private int fittedTemplate = -1;
    @Nullable
    private VehicleDefinition[] templateModels;
    private boolean needsRebuild;
    private boolean refreshing;

    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private String committed;
    private long lastChange;

    private boolean dragging;
    private int dragButton;
    private double pressX;
    private double pressY;
    private boolean moved;
    private boolean draggingScrollbar;
    private double scrollbarGrab;

    @Nullable
    private Component status;
    private int statusColor = Ui.TEXT;
    private long statusUntil;

    // Disposicion (se recalcula en init)
    private int panelX;
    private int panelW;
    private int contentW;
    private int viewTop;
    private int viewBottom;
    private int contentHeight;
    private int nameLabelX;
    private int unsavedX;
    private int headerRight;
    @Nullable
    private Button saveButton;
    @Nullable
    private Button undoButton;
    @Nullable
    private Button redoButton;
    private final List<NumberField> numberFields = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<PanelWidget> panelWidgets = new ArrayList<>();
    private final List<Element> elements = new ArrayList<>();
    @Nullable
    private Element activeElement;
    @Nullable
    private TemplateGrid templateGrid;
    @Nullable
    private PartsList partsList;
    @Nullable
    private Dropdown dropdown;

    // Modo raton
    private boolean mouseMode;
    private Tool tool = Tool.MOVE;
    private int brushSize = 1;
    private int brushColor = 0xFFE53935;
    private final List<ViewChip> viewChips = new ArrayList<>();
    /** Asa que se esta arrastrando: 0-2 flecha X/Y/Z, 3 = la pieza entera, 10-15 = cara (10 + cara). */
    private int gizmoHandle = -1;
    private double gizmoX0;
    private double gizmoY0;
    private float[] gizmoA = new float[3];
    private float[] gizmoB = new float[3];
    private int gizmoAxisA;
    private int gizmoAxisB;
    private final float[] gizmoApplied = new float[3];
    @Nullable
    private VehiclePart gizmoStart;
    private boolean painting;
    private boolean strokeChanged;
    /** Pestana Pintar: true = se dibuja con minibloques en vez de pintar pixeles. */
    private boolean voxelMode;
    /** Minibloques: poner tambien en el otro lado (espejo). */
    private boolean voxelMirror = true;
    /** Minibloques: el pincel cambia el color de los que ya hay en vez de poner nuevos. */
    private boolean voxelRecolor;
    /** Minibloques puestos en el trazo actual (no cuentan al apuntar, asi se pinta una capa). */
    private final java.util.Set<Long> strokeVoxels = new java.util.HashSet<>();
    private double paintLastX;
    private double paintLastY;

    // Pintura en la vista 3D
    private final PaintAtlas editorPaint = new PaintAtlas(
            ResourceLocation.fromNamespaceAndPath(VehicleStudio.MODID, "paint/editor"));
    private final PaintAtlas previewPaint = new PaintAtlas(
            ResourceLocation.fromNamespaceAndPath(VehicleStudio.MODID, "paint/preview"));
    private int paintVersion = 1;
    private int paintBuilt;
    private boolean paintShown;
    @Nullable
    private VehicleDefinition previewPaintDef;
    private boolean previewPaintShown;

    // Panel y modo facil
    private boolean panelCollapsed;
    private boolean resizingPanel;
    private int resizeX;
    /** Color de las partes del cuerpo nuevas (modo facil). */
    private int easyColor = 0x8BC34A;
    /** Modo facil: los cambios se aplican tambien a la pieza del otro lado. */
    /** Ayuda de la parte de abajo de la vista (una linea; si no cabe, entera en el tooltip). */
    @Nullable
    private Component helpText;
    private boolean helpCut;
    private int helpX;
    private int helpY;
    private int helpW;


    /** Widget de Minecraft colocado dentro del panel con desplazamiento (y en coordenadas del contenido). */
    private record PanelWidget(AbstractWidget widget, int contentY) {
    }

    /** Boton dibujado en la barra de la vista 3D (action = null: solo texto; swatch: muestra el color del pincel). */
    private record ViewChip(int x, int y, int w, Component label, BooleanSupplier chosen, @Nullable Runnable action,
                            @Nullable Component tip, boolean swatch) {
    }

    /** Fila de un desplegable. color = -1 si no lleva muestra de color. */
    private record DropItem(Component label, int color, int indent, boolean selected) {
    }

    public EditorScreen(Screen parent, VehicleDefinition def, boolean isNew, boolean showTemplates) {
        super(Ui.tr("editor.title"));
        this.parent = parent;
        this.def = def;
        this.isNew = isNew;
        if (def.id.isEmpty()) {
            def.id = VehicleDefinition.newId();
        }
        this.selected = def.parts.isEmpty() ? -1 : 0;
        this.committed = VehicleJson.write(def, false);
        this.dirty = isNew;
        EditorSettings.load();
        this.panelCollapsed = EditorSettings.panelCollapsed;
        this.tab = showTemplates ? Tab.TEMPLATE : Tab.PARTS;
    }

    // =======================================================================================
    // Construccion de la interfaz
    // =======================================================================================

    @Override
    protected void init() {
        numberFields.clear();
        refreshers.clear();
        panelWidgets.clear();
        elements.clear();
        activeElement = null;
        templateGrid = null;
        partsList = null;
        dropdown = null;
        draggingScrollbar = false;
        resizingPanel = false;
        saveButton = null;
        undoButton = null;
        redoButton = null;

        Tab[] visible = visibleTabs();
        if (indexOf(visible, tab) < 0) {
            tab = visible[0];
        }
        if (panelCollapsed) {
            panelW = STRIP_W;
        } else {
            int auto = Mth.clamp(width * 3 / 10, MIN_PANEL, 240);
            panelW = Mth.clamp(EditorSettings.panelWidth > 0 ? EditorSettings.panelWidth : auto, MIN_PANEL, maxPanel());
        }
        panelX = width - panelW;
        buildIndent = 0;
        contentW = panelW - PAD * 2 - SCROLLBAR;
        viewTop = HEADER_H + TAB_H + TITLE_H;
        viewBottom = height;
        preview.setArea(0, HEADER_H, panelX, height - HEADER_H);
        templatePreview.setArea(0, HEADER_H, panelX, height - HEADER_H);
        fittedTemplate = -1;
        if (!viewFitted) {
            preview.fit(def);
            viewFitted = true;
        }
        if (selected >= def.parts.size()) {
            selected = def.parts.size() - 1;
        }
        if (selected < 0 && !def.parts.isEmpty()) {
            selected = 0;
        }

        buildHeader();
        buildViewBar();
        if (panelCollapsed) {
            contentHeight = 0;
            return;
        }
        int y = 4;
        int bottom = switch (tab) {
            case TEMPLATE -> buildTemplateTab(y);
            case PARTS -> buildPartsTab(y);
            case SHAPE -> buildShapeTab(y);
            case WHEELS -> buildWheelsTab(y);
            case SEATS -> buildSeatsTab(y);
            case PAINT -> buildPaintTab(y);
            case DRIVE -> buildDriveTab(y);
            case FLIGHT -> buildFlightTab(y);
        };
        contentHeight = bottom + PAD;
        setScroll(scroll());
    }

    private void requestRebuild() {
        needsRebuild = true;
    }

    private void buildHeader() {
        Component exitText = Ui.tr("editor.exit");
        Component saveText = Ui.tr("editor.save");
        int bh = HEADER_H - 4;
        int wExit = Math.max(40, font.width(exitText) + 12);
        int wSave = Math.max(50, font.width(saveText) + 16);
        int x = width - 3 - wExit;
        addRenderableWidget(Button.builder(exitText, b -> onClose()).bounds(x, 2, wExit, bh).build());
        x -= wSave + 3;
        saveButton = addRenderableWidget(Button.builder(saveText, b -> save()).bounds(x, 2, wSave, bh)
                .tooltip(Tooltip.create(Ui.tr("editor.save_tooltip"))).build());
        x -= 20 + 8;
        redoButton = addRenderableWidget(Button.builder(Component.literal("\u21B7"), b -> redo())
                .bounds(x, 2, 20, bh).tooltip(Tooltip.create(Ui.tr("editor.redo"))).build());
        x -= 20 + 2;
        undoButton = addRenderableWidget(Button.builder(Component.literal("\u21B6"), b -> undo())
                .bounds(x, 2, 20, bh).tooltip(Tooltip.create(Ui.tr("editor.undo"))).build());

        nameLabelX = 6;
        int nx = nameLabelX + font.width(Ui.tr("editor.name_label")) + 4;
        headerRight = x;

        int nameW = Mth.clamp(headerRight - 12 - nx, 50, 150);
        EditBox nameBox = new EditBox(font, nx, 2, nameW, bh, Ui.tr("editor.name"));
        nameBox.setMaxLength(VehicleDefinition.NAME_MAX);
        nameBox.setValue(def.name);
        nameBox.setHint(Ui.tr("editor.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(s -> {
            def.name = s;
            nameEdited = true;
            markChanged();
        });
        addRenderableWidget(nameBox);
        unsavedX = nx + nameW + 8;
    }

    /** Barra de la vista 3D: opciones de la vista y, con el raton activado, sus herramientas. */
    private void buildViewBar() {
        viewChips.clear();
        int left = preview.x + 4;
        int maxX = preview.x + preview.w - 4;
        int[] pos = {left, preview.y + 4};
        viewChip(pos, left, maxX, Ui.onOff("editor.preview.drive", testDrive), () -> testDrive,
                () -> setTestDrive(!testDrive), Ui.tr("editor.preview.drive_tip"), false);
        viewChip(pos, left, maxX, Ui.onOff("editor.preview.lights", testLights), () -> testLights,
                () -> setTestLights(!testLights), Ui.tr("editor.preview.lights_tip"), false);
        viewChip(pos, left, maxX, Ui.tr("editor.preview.reset"), () -> false,
                () -> preview.fit(def), Ui.tr("editor.preview.reset_tip"), false);
        viewChip(pos, left, maxX, Ui.onOff("editor.preview.mouse", mouseMode), () -> mouseMode,
                () -> setMouseMode(!mouseMode), Ui.tr("editor.preview.mouse_tooltip"), false);
        if (!mouseMode) {
            return;
        }
        pos[0] = left;
        pos[1] += 15;
        for (Tool t : Tool.values()) {
            viewChip(pos, left, maxX, t.title(), () -> tool == t, () -> setTool(t), t.help(), false);
        }
        if (tool == Tool.BRUSH || tool == Tool.ERASER) {
            pos[0] += 6;
            viewChip(pos, left, maxX, Component.literal("-"), () -> false, () -> changeBrush(-1), Ui.tr("tool.size_down"), false);
            viewChip(pos, left, maxX, Ui.tr("tool.size", brushSize), () -> false, null, null, false);
            viewChip(pos, left, maxX, Component.literal("+"), () -> false, () -> changeBrush(1), Ui.tr("tool.size_up"), false);
            if (tool == Tool.BRUSH) {
                viewChip(pos, left, maxX, Component.literal("    "), () -> false, () -> {
                            setPanelCollapsed(false);
                            switchTab(Tab.PAINT);
                        },
                        Ui.tr("tool.color_tip"), true);
            }
        }
    }

    private void viewChip(int[] pos, int left, int maxX, Component label, BooleanSupplier chosen,
                          @Nullable Runnable action, @Nullable Component tip, boolean swatch) {
        int w = font.width(label) + 10;
        if (pos[0] + w > maxX && pos[0] > left) {
            pos[0] = left;
            pos[1] += 15;
        }
        viewChips.add(new ViewChip(pos[0], pos[1], w, label, chosen, action, tip, swatch));
        pos[0] += w + 2;
    }

    private void setMouseMode(boolean on) {
        endMouseAction();
        mouseMode = on;
        requestRebuild();
    }

    private void setTool(Tool t) {
        endMouseAction();
        tool = t;
        mouseMode = true;
        requestRebuild();
    }

    private void changeBrush(int direction) {
        int i = 0;
        for (int k = 0; k < BRUSH_SIZES.length; k++) {
            if (BRUSH_SIZES[k] == brushSize) {
                i = k;
            }
        }
        brushSize = BRUSH_SIZES[Mth.clamp(i + direction, 0, BRUSH_SIZES.length - 1)];
        buildViewBar();
    }

    private void setTestDrive(boolean value) {
        testDrive = value;
        buildViewBar();
    }

    private void setTestLights(boolean value) {
        testLights = value;
        buildViewBar();
    }

    // ---------------------------------------------------------------------------------------
    // Pestanas visibles, panel plegable y modo facil
    // ---------------------------------------------------------------------------------------

    private Tab[] visibleTabs() {
        return TABS;
    }

    private static int indexOf(Tab[] tabs, Tab t) {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == t) {
                return i;
            }
        }
        return -1;
    }

    private int maxPanel() {
        return Math.max(MIN_PANEL, Math.min(340, width - 200));
    }

    private void setPanelCollapsed(boolean collapsed) {
        if (panelCollapsed == collapsed) {
            return;
        }
        panelCollapsed = collapsed;
        EditorSettings.panelCollapsed = collapsed;
        EditorSettings.save();
        dropdown = null;
        activeElement = null;
        setFocused(null);
        requestRebuild();
    }

    /** El raton esta sobre el borde del panel (se arrastra para cambiar su ancho). */
    private boolean dividerHit(double mx, double my) {
        return !panelCollapsed && my >= HEADER_H && mx >= panelX - 2 && mx <= panelX + 2;
    }

    // ---------------------------------------------------------------------------------------
    // Contenido de cada pestana (coordenadas del contenido: x desde el borde izquierdo del panel,
    // y desde arriba del contenido sin desplazar)
    // ---------------------------------------------------------------------------------------

    private int buildTemplateTab(int y) {
        y = intro(y);
        y = text(y, Ui.tr("templates.warning"), WARN) + 6;
        templateGrid = add(new TemplateGrid(y));
        y += templateGrid.h + 6;
        return text(y, Ui.tr("templates.hint"), HINT) + 10;
    }

    private int buildPartsTab(int y) {
        y = intro(y);
        VehiclePart p = sel();
        boolean has = p != null;
        int bw = (contentW - GAP * 2) / 3;
        y = smallLabel(y, Ui.tr("parts.add"));
        PartKind[] kinds = PartKind.values();
        for (int k = 0; k < kinds.length; k++) {
            PartKind kind = kinds[k];
            int col = k % 3;
            int bx = col * (bw + GAP);
            button(bx, y, col == 2 ? contentW - bx : bw, Ui.tr("kind.add." + kind.key()),
                    b -> addPart(kind), Ui.tr("kind." + kind.key() + ".tip"));
            if (col == 2 || k == kinds.length - 1) {
                y += ROW;
            }
        }
        y += 4;
        button(0, y, bw, Ui.tr("editor.part.add"), b -> addPart(PartKind.BODY), Ui.tr("editor.part.add_tooltip"));
        button(bw + GAP, y, bw, Ui.tr("editor.part.duplicate"), b -> duplicateSelected(false),
                Ui.tr("editor.part.duplicate_tooltip")).active = has;
        button((bw + GAP) * 2, y, contentW - (bw + GAP) * 2, Ui.tr("editor.part.mirror"), b -> duplicateSelected(true),
                Ui.tr("editor.part.mirror_tooltip")).active = has;
        y += ROW;
        int half = (contentW - GAP) / 2;
        button(0, y, half, Ui.tr("editor.part.delete"), b -> deleteSelected(),
                Ui.tr("editor.part.delete_tooltip")).active = has;
        button(half + GAP, y, contentW - half - GAP, Ui.tr("editor.part.center_model"), b -> centerModel(),
                Ui.tr("editor.part.center_tooltip")).active = !def.parts.isEmpty();
        y += ROW + 4;
        if (p != null) {
            int lw = labelWidth("editor.field.name", "editor.field.parent");
            label(0, y, Ui.tr("editor.field.name"));
            textBox(lw, y, contentW - lw, VehiclePart.NAME_MAX, p.name, s -> {
                p.name = s;
                markChanged();
            }, null);
            y += ROW;
            label(0, y, Ui.tr("editor.field.parent"));
            add(new DropButton(lw, y, contentW - lw, () -> parentLabel(p), () -> parentColor(p),
                    Ui.tr("editor.field.parent_tooltip"), this::openParentDropdown));
            y += ROW + 2;
            y = smallLabel(y, Ui.tr("parts.kind"));
            y = chips(y, PartKind.values(), 3, () -> p.kind, v -> changeKind(p, v), v -> Ui.tr("kind." + v.key()),
                    v -> Ui.tr("kind." + v.key() + ".tip")) + 4;
            if (p.kind == PartKind.LIGHT) {
                y = smallLabel(y, Ui.tr("parts.light_mode"));
                y = chips(y, LightMode.values(), 3, () -> p.lightMode, v -> p.lightMode = v,
                        v -> Ui.tr("light." + v.key()), v -> Ui.tr("light." + v.key() + ".tip")) + 4;
            } else if (p.kind == PartKind.STEERING) {
                y = smallLabel(y, Ui.tr("parts.steering_style"));
                y = chips(y, SteeringStyle.values(), 2, () -> p.steeringStyle, v -> p.steeringStyle = v,
                        v -> Ui.tr("steering." + v.key()), v -> Ui.tr("steering." + v.key() + ".tip")) + 4;
            }
            y = toggleRow(y, "parts.solid", () -> p.solid, v -> p.solid = v, "parts.solid_tip") + 4;
        }
        y = section(y, Ui.tr("parts.list", def.parts.size(), VehicleDefinition.MAX_PARTS));
        int listH = Math.max(LIST_ROW * 5 + 4, viewBottom - viewTop - y - PAD);
        partsList = add(new PartsList(y, listH));
        return y + listH;
    }

    private int buildShapeTab(int y) {
        y = intro(y);
        VehiclePart p = sel();
        if (p == null) {
            return noParts(y);
        }
        y = pieceSelector(y);
        y = mouseHelper(y, SHAPE_TOOLS, "tool.shape_off");
        int lw = labelWidth("editor.field.position", "editor.field.size", "editor.field.pivot", "editor.field.rotation");
        y = axisHeader(y, lw);
        final int index = selected;
        y = vecRow(y, lw, "editor.field.position",
                () -> p.x, v -> moveSubtree(index, v - p.x, 0f, 0f),
                () -> p.y, v -> moveSubtree(index, 0f, v - p.y, 0f),
                () -> p.z, v -> moveSubtree(index, 0f, 0f, v - p.z),
                -512f, 512f, 1f);
        y = vecRow(y, lw, "editor.field.size",
                () -> p.w, v -> resize(p, v, p.h, p.d),
                () -> p.h, v -> resize(p, p.w, v, p.d),
                () -> p.d, v -> resize(p, p.w, p.h, v),
                0f, 256f, 1f);
        y = vecRow(y, lw, "editor.field.pivot",
                () -> p.px, v -> p.px = v,
                () -> p.py, v -> p.py = v,
                () -> p.pz, v -> p.pz = v,
                -512f, 512f, 1f);
        y = vecRow(y, lw, "editor.field.rotation",
                () -> p.rx, v -> p.rx = v,
                () -> p.ry, v -> p.ry = v,
                () -> p.rz, v -> p.rz = v,
                -360f, 360f, 5f);
        int half = (contentW - GAP) / 2;
        button(0, y, half, Ui.tr("editor.pivot.center"), b -> {
            p.centerPivot();
            markChanged();
        }, Ui.tr("editor.pivot.center_tooltip"));
        button(half + GAP, y, contentW - half - GAP, Ui.tr("editor.rotation.reset"), b -> {
            p.rx = 0f;
            p.ry = 0f;
            p.rz = 0f;
            markChanged();
        }, null);
        y += ROW + 6;
        return text(y, Ui.tr("editor.hint.fields"), HINT);
    }

    /** Cambia el tipo de una pieza (las ruedas pasan a tener el pivote en el centro). */
    private void changeKind(VehiclePart p, PartKind kind) {
        if (p.kind == kind) {
            return;
        }
        if (kind == PartKind.SEAT && p.kind != PartKind.SEAT && def.countKind(PartKind.SEAT) >= VehicleDefinition.MAX_SEATS) {
            warn(Ui.tr("editor.too_many_seats", VehicleDefinition.MAX_SEATS));
            return;
        }
        if (kind == PartKind.WHEEL && p.kind != PartKind.WHEEL
                && def.countKind(PartKind.WHEEL) >= VehicleDefinition.MAX_WHEELS) {
            warn(Ui.tr("editor.too_many_wheels", VehicleDefinition.MAX_WHEELS));
            return;
        }
        if (kind.propels() && !p.kind.propels() && propulsionCount() >= VehicleDefinition.MAX_PROPULSION) {
            warn(Ui.tr("editor.too_many_propulsion", VehicleDefinition.MAX_PROPULSION));
            return;
        }
        p.kind = kind;
        if (kind == PartKind.WHEEL) {
            p.centerPivot();
            p.material = MaterialIds.SOLID;
        }
        if (kind == PartKind.PROPELLER) {
            p.centerPivot();
            p.propAxis = p.h < Math.min(p.w, p.d) ? PropAxis.TOP : PropAxis.FRONT;
        }
        if (kind == PartKind.SEAT && def.countKind(PartKind.SEAT) == 1) {
            p.driver = true;
        }
        markChanged();
        requestRebuild();
    }

    /** Elige la primera pieza de un tipo (si la elegida no lo es) y dice si hay alguna. */
    @Nullable
    private VehiclePart selectKind(PartKind kind) {
        VehiclePart p = sel();
        if (p != null && p.kind == kind) {
            return p;
        }
        return null;
    }

    private int firstOf(PartKind kind) {
        for (int i = 0; i < def.parts.size(); i++) {
            if (def.parts.get(i).kind == kind) {
                return i;
            }
        }
        return -1;
    }

    /** Si la pieza elegida no es de ese tipo: un aviso y botones para elegir una o anadirla. Devuelve la nueva y. */
    private int notThisKind(int y, PartKind kind) {
        int first = firstOf(kind);
        y = text(y, Ui.tr(first >= 0 ? "kind.pick." + kind.key() : "kind.none." + kind.key()), Ui.TEXT) + 6;
        int half = (contentW - GAP) / 2;
        if (first >= 0) {
            button(0, y, half, Ui.tr("kind.select." + kind.key()), b -> select(firstOf(kind)), null);
            button(half + GAP, y, contentW - half - GAP, Ui.tr("kind.add." + kind.key()), b -> addPart(kind), null);
        } else {
            button(0, y, contentW, Ui.tr("kind.add." + kind.key()), b -> addPart(kind), null);
        }
        return y + ROW + 6;
    }

    // ---------------------------------------------------------------------------------------
    // Ruedas
    // ---------------------------------------------------------------------------------------

    private int buildWheelsTab(int y) {
        y = intro(y);
        if (def.countKind(PartKind.WHEEL) == 0) {
            y = notThisKind(y, PartKind.WHEEL);
            button(0, y, contentW, Ui.tr("wheels.add_four"), b -> addFourWheels(), Ui.tr("wheels.add_four_tip"));
            return y + ROW + 6;
        }
        VehiclePart p = selectKind(PartKind.WHEEL);
        if (p == null) {
            return notThisKind(y, PartKind.WHEEL);
        }
        y = pieceSelector(y);
        y = section(y, Ui.tr("wheels.presets"));
        y = text(y, Ui.tr("wheels.presets_hint"), HINT) + 3;
        WheelPreset[] presets = WheelPreset.values();
        int half = (contentW - GAP) / 2;
        for (int i = 0; i < presets.length; i++) {
            WheelPreset preset = presets[i];
            boolean left = i % 2 == 0;
            button(left ? 0 : half + GAP, y, left ? half : contentW - half - GAP, Ui.tr("wheel." + preset.key()), b -> {
                preset.applyTo(p, false);
                markChanged();
            }, Ui.tr("wheel." + preset.key() + ".tip"));
            if (!left || i == presets.length - 1) {
                y += ROW;
            }
        }
        y += 4;
        y = section(y, Ui.tr("wheels.size"));
        y = numField(y, "wheels.diameter", () -> Math.min(p.h, p.d), v -> setWheelDiameter(p, v), 1f, 96f, 1f, false);
        y = numField(y, "wheels.width", () -> p.w, v -> {
            float cx = p.x + p.w / 2f;
            p.w = v;
            p.x = cx - v / 2f;
            p.centerPivot();
        }, 0.5f, 64f, 0.5f, false);
        y = toggleRow(y, "wheels.steers", () -> p.steers, v -> p.steers = v, "wheels.steers_tip") + 4;

        y = section(y, Ui.tr("wheels.tire"));
        y = colorRows(y, () -> p.color, c -> p.color = c);
        y = smallLabel(y, Ui.tr("wheels.tread"));
        y = chips(y, Tread.values(), 2, () -> p.tread, v -> p.tread = v, v -> Ui.tr("tread." + v.key()), null) + 4;

        y = section(y, Ui.tr("wheels.rim"));
        y = colorRows(y, () -> p.rimColor, c -> p.rimColor = c);
        y = smallLabel(y, Ui.tr("wheels.rim_style"));
        y = chips(y, RimStyle.values(), 2, () -> p.rimStyle, v -> p.rimStyle = v, v -> Ui.tr("rim." + v.key()), null) + 4;
        y = sliderRow(y, Math.max(12, font.width(Ui.tr("wheels.rim_size")) + 6), Ui.tr("wheels.rim_size"), Ui.MUTED,
                () -> p.rimSize, v -> p.rimSize = v, 0.3f, 0.95f, 0f, 0.01f, 0.05f, Ui.tr("wheels.rim_size_tip"));
        y += 4;
        button(0, y, contentW, Ui.tr("wheels.copy_all"), b -> copyWheelToAll(p), Ui.tr("wheels.copy_all_tip"));
        y += ROW + 6;
        return text(y, Ui.tr("wheels.hint"), HINT);
    }

    /** Cambia el diametro sin mover el centro; si tocaba el suelo, lo sigue tocando. */
    private void setWheelDiameter(VehiclePart p, float diameter) {
        boolean grounded = Math.abs(p.y) < 0.01f;
        float cy = p.y + p.h / 2f;
        float cz = p.z + p.d / 2f;
        p.h = diameter;
        p.d = diameter;
        p.y = grounded ? 0f : cy - diameter / 2f;
        p.z = cz - diameter / 2f;
        p.centerPivot();
    }

    /** Copia el aspecto y el tamano de una rueda a las demas (cada una sigue en su sitio y gira o no como estaba). */
    private void copyWheelToAll(VehiclePart src) {
        for (VehiclePart q : def.parts) {
            if (q == src || q.kind != PartKind.WHEEL) {
                continue;
            }
            float cx = q.x + q.w / 2f;
            q.color = src.color;
            q.rimColor = src.rimColor;
            q.rimStyle = src.rimStyle;
            q.tread = src.tread;
            q.rimSize = src.rimSize;
            q.w = src.w;
            q.x = cx - src.w / 2f;
            setWheelDiameter(q, Math.min(src.h, src.d));
        }
        markChanged();
    }

    /** Cuatro ruedas de coche en las esquinas del vehiculo (las de delante giran). */
    private void addFourWheels() {
        for (int i = 0; i < 4 && def.countKind(PartKind.WHEEL) < VehicleDefinition.MAX_WHEELS; i++) {
            def.parts.add(VehicleParts.create(def, PartKind.WHEEL));
        }
        selected = def.parts.size() - 1;
        markChanged();
        requestRebuild();
    }

    /** Selector de color compacto (barra de color, hexadecimal y paleta). */
    private int colorRows(int y, IntSupplier getter, IntConsumer setter) {
        IntConsumer set = c -> {
            setter.accept(c & 0xFFFFFF);
            markChanged();
        };
        add(new ColorPicker(0, y, contentW, 40, getter, set));
        y += 40 + 4;
        add(new Swatch(0, y, FIELD_H, getter));
        hexBox(FIELD_H + 4, y, 58, getter, set);
        y += ROW;
        Palette palette = add(new Palette(0, y, contentW, getter, set));
        return y + palette.h + 4;
    }

    // ---------------------------------------------------------------------------------------
    // Asientos
    // ---------------------------------------------------------------------------------------

    private int buildSeatsTab(int y) {
        y = intro(y);
        add(new TextLine(0, y, () -> Ui.tr("seats.count", def.countKind(PartKind.SEAT), VehicleDefinition.MAX_SEATS),
                Ui.MUTED));
        y += 13;
        if (def.countKind(PartKind.SEAT) == 0) {
            y = text(y, Ui.tr("seats.none_warning"), WARN) + 4;
            return notThisKind(y, PartKind.SEAT);
        }
        VehiclePart p = selectKind(PartKind.SEAT);
        if (p == null) {
            return notThisKind(y, PartKind.SEAT);
        }
        y = pieceSelector(y);
        y = section(y, Ui.tr("seats.style"));
        y = chips(y, SeatStyle.values(), 2, () -> p.seatStyle, v -> p.seatStyle = v, v -> Ui.tr("seat." + v.key()),
                v -> Ui.tr("seat." + v.key() + ".tip")) + 4;
        y = toggleRow(y, "seats.driver", () -> p.driver || (def.driverSeat() == def.seats().indexOf(selected)
                && noDriverMarked()), v -> setDriver(p, v), "seats.driver_tip") + 4;
        y = buildPartLook(y, p);
        return text(y + 4, Ui.tr("seats.hint"), HINT);
    }

    private boolean noDriverMarked() {
        for (VehiclePart q : def.parts) {
            if (q.kind == PartKind.SEAT && q.driver) {
                return false;
            }
        }
        return true;
    }

    private void setDriver(VehiclePart p, boolean on) {
        for (VehiclePart q : def.parts) {
            q.driver = false;
        }
        p.driver = on;
    }

    // ---------------------------------------------------------------------------------------
    // Conduccion
    // ---------------------------------------------------------------------------------------

    private static final Integer[] STORAGE = {0, 9, 18, 27, 36, 45, 54};

    private int buildDriveTab(int y) {
        y = intro(y);
        if (def.countKind(PartKind.SEAT) == 0) {
            y = text(y, Ui.tr("seats.none_warning"), WARN) + 6;
        }
        y = section(y, Ui.tr("drive.engine"));
        y = numField(y, "drive.max_speed", () -> def.maxSpeed, v -> def.maxSpeed = v, 1f, VehicleDefinition.MAX_SPEED,
                5f, true);
        y = numField(y, "drive.accel", () -> def.accelTime, v -> def.accelTime = v, 0.3f, 30f, 0.5f, false);
        y = numField(y, "drive.brakes", () -> def.brakeTime, v -> def.brakeTime = v, 0.2f, 15f, 0.25f, false);
        y = numField(y, "drive.reverse", () -> (float) def.reverseSpeed, v -> def.reverseSpeed = Math.round(v), 0f, 100f,
                5f, true);
        y += 4;
        y = section(y, Ui.tr("drive.handling"));
        y = numField(y, "drive.steer", () -> def.steerAngle, v -> def.steerAngle = v, 5f, 70f, 1f, true);
        y = numField(y, "drive.drift", () -> (float) def.drift, v -> def.drift = Math.round(v), 0f, 100f, 5f, true);
        y = numField(y, "drive.step", () -> def.stepHeight, v -> def.stepHeight = v, 0f, 2f, 0.5f, false);
        add(new TextLine(0, y, () -> Ui.tr("drive.wheelbase", Ui.fmt(Math.round(def.wheelbase() * 10f) / 10f)), HINT));
        y += 13;
        y = section(y, Ui.tr("drive.body"));
        y = numField(y, "drive.health", () -> def.health, v -> def.health = v, 1f, 1000f, 5f, true);
        y = numField(y, "drive.scale", () -> def.scale, v -> def.scale = v, 0.25f, 4f, 0.25f, false);
        y = toggleRow(y, "drive.run_over", () -> def.runOver, v -> def.runOver = v, "drive.run_over_tip") + 4;
        y = section(y, Ui.tr("drive.extras"));
        y = smallLabel(y, Ui.tr("drive.storage"));
        y = chips(y, STORAGE, 4, () -> def.storage, v -> def.storage = v,
                v -> v == 0 ? Ui.tr("drive.storage.none") : Component.literal(String.valueOf(v)), null) + 4;
        y = enumRow(y, "drive.horn", HornSound.values(), () -> def.horn, v -> def.horn = v, v -> Ui.tr("horn." + v.key()));
        button(0, y, contentW, Ui.tr("drive.horn_test"), b -> playSound(), null);
        y += ROW;
        y = enumRow(y, "drive.engine_sound", EngineSound.values(), () -> def.engine, v -> def.engine = v,
                v -> Ui.tr("engine." + v.key()));
        y += 4;
        add(new TextLine(0, y, () -> Ui.tr("drive.cost", def.ironCost()), Ui.ACCENT));
        y += 14;
        y = section(y, Ui.tr("drive.controls"));
        return text(y, Ui.tr("drive.controls_text"), HINT);
    }

    // ---------------------------------------------------------------------------------------
    // Vuelo: helices, propulsores, vuelo y turbo
    // ---------------------------------------------------------------------------------------

    private int propulsionCount() {
        return def.countKind(PartKind.PROPELLER) + def.countKind(PartKind.THRUSTER);
    }

    private int buildFlightTab(int y) {
        y = intro(y);
        y = section(y, Ui.tr("flight.parts"));
        add(new TextLine(0, y, () -> Ui.tr("flight.count", def.countKind(PartKind.PROPELLER),
                def.countKind(PartKind.THRUSTER)), Ui.MUTED));
        y += 13;
        int half = (contentW - GAP) / 2;
        button(0, y, half, Ui.tr("kind.add.propeller"), b -> addPart(PartKind.PROPELLER), Ui.tr("kind.propeller.tip"));
        button(half + GAP, y, contentW - half - GAP, Ui.tr("kind.add.thruster"), b -> addPart(PartKind.THRUSTER),
                Ui.tr("kind.thruster.tip"));
        y += ROW + 4;
        VehiclePart p = sel();
        if (p != null && p.kind == PartKind.PROPELLER) {
            y = pieceSelector(y);
            y = buildPropeller(y, p);
        } else if (p != null && p.kind == PartKind.THRUSTER) {
            y = pieceSelector(y);
            y = buildThruster(y, p);
        } else if (propulsionCount() > 0) {
            y = text(y, Ui.tr("flight.pick"), Ui.TEXT) + 6;
            int firstProp = firstOf(PartKind.PROPELLER);
            int firstThr = firstOf(PartKind.THRUSTER);
            if (firstProp >= 0 && firstThr >= 0) {
                button(0, y, half, Ui.tr("kind.select.propeller"), b -> select(firstOf(PartKind.PROPELLER)), null);
                button(half + GAP, y, contentW - half - GAP, Ui.tr("kind.select.thruster"),
                        b -> select(firstOf(PartKind.THRUSTER)), null);
            } else {
                PartKind k = firstProp >= 0 ? PartKind.PROPELLER : PartKind.THRUSTER;
                button(0, y, contentW, Ui.tr("kind.select." + k.key()), b -> select(firstOf(k)), null);
            }
            y += ROW + 6;
        } else {
            y = text(y, Ui.tr("flight.none"), HINT) + 6;
        }

        y = section(y, Ui.tr("flight.section"));
        y = chips(y, FlightMode.values(), 3, () -> def.flight, v -> {
            def.flight = v;
            requestRebuild();
        }, v -> Ui.tr("flight_mode." + v.key()), v -> Ui.tr("flight_mode." + v.key() + ".tip")) + 4;
        if (def.flight != FlightMode.NONE) {
            if (propulsionCount() == 0) {
                y = text(y, Ui.tr("flight.need_parts"), WARN) + 4;
            }
            y = numField(y, "flight.air_speed", () -> def.airSpeed, v -> def.airSpeed = v, 5f,
                    VehicleDefinition.MAX_AIR_SPEED, 5f, true);
            y = numField(y, "flight.climb", () -> def.climb, v -> def.climb = v, 0.5f, 20f, 0.5f, false);
            if (def.flight == FlightMode.PLANE) {
                add(new TextLine(0, y, () -> Ui.tr("flight.takeoff", Math.round(def.takeoffSpeed())), HINT));
                y += 13;
            }
            y = text(y, Ui.tr(def.flight == FlightMode.PLANE ? "flight.plane_hint" : "flight.heli_hint"), HINT) + 6;
        }

        y = section(y, Ui.tr("turbo.section"));
        y = numField(y, "turbo.boost", () -> def.turbo, v -> def.turbo = v, 0f, VehicleDefinition.MAX_TURBO, 10f, true);
        y = numField(y, "turbo.time", () -> def.turboTime, v -> def.turboTime = v, 0.5f, 20f, 0.5f, false);
        y = numField(y, "turbo.recharge", () -> def.turboRecharge, v -> def.turboRecharge = v, 0f, 60f, 1f, false);
        add(new TextLine(0, y, () -> def.turbo > 0f && def.countKind(PartKind.THRUSTER) == 0
                ? Ui.tr("turbo.need_thruster") : Component.empty(), WARN));
        y += 13;
        y = text(y, Ui.tr("turbo.hint"), HINT) + 6;
        add(new TextLine(0, y, () -> Ui.tr("drive.cost", def.ironCost()), Ui.ACCENT));
        y += 14;
        y = section(y, Ui.tr("drive.controls"));
        return text(y, Ui.tr("flight.controls_text"), HINT);
    }

    private int buildPropeller(int y, VehiclePart p) {
        y = section(y, Ui.tr("propeller.section"));
        y = smallLabel(y, Ui.tr("propeller.axis"));
        y = chips(y, PropAxis.values(), 2, () -> p.propAxis, v -> VehicleParts.setPropAxis(p, v),
                v -> Ui.tr("prop_axis." + v.key()), v -> Ui.tr("prop_axis." + v.key() + ".tip")) + 4;
        y = numField(y, "propeller.blades", () -> (float) p.blades, v -> p.blades = Math.round(v), 2f, 8f, 1f, true);
        y = numField(y, "propeller.diameter", () -> p.propRadius() * 2f, v -> setPropDiameter(p, v), 2f, 128f, 1f,
                false);
        y = section(y, Ui.tr("propeller.blade_color"));
        y = colorRows(y, () -> p.color, c -> p.color = c);
        y = section(y, Ui.tr("propeller.hub_color"));
        y = colorRows(y, () -> p.rimColor, c -> p.rimColor = c);
        return text(y, Ui.tr("propeller.hint"), HINT) + 8;
    }

    /** Cambia el diametro de una helice sin mover su centro. */
    private void setPropDiameter(VehiclePart p, float diameter) {
        float cx = p.x + p.w / 2f;
        float cy = p.y + p.h / 2f;
        float cz = p.z + p.d / 2f;
        p.w = diameter;
        if (p.propAxis == PropAxis.TOP) {
            p.d = diameter;
        } else {
            p.h = diameter;
        }
        p.x = cx - p.w / 2f;
        p.y = cy - p.h / 2f;
        p.z = cz - p.d / 2f;
        p.centerPivot();
    }

    private int buildThruster(int y, VehiclePart p) {
        y = section(y, Ui.tr("thruster.section"));
        y = smallLabel(y, Ui.tr("thruster.style"));
        y = chips(y, ThrusterStyle.values(), 3, () -> p.thrusterStyle, v -> p.thrusterStyle = v,
                v -> Ui.tr("thruster_style." + v.key()), v -> Ui.tr("thruster_style." + v.key() + ".tip")) + 4;
        y = numField(y, "thruster.flame_size", () -> p.flameSize, v -> p.flameSize = v, 0.3f, 3f, 0.1f, false);
        y = section(y, Ui.tr("thruster.flame_color"));
        y = colorRows(y, () -> p.flameColor, c -> p.flameColor = c);
        y = section(y, Ui.tr("thruster.body_color"));
        y = colorRows(y, () -> p.color, c -> p.color = c);
        return text(y, Ui.tr("thruster.hint"), HINT) + 8;
    }

    private int buildPaintTab(int y) {
        y = intro(y);
        VehiclePart p = sel();
        if (p == null) {
            return noParts(y);
        }
        y = pieceSelector(y);
        y = smallLabel(y, Ui.tr("paint.mode"));
        y = chips(y, NO_YES, 2, () -> voxelMode, v -> {
            voxelMode = v;
            endMouseAction();
            requestRebuild();
        }, v -> Ui.tr(v ? "paint.mode.voxels" : "paint.mode.pixels"),
                v -> Ui.tr(v ? "paint.mode.voxels_tip" : "paint.mode.pixels_tip")) + 6;
        if (voxelMode) {
            y = buildVoxelSection(y, p);
        } else {
            y = buildPixelSection(y, p);
        }
        return buildPartLook(y, p);
    }

    private int buildPixelSection(int y, VehiclePart p) {
        y = section(y, Ui.tr("skin.section"));
        int halfS = (contentW - GAP) / 2;
        button(0, y, halfS, Ui.tr("skin.import"), b -> openSkinPicker(), Ui.tr("skin.import_tip"));
        button(halfS + GAP, y, contentW - halfS - GAP, Ui.tr("skin.export"), b -> exportSkin(), Ui.tr("skin.export_tip"));
        y += ROW;
        button(0, y, contentW, Ui.tr("library.folder"), b -> ClientFiles.openFolder(), Ui.tr("skin.folder_tip"));
        y += ROW;
        int mapped = SkinLayout.mapped(def);
        Component skinInfo = mapped > 0
                ? Ui.tr("skin.status", def.texW, def.texH, mapped, mapped + SkinLayout.unmapped(def))
                : Ui.tr("skin.none");
        y = text(y, skinInfo, HINT) + 8;

        y = section(y, Ui.tr("paint.brush_section"));
        y = mouseHelper(y, PAINT_TOOLS, "paint.mouse_off");
        y = smallLabel(y, Ui.tr("paint.size"));
        y = chips(y, BRUSH_SIZES, BRUSH_SIZES.length, () -> brushSize, v -> {
            brushSize = v;
            buildViewBar();
        }, v -> Component.literal(String.valueOf(v)), null) + 6;
        y = smallLabel(y, Ui.tr("paint.brush_color"));
        IntSupplier brush = () -> brushColor & 0xFFFFFF;
        IntConsumer setBrush = c -> {
            brushColor = 0xFF000000 | (c & 0xFFFFFF);
            refreshFields();
        };
        add(new ColorPicker(0, y, contentW, 46, brush, setBrush));
        y += 46 + 5;
        add(new Swatch(0, y, FIELD_H, brush));
        hexBox(FIELD_H + 4, y, 58, brush, setBrush);
        y += ROW;
        Palette brushPalette = add(new Palette(0, y, contentW, brush, setBrush));
        y += brushPalette.h + 6;
        int halfW = (contentW - GAP) / 2;
        button(0, y, halfW, Ui.tr("paint.clear_part"), b -> {
            p.clearPaint();
            markChanged();
        }, Ui.tr("paint.clear_part_tip"));
        button(halfW + GAP, y, contentW - halfW - GAP, Ui.tr("paint.clear_all"), b -> clearAllPaint(),
                Ui.tr("paint.clear_all_tip"));
        y += ROW + 4;
        return text(y, Ui.tr("paint.hint"), HINT) + 10;
    }

    /** Color de la pieza y su textura (en los dos modos de la pestana Pintar). */
    private int buildPartLook(int y, VehiclePart p) {
        IntConsumer setColor = c -> {
            p.color = c & 0xFFFFFF;
            markChanged();
        };
        y = section(y, Ui.tr("paint.color"));
        int pickerH = 58;
        add(new ColorPicker(0, y, contentW, pickerH, () -> p.color, setColor));
        y += pickerH + 5;
        add(new Swatch(0, y, FIELD_H, () -> p.color));
        hexBox(FIELD_H + 4, y, 58, () -> p.color, setColor);
        y += ROW;
        Palette palette = add(new Palette(0, y, contentW, () -> p.color, setColor));
        y += palette.h + 8;

        y = section(y, () -> Ui.tr("paint.texture", Materials.displayName(p.material)));
        TextureGrid grid = add(new TextureGrid(0, y, contentW, p));
        y += grid.h + 6;
        add(new Toggle(0, y, contentW, Ui.tr("editor.field.stretch"), () -> p.stretch, v -> p.stretch = v,
                Ui.tr("paint.stretch_tip"), true));
        y += CHIP_H + 2;
        add(new Toggle(0, y, contentW, Ui.tr("editor.field.glow"), () -> p.glow, v -> p.glow = v,
                Ui.tr("paint.glow_tip"), true));
        y += CHIP_H + 6;
        y = smallLabel(y, Ui.tr("paint.custom"));
        EditBox material = textBox(0, y, contentW, 128, p.material, s -> {
            p.material = s;
            markChanged();
        }, null);
        refreshers.add(() -> {
            if (!material.isFocused() && !material.getValue().equals(p.material)) {
                material.setValue(p.material);
                material.moveCursorToStart(false);
            }
            material.setTextColor(Materials.exists(p.material) ? Ui.TEXT : Ui.ERROR);
        });
        material.setTextColor(Materials.exists(p.material) ? Ui.TEXT : Ui.ERROR);
        y += ROW + 4;
        return text(y, Ui.tr("editor.hint.look"), HINT);
    }

    // ---------------------------------------------------------------------------------------
    // Minibloques
    // ---------------------------------------------------------------------------------------

    private int buildVoxelSection(int y, VehiclePart p) {
        y = section(y, Ui.tr("voxel.section"));
        if (!mouseMode) {
            y = text(y, Ui.tr("voxel.mouse_off"), Ui.ACCENT) + 4;
            button(0, y, Math.min(contentW, 150), Ui.tr("tool.enable"), b -> setTool(Tool.BRUSH), null);
            y += ROW + 6;
        } else {
            y = chips(y, PAINT_TOOLS, PAINT_TOOLS.length, () -> tool, this::setTool, Tool::title,
                    t -> Ui.tr("voxel.tool." + t.key + ".help")) + 4;
            y = text(y, Ui.tr("voxel.tool." + tool.key + ".help"), HINT) + 6;
        }
        y = smallLabel(y, Ui.tr("voxel.brush_does"));
        y = chips(y, NO_YES, 2, () -> voxelRecolor, v -> voxelRecolor = v,
                v -> Ui.tr(v ? "voxel.recolor" : "voxel.place"), v -> Ui.tr(v ? "voxel.recolor_tip" : "voxel.place_tip")) + 4;
        add(new Toggle(0, y, contentW, Ui.tr("voxel.mirror"), () -> voxelMirror, v -> voxelMirror = v,
                Ui.tr("voxel.mirror_tip"), false));
        y += CHIP_H + 6;
        y = smallLabel(y, Ui.tr("voxel.color"));
        IntSupplier brush = () -> brushColor & 0xFFFFFF;
        IntConsumer setBrush = c -> {
            brushColor = 0xFF000000 | (c & 0xFFFFFF);
            refreshFields();
        };
        add(new ColorPicker(0, y, contentW, 46, brush, setBrush));
        y += 46 + 5;
        add(new Swatch(0, y, FIELD_H, brush));
        hexBox(FIELD_H + 4, y, 58, brush, setBrush);
        y += ROW;
        Palette palette = add(new Palette(0, y, contentW, brush, setBrush));
        y += palette.h + 8;

        int half = (contentW - GAP) / 2;
        y += 4;
        button(0, y, half, Ui.tr("voxel.clear_part"), b -> {
            p.voxels = null;
            markChanged();
        }, Ui.tr("voxel.clear_part_tip"));
        button(half + GAP, y, contentW - half - GAP, Ui.tr("voxel.clear_all"), b -> {
            for (VehiclePart q : def.parts) {
                q.voxels = null;
            }
            markChanged();
        }, Ui.tr("voxel.clear_all_tip"));
        y += ROW + 2;
        add(new TextLine(0, y, () -> Ui.tr("voxel.count", Voxels.count(p.voxels), Voxels.total(def), Voxels.MAX_TOTAL),
                Ui.MUTED));
        y += 13;
        return text(y, Ui.tr("voxel.hint"), HINT) + 10;
    }

    /** Hueco de minibloque: pieza a la que se pega y posicion empaquetada. */
    private record VoxelTarget(int part, int pos) {
    }

    /** Lo que hay bajo el raton para la herramienta: el hueco donde poner o el minibloque que borrar/pintar. */
    @Nullable
    private VoxelTarget voxelTarget(double mx, double my, Matrix4f[] mats) {
        boolean existing = tool == Tool.ERASER || voxelRecolor;
        Preview3D.Hit hit = preview.pickHit(def, mats, mx, my, existing ? null : strokeVoxels, existing);
        if (hit == null || hit.part() >= def.parts.size()) {
            return null;
        }
        if (existing) {
            return hit.isVoxel() ? new VoxelTarget(hit.part(), hit.voxel()) : null;
        }
        if (hit.face() < 0) {
            return null;
        }
        int[] n = Voxels.NORMALS[hit.face()];
        VehiclePart p = def.parts.get(hit.part());
        int x;
        int y;
        int z;
        if (hit.isVoxel()) {
            x = Voxels.x(hit.voxel()) + n[0];
            y = Voxels.y(hit.voxel()) + n[1];
            z = Voxels.z(hit.voxel()) + n[2];
        } else {
            x = cell(hit.x() - p.x, p.w, n[0]);
            y = cell(hit.y() - p.y, p.h, n[1]);
            z = cell(hit.z() - p.z, p.d, n[2]);
        }
        return Voxels.inRange(x, y, z) ? new VoxelTarget(hit.part(), Voxels.pack(x, y, z)) : null;
    }

    /** Coordenada del hueco pegado a la cara de la caja: fuera de la cara en su eje, la celda del punto en los otros. */
    private static int cell(float r, float size, int normal) {
        int cells = Math.max(1, (int) Math.ceil(size - 1.0e-3f));
        if (normal > 0) {
            return cells;
        }
        if (normal < 0) {
            return -1;
        }
        return Mth.clamp((int) Math.floor(r), 0, cells - 1);
    }

    /** El mismo hueco al otro lado (en la misma pieza si esta centrada, o en su pareja). null si no tiene. */
    @Nullable
    private VoxelTarget mirrorTarget(VoxelTarget t) {
        VehiclePart p = def.parts.get(t.part());
        int qi;
        if (Math.abs(p.x + p.w / 2f) < 0.05f) {
            qi = t.part();
        } else {
            qi = VehicleParts.partner(def, t.part());
            if (qi < 0) {
                return null;
            }
        }
        VehiclePart q = def.parts.get(qi);
        float fx = -(p.x + Voxels.x(t.pos()) + 1f) - q.x;
        int x = Math.round(fx);
        if (Math.abs(fx - x) > 0.05f) {
            return null; // no cae en la rejilla del otro lado
        }
        int y = Math.round(p.y + Voxels.y(t.pos()) - q.y);
        int z = Math.round(p.z + Voxels.z(t.pos()) - q.z);
        return Voxels.inRange(x, y, z) ? new VoxelTarget(qi, Voxels.pack(x, y, z)) : null;
    }

    private void voxelAt(double mx, double my, Matrix4f[] mats) {
        VoxelTarget t = voxelTarget(mx, my, mats);
        if (t == null) {
            return;
        }
        boolean changed = applyVoxel(t);
        VoxelTarget m = voxelMirror ? mirrorTarget(t) : null;
        if (m != null && !m.equals(t)) {
            changed |= applyVoxel(m);
        }
        if (changed) {
            strokeChanged = true;
            dirty = true;
        }
    }

    /** Pone, borra o pinta un minibloque segun la herramienta. true si algo cambio. */
    private boolean applyVoxel(VoxelTarget t) {
        VehiclePart p = def.parts.get(t.part());
        int[] before = p.voxels;
        if (tool == Tool.ERASER) {
            p.voxels = Voxels.without(before, t.pos());
            return p.voxels != before;
        }
        int rgb = brushColor & 0xFFFFFF;
        boolean exists = Voxels.indexOf(before, t.pos()) >= 0;
        if (voxelRecolor) {
            if (!exists) {
                return false;
            }
            p.voxels = Voxels.with(before, t.pos(), rgb);
            return p.voxels != before;
        }
        strokeVoxels.add(Preview3D.voxelKey(t.part(), t.pos()));
        if (exists) {
            return false;
        }
        if (Voxels.total(def) >= Voxels.MAX_TOTAL) {
            warn(Ui.tr("voxel.too_many", Voxels.MAX_TOTAL));
            return false;
        }
        p.voxels = Voxels.with(before, t.pos(), rgb);
        return true;
    }

    /** Cuentagotas de los minibloques: el color del minibloque (o de la pieza) bajo el raton. */
    private boolean pickVoxelColor(double mx, double my, Matrix4f[] mats) {
        Preview3D.Hit hit = preview.pickHit(def, mats, mx, my, null, false);
        if (hit == null) {
            return false;
        }
        if (!hit.isVoxel()) {
            return pickColor(mx, my, mats);
        }
        VehiclePart p = def.parts.get(hit.part());
        int i = Voxels.indexOf(p.voxels, hit.voxel());
        if (i < 0) {
            return false;
        }
        brushColor = 0xFF000000 | Voxels.colorAt(p.voxels, i);
        showNotice(Ui.tr("tool.picked", VehicleJson.hex(brushColor)), true);
        voxelRecolor = false;
        setTool(Tool.BRUSH);
        return true;
    }

    /** Contorno del hueco bajo el raton en la vista 3D. */
    private void updateVoxelCursor(int mx, int my) {
        preview.cursorPart = -1;
        if (!voxelMode || tab != Tab.PAINT || !mouseMode || !tool.paints() || tool == Tool.PICKER
                || dragging || !preview.contains(mx, my)) {
            return;
        }
        VoxelTarget t = voxelTarget(mx, my, restMats());
        if (t == null) {
            return;
        }
        preview.cursorPart = t.part();
        preview.cursorPos = t.pos();
        preview.cursorColor = tool == Tool.ERASER ? 0xFFFF5050 : voxelRecolor ? brushColor | 0xFF000000 : 0xFFFFFFFF;
    }

    // ---------------------------------------------------------------------------------------
    // Mis animaciones (poses)
    // ---------------------------------------------------------------------------------------

    private static final String[] AXES = {"X", "Y", "Z"};
    private static final int[] AXIS_TEXT = {0xFFFF9A9A, 0xFF9AFF9A, 0xFF9AC4FF};

    /**
     * Barra para arrastrar un valor y su numero al lado (se puede escribir). fieldLimit: hasta donde se puede escribir
     * (0 = lo mismo que la barra; si no, de -fieldLimit a fieldLimit).
     */
    private int sliderRow(int y, int lw, Component label, int color, Supplier<Float> getter, Consumer<Float> setter,
                          float min, float max, float fieldLimit, float step, float bigStep, @Nullable Component tip) {
        int fieldW = 44;
        add(new ValueSlider(0, y + 1, contentW - fieldW - GAP, lw, label, color, getter, setter, min, max, step,
                bigStep, tip));
        float lo = fieldLimit > 0f ? -fieldLimit : min;
        float hi = fieldLimit > 0f ? fieldLimit : max;
        number(contentW - fieldW, y, fieldW, getter, setter, lo, hi, step, false);
        return y + ROW;
    }

    // ---------------------------------------------------------------------------------------
    // Inventario
    // ---------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------
    // Ayudantes de las pestanas Atributos e Inventario
    // ---------------------------------------------------------------------------------------

    /** Numero con su nombre a la izquierda; si el nombre no cabe, va encima. */
    private int numField(int y, String key, Supplier<Float> getter, Consumer<Float> setter, float min, float max,
                         float step, boolean integer) {
        Component label = Ui.tr(key);
        int fieldW = Mth.clamp(contentW / 3, 46, 70);
        if (font.width(label) + 6 + fieldW <= contentW) {
            label(0, y, label);
            number(contentW - fieldW, y, fieldW, getter, setter, min, max, step, integer);
            return y + ROW;
        }
        y = smallLabel(y, label);
        number(0, y, contentW, getter, setter, min, max, step, integer);
        return y + ROW;
    }

    private int toggleRow(int y, String key, BooleanSupplier getter, Consumer<Boolean> setter, @Nullable String tipKey) {
        add(new Toggle(0, y, contentW, Ui.tr(key), getter, setter, tipKey == null ? null : Ui.tr(tipKey), true));
        return y + CHIP_H + 2;
    }

    /** Lista desplegable para elegir un valor de una lista (efecto, estela...). */
    private <E> int enumRow(int y, String key, E[] values, Supplier<E> getter, Consumer<E> setter,
                            Function<E, Component> name) {
        Component label = Ui.tr(key);
        int lw = font.width(label) + 6;
        if (lw + 80 > contentW) {
            y = smallLabel(y, label);
            lw = 0;
        } else {
            label(0, y, label);
        }
        add(new DropButton(lw, y, contentW - lw, () -> name.apply(getter.get()), () -> -1, null,
                anchor -> openValueDropdown(anchor, values, getter, setter, name)));
        return y + ROW;
    }

    private <E> void openValueDropdown(Element anchor, E[] values, Supplier<E> getter, Consumer<E> setter,
                                       Function<E, Component> name) {
        List<DropItem> items = new ArrayList<>();
        E current = getter.get();
        for (E v : values) {
            items.add(new DropItem(name.apply(v), -1, 0, Objects.equals(v, current)));
        }
        openDropdown(anchor, items, i -> {
            setter.accept(values[i]);
            markChanged();
        });
    }

    private void playSoundId(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            BuiltInRegistries.SOUND_EVENT.getOptional(rl)
                    .ifPresent(event -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f)));
        }
    }

    @Nullable
    private static List<ResourceLocation> itemIds;

    // ---------------------------------------------------------------------------------------
    // Ayudantes para construir el panel
    // ---------------------------------------------------------------------------------------

    private <T extends Element> T add(T element) {
        element.x += buildIndent;
        elements.add(element);
        return element;
    }

    /** Sangria (en pixeles) de lo que se va anadiendo al panel, para las opciones de cada habilidad. */
    private int buildIndent;

    private void indent(int px) {
        buildIndent += px;
        contentW -= px;
    }

    private <T extends AbstractWidget> T panelWidget(T widget, int contentY) {
        panelWidgets.add(new PanelWidget(widget, contentY));
        addWidget(widget);
        return widget;
    }

    /** Ancho para las etiquetas de una pestana: el de la mas larga (maximo medio panel). */
    private int labelWidth(String... keys) {
        int w = 0;
        for (String key : keys) {
            w = Math.max(w, font.width(Ui.tr(key)));
        }
        return Math.min(contentW / 2, w + 6);
    }

    private int intro(int y) {
        return text(y, tab.description(), Ui.MUTED) + 8;
    }

    private int text(int y, Component text, int color) {
        TextBlock block = add(new TextBlock(0, y, contentW, text, color));
        return y + block.h;
    }

    private void label(int x, int y, Component text) {
        add(new TextLine(x, y + 4, () -> text, Ui.MUTED));
    }

    private int smallLabel(int y, Component text) {
        add(new TextLine(0, y, () -> text, Ui.MUTED));
        return y + 11;
    }

    private int section(int y, Component text) {
        return section(y, () -> text);
    }

    private int section(int y, Supplier<Component> text) {
        add(new Section(y, text));
        return y + 14;
    }

    private int noParts(int y) {
        y = text(y, Ui.tr("editor.no_parts_here"), Ui.TEXT) + 6;
        int half = (contentW - GAP) / 2;
        button(0, y, half, Ui.tr("editor.go_parts"), b -> switchTab(Tab.PARTS), null);
        button(half + GAP, y, contentW - half - GAP, Ui.tr("editor.go_templates"), b -> switchTab(Tab.TEMPLATE), null);
        return y + ROW;
    }

    /** Fila para usar el raton: un boton para activarlo, o las herramientas de esta pestana. */
    private int mouseHelper(int y, Tool[] tools, String offKey) {
        if (!mouseMode) {
            y = text(y, Ui.tr(offKey), Ui.ACCENT) + 4;
            button(0, y, Math.min(contentW, 150), Ui.tr("tool.enable"), b -> setTool(tools[0]), null);
            return y + ROW + 6;
        }
        y = chips(y, tools, tools.length, () -> tool, this::setTool, Tool::title, Tool::help) + 4;
        return text(y, tool.help(), HINT) + 8;
    }

    /** Cambia el tamano de una pieza desde los campos (la pintura se adapta). */
    private void resize(VehiclePart p, float w, float h, float d) {
        float ow = p.w;
        float oh = p.h;
        float od = p.d;
        p.w = w;
        p.h = h;
        p.d = d;
        p.onResized(ow, oh, od);
    }

    private void clearAllPaint() {
        for (VehiclePart q : def.parts) {
            q.clearPaint();
        }
        markChanged();
    }

    private int paintTexels() {
        int total = 0;
        for (VehiclePart q : def.parts) {
            if (q.validPaint()) {
                total += q.paint.length;
            }
        }
        return total;
    }

    private int pieceSelector(int y) {
        int lw = labelWidth("editor.piece");
        label(0, y, Ui.tr("editor.piece"));
        add(new PieceSelector(lw, y, contentW - lw));
        return y + ROW + 6;
    }

    private int axisHeader(int y, int lw) {
        int fw = (contentW - lw - GAP * 2) / 3;
        String[] axes = {"X", "Y", "Z"};
        int[] colors = {0xFFFF9A9A, 0xFF9AFF9A, 0xFF9AC4FF};
        for (int i = 0; i < 3; i++) {
            Component axis = Component.literal(axes[i]);
            int ax = lw + i * (fw + GAP) + (fw - font.width(axis)) / 2;
            add(new TextLine(ax, y, () -> axis, colors[i]));
        }
        return y + 11;
    }

    private Button button(int x, int y, int w, Component text, Button.OnPress onPress, @Nullable Component tooltip) {
        Button.Builder builder = Button.builder(text, onPress).bounds(cx0() + x, sy(y), w, FIELD_H);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(tooltip));
        }
        return panelWidget(builder.build(), y);
    }

    private EditBox textBox(int x, int y, int w, int maxLength, String value, Consumer<String> responder,
                            @Nullable MutableComponent hint) {
        EditBox box = new EditBox(font, cx0() + x, sy(y), w, FIELD_H, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.moveCursorToStart(false);
        if (hint != null) {
            box.setHint(hint.withStyle(ChatFormatting.DARK_GRAY));
        }
        box.setResponder(s -> {
            if (!refreshing) {
                responder.accept(s);
            }
        });
        return panelWidget(box, y);
    }

    private NumberField number(int x, int y, int w, Supplier<Float> getter, Consumer<Float> setter,
                               float min, float max, float step, boolean integer) {
        NumberField field = new NumberField(font, cx0() + x, sy(y), w, FIELD_H, getter, setter, min, max, step,
                integer, this::markChanged);
        numberFields.add(field);
        return panelWidget(field, y);
    }

    private int numRow(int y, int lw, String key, Supplier<Float> getter, Consumer<Float> setter,
                       float min, float max, float step, boolean integer) {
        label(0, y, Ui.tr(key));
        number(lw, y, contentW - lw, getter, setter, min, max, step, integer);
        return y + ROW;
    }

    private int vecRow(int y, int lw, String key,
                       Supplier<Float> getX, Consumer<Float> setX,
                       Supplier<Float> getY, Consumer<Float> setY,
                       Supplier<Float> getZ, Consumer<Float> setZ,
                       float min, float max, float step) {
        int fw = (contentW - lw - GAP * 2) / 3;
        label(0, y, Ui.tr(key));
        number(lw, y, fw, getX, setX, min, max, step, false).setTextColor(0xFFFF9A9A);
        number(lw + fw + GAP, y, fw, getY, setY, min, max, step, false).setTextColor(0xFF9AFF9A);
        number(lw + (fw + GAP) * 2, y, contentW - lw - (fw + GAP) * 2, getZ, setZ, min, max, step, false)
                .setTextColor(0xFF9AC4FF);
        return y + ROW;
    }

    private <E> int chips(int y, E[] values, int columns, Supplier<E> getter, Consumer<E> setter,
                          Function<E, Component> name, @Nullable Function<E, Component> tip) {
        Chips<E> group = add(new Chips<>(0, y, contentW, values, columns, getter, setter, name, tip));
        return y + group.h;
    }

    private EditBox hexBox(int x, int y, int w, IntSupplier getter, IntConsumer setter) {
        EditBox box = textBox(x, y, w, 7, VehicleJson.hex(getter.getAsInt()), s -> {
            int c = VehicleJson.parseHex(s);
            if (c >= 0 && c != (getter.getAsInt() & 0xFFFFFF)) {
                setter.accept(c);
            }
        }, null);
        refreshers.add(() -> {
            if (!box.isFocused()) {
                String hex = VehicleJson.hex(getter.getAsInt());
                if (!hex.equalsIgnoreCase(box.getValue())) {
                    box.setValue(hex);
                }
            }
        });
        return box;
    }

    private static int itemColor(String id) {
        if (id == null || id.isBlank()) {
            return Ui.TEXT;
        }
        return resolveItem(id).isEmpty() ? Ui.ERROR : Ui.OK;
    }

    private static ItemStack resolveItem(String id) {
        String clean = VehicleDefinition.cleanResource(id);
        if (clean.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(clean);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    private Component parentLabel(VehiclePart p) {
        if (p.parent < 0 || p.parent >= def.parts.size()) {
            return Ui.tr("editor.field.parent_none");
        }
        return Component.literal(def.parts.get(p.parent).name);
    }

    private int parentColor(VehiclePart p) {
        return p.parent < 0 || p.parent >= def.parts.size() ? -1 : def.parts.get(p.parent).color;
    }

    private List<FormattedCharSequence> tip(Component text) {
        return font.split(text, 190);
    }

    // ---------------------------------------------------------------------------------------
    // Desplazamiento del panel
    // ---------------------------------------------------------------------------------------

    private int cx0() {
        return panelX + PAD + buildIndent;
    }

    private int sy(int contentY) {
        return viewTop + contentY - scroll();
    }

    private int scroll() {
        return tabScroll[tab.ordinal()];
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (viewBottom - viewTop));
    }

    private void setScroll(int value) {
        tabScroll[tab.ordinal()] = Mth.clamp(value, 0, maxScroll());
        for (PanelWidget pw : panelWidgets) {
            AbstractWidget w = pw.widget();
            int y = sy(pw.contentY());
            w.setY(y);
            w.visible = y + w.getHeight() > viewTop && y < viewBottom;
        }
    }

    private boolean inPanelView(double mx, double my) {
        return !panelCollapsed && mx >= panelX && mx < width && my >= viewTop && my < viewBottom;
    }

    private int trackX() {
        return width - SCROLLBAR - 1;
    }

    private int trackY() {
        return viewTop + 2;
    }

    private int trackH() {
        return Math.max(1, viewBottom - viewTop - 4);
    }

    private int barH() {
        return Mth.clamp(trackH() * (viewBottom - viewTop) / Math.max(1, contentHeight), 12, trackH());
    }

    private int barY() {
        int max = maxScroll();
        return trackY() + (max == 0 ? 0 : (trackH() - barH()) * scroll() / max);
    }

    private boolean scrollbarHit(double mx, double my) {
        return maxScroll() > 0 && mx >= trackX() - 2 && mx < width && my >= trackY() && my < trackY() + trackH();
    }

    private void dragScrollbar(double my) {
        int range = Math.max(1, trackH() - barH());
        int pos = (int) Math.round(my - scrollbarGrab - trackY());
        setScroll(pos * maxScroll() / range);
    }

    // =======================================================================================
    // Operaciones del modelo
    // =======================================================================================

    @Nullable
    private VehiclePart sel() {
        return selected >= 0 && selected < def.parts.size() ? def.parts.get(selected) : null;
    }

    private void select(int index) {
        if (index == selected) {
            return;
        }
        selected = index;
        ensureVisible(index);
        requestRebuild();
    }

    /** Pasa a la pieza anterior o siguiente en el orden de la lista. */
    private void selectStep(int direction) {
        List<int[]> rows = displayRows();
        if (rows.isEmpty()) {
            return;
        }
        int row = 0;
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r)[0] == selected) {
                row = r;
                break;
            }
        }
        select(rows.get(Math.floorMod(row + direction, rows.size()))[0]);
    }

    private void switchTab(Tab newTab) {
        if (newTab == tab) {
            return;
        }
        endMouseAction();
        tab = newTab;
        dropdown = null;
        activeElement = null;
        setFocused(null);
        requestRebuild();
    }

    private void addPart(PartKind kind) {
        if (def.parts.size() >= VehicleDefinition.MAX_PARTS) {
            warn(Ui.tr("editor.too_many_parts", VehicleDefinition.MAX_PARTS));
            return;
        }
        if (kind == PartKind.WHEEL && def.countKind(PartKind.WHEEL) >= VehicleDefinition.MAX_WHEELS) {
            warn(Ui.tr("editor.too_many_wheels", VehicleDefinition.MAX_WHEELS));
            return;
        }
        if (kind == PartKind.SEAT && def.countKind(PartKind.SEAT) >= VehicleDefinition.MAX_SEATS) {
            warn(Ui.tr("editor.too_many_seats", VehicleDefinition.MAX_SEATS));
            return;
        }
        if (kind.propels() && propulsionCount() >= VehicleDefinition.MAX_PROPULSION) {
            warn(Ui.tr("editor.too_many_propulsion", VehicleDefinition.MAX_PROPULSION));
            return;
        }
        if (kind != PartKind.BODY) {
            def.parts.add(VehicleParts.create(def, kind));
            selected = def.parts.size() - 1;
            ensureVisible(selected);
            markChanged();
            requestRebuild();
            return;
        }
        VehiclePart p = VehiclePart.box(Ui.tr("editor.part.default_name", def.parts.size() + 1).getString(),
                -2, 0, -2, 4, 4, 4);
        p.color = PALETTE[(def.parts.size() * 5 + 4) % PALETTE.length];
        p.material = MaterialIds.SMOOTH;
        VehiclePart s = sel();
        if (s != null) {
            // Nuevo cubo encima de la pieza seleccionada y unido a ella.
            p.x = s.x + s.w / 2f - 2f;
            p.z = s.z + s.d / 2f - 2f;
            p.y = s.y + s.h;
            p.centerPivot();
            p.parent = selected;
        }
        def.parts.add(p);
        selected = def.parts.size() - 1;
        ensureVisible(selected);
        markChanged();
        requestRebuild();
    }

    /** Indice de la pieza y todas sus descendientes (la raiz primero). */
    private List<Integer> subtree(int root) {
        List<Integer> out = new ArrayList<>();
        out.add(root);
        for (int i = 0; i < def.parts.size(); i++) {
            if (i != root && PoseSolver.isDescendant(def.parts, i, root)) {
                out.add(i);
            }
        }
        return out;
    }

    private void duplicateSelected(boolean mirror) {
        if (sel() == null) {
            return;
        }
        List<Integer> nodes = subtree(selected);
        if (def.parts.size() + nodes.size() > VehicleDefinition.MAX_PARTS) {
            warn(Ui.tr("editor.too_many_parts", VehicleDefinition.MAX_PARTS));
            return;
        }
        Map<Integer, Integer> remap = new HashMap<>();
        int base = def.parts.size();
        for (int k = 0; k < nodes.size(); k++) {
            remap.put(nodes.get(k), base + k);
        }
        for (int index : nodes) {
            VehiclePart src = def.parts.get(index);
            VehiclePart copy = mirror ? src.mirroredX() : src.copy();
            if (!mirror) {
                copy.translate(2f, 0f, 0f);
            }
            copy.parent = remap.getOrDefault(src.parent, src.parent);
            def.parts.add(copy);
        }
        selected = base;
        ensureVisible(selected);
        markChanged();
        requestRebuild();
    }

    private void deleteSelected() {
        int i = selected;
        if (i < 0 || i >= def.parts.size()) {
            return;
        }
        int parentOfDeleted = def.parts.get(i).parent;
        def.parts.remove(i);
        for (VehiclePart p : def.parts) {
            if (p.parent == i) {
                p.parent = parentOfDeleted;
            }
            if (p.parent > i) {
                p.parent--;
            }
        }
        selected = Math.min(i, def.parts.size() - 1);
        markChanged();
        requestRebuild();
    }

    private void moveSubtree(int root, float dx, float dy, float dz) {
        if (root < 0 || root >= def.parts.size() || (dx == 0f && dy == 0f && dz == 0f)) {
            return;
        }
        for (int index : subtree(root)) {
            def.parts.get(index).translate(dx, dy, dz);
        }
    }

    private void nudgeSelected(float dx, float dy, float dz) {
        moveSubtree(selected, dx, dy, dz);
        markChanged();
    }

    private void centerModel() {
        def.centerOnGround();
        preview.fit(def);
        markChanged();
    }

    private VehicleDefinition templateModel(int index) {
        if (templateModels == null) {
            templateModels = new VehicleDefinition[Templates.ALL.size()];
        }
        if (templateModels[index] == null) {
            templateModels[index] = Templates.ALL.get(index).create();
        }
        return templateModels[index];
    }

    private void applyTemplate(Templates.Template template) {
        VehicleDefinition fresh = template.create();
        fresh.id = def.id;
        fresh.owner = def.owner;
        fresh.revision = def.revision;
        Component templateName = Component.translatable("template.vehiclestudio." + template.key());
        if (nameEdited || !isNew) {
            fresh.name = def.name;
        } else {
            fresh.name = templateName.getString();
        }
        def = fresh;
        selected = def.parts.isEmpty() ? -1 : 0;
        partsScroll = 0;
        preview.fit(def);
        markChanged();
        requestRebuild();
        showNotice(Ui.tr("templates.applied", templateName), true);
    }

    // ---------------------------------------------------------------------------------------
    // Skins (PNG)
    // ---------------------------------------------------------------------------------------

    private void openSkinPicker() {
        minecraft.setScreen(new SkinPickScreen(this, def.texW, def.texH, this::applySkin));
    }

    /** Pone la imagen elegida como skin del vehiculo. */
    private void applySkin(String fileName, ClientFiles.Image image) {
        boolean laidOut = false;
        if (SkinLayout.mapped(def) == 0) {
            // El vehiculo no viene de un modelo de Minecraft: se usa el reparto de su propia plantilla
            // (el mismo que al exportar la skin).
            SkinLayout.autoLayout(def);
            laidOut = true;
        }
        int painted = SkinLayout.apply(def, image.argb(), image.width(), image.height());
        if (painted == 0) {
            warn(Ui.tr("skin.no_parts"));
            requestRebuild();
            return;
        }
        markChanged();
        requestRebuild();
        int left = SkinLayout.unmapped(def);
        if (laidOut) {
            showNotice(Ui.tr("skin.applied_layout", fileName), true);
        } else if (left > 0) {
            showNotice(Ui.tr("skin.applied_some", fileName, left), true);
        } else {
            showNotice(Ui.tr("skin.applied", fileName), true);
        }
    }

    /** Guarda la skin del vehiculo como PNG (repartiendo antes las piezas que no tienen sitio). */
    private void exportSkin() {
        int placed = SkinLayout.autoLayout(def);
        if (def.texW <= 0 || def.texH <= 0 || SkinLayout.mapped(def) == 0) {
            warn(Ui.tr("skin.no_parts"));
            return;
        }
        if (placed > 0) {
            markChanged();
        }
        try {
            String name = def.name.isBlank() ? def.id : def.name;
            Path file = ClientFiles.writeSkin(name, SkinLayout.export(def), def.texW, def.texH);
            showNotice(Ui.tr("skin.exported", file.getFileName().toString()), true);
        } catch (IOException e) {
            warn(Ui.tr("skin.failed_write"));
        }
        requestRebuild();
    }

    /** Filas de la lista de piezas en orden de arbol: {indice, profundidad}. */
    private List<int[]> displayRows() {
        int n = def.parts.size();
        int[] parents = PoseSolver.safeParents(def.parts);
        List<List<Integer>> children = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            children.add(new ArrayList<>());
        }
        List<Integer> roots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (parents[i] >= 0) {
                children.get(parents[i]).add(i);
            } else {
                roots.add(i);
            }
        }
        List<int[]> rows = new ArrayList<>();
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        for (int r = roots.size() - 1; r >= 0; r--) {
            stack.push(new int[]{roots.get(r), 0});
        }
        while (!stack.isEmpty()) {
            int[] node = stack.pop();
            rows.add(node);
            List<Integer> kids = children.get(node[0]);
            for (int k = kids.size() - 1; k >= 0; k--) {
                stack.push(new int[]{kids.get(k), node[1] + 1});
            }
        }
        return rows;
    }

    private void ensureVisible(int index) {
        List<int[]> rows = displayRows();
        int row = -1;
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r)[0] == index) {
                row = r;
                break;
            }
        }
        if (row < 0) {
            return;
        }
        int visible = partsList != null ? partsList.visibleRows() : 8;
        if (row < partsScroll) {
            partsScroll = row;
        } else if (row >= partsScroll + visible) {
            partsScroll = row - visible + 1;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Desplegables
    // ---------------------------------------------------------------------------------------

    private void openDropdown(Element anchor, List<DropItem> items, IntConsumer onPick) {
        if (items.isEmpty()) {
            return;
        }
        dropdown = new Dropdown(anchor.left(), anchor.top(), anchor.w, anchor.h, items, onPick);
        setFocused(null);
    }

    private void openPartsDropdown(Element anchor) {
        List<int[]> rows = displayRows();
        List<DropItem> items = new ArrayList<>();
        for (int[] row : rows) {
            VehiclePart p = def.parts.get(row[0]);
            items.add(new DropItem(Component.literal(p.name), p.color, row[1], row[0] == selected));
        }
        openDropdown(anchor, items, i -> select(rows.get(i)[0]));
    }

    private void openParentDropdown(Element anchor) {
        VehiclePart p = sel();
        if (p == null) {
            return;
        }
        List<Integer> options = new ArrayList<>();
        List<DropItem> items = new ArrayList<>();
        options.add(-1);
        items.add(new DropItem(Ui.tr("editor.field.parent_none"), -1, 0, p.parent < 0));
        for (int[] row : displayRows()) {
            int j = row[0];
            if (j != selected && !PoseSolver.isDescendant(def.parts, j, selected)) {
                VehiclePart q = def.parts.get(j);
                options.add(j);
                items.add(new DropItem(Component.literal(q.name), q.color, row[1], p.parent == j));
            }
        }
        openDropdown(anchor, items, i -> {
            p.parent = options.get(i);
            markChanged();
        });
    }

    // =======================================================================================
    // Deshacer / guardar
    // =======================================================================================

    private void markChanged() {
        String now = VehicleJson.write(def, false);
        if (!now.equals(committed)) {
            dirty = true;
            paintVersion++;
            long t = Util.getMillis();
            if (t - lastChange > 700 || undoStack.isEmpty()) {
                undoStack.push(committed);
                while (undoStack.size() > 100) {
                    undoStack.removeLast();
                }
            }
            redoStack.clear();
            committed = now;
            lastChange = t;
        }
        refreshFields();
    }

    /** Vuelve a leer los valores en los campos de texto que el usuario no esta editando. */
    private void refreshFields() {
        refreshing = true;
        try {
            for (NumberField f : numberFields) {
                f.refresh();
            }
            for (Runnable r : refreshers) {
                r.run();
            }
        } finally {
            refreshing = false;
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            warn(Ui.tr("editor.nothing_to_undo"));
            return;
        }
        redoStack.push(committed);
        committed = undoStack.pop();
        loadState(committed);
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(committed);
        committed = redoStack.pop();
        loadState(committed);
    }

    private void loadState(String json) {
        try {
            def = VehicleJson.read(json);
        } catch (RuntimeException e) {
            return;
        }
        if (selected >= def.parts.size()) {
            selected = def.parts.size() - 1;
        }
        dirty = true;
        lastChange = 0L;
        paintVersion++;
        requestRebuild();
    }

    private void save() {
        VehicleDefinition copy = def.copy();
        copy.sanitize();
        if (copy.id.isEmpty()) {
            def.id = VehicleDefinition.newId();
            copy.id = def.id;
        }
        byte[] data = DefCodec.encode(copy);
        if (data.length > ModNetwork.MAX_TOTAL_UPLOAD) {
            showNotice(Ui.tr("editor.too_big"), false);
            return;
        }
        ModNetwork.sendSave(data);
        dirty = false;
        isNew = false;
        showNotice(Ui.tr("editor.saving"), true);
    }

    /** Aviso en rojo de algo que no se pudo hacer aqui (no cambial vehiculo: no la marca sin guardar). */
    private void warn(Component message) {
        status = message;
        statusColor = Ui.ERROR;
        statusUntil = Util.getMillis() + 3500;
    }

    public void showNotice(Component message, boolean success) {
        status = message;
        statusColor = success ? Ui.OK : Ui.ERROR;
        statusUntil = Util.getMillis() + 3500;
        if (!success) {
            dirty = true; // p. ej. el servidor rechazo el guardado: sigue habiendo cambios sin guardar
        }
    }

    private void returnToParent() {
        if (parent instanceof LibraryScreen library) {
            library.selectVehicle(def.id);
        }
        minecraft.setScreen(parent);
    }

    /** Prueba la bocina. */
    private void playSound() {
        playSoundId(def.horn.sound(), def.horn.pitch());
    }

    private void playSoundId(String id, float pitch) {
        if (id == null || id.isEmpty()) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return;
        }
        BuiltInRegistries.SOUND_EVENT.getOptional(rl)
                .ifPresent(event -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch)));
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    /**
     * Como se ve el vehiculo en la vista 3D: con "Probar" las ruedas ruedan y giran a un lado y al otro; con el raton
     * activado se queda quieto (para que las flechas y los minibloques coincidan con lo que se ve).
     */
    private VehicleGeometry.Motion previewMotion(boolean editing) {
        boolean lights = testLights;
        if (!testDrive || (editing && mouseMode)) {
            return new VehicleGeometry.Motion(0f, 0f, lights, false);
        }
        float t = (Util.getMillis() % 3_600_000L) / 50f;
        float steer = Mth.sin(t * 0.05f);
        boolean braking = Mth.sin(t * 0.05f + 1.2f) > 0.6f;
        // helices girando y llamas que crecen y menguan (como con el turbo a ratos)
        float thrust = 1f + 0.8f * Math.max(0f, Mth.sin(t * 0.04f)) + 0.08f * Mth.sin(t * 1.7f);
        return new VehicleGeometry.Motion(t * 0.15f, steer, lights, braking, t * 1.6f, thrust);
    }

    // =======================================================================================
    // Dibujado
    // =======================================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Ui.BG);
        g.fill(0, 0, width, HEADER_H, Ui.PANEL_DARK);
        g.hLine(0, width, HEADER_H - 1, Ui.BORDER);
        g.fillGradient(preview.x, preview.y, preview.x + preview.w, preview.y + preview.h,
                Ui.PREVIEW_TOP, Ui.PREVIEW_BOTTOM);
        boolean showTemplate = hoveredTemplate >= 0;
        boolean editing = !showTemplate;
        VehicleDefinition shown = showTemplate ? templateModel(hoveredTemplate) : def;
        Preview3D view = editing ? preview : templatePreview;
        PaintAtlas paint;
        if (editing) {
            paint = editorPaint();
        } else {
            templatePreview.yaw = 0.6f + (Util.getMillis() % 62832L) * 0.0006f;
            paint = previewPaintFor(shown);
        }
        VehicleGeometry.Motion anim = previewMotion(editing);
        if (editing) {
            updateVoxelCursor(mouseX, mouseY);
        } else {
            preview.cursorPart = -1;
        }
        int outline = editing && tab.usesPart() && !(mouseMode && tool.paints()) ? selected : -1;
        view.render(g, shown, anim, outline, true, paint);
        if (editing && mouseMode) {
            renderMouseOverlay(g, mouseX, mouseY);
        }
        g.fill(panelX, HEADER_H, width, height, panelCollapsed ? Ui.PANEL_DARK : Ui.PANEL);
        g.vLine(panelX, HEADER_H - 1, height, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (needsRebuild) {
            needsRebuild = false;
            rebuildWidgets();
        }
        updateTemplateHover(mouseX, mouseY);
        boolean blocked = dropdown != null && dropdown.inside(mouseX, mouseY);
        int mx = blocked ? -10000 : mouseX;
        int my = blocked ? -10000 : mouseY;
        if (undoButton != null) {
            undoButton.active = !undoStack.isEmpty();
        }
        if (redoButton != null) {
            redoButton.active = !redoStack.isEmpty();
        }
        super.render(g, mx, my, partialTick);
        renderViewChips(g, mx, my);

        renderHeader(g);
        renderTabs(g, mx, my);
        if (!panelCollapsed) {
            renderPanel(g, mx, my, partialTick);
        }
        renderPreviewOverlay(g);
        if (resizingPanel) {
            g.fill(resizeX - 1, HEADER_H, resizeX + 1, height, Ui.ACCENT);
        } else if (dividerHit(mx, my) && !dragging && gizmoHandle < 0 && !painting) {
            g.fill(panelX - 1, HEADER_H, panelX + 1, height, 0xFF8A93A6);
        }

        if (dropdown != null) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 400f);
            dropdown.render(g, mouseX, mouseY);
            g.pose().popPose();
        } else if (activeElement == null && !dragging && !draggingScrollbar && !resizingPanel
                && gizmoHandle < 0 && !painting) {
            renderTabTooltip(g, mx, my);
            renderElementTooltip(g, mx, my);
            renderViewChipTooltip(g, mx, my);
            if (helpCut && helpText != null && Ui.inside(mx, my, helpX, helpY - 1, helpW, 10)) {
                g.renderTooltip(font, font.split(helpText, 240), mx, my);
            } else if (dividerHit(mx, my)) {
                g.renderTooltip(font, tip(Ui.tr("editor.panel.resize")), mx, my);
            }
        }
    }

    private void renderViewChips(GuiGraphics g, int mx, int my) {
        for (ViewChip c : viewChips) {
            if (c.action() == null) {
                g.drawString(font, c.label(), c.x() + 5, c.y() + 3, Ui.TEXT, true);
                continue;
            }
            boolean hover = Ui.inside(mx, my, c.x(), c.y(), c.w(), 13);
            chip(g, c.x(), c.y(), c.w(), 13, c.label(), c.chosen().getAsBoolean(), hover);
            if (c.swatch()) {
                g.fill(c.x() + 3, c.y() + 3, c.x() + c.w() - 3, c.y() + 10, 0xFF000000 | brushColor);
            }
        }
    }

    private void renderViewChipTooltip(GuiGraphics g, int mx, int my) {
        for (ViewChip c : viewChips) {
            if (c.tip() != null && Ui.inside(mx, my, c.x(), c.y(), c.w(), 13)) {
                g.renderTooltip(font, tip(c.tip()), mx, my);
                return;
            }
        }
    }

    @Nullable
    private PaintAtlas editorPaint() {
        if (paintBuilt != paintVersion) {
            paintShown = editorPaint.rebuild(def);
            paintBuilt = paintVersion;
        }
        return paintShown ? editorPaint : null;
    }

    @Nullable
    private PaintAtlas previewPaintFor(VehicleDefinition d) {
        if (previewPaintDef != d) {
            previewPaintShown = previewPaint.rebuild(d);
            previewPaintDef = d;
        }
        return previewPaintShown ? previewPaint : null;
    }

    @Override
    public void removed() {
        editorPaint.close();
        previewPaint.close();
        paintBuilt = 0;
        previewPaintDef = null;
        super.removed();
    }

    private void updateTemplateHover(int mouseX, int mouseY) {
        hoveredTemplate = -1;
        boolean over = tab == Tab.TEMPLATE && dropdown == null && inPanelView(mouseX, mouseY);
        if (over && templateGrid != null) {
            hoveredTemplate = templateGrid.indexAt(mouseX, mouseY);
        }
        if (hoveredTemplate >= 0 && hoveredTemplate != fittedTemplate) {
            templatePreview.fit(templateModel(hoveredTemplate));
            fittedTemplate = hoveredTemplate;
        }
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(font, Ui.tr("editor.name_label"), nameLabelX, 6, Ui.MUTED, false);
        if (dirty) {
            Component unsaved = Ui.tr("editor.unsaved");
            if (unsavedX + font.width(unsaved) <= headerRight - 6) {
                g.drawString(font, unsaved, unsavedX, 6, UNSAVED, false);
            }
            if (saveButton != null) {
                int sx = saveButton.getX();
                int sy = saveButton.getY();
                g.fill(sx + 2, sy + 2, sx + 5, sy + 5, UNSAVED);
            }
        }
    }

    private int stripLeft() {
        return panelX + 1 + FOLD_W;
    }

    private int tabCellW() {
        return Math.max(1, (width - stripLeft()) / visibleTabs().length);
    }

    /**
     * Que hay debajo del raton en la fila (o columna, con el panel plegado) de pestanas: el indice de la pestana
     * en {@link #visibleTabs()}, -2 = boton de plegar/desplegar el panel, -1 = nada.
     */
    /** Alto de cada casilla de la columna de pestanas plegada (mas bajas si no caben todas). */
    private int stripCell() {
        int rows = visibleTabs().length + 1;
        return Math.max(16, Math.min(STRIP_CELL, (height - HEADER_H) / rows));
    }

    private int tabAt(double mx, double my) {
        Tab[] visible = visibleTabs();
        if (mx < panelX || mx >= width || my < HEADER_H) {
            return -1;
        }
        if (panelCollapsed) {
            int row = (int) ((my - HEADER_H) / stripCell());
            if (row == 0) {
                return -2;
            }
            return row - 1 < visible.length ? row - 1 : -1;
        }
        if (my >= HEADER_H + TAB_H) {
            return -1;
        }
        if (mx < stripLeft()) {
            return -2;
        }
        return Math.min(visible.length - 1, (int) ((mx - stripLeft()) / tabCellW()));
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        Tab[] visible = visibleTabs();
        int hovered = tabAt(mx, my);
        if (panelCollapsed) {
            int x = panelX + 1;
            int w = width - x;
            boolean hf = hovered == -2;
            int cell = stripCell();
            if (hf) {
                g.fill(x, HEADER_H, width, HEADER_H + cell, 0xFF2A2E38);
            }
            g.drawCenteredString(font, "\u00AB", x + w / 2, HEADER_H + (cell - 8) / 2, hf ? 0xFFFFFFFF : Ui.MUTED);
            for (int i = 0; i < visible.length; i++) {
                int y = HEADER_H + (i + 1) * cell;
                boolean active = visible[i] == tab;
                if (active) {
                    g.fill(x, y, width, y + cell, Ui.PANEL);
                    g.fill(x, y, x + 2, y + cell, Ui.ACCENT);
                } else if (hovered == i) {
                    g.fill(x, y, width, y + cell, 0xFF2A2E38);
                }
                g.renderItem(visible[i].icon(), x + (w - 16) / 2, y + (cell - 16) / 2);
            }
            return;
        }
        int top = HEADER_H;
        g.fill(panelX + 1, top, width, top + TAB_H, 0xFF17191E);
        boolean hf = hovered == -2;
        if (hf) {
            g.fill(panelX + 1, top, stripLeft(), top + TAB_H, 0xFF2A2E38);
        }
        g.drawString(font, "\u00BB", panelX + 4, top + 6, hf ? 0xFFFFFFFF : Ui.MUTED, false);
        int cw = tabCellW();
        for (int i = 0; i < visible.length; i++) {
            Tab t = visible[i];
            int x = stripLeft() + i * cw;
            int w = i == visible.length - 1 ? width - x : cw;
            boolean active = t == tab;
            if (active) {
                g.fill(x, top, x + w, top + TAB_H, Ui.PANEL);
                g.fill(x, top, x + w, top + 2, Ui.ACCENT);
            } else if (hovered == i) {
                g.fill(x, top, x + w, top + TAB_H, 0xFF2A2E38);
            }
            g.renderItem(t.icon(), x + (w - 16) / 2, top + 2);
        }
        g.hLine(panelX + 1, width, top + TAB_H, Ui.BORDER);
        // titulo de la pestana
        int ty = top + TAB_H + 1;
        g.fill(panelX + 1, ty, width, viewTop, Ui.PANEL);
        String title = tab.title().getString();
        int maxTitle = Math.max(10, panelW - PAD * 2);
        Component bold = Component.literal(title).withStyle(ChatFormatting.BOLD);
        while (title.length() > 1 && font.width(bold) > maxTitle) {
            title = title.substring(0, title.length() - 1);
            bold = Component.literal(title).withStyle(ChatFormatting.BOLD);
        }
        g.drawString(font, bold, panelX + PAD, ty + 3, 0xFFFFFFFF, false);
        g.hLine(panelX + PAD, width - PAD, viewTop - 1, 0xFF30343E);
    }

    private void renderPanel(GuiGraphics g, int mx, int my, float partialTick) {
        boolean inView = inPanelView(mx, my) && !draggingScrollbar;
        int emx = inView ? mx : -10000;
        int emy = inView ? my : -10000;
        g.enableScissor(panelX + 1, viewTop, width, viewBottom);
        for (Element e : elements) {
            if (e.onScreen()) {
                e.render(g, emx, emy);
            }
        }
        for (PanelWidget pw : panelWidgets) {
            if (pw.widget().visible) {
                pw.widget().render(g, emx, emy, partialTick);
            }
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int x = trackX();
            g.fill(x, trackY(), x + SCROLLBAR, trackY() + trackH(), 0xFF1A1C21);
            int by = barY();
            int color = draggingScrollbar ? 0xFFA9B3C6 : (scrollbarHit(mx, my) ? 0xFF8A93A6 : 0xFF5E6575);
            g.fill(x, by, x + SCROLLBAR, by + barH(), color);
        }
    }

    private void renderPreviewOverlay(GuiGraphics g) {
        Component help = mouseMode ? tool.help()
                : Ui.tr(tab.usesPart() ? "editor.preview.help" : "editor.preview.help_behavior");
        int avail = Math.max(20, preview.w - 8);
        String full = help.getString();
        String line = full;
        helpCut = font.width(full) > avail;
        if (helpCut) {
            line = font.plainSubstrByWidth(full, Math.max(10, avail - font.width("..."))) + "...";
        }
        int hy = preview.y + preview.h - 12;
        g.drawString(font, line, preview.x + 4, hy, 0xFF8A919E, false);
        helpText = help;
        helpX = preview.x + 4;
        helpY = hy;
        helpW = font.width(line);
        int toastY = hy - 17;
        if (hoveredTemplate >= 0) {
            Component name = Component.translatable("template.vehiclestudio." + Templates.ALL.get(hoveredTemplate).key());
            toast(g, Ui.tr("templates.previewing", name), Ui.ACCENT, toastY);
            toastY -= 18;
        }
        if (status != null && Util.getMillis() < statusUntil) {
            toast(g, status, statusColor, toastY);
        }
    }

    private void toast(GuiGraphics g, Component text, int color, int y) {
        String s = font.plainSubstrByWidth(text.getString(), Math.max(10, preview.w - 20));
        int sw = font.width(s) + 12;
        int sx = preview.x + (preview.w - sw) / 2;
        g.fill(sx, y, sx + sw, y + 14, 0xD0101216);
        g.renderOutline(sx, y, sw, 14, color);
        g.drawString(font, s, sx + 6, y + 3, color, false);
    }

    private void renderTabTooltip(GuiGraphics g, int mx, int my) {
        int i = tabAt(mx, my);
        if (i == -2) {
            g.renderTooltip(font, tip(Ui.tr(panelCollapsed ? "editor.panel.expand" : "editor.panel.collapse")), mx, my);
            return;
        }
        if (i < 0) {
            return;
        }
        Tab t = visibleTabs()[i];
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(t.title().withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        lines.addAll(font.split(t.description(), 190));
        if (i < 10) {
            lines.add(Ui.tr("editor.tab_key", (i + 1) % 10).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        g.renderTooltip(font, lines, mx, my);
    }

    private void renderElementTooltip(GuiGraphics g, int mx, int my) {
        if (!inPanelView(mx, my)) {
            return;
        }
        for (Element e : elements) {
            if (e.onScreen() && e.hit(mx, my)) {
                List<FormattedCharSequence> lines = e.tooltip(mx, my);
                if (lines != null && !lines.isEmpty()) {
                    g.renderTooltip(font, lines, mx, my);
                    return;
                }
            }
        }
    }

    /** Recuadro con borde (flechas del selector de piezas, desplegables). */
    private void box(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, hover ? 0xFF3A404D : 0xFF2B2F38);
        g.renderOutline(x, y, w, h, hover ? 0xFF8A93A6 : Ui.BORDER);
    }

    /** Opcion seleccionable: la elegida se resalta en azul. */
    private void chip(GuiGraphics g, int x, int y, int w, int h, Component text, boolean chosen, boolean hover) {
        int bg = chosen ? 0xFF2D5A8A : hover ? 0xFF3A404D : 0xFF2E323C;
        int border = chosen ? Ui.ACCENT : hover ? 0xFF7A8294 : 0xFF464C59;
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
        String s = font.plainSubstrByWidth(text.getString(), w - 6);
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, chosen ? 0xFFFFFFFF : Ui.TEXT, false);
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 255) / 255f;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 255) / 255f;
    }

    private static float blue(int rgb) {
        return (rgb & 255) / 255f;
    }

    // =======================================================================================
    // Entrada
    // =======================================================================================

    private boolean textFocused() {
        return getFocused() instanceof EditBox box && box.isFocused();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdown != null) {
            if (dropdown.inside(mouseX, mouseY)) {
                if (button == 0) {
                    dropdown.click(mouseX, mouseY);
                }
            } else {
                dropdown = null;
            }
            return true;
        }
        int t = tabAt(mouseX, mouseY);
        if (t != -1) {
            if (button == 0) {
                if (t == -2) {
                    setPanelCollapsed(!panelCollapsed);
                } else {
                    Tab target = visibleTabs()[t];
                    setPanelCollapsed(false);
                    switchTab(target);
                }
                playClick();
            }
            return true;
        }
        if (button == 0 && dividerHit(mouseX, mouseY)) {
            resizingPanel = true;
            resizeX = panelX;
            setFocused(null);
            return true;
        }
        for (ViewChip c : viewChips) {
            if (c.action() != null && Ui.inside(mouseX, mouseY, c.x(), c.y(), c.w(), 13)) {
                if (button == 0) {
                    c.action().run();
                    playClick();
                }
                return true;
            }
        }
        if (button == 0 && scrollbarHit(mouseX, mouseY)) {
            int by = barY();
            if (mouseY >= by && mouseY < by + barH()) {
                scrollbarGrab = mouseY - by;
            } else {
                scrollbarGrab = barH() / 2.0;
                dragScrollbar(mouseY);
            }
            draggingScrollbar = true;
            setFocused(null);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (inPanelView(mouseX, mouseY)) {
            setFocused(null);
            if (button == 0) {
                for (Element e : elements) {
                    if (e.onScreen() && e.hit(mouseX, mouseY) && e.click(mouseX, mouseY, button)) {
                        activeElement = e;
                        return true;
                    }
                }
            }
            return true;
        }
        if (preview.contains(mouseX, mouseY)) {
            setFocused(null);
            if (mouseMode && button == 0 && startMouseAction(mouseX, mouseY)) {
                return true;
            }
            dragging = true;
            dragButton = button;
            pressX = mouseX;
            pressY = mouseY;
            moved = false;
            return true;
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dropdown != null) {
            return true;
        }
        if (resizingPanel) {
            resizeX = Mth.clamp((int) Math.round(mouseX), width - maxPanel(), width - MIN_PANEL);
            return true;
        }
        if (draggingScrollbar) {
            dragScrollbar(mouseY);
            return true;
        }
        if (activeElement != null) {
            activeElement.drag(mouseX, mouseY);
            return true;
        }
        if (gizmoHandle >= 0) {
            updateGizmo(mouseX, mouseY);
            return true;
        }
        if (painting) {
            strokeTo(mouseX, mouseY);
            return true;
        }
        if (dragging) {
            if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) {
                moved = true;
            }
            if (dragButton == 0) {
                preview.rotate(dragX, dragY);
            } else {
                preview.pan(dragX, dragY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizingPanel) {
            resizingPanel = false;
            EditorSettings.panelWidth = width - resizeX;
            EditorSettings.save();
            requestRebuild();
            return true;
        }
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (activeElement != null) {
            activeElement.release();
            activeElement = null;
            return true;
        }
        if ((gizmoHandle >= 0 || painting) && button == 0) {
            endMouseAction();
            return true;
        }
        if (dragging && button == dragButton) {
            dragging = false;
            if (!moved && button == 0 && tab.usesPart()) {
                int hit = preview.pick(def, previewMotion(true), mouseX, mouseY);
                if (hit >= 0) {
                    select(hit);
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dropdown != null) {
            if (dropdown.inside(mouseX, mouseY)) {
                dropdown.scrollBy(scrollY);
            }
            return true;
        }
        if (scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (inPanelView(mouseX, mouseY)) {
            // La rueda solo cambia un numero si ese campo esta activo (se ha hecho clic en el);
            // si no, desplaza el panel.
            for (NumberField f : numberFields) {
                if (f.visible && f.isFocused() && f.isMouseOver(mouseX, mouseY)) {
                    f.nudge(Math.signum(scrollY));
                    return true;
                }
            }
            for (Element e : elements) {
                if (e.onScreen() && e.hit(mouseX, mouseY) && e.scrolled(scrollY)) {
                    return true;
                }
            }
            setScroll(scroll() - (int) Math.round(scrollY * 24));
            return true;
        }
        if (preview.contains(mouseX, mouseY)) {
            // como en AutoCAD: se acerca o aleja hacia donde esta el raton
            preview.zoomAt(scrollY, mouseX, mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dropdown != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                dropdown = null;
            }
            return true;
        }
        if (hasControlDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_Z -> {
                    if (hasShiftDown()) {
                        redo();
                    } else {
                        undo();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> {
                    redo();
                    return true;
                }
                case GLFW.GLFW_KEY_S -> {
                    save();
                    return true;
                }
                case GLFW.GLFW_KEY_D -> {
                    if (!textFocused() && tab.usesPart()) {
                        duplicateSelected(false);
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        if (!textFocused() && !hasControlDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_M -> {
                    setMouseMode(!mouseMode);
                    return true;
                }
                case GLFW.GLFW_KEY_H -> {
                    setPanelCollapsed(!panelCollapsed);
                    return true;
                }
                case GLFW.GLFW_KEY_F -> {
                    preview.fit(def);
                    return true;
                }
                case GLFW.GLFW_KEY_G -> {
                    setTool(Tool.MOVE);
                    return true;
                }
                case GLFW.GLFW_KEY_T -> {
                    setTool(Tool.RESIZE);
                    return true;
                }
                case GLFW.GLFW_KEY_B -> {
                    setTool(Tool.BRUSH);
                    return true;
                }
                case GLFW.GLFW_KEY_E -> {
                    setTool(Tool.ERASER);
                    return true;
                }
                case GLFW.GLFW_KEY_I -> {
                    setTool(Tool.PICKER);
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_KP_SUBTRACT, GLFW.GLFW_KEY_MINUS -> {
                    changeBrush(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_KEY_EQUAL -> {
                    changeBrush(1);
                    return true;
                }
                default -> {
                }
            }
        }
        if (!textFocused()) {
            Tab[] visible = visibleTabs();
            int number = keyCode == GLFW.GLFW_KEY_0 ? 9 : keyCode - GLFW.GLFW_KEY_1;
            if (!hasControlDown() && (keyCode == GLFW.GLFW_KEY_0 || keyCode >= GLFW.GLFW_KEY_1)
                    && number >= 0 && number < Math.min(visible.length, 10) && keyCode <= GLFW.GLFW_KEY_9) {
                setPanelCollapsed(false);
                switchTab(visible[number]);
                return true;
            }
            if (tab.usesPart() && sel() != null) {
                float step = hasShiftDown() ? 0.25f : (hasControlDown() ? 4f : 1f);
                switch (keyCode) {
                    case GLFW.GLFW_KEY_DELETE -> {
                        deleteSelected();
                        return true;
                    }
                    case GLFW.GLFW_KEY_LEFT -> {
                        nudgeSelected(-step, 0f, 0f);
                        return true;
                    }
                    case GLFW.GLFW_KEY_RIGHT -> {
                        nudgeSelected(step, 0f, 0f);
                        return true;
                    }
                    case GLFW.GLFW_KEY_UP -> {
                        nudgeSelected(0f, 0f, -step);
                        return true;
                    }
                    case GLFW.GLFW_KEY_DOWN -> {
                        nudgeSelected(0f, 0f, step);
                        return true;
                    }
                    case GLFW.GLFW_KEY_PAGE_UP -> {
                        nudgeSelected(0f, step, 0f);
                        return true;
                    }
                    case GLFW.GLFW_KEY_PAGE_DOWN -> {
                        nudgeSelected(0f, -step, 0f);
                        return true;
                    }
                    default -> {
                    }
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (dropdown != null) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (dropdown != null) {
            dropdown = null;
            return;
        }
        if (dirty) {
            minecraft.setScreen(new ConfirmScreen(discard -> {
                if (discard) {
                    returnToParent();
                } else {
                    minecraft.setScreen(this);
                }
            }, Ui.tr("editor.discard_title"), Ui.tr("editor.discard_message")));
        } else {
            returnToParent();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =======================================================================================
    // Raton: mover, estirar y pintar sobre la vista 3D
    // =======================================================================================

    private Matrix4f[] restMats() {
        return VehicleGeometry.pose(def, VehicleGeometry.Motion.REST);
    }

    private static float[] boxCenter(VehiclePart p) {
        return new float[]{p.x + p.w / 2f, p.y + p.h / 2f, p.z + p.d / 2f};
    }

    private static float[] faceCenter(VehiclePart p, int face) {
        float[] c = boxCenter(p);
        boolean max = FACE_SIGN[face] > 0;
        switch (FACE_AXIS[face]) {
            case 0 -> c[0] = max ? p.x + p.w : p.x;
            case 1 -> c[1] = max ? p.y + p.h : p.y;
            default -> c[2] = max ? p.z + p.d : p.z;
        }
        return c;
    }

    /** Matriz del padre: las coordenadas de la pieza se mueven en ese espacio. */
    private Matrix4f parentMatrix(Matrix4f[] mats, int index) {
        int parent = PoseSolver.safeParents(def.parts)[index];
        return parent >= 0 && parent < mats.length ? mats[parent] : new Matrix4f();
    }

    /** Cuanto avanza en pantalla (x, y y profundidad) un pixel del modelo en el eje dado. */
    private static float[] screenAxis(Matrix4f view, Matrix4f m, float[] at, int axis) {
        float[] a = Preview3D.project(view, m, at[0], at[1], at[2]);
        float[] b = Preview3D.project(view, m, at[0] + (axis == 0 ? 1f : 0f), at[1] + (axis == 1 ? 1f : 0f),
                at[2] + (axis == 2 ? 1f : 0f));
        return new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]};
    }

    private static float len2d(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1]);
    }

    /** Punta de cada flecha de mover (null si ese eje apunta hacia la camara). */
    private float[][] moveArrows(Matrix4f view, Matrix4f[] mats, VehiclePart p, float[] origin) {
        float[] c = boxCenter(p);
        Matrix4f pm = parentMatrix(mats, selected);
        float[][] tips = new float[3][];
        for (int k = 0; k < 3; k++) {
            float[] v = screenAxis(view, pm, c, k);
            float len = len2d(v);
            if (len > 0.05f) {
                tips[k] = new float[]{origin[0] + v[0] / len * 32f, origin[1] + v[1] / len * 32f};
            }
        }
        return tips;
    }

    /** true si la cara mira hacia la camara (en la vista, mas profundidad = mas cerca). */
    private boolean faceVisible(Matrix4f view, Matrix4f[] mats, VehiclePart p, int face) {
        float[] v = screenAxis(view, mats[selected], faceCenter(p, face), FACE_AXIS[face]);
        return v[2] * FACE_SIGN[face] > 0f;
    }

    /** Asa bajo el raton (ver {@link #gizmoHandle}) o -1. */
    private int handleAt(double mx, double my, Matrix4f view, Matrix4f[] mats) {
        VehiclePart p = sel();
        if (p == null || selected >= mats.length) {
            return -1;
        }
        if (tool == Tool.MOVE) {
            float[] c = boxCenter(p);
            float[] o = Preview3D.project(view, mats[selected], c[0], c[1], c[2]);
            double toCenter = Math.hypot(mx - o[0], my - o[1]);
            float[][] tips = moveArrows(view, mats, p, o);
            for (int k = 0; k < 3; k++) {
                if (tips[k] != null && toCenter > 7 && distToSegment(mx, my, o, tips[k]) < 4.5) {
                    return k;
                }
            }
            return toCenter <= 7 ? 3 : -1;
        }
        if (tool == Tool.RESIZE) {
            int best = -1;
            double bestD = 6.5;
            for (int f = 0; f < PaintLayout.FACES; f++) {
                if (!faceVisible(view, mats, p, f)) {
                    continue;
                }
                float[] fc = faceCenter(p, f);
                float[] h = Preview3D.project(view, mats[selected], fc[0], fc[1], fc[2]);
                double d = Math.hypot(mx - h[0], my - h[1]);
                if (d < bestD) {
                    bestD = d;
                    best = 10 + f;
                }
            }
            return best;
        }
        return -1;
    }

    private static double distToSegment(double px, double py, float[] a, float[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double l2 = dx * dx + dy * dy;
        double t = l2 < 1.0e-6 ? 0 : Mth.clamp(((px - a[0]) * dx + (py - a[1]) * dy) / l2, 0.0, 1.0);
        return Math.hypot(px - (a[0] + t * dx), py - (a[1] + t * dy));
    }

    /** Empieza a usar la herramienta en la vista 3D. false = no hay nada debajo (se gira la camara). */
    private boolean startMouseAction(double mx, double my) {
        Matrix4f view = preview.viewMatrix();
        Matrix4f[] mats = restMats();
        switch (tool) {
            case MOVE, RESIZE -> {
                if (sel() != null) {
                    int handle = handleAt(mx, my, view, mats);
                    if (handle >= 0) {
                        beginGizmo(handle, mx, my, view, mats);
                        return true;
                    }
                }
                Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
                if (hit == null) {
                    return false;
                }
                if (hit.part() != selected) {
                    select(hit.part());
                }
                if (tool == Tool.MOVE) {
                    beginGizmo(3, mx, my, view, mats);
                }
                return true;
            }
            case PICKER -> {
                return voxelMode && tab == Tab.PAINT ? pickVoxelColor(mx, my, mats) : pickColor(mx, my, mats);
            }
            default -> {
                boolean voxels = voxelMode && tab == Tab.PAINT;
                if (preview.pickHit(def, mats, mx, my, null, voxels && (tool == Tool.ERASER || voxelRecolor)) == null) {
                    return false;
                }
                painting = true;
                strokeChanged = false;
                strokeVoxels.clear();
                paintLastX = mx;
                paintLastY = my;
                if (voxels) {
                    voxelAt(mx, my, mats);
                } else {
                    paintAt(mx, my, mats);
                }
                return true;
            }
        }
    }

    private void beginGizmo(int handle, double mx, double my, Matrix4f view, Matrix4f[] mats) {
        VehiclePart p = sel();
        if (p == null || selected >= mats.length) {
            return;
        }
        gizmoHandle = handle;
        gizmoX0 = mx;
        gizmoY0 = my;
        gizmoApplied[0] = 0f;
        gizmoApplied[1] = 0f;
        gizmoApplied[2] = 0f;
        gizmoStart = p.copy();
        float[] c = boxCenter(p);
        if (handle < 3) {
            gizmoA = screenAxis(view, parentMatrix(mats, selected), c, handle);
            gizmoAxisA = handle;
        } else if (handle == 3) {
            Matrix4f pm = parentMatrix(mats, selected);
            float[][] axes = {screenAxis(view, pm, c, 0), screenAxis(view, pm, c, 1), screenAxis(view, pm, c, 2)};
            // Los dos ejes que mas se ven en pantalla forman el plano en el que se arrastra la pieza.
            int skip = 0;
            for (int k = 1; k < 3; k++) {
                if (len2d(axes[k]) < len2d(axes[skip])) {
                    skip = k;
                }
            }
            gizmoAxisA = skip == 0 ? 1 : 0;
            gizmoAxisB = skip == 2 ? 1 : 2;
            gizmoA = axes[gizmoAxisA];
            gizmoB = axes[gizmoAxisB];
        } else {
            int face = handle - 10;
            float[] v = screenAxis(view, mats[selected], faceCenter(p, face), FACE_AXIS[face]);
            int sign = FACE_SIGN[face];
            gizmoA = new float[]{v[0] * sign, v[1] * sign, v[2] * sign};
            gizmoAxisA = FACE_AXIS[face];
        }
    }

    private static float snap(float v, float step) {
        return Math.round(v / step) * step;
    }

    private void updateGizmo(double mx, double my) {
        VehiclePart p = sel();
        VehiclePart s0 = gizmoStart;
        if (p == null || s0 == null) {
            return;
        }
        float dx = (float) (mx - gizmoX0);
        float dy = (float) (my - gizmoY0);
        float step = hasShiftDown() ? 0.25f : 1f;
        if (gizmoHandle <= 3) {
            float[] want = new float[3];
            if (gizmoHandle < 3) {
                float l2 = gizmoA[0] * gizmoA[0] + gizmoA[1] * gizmoA[1];
                if (l2 < 1.0e-4f) {
                    return;
                }
                want[gizmoAxisA] = snap((dx * gizmoA[0] + dy * gizmoA[1]) / l2, step);
            } else {
                float det = gizmoA[0] * gizmoB[1] - gizmoA[1] * gizmoB[0];
                if (Math.abs(det) < 1.0e-4f) {
                    return;
                }
                want[gizmoAxisA] = snap((dx * gizmoB[1] - dy * gizmoB[0]) / det, step);
                want[gizmoAxisB] = snap((gizmoA[0] * dy - gizmoA[1] * dx) / det, step);
            }
            moveSubtree(selected, want[0] - gizmoApplied[0], want[1] - gizmoApplied[1], want[2] - gizmoApplied[2]);
            System.arraycopy(want, 0, gizmoApplied, 0, 3);
        } else {
            float l2 = gizmoA[0] * gizmoA[0] + gizmoA[1] * gizmoA[1];
            if (l2 < 1.0e-4f) {
                return;
            }
            float s = snap((dx * gizmoA[0] + dy * gizmoA[1]) / l2, step);
            int face = gizmoHandle - 10;
            boolean positive = FACE_SIGN[face] > 0;
            float size0 = gizmoAxisA == 0 ? s0.w : gizmoAxisA == 1 ? s0.h : s0.d;
            s = Mth.clamp(s, -size0, 256f - size0);
            p.x = s0.x;
            p.y = s0.y;
            p.z = s0.z;
            p.w = s0.w;
            p.h = s0.h;
            p.d = s0.d;
            p.paint = s0.paint == null ? null : s0.paint.clone();
            p.pw = s0.pw;
            p.ph = s0.ph;
            p.pd = s0.pd;
            switch (gizmoAxisA) {
                case 0 -> {
                    p.w = size0 + s;
                    if (!positive) {
                        p.x = s0.x - s;
                    }
                }
                case 1 -> {
                    p.h = size0 + s;
                    if (!positive) {
                        p.y = s0.y - s;
                    }
                }
                default -> {
                    p.d = size0 + s;
                    if (!positive) {
                        p.z = s0.z - s;
                    }
                }
            }
            p.onResized(s0.w, s0.h, s0.d);
            paintVersion++;
        }
        dirty = true;
        refreshFields();
    }

    /** Termina el arrastre o el trazo actual (un solo paso para deshacer). */
    private void endMouseAction() {
        boolean changed = gizmoHandle >= 0 || (painting && strokeChanged);
        gizmoHandle = -1;
        gizmoStart = null;
        painting = false;
        strokeChanged = false;
        strokeVoxels.clear();
        if (changed) {
            markChanged();
        }
    }

    /** Texel bajo el punto de impacto: {cara, i, j, ancho de la cara, alto de la cara}, o null. */
    @Nullable
    private int[] texelAt(Preview3D.Hit hit) {
        if (hit.face() < 0 || hit.part() >= def.parts.size()) {
            return null;
        }
        VehiclePart p = def.parts.get(hit.part());
        int pw = p.validPaint() ? p.pw : VehiclePart.paintDim(p.w);
        int ph = p.validPaint() ? p.ph : VehiclePart.paintDim(p.h);
        int pd = p.validPaint() ? p.pd : VehiclePart.paintDim(p.d);
        int fw = PaintLayout.faceW(hit.face(), pw, pd);
        int fh = PaintLayout.faceH(hit.face(), ph, pd);
        float[] st = PaintLayout.st(hit.face(), hit.x(), hit.y(), hit.z(),
                p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d);
        int i = Mth.clamp((int) Math.floor(st[0] * fw), 0, fw - 1);
        int j = Mth.clamp((int) Math.floor(st[1] * fh), 0, fh - 1);
        return new int[]{hit.face(), i, j, fw, fh};
    }

    private void paintAt(double mx, double my, Matrix4f[] mats) {
        Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
        if (hit == null) {
            return;
        }
        int[] tx = texelAt(hit);
        if (tx == null) {
            return;
        }
        VehiclePart p = def.parts.get(hit.part());
        boolean erase = tool == Tool.ERASER;
        if (!p.validPaint()) {
            if (erase) {
                return;
            }
            int pd = VehiclePart.paintDim(p.d);
            int need = PaintLayout.imageW(VehiclePart.paintDim(p.w), pd) * PaintLayout.imageH(VehiclePart.paintDim(p.h), pd);
            if (paintTexels() + need > VehicleDefinition.MAX_PAINT_TEXELS) {
                warn(Ui.tr("paint.too_much"));
                return;
            }
            p.ensurePaint();
        }
        int color = erase ? 0 : 0xFF000000 | (brushColor & 0xFFFFFF);
        int i0 = tx[1] - (brushSize - 1) / 2;
        int j0 = tx[2] - (brushSize - 1) / 2;
        boolean changed = false;
        for (int j = j0; j < j0 + brushSize; j++) {
            for (int i = i0; i < i0 + brushSize; i++) {
                if (i >= 0 && j >= 0 && i < tx[3] && j < tx[4] && p.texel(tx[0], i, j) != color) {
                    p.setTexel(tx[0], i, j, color);
                    changed = true;
                }
            }
        }
        if (changed) {
            strokeChanged = true;
            dirty = true;
            paintVersion++;
        }
    }

    /** Pinta a lo largo del movimiento del raton para que el trazo no tenga huecos. */
    private void strokeTo(double mx, double my) {
        Matrix4f[] mats = restMats();
        double dx = mx - paintLastX;
        double dy = my - paintLastY;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / 2.0));
        boolean voxels = voxelMode && tab == Tab.PAINT;
        for (int k = 1; k <= steps; k++) {
            if (voxels) {
                voxelAt(paintLastX + dx * k / steps, paintLastY + dy * k / steps, mats);
            } else {
                paintAt(paintLastX + dx * k / steps, paintLastY + dy * k / steps, mats);
            }
        }
        paintLastX = mx;
        paintLastY = my;
    }

    /** Cuentagotas: copia el color pintado (o el color de la pieza) y vuelve al pincel. */
    private boolean pickColor(double mx, double my, Matrix4f[] mats) {
        Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
        if (hit == null) {
            return false;
        }
        VehiclePart p = def.parts.get(hit.part());
        int[] tx = texelAt(hit);
        int c = tx == null ? 0 : p.texel(tx[0], tx[1], tx[2]);
        if ((c >>> 24) == 0) {
            c = p.color;
        }
        brushColor = 0xFF000000 | (c & 0xFFFFFF);
        showNotice(Ui.tr("tool.picked", VehicleJson.hex(brushColor)), true);
        setTool(Tool.BRUSH);
        return true;
    }

    /** Flechas, asas o el cuadrado del pincel encima de la vista 3D. */
    private void renderMouseOverlay(GuiGraphics g, int mx, int my) {
        Matrix4f view = preview.viewMatrix();
        Matrix4f[] mats = restMats();
        g.enableScissor(preview.x, preview.y, preview.x + preview.w, preview.y + preview.h);
        VehiclePart p = sel();
        if ((tool == Tool.MOVE || tool == Tool.RESIZE) && p != null && selected < mats.length) {
            int hover = gizmoHandle >= 0 ? gizmoHandle : (preview.contains(mx, my) ? handleAt(mx, my, view, mats) : -1);
            if (tool == Tool.MOVE) {
                float[] c = boxCenter(p);
                float[] o = Preview3D.project(view, mats[selected], c[0], c[1], c[2]);
                float[][] tips = moveArrows(view, mats, p, o);
                for (int k = 0; k < 3; k++) {
                    if (tips[k] != null) {
                        int color = hover == k ? 0xFFFFFFFF : AXIS_COLORS[k];
                        line(g, o[0], o[1], tips[k][0], tips[k][1], hover == k ? 3 : 2, color);
                        square(g, tips[k][0], tips[k][1], 3, color);
                    }
                }
                square(g, o[0], o[1], hover == 3 ? 4 : 3, hover == 3 ? 0xFFFFFFFF : 0xFFFFD84A);
            } else {
                for (int f = 0; f < PaintLayout.FACES; f++) {
                    if (hover != 10 + f && !faceVisible(view, mats, p, f)) {
                        continue;
                    }
                    float[] fc = faceCenter(p, f);
                    float[] h = Preview3D.project(view, mats[selected], fc[0], fc[1], fc[2]);
                    float[] v = screenAxis(view, mats[selected], fc, FACE_AXIS[f]);
                    float len = len2d(v);
                    int color = hover == 10 + f ? 0xFFFFFFFF : AXIS_COLORS[FACE_AXIS[f]];
                    if (len > 0.05f) {
                        float ex = h[0] + FACE_SIGN[f] * v[0] / len * 10f;
                        float ey = h[1] + FACE_SIGN[f] * v[1] / len * 10f;
                        line(g, h[0], h[1], ex, ey, 2, color);
                    }
                    square(g, h[0], h[1], hover == 10 + f ? 4 : 3, color);
                }
            }
        } else if ((tool == Tool.BRUSH || tool == Tool.ERASER) && !(voxelMode && tab == Tab.PAINT)
                && preview.contains(mx, my) && !dragging) {
            Preview3D.Hit hit = preview.pickHit(def, mats, mx, my);
            int[] tx = hit == null ? null : texelAt(hit);
            if (hit != null && tx != null) {
                VehiclePart q = def.parts.get(hit.part());
                int i0 = Mth.clamp(tx[1] - (brushSize - 1) / 2, 0, tx[3]);
                int i1 = Mth.clamp(tx[1] - (brushSize - 1) / 2 + brushSize, 0, tx[3]);
                int j0 = Mth.clamp(tx[2] - (brushSize - 1) / 2, 0, tx[4]);
                int j1 = Mth.clamp(tx[2] - (brushSize - 1) / 2 + brushSize, 0, tx[4]);
                float[] fr = PaintLayout.frame(tx[0], q.x, q.y, q.z, q.x + q.w, q.y + q.h, q.z + q.d);
                float[][] st = {
                        {i0 / (float) tx[3], j0 / (float) tx[4]}, {i1 / (float) tx[3], j0 / (float) tx[4]},
                        {i1 / (float) tx[3], j1 / (float) tx[4]}, {i0 / (float) tx[3], j1 / (float) tx[4]}
                };
                float[][] pts = new float[4][];
                for (int k = 0; k < 4; k++) {
                    float s = st[k][0];
                    float t = st[k][1];
                    pts[k] = Preview3D.project(view, mats[hit.part()], fr[0] + fr[3] * s + fr[6] * t,
                            fr[1] + fr[4] * s + fr[7] * t, fr[2] + fr[5] * s + fr[8] * t);
                }
                int color = tool == Tool.ERASER ? 0xFFFF6060 : 0xFFFFFFFF;
                for (int k = 0; k < 4; k++) {
                    line(g, pts[k][0], pts[k][1], pts[(k + 1) % 4][0], pts[(k + 1) % 4][1], 1, color);
                }
            }
        }
        g.disableScissor();
    }

    /** Linea de pantalla hecha con cuadraditos (no hace falta nada mas que fill). */
    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        int a = thickness / 2;
        for (int k = 0; k <= steps; k++) {
            int px = Math.round(x0 + dx * k / steps) - a;
            int py = Math.round(y0 + dy * k / steps) - a;
            g.fill(px, py, px + thickness, py + thickness, color);
        }
    }

    private static void square(GuiGraphics g, float cx, float cy, int half, int color) {
        int x = Math.round(cx);
        int y = Math.round(cy);
        g.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1, 0xFF101216);
        g.fill(x - half, y - half, x + half, y + half, color);
    }

    // =======================================================================================
    // Elementos dibujados a mano dentro del panel (coordenadas del contenido)
    // =======================================================================================

    private abstract class Element {
        protected int x;
        protected int y;
        protected int w;
        protected int h;

        Element(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        int left() {
            return panelX + PAD + x; // la sangria ya esta sumada en x (ver add)
        }

        int top() {
            return sy(y);
        }

        boolean hit(double mx, double my) {
            return Ui.inside(mx, my, left(), top(), w, h);
        }

        boolean onScreen() {
            int t = top();
            return t + h > viewTop && t < viewBottom;
        }

        abstract void render(GuiGraphics g, int mx, int my);

        boolean click(double mx, double my, int button) {
            return false;
        }

        void drag(double mx, double my) {
        }

        void release() {
        }

        boolean scrolled(double amount) {
            return false;
        }

        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return null;
        }
    }

    /** Texto partido en varias lineas. */
    private final class TextBlock extends Element {
        private final List<FormattedCharSequence> lines;
        private final int color;

        TextBlock(int x, int y, int w, Component text, int color) {
            super(x, y, w, 0);
            this.lines = font.split(text, Math.max(10, w));
            this.h = lines.size() * 10;
            this.color = color;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int ty = top();
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, left(), ty, color, false);
                ty += 10;
            }
        }
    }

    /** Una linea de texto (puede cambiar mientras se edita). */
    private final class TextLine extends Element {
        private final Supplier<Component> text;
        private final int color;

        TextLine(int x, int y, Supplier<Component> text, int color) {
            super(x, y, 0, 9);
            this.text = text;
            this.color = color;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Component c = text.get();
            int max = Math.max(10, contentW - x);
            if (font.width(c) <= max) {
                g.drawString(font, c, left(), top(), color, false);
            } else {
                g.drawString(font, font.plainSubstrByWidth(c.getString(), max), left(), top(), color, false);
            }
        }
    }

    /** Titulo de seccion con una linea a la derecha. */
    private final class Section extends Element {
        private final Supplier<Component> text;

        Section(int y, Supplier<Component> text) {
            super(0, y, contentW, 12);
            this.text = text;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Component t = text.get();
            int l = left();
            int ty = top() + 2;
            g.drawString(font, t, l, ty, Ui.TEXT, false);
            int lineX = l + font.width(t) + 5;
            if (lineX < l + w) {
                g.hLine(lineX, l + w - 1, ty + 4, Ui.BORDER);
            }
        }
    }

    /** Cuadrado con el color actual. */
    private final class Swatch extends Element {
        private final IntSupplier color;

        Swatch(int x, int y, int size, IntSupplier color) {
            super(x, y, size, size);
            this.color = color;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Ui.swatch(g, left(), top(), w, color.getAsInt(), false);
        }
    }

    /** [\u25C0] [\u25A0 nombre \u25BE] [\u25B6]: cambia la pieza que se esta editando. */
    private final class PieceSelector extends Element {
        PieceSelector(int x, int y, int w) {
            super(x, y, w, FIELD_H);
        }

        /** 0 = anterior, 1 = elegir de la lista, 2 = siguiente. */
        private int zone(double mx) {
            double rel = mx - left();
            if (rel < 16) {
                return 0;
            }
            return rel >= w - 16 ? 2 : 1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            int z = hit(mx, my) ? zone(mx) : -1;
            box(g, l, t, 14, h, z == 0);
            g.drawCenteredString(font, "\u25C0", l + 7, t + 4, z == 0 ? 0xFFFFFFFF : Ui.MUTED);
            box(g, l + w - 14, t, 14, h, z == 2);
            g.drawCenteredString(font, "\u25B6", l + w - 7, t + 4, z == 2 ? 0xFFFFFFFF : Ui.MUTED);
            int bx = l + 16;
            int bw = w - 32;
            box(g, bx, t, bw, h, z == 1);
            VehiclePart p = sel();
            if (p != null) {
                Ui.swatch(g, bx + 4, t + 4, 8, p.color, false);
                String name = font.plainSubstrByWidth(p.name, Math.max(10, bw - 28));
                g.drawString(font, name, bx + 16, t + 4, Ui.TEXT, false);
            }
            g.drawString(font, "\u25BE", bx + bw - 9, t + 4, Ui.MUTED, false);
        }

        @Override
        boolean click(double mx, double my, int button) {
            switch (zone(mx)) {
                case 0 -> selectStep(-1);
                case 2 -> selectStep(1);
                default -> openPartsDropdown(this);
            }
            playClick();
            return true;
        }

        @Override
        List<FormattedCharSequence> tooltip(double mx, double my) {
            String key = switch (zone(mx)) {
                case 0 -> "editor.piece_prev";
                case 2 -> "editor.piece_next";
                default -> "editor.piece_pick";
            };
            return tip(Ui.tr(key));
        }
    }

    /** Boton que abre una lista desplegable. */
    private final class DropButton extends Element {
        private final Supplier<Component> text;
        private final IntSupplier color;
        @Nullable
        private final Component tip;
        private final Consumer<Element> open;

        DropButton(int x, int y, int w, Supplier<Component> text, IntSupplier color, @Nullable Component tip,
                   Consumer<Element> open) {
            super(x, y, w, FIELD_H);
            this.text = text;
            this.color = color;
            this.tip = tip;
            this.open = open;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            box(g, l, t, w, h, hit(mx, my));
            int tx = l + 4;
            int c = color.getAsInt();
            if (c >= 0) {
                Ui.swatch(g, tx, t + 4, 8, c, false);
                tx += 12;
            }
            String s = font.plainSubstrByWidth(text.get().getString(), Math.max(10, l + w - 12 - tx));
            g.drawString(font, s, tx, t + 4, Ui.TEXT, false);
            g.drawString(font, "\u25BE", l + w - 9, t + 4, Ui.MUTED, false);
        }

        @Override
        boolean click(double mx, double my, int button) {
            open.accept(this);
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return tip == null ? null : tip(tip);
        }
    }

    /** Grupo de opciones: una sola elegida, resaltada. columns = 0 coloca las opciones seguidas. */
    private final class Chips<E> extends Element {
        private final E[] values;
        private final Supplier<E> getter;
        private final Consumer<E> setter;
        private final Function<E, Component> name;
        @Nullable
        private final Function<E, Component> tipFor;
        private final int[] rx;
        private final int[] ry;
        private final int[] rw;

        Chips(int x, int y, int w, E[] values, int columns, Supplier<E> getter, Consumer<E> setter,
              Function<E, Component> name, @Nullable Function<E, Component> tipFor) {
            super(x, y, w, 0);
            this.values = values;
            this.getter = getter;
            this.setter = setter;
            this.name = name;
            this.tipFor = tipFor;
            int n = values.length;
            rx = new int[n];
            ry = new int[n];
            rw = new int[n];
            if (columns > 0) {
                int cw = (w - GAP * (columns - 1)) / columns;
                for (int i = 0; i < n; i++) {
                    int col = i % columns;
                    rx[i] = col * (cw + GAP);
                    ry[i] = (i / columns) * (CHIP_H + 2);
                    rw[i] = col == columns - 1 ? w - rx[i] : cw;
                }
                h = ((n + columns - 1) / columns) * (CHIP_H + 2) - 2;
            } else {
                int cx = 0;
                int cy = 0;
                for (int i = 0; i < n; i++) {
                    int cw = Math.min(w, font.width(name.apply(values[i])) + 12);
                    if (cx > 0 && cx + cw > w) {
                        cx = 0;
                        cy += CHIP_H + 2;
                    }
                    rx[i] = cx;
                    ry[i] = cy;
                    rw[i] = cw;
                    cx += cw + GAP;
                }
                h = cy + CHIP_H;
            }
        }

        private int indexAt(double mx, double my) {
            for (int i = 0; i < values.length; i++) {
                if (Ui.inside(mx, my, left() + rx[i], top() + ry[i], rw[i], CHIP_H)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            E current = getter.get();
            int hovered = indexAt(mx, my);
            for (int i = 0; i < values.length; i++) {
                chip(g, left() + rx[i], top() + ry[i], rw[i], CHIP_H, name.apply(values[i]),
                        Objects.equals(values[i], current), i == hovered);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            if (!Objects.equals(values[i], getter.get())) {
                setter.accept(values[i]);
                markChanged();
            }
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            int i = indexAt(mx, my);
            return tipFor == null || i < 0 ? null : tip(tipFor.apply(values[i]));
        }
    }

    /** Barra que se arrastra para cambiar un valor (Mayus: saltos grandes). */
    private final class ValueSlider extends Element {
        private final int labelW;
        private final Component label;
        private final int color;
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final float min;
        private final float max;
        private final float step;
        private final float bigStep;
        @Nullable
        private final Component tip;
        private boolean held;
        private boolean changed;

        ValueSlider(int x, int y, int w, int labelW, Component label, int color, Supplier<Float> getter,
                    Consumer<Float> setter, float min, float max, float step, float bigStep, @Nullable Component tip) {
            super(x, y, w, CHIP_H);
            this.labelW = labelW;
            this.label = label;
            this.color = color;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
            this.bigStep = bigStep;
            this.tip = tip;
        }

        private int trackL() {
            return left() + labelW;
        }

        private int trackW() {
            return Math.max(10, w - labelW - 3);
        }

        private int xOf(float v) {
            return trackL() + Math.round((Mth.clamp(v, min, max) - min) / (max - min) * (trackW() - 1));
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            int tl = trackL();
            int tw = trackW();
            boolean hover = held || hit(mx, my);
            g.drawString(font, label, l, t + 3, color, false);
            int cy = t + h / 2;
            g.fill(tl, cy - 1, tl + tw, cy + 1, hover ? 0xFF8A93A6 : 0xFF4A505C);
            if (min < 0f && max > 0f) {
                int zx = xOf(0f);
                g.fill(zx, cy - 3, zx + 1, cy + 4, 0xFF8A93A6);
                int kx = xOf(getter.get());
                g.fill(Math.min(zx, kx), cy - 1, Math.max(zx, kx) + 1, cy + 1, color);
            }
            int kx = xOf(getter.get());
            g.fill(kx - 2, t + 2, kx + 3, t + h - 2, 0xFF101216);
            g.fill(kx - 1, t + 3, kx + 2, t + h - 3, held ? Ui.ACCENT : (hover ? 0xFFFFFFFF : 0xFFD0D4DC));
        }

        @Override
        boolean click(double mx, double my, int button) {
            if (mx < trackL() - 4) {
                return false;
            }
            held = true;
            changed = false;
            drag(mx, my);
            return true;
        }

        @Override
        void drag(double mx, double my) {
            if (!held) {
                return;
            }
            float f = (float) ((mx - trackL()) / Math.max(1, trackW() - 1));
            float v = min + Mth.clamp(f, 0f, 1f) * (max - min);
            float st = hasShiftDown() ? bigStep : step;
            v = Mth.clamp(Math.round(v / st) * st, min, max);
            v = Math.round(v * 1000f) / 1000f; // lo mismo que se ve en el numero
            if (v != getter.get()) {
                setter.accept(v);
                changed = true;
                refreshFields();
            }
        }

        @Override
        void release() {
            held = false;
            if (changed) {
                markChanged();
            }
            changed = false;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return tip == null ? null : tip(tip);
        }
    }

    private final class Toggle extends Element {
        private final Component label;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        @Nullable
        private final Component tip;
        private final boolean model;

        Toggle(int x, int y, int w, Component label, BooleanSupplier getter, Consumer<Boolean> setter,
               @Nullable Component tip, boolean model) {
            super(x, y, w, CHIP_H);
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.tip = tip;
            this.model = model;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            boolean hover = hit(mx, my);
            boolean on = getter.getAsBoolean();
            if (hover) {
                g.fill(l, t, l + w, t + h, 0x20FFFFFF);
            }
            g.fill(l + 2, t + 2, l + 12, t + 12, 0xFF1A1C21);
            g.renderOutline(l + 2, t + 2, 10, 10, on ? Ui.ACCENT : (hover ? 0xFF8A93A6 : 0xFF5A6070));
            if (on) {
                g.fill(l + 4, t + 4, l + 10, t + 10, Ui.ACCENT);
            }
            String s = font.plainSubstrByWidth(label.getString(), Math.max(10, w - 18));
            g.drawString(font, s, l + 16, t + 3, on ? 0xFFFFFFFF : Ui.TEXT, false);
        }

        @Override
        boolean click(double mx, double my, int button) {
            setter.accept(!getter.getAsBoolean());
            if (model) {
                markChanged();
            }
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            return tip == null ? null : tip(tip);
        }
    }

    /** Selector de color HSV: cuadrado de saturacion/brillo y barra de tono. Se arrastra. */
    private final class ColorPicker extends Element {
        private final IntSupplier getter;
        private final IntConsumer setter;
        private float hue;
        private float sat;
        private float val;
        private int known = -1;
        private boolean dragSv;
        private boolean dragHue;

        ColorPicker(int x, int y, int w, int h, IntSupplier getter, IntConsumer setter) {
            super(x, y, w, h);
            this.getter = getter;
            this.setter = setter;
            sync();
        }

        private int svW() {
            return w - 14;
        }

        private void sync() {
            int c = getter.getAsInt() & 0xFFFFFF;
            if (c != known) {
                float[] hsv = Ui.rgbToHsv(c);
                if (hsv[1] > 0.02f && hsv[2] > 0.02f) {
                    hue = hsv[0]; // en grises se conserva el tono elegido
                }
                sat = hsv[1];
                val = hsv[2];
                known = c;
            }
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            sync();
            int l = left();
            int t = top();
            int sw = svW();
            for (int i = 0; i < sw; i += 2) {
                int topColor = 0xFF000000 | Ui.hsvToRgb(hue, (i + 1) / (float) sw, 1f);
                g.fillGradient(l + i, t, Math.min(l + i + 2, l + sw), t + h, topColor, 0xFF000000);
            }
            g.renderOutline(l - 1, t - 1, sw + 2, h + 2, 0xFF000000);
            int cx = l + Math.round(sat * (sw - 1));
            int cy = t + Math.round((1f - val) * (h - 1));
            g.renderOutline(cx - 2, cy - 2, 5, 5, val > 0.6f && sat < 0.4f ? 0xFF000000 : 0xFFFFFFFF);
            int hx = l + sw + 4;
            for (int k = 0; k < 6; k++) {
                int c1 = 0xFF000000 | Ui.hsvToRgb(k / 6f, 1f, 1f);
                int c2 = 0xFF000000 | Ui.hsvToRgb((k + 1) / 6f, 1f, 1f);
                g.fillGradient(hx, t + k * h / 6, hx + 10, t + (k + 1) * h / 6, c1, c2);
            }
            g.renderOutline(hx - 1, t - 1, 12, h + 2, 0xFF000000);
            int hy = t + Math.round(hue * (h - 1));
            g.fill(hx - 2, hy - 1, hx + 12, hy + 1, 0xFFFFFFFF);
        }

        @Override
        boolean click(double mx, double my, int button) {
            int l = left();
            int t = top();
            int sw = svW();
            if (Ui.inside(mx, my, l, t, sw, h)) {
                dragSv = true;
            } else if (Ui.inside(mx, my, l + sw + 2, t, 14, h)) {
                dragHue = true;
            } else {
                return false;
            }
            drag(mx, my);
            return true;
        }

        @Override
        void drag(double mx, double my) {
            int l = left();
            int t = top();
            int sw = svW();
            if (dragSv) {
                sat = Mth.clamp((float) (mx - l) / Math.max(1, sw - 1), 0f, 1f);
                val = 1f - Mth.clamp((float) (my - t) / Math.max(1, h - 1), 0f, 1f);
            } else if (dragHue) {
                hue = Mth.clamp((float) (my - t) / Math.max(1, h - 1), 0f, 0.999f);
            } else {
                return;
            }
            int rgb = Ui.hsvToRgb(hue, sat, val);
            known = rgb;
            if (rgb != (getter.getAsInt() & 0xFFFFFF)) {
                setter.accept(rgb);
            }
        }

        @Override
        void release() {
            dragSv = false;
            dragHue = false;
        }
    }

    /** Colores rapidos. */
    private final class Palette extends Element {
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final int perRow;

        Palette(int x, int y, int w, IntSupplier getter, IntConsumer setter) {
            super(x, y, w, 0);
            this.getter = getter;
            this.setter = setter;
            this.perRow = Math.max(1, (w + 2) / 14);
            this.h = ((PALETTE.length + perRow - 1) / perRow) * 14 - 2;
        }

        private int indexAt(double mx, double my) {
            for (int i = 0; i < PALETTE.length; i++) {
                if (Ui.inside(mx, my, left() + (i % perRow) * 14, top() + (i / perRow) * 14, 12, 12)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int current = getter.getAsInt() & 0xFFFFFF;
            int hovered = indexAt(mx, my);
            for (int i = 0; i < PALETTE.length; i++) {
                Ui.swatch(g, left() + (i % perRow) * 14, top() + (i / perRow) * 14, 12, PALETTE[i],
                        i == hovered || PALETTE[i] == current);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            setter.accept(PALETTE[i]);
            playClick();
            return true;
        }
    }

    /** Todas las texturas, tenidas con el color de la pieza. */
    private final class TextureGrid extends Element {
        private final VehiclePart part;
        private final int cols;
        /** Cuantas texturas se ensenan (las primeras de la lista). */
        private final int count;
        /** Que hacer al elegir una textura (null = ponersela a la pieza). */
        @Nullable
        private final Consumer<String> onPick;

        TextureGrid(int x, int y, int w, VehiclePart part) {
            this(x, y, w, part, null, Materials.PRESETS.size());
        }

        TextureGrid(int x, int y, int w, VehiclePart part, @Nullable Consumer<String> onPick, int limit) {
            super(x, y, w, 0);
            this.part = part;
            this.onPick = onPick;
            this.count = Math.max(1, Math.min(limit, Materials.PRESETS.size()));
            this.cols = Math.max(1, w / CELL);
            this.h = ((count + cols - 1) / cols) * CELL;
        }

        private int indexAt(double mx, double my) {
            if (!hit(mx, my)) {
                return -1;
            }
            int c = (int) ((mx - left()) / CELL);
            int r = (int) ((my - top()) / CELL);
            int i = r * cols + c;
            return c < cols && i < count ? i : -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            Function<ResourceLocation, TextureAtlasSprite> atlas = Materials.atlas();
            float r = red(part.color);
            float gg = green(part.color);
            float b = blue(part.color);
            String current = VehicleDefinition.cleanMaterial(part.material);
            int hovered = indexAt(mx, my);
            for (int i = 0; i < count; i++) {
                Materials.Preset preset = Materials.PRESETS.get(i);
                int cx = left() + (i % cols) * CELL;
                int cy = top() + (i / cols) * CELL;
                g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF1A1C21);
                g.blit(cx + 1, cy + 1, 0, 16, 16, Materials.sprite(atlas, preset.id()), r, gg, b, 1f);
                if (preset.id().equals(current)) {
                    g.renderOutline(cx, cy, CELL, CELL, Ui.ACCENT);
                } else if (i == hovered) {
                    g.renderOutline(cx, cy, CELL, CELL, 0xFFFFFFFF);
                }
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            String id = Materials.PRESETS.get(i).id();
            if (onPick != null) {
                onPick.accept(id);
            } else {
                part.material = id;
                markChanged();
            }
            playClick();
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            int i = indexAt(mx, my);
            return i < 0 ? null
                    : tip(Component.translatable("material.vehiclestudio." + Materials.PRESETS.get(i).key()));
        }
    }

    /** Tarjetas de plantillas: al pasar el raton se ven en 3D, con un clic se aplican. */
    private final class TemplateGrid extends Element {
        private final int cols;

        TemplateGrid(int y) {
            super(0, y, contentW, 0);
            this.cols = contentW >= 230 ? 2 : 1;
            int rows = (Templates.ALL.size() + cols - 1) / cols;
            this.h = rows * (CARD_H + 3) - 3;
        }

        private int cardW() {
            return (w - GAP * (cols - 1)) / cols;
        }

        private int cardX(int i) {
            return left() + (i % cols) * (cardW() + GAP);
        }

        private int cardY(int i) {
            return top() + (i / cols) * (CARD_H + 3);
        }

        int indexAt(double mx, double my) {
            for (int i = 0; i < Templates.ALL.size(); i++) {
                if (Ui.inside(mx, my, cardX(i), cardY(i), cardW(), CARD_H)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int hovered = indexAt(mx, my);
            int cw = cardW();
            for (int i = 0; i < Templates.ALL.size(); i++) {
                Templates.Template t = Templates.ALL.get(i);
                int cx = cardX(i);
                int cy = cardY(i);
                boolean hover = i == hovered;
                g.fill(cx, cy, cx + cw, cy + CARD_H, hover ? 0xFF3A4458 : 0xFF2E323C);
                g.renderOutline(cx, cy, cw, CARD_H, hover ? Ui.ACCENT : 0xFF454B58);
                g.drawString(font, Component.translatable("template.vehiclestudio." + t.key()), cx + 6, cy + 4,
                        0xFFFFFFFF, false);
                String desc = Component.translatable("template.vehiclestudio." + t.key() + ".desc").getString();
                g.drawString(font, font.plainSubstrByWidth(desc, Math.max(10, cw - 12)), cx + 6, cy + 14, Ui.MUTED,
                        false);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int i = indexAt(mx, my);
            if (i < 0) {
                return false;
            }
            applyTemplate(Templates.ALL.get(i));
            playClick();
            return true;
        }
    }

    /** Lista en arbol de todas las piezas, con su propio desplazamiento. */
    private final class PartsList extends Element {
        PartsList(int y, int h) {
            super(0, y, contentW, h);
        }

        int visibleRows() {
            return Math.max(1, (h - 4) / LIST_ROW);
        }

        @Override
        void render(GuiGraphics g, int mx, int my) {
            int l = left();
            int t = top();
            g.fill(l, t, l + w, t + h, Ui.PANEL_DARK);
            g.renderOutline(l, t, w, h, Ui.BORDER);
            List<int[]> rows = displayRows();
            int visible = visibleRows();
            partsScroll = Mth.clamp(partsScroll, 0, Math.max(0, rows.size() - visible));
            g.enableScissor(l + 1, t + 1, l + w - 1, t + h - 1);
            for (int r = 0; r < visible + 1 && r + partsScroll < rows.size(); r++) {
                int[] row = rows.get(r + partsScroll);
                int index = row[0];
                int depth = Math.min(row[1], 8);
                int ry = t + 2 + r * LIST_ROW;
                boolean isSelected = index == selected;
                boolean hover = Ui.inside(mx, my, l + 1, ry, w - 2, LIST_ROW);
                if (isSelected) {
                    g.fill(l + 1, ry, l + w - 1, ry + LIST_ROW, Ui.SELECT);
                } else if (hover) {
                    g.fill(l + 1, ry, l + w - 1, ry + LIST_ROW, 0x30FFFFFF);
                }
                VehiclePart p = def.parts.get(index);
                int px = l + 4 + depth * 7;
                if (depth > 0) {
                    g.vLine(px - 4, ry - 1, ry + 6, 0xFF4A5060);
                    g.hLine(px - 4, px - 2, ry + 6, 0xFF4A5060);
                }
                Ui.swatch(g, px, ry + 2, 8, p.color, false);
                String name = font.plainSubstrByWidth(p.name, Math.max(10, l + w - 16 - (px + 11)));
                g.drawString(font, name, px + 11, ry + 2, isSelected ? 0xFFFFFFFF : Ui.TEXT, false);
                if (p.kind != PartKind.BODY) {
                    String mark = kindMark(p.kind);
                    g.drawString(font, mark, l + w - 5 - font.width(mark), ry + 1, Ui.ACCENT, false);
                }
            }
            g.disableScissor();
            if (rows.isEmpty()) {
                Ui.wrapped(g, font, Ui.tr("editor.no_parts"), l + 5, t + 5, w - 10, Ui.MUTED);
            }
            if (rows.size() > visible) {
                int trackH = h - 4;
                int barH = Math.max(8, trackH * visible / rows.size());
                int barY = t + 2 + (trackH - barH) * partsScroll / Math.max(1, rows.size() - visible);
                g.fill(l + w - 3, barY, l + w - 1, barY + barH, 0xFF6A7080);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            int row = (int) ((my - top() - 2) / LIST_ROW) + partsScroll;
            List<int[]> rows = displayRows();
            if (row >= 0 && row < rows.size() && my >= top() + 2) {
                select(rows.get(row)[0]);
            }
            return true;
        }

        @Override
        boolean scrolled(double amount) {
            partsScroll -= (int) Math.signum(amount);
            return true;
        }

        @Override
        @Nullable
        List<FormattedCharSequence> tooltip(double mx, double my) {
            int row = (int) ((my - top() - 2) / LIST_ROW) + partsScroll;
            List<int[]> rows = displayRows();
            if (row < 0 || row >= rows.size()) {
                return null;
            }
            VehiclePart p = def.parts.get(rows.get(row)[0]);
            return tip(Ui.tr("kind." + p.kind.key()));
        }
    }

    // =======================================================================================
    // Lista desplegable (se dibuja por encima de todo)
    // =======================================================================================

    /** Marca corta del tipo de pieza en la lista (rueda, asiento...). */
    private static String kindMark(PartKind kind) {
        return switch (kind) {
            case WHEEL -> "O";
            case SEAT -> "h";
            case STEERING -> "@";
            case LIGHT -> "*";
            case EXHAUST -> "~";
            default -> "";
        };
    }

    private final class Dropdown {
        private final List<DropItem> items;
        private final IntConsumer onPick;
        private final int x;
        private final int y;
        private final int w;
        private final int rows;
        private int scroll;

        Dropdown(int ax, int ay, int aw, int ah, List<DropItem> items, IntConsumer onPick) {
            this.items = items;
            this.onPick = onPick;
            int maxW = aw;
            for (DropItem it : items) {
                maxW = Math.max(maxW, font.width(it.label()) + 18 + Math.min(it.indent(), 6) * 6
                        + (it.color() >= 0 ? 11 : 0));
            }
            this.w = Math.min(maxW, width - 8);
            int below = height - (ay + ah) - 3;
            int above = ay - HEADER_H - 3;
            int wanted = items.size() * LIST_ROW + 4;
            boolean down = below >= wanted || below >= above;
            int space = down ? below : above;
            this.rows = Mth.clamp((space - 4) / LIST_ROW, 1, items.size());
            int dh = rows * LIST_ROW + 4;
            this.y = down ? ay + ah + 1 : ay - dh - 1;
            this.x = Mth.clamp(ax, 2, Math.max(2, width - w - 2));
            int current = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).selected()) {
                    current = i;
                    break;
                }
            }
            this.scroll = Mth.clamp(current - rows / 2, 0, Math.max(0, items.size() - rows));
        }

        private int boxH() {
            return rows * LIST_ROW + 4;
        }

        boolean inside(double mx, double my) {
            return Ui.inside(mx, my, x, y, w, boxH());
        }

        void render(GuiGraphics g, int mx, int my) {
            Ui.popup(g, x, y, w, boxH());
            boolean scrollable = items.size() > rows;
            for (int r = 0; r < rows && r + scroll < items.size(); r++) {
                DropItem it = items.get(r + scroll);
                int ry = y + 2 + r * LIST_ROW;
                boolean hover = Ui.inside(mx, my, x + 1, ry, w - 2, LIST_ROW);
                if (it.selected()) {
                    g.fill(x + 1, ry, x + w - 1, ry + LIST_ROW, Ui.SELECT);
                }
                if (hover) {
                    g.fill(x + 1, ry, x + w - 1, ry + LIST_ROW, 0x40FFFFFF);
                }
                int tx = x + 4 + Math.min(it.indent(), 6) * 6;
                if (it.color() >= 0) {
                    Ui.swatch(g, tx, ry + 2, 8, it.color(), false);
                    tx += 11;
                }
                String s = font.plainSubstrByWidth(it.label().getString(), Math.max(10, x + w - 6 - (scrollable ? 4 : 0) - tx));
                g.drawString(font, s, tx, ry + 2, it.selected() || hover ? 0xFFFFFFFF : Ui.TEXT, false);
            }
            if (scrollable) {
                int trackH = boxH() - 4;
                int barH = Math.max(8, trackH * rows / items.size());
                int barY = y + 2 + (trackH - barH) * scroll / Math.max(1, items.size() - rows);
                g.fill(x + w - 4, barY, x + w - 2, barY + barH, 0xFF8A93A6);
            }
        }

        void click(double mx, double my) {
            int r = (int) ((my - y - 2) / LIST_ROW);
            int i = r + scroll;
            if (my >= y + 2 && r >= 0 && r < rows && i < items.size()) {
                dropdown = null;
                onPick.accept(i);
                playClick();
            }
        }

        void scrollBy(double amount) {
            int step = items.size() > 200 ? 8 : items.size() > 60 ? 4 : 1;
            scroll = Mth.clamp(scroll - (int) Math.signum(amount) * step, 0, Math.max(0, items.size() - rows));
        }
    }
}
