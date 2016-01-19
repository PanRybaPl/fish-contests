/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests.modes;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalWorld;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import pl.panryba.mc.contests.Contest;
import pl.panryba.mc.contests.ContestListener;
import pl.panryba.mc.contests.ItemLoreHelper;
import pl.panryba.mc.contests.PluginApi;
import pl.panryba.mc.contests.modes.OXMode.OXQuestion;
import pl.panryba.mc.guilds.StringUtils;

/**
 *
 * @author PanRyba.pl
 */
public class OX implements Contest {

    private String yesRegionName;
    private String noRegionName;
    private String boardRegionName;
    private String wallRegionName;
    private Location spawnLocation;
    private Location spectateLocation;
    private World world;
    private Map<String, Integer> points;
    private Set<String> players;
    private Set<String> losers;
    private Set<String> playing;
    private OXQuestion currentQuestion;
    private List<OXQuestion> questions;
    private boolean running;
    private boolean started;
    private boolean finished;
    private String startedByName;
    private ContestListener listener;
    private boolean scorePublished;
    private boolean prizesGiven;
    private BukkitTask joinBoardcastTask;
    private BukkitTask startEventTask;
    private BukkitTask nextQuestionTask;
    private BukkitTask broadcastCurrentTask;
    private BukkitTask completeCurrentTask;

    private Location readLocation(ConfigurationSection section, String name) {
        ConfigurationSection localSection = section.getConfigurationSection(name);

        double x = localSection.getDouble("x");
        double y = localSection.getDouble("y");
        double z = localSection.getDouble("z");
        float pitch = (float) localSection.getDouble("pitch");
        float yaw = (float) localSection.getDouble("yaw");

        return new Location(this.world, x, y, z, yaw, pitch);
    }

    private List<OXQuestion> readQuestions(ConfigurationSection section) {
        List<OXQuestion> results = new ArrayList<>();

        List<Map<String, Object>> list = (List<Map<String, Object>>) section.getList("questions");
        for (Map<String, Object> map : list) {
            try {
                OXQuestion q = OXQuestion.deserialize(map);
                results.add(q);
            }
            catch(Exception ex) {
                // Ignore failures
            }
        }

        return results;
    }

    public OX(CommandSender startedBy) {
        this.startedByName = startedBy.getName();
        this.running = false;
        this.finished = false;
        this.questions = new ArrayList<>();

        if (startedBy instanceof Player) {
            Player player = (Player) startedBy;
            this.world = player.getWorld();
        }
    }

    @Override
    public void initialize(CommandSender cs, ContestListener listener, String[] args) {
        this.listener = listener;


        if (args.length == 0) {
            return;
        }

        switch (args[0]) {
            case "autostart": {
                if (args.length != 2) {
                    cs.sendMessage("Musisz podac: [nazwe areny]");
                    return;
                }

                String arenaName = args[1];
                loadArena(cs, arenaName);
                loadQuestions(cs);

                autorun();
            }
            break;
        }
    }

    @Override
    public String getName() {
        return "OX";
    }

    @Override
    public String getStartedBy() {
        return this.startedByName;
    }

    private OXQuestion getCurrentQuestion() {
        return this.currentQuestion;
    }

    @Override
    public void Start() {
        this.running = true;
        this.finished = false;
    }

    @Override
    public void Stop() {
        this.running = false;
        this.finished = true;

        stopCurrentBroadcasting();
        stopJoinBroadcasting();
        completeCurrentCompletionTask();
        completeNextQuestionTask();
        completeStartEventTask();

        Bukkit.getScheduler().runTask(this.listener.getPlugin(), () -> OX.this.handlePrizeWinners());
    }
    
    private int getWinnerPoints() {
        int max = 0;
        for (int value : this.points.values()) {
            max = Math.max(max, value);
        }
        return max;
    }
    
    private List<String> getWinners() {
        int max = this.getWinnerPoints();
        List<String> currentWinners = new ArrayList<>();
        
        if (max > 0) {
            for (String name : this.points.keySet()) {
                if (this.points.get(name) == max && this.playing.contains(name)) {
                    currentWinners.add(name);
                }
            }
        }
        
        return currentWinners;
    }

    @Override
    public String[] getResults() {
        List<String> currentWinners = this.getWinners();
        
        if (currentWinners.isEmpty()) {
            return new String[]{ChatColor.YELLOW + "Brak zwyciezcow"};
        } else {
            String[] namesArr = new String[currentWinners.size()];
            currentWinners.toArray(namesArr);
            
            String winnersTitle;
            if(currentWinners.size() == 1)
                winnersTitle = "Zwyciezca";
            else
                winnersTitle = "Zwyciezcy";
            
            return new String[]{ChatColor.YELLOW + winnersTitle + " (" + ChatColor.GREEN + getWinnerPoints() + " pkt." + ChatColor.YELLOW + "): " + ChatColor.RED + StringUtils.join(namesArr)};
        }
    }

    @Override
    public String[] getRules() {
        return new String[]{
            ChatColor.YELLOW + "Za kazda poprawna odpowiedz " + ChatColor.GREEN + "otrzymujesz 1 punkt",
            ChatColor.YELLOW + "Wygrywaja gracze, ktorzy zdobeda najwieksza liczbe punktow",
        };
    }

    @Override
    public boolean handleCommand(CommandSender cs, String cmnd, String[] args) {
        if (cs instanceof Player) {
            Player player = (Player) cs;

            if (cmnd.equalsIgnoreCase("dolacz")) {
                if (!this.running) {
                    if (this.finished) {
                        player.sendMessage(ChatColor.RED + "Event zostal zakonczony wiec nie mozesz do niego dolaczyc");
                        return true;
                    }

                    return false;
                }
                
                if(this.started) {
                    player.sendMessage(ChatColor.RED + "Event juz sie rozpoczal i nie mozesz do niego dolaczyc");
                    return true;
                }

                addToEvent(player);
                return true;
            }

            if (cmnd.equalsIgnoreCase("opusc")) {
                if (!this.running) {
                    if (this.finished) {
                        player.sendMessage(ChatColor.RED + "Event zostal zakonczony wiec nie bierzesz w nim udzialu");
                        return true;
                    }

                    return false;
                }

                removeFromEvent(player);
                return true;
            }

            if (cmnd.equalsIgnoreCase("pomoc")) {
                player.sendMessage(new String[]{
                    ChatColor.RED + "/event dolacz " + ChatColor.YELLOW + "- dolaczenie do eventu OX",
                    ChatColor.RED + "/event opusc " + ChatColor.YELLOW + "- opuszczenie eventu"
                });
            }
        }

        if (!cs.getName().equals(this.startedByName)) {
            return false;
        }

        if (cmnd.equalsIgnoreCase("pomoc")) {
            cs.sendMessage(new String[]{
                ChatColor.RED + "/event start [nazwa areny] " + ChatColor.YELLOW + "- uruchomienie eventu na podanej arenie",
                ChatColor.RED + "/event dalej [odpowiedz (true|false)] [pytanie] " + ChatColor.YELLOW + "- ogloszenie kolejnego pytania",
                ChatColor.RED + "/event wynik " + ChatColor.YELLOW + "- ogloszenie wynikow dla aktualnego pytania",
                ChatColor.RED + "/event pytanie " + ChatColor.YELLOW + "- przypomnienie aktualnego pytania"
            });
            return true;
        }

        if (cmnd.equalsIgnoreCase("start")) {
            if (args.length < 1) {
                cs.sendMessage("Musisz podac: [nazwe areny]");
                return true;
            }

            String arenaName = args[0];
            loadArena(cs, arenaName);
            prepareForStart();
            redrawBoard();

            this.listener.contestStarted(this);
            return true;
        }

        if (cmnd.equalsIgnoreCase("pytanie")) {
            OXQuestion q = getCurrentQuestion();
            if (q == null) {
                cs.sendMessage("Brak aktualnego pytania");
                return true;
            }

            this.broadcastQuestion(q);

            return true;
        }

        if (cmnd.equalsIgnoreCase("wynik")) {
            if (this.scorePublished) {
                cs.sendMessage("Wyniki dla tego pytania zostaly juz ogloszone");
                return true;
            }

            if (this.playing.isEmpty()) {
                cs.sendMessage("W tej chwili nikt nie bierze udzialu w evencie");
                return true;
            }

            if (this.getCurrentQuestion() == null) {
                cs.sendMessage("Brak aktualnego pytania");
                return true;
            }

            this.publishCurrentResults();

            return true;
        }

        if (cmnd.equalsIgnoreCase("dalej")) {
            if (args.length < 2) {
                cs.sendMessage("Musisz podac: [poprawna odpowiedz] [pytanie]");
                return true;
            }

            this.scorePublished = false;

            String q = StringUtils.join(args, 1);
            boolean a = Boolean.parseBoolean(args[0]);

            OXQuestion newQuestion = new OXQuestion(q, a);
            startNextQuestion(newQuestion);

            return true;
        }

        return false;
    }

    private void redrawBoard() {
        if (!this.boardRegionName.isEmpty()) {
            fillRegion(boardRegionName, Material.AIR, 0);
            wallRegion(boardRegionName, Material.GLASS, 0);
        }

        bottomRegion(yesRegionName, Material.WOOL, 5);
        bottomRegion(noRegionName, Material.WOOL, 14);
        fillRegion(wallRegionName, Material.AIR, 0);
    }

    private Set<String> getPlayersInRegion(String name) {
        ProtectedRegion region = getRegion(name);
        Set<String> result = new HashSet<>();

        for (Player player : this.world.getPlayers()) {
            if (!this.playing.contains(player.getName())) {
                continue;
            }

            Location playerLoc = player.getLocation();
            Vector playerVec = new Vector(playerLoc.getX(), playerLoc.getY(), playerLoc.getZ());

            if (region.contains(playerVec)) {
                result.add(player.getName());
            }
        }

        return result;
    }

    private ProtectedRegion getRegion(String name) {
        RegionManager rm = WGBukkit.getRegionManager(this.world);
        return rm.getRegion(name);
    }

    private void bottomRegion(String name, Material material, int data) {
        ProtectedRegion region = getRegion(name);

        LocalWorld lw = new BukkitWorld(this.world);
        EditSession session = WorldEdit.getInstance().getEditSessionFactory().getEditSession(lw, -1);

        BlockVector fromPoint = region.getMinimumPoint();
        BlockVector toPoint = region.getMaximumPoint();

        int minY = Math.min(fromPoint.getBlockY(), toPoint.getBlockY());

        Vector from = new Vector(fromPoint.getBlockX(), minY, fromPoint.getBlockZ());
        Vector to = new Vector(toPoint.getBlockX(), minY, toPoint.getBlockZ());

        Region weRegion = new CuboidRegion(lw, from, to);

        BaseBlock baseBlock = new BaseBlock(material.getId(), data);
        try {
            session.setBlocks(weRegion, baseBlock);
        } catch (MaxChangedBlocksException ex) {
            Logger.getLogger(OX.class.getName()).log(Level.SEVERE, null, ex);
        }

        session.flushQueue();
    }

    private void wallRegion(String name, Material material, int data) {
        ProtectedRegion region = getRegion(name);

        LocalWorld lw = new BukkitWorld(this.world);
        EditSession session = WorldEdit.getInstance().getEditSessionFactory().getEditSession(lw, -1);

        BlockVector fromPoint = region.getMinimumPoint();
        BlockVector toPoint = region.getMaximumPoint();

        Vector from = new Vector(fromPoint.getBlockX(), fromPoint.getBlockY(), fromPoint.getBlockZ());
        Vector to = new Vector(toPoint.getBlockX(), toPoint.getBlockY(), toPoint.getBlockZ());

        Region weRegion = new CuboidRegion(lw, from, to);

        BaseBlock baseBlock = new BaseBlock(material.getId(), data);
        try {
            session.makeCuboidFaces(weRegion, baseBlock);
        } catch (MaxChangedBlocksException ex) {
            Logger.getLogger(OX.class.getName()).log(Level.SEVERE, null, ex);
        }

        session.flushQueue();
    }

    private void fillRegion(String name, Material material, int data) {
        ProtectedRegion region = getRegion(name);

        LocalWorld lw = new BukkitWorld(this.world);
        EditSession session = WorldEdit.getInstance().getEditSessionFactory().getEditSession(lw, -1);

        BlockVector fromPoint = region.getMinimumPoint();
        BlockVector toPoint = region.getMaximumPoint();

        Vector from = new Vector(fromPoint.getBlockX(), fromPoint.getBlockY(), fromPoint.getBlockZ());
        Vector to = new Vector(toPoint.getBlockX(), toPoint.getBlockY(), toPoint.getBlockZ());

        Region weRegion = new CuboidRegion(lw, from, to);

        BaseBlock baseBlock = new BaseBlock(material.getId(), data);
        try {
            session.setBlocks(weRegion, baseBlock);
        } catch (MaxChangedBlocksException ex) {
            Logger.getLogger(OX.class.getName()).log(Level.SEVERE, null, ex);
        }

        session.flushQueue();
    }

    private void addToEvent(Player player) {
        String name = player.getName();

        if (this.losers.contains(name)) {
            teleportToSpectatePoint(player);
            player.sendMessage(ChatColor.RED + "Juz wziales udzial w tym evencie i przegrales. Zostales przeniesiony na widownie.");
            return;
        }

        if (this.players.contains(name)) {
            player.sendMessage(ChatColor.RED + "Juz dolaczyles do tego eventu");
            return;
        }

        this.players.add(name);
        this.playing.add(name);
        this.points.put(name, 0);
        
        Bukkit.getLogger().info("OX: " + name + " joined");

        player.teleport(spawnLocation);
        player.sendMessage("[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Dolaczyles do eventu " + ChatColor.GREEN + "O" + ChatColor.RED + "X" + ChatColor.YELLOW + "! Liczba uczestnikow: " + ChatColor.GREEN + this.players.size());

        final String otherMsg = "[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " + ChatColor.RED + player.getName() + ChatColor.YELLOW + " dolaczyl do eventu! Liczba uczestnikow: " + ChatColor.GREEN + this.players.size();
        forOtherPlayers(player, otherPlayer -> otherPlayer.sendMessage(otherMsg));
    }

    private void handleLoser(String name) {
        this.losers.add(name);
        this.playing.remove(name);

        Player loser = Bukkit.getPlayerExact(name);
        if(loser == null) {
            return;
        }

        teleportToSpectatePoint(loser);

        int current = this.points.get(name);
        loser.sendMessage("[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Twoja odpowiedz byla " + ChatColor.RED + "niepoprawna" + ChatColor.YELLOW + ". Zakonczyles udzial w evencie z wynikiem " + ChatColor.GREEN + current + " pkt.");
    }

    private void handleWinner(String name) {
        int current = this.points.get(name) + 1;
        this.points.put(name, current);
        
        Player winner = Bukkit.getPlayerExact(name);
        if(winner == null) {
            return;
        }
        
        winner.sendMessage("[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " +
                ChatColor.RED + "Brawo!"  + ChatColor.YELLOW + "Twoja odpowiedz jest " + ChatColor.GREEN + "poprawna" +
                ChatColor.YELLOW + " - zyskujesz " + ChatColor.GREEN + "1 punkt!" + ChatColor.YELLOW + " Masz teraz " + ChatColor.GREEN + current + " pkt.");
    }

    private void forPlayingPlayers(PlayerCallback cb) {
        if (cb == null) {
            return;
        }

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (!this.playing.contains(player.getName())) {
                continue;
            }

            cb.onPlayer(player);
        }
    }

    private void forNonPlayingPlayers(PlayerCallback cb) {
        if (cb == null) {
            return;
        }

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (this.playing.contains(player.getName())) {
                continue;
            }

            cb.onPlayer(player);
        }
    }

    private void forOtherPlayers(Player player, PlayerCallback cb) {
        if (cb == null) {
            return;
        }

        for (Player otherPlayer : Bukkit.getServer().getOnlinePlayers()) {
            if (otherPlayer.getName().equals(player.getName())) {
                continue;
            }

            cb.onPlayer(otherPlayer);
        }
    }

    private void removeFromEvent(Player player) {
        String name = player.getName();

        if (!this.players.contains(name)) {
            player.sendMessage(ChatColor.RED + "Nie bierzesz udzialu w evencie");
            return;
        }

        if (this.playing.contains(name)) {
            this.playing.remove(name);
        }

        int score = this.points.get(name);

        player.sendMessage(ChatColor.RED + "Opusciles event z wynikiem " + ChatColor.GREEN + score + " pkt. " + ChatColor.YELLOW + "Zostaniesz przeniesiony na widownie.");
        teleportToSpectatePoint(player);
    }

    private void loadArena(CommandSender cs, String arenaName) {
        FileConfiguration config = this.listener.getConfiguration("ox_arenas.yml");
        ConfigurationSection section = config.getConfigurationSection(arenaName);

        this.world = Bukkit.getWorld(section.getString("world"));
        this.boardRegionName = section.getString("board");
        this.wallRegionName = section.getString("wall");
        this.yesRegionName = section.getString("true");
        this.noRegionName = section.getString("false");

        this.spawnLocation = readLocation(section, "spawn");
        this.spectateLocation = readLocation(section, "spectate");

        cs.sendMessage(new String[]{
            "Ustawiles nastepujace regiony: plansza - " + boardRegionName
            + ", sciana - " + wallRegionName
            + ", tak - " + yesRegionName
            + ", nie - " + noRegionName
        });
    }

    private void loadQuestions(CommandSender cs) {
        FileConfiguration config = this.listener.getConfiguration("ox_questions.yml");

        this.questions = readQuestions(config);
        cs.sendMessage("Zaladowano " + this.questions.size() + " pytan");
    }

    private void startNextQuestion(OXQuestion nextQuestion) {
        Bukkit.getLogger().info("OX: " + nextQuestion.getValue() + " - " + nextQuestion.getAnswer());
        
        scorePublished = false;
        currentQuestion = nextQuestion;
        
        redrawBoard();

        broadcastQuestion(currentQuestion);
        forPlayingPlayers(player -> player.teleport(spawnLocation));
    }

    private void broadcastJoin() {

        final String playingMsg = "[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Za chwile zaczynamy event - przygotuj sie!";
        sendToAllPlayingPlayers(playingMsg);

        sendToAllNonPlayingPlayers(new String[] {
            "[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " +
                ChatColor.YELLOW + "Dolacz do eventu OX! Wpisz " + ChatColor.GREEN + "/event dolacz"
        });
    }

    private void broadcastCurrentQuestion() {
        OXQuestion q = getCurrentQuestion();
        if (q == null) {
            return;
        }

        this.broadcastQuestion(q);
    }

    private void broadcastQuestion(OXQuestion q) {
        String question = q.getValue();
        final String msg = "[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] "
                + ChatColor.YELLOW + "Aktualne pytanie: " + ChatColor.RED + question
                + " (" + ChatColor.GREEN + "TAK" + ChatColor.YELLOW + " / " + ChatColor.RED + "NIE" + ChatColor.YELLOW + ")";
        
        sendToAllPlayingPlayers(msg);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Automation
    ////////////////////////////////////////////////////////////////////////////
    private void autocompleteCurrentQuestion() {
        stopCurrentBroadcasting();
        completeCurrentCompletionTask();

        this.publishCurrentResults();
        if(!this.finished) {
            final String msg = "[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " + ChatColor.YELLOW + " Za chwile ruszamy dalej!";
            sendToAllPlayingPlayers(msg);
            this.autowaitForNextQuestion(10);
        }
    }

    private void publishCurrentResults() {
        if (this.scorePublished) {
            return;
        }

        this.scorePublished = true;

        OXQuestion q = getCurrentQuestion();
        if (q == null) {
            return;
        }

        fillRegion(wallRegionName, Material.GLASS, 0);
        if (q.getAnswer()) {
            bottomRegion(yesRegionName, Material.DIAMOND_BLOCK, 0);
            bottomRegion(noRegionName, Material.OBSIDIAN, 0);
        } else {
            bottomRegion(noRegionName, Material.DIAMOND_BLOCK, 0);
            bottomRegion(yesRegionName, Material.OBSIDIAN, 0);
        }

        Set<String> yesPlayers = getPlayersInRegion(yesRegionName);
        int yesCount = yesPlayers.size();
        
        Set<String> noPlayers = getPlayersInRegion(noRegionName);
        int noCount = noPlayers.size();

        String msg = "[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Odpowiedzi - "
                + ChatColor.GREEN + "TAK" + ChatColor.YELLOW + ": " + yesCount + ", "
                + ChatColor.RED + "NIE" + ChatColor.YELLOW + ": " + noCount;
        
        Bukkit.getLogger().info(msg);
        
        sendToAllPlayingPlayers(msg);

        Set<String> localWinners;
        Set<String> localLosers;

        if (q.getAnswer()) {
            localLosers = noPlayers;
            localWinners = yesPlayers;
        } else {
            localLosers = yesPlayers;
            localWinners = noPlayers;
        }
        
        logPlayers("winners", localWinners);
        logPlayers("losers", localLosers);
        
        for(String playingPlayer : this.playing) {
            if(!localWinners.contains(playingPlayer)) {
                localLosers.add(playingPlayer);
            }
        }

        localLosers.forEach(this::handleLoser);
        localWinners.forEach(this::handleWinner);

        if (this.playing.size() < 2) {
            completeEvent();
        }
    }

    private void autowaitForNextQuestion(int secondsToWait) {
        this.nextQuestionTask = Bukkit.getScheduler().runTaskLater(this.listener.getPlugin(), new NextQuestionTask(this), secondsToWait * 20);
    }

    private void sendToAllPlayingPlayers(final String msg) {
        forPlayingPlayers(player -> player.sendMessage(msg));
    }

    private void sendToAllNonPlayingPlayers(final String[] msgs) {
        forNonPlayingPlayers(player -> {
            for(String msg : msgs) {
                player.sendMessage(msg);
            }
        });
    }

    private void startCurrentBroadcasting(int secondsBetweenRepeat) {
        this.broadcastCurrentTask = Bukkit.getScheduler().runTaskTimer(this.listener.getPlugin(), new BroadcastCurrentQuestion(this), secondsBetweenRepeat * 20, secondsBetweenRepeat * 20);
    }

    private void scheduleCompleteCurrent(int secondsBeforeCompletion) {
        this.completeCurrentTask = Bukkit.getScheduler().runTaskLater(this.listener.getPlugin(), new CompleteCurrentQuestion(this), secondsBeforeCompletion * 20);
    }

    private void startJoinBroadcasting(int secondsBeforeRepeat) {
        this.joinBoardcastTask = Bukkit.getScheduler().runTaskTimer(this.listener.getPlugin(), new JoinBroadcaster(this), 0, secondsBeforeRepeat * 20);
    }

    private void scheduleEventStart(int secondsToStart) {
        this.startEventTask = Bukkit.getScheduler().runTaskLater(this.listener.getPlugin(), new StartEvent(this), secondsToStart * 20);
    }

    private void completeEvent() {
        this.listener.contestFinished(this);
    }

    private void stopCurrentBroadcasting() {
        if (this.broadcastCurrentTask == null) {
            return;
        }

        this.broadcastCurrentTask.cancel();
        this.broadcastCurrentTask = null;
    }

    private void completeNextQuestionTask() {
        if (this.nextQuestionTask == null) {
            return;
        }

        this.nextQuestionTask.cancel();
        this.nextQuestionTask = null;
    }

    private void completeStartEventTask() {
        if (this.startEventTask == null) {
            return;
        }

        this.startEventTask.cancel();
        this.startEventTask = null;
    }

    private void completeCurrentCompletionTask() {
        if (this.completeCurrentTask == null) {
            return;
        }

        this.completeCurrentTask.cancel();
        this.completeCurrentTask = null;
    }

    private void handlePrizeWinners() {
        if (prizesGiven) {
            return;
        }

        this.prizesGiven = true;

        List<String> winnerNames = this.getWinners();
        
        for (String winnerName : winnerNames) {
            try {
                Player winner = Bukkit.getPlayerExact(winnerName);
                
                if (winner == null) {
                    Bukkit.getLogger().info("OX winner not found: " + winnerName);
                    continue;
                }

                ItemStack prize = new ItemStack(Material.SPONGE, 1);

                int winnerPoints = this.points.get(winner.getName());
                ItemLoreHelper.addItemLore(prize, new String[]{"nagroda dla " + winner.getName(), "za wygranie eventu OX (" + winnerPoints + " pkt.)"});

                PlayerInventory inv = winner.getInventory();
                Map<Integer, ItemStack> failed = inv.addItem(prize);

                if (failed.isEmpty()) {
                    winner.sendMessage("[" + ChatColor.RED + "EVENT" + ChatColor.RESET + "] " + ChatColor.RED + "W Twoim plecaku zostala umieszczona nagroda, za wygranie eventu!");
                    Bukkit.getLogger().info("OX prize given to: " + winner.getName());
                } else {
                    winner.getWorld().dropItem(winner.getLocation(), prize);
                    winner.sendMessage("[" + ChatColor.RED + "EVENT" + ChatColor.RESET + "] " + ChatColor.RED + "Otrzymujesz nagrode, za wygranie eventu! (nie miesci sie ona w Twoim plecaku wiec szybko ja podnies!)");
                    Bukkit.getLogger().info("OX prize dropped for: " + winner.getName());
                }

                teleportToSpectatePoint(winner);
            }
            catch(Exception ex) {
                Logger.getLogger(PluginApi.class.getName()).log(Level.SEVERE, null, ex);                
            }
        }
    }

    private void teleportToSpectatePoint(Player player) {
        player.teleport(this.spectateLocation);
    }

    private void prepareForStart() {
        this.players = new HashSet<>();
        this.losers = new HashSet<>();
        this.points = new HashMap<>();
        this.playing = new HashSet<>();        
    }

    private void logPlayers(String title, Set<String> names) {
        String res = "";
        
        for(String name : names) {
            if(res.length() > 0) {
                res += ", ";
            }
            
            res += name;
        }
        
        Bukkit.getLogger().info("OX " + title + ": " + res);
    }

    private class BroadcastCurrentQuestion implements Runnable {

        private final OX ox;

        public BroadcastCurrentQuestion(OX ox) {
            this.ox = ox;
        }

        @Override
        public void run() {
            ox.broadcastCurrentQuestion();
        }
    }

    private class CompleteCurrentQuestion implements Runnable {

        private OX ox;

        public CompleteCurrentQuestion(OX ox) {
            this.ox = ox;
        }

        @Override
        public void run() {
            ox.autocompleteCurrentQuestion();
        }
    }

    private void autoNextQuestion() {
        completeNextQuestionTask();

        if (this.questions.isEmpty()) {
            completeEvent();
            return;
        }

        Random rand = new Random();
        int questionIndex = rand.nextInt(this.questions.size());

        OXQuestion q = this.questions.remove(questionIndex);
        startNextQuestion(q);

        startCurrentBroadcasting(5);
        scheduleCompleteCurrent(15);
    }

    private class NextQuestionTask implements Runnable {

        private final OX ox;

        public NextQuestionTask(OX ox) {
            this.ox = ox;
        }

        @Override
        public void run() {
            this.ox.autoNextQuestion();
        }
    }

    private void autostartEvent() {
        completeStartEventTask();

        if(this.players.size() < 2) {
            final String msg = "[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Event zostaje anulowany - nie zebrano wystarczajacej liczby graczy (przynajmniej 2)";
            sendToAllPlayingPlayers(msg);
            this.listener.contestFinished(this);
            return;
        }
        
        this.started = true;
        
        final String msg = "[" + ChatColor.RED + "EVENT OX" + ChatColor.RESET + "] " + ChatColor.YELLOW + "Rozpoczynamy event OX!";
        sendToAllPlayingPlayers(msg);

        this.autowaitForNextQuestion(10);
    }

    private class JoinBroadcaster implements Runnable {

        private final OX ox;

        public JoinBroadcaster(OX ox) {
            this.ox = ox;
        }

        @Override
        public void run() {
            ox.broadcastJoin();
        }
    }

    private void stopJoinBroadcasting() {
        if (this.joinBoardcastTask == null) {
            return;
        }

        this.joinBoardcastTask.cancel();
        this.joinBoardcastTask = null;
    }

    private class StartEvent implements Runnable {

        private final OX ox;

        public StartEvent(OX ox) {
            this.ox = ox;
        }

        @Override
        public void run() {
            ox.stopJoinBroadcasting();
            ox.autostartEvent();
        }
    }

    private void autorun() {
        prepareForStart();
        this.listener.contestStarted(this);
        
        final OX ox = this;
        
        // Need to run as task as it fails from async calls
        Bukkit.getScheduler().runTask(this.listener.getPlugin(), () -> ox.redrawBoard());
        
        startJoinBroadcasting(5);
        scheduleEventStart(60);
    }
}
