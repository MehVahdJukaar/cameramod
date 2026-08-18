package net.mehvahdjukaar.vista.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class VistaWelcomeScreen extends Screen {

    private final Screen lastScreen;
    private final Consumer<String> onCustom;
    private final Runnable onDefault;
    private final Runnable onDisable;
    private final Component messageText;

    private int ticksUntilEnable;
    private boolean showingCustom;

    private Button disableButton;
    private Button defaultButton;
    private Button customButton;

    private EditBox urlField;
    private Button confirmCustomButton;
    private Button backButton;
    private MultiLineLabel customHintLabel;

    private MultiLineLabel messageLabel;

    public VistaWelcomeScreen(final Screen lastScreen,
                              Consumer<String> callback,
                              Runnable onDefault, Runnable onDisable) {
        super(Component.translatable("gui.vista.welcome.title").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        this.lastScreen = lastScreen;
        this.messageText = Component.translatable("gui.vista.welcome.message",
                Component.translatable("block.vista.wave_gate"));
        this.onCustom = callback;
        this.onDefault = onDefault;
        this.onDisable = onDisable;
        this.ticksUntilEnable = 60;
        this.showingCustom = false;
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), this.messageText);
    }

    @Override
    protected void init() {
        super.init();
        final int buttonWidth = 150;
        final int buttonHeight = 20;
        final int centerX = this.width / 2;
        int bottomY = this.height * 3 / 6;


        int centerButton = centerX - buttonWidth / 2;

        this.defaultButton = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.vista.welcome.install_defaults")
                                        .withStyle(ChatFormatting.GREEN)
                                , btn -> {
                                    onDefault.run();
                                    Minecraft.getInstance().setScreen(lastScreen);
                                })
                        .bounds(centerButton, bottomY, buttonWidth, buttonHeight)
                        .build()
        );

        bottomY += buttonHeight + 4;
        this.customButton = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.vista.welcome.install_custom"), btn -> enterCustomMode())
                        .bounds(centerButton, bottomY, buttonWidth, buttonHeight)
                        .build()
        );

        bottomY += buttonHeight + 4;
        this.disableButton = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.vista.welcome.disable"), btn -> {
                            onDisable.run();
                            Minecraft.getInstance().setScreen(lastScreen);
                        })
                        .bounds(centerButton, bottomY, buttonWidth, buttonHeight)
                        .build()
        );


        final int fieldWidth = 200;
        final int fieldHeight = 20;
        final int customY = this.height * 3 / 6 - 11;  // slightly above the normal button area

        this.confirmCustomButton = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ok"), btn -> {
                            final String url = urlField.getValue().trim();
                            if (!url.isEmpty()) {
                                onCustom.accept(url);
                                Minecraft.getInstance().setScreen(lastScreen);
                            }
                        })
                        .bounds(centerButton, customY + 35, buttonWidth, buttonHeight)
                        .build()
        );
        this.confirmCustomButton.visible = false;
        this.confirmCustomButton.active = false;

        this.backButton = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), btn -> leaveCustomMode())
                        .bounds(centerButton, customY + 59, buttonWidth, buttonHeight)
                        .build()
        );
        this.backButton.visible = false;

        this.customHintLabel = MultiLineLabel.create(this.font,
                Component.translatable("gui.vista.welcome.custom_hint").withStyle(ChatFormatting.YELLOW),
                this.width - 50
        );

        this.urlField = new EditBox(
                this.font,
                centerX - fieldWidth / 2,
                customY + 10,
                fieldWidth,
                fieldHeight,
                Component.empty()
        );
        this.urlField.setResponder(s -> {
            this.confirmCustomButton.active = !s.isBlank();
        });
        this.urlField.setMaxLength(300);
        this.urlField.setVisible(false);
        this.addRenderableWidget(this.urlField);   // manually added because it's not a button


        // Message label (always visible)
        this.messageLabel = MultiLineLabel.create(this.font, this.messageText, this.width - 50);

        activateIfReady();
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);

        // Main message
        this.messageLabel.renderCentered(graphics, this.width / 2, 55);

        if (this.showingCustom) {
            // Hint and URL field
            this.customHintLabel.renderCentered(graphics, this.width / 2, this.urlField.getY() - 12);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.ticksUntilEnable > 0) {
            this.ticksUntilEnable--;
            activateIfReady();
        }
    }

    private void activateIfReady() {
        if (this.ticksUntilEnable <= 0) {
            this.customButton.active = true;
            this.defaultButton.active = true;
            this.disableButton.active = true;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    private void enterCustomMode() {
        this.showingCustom = true;
        this.urlField.setValue("");
        this.urlField.setVisible(true);
        this.confirmCustomButton.visible = true;
        this.backButton.visible = true;
        this.setFocused(this.urlField);

        this.disableButton.visible = false;
        this.defaultButton.visible = false;
        this.customButton.visible = false;
    }

    private void leaveCustomMode() {
        this.showingCustom = false;
        this.urlField.setVisible(false);
        this.confirmCustomButton.visible = false;
        this.backButton.visible = false;
        this.setFocused(null);

        this.disableButton.visible = true;
        this.defaultButton.visible = true;
        this.customButton.visible = true;
    }

}