package com.codegym.repository;

import com.codegym.model.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface ISpecialtyRepository extends JpaRepository<Specialty, Long> {
    List<Specialty> findByActiveTrueOrderByNameAsc();
}