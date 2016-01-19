/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests.modes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import pl.panryba.mc.contests.Contest;
import pl.panryba.mc.contests.ContestListener;
import pl.panryba.mc.contests.PluginApi;
import pl.panryba.mc.guilds.entities.Guild;
import pl.panryba.mc.guilds.entities.GuildMember;

/**
 *
 * @author PanRyba.pl
 */
public class Skirmish implements Contest, Listener {

    private Plugin plugin;
    private pl.panryba.mc.guilds.PluginApi guildsApi;
    private PluginApi pluginApi;
    private Map<Guild, Integer> guildScores;
    private Map<Guild, Set<String>> scoringMembers;
    private Location spawnLocation;
    private Guild winningGuild;
    private int winningGuildScore;
    private String startedByName;
    private ContestListener listener;
    private final static String EVENT_PREFIX = "[" + ChatColor.RED + "EVENT" + ChatColor.RESET + "] ";

    public Skirmish(Plugin plugin, PluginApi api, pl.panryba.mc.guilds.PluginApi guildsApi, CommandSender cs) {
        this.plugin = plugin;
        this.guildsApi = guildsApi;
        this.pluginApi = pluginApi;
        this.guildScores = new HashMap<>();
        this.scoringMembers = new HashMap<>();

        if (cs instanceof Player) {
            Player player = (Player) cs;
            this.spawnLocation = player.getLocation();
        }
        this.startedByName = cs.getName();
    }

    @Override
    public void initialize(CommandSender cs, ContestListener listener, String[] args) {
        this.listener = listener;

        if (args.length > 0) {
            switch (args[0]) {
                case "autostart":
                    if (args.length != 2) {
                        return;
                    }
                    this.loadSpawnLocation(cs, args[1]);
                    break;
            }
        }

        this.listener.contestStarted(this);
    }

    @Override
    public String getName() {
        return "Rzeznia";
    }

    @Override
    public String getStartedBy() {
        return this.startedByName;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim == null) {
            return;
        }

        Player killer = victim.getKiller();
        if (killer == null) {
            Bukkit.getLogger().info(victim.getName() + " has no killer");
            return;
        }

        GuildMember member = this.guildsApi.getPlayerMember(killer);
        if (member == null) {
            Bukkit.getLogger().info("killer has no guild member");
            return;
        }

        Guild guild = member.getGuild();
        GuildMember victimMember = this.guildsApi.getPlayerMember(victim);

        if (victimMember == null) {
            Bukkit.getLogger().info("victim has no guild member");
            return;
        }

        if (guild.isSameGuild(victimMember.getGuild())) {
            Bukkit.getLogger().info("killer and victim are in same guild");
            return;
        }

        String playerName = member.getPlayer();

        Integer currentGuildScore = this.guildScores.get(guild);
        if (currentGuildScore == null) {
            currentGuildScore = 0;
        }

        currentGuildScore += 1;

        if (currentGuildScore > winningGuildScore) {
            winningGuildScore = currentGuildScore;
            winningGuild = guild;
        }

        this.guildScores.put(guild, currentGuildScore);

        Set<String> guildMembers = this.scoringMembers.get(guild);

        if (guildMembers == null) {
            guildMembers = new HashSet<>();
            this.scoringMembers.put(guild, guildMembers);
        }

        guildMembers.add(playerName);

        killer.sendMessage(getEventMessage(
                ChatColor.YELLOW + "Zdobyles punkt dla gildii " + guild.getTagName() + ", ktora posiada teraz " + currentGuildScore + " pkt."));

        this.pluginApi.sendToOthers(killer, getEventMessage(
                ChatColor.YELLOW + playerName + " zdobyl punkt dla gildii " + guild.getTagName() + ", ktora posiada teraz " + currentGuildScore + " pkt."));

        this.pluginApi.sendToAll(getEventMessage(
                ChatColor.GREEN + "W tej chwili wygrywa gildia " + winningGuild.getTagName() + " - " + winningGuildScore + " pkt."));
    }

    @Override
    public void Start() {
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void Stop() {
        HandlerList.unregisterAll(this);

        Guild theWinningGuild = getWinningGuild();
        Set<String> members = this.scoringMembers.get(theWinningGuild);

        if (members != null) {
            for (String playerName : members) {
                Player winner = Bukkit.getPlayerExact(playerName);
                if (winner == null) {
                    continue;
                }

                ItemStack prize = new ItemStack(Material.SPONGE, 1);
                PlayerInventory inv = winner.getInventory();
                Map<Integer, ItemStack> failed = inv.addItem(prize);

                if (failed.isEmpty()) {
                    winner.sendMessage("[" + ChatColor.RED + "EVENT" + ChatColor.RESET + "] " + ChatColor.RED + "W Twoim plecaku zostala umieszczona nagroda, za wygranie eventu!");
                } else {
                    winner.getWorld().dropItem(winner.getLocation(), prize);
                    winner.sendMessage("[" + ChatColor.RED + "EVENT" + ChatColor.RESET + "] " + ChatColor.RED + "Otrzymujesz nagrode, za wygranie eventu! (nie miesci sie ona w Twoim plecaku wiec szybko ja podnies!)");
                }
            }
        }
    }

    @Override
    public String[] getResults() {
        List<String> results = new ArrayList<>();

        Guild theWinningGuild = getWinningGuild();

        if (theWinningGuild == null) {
            results.add("Zadna gildia nie wziela udzialu w wydarzeniu, w zwiazku z czym nie wyloniono zwyciezcy.");
        } else {
            results.add(getEventMessage("Wygrywa gildia " + theWinningGuild.getFullName() + ", z wynikiem " + winningGuildScore + " pkt."));

            Set<String> members = this.scoringMembers.get(theWinningGuild);
            if (members != null && !members.isEmpty()) {
                String players = "(";

                int count = 0;
                for (String player : members) {
                    if (count > 0) {
                        players += ", ";
                    }

                    players += player;
                    ++count;
                }

                players += ")";

                results.add(getEventMessage(players));
            }
        }

        String[] arrResults = new String[results.size()];
        results.toArray(arrResults);

        return arrResults;
    }

    @Override
    public String[] getRules() {
        List<String> results = new ArrayList<>();

        results.add(ChatColor.YELLOW + "Za kazdego zabitego czlonka innej gildii, Twoja gildia otrzymuje " + ChatColor.GREEN + "1 punkt.");
        results.add(ChatColor.YELLOW + "Wygrywa gildia, ktora zdobedzie " + ChatColor.RED + "najwiecej punktow!");
        results.add(ChatColor.YELLOW + "Aby przeteleportowac sie na teren walki, wpisz - " + ChatColor.RED + "/event tp");

        String[] arrResults = new String[results.size()];
        results.toArray(arrResults);

        return arrResults;
    }

    private String getEventMessage(String string) {
        return EVENT_PREFIX + string;
    }

    private Guild getWinningGuild() {
        return winningGuild;
    }

    @Override
    public boolean handleCommand(CommandSender cs, String cmnd, String[] args) {
        if (!cmnd.equalsIgnoreCase("tp")) {
            return false;
        }

        if (!(cs instanceof Player)) {
            return false;
        }

        Player player = (Player) cs;

        player.sendMessage("Teleportowanie na miejsce eventu..");
        player.teleport(this.spawnLocation, PlayerTeleportEvent.TeleportCause.COMMAND);
        return true;
    }

    private void loadSpawnLocation(CommandSender cs, String name) {
        FileConfiguration config = this.listener.getConfiguration("skirmish.yml");
        ConfigurationSection section = config.getConfigurationSection("spawns." + name);

        World locWorld = Bukkit.getWorld(section.getString("world"));
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");

        this.spawnLocation = new Location(locWorld, x, y, z, yaw, pitch);
        cs.sendMessage("Zaladowano lokalizacje spawnu: " + this.spawnLocation);
    }
}
