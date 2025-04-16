package fr.uga.miashs.dciss.chatservice.client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import fr.uga.miashs.dciss.chatservice.common.Packet;

public class ClientMsg {

	private String serverAddress;
	private int serverPort;

	private Socket s;
	private DataOutputStream dos;
	private DataInputStream dis;

	private int identifier;

	private List<MessageListener> mListeners;
	private List<ConnectionListener> cListeners;

	private String password;

	public ClientMsg(int id, String address, int port) {
		if (id < 0)
			throw new IllegalArgumentException("id must not be less than 0");
		if (port <= 0)
			throw new IllegalArgumentException("Server port must be greater than 0");
		serverAddress = address;
		serverPort = port;
		identifier = id;
		mListeners = new ArrayList<>();
		cListeners = new ArrayList<>();
	}

	public ClientMsg(String address, int port) {
		this(0, address, port);
	}

	public void addMessageListener(MessageListener l) {
		if (l != null)
			mListeners.add(l);
	}

	protected void notifyMessageListeners(Packet p) {
		mListeners.forEach(x -> x.messageReceived(p));
	}

	public void addConnectionListener(ConnectionListener l) {
		if (l != null)
			cListeners.add(l);
	}

	protected void notifyConnectionListeners(boolean active) {
		cListeners.forEach(x -> x.connectionEvent(active));
	}

	public int getIdentifier() {
		return identifier;
	}

	public void startSession(String password) throws UnknownHostException {
		this.password = password;
		if (s == null || s.isClosed()) {
			try {
				System.out.println("🔌 Tentative de connexion au serveur...");
				s = new Socket(serverAddress, serverPort);
				dos = new DataOutputStream(s.getOutputStream());
				dis = new DataInputStream(s.getInputStream());

				// Envoi de l'identifiant et du mot de passe
				dos.writeInt(identifier);
				dos.writeUTF(password);
				dos.flush();
				System.out.println("📤 Identifiants envoyés au serveur.");

				if (identifier == 0) {
					// Création de compte
					identifier = dis.readInt();
					System.out.println("✅ Compte créé. ID reçu : " + identifier);
				} else {
					// Connexion normale
					boolean accepted = dis.readBoolean();
					System.out.println("📥 Réponse du serveur : " + accepted);
					if (!accepted) {
						throw new IOException("Mot de passe incorrect.");
					}
					System.out.println("✅ Connexion acceptée.");
				}

				new Thread(() -> receiveLoop()).start();
				System.out.println("🔄 Démarrage de la réception des messages.");
				notifyConnectionListeners(true);
			} catch (IOException e) {
				System.out.println("❌ Erreur de connexion : " + e.getMessage());
				e.printStackTrace();
				closeSession();
				throw new RuntimeException("Erreur de connexion : " + e.getMessage());
			}
		}
	}

	
	public void startSession(String password, String pseudo) throws UnknownHostException {
		this.password = password;
		if (s == null || s.isClosed()) {
			try {
				s = new Socket(serverAddress, serverPort);
				dos = new DataOutputStream(s.getOutputStream());
				dis = new DataInputStream(s.getInputStream());

				// Envoi de l'identifiant (0), mot de passe et pseudo
				dos.writeInt(identifier);       // = 0 pour un nouveau compte
				dos.writeUTF(password);
				dos.writeUTF(pseudo);           // 🆕 on envoie le pseudo ici
				dos.flush();

				// Le serveur renvoie l'identifiant attribué
				identifier = dis.readInt();

				new Thread(this::receiveLoop).start();
				notifyConnectionListeners(true);

			} catch (IOException e) {
				e.printStackTrace();
				closeSession();
				throw new RuntimeException("Erreur de connexion : " + e.getMessage());
			}
		}
	}


	public void sendPacket(int destId, byte[] data) {
		try {
			synchronized (dos) {
				dos.writeInt(destId);
				dos.writeInt(data.length);
				dos.write(data);
				dos.flush();
			}
		} catch (IOException e) {
			closeSession();
		}
	}

	private void receiveLoop() {
		try {
			while (s != null && !s.isClosed()) {
				int sender = dis.readInt();
				int dest = dis.readInt();
				int length = dis.readInt();
				byte[] data = new byte[length];
				dis.readFully(data);
				notifyMessageListeners(new Packet(sender, dest, data));
			}
		} catch (IOException e) {
			// Erreur ou fermeture
		}
		closeSession();
	}

	public void closeSession() {
		try {
			if (s != null)
				s.close();
		} catch (IOException e) {
			// rien
		}
		s = null;
		notifyConnectionListeners(false);
	}

	// 📦 Nouvelle méthode : récupérer la map ID → pseudo depuis le serveur
	public Map<Integer, String> getUserMapFromServer() throws IOException {
	    Map<Integer, String> map = new HashMap<>();

	    // ⚠️ On ouvre une nouvelle connexion temporaire
	    try (Socket tempSocket = new Socket(serverAddress, serverPort)) {
	        DataOutputStream tempDos = new DataOutputStream(tempSocket.getOutputStream());
	        DataInputStream tempDis = new DataInputStream(tempSocket.getInputStream());

	        // 📤 Envoi de la commande spéciale
	        tempDos.writeUTF("GET_USER_MAP");
	        tempDos.flush();

	        // 📥 Réception des données
	        int size = tempDis.readInt(); // combien d'utilisateurs
	        for (int i = 0; i < size; i++) {
	            int id = tempDis.readInt();
	            String name = tempDis.readUTF();
	            map.put(id, name);
	        }
	    }

	    return map;
	}

}
