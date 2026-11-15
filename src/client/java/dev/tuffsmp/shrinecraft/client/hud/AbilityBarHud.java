package dev.tuffsmp.shrinecraft.client.hud;

import dev.tuffsmp.shrinecraft.ability.Abilities;
import dev.tuffsmp.shrinecraft.client.mixin.BossBarHudAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The ability bars, drawn exactly like vanilla BOSS BARS (the real boss-bar textures, top-centre, weapon name above the bar,
 * pushed below any real boss bar) - but each bar is TINTED in its weapon's colour and carries a second counter in the title.
 * Several bars sit side by side in one row (the whole row shrinks if the window is too narrow).
 *
 *   READY     the weapon you are holding: full bar, "READY" in green
 *   ACTIVE    the buff / armed window is running: the bar DRAINS, counter = seconds left
 *   COOLDOWN  recharging: the bar REFILLS (a bit darker), counter = seconds until ready
 * Weapons you are not holding only get a bar while they are ACTIVE or on COOLDOWN.
 * Bars glide to their value and fade in/out softly. All timer maths is in ability/Abilities (shared with the items).
 */
public final class AbilityBarHud {
    private AbilityBarHud() {}

    // ======================= TWEAK ME =======================
    /** A vanilla boss bar is 182 x 5 pixels; the gap between side-by-side bars. */
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_GAP = 14;
    /** Where the first bar sits (vanilla boss bars start at y = 12; each real boss bar takes 19). */
    private static final int TOP_Y = 12;
    private static final int VANILLA_BOSS_BAR_HEIGHT = 19;
    /** How fast things glide (bigger = snappier). Frame-rate independent. */
    private static final float SMOOTH_BAR = 9.0F;
    private static final float SMOOTH_FADE = 7.0F;
    private static final float SMOOTH_SLIDE = 10.0F;
    /** A READY bar (the ability has recharged, or you just took the weapon in hand) stays this long, then fades away. */
    private static final float READY_SHOW_SECONDS = 3.0F;
    /** The white boss-bar sprite is grey (about 73% bright), so the tint is brightened by this much to reach the weapon colour. */
    private static final float TINT_BOOST = 1.38F;
    static final int READY_GREEN = 0xFF8CFFB0;
    // ========================================================

    private static final Identifier BACKGROUND = Identifier.ofVanilla("boss_bar/white_background");
    private static final Identifier PROGRESS = Identifier.ofVanilla("boss_bar/white_progress");

    /** Every weapon with an ability, in a FIXED order so bars do not jump around when you switch items. */
    private static final List<Class<? extends Item>> ORDER = List.of(
        dev.tuffsmp.shrinecraft.item.MoltenGreataxeItem.class, dev.tuffsmp.shrinecraft.item.CutlassOfDoomItem.class,
        dev.tuffsmp.shrinecraft.item.SculkMaceItem.class, dev.tuffsmp.shrinecraft.item.CloudLanceItem.class,
        dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem.class);

    /** Animation state of one bar (kept between frames). */
    static final class Card {
        ItemStack stack = ItemStack.EMPTY;
        Abilities.Status status;
        float alpha;        // 0..1 fade
        float fill;         // displayed (eased) bar fill
        float x;            // displayed (eased) centre
        boolean visibleNow; // wanted this frame
        boolean placed;     // x has been initialised
    }

    private static final Map<Class<? extends Item>, Card> CARDS = new HashMap<>();
    private static long lastNanos = 0;
    /** How long each weapon's READY bar has been showing (resets as soon as the weapon is not READY or not held). */
    private static final Map<Class<? extends Item>, Float> READY_SHOWN = new HashMap<>();

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.of("shrinecraft", "ability_bars"), AbilityBarHud::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) {
            return;
        }
        long nowNanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016F : Math.min(0.1F, (nowNanos - lastNanos) / 1.0E9F);
        lastNanos = nowNanos;
        long now = mc.world.getTime();
        float partial = tickCounter.getTickProgress(false);

        // ---- 1. which weapons want a bar this frame ----
        for (Card c : CARDS.values()) {
            c.visibleNow = false;
        }
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();
        Set<Class<? extends Item>> seen = new HashSet<>();
        for (ItemStack stack : Abilities.candidates(mc.player)) {
            if (stack.isEmpty() || !Abilities.hasAbility(stack.getItem())) {
                continue;
            }
            Class<? extends Item> type = stack.getItem().getClass();
            if (!seen.add(type)) {
                continue;
            }
            Abilities.Status st = Abilities.status(stack, now);
            if (st == null) {
                continue;
            }
            boolean held = stack == main || stack == off;
            if (!held && (st.phase() == Abilities.Phase.READY || st.phase() == Abilities.Phase.OUT)) {
                READY_SHOWN.remove(type);
                continue; // weapons in the bag only get a bar while something is happening
            }
            if (st.phase() == Abilities.Phase.READY) {
                // READY is only a short "it is back" notice: show it READY_SHOW_SECONDS, then let it fade away
                float shown = READY_SHOWN.merge(type, dt, Float::sum);
                if (shown > READY_SHOW_SECONDS) {
                    continue;
                }
            } else {
                READY_SHOWN.remove(type);
            }
            Card c = CARDS.computeIfAbsent(type, k -> new Card());
            c.stack = stack;
            c.status = st;
            c.visibleNow = true;
        }

        // ---- 2. fade in / out, drop finished bars ----
        float fade = 1.0F - (float) Math.exp(-SMOOTH_FADE * dt);
        List<Card> row = new ArrayList<>();
        for (Class<? extends Item> type : ORDER) {
            Card c = CARDS.get(type);
            if (c == null) {
                continue;
            }
            c.alpha += ((c.visibleNow ? 1.0F : 0.0F) - c.alpha) * fade;
            if (!c.visibleNow && c.alpha < 0.03F) {
                CARDS.remove(type);
                continue;
            }
            row.add(c);
        }
        if (row.isEmpty()) {
            return;
        }

        // ---- 3. layout: one centred row; if the window is too narrow the bars shrink together ----
        int screenW = ctx.getScaledWindowWidth();
        int n = row.size();
        float rowW = n * BAR_WIDTH + (n - 1) * BAR_GAP;
        float scale = Math.min(1.0F, (screenW - 12) / rowW);
        float step = (BAR_WIDTH + BAR_GAP) * scale;
        float startCx = screenW / 2.0F - (n - 1) * step / 2.0F;
        int bossBars = ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).shrinecraft$getBossBars().size();
        int y = TOP_Y + bossBars * VANILLA_BOSS_BAR_HEIGHT;
        float slide = 1.0F - (float) Math.exp(-SMOOTH_SLIDE * dt);
        float barEase = 1.0F - (float) Math.exp(-SMOOTH_BAR * dt);
        for (int i = 0; i < n; i++) {
            Card c = row.get(i);
            float targetX = startCx + i * step;
            Abilities.Status st = c.status;
            if (!c.placed) {
                c.x = targetX;
                c.fill = st.fill();
                c.placed = true;
            }
            c.x += (targetX - c.x) * slide;
            // the bar glides to where the timer says it should be (extrapolated between ticks so it never steps)
            float target = st.fill();
            if (st.remainingTicks() > 0 && (st.phase() == Abilities.Phase.ACTIVE || st.phase() == Abilities.Phase.COOLDOWN)) {
                float perTick = st.phase() == Abilities.Phase.ACTIVE ? -st.fill() / st.remainingTicks() : (1.0F - st.fill()) / st.remainingTicks();
                target = Math.max(0.0F, Math.min(1.0F, st.fill() + perTick * partial));
            }
            c.fill += (target - c.fill) * barEase;
            drawBar(ctx, mc.textRenderer, c, c.x, y, scale, partial);
        }
    }

    /** One boss-style bar centred on cx: title (weapon name + counter) above, the tinted bar below. */
    private static void drawBar(DrawContext ctx, TextRenderer font, Card c, float cx, int y, float scale, float partial) {
        Abilities.Status st = c.status;
        float al = Math.max(0.0F, Math.min(1.0F, c.alpha));
        int a = Math.max(4, Math.round(al * 255));
        int color = st.color();

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, y + (1.0F - al) * -4.0F); // drifts down into place while fading in
        m.scale(scale, scale);

        // --- title, like a boss name: weapon name in white, then the counter in the weapon colour ---
        String counter = switch (st.phase()) {
            case READY -> "READY";
            case OUT -> "OUT";
            default -> formatSeconds(Math.max(0.0F, st.remainingTicks() - partial));
        };
        int counterColor = st.phase() == Abilities.Phase.READY ? READY_GREEN : st.phase() == Abilities.Phase.ACTIVE ? 0xFFFFFFFF : lighten(color, 0.3F);
        Text title = Text.empty()
            .append(st.title().copy().setStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(false).withItalic(false)))
            .append(Text.literal("  " + counter).setStyle(Style.EMPTY.withColor(counterColor & 0xFFFFFF)));
        int tw = font.getWidth(title);
        ctx.drawText(font, title, -tw / 2, -9, (a << 24) | 0xFFFFFF, true);

        // --- the bar: vanilla boss-bar sprites tinted to the weapon ---
        int half = BAR_WIDTH / 2;
        float dim = st.phase() == Abilities.Phase.COOLDOWN ? 0.72F : st.phase() == Abilities.Phase.OUT ? 0.55F : 1.0F;
        int progressTint = (a << 24) | boost(color, TINT_BOOST * dim);
        int backTint = (a << 24) | boost(color, 0.95F);
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND, -half, 0, BAR_WIDTH, BAR_HEIGHT, backTint);
        int fw = Math.round(BAR_WIDTH * Math.max(0.0F, Math.min(1.0F, c.fill)));
        if (fw > 0) {
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, PROGRESS, BAR_WIDTH, BAR_HEIGHT, 0, 0, -half, 0, fw, BAR_HEIGHT, progressTint);
        }
        m.popMatrix();
    }

    /** 47s, 7.3s, 12:05 - whole seconds above 10, one decimal below, minutes:seconds from one minute up. */
    public static String formatSeconds(float ticks) {
        float s = Math.max(0.0F, ticks) / 20.0F;
        if (s >= 60.0F) {
            int total = (int) Math.ceil(s);
            return (total / 60) + ":" + String.format("%02d", total % 60);
        }
        if (s >= 10.0F) {
            return (int) Math.ceil(s) + "s";
        }
        return String.format("%.1fs", s);
    }

    /** RGB (no alpha) of the colour times a brightness factor, clamped. */
    private static int boost(int argb, float f) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((argb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int lighten(int argb, float f) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (int) ((255 - r) * f);
        g += (int) ((255 - g) * f);
        b += (int) ((255 - b) * f);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
}
