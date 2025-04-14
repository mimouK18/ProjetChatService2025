package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import fr.uga.miashs.dciss.chatservice.client.ClientMsg;
import fr.uga.miashs.dciss.chatservice.client.MessageListener;
import fr.uga.miashs.dciss.chatservice.common.Packet;

public class ChatFrame extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private JTextField destField;
    private ClientMsg client;

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

        sendButton.addActionListener(e -> {
            String message = inputField.getText();
            if (!message.isEmpty()) {
                try {
                    int destId = Integer.parseInt(destField.getText());
                    client.sendPacket(destId, message.getBytes());
                    chatArea.append("Moi → " + destId + " : " + message + "\n");
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
			    chatArea.append("De " + p.getSrcId() + " : " + msg + "\n");
			}
        });
        
     // Organisation de l’interface
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
