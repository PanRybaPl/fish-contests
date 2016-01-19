/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests.modes.OXMode;

import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Bukkit;

/**
 *
 * @author PanRyba.pl
 */
public class OXQuestion {

    public static OXQuestion deserialize(Map<String, Object> map) {
        try {
            String question = (String) map.get("q");
            boolean answer = (boolean) map.get("a");
            
            OXQuestion q = new OXQuestion(question, answer);
            return q;
        } catch(Exception ex) {
            Bukkit.getLogger().info("Reading question failed: " + ex);
            
            if(map != null) {
                for(Entry<String, Object> entry : map.entrySet()) {
                    Bukkit.getLogger().info(entry.getKey() + ": " + entry.getValue());
                }
            }
            
            throw ex;
        }
    }
    private String value;
    private boolean answer;

    public OXQuestion(String value, boolean answer) {
        this.value = value;
        this.answer = answer;
    }

    public String getValue() {
        return this.value;
    }

    public boolean getAnswer() {
        return this.answer;
    }
}