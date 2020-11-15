package plus.crates.storage;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

public interface IStorageHandler {

    Object getPlayerData(UUID uuid, String key);

    void setPlayerData(UUID uuid, String key, String value);

    void incPlayerData(UUID uuid, String key, Integer value);

    void removeCrateLocation(String crate, Location location);

    void addCrateLocation(String crate, Location location);

    Map<UUID, Map<String, Integer>> getPendingKeys();

    void updateKeysData(UUID uuid, Map<String, Integer> keys);
}
