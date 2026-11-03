package net.mehvahdjukaar.vista.client.ui;


import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.Nullable;

//copied from Suppl
public class ModWarningScreen extends Screen {

    private static final Component IRIS_TEXT = Component.translatable("gui.vista.iris_warning.message");
    private static final Component IRIS_URL = Component.translatable("gui.vista.iris_warning.suggestions")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).applyFormat(ChatFormatting.UNDERLINE)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/irisbonk")));
    private static final Component IRIS_TITLE = Component.translatable("gui.vista.iris_warning.title")
            .withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
    private static final Component VEIL_TEXT = Component.translatable("gui.vista.veil_warning.message");
    private static final Component VEIL_URL = Component.translatable("gui.vista.veil_warning.suggestions")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).applyFormat(ChatFormatting.UNDERLINE)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/veil-bonk")));
    private static final Component VEIL_TITLE = Component.translatable("gui.vista.veil_warning.title")
            .withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);

    private final Screen lastScreen;
    private final Component text;
    @Nullable
    private final Component url;
    private final Runnable onTurnOff;
    private int ticksUntilEnable;
    private MultiLineLabel message;
    private MultiLineLabel suggestions;
    private Button exitButton;
    private Button disaleButton;

    public ModWarningScreen(Screen screen, int ticksUntilEnable,
                            Component title, Component text, @Nullable Component url,
                            Runnable onTurnOff) {
        super(title);
        this.message = MultiLineLabel.EMPTY;
        this.suggestions = MultiLineLabel.EMPTY;
        this.lastScreen = screen;
        this.ticksUntilEnable = ticksUntilEnable;
        this.text = text;
        this.url = url;
        this.onTurnOff = onTurnOff;
    }


    // static stuff

    public static ModWarningScreen createIris(Screen screen) {
        return new ModWarningScreen(screen, 60, IRIS_TITLE, IRIS_TEXT,
                IRIS_URL, ClientConfigs::disableIrisWarn);
    }

    public static ModWarningScreen createVeil(Screen screen) {
        return new ModWarningScreen(screen, 60, VEIL_TITLE, VEIL_TEXT,
                VEIL_URL, ClientConfigs::disableVeilWarn);
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), text);
    }

    @Override
    protected void init() {
        super.init();
        this.exitButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_PROCEED, (pressed) -> {
            Minecraft.getInstance().setScreen(this.lastScreen);
        }).bounds(this.width / 2 + 5, this.height * 5 / 6, 150, 20).build());
        this.exitButton.active = false;

        this.disaleButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.vista.warning_screen.turn_off"), (pressed) -> {
                    Minecraft.getInstance().setScreen(this.lastScreen);
                    onTurnOff.run();
                }).bounds(this.width / 2 - 155, this.height * 5 / 6, 150, 20).build());
        this.disaleButton.active = false;

        this.message = MultiLineLabel.create(this.font, text, this.width - 50);
        this.suggestions = url == null ? MultiLineLabel.EMPTY :
                MultiLineLabel.create(this.font, url, this.width - 50);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 30, CommonColors.WHITE);
        this.message.renderCentered(graphics, this.width / 2, 55);
        this.suggestions.renderCentered(graphics, this.width / 2, 180);
    }

    @Override
    public void tick() {
        super.tick();
        if (--this.ticksUntilEnable <= 0) {
            this.exitButton.active = true;
            this.disaleButton.active = true;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.ticksUntilEnable <= 0;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (pMouseY > 180.0 && pMouseY < 190.0 && this.url != null) {
            Style style = this.getClickedComponentStyleAt((int) pMouseX);
            if (url != null && style != null && style.getClickEvent() != null && style.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                this.handleComponentClicked(style);
                return false;
            }
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    private @Nullable Style getClickedComponentStyleAt(int xPos) {
        int wid = Minecraft.getInstance().font.width(url);
        int left = this.width / 2 - wid / 2;
        int right = this.width / 2 + wid / 2;
        return xPos >= left && xPos <= right ? Minecraft.getInstance().font.getSplitter().componentStyleAtWidth(url, xPos - left) : null;
    }

}
