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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.milkbowl.vault.economy.Economy;


public class BedHomes extends JavaPlugin implements Listener {

    boolean debug = true;
    Logger logger = getLogger();
    static Economy economy = null;
    
    // TODO maybe implement other databases/storage for the location data.   
    Map<UUID, Location> beds = new HashMap<>();
    static Map<UUID, BukkitTask> warmups = new HashMap<>();
    
    private String logonMsg;
    private String brokenMsg;
    
    private int warmupDelay;
    static int cost;
    
    File bedsYml;
    FileConfiguration saveData;
    
    private final BukkitRunnable saveBeds = new BukkitRunnable() {
        @Override
        public void run() {
            
            if ( debug )
                logger.log( Level.INFO, "Saving beds.yml" );
            
            for ( Entry<UUID, Location> each : beds.entrySet() ) {
                saveData.createSection( each.getKey().toString(), each.getValue().serialize() );
            }
            
            try {
                saveData.save( bedsYml );                
            } catch ( IOException e ) {
                logger.log( Level.SEVERE, "Bedhomes was unable to save beds.yml, all bed locations will be lost." );
                e.printStackTrace();
            }
        }
    };
     
    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        warmupDelay = config.getInt( "warmup" );
        cost = config.getInt( "cost" );
        
        bedsYml = new File(getDataFolder(), "beds.yml");
        saveData = YamlConfiguration.loadConfiguration( bedsYml );
        
        Bukkit.getPluginManager().registerEvents( this, this );
        ConfigurationSerialization.registerClass( Location.class );
       
        // load the saved bed locations.
        for ( String each : saveData.getKeys( false ) ) {
            
            beds.put( UUID.fromString( each ), 
                      Location.deserialize( saveData.getConfigurationSection( each )
                                                    .getValues( false ) ) );
        }
        if ( !setupEconomy() ) 
            logger.log( Level.WARNING, "Vault +/- Economy plugins not found, cost per command will not function." );
        
        // saves the bed locations every 10 mins.
        saveBeds.runTaskTimerAsynchronously( this, 12000, 12000 );
    }
    
    private boolean setupEconomy() {
        if ( Bukkit.getPluginManager().getPlugin("Vault") == null ) return false;
        
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration( Economy.class );
        if ( rsp == null ) return false;
        
        economy = rsp.getProvider();
        return economy != null;
    }
    
    @Override
    public void onDisable() {  
       
        Bukkit.getScheduler().cancelTasks( this );
        // one last run of the save routine before shutdown.
        saveBeds.run();          
    }
    
    @EventHandler
    public void onPlayerJoining( PlayerJoinEvent event ) {
        // sends a message to players telling them to set their beds in the new plugin.
        
        Player player = event.getPlayer();
        
        if ( !beds.containsKey( player.getUniqueId() ) ) {
            
            if ( logonMsg == null )
                logonMsg = String.join( "", 
                        ChatColor.RED.toString(), ChatColor.BOLD.toString(), "IMPORTANT: ", System.lineSeparator(),
                        ChatColor.RESET.toString(), "There is a new plugin providing /home, you need to ",
                        ChatColor.RED.toString(), "sleep in your bed again", 
                        ChatColor.RESET.toString(), " to re-link the command." );
            
            player.sendMessage( logonMsg );
        }    
    }
    
    
    @EventHandler( priority = EventPriority.MONITOR, ignoreCancelled = true )
    public void onUsingBed( PlayerBedEnterEvent event ) {
        // records the location where the player was standing when they clicked the bed
        // (which is assumed to be safe to return them to).
        // sets that location as the API BedSpawnLocation, and also saves it to our own hashmap.
        
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
        // Removes beds that get broken from the hashmap. 
        // Priority = MONITOR & ignoreCancelled = true, to respect block/grief protection plugins
        
        Block block = event.getBlock();
        
        if ( block.getType() == Material.BED_BLOCK ) {
            
            UUID uuid = null; 
            
            for ( Entry<UUID, Location> each : beds.entrySet() ) {
                
                if ( block.getLocation().distanceSquared( each.getValue() ) < 5 ) {
                    
                    uuid = each.getKey();
                    
                    Player player = Bukkit.getPlayer( uuid );
                    
                    if ( player != null ) {
                        if ( brokenMsg == null )
                            brokenMsg = String.join( "", 
                                    ChatColor.RED.toString(), ChatColor.BOLD.toString(), "Your bed has been broken!",
                                    System.lineSeparator(),
                                    ChatColor.RESET.toString(), " /home will not work until you use another." );
                            
                        player.sendMessage( brokenMsg );
                    }
                    if ( debug )
                        logger.log( Level.INFO, String.join( "", "The Bed at ", block.getLocation().toString(),
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
        }
        
        if ( sender instanceof Player && cmd.getName().equalsIgnoreCase( "home" ) ) { 
            
            Player player = (Player) sender;
            Location myBed = getBedFor( player );
            
            if ( myBed == null ) {
                player.sendMessage( 
                        ChatColor.YELLOW.toString().concat( "No saved bed location for you.  Did you sleep in one yet?" ) );
                return true;
            }
            if ( economy != null && !economy.has( player, cost ) ) {
                player.sendMessage( String.join( "", 
                        ChatColor.YELLOW.toString(), "using /home costs ", 
                        String.valueOf( cost ), economy.currencyNamePlural(), " each time you use it." ) );
                return true;
            }
            warmups.put( player.getUniqueId(), 
                         new DelayedTeleport( player, myBed ).runTaskLater( this, 20 * warmupDelay ) );
            player.sendMessage( String.join( "", 
                    ChatColor.RED.toString(), "Do Not Move for ", 
                    String.valueOf( warmupDelay ), " seconds to return home." ) );         
        } 
        return true; 
    }
    
    @EventHandler
    public void warmupWatcher( PlayerMoveEvent event ) {
        
        UUID uuid = event.getPlayer().getUniqueId();
        
        if ( warmups.containsKey( uuid ) ) {
            warmups.remove( uuid ).cancel();
            event.getPlayer().sendMessage( String.join( "", 
                    ChatColor.RED.toString(), "Movement Detected!", System.lineSeparator(),
                    ChatColor.RESET.toString(), "/home command cancelled." ) );
        } 
    }
    
    private Location getBedFor( Player player ) {
        
        UUID uuid = player.getUniqueId();
        Location myBed = beds.get( uuid );
            
        if ( myBed == null )
            beds.remove( uuid );
        
        return myBed;     
    }
    
    private static class DelayedTeleport extends BukkitRunnable {
        
        private final Player p;
        private final Location loc;
        
        DelayedTeleport( Player player, Location location ) {
            p = player;
            loc = location;
        }
        
        @Override
        public void run() {
            warmups.remove( p.getUniqueId() );
            
            if ( economy == null || economy.withdrawPlayer( p, cost ).transactionSuccess() )
                p.teleport( loc, TeleportCause.COMMAND );
            else
                p.sendMessage( String.join( "", 
                        ChatColor.YELLOW.toString(), "using /home costs ", 
                        String.valueOf( cost ), economy.currencyNamePlural() ) );
        }      
    }
}
