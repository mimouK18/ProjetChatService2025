package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import fr.uga.miashs.dciss.chatservice.client.ClientMsg;
import fr.uga.miashs.dciss.chatservice.common.Packet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Map;

public class GroupeDiscussionFrame extends JFrame {

    private final JTextArea chatArea;
    private final JTextField messageField;
    private final Groupe groupe;
    private final ClientMsg client;

    public GroupeDiscussionFrame(ClientMsg client, Groupe groupe, Map<Integer, String> userMap) {
        this.client = client;
        this.groupe = groupe;

        setTitle("Discussion de groupe - " + groupe.getNom());
        setSize(500, 400);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        messageField = new JTextField();
        JButton sendButton = new JButton("Envoyer");

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener((ActionEvent e) -> envoyerMessage());
        messageField.addActionListener((ActionEvent e) -> envoyerMessage());

        // Écoute les messages du groupe
        client.addMessageListener(p -> {
            if (p.destId == groupe.getId()) {
                String sender = userMap.getOrDefault(p.srcId, "Utilisateur " + p.srcId);
                String message = new String(p.getData());
                chatArea.append("💬 " + sender + " : " + message + "\n");
            }
        });

        setVisible(true);
    }

    private void envoyerMessage() {
        try {
            String msg = messageField.getText().trim();
            if (!msg.isEmpty()) {
                client.sendPacket(groupe.getId(), msg.getBytes());
                chatArea.append("🧑 Moi : " + msg + "\n");
                messageField.setText("");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erreur d'envoi : " + e.getMessage());
        }
    }
}
