package org.jabelpeeps.bedhomes;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;


public class BedHomes extends JavaPlugin implements Listener {

    private boolean debug = true;
    private Logger logger = getLogger();
//    private Map<UUID, Location> beds = new HashMap<>();
    
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // TODO add code to load persisted bed locations.
        
        // TODO add code to load configuration file - when there are some configuration options to load.
    }
    
    @Override
    public void onDisable() {
        // TODO add code to persist saved bed locations across server restarts.
    }
        
    @EventHandler
    public void onUsingBed( PlayerBedEnterEvent event ) {
        // records the location of the bed for future use.
        
        Player player = event.getPlayer();
        long time = player.getWorld().getTime();
        
        // do nothing in day time
  //      if ( time > 6000 && time < 18000 ) return;
        // TODO consider adding a message to players telling them to sleep at night to set their /home location.
        
        if ( debug ) logger.log( Level.INFO, "Player " + player.getName() + " is using a bed, at time:- " + String.valueOf( time ));
        
 //       beds.put( player.getUniqueId(), player.getLocation() );
    }
    
    @Override
    public boolean onCommand( CommandSender sender, Command cmd, String label, String[] args ) {
        
        if ( sender instanceof Player && cmd.getName().equalsIgnoreCase( "home" ) ) { 
            
            Player player = (Player) sender;
            Location bed = player.getBedSpawnLocation();
            
            if ( bed != null ) {
                player.teleport( bed, TeleportCause.COMMAND );
            }
            else {
                sender.sendMessage( "No valid bed location exists for you." );
            }
                
            // TODO add configurable warm up
            // TODO add configurable cost
            
            return true;
        } 
        return false; 
    }
}
