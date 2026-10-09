package com.talent360bank.talent360bank.entity;

import jakarta.persistence.EntityNotFoundException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.proxy.HibernateProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifie qu'une entite referencee existe vraiment avant de la rendre.
 *
 * <p>Une colonne id_entite peut pointer vers une ligne absente (0 ecrit par une migration
 * ddl-auto, ligne supprimee a la main) : Hibernate en fait alors un proxy qui leve
 * EntityNotFoundException au premier acces, et la page tombe. Les getters des entites passent par
 * ici et rendent null a la place : l'ecran affiche "Sans direction", le journal le signale.
 *
 * <p>Sans requete de plus : une entite deja chargee (jointure des requetes de lecture, ou deja dans
 * la session) est rendue telle quelle ; un proxy pas encore charge l'est ici, comme il l'aurait ete
 * au premier acces a l'un de ses champs.
 */
final class EntiteExistante {

    private static final Logger log = LoggerFactory.getLogger(EntiteExistante.class);

    private EntiteExistante() {
    }

    /**
     * @param proprietaire description de la reference, pour le journal (ex. "collaborateur BP001")
     * @return l'entite, ou null si elle est absente ou n'existe pas en base
     */
    static Entite ou(Entite entite, String proprietaire) {
        if (!(entite instanceof HibernateProxy proxy)) {
            return entite;
        }
        // Pas Hibernate.isInitialized : apres une jointure (left join fetch) sans ligne en face, Hibernate
        // peut marquer le proxy initialise alors que sa cible n'existe pas. On regarde la cible.
        try {
            Object cible = proxy.getHibernateLazyInitializer().getImplementation();
            // Une vraie ligne a toujours un libelle (NOT NULL) : une cible vide est une cle sans ligne.
            return cible instanceof Entite chargee && chargee.getLibelle() != null ? entite : signaler(proprietaire);
        } catch (EntityNotFoundException | ObjectNotFoundException e) {
            return signaler(proprietaire);
        }
    }

    private static Entite signaler(String proprietaire) {
        log.warn("Reference vers une entite inexistante ({}) : traitee comme sans entite. "
                + "Voir la requete de controle du guide (section Entites manquantes).", proprietaire);
        return null;
    }
}
