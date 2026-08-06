package net.mehvahdjukaar.vista.client;

import net.mehvahdjukaar.moonlight.api.client.PostShadersHelper;
import net.mehvahdjukaar.moonlight.api.misc.EventCalled;
import net.mehvahdjukaar.moonlight.api.util.math.EntityAngles;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;


public class ViewFinderController {
    private static final PostShadersHelper.Group LENSES_GROUP = new PostShadersHelper.Group(
            VistaMod.res("lenses"), 20
    );

    @Nullable
    protected static ViewFinderBlockEntity viewFinder;

    private static CameraType lastCameraType;
    // values controlled by player mouse movement. Not actually what camera uses
    private static float yawIncrease;
    private static float pitchIncrease;
    private static boolean needsToUpdateServer;
    // lerp camera
    private static float lastCameraYaw = 0;
    private static float lastCameraPitch = 0;
    private static boolean turnedLastTick = false;

    private static int viewFinderZoom = 0;
    private static boolean justStarted = false;

    public static void startControlling(ViewFinderBlockEntity tile) {
        Minecraft mc = Minecraft.getInstance();
        if (isAdventureNoInteraction(tile)) return;
        if (viewFinder == null) {
            viewFinder = tile;
            lastCameraType = mc.options.getCameraType();
            justStarted = true;
        } //if not it means we entered from manoeuvre mode gui
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        MutableComponent message = Component.translatable("message.vista.viewfinder.control",
                mc.options.keyShift.getTranslatedKeyMessage(),
                mc.options.keyAttack.getTranslatedKeyMessage());
        mc.gui.setOverlayMessage(message, false);
        mc.getNarrator().sayNow(message);

        viewFinderZoom = tile.getZoomLevel();
    }

    // only works if we are already controlling
    private static void stopControllingAndSync() {
        if (viewFinder == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        viewFinder.syncToServer( true, player);
        stopControlling();
    }

    public static void stopControlling() {
        if (viewFinder == null) return;
        viewFinder = null;
        lastCameraYaw = 0;
        lastCameraPitch = 0;
        turnedLastTick = false;
        justStarted = false;
        if (lastCameraType != null) {
            Minecraft.getInstance().options.setCameraType(lastCameraType);
        }
    }

    public static ViewFinderBlockEntity getViewFinder(){
        return viewFinder;
    }

    public static boolean isActive() {
        return viewFinder != null;
    }

    public static boolean isActiveFor(BlockEntity tile) {
        return viewFinder == tile;
    }

    public static boolean setupCamera(Camera camera, BlockGetter level, Entity entity,
                                      boolean detached, boolean thirdPersonReverse, float partialTick) {

        if (!isActive()) return false;
        Vec3 centerCannonPos = viewFinder.getGlobalPosition(partialTick);

        // lerp camera
        Vec3 targetCameraPos = centerCannonPos.add(0, 0.5, 0);

        float targetYRot;
        float targetXRot;

        if (justStarted) {
            EntityAngles angles = EntityAngles.fromQuaternion(viewFinder.getWorldOrientation(partialTick));
            targetYRot = angles.yaw();
            targetXRot = angles.pitch();
            justStarted = false;
        } else {
            targetYRot = camera.getYRot() + yawIncrease;
            targetXRot = Mth.clamp(camera.getXRot() + pitchIncrease, -90, 90);
        }

        camera.setPosition(targetCameraPos);
        camera.setRotation(targetYRot, targetXRot);

        lastCameraYaw = camera.getYRot();
        lastCameraPitch = camera.getXRot();

        yawIncrease = 0;
        pitchIncrease = 0;

        viewFinder.setWorldOrientation(EntityAngles.of(targetXRot, targetYRot).toQuaternion());
        if (turnedLastTick) viewFinder.snapToWantedRotationInstantly();
        turnedLastTick = true;
        return true;
    }

    // true cancels the thing
    public static boolean onPlayerRotated(double yawAdd, double pitchAdd) {
        if (isActive()) {
            if (isLocked()) return true;
            if (isAdventureViewOnly()) return true;
            float scale = 0.2f;
            yawIncrease += (float) (yawAdd * scale);
            pitchIncrease += (float) (pitchAdd * scale);
            if (yawAdd != 0 || pitchAdd != 0) needsToUpdateServer = true;
            if (viewFinder.shouldRotatePlayerFaceWhenManeuvering()) {

                //make player face camera while maneuvering
                LocalPlayer player = Minecraft.getInstance().player;
                player.turn(Mth.wrapDegrees((lastCameraYaw + yawAdd) - player.yHeadRot),
                        Mth.wrapDegrees((lastCameraPitch + pitchAdd) - player.getXRot()));
                player.yHeadRotO = player.yHeadRot;
                player.xRotO = player.getXRot();
            }
            return true;
        }
        return false;
    }

    private static void onKeyJump() {
    }

    private static void onKeyInventory() {
        //Disabled, too buggy
        //viewFinder.sendOpenGuiRequest();
    }

    private static void onKeyShift() {
        stopControllingAndSync();
    }

    @EventCalled
    public static boolean onMouseScrolled(double scrollDelta) {
        if (!isActive()) return false;
        if (isLocked()) return true;
        if (isAdventureViewOnly()) return true;

        if (scrollDelta != 0) {
            int newZoom = (Math.clamp((int) (viewFinder.getZoomLevel() + scrollDelta), 1, ViewFinderBlockEntity.MAX_ZOOM));
            int oldZoom = viewFinder.getZoomLevel();
            if (newZoom != oldZoom) {
                viewFinderZoom = newZoom;
                needsToUpdateServer = true;
                if (newZoom % 4 == 0) playZoomClick();
            }
        }
        return true;
    }

    // click fed back while zooming, both from the overlay wheel and the view finder gui slider
    public static void playZoomClick() {
        //TODO: proper sound here
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 3));
    }

    @EventCalled
    public static boolean onPlayerAttack() {
        if (!isActive()) return false;
        if (isAdventureViewOnly()) return true;
        toggleLock();
        return true;
    }

    @EventCalled
    public static boolean onPlayerUse() {
        if (!isActive()) return false;
        if (isAdventureViewOnly()) return true;
        toggleLock();
        return true;
    }

    public static void onInputUpdate(Input input) {
        // resets input
        input.down = false;
        input.up = false;
        input.left = false;
        input.right = false;
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.shiftKeyDown = false;
        input.jumping = false;
    }

    public static void onClientTick(Minecraft mc) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        setupPostShaders();
        if (!isActive()) return;
        if (viewFinder.stillValid(player)) {
            //keep setting zoom so we avoid jitter
            viewFinder.setZoomLevel(viewFinderZoom);
            if (needsToUpdateServer) {
                needsToUpdateServer = false;
                viewFinder.syncToServer( false, player);
            }

        } else {
            stopControllingAndSync();
        }
    }

    //called by mixin. its cancellable. maybe switch all to this
    public static boolean onEarlyKeyPress(int key, int scanCode, int action, int modifiers) {
        if (!isActive()) return false;
        var options = Minecraft.getInstance().options;

        if (action != GLFW.GLFW_PRESS) return false;
        if (key == 256) {
            stopControllingAndSync();
            return true;
        } else if (options.keyInventory.matches(key, scanCode)) {
            onKeyInventory();
            return true;
        }
        if (options.keyJump.matches(key, scanCode)) {
            onKeyJump();
            return true;
        }
        if (options.keyShift.matches(key, scanCode)) {
            onKeyShift();
            return true;
        }
        return false;
    }

    public static boolean cancelsXPBar() {
        return isActive();
    }

    public static boolean cancelsHotBar() {
        return isActive();
    }

    public static boolean isZooming() {
        if (isActive()) {
            return viewFinder.getZoomLevel() > 1;
        }
        return false;
    }

    private static void toggleLock() {
        viewFinder.cycleLock();
        needsToUpdateServer = true;
    }

    public static boolean isLocked() {
        return viewFinder.isLocked();
    }

    @EventCalled
    public static float modifyFOV(float start, float modified, Player player) {
        if (isActive()) {
            return viewFinder.getFOVModifier();
        }
        return modified;
    }

    private static void setupPostShaders() {
        if (isActive()) {
            ResourceLocation lensShader = viewFinder.getLensShader();
            PostShadersHelper.toggleEffect(lensShader, LENSES_GROUP);
        } else {
            PostShadersHelper.toggleEffect(null, LENSES_GROUP);
        }
    }

    private static boolean isAdventureNoInteraction(ViewFinderBlockEntity tile) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        if (mc.gameMode.getPlayerMode() != GameType.ADVENTURE) return false;
        return tile.getAdventureModeOperation() == ViewFinderBlockEntity.AdventureModeOperation.NO_INTERACTION;
    }

    private static boolean isAdventureViewOnly() {
        if (viewFinder == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        if (mc.gameMode.getPlayerMode() != GameType.ADVENTURE) return false;
        return viewFinder.getAdventureModeOperation() == ViewFinderBlockEntity.AdventureModeOperation.VIEW_ONLY;
    }


}

