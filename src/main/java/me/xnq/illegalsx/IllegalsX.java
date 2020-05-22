package me.xnq.illegalsx;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class IllegalsX extends JavaPlugin implements Listener {
    private YamlConfiguration config;
    private List<String> illegals;
    private List<Material> illegalsMat;
    private Set<String> enchantments;

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        this.saveDefaultConfig();
        this.loadConfig();
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.HIGH, PacketType.Play.Server.SET_SLOT) {
            public void onPacketSending(PacketEvent e) {
                if (e.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                    PacketContainer packet = e.getPacket();
                    ItemStack item = packet.getItemModifier().getValues().get(0).clone();
                    if (checkSingle(item, true) && e.getPlayer() != null) e.getPlayer().getInventory().remove(item);
                }
            }
        });
        Bukkit.getServer().broadcastMessage(ChatColor.GRAY + "Initialized " + ChatColor.DARK_AQUA + "IllegalsX");
    }

    public void checkInventory(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            checkSingle(item, false);
        }
    }

    public boolean checkSingle(ItemStack item, boolean soft) {
        if (item == null || item.getType() == Material.AIR) return false;

        if (config.getBoolean("illegals.enabled")) {
            if (illegalsMat.contains(item.getType())) {
                if (!soft) item.setAmount(0);
                return true;
            }

            if (item.getAmount() > item.getMaxStackSize()) {
                if (!soft) item.setAmount(0);
                return true;
            }
            if (item.getType().getKey().getKey().endsWith("spawn_egg") && illegals.contains("SPAWN_EGG")) {
                if (!soft) item.setAmount(0);
                return true;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (item.getType().getKey().getKey().endsWith("potion")) {
                    PotionMeta potMeta = (PotionMeta) meta;
                    if (potMeta.hasCustomEffects() || potMeta.getBasePotionData().getType() == PotionType.UNCRAFTABLE) {
                        if (!soft) item.setAmount(0);
                        return true;
                    }
                }
                if (meta.hasAttributeModifiers()) {
                    if (!soft) item.setAmount(0);
                    return true;
                }
                if (meta.hasDisplayName() && meta.getDisplayName().contains("§")) {
                    if (!soft) item.setAmount(0);
                    return true;
                }
                if (meta.hasLore() && config.getBoolean("illegals.check-lore")) {
                    if (!soft) item.setAmount(0);
                    return true;
                }
                if (item.getType().equals(Material.PLAYER_HEAD) && meta.hasLore()) {
                    if (!soft) item.setAmount(0);
                    return true;
                }
            }
            if (!item.getEnchantments().isEmpty()) {
                for (String enchantment : this.enchantments) {
                    Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(enchantment));
                    if (this.config.getBoolean("illegals.enchantment-filter")) {
                        if (!item.getEnchantments().containsKey(ench)) {
                            return false;
                        }
                        int illegalLevel;
                        if (ench != null) {
                            illegalLevel = this.config.getBoolean("illegals.enchantment-level")
                                    ? this.config.getInt("illegals.enchantments." + enchantment + ".max-level")
                                    : ench.getMaxLevel();

                            if (item.getEnchantmentLevel(ench) > illegalLevel) {
                                if (!soft) item.setAmount(0);
                                return true;
                            }
                        }
                    } else if (ench != null && item.getEnchantmentLevel(ench) > ench.getMaxLevel()) {
                        if (!soft) item.setAmount(0);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent e) {
        if (checkSingle(e.getItem(), false)) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (checkSingle(e.getCurrentItem(), false)) e.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            Player player = (Player) e.getDamager();
            if (player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.DAMAGE_ALL) > Enchantment.DAMAGE_ALL.getMaxLevel())
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        if (checkSingle(e.getItemDrop().getItemStack(), false))
            e.setCancelled(true);
    }

    @EventHandler
    public void onLogin(PlayerJoinEvent e) {
        if (this.config.getBoolean("misc.enabled") && this.config.getBoolean("misc.join-messages")) {
            e.getPlayer().getServer().broadcastMessage(ChatColor.AQUA + e.getPlayer().getName() + ChatColor.GRAY + " joined the server");
            e.setJoinMessage(null);
        }
        checkInventory(e.getPlayer().getInventory());
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        if (this.config.getBoolean("misc.enabled") && this.config.getBoolean("misc.leave-messages")) {
            e.getPlayer().getServer().broadcastMessage(ChatColor.RED + e.getPlayer().getName() + ChatColor.GRAY + " left the server");
            e.setQuitMessage(null);
        }
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        ItemStack newItem = e.getPlayer().getInventory().getItem(e.getNewSlot());
        checkSingle(newItem, false);
    }

    @EventHandler
    public void onPlaceBlock(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();

        if (illegals.contains(e.getBlock().getType().name())) {
            e.setCancelled(true);
            checkSingle(e.getItemInHand(), false);
        }

        if (e.getBlock().getType() == Material.FURNACE
                && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()) {
            e.setCancelled(true);
            item.setAmount(0);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        checkSingle(e.getItem(), false);
    }

    @EventHandler
    public void onDispenserFire(BlockDispenseEvent e) {
        ItemStack item = e.getItem();
        if (item.getType() == Material.LINGERING_POTION || item.getType() == Material.SPLASH_POTION) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null && (meta.hasCustomEffects() || meta.getBasePotionData().getType() == PotionType.UNCRAFTABLE)) {
                item.setAmount(0);
                e.setCancelled(true);
            }
        } else if (item.getType().getKey().getKey().endsWith("spawn_egg") && illegals.contains("SPAWN_EGG")) {
            item.setAmount(0);
            e.setCancelled(true);
        }
    }

    private void loadConfig() {
        this.config = YamlConfiguration.loadConfiguration(new File(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("IllegalsX")).getDataFolder(), "config.yml"));
        this.illegals = config.getStringList("illegals.items");
        this.illegalsMat = illegals.stream().filter(str -> !str.equals("SPAWN_EGG")).map(Material::getMaterial).collect(Collectors.toList());
        this.enchantments = (this.config.getBoolean("illegals.enchantment-filter")
                ? Objects.requireNonNull(this.config.getConfigurationSection("illegals.enchantments")).getKeys(false)
                : new HashSet<>(Arrays.stream(Enchantment.values()).map(en -> en.getKey().getKey()).collect(Collectors.toList())));
    }
}
