package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import java.awt.BorderLayout;
import java.io.IOException;
import java.util.Map;

import javax.swing.*;

import fr.uga.miashs.dciss.chatservice.client.ClientMsg;
import fr.uga.miashs.dciss.chatservice.client.MessageListener;
import fr.uga.miashs.dciss.chatservice.common.Packet;

public class ChatFrame extends JFrame {
	private JTextArea chatArea;
	private JTextField inputField;
	private JTextField destField;
	private ClientMsg client;
	private Map<Integer, String> userMap;

	public ChatFrame(ClientMsg client) {
		this.client = client;

		setTitle("Chat");
		setSize(500, 400);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		chatArea = new JTextArea();
		chatArea.setEditable(false);

		inputField = new JTextField();
		destField = new JTextField("1");
		JButton sendButton = new JButton("Envoyer");

		try {
			userMap = client.getUserMapFromServer(); // 📦 Récupération des pseudos
		} catch (IOException e) {
			e.printStackTrace();
			userMap = null;
		}

		sendButton.addActionListener(e -> {
			String message = inputField.getText();
			if (!message.isEmpty()) {
				try {
					int destId = Integer.parseInt(destField.getText());
					client.sendPacket(destId, message.getBytes());
					String destName = userMap != null ? userMap.getOrDefault(destId, "Utilisateur " + destId) : "Utilisateur " + destId;
					chatArea.append("Moi → " + destName + " : " + message + "\n");
					inputField.setText("");
				} catch (NumberFormatException ex) {
					chatArea.append("⚠️ ID invalide\n");
				}
			}
		});

		client.addMessageListener(new MessageListener() {
			@Override
			public void messageReceived(Packet p) {
				String msg = new String(p.getData());
				String senderName = userMap != null ? userMap.getOrDefault(p.getSrcId(), "Utilisateur " + p.getSrcId()) : "Utilisateur " + p.getSrcId();
				chatArea.append(senderName + " : " + msg + "\n");
			}
		});

		JPanel inputPanel = new JPanel(new BorderLayout());
		inputPanel.add(inputField, BorderLayout.CENTER);
		inputPanel.add(sendButton, BorderLayout.EAST);

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(new JLabel("Envoyer à l’ID : "), BorderLayout.WEST);
		topPanel.add(destField, BorderLayout.CENTER);

		setLayout(new BorderLayout());
		add(topPanel, BorderLayout.NORTH);
		add(new JScrollPane(chatArea), BorderLayout.CENTER);
		add(inputPanel, BorderLayout.SOUTH);

		setVisible(true);
	}
}
