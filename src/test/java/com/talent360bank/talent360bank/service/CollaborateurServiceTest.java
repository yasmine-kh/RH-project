package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.controller.dto.CollaborateurDTO;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollaborateurServiceTest {

    @Mock
    private CollaborateurRepository collaborateurRepository;

    @InjectMocks
    private CollaborateurService collaborateurService;

    private Collaborateur collaborateur;

    @BeforeEach
    void setUp() {
        collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur("BP001");
        collaborateur.setNom("Dupont");
        collaborateur.setPrenom("Jean");
        collaborateur.setStatut(StatutCollaborateur.ACTIF);
    }

    @Test
    void testArchiverCollaborateur_Succes() {
        when(collaborateurRepository.findById("BP001")).thenReturn(Optional.of(collaborateur));

        collaborateurService.archiverCollaborateur("BP001");

        assertEquals(StatutCollaborateur.ARCHIVE, collaborateur.getStatut());
        verify(collaborateurRepository, times(1)).save(collaborateur);
    }

    @Test
    void testArchiverCollaborateur_NonTrouve() {
        when(collaborateurRepository.findById("INCONNU")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                collaborateurService.archiverCollaborateur("INCONNU")
        );
    }
}