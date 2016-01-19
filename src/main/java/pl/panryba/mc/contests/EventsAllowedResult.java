/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests;

/**
 *
 * @author PanRyba.pl
 */
public class EventsAllowedResult {
    private boolean allowed;
    private String reason;
    
    public static EventsAllowedResult NotAllowed() {
        return EventsAllowedResult.NotAllowed(null);
    }
    
    public static EventsAllowedResult NotAllowed(String reason) {
        EventsAllowedResult res = new EventsAllowedResult();
        res.allowed = false;
        res.reason = reason;
        
        return res;
    }
    
    public static EventsAllowedResult Allowed() {
        EventsAllowedResult res = new EventsAllowedResult();
        res.allowed = true;
        return res;
    }

    public boolean getAllowed() {
        return this.allowed;
    }

    public String getReason() {
        return this.reason;
    }
}
