package me.bintanq;

import me.bintanq.command.DummyCommand;
import me.bintanq.listener.DamageListener;
import me.bintanq.manager.DummyManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class VisantaraEventHandler extends JavaPlugin {

    private static VisantaraEventHandler instance;
    private DummyManager dummyManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadConfig();

        this.dummyManager = new DummyManager(this);

        registerCommands();
        registerListeners();

        getLogger().info("VisantaraEventHandler enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (dummyManager != null) {
            dummyManager.removeAllDummies();
        }
        getLogger().info("VisantaraEventHandler disabled. All dummies removed.");
    }

    private void registerCommands() {
        DummyCommand dummyCommand = new DummyCommand(this);
        var cmd = getCommand("vdummy");
        if (cmd != null) {
            cmd.setExecutor(dummyCommand);
            cmd.setTabCompleter(dummyCommand);
        } else {
            getLogger().log(Level.SEVERE, "Failed to register /vdummy command. Check plugin.yml.");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
    }

    public static VisantaraEventHandler getInstance() {
        return instance;
    }

    public DummyManager getDummyManager() {
        return dummyManager;
    }

    public void reload() {
        reloadConfig();
        dummyManager.removeAllDummies();
        getLogger().info("VisantaraEventHandler configuration reloaded.");
    }
}