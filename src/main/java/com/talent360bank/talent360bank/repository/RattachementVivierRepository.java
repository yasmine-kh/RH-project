package com.talent360bank.talent360bank.repository;

import com.talent360bank.talent360bank.entity.RattachementVivier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RattachementVivierRepository extends JpaRepository<RattachementVivier, Integer> {

    Optional<RattachementVivier> findByDirection(String direction);
}
