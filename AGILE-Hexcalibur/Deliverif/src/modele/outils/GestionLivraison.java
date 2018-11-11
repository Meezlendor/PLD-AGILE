/*
 * Projet Deliverif
 *
 * Hexanome n° 41
 *
 * Projet développé dans le cadre du cours "Conception Orientée Objet
 * et développement logiciel AGILE".
 */
package modele.outils;

import java.io.IOException;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import org.xml.sax.SAXException;

/** 
 * Point d'entrée du contrôleur. Permet à l'application d'accéder de manière
 * sécurisée aux objets composant le modèle.
 * @version 1.0 23/10/2018
 * @author Louis Ohl
 */
public class GestionLivraison extends Observable{
    
    private PlanVille plan;
    private Tournee[] tournees;
    private DemandeLivraison demande;
    private TemplateTSP tsp;
    private Thread threadTSP;
    
    
    /**
     * Créer une nouvelle GestionLivraison
     */
    public GestionLivraison(){
        this.tournees=null;
        this.plan=null;
        this.demande=null;
    }
    
    /**
     * Calcule le TSP (et donc tournées) pour les livreurs en un temps donné.
     * @param nbLivreur - Le nombre de liveur (i.e. le nombre de tournée) à prendre en compte.
     * @param tempsLimite - Le temps maximal (en s) alloué au calcul de tournée.
     * @throws Exception 
     */
    public void calculerTournees(int nbLivreur, int tempsLimite) throws Exception{
        if(this.plan==null || this.demande==null || (this.calculTSPEnCours())){
            return;
        }
        System.out.println("Début du calcul de tournée !");
        List<PointPassage> listePoints = new ArrayList<>();
        listePoints.add(this.demande.getEntrepot());
        listePoints.addAll(this.demande.getLivraisons());
        List<Chemin> graphe = this.plan.dijkstraTousPoints(listePoints);//Nous sommes déjà sûrs que l'entrepot est à l'indice 0
        int[][] cout = new int[listePoints.size()][listePoints.size()];
        for(Chemin c : graphe){
            int i = listePoints.indexOf(c.getDebut());
            int j = listePoints.indexOf(c.getFin());
            cout[i][j]=(int)c.getDuree();
        }
        tsp = new TSPMinCFC(nbLivreur);
        ObservateurTSP observateur = new ObservateurTSP(graphe, listePoints, nbLivreur);
        tsp.addObserver(observateur);
        threadTSP = new Thread(new Runnable(){
            @Override
            public void run(){
                tsp.chercheSolution(tempsLimite, listePoints.size(), nbLivreur, cout); //*1000
                if (tsp.getCoutMeilleureSolution()!=Integer.MAX_VALUE){
                    genererTournee(graphe, listePoints, nbLivreur);
                }
            }
        });
        threadTSP.start();
    }
    
    /**
     * Génère les tournées en fonction des chemins d'un point de passage à un autre, connaissant
     * le nombre de livreurs (i.e. nombre de tournées).
     * @param graphe - L'ensemble des chemins d'un point de passage à un autre.
     * @param listePoints - L'ensemble des points de passage (dont entrepot)
     * @param nbLivreur - Le nombre de livreur/tournée.
     */
    private synchronized void genererTournee(List<Chemin> graphe, List<PointPassage>  listePoints, int nbLivreur){
        List<Tournee> listeTournee = new ArrayList<Tournee>(nbLivreur);
        this.tournees = new Tournee[nbLivreur];
        List<Chemin> trajets = new ArrayList<Chemin>();
        
        for(int i=0; i<listePoints.size()+nbLivreur-2;i++){
            //Pour chaque chemin, l'ajouter à la tournée en cours.
            int deb = tsp.getMeilleureSolution(i);
            int fin = tsp.getMeilleureSolution(i+1);
            for(Chemin c : graphe){
                if (c.getDebut()==listePoints.get(deb) && c.getFin()==listePoints.get(fin)){
                    trajets.add(c);
                    break;
                }
            }
            if (fin==0){
                //On revient vers l'entrepot, on change de tournée !
                listeTournee.add(new Tournee(trajets, demande.getHeureDepart()));
                trajets = new ArrayList<Chemin>();
            }
        }
        //Ne pas oublier de fermer la dernière tournée.
        int fin = tsp.getMeilleureSolution(listePoints.size()+nbLivreur-2);
        for(Chemin c : graphe){
            if (c.getDebut()==listePoints.get(fin) && c.getFin()==this.demande.getEntrepot()){
                trajets.add(c);
                break;
            }
        }
        
        listeTournee.add(new Tournee(trajets, demande.getHeureDepart()));
        //Rétablir l'attribut this.tournees[]
        listeTournee.toArray(this.tournees);
        if(this.tournees!=null){
            setChanged();
            this.notifyObservers(tournees);
        }
    }
    
    /**
     * Permet d'ajouter une livraison à la fin d'une tournée donnée.
     * @param intersection - L'intersection sur laquelle a été aimanté la livraison à ajouter
     * @param dureePassage - La durée que l'utilisateur devra passer sur le point de livraison
     * @param numeroTournee - La tournée à laquelle cette livraison doit être ajoutée.
     * @param pointPrecedent - L'indice du point de passage après lequel le novueau point est inséré
     */
    public void ajouterLivraison(Intersection intersection, float dureePassage, int numeroTournee, int pointPrecedent){
        //FIXME : something for the undo/redo stuff.
        if (pointPrecedent>=this.tournees[numeroTournee].getTrajet().size()){
            return;
        }
        PointPassage livraison = new PointPassage(false, intersection, dureePassage);
        this.demande.ajouterLivraison(livraison);
        PointPassage derniereLivraison = this.tournees[numeroTournee].getPointPassage(
            pointPrecedent);
        Map depuisLivraison = this.plan.dijkstra(livraison);
        Map depuisPrecedent = this.plan.dijkstra(derniereLivraison);
        Chemin finVersLivraison = this.plan.reconstruireChemin(derniereLivraison, livraison, depuisPrecedent);
        
        Chemin livraisonVersSuivant = this.plan.reconstruireChemin(livraison, this.tournees[pointPrecedent].getTrajet().get(pointPrecedent).getFin(), depuisLivraison);
        //Modifications sur la référence, rien d'autre à faire.
        List<Chemin> ancienneTournee = this.tournees[numeroTournee].getTrajet();
        List<Chemin> nouvelleTournee = new ArrayList<Chemin>();
        for(int i=0; i<ancienneTournee.size(); i++){
            if (i==pointPrecedent){
                nouvelleTournee.add(finVersLivraison);
                nouvelleTournee.add(livraisonVersSuivant);
            } else {
                nouvelleTournee.add(ancienneTournee.get(i));
            }
        }
        this.tournees[numeroTournee]=new Tournee(nouvelleTournee, this.demande.getHeureDepart());
        setChanged();
        notifyObservers(this.tournees);
    }
    
    /**
     * Permet de supprimer une livraison connue
     * @param livraison - La livraison à supprimer
     */
    public void supprimerLivraison(PointPassage livraison){
        //FIXME : à changer pour l'undo/redo
        if (livraison.estEntrepot()){
            return;//Non monsieur, on ne supprime pas l'entrepôt
        }
        //Récupérer la tournée à laquelle appartient ce point, ainsi que sa place dans la tournée.
        int numero=0;
        for(numero=0; numero<this.tournees.length && !this.tournees[numero].contientPointPassage(livraison); numero++);
        int place;
        for(place=0; place<this.tournees[numero].getTrajet().size() &&
                    this.tournees[numero].getPointPassage(place)!=livraison;place++);
        //Remplacement de : precedent => suppression => suivant, par precedent => suivant
        Map res = this.plan.dijkstra(this.tournees[numero].getPointPassage(place-1));
        PointPassage suivant=null;
        if (place==this.tournees[numero].getTrajet().size()-1){
            suivant=this.demande.getEntrepot();
        } else {
            suivant=this.tournees[numero].getPointPassage(place+1);
        }
        Chemin nouveauChemin = this.plan.reconstruireChemin(this.tournees[numero].getPointPassage(place-1), suivant, res);
        List<Chemin> ancienneTournee = this.tournees[numero].getTrajet();
        List<Chemin> nouvelleTournee = new ArrayList<Chemin>();
        for(Chemin chemin : ancienneTournee){
            if (chemin.getDebut()!=livraison && chemin.getFin()!=livraison){
                //Ignorer les deux chemins : precedent => suppression => suivant
                nouvelleTournee.add(chemin);
            }
            if (chemin.getDebut()==livraison){
                //Remplacer par le raccourci : precedent => suivant
                nouvelleTournee.add(nouveauChemin);
            }
        }
        this.tournees[numero] = new Tournee(nouvelleTournee, this.demande.getHeureDepart());
        
        setChanged();
        notifyObservers(this.tournees);
        
    }
    
    /**
     * Interrompt le calcul (éventuellement) en cours du TSP.
     */
    public void arreterCalculTournee(){
        if (tsp!=null && tsp.calculEnCours()){
            tsp.arreterCalcul();
        }
    }
    
    /**
     * @return true Si un calcul de TSP est déjà en cours, false sinon.
     */
    public boolean calculTSPEnCours(){
        if (tsp!=null){
            return tsp.calculEnCours();
        }
        return false;
    }
    
    public boolean aSolution(){
        if (tsp==null){
            return false;
        }
        return tsp.getCoutMeilleureSolution()!=Integer.MAX_VALUE;
    }
    
    /**
     * Methode de test
     */
    public boolean threadAlive(){
        if (threadTSP!=null){
            if (threadTSP.getState().equals(State.TERMINATED)){
                return false;
            }
        }
        return true;
    }
    /**
     * Connstruit le plan de la ville à l'aide d'un fichier de description
     * xml.
     * @param fichier - L'url du fichier xml décrivant le plan d'une ville.
     */
    public void chargerPlan(String fichier) throws SAXException, IOException, Exception{
        modele.flux.LecteurXML Lecteur = new modele.flux.LecteurXML();
        this.plan = Lecteur.creerPlanVille(fichier);
        setChanged();
        this.notifyObservers(plan); //?

    }
    
    /**
     * Construit la demande de livraison à l'aide d'un fichier de description
     * xml.
     * @param fichier - l'url du fichier xml contenant une demande de livraison.
     */
    public void chargerDemandeLivraison(String fichier) throws SAXException, IOException, Exception{
        modele.flux.LecteurXML Lecteur = new modele.flux.LecteurXML();
        this.demande = Lecteur.creerDemandeLivraison(fichier, this.plan);
        setChanged();
        this.notifyObservers(demande); //?
    }
    
    //Test
    public Intersection identifierPointPassage(String point){
        String[] identifiants = point.split("_");
        
        int numTournee = Integer.parseInt(identifiants[0]);
        int numLivraison = Integer.parseInt(identifiants[1]);
        
        if(numTournee != -1)
            return this.tournees[numTournee-1].getPointPassage(numLivraison-1).getPosition();
        else
            return this.demande.getLivraisons().get(numLivraison-1).getPosition();
    }
    
    /**
     * 
     */
    public Intersection intersectionPlusProche(double latitude, double longitude) {
        
        Intersection resultat = this.plan.getIntersections().get(0);
        double minDistance = Math.sqrt(Math.pow((resultat.getLatitude()-latitude), 2) + Math.pow((resultat.getLongitude()-longitude),2));
        
        for(Intersection i: this.plan.getIntersections()){
            double distance = Math.sqrt(Math.pow((i.getLatitude()-latitude), 2) + Math.pow((i.getLongitude()-longitude),2));
            if(distance <minDistance) {
                resultat = i;
                minDistance = distance;
            }
        }
        
        return resultat;
    }
    
    /**
     *
     * @return - Le plan de la Ville
     */
    public PlanVille getPlan() {
        return plan;
    }

    /**
     *
     * @return - Les tournées calculées précédemment
     */
    public Tournee[] getTournees() {
        return tournees;
    }

    /**
     *
     * @return - La demande de livraison
     */
    public DemandeLivraison getDemande() {
        return demande;
    }
    
    private class ObservateurTSP implements Observer{
        
        private List<Chemin> graphe;
        private List<PointPassage> listePoints;
        private int nbLivreur;
        private long dernierAffichage;
        
        public ObservateurTSP(List<Chemin> graphe, List<PointPassage> listePoints, int nbLivreur){
            this.graphe=graphe;
            this.listePoints=listePoints;
            this.nbLivreur=nbLivreur;
            dernierAffichage=0;
        }
        
        @Override
        public void update(Observable o, Object arg){
            genererTournee(graphe, listePoints, nbLivreur);
        }
    }
}
