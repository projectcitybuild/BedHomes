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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;


public class BedHomes extends JavaPlugin implements Listener {

    private boolean debug = true;
    private Logger logger = getLogger();
    private Map<UUID, Location> beds = new HashMap<>();
    
    private File bedsYml;
    private FileConfiguration saveData;
    
    @Override
    public void onEnable() {
        
        bedsYml = new File(getDataFolder(), "beds.yml");
        saveData = YamlConfiguration.loadConfiguration( bedsYml );
        
        Bukkit.getPluginManager().registerEvents( this, this );
        ConfigurationSerialization.registerClass( Location.class );
       
        for ( String each : saveData.getKeys( false ) ) {
            
            beds.put( UUID.fromString( each ), 
                      Location.deserialize( saveData.getConfigurationSection( each )
                                                    .getValues( false ) ) );
        }
        
        // TODO add code to load configuration file - when there are some configuration options to load.
    }
    
    @Override
    public void onDisable() {        
        // TODO maybe implement other databases/storage for the location data.
        
        for ( Entry<UUID, Location> each : beds.entrySet() ) {
            saveData.createSection( each.getKey().toString(), each.getValue().serialize() );
        }
        
        try {
            saveData.save( bedsYml );
            
        } catch ( IOException e ) {
            logger.log( Level.SEVERE, "Bedhomes was unable to save beds.yml, all bed locations will be lost."  );
            e.printStackTrace();
        }
    }
     
    @EventHandler
    public void onPlayerJoining( PlayerJoinEvent event ) {
        // sends a message to players telling them to set their beds in the new plugin.
        
        Player player = event.getPlayer();
        
        if ( !beds.containsKey( player.getUniqueId() ) ) {
            player.sendMessage( String.join( "", ChatColor.RED.toString(), "IMPORTANT:", 
                    ChatColor.RESET.toString(), "We have a new plugin providing /home, you need ",
                    ChatColor.RED.toString(), "to sleep in your bed again", 
                    ChatColor.RESET.toString(), " to re-link with the command." ) );
        }    
    }
    
    @EventHandler( priority=EventPriority.MONITOR, ignoreCancelled = true )
    public void onUsingBed( PlayerBedEnterEvent event ) {
        // records the location where the player was standing when they clicked the bed
        // (which is assumed to be safe to return them to).
        
        Player player = event.getPlayer();
            
        if ( debug ) 
            logger.log( Level.INFO, "Player " + player.getName() + " is using a bed, at time:- " 
                            + String.valueOf( player.getWorld().getTime() ));
        
        Location bed = player.getLocation();
        player.setBedSpawnLocation( bed, true );
        beds.put( player.getUniqueId(), bed );
    }
    
    @EventHandler( priority = EventPriority.MONITOR, ignoreCancelled = true )
    public void onBreakingBeds( BlockBreakEvent event ) {
        // removes beds that get broken from the hashmap. Priority = MONITOR to respect the block/grief protection plugins
        
        Block block = event.getBlock();
        
        if ( block.getType() == Material.BED_BLOCK ) {
            
            UUID uuid = null; 
            
            for ( Entry<UUID, Location> each : beds.entrySet() ) {
                
                if ( block.getLocation().distanceSquared( each.getValue() ) < 5 ) {
                    
                    uuid = each.getKey();
                    
                    Player player = Bukkit.getPlayer( uuid );
                    
                    if ( player != null ) {
                        player.sendMessage( "Your bed has been broken, /home will not work until you set another." );
                    }
                    if ( debug )
                        logger.log( Level.INFO, String.join( "", "The Bed at ", block.toString(),
                                 " belonging to ", Bukkit.getOfflinePlayer( uuid ).getName(), " has been broken." ) );
                    break;
                }
            }
            if ( uuid != null ) beds.remove( uuid );
        }
    }
        
    @Override
    public boolean onCommand( CommandSender sender, Command cmd, String label, String[] args ) {
        
        if ( sender instanceof ConsoleCommandSender ) {
            sender.sendMessage( "This command is only available to players" );
            return false;
        }
        
        if ( sender instanceof Player && cmd.getName().equalsIgnoreCase( "bed" ) ) { 
            
            Player player = (Player) sender;
            Location myBed = getBedFor( player );
            
            if ( myBed != null )
                player.teleport( myBed, TeleportCause.COMMAND );
            else 
                player.sendMessage( "No bed location is saved for you.  Did you sleep in your bed yet?" );
            
                
            // TODO add configurable warm up
            // TODO add configurable cost
            
            return true;
        } 
        return false; 
    }
    
    private Location getBedFor( Player player ) {
        
        UUID uuid = player.getUniqueId();
        Location myBed = beds.get( uuid );
            
        if ( myBed == null )
            beds.remove( uuid );
        
        return myBed;     
    }
}
