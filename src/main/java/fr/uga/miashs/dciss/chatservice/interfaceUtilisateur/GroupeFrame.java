package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;

public class GroupeFrame extends JFrame {

    private final Groupe groupe;
    private final DefaultListModel<String> membresModel = new DefaultListModel<>();
    private final Map<Integer, String> userMap;
    private final int currentUserId;

    public GroupeFrame(Groupe groupe, Map<Integer, String> userMap, int currentUserId) {
        this.groupe = groupe;
        this.userMap = userMap;
        this.currentUserId = currentUserId;

        setTitle("Groupe : " + groupe.getNom());
        setSize(400, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());

        // Liste des membres
        JList<String> membresList = new JList<>(membresModel);
        refreshMembres();
        panel.add(new JScrollPane(membresList), BorderLayout.CENTER);

        // Zone d'ajout
        JPanel addPanel = new JPanel(new BorderLayout());
        JTextField idField = new JTextField();
        JButton addButton = new JButton("Ajouter membre");
        addPanel.add(idField, BorderLayout.CENTER);
        addPanel.add(addButton, BorderLayout.EAST);

        addButton.addActionListener((ActionEvent e) -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                if (userMap.containsKey(id)) {
                    groupe.ajouterMembre(id);
                    refreshMembres();
                    JOptionPane.showMessageDialog(this, "Utilisateur " + id + " ajouté !");
                    idField.setText("");
                } else {
                    JOptionPane.showMessageDialog(this, "ID utilisateur inconnu.");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage());
            }
        });

        // Bouton suppression (créateur uniquement)
        JButton removeButton = new JButton("Supprimer membre sélectionné");
        removeButton.addActionListener(e -> {
            int index = membresList.getSelectedIndex();
            if (index != -1) {
                int userId = groupe.getMembres().get(index);
                if (userId == groupe.getCreateurId()) {
                    JOptionPane.showMessageDialog(this, "❌ Impossible de retirer le créateur !");
                } else {
                    groupe.supprimerMembre(userId);
                    refreshMembres();
                }
            }
        });

        if (currentUserId == groupe.getCreateurId()) {
            panel.add(removeButton, BorderLayout.SOUTH);
        }

        panel.add(addPanel, BorderLayout.NORTH);
        add(panel);
        setVisible(true);
    }

    private void refreshMembres() {
        membresModel.clear();
        List<Integer> membres = groupe.getMembres();
        for (Integer id : membres) {
            String name = userMap.getOrDefault(id, "Utilisateur " + id);
            String label = name + (id == groupe.getCreateurId() ? " 👑 (créateur)" : "");
            membresModel.addElement(label);
        }
    }
}
