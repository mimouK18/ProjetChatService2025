package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import fr.uga.miashs.dciss.chatservice.client.ClientMsg;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {

    public LoginFrame() {
        setTitle("Connexion au Chat");
        setSize(300, 180);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // centrer la fenêtre

        JTextField idField = new JTextField();
        JPasswordField passwordField = new JPasswordField();

        JButton connectButton = new JButton("Connexion");
        JButton createButton = new JButton("Créer un compte");

        // Connexion à un compte existant
        connectButton.addActionListener(e -> {
            try {
                int id = Integer.parseInt(idField.getText());
                String password = new String(passwordField.getPassword());

                String ip = "localhost"; // IP fixe
                int port = 1666;         // Port fixe

                ClientMsg client = new ClientMsg(id, ip, port);
                client.startSession(password); // envoyer le mot de passe au moment de l’authentification

                new ChatFrame(client);
                dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur de connexion : " + ex.getMessage());
            }
        });

        // Création d’un nouveau compte
        createButton.addActionListener(e -> {
            try {
                String password = new String(passwordField.getPassword());

                String ip = "localhost"; // IP fixe
                int port = 1666;         // Port fixe

                ClientMsg client = new ClientMsg(0, ip, port); // Création de compte (id == 0)
                client.startSession(password); // mot de passe transmis ici aussi

                JOptionPane.showMessageDialog(this, "Nouveau compte créé. Identifiant : " + client.getIdentifier());

                new ChatFrame(client);
                dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur création de compte : " + ex.getMessage());
            }
        });

        setLayout(new GridLayout(3, 2, 5, 5));
        add(new JLabel("ID utilisateur :")); add(idField);
        add(new JLabel("Mot de passe :")); add(passwordField);
        add(connectButton); add(createButton);

        setVisible(true);
    }
}
