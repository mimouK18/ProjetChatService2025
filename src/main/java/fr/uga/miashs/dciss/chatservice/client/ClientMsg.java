package fr.uga.miashs.dciss.chatservice.client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import fr.uga.miashs.dciss.chatservice.common.Packet;

/**
 * Manages the connection to a ServerMsg. Method startSession() is used to
 * establish the connection. Then messages can be send by a call to sendPacket.
 * The reception is done asynchronously (internally by the method receiveLoop())
 * and the reception of a message is notified to MessagesListeners. To register
 * a MessageListener, the method addMessageListener has to be called. Session
 * are closed thanks to the method closeSession().
 */
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

	// Constructeur pour un client avec un identifiant connu
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

	// Constructeur pour un client sans identifiant (création de compte)
	public ClientMsg(String address, int port) {
		this(0, address, port);
	}

	// Ajouter un écouteur de messages
	public void addMessageListener(MessageListener l) {
		if (l != null)
			mListeners.add(l);
	}

	protected void notifyMessageListeners(Packet p) {
		mListeners.forEach(x -> x.messageReceived(p));
	}

	// Ajouter un écouteur de connexion
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

	/**
	 * Méthode appelée pour établir la connexion avec mot de passe.
	 */
	public void startSession(String password) throws UnknownHostException {
		this.password = password;
		if (s == null || s.isClosed()) {
			try {
				s = new Socket(serverAddress, serverPort);
				dos = new DataOutputStream(s.getOutputStream());
				dis = new DataInputStream(s.getInputStream());

				// Envoi de l'identifiant et du mot de passe
				dos.writeInt(identifier);
				dos.writeUTF(password);
				dos.flush();

				if (identifier == 0) {
					// Création de compte : le serveur renvoie seulement un identifiant
					identifier = dis.readInt();
				} else {
					// Connexion normale : le serveur renvoie un boolean (authentification réussie ?)
					boolean accepted = dis.readBoolean();
					if (!accepted) {
						throw new IOException("Mot de passe incorrect.");
					}
				}

				// Démarrage de la boucle de réception
				new Thread(() -> receiveLoop()).start();
				notifyConnectionListeners(true);
			} catch (IOException e) {
				e.printStackTrace();
				closeSession();
				throw new RuntimeException("Erreur de connexion : " + e.getMessage());
			}
		}
	}

	// Envoie d’un paquet à un destinataire
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

	// Boucle d'écoute des messages entrants
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

	// Fermeture de la session
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
}
