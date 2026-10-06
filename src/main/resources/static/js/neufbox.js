// Page 9-Box (templates/9box.html), sans bibliotheque. Sans ce script, tout marche
// par rechargement (?case=N#detail, ?q=, ?tri=). Avec lui :
//  - un clic sur une case ouvre son panneau de detail sur place (les 9 panneaux sont deja dans la page) ;
//  - la recherche filtre et le tri reordonne les lignes du panneau ouvert, sans recharger.
(function () {
    var cases = document.querySelectorAll('.matrice9-case[data-case]');
    var panneaux = document.querySelectorAll('.neufbox-panneau[data-case]');
    if (!cases.length || !panneaux.length) {
        return;
    }

    function ouvrir(numero) {
        cases.forEach(function (c) {
            var choisie = c.getAttribute('data-case') === numero;
            c.classList.toggle('matrice9-selectionnee', choisie);
            if (choisie) {
                c.setAttribute('aria-current', 'true');
            } else {
                c.removeAttribute('aria-current');
            }
        });
        panneaux.forEach(function (p) {
            p.hidden = p.getAttribute('data-case') !== numero;
        });
    }

    cases.forEach(function (c) {
        c.addEventListener('click', function (e) {
            e.preventDefault();
            var numero = c.getAttribute('data-case');
            ouvrir(numero);
            if (window.history && window.history.replaceState) {
                window.history.replaceState(null, '', c.getAttribute('href'));
            }
            var detail = document.getElementById('detail');
            if (detail && window.innerWidth < 992) {
                detail.scrollIntoView({behavior: 'smooth'});
            }
        });
    });

    function sansAccents(texte) {
        return (texte || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    }

    function appliquer(panneau) {
        var recherche = sansAccents(panneau.querySelector('.neufbox-recherche').value.trim());
        var tri = panneau.querySelector('.neufbox-tri').value;
        var corps = panneau.querySelector('tbody');
        if (!corps) {
            return;
        }
        var lignes = Array.prototype.slice.call(corps.querySelectorAll('.neufbox-ligne'));
        lignes.forEach(function (l) {
            l.hidden = recherche !== '' && sansAccents(l.getAttribute('data-recherche')).indexOf(recherche) < 0;
        });
        lignes.sort(function (a, b) {
            if (tri === 'PERFORMANCE' || tri === 'POTENTIEL') {
                var attribut = tri === 'PERFORMANCE' ? 'data-performance' : 'data-potentiel';
                var x = parseFloat(a.getAttribute(attribut)), y = parseFloat(b.getAttribute(attribut));
                x = isNaN(x) ? -1 : x;
                y = isNaN(y) ? -1 : y;
                if (x !== y) {
                    return y - x;
                }
            }
            return sansAccents(a.getAttribute('data-nom')).localeCompare(sansAccents(b.getAttribute('data-nom')));
        });
        lignes.forEach(function (l) {
            corps.appendChild(l);
        });
    }

    panneaux.forEach(function (panneau) {
        var formulaire = panneau.querySelector('form');
        if (!formulaire) {
            return;
        }
        panneau.querySelector('.neufbox-recherche').addEventListener('input', function () {
            appliquer(panneau);
        });
        panneau.querySelector('.neufbox-tri').addEventListener('change', function () {
            appliquer(panneau);
        });
        formulaire.addEventListener('submit', function (e) {
            e.preventDefault();
            appliquer(panneau);
        });
    });
})();
