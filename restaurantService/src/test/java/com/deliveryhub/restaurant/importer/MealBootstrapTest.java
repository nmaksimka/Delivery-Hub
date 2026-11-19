package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MealBootstrapTest {

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final ImportJobService importJobService = mock(ImportJobService.class);

    @Test
    void startsMealImportWhenCatalogIsEmpty() {
        when(repository.count()).thenReturn(0L);

        bootstrap(true).importMealsIfCatalogIsEmpty();

        verify(importJobService).start(ImportSource.MEALDB);
    }

    @Test
    void skipsImportWhenRestaurantsExist() {
        when(repository.count()).thenReturn(3L);

        bootstrap(true).importMealsIfCatalogIsEmpty();

        verify(importJobService, never()).start(ImportSource.MEALDB);
    }

    @Test
    void skipsImportWhenDisabled() {
        bootstrap(false).importMealsIfCatalogIsEmpty();

        verify(repository, never()).count();
        verify(importJobService, never()).start(ImportSource.MEALDB);
    }

    @Test
    void doesNotFailStartupWhenImportCannotBeStarted() {
        when(repository.count()).thenReturn(0L);
        when(importJobService.start(ImportSource.MEALDB))
                .thenThrow(new ImportAlreadyRunningException(ImportSource.MEALDB));

        assertThatCode(() -> bootstrap(true).importMealsIfCatalogIsEmpty()).doesNotThrowAnyException();
    }

    private MealBootstrap bootstrap(boolean enabled) {
        return new MealBootstrap(new BootstrapProperties(enabled), repository, importJobService);
    }
}
