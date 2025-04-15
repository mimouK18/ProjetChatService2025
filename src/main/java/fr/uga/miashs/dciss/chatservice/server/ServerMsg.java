package fr.uga.miashs.dciss.chatservice.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import fr.uga.miashs.dciss.chatservice.common.Packet;
import java.util.*;

public class ServerMsg {

	private final static Logger LOG = Logger.getLogger(ServerMsg.class.getName());
	public final static int SERVER_CLIENTID = 0;

	private transient ServerSocket serverSock;
	private transient boolean started;
	private transient ExecutorService executor;
	private transient ServerPacketProcessor sp;

	// maps pour associer les id aux utilisateurs et groupes
	private Map<Integer, UserMsg> users;
	private Map<Integer, GroupMsg> groups;

	// 🔐 Map pour stocker les mots de passe des utilisateurs
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
					if (oldUser != null) {
						oldUser.close(); // libérer socket/threads précédents
					}

					// 🛠️ Recréer le UserMsg
					users.put(userId, new UserMsg(userId, this));
				}

				UserMsg user = users.get(userId);
				if (user != null && user.open(s)) {
					LOG.info("✅ Utilisateur " + userId + " connecté");
					executor.submit(() -> user.receiveLoop());
					executor.submit(() -> user.sendLoop());
				} else {
					s.close();
				}

			} catch (IOException e) {
				LOG.info("❌ Erreur ou fermeture du serveur");
				e.printStackTrace();
			}
		}
	}

	// 🔄 Redirection des paquets en fonction de la destination
	public void processPacket(Packet p) {
		PacketProcessor pp = null;
		if (p.destId < 0) {
			// Groupe
			UserMsg sender = users.get(p.srcId);
			GroupMsg g = groups.get(p.destId);
			if (g != null && g.getMembers().contains(sender)) {
				pp = g;
			}
		} else if (p.destId > 0) {
			// Utilisateur individuel
			pp = users.get(p.destId);
		} else {
			// Paquet de gestion pour le serveur
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
