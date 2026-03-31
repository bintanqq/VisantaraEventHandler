package me.bintanq;

import me.bintanq.command.VHandlerCommand;
import me.bintanq.listener.DamageListener;
import me.bintanq.manager.DummyManager;
import me.bintanq.naturaldrops.BlockListener;
import me.bintanq.naturaldrops.DropConfig;
import me.bintanq.naturaldrops.NaturalDropManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class VisantaraEventHandler extends JavaPlugin {

    private static VisantaraEventHandler instance;
    private DummyManager dummyManager;
    private NaturalDropManager naturalDropManager;
    private DropConfig dropConfig;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadConfig();

        this.dummyManager = new DummyManager(this);
        this.naturalDropManager = new NaturalDropManager(this);
        this.dropConfig = new DropConfig(this);

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
        getLogger().info("VisantaraEventHandler disabled.");
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
    }

    public static VisantaraEventHandler getInstance() {
        return instance;
    }

    public DummyManager getDummyManager() {
        return dummyManager;
    }

    public NaturalDropManager getNaturalDropManager() {
        return naturalDropManager;
    }

    public DropConfig getDropConfig() {
        return dropConfig;
    }

    public void reload() {
        reloadConfig();
        dropConfig.load(this);
        dummyManager.removeAllDummies();
        getLogger().info("VisantaraEventHandler configuration reloaded.");
    }
}