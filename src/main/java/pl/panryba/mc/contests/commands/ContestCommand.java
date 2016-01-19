/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests.commands;

import pl.panryba.mc.contests.EventsAllowedResult;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.panryba.mc.contests.Contest;
import pl.panryba.mc.contests.PluginApi;
import pl.panryba.mc.guilds.StringUtils;

/**
 *
 * @author PanRyba.pl
 */
public class ContestCommand implements CommandExecutor {

    private PluginApi api;

    public ContestCommand(PluginApi api) {
        this.api = api;
    }

    private boolean handleStartEvent(CommandSender cs, String[] strings) {
        if (!cs.hasPermission("contests.start")) {
            cs.sendMessage("Nie posiadasz wystarczajacych uprawnien");
            return true;
        }

        if (this.api.getContestRunning()) {
            cs.sendMessage("Event juz trwa. Musisz go najpierw zakonczyc.");
            return true;
        }

        Contest contest = null;

        String eventType;
        if (strings.length == 1) {
            eventType = "rzeznia";
        } else {
            eventType = strings[1];
        }

        switch (eventType) {
            case "rzeznia": {
                contest = this.api.newSkirmish(cs);
            }
            break;
            case "poszukiwania": {
                if (!(cs instanceof Player)) {
                    cs.sendMessage("Event rzeznia moze zostac rozpoczety wylacznie przez gracza");
                    return true;
                }

                Player player = (Player) cs;
                contest = this.api.newFindPlace(player);
            }
            break;
            case "ox": {
                contest = this.api.newOx(cs);
            }
            break;
            case "parkour": {
                contest = this.api.newParkour(cs);
            }
            break;
        }

        if (contest == null) {
            cs.sendMessage("Nieznany lub niedozwolony tryb eventu");
            return false;
        }

        String[] args = new String[strings.length - 2];
        for (int i = 2; i < strings.length; i++) {
            args[i - 2] = strings[i];
        }

        if (!this.api.startContest(cs, contest, args)) {
            cs.sendMessage("Event nie moze zostac rozpoczety");
            return true;
        }

        cs.sendMessage("Utworzyles nowy event - " + contest.getName());
        return true;
    }

    private boolean handleStopEvent(CommandSender cs, String[] strings) {
        if (!cs.hasPermission("contests.stop")) {
            cs.sendMessage("Nie posiadasz wystarczajacych uprawnien");
            return true;
        }

        if (!this.api.getContestRunning()) {
            cs.sendMessage("W tej chwili nie trwa zaden event.");
            return true;
        }

        Contest currentContest = this.api.getCurrentContest();

        if (!this.api.stopCurrentContest()) {
            cs.sendMessage("Event nie mogl zostac zakonczony");
            return true;
        }

        this.api.broadcastContestStop(cs, currentContest);
        return true;
    }

    private boolean handleEventCommand(CommandSender cs, String[] strings) {
        Contest contest = api.getCurrentContest();
        if (contest == null) {
            return false;
        }

        String cmnd = strings[0];
        String[] args = new String[strings.length - 1];

        for (int i = 1; i < strings.length; i++) {
            args[i - 1] = strings[i];
        }

        if (!contest.handleCommand(cs, cmnd, args)) {
            cs.sendMessage("Brak takiej opcji dla polecenia /event");
        }

        return true;
    }

    @Override
    public boolean onCommand(CommandSender cs, Command cmnd, String string, String[] strings) {
        if (!(cmnd.getName().equalsIgnoreCase("event"))) {
            return false;
        }

        EventsAllowedResult result = api.checkIfAllowedToUseEvents(cs);

        if (!result.getAllowed()) {
            String reason = result.getReason();

            if (reason == null || reason.equals("")) {
                cs.sendMessage(ChatColor.RED + "Twoj dostep do eventow zostal zablokowany");
            } else {
                cs.sendMessage(ChatColor.RED + "Twoj dostep do eventow zostal zablokowany. Powod: " + ChatColor.YELLOW + reason);
            }

            return true;
        }

        if (strings.length == 0) {
            Contest contest = api.getCurrentContest();
            if (contest == null || !api.wasContestRunning()) {
                cs.sendMessage(this.api.getEventMessage(ChatColor.YELLOW + "Brak informacji o evencie"));
                return true;
            }

            if (this.api.getContestRunning()) {
                cs.sendMessage(this.api.getEventMessage(ChatColor.YELLOW + "Informacje o trwajacym evencie (" + contest.getName() +"):"));
                this.api.sendMessages(cs, contest.getRules());
                
                cs.sendMessage(ChatColor.RED + "Aktualne wyniki:");
            } else {
                cs.sendMessage(this.api.getEventMessage(ChatColor.RED + "Wyniki ostatniego eventu:"));
            }
            
            this.api.sendMessages(cs, contest.getResults());            
        } else {
            switch (strings[0]) {
                case "ban":
                    return handleBan(cs, strings);
                case "unban":
                    return handleUnban(cs, strings);
                case "nowy":
                    return handleStartEvent(cs, strings);
                case "zakoncz":
                    return handleStopEvent(cs, strings);
                case "glosuj":
                    return handleVoteEvent(cs, strings);
                default:
                    if (api.getCurrentContest() != null) {
                        return handleEventCommand(cs, strings);
                    } else {
                        return false;
                    }
            }
        }

        return true;
    }

    private boolean handleVoteEvent(CommandSender cs, String[] strings) {
        if (!(cs instanceof Player)) {
            cs.sendMessage("Nie mozesz glosowac z konsoli");
            return true;
        }

        if (strings.length < 2) {
            cs.sendMessage("Musisz podac nazwe eventu, na ktory chcesz zaglosowac (OX, Pakour lub Rzeznia)");
            return true;
        }

        api.vote((Player) cs, strings[1]);

        return true;
    }

    private boolean handleBan(CommandSender cs, String[] strings) {
        if (!cs.hasPermission("contests.ban")) {
            cs.sendMessage("Nie posiadasz uprawnien do tego polecenia");
            return true;
        }

        if (strings.length < 2) {
            cs.sendMessage("/event ban <nick gracza> [powod]");
            return true;
        }

        String nick = strings[1];
        String reason;

        if (strings.length > 2) {
            reason = StringUtils.join(strings, 2);
        } else {
            reason = "";
        }

        api.banFromEvents(nick, reason);
        cs.sendMessage("Zbanowales " + ChatColor.RED + nick + ChatColor.RESET + " w eventach za " + ChatColor.YELLOW + reason);

        return true;
    }

    private boolean handleUnban(CommandSender cs, String[] strings) {
        if (!cs.hasPermission("contests.unban")) {
            cs.sendMessage("Nie posiadasz uprawnien do tego polecenia");
            return true;
        }

        if (strings.length < 2) {
            cs.sendMessage("/event ban <nick gracza> [powod]");
            return true;
        }

        String nick = strings[1];
        api.unbanFromEvents(nick);
        
        cs.sendMessage("Odbanowales " + ChatColor.RED + nick + ChatColor.RESET + " w eventach");
        
        return true;
    }
}