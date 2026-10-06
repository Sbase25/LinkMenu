package linkmenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LinkMenuMod implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("LinkMenu");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_CONFIG = """
            {
              "Website": "https://domain.com",
              "Discord": "https://discord.gg/invite"
            }
            """;

    private static volatile List<ServerLinks.Entry> customLinks = List.of();

    @Override
    public void onInitializeServer() {
        loadLinks();

        // Same behaviour as the Bukkit plugin: send the link list when a player joins.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendLinks(server, handler));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("linkmenu")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(ctx -> {
                            ctx.getSource().sendFailure(Component.literal("Usage: /linkmenu reload"));
                            return 0;
                        })
                        .then(Commands.literal("reload").executes(ctx -> {
                            loadLinks();
                            MinecraftServer server = ctx.getSource().getServer();
                            // Unlike the Bukkit version, online players get the new list immediately.
                            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                sendLinks(server, player.connection);
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("LinkMenu links have been reloaded!").withStyle(ChatFormatting.YELLOW),
                                    true);
                            return 1;
                        }))));

        LOGGER.info("LinkMenu enabled");
    }

    private static void sendLinks(MinecraftServer server, ServerGamePacketListenerImpl handler) {
        List<ServerLinks.Entry> all = new ArrayList<>(server.serverLinks().entries());
        all.addAll(customLinks);
        handler.send(new ClientboundServerLinksPacket(new ServerLinks(all).untrust()));
    }

    private static void loadLinks() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("linkmenu.json");
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_CONFIG);
            }
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            List<ServerLinks.Entry> loaded = new ArrayList<>();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                try {
                    URI uri = new URI(e.getValue().getAsString());
                    loaded.add(ServerLinks.Entry.custom(parseLegacy(e.getKey()), uri));
                } catch (URISyntaxException | RuntimeException ex) {
                    LOGGER.warn("Invalid link for key {}: {}", e.getKey(), e.getValue());
                }
            }
            customLinks = List.copyOf(loaded);
            LOGGER.info("Loaded {} links", loaded.size());
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Could not read {}", file, ex);
        }
    }

    /** Converts legacy colour codes (&c or §c) in a label into a styled Component. */
    private static Component parseLegacy(String input) {
        MutableComponent root = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(input.charAt(i + 1)));
                if (f != null) {
                    if (!sb.isEmpty()) {
                        root.append(Component.literal(sb.toString()).withStyle(style));
                        sb.setLength(0);
                    }
                    if (f == ChatFormatting.RESET) style = Style.EMPTY;
                    else if (Character.digit(input.charAt(i + 1), 16) >= 0) style = Style.EMPTY.withColor(f);
                    else style = style.applyFormat(f);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        if (!sb.isEmpty()) root.append(Component.literal(sb.toString()).withStyle(style));
        return root;
    }
}
