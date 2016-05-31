package org.jabelpeeps.bedhomes;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;


public class BedHomes extends JavaPlugin implements Listener {

    private boolean debug = true;
    private Logger logger = getLogger();
    private Map<UUID, Location> beds = new HashMap<>();
    
    private File bedsYml = new File(getDataFolder(), "beds.yml");
    private FileConfiguration saveData;
    
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents( this, this );
        
        ConfigurationSerialization.registerClass( Location.class );
        
        saveData = YamlConfiguration.loadConfiguration( bedsYml );
       
        for ( String each : saveData.getKeys( false ) ) {
            @SuppressWarnings( "unchecked" )
            Location location = Location.deserialize( (Map<String, Object>) saveData.get( each ) );
            beds.put( UUID.fromString( each ), location );
        }
        
        // TODO add code to load configuration file - when there are some configuration options to load.
    }
    
    @Override
    public void onDisable() {
        
        for ( Entry<UUID, Location> each : beds.entrySet() ) {
            saveData.createSection( each.getKey().toString(), each.getValue().serialize() );
        }
        try {
            saveData.save( bedsYml );
            
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }
      
    @EventHandler
    public void onPlayerJoining( PlayerJoinEvent event ) {
        // send a message to players telling them to set their beds in the new plugin.
        Player player = event.getPlayer();
        
        if ( !beds.containsKey( player.getUniqueId() ) ) {
            player.sendMessage( "IMPORTANT: There has been a change in the plugin that provides /home, "
                    + "you will need to sleep in your bed again to re-link it to the command." );
        }
        
    }
    @EventHandler
    public void onUsingBed( PlayerBedEnterEvent event ) {
        // records the location where the player was standing when they clicked the bed
        // (which is assumed to be safe to return them to).
        
        Player player = event.getPlayer();
            
        if ( debug ) 
            logger.log( Level.INFO, "Player " + player.getName() + " is using a bed, at time:- " 
                            + String.valueOf( player.getWorld().getTime() ));
        
        beds.put( player.getUniqueId(), player.getLocation() );
    }
    
    @Override
    public boolean onCommand( CommandSender sender, Command cmd, String label, String[] args ) {
        
        if ( sender instanceof Player && cmd.getName().equalsIgnoreCase( "bed" ) ) { 
            
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            
            if ( beds.containsKey( uuid ) ) {
                player.teleport( beds.get( uuid ), TeleportCause.COMMAND );
            }
            else {
                sender.sendMessage( "No valid bed location has been saved for you.  Did you sleep in your bed yet?" );
            }
                
            // TODO add configurable warm up
            // TODO add configurable cost
            
            return true;
        } 
        return false; 
    }
}
