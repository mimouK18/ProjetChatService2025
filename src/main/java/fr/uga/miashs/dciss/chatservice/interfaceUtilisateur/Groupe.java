package fr.uga.miashs.dciss.chatservice.interfaceUtilisateur;

import java.util.List;
import java.util.ArrayList;

public class Groupe {
    private int id;
    private String nom;
    private List<Integer> membres;
    private int createurId;

    public Groupe(int id, String nom, int createurId) {
        this.id = id;
        this.nom = nom;
        this.createurId = createurId;
        this.membres = new ArrayList<>();
        this.membres.add(createurId); // Le créateur est membre automatiquement
    }

    public void ajouterMembre(int utilisateurId) {
        if (!membres.contains(utilisateurId)) {
            membres.add(utilisateurId);
        }
    }

    public void supprimerMembre(int utilisateurId) {
        membres.remove((Integer) utilisateurId);
    }

    public boolean contient(int utilisateurId) {
        return membres.contains(utilisateurId);
    }

    public List<Integer> getMembres() {
        return membres;
    }

    public String getNom() {
        return nom;
    }

    public int getId() {
        return id;
    }

    public int getCreateurId() {
        return createurId;
    }

    @Override
    public String toString() {
        return nom + " (ID: " + id + ", membres: " + membres.size() + ")";
    }
}
