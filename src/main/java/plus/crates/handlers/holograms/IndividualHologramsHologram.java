package plus.crates.handlers.holograms;

import com.micrlink.individualholograms.IndividualHolograms;
import org.bukkit.Location;
import plus.crates.crates.Crate;
import plus.crates.util.LinfootUtil;

import java.util.List;

public class IndividualHologramsHologram implements Hologram {

    public void create(Location location, Crate crate, List<String> lines) {
        IndividualHolograms.get().getHologramManager().createNewHologram(LinfootUtil.formatLocation(location), location.clone().add(0, -1, 0), lines);
    }

    public void remove(Location location, Crate crate) {
        IndividualHolograms.get().getHologramManager().removeHologram(LinfootUtil.formatLocation(location));
    }

}
