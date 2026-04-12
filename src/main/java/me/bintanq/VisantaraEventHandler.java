package me.bintanq;

import me.bintanq.command.VHandlerCommand;
import me.bintanq.listener.DamageListener;
import me.bintanq.manager.DummyManager;
import me.bintanq.manager.MessageManager;
import me.bintanq.naturaldrops.BlockListener;
import me.bintanq.naturaldrops.DropConfig;
import me.bintanq.naturaldrops.NaturalDropManager;
import me.bintanq.util.ConfigUpdater;
import me.bintanq.cinematic.ForgeCinematicListener;
import me.bintanq.forgemenu.ForgeMenuConfig;
import me.bintanq.forgemenu.ForgeMenuGUI;
import me.bintanq.forgemenu.ForgeMenuListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Level;

public final class VisantaraEventHandler extends JavaPlugin {

    private static VisantaraEventHandler instance;
    private DummyManager dummyManager;
    private NaturalDropManager naturalDropManager;
    private DropConfig dropConfig;
    private MessageManager messageManager;
    private ForgeCinematicListener forgeCinematicListener;
    private ForgeMenuConfig forgeMenuConfig;
    private ForgeMenuGUI forgeMenuGUI;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        runConfigUpdater();

        reloadConfig();

        this.messageManager = new MessageManager(this);
        this.dummyManager = new DummyManager(this);
        this.naturalDropManager = new NaturalDropManager(this);
        this.dropConfig = new DropConfig(this);

        this.forgeMenuConfig = new ForgeMenuConfig(this);
        this.forgeMenuGUI    = new ForgeMenuGUI(this, forgeMenuConfig);

        registerCommands();
        registerListeners();

        getLogger().info("VisantaraEventHandler enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (dummyManager != null) {
            dummyManager.removeAllDummies();
        }
        if (naturalDropManager != null) {
            naturalDropManager.close();
        }
        if (forgeMenuGUI != null) {
            forgeMenuGUI.closeAll();
        }
        getLogger().info("VisantaraEventHandler disabled.");
    }

    private void runConfigUpdater() {
        try {
            ConfigUpdater.update(this, "config.yml");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to update config.yml", e);
        }
        try {
            ConfigUpdater.update(this, "message.yml");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to update message.yml", e);
        }
    }

    private void registerCommands() {
        VHandlerCommand handlerCommand = new VHandlerCommand(this);
        var cmd = getCommand("vhandler");
        if (cmd != null) {
            cmd.setExecutor(handlerCommand);
            cmd.setTabCompleter(handlerCommand);
        } else {
            getLogger().log(Level.SEVERE, "Failed to register /vhandler command. Check plugin.yml.");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this, naturalDropManager, dropConfig), this);

        this.forgeCinematicListener = new ForgeCinematicListener(this);
        getServer().getPluginManager().registerEvents(forgeCinematicListener, this);

        getServer().getPluginManager().registerEvents(forgeMenuGUI, this);
        getServer().getPluginManager().registerEvents(
                new ForgeMenuListener(this, forgeMenuConfig, forgeMenuGUI), this);
    }


    public static VisantaraEventHandler getInstance() { return instance; }
    public DummyManager getDummyManager() { return dummyManager; }
    public NaturalDropManager getNaturalDropManager() { return naturalDropManager; }
    public DropConfig getDropConfig() { return dropConfig; }
    public MessageManager getMessageManager() { return messageManager; }
    public ForgeCinematicListener getForgeCinematicListener() { return forgeCinematicListener; }
    public ForgeMenuConfig getForgeMenuConfig() { return forgeMenuConfig; }
    public ForgeMenuGUI getForgeMenuGUI() { return forgeMenuGUI; }


    public void reload() {
        runConfigUpdater();
        reloadConfig();
        messageManager.load();
        dropConfig.load(this);
        dummyManager.removeAllDummies();

        forgeMenuGUI.closeAll();
        forgeMenuConfig.load();

        getLogger().info("VisantaraEventHandler configuration reloaded.");
    }
}