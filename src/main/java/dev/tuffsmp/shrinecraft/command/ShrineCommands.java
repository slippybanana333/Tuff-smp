package dev.tuffsmp.shrinecraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.ability.TrustManager;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipe;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipes;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * All the mod's server commands, under /tuffsmp. Only three have a SHORT form (no /tuff root, no /tuffgive, no /tuffreload - removed on request):
 *
 *   EVERYONE
 *     /tuffsmp trust <player>        =  /trust <player>      toggle: your weapon abilities no longer hurt that player
 *     /tuffsmp trust list            =  /trust list          who you trust
 *     /tuffsmp gui <theme|auto|off|where|reload>  =  /gui ...   (CLIENT side, see client/gui/GuiCommand.java; listed here for tab-completion)
 *   OPS (permission level 2)
 *     /tuffsmp give <player> <recipe_id>                        hand out a finished item (fully enchanted)
 *     /tuffsmp essence <player> [amount]   =  /essence ...      hand out Magic Essence
 *     /tuffsmp reload                                           re-read config/shrinecraft/shrine_recipes.json
 *     /tuffsmp selftest                                          the built-in checks (developer tool)
 *   (the old "recipes" and "reset" commands were removed on request - SHRINE_RECIPES.md lists the recipes)
 */
public final class ShrineCommands {
    private ShrineCommands() {}

    /** Admin commands need permission level 2 (op / command block); trust and gui are for EVERYONE. */
    private static final Predicate<ServerCommandSource> ADMIN = CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK);

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("tuffsmp")
                .then(trust("trust"))
                .then(give("give"))
                .then(essence("essence"))
                .then(reload("reload"))
                .then(CommandManager.literal("selftest").requires(ADMIN).executes(ctx -> SelfTest.run(ctx.getSource())))
                .then(guiHint()));
            // the only short forms: /trust and /essence (/gui is client side)
            dispatcher.register(trust("trust"));
            dispatcher.register(essence("essence"));
        });

        // DEV helper: put an empty file named "selftest-on" in the server's run directory (or start with
        // -Dtuffsmp.selftest=1) and the built-in self-test runs automatically a few seconds after the world
        // loads (the bash server_selftest.sh types the console commands for you; the file / flag exists so the
        // same test also works on Windows / from IDE run configs without console typing). The file is a
        // one-shot: it is deleted when read.
        boolean auto = Boolean.getBoolean("tuffsmp.selftest");
        java.nio.file.Path flagFile = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("selftest-on");
        if (!auto) {
            try {
                auto = java.nio.file.Files.deleteIfExists(flagFile);
            } catch (java.io.IOException e) {
                ShrineCraft.LOGGER.warn("could not read the selftest-on flag file: {}", e.toString());
            }
        }
        if (auto) {
            SelfTest.registerAutoRun();
            ShrineCraft.LOGGER.info("self-test auto-run armed (selftest-on file / -Dtuffsmp.selftest=1).");
        }
    }

    // ------------------------------------------------------------------ trust (everyone)

    private static LiteralArgumentBuilder<ServerCommandSource> trust(String name) {
        return CommandManager.literal(name)
            .then(CommandManager.literal("list").executes(ctx -> {
                ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
                ArrayList<String> names = new ArrayList<>();
                for (UUID u : TrustManager.trustedBy(me.getUuid())) {
                    ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(u);
                    names.add(online != null ? online.getName().getString() : u.toString().substring(0, 8) + "...");
                }
                ctx.getSource().sendFeedback(() -> Text.literal(names.isEmpty() ? "You trust nobody: your weapon abilities can hurt everyone."
                    : "Your weapon abilities spare: " + String.join(", ", names)).formatted(Formatting.AQUA), false);
                return names.size();
            }))
            .then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
                ServerPlayerEntity other = EntityArgumentType.getPlayer(ctx, "player");
                if (other == me) {
                    ctx.getSource().sendError(Text.literal("You can't trust yourself - your abilities never hurt you anyway."));
                    return 0;
                }
                boolean now = TrustManager.toggle(me.getUuid(), other.getUuid());
                String who = other.getName().getString();
                ctx.getSource().sendFeedback(() -> Text.literal(now
                    ? "You now TRUST " + who + ": your weapon abilities will not hurt them. (Run it again to undo.)"
                    : "You no longer trust " + who + ": your weapon abilities can hurt them again.").formatted(now ? Formatting.GREEN : Formatting.YELLOW), false);
                other.sendMessage(Text.literal(me.getName().getString() + (now ? " now trusts you: their weapon abilities won't hurt you." : " no longer trusts you.")).formatted(Formatting.GRAY), false);
                return 1;
            }));
    }

    // ------------------------------------------------------------------ admin

    private static LiteralArgumentBuilder<ServerCommandSource> give(String name) {
        return CommandManager.literal(name).requires(ADMIN)
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .then(CommandManager.argument("recipe_id", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        ShrineRecipes.all().forEach(r -> builder.suggest(r.id()));
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                        ShrineRecipe recipe = ShrineRecipes.byId(StringArgumentType.getString(ctx, "recipe_id"));
                        if (recipe == null) {
                            ctx.getSource().sendError(Text.literal("Unknown recipe id (press Tab after the player name to see them all)."));
                            return 0;
                        }
                        target.getInventory().offerOrDrop(recipe.createOutput(ctx.getSource().getRegistryManager()));
                        ctx.getSource().sendFeedback(() -> Text.literal("Gave " + recipe.displayName() + " to " + target.getName().getString()), true);
                        return 1;
                    })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> essence(String name) {
        return CommandManager.literal(name).requires(ADMIN)
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> giveEssence(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), 1))
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 2304))
                    .executes(ctx -> giveEssence(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "amount")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> reload(String name) {
        return CommandManager.literal(name).requires(ADMIN).executes(ctx -> {
            String result = ShrineRecipes.reload();
            ctx.getSource().sendFeedback(() -> Text.literal(result).formatted(Formatting.GREEN), true);
            return 1;
        });
    }

    /** "/tuffsmp gui ..." is handled on the CLIENT (client/gui/GuiCommand.java catches the typed text before it is sent). This only feeds tab-completion. */
    private static LiteralArgumentBuilder<ServerCommandSource> guiHint() {
        LiteralArgumentBuilder<ServerCommandSource> gui = CommandManager.literal("gui").executes(ctx -> {
            ctx.getSource().sendFeedback(() -> Text.literal("/tuffsmp gui <ocean|lava|sculk|arctic|end|off> needs the Tuff SMP mod on your own client.").formatted(Formatting.YELLOW), false);
            return 0;
        });
        for (String w : new String[] {"off", "ocean", "lava", "sculk", "arctic", "end"}) {
            gui.then(CommandManager.literal(w).executes(ctx -> 0));
        }
        return gui;
    }

    /** Gives <amount> Magic Essence (split into stacks; whatever does not fit is dropped at the player's feet). */
    private static int giveEssence(ServerCommandSource source, ServerPlayerEntity target, int amount) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, dev.tuffsmp.shrinecraft.item.MagicEssenceItem.MAX_STACK);
            target.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.MAGIC_ESSENCE, n));
            left -= n;
        }
        source.sendFeedback(() -> Text.literal("Gave " + amount + " Magic Essence to " + target.getName().getString()), true);
        return amount;
    }
}
