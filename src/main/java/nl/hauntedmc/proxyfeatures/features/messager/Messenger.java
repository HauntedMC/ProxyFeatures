package nl.hauntedmc.proxyfeatures.features.messager;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.messager.command.ReplyCommand;
import nl.hauntedmc.proxyfeatures.features.messager.internal.MessagingHandler;
import nl.hauntedmc.proxyfeatures.features.messager.command.MessagingCommand;
import nl.hauntedmc.proxyfeatures.features.messager.listener.PlayerListener;
import nl.hauntedmc.proxyfeatures.features.messager.meta.Meta;

public class Messenger extends VelocityBaseFeature<Meta> {

    private MessagingHandler handler;

    public Messenger(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("message.self", "&8&l[&6&lMSG&8&l] &r&cJe kunt jezelf geen bericht sturen.");
        m.add("message.offline", "&8&l[&6&lMSG&8&l] &r&cDeze speler is offline.");
        m.add("message.blocked", "&8&l[&6&lMSG&8&l] &r&cJe kunt deze speler geen bericht sturen.");
        m.add("message.disabled.receiver", "&8&l[&6&lMSG&8&l] &r&cDeze speler heeft berichten uit staan.");
        m.add("message.disabled.sender", "&8&l[&6&lMSG&8&l] &r&cJe moet hiervoor het sturen van berichten aanzetten met /msg toggle");
        m.add("message.format.from", "&8&l[&6&lMSG&8&l] &r&7[{sender_server}] &6{sender} &8➜ &6Jij: &d{message}");
        m.add("message.format.to", "&8&l[&6&lMSG&8&l] &6Jij &8➜ &7[{receiver_server}] &6{receiver}: &d{message}");
        m.add("message.format.spy", "&7[&cSpy&7] &7{sender} &8➜ &7{receiver}: {message}");
        m.add("message.block.already", "&8&l[&6&lMSG&8&l] &r&cJe hebt {player} al geblokkeerd.");
        m.add("message.block.success", "&8&l[&6&lMSG&8&l] &r&aJe hebt {player} geblokkeerd.");
        m.add("message.unblock.success", "&8&l[&6&lMSG&8&l] &r&aJe hebt {player} gedeblokkeerd.");
        m.add("message.unblock.not_blocked", "&8&l[&6&lMSG&8&l] &r&cJe hebt {player} niet geblokkeerd.");
        m.add("message.toggle.enabled", "&8&l[&6&lMSG&8&l] &r&aBerichten zijn nu ingeschakeld.");
        m.add("message.toggle.disabled", "&8&l[&6&lMSG&8&l] &r&cBerichten zijn nu uitgeschakeld.");
        m.add("message.spy.enabled", "&8&l[&6&lMSG&8&l] &r&aSpy mode ingeschakeld.");
        m.add("message.spy.disabled", "&8&l[&6&lMSG&8&l] &r&aSpy mode uitgeschakeld.");
        m.add("message.cmd_usage", "&8&l[&6&lMSG&8&l] &r&eGebruik: /msg <naam> <bericht> | /msg reply <bericht&8&l[&e&lMSG&8&l] &r> | /msg spy | /msg block <naam> | /msg unblock <naam> | /msg toggle");
        m.add("message.reply.no_last", "&8&l[&6&lMSG&8&l] &r&cEr is nog niemand om op te antwoorden.");
        return m;
    }

    @Override
    public void initialize() {
        this.handler = new MessagingHandler(this);
        getLifecycleManager().getCommandManager().registerFeatureCommand(new MessagingCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ReplyCommand(this));
        getLifecycleManager().getListenerManager().registerListener(new PlayerListener(this));
    }

    @Override
    public void disable() {
        handler.cleanupAll();
    }

    public MessagingHandler getHandler() {
        return handler;
    }
}
