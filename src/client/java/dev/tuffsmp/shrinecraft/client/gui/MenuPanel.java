package dev.tuffsmp.shrinecraft.client.gui;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.util.Identifier;

/**
 * The framed, painted panel behind the buttons of the pause (Esc) menu, in the current theme - the same frame and scene as the inventory.
 *
 * The panel is NOT a fixed picture: every frame it is sized round the buttons that are really on the screen, so buttons that OTHER MODS
 * add or move (Mod Menu's "Mods" button, Replay Mod's recording buttons, anything else) end up inside the frame too. Two sources:
 *   1. the screen's own widgets (normal buttons, including the ones mods add the vanilla way), and
 *   2. every button sprite that was actually drawn last frame (DrawContextMixin reports them) - this catches mods that draw their buttons
 *      with their own GUI library instead of adding widgets to the screen.
 * Mod buttons that use the vanilla button look are themed automatically (sprite swap); a mod's own icon texture keeps its look but sits on the panel.
 */
public final class MenuPanel {
    private MenuPanel() {}

    // ======================= TWEAK ME =======================
    /** Space between the outermost buttons and the inside of the frame, in GUI pixels. */
    private static final int PAD_X = 14;
    private static final int PAD_TOP = 12;
    private static final int PAD_BOTTOM = 14;
    /** How dark the painted scene is behind the buttons (0 = full picture, 255 = black). Keeps the button text readable. */
    private static final int SCENE_DIM_ALPHA = 0x70;
    // ========================================================

    private static final int BAND = 9, SIDE = 5, END = 44, SHEET = 256;

    // bounding box of button sprites drawn during the frame that is being built (see track / endFrame)
    private static int curL = Integer.MAX_VALUE, curT = Integer.MAX_VALUE, curR = Integer.MIN_VALUE, curB = Integer.MIN_VALUE;
    private static int lastL = Integer.MAX_VALUE, lastT = Integer.MAX_VALUE, lastR = Integer.MIN_VALUE, lastB = Integer.MIN_VALUE;
    private static Screen trackedScreen = null;

    /** Called by DrawContextMixin for every button sprite drawn while a pause menu is open. */
    public static void track(int x, int y, int w, int h) {
        if (w < 16 || h < 12 || h > 40) {
            return; // not a button-sized thing
        }
        curL = Math.min(curL, x);
        curT = Math.min(curT, y);
        curR = Math.max(curR, x + w);
        curB = Math.max(curB, y + h);
    }

    /** Draws the panel behind the buttons of this screen. Called right after the screen's background (blur) and before its widgets. */
    public static void draw(DrawContext ctx, Screen screen) {
        GuiTheme theme = GuiSettings.currentTheme();
        // roll the tracking window: what was drawn since the last call is "last frame"
        if (trackedScreen == screen) {
            lastL = curL; lastT = curT; lastR = curR; lastB = curB;
        } else {
            lastL = lastT = Integer.MAX_VALUE;
            lastR = lastB = Integer.MIN_VALUE;
            trackedScreen = screen;
        }
        curL = curT = Integer.MAX_VALUE;
        curR = curB = Integer.MIN_VALUE;
        if (theme == null) {
            return;
        }
        int l = lastL, t = lastT, r = lastR, b = lastB;
        int titleY = Integer.MAX_VALUE;
        for (Element e : screen.children()) {
            if (e instanceof TextWidget text && text.visible) {
                titleY = Math.min(titleY, text.getY());
            } else if (e instanceof PressableWidget w && ((ClickableWidget) w).visible) {
                l = Math.min(l, w.getX());
                t = Math.min(t, w.getY());
                r = Math.max(r, w.getX() + w.getWidth());
                b = Math.max(b, w.getY() + w.getHeight());
            }
        }
        if (r <= l || b <= t) {
            return; // no buttons (F3+Esc "paused" without a menu)
        }
        int x0 = Math.max(2, l - PAD_X - SIDE);
        int x1 = Math.min(ctx.getScaledWindowWidth() - 2, r + PAD_X + SIDE);
        int y0 = t - PAD_TOP - BAND;
        if (titleY != Integer.MAX_VALUE && t - titleY < 70) {
            y0 = Math.min(y0, titleY - 6 - BAND); // the "Game Menu" title sits inside the frame too
        }
        y0 = Math.max(2, y0);
        int y1 = Math.min(ctx.getScaledWindowHeight() - 2, b + PAD_BOTTOM + BAND);
        drawPanel(ctx, theme, x0, y0, x1 - x0, y1 - y0);
    }

    /** A framed panel of any size from the theme's piece sheet (textures/gui/themed/<theme>/panel.png, made by build_themed_widgets.py). */
    public static void drawPanel(DrawContext ctx, GuiTheme theme, int x, int y, int w, int h) {
        Identifier sheet = Identifier.of(ShrineCraft.MOD_ID, "textures/gui/themed/" + theme.key + "/panel.png");
        var pipe = RenderPipelines.GUI_TEXTURED;
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, theme.outlineColor | 0xFF000000);
        // painted scene, tiled (the sheet holds the tile + its mirror image side by side, so the repeat has no seams)
        int ix0 = x + SIDE, iy0 = y + BAND, ix1 = x + w - SIDE, iy1 = y + h - BAND;
        for (int ty = iy0; ty < iy1; ty += 70) {
            int th = Math.min(70, iy1 - ty);
            for (int tx = ix0; tx < ix1; tx += 80) {
                int tw = Math.min(80, ix1 - tx);
                ctx.drawTexture(pipe, sheet, tx, ty, 16, 18, tw, th, SHEET, SHEET);
            }
        }
        ctx.fill(ix0, iy0, ix1, iy1, SCENE_DIM_ALPHA << 24);
        // top + bottom bands: ornate ends, repeated middle
        for (int band = 0; band < 2; band++) {
            int by = band == 0 ? y : y + h - BAND;
            int v = band * BAND;
            int end = Math.min(END, w / 2);
            for (int mx = x + end; mx < x + w - end; mx += 176 - 2 * END) {
                ctx.drawTexture(pipe, sheet, mx, by, END, v, Math.min(176 - 2 * END, x + w - end - mx), BAND, SHEET, SHEET);
            }
            ctx.drawTexture(pipe, sheet, x, by, 0, v, end, BAND, SHEET, SHEET);
            ctx.drawTexture(pipe, sheet, x + w - end, by, 176 - end, v, end, BAND, SHEET, SHEET);
        }
        // side edges
        for (int ey = y + BAND; ey < y + h - BAND; ey += 32) {
            int eh = Math.min(32, y + h - BAND - ey);
            ctx.drawTexture(pipe, sheet, x, ey, 0, 18, SIDE, eh, SHEET, SHEET);
            ctx.drawTexture(pipe, sheet, x + w - SIDE, ey, 5, 18, SIDE, eh, SHEET, SHEET);
        }
    }
}
