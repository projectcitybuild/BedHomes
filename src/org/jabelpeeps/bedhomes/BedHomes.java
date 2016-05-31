package org.jabelpeeps.bedhomes;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;


public class BedHomes extends JavaPlugin {

    @Override
    public boolean onCommand( CommandSender sender, Command cmd, String label, String[] args ) {
        
        if ( sender instanceof Player && cmd.getName().equalsIgnoreCase( "bed" ) ) { 
            
            Player player = (Player) sender;
            Location bed = player.getBedSpawnLocation();
            
            if ( bed != null ) {
                player.teleport( bed, TeleportCause.COMMAND );
            }
            else {
                sender.sendMessage( "No valid bed location exists for you." );
            }
                
            // TODO add configurable warm up ?
            // TODO add configurable cost ?
            
            return true;
        } 
        return false; 
    }
}
