package com.deliveryhub.search.api;

import com.deliveryhub.search.catalog.CatalogSearch;
import com.deliveryhub.search.catalog.SearchKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/search")
public class SearchController {

    private final CatalogSearch catalogSearch;

    public SearchController(CatalogSearch catalogSearch) {
        this.catalogSearch = catalogSearch;
    }

    @GetMapping
    public SearchPageDto search(@RequestParam @NotBlank @Size(max = 100) String q,
                                @RequestParam(defaultValue = "ALL") SearchKind kind,
                                @RequestParam(defaultValue = "0") @Min(0) @Max(100) int page,
                                @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) throws IOException {
        return catalogSearch.search(q.strip(), kind, page, size);
    }

    @GetMapping("/suggest")
    public List<SuggestionDto> suggest(@RequestParam @NotBlank @Size(max = 50) String q) throws IOException {
        return catalogSearch.suggest(q.strip());
    }
}
