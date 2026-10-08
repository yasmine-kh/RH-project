/* =========================================================================
   Comportements du prototype (docs/prototype/index.html), copies tels quels :
   openModal / closeModal, fermeture au clic sur le fond, changement de profil,
   cloche, clic sur une case de la 9-Box. Seule la source des donnees change :
   le prototype lit ses tableaux EMPLOYEES / computeKPI() ; ici, aucune donnee
   n'est calculee dans le navigateur, tout vient du moteur (page rendue par le
   serveur, ou API existantes en lecture seule).
   ========================================================================= */

/* ---------- Modale (prototype : openModal / closeModal) ---------- */
function openModal(title, html){ document.getElementById('modalTitle').textContent=title; document.getElementById('modalBody').innerHTML=html; document.getElementById('modal-backdrop').classList.add('show'); }
function closeModal(){ document.getElementById('modal-backdrop').classList.remove('show'); }
(function(){
  const fond = document.getElementById('modal-backdrop');
  if(fond) fond.addEventListener('click', e=>{ if(e.target.id==='modal-backdrop') closeModal(); });
  document.addEventListener('keydown', e=>{ if(e.key==='Escape') closeModal(); });
})();

/* Scores du moteur : 2 decimales (le JSON perd les zeros de fin, ex. 92.10 -> 92.1). */
function score(v){ return v==null ? '—' : Number(v).toFixed(2); }

/* Echappement HTML : les noms viennent des donnees importees. */
function esc(v){ return String(v==null?'':v).replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

/* ---------- Profil (prototype : buildRoleSelect, sel.addEventListener('change', ...)) ----------
   Le prototype reconstruit le menu sur place ; ici le serveur garde le profil en session (/profil).
   Changer de personne (Manager / Collaborateur) recharge la page courante pour la nouvelle personne :
   /managers/X, ?matricule=X et ?q=X passent a la nouvelle personne ; les autres pages se rechargent
   telles quelles (leur menu suit la personne). Premier choix : la page de la personne. */
function avecPersonne(url, ancien, nouveau){
  const u = new URL(url, location.origin);
  if(u.pathname === '/managers/' + ancien) u.pathname = '/managers/' + nouveau;
  ['matricule', 'q'].forEach(k=>{ if(u.searchParams.get(k) === ancien) u.searchParams.set(k, nouveau); });
  return u.pathname + u.search + u.hash;
}
(function(){
  const form = document.getElementById('profil-form');
  const sel = document.getElementById('role-select');
  if(!form || !sel) return;
  const cible = document.getElementById('profil-cible');
  sel.addEventListener('change', ()=>{ if(cible) cible.value=''; form.submit(); });
  if(cible){
    cible.addEventListener('change', async ()=>{
      const ancien = cible.dataset.actuel, nouveau = cible.value;
      if(!nouveau) return;
      if(!ancien || !window.fetch){ form.submit(); return; }
      try {
        await fetch(form.action + '?' + new URLSearchParams(new FormData(form)), {credentials:'same-origin', redirect:'manual'});
        location.href = avecPersonne(location.href, ancien, nouveau);
      } catch(e){ form.submit(); }
    });
  }
})();

/* ---------- Cloche (prototype : updateBell) ----------
   Nombre d'alertes du trimestre, lu sur l'API existante /api/notifications/badge. */
(function(){
  const count = document.getElementById('bellCount');
  if(!count || !window.fetch) return;
  fetch('/api/notifications/badge', {credentials:'same-origin', headers:{'Accept':'application/json'}})
    .then(r=> r.ok ? r.json() : null)
    .then(d=>{
      if(!d || typeof d.total!=='number') return;
      count.textContent = d.total; count.hidden = false;
      document.getElementById('bell').title = `${d.total} alerte(s) — ${d.critiques} critique(s), ${d.elevees} élevée(s), ${d.moyennes} moyenne(s)`;
    })
    .catch(()=>{});
})();

/* ---------- 9-Box (prototype : renderBox9Grid, clic sur .box9-cell) ----------
   La grille est rendue par le serveur avec les effectifs du moteur. Un clic ouvre la modale du
   prototype ; la liste des collaborateurs de la case vient de l'API existante
   /api/trimestres/{annee}/{numero}/collaborateurs?case=N (lecture seule, toutes les pages). */
async function collaborateursDeLaCase(trimestre, numero, filtre){ return collaborateurs(trimestre, `case=${encodeURIComponent(numero)}` + (filtre ? '&' + filtre : '')); }
/* Toutes les pages de /api/trimestres/{a}/{n}/collaborateurs pour un filtre (lecture seule). */
async function collaborateurs(trimestre, filtre){
  const [annee, n] = trimestre.split('-');
  let page = 1, lignes = [], nbPages = 1;
  do {
    const r = await fetch(`/api/trimestres/${encodeURIComponent(annee)}/${encodeURIComponent(n)}/collaborateurs?${filtre}&taille=100&page=${page}`,
      {credentials:'same-origin', headers:{'Accept':'application/json'}});
    if(!r.ok) throw new Error(r.status);
    const d = await r.json();
    lignes = lignes.concat(d.lignes); nbPages = d.nbPages; page++;
  } while(page <= nbPages);
  return lignes;
}
document.querySelectorAll('.box9-grid[data-trimestre]').forEach(grille=>{
  const trimestre = grille.dataset.trimestre;
  grille.querySelectorAll('.box9-cell[data-case]').forEach(cell=>{
    cell.addEventListener('click', async e=>{
      e.preventDefault();
      const titre = cell.dataset.titre;
      let list;
      try { list = await collaborateursDeLaCase(trimestre, cell.dataset.case, grille.dataset.filtre); }
      catch(err){ window.location.href = cell.getAttribute('href'); return; }
      openModal(`${titre} — ${list.length} collaborateur(s)`,
        list.length ? `<table><thead><tr><th>Matricule</th><th>Nom</th><th>Direction</th><th>Poste</th><th>Perf.</th><th>Potentiel</th></tr></thead><tbody>${
          list.map(e=>`<tr data-href="${esc('/fiche-collaborateur?matricule='+encodeURIComponent(e.matricule)+'&trimestre='+encodeURIComponent(trimestre))}"><td class="mono">${esc(e.matricule)}</td><td>${esc(e.nomComplet)}</td><td>${esc(e.direction||'—')}</td><td>${esc(e.poste||'—')}</td><td>${esc(score(e.scorePerformance))}</td><td>${esc(score(e.scorePotentiel))}</td></tr>`).join('')}</tbody></table>`
        : `<div class="empty-state">Aucun collaborateur dans cette case.</div>`);
      document.querySelectorAll('#modalBody tr[data-href]').forEach(tr=> tr.addEventListener('click', ()=> location.href = tr.dataset.href));
    });
  });
});

/* ---------- Filtres : le trimestre s'applique des qu'il est choisi ---------- */
document.querySelectorAll('form.filters select[data-auto]').forEach(s=> s.addEventListener('change', ()=> s.form.submit()));

/* ---------- Chiffres de la synthese du moteur (API existante /api/dashboard/synthese) ----------
   Un bloc [data-synthese-trimestre="AAAA-N"], cache tant que les chiffres ne sont pas la : chaque
   element [data-synthese="champ"] recoit la valeur du champ, [data-synthese-largeur="champ"] la
   prend comme largeur en %. Aucune valeur n'est calculee ici. */
(function(){
  const blocs = document.querySelectorAll('[data-synthese-trimestre]');
  if(!blocs.length || !window.fetch) return;
  const [annee, numero] = blocs[0].dataset.syntheseTrimestre.split('-');
  fetch(`/api/dashboard/synthese?annee=${encodeURIComponent(annee)}&numero=${encodeURIComponent(numero)}`,
        {credentials:'same-origin', headers:{'Accept':'application/json'}})
    .then(r=> r.ok ? r.json() : null)
    .then(d=>{
      if(!d) return;
      blocs.forEach(bloc=>{
        bloc.querySelectorAll('[data-synthese]').forEach(el=>{ const v = d[el.dataset.synthese]; el.textContent = v==null ? '—' : v; });
        bloc.querySelectorAll('[data-synthese-largeur]').forEach(el=>{ const v = d[el.dataset.syntheseLargeur]; if(v!=null) el.style.width = v + '%'; });
        bloc.hidden = false;
      });
    })
    .catch(()=>{});
})();

/* ---------- Lignes cliquables (prototype : <tr onclick="openPassport(...)">) ---------- */
document.querySelectorAll('tbody tr[data-href], .succ-row[data-href]').forEach(tr=> tr.addEventListener('click', e=>{
  if(e.target.closest('a, button, input, select')) return;
  location.href = tr.dataset.href;
}));

/* ---------- Seuils appliques (prototype : renderBox9View, panneau "Seuils appliques") ----------
   Les seuils du trimestre, lus sur l'API existante /api/trimestres/{a}/{n}/parametre (lecture seule). */
(function(){
  const bloc = document.querySelector('[data-seuils]');
  if(!bloc || !window.fetch) return;
  const [annee, numero] = bloc.dataset.seuils.split('-');
  const n = v => v==null ? '—' : String(Number(v));
  fetch(`/api/trimestres/${encodeURIComponent(annee)}/${encodeURIComponent(numero)}/parametre`,
        {credentials:'same-origin', headers:{'Accept':'application/json'}})
    .then(r=> r.ok ? r.json() : null)
    .then(p=>{
      if(!p) return;
      const perf = p.seuilsNeufBox || {}, pot = p.seuilsNeufBoxPotentiel || {}, t = p.seuilsTalent || {};
      bloc.innerHTML = [
        `Performance : &lt;${n(perf.seuilMoyen)} faible · ${n(perf.seuilMoyen)} à &lt;${n(perf.seuilEleve)} moyenne · ≥${n(perf.seuilEleve)} élevée`,
        `Potentiel : &lt;${n(pot.seuilMoyen)} faible · ${n(pot.seuilMoyen)} à &lt;${n(pot.seuilEleve)} moyen · ≥${n(pot.seuilEleve)} élevé`,
        `Talent proposé : Performance ≥${n(t.seuilPerformance)} ET Potentiel ≥${n(t.seuilPotentiel)}`,
        `Haut potentiel : Performance ≥${n(t.seuilHautPotentielPerformance)} ET Potentiel ≥${n(t.seuilHautPotentielPotentiel)}`
      ].map(x=>`<span class="pill">${x}</span>`).join('');
    })
    .catch(()=>{});
})();

/* ---------- Viviers (prototype : listVivier, "Voir les membres") ----------
   Membres du vivier lus sur l'API existante /api/trimestres/{a}/{n}/collaborateurs?vivier=CODE. */
function readinessBadge(code, libelle){ if(!libelle) return '—'; const cls = code==='READY_NOW'?'g':(code==='PLUS_2_ANS'?'b':'o'); return `<span class="dot ${cls}"></span>${esc(libelle)}`; }
document.querySelectorAll('[data-vivier][data-trimestre]').forEach(el=> el.addEventListener('click', async ()=>{
  const trimestre = el.dataset.trimestre, nom = el.dataset.nom;
  let membres;
  try { membres = await collaborateurs(trimestre, `vivier=${encodeURIComponent(el.dataset.vivier)}`); } catch(err){ return; }
  openModal(`Vivier — ${nom} (${membres.length})`, membres.length ? `<table><thead><tr><th>Matricule</th><th>Nom</th><th>Poste actuel</th><th>Poste cible</th><th>Matching</th><th>Readiness</th></tr></thead><tbody>${
    membres.map(e=>`<tr data-href="${esc('/fiche-collaborateur?matricule='+encodeURIComponent(e.matricule)+'&trimestre='+encodeURIComponent(trimestre))}"><td class="mono">${esc(e.matricule)}</td><td>${esc(e.nomComplet)}</td><td>${esc(e.poste||'—')}</td><td>${esc(e.posteCible?e.posteCible.nomPoste:'—')}</td><td>${e.posteCible&&e.posteCible.scoreMatching!=null?esc(score(e.posteCible.scoreMatching))+'%':'—'}</td><td>${e.posteCible?readinessBadge(e.posteCible.readiness, e.posteCible.readinessLibelle):'—'}</td></tr>`).join('')}</tbody></table>`
    : `<div class="empty-state">Aucun membre pour le moment.</div>`);
  document.querySelectorAll('#modalBody tr[data-href]').forEach(tr=> tr.addEventListener('click', ()=> location.href = tr.dataset.href));
}));

/* ---------- Talent Passport (prototype : tp-search-input, change -> openPassport) ---------- */
(function(){
  const input = document.getElementById('tp-search-input');
  if(!input) return;
  input.addEventListener('change', ()=>{
    const liste = document.getElementById(input.getAttribute('list'));
    const m = input.value.split(' — ')[0].trim();
    if(liste && Array.prototype.some.call(liste.options, o=>o.value===m)){ input.value = m; input.form.submit(); }
  });
})();

/* ---------- Comite Talent : "Successions a valider" (prototype : renderComite) ----------
   Les successeurs identifies de chaque poste critique, lus sur l'API existante /api/postes-critiques
   (lecture seule). La validation des successions n'existe pas encore : boutons du prototype grises. */
(function(){
  const bloc = document.querySelector('[data-successions-trimestre]');
  if(!bloc || !window.fetch) return;
  const [annee, numero] = bloc.dataset.successionsTrimestre.split('-');
  const boutons = '<div class="decision-btns indispo" title="En cours de développement">'
    + '<button class="dbtn green" type="button" disabled>🟢 Valider</button>'
    + '<button class="dbtn amber" type="button" disabled>🟠 Valider avec plan</button>'
    + '<button class="dbtn blue" type="button" disabled>🔵 Maintenir en vivier</button>'
    + '<button class="dbtn grey" type="button" disabled>⚪ Réévaluer</button>'
    + '<button class="dbtn red" type="button" disabled>🔴 Ne pas retenir</button></div>';
  fetch(`/api/postes-critiques?annee=${encodeURIComponent(annee)}&numero=${encodeURIComponent(numero)}`,
        {credentials:'same-origin', headers:{'Accept':'application/json'}})
    .then(r=> r.ok ? r.json() : Promise.reject(r.status))
    .then(postes=>{
      const lignes = [];
      postes.forEach(pc=> (pc.successeurs||[]).forEach(s=>{
        lignes.push(`<div class="succ-row"><span class="m">${esc(s.candidat.idCollaborateur)}</span><span class="nm">${esc(s.candidat.nomComplet)} → ${esc(pc.nomPoste)} (${esc(score(s.scoreMatching))}%)</span>${boutons}</div>`);
      }));
      bloc.innerHTML = lignes.join('') || '<div class="empty-state">Aucun successeur identifié.</div>';
    })
    .catch(()=>{ bloc.innerHTML = '<div class="empty-state">Successeurs indisponibles.</div>'; });
})();

/* ---------- Talent Passport : detail du matching (prototype : .match-bd) ----------
   Sous-scores sur 100 du moteur pour le poste cible, lus sur l'API existante
   /api/postes/{poste}/candidats/{matricule} (lecture seule). Un critere non applicable n'est pas affiche. */
(function(){
  const bloc = document.querySelector('[data-matching-poste]');
  if(!bloc || !window.fetch) return;
  const [annee, numero] = (bloc.dataset.matchingTrimestre || '').split('-');
  const url = `/api/postes/${encodeURIComponent(bloc.dataset.matchingPoste)}/candidats/${encodeURIComponent(bloc.dataset.matchingMatricule)}`
            + `?annee=${encodeURIComponent(annee)}&numero=${encodeURIComponent(numero)}`;
  const libelles = [['competences','Compétences'],['experience','Expérience'],['performance','Performance'],
                    ['potentiel','Potentiel'],['leadership','Leadership'],['mobilite','Mobilité']];
  fetch(url, {credentials:'same-origin', headers:{'Accept':'application/json'}})
    .then(r=> r.ok ? r.json() : null)
    .then(m=>{
      if(!m || !m.detail) return;
      bloc.querySelector('.matching-detail').innerHTML = libelles.filter(([k])=> m.detail[k] != null).map(([k, l])=>
        `<div class="match-bd"><span>${l}</span><div class="bar-track"><div class="bar-fill" style="width:${Math.max(0, Math.min(100, Number(m.detail[k])))}%"></div></div><span class="mono">${esc(score(m.detail[k]))}%</span></div>`).join('');
    })
    .catch(()=>{});
})();
