package com.palmergames.bukkit.towny.listeners;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.object.PlayerCache.TownBlockStatus;
import com.palmergames.bukkit.towny.regen.TownyRegenAPI;
import com.palmergames.bukkit.towny.regen.block.BlockLocation;
import com.palmergames.bukkit.towny.tasks.ProtectionRegenTask;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import com.palmergames.bukkit.towny.utils.SiegeWarUtil;
import com.palmergames.bukkit.towny.war.eventwar.War;
import com.palmergames.bukkit.towny.war.flagwar.TownyWar;
import com.palmergames.bukkit.towny.war.flagwar.TownyWarConfig;

import com.palmergames.bukkit.towny.war.siegewar.Siege;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

public class TownyBlockListener implements Listener {

	private final Towny plugin;

	public TownyBlockListener(Towny instance) {

		plugin = instance;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		Player player = event.getPlayer();
		Block block = event.getBlock();

		//Siege War
		if (TownySettings.getWarSiegeEnabled()) {
			boolean skipPermChecks = evaluateSiegeWarBreakBlockRequest(player, block, event);
			if (skipPermChecks) {
				return;
			}
		}

		//Get build permissions (updates cache if none exist)
		//boolean bDestroy = PlayerCacheUtil.getCachePermission(player, block.getLocation(), BukkitTools.getTypeId(block), BukkitTools.getData(block), TownyPermission.ActionType.DESTROY);
		boolean bDestroy = PlayerCacheUtil.getCachePermission(player, block.getLocation(), block.getType(), TownyPermission.ActionType.DESTROY);
		
		// Allow destroy if we are permitted
		if (bDestroy)
			return;

		/*
		 * Fetch the players cache
		 */
		PlayerCache cache = plugin.getCache(player);
		
		
		/*
		 * Allows War Event to piggy back off of Flag War editable materials, while accounting for neutral nations.
		 */
		boolean playerNeutral = false;
		if (TownyUniverse.isWarTime()) {			
			try {
				Resident resident = TownyUniverse.getDataSource().getResident(player.getName());
				if (resident.isJailed())
					playerNeutral = true;				
				if (resident.hasTown())
					if (!War.isWarringTown(resident.getTown())) {						
						playerNeutral = true;
					}
			} catch (NotRegisteredException e) {
			}
			
		}	
		
		/*
		 * Allow destroy in a WarZone (FlagWar) if it's an editable material.
		 * Event War piggy backing on flag war's EditableMaterialInWarZone 
		 */
		try {
			if (cache.getStatus() == TownBlockStatus.WARZONE || (TownyUniverse.isWarTime() && cache.getStatus() == TownBlockStatus.ENEMY && !playerNeutral && War.isWarringTown(cache.getLastTownBlock().getTownBlock().getTown()))) {
				if (!TownyWarConfig.isEditableMaterialInWarZone(block.getType())) {				
					event.setCancelled(true);
					TownyMessaging.sendErrorMsg(player, String.format(TownySettings.getLangString("msg_err_warzone_cannot_edit_material"), "destroy", block.getType().toString().toLowerCase()));
				}
				return;
			}
		} catch (NotRegisteredException e) {
			e.printStackTrace();
		}

		event.setCancelled(true);
		

		/* 
		 * display any error recorded for this plot
		 */
		if ((cache.hasBlockErrMsg()) && (event.isCancelled()))
			TownyMessaging.sendErrorMsg(player, cache.getBlockErrMsg());

	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		Player player = event.getPlayer();
		Block block = event.getBlock();
		WorldCoord worldCoord;
		
		try {

			//Siege War
			if (TownySettings.getWarSiegeEnabled()) {
				boolean skipPermChecks = evaluateSiegeWarPlaceBlockRequest(player, block,event);
				if(skipPermChecks) {
					return;
				}
			}

			TownyWorld world = TownyUniverse.getDataSource().getWorld(block.getWorld().getName());
			worldCoord = new WorldCoord(world.getName(), Coord.parseCoord(block));

			//Get build permissions (updates if none exist)
			//boolean bBuild = PlayerCacheUtil.getCachePermission(player, block.getLocation(), BukkitTools.getTypeId(block), BukkitTools.getData(block), TownyPermission.ActionType.BUILD);
			boolean bBuild = PlayerCacheUtil.getCachePermission(player, block.getLocation(), block.getType(), TownyPermission.ActionType.BUILD);

			// Allow build if we are permitted
			if (bBuild)
				return;

			/*
			 * Fetch the players cache
			 */
			PlayerCache cache = plugin.getCache(player);
			TownBlockStatus status = cache.getStatus();

			/*
			 * Allows War Event to piggy back off of Flag War editable materials, while accounting for neutral nations.
			 */
			boolean playerNeutral = false;
			if (TownyUniverse.isWarTime()) {			
				try {
					Resident resident = TownyUniverse.getDataSource().getResident(player.getName());
					if (resident.isJailed())
						playerNeutral = true;	
					if (resident.hasTown())
						if (!War.isWarringTown(resident.getTown()))
							playerNeutral = true;
				} catch (NotRegisteredException e) {
				}
				
			}	
			
			/*
			 * Flag war
			 */
			if (((status == TownBlockStatus.ENEMY) && TownyWarConfig.isAllowingAttacks()) && (event.getBlock().getType() == TownyWarConfig.getFlagBaseMaterial())) {

				try {
					if (TownyWar.callAttackCellEvent(plugin, player, block, worldCoord))
						return;
				} catch (TownyException e) {
					TownyMessaging.sendErrorMsg(player, e.getMessage());
				}

				event.setBuild(false);
				event.setCancelled(true);

			// Event War piggy backing on flag war's EditableMaterialInWarZone 
			} else if (status == TownBlockStatus.WARZONE || (TownyUniverse.isWarTime() && cache.getStatus() == TownBlockStatus.ENEMY && !playerNeutral && War.isWarringTown(cache.getLastTownBlock().getTownBlock().getTown()))) {
				if (!TownyWarConfig.isEditableMaterialInWarZone(block.getType())) {
					event.setBuild(false);
					event.setCancelled(true);
					TownyMessaging.sendErrorMsg(player, String.format(TownySettings.getLangString("msg_err_warzone_cannot_edit_material"), "build", block.getType().toString().toLowerCase()));
				}
				return;
			} else {
				event.setBuild(false);
				event.setCancelled(true);
			}

			/* 
			 * display any error recorded for this plot
			 */
			if ((cache.hasBlockErrMsg()) && (event.isCancelled()))
				TownyMessaging.sendErrorMsg(player, cache.getBlockErrMsg());

		} catch (NotRegisteredException e1) {
			TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_not_configured"));
			event.setCancelled(true);
		}

	}

	/*
	 * coloured banner - could be attack or invade
	 * white banner - could be surrender
	 * chest - could be plunder
	 *
	 * Return - skipOtherPerChecks
	 */
	public boolean evaluateSiegeWarPlaceBlockRequest(Player player,
													 Block block,
													 BlockPlaceEvent event)
	{
		try {
			String blockTypeName = block.getType().getKey().getKey();
			if (blockTypeName.contains("banner")) {

				if (blockTypeName.contains("white")
						&& ((Banner) block.getState()).getPatterns().size() == 0) {
					//white banner
					return evaluateSiegeWarPlaceWhiteBannerRequest(player, block, event);
				} else {
					//coloured banner
					return evaluateSiegeWarPlaceColouredBannerRequest(player, block, event);
				}
			} else if (block.getType().equals(Material.CHEST)) {
				//Chest
				return evaluateSiegeWarPlaceChestRequest(player, block);
			} else {
				return false;
			}
		} catch (NotRegisteredException e) {
			TownyMessaging.sendErrorMsg(player, "Problem placing siege banner");
			TownyMessaging.sendErrorMsg(player, e.getMessage());
			event.setCancelled(true);
			return true;
		}
	}

	//While a siege exists, nobody can destroy the siege banner
	//Returns skipAdditionalPermChecks
	public boolean evaluateSiegeWarBreakBlockRequest(Player player, Block block, BlockBreakEvent event)  {
		boolean skipRemainingPermChecks = false;
		try {
			String blockTypeName = block.getType().getKey().getKey();

			if (blockTypeName.contains("banner")) {
				Resident resident = TownyUniverse.getDataSource().getResident(player.getName());

				if (resident.hasTown()) {
					Siege activeSiege = SiegeWarUtil.getActiveSiegeGivenBannerLocation(block.getLocation());

					if (activeSiege == null) {
						//This is not a siege banner
						return false;
					} else {
						//This block is the banner of an active siege
						event.setCancelled(true);
						skipRemainingPermChecks = true;
						throw new TownyException("This is a siege banner. It cannot be destroyed while the associated siege attack is in progress.");
					}
				}
			}
		} catch (TownyException x) {
			TownyMessaging.sendErrorMsg(player, x.getMessage());
		}
		return skipRemainingPermChecks;
	}


	//Return - skip other perm checks
	private boolean evaluateSiegeWarPlaceColouredBannerRequest(Player player, Block block, BlockPlaceEvent event) throws NotRegisteredException {
		//If the target block is in the wilderness next to a town,
		//Then only valid siege actions are permitted
		TownBlock townBlock = TownyUniverse.getTownBlock(block.getLocation());

		if(townBlock == null) {
			//Check for siege start request
			Coord blockCoordinate = Coord.parseCoord(block);
			TownyWorld townyWorld = TownyUniverse.getDataSource().getWorld(player.getWorld().getName());

			TownBlock nearbyTownBlock;
			List<TownBlock> nearbyTownBlocksWithTowns = new ArrayList<>();

			if(townyWorld.hasTownBlock(blockCoordinate.add(0,-1))) {
				nearbyTownBlock= townyWorld.getTownBlock(blockCoordinate.add(0,-1));
				if(nearbyTownBlock.hasTown()) {
					nearbyTownBlocksWithTowns.add(nearbyTownBlock);
				}
			}

			if(townyWorld.hasTownBlock(blockCoordinate.add(0,1))) {
				nearbyTownBlock= townyWorld.getTownBlock(blockCoordinate.add(0,1));
				if(nearbyTownBlock.hasTown()) {
					nearbyTownBlocksWithTowns.add(nearbyTownBlock);
				}
			}

			if(townyWorld.hasTownBlock(blockCoordinate.add(1,0))) {
				nearbyTownBlock= townyWorld.getTownBlock(blockCoordinate.add(1,0));
				if(nearbyTownBlock.hasTown()) {
					nearbyTownBlocksWithTowns.add(nearbyTownBlock);
				}
			}

			if(townyWorld.hasTownBlock(blockCoordinate.add(-1,0))) {
				nearbyTownBlock= townyWorld.getTownBlock(blockCoordinate.add(-1,0));
				if(nearbyTownBlock.hasTown()) {
					nearbyTownBlocksWithTowns.add(nearbyTownBlock);
				}
			}

			if(nearbyTownBlocksWithTowns.size() == 0) {
				//No town blocks nearby. Continue.
				return false;
			} else  {
				//One or more town blocks nearby. Evaluate siege attack request
				SiegeWarUtil.processAttackTownRequest(player, block, nearbyTownBlocksWithTowns,event);
				return true;
			}

		} else {
			//Check for siege invasion request

			Town townWhereBlockWasPlaced;
			if(townBlock.hasTown()) {
				townWhereBlockWasPlaced = townBlock.getTown();
			} else {
				return false;	//Not sure how we get here. But in any case,
				                //don't treat it as an invasion request
			}

			//If the target town is different town to player, evaluate siege request
			Resident resident = TownyUniverse.getDataSource().getResident(player.getName());
			if (!resident.hasTown() || resident.getTown() != townWhereBlockWasPlaced ) {
				SiegeWarUtil.processInvadeTownRequest(plugin, player, townWhereBlockWasPlaced.getName(),event);
				return true;
			} else {
				return false;
			}
		}
	}

	private boolean evaluateSiegeWarPlaceWhiteBannerRequest(Player player, Block block, BlockPlaceEvent event) throws NotRegisteredException {
		Resident resident = TownyUniverse.getDataSource().getResident(player.getName());
		if (resident.hasTown()) {

			//Get Town Where block was placed
			Town townWhereBlockWasPlaced;
			TownBlock townBlock = TownyUniverse.getTownBlock(block.getLocation());
			if (townBlock != null && townBlock.hasTown()) {
				townWhereBlockWasPlaced = townBlock.getTown();
			} else {
				return false;
			}

			//Under Siege
			if(townWhereBlockWasPlaced.hasSiege()) {
				// Residents town - surrender
				// Not residents town  abandon
				if(townWhereBlockWasPlaced == resident.getTown()) {
					SiegeWarUtil.processSurrenderRequest(player, event);
					return true;
				} else {
					SiegeWarUtil.processAbandonSiegeRequest(player, townWhereBlockWasPlaced.getName(), event);
					return true;
				}
			}
		}
		return false;
	}


	private boolean evaluateSiegeWarPlaceChestRequest(Player player, Block block) throws NotRegisteredException {
		Resident resident = TownyUniverse.getDataSource().getResident(player.getName());
		if (resident.hasTown()) {

			//Get Town Where block was placed
			Town townWhereBlockWasPlaced;
			TownBlock townBlock = TownyUniverse.getTownBlock(block.getLocation());
			if (townBlock != null && townBlock.hasTown()) {
				townWhereBlockWasPlaced = townBlock.getTown();
			} else {
				return false;
			}

			//If the target town is a a different town than player's, evaluate plunder request
			Town residentTown = resident.getTown();
			if (townWhereBlockWasPlaced != residentTown) {
				return SiegeWarUtil.evaluateTownPlunderRequest(player, townWhereBlockWasPlaced.getName());
			}
		}
		return false;
	}

	// prevent blocks igniting if within a protected town area when fire spread is set to off.
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBurn(BlockBurnEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		if (onBurn(event.getBlock()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onBlockIgnite(BlockIgniteEvent event) {

		if (event.isCancelled() || plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		if (onBurn(event.getBlock()))
			event.setCancelled(true);

	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPistonRetract(BlockPistonRetractEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		List<Block> blocks = event.getBlocks();
		if (testBlockMove(event.getBlock(), event.getDirection(), true))
			event.setCancelled(true);

		if (!blocks.isEmpty()) {
			//check each block to see if it's going to pass a plot boundary
			for (Block block : blocks) {
				if (testBlockMove(block, event.getDirection(), false))
					event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPistonExtend(BlockPistonExtendEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}
		
		if (testBlockMove(event.getBlock(), event.getDirection(), false))
			event.setCancelled(true);
		
		List<Block> blocks = event.getBlocks();

		if (!blocks.isEmpty()) {
			//check each block to see if it's going to pass a plot boundary
			for (Block block : blocks) {
				if (testBlockMove(block, event.getDirection(), false))
					event.setCancelled(true);
			}
		}
	}

	/**
	 * testBlockMove
	 * 
	 * @param block - block that is being moved, or if pistonBlock is true the piston itself
	 * @param direction - direction the blocks are going
	 * @param pistonBlock - test is slightly different when the piston block itself is being checked.	 * 
	 */
	private boolean testBlockMove(Block block, BlockFace direction, boolean pistonBlock) {

		Block blockTo = null;
		if (!pistonBlock)
			blockTo = block.getRelative(direction);
		else {
			blockTo = block.getRelative(direction.getOppositeFace());
		}
		Location loc = block.getLocation();
		Location locTo = blockTo.getLocation();
		Coord coord = Coord.parseCoord(loc);
		Coord coordTo = Coord.parseCoord(locTo);

		TownyWorld townyWorld = null;
		TownBlock currentTownBlock = null, destinationTownBlock = null;

		try {
			townyWorld = TownyUniverse.getDataSource().getWorld(loc.getWorld().getName());
			currentTownBlock = townyWorld.getTownBlock(coord);
		} catch (NotRegisteredException e) {
		}

		try {
			destinationTownBlock = townyWorld.getTownBlock(coordTo);
		} catch (NotRegisteredException e1) {
		}

		if (currentTownBlock != destinationTownBlock) {
			
			// Cancel if either is not null, but other is (wild to town).
			if (((currentTownBlock == null) && (destinationTownBlock != null)) || ((currentTownBlock != null) && (destinationTownBlock == null))) {
				return true;
			}

			// If both blocks are owned by the town.
			if (!currentTownBlock.hasResident() && !destinationTownBlock.hasResident()) {
				return false;
			}

			try {
				if ((!currentTownBlock.hasResident() && destinationTownBlock.hasResident()) || (currentTownBlock.hasResident() && !destinationTownBlock.hasResident()) || (currentTownBlock.getResident() != destinationTownBlock.getResident())

				|| (currentTownBlock.getPlotPrice() != -1) || (destinationTownBlock.getPlotPrice() != -1)) {
					return true;
				}
			} catch (NotRegisteredException e) {
				// Failed to fetch a resident
				return true;
			}
		}

		return false;
	}

	private boolean onBurn(Block block) {

		Location loc = block.getLocation();
		Coord coord = Coord.parseCoord(loc);
		TownyWorld townyWorld;

		try {
			townyWorld = TownyUniverse.getDataSource().getWorld(loc.getWorld().getName());

			if (!townyWorld.isUsingTowny())
				return false;
			
			TownBlock townBlock = TownyUniverse.getTownBlock(loc);
			
		
			// Give the wilderness a pass on portal ignition, like we do in towns when fire is disabled.
			if ((block.getRelative(BlockFace.DOWN).getType() != Material.OBSIDIAN) && ((townBlock == null && !townyWorld.isForceFire() && !townyWorld.isFire()))) {
				TownyMessaging.sendDebugMsg("onBlockIgnite: Canceled " + block.getType().name() + " from igniting within " + coord.toString() + ".");
				return true;
			}

			try {

				//TownBlock townBlock = townyWorld.getTownBlock(coord);
				
				boolean inWarringTown = false;
				if (TownyUniverse.isWarTime()) {					
					if (townyWorld.hasTownBlock(coord))
						if (War.isWarringTown(townBlock.getTown()))
							inWarringTown = true;
				}
				/*
				 * Event War piggybacking off of Flag War's fire control setting.
				 */
				if (townyWorld.isWarZone(coord) || TownyUniverse.isWarTime() && inWarringTown) {					
					if (TownyWarConfig.isAllowingFireInWarZone()) {
						return false;
					} else {
						TownyMessaging.sendDebugMsg("onBlockIgnite: Canceled " + block.getType().name() + " from igniting within " + coord.toString() + ".");
						return true;
					}
				}
				if (townBlock != null)
					// Give a pass to Obsidian for portal lighting and Netherrack for fire decoration.
					if (((block.getRelative(BlockFace.DOWN).getType() != Material.OBSIDIAN) || (block.getRelative(BlockFace.DOWN).getType() != Material.NETHERRACK)) && ((!townBlock.getTown().isFire() && !townyWorld.isForceFire() && !townBlock.getPermissions().fire) || (TownyUniverse.isWarTime() && TownySettings.isAllowWarBlockGriefing() && !townBlock.getTown().hasNation()))) {
						TownyMessaging.sendDebugMsg("onBlockIgnite: Canceled " + block.getType().name() + " from igniting within " + coord.toString() + ".");
						return true;
					}
			} catch (TownyException x) {
				// Not a town so check the world setting for fire
				if (!townyWorld.isFire()) {
					TownyMessaging.sendDebugMsg("onBlockIgnite: Canceled " + block.getType().name() + " from igniting within " + coord.toString() + ".");
					return true;
				}
			}

		} catch (NotRegisteredException e) {
			// Failed to fetch the world
		}

		return false;
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCreateExplosion(BlockExplodeEvent event) {
		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}
		
		TownyWorld townyWorld = null;
		List<Block> blocks = event.blockList();
		int count = 0;
		
		try {
			townyWorld = TownyUniverse.getDataSource().getWorld(event.getBlock().getLocation().getWorld().getName());
		} catch (NotRegisteredException e) {
			e.printStackTrace();
		}
		for (Block block : blocks) {
			count++;
			
			if (!locationCanExplode(townyWorld, block.getLocation())) {
				event.setCancelled(true);
				return;
			}
			
			if (TownyUniverse.isWilderness(block)) {
				if (townyWorld.isUsingTowny()) {
					if (townyWorld.isExpl()) {
						if (townyWorld.isUsingPlotManagementWildRevert()) {
							//TownyMessaging.sendDebugMsg("onCreateExplosion: Testing block: " + entity.getType().getEntityClass().getSimpleName().toLowerCase() + " @ " + coord.toString() + ".");
							if ((!TownyRegenAPI.hasProtectionRegenTask(new BlockLocation(block.getLocation()))) && (block.getType() != Material.TNT)) {
								ProtectionRegenTask task = new ProtectionRegenTask(plugin, block);
								task.setTaskId(plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, ((TownySettings.getPlotManagementWildRegenDelay() + count) * 20)));
								TownyRegenAPI.addProtectionRegenTask(task);
								event.setYield((float) 0.0);
								block.getDrops().clear();
							}
						}
					}
				}
			}
		}
		
	}
	
	/**
	 * Test if this location has explosions enabled.
	 * 
	 * @param world
	 * @param target
	 * @return true if allowed.
	 */
	public boolean locationCanExplode(TownyWorld world, Location target) {

		Coord coord = Coord.parseCoord(target);

		if (world.isWarZone(coord) && !TownyWarConfig.isAllowingExplosionsInWarZone()) {
			return false;
		}
		TownBlock townBlock = null;
		boolean isNeutral = false;
		try {
			townBlock = world.getTownBlock(coord);
			if (townBlock.hasTown())
				if (!War.isWarZone(townBlock.getWorldCoord()))
					isNeutral = true;
		} catch (NotRegisteredException e1) {
			if (TownyUniverse.isWilderness(target.getBlock())) {
				isNeutral = !world.isExpl();
				if (!world.isExpl() && !TownyUniverse.isWarTime())
					return false;				
				if (world.isExpl() && !TownyUniverse.isWarTime())
					return true;	
			}
		}
		
		try {			
			if (world.isUsingTowny() && !world.isForceExpl()) {
				if (TownyUniverse.isWarTime() && TownyWarConfig.explosionsBreakBlocksInWarZone() && !isNeutral){
					return true;				
				}
				if ((!townBlock.getPermissions().explosion) || (TownyUniverse.isWarTime() && TownyWarConfig.isAllowingExplosionsInWarZone() && !townBlock.getTown().hasNation() && !townBlock.getTown().isBANG()))
					return false;
			}
		} catch (NotRegisteredException e) {
			return world.isExpl();
		}
		return true;
	}

}