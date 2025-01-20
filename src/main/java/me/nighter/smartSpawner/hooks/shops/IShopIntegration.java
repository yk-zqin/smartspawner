package me.nighter.smartSpawner.hooks.shops;

import org.bukkit.entity.Player;
import me.nighter.smartSpawner.utils.SpawnerData;

public interface IShopIntegration {
    boolean sellAllItems(Player player, SpawnerData spawner);
    boolean isEnabled();
}

