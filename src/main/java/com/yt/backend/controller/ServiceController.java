package com.yt.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yt.backend.dto.ServiceLimitedDTO;
import com.yt.backend.model.*;
import com.yt.backend.model.category.Category;
import com.yt.backend.model.category.Subcategory;
import com.yt.backend.model.user.Role;
import com.yt.backend.model.user.User;
import com.yt.backend.repository.CategoryRepository;
import com.yt.backend.repository.ImageRepository;
import com.yt.backend.repository.KeywordRepository;
import com.yt.backend.repository.ServiceRepository;
import com.yt.backend.repository.SubcategoryRepository;
import com.yt.backend.repository.UserRepository;
import com.yt.backend.service.ServiceService;
import com.yt.backend.service.ImageService;
import com.yt.backend.exception.ResourceNotFoundException;
import com.yt.backend.exception.BusinessException;
import com.yt.backend.exception.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;


@RequestMapping("/api/v1")
@RestController
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final KeywordRepository keywordRepository;
    private final ImageRepository imageRepository;
    private final ImageService imageService;
    private final ServiceService serviceService;

    public ServiceController(ServiceRepository serviceRepository, UserRepository userRepository,
            CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
            ServiceService serviceService, KeywordRepository keywordRepository, ImageRepository imageRepository,
            ImageService imageService) {
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.keywordRepository = keywordRepository;
        this.serviceService = serviceService;
        this.imageRepository = imageRepository;
        this.imageService = imageService;
    }

    @Operation(summary = "Get all services", description = "Retrieve a list of all services with limited information for unauthenticated users")
    @GetMapping("/services")
    public ResponseEntity<List<?>> getAllServices(Authentication authentication) {
        List<Service> services = serviceRepository.findAll();
        if (services.isEmpty()) {
            throw new ResourceNotFoundException("No services found");
        }
        
        // Check if user is authenticated
        boolean isAuthenticated = (authentication != null && authentication.isAuthenticated());
        
        if (isAuthenticated) {
            // Return full service details for authenticated users
            return ResponseEntity.ok(services);
        } else {
            // Return limited service details for unauthenticated users
            List<ServiceLimitedDTO> limitedServices = services.stream()
                .map(this::convertToLimitedDTO)
                .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(limitedServices);
        }
    }
    
    // Helper method to convert Service to a DTO with limited information
    private ServiceLimitedDTO convertToLimitedDTO(Service service) {
        ServiceLimitedDTO dto = new ServiceLimitedDTO();
        dto.setId(service.getId());
        dto.setName(service.getName());
        dto.setDescription(service.getDescription());
        
        // Include category information
        if (service.getCategory() != null) {
            dto.setCategoryId(service.getCategory().getId());
            dto.setCategoryName(service.getCategory().getName());
        }
        
        // Include subcategory information if available
        if (service.getSubcategory() != null) {
            dto.setSubcategoryId(service.getSubcategory().getId());
            dto.setSubcategoryName(service.getSubcategory().getName());
        }
        
        // Include basic professional information without sensitive details
        if (service.getProfessional() != null) {
            User professional = service.getProfessional();
            dto.setProfessionalId(professional.getId());
            dto.setProfessionalName(professional.getFirstname() + " " + professional.getLastname());
            dto.setProfessionalProfileImage(professional.getProfileImage());
        }
        
        // Include keywords
        if (service.getKeywordList() != null && !service.getKeywordList().isEmpty()) {
            List<String> keywordNames = service.getKeywordList().stream()
                .map(Keyword::getKeywordName)
                .collect(java.util.stream.Collectors.toList());
            dto.setKeywords(keywordNames);
        }
        
        // Include primary image ID (first image in the list)
        if (service.getImages() != null && !service.getImages().isEmpty()) {
            dto.setPrimaryImageId(service.getImages().get(0).getId());
        }
        
        // Set the creation date
        dto.setCreatedAt(service.getCreatedAt());
        
        // Set the city from the address
        if (service.getAdress() != null) {
            dto.setCity(service.getAdress().getCity());
        }
        
        return dto;
    }

    @Operation(summary = "Get service by ID", description = "Retrieve a service by its unique identifier")
    @GetMapping("/services/{id}")
    public ResponseEntity<Service> getServiceById(@PathVariable("id") long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        return ResponseEntity.ok(service);
    }

    @Operation(summary = "Create a new service", description = "Allows authorized users to create a new service")
    @ApiResponse(responseCode = "201", description = "Service created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body or insufficient privileges")
    @PostMapping(value = "/services/createService", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> createService(
            @RequestPart("service") String serviceJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Service service = mapper.readValue(serviceJson, Service.class);
            
            // Validate service data
            Map<String, String> validationErrors = validateService(service);
            if (!validationErrors.isEmpty()) {
                throw new ValidationException("Erreurs de validation lors de la création du service", validationErrors);
            }
            
            return createSingleService(service, images);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Invalid JSON format: " + e.getMessage());
        }
    }

    private Map<String, String> validateService(Service service) {
        Map<String, String> errors = new HashMap<>();
        
        // Validate name
        if (service.getName() == null || service.getName().trim().isEmpty()) {
            errors.put("name", "Le nom du service est obligatoire");
        } else if (service.getName().length() < 3 || service.getName().length() > 100) {
            errors.put("name", "Le nom du service doit contenir entre 3 et 100 caractères");
        }
        
        // Validate description
        if (service.getDescription() == null || service.getDescription().trim().isEmpty()) {
            errors.put("description", "La description du service est obligatoire");
        } else if (service.getDescription().length() < 10 || service.getDescription().length() > 3000) {
            errors.put("description", "La description doit contenir entre 10 et 3000 caractères");
        }
        
        // Validate professional
        if (service.getProfessional() == null || service.getProfessional().getId() == null) {
            errors.put("professional", "L'identifiant du professionnel est obligatoire");
        }
        
        // Validate category
        if (service.getCategory() == null || service.getCategory().getId() == null) {
            errors.put("category", "La catégorie est obligatoire");
        }
        
        // Validate address if present
        if (service.getAdress() != null) {
            if (service.getAdress().getCity() == null || service.getAdress().getCity().trim().isEmpty()) {
                errors.put("address.city", "La ville est obligatoire");
            }
            if (service.getAdress().getCountry() == null || service.getAdress().getCountry().trim().isEmpty()) {
                errors.put("address.country", "Le pays est obligatoire");
            }
        } else {
            errors.put("address", "L'adresse est obligatoire");
        }
        
        return errors;
    }

    // Update the createSingleService method to use ServiceState.ACTIVE instead of Boolean.TRUE
    private ResponseEntity<Service> createSingleService(Service service, List<MultipartFile> imageFiles) {
        User professional = userRepository.findById(service.getProfessional().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found with id: " + service.getProfessional().getId()));
                
        Category category = categoryRepository.findById(service.getCategory().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + service.getCategory().getId()));
                
        Subcategory subcategory = null;
        if (service.getSubcategory() != null && service.getSubcategory().getId() != null) {
            subcategory = subcategoryRepository.findById(service.getSubcategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found with id: " + service.getSubcategory().getId()));
        }

        if (!Role.valueOf("ADMIN").equals(professional.getRole()) && !Role.valueOf("PROFESSIONAL").equals(professional.getRole())) {
            throw new BusinessException("The user is not a professional or an admin. Please upgrade your privileges and try again!");
        }
            
        // Create new service with the fetched entities
        Service _service = new Service(
            service.getName(),
            service.getDescription(),
            professional,
            category,
            subcategory,
            ServiceState.ACTIVE, // Changed from Boolean.TRUE to ServiceState.ACTIVE
            service.getAdress(),
            service.getLinks()
        );
        
        // Save the service first to get its ID
        _service = serviceRepository.save(_service);

        // Handle keywords
        if (service.getKeywordList() != null && !service.getKeywordList().isEmpty()) {
            for (Keyword keyword : service.getKeywordList()) {
                keyword.setService(_service);
                _service.addKeyword(keyword);
            }
            keywordRepository.saveAll(_service.getKeywordList());
        }

        // Handle image uploads
        if (imageFiles != null && !imageFiles.isEmpty()) {
            List<Image> images = new ArrayList<>();
            for (MultipartFile imageFile : imageFiles) {
                if (!imageFile.isEmpty()) {
                    validateImageFile(imageFile);
                    try {
                        Image image = imageService.addImageToService(imageFile, _service.getId());
                        images.add(image);
                    } catch (IOException e) {
                        throw new BusinessException("Error processing image file: " + e.getMessage());
                    }
                }
            }
            _service.setImages(images);
        }

        // Save the final service with all relationships
        _service = serviceRepository.save(_service);
        return new ResponseEntity<>(_service, HttpStatus.CREATED);
    }

    // Update validateImageFile to use ValidationException
    private void validateImageFile(MultipartFile file) {
        Map<String, String> errors = new HashMap<>();
        
        if (file.isEmpty()) {
            errors.put("file", "Le fichier est vide");
        }
    
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            errors.put("fileType", "Type de fichier invalide. Seules les images sont autorisées.");
        }
    
        if (file.getSize() > 5 * 1024 * 1024) {
            errors.put("fileSize", "Taille du fichier trop grande. La taille maximale autorisée est de 5MB");
        }
        
        if (!errors.isEmpty()) {
            throw new ValidationException("Erreurs de validation du fichier image", errors);
        }
    }

    @Operation(summary = "Update an existing service", description = "Allows authorized users to update an existing service")
    @ApiResponse(responseCode = "200", description = "Service updated successfully")
    @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    @PutMapping("/services/updateService/{id}")
    public ResponseEntity<?> updateService(@PathVariable("id") long id, @RequestBody Service service,
            Authentication authentication) {
        if (!serviceService.isAdminOrProfessional(authentication)) {
            throw new BusinessException("Insufficient privileges");
        }
    
        Service _service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
    
        // Validate service data
        Map<String, String> validationErrors = validateService(service);
        if (!validationErrors.isEmpty()) {
            throw new ValidationException("Erreurs de validation lors de la mise à jour du service", validationErrors);
        }
    
        // Update all fields
        _service.setName(service.getName());
        _service.setDescription(service.getDescription());
        _service.setState(service.getState());
        _service.setCategory(service.getCategory());
        _service.setSubcategory(service.getSubcategory());
        _service.setProfessional(service.getProfessional());
        _service.setAdress(service.getAdress());
        _service.setLinks(service.getLinks());
        _service.setChecked(service.getChecked());
    
        // Handle keywords
        if (service.getKeywordList() != null) {
            _service.getKeywordList().clear();
            for (Keyword keyword : service.getKeywordList()) {
                keyword.setService(_service);
                _service.getKeywordList().add(keyword);
            }
        }
    
        // Handle images
        if (service.getImages() != null) {
            _service.getImages().clear();
            for (Image image : service.getImages()) {
                image.setService(_service);
                _service.getImages().add(image);
            }
        }
    
        Service updatedService = serviceRepository.save(_service);
        return ResponseEntity.ok(updatedService);
    }
    
    @Operation(summary = "Delete a service by ID", description = "Allows authorized users to delete a service by its ID")
    @ApiResponse(responseCode = "204", description = "Service deleted successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @DeleteMapping("/services/deleteService/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable("id") long id, Authentication authentication) {
        if (!serviceService.isAdminOrProfessional(authentication)) {
            throw new BusinessException("Insufficient privileges");
        }

        if (!serviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Service not found with id: " + id);
        }

        serviceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete all services", description = "Allows authorized users to delete all services")
    @ApiResponse(responseCode = "204", description = "All services deleted successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @DeleteMapping("/services/deleteAllServices")
    public ResponseEntity<Void> deleteAllServices(Authentication authentication) {
        if (!serviceService.isAdminOrProfessional(authentication)) {
            throw new BusinessException("Insufficient privileges");
        }
        serviceRepository.deleteAll();
        return ResponseEntity.noContent().build();
    }

@Operation(summary = "Get recent professionals", description = "Get a list of recent professionals with an optional limit on the number of rows returned (Max-Rows).")
@GetMapping("/services/recentProfessionals")
public ResponseEntity<List<ServiceLimitedDTO>> getRecentProfessionals(
        @RequestHeader(value = "Max-Rows", required = false) Integer maxRows) {

    List<Service> recentServices = serviceRepository.findAllByOrderByCreatedAtDesc();

    // Limiter les services si Max-Rows est précisé
    if (maxRows != null && maxRows > 0 && maxRows < recentServices.size()) {
        recentServices = recentServices.subList(0, maxRows);
    }

    if (recentServices.isEmpty()) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    List<ServiceLimitedDTO> limitedServices = recentServices.stream()
            .map(this::convertToLimitedDTO)
            .collect(Collectors.toList());

    return new ResponseEntity<>(limitedServices, HttpStatus.OK);
}

@Operation(summary = "Get popular professionals", description = "Retrieve a list of popular professionals with an optional limit on the number of rows returned (Max-Rows).")
@GetMapping("/services/popularProfessionals")
public ResponseEntity<List<ServiceLimitedDTO>> getPopularProfessionals(
        @RequestHeader(value = "Max-Rows", required = false) Integer maxRows) {

    List<Service> popularServices = serviceRepository.findAllByOrderByNbrConsultationsDesc();

    // Limiter les services si Max-Rows est précisé
    if (maxRows != null && maxRows > 0 && maxRows < popularServices.size()) {
        popularServices = popularServices.subList(0, maxRows);
    }

    if (popularServices.isEmpty()) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    List<ServiceLimitedDTO> limitedServices = popularServices.stream()
            .map(this::convertToLimitedDTO)
            .collect(Collectors.toList());

    return new ResponseEntity<>(limitedServices, HttpStatus.OK);
}

    public SubcategoryRepository getSubcategoryRepository() {
        return subcategoryRepository;
    }

    @Operation(summary = "Get services by category and subcategory", description = "Retrieve a list of services by category and subcategory IDs")
    @GetMapping("/services/byCategoryAndSubcategory/{categoryId}/{subcategoryId}")
    public ResponseEntity<? extends Object> getServicesByCategoryAndSubcategory(
            @PathVariable("categoryId") long categoryId, @PathVariable("subcategoryId") long subcategoryId) {
        Optional<Category> categoryOptional = categoryRepository.findById(categoryId);
        Optional<Subcategory> subcategoryOptional = subcategoryRepository.findById(subcategoryId);

        if (categoryOptional.isPresent() && subcategoryOptional.isPresent()) {
            Category category = categoryOptional.get();
            Subcategory subcategory = subcategoryOptional.get();

            if (!category.getSubcategories().contains(subcategory)) {
                return new ResponseEntity<String>(
                        "Subcategory with ID " + subcategoryId + " does not belong to category with ID " + categoryId,
                        HttpStatus.BAD_REQUEST);
            }

            List<Service> services = serviceRepository.findByCategoryAndSubcategory(category, subcategory);
            if (services.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<List<Service>>(services, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Get services by category", description = "Retrieve a list of services by category ID")
    @GetMapping("/services/byCategory/{categoryId}")
    public ResponseEntity<? extends Object> getServicesByCategory(@PathVariable("categoryId") long categoryId) {
        Optional<Category> categoryOptional = categoryRepository.findById(categoryId);

        if (categoryOptional.isPresent()) {
            Category category = categoryOptional.get();

            List<Service> services = serviceRepository.findByCategory(category);
            if (services.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<List<Service>>(services, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Get services by user", description = "Retrieve a list of services by user ID")
    @GetMapping("/services/byUser/{userId}")
    public ResponseEntity<?> getServicesByUser(@PathVariable("userId") long userId) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            List<Service> services = serviceRepository.findByProfessional(user);
            if (services.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(services, HttpStatus.OK);
        } else {
            return new ResponseEntity<>("User with ID " + userId + " not found", HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Get services by keyword", description = "Retrieve a list of services matching the provided keywords or partial keywords")
    @GetMapping("/services/byKeyword")
    public ResponseEntity<?> getServicesByKeyword(@RequestParam List<String> keywords) {
        List<Keyword> keywordList = new ArrayList<>();
        for (String keyword : keywords) {
            // Using a method that finds keywords that start with the provided string (partial match)
            keywordList.addAll(keywordRepository.findByKeywordNameStartingWithIgnoreCase(keyword));
        }
        
        if (keywordList.isEmpty()) {
            return new ResponseEntity<>("No services found for the provided keywords", HttpStatus.NOT_FOUND);
        }
        
        List<Service> services = serviceRepository.findByKeywordListIn(keywordList);
        if (services.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        
        return new ResponseEntity<>(services, HttpStatus.OK);
    }


    // @Operation(summary = "Get services by keyword", description = "Retrieve a list of services matching the provided keywords")
    // @GetMapping("/services/byKeyword")
    // public ResponseEntity<?> getServicesByKeyword(@RequestParam List<String> keywords) {
    //     List<Keyword> keywordList = new ArrayList<>();
    //     for (String keyword : keywords) {
    //         // Assuming you have a method in KeywordRepository to find by keywordName
    //         keywordList.addAll(keywordRepository.findByKeywordNameIgnoreCase(keyword));
    //     }
    //     if (keywordList.isEmpty()) {
    //         return new ResponseEntity<>("No services found for the provided keywords", HttpStatus.NOT_FOUND);
    //     }
    //     List<Service> services = serviceRepository.findByKeywordListIn(keywordList);
    //     if (services.isEmpty()) {
    //         return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    //     }
    //     return new ResponseEntity<>(services, HttpStatus.OK);
    // }
    @Operation(summary = "Update service state", description = "Allows authorized users to update the state of a service")
@ApiResponse(responseCode = "200", description = "Service state updated successfully")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "404", description = "Service not found")
@PutMapping("/services/{id}/updateState")
public ResponseEntity<Service> updateServiceState(
        @PathVariable("id") long id,
        @io.swagger.v3.oas.annotations.Parameter(description = "New service state (ACTIVE, INACTIVE, SUSPENDED)", required = true)
        @RequestParam ServiceState state,
        Authentication authentication) {
    
    if (!serviceService.isAdminOrProfessional(authentication)) {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }
    
    Service updatedService = serviceService.updateServiceState(id, state);
    return ResponseEntity.ok(updatedService);
}

}



