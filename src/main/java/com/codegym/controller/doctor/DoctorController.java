package com.codegym.controller.doctor;

import com.codegym.model.Doctor;
import com.codegym.model.Specialty;
import com.codegym.dto.doctor.DoctorDirectoryForm;
import com.codegym.service.doctor.IDoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctors")
public class DoctorController {

    @Autowired
    private IDoctorService doctorService;

    @GetMapping
    public String listDoctors(
            @ModelAttribute DoctorDirectoryForm filters,
            @PageableDefault(size = 6, sort = "name") Pageable pageable,
            Model model) {
        Long specialtyId = filters.getSpecialtyId();
        String searchTerm = filters.getQ();
        String sortBy = filters.getSortBy();
        populateDirectoryModel(specialtyId, searchTerm, sortBy, pageable, model);
        return "doctor/list";
    }

    @GetMapping("/results")
    public String listDoctorResults(
            @ModelAttribute DoctorDirectoryForm filters,
            @PageableDefault(size = 6, sort = "name") Pageable pageable,
            Model model) {
        Long specialtyId = filters.getSpecialtyId();
        String searchTerm = filters.getQ();
        String sortBy = filters.getSortBy();
        populateDirectoryModel(specialtyId, searchTerm, sortBy, pageable, model);
        return "fragments/doctor-results :: content";
    }

    private void populateDirectoryModel(Long specialtyId, String searchTerm, String sortBy,
                                        Pageable pageable, Model model) {
        String normalizedSearch = searchTerm == null ? null : searchTerm.trim();
        if (normalizedSearch != null && normalizedSearch.isEmpty()) {
            normalizedSearch = null;
        }
        if (normalizedSearch != null && normalizedSearch.length() > 80) {
            normalizedSearch = normalizedSearch.substring(0, 80);
        }

        Sort sort = switch (sortBy) {
            case "feeAsc" -> Sort.by(Sort.Direction.ASC, "consultationFee").and(Sort.by("name"));
            case "feeDesc" -> Sort.by(Sort.Direction.DESC, "consultationFee").and(Sort.by("name"));
            default -> Sort.by("name");
        };
        Pageable queryPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        Page<Doctor> doctors = doctorService.searchDoctors(specialtyId, normalizedSearch, queryPageable);

        List<Specialty> specialties = doctorService.findAllSpecialties();
        Map<Long, List<Doctor>> doctorsBySpecialty = doctorService.findAllDoctors(Pageable.unpaged())
                .getContent().stream()
                .collect(Collectors.groupingBy(doctor -> doctor.getSpecialty().getId()));

        model.addAttribute("doctors", doctors);
        model.addAttribute("specialties", specialties);
        model.addAttribute("doctorsBySpecialty", doctorsBySpecialty);
        model.addAttribute("selectedSpecialtyId", specialtyId);
        model.addAttribute("searchTerm", normalizedSearch);
        model.addAttribute("selectedSortBy", sortBy.equals("feeAsc") || sortBy.equals("feeDesc") ? sortBy : "name");
    }

    @GetMapping("/{id}")
    public String doctorDetail(@PathVariable("id") Long id, Model model) {
        Doctor doctor = doctorService.findDoctorById(id);
        if (doctor == null) {
            return "redirect:/doctors";
        }
        model.addAttribute("doctor", doctor);
        return "doctor/detail";
    }
}