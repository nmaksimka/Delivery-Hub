package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.entity.MenuSection;

import java.util.List;

public final class ProductShelves {

    public static final Shelf GROCERY = new Shelf(
            "GROCERY", MenuSection.PRODUCT, "Гастроном «Сибирский»", "Продукты", "ул. Нахимова, 15", "+73822410101");
    public static final Shelf BAR = new Shelf(
            "BAR", MenuSection.DRINK, "Напитки «Томь»", "Напитки", "пр. Ленина, 60", "+73822410202");

    private static final List<Group> GROUPS = List.of(
            new Group("waters", BAR, "Вода"),
            new Group("sodas", BAR, "Газировка"),
            new Group("fruit-juices", BAR, "Соки"),
            new Group("teas", BAR, "Чай"),
            new Group("dairies", GROCERY, "Молочные продукты"),
            new Group("breads", GROCERY, "Хлеб"),
            new Group("breakfast-cereals", GROCERY, "Завтраки"),
            new Group("chocolates", GROCERY, "Сладости"));

    private ProductShelves() {
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public record Shelf(String externalId, MenuSection section, String restaurantName, String cuisine,
                        String street, String phone) {

        public String address() {
            return "г. Томск, " + street;
        }
    }

    public record Group(String categoryTag, Shelf shelf, String menuCategory) {
    }
}
