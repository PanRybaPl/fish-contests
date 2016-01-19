/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests;

/**
 *
 * @author PanRyba.pl
 */
public class ContestManager {
    private boolean running;
    private boolean wasRunning;
    private Contest current;
    
    public Contest getCurrent() {
        return this.current;
    }
    
    public boolean setCurrent(Contest contest) {
        if(running) {
            return false;
        }
        
        this.current = contest;
        this.wasRunning = false;
        return true;
    }
    
    public boolean start() {
        if(running) {
            return false;
        }
        
        this.current.Start();
        this.running = true;
        this.wasRunning = true;
        return true;
    }
    
    public boolean stopCurrent() {
        if(this.current == null) {
            return false;
        }
        
        this.current.Stop();
        this.running = false;
        return true;
    }
    
    public String[] getResults() {
        if(this.current == null) {
            return null;
        }
        
        return this.current.getResults();
    }

    boolean getContestRunning() {
        return this.running;
    }

    boolean getWasRunning() {
        return this.wasRunning;
    }
}
