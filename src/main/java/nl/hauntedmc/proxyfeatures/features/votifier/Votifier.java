package nl.hauntedmc.proxyfeatures.features.votifier;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.MessageRegistry;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.votifier.command.VotifierCommand;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteMonthlyEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteStatsEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.VotifierRolloverStateEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.internal.VotifierService;
import nl.hauntedmc.proxyfeatures.features.votifier.listener.VotifierPlayerListener;
import nl.hauntedmc.proxyfeatures.features.votifier.messaging.VoteMessage;
import nl.hauntedmc.proxyfeatures.features.votifier.meta.Meta;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class Votifier extends VelocityBaseFeature<Meta> {

    private static final AtomicBoolean MESSAGE_TYPE_REGISTERED = new AtomicBoolean(false);

    private volatile VotifierService service;

    public Votifier(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();

        cfg.put("enabled", false);

        // Network
        cfg.put("host", "0.0.0.0");
        cfg.put("port", 8249);
        cfg.put("readTimeoutMillis", 5000);
        cfg.put("backlog", 50);

        // Security and files under <dataDir>/local/<file_name>
        cfg.put("generateKeys", true);
        cfg.put("keyBits", 2048);
        cfg.put("publicKeyFile", "public.key");
        cfg.put("privateKeyFile", "private.key");

        // Inbound IP filtering (CSV string). Empty allowlist means allow all unless denied.
        cfg.put("allowlist", "");
        cfg.put("denylist", "");

        // Safety
        cfg.put("maxPacketBytes", 8192);

        // Redis publish settings
        cfg.put("redis.enabled", true);
        cfg.put("redis.channel", "proxy.votifier.vote");
        cfg.put("redis.publish_legacy_channel", true);
        cfg.put("redis.legacy_channel", "vote");

        // Vote stats and rollover
        cfg.put("stats.enabled", true);
        cfg.put("stats.timezone", "Europe/Amsterdam");
        cfg.put("stats.reset_check_minutes", 5);
        cfg.put("stats.streak_gap_hours", 36);
        cfg.put("stats.dump_top_n", 100);
        cfg.put("stats.dump_file_prefix", "votifier-top100");

        // Player command link
        // Shows on: /vote and /vote links
        cfg.put("vote.url", "https://hauntedmc.nl/vote");

        // /vote leaderboard link
        cfg.put("vote.leaderboard_url", "https://www.hauntedmc.nl/leaderboard/votes/");

        // Vote reminders
        cfg.put("remind.enabled", true);
        cfg.put("remind.threshold_hours", 24);
        cfg.put("remind.initial_delay_minutes", 1);
        cfg.put("remind.interval_minutes", 60);

        // Logging
        cfg.put("logging.log_votes", true);

        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        m.add("votifier.command.usage",
                "&7Gebruik: &f/vote &7<status|top|dump|stats|winners|remind|links|leaderboard|test>");
        m.add("votifier.command.status",
                "&7[&aVotifier&7] status=&f{status}&7 host=&f{host}&7 port=&f{port}&7 timeout=&f{timeout}ms&7 keyBits=&f{keybits}&7 redis=&f{redis}&7 db=&f{db}");

        m.add("votifier.command.top.header",
                "&7[&aVote&7] Top &f{limit}&7 voor maand &f{month}&7:");
        m.add("votifier.command.top.entry",
                "  &8#&f{rank}&8: &f{player} &8| &e{votes} &fvotes");
        m.add("votifier.command.top.empty",
                "&7[&aVote&7] &7Geen votes gevonden voor maand &f{month}&7.");

        m.add("votifier.command.dump.ok",
                "&7[&aVotifier&7] &aDump geschreven: &f{file}");
        m.add("votifier.command.dump.fail",
                "&7[&aVotifier&7] &cDump mislukt: &f{error}");

        // Stats
        m.add("votifier.command.stats.header",
                "&7[&aVote&7] Stats van &f{player}&7:");
        m.add("votifier.command.stats.line1",
                "  &8• &7Maand votes: &f{month_votes} &8| &7Beste maand: &f{best_month_votes} &8| &7Totaal: &f{total_votes}");
        m.add("votifier.command.stats.line2",
                "  &8• &7Streak: &f{streak} &8| &7Beste streak: &f{best_streak}");
        m.add("votifier.command.stats.not_found",
                "&7[&aVote&7] &cSpeler niet gevonden: &f{player}");
        m.add("votifier.command.stats.player_only",
                "&7[&aVote&7] &cAlleen spelers kunnen dit zonder speler argument gebruiken.");

        // Winners medal ranking
        m.add("votifier.command.winners.header",
                "&7[&aVote&7] Winners ranking (medals) &8| &fmax {limit}");
        m.add("votifier.command.winners.entry",
                "  &8#&f{rank}&8: &f{player} &8| &6{gold}&7 &8/ &7{silver}&7 &8/ &c{bronze}");
        m.add("votifier.command.winners.empty",
                "&7[&aVote&7] &7Nog geen winners gevonden.");

        // Winner congrats (one time per winning month)
        m.add("votifier.winner.congrats",
                "&8[&aVote&8] &aGefeliciteerd&7! Je eindigde als &f#{rank}&7 in de vote top 3 van &f{month}&7 met &e{votes}&7 votes.");

        // Participant monthly result (non top 3)
        m.add("votifier.month.result",
                "&8[&aVote&8] &7Je eindigde op plek &f#{rank}&7 in &f{month}&7 met &e{votes}&7 votes.");

        // Vote reminders
        m.add("votifier.remind.header",
                "&8[&aVote&8] &eHet is alweer &f{time} {unit}&e sinds je laatste vote. &eStem voor leuke rewards en een plek in de top-3!");

        m.add("votifier.remind.never",
                "&8[&aVote&8] &eJe hebt nog niet gevote. &eStem voor leuke rewards en een plek in de top-3!");

        m.add("votifier.remind.line",
                "  &8• &7Klik om te stemmen: {url}");

        // Units for reminders (translatable)
        m.add("votifier.remind.unit.hour", "uur");
        m.add("votifier.remind.unit.hours", "uur");
        m.add("votifier.remind.unit.day", "dag");
        m.add("votifier.remind.unit.days", "dagen");

        m.add("votifier.command.remind.usage",
                "&7Gebruik: &f/vote remind &7<on|off|toggle>");
        m.add("votifier.command.remind.player_only",
                "&8[&aVote&8] &cAlleen spelers kunnen dit gebruiken.");
        m.add("votifier.command.remind.unavailable",
                "&8[&aVote&8] &cVote herinneringen zijn nu niet beschikbaar.");

        // Status line + translatable status words
        m.add("votifier.command.remind.status.current",
                "&8[&aVote&8] &7Vote herinneringen zijn {status}&7.");
        m.add("votifier.command.remind.status.enabled",
                "&aingeschakeld");
        m.add("votifier.command.remind.status.disabled",
                "&cuitgeschakeld");

        m.add("votifier.command.remind.enabled",
                "&8[&aVote&8] &7Vote herinneringen zijn nu &aingeschakeld&7.");
        m.add("votifier.command.remind.disabled",
                "&8[&aVote&8] &7Vote herinneringen zijn nu &cuitgeschakeld&7.");

        // /vote link output
        m.add("votifier.vote.header",
                "&8[&aVote&8] &7Stem op &fHauntedMC&7:");
        m.add("votifier.vote.line",
                "  &8• &7Klik om te openen: {url}");
        m.add("votifier.vote.not_configured",
                "&8[&aVote&8] &cVote link is niet ingesteld.");

        // /vote leaderboard output
        m.add("votifier.vote.leaderboard.header",
                "&8[&aVote&8] &7Live leaderboard:");
        m.add("votifier.vote.leaderboard.not_configured",
                "&8[&aVote&8] &cLeaderboard link is niet ingesteld.");

        // Test vote
        m.add("votifier.command.test.usage",
                "&7Gebruik: &f/vote test &7<service> <player>");
        m.add("votifier.command.test.ok",
                "&7[&aVotifier&7] &aTest vote gepubliceerd&7 service=&f{service}&7 player=&f{player}");
        m.add("votifier.command.test.unavailable",
                "&7[&aVotifier&7] &cTest vote niet beschikbaar (redis publish staat uit).");

        return m;
    }

    @Override
    public void initialize() {
        // Commands
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new VotifierCommand(this));

        // Listener: month results and reminders
        getLifecycleManager().getListenerManager().registerListener(new VotifierPlayerListener(this));

        // Data provider init
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());

        // Redis messaging (optional)
        Optional<DatabaseProvider> redisOpt = getLifecycleManager()
                .getDataManager()
                .registerConnection("redis", DatabaseType.REDIS_MESSAGING, "default");

        MessagingDataAccess redisBus = null;
        if (redisOpt.isPresent()) {
            try {
                redisBus = (MessagingDataAccess) redisOpt.get().getDataAccess();
            } catch (ClassCastException e) {
                getLogger().warn("Redis DataAccess is not MessagingDataAccess, vote publish disabled.");
            }
        } else {
            getLogger().warn("Redis messaging provider not available, vote publish disabled.");
        }

        // ORM for player lookup and vote stats (optional but recommended)
        Optional<DatabaseProvider> ormOpt = getLifecycleManager()
                .getDataManager()
                .registerConnection("orm", DatabaseType.MYSQL, "player_data_rw");

        ORMContext orm = null;
        if (ormOpt.isPresent()) {
            orm = getLifecycleManager().getDataManager()
                    .createORMContext(
                            "orm",
                            PlayerEntity.class,
                            PlayerVoteStatsEntity.class,
                            PlayerVoteMonthlyEntity.class,
                            VotifierRolloverStateEntity.class
                    )
                    .orElse(null);
            if (orm == null) {
                getLogger().warn("Failed to create ORMContext, vote stats disabled.");
            }
        } else {
            getLogger().warn("MySQL provider not available, vote stats disabled.");
        }

        // Register message type once
        if (MESSAGE_TYPE_REGISTERED.compareAndSet(false, true)) {
            try {
                MessageRegistry.register("votifier", VoteMessage.class);
            } catch (Throwable t) {
                getLogger().warn("Failed to register VoteMessage type: " + t.getMessage());
            }
        }

        // Service orchestration
        VotifierService svc = new VotifierService(this, redisBus, orm);
        this.service = svc;
        svc.start();
    }

    @Override
    public void disable() {
        VotifierService svc = this.service;
        this.service = null;
        if (svc != null) {
            svc.shutdown();
        }
    }

    public VotifierService getService() {
        return service;
    }

    public boolean isRunning() {
        VotifierService svc = service;
        return svc != null && svc.isRunning();
    }

    public String currentHost() {
        VotifierService svc = service;
        return svc != null ? svc.currentHost() : String.valueOf(getConfigHandler().get("host"));
    }

    public int currentPort() {
        VotifierService svc = service;
        return svc != null ? svc.currentPort() : (int) getConfigHandler().get("port");
    }

    public int currentTimeoutMs() {
        return (int) getConfigHandler().get("readTimeoutMillis");
    }

    public int currentKeyBits() {
        return (int) getConfigHandler().get("keyBits");
    }
}