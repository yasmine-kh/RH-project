// Tableau de bord RH interactif (templates/dashboard.html), sans autre bibliotheque que Chart.js (local).
// Les donnees du trimestre sont embarquees une fois (#tdb[data-donnees], JSON echappe par le serveur) ;
// chaque clic filtre sur place cartes, graphiques et listes, sans requete au serveur.
// Regles de filtrage : les memes que TableauDeBordInteractifService (a garder identiques) :
//  - une personne passe si elle repond a chaque filtre ; "alerte" : au moins une alerte de ce type ;
//  - une alerte sur une personne passe si la personne passe (hors filtre "alerte") et si son type convient ;
//    une alerte sur un poste ne passe que sans filtre propre aux personnes, si sa direction est celle du
//    filtre "entite" et si son type convient ;
//  - chaque graphique compte avec tous les filtres sauf le sien (choix en avant, autres estompes).
// Les filtres restent dans l'URL (?case=9&entite=...) : la page s'ouvre deja filtree et se recharge a l'identique.
(function () {
    'use strict';
    var racine = document.getElementById('tdb');
    if (!racine) {
        return;
    }
    var D = JSON.parse(racine.getAttribute('data-donnees'));
    var PARAMS = ['case', 'entite', 'vigilance', 'perf', 'pot', 'vivier', 'alerte'];
    var PROPRES_PERSONNES = ['case', 'vigilance', 'perf', 'pot', 'vivier'];
    var NOMS = {case: '9-Box', entite: 'Entité', vigilance: 'Vigilance', perf: 'Performance', pot: 'Potentiel',
        vivier: 'Vivier', alerte: 'Alerte'};

    // --- donnees ----------------------------------------------------------------------------------------
    var libelles = {};
    PARAMS.forEach(function (dim) {
        libelles[dim] = {};
        D.dimensions[dim].forEach(function (e) { libelles[dim][String(e.code)] = e.libelle; });
    });
    var personnes = {};
    D.personnes.forEach(function (p) { personnes[p.matricule] = p; });
    var typesParPersonne = {};
    D.alertes.forEach(function (a) {
        if (a.personne) {
            (typesParPersonne[a.matricule] = typesParPersonne[a.matricule] || {})[a.type] = true;
        }
    });

    // --- filtres ------------------------------------------------------------------------------------------
    function lireUrl() {
        var url = new URLSearchParams(window.location.search);
        var f = {};
        PARAMS.forEach(function (dim) {
            var v = url.get(dim);
            f[dim] = v !== null && libelles[dim].hasOwnProperty(v.trim()) ? v.trim() : null;
        });
        return f;
    }

    var f = lireUrl();

    function url(filtres) {
        var u = new URLSearchParams();
        u.set('trimestre', D.trimestre);
        PARAMS.forEach(function (dim) {
            if (filtres[dim] !== null) {
                u.set(dim, filtres[dim]);
            }
        });
        return '/?' + u.toString();
    }

    function valeurPersonne(p, dim) {
        switch (dim) {
            case 'case': return p.caseNumero === null ? null : String(p.caseNumero);
            case 'entite': return p.directionCode;
            case 'vigilance': return p.vigilance;
            case 'perf': return p.categoriePerformance;
            case 'pot': return p.categoriePotentiel;
            default: return null;
        }
    }

    function passePersonne(p, ignore) {
        for (var i = 0; i < PARAMS.length; i++) {
            var dim = PARAMS[i];
            if (f[dim] === null || dim === ignore) {
                continue;
            }
            if (dim === 'vivier') {
                if (p.viviers.indexOf(f.vivier) < 0) { return false; }
            } else if (dim === 'alerte') {
                if (!(typesParPersonne[p.matricule] || {})[f.alerte]) { return false; }
            } else if (valeurPersonne(p, dim) !== f[dim]) {
                return false;
            }
        }
        return true;
    }

    function passeAlerte(a, ignore) {
        if (f.alerte !== null && ignore !== 'alerte' && f.alerte !== a.type) {
            return false;
        }
        if (a.personne) {
            return passePersonne(personnes[a.matricule], ignore === null ? 'alerte' : ignore);
        }
        for (var i = 0; i < PROPRES_PERSONNES.length; i++) {
            var dim = PROPRES_PERSONNES[i];
            if (f[dim] !== null && dim !== ignore) {
                return false;
            }
        }
        return f.entite === null || ignore === 'entite' || libelles.entite[f.entite] === a.direction;
    }

    function basculer(dim, code) {
        f[dim] = f[dim] === code ? null : code;
        appliquer(true);
    }

    // --- rendu : outils -------------------------------------------------------------------------------------
    function el(nom, attributs, enfants) {
        var e = document.createElement(nom);
        Object.keys(attributs || {}).forEach(function (k) {
            if (k === 'texte') { e.textContent = attributs[k]; } else { e.setAttribute(k, attributs[k]); }
        });
        (enfants || []).forEach(function (c) { e.appendChild(typeof c === 'string' ? document.createTextNode(c) : c); });
        return e;
    }

    function vider(e) {
        while (e.firstChild) { e.removeChild(e.firstChild); }
    }

    function score(v) {
        return v === null || v === undefined ? '—' : Number(v).toFixed(2);
    }

    /** Texte a afficher : "—" pour une valeur absente (le serveur l'a deja fait, par prudence). */
    function texte(v) {
        return v === null || v === undefined || v === '' ? '—' : String(v);
    }

    /** Arrondi au plus proche, demi vers le haut, comme RoundingMode.HALF_UP (valeurs positives). */
    function arrondi(numerateur, denominateur, decimales) {
        var facteur = Math.pow(10, decimales);
        return (Math.floor(numerateur * facteur / denominateur + 0.5 + 1e-9) / facteur).toFixed(decimales);
    }

    // --- cartes -----------------------------------------------------------------------------------------------
    // Les 10 cartes du prototype : seules celles sur les personnes (data-filtre="true") suivent les filtres ;
    // Postes critiques, Couverture, Ready Now, Postes sans releve et Gaps critiques gardent la valeur du moteur.
    function kpis(p) {
        function compter(test) { return String(p.filter(test).length); }
        var engagements = p.filter(function (x) { return x.engagement !== null && x.engagement !== undefined; });
        var centimes = engagements.reduce(function (s, x) { return s + Math.round(Number(x.engagement) * 100); }, 0);
        var moyenne = engagements.length ? arrondi(centimes, engagements.length * 100, 2) : null;
        var viviers = {};
        p.forEach(function (x) { x.viviers.forEach(function (v) { if (v !== 'RELEVE') { viviers[v] = true; } }); });
        return {
            population: [String(p.length)],
            talentsValides: [compter(function (x) { return x.estTalentValide; })],
            hautsPotentiels: [compter(function (x) { return x.estHautPotentiel; })],
            viviersActifs: [String(Object.keys(viviers).length)],
            engagement: [moyenne === null ? '—' : moyenne,
                '❤️ Engagement (' + engagements.length + ' réponse' + (engagements.length > 1 ? 's' : '') + ')',
                // Couleur du prototype : >= 75 teal, >= 60 gold, sinon rust.
                moyenne === null ? 'gold' : (Number(moyenne) >= 75 ? 'teal' : (Number(moyenne) >= 60 ? 'gold' : 'rust'))]
        };
    }

    function rendreKpis(p) {
        var valeurs = kpis(p);
        document.querySelectorAll('#kpis .kpi[data-filtre="true"]').forEach(function (carte) {
            var v = valeurs[carte.getAttribute('data-kpi')];
            if (!v) { return; }
            carte.querySelector('.val').textContent = v[0];
            if (v[1]) { carte.querySelector('.lbl').textContent = v[1]; }
            if (v[2]) {
                carte.classList.remove('teal', 'gold', 'rust');
                carte.classList.add(v[2]);
            }
        });
    }

    // --- puces -----------------------------------------------------------------------------------------------
    function rendrePuces() {
        var conteneur = document.getElementById('tdb-puces');
        vider(conteneur);
        var actifs = 0;
        PARAMS.forEach(function (dim) {
            if (f[dim] === null) { return; }
            actifs++;
            var copie = {};
            PARAMS.forEach(function (d) { copie[d] = d === dim ? null : f[d]; });
            var puce = el('a', {'class': 'puce', href: url(copie), 'data-parametre': dim, title: 'Retirer ce filtre'}, [
                el('span', {texte: NOMS[dim] + ' : ' + (libelles[dim][f[dim]] || f[dim])}),
                el('span', {'class': 'puce-x', 'aria-hidden': 'true', texte: '✕'})]);
            puce.addEventListener('click', function (e) {
                e.preventDefault();
                f[dim] = null;
                appliquer(true);
            });
            conteneur.appendChild(puce);
        });
        document.getElementById('tdb-aucun-filtre').hidden = actifs > 0;
        document.getElementById('tdb-reinitialiser').hidden = actifs === 0;
    }

    // --- matrice 9-Box ----------------------------------------------------------------------------------------
    function rendreNeufBox() {
        document.querySelectorAll('#tdb-neufbox .box9-cell[data-case]').forEach(function (cellule) {
            var numero = cellule.getAttribute('data-case');
            var nombre = D.personnes.filter(function (p) {
                return String(p.caseNumero) === numero && passePersonne(p, 'case');
            }).length;
            cellule.querySelector('.cnt').textContent = String(nombre);
            var choisie = f['case'] === numero;
            cellule.classList.toggle('selectionnee', choisie);
            cellule.classList.toggle('estompee', f['case'] !== null && !choisie);
            var copie = Object.assign({}, f);
            copie['case'] = choisie ? null : numero;
            cellule.setAttribute('href', url(copie));
        });
    }

    document.querySelectorAll('#tdb-neufbox .box9-cell[data-case]').forEach(function (cellule) {
        cellule.addEventListener('click', function (e) {
            e.preventDefault();
            basculer('case', cellule.getAttribute('data-case'));
        });
    });

    // --- graphiques (Chart.js) ---------------------------------------------------------------------------------
    var css = getComputedStyle(document.documentElement);
    function couleur(nom, defaut) { return (css.getPropertyValue(nom) || '').trim() || defaut; }
    var C = {navy: couleur('--navy', '#101B33'), gold: couleur('--gold', '#B08D4F'), teal: couleur('--teal', '#2F6F62'),
        rust: couleur('--rust', '#B0503A'), amber: couleur('--amber', '#B98A2E'), blue: couleur('--blue', '#3B5C8A'),
        muted: couleur('--muted', '#5C6270'), line: couleur('--line', '#D8D9D3')};

    function transparent(hex, alpha) {
        var h = hex.replace('#', '');
        return 'rgba(' + parseInt(h.substr(0, 2), 16) + ',' + parseInt(h.substr(2, 2), 16) + ','
            + parseInt(h.substr(4, 2), 16) + ',' + alpha + ')';
    }

    // Chaque graphique : sa dimension, ses codes (ordre d'affichage) et leurs couleurs.
    var presents = function (dim, liste, valeur) {
        var vus = {};
        liste.forEach(function (x) { [].concat(valeur(x)).forEach(function (v) { if (v !== null) { vus[v] = true; } }); });
        return D.dimensions[dim].map(function (e) { return String(e.code); }).filter(function (c) { return vus[c]; });
    };
    var GRAPHES = [
        {dim: 'entite', id: 'graphe-entite', type: 'bar', horizontal: true, couleur: function () { return C.blue; },
            codes: presents('entite', D.personnes, function (p) { return p.directionCode; })},
        {dim: 'vigilance', id: 'graphe-vigilance', type: 'doughnut', codes: ['FAIBLE', 'MODEREE', 'ELEVEE'],
            couleur: function (c) { return {FAIBLE: C.teal, MODEREE: C.amber, ELEVEE: C.rust}[c]; }},
        {dim: 'perf', id: 'graphe-perf', type: 'bar', couleur: function () { return C.navy; },
            codes: D.dimensions.perf.map(function (e) { return e.code; })},
        {dim: 'pot', id: 'graphe-pot', type: 'bar', couleur: function () { return C.teal; },
            codes: D.dimensions.pot.map(function (e) { return e.code; })},
        {dim: 'vivier', id: 'graphe-vivier', type: 'bar', couleur: function (c) { return c === 'RELEVE' ? C.gold : C.blue; },
            codes: D.dimensions.vivier.map(function (e) { return e.code; })},
        {dim: 'alerte', id: 'graphe-alerte', type: 'bar', horizontal: true, alertes: true,
            couleur: function () { return C.rust; },
            codes: presents('alerte', D.alertes, function (a) { return a.type; })}
    ];

    function comptes(g) {
        var n = {};
        g.codes.forEach(function (c) { n[c] = 0; });
        if (g.alertes) {
            D.alertes.forEach(function (a) {
                if (n.hasOwnProperty(a.type) && passeAlerte(a, 'alerte')) { n[a.type]++; }
            });
        } else {
            D.personnes.forEach(function (p) {
                if (!passePersonne(p, g.dim)) { return; }
                var valeurs = g.dim === 'vivier' ? p.viviers : [valeurPersonne(p, g.dim)];
                valeurs.forEach(function (v) { if (n.hasOwnProperty(v)) { n[v]++; } });
            });
        }
        return g.codes.map(function (c) { return n[c]; });
    }

    function couleurs(g) {
        return g.codes.map(function (c) {
            var base = g.couleur(c);
            return f[g.dim] === null || f[g.dim] === c ? base : transparent(base, 0.22);
        });
    }

    if (window.Chart) {
        Chart.defaults.font.family = "'IBM Plex Sans', sans-serif";
        Chart.defaults.font.size = 11;
        Chart.defaults.color = C.muted;
        GRAPHES.forEach(function (g) {
            var canvas = document.getElementById(g.id);
            if (!canvas) { return; }
            var donut = g.type === 'doughnut';
            g.chart = new Chart(canvas, {
                type: g.type,
                data: {
                    labels: g.codes.map(function (c) { return libelles[g.dim][c] || c; }),
                    datasets: [{data: comptes(g), backgroundColor: couleurs(g), borderWidth: donut ? 2 : 0,
                        borderColor: '#fff', maxBarThickness: 26, borderRadius: donut ? 0 : 2}]
                },
                options: {
                    indexAxis: g.horizontal ? 'y' : 'x',
                    responsive: true, maintainAspectRatio: false, animation: {duration: 250},
                    cutout: donut ? '58%' : undefined,
                    plugins: {
                        legend: {display: donut, position: 'right', labels: {boxWidth: 10, boxHeight: 10}},
                        tooltip: {callbacks: {label: function (ctx) {
                            return ' ' + ctx.raw + (g.alertes ? ' alerte(s)' : ' collaborateur(s)');
                        }}}
                    },
                    scales: donut ? {} : {
                        x: {grid: {display: !!g.horizontal, color: C.line}, ticks: {precision: 0}, beginAtZero: true},
                        y: {grid: {display: !g.horizontal, color: C.line}, ticks: {precision: 0}, beginAtZero: true}
                    },
                    onHover: function (e, elements) {
                        e.native.target.style.cursor = elements.length ? 'pointer' : 'default';
                    },
                    onClick: function (e, elements) {
                        if (elements.length) {
                            basculer(g.dim, g.codes[elements[0].index]);
                        }
                    }
                }
            });
        });
    }

    function rendreGraphes() {
        GRAPHES.forEach(function (g) {
            if (!g.chart) { return; }
            g.chart.data.datasets[0].data = comptes(g);
            g.chart.data.datasets[0].backgroundColor = couleurs(g);
            g.chart.update();
        });
    }

    // --- listes ---------------------------------------------------------------------------------------------------
    function rendreCollaborateurs(p) {
        var corps = document.getElementById('tdb-collaborateurs');
        vider(corps);
        p.forEach(function (x) {
            corps.appendChild(el('tr', {}, [
                el('td', {'class': 'mono', texte: x.matricule}),
                el('td', {}, [el('a', {href: x.lien, texte: texte(x.nomComplet)})]),
                el('td', {texte: texte(x.direction)}),
                el('td', {texte: texte(x.caseEtiquette)}),
                el('td', {texte: score(x.performance)}),
                el('td', {texte: score(x.potentiel)}),
                el('td', {texte: texte(x.vigilanceLibelle)}),
                el('td', {texte: texte(x.viviersTexte)})
            ]));
        });
        document.getElementById('tdb-nb-collaborateurs').textContent = '(' + p.length + ')';
        corps.closest('table').hidden = p.length === 0;
        document.getElementById('tdb-collaborateurs-vide').hidden = p.length > 0;
    }

    function rendreAlertes(alertes) {
        var conteneur = document.getElementById('tdb-alertes');
        vider(conteneur);
        alertes.forEach(function (a) {
            conteneur.appendChild(el('div', {'class': 'alert ' + a.classe}, [
                el('div', {'class': 'sev', texte: a.sev}),
                el('div', {'class': 'txt'}, [el('span', {texte: texte(a.typeLibelle) + ' — '}), el('b', {texte: texte(a.sujet)}),
                    el('span', {texte: ' : ' + texte(a.message) + ' '}), el('a', {href: a.lien || '#', texte: texte(a.lienLibelle)})])
            ]));
        });
        if (!alertes.length) {
            conteneur.appendChild(el('div', {'class': 'empty-state', texte: 'Aucune alerte pour ces filtres.'}));
        }
        document.getElementById('tdb-nb-alertes').textContent = '(' + alertes.length + ')';
    }

    function rendreViviers(p) {
        document.querySelectorAll('#tdb-viviers .tdb-vivier[data-vivier]').forEach(function (carte) {
            var code = carte.getAttribute('data-vivier');
            var membres = p.filter(function (x) { return x.viviers.indexOf(code) >= 0; });
            carte.querySelector('.tdb-vivier-tete .mono').textContent = String(membres.length);
            var liste = carte.querySelector('.tdb-vivier-membres');
            vider(liste);
            membres.forEach(function (m, i) {
                liste.appendChild(el('a', {href: m.lien, texte: texte(m.nomComplet)}));
                if (i < membres.length - 1) { liste.appendChild(document.createTextNode(', ')); }
            });
            if (!membres.length) { liste.appendChild(el('span', {'class': 'hint', texte: 'Aucun membre.'})); }
            carte.classList.toggle('estompee', f.vivier !== null && f.vivier !== code);
        });
    }

    // --- tout -------------------------------------------------------------------------------------------------------
    function appliquer(historique) {
        var p = D.personnes.filter(function (x) { return passePersonne(x, null); });
        var alertes = D.alertes.filter(function (a) { return passeAlerte(a, null); });
        rendreKpis(p);
        rendrePuces();
        rendreNeufBox();
        rendreGraphes();
        rendreCollaborateurs(p);
        rendreAlertes(alertes);
        rendreViviers(p);
        if (historique && window.history && window.history.pushState) {
            window.history.pushState(null, '', url(f));
        }
    }

    document.getElementById('tdb-reinitialiser').addEventListener('click', function (e) {
        e.preventDefault();
        PARAMS.forEach(function (dim) { f[dim] = null; });
        appliquer(true);
    });
    window.addEventListener('popstate', function () {
        f = lireUrl();
        appliquer(false);
    });

    appliquer(false);
})();
