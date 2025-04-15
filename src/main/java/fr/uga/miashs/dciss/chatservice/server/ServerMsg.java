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
			stmt.executeUpdate();
		} catch (SQLException e) {
			if (!"X0Y32".equals(e.getSQLState())) { // table already exists
				throw e;
			}
		}
	}

	private void loadUsersFromDatabase() throws SQLException {
		String sql = "SELECT id, password FROM users";
		try (PreparedStatement stmt = dbConnection.prepareStatement(sql);
			 ResultSet rs = stmt.executeQuery()) {
			while (rs.next()) {
				int id = rs.getInt("id");
				String pwd = rs.getString("password");
				userPasswords.put(id, pwd);
				users.put(id, new UserMsg(id, this));
				nextUserId.updateAndGet(curr -> Math.max(curr, id + 1));
			}
		}
	}

	private void saveUserToDatabase(int id, String password) throws SQLException {
		String sql = "INSERT INTO users (id, password) VALUES (?, ?)";
		try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
			stmt.setInt(1, id);
			stmt.setString(2, password);
			stmt.executeUpdate();
		}
	}

	public void start() {
		started = true;
		while (started) {
			try {
				Socket s = serverSock.accept();

				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				int userId = dis.readInt();
				String password = dis.readUTF();

				if (userId == 0) {
					// 🆕 Création de compte
					userId = nextUserId.getAndIncrement();
					dos.writeInt(userId);
					dos.flush();
					userPasswords.put(userId, password);
					users.put(userId, new UserMsg(userId, this));
					saveUserToDatabase(userId, password);
				} else {
					// 🔐 Authentification
					String storedPassword = userPasswords.get(userId);
					if (storedPassword == null || !storedPassword.equals(password)) {
						dos.writeBoolean(false); // ❌ mot de passe incorrect
						dos.flush();
						s.close();
						continue;
					}
					dos.writeBoolean(true); // ✅ mot de passe accepté
					dos.flush();

					// ✅ Fermer l'ancien UserMsg s'il existe
					UserMsg oldUser = users.get(userId);
					if (oldUser != null) oldUser.close();
					users.put(userId, new UserMsg(userId, this));
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
