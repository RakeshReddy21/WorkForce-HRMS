package org.example.workforce.service;

import org.example.workforce.dto.DesignationRequest;
import org.example.workforce.exception.DuplicateResourceException;
import org.example.workforce.exception.InvalidActionException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Designation;
import org.example.workforce.repository.DesignationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DesignationService {
    @Autowired
    private DesignationRepository designationRepository;

    public Designation createDesignation(DesignationRequest request) {
        if (designationRepository.existsByDesignationName(request.getDesignationName())) {
            throw new DuplicateResourceException("Designation '" + request.getDesignationName() + "' already exists");
        }
        Designation designation = Designation.builder()
                .designationName(request.getDesignationName())
                .description(request.getDescription())
                .build();
        return designationRepository.save(designation);
    }

    public Designation updateDesignation(Integer designationId, DesignationRequest request) {
        Designation designation = designationRepository.findById(designationId)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found with id: " + designationId));
        designationRepository.findByDesignationName(request.getDesignationName()).ifPresent(existing -> {
            if (!existing.getDesignationId().equals(designationId)) {
                throw new DuplicateResourceException("Designation '" + request.getDesignationName() + "' already exists");
            }
        });
        designation.setDesignationName(request.getDesignationName());
        designation.setDescription(request.getDescription());
        return designationRepository.save(designation);
    }

    public Designation deactivateDesignation(Integer designationId) {
        Designation designation = designationRepository.findById(designationId)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found with id: " + designationId));
        if (!designation.getIsActive()) {
            throw new InvalidActionException("Designation is already deactivated");
        }
        designation.setIsActive(false);
        return designationRepository.save(designation);
    }

    public Designation activateDesignation(Integer designationId) {
        Designation designation = designationRepository.findById(designationId)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found with id: " + designationId));
        if (designation.getIsActive()) {
            throw new InvalidActionException("Designation is already active");
        }
        designation.setIsActive(true);
        return designationRepository.save(designation);
    }

    public List<Designation> getAllDesignations() {
        return designationRepository.findAll();
    }

    public Designation getDesignationById(Integer designationId) {
        return designationRepository.findById(designationId)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found with id: " + designationId));
    }
}
