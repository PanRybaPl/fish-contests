/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests.modes;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import pl.panryba.mc.contests.Contest;
import pl.panryba.mc.contests.ContestListener;
import pl.panryba.mc.contests.Plugin;
import pl.panryba.mc.contests.PluginApi;

/**
 *
 * @author PanRyba.pl
 */
public class FindPlace implements Contest, Listener {
    private Plugin plugin;
    private PluginApi api;
    private ContestListener listener;
    private String startedByName;
    private boolean completed;
    private Location hiddenLocation;
    private String winner;
    
    public FindPlace(Plugin plugin, PluginApi api, Player startedBy) {
        this.plugin = plugin;
        this.api = api;
        this.startedByName = startedBy.getName();
        this.hiddenLocation = startedBy.getLocation();
    }
    
    @Override
    public void initialize(CommandSender cs, ContestListener listener, String[] args) {
        this.listener = listener;
        this.listener.contestStarted(this);
    }
    
    @Override
    public String getName() {
        return "Poszukiwania";
    }    
    
    @Override
    public String getStartedBy() {
        return this.startedByName;
    }
    
    @EventHandler
    protected void onPlayerMove(PlayerMoveEvent event) {
        if(this.completed)
            return;
        
        if(event.isCancelled()) {
            return;
        }
        
        Player player = event.getPlayer();
        if(player == null) {
            return;
        }
        
        if(player.getName().equals(this.startedByName)) {
            return;
        }
        
        Location loc = player.getLocation();
        
        if(loc.getWorld() == this.hiddenLocation.getWorld() &&
                loc.getBlockX() == this.hiddenLocation.getBlockX() &&
                loc.getBlockY() == this.hiddenLocation.getBlockY() &&
                loc.getBlockZ() == this.hiddenLocation.getBlockZ()) {
            completed = true;
            winner = player.getName();
            
            this.listener.contestFinished(this);
        }
    }

    @Override
    public void Start() {
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void Stop() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public String[] getResults() {
        
        if(winner == null) {
            return new String[] { ChatColor.RED + "Nikt nie odnalazl ukrytego miejsca." };
        }
        
        return new String[] { ChatColor.GREEN + "Ukryte miejsce (" +
                this.hiddenLocation.getBlockX() + "," + this.hiddenLocation.getBlockY() + "," + this.hiddenLocation.getBlockZ()
                + ") odnalazl " + ChatColor.YELLOW + winner + ChatColor.GREEN + "!" };
    }

    @Override
    public String[] getRules() {
        return new String[] {
          "Aby wygrac, musisz odnalezc ukryte miejsce, wyznaczone przez " + this.startedByName
        };
    }

    @Override
    public boolean handleCommand(CommandSender cs, String cmnd, String[] args) {
        return false;
    }
    
}
