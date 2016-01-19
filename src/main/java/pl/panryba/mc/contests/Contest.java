/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests;

import org.bukkit.command.CommandSender;

/**
 *
 * @author PanRyba.pl
 */
public interface Contest {
    void Start();
    void Stop();
    
    String[] getResults();
    String[] getRules();
    
    void initialize(CommandSender cs, ContestListener listener, String[] args);
    
    String getName();
    String getStartedBy();
    boolean handleCommand(CommandSender cs, String cmnd, String[] args);
}
