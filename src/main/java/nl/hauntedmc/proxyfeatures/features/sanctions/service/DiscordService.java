package nl.hauntedmc.proxyfeatures.features.sanctions.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.api.util.http.DiscordUtils;
import nl.hauntedmc.proxyfeatures.api.util.parse.JsonUtils;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;

import java.time.Instant;
import java.util.Map;

public class DiscordService {

    private final Sanctions feature;

    public DiscordService(Sanctions feature) {
        this.feature = feature;
    }

    /* =========================  Public API  ========================= */

    public void sendBan(SanctionEntity s) {
        Map<String,String> ph = feature.getService().placeholdersFor(s);
        sendEmbed(
                "Ban",
                15158332, // red
                field("Speler", ph.get("target")),
                field("Duur",   ph.get("duration")),
                field("Reden",  ph.get("reason")),
                field("Door",   ph.get("actor"))
        );
    }

    public void sendUnban(PlayerEntity target, String actorName) {
        sendEmbed(
                "Unban",
                3066993, // green
                field("Speler", safeName(target)),
                field("Door",   actorName)
        );
    }

    public void sendMute(SanctionEntity s) {
        Map<String,String> ph = feature.getService().placeholdersFor(s);
        sendEmbed(
                "Mute",
                15158332, // red
                field("Speler", ph.get("target")),
                field("Duur",   ph.get("duration")),
                field("Reden",  ph.get("reason")),
                field("Door",   ph.get("actor"))
        );
    }

    public void sendUnmute(PlayerEntity target, String actorName) {
        sendEmbed(
                "Unmute",
                3066993, // green
                field("Speler", safeName(target)),
                field("Door",   actorName)
        );
    }

    public void sendWarn(PlayerEntity target, String reason, String actorName) {
        sendEmbed(
                "Warn",
                15844367, // yellow
                field("Speler", safeName(target)),
                field("Reden",  nullToDash(reason)),
                field("Door",   actorName)
        );
    }

    public void sendKick(PlayerEntity target, String reason, String actorName) {
        sendEmbed(
                "Kick",
                16733525, // red-ish
                field("Speler", safeName(target)),
                field("Reden",  nullToDash(reason)),
                field("Door",   actorName)
        );
    }

    /* =========================  Internals  ========================= */

    /**
     * Sends an embed with fixed title "Sanctie Melding" and the first field "Sanctie Type".
     *
     * @param sanctionType   e.g. "Ban", "IP-Ban", "Unban", "Mute", "Warn", "Kick"
     * @param color          embed color (decimal RGB int)
     * @param fieldJsonParts additional field JSON snippets (already formatted)
     */
    private void sendEmbed(String sanctionType, int color, String... fieldJsonParts) {
        String webhookUrl = getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            feature.getLogger().warn("[Sanctions/Discord] Discord webhook URL not configured.");
            return;
        }

        String timestamp = Instant.now().toString();

        // Prepend "Sanctie Type" as the first field
        String[] finalFields = new String[(fieldJsonParts == null ? 0 : fieldJsonParts.length) + 1];
        finalFields[0] = field("Sanctie Type", sanctionType);
        if (fieldJsonParts != null && fieldJsonParts.length > 0) {
            System.arraycopy(fieldJsonParts, 0, finalFields, 1, fieldJsonParts.length);
        }

        String fieldsJoined = joinWithCommas(finalFields);

        String payload =
                "{"
                        + "\"embeds\":[{"
                        + "  \"title\":\"" + json("Sanctie Melding") + "\","
                        + "  \"description\":\"" + json("Er is een nieuwe sanctie geregistreerd.") + "\","
                        + "  \"color\":" + color + ","
                        + "  \"author\":{"
                        + "     \"name\":\"HauntedMC\","
                        + "     \"icon_url\":\"https://hauntedmc.nl/HauntedLog.png\""
                        + "  },"
                        + "  \"fields\":[" + fieldsJoined + "],"
                        + "  \"timestamp\":\"" + timestamp + "\","
                        + "  \"footer\":{"
                        + "     \"text\":\"HauntedMC Sanctions " + json(feature.getFeatureVersion()) + "\","
                        + "     \"icon_url\":\"https://hauntedmc.nl/HauntedLog.png\""
                        + "  }"
                        + "}]"
                        + "}";

        DiscordUtils.sendPayload(webhookUrl, payload);
    }

    private String field(String name, String value) {
        return "{"
                + "\"name\":\"" + json(name) + "\","
                + "\"value\":\"" + json(nullToDash(value)) + "\","
                + "\"inline\":true"
                + "}";
    }

    private String joinWithCommas(String[] parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (!first) sb.append(',');
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }

    private String json(String s) {
        return JsonUtils.escapeJson(s == null ? "" : s);
    }

    private String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private String safeName(PlayerEntity p) {
        if (p == null) return "-";
        return nullToDash(p.getUsername());
    }

    private String getWebhookUrl() {
        try {
            Object v = feature.getConfigHandler().getSetting("discordWebhookURL");
            return v == null ? "" : String.valueOf(v);
        } catch (Throwable ignored) {}
        return "";
    }
}
