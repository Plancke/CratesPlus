package plus.crates;

import com.google.common.io.ByteStreams;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import plus.crates.Commands.CrateCommand;
import plus.crates.Handlers.*;
import plus.crates.Listeners.BlockListeners;
import plus.crates.Listeners.GUIListeners;
import plus.crates.Listeners.PlayerInteract;
import plus.crates.Listeners.PlayerJoin;
import plus.crates.Utils.*;
import plus.crates.storage.FlatStorageHandler;
import plus.crates.storage.IStorageHandler;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CratesPlus extends JavaPlugin implements Listener {
    private String pluginPrefix = "";
    private String updateMessage = "";
    private String configBackup = null;
    private boolean updateAvailable = false;
    private ConfigHandler configHandler;
    private CrateHandler crateHandler;
    private SettingsHandler settingsHandler;
    private HologramHandler hologramHandler;
    private IStorageHandler storageHandler;
    private String bukkitVersion = "0.0";
    private Version_Util version_util;
    private static OpenHandler openHandler;
    private final List<UUID> creatingCrate = new ArrayList<>();

    public void onEnable() {
        Server server = getServer();
        Pattern pattern = Pattern.compile("(^[^\\-]*)");
        Matcher matcher = pattern.matcher(server.getBukkitVersion());
        if (!matcher.find()) {
            getLogger().severe("Could not find Bukkit version... Disabling plugin");
            setEnabled(false);
            return;
        }
        bukkitVersion = matcher.group(1);

        if (getConfig().isSet("Bukkit Version"))
            bukkitVersion = getConfig().getString("Bukkit Version");

        if (LinfootUtil.versionCompare(bukkitVersion, "1.14.2") > 0) {
            // This means the plugin is using something newer than the latest tested build... we'll show a warning but carry on as usual
            getLogger().warning("CratesPlus has not yet been officially tested with Bukkit " + bukkitVersion + " but should still work");
        }

        if (LinfootUtil.versionCompare(bukkitVersion, "1.9") > -1) {
            // Use 1.9+ Util
            version_util = new Version_1_9(this);
        } else if (LinfootUtil.versionCompare(bukkitVersion, "1.8") > -1) {
            // Use 1.8 Util
            version_util = new Version_1_8(this);
        } else if (LinfootUtil.versionCompare(bukkitVersion, "1.7") > -1) {
            // Use Default Util
            getLogger().warning("CratesPlus does NOT fully support Bukkit 1.7, if you have issues please report them but they may not be fixed");
            version_util = new Version_Util(this);
        } else {
            getLogger().severe("CratesPlus does NOT support Bukkit " + bukkitVersion + ", if you believe this is an error please let me know");
            if (!getConfig().isSet("Ignore Version") || !getConfig().getBoolean("Ignore Version")) { // People should only ignore this in the case of an error, doing an ignore on a unsupported version could break something
                setEnabled(false);
                return;
            }
            version_util = new Version_Util(this); // Use the 1.7 util? Probably has a lower chance of breaking
        }

        final ConsoleCommandSender console = server.getConsoleSender();
        getConfig().options().copyDefaults(true);
        saveConfig();

        hologramHandler = new HologramHandler();

        // TODO configurable
        storageHandler = new FlatStorageHandler(this);

        // Load new messages.yml
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            try {
                messagesFile.createNewFile();
                InputStream inputStream = getResource("messages.yml");
                OutputStream outputStream = new FileOutputStream(messagesFile);
                ByteStreams.copy(inputStream, outputStream);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to load messages.yml", e);
            }
        }

        YamlConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        MessageHandler.loadMessageConfiguration(this, messagesConfig, messagesFile);

        configHandler = new ConfigHandler(getConfig(), this);

        if (getConfig().getBoolean("Metrics")) {
            try {
                Metrics metrics = new Metrics(this);
                metrics.start();

                MetricsCustom metricsCustom = new MetricsCustom(this);
                metricsCustom.start();
            } catch (IOException e) {
                // Failed to submit the stats :-(
            }
        }

        // Load the crate handler
        crateHandler = new CrateHandler(this);

        // Do Prefix
        pluginPrefix = ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("Prefix", "&7[&bCratesPlus&7]")) + " " + ChatColor.RESET;

        // Register /crate command
        Bukkit.getPluginCommand("crate").setExecutor(new CrateCommand(this));

        // Register Events
        Bukkit.getPluginManager().registerEvents(new BlockListeners(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoin(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIListeners(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerInteract(this), this);

        openHandler = new OpenHandler(this);

        settingsHandler = new SettingsHandler(this);

        console.sendMessage(ChatColor.AQUA + getDescription().getName() + " Version " + getDescription().getVersion());
        if (getDescription().getVersion().contains("SNAPSHOT")) { // Added this because some people didn't really understand what a "snapshot" is...
            console.sendMessage(ChatColor.RED + "Warning: You are running a snapshot build of CratesPlus");
            console.sendMessage(ChatColor.RED + "It is advised that you do NOT run this on a production server!");
        }

        switch (getHologramHandler().getHologramPlugin()) {
            default:
            case NONE:
                console.sendMessage(ChatColor.RED + "Unable to find compatible Hologram plugin, holograms will not work!");
                break;
            case HOLOGRAPHIC_DISPLAYS:
                console.sendMessage(ChatColor.GREEN + "HolographicDisplays was found, hooking in!");
                break;
            case INDIVIDUAL_HOLOGRAMS:
                console.sendMessage(ChatColor.GREEN + "IndividualHolograms was found, hooking in!");
                break;
        }

        if (configBackup != null && Bukkit.getOnlinePlayers().size() > 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("cratesplus.admin")) {
                    player.sendMessage(pluginPrefix + ChatColor.GREEN + "Your config has been updated. Your old config was backed up to " + configBackup);
                    configBackup = null;
                }
            }
        }

        if (getConfig().getBoolean("Update Checks", true)) {
            getServer().getScheduler().runTaskLaterAsynchronously(this, () -> checkUpdate(console), 10L);
        }
    }

    public void onDisable() {
        getConfigHandler().getCrates().forEach((key, crate) -> crate.onDisable());
    }

    public String uploadConfig() {
        return uploadFile("config.yml");
    }

    public String uploadData() {
        return uploadFile("data.yml");
    }

    public String uploadMessages() {
        return uploadFile("messages.yml");
    }

    public String uploadFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists())
            return null;
        LineIterator it;
        StringBuilder lines = new StringBuilder();
        try {
            it = FileUtils.lineIterator(file, "UTF-8");
            try {
                while (it.hasNext()) {
                    String line = it.nextLine();
                    lines.append(line).append("\n");
                }
            } finally {
                it.close();
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "failed to upload file", e);
        }
        return MCDebug.paste(fileName, lines.toString());
    }

    private void checkUpdate(final ConsoleCommandSender console) {
        String updateBranch = getConfig().getString("Update Branch");

        if (getDescription().getVersion().contains("SNAPSHOT"))
            updateBranch = "snapshot";//Force snapshot branch on snapshot builds

        String branch = updateBranch.toLowerCase();

        if (branch.equalsIgnoreCase("snapshot")) {
            console.sendMessage(ChatColor.RED + "WARNING: Snapshot updates are not recommended on production servers");
        }
        console.sendMessage(ChatColor.GREEN + "Checking for updates via " + branch + " branch...");
        final LinfootUpdater updater = new LinfootUpdater(this, branch);
        final LinfootUpdater.UpdateResult snapShotResult = updater.getResult();
        switch (snapShotResult) {
            default:
            case FAILED:
                updateAvailable = false;
                updateMessage = pluginPrefix + "Failed to check for updates. Will try again later.";
                getServer().getScheduler().runTaskLaterAsynchronously(this, () -> checkUpdate(console), 60 * (60 * 20L)); // Checks again an hour later
                break;
            case NO_UPDATE:
                updateAvailable = false;
                updateMessage = pluginPrefix + "No update was found, you are running the latest version. Will check again later.";
                getServer().getScheduler().runTaskLaterAsynchronously(this, () -> checkUpdate(console), 60 * (60 * 20L)); // Checks again an hour later
                break;
            case SNAPSHOT_UPDATE_AVAILABLE:
                updateAvailable = true;
                updateMessage = pluginPrefix + "A snapshot update for CratesPlus is available, new version is " + updater.getVersion() + ". Your installed version is " + getDescription().getVersion() + ".\nPlease update to the latest version :)";
                break;
            case UPDATE_AVAILABLE:
                updateAvailable = true;
                updateMessage = pluginPrefix + "An update for CratesPlus is available, new version is " + updater.getVersion() + ". Your installed version is " + getDescription().getVersion() + ".\nPlease update to the latest version :)";
                break;
        }

        if (updateMessage != null)
            console.sendMessage(updateMessage);
    }

    public void reloadPlugin() {
        reloadConfig();

        // Do Prefix
        pluginPrefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("Prefix", "&7[&bCratesPlus&7]")) + " " + ChatColor.RESET;

        // Reload Configuration
        configHandler = new ConfigHandler(getConfig(), this);

        // Settings Handler
        settingsHandler = new SettingsHandler(this);

    }

    public SettingsHandler getSettingsHandler() {
        return settingsHandler;
    }

    public String getPluginPrefix() {
        return pluginPrefix;
    }

    public ConfigHandler getConfigHandler() {
        return configHandler;
    }

    public HologramHandler getHologramHandler() {
        return hologramHandler;
    }

    public IStorageHandler getStorageHandler() {
        return storageHandler;
    }

    public String getUpdateMessage() {
        return updateMessage;
    }

    public String getConfigBackup() {
        return configBackup;
    }

    public void setConfigBackup(String configBackup) {
        this.configBackup = configBackup;
    }

    public Version_Util getVersion_util() {
        return version_util;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public CrateHandler getCrateHandler() {
        return crateHandler;
    }

    public static OpenHandler getOpenHandler() {
        return openHandler;
    }

    public String getBukkitVersion() {
        return bukkitVersion;
    }

    public boolean isCreating(UUID uuid) {
        return creatingCrate.contains(uuid);
    }

    public void addCreating(UUID uuid) {
        creatingCrate.add(uuid);
    }

    public void removeCreating(UUID uuid) {
        creatingCrate.remove(uuid);
    }

}
