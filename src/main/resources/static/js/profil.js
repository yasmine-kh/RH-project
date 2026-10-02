// Selecteur de profil de la sidebar (fragments/sidebar.html), sans bibliotheque.
// Choisir un profil l'applique tout de suite (le serveur garde la page courante) ;
// pour Collaborateur et Manager, choisir une personne de la liste ouvre sa page.
// Sans JavaScript, le bouton Appliquer fait la meme chose.
(function () {
    var form = document.getElementById('profil-form');
    if (!form) {
        return;
    }
    var type = document.getElementById('profil-type');
    var cible = document.getElementById('profil-cible');
    var appliquer = document.getElementById('profil-appliquer');
    if (appliquer && !cible) {
        appliquer.style.display = 'none';
    }

    type.addEventListener('change', function () {
        // Changer de profil efface la personne : elle se choisit ensuite dans le nouveau profil.
        if (cible) {
            cible.value = '';
        }
        form.submit();
    });
    if (cible) {
        // Une option choisie dans la liste ouvre directement la page.
        cible.addEventListener('change', function () {
            var liste = document.getElementById(cible.getAttribute('list'));
            var connue = liste && Array.prototype.some.call(liste.options, function (o) {
                return o.value === cible.value;
            });
            if (connue) {
                form.submit();
            }
        });
    }
})();
