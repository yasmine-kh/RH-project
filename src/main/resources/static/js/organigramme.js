// Page Organigramme (templates/entites.html), sans bibliotheque.
// Sans JavaScript, toutes les cartes et branches sont ouvertes. Ici : tout est
// replie au chargement (un clic sur une carte montre ses departements) et le champ
// de recherche filtre les entites par nom (lignes trouvees, leurs parents et leurs enfants).
(function () {
    var bloc = document.getElementById('organigramme-recherche-bloc');
    var champ = document.getElementById('organigramme-recherche');
    var aucun = document.getElementById('organigramme-aucun');
    var cartes = Array.prototype.slice.call(document.querySelectorAll('.organigramme-carte'));
    if (!cartes.length) {
        return;
    }

    function normaliser(texte) {
        return (texte || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    }

    function lignes(racine) {
        return Array.prototype.slice.call(racine.querySelectorAll('[data-nom]'));
    }

    function toutReplier() {
        cartes.forEach(function (carte) {
            carte.hidden = false;
            carte.open = false;
            lignes(carte).forEach(function (ligne) {
                ligne.hidden = false;
                if (ligne.tagName === 'DETAILS') {
                    ligne.open = false;
                }
            });
        });
        aucun.hidden = true;
    }

    // Montre la ligne, ses enfants, et ouvre ses parents jusqu'a la carte.
    function montrer(ligne, carte) {
        ligne.hidden = false;
        lignes(ligne).forEach(function (enfant) { enfant.hidden = false; });
        var parent = ligne.parentElement;
        while (parent && parent !== carte) {
            if (parent.tagName === 'DETAILS') {
                parent.hidden = false;
                parent.open = true;
            }
            parent = parent.parentElement;
        }
    }

    function filtrer() {
        var q = normaliser(champ.value.trim());
        if (!q) {
            toutReplier();
            return;
        }
        var trouve = false;
        cartes.forEach(function (carte) {
            var toutes = lignes(carte);
            if (normaliser(carte.getAttribute('data-nom')).indexOf(q) >= 0) {
                // La direction elle-meme : toute la carte, ouverte.
                toutes.forEach(function (ligne) { ligne.hidden = false; });
                carte.hidden = false;
                carte.open = true;
                trouve = true;
                return;
            }
            toutes.forEach(function (ligne) { ligne.hidden = true; });
            var correspondantes = toutes.filter(function (ligne) {
                return normaliser(ligne.getAttribute('data-nom')).indexOf(q) >= 0;
            });
            correspondantes.forEach(function (ligne) { montrer(ligne, carte); });
            carte.hidden = correspondantes.length === 0;
            carte.open = correspondantes.length > 0;
            trouve = trouve || correspondantes.length > 0;
        });
        aucun.hidden = trouve;
    }

    bloc.hidden = false;
    champ.addEventListener('input', filtrer);
    toutReplier();
})();
