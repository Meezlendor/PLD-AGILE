/*
 * Projet Deliverif
 *
 * Hexanome n° 4102
 *
 * Projet développé dans le cadre du cours "Conception Orientée Objet
 * et développement logiciel AGILE".
 */
package controleur.commandes;

import modele.GestionLivraison;
import modele.Intersection;
import modele.PointPassage;

/**
 * Commande permettant d'ajouter une livraison à une tournée dans la GestionLivraison.
 * @author Hex'calibur
 */
public class CdeAjoutLivraison extends Commande{
    
    //La tournée où est insérée la livraison
    private int numeroTournee;
    //L'indice après lequel la livraison doit s'insérer
    private int positionTournee;
    //La livraison à insérer
    private PointPassage livraison;
    
    /**
     * Crée une nouvelle commande d'ajout de livraison dans la GestionLivraison.
     * @param gestion - La GestionLivraison
     * @param intersection - L'intersection où l'on doit réaliser une livraison
     * @param numeroTournee - Le numéro de la tournée à laquelle ce point de passage doit être ajouté
     * @param positionTournee - L'indice auquel doit être ajouté ce point de passage. /!\  : positionTournee!= indiceEntrepot (0 et size()-1)
     * @param duree - La durée que le livreur devra passer sur ce point de livraison.
     */
    public CdeAjoutLivraison(GestionLivraison gestion, Intersection intersection,
            int numeroTournee, int positionTournee, float duree){
        super(gestion);
        this.livraison = new PointPassage(false, intersection, duree);
        this.numeroTournee=numeroTournee;
        this.positionTournee=positionTournee;
    }
    
    @Override
    public void doCde(){
        gestion.ajouterLivraison(this.livraison, numeroTournee, positionTournee);
    }
    
    @Override
    public void undoCde(){
        gestion.supprimerLivraison(livraison);
    }
    
}
