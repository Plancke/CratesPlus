package plus.crates.opener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import plus.crates.CratesPlus;

import java.io.File;
import java.io.IOException;

public abstract class FlatConfigurableOpener extends Opener {

    public FlatConfigurableOpener(Plugin plugin, String name) {
        this(plugin, name, false);
    }

    public FlatConfigurableOpener(Plugin plugin, String name, boolean async) {
        super(plugin, name, async);
    }

    protected File getOpenerConfigFile() {
        File openersDir = new File(JavaPlugin.getPlugin(CratesPlus.class).getDataFolder(), "openers");
        if (!openersDir.exists())
            if (!openersDir.mkdirs())
                return null;
        File configurationFile = new File(openersDir, getName() + ".yml");
        if (!configurationFile.exists())
            try {
                if (!configurationFile.createNewFile())
                    return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        return configurationFile;
    }

    protected FileConfiguration getOpenerConfig() {
        File file = getOpenerConfigFile();
        if (file == null)
            return null;
        return YamlConfiguration.loadConfiguration(file);
    }

}
