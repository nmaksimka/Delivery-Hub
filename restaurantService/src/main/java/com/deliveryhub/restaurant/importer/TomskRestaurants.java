package com.deliveryhub.restaurant.importer;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class TomskRestaurants {

    public static final String CITY = "Томск";
    public static final LocalTime OPENS_AT = LocalTime.of(10, 0);
    public static final LocalTime CLOSES_AT = LocalTime.of(23, 0);

    private static final Profile WORLD = new Profile("Кухни мира", "Смешанная");

    private static final Map<String, Profile> BY_AREA = Map.ofEntries(
            Map.entry("American", new Profile("Бруклин Гриль", "Американская")),
            Map.entry("British", new Profile("Паб «Старый Лондон»", "Британская")),
            Map.entry("Canadian", new Profile("Кленовый лист", "Канадская")),
            Map.entry("Chinese", new Profile("Золотой дракон", "Китайская")),
            Map.entry("Croatian", new Profile("Адриатика", "Хорватская")),
            Map.entry("Dutch", new Profile("Амстердам", "Голландская")),
            Map.entry("Egyptian", new Profile("Берега Нила", "Египетская")),
            Map.entry("Filipino", new Profile("Манила", "Филиппинская")),
            Map.entry("French", new Profile("Бистро «Прованс»", "Французская")),
            Map.entry("Greek", new Profile("Таверна «Олива»", "Греческая")),
            Map.entry("Indian", new Profile("Масала", "Индийская")),
            Map.entry("Irish", new Profile("Дублин", "Ирландская")),
            Map.entry("Italian", new Profile("Траттория «Белла Виста»", "Итальянская")),
            Map.entry("Jamaican", new Profile("Ямайка Джерк", "Ямайская")),
            Map.entry("Japanese", new Profile("Токио Рамен", "Японская")),
            Map.entry("Kenyan", new Profile("Саванна", "Кенийская")),
            Map.entry("Malaysian", new Profile("Куала-Лумпур", "Малайзийская")),
            Map.entry("Mexican", new Profile("Кантина «Агава»", "Мексиканская")),
            Map.entry("Moroccan", new Profile("Марракеш", "Марокканская")),
            Map.entry("Polish", new Profile("Пироги и компания", "Польская")),
            Map.entry("Portuguese", new Profile("Лиссабон", "Португальская")),
            Map.entry("Russian", new Profile("Сибирская трапеза", "Русская")),
            Map.entry("Spanish", new Profile("Тапас-бар «Севилья»", "Испанская")),
            Map.entry("Thai", new Profile("Том Ям", "Тайская")),
            Map.entry("Tunisian", new Profile("Карфаген", "Тунисская")),
            Map.entry("Turkish", new Profile("Стамбул Кебаб", "Турецкая")),
            Map.entry("Ukrainian", new Profile("Хата", "Украинская")),
            Map.entry("Vietnamese", new Profile("Фо Ханой", "Вьетнамская")));

    private static final List<String> STREETS = List.of(
            "пр. Ленина", "ул. Красноармейская", "пр. Фрунзе", "пр. Кирова", "ул. Нахимова", "ул. Учебная",
            "пр. Комсомольский", "ул. Алтайская", "ул. Герцена", "ул. Советская", "ул. Белинского",
            "ул. Карташова", "ул. Вершинина", "пер. 1905 года", "ул. Розы Люксембург", "Иркутский тракт",
            "ул. Сибирская", "ул. Елизаровых", "ул. Усова", "ул. Гагарина");

    private static final Map<String, String> CATEGORIES = Map.ofEntries(
            Map.entry("Beef", "Говядина"),
            Map.entry("Breakfast", "Завтраки"),
            Map.entry("Chicken", "Курица"),
            Map.entry("Dessert", "Десерты"),
            Map.entry("Goat", "Козлятина"),
            Map.entry("Lamb", "Баранина"),
            Map.entry("Miscellaneous", "Разное"),
            Map.entry("Pasta", "Паста"),
            Map.entry("Pork", "Свинина"),
            Map.entry("Seafood", "Морепродукты"),
            Map.entry("Side", "Гарниры"),
            Map.entry("Starter", "Закуски"),
            Map.entry("Vegan", "Веганское"),
            Map.entry("Vegetarian", "Вегетарианское"));

    private TomskRestaurants() {
    }

    public static Profile forArea(String area) {
        return BY_AREA.getOrDefault(areaKey(area), WORLD);
    }

    public static String areaKey(String area) {
        return area != null && BY_AREA.containsKey(area) ? area : "World";
    }

    public static String addressFor(String area) {
        int seed = Math.floorMod(areaKey(area).hashCode(), 10_000);
        return "г. Томск, " + STREETS.get(seed % STREETS.size()) + ", " + (1 + seed % 120);
    }

    public static String phoneFor(String area) {
        int seed = Math.floorMod(areaKey(area).hashCode(), 900_000);
        return "+73822" + String.format("%06d", 100_000 + seed);
    }

    public static String categoryName(String category) {
        if (category == null || category.isBlank()) {
            return "Разное";
        }
        return CATEGORIES.getOrDefault(category, category);
    }

    public record Profile(String name, String cuisine) {
    }
}
