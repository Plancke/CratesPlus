package plus.crates.handlers.holograms;

import org.bukkit.Location;
import plus.crates.crates.Crate;

import java.util.List;

public interface Hologram {

    void create(Location location, Crate crate, List<String> lines);

    void remove(Location location, Crate crate);

}
