package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;
import fr.uga.miashs.dciss.chatservice.client.ClientMsg;
import fr.uga.miashs.dciss.chatservice.client.MessageListener;
import fr.uga.miashs.dciss.chatservice.common.Packet;
import java.util.List;
import java.util.ArrayList;

public class ChatFrame extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private JLabel conversationLabel;
    private ClientMsg client;
    private Map<Integer, String> userMap;
    private List<Groupe> groupes;
    private Groupe groupeActuel = null;
    private Integer userActuelId = null;

    private DefaultListModel<String> groupListModel;
    private JList<String> groupList;

    private DefaultListModel<String> userListModel;
    private JList<String> userList;

    private Map<Integer, List<String>> messagesParUtilisateur = new HashMap<>();
    private Map<Integer, List<String>> messagesParGroupe = new HashMap<>();

    public ChatFrame(ClientMsg client) {
        this.client = client;
        this.groupes = new ArrayList<>();

        setTitle("Chat Application");
        setSize(800, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        inputField = new JTextField();

        conversationLabel = new JLabel("Aucune conversation", JLabel.CENTER);
        conversationLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JButton sendButton = new JButton("Envoyer");
        JButton createGroupButton = new JButton("Créer un Groupe");
        JButton addUserButton = new JButton("Ajouter Utilisateur");
        JButton removeUserButton = new JButton("Supprimer Utilisateur");

        groupListModel = new DefaultListModel<>();
        groupList = new JList<>(groupListModel);
        JScrollPane groupScroll = new JScrollPane(groupList);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScroll = new JScrollPane(userList);

        try {
            userMap = client.getUserMapFromServer();
            if (userMap != null) {
                userMap.forEach((id, name) -> userListModel.addElement(name));
            }
        } catch (Exception e) {
            e.printStackTrace();
            userMap = new HashMap<>();
        }

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(createGroupButton, BorderLayout.NORTH);
        leftPanel.add(groupScroll, BorderLayout.CENTER);

        JPanel groupButtons = new JPanel(new GridLayout(2, 1));
        groupButtons.add(addUserButton);
        groupButtons.add(removeUserButton);
        leftPanel.add(groupButtons, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JLabel("Utilisateurs"), BorderLayout.NORTH);
        rightPanel.add(userScroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(conversationLabel, BorderLayout.NORTH);
        add(leftPanel, BorderLayout.WEST);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        createGroupButton.addActionListener(e -> createGroup());
        addUserButton.addActionListener(e -> addUserToGroup());
        removeUserButton.addActionListener(e -> removeUserFromGroup());

        groupList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = groupList.getSelectedIndex();
                if (selectedIndex != -1) {
                    groupeActuel = groupes.get(selectedIndex);
                    userActuelId = null;
                    chatArea.setText("");
                    conversationLabel.setText("🔵 Rejoint groupe : " + groupeActuel.getNom());
                    refreshChatDisplay();
                }
            }
        });

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                userActuelId = userList.getSelectedIndex() + 1;
                groupeActuel = null;
                chatArea.setText("");
                conversationLabel.setText("🟢 Chat avec : " + userMap.get(userActuelId));
                refreshChatDisplay();
            }
        });

        client.addMessageListener(new MessageListener() {
            @Override
            public void messageReceived(Packet p) {
                String msg = new String(p.getData());
                String senderName = userMap.getOrDefault(p.getSrcId(), "Utilisateur " + p.getSrcId());

                if (groupeActuel != null && groupeActuel.getMembres().contains(p.getSrcId())) {
                    messagesParGroupe.putIfAbsent(groupeActuel.getId(), new ArrayList<>());
                    messagesParGroupe.get(groupeActuel.getId()).add(senderName + " : " + msg);
                } else {
                    messagesParUtilisateur.putIfAbsent(p.getSrcId(), new ArrayList<>());
                    messagesParUtilisateur.get(p.getSrcId()).add(senderName + " : " + msg);
                }

                refreshChatDisplay();
            }
        });

        setVisible(true);
    }

    private void refreshChatDisplay() {
        chatArea.setText("");

        if (groupeActuel != null) {
            List<String> msgs = messagesParGroupe.getOrDefault(groupeActuel.getId(), new ArrayList<>());
            for (String m : msgs) {
                chatArea.append(m + "\n");
            }

            chatArea.append("\n👥 Membres du groupe :\n");
            for (Integer userId : groupeActuel.getMembres()) {
                String memberName = userMap.getOrDefault(userId, "Utilisateur " + userId);
                chatArea.append("- " + memberName + "\n");
            }
        } else if (userActuelId != null) {
            List<String> msgs = messagesParUtilisateur.getOrDefault(userActuelId, new ArrayList<>());
            for (String m : msgs) {
                chatArea.append(m + "\n");
            }
        }
    }

    private void sendMessage() {
        String msg = inputField.getText();
        if (msg.isEmpty()) return;

        if (groupeActuel != null) {
            for (Integer id : groupeActuel.getMembres()) {
                try {
                    client.sendPacket(id, msg.getBytes());
                } catch (Exception e) {
                    chatArea.append("⚠️ Erreur envoi à " + id + "\n");
                }
            }
        } else if (userActuelId != null) {
            try {
                client.sendPacket(userActuelId, msg.getBytes());
            } catch (Exception e) {
                chatArea.append("⚠️ Erreur envoi à " + userActuelId + "\n");
            }
        } else {
            chatArea.append("⚠️ Aucun destinataire sélectionné\n");
        }

        inputField.setText("");
        refreshChatDisplay();
    }

    private void createGroup() {
        String name = JOptionPane.showInputDialog(this, "Nom du groupe :");
        if (name != null && !name.isEmpty()) {
            int groupId = (int)(System.currentTimeMillis() % 100000);
            Groupe g = new Groupe(groupId, name, 1);
            g.ajouterMembre(1);
            groupes.add(g);
            groupListModel.addElement(name);
            chatArea.append("✅ Groupe créé : " + name + "\n");
        }
    }

    private void addUserToGroup() {
        if (groupeActuel == null) {
            chatArea.append("⚠️ Sélectionnez un groupe\n");
            return;
        }

        int index = userList.getSelectedIndex();
        if (index != -1) {
            int userId = (int) userMap.keySet().toArray()[index];
            if (!groupeActuel.getMembres().contains(userId)) {
                groupeActuel.ajouterMembre(userId);
                String notif = "ADD_TO_GROUP|groupId=" + groupeActuel.getId() + "|groupName=" + groupeActuel.getNom();
                client.sendPacket(userId, notif.getBytes());
                chatArea.append("👤 " + userMap.get(userId) + " ajouté au groupe.\n");
                refreshChatDisplay();
            }
        }
    }

    private void removeUserFromGroup() {
        if (groupeActuel == null) {
            chatArea.append("⚠️ Sélectionnez un groupe\n");
            return;
        }

        int index = userList.getSelectedIndex();
        if (index != -1) {
            int userId = (int) userMap.keySet().toArray()[index];
            if (groupeActuel.getMembres().contains(userId)) {
                groupeActuel.supprimerMembre(userId);
                chatArea.append("👤 " + userMap.get(userId) + " retiré du groupe.\n");
            }
        }
    }
}
