// Selecteur de profil de la sidebar (fragments/sidebar.html), sans bibliotheque.
// RH et Comite ouvrent leur page des le choix ; Collaborateur, Manager et Entite
// montrent le champ de recherche avec la bonne liste, puis ouvrent le choix.
(function () {
    var form = document.getElementById('profil-form');
    if (!form) {
        return;
    }
    var type = document.getElementById('profil-type');
    var bloc = document.getElementById('profil-cible-bloc');
    var cible = document.getElementById('profil-cible');
    var listes = {COLLABORATEUR: 'profils-collaborateurs', MANAGER: 'profils-managers', ENTITE: 'profils-entites'};

    function afficher() {
        var liste = listes[type.value];
        bloc.style.display = liste ? '' : 'none';
        if (liste) {
            cible.setAttribute('list', liste);
        }
    }

    type.addEventListener('change', function () {
        cible.value = '';
        afficher();
        if (!listes[type.value]) {
            form.submit();
        } else {
            cible.focus();
        }
    });
    // Une option choisie dans la liste ouvre directement la page.
    cible.addEventListener('change', function () {
        var liste = document.getElementById(cible.getAttribute('list'));
        var connue = Array.prototype.some.call(liste.options, function (o) { return o.value === cible.value; });
        if (connue) {
            form.submit();
        }
    });
    afficher();
})();
