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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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


public final class BedHomes extends JavaPlugin implements Listener {

    protected boolean debug = true;
    protected Logger logger = getLogger();
    protected static Economy economy = null;
    
    // TODO maybe implement other databases/storage for the location data.   
    protected Map<UUID, Location> beds = new HashMap<>();
    protected static Map<UUID, BukkitTask> warmups = new HashMap<>();

    protected String logonMsg;
    private String brokenMsg;   
    private int homeWarmupDelay;
    protected int homeCost;
    private boolean homeEnabled;
    
    private int spawnWarmupDelay;
    protected int spawnCost;
    private boolean spawnEnabled;
    private String spawnWorld;
    
    protected File bedsYml;
    protected FileConfiguration saveData;
    
    private final BukkitRunnable saveBeds = new BukkitRunnable() {
        @Override
        public void run() {
                
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
        debug = config.getBoolean( "debug" );
        
        ConfigurationSection homeSection = config.getConfigurationSection( "home" );
        homeWarmupDelay = homeSection.getInt( "warmup" );
        homeCost = homeSection.getInt( "cost" );
        homeEnabled = homeSection.getBoolean( "enabled" );
        
        ConfigurationSection spawnSection = config.getConfigurationSection( "spawn" );
        spawnWarmupDelay = spawnSection.getInt( "warmup" );
        spawnCost = spawnSection.getInt( "cost" );
        spawnEnabled = spawnSection.getBoolean( "enabled" );
        spawnWorld = spawnSection.getString( "world" );
        
        bedsYml = new File( getDataFolder(), "beds.yml" );
        saveData = YamlConfiguration.loadConfiguration( bedsYml );
        
        Bukkit.getPluginManager().registerEvents( this, this );
       
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
        
        // check that the configured world for the spawn command is valid.
        if ( spawnEnabled && Bukkit.getWorld( spawnWorld ) == null 
                                && !spawnWorld.equalsIgnoreCase( "current" ) ) {
            
            logger.log( Level.WARNING, "The configured spawn world '" + spawnWorld + "' was not found. /spawn disabled." );
            spawnEnabled = false;          
        }          
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
                
        if ( logonMsg == null )
            logonMsg = String.join( "", 
                    ChatColor.RED.toString(), ChatColor.BOLD.toString(), "IMPORTANT: ", System.lineSeparator(),
                    ChatColor.RESET.toString(), "There is a new plugin providing /home, you need to ",
                    ChatColor.RED.toString(), "sleep in your bed again", 
                    ChatColor.RESET.toString(), " to re-link the command." );
        
        if ( !beds.containsKey( player.getUniqueId() ) ) {
            
            Bukkit.getScheduler().runTaskLater( this, () -> player.sendMessage( logonMsg ), 100 );   
        }
    }
    
    
    @EventHandler( priority = EventPriority.MONITOR, ignoreCancelled = true )
    public void onUsingBed( PlayerBedEnterEvent event ) {
        // records the location where the player was standing when they clicked the bed
        // (which is assumed to be safe to return them to).
        // Sets that location as the API BedSpawnLocation, and also saves it to our own hashmap.
        
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
        
        if ( !(sender instanceof Player) ) {
            sender.sendMessage( "This command is only available to players" );
            return true;
        }
        
        Player player = (Player) sender;
        String command = cmd.getName();
        
        if ( homeEnabled && command.equalsIgnoreCase( "home" ) ) { 
            
            Location myBed = getBedFor( player );
            
            if ( myBed == null ) {
                player.sendMessage( 
                        ChatColor.YELLOW.toString().concat( "No saved bed location for you.  Did you sleep in one yet?" ) );
                return true;
            }
            checkEconAndTeleport( player, myBed, command, homeCost, homeWarmupDelay );  
            
        }         
        else if ( spawnEnabled && command.equalsIgnoreCase( "spawn" ) ) {
            
            Location spawn = null;
            
            if ( spawnWorld.equalsIgnoreCase( "current" ) ) 
                spawn = player.getWorld().getSpawnLocation();
            else 
                spawn = Bukkit.getWorld( spawnWorld ).getSpawnLocation();
                       
            checkEconAndTeleport( player, spawn, command, spawnCost, spawnWarmupDelay );            
        }
        return true; 
    }
    
    private void checkEconAndTeleport( Player player, Location loc, String cmd, int cost, int delay ) {
        
        if ( economy != null ) {
            player.sendMessage( String.join( "",  ChatColor.YELLOW.toString(), "using /", cmd, " costs ", 
                    String.valueOf( cost ), economy.currencyNamePlural(), " each time you use it." ) );
            
            if ( !economy.has( player, cost ) )
                return;
        }
        
        warmups.put( player.getUniqueId(), 
                     new DelayedTeleport( player, loc, cost ).runTaskLater( this, 20 * delay ) );
        
        player.sendMessage( String.join( "", ChatColor.RED.toString(), "Do Not Move for ", 
                            String.valueOf( delay ), " seconds to teleport." ) );         
    }
    
    @EventHandler
    public void warmupWatcher( PlayerMoveEvent event ) {
        
        UUID uuid = event.getPlayer().getUniqueId();
        
        if ( warmups.containsKey( uuid ) ) {
            warmups.remove( uuid ).cancel();
            event.getPlayer().sendMessage( String.join( "",  ChatColor.RED.toString(), "Movement Detected!", 
                    System.lineSeparator(), ChatColor.RESET.toString(), "Teleport cancelled." ) );
        } 
    }
    
    private Location getBedFor( Player player ) {
        
        UUID uuid = player.getUniqueId();
        Location myBed = beds.get( uuid );
            
        if ( myBed == null )
            beds.remove( uuid );
        
        return myBed;     
    }
    
    private class DelayedTeleport extends BukkitRunnable {
        
        private final Player p;
        private final Location loc;
        private final int cost;
        
        DelayedTeleport( Player player, Location location, int _cost ) {
            p = player;
            loc = location;
            cost = _cost;
        }
        
        @Override
        public void run() {
            warmups.remove( p.getUniqueId() );
            
            if ( economy == null || economy.withdrawPlayer( p, homeCost ).transactionSuccess() ) {
                p.teleport( loc, TeleportCause.COMMAND );
                
                if ( economy != null )
                    p.sendMessage( String.join( "", ChatColor.YELLOW.toString(), "You have been charged ", 
                        String.valueOf( cost ), economy.currencyNamePlural() ) );
            }
        }      
    }
}
