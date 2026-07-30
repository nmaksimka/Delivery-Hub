package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.mealdb.MealDbCategory;
import com.deliveryhub.restaurant.mealdb.MealDbClient;
import com.deliveryhub.restaurant.mealdb.MealDbMeal;
import com.deliveryhub.restaurant.mealdb.MealDbProperties;
import com.deliveryhub.restaurant.service.CatalogCache;
import com.deliveryhub.restaurant.storage.ImageMirror;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MealImportTest {

    private static final UUID JOB = UUID.randomUUID();
    private static final MealDbMeal BROKEN = new MealDbMeal("1", "Broken", "Beef", "Italian", "Cook.", null);
    private static final MealDbMeal FINE = new MealDbMeal("2", "Fine", "Beef", "Italian", "Cook.", null);
    private static final MealDbMeal ODD_ID = new MealDbMeal("../3", "Odd", "Beef", "Italian", "Cook.", null);

    private final MealDbClient client = mock(MealDbClient.class);
    private final MealCatalogWriter writer = mock(MealCatalogWriter.class);
    private final ImportProgress progress = mock(ImportProgress.class);
    private final ImageMirror imageMirror = mock(ImageMirror.class);
    private final MealImport mealImport = new MealImport(client, writer, progress, properties(), imageMirror,
            mock(CatalogCache.class));

    @Test
    void skipsMealThatCannotBeStoredAndFinishesTheJob() {
        when(client.categories()).thenReturn(List.of(new MealDbCategory("1", "Beef", null)));
        when(client.mealsOfCategory("Beef")).thenReturn(List.of(BROKEN, FINE, ODD_ID));
        when(client.meal(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case "1" -> Optional.of(BROKEN);
            case "2" -> Optional.of(FINE);
            default -> Optional.of(ODD_ID);
        });
        when(imageMirror.mirror(anyString(), any())).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("value too long")).when(writer).store(JOB, BROKEN, null);

        mealImport.run(JOB);

        verify(writer).store(JOB, FINE, null);
        verify(writer, never()).store(eq(JOB), eq(ODD_ID), any());
        verify(progress, times(2)).skipped(JOB);
        verify(progress).completed(JOB);
        verify(progress, never()).failed(eq(JOB), any());
    }

    private static MealDbProperties properties() {
        return new MealDbProperties("http://localhost", Duration.ofSeconds(1), Duration.ofSeconds(1), 5,
                Duration.ofSeconds(1), 1, Duration.ofMillis(1), Duration.ofHours(1), 12);
    }
}
