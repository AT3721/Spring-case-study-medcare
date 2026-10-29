package com.codegym.repository;

import com.codegym.model.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface IDoctorRepository extends JpaRepository<Doctor, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

    @Query("select d from Doctor d where d.active = true and d.specialty.active = true")
    Page<Doctor> findAllActive(Pageable pageable);

    @Query("select d from Doctor d where d.id = :id and d.active = true and d.specialty.active = true")
    Optional<Doctor> findActiveById(@Param("id") Long id);

    // Lọc danh sách Bác sĩ theo Chuyên khoa kèm Phân trang
    Page<Doctor> findBySpecialtyId(Long specialtyId, Pageable pageable);

    @Query("select d from Doctor d where d.active = true and d.specialty.active = true and d.specialty.id = :specialtyId")
    java.util.List<Doctor> findActiveBySpecialtyId(@Param("specialtyId") Long specialtyId);

    // Tìm kiếm Bác sĩ theo tên (không phân biệt hoa thường) kèm Phân trang
    Page<Doctor> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
            select d from Doctor d
            where d.active = true and d.specialty.active = true
              and (:specialtyId is null or d.specialty.id = :specialtyId)
              and (:searchTerm is null or lower(d.name) like lower(concat('%', :searchTerm, '%')))
            """)
    Page<Doctor> searchDoctors(@Param("specialtyId") Long specialtyId,
                               @Param("searchTerm") String searchTerm,
                               Pageable pageable);
}