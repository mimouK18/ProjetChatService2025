package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import fr.uga.miashs.dciss.chatservice.client.ClientMsg;

import javax.swing.*;



import java.awt.*;

public class LoginFrame extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);

    public LoginFrame() {
        setTitle("Bienvenue sur le Chat");
        setSize(350, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Page principale (accueil)
        JPanel welcomePanel = new JPanel(new GridLayout(2, 1, 10, 10));
        JButton connectButton = new JButton("Connexion");
        JButton createButton = new JButton("Créer un compte");
        welcomePanel.add(connectButton);
        welcomePanel.add(createButton);

        // Page de connexion
        JPanel loginPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField idField = new JTextField();
        JPasswordField passwordFieldLogin = new JPasswordField();
        JButton loginSubmit = new JButton("Se connecter");
        loginPanel.add(new JLabel("ID utilisateur :")); loginPanel.add(idField);
        loginPanel.add(new JLabel("Mot de passe :")); loginPanel.add(passwordFieldLogin);
        loginPanel.add(new JLabel("")); loginPanel.add(loginSubmit);

        // Page de création
        JPanel createPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField pseudoField = new JTextField();
        JPasswordField passwordFieldCreate = new JPasswordField();
        JButton createSubmit = new JButton("Créer");
        createPanel.add(new JLabel("Nom d'utilisateur :")); createPanel.add(pseudoField);
        createPanel.add(new JLabel("Mot de passe :")); createPanel.add(passwordFieldCreate);
        createPanel.add(new JLabel("")); createPanel.add(createSubmit);

        // Actions de navigation
        connectButton.addActionListener(e -> cardLayout.show(mainPanel, "login"));
        createButton.addActionListener(e -> cardLayout.show(mainPanel, "create"));

        // Connexion existante
        loginSubmit.addActionListener(e -> {
            try {
                int id = Integer.parseInt(idField.getText());
                String password = new String(passwordFieldLogin.getPassword());

                ClientMsg client = new ClientMsg(id, "localhost", 1666);
                client.startSession(password);

                new ChatFrame(client);
                dispose();

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Erreur de connexion : " + ex.getMessage());
            }
        });

        // Création de compte
        createSubmit.addActionListener(e -> {
            try {
                String pseudo = pseudoField.getText();
                String password = new String(passwordFieldCreate.getPassword());

                ClientMsg client = new ClientMsg("localhost", 1666); // ID = 0 pour création
                client.startSession(password, pseudo); // Création de compte

                JOptionPane.showMessageDialog(this, "Compte '" + pseudo + "' créé avec l'ID : " + client.getIdentifier());
                new ChatFrame(client);
                dispose();

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Erreur création de compte : " + ex.getMessage());
            }
        });

        // Ajout des vues dans le panel principal
        mainPanel.add(welcomePanel, "welcome");
        mainPanel.add(loginPanel, "login");
        mainPanel.add(createPanel, "create");

        add(mainPanel);
        cardLayout.show(mainPanel, "welcome");
        setVisible(true);
    }
}
