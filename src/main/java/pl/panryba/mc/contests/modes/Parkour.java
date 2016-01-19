/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests.modes;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import pl.panryba.mc.contests.Contest;
import pl.panryba.mc.contests.ContestListener;
import pl.panryba.mc.contests.ItemLoreHelper;
import pl.panryba.mc.contests.Plugin;

/**
 *
 * @author PanRyba.pl
 */
public class Parkour implements Contest, Listener {
    private final Plugin plugin;
    
    private final String startedByName;
    private ContestListener listener;
    
    private boolean running;
    private boolean generalPrizeGiven;
    
    private Map<String, Long> sessions;
    private Map<String, Long> results;
    private Set<String> playersWithPrizes;
    
    private long bestResult;
    private String bestName;
    
    private Location startLocation;
    private Location finishLocation;
    private String failRegionName;
    private ItemStack prizeForCompletion;
    private ItemStack generalPrize;
    
    public Parkour(Plugin plugin, CommandSender startedBy) {
        this.plugin = plugin;
        this.startedByName = startedBy.getName();
    }

    @Override
    public void Start() {
        this.running = true;
        this.sessions = new HashMap<>();
        this.results = new HashMap<>();
        this.playersWithPrizes = new HashSet<>();

        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    protected void onPlayerMove(PlayerMoveEvent event) {
        if (event == null) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        String playerName = player.getName();
        if(isPlayerWhoStarted(playerName)) {
            return;
        }
        
        if(!hasEventSession(playerName)) {
            return;
        }

        if (isPlayerOnFinishLocation(player)) {
            long start = this.sessions.remove(playerName);
            long stop = new Date().getTime();

            long diff = stop - start;
            long diffSecs = diff / 1000;

            if (bestName == null || bestResult > diffSecs) {
                bestName = playerName;
                bestResult = diffSecs;
            }

            String playerResultMsg = "[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Gracz " + ChatColor.RED + playerName + ChatColor.YELLOW + " ukonczyl parkour w " + ChatColor.RED + diffSecs + "s";
            String bestMsg = "[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Obecnie wygrywa " + ChatColor.RED + bestName + ChatColor.YELLOW + " z wynikiem " + ChatColor.RED + bestResult + "s";

            this.plugin.getServer().broadcastMessage(playerResultMsg);
            this.plugin.getServer().broadcastMessage(bestMsg);

            handlePrizeForCompletion(player);

            if (this.results.containsKey(playerName)) {
                long currentResult = this.results.get(playerName);

                if (currentResult < diffSecs) {
                    return;
                }
            }

            this.results.put(playerName, diffSecs);
        } else if(isPlayerInFailRegion(player)) {
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.YELLOW + " Nie udalo sie Tobie przejsc Parkoura. Probuj jeszcze raz!");
            startPlayerSession(player);
        }
    }

    @Override
    public void Stop() {
        this.running = false;
        HandlerList.unregisterAll(this);
        handleGeneralPrize();
    }

    @Override
    public String[] getResults() {
        if (bestName == null) {
            return new String[] {ChatColor.YELLOW + "Zaden gracz nie ukonczyl parkoura"};
        }

        return new String[]{ ChatColor.YELLOW + "Wygrywa gracz " + bestName + " z wynikiem " + bestResult + "s"};
    }

    @Override
    public String[] getRules() {
        return new String[]{
                    ChatColor.YELLOW + "Wygrywa gracz, ktory w najkrotszym czasie przejdzie Parkour",
                    "Aby podjac probe, wpisz " + ChatColor.RED + "/event tp"
                };
    }

    @Override
    public void initialize(CommandSender cs, ContestListener listener, String[] args) {
        this.listener = listener;
        
        if(args.length == 0) {
            return;
        }
        
        switch(args[0]) {
            case "autostart":
                if(args.length != 4) {
                    return;
                }
                
                this.loadArena(cs, new String[] { args[1], args[2], args[3] });
                this.start();
                break;
        }
    }

    @Override
    public String getName() {
        return "Parkour";
    }

    @Override
    public String getStartedBy() {
        return this.startedByName;
    }

    @Override
    public boolean handleCommand(CommandSender cs, String cmnd, String[] args) {
        Player player;
        if(cs instanceof Player) {
            player = (Player)cs;
        } else {
            player = null;
        }

        if (cmnd.equalsIgnoreCase("tp")) {
            if (!this.running) {
                return false;
            }
            
            if(player == null) {
                cs.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] Nie mozesz teleportowac sie z konsoli :-)");
                return true;
            }

            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.RED + "Zostales przeteleportowany na poczatek trasy. Biegnij szybko - wygrywa najlepszy!");

            startPlayerSession(player);
            return true;
        }

        if (player != null && !player.getName().equalsIgnoreCase(this.startedByName)) {
            return false;
        }

        if (cmnd.equalsIgnoreCase("poczatek")) {
            if(player == null) {
                cs.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] Nie mozesz ustawic miejsca poczatku eventu z konsoli");
                return true;
            }
            
            setStartLocation(player.getLocation());
            
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + "Ustawiles miejsce startowe eventu");
            return true;
        }

        if (cmnd.equalsIgnoreCase("koniec")) {
            if(player == null) {
                cs.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] Nie mozesz ustawic miejsca konca eventu z konsoli");
                return true;
            }
            
            this.finishLocation = player.getLocation().getBlock().getLocation();
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + "Ustawiles miejsce koncowe eventu");
            return true;
        }

        if (cmnd.equalsIgnoreCase("start")) {
            this.start();
            return true;
        }

        if (cmnd.equalsIgnoreCase("nagroda")) {
            if(player == null) {
                cs.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] Nie mozesz ustawic nagrody za ukonczenie parkoura z konsoli");
                return true;
            }
            
            ItemStack prize = player.getItemInHand();
            this.setPrizeForCompletion(player, prize);
            return true;
        }

        if (cmnd.equalsIgnoreCase("nagroda_glowna")) {
            if(player == null) {
                cs.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] Nie mozesz ustawic nagrody glownej z konsoli");
                return true;
            }
            
            ItemStack prize = player.getItemInHand();
            this.setGeneralPrize(player, prize);
            return true;
        }

        if (cmnd.equalsIgnoreCase("porazka")) {
            if (args.length != 1) {
                cs.sendMessage("Musisz podac: [nazwa regionu porazki]");
                return true;
            }

            this.setFailRegion(cs, args[0]);
            return true;
        }
        
        if(cmnd.equalsIgnoreCase("wczytaj")) {
            if(args.length < 1) {
                player.sendMessage("Musisz podac: [nazwa areny] ([nazwa prezentu za przejscie] | \"\") ([nazwa glownego prezentu] | \"\")");
                return true;
            }
            
            this.loadArena(cs, args);
            return true;
        }

        return false;
    }

    private boolean isPlayerOnFinishLocation(Player player) {
        Location playerLoc = player.getLocation();

        return playerLoc.getWorld() == this.finishLocation.getWorld()
                && playerLoc.getBlockX() == this.finishLocation.getBlockX()
                && playerLoc.getBlockY() == this.finishLocation.getBlockY()
                && playerLoc.getBlockZ() == this.finishLocation.getBlockZ();
    }

    private void setPrizeForCompletion(Player player, ItemStack prize) {
        if (prize == null || prize.getType().equals(Material.AIR)) {
            this.prizeForCompletion = null;
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Ustawiles brak nagrody za ukonczenie parkoura");
            return;
        }

        this.prizeForCompletion = prize.clone();
        player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + "Ustawiles nagrode za ukonczenie parkoura - " + this.prizeForCompletion.toString());
    }

    private void setGeneralPrize(Player player, ItemStack prize) {
        if (prize == null || prize.getType().equals(Material.AIR)) {
            this.generalPrize = null;
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Ustawiles brak glownej nagrody dla zwyciezcy parkoura");
            return;
        }

        this.generalPrize = prize.clone();
        player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Ustawiles glowna nagrode za wygranie eventu Parkour - " + this.generalPrize.toString());
    }

    private void handlePrizeForCompletion(Player player) {
        if (this.prizeForCompletion == null || this.prizeForCompletion.getType().equals(Material.AIR)) {
            return;
        }

        if (this.playersWithPrizes.contains(player.getName())) {
            return;
        }

        PlayerInventory inv = player.getInventory();

        ItemStack prize = preparePrizeForCompletion(player);
        Map<Integer, ItemStack> failed = inv.addItem(prize);

        if (failed.isEmpty()) {
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.RED + "W Twoim plecaku zostala umieszczona nagroda, za ukonczenie parkoura!");
        } else {
            player.getWorld().dropItem(player.getLocation(), prize);
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.RED + "Otrzymujesz nagrode, za ukonczenie parkoura! (nie miesci sie ona w Twoim plecaku wiec szybko ja podnies!)");
        }

        this.playersWithPrizes.add(player.getName());
    }

    private ItemStack preparePrizeForCompletion(Player player) {
        ItemStack prize = this.prizeForCompletion.clone();
        ItemLoreHelper.addItemLore(prize, new String[] { "nagroda za ukonczenie Parkoura", "dla " + player.getName() });

        return prize;
    }

    private void handleGeneralPrize() {
        if (this.generalPrizeGiven) {
            return;
        }

        if (bestName == null) {
            return;
        }

        if (this.generalPrize == null || this.generalPrize.getType().equals(Material.AIR)) {
            return;
        }

        Player player = this.plugin.getServer().getPlayerExact(bestName);
        if (player == null) {
            return;
        }

        ItemStack prize = prepareGeneralPrize(player);

        PlayerInventory inv = player.getInventory();
        Map<Integer, ItemStack> failed = inv.addItem(prize);

        if (failed.isEmpty()) {
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.RED + "W Twoim plecaku zostala umieszczona nagroda, za wygranie eventu Parkour!");
        } else {
            player.getWorld().dropItem(player.getLocation(), prize);
            player.sendMessage("[" + ChatColor.RED + "EVENT PARKOUR" + ChatColor.RESET + "] " + ChatColor.RED + "Otrzymujesz nagrode, za wygranie eventu Parkour! (nie miesci sie ona w Twoim plecaku wiec szybko ja podnies!)");
        }

        generalPrizeGiven = true;
    }

    private ItemStack prepareGeneralPrize(Player player) {
        ItemStack prize = this.generalPrize.clone();
        ItemLoreHelper.addItemLore(prize, new String[] {"nagroda za wygranie Parkoura (" + bestResult + "s)", "dla " + player.getName() });

        return prize;
    }

    private void setFailRegion(CommandSender cs, String regionName) {
        if (regionName == null || regionName.isEmpty()) {
            this.failRegionName = null;
            cs.sendMessage("Ustawiles brak regionu porazki");
            return;
        }
        
        Object region = getRegion(regionName);
        if(region == null) {
            cs.sendMessage("Region o nazwie " + ChatColor.RED + regionName + ChatColor.RESET + " nie istnieje wiec nie mozesz go ustawic jako region porazki!");
            return;
        }

        this.failRegionName = regionName;
        cs.sendMessage("Ustawiles region porazki na " + regionName);
    }

    private boolean isPlayerInFailRegion(Player player) {
        if(!this.sessions.containsKey(player.getName())) {
            return false;
        }
        
        if(this.failRegionName == null) {
            return false;
        }
        
        ProtectedRegion region = getRegion(this.failRegionName);
        if(region == null) {
            return false;
        }
        
        Location playerLoc = player.getLocation();
        Vector playerVec = new Vector(playerLoc.getX(), playerLoc.getY(), playerLoc.getZ());
                    
        return region.contains(playerVec);
    }
    
    private ProtectedRegion getRegion(String name) {
        RegionManager rm = WGBukkit.getRegionManager(this.startLocation.getWorld());
        return rm.getRegion(name);
    }    

    private void startPlayerSession(Player player) {
        player.teleport(this.startLocation);
        this.sessions.put(player.getName(), new Date().getTime());
    }

    private void loadArena(CommandSender cs, String[] args) {
        String name = args[0];
        
        FileConfiguration config = this.listener.getConfiguration("parkour.yml");
        
        ConfigurationSection arenaSection = config.getConfigurationSection("arenas." + name);
        if(arenaSection == null) {
            cs.sendMessage("Nie znaleziono konfiguracji dla areny " + name);
            return;
        }
        
        setStartLocation(readLocation(arenaSection.getConfigurationSection("start")));
        this.finishLocation = readLocation(arenaSection.getConfigurationSection("finish"));
        this.failRegionName = arenaSection.getString("fail");
        
        cs.sendMessage(
                new String[] {
                    "Wczytales arene " + name + ":",
                    "Lokalizacja poczatkowa: " + this.startLocation,
                    "Lokalizacja koncowa: " + this.finishLocation,
                    "Region porazki: " + this.failRegionName
                });
        
        if(args.length == 1) {
            this.prizeForCompletion = null;
            this.generalPrize = null;
            return;
        }
        
        ConfigurationSection completionPrizeSection = config.getConfigurationSection("prizes." + args[1]);
        this.prizeForCompletion = loadPrize(completionPrizeSection);
        
        cs.sendMessage("Nagroda za ukonczenie: " + this.prizeForCompletion);
        
        if(args.length == 2) {
            return;
        }
        
        ConfigurationSection generalPrizeSection = config.getConfigurationSection("prizes." + args[2]);
        this.generalPrize = loadPrize(generalPrizeSection);
        
        cs.sendMessage("Nagroda za wygrana: " + this.generalPrize);
    }

    private Location readLocation(ConfigurationSection section) {
        World locWorld = this.plugin.getServer().getWorld(section.getString("world"));
        
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float)section.getDouble("yaw");
        float pitch = (float)section.getDouble("pitch");
        
        return new Location(locWorld, x, y, z, yaw, pitch);
    }

    private ItemStack loadPrize(ConfigurationSection section) {
        if(section == null) {
            return null;
        }
        
        Material type = Material.getMaterial(section.getString("material"));
        Integer amount = section.getInt("amount");
        Short damage = (short)section.getInt("damage");
        
        if(damage != null) {
            return new ItemStack(type, amount, damage);
        } else {
            return new ItemStack(type, amount);
        }
    }

    private void setStartLocation(Location location) {
        this.startLocation = location;
    }

    private void start() {
        this.listener.contestStarted(this);
    }

    private boolean hasEventSession(String playerName) {
        return this.sessions.containsKey(playerName);
    }

    private boolean isPlayerWhoStarted(String playerName) {
        return playerName.equals(this.startedByName);
    }
}
