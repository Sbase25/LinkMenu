# LinkMenu

A server-side Fabric mod that adds your own links (website, Discord, store, ...) to the
**Server Links** menu in the pause screen. Works with vanilla clients.

- Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API, Java 25
- Server-side only: players do not need to install anything

## Config

`config/linkmenu.json` is created on first start. The key is the label, the value is the link:

```json
{
  "Website": "https://example.com",
  "&9Discord": "https://discord.gg/invite"
}
```

Labels support `&` / `§` colour and formatting codes (for example `&c`, `&l`, `&r`).
Links must be valid URLs starting with `https://`.

## Commands

- `/linkmenu reload` (operators) – reloads the config and sends the new links to online players.

## Building

```
gradle wrapper --gradle-version 9.5.1
./gradlew build
```

The jar is written to `build/libs/linkmenu-<version>.jar`.

## License

MIT, see [LICENSE](LICENSE).
