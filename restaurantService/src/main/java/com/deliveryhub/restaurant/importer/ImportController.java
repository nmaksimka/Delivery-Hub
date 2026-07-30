package com.deliveryhub.restaurant.importer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/imports")
public class ImportController {

    private final ImportJobService importJobService;

    public ImportController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @PostMapping("/meals")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportJobDto importMeals() {
        return importJobService.start(ImportSource.MEALDB);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportJobDto importProducts() {
        return importJobService.start(ImportSource.OPENFOODFACTS);
    }

    @GetMapping("/{jobId}")
    public ImportJobDto getJob(@PathVariable UUID jobId) {
        return importJobService.get(jobId);
    }

    @GetMapping
    public List<ImportJobDto> recentJobs() {
        return importJobService.recent();
    }
}
