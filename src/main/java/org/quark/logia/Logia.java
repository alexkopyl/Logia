package org.quark.logia;

import com.mongodb.client.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Logia extends JavaPlugin implements Listener {
    private static final Logger logger = LogManager.getRootLogger();
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> usersCollection;
    private final Map<Player, Boolean> loginStatus = new HashMap<>();
    private final Set<Player> authenticatedPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        // Подключение обработчика событий
        Bukkit.getPluginManager().registerEvents(this, this);

        // Подключение к базе данных MongoDB
        mongoClient = MongoClients.create("mongodb://localhost:27017");
        database = mongoClient.getDatabase("minecraft");
        usersCollection = database.getCollection("users");

        logger.info("Плагин Logia загружен");

    }

    @Override
    public void onDisable() {
        if (mongoClient != null) {
            mongoClient.close();
        }
        logger.info("Плагин Logia выгружен");
    }

    //Логин игрока
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();

        authenticatedPlayers.remove(player);  // Удаляем игрока из списка авторизованных, если он повторно зашел на сервер
        loginStatus.put(player, false);

        Document query = new Document("username", player.getName());
        Document user = usersCollection.find(query).first();

        if (user == null) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.text("Для того чтобы играть на нашем сервере, необходимо зарегистрироваться на нашем сайте.", NamedTextColor.WHITE));
            logger.warn("ГОСТЬ(бот): {} :был кикнут", player.getName());
        }
    }

    //подключение игрока к серверу
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Document query = new Document("username", player.getName());
        Document user = usersCollection.find(query).first();

        player.sendMessage(Component.text("Для того, чтобы войти на сервер",NamedTextColor.AQUA));
        player.sendMessage(Component.text("используйте команду: /login <пароль>",NamedTextColor.AQUA));
        player.sendMessage(Component.text("у вас есть 20 секунд...",NamedTextColor.AQUA));

        if (user != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!loginStatus.get(player)) {
                        player.kick(Component.text("Вы не успели ввести пароль.", NamedTextColor.RED));
                        logger.warn("ИГРОК: {} :не успел ввести пароль", player.getName());
                    }
                }
            }.runTaskLater(this, 20 * 20); // 20 секунд
        }
    }

    //выход игрока
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        authenticatedPlayers.remove(player);  // Удаляем игрока из списка авторизованных, когда он выходит
    }

    public void onPasswordInput(Player player, String password) {
        if (authenticatedPlayers.contains(player)) {
            player.sendMessage(Component.text("Вы уже авторизованы!", NamedTextColor.RED));
            logger.info("ИГРОК: {} :уже авторизован (onPasswordInput)", player.getName());
            return;
        }

        Document query = new Document("username", player.getName())
                .append("password", password);
        Document user = usersCollection.find(query).first();

        if (user != null) {
            String hashPassword = user.getString("password");
            if (BCrypt.checkPwd(password, hashPassword)) { //выбрать кодировщика
                loginStatus.put(player, true);
                authenticatedPlayers.add(player);  // Добавляем игрока в список авторизованных
                player.sendMessage(Component.text("Добро пожаловать!", NamedTextColor.GREEN));
                logger.info("ИГРОК: {} :авторизован", player.getName());
            } else {
                player.kick(Component.text("Неправильный пароль."));
                logger.info("ИГРОК: {} :ввел неверный пароль", player.getName());
            }
        }
        else {
            player.kick(Component.text("Неправильный пароль."));
            logger.info("ИГРОК: {} :ввел неверный пароль", player.getName());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("login") && sender instanceof Player) {
            Player player = (Player) sender;
            if (authenticatedPlayers.contains(player)) {
                player.sendMessage(Component.text("Вы уже авторизованы!", NamedTextColor.RED));
                logger.info("ИГРОК: {} :уже авторизован (onCommand)", player.getName());
                return true;
            }
            if (args.length == 1) {
                String password = args[0];
                onPasswordInput(player, password);
                return true;
            } else {
                player.sendMessage(Component.text("Используйте команду: /login <пароль>",NamedTextColor.AQUA));
                logger.warn("ИГРОК: {} :ввел неверную команду", player.getName());
                return false;
            }
        }
        return false;
    }

}
