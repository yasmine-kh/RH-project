# Requêtes d'écriture depuis un écran

L'application refuse toute requête **POST, PUT, PATCH ou DELETE** qui ne porte pas l'en-tête `X-Talent360`. C'est la protection contre les requêtes forgées depuis un autre site (CSRF) : un navigateur ne peut pas ajouter cet en-tête à une requête venue d'une autre page sans une vérification que l'application refuse.

Les lectures (GET) ne sont pas concernées : les écrans actuels (`/`, `/9box`, `/viviers`, `/comite-talent`) ne changent pas.

Le nom de l'en-tête est défini à un seul endroit : `ProtectionRequetesFilter.EN_TETE_ECRITURE` (package `config`). La valeur n'a pas d'importance tant qu'elle n'est pas vide ; on envoie `1`.

## Depuis un écran (JavaScript)

Un formulaire HTML classique (`<form method="post">`) ne peut pas ajouter d'en-tête : une écriture passe donc par `fetch`.

Exemple pour l'écran Paramètres, qui enregistre les réglages d'un trimestre :

```js
const EN_TETE_ECRITURE = 'X-Talent360';

async function enregistrerReglages(annee, numero, reglages) {
  const reponse = await fetch(`/api/trimestres/${annee}/${numero}/parametre`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      [EN_TETE_ECRITURE]: '1'
    },
    body: JSON.stringify(reglages)
  });

  if (!reponse.ok) {
    // Corps d'erreur commun à toute l'API : { statut, erreur, message, details }
    const erreur = await reponse.json();
    throw new Error(erreur.message + (erreur.details ? ' : ' + erreur.details.join(', ') : ''));
  }
  return reponse.json();
}
```

La réponse de ce `PUT` contient les réglages enregistrés et, sous `recalcul`, le bilan du recalcul du trimestre lancé automatiquement (`recalcule`, `nbCollaborateursScores`, `nbPlaces9Box`, `dureeMs`, `erreur`). Si `recalcul.recalcule` vaut `false`, les réglages sont bien enregistrés mais le recalcul a échoué : afficher `recalcul.erreur`. Un `409` (`recalcul_en_cours`) signale qu'un recalcul du même trimestre tourne déjà (double clic) : rien n'est enregistré, réessayer quand il est fini. Voir la section « Changing the settings » de [`guide-developpeur.md`](guide-developpeur.md).

Une écriture sans corps (recalcul, placement 9-Box, enregistrement du vivier de relève) porte aussi l'en-tête :

```js
await fetch(`/api/trimestres/${annee}/${numero}/scores/recalcul`, {
  method: 'POST',
  headers: { [EN_TETE_ECRITURE]: '1' }
});
```

Pour éviter d'écrire le nom en dur dans chaque page, le template peut le recevoir du contrôleur : `model.addAttribute("enTeteEcriture", ProtectionRequetesFilter.EN_TETE_ECRITURE)`, puis `const EN_TETE_ECRITURE = /*[[${enTeteEcriture}]]*/ 'X-Talent360';` dans un `<script th:inline="javascript">`.

## Réponse en cas d'oubli

```
403 Forbidden
{ "statut": 403, "erreur": "en_tete_manquant",
  "message": "Les requetes d'ecriture doivent porter l'en-tete X-Talent360" }
```

## Depuis un outil (curl, Postman)

```
curl -X POST -H "X-Talent360: 1" http://localhost:8080/api/trimestres/2026/3/scores/recalcul
```

## Adresse du serveur

L'application n'écoute que sur la machine locale (`server.address=127.0.0.1`) et refuse toute requête dont l'en-tête `Host` n'est pas `localhost` ou `127.0.0.1`, lectures comprises (protection contre le « DNS rebinding »). On l'ouvre donc par `http://localhost:8080` ou `http://127.0.0.1:8080`. La liste des hôtes se règle par la propriété `talent360.securite.hotes-autorises` si un nom de poste doit être ajouté.
