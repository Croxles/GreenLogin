
package me.greenlogin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GreenLogin extends JavaPlugin implements Listener {

    private final Set<UUID> logged = new HashSet<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Map<UUID, Location> positions = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private boolean allowed(Player p) {
        return logged.contains(p.getUniqueId())
                || p.hasPermission("greenlogin.admin");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("GreenLogin aktif!");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        logged.remove(id);
        attempts.put(id, 0);
        positions.put(id, p.getLocation().clone());

        if (getConfig().contains("users." + id)) {
            p.sendTitle(
                    color("&a&lGIRIS YAP"),
                    color("&e/gir <sifre>"),
                    5, 100, 10
            );
            p.sendMessage(color("&aGiris yapmak icin: &e/gir <sifre>"));
        } else {
            p.sendTitle(
                    color("&a&lKAYIT OL"),
                    color("&e/kayit <sifre> <sifre>"),
                    5, 100, 10
            );
            p.sendMessage(color("&aKayit olmak icin: &e/kayit <sifre> <sifre>"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        logged.remove(id);
        attempts.remove(id);
        positions.remove(id);
    }

    private String hash(String password) throws Exception {
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, 120000, 256
        );

        byte[] result = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();

        spec.clearPassword();

        return Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(result);
    }

    private boolean checkPassword(String password, String stored)
            throws Exception {
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, 120000, 256
        );

        byte[] actual = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();

        spec.clearPassword();
        return MessageDigest.isEqual(expected, actual);
    }

    private void savePassword(UUID id, String password) throws Exception {
        getConfig().set("users." + id, hash(password));
        saveConfig();
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command,
            String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Bu komut oyuncular icindir.");
            return true;
        }

        UUID id = p.getUniqueId();
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("kayit")) {
            if (allowed(p)) {
                p.sendMessage(color("&aZaten giris yaptin."));
                return true;
            }

            if (getConfig().contains("users." + id)) {
                p.sendMessage(color("&cZaten kayitlisin. /gir <sifre>"));
                return true;
            }

            if (args.length != 2) {
                p.sendMessage(color("&e/kayit <sifre> <sifre>"));
                return true;
            }

            if (args[0].length() < 6) {
                p.sendMessage(color("&cSifre en az 6 karakter olmali!"));
                return true;
            }

            if (!args[0].equals(args[1])) {
                p.sendMessage(color("&cSifreler ayni degil!"));
                return true;
            }

            try {
                savePassword(id, args[0]);
                logged.add(id);
                attempts.remove(id);
                p.sendTitle(color("&a&lKAYIT BASARILI"),
                        color("&aHos geldin!"), 5, 50, 10);
                p.sendMessage(color("&aKayit tamamlandi!"));
            } catch (Exception ex) {
                p.sendMessage(color("&cKayit yapilamadi."));
                getLogger().severe("Kayit hatasi: " + ex.getMessage());
            }
            return true;
        }

        if (cmd.equals("gir")) {
            if (allowed(p)) {
                p.sendMessage(color("&aZaten giris yaptin."));
                return true;
            }

            if (args.length != 1) {
                p.sendMessage(color("&e/gir <sifre>"));
                return true;
            }

            String stored = getConfig().getString("users." + id);
            if (stored == null) {
                p.sendMessage(color("&cOnce /kayit <sifre> <sifre> yaz!"));
                return true;
            }

            try {
                if (checkPassword(args[0], stored)) {
                    logged.add(id);
                    attempts.remove(id);
                    p.sendTitle(color("&a&lGIRIS BASARILI"),
                            color("&aOyuna hos geldin!"), 5, 50, 10);
                    p.sendMessage(color("&aGiris basarili!"));
                } else {
                    int used = attempts.merge(id, 1, Integer::sum);
                    int left = 3 - used;
                    if (left <= 0) {
                        p.kickPlayer(color("&c3 yanlis deneme yaptin!"));
                    } else {
                        p.sendMessage(color("&cYanlis sifre! &eKalan hak: " + left));
                    }
                }
            } catch (Exception ex) {
                p.sendMessage(color("&cGiris islemi basarisiz."));
                getLogger().warning("Giris hatasi: " + ex.getMessage());
            }
            return true;
        }

        if (cmd.equals("sifredeyisdir")) {
            if (!allowed(p)) {
                p.sendMessage(color("&cOnce giris yapmalisin!"));
                return true;
            }

            if (args.length != 2) {
                p.sendMessage(color("&e/sifredeyisdir <eskiSifre> <yeniSifre>"));
                return true;
            }

            if (args[1].length() < 6) {
                p.sendMessage(color("&cYeni sifre en az 6 karakter olmali!"));
                return true;
            }

            String stored = getConfig().getString("users." + id);
            if (stored == null) {
                p.sendMessage(color("&cKayitli sifre bulunamadi."));
                return true;
            }

            try {
                if (!checkPassword(args[0], stored)) {
                    p.sendMessage(color("&cEski sifren yanlis!"));
                    return true;
                }

                savePassword(id, args[1]);
                p.sendMessage(color("&aSifren basariyla degistirildi!"));
            } catch (Exception ex) {
                p.sendMessage(color("&cSifre degistirilemedi."));
                getLogger().warning("Sifre degistirme hatasi: " + ex.getMessage());
            }
            return true;
        }

        return false;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (allowed(p) || e.getTo() == null) return;

        if (e.getFrom().getX() != e.getTo().getX()
                || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler
    public void onCommandUse(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (allowed(p)) return;

        String msg = e.getMessage().toLowerCase().trim();
        if (msg.matches("^/(gir|kayit)(\\s+.*)?$")) return;

        e.setCancelled(true);
        p.sendMessage(color("&cOnce /gir veya /kayit kullanmalisin!"));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!allowed(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!allowed(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!allowed(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!allowed(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && !allowed(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && !allowed(p)) {
            e.setCancelled(true);
        }
    }
            }
                
