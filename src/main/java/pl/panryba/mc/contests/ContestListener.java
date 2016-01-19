/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests;

import org.bukkit.configuration.file.FileConfiguration;

/**
 *
 * @author PanRyba.pl
 */
public interface ContestListener {
    public Plugin getPlugin();
    public void contestStarted(Contest contest);
    public void contestFinished(Contest contest);
    public FileConfiguration getConfiguration(String name);
}
