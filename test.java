import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import java.net.InetAddress;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public class test {
    public void foo() throws Exception {
        UUID u = UUID.randomUUID();
        InetAddress a = InetAddress.getByName("127.0.0.1");
        
        AsyncPlayerPreLoginEvent e1 = new AsyncPlayerPreLoginEvent("a", a, u);
        AsyncPlayerPreLoginEvent e2 = new AsyncPlayerPreLoginEvent("a", a, u, false);
        PlayerQuitEvent q1 = new PlayerQuitEvent(null, "q");
        PlayerQuitEvent q2 = new PlayerQuitEvent(null, Component.text("q"));
    }
}
