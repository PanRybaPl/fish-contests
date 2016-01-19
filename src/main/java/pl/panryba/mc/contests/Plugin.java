/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests;

import org.bukkit.plugin.java.JavaPlugin;
import pl.panryba.mc.contests.commands.ContestCommand;

/**
 *
 * @author PanRyba.pl
 */
public class Plugin extends JavaPlugin {

    @Override
    public void onEnable() {
        PluginApi api = new PluginApi(this, pl.panryba.mc.guilds.PluginApi.getInstance());
        getCommand("event").setExecutor(new ContestCommand(api));
    }
    
}
