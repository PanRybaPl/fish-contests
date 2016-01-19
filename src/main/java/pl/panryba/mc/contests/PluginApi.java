/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.panryba.mc.contests.modes.FindPlace;
import pl.panryba.mc.contests.modes.OX;
import pl.panryba.mc.contests.modes.Parkour;
import pl.panryba.mc.contests.modes.Skirmish;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author PanRyba.pl
 */
public class PluginApi implements ContestListener {

    public void vote(Player player, String eventType) {
        if(getContestRunning()) {
            player.sendMessage(ChatColor.YELLOW + "Event wlasnie trwa wiec nie mozesz glosowac");
            return;
        }
        
        if(this.voted.containsKey(player.getName())) {
            player.sendMessage(ChatColor.YELLOW + "Juz zaglosowales na kolejny event - " + ChatColor.GREEN + this.voted.get(player.getName()));
            return;
        }
        
        String normalized = eventType.toUpperCase();
        switch(normalized) {
            case "OX":
            case "PARKOUR":
            case "RZEZNIA":
                break;
            default:
                player.sendMessage(ChatColor.YELLOW + "Obecnie nie organizujemy eventow " + eventType + " wiec nie mozesz zaglosowac");
                return;
        }
        
        this.voted.put(player.getName(), normalized);
        
        int newScore;
        if(!this.votes.containsKey(normalized)) {
            newScore = 1;
        } else {
            newScore = this.votes.get(normalized) + 1;
        }
        
        this.votes.put(normalized, newScore);
        
        player.sendMessage(ChatColor.YELLOW + "Dziekujemy za zaglosowanie na event " + normalized);
        Bukkit.broadcastMessage(ChatColor.RED + player.getName() + ChatColor.YELLOW +  " zaglosowal za organizacja eventu " + ChatColor.GREEN + normalized);
        
        String results = ChatColor.YELLOW + "Aktualne wyniki glosowania - ";
        
        boolean first = true;
        for(Entry<String, Integer> result : this.votes.entrySet()) {
            if(!first) {
                results += ChatColor.YELLOW + ", ";
            }
            
            results += ChatColor.RED + result.getKey() + ChatColor.YELLOW + ": " + ChatColor.GREEN + result.getValue();
        }
        
        Bukkit.broadcastMessage(results);
    }

    public EventsAllowedResult checkIfAllowedToUseEvents(CommandSender cs) {
        if(cs instanceof ConsoleCommandSender) {
            return EventsAllowedResult.Allowed();
        }
        
        if(!(cs instanceof Player)) {
            return EventsAllowedResult.NotAllowed();
        }
        
        Player player = (Player)cs;
        
        try
        {
            FileConfiguration playerEventsConfig = getPlayerEventsConfig(player.getName());
        
            boolean isBanned = playerEventsConfig.getBoolean("banned", false);
            if(isBanned) {
                String reason = playerEventsConfig.getString("ban_reason", null);
                return EventsAllowedResult.NotAllowed(reason);
            }
        } catch(Exception ex) {
            Logger.getLogger(PluginApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return EventsAllowedResult.Allowed();
    }

    public boolean banFromEvents(String nick, String reason) {
        FileConfiguration config = getPlayerEventsConfig(nick);
        config.set("banned", true);
        config.set("ban_reason", reason);
        
        try {
            config.save(getPlayerConfigFile(nick));
            return true;
        } catch (IOException ex) {
            Logger.getLogger(PluginApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return false;
    }
    
    public boolean unbanFromEvents(String nick) {
        FileConfiguration config = getPlayerEventsConfig(nick);
        config.set("banned", false);
        config.set("ban_reason", "");
        
        try {
            config.save(getPlayerConfigFile(nick));
            return true;
        } catch (IOException ex) {
            Logger.getLogger(PluginApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return false;
    }

    private FileConfiguration getPlayerEventsConfig(String name) {
        return getConfiguration("players/" + name.toLowerCase() + ".yml");
    }

    private String getPlayerConfigFile(String nick) {
        File folder = this.plugin.getDataFolder();
        File file = new File(folder, "players/" + nick.toLowerCase() + ".yml");
        
        return file.getPath();
    }
    
    private class ContestBroadcast implements Runnable {
        private PluginApi api;
        
        public ContestBroadcast(PluginApi api) {
            this.api = api;
        }

        @Override
        public void run() {
            api.broadcastCurrentContestInProgress();
        }
    }
    
    private pl.panryba.mc.guilds.PluginApi guildsApi;
    private Plugin plugin;
    private ContestManager manager;
    private int currentBroadcastTask;
    private Map<String, String> voted;
    private Map<String, Integer> votes;
    
    private static final String EVENT_PREFIX = "[" + ChatColor.RED + "EVENT" + ChatColor.RESET + "] ";
        
    public PluginApi(Plugin plugin, pl.panryba.mc.guilds.PluginApi guildsApi) {
        this.guildsApi = guildsApi;
        this.manager = new ContestManager();
        this.plugin = plugin;
        this.votes = new HashMap<>();
        this.voted = new HashMap<>();
    }
    
    public boolean wasContestRunning() {
        return this.manager.getWasRunning();
    }    
    
    @Override
    public Plugin getPlugin() {
        return this.plugin;
    }

    @Override
    public void contestFinished(Contest contest) {
        if(contest != this.manager.getCurrent()) {
            return;
        }
        
        if(!stopCurrentContest()) {
            return;
        }
        
        this.plugin.getServer().broadcastMessage(this.getEventMessage(ChatColor.YELLOW + "Event zakonczyl sie! Oto wyniki:"));
        this.broadContestResult(contest);
    }
    
    @Override
    public void contestStarted(Contest contest) {
        if(contest != this.manager.getCurrent()) {
            return;
        }
        
        this.voted.clear();
        this.votes.clear();
        
        this.manager.start();
        String startedBy = contest.getStartedBy();
        
        this.plugin.getServer().broadcastMessage(this.getEventMessage(ChatColor.YELLOW + startedBy + " rozpoczal event! Oto zasady:"));
        this.broadcastMessages(contest.getRules());
        
        ContestBroadcast bc = new ContestBroadcast(this);
        this.currentBroadcastTask = this.plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, bc, 20 * 60, 20 * 60);        
    }

    public Contest getCurrentContest() {
        return this.manager.getCurrent();
    }

    public void sendMessages(CommandSender cs, String[] messages) {
        cs.sendMessage(messages);
    }

    public boolean getContestRunning() {
        return this.manager.getContestRunning();
    }

    public Contest newSkirmish(CommandSender startedBy) {
        return new Skirmish(this.plugin, this, this.guildsApi, startedBy);
    }
    
    public Contest newParkour(CommandSender startedBy) {
        return new Parkour(plugin, startedBy);
    }
    
    public Contest newFindPlace(Player startedBy) {
        return new FindPlace(plugin, this, startedBy);
    }
    
    public Contest newOx(CommandSender startedBy) {
        return new OX(startedBy);
    }

    public boolean startContest(CommandSender cs, Contest contest, String[] args) {
        boolean result = this.manager.setCurrent(contest);
        
        if(result) {
            contest.initialize(cs, this, args);
        }
        
        return result;
    }

    public boolean stopCurrentContest() {
        this.plugin.getServer().getScheduler().cancelTask(this.currentBroadcastTask);
        boolean result = this.manager.stopCurrent();
        
        return result;
    }
    
    public void broadcastContestStop(CommandSender cs, Contest currentContest) {
        this.plugin.getServer().broadcastMessage(this.getEventMessage(ChatColor.YELLOW + cs.getName() + " zakonczyl event! Oto wyniki:"));
        this.broadContestResult(currentContest);
    }
    
    private void broadContestResult(Contest contest) {
        this.broadcastMessages(contest.getResults());
    }

    private void broadcastCurrentContestInProgress() {
        Contest contest = this.manager.getCurrent();
        if(contest == null) {
            return;
        }
        
        this.plugin.getServer().broadcastMessage(this.getEventMessage(ChatColor.YELLOW + "Na serwerze trwa event " + contest.getName() + ". Szczegoly - wpisz " + ChatColor.GREEN + "/event"));
    }    
    
    private void broadcastMessages(String[] messages) {
        for(Player player : this.plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(messages);
        }
    }

    public String getEventMessage(String string) {
        return EVENT_PREFIX + string;
    }

    @Override
    public FileConfiguration getConfiguration(String name) {
        File folder = this.plugin.getDataFolder();
        File file = new File(folder, name);
        
        YamlConfiguration config = new YamlConfiguration();
        
        try {
            config.load(file);
        } catch (FileNotFoundException ex) {
        } catch (IOException ex) {
            Logger.getLogger(PluginApi.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidConfigurationException ex) {
            Logger.getLogger(PluginApi.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return config;
    }

    public void sendToOthers(Player excluded, String message) {
        for(Player player : plugin.getServer().getOnlinePlayers()) {
            if(player.getName().equals(excluded.getName()))
                continue;

            player.sendMessage(message);
        }
    }

    public void sendToAll(String message) {
        for(Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }


}
