package fr.uga.miashs.dciss.chatservice.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import fr.uga.miashs.dciss.chatservice.common.Packet;
import java.util.*;

public class ServerMsg {

	private final static Logger LOG = Logger.getLogger(ServerMsg.class.getName());
	public final static int SERVER_CLIENTID = 0;
	private Connection dbConnection;

	private transient ServerSocket serverSock;
	private transient boolean started;
	private transient ExecutorService executor;
	private transient ServerPacketProcessor sp;

	// maps pour associer les id aux utilisateurs et groupes
	private Map<Integer, UserMsg> users;
	private Map<Integer, GroupMsg> groups;

	// 🔐 Map pour stocker les mots de passe des utilisateurs (chargée depuis la BDD)
	private Map<Integer, String> userPasswords = new ConcurrentHashMap<>();

	// Séquences pour générer les identifiants d'utilisateurs et de groupes
	private AtomicInteger nextUserId;
	private AtomicInteger nextGroupId;
	
	public ServerMsg(int port) throws IOException {
		serverSock = new ServerSocket(port);
		started = false;
		users = new ConcurrentHashMap<>();
		groups = new ConcurrentHashMap<>();
		nextUserId = new AtomicInteger(1);
		nextGroupId = new AtomicInteger(-1);
		sp = new ServerPacketProcessor(this);
		executor = Executors.newCachedThreadPool();

		// Connexion à SQLite et chargement des utilisateurs existants
		try {
			dbConnection = DriverManager.getConnection("jdbc:derby:chat_users.db;create=true");
			createUserTableIfNotExists();
			loadUsersFromDatabase();
		} catch (SQLException e) {
			throw new IOException("Erreur base de données", e);
		}
	}

	private void createUserTableIfNotExists() throws SQLException {
		String sql = "CREATE TABLE users (id INT PRIMARY KEY, password VARCHAR(255))";
		try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
			stmt.executeUpdate(); // Création de table si elle n'existe pas
		} catch (SQLException e) {
			if (!"X0Y32".equals(e.getSQLState())) { // X0Y32 = table already exists
				throw e;
			} else {
				// La table existe → on tente d’ajouter la colonne "username" si elle n'existe pas encore
				try {
					dbConnection.createStatement().executeUpdate("ALTER TABLE users ADD COLUMN username VARCHAR(255)");
					System.out.println("✔️ Colonne 'username' ajoutée.");
				} catch (SQLException ex) {
					if ("X0Y32".equals(ex.getSQLState()) || "42X14".equals(ex.getSQLState())) {
						System.out.println("ℹ️ Colonne 'username' existe déjà.");
					} else {
						throw ex;
					}
				}
			}
		}
	}



	private void loadUsersFromDatabase() throws SQLException {
		String sql = "SELECT id, username, password FROM users";
		try (PreparedStatement stmt = dbConnection.prepareStatement(sql);
			 ResultSet rs = stmt.executeQuery()) {
			while (rs.next()) {
				int id = rs.getInt("id");
				String username = rs.getString("username");
				String pwd = rs.getString("password");
				userPasswords.put(id, pwd);
				users.put(id, new UserMsg(id, this));
				nextUserId.updateAndGet(curr -> Math.max(curr, id + 1));
			}
		}
	}

	private void saveUserToDatabase(int id, String username, String password) throws SQLException {
		String sql = "INSERT INTO users (id, username, password) VALUES (?, ?, ?)";
		try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
			stmt.setInt(1, id);
			stmt.setString(2, username);
			stmt.setString(3, password);
			stmt.executeUpdate();
		}
	}
	
	private Map<Integer, String> getUserMap() throws SQLException {
		Map<Integer, String> map = new HashMap<>();
		String sql = "SELECT id, username FROM users";
		try (PreparedStatement stmt = dbConnection.prepareStatement(sql);
		     ResultSet rs = stmt.executeQuery()) {
			while (rs.next()) {
				int id = rs.getInt("id");
				String username = rs.getString("username");
				if (username == null) {
					username = "Utilisateur_" + id; // ou "Inconnu"
				}
				map.put(id, username);
			}
		}
		return map;
	}

	public void start() {
		started = true;
		while (started) {
			try {
				Socket s = serverSock.accept();
				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				s.setSoTimeout(100); // temps court pour détecter une commande spéciale

				boolean handled = false;

				// 🧠 Vérifie si une commande spéciale arrive immédiatement (ex: GET_USER_MAP)
				try {
					if (dis.available() > 0) {
						String cmd = dis.readUTF();
						if ("GET_USER_MAP".equals(cmd)) {
							Map<Integer, String> userMap = getUserMap();
							dos.writeInt(userMap.size());
							for (Map.Entry<Integer, String> entry : userMap.entrySet()) {
								dos.writeInt(entry.getKey());
								dos.writeUTF(entry.getValue());
							}
							dos.flush();
							s.close();
							handled = true;
						}
					}
				} catch (SocketTimeoutException ignored) {
					// Pas une commande spéciale, c’est une connexion utilisateur normale
				}

				s.setSoTimeout(0); // désactive le timeout

				if (handled) continue;

				// 🔐 Authentification ou création de compte
				int userId = dis.readInt();
				String password = dis.readUTF();

				System.out.println("Tentative de connexion :");
				System.out.println(" - ID reçu : " + userId);
				System.out.println(" - Mot de passe reçu : " + password);

				if (userId == 0) {
					// 🆕 Création de compte
					userId = nextUserId.getAndIncrement();

					String username = dis.readUTF(); // 🔄 lecture du pseudo

					dos.writeInt(userId);
					dos.flush();

					userPasswords.put(userId, password);
					users.put(userId, new UserMsg(userId, this));
					saveUserToDatabase(userId, username, password);

					System.out.println("✅ Compte créé : ID = " + userId + ", pseudo = " + username);
				} else {
					// ✅ Connexion
					String storedPassword = userPasswords.get(userId);

					System.out.println(" - Mot de passe attendu : " + storedPassword);
					System.out.println(" - Mot de passe correct ? " + password.equals(storedPassword));

					if (storedPassword == null || !storedPassword.equals(password)) {
						System.out.println("❌ Connexion refusée (mot de passe incorrect ou ID inconnu)");
						dos.writeBoolean(false);
						dos.flush();
						s.close();
						continue;
					}

					dos.writeBoolean(true);
					dos.flush();

					UserMsg oldUser = users.get(userId);
					if (oldUser != null) oldUser.close();
					users.put(userId, new UserMsg(userId, this));

					System.out.println("✅ Connexion acceptée pour l'utilisateur " + userId);
				}

				UserMsg user = users.get(userId);
				if (user != null && user.open(s)) {
					LOG.info("✅ Utilisateur " + userId + " connecté");
					executor.submit(user::receiveLoop);
					executor.submit(user::sendLoop);
				} else {
					s.close();
				}

			} catch (IOException | SQLException e) {
				LOG.info("❌ Erreur ou fermeture du serveur");
				e.printStackTrace();
			}
		}
	}



	public void processPacket(Packet p) {
		PacketProcessor pp = null;
		if (p.destId < 0) {
			UserMsg sender = users.get(p.srcId);
			GroupMsg g = groups.get(p.destId);
			if (g != null && g.getMembers().contains(sender)) {
				pp = g;
			}
		} else if (p.destId > 0) {
			pp = users.get(p.destId);
		} else {
			pp = sp;
		}

		if (pp != null) {
			pp.process(p);
		}
	}

	public boolean removeGroup(int groupId) {
		GroupMsg g = groups.remove(groupId);
		if (g == null) return false;
		g.beforeDelete();
		return true;
	}

	public boolean removeUser(int userId) {
		UserMsg u = users.remove(userId);
		if (u == null) return false;
		u.beforeDelete();
		return true;
	}

	public GroupMsg createGroup(int ownerId) {
		UserMsg owner = users.get(ownerId);
		if (owner == null) throw new ServerException("Utilisateur inconnu : " + ownerId);
		int id = nextGroupId.getAndDecrement();
		GroupMsg g = new GroupMsg(id, owner);
		groups.put(id, g);
		LOG.info("👥 Groupe " + id + " créé");
		return g;
	}

	public UserMsg getUser(int userId) {
		return users.get(userId);
	}

	public void stop() {
		started = false;
		try {
			serverSock.close();
			users.values().forEach(UserMsg::close);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		ServerMsg s = new ServerMsg(1666);
		s.start();
	}
}
