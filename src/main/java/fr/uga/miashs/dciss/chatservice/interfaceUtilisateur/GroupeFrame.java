package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupeFrame extends JFrame {

    private final Groupe groupe;
    private final Map<Integer, String> userMap;
    private final int currentUserId;
    private final DefaultListModel<String> membresModel = new DefaultListModel<>();
    private Runnable onUpdateUI;

    public GroupeFrame(Groupe groupe, Map<Integer, String> userMap, int currentUserId) {
        this(groupe, userMap, currentUserId, null);
    }

    public GroupeFrame(Groupe groupe, Map<Integer, String> userMap, int currentUserId, Runnable onUpdateUI) {
        this.groupe = groupe;
        this.userMap = userMap;
        this.currentUserId = currentUserId;
        this.onUpdateUI = onUpdateUI;

        setTitle("Groupe: " + groupe.getNom());
        setSize(400, 300);
        setLocationRelativeTo(null);

        JList<String> membresList = new JList<>(membresModel);
        JScrollPane membresScroll = new JScrollPane(membresList);

        JComboBox<Integer> ajouterCombo = new JComboBox<>();
        for (Integer id : userMap.keySet()) {
            if (id != currentUserId && !groupe.getMembres().contains(id)) {
                ajouterCombo.addItem(id);
            }
        }

        JButton ajouterBtn = new JButton("Ajouter");
        ajouterBtn.addActionListener((ActionEvent e) -> {
            Integer selectedId = (Integer) ajouterCombo.getSelectedItem();
            if (selectedId != null) {
                groupe.ajouterMembre(selectedId);
                updateMembres();
                ajouterCombo.removeItem(selectedId);
                if (onUpdateUI != null) onUpdateUI.run();
            }
        });

        JPanel ajouterPanel = new JPanel(new BorderLayout());
        ajouterPanel.add(ajouterCombo, BorderLayout.CENTER);
        ajouterPanel.add(ajouterBtn, BorderLayout.EAST);

        add(membresScroll, BorderLayout.CENTER);
        add(ajouterPanel, BorderLayout.SOUTH);

        updateMembres();
        setVisible(true);
    }

    private void updateMembres() {
        membresModel.clear();
        for (Integer id : groupe.getMembres()) {
            String name = userMap.getOrDefault(id, "Utilisateur " + id);
            membresModel.addElement(name);
        }
    }
}
