package to.itsme.itsmyconfig.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import to.itsme.itsmyconfig.ItsMyConfig;
import to.itsme.itsmyconfig.font.MappedFont;
import to.itsme.itsmyconfig.placeholder.Placeholder;
import to.itsme.itsmyconfig.placeholder.PlaceholderType;
import to.itsme.itsmyconfig.util.Strings;
import to.itsme.itsmyconfig.util.Utilities;

/**
 * DynamicPlaceHolder class is a PlaceholderExpansion that handles dynamic placeholders for the ItsMyConfig plugin.
 * It provides methods for handling various types of placeholders, such as fonts, progress bars, and custom placeholders.
 * This class extends the PlaceholderExpansion class.
 */
public final class PAPIHook extends PlaceholderExpansion {

    /**
     * This variable is an instance of the ItsMyConfig class.
     */
    private final ItsMyConfig plugin;
    /**
     * ILLEGAL_NUMBER_FORMAT_MSG represents the error message when an illegal number format is encountered.
     */
    public static final String ILLEGAL_NUMBER_FORMAT_MSG = "Illegal Number Format";
    /**
     * ILLEGAL_ARGUMENT_MSG represents a string constant that indicates an illegal argument has been provided.
     * This constant is used in various methods in the DynamicPlaceHolder class.
     */
    public static final String ILLEGAL_ARGUMENT_MSG = "Illegal Argument";
    /**
     * PLACEHOLDER_NOT_FOUND_MSG is a constant variable that represents the message displayed when a placeholder is not found.
     */
    public static final String PLACEHOLDER_NOT_FOUND_MSG = "Placeholder not found";
    private final String identifier;

    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer
            .builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();

    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer
            .builder()
            .character('§')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * DynamicPlaceHolder is a class that represents a dynamic placeholder for a placeholder expansion.
     * It handles different types of placeholders and provides methods to handle font, progress, and custom placeholders.
     */
    public PAPIHook(final ItsMyConfig plugin, final String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    /**
     * Returns the identifier for this object.
     *
     * @return the identifier for this object
     */
    @Override
    public @NotNull String getIdentifier() {
        return this.identifier;
    }

    /**
     * Retrieves the author(s) of the plugin.
     *
     * @return The author(s) of the plugin as a string. If there are multiple authors,
     *         they are joined by commas.
     */
    @Override
    @SuppressWarnings("deprecation")
    public @NotNull String getAuthor() {
        return String.join(", ", this.plugin.getDescription().getAuthors());
    }

    /**
     * Retrieves the version of the plugin.
     *
     * @return The version of the plugin.
     */
    @Override
    @SuppressWarnings("deprecation")
    public @NotNull String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    /**
     * This method is used to persist data.
     *
     * @return true if the data is successfully persisted, false otherwise.
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * This method handles placeholder requests for the DynamicPlaceHolder expansion.
     * It replaces placeholders in the given params string with actual values and returns the result.
     *
     * @param player The player for whom the placeholder is being requested.
     * @param params The placeholder parameters string.
     * @return The result of the placeholder request.
     */
    @Override
    public @Nullable String onPlaceholderRequest(final Player player, @NotNull String params) {
        params = PlaceholderAPI.setPlaceholders(player, params.replaceAll("\\$\\((.*?)\\)\\$", "%$1%"));
        params = PlaceholderAPI.setBracketPlaceholders(player, params);

        final String[] splitParams = params.split("_");
        if (splitParams.length == 0) {
            return ILLEGAL_ARGUMENT_MSG;
        }

        final String firstParam = splitParams[0].toLowerCase();

        // %imc_parse_<...>[_mode]% where mode in [mini|legacy|console]
        if ("parse".equals(firstParam)) {
            if (splitParams.length < 2) {
                return ILLEGAL_ARGUMENT_MSG;
            }

            final String last = splitParams[splitParams.length - 1].toLowerCase();
            final boolean hasMode = switch (last) {
                case "c", "console", "l", "legacy", "m", "mini" -> true;
                default -> false;
            };

            final String mode = hasMode ? last : "legacy";
            final int endExclusive = hasMode ? splitParams.length - 1 : splitParams.length;
            final StringBuilder msgBuilder = new StringBuilder();
            for (int i = 1; i < endExclusive; i++) {
                if (i > 1) msgBuilder.append('_');
                msgBuilder.append(splitParams[i]);
            }
            final String message = msgBuilder.toString();

            // Choose a player context if available; for console, fallback to first online player when present
            final Player context = player != null ? player : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);

            // Resolve <papi:...> eagerly as %...% for better compatibility with non-MM placeholders
            String papiResolved = message.replaceAll("<papi:([^>]+)>", "%$1%");
            try {
                papiResolved = (context != null)
                        ? PlaceholderAPI.setPlaceholders(context, papiResolved)
                        : PlaceholderAPI.setPlaceholders(null, papiResolved);
            } catch (Throwable ignored) {
                // keep original if PAPI resolution fails
            }

            final Component component = (context != null)
                    ? Utilities.translate(papiResolved, context)
                    : Utilities.translate(papiResolved);

            return switch (mode) {
                case "c", "console" -> SECTION_SERIALIZER.serialize(component);
                case "m", "mini" -> Utilities.MM.serialize(component);
                case "l", "legacy" -> AMPERSAND_SERIALIZER.serialize(component);
                default -> AMPERSAND_SERIALIZER.serialize(component);
            };
        }
        if (("font".equals(firstParam) || "f".equals(firstParam)) && splitParams.length >= 3) {
            return handleFont(splitParams);
        }
        return handlePlaceholder(splitParams, player);
    }

    /**
     * Handles font-related operations based on the given parameters.
     *
     * @param splitParams The array of parameters, where the font type is at index 1 and additional parameters are at subsequent indices.
     * @return The processed font or an error message if the font type is unknown or if an error occurs during font processing.
     */
    private String handleFont(final String[] splitParams) {
        String fontType = splitParams[1].toLowerCase();
        if ("latin".equals(fontType)) {
            try {
                int integer = Integer.parseInt(splitParams[2]);
                return Strings.integerToRoman(integer);
            } catch (NumberFormatException e) {
                return ILLEGAL_NUMBER_FORMAT_MSG;
            }
        } else if ("smallcaps".equals(fontType)) {
            String message = splitParams[2].toLowerCase();
            return MappedFont.SMALL_CAPS.apply(message);
        }
        return "ERROR";
    }

    /**
     * Handles the placeholder based on the params and player.
     *
     * @param params The array of split parameters.
     * @param player      The player object.
     * @return The formatted string.
     */
    private String handlePlaceholder(
            final String[] params,
            final Player player
    ) {
        final Placeholder placeholder = plugin.getPlaceholderManager().get(params[0]);
        if (placeholder == null) {
            return PLACEHOLDER_NOT_FOUND_MSG;
        }

        if (params.length == 1) {
            return placeholder.asString(player, new String[0]);
        }

        final String firstParam = params[1];
        if (params.length == 2) {
            return placeholder.asString(player, firstParam.split("::"));
        }

        final StringBuilder builder = new StringBuilder();
        builder.append(firstParam);

        final PlaceholderType type = placeholder.getType();
        switch (type) {
            case COLOR:
            case COLORED_TEXT:
                switch (firstParam.toLowerCase()) {
                    case "m":
                    case "l":
                    case "c":
                    case "mini":
                    case "legacy":
                    case "console":
                        builder.append("::");
                        break;
                    default:
                        builder.append("_");
                        break;
                }
                break;
            case MATH:
                final String lower = firstParam.toLowerCase();
                if (lower.endsWith("dp")) {
                    builder.append("::");
                    break;
                } else {
                    switch (lower) {
                        case "commas":
                        case "fixed":
                        case "formatted":
                            builder.append("::");
                            break;
                        default:
                            builder.append("_");
                            break;
                    }
                }
                break;
            default:
                builder.append("_");
                break;
        }

        for (int i = 2; i < params.length; i++) {
            builder.append(params[i]);
            if (i < params.length - 1) {
                builder.append("_");
            }
        }

        return placeholder.asString(player, builder.toString().split(type == PlaceholderType.PROGRESS_BAR ? "_" : "::"));
    }

}