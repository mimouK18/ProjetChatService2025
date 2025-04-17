package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import fr.uga.miashs.dciss.chatservice.client.ClientMsg;
import fr.uga.miashs.dciss.chatservice.client.MessageListener;
import fr.uga.miashs.dciss.chatservice.common.Packet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;


public class ChatFrame extends JFrame {

    private final ClientMsg client;
    private final JTextArea chatArea;
    private final JTextField messageField;
    private final JComboBox<Integer> userSelector;
    private final Map<Integer, String> userMap;
    private final List<Groupe> groupes = new ArrayList<>();

    public ChatFrame(ClientMsg client) throws Exception {
        this.client = client;
        setTitle("Chat - Utilisateur " + client.getIdentifier());
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        userMap = client.getUserMapFromServer();

        JTabbedPane tabbedPane = new JTabbedPane();

        // === ONGLET 1 : Discussion privée ===
        JPanel chatPanel = new JPanel(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        messageField = new JTextField();
        JButton sendButton = new JButton("Envoyer");

        userSelector = new JComboBox<>();
        for (Integer id : userMap.keySet()) {
            if (id != client.getIdentifier()) {
                userSelector.addItem(id);
            }
        }

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(userSelector, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatPanel.add(scrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        client.addMessageListener(new MessageListener() {
            @Override
            public void messageReceived(Packet p) {
                String senderName = userMap.getOrDefault(p.srcId, "Utilisateur " + p.srcId);
                String content = new String(p.getData());
                chatArea.append("💬 " + senderName + " : " + content + "\n");
            }
        });

        // === ONGLET 2 : Groupes ===
        JPanel groupePanel = new JPanel(new BorderLayout());
        JButton creerGroupeButton = new JButton("Créer un groupe");
        DefaultListModel<String> groupeListModel = new DefaultListModel<>();
        JList<String> groupeList = new JList<>(groupeListModel);
        JScrollPane groupeScroll = new JScrollPane(groupeList);

        creerGroupeButton.addActionListener((ActionEvent e) -> {
            String nomGroupe = JOptionPane.showInputDialog(this, "Nom du groupe :");
            if (nomGroupe != null && !nomGroupe.trim().isEmpty()) {
                Groupe groupe = new Groupe(-1, nomGroupe.trim(), client.getIdentifier());
                groupes.add(groupe);
                groupeListModel.addElement(groupe.toString());
                new GroupeFrame(groupe, userMap, client.getIdentifier());
            }
        });

        groupePanel.add(creerGroupeButton, BorderLayout.NORTH);
        groupePanel.add(groupeScroll, BorderLayout.CENTER);

        // Ajout des onglets au JTabbedPane
        tabbedPane.addTab("Messages privés", chatPanel);
        tabbedPane.addTab("Groupes", groupePanel);

        add(tabbedPane);
        setVisible(true);
    }

    private void sendMessage() {
        try {
            int destId = (Integer) userSelector.getSelectedItem();
            String message = messageField.getText().trim();
            if (!message.isEmpty()) {
                client.sendPacket(destId, message.getBytes());
                String recipient = userMap.getOrDefault(destId, "Utilisateur " + destId);
                chatArea.append("🧑 Moi → " + recipient + " : " + message + "\n");
                messageField.setText("");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erreur d'envoi : " + ex.getMessage());
        }
    }
}
