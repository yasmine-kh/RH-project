package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.Sexe;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corps de POST /api/collaborateurs (creation, ou mise a jour d'un matricule existant). Valide avant tout
 * enregistrement : une valeur refusee rend 400 avec un message en francais par champ (ApiExceptionHandler),
 * jamais 500. Les contraintes reprennent celles de l'entite Collaborateur.
 *
 * @param entite  entite de rattachement ({"idEntite": 12}), facultative ; doit exister
 * @param manager manager ({"idManager": 3}), facultatif ; doit exister
 * @param statut  ACTIF par defaut
 */
public record CollaborateurRequest(
        @NotBlank(message = "Le matricule est obligatoire")
        @Size(max = 20, message = "Le matricule fait au plus 20 caractères")
        String idCollaborateur,
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom fait au plus 100 caractères")
        String nom,
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom fait au plus 100 caractères")
        String prenom,
        Sexe sexe,
        @Past(message = "La date de naissance doit être dans le passé")
        LocalDate dateNaissance,
        @NotNull(message = "La date d'entrée est obligatoire")
        @PastOrPresent(message = "La date d'entrée ne peut pas être dans le futur")
        LocalDate dateEntree,
        @Size(max = 100, message = "La fonction fait au plus 100 caractères")
        String fonction,
        @Size(max = 50, message = "Le grade fait au plus 50 caractères")
        String grade,
        @Email(message = "L'adresse e-mail n'est pas valide")
        @Size(max = 150, message = "L'adresse e-mail fait au plus 150 caractères")
        String email,
        StatutCollaborateur statut,
        @Valid Reference entite,
        @Valid ReferenceManager manager) {

    /** Reference a une entite existante. */
    public record Reference(@NotNull(message = "L'identifiant de l'entité est obligatoire") Integer idEntite) {
    }

    /** Reference a un manager existant. */
    public record ReferenceManager(@NotNull(message = "L'identifiant du manager est obligatoire") Integer idManager) {
    }
}
